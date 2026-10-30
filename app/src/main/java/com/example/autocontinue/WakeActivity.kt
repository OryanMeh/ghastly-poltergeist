package com.example.autocontinue

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle

/** Turns the screen on, dismisses a non-secure lock screen, then opens Claude. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = go()
            override fun onDismissError() = go()
            override fun onDismissCancelled() = go()
        })
    }

    private fun go() {
        launchClaude(this)
        finish()
    }
}

fun launchClaude(c: android.content.Context): Boolean {
    val i = c.packageManager.getLaunchIntentForPackage(CLAUDE_PKG) ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    c.startActivity(i)
    return true
}
