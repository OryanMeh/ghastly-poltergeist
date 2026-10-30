package com.example.autocontinue

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    const val CHECK = "com.example.autocontinue.CHECK"
    const val RESUME = "com.example.autocontinue.RESUME"
    const val CHECK_EVERY_MS = 30 * 60 * 1000L

    private fun pi(c: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, if (action == CHECK) 1 else 2,
            Intent(c, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun schedule(c: Context, action: String, atMillis: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(c, action))
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(c, action))
    }

    fun cancel(c: Context, action: String) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, action))
    }
}
