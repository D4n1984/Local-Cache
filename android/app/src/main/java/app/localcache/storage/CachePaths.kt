package app.localcache.storage

import android.content.Context
import app.localcache.Prefs
import java.io.File

/** Single source of truth for where a cached movie lives on the USB drive. */
object CachePaths {

    fun cacheRoot(context: Context): File? =
        Prefs.cacheDirPath(context)?.let { File(it) }

    fun finalFile(context: Context, cacheKey: String, url: String): File? {
        val root = cacheRoot(context) ?: return null
        return File(root, safeName(cacheKey) + guessExt(url))
    }

    /** Reuse persisted paths first: renaming an in-flight partial breaks resume. */
    @Synchronized
    fun forEntry(context: Context, entry: CacheEntry): File? {
        val root = cacheRoot(context)?.canonicalFile ?: return null
        entry.filePath?.let { path ->
            val existing = File(path).canonicalFile
            require(existing.parentFile == root) { "File is outside the selected folder" }
            return existing
        }
        // Restore identity even when the upstream supplies a different display title.
        val saved = root.listFiles()?.firstOrNull { file ->
            file.name.endsWith(".meta.json") && runCatching {
                org.json.JSONObject(file.readText()).optString("cacheKey") == entry.cacheKey
            }.getOrDefault(false)
        }
        val target = if (saved != null) File(saved.path.removeSuffix(".meta.json")) else {
            val name = MediaFileNames.name(entry.url, entry.label, safeName(entry.cacheKey))
            File(root, MediaFileNames.available(name) { candidate ->
                val file = File(root, candidate)
                file.exists() || partFile(file).exists() || LocalLibrary.metaFile(file).exists() ||
                    CacheRegistry.all().any { it.filePath?.equals(file.absolutePath, ignoreCase = true) == true } ||
                    root.listFiles()?.any { it.name.equals(candidate, true) ||
                        it.name.equals(candidate + ".part", true) ||
                        it.name.equals(candidate + ".meta.json", true) } == true
            })
        }
        entry.filePath = target.absolutePath
        return target
    }

    fun partFile(finalFile: File): File = File(finalFile.absolutePath + ".part")

    fun guessExt(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".mkv") -> ".mkv"
            lower.contains(".webm") -> ".webm"
            lower.contains(".avi") -> ".avi"
            else -> ".mp4"
        }
    }

    fun safeName(key: String): String = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
