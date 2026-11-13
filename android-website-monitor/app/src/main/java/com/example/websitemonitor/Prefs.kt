package com.example.websitemonitor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Settings and the result of the previous check, stored in SharedPreferences. */
class Prefs(context: Context) {
    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    var url: String
        get() = raw.getString(KEY_URL, "") ?: ""
        set(v) = raw.edit { putString(KEY_URL, v) }

    var keyword: String
        get() = raw.getString(KEY_KEYWORD, "") ?: ""
        set(v) = raw.edit { putString(KEY_KEYWORD, v) }

    var notifyOnChange: Boolean
        get() = raw.getBoolean(KEY_NOTIFY_CHANGE, true)
        set(v) = raw.edit { putBoolean(KEY_NOTIFY_CHANGE, v) }

    var monitoring: Boolean
        get() = raw.getBoolean(KEY_MONITORING, false)
        set(v) = raw.edit { putBoolean(KEY_MONITORING, v) }

    /** The previous result, or null if the current URL has not been checked yet. */
    var last: CheckResult?
        get() {
            if (!raw.contains(KEY_LAST_TIME)) return null
            val kw = raw.getString(KEY_LAST_KEYWORD, "")
            return CheckResult(
                url = raw.getString(KEY_LAST_URL, "") ?: "",
                timeMillis = raw.getLong(KEY_LAST_TIME, 0),
                up = raw.getBoolean(KEY_LAST_UP, false),
                httpCode = raw.getInt(KEY_LAST_CODE, 0),
                contentHash = raw.getString(KEY_LAST_HASH, null),
                keywordFound = if (kw.isNullOrEmpty()) null else kw.toBoolean(),
                responseMillis = raw.getLong(KEY_LAST_RESPONSE_MS, 0),
                error = raw.getString(KEY_LAST_ERROR, null),
            )
        }
        set(r) = raw.edit {
            if (r == null) {
                listOf(
                    KEY_LAST_URL, KEY_LAST_TIME, KEY_LAST_UP, KEY_LAST_CODE, KEY_LAST_HASH,
                    KEY_LAST_KEYWORD, KEY_LAST_RESPONSE_MS, KEY_LAST_ERROR,
                ).forEach { remove(it) }
            } else {
                putString(KEY_LAST_URL, r.url)
                putLong(KEY_LAST_TIME, r.timeMillis)
                putBoolean(KEY_LAST_UP, r.up)
                putInt(KEY_LAST_CODE, r.httpCode)
                putString(KEY_LAST_HASH, r.contentHash)
                putString(KEY_LAST_KEYWORD, r.keywordFound?.toString() ?: "")
                putLong(KEY_LAST_RESPONSE_MS, r.responseMillis)
                putString(KEY_LAST_ERROR, r.error)
            }
        }

    /** Newest-first log of recent checks, one line each. */
    val history: List<String>
        get() = (raw.getString(KEY_HISTORY, "") ?: "").lines().filter { it.isNotBlank() }

    fun addHistory(line: String) {
        val updated = (listOf(line) + history).take(MAX_HISTORY)
        raw.edit { putString(KEY_HISTORY, updated.joinToString("\n")) }
    }

    fun clearHistory() = raw.edit { remove(KEY_HISTORY) }

    companion object {
        private const val MAX_HISTORY = 100
        private const val KEY_URL = "url"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_NOTIFY_CHANGE = "notify_change"
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_LAST_URL = "last_url"
        const val KEY_LAST_TIME = "last_time"
        private const val KEY_LAST_UP = "last_up"
        private const val KEY_LAST_CODE = "last_code"
        private const val KEY_LAST_HASH = "last_hash"
        private const val KEY_LAST_KEYWORD = "last_keyword"
        private const val KEY_LAST_RESPONSE_MS = "last_response_ms"
        private const val KEY_LAST_ERROR = "last_error"
        const val KEY_HISTORY = "history"
    }
}
