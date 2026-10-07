package org.example.app

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kdesign.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.*
import java.time.Duration
import java.util.concurrent.Executors

class FileStore(directory: Path) {
    val root: Path = Files.createDirectories(directory).toRealPath()
    fun resolve(name: String): Path {
        require(!Path.of(name).isAbsolute) { "Use relative paths" }
        val path = root.resolve(name).normalize()
        require(path.startsWith(root)) { "Path outside data directory" }
        var part = root
        root.relativize(path).forEach { part = part.resolve(it); require(!Files.isSymbolicLink(part)) { "Symbolic links are not supported" } }
        return path
    }
    fun read(name: String) = Files.readString(resolve(name))
    @Synchronized fun write(name: String, content: String) {
        val path = resolve(name)
        require(path != root) { "A file path is required" }
        if (path == root.resolve("project.json")) json.decodeFromString<Project>(content).validate()
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, ".write-", ".tmp")
        try {
            Files.writeString(temp, content)
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
    fun list(name: String): JsonArray = Files.list(resolve(name)).use { paths -> JsonArray(paths.sorted().map { p -> buildJsonObject {
        put("name", p.fileName.toString()); put("directory", Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
    } }.toList()) }
}
fun HttpExchange.respond(code: Int, content: String, type: String = "application/json") {
    responseHeaders.set("Content-Type", "$type; charset=utf-8")
    responseHeaders.set("X-Content-Type-Options", "nosniff")
    val bytes = content.toByteArray(); sendResponseHeaders(code, bytes.size.toLong()); responseBody.use { it.write(bytes) }
}
fun HttpExchange.body(): String {
    val bytes = requestBody.readNBytes(4 * 1024 * 1024 + 1)
    require(bytes.size <= 4 * 1024 * 1024) { "Request exceeds 4 MiB" }
    return bytes.toString(Charsets.UTF_8)
}
fun generateTile(body: String): String {
    val key = System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() } ?: error("Set OPENAI_API_KEY on the server to enable generation")
    val input = json.parseToJsonElement(body).jsonObject
    val description = input["description"]!!.jsonPrimitive.content
    val size = input["size"]!!.jsonPrimitive.int
    require(size in listOf(8,16) && description.length in 1..2000) { "Invalid generation request" }
    val schema = buildJsonObject {
        put("type", "object"); put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("pixels") { put("type", "array"); put("minItems", size*size); put("maxItems", size*size); putJsonObject("items") { put("type", "integer"); put("minimum", -1); put("maximum",63) } }
        }
        putJsonArray("required") { add("name"); add("pixels") }
    }
    val request = buildJsonObject {
        put("model", System.getenv("OPENAI_MODEL") ?: "gpt-4.1-mini")
        put("store", false)
        put("input", "Design a readable side-scrolling game pixel-art tile: $description. Size ${size}x$size. Return row-major palette indexes; -1 is transparent. Palette: ${palette.mapIndexed { i,c -> "$i=$c" }.joinToString()}. Use a small coherent subset of colors. Return exactly ${size*size} pixels.")
        putJsonObject("text") { putJsonObject("format") { put("type", "json_schema"); put("name", "tile"); put("strict", true); put("schema", schema) } }
    }
    val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build().send(
        HttpRequest.newBuilder(URI("https://api.openai.com/v1/responses")).timeout(Duration.ofSeconds(90)).header("Authorization", "Bearer $key").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request.toString())).build(), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() in 200..299) { "OpenAI returned HTTP ${response.statusCode()}; check model access, quota and server configuration" }
    val output = json.parseToJsonElement(response.body()).jsonObject["output"]!!.jsonArray
    val text = output.flatMap { it.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList()) }.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "output_text" }?.jsonObject?.get("text")?.jsonPrimitive?.content ?: error("Generation returned no tile (possibly refused or incomplete)")
    val tile = json.parseToJsonElement(text).jsonObject
    val pixels = tile["pixels"]!!.jsonArray.map { it.jsonPrimitive.int }
    require(pixels.size == size*size && pixels.all { it in -1..63 }) { "Invalid generated pixels" }
    return tile.toString()
}
fun main() {
    val store = FileStore(Path.of(System.getenv("DATA_DIR") ?: "data"))
    if (!Files.exists(store.resolve("project.json"))) store.write("project.json", json.encodeToString(sampleProject()))
    val web = Path.of(System.getenv("WEB_DIR") ?: "web/build/dist/wasmJs/developmentExecutable").toAbsolutePath().normalize()
    val port = System.getenv("PORT")?.toInt() ?: 8080
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = Executors.newFixedThreadPool(8)
    server.createContext("/") { exchange ->
        try {
            val origin = exchange.requestHeaders.getFirst("Origin")
            require(origin == null || origin in listOf("http://localhost:$port", "http://127.0.0.1:$port")) { "Cross-origin requests are not allowed" }
            require(exchange.requestHeaders.getFirst("Host") in listOf("localhost:$port", "127.0.0.1:$port")) { "Invalid host" }
            val path = exchange.requestURI.path
            val query = exchange.requestURI.rawQuery?.split('&')?.associate { val p = it.split('=', limit=2); URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8") } ?: emptyMap()
            when {
                path == "/api/health" && exchange.requestMethod == "GET" -> exchange.respond(200, buildJsonObject { put("status", "ok"); put("generation", !System.getenv("OPENAI_API_KEY").isNullOrBlank()) }.toString())
                path == "/api/files" && exchange.requestMethod == "GET" -> exchange.respond(200, store.read(query["path"] ?: error("Missing path")), "text/plain")
                path == "/api/files" && exchange.requestMethod == "PUT" -> { store.write(query["path"] ?: error("Missing path"), exchange.body()); exchange.respond(200, "{\"saved\":true}") }
                path == "/api/directory" && exchange.requestMethod == "GET" -> exchange.respond(200, store.list(query["path"] ?: "").toString())
                path == "/api/generate" && exchange.requestMethod == "POST" -> exchange.respond(200, generateTile(exchange.body()))
                path.startsWith("/api/") -> exchange.respond(405, "{\"error\":\"Unsupported endpoint or method\"}")
                exchange.requestMethod == "GET" -> {
                    val file = web.resolve(if(path=="/") "index.html" else path.removePrefix("/")).normalize()
                    require(file.startsWith(web)) { "Invalid path" }
                    if (!Files.isRegularFile(file)) exchange.respond(404, "Not found", "text/plain") else {
                        exchange.responseHeaders.set("Content-Type", when(file.fileName.toString().substringAfterLast('.')) { "html" -> "text/html"; "js", "mjs" -> "text/javascript"; "wasm" -> "application/wasm"; "css" -> "text/css"; else -> "application/octet-stream" })
                        exchange.sendResponseHeaders(200, Files.size(file)); exchange.responseBody.use { Files.copy(file,it) }
                    }
                }
                else -> exchange.respond(405, "{\"error\":\"Method not allowed\"}")
            }
        } catch (e: Exception) {
            val code = when(e) { is NoSuchFileException -> 404; is IllegalArgumentException -> 400; else -> 500 }
            exchange.respond(code, buildJsonObject { put("error", e.message ?: "Request failed") }.toString())
        } finally { exchange.close() }
    }
    server.start()
    println("Kdesign: http://localhost:$port • data: ${store.root}")
}
