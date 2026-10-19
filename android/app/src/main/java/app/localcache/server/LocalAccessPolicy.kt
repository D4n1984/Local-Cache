package app.localcache.server

import java.net.URI
import java.security.MessageDigest

/** No Android dependencies: regression-tested boundary for the local-only control plane. */
object LocalAccessPolicy {
    fun acceptsHost(host: String?, port: Int): Boolean =
        host?.lowercase() in setOf("127.0.0.1:$port", "localhost:$port", "[::1]:$port")

    fun acceptsOrigin(origin: String?, port: Int): Boolean {
        if (origin == null) return true // Native Android clients do not send Origin.
        return origin in setOf("http://127.0.0.1:$port", "http://localhost:$port", "http://[::1]:$port")
    }

    fun acceptsReferer(referer: String?, port: Int): Boolean {
        if (referer == null) return true
        return runCatching {
            val uri = URI(referer)
            uri.scheme == "http" && acceptsHost(uri.rawAuthority, port)
        }.getOrDefault(false)
    }

    fun tokenMatches(expected: String, candidate: String?): Boolean =
        candidate != null && candidate.length == expected.length &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), candidate.toByteArray(Charsets.UTF_8))

    fun cookieToken(cookie: String?): String? = cookie?.split(';')
        ?.map { it.trim() }?.firstOrNull { it.startsWith("lc_admin=") }?.substringAfter('=')

    fun adminRoute(path: String): Boolean = path.trimEnd('/') in setOf(
        "/settings", "/logs", "/logs.txt", "/status", "/cache", "/cancel", "/test",
    )
}
