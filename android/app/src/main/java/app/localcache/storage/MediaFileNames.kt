package app.localcache.storage

import java.net.URI
import java.net.URLDecoder

/** Human readable names, independent of the secret URL and cache identity. */
object MediaFileNames {
    private val video = Regex("(?i).+\\.(mkv|mp4|webm|avi|m4v|mov|ts)")

    fun original(url: String): String? = runCatching {
        URLDecoder.decode(URI(url).rawPath.orEmpty().substringAfterLast('/').replace("+", "%2B"), "UTF-8")
    }.getOrNull()?.takeIf { video.matches(it) }

    fun name(url: String, label: String, fallback: String): String {
        val hint = original(url) ?: label
        var clean = hint.replace(Regex("[\\p{Cc}\\p{Cf}<>:\"/\\\\|?*]"), "_")
            .trim().trimEnd('.', ' ')
        if (clean.isBlank() || clean.all { it == '.' }) clean = fallback
        val extension = if (video.matches(clean)) "." + clean.substringAfterLast('.') else CachePaths.guessExt(url)
        var stem = if (video.matches(clean)) clean.dropLast(extension.length) else clean
        // Leave room for collision suffix, partial and sidecar names on exFAT (UTF-8 too).
        while (stem.toByteArray(Charsets.UTF_8).size > 180) stem = stem.dropLast(1)
        return stem + extension
    }

    fun available(name: String, occupied: (String) -> Boolean): String {
        val dot = name.lastIndexOf('.')
        val stem = name.substring(0, dot)
        val ext = name.substring(dot)
        var candidate = name
        var n = 2
        while (occupied(candidate)) {
            check(n < 10000) { "Too many files with the same name" }
            candidate = "$stem ($n)$ext"
            n++
        }
        return candidate
    }
}
