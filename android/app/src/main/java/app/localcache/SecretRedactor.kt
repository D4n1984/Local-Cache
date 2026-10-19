package app.localcache

/** URLs may contain the complete Real-Debrid credential in a path, not just a query. */
object SecretRedactor {
    private val urls = Regex("(?i)(?:https?://|magnet:\\?)[^\\s<>\"']+")
    fun redact(message: String): String = urls.replace(message, "[URL redacted]")
}
