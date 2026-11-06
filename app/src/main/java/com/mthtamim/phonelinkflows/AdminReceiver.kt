package com.mthtamim.phonelinkflows

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Trimmed device-admin receiver for the standalone Flows app.
 * PhoneLink's AdminReceiver carried forwarding-side hooks (relay,
 * session state, snapshot service); this one keeps only what Flow
 * code actually uses: isActive() for the lock_screen action, plus
 * the receiver itself so the user can grant device admin.
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun isActive(ctx: Context): Boolean {
            return try {
                val dpm = ctx.getSystemService(
                    Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.isAdminActive(
                    ComponentName(ctx, AdminReceiver::class.java))
            } catch (_: Exception) {
                false
            }
        }

        /** Salvaged from PhoneLink: the lock_screen automation
         *  action calls this (Automation.kt). No relay hooks. */
        fun lockNow(ctx: Context): Boolean {
            return try {
                val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
                if (dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) {
                    dpm.lockNow()
                    true
                } else false
            } catch (_: Exception) { false }
        }
    }
}
