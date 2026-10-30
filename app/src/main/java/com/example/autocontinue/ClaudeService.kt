package com.example.autocontinue

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class ClaudeService : AccessibilityService() {

    companion object {
        @Volatile var instance: ClaudeService? = null
        private const val TICK_MS = 1500L
        private const val MAX_RUN_MS = 120_000L
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var p: Prefs
    private var mode = Mode.IDLE
    private var ticks = 0
    private var startedAt = 0L
    private var triedRecent = false
    private var screenWasOn = true
    private var wl: PowerManager.WakeLock? = null

    override fun onServiceConnected() { instance = this; p = Prefs(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    // ---------- run lifecycle ----------

    fun begin(m: Mode) {
        if (mode != Mode.IDLE) return
        mode = m; ticks = 0; triedRecent = false; startedAt = System.currentTimeMillis()
        val pm = getSystemService(PowerManager::class.java)
        screenWasOn = pm.isInteractive
        @Suppress("DEPRECATION")
        wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "autocontinue:run"
        ).also { it.acquire(3 * 60_000L) }
        p.log("Run started: $m")
        startActivity(Intent(this, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        h.postDelayed(::tick, 3000)
    }

    private fun finish() {
        mode = Mode.IDLE
        h.removeCallbacksAndMessages(null)
        performGlobalAction(GLOBAL_ACTION_HOME)
        h.postDelayed({
            if (!screenWasOn) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            if (wl?.isHeld == true) wl?.release()
        }, 1000)
    }

    private fun tick() {
        if (mode == Mode.IDLE) return
        if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) { p.log("Timed out"); finish(); return }
        ticks++
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != CLAUDE_PKG) {
            if (ticks == 5) launchClaude(this)
            h.postDelayed(::tick, TICK_MS); return
        }
        if (ticks >= 3 && !triedRecent) { triedRecent = true; openRecent(root) }
        if (ticks >= 6) {
            if (mode == Mode.CHECKING) evaluate(root) else sendContinue(root)
            return
        }
        h.postDelayed(::tick, TICK_MS)
    }

    // ---------- steps ----------

    /** Best effort: tap the first chat under a "Recents" heading, if one is visible. */
    private fun openRecent(root: AccessibilityNodeInfo) {
        val all = nodes(root)
        val idx = all.indexOfFirst {
            val t = it.text?.toString()?.trim()?.lowercase()
            t == "recents" || t == "recent chats" || t == "recent"
        }
        if (idx < 0) return
        for (i in idx + 1 until all.size) {
            if (label(all[i]).isNotBlank() && clickUp(all[i])) return
        }
    }

    private fun evaluate(root: AccessibilityNodeInfo) {
        val text = nodes(root).joinToString(" ") { label(it) }.lowercase()
        if (!isLimit(text)) { p.log("No usage limit on latest chat"); finish(); return }
        val at = parseReset(text) ?: (System.currentTimeMillis() + 30 * 60_000L)
        p.resetAt = at; p.retries = 0
        Scheduler.schedule(this, Scheduler.RESUME, at + 60_000)
        p.log("Limit hit. Will send Continue after " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at)))
        finish()
    }

    private fun sendContinue(root: AccessibilityNodeInfo) {
        val field = nodes(root).firstOrNull { it.isEditable }
        if (field == null) { retryLater("No text box found"); return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Continue")
        }
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        h.postDelayed({
            val r = rootInActiveWindow
            val send = r?.let { nodes(it).firstOrNull { n ->
                val d = n.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                d == "send" || d.startsWith("send message")
            } }
            if (send != null && clickUp(send)) {
                p.resetAt = 0; p.retries = 0
                p.log("Sent \"Continue\"")
                h.postDelayed({ finish() }, 3000)
            } else retryLater("Send button not found")
        }, 800)
    }

    private fun retryLater(why: String) {
        p.retries += 1
        if (p.retries < 6) {
            Scheduler.schedule(this, Scheduler.RESUME, System.currentTimeMillis() + 5 * 60_000)
            p.log("$why. Retrying in 5 min (${p.retries}/6)")
        } else {
            p.resetAt = 0; p.retries = 0
            p.log("$why. Gave up")
        }
        finish()
    }

    // ---------- limit text parsing (adjust if Claude's wording differs) ----------

    private fun isLimit(t: String) =
        Regex("(hit|reached)[^.]{0,30}limit|limit[^.]{0,30}(reached|hit)|usage limit").containsMatchIn(t)

    private fun parseReset(t: String): Long? {
        val now = System.currentTimeMillis()
        Regex("reset[s]? in\\s+(?:(\\d+)\\s*(?:hours?|hrs?|h))?\\s*(?:(\\d+)\\s*(?:minutes?|mins?|m))?")
            .find(t)?.let { m ->
                val hh = m.groupValues[1].toLongOrNull()
                val mm = m.groupValues[2].toLongOrNull()
                if (hh != null || mm != null) return now + ((hh ?: 0) * 60 + (mm ?: 0)) * 60_000
            }
        Regex("reset[s]?(?:\\s+at)?\\s+(?:today\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            when (m.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            if (hour > 23 || min > 59) return null
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
        return null
    }

    // ---------- node helpers ----------

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun dfs(n: AccessibilityNodeInfo?) {
            if (n == null) return
            out.add(n)
            for (i in 0 until n.childCount) dfs(n.getChild(i))
        }
        dfs(root)
        return out
    }

    private fun label(n: AccessibilityNodeInfo) =
        listOfNotNull(n.text, n.contentDescription).joinToString(" ")

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null) {
            if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            c = c.parent
        }
        return false
    }
}
