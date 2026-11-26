package com.mthtamim.phonelinkflows

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlin.concurrent.thread

/**
 * App Hide (v1.4.14): hide apps from the launcher, keep them usable.
 *
 * How it works: Shizuku runs `pm disable <pkg>/<launcher-activity>`
 * for every launcher activity of the app, so its icon disappears
 * from the launcher. The app itself stays installed and fully
 * usable — AutomationHub opens it from the vault by enabling the
 * launcher again first, then launching it.
 *
 * What this is NOT: it is not XHide and not a Device Owner hide.
 * A regular app cannot hide another app the system way (that needs
 * to be a system app, the launcher, or a device owner), and a
 * whole-package `pm hide` / `pm disable-user` would make the app
 * unlaunchable — which is exactly what this feature refuses to do.
 * Only the launcher *icon* (the MAIN/LAUNCHER activity) is
 * disabled, never the app itself.
 *
 * Honest limits:
 *  - needs Shizuku granted; without it nothing hides.
 *  - an app update can re-enable its launcher activity, so the
 *    icon may come back — the vault has a "Hide all again" button.
 *  - an app with no launcher activity cannot be hidden.
 */
object AppHide {

    private fun shq(v: String): String = "'" + v.replace("'", "'\\''") + "'"

    private fun compArg(c: ComponentName): String {
        val cls = c.className
        val full = if (cls.startsWith(".")) c.packageName + cls else cls
        return shq(c.packageName + "/" + full)
    }

    /** Every MAIN/LAUNCHER activity of the app (aliases included). */
    fun launcherComponents(ctx: Context, pkg: String): List<ComponentName> {
        val pm = ctx.packageManager
        val out = mutableListOf<ComponentName>()
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            intent.setPackage(pkg)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                out.add(ComponentName(ai.packageName, ai.name))
            }
        } catch (_: Exception) { }
        if (out.isEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                intent.setPackage(pkg)
                val ai = pm.resolveActivity(intent, 0)?.activityInfo
                if (ai != null) out.add(ComponentName(ai.packageName, ai.name))
            } catch (_: Exception) { }
        }
        return out.distinct()
    }

    /** True when every launcher activity is currently disabled. */
    fun isHidden(ctx: Context, pkg: String): Boolean {
        val comps = launcherComponents(ctx, pkg)
        if (comps.isEmpty()) return false
        return try {
            val pm = ctx.packageManager
            comps.all {
                pm.getComponentEnabledSetting(it) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        } catch (_: Exception) { false }
    }

    /** Result of one hide attempt, with the real reason when it
     *  fails — v1.4.15: the picker used to swallow pm's error
     *  and only count successes, so a failed Save looked like
     *  nothing happened at all. */
    data class HideResult(val ok: Boolean, val detail: String)

    /** Disable every launcher activity via Shizuku, and say why
     *  when it fails. pm's error text goes to stderr on some
     *  ROMs, so it is merged into stdout (" 2>&1") — otherwise
     *  the reason is lost. A success is also verified: some
     *  ROMs accept the command and change nothing. */
    fun hideDetailed(ctx: Context, pkg: String): HideResult {
        if (!ShizukuHelper.granted())
            return HideResult(false, "Shizuku not granted")
        val comps = launcherComponents(ctx, pkg)
        if (comps.isEmpty())
            return HideResult(false, "no launcher activity found")
        val notes = mutableListOf<String>()
        var ok = true
        for (c in comps) {
            val (code, out) =
                ShizukuHelper.runCodeOut("pm disable " + compArg(c) + " 2>&1")
            if (code != 0) {
                ok = false
                val reason = out.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?: ShizukuHelper.lastError ?: "pm exit $code"
                notes.add(reason.take(200))
            }
        }
        if (!ok) return HideResult(false, notes.firstOrNull() ?: "pm failed")
        if (!isHidden(ctx, pkg))
            return HideResult(false, "pm said ok but icon state did not change")
        return HideResult(true, "")
    }

    /** Disable every launcher activity via Shizuku. False when
     *  Shizuku is missing, the app has no launcher, or any pm
     *  call fails (ShizukuHelper.lastError explains why). */
    fun hide(ctx: Context, pkg: String): Boolean = hideDetailed(ctx, pkg).ok

    /** Re-enable every launcher activity via Shizuku. */
    fun unhide(ctx: Context, pkg: String): Boolean {
        if (!ShizukuHelper.granted()) return false
        val comps = launcherComponents(ctx, pkg)
        if (comps.isEmpty()) return false
        var ok = true
        for (c in comps) {
            val (code, _) = ShizukuHelper.runCodeOut("pm enable " + compArg(c))
            if (code != 0) ok = false
        }
        return ok
    }

    /** Open a hidden app from the vault: enable its launcher,
     *  launch it, and (when auto re-hide is on) remember to hide
     *  it again once it closes. */
    fun openHidden(ctx: Context, pkg: String): Boolean {
        if (!unhide(ctx, pkg)) return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            if (Config.hideAutoRehide(ctx)) {
                val pending = Config.hidePending(ctx).toMutableSet()
                if (pending.add(pkg))
                    Config.setHidePending(ctx, pending.joinToString(","))
            }
            true
        } catch (_: Exception) { false }
    }

    /** App-closed hook (called from Automation.fireAppEventInner):
     *  an app opened from the vault is hidden again once it
     *  closes. Off-thread on purpose — the Shizuku call must
     *  never block the event dispatch lock. */
    fun onAppClosed(ctx: Context, pkg: String) {
        if (!Config.hideAutoRehide(ctx)) return
        if (pkg !in Config.hidePending(ctx)) return
        val appCtx = ctx.applicationContext
        thread {
            try {
                if (pkg in Config.hidePkgs(appCtx)) hide(appCtx, pkg)
            } catch (_: Exception) { }
            val pending = Config.hidePending(appCtx).toMutableSet()
            if (pending.remove(pkg))
                Config.setHidePending(appCtx, pending.joinToString(","))
        }
    }

    /** Re-apply hide to every app in the hidden set (e.g. after
     *  an update restores an icon). Returns the success count. */
    fun rehideAll(ctx: Context): Int {
        if (!ShizukuHelper.granted()) return 0
        var n = 0
        for (pkg in Config.hidePkgs(ctx)) {
            try {
                if (hide(ctx, pkg)) n++
            } catch (_: Exception) { }
        }
        return n
    }
}
