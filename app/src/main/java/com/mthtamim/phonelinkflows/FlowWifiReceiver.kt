package com.mthtamim.phonelinkflows

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import org.json.JSONObject

class FlowWifiReceiver : BroadcastReceiver() {
    // v1.4.9: fires only on a real Wi-Fi transport change. The old
    // version decided from the SSID alone: with Location off the
    // SSID is always "" so it fired wifi_disconnected on EVERY
    // connectivity broadcast (even a mobile-data toggle, even
    // while Wi-Fi was connected) and never wifi_connected.
    private var lastWifiOn: Boolean? = null

    override fun onReceive(ctx: Context, intent: Intent?) {
        try {
            val appCtx = ctx.applicationContext
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val wifiOn = caps?.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            if (lastWifiOn == wifiOn) return
            lastWifiOn = wifiOn
            if (wifiOn) {
                val wm = appCtx.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wm.connectionInfo
                val ssid = info?.ssid?.removeSurrounding("\"") ?: ""
                val clean = if (ssid.isEmpty() || ssid == "<unknown ssid>") "" else ssid
                val data = JSONObject()
                data.put("ssid", clean)
                FlowEngine.onEvent(ctx, "wifi_connected", data)
            } else {
                FlowEngine.onEvent(ctx, "wifi_disconnected", JSONObject())
            }
        } catch (_: Exception) {
        }
    }
}
