package org.example.app
import kdesign.*
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.*
class FileStoreTest {
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
