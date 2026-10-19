package app.localcache.storage

/** A downloader stops when full; it never evicts the user's library. */
object PersistentSpace {
    fun room(quota: Long, used: Long, free: Long, reserve: Long): Long = minOf(
        (quota.coerceAtLeast(0) - used.coerceAtLeast(0)).coerceAtLeast(0),
        (free.coerceAtLeast(0) - reserve.coerceAtLeast(0)).coerceAtLeast(0),
    )
}
