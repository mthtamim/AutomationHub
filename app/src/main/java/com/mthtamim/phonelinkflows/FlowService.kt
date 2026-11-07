package com.mthtamim.phonelinkflows

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

/**
 * PhoneLink v2.24.0 (Lab merge) background service: hosts Flow
 * trigger evaluation while the app is closed, so flows fire
 * without the Automation screen open. Started by the Flows tab
 * service switch (and re-armed on boot, only if the switch was
 * left ON). When the switch is OFF the service does not run at
 * all — no silent background work. The 60-second tick and the
 * dynamic screen/wifi receivers live ONLY here; the Automation
 * screen never starts the trigger hub, and manual Run-now needs
 * no hub. Foreground notification id 101.
 * AutomationHub: the Rules tick and the LAN webhook server are
 * also hosted here now (LinkService, which used to host them,
 * is gone with forwarding).
 */
class FlowService : Service() {

    companion object {
        // v1.4.12: the service notification gets its OWN channel at
        // IMPORTANCE_MIN — no status-bar icon, no sound, collapsed
        // at the bottom of the shade. It used to share the
        // "phonelink_flows" user-notification channel at LOW, so
        // the persistent "AutomationHub is running" row sat in the
        // shade. A foreground service must still post a
        // notification; fully invisible is impossible while it
        // runs. User notifications (local_notify / restore) stay on
        // "phonelink_flows" at DEFAULT.
        const val SERVICE_CHANNEL_ID = "phonelink_service"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    // v1.4.12: Study Guard used to run only on the 60 s tick, so
    // a blocked app could keep running for up to a minute past its
    // limit. This loop re-checks Study Guard alone every 15 s (a
    // cheap UsageStats read, on a background thread); the global
    // 60 s tick is untouched. pollStudyGuard is idempotent — warn
    // once per session, fire once per session/day — so the extra
    // calls only improve timeliness, never double-fire.
    private val sgRunnable = object : Runnable {
        override fun run() {
            try {
                if (running && Config.automationOn(this@FlowService)) {
                    Thread {
                        try {
                            val list = Automation.rules(this@FlowService)
                                .filter { it.optBoolean("enabled", true) }
                            Automation.pollStudyGuard(this@FlowService, list)
                        } catch (_: Exception) {
                        }
                    }.start()
                }
            } catch (_: Exception) {
            }
            if (running) {
                handler.postDelayed(this, 15_000L)
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                // v1.4.3 heartbeat: stamp every tick so Home can
                // tell a live service from a killed one. Stamped
                // here, on time, before the work leaves the main
                // thread. 2026-11-06: the tick work itself runs
                // on a background thread — it used to run on the
                // main looper and jank the UI every 60 s.
                FlowStore.setServiceLastTick(this@FlowService, System.currentTimeMillis())
                Thread {
                    try {
                        FlowEngine.tickForeground(this@FlowService)
                        FlowTriggers.checkForegroundApp(this@FlowService)
                        FlowTriggers.maybeArm(this@FlowService)
                        // Rules tick, formerly driven by LinkService's
                        // heartbeat: redial polling + time/interval triggers.
                        Automation.tick(this@FlowService)
                    } catch (_: Exception) {
                    }
                }.start()
            } catch (_: Exception) {
            }
            if (running) {
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON ->
                    FlowEngine.onEvent(ctx, "screen_on", JSONObject())
                Intent.ACTION_SCREEN_OFF ->
                    FlowEngine.onEvent(ctx, "screen_off", JSONObject())
                Intent.ACTION_USER_PRESENT ->
                    FlowEngine.onEvent(ctx, "screen_unlocked", JSONObject())
                else -> {
                }
            }
        }
    }

    private val wifiReceiver = FlowWifiReceiver()

    // Live battery path (2026-11-06): Automation.onBatteryChanged
    // used to be called by LinkService's ACTION_BATTERY_CHANGED
    // receiver — LinkService is gone, so nothing called it and
    // charging_on/off only came from the 60 s poll. This dynamic
    // receiver (sticky intent: delivered at once, then on every
    // battery change) restores the live path while the service
    // runs. The 60 s poll stays as fallback.
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent?) {
            try {
                Automation.onBatteryChanged(ctx)
            } catch (_: Exception) {
            }
        }
    }

    // Live WiFi path (2026-11-06): Rules only polled the SSID
    // every 60 s, and the Flow wifi receiver only sees
    // CONNECTIVITY_ACTION. A NetworkCallback fires the moment
    // WiFi connects or drops, to BOTH engines, carrying the
    // SSID. The 60 s poll + FlowWifiReceiver stay as fallback.
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            try {
                // v1.4.4: fire even when the SSID is unknown
                // (Location off masks it to ""). Blank-param rules
                // match any network; SSID-param rules still
                // correctly don't match an unknown SSID. Skipping
                // here used to make wifi_connected never fire at
                // all with Location off.
                val ssid = currentWifiSsid(this@FlowService)
                // v1.4.9: record the state so the 60 s poll doesn't
                // re-fire this same transition.
                Automation.noteWifiLive(this@FlowService, true, ssid)
                FlowEngine.onEvent(this@FlowService, "wifi_connected",
                    JSONObject().put("ssid", ssid).put("title", ssid))
                Automation.onEvent(this@FlowService, "wifi_connected",
                    JSONObject().put("ssid", ssid).put("title", ssid))
            } catch (_: Exception) {
            }
        }

        override fun onLost(network: Network) {
            try {
                // v1.4.9: record the state so the 60 s poll doesn't
                // re-fire this same transition.
                Automation.noteWifiLive(this@FlowService, false, "")
                FlowEngine.onEvent(this@FlowService, "wifi_disconnected", JSONObject())
                Automation.onEvent(this@FlowService, "wifi_disconnected", JSONObject())
            } catch (_: Exception) {
            }
        }
    }

    private var wifiCallbackRegistered = false

    // SSID from the live network, never "<unknown ssid>".
    private fun currentWifiSsid(ctx: Context): String {
        return try {
            val cm = ctx.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val info = caps?.transportInfo as? WifiInfo
            val ssid = info?.ssid?.removeSurrounding("\"") ?: ""
            if (ssid.isEmpty() || ssid == "<unknown ssid>") "" else ssid
        } catch (_: Exception) {
            ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(101, buildNotification())
        val screenFilter = IntentFilter()
        screenFilter.addAction(Intent.ACTION_SCREEN_ON)
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF)
        screenFilter.addAction(Intent.ACTION_USER_PRESENT)
        registerReceiver(screenReceiver, screenFilter)
        val wifiFilter = IntentFilter()
        wifiFilter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        wifiFilter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(wifiReceiver, wifiFilter)
        // Live battery receiver (see batteryReceiver above).
        try {
            registerReceiver(batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
        }
        // Live WiFi callback (see wifiCallback above).
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(req, wifiCallback)
            wifiCallbackRegistered = true
        } catch (_: Exception) {
        }
        // v1.4.0: the trigger hub (sensors, observers, callbacks)
        // runs while the service runs.
        try {
            FlowTriggers.start(this)
        } catch (_: Exception) {
        }
        // LAN webhook trigger host, formerly started by
        // LinkService: the webhook trigger still works locally.
        try { WebhookServer.start(this) } catch (_: Exception) { }
        // LAN webhook trigger host, formerly started by
        // LinkService: the webhook trigger still works locally.
        try { WebhookServer.start(this) } catch (_: Exception) { }
        // LAN webhook trigger host, formerly started by
        // LinkService: the webhook trigger still works locally.
        try { WebhookServer.start(this) } catch (_: Exception) { }
        // v1.4.4: register the system receiver (BT ACL, airplane,
        // hotspot AP state, battery saver, ACTION_FOUND). It used
        // to be registered only when rules were saved, so after
        // every reboot/kill those 10 rule triggers were dead
        // until the next save. The service owns it now.
        try { Automation.refreshWatchers(this) } catch (_: Exception) { }
        running = true
        FlowStore.setServiceLastTick(this, System.currentTimeMillis())
        handler.post(tickRunnable)
        handler.post(sgRunnable)
        FlowStore.log(this, getString(R.string.flow_service_started))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Idempotent: re-starting an already-running service just
        // re-enters here; the loop and receivers live in onCreate.
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(sgRunnable)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(wifiReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        if (wifiCallbackRegistered) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(wifiCallback)
            } catch (_: Exception) {
            }
            wifiCallbackRegistered = false
        }
        try {
            FlowTriggers.stop(this)
        } catch (_: Exception) {
        }
        try { WebhookServer.stop() } catch (_: Exception) { }
        try { WebhookServer.stop() } catch (_: Exception) { }
        try { WebhookServer.stop() } catch (_: Exception) { }
        // v1.4.3: clearing the stamp is the "not running" signal;
        // a stale stamp (no tick for a while) means the same.
        FlowStore.setServiceLastTick(this, 0L)
        FlowStore.log(this, getString(R.string.flow_service_stopped))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                getString(R.string.service_channel),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = getString(R.string.service_channel_desc)
            nm.createNotificationChannel(channel)
        }
        val openIntent = Intent(this, AutomationActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, SERVICE_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle(getString(R.string.flow_service_title))
        builder.setContentText(getString(R.string.flow_service_text))
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
        builder.setContentIntent(pending)
        builder.setOngoing(true)
        return builder.build()
    }
}
