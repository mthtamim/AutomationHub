package com.mthtamim.phonelinkflows

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Lab notification listener. v1.2.0 only declared it for grant
 * parity; v1.3.0 wires it for real: posted notifications are
 * tracked so the notif_reply action can answer one via
 * RemoteInput. The user enables the listener in system Settings
 * (Permissions screen row); nothing auto-enables it. When the
 * listener is off, replies simply log "no matching notification".
 */
class FlowNotifListener : NotificationListenerService() {

    companion object {
        private val active = ConcurrentHashMap<String, StatusBarNotification>()
        private val rankingByKey = ConcurrentHashMap<String, Ranking>()

        @Volatile
        private var appCtx: Context? = null

        /**
         * AutomationHub: live service instance, mirroring PhoneLink's
         * NotifListener pattern. The Automation engine's notification
         * conditions/actions (notif_present, clear_notif, notif_reply)
         * need cancel/reply calls that only a bound listener service
         * can make; the tracked `active` map alone cannot dismiss.
         * Null while the listener is off — every helper below then
         * honestly reports false instead of crashing.
         */
        @Volatile
        private var instance: FlowNotifListener? = null

        /**
         * PhoneLink v2.16 notif_present: is a notification from [pkg]
         * (blank = any app) currently on the shade? Same semantics
         * as the removed NotifListener.hasNotification.
         */
        fun hasNotification(pkg: String): Boolean {
            val svc = instance ?: return false
            return try {
                svc.activeNotifications.any {
                    pkg.isEmpty() || it.packageName == pkg
                }
            } catch (_: Exception) {
                false
            }
        }

        /** Dismisses every notification posted by one package. */
        fun cancelPackage(pkg: String): Boolean {
            val svc = instance ?: return false
            return try {
                var n = 0
                for (sbn in svc.activeNotifications) {
                    if (sbn.packageName == pkg) {
                        svc.cancelNotification(sbn.key)
                        n++
                    }
                }
                n > 0
            } catch (_: Exception) {
                false
            }
        }

        /** Dismisses all of this phone's notifications. */
        fun dismissAll(): Boolean {
            val svc = instance ?: return false
            return try {
                svc.cancelAllNotifications()
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Replies to the most recent replyable notification of a
         * package (newest first, RemoteInput action only). Same
         * contract as the removed NotifListener.replyByPackage.
         */
        fun replyByPackage(ctx: Context, pkg: String, text: String): Boolean {
            val svc = instance ?: return false
            return try {
                val target = svc.activeNotifications
                    .filter { it.packageName == pkg }
                    .maxByOrNull { it.postTime } ?: return false
                val n = target.notification ?: return false
                for (action in n.actions ?: emptyArray()) {
                    val inputs = action.remoteInputs ?: continue
                    if (inputs.isEmpty()) continue
                    val results = Bundle()
                    results.putCharSequence(inputs[0].resultKey, text)
                    val intent = android.content.Intent()
                    RemoteInput.addResultsToIntent(inputs, intent, results)
                    action.actionIntent.send(ctx, 0, intent)
                    return true
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        /**
         * v1.4.0: ranking for the newest posted notification of
         * [pkg], used by the app_notif_priority / visibility tick
         * branches. Null when nothing from that package is posted.
         */
        fun rankingFor(pkg: String): Ranking? {
            return try {
                var best: StatusBarNotification? = null
                for ((_, sbn) in active) {
                    if (sbn.packageName != pkg) {
                        continue
                    }
                    if (best == null || sbn.postTime > best.postTime) {
                        best = sbn
                    }
                }
                val target = best ?: return null
                rankingByKey[target.key]
            } catch (_: Exception) {
                null
            }
        }

        // ---------------- v1.4.7: snooze / undo ----------------
        private const val DISMISSED_KEY = "dismissed_notifs"
        private const val DISMISSED_MAX = 10

        /** v1.4.8: one definition of "this notification matches
         *  [match]" — package equals/contains, app NAME contains
         *  (users type "WhatsApp", not "com.whatsapp"), or title/
         *  body contains (all ignoreCase). Used by snooze, reply
         *  and notif_present so all three agree. */
        private fun notifMatches(sbn: StatusBarNotification, match: String): Boolean {
            if (match.isEmpty()) return true
            val pkg = sbn.packageName ?: ""
            if (pkg.equals(match, ignoreCase = true) ||
                pkg.contains(match, ignoreCase = true)) return true
            val ctx = appCtx ?: instance?.applicationContext
            if (ctx != null) {
                val label = try {
                    val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
                    ctx.packageManager.getApplicationLabel(ai).toString()
                } catch (_: Exception) { "" }
                if (label.isNotEmpty() && label.contains(match, ignoreCase = true)) return true
            }
            val n = sbn.notification ?: return false
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            return title.contains(match, ignoreCase = true) ||
                body.contains(match, ignoreCase = true)
        }

        /** Snooze the newest notification matching [match]
         *  (package, or title/body text; blank = most recent) for
         *  [minutes]. False when the listener is off or nothing
         *  matches. */
        fun snoozeByMatch(match: String, minutes: Long): Boolean {
            val svc = instance ?: return false
            return try {
                val target = svc.activeNotifications
                    // v1.4.8: shared match — package, app name, title, body.
                    .filter { notifMatches(it, match) }
                    .maxByOrNull { it.postTime } ?: return false
                svc.snoozeNotification(target.key, minutes.coerceAtLeast(1) * 60_000L)
                true
            } catch (_: Exception) { false }
        }

        /** Stash a dismissed notification for undo. Keeps the last
         *  [DISMISSED_MAX] in the shared state prefs. */
        private fun stashDismissed(ctx: Context, sbn: StatusBarNotification) {
            try {
                val n = sbn.notification ?: return
                val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isEmpty() && body.isEmpty()) return
                val item = org.json.JSONObject().put("title", title).put("body", body)
                    .put("pkg", sbn.packageName).put("time", sbn.postTime)
                val prefs = ctx.getSharedPreferences("phonelink_state", Context.MODE_PRIVATE)
                val arr = try { JSONArray(prefs.getString(DISMISSED_KEY, "[]")) } catch (_: Exception) { JSONArray() }
                arr.put(item)
                while (arr.length() > DISMISSED_MAX) arr.remove(0)
                prefs.edit().putString(DISMISSED_KEY, arr.toString()).apply()
            } catch (_: Exception) { }
        }

        /** Re-post the most recently dismissed notification as an
         *  AutomationHub notification (tap opens the source app).
         *  False when nothing is stashed. */
        fun restoreLatest(ctx: Context): Boolean {
            try {
                val prefs = ctx.getSharedPreferences("phonelink_state", Context.MODE_PRIVATE)
                val arr = try { JSONArray(prefs.getString(DISMISSED_KEY, "[]")) } catch (_: Exception) { JSONArray() }
                if (arr.length() == 0) return false
                val item = arr.optJSONObject(arr.length() - 1) ?: return false
                arr.remove(arr.length() - 1)
                prefs.edit().putString(DISMISSED_KEY, arr.toString()).apply()
                val title = item.optString("title")
                val body = item.optString("body")
                val pkg = item.optString("pkg")
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(android.app.NotificationChannel(
                        FlowEngine.NOTIF_CHANNEL_ID,
                        ctx.getString(R.string.notif_channel),
                        android.app.NotificationManager.IMPORTANCE_DEFAULT))
                }
                val launch = try { ctx.packageManager.getLaunchIntentForPackage(pkg) } catch (_: Exception) { null }
                val pi = if (launch != null) PendingIntent.getActivity(ctx, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) else null
                val builder = NotificationCompat.Builder(ctx, FlowEngine.NOTIF_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(if (title.isNotEmpty()) title else pkg)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                if (pi != null) builder.setContentIntent(pi)
                nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
                return true
            } catch (_: Exception) { return false }
        }

        /**
         * Reply [text] to the first active notification whose title
         * or body contains [match] (case-insensitive) and whose
         * actions carry a RemoteInput. Returns false when nothing
         * matches or the send fails.
         */
        fun reply(match: String, text: String): Boolean {
            val ctx = appCtx ?: return false
            for ((_, sbn) in active) {
                // v1.4.8: shared match — package, app name, title, body.
                if (!notifMatches(sbn, match)) continue
                val actions = n.actions ?: continue
                for (action in actions) {
                    val inputs = action.remoteInputs ?: continue
                    if (inputs.isEmpty()) {
                        continue
                    }
                    try {
                        val results = Bundle()
                        results.putCharSequence(inputs[0].resultKey, text)
                        val intent = android.content.Intent()
                        RemoteInput.addResultsToIntent(inputs, intent, results)
                        action.actionIntent.send(ctx, 0, intent)
                        return true
                    } catch (_: Exception) {
                    }
                }
            }
            return false
        }

        /**
         * PhoneLink v2.24.0: the notif_present condition. Blank
         * [match] means any active notification; otherwise true
         * when some active notification matches the text — package,
         * app name, title or body (see notifMatches). False when
         * the listener is off (nothing tracked) — the condition
         * then honestly never matches, it never claims a
         * notification that is not there.
         */
        fun hasActive(match: String): Boolean {
            if (match.isEmpty()) {
                return active.isNotEmpty()
            }
            for ((_, sbn) in active) {
                // v1.4.8: shared match — package, app name, title, body.
                if (notifMatches(sbn, match)) return true
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        appCtx = applicationContext
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            active[sbn.key] = sbn
            try {
                val ranking = Ranking()
                if (currentRanking.getRanking(sbn.key, ranking)) {
                    rankingByKey[sbn.key] = ranking
                }
            } catch (_: Exception) {
            }
            val ctx = appCtx ?: applicationContext
            try {
                val n = sbn.notification
                val data = org.json.JSONObject()
                data.put(
                    "title",
                    n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                )
                data.put(
                    "body",
                    n?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                )
                data.put("app", sbn.packageName)
                data.put("pkg", sbn.packageName)
                FlowEngine.onEvent(ctx, "notif_received", data)
            } catch (_: Exception) {
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onNotificationRemovedWithReason(sbn, -1)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: android.service.notification.NotificationListenerService.RankingMap?,
        reason: Int
    ) {
        onNotificationRemovedWithReason(sbn, reason)
    }

    private fun onNotificationRemovedWithReason(sbn: StatusBarNotification?, reason: Int) {
        if (sbn != null) {
            active.remove(sbn.key)
            rankingByKey.remove(sbn.key)
            // v1.4.7: stash user/app/listener dismissals for undo.
            // Snoozes, timeouts and summary cleanups are NOT stashed
            // (a snoozed notification comes back on its own).
            if (reason == REASON_CLICK || reason == REASON_CANCEL ||
                reason == REASON_APP_CANCEL || reason == REASON_LISTENER_CANCEL ||
                reason == -1) {
                try { stashDismissed(applicationContext, sbn) } catch (_: Exception) { }
            }
            val ctx = appCtx ?: applicationContext
            try {
                val n = sbn.notification
                val data = org.json.JSONObject()
                data.put(
                    "title",
                    n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                )
                data.put(
                    "body",
                    n?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                )
                data.put("app", sbn.packageName)
                data.put("pkg", sbn.packageName)
                FlowEngine.onEvent(ctx, "notif_cleared", data)
                if (reason == NotificationListenerService.REASON_CLICK) {
                    FlowEngine.onEvent(ctx, "notif_interacted", data)
                }
            } catch (_: Exception) {
            }
        }
    }
}
