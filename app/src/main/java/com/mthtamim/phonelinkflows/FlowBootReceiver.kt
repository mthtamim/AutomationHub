package com.mthtamim.phonelinkflows

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class FlowBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // v1.2.0: armed exit actions do NOT survive a reboot —
            // the entry that armed them is gone, so a stale entry
            // must never fire an exit after boot.
            Automation.clearAllExits(ctx)
            // v1.2.0: re-arm the background service ONLY if the user
            // left the Flows switch ON. Never auto-start when left
            // OFF. Some devices block background service starts
            // after boot; opening the Automation screen's Flows tab
            // once re-asserts it via the service switch.
            if (FlowStore.flowServiceOn(ctx)) {
                try {
                    ctx.startForegroundService(Intent(ctx, FlowService::class.java))
                } catch (_: Exception) {
                }
            }
            FlowEngine.onEvent(ctx, "boot", JSONObject())
            // Rules side (salvaged from PhoneLink SystemReceiver):
            // time rules must be re-armed after reboot, and the
            // boot event fires Rules triggers too.
            Automation.onEvent(ctx, "boot", JSONObject())
            Automation.scheduleTimeRules(ctx)
            // Rules side (salvaged from PhoneLink SystemReceiver):
            // time rules must be re-armed after reboot, and the
            // boot event fires Rules triggers too.
            Automation.onEvent(ctx, "boot", JSONObject())
            Automation.scheduleTimeRules(ctx)
            // Rules side (salvaged from PhoneLink SystemReceiver):
            // time rules must be re-armed after reboot, and the
            // boot event fires Rules triggers too.
            Automation.onEvent(ctx, "boot", JSONObject())
            Automation.scheduleTimeRules(ctx)
        }
    }
}
