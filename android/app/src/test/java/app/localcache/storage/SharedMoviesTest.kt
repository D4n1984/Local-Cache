package app.localcache.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

// Host-JVM tests: java.nio runs on the build JDK, not on an Android device.
class SharedMoviesTest {
    @Test fun moveOnlyOwnedMediaAndSidecars() {
        val root = Files.createTempDirectory("movies-test").toFile()
        try {
            val source = File(root, "old").apply { mkdir() }
            val dest = SharedMovies.directory(root).apply { mkdir() }
            File(source, "owned.mp4.part").writeText("partial bytes")
            File(source, "owned.mp4.meta.json").writeText("{}")
            File(source, "other.mp4").writeText("not owned")
            File(dest, "personal.mkv").writeText("keep this")
            assertEquals(1, SharedMovies.migrateOwned(source, dest))
            assertEquals("partial bytes", File(dest, "owned.mp4.part").readText())
            assertTrue(File(dest, "owned.mp4.meta.json").isFile)
            assertTrue(File(source, "other.mp4").isFile)
            assertFalse(File(source, "owned.mp4.part").exists())
            assertEquals("keep this", File(dest, "personal.mkv").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun collisionMovesNothing() {
        val root = Files.createTempDirectory("movies-test").toFile()
        try {
            val source = File(root, "old").apply { mkdir() }
            val dest = SharedMovies.directory(root).apply { mkdir() }
            File(source, "owned.mkv").writeText("original")
            File(source, "owned.mkv.meta.json").writeText("{}")
            File(dest, "owned.mkv.meta.json").writeText("existing")
            assertThrows(java.io.IOException::class.java) { SharedMovies.migrateOwned(source, dest) }
            assertEquals("original", File(source, "owned.mkv").readText())
            assertFalse(File(dest, "owned.mkv").exists())
            assertEquals("existing", File(dest, "owned.mkv.meta.json").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun noSymlinkEscape() {
        val root = Files.createTempDirectory("movies-test").toFile()
        val other = Files.createTempDirectory("other-test").toFile()
        try {
            Files.createSymbolicLink(File(root, "Movies").toPath(), other.toPath())
            assertThrows(IllegalArgumentException::class.java) { SharedMovies.directory(root) }
        } finally { root.deleteRecursively(); other.deleteRecursively() }
    }
    @Test fun persistentBudgetDoesNotInventSpace() {
        assertEquals(60L, PersistentSpace.room(100, 40, 90, 5))
        assertEquals(5L, PersistentSpace.room(100, 40, 10, 5))
        assertEquals(0L, PersistentSpace.room(100, 101, 90, 5))
        assertEquals(0L, PersistentSpace.room(100, 40, 4, 5))
        assertEquals(0L, PersistentSpace.room(100, 40, -1, 5))
    }
}
