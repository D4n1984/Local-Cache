package app.localcache

import org.junit.Assert.*
import org.junit.Test

class ExternalLinkTest {
    @Test fun onlyHttpWithoutCredentialsOrControls() {
        listOf(null, "", "file:///etc/passwd", "content://media/1", "magnet:?xt=foo", "https://u:p@example.com/a", "https://example.com/a\n", "https://example.com:0/a", "https://example.com:65536/a", "https://example.com/a#secret").forEach {
            assertNull(it, ExternalLink.parse(it))
        }
        assertNotNull(ExternalLink.parse("https://example.com/signed/path?token=secret"))
        assertNotNull(ExternalLink.parse("http://127.0.0.1:17101/fixture.mp4"))
    }
    @Test fun stableOpaqueIdentityAndNoSensitiveFallbackTitle() {
        val a = ExternalLink.parse("https://example.com/SECRET?key=OTHER")!!
        assertEquals(a.key, ExternalLink.parse(a.url)!!.key)
        assertTrue(a.key.matches(Regex("external_[0-9a-f]{64}")))
        assertFalse(a.title.contains("SECRET"))
        assertNotEquals(a.key, ExternalLink.parse("https://example.com/different")!!.key)
    }
    @Test fun safeDisplayTitleNeverUsedAsPath() {
        assertEquals("My Film.mkv", ExternalLink.parse("https://example.com/My%20Film.mkv")!!.title)
        val a = ExternalLink.parse("https://example.com/a", "Title\nhttps://example.com/SECRET\u202E")!!
        assertFalse(a.title.contains("SECRET"))
        assertFalse(a.title.contains('\n'))
        assertFalse(a.title.contains('\u202E'))
        assertEquals(180, ExternalLink.parse("https://example.com/a", "x".repeat(400))!!.title.length)
    }
}
