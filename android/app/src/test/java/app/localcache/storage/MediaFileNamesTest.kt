package app.localcache.storage

import org.junit.Assert.*
import org.junit.Test

class MediaFileNamesTest {
    @Test fun preservesReleaseAndEpisode() {
        assertEquals("Mi Serie S02E03 + Español.mkv", MediaFileNames.name("https://example.com/Mi%20Serie%20S02E03%20+%20Espa%C3%B1ol.mkv?token=SECRET", "generic", "id"))
    }
    @Test fun fallsBackToLabelWithoutLeakingToken() {
        assertEquals("Serie S01E04.mp4", MediaFileNames.name("https://example.com/SECRET?token=SECRET", "Serie S01E04", "id"))
    }
    @Test fun sanitizesPathsAndControls() {
        val n = MediaFileNames.name("https://example.com/a", "../S01E01:bad\u202E\n?.mkv", "id")
        assertFalse(n.contains('/')); assertFalse(n.contains(':')); assertFalse(n.contains('\u202E'))
        assertTrue(n.endsWith(".mkv"))
    }
    @Test fun boundsUnicodeLength() {
        val n = MediaFileNames.name("https://example.com/a", "電".repeat(400) + ".mkv", "id")
        assertTrue(n.toByteArray(Charsets.UTF_8).size <= 184)
    }
    @Test fun noOverwriteIncludesCaseInsensitiveCollisions() {
        val occupied = setOf("episode.mkv", "episode (2).mkv")
        assertEquals("Episode (3).mkv", MediaFileNames.available("Episode.mkv") { it.lowercase() in occupied })
    }
    @Test fun originalBeatsGenericTitle() {
        assertEquals("Serie.S03E02.mkv", app.localcache.ExternalLink.parse("https://example.com/Serie.S03E02.mkv", "Serie")!!.title)
    }
}
