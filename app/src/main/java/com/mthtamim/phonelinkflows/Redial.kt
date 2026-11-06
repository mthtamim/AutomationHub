package com.mthtamim.phonelinkflows

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.media.AudioManager
import com.autotap.app.Region
import com.autotap.app.TapAccessibilityService

/**
 * v2.22: "উত্তর না দেওয়া পর্যন্ত বারবার কল" — redial until
 * answered, shared by the Dashboard card (manual) and the
 * redial_control rule action. One session at a time; the
 * session lives in its own prefs so it survives a process
 * restart, and Automation.tick (~60 s) drives it — so the
 * gap is approximate (the first tick after the gap), never
 * exact. v1.2.4: the tick alone is not reliable in the
 * background (not Doze-proof, dies with the process), so the
 * next redial is armed as an exact AlarmManager alarm
 * (setExactAndAllowWhileIdle, RedialReceiver) and every
 * place is verified from the call log — a call that never
 * started does not burn an attempt, it retries. The call
 * end itself is caught promptly: FlowCallReceiver's IDLE
 * branch polls Redial, and one poll can advance several
 * steps (calling -> ended -> wait), so the real wait after
 * a call ends is the 5 s log check + the gap — not the 60 s
 * tick times three transitions. The exact alarm needs the
 * SCHEDULE_EXACT_ALARM grant (declared in the manifest,
 * checked before scheduling, CallShield's pattern): when it
 * is missing the session still starts on the 60 s tick, but
 * the log, the Dashboard toast and the status line all say
 * so — never a silent slow redial.
 *
 * State machine: calling -> ended (the log row for this call;
 * 5 s check, the 25 s settle only when the row is not there
 * yet) -> wait (gap) -> calling ... A call ends when
 * TelephonyManager reports IDLE. "Answered" is read from the
 * call log: the latest outgoing call to the number with
 * duration > 0.
 * A duration of 0 (or no fresh log entry) means not
 * answered. The session stops on its own when answered,
 * when the attempt cap is hit, when CALL_PHONE, call log
 * or phone-state permission is lost, or when the user
 * stops it. Starting a new session replaces a running one.
 *
 * v2.23: ring limit (seconds, 0 = none) — when a call has
 * stayed OFFHOOK past the limit, the merged AutoTap
 * Accessibility engine taps the dialer's "End call" button.
 * The tap is gated twice: TapAccessibilityService must be
 * enabled, and the foreground app must be a dialer. It is
 * gated a third time by a ringing guard — the tap fires
 * only while the dialer still shows Dialing, Calling or
 * Ringing. Once answered, the in-call screen shows a timer
 * instead, so a missing ringing label means "do not tap":
 * a live conversation is never cut by the ring limit.
 * On any failed gate the reason is logged once per call
 * and the session waits for the natural end (carrier
 * timeout, decline, voicemail). The dialer UI varies, so
 * the ring limit is best-effort until phone-tested.
 * onanswer resume = an answered call counts as one attempt
 * and the session continues after the gap; stop (default)
 * ends the session on answer. speakerOn sets the
 * speakerphone while a redial call is OFFHOOK and restores
 * it to off when the call ends — Redial only touches the
 * speakerphone when the toggle is ON.
 */
object Redial {
    const val OK = 0
    const val ERR_NUMBER = 1
    const val ERR_CALL_PHONE = 2
    const val ERR_CALL_LOG = 3

    private const val PREFS = "phonelink_redial"
    private const val LOG_SETTLE_MS = 25_000L
    private const val PLACE_GUARD_MS = 10_000L
    // v1.2.4: redial scheduling no longer depends on the 60 s
    // Handler tick alone — the tick is not Doze-proof and dies
    // with the process, so the next redial is armed as an exact
    // AlarmManager alarm (setExactAndAllowWhileIdle, CallShield's
    // pattern). The grant is declared in the manifest
    // (SCHEDULE_EXACT_ALARM, already there for the time rules)
    // and checked before scheduling — never silently replaced
    // by an inexact alarm; a missing grant is surfaced (log,
    // Dashboard toast, status line) and the 60 s tick stays as
    // the slow fallback. One alarm slot (same PendingIntent):
    // entering "wait" arms the redial time, placing a call arms
    // a watchdog (settle + 10 s) so a stalled calling/ended
    // phase is always re-polled, and a missing log row arms a
    // 5 s re-check (CallShield's log-check delay — the row is
    // usually written at call end, but not guaranteed).
    private const val REDIAL_ALARM_REQ = 9001
    private const val PLACE_WATCHDOG_MS = 35_000L
    private const val LOG_CHECK_DELAY_MS = 5_000L
    private const val PLACE_RETRY_MS = 30_000L
    private const val MAX_PLACE_FAILS = 5
    private const val TAP_OK = 0
    private const val TAP_NO_A11Y = 1
    private const val TAP_NO_BUTTON = 2
    private const val TAP_WRONG_APP = 3
    private const val TAP_MAYBE_ANSWERED = 4

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun digits(s: String): String = s.filter { it.isDigit() }

    fun isActive(ctx: Context): Boolean =
        sp(ctx).getBoolean("active", false)

    fun sessionNumber(ctx: Context): String =
        sp(ctx).getString("number", "") ?: ""

    fun sessionSim(ctx: Context): String =
        sp(ctx).getString("sim", "default") ?: "default"

    fun sessionGap(ctx: Context): Int = sp(ctx).getInt("gapSec", 30)

    fun sessionMax(ctx: Context): Int = sp(ctx).getInt("maxAtt", 20)

    fun sessionRing(ctx: Context): Int = sp(ctx).getInt("ringSec", 0)

    fun sessionOnanswer(ctx: Context): String =
        sp(ctx).getString("onanswer", "stop") ?: "stop"

    fun sessionSpeaker(ctx: Context): Boolean =
        sp(ctx).getBoolean("speakerOn", false)

    fun statusText(ctx: Context): String {
        val prefs = sp(ctx)
        return if (prefs.getBoolean("active", false)) {
            val base = ctx.getString(R.string.dash_redial_active,
                prefs.getInt("attempts", 0), prefs.getInt("maxAtt", 20))
            // v1.2.4: when the exact-alarm grant is missing the
            // session still runs on the 60 s tick, but slowly —
            // say so on the status line, never silently.
            if (exactAlarmAllowed(ctx)) base
            else base + " — " + ctx.getString(
                R.string.dash_redial_no_exact_alarm_short)
        } else {
            val last = prefs.getString("lastResult", "") ?: ""
            if (last.isNotEmpty()) last
            else ctx.getString(R.string.dash_redial_idle)
        }
    }

    /** Places the first call immediately; returns an error code.
     *  ringSec 0 = no ring limit; onanswer "resume" continues
     *  after an answered call, anything else stops on answer. */
    fun start(ctx: Context, number: String, sim: String,
              gapSec: Int, maxAtt: Int, ringSec: Int,
              onanswer: String, speakerOn: Boolean): Int {
        val num = number.filter { it.isDigit() || it == '+' }
        if (num.isEmpty()) return ERR_NUMBER
        if (ctx.checkSelfPermission(
                android.Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED) return ERR_CALL_PHONE
        if (ctx.checkSelfPermission(
                android.Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED) return ERR_CALL_LOG
        val gap = gapSec.coerceIn(10, 3600)
        val max = maxAtt.coerceIn(1, 999)
        val now = System.currentTimeMillis()
        sp(ctx).edit()
            .putBoolean("active", true)
            .putString("number", num)
            .putString("sim", sim)
            .putInt("gapSec", gap)
            .putInt("maxAtt", max)
            .putInt("ringSec", ringSec.coerceIn(0, 300))
            .putString("onanswer",
                if (onanswer == "resume") "resume" else "stop")
            .putBoolean("speakerOn", speakerOn)
            .putInt("attempts", 1)
            .putString("state", "calling")
            .putLong("lastCallAt", now)
            .putLong("endedAt", 0L)
            .putLong("nextAt", 0L)
            .putBoolean("ringTapTried", false)
            .putBoolean("speakerApplied", false)
            .putBoolean("retryPlace", false)
            .putBoolean("alarmGrantWarned", false)
            .putInt("placeFails", 0)
            .apply()
        Automation.log(ctx, "redial: " + num + " — 1/" + max)
        placeCall(ctx, num, sim)
        scheduleAlarm(ctx, now + PLACE_WATCHDOG_MS)
        // v1.2.4: a missing exact-alarm grant is logged (once
        // per session) — the session still starts on the 60 s
        // tick, but the user must know it will be slow.
        if (!exactAlarmAllowed(ctx)) noteAlarmMissing(ctx)
        return OK
    }

    /** reason: user | answered | max | perm | placefail.
     *  No-op when idle. */
    fun stop(ctx: Context, reason: String) {
        val prefs = sp(ctx)
        if (!prefs.getBoolean("active", false)) return
        // v1.2.4: a stopped session must never wake the app.
        cancelAlarm(ctx)
        if (prefs.getBoolean("speakerApplied", false))
            setSpeaker(ctx, false)
        val attempts = prefs.getInt("attempts", 0)
        val text = when (reason) {
            "answered" -> ctx.getString(
                R.string.redial_log_answered, attempts)
            "max" -> ctx.getString(R.string.redial_log_max)
            "perm" -> ctx.getString(R.string.redial_log_perm)
            "placefail" -> ctx.getString(R.string.redial_log_placefail)
            else -> ctx.getString(R.string.redial_log_stopped)
        }
        prefs.edit().putBoolean("active", false)
            .putString("state", "idle")
            .putBoolean("speakerApplied", false)
            .putString("lastResult", text)
            .apply()
        Automation.log(ctx, "redial: " + text)
    }

    /** Places the call. Returns false when the start itself
     *  threw — and logs it. A non-throwing start can still be
     *  dropped by the background-start restriction, so a false
     *  return is not the whole check: poll() verifies from the
     *  call log that a call really started (see "calling"). */
    fun placeCall(ctx: Context, number: String, sim: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL,
                Uri.parse("tel:" + number))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (sim != "default") {
                val slot = if (sim == "sim1") 0 else 1
                Automation.simAccountForSlot(ctx, slot)?.let {
                    intent.putExtra(
                        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                }
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Automation.log(ctx, "redial: place failed — " +
                (e.message ?: "could not start call"))
            false
        }
    }

    /** Driven by Automation.tick (~60 s), by the redial alarm
     *  (v1.2.4) and by FlowCallReceiver's IDLE branch (v1.2.4).
     *  Synchronized: the tick thread, the alarm receiver and
     *  the phone-state receiver can fire at once, and two polls
     *  must never place the same redial twice. Never throws. */
    @Synchronized
    fun poll(ctx: Context) {
        try {
            pollInner(ctx)
        } catch (_: Exception) { }
    }

    private fun pollInner(ctx: Context) {
        val prefs = sp(ctx)
        if (!prefs.getBoolean("active", false)) return
        if (ctx.checkSelfPermission(
                android.Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(
                android.Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED) {
            stop(ctx, "perm")
            return
        }
        val tmState = try {
            (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as TelephonyManager).callState
        } catch (_: Exception) {
            stop(ctx, "perm")
            return
        }
        // The redial call ended (or never started): whatever
        // speaker state Redial set goes back to off, even
        // when the session itself continues (resume mode).
        if (prefs.getBoolean("speakerApplied", false) &&
            tmState == TelephonyManager.CALL_STATE_IDLE) {
            prefs.edit().putBoolean("speakerApplied", false).apply()
            setSpeaker(ctx, false)
        }
        // v1.2.4: one poll can advance several steps (calling ->
        // ended -> wait) so the gap is honoured instead of
        // costing a 60 s tick per transition. Bounded: at most
        // 4 steps; a step that moves nothing ends the poll.
        var now = System.currentTimeMillis()
        repeat(4) {
            val state = prefs.getString("state", "idle") ?: "idle"
            if (!pollStep(ctx, prefs, state, tmState, now)) return
            now = System.currentTimeMillis()
        }
    }

    /** One state-machine step. Returns true when it moved the
     *  session to another state (the caller then re-polls at
     *  once, so a call end can reach "wait" in a single poll);
     *  false when nothing moved. Terminal stops (answered,
     *  max, placefail) return false — the session is over. */
    private fun pollStep(
        ctx: Context,
        prefs: android.content.SharedPreferences,
        state: String,
        tmState: Int,
        now: Long
    ): Boolean {
        when (state) {
            "calling" -> {
                if (tmState == TelephonyManager.CALL_STATE_IDLE) {
                    val since = now - prefs.getLong("lastCallAt", 0)
                    if (since < PLACE_GUARD_MS) return false
                    val number = prefs.getString("number", "") ?: ""
                    val lastAt = prefs.getLong("lastCallAt", 0)
                    if (hasFreshOutgoingCall(ctx, number, lastAt)) {
                        // A call really started and ended.
                        prefs.edit().putString("state", "ended")
                            .putLong("endedAt", now)
                            .putInt("placeFails", 0).apply()
                        return true
                    }
                    if (since >= LOG_SETTLE_MS) {
                        // No call-log entry after the settle
                        // time: the call never started (the
                        // start was dropped in the background).
                        // Do NOT burn the attempt — retry.
                        onPlaceFailed(ctx, prefs, now)
                        return true
                    }
                    // 10–25 s with no entry yet: the log row is
                    // usually written at call end, but not
                    // guaranteed — re-poll in 5 s (CallShield's
                    // log-check delay) instead of waiting for
                    // the 60 s tick or the full 25 s settle.
                    armAlarm(ctx, now + LOG_CHECK_DELAY_MS)
                    return false
                }
                // OFFHOOK: the redial call is ringing or live.
                if (prefs.getBoolean("speakerOn", false)) {
                    setSpeaker(ctx, true)
                    prefs.edit()
                        .putBoolean("speakerApplied", true).apply()
                }
                val ring = prefs.getInt("ringSec", 0)
                if (ring > 0 &&
                    !prefs.getBoolean("ringTapTried", false) &&
                    now - prefs.getLong("lastCallAt", 0) >=
                    ring * 1000L) {
                    prefs.edit()
                        .putBoolean("ringTapTried", true).apply()
                    val tap = attemptEndCallTap(ctx)
                    val reason = when (tap.first) {
                        TAP_OK -> ctx.getString(
                            R.string.redial_log_ring_ending)
                        TAP_NO_A11Y -> ctx.getString(
                            R.string.redial_log_ring_no_a11y)
                        TAP_WRONG_APP -> ctx.getString(
                            R.string.redial_log_ring_wrong_app,
                            tap.second)
                        TAP_MAYBE_ANSWERED -> ctx.getString(
                            R.string.redial_log_ring_answered)
                        else -> ctx.getString(
                            R.string.redial_log_ring_no_button)
                    }
                    Automation.log(ctx, "redial: " + reason)
                }
                return false
            }
            "ended" -> {
                if (tmState != TelephonyManager.CALL_STATE_IDLE) {
                    prefs.edit().putString("state", "calling").apply()
                    return true
                }
                val number = prefs.getString("number", "") ?: ""
                val lastAt = prefs.getLong("lastCallAt", 0)
                val entry = latestOutgoing(ctx, number)
                val entryPresent = entry != null &&
                    entry.first >= lastAt - 2000L
                if (!entryPresent &&
                    now - prefs.getLong("endedAt", 0) < LOG_SETTLE_MS) {
                    // The row for this call is not in the log
                    // yet — re-poll in 5 s instead of burning
                    // the full 25 s settle.
                    armAlarm(ctx, now + LOG_CHECK_DELAY_MS)
                    return false
                }
                // Entry present, or the settle expired without
                // one: judge from this call's row only (an older
                // row from a previous attempt must not count
                // as "answered").
                val dur = if (entryPresent) entry?.second else null
                if (dur != null && dur > 0) {
                    val attempts = prefs.getInt("attempts", 0)
                    val max = prefs.getInt("maxAtt", 20)
                    if (prefs.getString("onanswer", "stop") ==
                        "resume") {
                        Automation.log(ctx, "redial: " +
                            ctx.getString(
                                R.string.redial_log_answered_resume,
                                attempts, max))
                    } else {
                        stop(ctx, "answered")
                        return false
                    }
                }
                val attempts = prefs.getInt("attempts", 0)
                val max = prefs.getInt("maxAtt", 20)
                if (attempts >= max) {
                    stop(ctx, "max")
                    return false
                }
                val nextAt = now + prefs.getInt("gapSec", 30) * 1000L
                prefs.edit().putString("state", "wait")
                    .putLong("nextAt", nextAt)
                    .apply()
                armAlarm(ctx, nextAt)
                return true
            }
            "wait" -> {
                if (tmState != TelephonyManager.CALL_STATE_IDLE)
                    return false
                val nextAt = prefs.getLong("nextAt", 0)
                if (now < nextAt) return false
                val attempts = prefs.getInt("attempts", 0)
                val max = prefs.getInt("maxAtt", 20)
                val number = prefs.getString("number", "") ?: ""
                val sim = prefs.getString("sim", "default") ?: "default"
                // A retry after a call that never started
                // re-uses the same attempt number — the failed
                // place did not burn an attempt, so the max
                // check does not apply to it.
                val retry = prefs.getBoolean("retryPlace", false)
                val nextAttempts = if (retry) attempts else attempts + 1
                if (!retry && attempts >= max) {
                    stop(ctx, "max")
                    return false
                }
                prefs.edit().putInt("attempts", nextAttempts)
                    .putLong("lastCallAt", now)
                    .putBoolean("ringTapTried", false)
                    .putBoolean("retryPlace", false)
                    .putString("state", "calling").apply()
                Automation.log(ctx, "redial: " + number + " — " +
                    nextAttempts + "/" + max)
                placeCall(ctx, number, sim)
                armAlarm(ctx, now + PLACE_WATCHDOG_MS)
                return true
            }
            else -> return false
        }
    }

    /** v1.2.4: the next redial is armed as an AlarmManager alarm
     *  as well as the 60 s tick — the tick is not Doze-proof and
     *  dies with the process, the alarm fires in Doze and wakes
     *  a dead process. CallShield's pattern: the alarm is EXACT
     *  (setExactAndAllowWhileIdle) and the grant is declared in
     *  the manifest (SCHEDULE_EXACT_ALARM, already there for the
     *  time rules) and checked — never silently replaced by an
     *  inexact alarm. scheduleAlarm returns false when the grant
     *  is missing; the caller must surface that (armAlarm logs
     *  it once per session), and the 60 s tick stays as the slow
     *  fallback. One alarm slot: each schedule replaces the
     *  previous one. */
    fun exactAlarmAllowed(ctx: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE)
                as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } catch (_: Exception) { false }
    }

    private fun redialPi(ctx: Context): android.app.PendingIntent {
        val intent = Intent(ctx, RedialReceiver::class.java)
            .setAction("com.mthtamim.phonelinkflows.REDIAL_POLL")
        return android.app.PendingIntent.getBroadcast(ctx,
            REDIAL_ALARM_REQ, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAlarm(ctx: Context, atMillis: Long): Boolean {
        if (!exactAlarmAllowed(ctx)) return false
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE)
                as android.app.AlarmManager
            am.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, atMillis,
                redialPi(ctx))
            true
        } catch (_: Exception) { false }
    }

    /** Arms the redial alarm; when the exact-alarm grant is
     *  missing, notes it once per session (log) so a missing
     *  grant is never a silent no-dial. */
    private fun armAlarm(ctx: Context, atMillis: Long): Boolean {
        if (scheduleAlarm(ctx, atMillis)) return true
        noteAlarmMissing(ctx)
        return false
    }

    private fun noteAlarmMissing(ctx: Context) {
        val prefs = sp(ctx)
        if (prefs.getBoolean("alarmGrantWarned", false)) return
        prefs.edit().putBoolean("alarmGrantWarned", true).apply()
        Automation.log(ctx, "redial: " + ctx.getString(
            R.string.redial_log_no_exact_alarm))
    }

    private fun cancelAlarm(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE)
                as android.app.AlarmManager
            am.cancel(redialPi(ctx))
        } catch (_: Exception) { }
    }

    /** v1.2.4: a redial whose call never started (no call-log
     *  entry after the settle time). The failed try does not
     *  count as an attempt; retry after 30 s. After 5
     *  consecutive failures the session stops with a clear
     *  message instead of retrying forever. */
    private fun onPlaceFailed(
        ctx: Context,
        prefs: android.content.SharedPreferences,
        now: Long
    ) {
        val fails = prefs.getInt("placeFails", 0) + 1
        if (fails >= MAX_PLACE_FAILS) {
            Automation.log(ctx, "redial: place failed $fails times — " +
                "calls could not be started, stopping")
            stop(ctx, "placefail")
            return
        }
        val nextAt = now + PLACE_RETRY_MS
        prefs.edit().putInt("placeFails", fails)
            .putBoolean("retryPlace", true)
            .putString("state", "wait")
            .putLong("nextAt", nextAt)
            .apply()
        Automation.log(ctx, "redial: " + ctx.getString(
            R.string.redial_log_place_failed, fails, MAX_PLACE_FAILS))
        armAlarm(ctx, nextAt)
    }

    /** Speakerphone is only touched when the session asked
     *  for it; otherwise Redial never changes audio routing. */
    private fun setSpeaker(ctx: Context, on: Boolean) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE)
                as AudioManager
            am.setSpeakerphoneOn(on)
        } catch (_: Exception) { }
    }

    /** Ends the redial call via the merged AutoTap engine:
     *  finds the dialer's "End call" button and taps it.
     *  Returns the tap code plus the foreground package for
     *  the log. The ringing guard is the safety: the tap
     *  fires only while the dialer still shows Dialing,
     *  Calling or Ringing — once answered, the in-call
     *  screen shows a timer instead, and a missing ringing
     *  label means "do not tap", so a live conversation is
     *  never cut. A dialer that shows neither label falls
     *  back to waiting for the natural end. */
    private fun attemptEndCallTap(ctx: Context): Pair<Int, String> {
        val svc = TapAccessibilityService.instance
            ?: return Pair(TAP_NO_A11Y, "")
        val pkg = try { svc.currentPackage() }
            catch (_: Exception) { "" }
        val p = pkg.lowercase()
        if (!p.contains("dialer") && !p.contains("incall"))
            return Pair(TAP_WRONG_APP, pkg)
        val ringing = try {
            svc.findText("Dialing", Region.FULL) != null ||
                svc.findText("Calling", Region.FULL) != null ||
                svc.findText("Ringing", Region.FULL) != null
        } catch (_: Exception) { false }
        if (!ringing) return Pair(TAP_MAYBE_ANSWERED, pkg)
        val pt = try { svc.findText("End call", Region.FULL) }
            catch (_: Exception) { null }
            ?: return Pair(TAP_NO_BUTTON, pkg)
        val ok = try { svc.tap(pt[0], pt[1], 100L) }
            catch (_: Exception) { false }
        return if (ok) Pair(TAP_OK, pkg)
            else Pair(TAP_NO_BUTTON, pkg)
    }

    /** Date + duration of the latest outgoing call to [number]
     *  in the last 15 minutes, or null when no such entry exists
     *  yet. v1.2.4: shared by the answered check (duration) and
     *  the place check (date). */
    private fun latestOutgoing(
        ctx: Context, number: String): Pair<Long, Long>? {
        val target = digits(number).takeLast(10)
        if (target.isEmpty()) return null
        try {
            val cur = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION,
                    CallLog.Calls.DATE),
                CallLog.Calls.TYPE + "=?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
                CallLog.Calls.DATE + " DESC")
            cur?.use {
                val now = System.currentTimeMillis()
                var seen = 0
                while (it.moveToNext() && seen < 8) {
                    seen++
                    val date = it.getLong(2)
                    if (now - date > 15 * 60_000L) break
                    val n = digits(it.getString(0) ?: "")
                    if (n.takeLast(10) == target)
                        return Pair(date, it.getLong(1))
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** True when a call to [number] was placed at or after
     *  [sinceAt] (2 s tolerance for log clock granularity). */
    private fun hasFreshOutgoingCall(
        ctx: Context, number: String, sinceAt: Long): Boolean {
        val date = latestOutgoing(ctx, number)?.first ?: return false
        return date >= sinceAt - 2000L
    }
}
