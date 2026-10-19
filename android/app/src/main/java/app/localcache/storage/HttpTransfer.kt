package app.localcache.storage

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Small, independently tested HTTP-only transfer; never promotes an unverified partial. */
class HttpTransfer(
    private val client: OkHttpClient,
    private val maxRetries: Int = 3,
    private val backoff: (Int) -> Unit = { attempt -> Thread.sleep(1000L shl attempt) },
) {
    class BudgetExceeded : IOException("Insufficient cache budget")
    class Cancelled : IOException("Cancelled")
    class InvalidResponse(message: String) : IOException(message)

    fun download(
        url: String,
        part: File,
        budget: Long,
        expectedTotal: Long = 0,
        initialValidator: String? = null,
        cancelled: () -> Boolean = { false },
        onCall: (Call) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onValidator: (String?) -> Unit = {},
    ): Long {
        var validator = initialValidator
        var knownTotal = expectedTotal
        var retries = 0
        while (true) {
            if (cancelled()) throw Cancelled()
            var offset = if (part.exists()) part.length() else 0L
            val request = Request.Builder().url(url)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "LocalCache-D4n1984")
            if (offset > 0) {
                request.header("Range", "bytes=$offset-")
                validator?.let { request.header("If-Range", it) }
            }
            val call = client.newCall(request.build())
            onCall(call)
            try {
                if (cancelled()) { call.cancel(); throw Cancelled() }
                call.execute().use { response ->
                    if (response.code in setOf(408, 429) || response.code in 500..599) {
                        throw IOException("Temporary upstream HTTP ${response.code}")
                    }
                    if (response.code != 200 && response.code != 206) {
                        throw InvalidResponse("Upstream HTTP ${response.code}; refresh source if expired")
                    }
                    val type = response.header("Content-Type").orEmpty().lowercase()
                    if (listOf("html", "json", "javascript").any { it in type }) {
                        throw InvalidResponse("Upstream returned an error document, not video")
                    }
                    val body = response.body ?: throw InvalidResponse("Empty upstream body")
                    if (response.header("Content-Encoding").let { it != null && !it.equals("identity", true) }) {
                        throw InvalidResponse("Encoded response cannot be safely resumed")
                    }
                    val responseValidator = response.header("ETag")?.takeUnless { it.startsWith("W/") }
                        ?: response.header("Last-Modified")
                    val total: Long
                    if (response.code == 206) {
                        val range = parseRange(response.header("Content-Range"))
                        if (range.first != offset) throw InvalidResponse("Misaligned Content-Range; partial preserved")
                        if (offset > 0 && validator != null && responseValidator != null && validator != responseValidator) {
                            throw InvalidResponse("Upstream entity changed; partial preserved")
                        }
                        if (offset > 0 && knownTotal > 0 && knownTotal != range.third) {
                            throw InvalidResponse("Upstream size changed; partial preserved")
                        }
                        if (body.contentLength() >= 0 && body.contentLength() != range.second - range.first + 1) {
                            throw InvalidResponse("Content-Length disagrees with Content-Range")
                        }
                        total = range.third
                    } else {
                        // If-Range mismatch or ignored Range: truncate, never append a 200 response.
                        offset = 0
                        total = body.contentLength().takeIf { it > 0 } ?: knownTotal
                    }
                    if (total <= 0) throw InvalidResponse("Unknown total size; cannot verify completion")
                    if (total > budget) throw BudgetExceeded()
                    knownTotal = total
                    var done = offset
                    FileOutputStream(part, offset > 0).use { raw ->
                        // Persist a new identity only after a 200 has actually truncated the old file.
                        validator = responseValidator
                        onValidator(validator)
                        java.io.BufferedOutputStream(raw, 1024 * 1024).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(256 * 1024)
                                var firstFlush = false
                                while (true) {
                                    if (cancelled()) throw Cancelled()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    if (done + count > total || done + count > budget) {
                                        throw InvalidResponse("Received more bytes than declared")
                                    }
                                    output.write(buffer, 0, count)
                                    done += count
                                    if (!firstFlush && done - offset >= 512 * 1024) {
                                        output.flush()
                                        firstFlush = true
                                    }
                                    onProgress(done, total)
                                }
                            }
                            output.flush()
                            raw.fd.sync()
                        }
                    }
                    if (done != total || part.length() != total) throw EOFException("Transfer incomplete; partial retained")
                    return total
                }
            } catch (error: IOException) {
                if (cancelled() || error is Cancelled) throw Cancelled()
                if (error is InvalidResponse || error is BudgetExceeded || retries >= maxRetries) throw error
                backoff(retries++)
            }
        }
    }

    companion object {
        fun parseRange(value: String?): Triple<Long, Long, Long> {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty())
                ?: throw InvalidResponse("Missing or invalid Content-Range")
            val values = match.groupValues.drop(1).map { it.toLongOrNull() ?: throw InvalidResponse("Range overflow") }
            val (start, end, total) = values
            if (start > end || end >= total || total <= 0) throw InvalidResponse("Invalid range bounds")
            return Triple(start, end, total)
        }
    }
}
