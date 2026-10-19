package app.localcache.storage

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class HttpTransferTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var part: File
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .readTimeout(5, TimeUnit.SECONDS).build()
    private fun url() = server.url("/movie").newBuilder().host("127.0.0.1").build().toString()
    private fun transfer(retries: Int = 0) = HttpTransfer(client, retries) { }
    @Before fun setup() {
        server = MockWebServer().also { it.start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        dir = Files.createTempDirectory("local-cache-test").toFile()
        part = File(dir, "video.part")
    }
    @After fun cleanup() { server.shutdown(); dir.deleteRecursively() }

    @Test fun freshDownloadHasExactSizeAndIdentityEncoding() {
        server.enqueue(MockResponse().setBody("0123456789"))
        assertEquals(10L, transfer().download(url(), part, 100))
        assertEquals("0123456789", part.readText())
        assertEquals("identity", server.takeRequest().getHeader("Accept-Encoding"))
    }
    @Test fun resumesOnlyAlignedPartialWithMatchingValidator() {
        part.writeText("01234")
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-9/10")
            .setHeader("ETag", "\"same\"").setBody("56789"))
        transfer().download(url(), part, 100, 10, "\"same\"")
        val request = server.takeRequest()
        assertEquals("bytes=5-", request.getHeader("Range"))
        assertEquals("\"same\"", request.getHeader("If-Range"))
        assertEquals("0123456789", part.readText())
    }
    @Test fun ignoredRangeRestartsWithoutAppendingOrCorrupting() {
        part.writeText("old-partial")
        server.enqueue(MockResponse().setBody("new-full-file"))
        transfer().download(url(), part, 100)
        assertEquals("new-full-file", part.readText())
    }
    @Test fun misalignedRangeLeavesPartialUntouched() {
        part.writeText("01234")
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-4/10").setBody("wrong"))
        assertThrows(HttpTransfer.InvalidResponse::class.java) {
            transfer().download(url(), part, 100, 10)
        }
        assertEquals("01234", part.readText())
    }
    @Test fun changedEntityLeavesPartialUntouched() {
        part.writeText("01234")
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-9/10")
            .setHeader("ETag", "\"changed\"").setBody("56789"))
        assertThrows(HttpTransfer.InvalidResponse::class.java) {
            transfer().download(url(), part, 100, 10, "\"old\"")
        }
        assertEquals("01234", part.readText())
    }
    @Test fun shortEofIsNeverSuccess() {
        server.enqueue(MockResponse().setBody("short").setHeader("Content-Length", "100")
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        assertThrows(java.io.IOException::class.java) {
            transfer().download(url(), part, 100)
        }
        assertEquals("short", part.readText())
    }
    @Test fun connectionDropRetriesFromActualBytesOnDisk() {
        val payload = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        var calls = 0
        var resumedOffset = -1
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls++
                if (calls == 1) return MockResponse().setBody(Buffer().write(payload))
                    .setHeader("ETag", "\"stable\"").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                resumedOffset = request.getHeader("Range")!!.removePrefix("bytes=").removeSuffix("-").toInt()
                return MockResponse().setResponseCode(206).setHeader("ETag", "\"stable\"")
                    .setHeader("Content-Range", "bytes $resumedOffset-${payload.lastIndex}/${payload.size}")
                    .setBody(Buffer().write(payload, resumedOffset, payload.size - resumedOffset))
            }
        }
        transfer(2).download(url(), part, payload.size.toLong(), payload.size.toLong())
        assertTrue(resumedOffset > 0)
        assertTrue(calls >= 2)
        assertArrayEquals(payload, part.readBytes())
    }
    @Test fun budgetFailureDoesNotDeleteExistingPartial() {
        part.writeText("01234")
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-9/10").setBody("56789"))
        assertThrows(HttpTransfer.BudgetExceeded::class.java) {
            transfer().download(url(), part, 7, 10)
        }
        assertEquals("01234", part.readText())
    }
    @Test fun cancellationRetainsPartialWithoutRequest() {
        part.writeText("keep")
        assertThrows(HttpTransfer.Cancelled::class.java) {
            transfer().download(url(), part, 100, cancelled = { true })
        }
        assertEquals("keep", part.readText())
        assertEquals(0, server.requestCount)
    }
    @Test fun invalidOrOverflowingRangesRejected() {
        listOf(null, "", "bytes 9-5/10", "bytes 0-10/10", "bytes 0-4/*", "bytes 0-4/99999999999999999999999999").forEach {
            assertThrows(HttpTransfer.InvalidResponse::class.java) { HttpTransfer.parseRange(it) }
        }
    }
    @Test fun cannotRelabelOldPartialIfOpeningOutputFails() {
        part.mkdir() // A directory cannot be opened/truncated as a file.
        val offset = part.length()
        server.enqueue(MockResponse().setResponseCode(206)
            .setHeader("Content-Range", "bytes $offset-${offset + 4}/${offset + 5}")
            .setHeader("ETag", "\"new\"").setBody("12345"))
        var validatorSaved = false
        assertThrows(java.io.IOException::class.java) {
            transfer().download(url(), part, 100000,
                onValidator = { validatorSaved = true })
        }
        assertFalse(validatorSaved)
    }
    @Test fun compressedResponseCannotBeAppendedAsVideo() {
        part.writeText("keep")
        server.enqueue(MockResponse().setBody("compressed").setHeader("Content-Encoding", "gzip"))
        assertThrows(HttpTransfer.InvalidResponse::class.java) {
            transfer().download(url(), part, 100)
        }
        assertEquals("keep", part.readText())
    }
}
