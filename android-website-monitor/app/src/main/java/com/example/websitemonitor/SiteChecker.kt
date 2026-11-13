package com.example.websitemonitor

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class CheckResult(
    val url: String,
    val timeMillis: Long,
    /** True when the server answered with a 2xx/3xx status. */
    val up: Boolean,
    val httpCode: Int,
    /** SHA-256 of the response body, null if the request failed. */
    val contentHash: String?,
    /** Whether the watched text was found; null if no text is being watched. */
    val keywordFound: Boolean?,
    val responseMillis: Long,
    val error: String?,
    /** Item titles if the response is an RSS/Atom feed, otherwise null. */
    val feedItems: List<String>? = null,
)

object SiteChecker {
    private const val MAX_BYTES = 5 * 1024 * 1024

    fun check(url: String, keyword: String): CheckResult {
        val now = System.currentTimeMillis()
        val start = SystemClock.elapsedRealtime()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "WebsiteMonitor/1.0 (Android)")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.use { readLimited(it) } ?: ByteArray(0)
            val elapsed = SystemClock.elapsedRealtime() - start
            val text = String(body, Charsets.UTF_8)
            val found = if (keyword.isBlank()) null
            else text.contains(keyword.trim(), ignoreCase = true)
            val items = FeedParser.itemTitles(text)
            CheckResult(
                url = url,
                timeMillis = now,
                up = code in 200..399,
                httpCode = code,
                // For feeds, hash only the item titles: build dates and other
                // metadata change on every request and would look like changes.
                contentHash = if (items != null) sha256(items.joinToString("\n").toByteArray())
                else sha256(body),
                keywordFound = found,
                responseMillis = elapsed,
                error = if (code >= 400) "HTTP $code" else null,
                feedItems = items,
            )
        } catch (e: Exception) {
            CheckResult(
                url = url,
                timeMillis = now,
                up = false,
                httpCode = 0,
                contentHash = null,
                keywordFound = null,
                responseMillis = SystemClock.elapsedRealtime() - start,
                error = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""),
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
