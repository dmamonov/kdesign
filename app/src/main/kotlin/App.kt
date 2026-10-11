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
    val request = buildJsonObject {
        put("model", System.getenv("OPENAI_IMAGE_MODEL")?.takeIf {it.isNotBlank()} ?: "gpt-image-1.5")
        put("size", "1024x1024")
        put("quality", "medium")
        put("background", "transparent")
        put("output_format", "png")
        put("n", 1)
        put("prompt", """Create one readable pixel-art sprite for a side-scrolling NES-style game.
            Subject: $description
            Design for a final ${size}x$size pixel tile, shown enlarged on this canvas.
            Use a very coarse grid, large flat color clusters, a strong dark outline, and a clear silhouette.
            Keep only the defining features that remain recognizable at ${size}x$size.
            Use 4 to 6 coherent colors. No gradients, tiny details, text, labels, grids, or checkerboard.
            For an object, show exactly one complete centered object, front or slight side view,
            occupying most of the image, with transparent surroundings and no cast shadow or scenery.
            For a requested terrain or surface tile, fill the entire square with that material.
            Do not show a sprite sheet, multiple versions, or an illustration of a game screen.
        """.trimIndent())
    }
    val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build().send(
        HttpRequest.newBuilder(URI("https://api.openai.com/v1/images/generations")).timeout(Duration.ofSeconds(240)).header("Authorization", "Bearer $key").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request.toString())).build(), HttpResponse.BodyHandlers.ofString())
    if(response.statusCode() !in 200..299) throw generationError(response.statusCode(), response.body(), response.headers().firstValue("Retry-After").orElse(null))
    val encoded = json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
        ?: error("OpenAI returned no image. Try a different description.")
    val bytes = java.util.Base64.getDecoder().decode(encoded)
    val image = javax.imageio.ImageIO.read(bytes.inputStream()) ?: error("OpenAI returned an unreadable image")
    val pixels = rasterToTile(image, size)
    return buildJsonObject {
        put("name", description.trim().lineSequence().first().take(80))
        put("pixels", JsonArray(pixels.map {JsonPrimitive(it)}))
    }.toString()
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
            val code = when(e) { is GenerationError -> e.status; is NoSuchFileException -> 404; is IllegalArgumentException -> 400; else -> 500 }
            exchange.respond(code, buildJsonObject { put("error", e.message ?: "Request failed"); if(e is GenerationError) put("code", e.errorCode) }.toString())
        } finally { exchange.close() }
    }
    server.start()
    println("Kdesign: http://localhost:$port • data: ${store.root}")
}
