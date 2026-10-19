package app.localcache.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CacheOwnershipTest {
    @Test fun onlyManagedMediaCanBeEvicted() {
        val root = Files.createTempDirectory("cache-ownership").toFile()
        try {
            File(root, "family-video.mkv").writeText("unrelated")
            File(root, "local-cache-config.json").writeText("private config")
            File(root, "local-cache-debug.log").writeText("diagnostics")
            File(root, "managed.mkv.part").writeText("partial")
            File(root, "managed.mkv.meta.json").writeText("{}")
            assertEquals(listOf("managed.mkv.part"), CacheOwnership.mediaFiles(root).map { it.name })
            assertTrue(File(root, "family-video.mkv").exists())
        } finally { root.deleteRecursively() }
    }
}
