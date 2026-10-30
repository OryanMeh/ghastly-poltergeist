package com.example.autocontinue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Prefs(c)
        if (!p.enabled) return
        val now = System.currentTimeMillis()
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED ->
                Scheduler.schedule(c, Scheduler.CHECK, now + 60_000)

            Scheduler.CHECK -> {
                Scheduler.schedule(c, Scheduler.CHECK, now + Scheduler.CHECK_EVERY_MS)
                if (p.resetAt > now) return // already waiting for a reset
                val s = ClaudeService.instance
                if (s == null) p.log("Accessibility service is not running") else s.begin(Mode.CHECKING)
            }

            Scheduler.RESUME -> {
                val s = ClaudeService.instance
                if (s == null) p.log("Accessibility service is not running") else s.begin(Mode.RESUMING)
            }
        }
    }
}
