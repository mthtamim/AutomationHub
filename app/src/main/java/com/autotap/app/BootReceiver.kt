package com.autotap.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v1.4.12: restart the AutoTap trigger host after a reboot.
 * TriggerService used to start only from AutoTap's MainActivity,
 * so after a reboot every trigger stayed dead until the app was
 * opened (MainActivity even said so). The service is only started
 * when at least one script actually has a trigger or a schedule,
 * so phones that never use AutoTap triggers gain nothing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val hasTrigger = Store.getScripts(ctx).any { s ->
                s.scheduleEnabled ||
                    s.triggerWifi.isNotEmpty() ||
                    s.triggerBatteryLevel >= 0 ||
                    s.triggerHeadset ||
                    s.triggerScreenOn ||
                    s.triggerNotifPkg.isNotEmpty()
            }
            if (hasTrigger) TriggerService.start(ctx)
        } catch (_: Exception) {
        }
    }
}
