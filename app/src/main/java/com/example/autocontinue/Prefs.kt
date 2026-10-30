package com.example.autocontinue

import android.content.Context
import java.text.DateFormat
import java.util.Date

enum class Mode { IDLE, CHECKING, RESUMING }

const val CLAUDE_PKG = "com.anthropic.claude"

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("auto_continue", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    /** Epoch millis when the limit resets (0 = no limit pending). */
    var resetAt: Long
        get() = sp.getLong("resetAt", 0L)
        set(v) { sp.edit().putLong("resetAt", v).apply() }

    var retries: Int
        get() = sp.getInt("retries", 0)
        set(v) { sp.edit().putInt("retries", v).apply() }

    var lastLog: String
        get() = sp.getString("lastLog", "Nothing yet") ?: ""
        set(v) { sp.edit().putString("lastLog", v).apply() }

    fun log(msg: String) {
        lastLog = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) + " - " + msg
    }
}
