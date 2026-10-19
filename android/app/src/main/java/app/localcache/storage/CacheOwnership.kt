package app.localcache.storage

import java.io.File

/** Quota cleanup must never evict arbitrary files placed beside the cache. */
object CacheOwnership {
    fun mediaFiles(root: File): List<File> {
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        return root.listFiles()?.filter { file ->
            file.isFile && !file.name.endsWith(".meta.json") &&
                runCatching { file.canonicalFile.parentFile == base }.getOrDefault(false) &&
                File(file.absolutePath.removeSuffix(".part") + ".meta.json").isFile
        }.orEmpty()
    }
}
