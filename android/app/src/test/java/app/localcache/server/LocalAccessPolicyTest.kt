package app.localcache.server

import app.localcache.SecretRedactor
import org.junit.Assert.*
import org.junit.Test

class LocalAccessPolicyTest {
    @Test fun onlyExplicitLoopbackHosts() {
        assertTrue(LocalAccessPolicy.acceptsHost("127.0.0.1:7100", 7100))
        assertTrue(LocalAccessPolicy.acceptsHost("localhost:7100", 7100))
        listOf(null, "192.168.1.223:7100", "evil.example:7100", "127.0.0.1.evil:7100", "127.0.0.1:80").forEach {
            assertFalse(LocalAccessPolicy.acceptsHost(it, 7100))
        }
    }
    @Test fun crossSiteOriginsAndReferersDenied() {
        assertTrue(LocalAccessPolicy.acceptsOrigin(null, 7100))
        assertTrue(LocalAccessPolicy.acceptsOrigin("http://127.0.0.1:7100", 7100))
        listOf("null", "https://attacker.example", "http://127.0.0.1:7101").forEach {
            assertFalse(LocalAccessPolicy.acceptsOrigin(it, 7100))
        }
        assertFalse(LocalAccessPolicy.acceptsReferer("http://evil.example/settings", 7100))
        assertFalse(LocalAccessPolicy.acceptsReferer("http://127.0.0.1:7100@evil.example/", 7100))
        assertTrue(LocalAccessPolicy.acceptsReferer("http://127.0.0.1:7100/settings", 7100))
    }
    @Test fun controlRoutesRequireToken() {
        listOf("/settings", "/settings/", "/cache", "/status", "/cancel", "/test", "/logs.txt", "/logs").forEach {
            assertTrue(LocalAccessPolicy.adminRoute(it))
        }
        assertFalse(LocalAccessPolicy.adminRoute("/manifest.json"))
        assertFalse(LocalAccessPolicy.adminRoute("/stream/movie/tt123.json"))
        assertTrue(LocalAccessPolicy.tokenMatches("synthetic-token", "synthetic-token"))
        assertFalse(LocalAccessPolicy.tokenMatches("synthetic-token", null))
        assertFalse(LocalAccessPolicy.tokenMatches("synthetic-token", "synthetic-tokeX"))
        assertEquals("value", LocalAccessPolicy.cookieToken("other=1; lc_admin=value; another=2"))
        assertNull(LocalAccessPolicy.cookieToken("lc_admin_fake=value"))
    }
    @Test fun redactsCredentialsEmbeddedInPathsAndQueries() {
        val message = "error https://example.test/apikey=FAKE/manifest.json http://example.test/?token=FAKE magnet:?xt=FAKE"
        val redacted = SecretRedactor.redact(message)
        assertFalse(redacted.contains("FAKE"))
        assertFalse(redacted.contains("example.test"))
        assertTrue(redacted.contains("URL redacted"))
    }
}
