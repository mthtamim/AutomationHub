package com.mthtamim.phonelinkflows

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object Config {
    private const val PREFS = "phonelink_cfg"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- pairing ----------


    fun deviceName(ctx: Context): String =
        prefs(ctx).getString("device_name", android.os.Build.MODEL ?: "Phone") ?: "Phone"





    // ---------- modes ----------

    // ---------- feature toggles (sender side) ----------

    // ---------- smart control (v2.1) ----------

    /** Quiet hours: receiver notifications stay silent in this window. */
    fun quietOn(ctx: Context) = prefs(ctx).getBoolean("quiet_on", false)
    fun quietStart(ctx: Context) = prefs(ctx).getInt("quiet_start", 23 * 60)
    fun quietEnd(ctx: Context) = prefs(ctx).getInt("quiet_end", 7 * 60)
    fun setQuietWindow(ctx: Context, startMin: Int, endMin: Int) {
        prefs(ctx).edit().putInt("quiet_start", startMin).putInt("quiet_end", endMin).apply()
    }

    fun digestMinutes(ctx: Context) = prefs(ctx).getInt("digest_minutes", 60)

    private fun digitsOf(s: String): String = s.filter { it.isDigit() }

    fun geoOn(ctx: Context) = prefs(ctx).getBoolean("geo_on", false)
    fun geoLat(ctx: Context) = prefs(ctx).getFloat("geo_lat", 0f).toDouble()
    fun geoLng(ctx: Context) = prefs(ctx).getFloat("geo_lng", 0f).toDouble()
    fun geoRadius(ctx: Context) = prefs(ctx).getInt("geo_radius", 200)
    fun setGeoRadius(ctx: Context, v: Int) { prefs(ctx).edit().putInt("geo_radius", v).apply() }

    /** Offline send-queue cap (v2.17.5): oldest queued events are
     *  dropped past this limit. Default 300 = the pre-v2.17.5 behavior. */
    fun setGeoCenter(ctx: Context, lat: Double, lng: Double) {
        prefs(ctx).edit().putFloat("geo_lat", lat.toFloat()).putFloat("geo_lng", lng.toFloat()).apply()
    }
    fun geoIsSet(ctx: Context) = geoLat(ctx) != 0.0 || geoLng(ctx) != 0.0


    // ---------- v2.7 ----------
    /** SMS fallback: urgent alerts also go out as a plain SMS when the relay is unreachable. */

    // ---------- v2.8: SMTP (automation send_email) ----------
    fun smtpHost(ctx: Context) = prefs(ctx).getString("smtp_host", "") ?: ""
    fun smtpPort(ctx: Context) = prefs(ctx).getInt("smtp_port", 587)
    fun smtpUser(ctx: Context) = prefs(ctx).getString("smtp_user", "") ?: ""
    fun smtpPass(ctx: Context) = prefs(ctx).getString("smtp_pass", "") ?: ""
    fun smtpFrom(ctx: Context) = prefs(ctx).getString("smtp_from", "") ?: ""
    // AutomationHub: the setter was dropped with MainActivity's old
    // SMTP-card binding; restored so the SMTP card can save again.
    fun setSmtp(ctx: Context, host: String, port: Int, user: String, pass: String, from: String) {
        prefs(ctx).edit().putString("smtp_host", host.trim())
            .putInt("smtp_port", port)
            .putString("smtp_user", user.trim())
            .putString("smtp_pass", pass)
            .putString("smtp_from", from.trim()).apply()
    }

    // ---------- v2.2 ----------

    /** Alert words: any message containing one is treated as urgent. */


    // ---------- v2.18: MQTT broker (WS4) ----------
    fun mqttOn(ctx: Context): Boolean = prefs(ctx).getBoolean("mqtt_on", false)
    fun mqttBroker(ctx: Context): String = prefs(ctx).getString("mqtt_broker", "") ?: ""
    fun mqttUser(ctx: Context): String = prefs(ctx).getString("mqtt_user", "") ?: ""
    fun mqttPass(ctx: Context): String = prefs(ctx).getString("mqtt_pass", "") ?: ""
    fun setMqtt(ctx: Context, on: Boolean, broker: String, user: String, pass: String) {
        prefs(ctx).edit().putBoolean("mqtt_on", on)
            .putString("mqtt_broker", broker.trim())
            .putString("mqtt_user", user.trim())
            .putString("mqtt_pass", pass).apply()
    }

    // ---------- pairing profiles ----------



    // ---------- per-app daily usage limits (minutes) ----------



    // ---------- v2.3 ----------

    /** Stable per-device id so group chats (3+ phones) can ignore themselves. */

    // ----- command PIN (stored only as a salted digest) -----
    private fun pinDigestOf(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest("phonelink-pin:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }


    // ----- quiet-until (timed silence, e.g. meeting mode) -----

    // ----- modes -----
    fun applyMode(ctx: Context, mode: String) {
        val e = prefs(ctx).edit()
        when (mode) {
            "sleep" -> {
                e.putBoolean("quiet_on", true)
                e.putInt("quiet_start", 23 * 60).putInt("quiet_end", 7 * 60)
                e.putLong("quiet_until", 0L)
                e.putBoolean("study_mode", false)
                e.putBoolean("tts_codes", false)
            }
            "meeting" -> {
                e.putLong("quiet_until", System.currentTimeMillis() + 60 * 60 * 1000L)
                e.putBoolean("auto_reply_on", true)
                e.putBoolean("study_mode", false)
            }
            "study" -> {
                e.putBoolean("study_mode", true)
                if (digestMinutes(ctx) == 0) e.putInt("digest_minutes", 30)
                e.putLong("quiet_until", 0L)
            }
            else -> {
                e.putBoolean("study_mode", false)
                e.putLong("quiet_until", 0L)
            }
        }
        e.putString("mode_name", mode).apply()
    }

    // ----- misc v2.3 toggles & data -----
    fun guardOn(ctx: Context): Boolean = prefs(ctx).getBoolean("guard_on", false)
    fun intruderOn(ctx: Context): Boolean = prefs(ctx).getBoolean("intruder_on", false)

    fun autoTapScript(ctx: Context): String = prefs(ctx).getString("autotap_script", "") ?: ""
    fun setAutoTapScript(ctx: Context, s: String) { prefs(ctx).edit().putString("autotap_script", s.trim()).apply() }




    fun setToggle(ctx: Context, key: String, v: Boolean) {
        prefs(ctx).edit().putBoolean(key, v).apply()
    }

    // ----- remote control (SET command) key mapping -----






    // ---------- topics ----------
    // Topic names are a SHA-256 hash of the pair code, so nobody can guess
    // or enumerate them on the public relay. Data and commands use
    // separate topics so the two directions never mix.


    // ---------- receiver state ----------

    // ---------- security ----------
    /** End-to-end encryption of relay payloads (key derived from the pair code). */

    fun appLockOn(ctx: Context): Boolean = prefs(ctx).getBoolean("app_lock_on", false)
    fun setAppLockOn(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("app_lock_on", v).apply() }
    fun pinHash(ctx: Context): String = prefs(ctx).getString("app_pin_hash", "") ?: ""
    fun setPinHash(ctx: Context, h: String) { prefs(ctx).edit().putString("app_pin_hash", h).apply() }

    // ---------- appearance / onboarding ----------
    /** UI language: "bn", "en" or "system". */
    fun language(ctx: Context): String = prefs(ctx).getString("app_lang", "bn") ?: "bn"
    fun setLanguage(ctx: Context, v: String) { prefs(ctx).edit().putString("app_lang", v).apply() }

    /** Theme: "system", "light" or "dark". */
    fun themeMode(ctx: Context): String = prefs(ctx).getString("theme_mode", "system") ?: "system"
    fun setThemeMode(ctx: Context, v: String) { prefs(ctx).edit().putString("theme_mode", v).apply() }

    /** The automatic permission wizard is shown only once (first run / first service start). */
    fun wizardShown(ctx: Context): Boolean = prefs(ctx).getBoolean("perm_wizard_shown", false)
    fun setWizardShown(ctx: Context) { prefs(ctx).edit().putBoolean("perm_wizard_shown", true).apply() }

    // ---------- v2.4: group roles & per-peer rules ----------
    /** Device ids allowed to run commands here; empty = everyone (legacy). */




    // ---------- v2.4: lost mode ----------

    // ---------- v2.4: simple mode & check-in ----------

    // ---------- v2.4: LAN direct mode ----------

    // ---------- v2.4: clipboard auto-sync ----------

    // ---------- v2.4: smart importance ----------

    // ---------- v2.4: automation ----------
    fun automationOn(ctx: Context) = prefs(ctx).getBoolean("automation_on", true)

    // ---------- v2.20: FocusLock integration ----------
    /** The FocusLock automation token, saved ONCE from the
     *  Automation screen's FocusLock card and reused by every
     *  focuslock_control step at run time (the steps themselves
     *  never carry it). Lives in the same private cfg store as
     *  the pair code. Empty = not set = steps log and skip. */
    fun focusLockToken(ctx: Context): String =
        prefs(ctx).getString("focuslock_token", "") ?: ""
    fun setFocusLockToken(ctx: Context, v: String) {
        prefs(ctx).edit().putString("focuslock_token", v).apply() }

    // ---------- v2.20: Study Guard lists ----------
    /** One named Study Guard template: the study apps the user
     *  picked once, reused by every Study Guard trigger (Tamim's
     *  decision, 24 Oct 2026: MULTIPLE named lists). Stored as a
     *  JSON array in cfg key "study_lists" — [{id, name, pkgs}] —
     *  the same shape the profiles/templates stores use. */
    class StudyList(val id: String, val name: String,
                    val pkgs: Set<String>)

    fun studyLists(ctx: Context): List<StudyList> {
        val out = mutableListOf<StudyList>()
        try {
            val arr = org.json.JSONArray(
                prefs(ctx).getString("study_lists", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pkgs = mutableSetOf<String>()
                val pa = o.optJSONArray("pkgs")
                if (pa != null) for (j in 0 until pa.length())
                    pa.optString(j).takeIf { it.isNotEmpty() }
                        ?.let { pkgs.add(it) }
                out.add(StudyList(o.optString("id"),
                    o.optString("name"), pkgs))
            }
        } catch (e: Exception) { }
        return out.filter { it.id.isNotEmpty() }
    }

    fun studyList(ctx: Context, id: String): StudyList? =
        studyLists(ctx).firstOrNull { it.id == id }

    /** Upsert by id; a blank id mints a fresh stable one. */
    fun saveStudyList(ctx: Context, list: StudyList): StudyList {
        val saved = if (list.id.isNotEmpty()) list else StudyList(
            "sl" + System.currentTimeMillis().toString(36) +
                (100..999).random(), list.name, list.pkgs)
        val cur = studyLists(ctx).filter { it.id != saved.id } + saved
        val arr = org.json.JSONArray()
        for (l in cur) {
            val pa = org.json.JSONArray()
            for (p in l.pkgs) pa.put(p)
            arr.put(org.json.JSONObject().put("id", l.id)
                .put("name", l.name).put("pkgs", pa))
        }
        prefs(ctx).edit().putString("study_lists", arr.toString())
            .apply()
        return saved
    }

    fun deleteStudyList(ctx: Context, id: String) {
        val cur = studyLists(ctx).filter { it.id != id }
        val arr = org.json.JSONArray()
        for (l in cur) {
            val pa = org.json.JSONArray()
            for (p in l.pkgs) pa.put(p)
            arr.put(org.json.JSONObject().put("id", l.id)
                .put("name", l.name).put("pkgs", pa))
        }
        prefs(ctx).edit().putString("study_lists", arr.toString())
            .apply()
    }
    // ---- v2.11 ----
    fun sosNumbers(ctx: Context): List<String> =
        (prefs(ctx).getString("sos_numbers", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun setSosNumbers(ctx: Context, v: String) { prefs(ctx).edit().putString("sos_numbers", v).apply() }
    fun panicPinHash(ctx: Context): String = prefs(ctx).getString("panic_pin_hash", "") ?: ""
    fun setPanicPinHash(ctx: Context, v: String) { prefs(ctx).edit().putString("panic_pin_hash", v).apply() }
    fun panicActive(ctx: Context): Boolean = prefs(ctx).getBoolean("panic_active", false)
    fun setPanicActive(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("panic_active", v).apply() }
    fun accent(ctx: Context): String = prefs(ctx).getString("accent", "emerald") ?: "emerald"
    fun setAccent(ctx: Context, v: String) { prefs(ctx).edit().putString("accent", v).apply() }
    // v2.25.0: optional app theme (phonelink_cfg key "app_theme").
    // Unset/missing = "emerald_dark" = exact pre-2.25 behaviour.
    fun appTheme(ctx: Context): String = prefs(ctx).getString("app_theme", "emerald_dark") ?: "emerald_dark"
    fun setAppTheme(ctx: Context, v: String) { prefs(ctx).edit().putString("app_theme", v).apply() }
    fun focusOn(ctx: Context): Boolean = prefs(ctx).getBoolean("focus_on", false)
    fun setFocusOn(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("focus_on", v).apply() }
    fun focusPkgs(ctx: Context): Set<String> =
        (prefs(ctx).getString("focus_pkgs", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun setFocusPkgs(ctx: Context, v: String) { prefs(ctx).edit().putString("focus_pkgs", v).apply() }
    fun focusWindow(ctx: Context): Pair<Int, Int> =
        (prefs(ctx).getInt("focus_start", 9 * 60) to prefs(ctx).getInt("focus_end", 17 * 60))
    fun setFocusWindow(ctx: Context, start: Int, end: Int) {
        prefs(ctx).edit().putInt("focus_start", start).putInt("focus_end", end).apply()
    }
    // ---- v2.11.1 launcher hide (icon lives on .MainAlias) ----
    fun updateLastCheck(ctx: Context): Long = prefs(ctx).getLong("update_last_check", 0L)
    fun setUpdateLastCheck(ctx: Context, v: Long) { prefs(ctx).edit().putLong("update_last_check", v).apply() }

    /** v2.16: auto-update installs new releases by itself
     *  (Shizuku silent install, else a one-tap installer notice). */
    fun autoUpdate(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_update", true)
    fun setAutoUpdate(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("auto_update", v).apply() }
}
