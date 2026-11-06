package com.mthtamim.phonelinkflows

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class FlowPackageReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        try {
            val pkg = intent?.data?.schemeSpecificPart ?: return
            val event = when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED -> "app_installed"
                Intent.ACTION_PACKAGE_REMOVED -> "app_removed"
                Intent.ACTION_PACKAGE_REPLACED -> "app_updated"
                else -> return
            }
            // A changed package may have a new label/icon: drop its
            // cached entries (Automation + AppLocker) so the lists
            // never show a stale name or icon.
            Automation.clearAppLabelCache(pkg)
            AppLocker.clearAppCache(pkg)
            val data = JSONObject()
            data.put("pkg", pkg)
            FlowEngine.onEvent(ctx, event, data)
        } catch (_: Exception) {
        }
    }
}
