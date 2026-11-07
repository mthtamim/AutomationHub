package com.mthtamim.phonelinkflows

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * v2.11: checks the GitHub releases of mthtamim/AutomationHub for a
 * version newer than the installed one. Version strings are read from
 * release tags, names and asset names (the CI release keeps one tag,
 * so all three places are scanned). Manual checks report back through
 * the callback; the daily background check only notifies on a hit.
 * v1.4.11: the manual check now downloads and installs in-app too —
 * it used to just open the release page in the browser.
 */
object Update {

    private val verRx = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)")

    private fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) { "0.0.0" }

    /** v1.4.13: versionCode of the installed app, or null.
     *  Int versionCode (not longVersionCode) because minSdk is 26. */
    private fun installedVersionCode(ctx: Context): Int? = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) { null }

    /** v1.4.13: versionCode baked into a downloaded APK, or null. */
    private fun apkVersionCode(ctx: Context, file: java.io.File): Int? = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.versionCode
    } catch (_: Exception) { null }

    /** v1.4.13: the "Failure [...]" line pm prints, or null. */
    private fun failureLine(output: String): String? =
        output.lines().firstOrNull { it.contains("Failure", ignoreCase = true) }
            ?.trim()?.take(160)

    private fun cmp(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun check(ctx: Context, manual: Boolean,
              onResult: ((newer: String?, releaseUrl: String?,
                          apkUrl: String?) -> Unit)? = null) {
        thread {
            var best: String? = null
            var bestUrl: String? = null
            var bestApk: String? = null
            var ok = false
            try {
                val conn = URL(
                    "https://api.github.com/repos/mthtamim/AutomationHub/releases?per_page=10"
                ).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty("User-Agent", "AutomationHub")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val rel = arr.optJSONObject(i) ?: continue
                    val hay = rel.optString("tag_name") + " " + rel.optString("name") +
                        " " + (rel.optJSONArray("assets")?.let { a ->
                            (0 until a.length()).joinToString(" ") {
                                a.optJSONObject(it)?.optString("name") ?: ""
                            }
                        } ?: "")
                    for (m in verRx.findAll(hay)) {
                        val v = "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}"
                        if (best == null || cmp(v, best!!) > 0) {
                            best = v
                            bestUrl = rel.optString("html_url")
                            val assets = rel.optJSONArray("assets")
                            if (assets != null) {
                                for (j in 0 until assets.length()) {
                                    val a = assets.optJSONObject(j) ?: continue
                                    if (a.optString("name").endsWith(".apk")) {
                                        bestApk = a.optString(
                                            "browser_download_url")
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
                ok = true
            } catch (_: Exception) { }
            // v1.4.11: only stamp the check when the API actually
            // answered — a failed check (offline) used to block any
            // retry for 24 hours.
            if (ok) Config.setUpdateLastCheck(ctx, System.currentTimeMillis())
            val newer = best?.takeIf { cmp(it, currentVersion(ctx)) > 0 }
            if (onResult != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(newer, bestUrl, bestApk)
                }
            } else if (newer != null) {
                // v2.16: actually UPDATE, don't just announce —
                // download the release APK and install it (silent
                // via Shizuku when available, else one-tap).
                if (Config.autoUpdate(ctx) && bestApk != null)
                    downloadAndInstall(ctx, bestApk!!, newer)
                else notifyNewer(ctx, newer)
            }
        }
    }

    /** v2.16: download the release APK and install it. Shizuku
     *  installs silently (pm install -r); without Shizuku the user
     *  gets a notification that opens the system installer once. */
    fun downloadAndInstall(ctx: Context, url: String, version: String) {
        thread {
            try {
                val dir = ctx.externalCacheDir ?: ctx.cacheDir
                val file = java.io.File(dir, "automationhub-update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "AutomationHub")
                conn.inputStream.use { ins ->
                    file.outputStream().use { out -> ins.copyTo(out) } }
                if (file.length() < 1_000_000) {
                    notifyUpdate(ctx, "⬆️ আপডেট ডাউনলোড অসম্পূর্ণ",
                        "v$version-এর ফাইলটি ঠিকমতো আসেনি।", null)
                    return@thread
                }
                if (ShizukuHelper.granted()) {
                    // v1.4.13: the old code only checked
                    // lastError == null — but run() never sees
                    // a failed pm install: pm prints
                    // "Failure [...]" on stdout and exits
                    // non-zero without throwing, so a failed
                    // install used to report itself as done.
                    // Now success needs BOTH: exit code 0 AND
                    // the installed versionCode matching the
                    // APK's. Nothing claims success unverified.
                    val targetCode = apkVersionCode(ctx, file)
                    val (exit, output) = ShizukuHelper.runCodeOut(
                        "pm install -r '" +
                            file.absolutePath.replace("'", "") + "'")
                    var installed: Int? = null
                    if (exit == 0) {
                        // The package manager can lag a beat
                        // behind pm's exit; retry briefly.
                        for (i in 0 until 3) {
                            installed = installedVersionCode(ctx)
                            if (installed != null) break
                            try { Thread.sleep(300) } catch (_: Exception) { }
                        }
                    }
                    if (exit == 0 && targetCode != null &&
                        installed != null && installed == targetCode) {
                        notifyUpdate(ctx, "⬆️ AutomationHub আপডেট হয়ে গেছে",
                            "v$version নিজে নিজে ইনস্টল হয়েছে।", null)
                        return@thread
                    }
                    // v1.4.13: tell the truth instead. A
                    // signature mismatch can never install over
                    // the app — it needs a one-time uninstall
                    // first (rules/flows exported beforehand).
                    if (output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ||
                        output.contains("signatures do not match", ignoreCase = true)) {
                        notifyUpdate(ctx,
                            ctx.getString(R.string.update_install_incompatible_title),
                            ctx.getString(R.string.update_install_incompatible_text, version),
                            null)
                    } else {
                        val reason = failureLine(output)
                            ?: ShizukuHelper.lastError
                            ?: ctx.getString(R.string.update_install_unverified_reason)
                        notifyUpdate(ctx,
                            ctx.getString(R.string.update_install_failed_title),
                            ctx.getString(R.string.update_install_failed_text, version, reason),
                            null)
                    }
                    return@thread
                }
                // v1.4.11: without the one-time "install unknown
                // apps" grant the system installer would just
                // refuse — send the user to that setting instead,
                // with the APK already downloaded.
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    val settings = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val settingsPi = PendingIntent.getActivity(ctx, 779, settings,
                        PendingIntent.FLAG_IMMUTABLE or
                            PendingIntent.FLAG_UPDATE_CURRENT)
                    notifyUpdate(ctx, "⬆️ ইনস্টলের অনুমতি দরকার",
                        "একবার অনুমতি দাও, তারপর আবার আপডেট চেক করো।", settingsPi)
                    return@thread
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.packageName + ".files", file)
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri,
                        "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(ctx, 778, install,
                    PendingIntent.FLAG_IMMUTABLE or
                        PendingIntent.FLAG_UPDATE_CURRENT)
                notifyUpdate(ctx, "⬆️ নতুন সংস্করণ v$version তৈরি",
                    "ট্যাপ করলেই ইনস্টল হবে (APK ডাউনলোড হয়ে গেছে)।", pi)
            } catch (e: Exception) {
                notifyUpdate(ctx, "⬆️ আপডেট ডাউনলোড হয়নি",
                    e.message ?: e.javaClass.simpleName, null)
            }
        }
    }

    private fun notifyUpdate(ctx: Context, title: String, text: String,
                             pi: PendingIntent?) {
        try {
            val intent = Intent(ctx, MainActivity::class.java)
            val openPi = pi ?: PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(ctx, FlowEngine.NOTIF_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java)
                .notify(777, n)
        } catch (_: Exception) { }
    }

    private fun notifyNewer(ctx: Context, version: String) {
        try {
            val intent = Intent(ctx, MainActivity::class.java)
            val pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(ctx, FlowEngine.NOTIF_CHANNEL_ID)
                .setContentTitle("⬆️ AutomationHub-এর নতুন সংস্করণ")
                .setContentText("v$version পাওয়া গেছে — অ্যাপ খুলে আপডেট বোতামে দেখো।")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(777, n)
        } catch (_: Exception) { }
    }
}
