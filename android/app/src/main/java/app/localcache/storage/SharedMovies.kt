package app.localcache.storage

import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Persistent public media. No arbitrary directories, replacement or recursive migration. */
object SharedMovies {
    fun directory(usbRoot: File): File {
        val base = usbRoot.canonicalFile
        require(base.isDirectory) { "USB root missing" }
        val movies = File(base, "Movies")
        require(movies.canonicalFile == movies.absoluteFile) { "Movies must not be a symbolic link" }
        return movies
    }

    @RequiresApi(26)
    fun migrateOwned(source: File, destination: File): Int {
        if (!source.isDirectory || source.canonicalFile == destination.canonicalFile) return 0
        require(destination.isDirectory) { "Destination missing" }
        val owned = CacheOwnership.mediaFiles(source)
        val files = (owned + owned.map { File(it.absolutePath.removeSuffix(".part") + ".meta.json") }).distinct()
        // Preflight the entire move before touching anything. Existing Movies content is not ours.
        for (file in files) {
            require(file.canonicalFile.parentFile == source.canonicalFile && file.isFile) { "Invalid source" }
            if (File(destination, file.name).exists()) throw IOException("Destination collision; no files replaced")
        }
        val moved = mutableListOf<Pair<File, File>>()
        try {
            files.forEach { file ->
                val target = File(destination, file.name)
                Files.move(file.toPath(), target.toPath()) // No REPLACE_EXISTING.
                moved += file to target
            }
        } catch (error: Exception) {
            moved.asReversed().forEach { (from, to) ->
                runCatching { Files.move(to.toPath(), from.toPath()) }
            }
            throw IOException("Migration interrupted; files retained, check both folders", error)
        }
        return owned.size
    }
}
