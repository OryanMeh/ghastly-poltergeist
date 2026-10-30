package com.example.autocontinue

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var p: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        val sw = Switch(this).apply {
            text = "Auto-continue"; textSize = 22f; isChecked = p.enabled
            setOnCheckedChangeListener { _, on ->
                p.enabled = on
                if (on) {
                    Scheduler.schedule(this@MainActivity, Scheduler.CHECK, System.currentTimeMillis() + 5000)
                    p.log("Turned on")
                } else {
                    Scheduler.cancel(this@MainActivity, Scheduler.CHECK)
                    Scheduler.cancel(this@MainActivity, Scheduler.RESUME)
                    p.resetAt = 0
                    p.log("Turned off")
                }
                refresh()
            }
        }
        col.addView(sw)

        status = TextView(this).apply { setPadding(0, pad, 0, pad) }
        col.addView(status)

        fun btn(label: String, onClick: () -> Unit) {
            col.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
        }
        val pkgUri = Uri.parse("package:$packageName")
        btn("1. Open this app's info (allow restricted settings)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        }
        btn("2. Turn on accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("3. Allow exact alarms") {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
        }
        btn("4. Disable battery optimisation") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
        btn("Run a check now (test)") {
            val s = ClaudeService.instance
            if (s == null) Toast.makeText(this, "Accessibility service is off", Toast.LENGTH_LONG).show()
            else s.begin(Mode.CHECKING)
        }
        btn("Refresh status") { refresh() }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val enabledList = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val svcOn = enabledList.contains(packageName)
        status.text = "Accessibility service: " + (if (svcOn) "ON" else "OFF") +
            "\nToggle: " + (if (p.enabled) "ON" else "OFF") +
            "\nLast event: " + p.lastLog
    }
}
