package org.example.app
import kdesign.*
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.*
class FileStoreTest {
    @Test fun binaryWritesPreserveBytesAndValidatePaths() {
        val dir=Files.createTempDirectory("kdesign-binary-test")
        try {
            val store=FileStore(dir)
            val bytes=byteArrayOf(-119,80,78,71,13,10,26,10,0,-1)
            store.writeBytes("generations/test.png",bytes)
            assertContentEquals(bytes,Files.readAllBytes(dir.resolve("generations/test.png")))
            store.writeBytes("generations/test.png",bytes.reversedArray())
            assertContentEquals(bytes.reversedArray(),Files.readAllBytes(dir.resolve("generations/test.png")))
            assertFails {store.writeBytes("../outside.png",bytes)}
            store.write("project.json",json.encodeToString(sampleProject()))
            val saved=store.read("project.json")
            assertFails {store.writeBytes("project.json",bytes)}
            assertEquals(saved,store.read("project.json"))
        } finally {dir.toFile().deleteRecursively()}
    }
    @Test fun readWriteListAndValidate() {
        val dir=Files.createTempDirectory("kdesign-test")
        try {
            val store=FileStore(dir)
            store.write("notes/test.txt","hello")
            assertEquals("hello",store.read("notes/test.txt"))
            assertTrue(store.list("").toString().contains("notes"))
            store.write("project.json",json.encodeToString(sampleProject()))
            val saved=store.read("project.json")
            assertFails {store.write("project.json", "{\"version\":99}")}
            assertEquals(saved,store.read("project.json"))
        } finally {dir.toFile().deleteRecursively()}
    }
    @Test fun rejectsTraversalAndSymlinks() {
        val dir=Files.createTempDirectory("kdesign-test")
        val outside=Files.createTempDirectory("kdesign-outside")
        try {
            val store=FileStore(dir)
            assertFails {store.read("../secret")}
            assertFails {store.write("/tmp/secret","no")}
            Files.createSymbolicLink(dir.resolve("link"),outside)
            assertFails {store.write("link/secret","no")}
            assertFails {store.list("link")}
        } finally {dir.toFile().deleteRecursively();outside.toFile().deleteRecursively()}
    }
}
