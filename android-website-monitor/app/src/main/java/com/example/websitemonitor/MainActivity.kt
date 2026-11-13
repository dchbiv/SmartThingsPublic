package com.example.websitemonitor

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var urlInput: TextInputEditText
    private lateinit var keywordInput: TextInputEditText
    private lateinit var notifySwitch: MaterialSwitch
    private lateinit var statusText: TextView
    private lateinit var historyText: TextView

    // Kept as a field: SharedPreferences only holds listeners weakly.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { render() }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notifications are off; alerts won't be shown.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Notifier.createChannel(this)

        urlInput = findViewById(R.id.urlInput)
        keywordInput = findViewById(R.id.keywordInput)
        notifySwitch = findViewById(R.id.notifyChangeSwitch)
        statusText = findViewById(R.id.statusText)
        historyText = findViewById(R.id.historyText)

        urlInput.setText(prefs.url)
        keywordInput.setText(prefs.keyword)
        notifySwitch.isChecked = prefs.notifyOnChange

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!saveSettings()) return@setOnClickListener
            prefs.monitoring = true
            Scheduler.start(this)
            Scheduler.checkNow(this)
            requestNotificationPermission()
            Toast.makeText(this, "Checking every ${Scheduler.INTERVAL_MINUTES} minutes", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            prefs.monitoring = false
            Scheduler.stop(this)
        }
        findViewById<Button>(R.id.checkNowButton).setOnClickListener {
            if (!saveSettings()) return@setOnClickListener
            Scheduler.checkNow(this)
            Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        prefs.raw.registerOnSharedPreferenceChangeListener(prefsListener)
        render()
    }

    override fun onStop() {
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    /** Validates and stores the form. Returns false if the URL is invalid. */
    private fun saveSettings(): Boolean {
        var url = urlInput.text?.toString()?.trim().orEmpty()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            urlInput.error = "Enter a valid web address"
            return false
        }
        urlInput.setText(url)
        val keyword = keywordInput.text?.toString()?.trim().orEmpty()
        if (url != prefs.url || keyword != prefs.keyword) {
            // New target: start fresh so the first check isn't compared to the old site.
            prefs.last = null
            prefs.clearHistory()
        }
        prefs.url = url
        prefs.keyword = keyword
        prefs.notifyOnChange = notifySwitch.isChecked
        return true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun render() {
        val last = prefs.last
        val sb = StringBuilder()
        sb.append(
            if (prefs.monitoring) "● Monitoring every ${Scheduler.INTERVAL_MINUTES} min"
            else "○ Not monitoring"
        )
        if (last != null) {
            val ago = DateUtils.getRelativeTimeSpanString(last.timeMillis)
            sb.append("\nLast check: ").append(ago)
            sb.append("\nStatus: ")
            if (last.up) sb.append("UP (HTTP ${last.httpCode}, ${last.responseMillis} ms)")
            else sb.append("DOWN (${last.error ?: "no response"})")
            last.keywordFound?.let {
                sb.append("\nWatched text: ").append(if (it) "found" else "not found")
            }
        }
        statusText.text = sb
        historyText.text = prefs.history.joinToString("\n").ifEmpty { "No checks yet." }
    }
}
