package app.localcache

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

/** Untrusted VIEW intents are proposals, never instructions to start a transfer. */
object ExternalLink {
    data class Candidate(val url: String, val key: String, val title: String)

    fun parse(url: String?, title: String? = null): Candidate? {
        if (url == null || url.length !in 1..16384 || url.any { it <= ' ' }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.fragment != null ||
            uri.port !in -1..65535 || uri.port == 0) return null
        val key = "external_" + MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = runCatching {
            URLDecoder.decode(uri.rawPath.orEmpty().substringAfterLast('/').replace("+", "%2B"), "UTF-8")
        }.getOrDefault("")
        val hint = file.takeIf { it.matches(Regex("(?i).+\\.(mkv|mp4|webm|avi|m4v|mov|ts)")) }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "Descarga recibida de Stremio"
        val safeTitle = SecretRedactor.redact(hint)
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").trim().take(180)
            .ifBlank { "Descarga recibida de Stremio" }
        return Candidate(url, key, safeTitle)
    }
}
