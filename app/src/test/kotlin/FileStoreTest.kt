package org.example.app
import kdesign.*
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.*
class FileStoreTest {
    @Test fun rejectsStaleAndUnversionedWritesAndPreservesHistory() {
        val dir=Files.createTempDirectory("kdesign-version-test")
        try {
            val store=FileStore(dir)
            store.write("project.json",json.encodeToString(sampleProject()))
            val original=store.snapshot("project.json")
            assertFailsWith<VersionRequired> {
                store.conditionalWrite("project.json",original.content,null,false)
            }
            val changed=json.decodeFromString<Project>(original.content).copy(title="New title")
            val saved=store.conditionalWrite("project.json",json.encodeToString(changed),original.version,false)
            assertNotEquals(original.version,saved.version)
            assertEquals(original.content,store.read("backups/project-${original.version.trim('"')}.json"))
            assertFailsWith<VersionConflict> {
                store.conditionalWrite("project.json",original.content,original.version,false)
            }
            assertEquals(saved,FileStore(dir).snapshot("project.json"))
            assertFails {store.conditionalWrite("project.json","invalid",saved.version,false)}
            assertEquals(saved,store.snapshot("project.json"))
            Files.writeString(dir.resolve("project.json"),original.content)
            assertFailsWith<VersionConflict> {
                store.conditionalWrite("project.json",saved.content,saved.version,false)
            }
        } finally {dir.toFile().deleteRecursively()}
    }
    @Test fun createOnlyAndConcurrentSaves() {
        val dir=Files.createTempDirectory("kdesign-concurrent-test")
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val store=FileStore(dir)
            val initial=store.conditionalWrite("notes.txt","initial",null,true)
            assertFailsWith<VersionConflict> {store.conditionalWrite("notes.txt","other",null,true)}
            val start=java.util.concurrent.CountDownLatch(1)
            val tasks=(1..2).map { i -> pool.submit<Boolean> {
                start.await()
                try {store.conditionalWrite("notes.txt","edit $i",initial.version,false);true}
                catch(_: VersionConflict) {false}
            } }
            start.countDown()
            assertEquals(1,tasks.count {it.get()})
        } finally {pool.shutdownNow();dir.toFile().deleteRecursively()}
    }

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
