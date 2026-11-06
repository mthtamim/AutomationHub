package com.mthtamim.phonelinkflows

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v1.2.4: AlarmManager entry point for redial, so a scheduled
 * redial fires even when the 60 s Handler tick is dead (Doze,
 * killed process). Mirrors TimeRuleReceiver: just runs a poll —
 * the state machine in Redial decides what happens next.
 */
class RedialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // v1.2.5: pollAsync — the poll runs on Redial's worker
        // thread, not on the receiver's main thread. The alarm
        // itself still fires on time; only execution moves.
        try {
            Redial.pollAsync(context.applicationContext)
        } catch (_: Exception) { }
    }
}
