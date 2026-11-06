package com.mthtamim.phonelinkflows

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Templates gallery (new 2026-11-06, Tamim's choice): 40+ ready-made
 * Rules + Flows, picked from the Automation screen's ⋮ menu
 * (TemplatesActivity). Every trigger/action type used here was
 * verified against the real engines before authoring:
 *
 *  RULES run through Automation.runAction / Automation.onEvent, so a
 *  rule template may only use triggers that actually reach the rule
 *  engine: time_at, time_at_days, time_interval, sunrise, sunset,
 *  battery_below/above/full/temp_above, charging_on/off,
 *  wifi_connected/disconnected (tick poll), boot, shake, flip,
 *  proximity, light, app_launched/closed/installed/removed/updated,
 *  screen_unlocked/locked, bt_connected/disconnected, dnd_on/off,
 *  airplane_on/off, dark_on/off, vpn_on/off, roaming_on/off,
 *  music_started/stopped, steps_goal, applock_failed, volume_up/down,
 *  sms_sent. Deliberately NOT used in rules: call_incoming/missed,
 *  sms_received, headphone_in/out, screen_on/off, geofence_in/out —
 *  those events are dispatched to FlowEngine.onEvent only (or, for
 *  geofence, proximity alerts are registered from FLOW nodes only),
 *  so a rule with one of them would silently never fire.
 *
 *  FLOWS run through FlowEngine, whose param formats differ from the
 *  rule engine in places: volume_set is "stream=raw" (e.g. music=10),
 *  brightness_set is 0-255 (e.g. 76 ≈ 30%), dnd_set "on" = full
 *  silent / "off" = normal, screen_timeout_set is milliseconds,
 *  set_alarm is "HH:MM" only, set_timer is seconds only, vibrate and
 *  play_sound take no param. "wait" does not exist in FlowEngine and
 *  "ringer"/"mode_set" are not flow actions — none are used here.
 *  Sunrise/sunset are in FlowEngine's TICK_IGNORED set with no
 *  event source, so they are rules-only. No NFC anywhere (Tamim),
 *  no MQTT, no relay/peer actions, no FocusLock/AutoTap/redial
 *  picker steps, no send_sms/call_number (they need a real number
 *  the template cannot know).
 *
 *  Names/descriptions are Bangla in code (the app's default
 *  language); trigger/action labels in the UI come from
 *  AutoCatalog/FlowCatalog, so they follow the current locale.
 *  Adding a template never edits code: rules go through
 *  Automation.importJson (collision-safe re-id), flows through
 *  FlowStore.saveFlow (the Activity re-ids on collision).
 *  Geofence flow templates read the saved geo centre at add time
 *  (Config.geoIsSet/Lat/Lng/Radius) and refuse with a toast when
 *  no location is set yet.
 */
object Templates {

    data class Template(
        val key: String,
        val kind: String, // "rule" | "flow"
        val name: String,
        val desc: String,
        val trigType: String,
        val trigParam: String,
        val actions: List<Pair<String, String>>,
        val constraints: List<Pair<String, String>> = emptyList(), // rules only
        val condType: String? = null, // flows only
        val condParam: String = "",
        val needsGeo: Boolean = false, // flows only
        // v1.2.0: exit actions (rules only) — run when the trigger
        // ends; same (type, param) shape as [actions].
        val exitActions: List<Pair<String, String>> = emptyList(),
    )

    // ---------------- Rules ----------------

    val rules: List<Template> = listOf(
        Template("r_night_silent", "rule",
            "রাত ১১টায় সাইলেন্ট",
            "রাত ১১টা বাজলেই ফোন পুরো সাইলেন্ট হয়ে যাবে",
            "time_at", "23:00",
            listOf("dnd_set" to "none")),
        Template("r_morning_unsilent", "rule",
            "সকাল ৭টায় সাইলেন্ট বন্ধ",
            "সকাল ৭টায় আবার স্বাভাবিক শব্দ ফিরে আসবে",
            "time_at", "07:00",
            listOf("dnd_set" to "off")),
        Template("r_study_silent", "rule",
            "পড়ার সময় সাইলেন্ট",
            "সন্ধ্যা ৬টায় সাইলেন্ট হয়ে যাবে, যাতে পড়ায় মন বসে",
            "time_at", "18:00",
            listOf("dnd_set" to "none", "toast_local" to "পড়ার সময় — ফোন দূরে রাখো")),
        Template("r_study_end", "rule",
            "পড়া শেষে সাইলেন্ট বন্ধ",
            "রাত ১০টায় আবার স্বাভাবিক শব্দ ফিরবে",
            "time_at", "22:00",
            listOf("dnd_set" to "off")),
        Template("r_class_silent", "rule",
            "ক্লাসের সময় সাইলেন্ট",
            "রবি–বৃহ সকাল ৯টায় ক্লাসের সময় ফোন সাইলেন্ট থাকবে",
            "time_at_days", "time=09:00|days=sun,mon,tue,wed,thu",
            listOf("dnd_set" to "none")),
        Template("r_class_end", "rule",
            "ক্লাস শেষে সাইলেন্ট বন্ধ",
            "রবি–বৃহ দুপুর ২টায় ক্লাস শেষে শব্দ ফিরবে",
            "time_at_days", "time=14:00|days=sun,mon,tue,wed,thu",
            listOf("dnd_set" to "off")),
        Template("r_batt_low", "rule",
            "ব্যাটারি ২০%-এর নিচে",
            "ব্যাটারি কমে গেলে জানাবে আর উজ্জ্বলতা কমিয়ে দেবে",
            "battery_below", "20",
            listOf("local_notify" to "ব্যাটারি ২০%-এর নিচে — চার্জে দাও",
                "brightness_set" to "30")),
        Template("r_batt_80", "rule",
            "ব্যাটারি ৮০% হলে",
            "৮০% চার্জ হলেই জানাবে, চার্জার খুলতে পারো",
            "battery_above", "80",
            listOf("local_notify" to "ব্যাটারি ৮০% হয়েছে — চার্জার খুলতে পারো")),
        Template("r_batt_full", "rule",
            "ব্যাটারি ফুল হলে",
            "১০০% চার্জ হলে জানাবে",
            "battery_full", "",
            listOf("local_notify" to "ব্যাটারি ফুল — চার্জার খুলে নাও")),
        Template("r_batt_hot", "rule",
            "ব্যাটারি গরম হলে",
            "ফোনের তাপমাত্রা ৪০° ছাড়ালে সতর্ক করবে",
            "battery_temp_above", "40",
            listOf("local_notify" to "ফোন গরম হচ্ছে — চার্জার খুলে নাও")),
        Template("r_charging_on", "rule",
            "চার্জে দিলে",
            "চার্জার লাগালেই নোটিফিকেশন দেবে",
            "charging_on", "",
            listOf("local_notify" to "চার্জার লাগানো হয়েছে")),
        Template("r_charging_off", "rule",
            "চার্জার খুললে",
            "চার্জার খুললেই নোটিফিকেশন দেবে",
            "charging_off", "",
            listOf("local_notify" to "চার্জার খোলা হয়েছে")),
        Template("r_youtube_vol", "rule",
            "ইউটিউব খুললে ভলিউম ৮০%",
            "ইউটিউব খুললেই মিডিয়া ভলিউম ৮০% করে দেবে",
            "app_launched", "com.google.android.youtube",
            listOf("volume_set" to "music:80")),
        Template("r_shake_torch", "rule",
            "ঝাঁকালে টর্চ",
            "ফোন ঝাঁকালেই টর্চ জ্বলবে বা নিভবে",
            "shake", "",
            listOf("torch_toggle" to "")),
        Template("r_applock_try", "rule",
            "লক করা অ্যাপে ভুল চেষ্টা",
            "কেউ লক করা অ্যাপ খুলতে চাইলে জানাবে",
            "applock_failed", "",
            listOf("local_notify" to "কেউ তোমার লক করা অ্যাপ খুলতে চেয়েছে")),
        Template("r_water", "rule",
            "প্রতি ৩০ মিনিটে পানি",
            "প্রতি আধা ঘণ্টায় পানি খেতে মনে করিয়ে দেবে",
            "time_interval", "30",
            listOf("local_notify" to "পানি খাওয়ার সময় হয়েছে")),
        Template("r_steps", "rule",
            "১০,০০০ স্টেপ পূর্ণ হলে",
            "দিনে ১০,০০০ স্টেপ হলেই জানাবে",
            "steps_goal", "10000",
            listOf("local_notify" to "১০,০০০ স্টেপ পূর্ণ হয়েছে!")),
        Template("r_sunset", "rule",
            "সূর্যাস্তে জানানো",
            "সূর্য ডুবলেই নোটিফিকেশন দেবে",
            "sunset", "",
            listOf("local_notify" to "সন্ধ্যা হয়েছে")),
        Template("r_boot", "rule",
            "ফোন চালু হলে",
            "ফোন চালু হলেই জানাবে AutomationHub প্রস্তুত",
            "boot", "",
            listOf("toast_local" to "AutomationHub প্রস্তুত")),
        Template("r_night_alarm", "rule",
            "রাতে কালকের অ্যালার্ম",
            "রাত ১০টায় সকাল ৬টার অ্যালার্ম ঠিক করে দেবে",
            "time_at", "22:00",
            listOf("set_alarm" to "06:00|সকালে ওঠো")),
        Template("r_night_timeout", "rule",
            "রাত ১১টায় স্ক্রিন টাইমআউট",
            "রাত ১১টায় স্ক্রিন ৩০ সেকেন্ডে বন্ধ হবে",
            "time_at", "23:00",
            listOf("screen_timeout_set" to "30")),
        Template("r_music_vol", "rule",
            "গান শুরু হলে ভলিউম ৭০%",
            "গান চালু হলেই মিডিয়া ভলিউম ৭০% করে দেবে",
            "music_started", "",
            listOf("volume_set" to "music:70")),
        Template("r_morning_greet", "rule",
            "সকালে আনলক করলে শুভেচ্ছা",
            "সকাল ৬–৯টার মধ্যে ফোন আনলক করলে শুভ সকাল জানাবে",
            "screen_unlocked", "",
            listOf("local_notify" to "শুভ সকাল — আজকের পড়ার তালিকা দেখে নাও"),
            constraints = listOf("time_range" to "06:00-09:00")),
        Template("r_wifi_on", "rule",
            "ওয়াইফাই পেলে",
            "ওয়াইফাই যুক্ত হলেই জানাবে",
            "wifi_connected", "",
            listOf("local_notify" to "ওয়াইফাই যুক্ত হয়েছে")),
        Template("r_wifi_off", "rule",
            "ওয়াইফাই ছুটে গেলে",
            "ওয়াইফাই ছুটে গেলেই জানাবে",
            "wifi_disconnected", "",
            listOf("local_notify" to "ওয়াইফাই ছুটে গেছে — মোবাইল ডেটা চলছে")),
        // v1.2.0: exit-action demos — entry runs when the trigger
        // starts, exit runs when it ends (same toggle back).
        Template("r_brave_data", "rule",
            "Brave খুললে ডেটা, বন্ধ করলে আবার",
            "Brave খুললেই মোবাইল ডেটা টগল হবে, Brave বন্ধ করলেই আবার টগল হবে",
            "app_launched", "com.brave.browser",
            listOf("mobile_data_toggle" to ""),
            exitActions = listOf("mobile_data_toggle" to "")),
        Template("r_charge_dnd", "rule",
            "চার্জে দিলে সাইলেন্ট, খুললে শব্দ",
            "চার্জে দিলেই সাইলেন্ট হবে, চার্জার খুললেই আবার শব্দ ফিরবে",
            "charging_on", "",
            listOf("dnd_set" to "none"),
            exitActions = listOf("dnd_set" to "off")),
    )

    // ---------------- Flows ----------------

    val flows: List<Template> = listOf(
        Template("f_night_silent", "flow",
            "রাত ১১টায় সাইলেন্ট",
            "রাত ১১টা বাজলেই ফোন পুরো সাইলেন্ট হয়ে যাবে",
            "time_at", "23:00",
            listOf("dnd_set" to "on")),
        Template("f_morning_unsilent", "flow",
            "সকাল ৭টায় সাইলেন্ট বন্ধ",
            "সকাল ৭টায় আবার স্বাভাবিক শব্দ ফিরে আসবে",
            "time_at", "07:00",
            listOf("dnd_set" to "off")),
        Template("f_batt_low", "flow",
            "ব্যাটারি ২০%-এর নিচে",
            "ব্যাটারি কমে গেলে জানাবে আর উজ্জ্বলতা কমিয়ে দেবে",
            "battery_below", "20",
            listOf("local_notify" to "ব্যাটারি ২০%-এর নিচে — চার্জে দাও",
                "brightness_set" to "76")),
        Template("f_batt_80", "flow",
            "ব্যাটারি ৮০% হলে",
            "৮০% চার্জ হলেই জানাবে, চার্জার খুলতে পারো",
            "battery_above", "80",
            listOf("local_notify" to "ব্যাটারি ৮০% হয়েছে — চার্জার খুলতে পারো")),
        Template("f_charging_on", "flow",
            "চার্জে দিলে",
            "চার্জার লাগালেই জানাবে",
            "charging_on", "",
            listOf("toast_local" to "চার্জ হচ্ছে")),
        Template("f_charging_off", "flow",
            "চার্জার খুললে",
            "চার্জার খুললেই জানাবে",
            "charging_off", "",
            listOf("toast_local" to "চার্জার খোলা হয়েছে")),
        Template("f_hp_play", "flow",
            "হেডফোন লাগালে গান চালু",
            "হেডফোন লাগালেই গান চালু হয়ে যাবে",
            "headphone_in", "",
            listOf("media_control" to "play")),
        Template("f_hp_pause", "flow",
            "হেডফোন খুললে গান থামে",
            "হেডফোন খুললেই গান থেমে যাবে",
            "headphone_out", "",
            listOf("media_control" to "pause")),
        Template("f_shake_torch", "flow",
            "ঝাঁকালে টর্চ",
            "ফোন ঝাঁকালেই টর্চ জ্বলবে বা নিভবে",
            "shake", "",
            listOf("torch_toggle" to "")),
        Template("f_boot", "flow",
            "ফোন চালু হলে",
            "ফোন চালু হলেই জানাবে AutomationHub প্রস্তুত",
            "boot", "",
            listOf("toast_local" to "AutomationHub প্রস্তুত")),
        Template("f_water", "flow",
            "প্রতি ৩০ মিনিটে পানি",
            "প্রতি আধা ঘণ্টায় পানি খেতে মনে করিয়ে দেবে",
            "time_interval", "30",
            listOf("local_notify" to "পানি খাওয়ার সময় হয়েছে")),
        Template("f_missed_call", "flow",
            "মিসড কল হলে",
            "মিসড কল এলে ভাইব্রেট করে জানাবে",
            "call_missed", "",
            listOf("vibrate" to "", "local_notify" to "একটা মিসড কল এসেছে")),
        Template("f_otp", "flow",
            "OTP মেসেজ এলে",
            "OTP লেখা মেসেজ এলে জানাবে",
            "sms_received", "OTP",
            listOf("local_notify" to "OTP মেসেজ এসেছে")),
        Template("f_wifi_on", "flow",
            "ওয়াইফাই পেলে",
            "ওয়াইফাই যুক্ত হলেই জানাবে",
            "wifi_connected", "",
            listOf("toast_local" to "ওয়াইফাই যুক্ত হয়েছে")),
        Template("f_wifi_off", "flow",
            "ওয়াইফাই ছুটে গেলে",
            "ওয়াইফাই ছুটে গেলেই জানাবে",
            "wifi_disconnected", "",
            listOf("toast_local" to "ওয়াইফাই ছুটে গেছে")),
        Template("f_youtube_vol", "flow",
            "ইউটিউব খুললে ভলিউম",
            "ইউটিউব খুললেই মিডিয়া ভলিউম বাড়িয়ে দেবে",
            "app_launched", "com.google.android.youtube",
            listOf("volume_set" to "music=10")),
        Template("f_morning_alarm", "flow",
            "সকাল ৬:৩০-এ অ্যালার্ম",
            "সকাল ৬:৩০-এ অ্যালার্ম বাজবে",
            "time_at", "06:30",
            listOf("set_alarm" to "06:30")),
        Template("f_night_bright", "flow",
            "রাত ১০টায় উজ্জ্বলতা কম",
            "রাত ১০টায় স্ক্রিনের উজ্জ্বলতা কমিয়ে দেবে",
            "time_at", "22:00",
            listOf("brightness_set" to "51")),
        Template("f_morning_bright", "flow",
            "সকাল ৭টায় উজ্জ্বলতা বেশি",
            "সকাল ৭টায় স্ক্রিনের উজ্জ্বলতা বাড়িয়ে দেবে",
            "time_at", "07:00",
            listOf("brightness_set" to "204")),
        Template("f_batt_night", "flow",
            "রাত ১০টায় ব্যাটারি কম থাকলে জানাও",
            "রাত ১০টায় ব্যাটারি ৩০%-এর নিচে থাকলে চার্জে দিতে বলবে",
            "time_at", "22:00",
            listOf("local_notify" to "ব্যাটারি কম — রাতে চার্জে দাও"),
            condType = "battery_below", condParam = "30"),
        Template("f_late_night", "flow",
            "গভীর রাতে ফোন ধরলে",
            "রাত ১১টা–সকাল ৬টার মধ্যে স্ক্রিন অন করলে ঘুমাতে বলবে",
            "screen_on", "",
            listOf("toast_local" to "অনেক রাত — ঘুমিয়ে পড়ো"),
            condType = "time_range", condParam = "23:00-06:00"),
        Template("f_geo_home", "flow",
            "বাড়ি ফিরলে",
            "বাড়ির জিও-ফেন্সে ঢুকলে জানাবে আর সাইলেন্ট বন্ধ করবে (আগে জিও-ফেন্সের লোকেশন ঠিক করো)",
            "geofence_in", "",
            listOf("local_notify" to "বাড়ি ফিরেছ", "dnd_set" to "off"),
            needsGeo = true),
        Template("f_geo_leave", "flow",
            "বাড়ি ছাড়লে",
            "বাড়ির জিও-ফেন্স ছাড়লে জানাবে আর সাইলেন্ট করবে (আগে জিও-ফেন্সের লোকেশন ঠিক করো)",
            "geofence_out", "",
            listOf("local_notify" to "বাড়ি ছেড়েছ", "dnd_set" to "on"),
            needsGeo = true),
        Template("f_night_lock", "flow",
            "রাত ১১:৩০-এ স্ক্রিন লক",
            "রাত সাড়ে ১১টায় ফোন লক হয়ে যাবে",
            "time_at", "23:30",
            listOf("lock_screen" to "")),
    )

    // ---------------- Builders ----------------

    /** A rule template as the stored rule JSON. Rules are grouped
     *  under cat "টেমপ্লেট" in the Automation list. The id is fresh
     *  per add; Automation.importJson re-ids on collision anyway. */
    fun buildRule(t: Template): JSONObject {
        val rule = JSONObject()
            .put("id", "r" + System.currentTimeMillis())
            .put("name", t.name)
            .put("cat", "টেমপ্লেট")
            .put("enabled", true)
        rule.put("trigger", JSONObject()
            .put("type", t.trigType).put("param", t.trigParam))
        rule.put("constraints_mode", "all")
        val cons = JSONArray()
        for ((ct, cp) in t.constraints) {
            cons.put(JSONObject().put("type", ct).put("param", cp))
        }
        rule.put("constraints", cons)
        val acts = JSONArray()
        for ((at, ap) in t.actions) {
            acts.put(JSONObject().put("type", at).put("param", ap))
        }
        rule.put("actions", acts)
        // v1.2.0: exit actions (rules only).
        if (t.exitActions.isNotEmpty()) {
            val exits = JSONArray()
            for ((at, ap) in t.exitActions) {
                exits.put(JSONObject().put("type", at).put("param", ap))
            }
            rule.put("exit_actions", exits)
        }
        return rule
    }

    /** A flow template as the stored flow JSON ({id, name, enabled,
     *  nodes, edges}). Null when a geofence template has no saved
     *  geo centre yet — the caller toasts and does not add. Node
     *  positions are simple top-to-bottom canvas coordinates. */
    fun buildFlow(ctx: Context, t: Template): JSONObject? {
        val trigParam = if (t.needsGeo) {
            if (!Config.geoIsSet(ctx)) return null
            "${Config.geoLat(ctx)}|${Config.geoLng(ctx)}|${Config.geoRadius(ctx)}"
        } else {
            t.trigParam
        }
        val flow = JSONObject()
        flow.put("id", "f" + System.currentTimeMillis().toString())
        flow.put("name", t.name)
        flow.put("enabled", true)
        val nodes = JSONArray()
        val edges = JSONArray()
        var y = 120f
        val trig = FlowFlow.newNode("t", t.trigType, trigParam, 80f, y)
        nodes.put(trig)
        y += 170f
        var prevId = trig.optString("id")
        var prevBranch = "any"
        if (t.condType != null) {
            val cond = FlowFlow.newNode("c", t.condType, t.condParam, 80f, y)
            nodes.put(cond)
            y += 170f
            edges.put(FlowFlow.newEdge(prevId, cond.optString("id"), "any"))
            prevId = cond.optString("id")
            prevBranch = "yes"
        }
        for ((at, ap) in t.actions) {
            val a = FlowFlow.newNode("a", at, ap, 80f, y)
            nodes.put(a)
            y += 170f
            edges.put(FlowFlow.newEdge(prevId, a.optString("id"), prevBranch))
            prevId = a.optString("id")
            prevBranch = "any"
        }
        flow.put("nodes", nodes)
        flow.put("edges", edges)
        return flow
    }
}
