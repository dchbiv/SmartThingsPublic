package com.example.websitemonitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs one check of the configured website and raises notifications on changes. */
class MonitorWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val url = prefs.url
        if (url.isBlank()) return@withContext Result.success()

        val keyword = prefs.keyword
        val result = SiteChecker.check(url, keyword)
        // Only compare against a previous result for the same URL.
        val prev = prefs.last?.takeIf { it.url == url }

        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url

        if (prev == null) {
            if (!result.up) {
                Notifier.notify(ctx, ID_STATUS, "$host is DOWN", describe(result), url)
            } else if (!result.feedItems.isNullOrEmpty()) {
                // First check of a feed: report what is currently listed.
                Notifier.notify(
                    ctx, ID_CHANGE, "$host: ${result.feedItems.size} active item(s)",
                    result.feedItems.joinToString("\n") { "• $it" }, url,
                )
            }
        } else {
            if (prev.up && !result.up) {
                Notifier.notify(ctx, ID_STATUS, "$host is DOWN", describe(result), url)
            } else if (!prev.up && result.up) {
                Notifier.notify(ctx, ID_STATUS, "$host is back UP", describe(result), url)
            }

            val isFeed = result.feedItems != null && prev.feedItems != null
            if (isFeed) {
                val before = prev.feedItems!!.toSet()
                val after = result.feedItems!!.toSet()
                val added = result.feedItems.filter { it !in before }
                val removed = prev.feedItems.filter { it !in after }
                if (added.isNotEmpty()) {
                    Notifier.notify(
                        ctx, ID_CHANGE,
                        if (added.size == 1) "$host: new update" else "$host: ${added.size} new updates",
                        added.joinToString("\n") { "• $it" }, url,
                    )
                }
                if (removed.isNotEmpty() && prefs.notifyOnChange) {
                    Notifier.notify(
                        ctx, ID_CLEARED, "$host: ${removed.size} item(s) cleared",
                        removed.joinToString("\n") { "• $it" }, url,
                    )
                }
            } else if (prefs.notifyOnChange && prev.up && result.up &&
                prev.contentHash != null && result.contentHash != null &&
                prev.contentHash != result.contentHash
            ) {
                Notifier.notify(ctx, ID_CHANGE, "$host changed", "The page content has changed.", url)
            }

            if (result.keywordFound != null && prev.keywordFound != null &&
                result.keywordFound != prev.keywordFound
            ) {
                val what = if (result.keywordFound) "now appears" else "no longer appears"
                Notifier.notify(ctx, ID_KEYWORD, "$host: text $what", "\"${keyword.trim()}\" $what on the page.", url)
            }
        }

        // A failed request has no content hash; keep the last good one so a brief
        // outage isn't reported as a content change once the site recovers.
        val stored = if (!result.up && prev != null) {
            result.copy(
                contentHash = prev.contentHash,
                keywordFound = prev.keywordFound,
                feedItems = prev.feedItems,
            )
        } else result
        prefs.last = stored
        prefs.addHistory(historyLine(result, prev))

        Result.success()
    }

    private fun describe(r: CheckResult): String =
        if (r.up) "HTTP ${r.httpCode} in ${r.responseMillis} ms"
        else r.error ?: "No response"

    private fun historyLine(r: CheckResult, prev: CheckResult?): String {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(r.timeMillis))
        val state = if (r.up) "UP  " else "DOWN"
        val items = r.feedItems?.let { " items:${it.size}" } ?: ""
        val changed = if (r.up && prev?.contentHash != null && r.contentHash != null &&
            prev.contentHash != r.contentHash
        ) " changed" else ""
        val kw = when (r.keywordFound) {
            true -> " text:found"
            false -> " text:missing"
            null -> ""
        }
        return "$time $state ${describe(r)}$items$changed$kw"
    }

    companion object {
        private const val ID_STATUS = 1
        private const val ID_CHANGE = 2
        private const val ID_KEYWORD = 3
        private const val ID_CLEARED = 4
    }
}
