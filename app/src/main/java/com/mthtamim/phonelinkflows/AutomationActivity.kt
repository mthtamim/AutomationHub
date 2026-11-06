package com.mthtamim.phonelinkflows

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject
import kotlin.concurrent.thread

/** Type catalogs shared by the list and the editor. */
object AutoCatalog {
    val triggers = listOf(
        "time_at", "time_interval", "sunrise", "sunset",
        "battery_below", "battery_above", "charging_on", "charging_off",
        "wifi_connected", "wifi_disconnected", "screen_on", "screen_off",
        "boot", "shake", "flip_down", "flip_up", "proximity_near", "proximity_far",
        "light_below", "light_above", "headphone_in", "headphone_out", "usb_connected",
        "app_launched", "app_closed", "sms_received", "notif_received",
        "call_incoming", "call_missed", "geofence_in", "geofence_out",
        "steps_goal", "activity", "weather", "nfc_tag", "clipboard",
        "screen_text", "peer_offline",
        // v2.7 MacroDroid expansion
        "battery_full", "battery_temp_above", "call_outgoing", "call_ended",
        "sms_sent", "notif_cleared", "app_installed", "app_removed",
        "screen_unlocked", "bt_connected", "bt_disconnected", "dnd_on", "dnd_off",
        "airplane_on", "airplane_off", "storage_low", "timezone_changed",
        "locale_changed", "stopwatch", "date_at",
        "music_started", "music_stopped", "vpn_on", "vpn_off",
        "roaming_on", "roaming_off", "dark_on", "dark_off",
        // v2.8 final MacroDroid round
        "volume_up", "volume_down", "qr_scanned", "webhook", "weather_forecast",
        "calendar_start", "calendar_end", "magnet_above", "pressure_above",
        "hotspot_on", "hotspot_off", "rule_toggled",
        // v2.9 final round
        "battery_saver_on", "battery_saver_off", "wireless_charging",
        "screen_locked", "screenshot_taken", "photo_taken", "video_taken",
        "app_updated", "sms_delivered", "orientation_changed", "pick_up",
        "bt_near", "bt_far", "sim_changed", "ip_changed", "camera_used",
        "mic_used", "split_screen", "notify_button",
        // v2.10 Shizuku round
        "mobile_data_on", "mobile_data_off",
        // v2.16 MacroDroid shortlist round
        "ui_click", "var_changed", "gps_on", "gps_off", "file_changed",
        "call_active",
        // v2.18 (WS4)
        "mqtt_message",
        // v2.18 App Locker
        "applock_failed",
        // v2.20 Study Guard (picker cards in the rule editor,
        // like focuslock_control — not the raw trigger picker)
        "study_guard_continuous", "study_guard_daily",
        // v2.21 Screen rest (picker card in the rule editor,
        // like Study Guard — not the raw trigger picker)
        "screen_on_continuous",
        // v2.23 Day-time (picker card in the rule editor,
        // like screen_on_continuous — not the raw trigger picker)
        "time_at_days")
    val constraints = listOf(
        "time_range", "day_of_week", "battery_above", "battery_below", "charging",
        "wifi_ssid", "location_in", "screen", "foreground_app", "variable",
        // v2.7 MacroDroid expansion
        "music_playing", "call_active", "vpn", "roaming", "dark_mode", "dnd",
        "charging_type", "battery_temp_below", "battery_temp_above", "steps_today",
        "proximity_near", "light_below", "light_above", "airplane",
        "bt_connected",
        // v2.8 final MacroDroid round
        "date_range", "day_of_month", "signal_above", "rule_enabled",
        // v2.9 final round
        "keyguard_locked", "battery_saver", "data_saver", "speed_above",
        "uptime_above", "calendar_busy", "headset_mic", "process_running",
        // v2.10 Shizuku round
        "mobile_data",
        // v2.16 MacroDroid shortlist round
        "notif_present", "gps_state", "hotspot_state")
    val actions = listOf(
        "local_notify", "speak_local", "command_peer",
        "send_sms", "torch", "torch_on", "torch_off", "torch_toggle",
        "ringer", "open_app", "http", "write_file",
        "set_var", "if_var", "if_else", "wait", "mode_set", "lost_on", "lost_off", "tap_text",
        // v2.7 MacroDroid expansion
        "media_control", "volume_set", "brightness_set", "screen_timeout_set",
        "auto_rotate_set", "bt_toggle", "dnd_set", "vibrate", "play_sound",
        "record_audio", "take_photo", "open_url", "share_text", "kill_app",
        "answer_call", "end_call", "set_wallpaper", "clear_notif", "global_key",
        "stopwatch_start", "stopwatch_stop", "rule_enable", "rule_disable",
        "rule_run", "inc_var", "delete_file", "lock_screen", "wake_screen",
        "send_intent", "toast_local", "stop",
        // v2.8 final MacroDroid round
        "gesture_tap", "gesture_swipe", "read_screen", "send_email",
        "calendar_event", "set_alarm", "set_timer", "file_copy", "file_move",
        "file_zip", "dialog_choice", "keep_awake", "brightness_auto", "flash_pattern",
        // v2.9 final round
        "call_number", "ussd_run", "whatsapp_msg", "telegram_msg",
        "notif_reply", "rich_notify", "type_text", "font_scale",
        "night_light", "torch_level", "ftp_upload", "ftp_download",
        "image_resize", "file_encrypt", "file_decrypt", "json_get",
        "http_to_var", "regex_op", "date_calc", "loop_start", "loop_end",
        "break", "else", "end_if", "shell_cmd", "screen_pin", "unpin",
        "private_dns", "install_app", "uninstall_app", "aod_toggle",
        "sqlite_query",
        // v2.10 Shizuku (root-class) round
        "reboot_device", "airplane_toggle", "mobile_data_toggle",
        "wifi_toggle", "shell_screenshot", "clear_app_data", "app_disable",
        "app_enable", "force_stop", "install_silent", "perm_grant",
        "perm_revoke", "appops_set", "wm_density", "wm_size", "anim_scale",
        "stay_on", "set_time", "set_locale", "shell_tap", "shell_swipe",
        "shell_input", "shell_key", "shell_to_var",
        // v2.16 MacroDroid shortlist round
        "hotspot_toggle", "dark_theme", "battery_saver", "gps_toggle",
        "nfc_toggle", "record_video", "qr_show", "read_file",
        "clipboard_set", "delete_var", "calc_var", "wait_until",
        // v2.18 (WS4)
        "mqtt_publish",
        // v2.18 App Locker
        "lock_app", "unlock_app",
        // v2.20 FocusLock (picker step; added via its editor button,
        // like if_else, not the raw action picker)
        "focuslock_control",
        // v2.21 AutoTap (picker step; added via its editor button,
        // like focuslock_control, not the raw action picker)
        "autotap_control",
        // v2.22 Redial (picker step; added via its editor button,
        // like autotap_control, not the raw action picker)
        "redial_control")

    fun label(ctx: Context, kind: String, type: String): String {
        val resName = when (kind) {
            "t" -> "at_$type"; "c" -> "ac_$type"; else -> "aa_$type"
        }
        val id = ctx.resources.getIdentifier(resName, "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else type
    }

    fun hint(ctx: Context, kind: String, type: String): String {
        val resName = when (kind) {
            "t" -> "ath_$type"; "c" -> "ach_$type"; else -> "aah_$type"
        }
        val id = ctx.resources.getIdentifier(resName, "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else ""
    }
}

class AutomationActivity : LocalizedActivity() {

    private lateinit var container: LinearLayout

    /** v2.24.0 (Lab merge): which tab is showing. Rules rendering
     *  is untouched; the Flows tab builds its own cards below. */
    private var flowTab = false

    companion object {
        private const val MENU_SG = 1
        private const val MENU_BACKUP = 2
        private const val MENU_QR = 3
        private const val MENU_LOG = 4
        private const val MENU_VARS = 5
        private const val MENU_EXPORT = 6
        private const val MENU_IMPORT = 7
        private const val MENU_FLOW_IMPORT = 11
        private const val MENU_FLOW_FLO = 12
        private const val MENU_FLOW_EXPORT = 13
        private const val MENU_FLOW_BACKUP = 14
        private const val MENU_FLOW_RESTORE = 15
        private const val MENU_FLOW_LOG = 16
        private const val MENU_TEMPLATES = 20
    }

    private val scanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra("code") ?: return@registerForActivityResult
        if (code.isNotEmpty()) Automation.onEvent(this, "qr_scanned",
            JSONObject().put("title", code).put("body", code))
    }

    private fun showLog() {
        val lines = Automation.recentLog(this)
        val tv = TextView(this).apply {
            text = if (lines.isEmpty()) getString(R.string.auto_log_empty)
                else lines.joinToString("\n")
            setPadding(48, 24, 48, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
        }
        val scroll = android.widget.ScrollView(this).apply { addView(tv) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_log))
            .setView(scroll)
            .setNeutralButton(getString(R.string.auto_log_clear)) { _, _ ->
                Automation.clearLog(this)
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showVars() {
        val names = Automation.varNames(this)
        if (names.isEmpty()) {
            android.widget.Toast.makeText(this,
                getString(R.string.auto_vars_empty), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val labels = names.map { "$it = ${Automation.getVar(this, it)}" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_vars))
            .setItems(labels) { _, which ->
                val name = names[which]
                val input = android.widget.EditText(this).apply {
                    setText(Automation.getVar(this@AutomationActivity, name))
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(name)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Automation.setVar(this, name, input.text.toString())
                    }
                    .setNegativeButton(getString(R.string.auto_var_delete)) { _, _ ->
                        Automation.deleteVar(this, name)
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .show()
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** v2.9: pick one of the rotating rule-set backups to restore. */
    private fun showBackups() {
        val backs = Automation.backups(this)
        if (backs.isEmpty()) {
            android.widget.Toast.makeText(this,
                getString(R.string.auto_backup_none),
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val fmt = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US)
        val labels = backs.map { (t, json) ->
            val n = try { org.json.JSONArray(json).length() }
                catch (e: Exception) { 0 }
            fmt.format(java.util.Date(t)) + " — " + n + " rules"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_backup_title))
            .setItems(labels) { _, which ->
                if (Automation.restoreBackup(this, backs[which].second)) {
                    android.widget.Toast.makeText(this,
                        getString(R.string.auto_backup_restored),
                        android.widget.Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private val importFileLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            thread {
                val text = try {
                    contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.readText().orEmpty()
                } catch (e: Exception) { "" }
                runOnUiThread { doImport(text) }
            }
        }
    }

    /** Shared by the paste box and the file picker (v2.14.3). */
    private fun doImport(raw: String) {
        val text = raw.trim()
        if (text.startsWith("{") && text.contains("full-backup")) {
            val ok = SettingsTransfer.import(this, text)
            android.widget.Toast.makeText(this,
                getString(if (ok) R.string.toast_cfg_imported
                    else R.string.auto_import_failed),
                android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val n = Automation.importJson(this, text)
            android.widget.Toast.makeText(this,
                if (n >= 0) getString(R.string.auto_imported, n)
                else getString(R.string.auto_import_failed),
                android.widget.Toast.LENGTH_SHORT).show()
        }
        renderHealth()
        render()
    }

    private fun showImport() {
        // v2.14.3: pasting a long JSON into a one-line box was nearly
        // unusable — the field is properly multi-line now, and a file
        // picker does the same import without any copy-paste at all.
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.auto_import_hint)
            minLines = 4
            maxLines = 8
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_import))
            .setView(input)
            .setNeutralButton(getString(R.string.auto_import_file)) { _, _ ->
                importFileLauncher.launch(
                    arrayOf("application/json", "text/plain", "*/*"))
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                doImport(input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automation)
        container = findViewById(R.id.autoContainer)
        // v1.2.0: same Essentials pill bottom bar as MainActivity.
        // Home/Settings hand over to MainActivity with a tab extra;
        // the bar's + button adds a rule (Rules tab) or a flow
        // (Flows tab) — it replaces the two old FABs (removed).
        setupNavBar()
        // v2.24.1: no header row — the Rules FAB (same style and
        // v1.2.0: the bar's + button (see setupNavBar) replaces
        // the two old FABs — it opens the rule editor on the Rules
        // tab and the flow editor on the Flows tab.
        // v2.24.1: filled Rules | Flows segmented control on top.
        // The ⋮ menu (right end of the tab row) branches with the
        // tab; the Flows FAB opens a new flow editor.
        findViewById<TextView>(R.id.tabRules).setOnClickListener { switchTab(false) }
        findViewById<TextView>(R.id.tabFlows).setOnClickListener { switchTab(true) }
        findViewById<android.widget.ImageButton>(R.id.btnAutoMenu).setOnClickListener {
            if (flowTab) showFlowMenu(it) else showAutoMenu(it)
        }
        // v2.20.1: the compact line expands to the full 3-row detail.
        findViewById<LinearLayout>(R.id.statusLineRow).setOnClickListener {
            val detail = findViewById<LinearLayout>(R.id.statusDetail)
            val open = detail.visibility != View.VISIBLE
            detail.visibility = if (open) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.tvStatusChev).text = if (open) "⌃" else "⌄"
        }
        findViewById<Button>(R.id.btnFlToken).setOnClickListener {
            showFocusLockTokenDialog()
        }
        findViewById<Button>(R.id.btnFlOpen).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage(
                Automation.FOCUSLOCK_PKG)
            if (launch != null) startActivity(launch)
            else android.widget.Toast.makeText(this,
                getString(R.string.fl_not_installed_toast),
                android.widget.Toast.LENGTH_SHORT).show()
        }
        renderFocusLock()
        val btnRest = findViewById<Button>(R.id.btnRestTime)
        btnRest.text = Automation.restSeconds(this).toString() + "s"
        btnRest.setOnClickListener {
            val et = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(Automation.restSeconds(this@AutomationActivity)
                    .toString())
                setSelection(text.length)
                hint = getString(R.string.auto_rest_hint)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.auto_rest_title))
                .setView(et)
                .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                    val sec = et.text.toString().toIntOrNull() ?: 10
                    Automation.setRestSeconds(this, sec)
                    btnRest.text = Automation.restSeconds(this)
                        .toString() + "s"
                    refreshStatusLine()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // v1.2.0: re-paint the pill bar selection (the old
        // applyGlassNavTint call is gone with the BottomNavigationView).
        selectNavTab(R.id.navAutomation)
        renderHealth()
        renderFocusLock()
        if (flowTab) renderFlows() else render()
    }

    /** v2.20: FocusLock card status — install state from the
     *  PackageManager, token state from Config. The token itself
     *  is NEVER shown; the status only carries the •••• marker. */
    // v1.2.0: dp helper for the DIY card builders (the rest of
    // this file uses raw pixels; the new card code uses dp).
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun renderFocusLock() {
        val installed = Automation.focusLockInstalled(this)
        val hasToken = Config.focusLockToken(this).isNotEmpty()
        findViewById<TextView>(R.id.tvFlStatus).text =
            getString(if (installed) R.string.fl_status_connected
                else R.string.fl_status_missing) + " • " +
            getString(if (hasToken) R.string.fl_status_token_set
                else R.string.fl_status_token_unset)
        findViewById<Button>(R.id.btnFlToken).text =
            getString(if (hasToken) R.string.fl_btn_token_change
                else R.string.fl_btn_token_set)
    }

    /** v2.20: one-time token save. The field always starts EMPTY
     *  (the saved token is never displayed anywhere); saving an
     *  empty field clears the token. */
    private fun showFocusLockTokenDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.fl_token_hint)
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.fl_token_dialog_title))
            .setMessage(getString(R.string.fl_token_dialog_msg))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val v = input.text.toString().trim()
                Config.setFocusLockToken(this, v)
                android.widget.Toast.makeText(this,
                    getString(if (v.isEmpty()) R.string.fl_token_cleared
                        else R.string.fl_token_saved),
                    android.widget.Toast.LENGTH_SHORT).show()
                renderFocusLock()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    /** v2.14.1: trigger health — several trigger families silently do
     *  nothing while a system access is off. Show each one's live
     *  status. v2.17.4: a tap no longer opens a second grant path —
     *  every broken row deep-links to Permissions & Access, the one
     *  place all access is granted (inventory move #5/#6). */
    private fun renderHealth() {
        val box = findViewById<LinearLayout>(R.id.healthRows)
        box.removeAllViews()
        val toPermissions: () -> Unit = {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        val items = mutableListOf<Triple<Int, Boolean, (() -> Unit)?>>()
        items.add(Triple(R.string.auto_health_a11y, a11yEnabled(), toPermissions))
        items.add(Triple(R.string.auto_health_notif, PermChecks.notifAccess(this), toPermissions))
        items.add(Triple(R.string.auto_health_usage, usageAccess(), toPermissions))
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            items.add(Triple(R.string.auto_health_alarm, am.canScheduleExactAlarms(), toPermissions))
        }
        items.add(Triple(R.string.auto_health_shizuku, ShizukuHelper.granted(), toPermissions))
        // v2.15.0: sms_sent quietly dies without READ_SMS — visible here.
        items.add(Triple(R.string.auto_health_sms,
            checkSelfPermission(android.Manifest.permission.READ_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED, toPermissions))
        // v2.14.2: compact — the title line carries the summary, and
        // only the broken items get a row (the full five-row list ate
        // too much of the screen).
        val broken = items.filter { !it.second }
        findViewById<TextView>(R.id.tvHealthTitle).text =
            if (broken.isEmpty())
                getString(R.string.auto_health_all_ok)
            else
                getString(R.string.auto_health_broken, broken.size)
        for ((label, _, action) in broken) {
            box.addView(TextView(this).apply {
                text = "• " + getString(label) + getString(R.string.auto_health_fix)
                setPadding(0, 6, 0, 6)
                if (action != null) setOnClickListener { action() }
            })
        }
    }

    private fun a11yEnabled(): Boolean = try {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    } catch (_: Exception) { false }

    private fun usageAccess(): Boolean = try {
        val ao = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        else
            @Suppress("DEPRECATION")
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    /** v2.20.1: the compact header line — rule counts + trigger
     *  health + rest seconds in one row. The health title already
     *  carries the broken count, so it is not appended twice. The
     *  sync mark is dim when rule sync is off; the switch itself
     *  lives in the expanded detail. */
    private fun refreshStatusLine() {
        val list = Automation.rules(this)
        val total = list.size
        val active = list.count { it.optBoolean("enabled", true) }
        val health = findViewById<TextView>(R.id.tvHealthTitle).text.toString()
        val rest = Automation.restSeconds(this).toString() + "s"
        findViewById<TextView>(R.id.tvStatusLine).text =
            getString(R.string.auto_pill_compact, active, total) +
                " · " + health + " · " + rest
        findViewById<TextView>(R.id.tvSyncMark).alpha =
            if (Config.automationOn(this)) 1.0f else 0.35f
    }

    /** v2.20.1: the ⋮ menu — Study Guard first (its list count is
     *  built fresh on every open), then the six quick tools that
     *  used to sit in the fixed header row. */
    private fun showAutoMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        val n = Config.studyLists(this).size
        val sgTitle = getString(R.string.sg_card_title) + " · " +
            if (n == 0) getString(R.string.sg_card_empty)
            else getString(R.string.sg_card_status, n)
        popup.menu.add(0, MENU_SG, 0, sgTitle)
        popup.menu.add(0, MENU_BACKUP, 1, getString(R.string.auto_backup))
        popup.menu.add(0, MENU_QR, 2, getString(R.string.auto_qr))
        popup.menu.add(0, MENU_LOG, 3, getString(R.string.auto_log))
        popup.menu.add(0, MENU_VARS, 4, getString(R.string.auto_vars))
        popup.menu.add(0, MENU_EXPORT, 5, getString(R.string.auto_export))
        popup.menu.add(0, MENU_IMPORT, 6, getString(R.string.auto_import))
        popup.menu.add(0, MENU_TEMPLATES, 7, getString(R.string.templates_menu))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SG -> StudyGuardUi.showManager(this) { }
                MENU_BACKUP -> showBackups()
                MENU_QR -> scanLauncher.launch(Intent(this, FlowScanActivity::class.java))
                MENU_LOG -> showLog()
                MENU_VARS -> showVars()
                MENU_EXPORT -> {
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_TEXT, Automation.exportJson(this))
                    startActivity(Intent.createChooser(send, getString(R.string.auto_export)))
                }
                MENU_IMPORT -> showImport()
                MENU_TEMPLATES -> startActivity(
                    Intent(this, TemplatesActivity::class.java))
            }
            true
        }
        popup.show()
    }

    // v2.25.1: themeAccentColor() now lives on LocalizedActivity and
    // delegates to the single ThemeColors resolver; the old private
    // copy here was deleted so there is exactly one resolver.

    /** v2.24.0 (Lab merge): tab switch. Rules tab restores
     *  everything exactly as before (status card, FocusLock card,
     *  Add Rule FAB); Flows tab hides the rule chrome, shows
     *  the flow FAB, and renders flow cards into the same
     *  container. Token setup stays where it was — the FocusLock
     *  card on the Rules tab and relay settings — never in Flow. */
    /** v1.2.0: wire the Essentials pill bottom bar — same bar as
     *  MainActivity. Home/Settings hand over to MainActivity with
     *  a tab extra and finish; DIY is this screen; the bar's +
     *  button adds a rule (Rules tab) or a flow (Flows tab),
     *  replacing the two old FABs. */
    private fun setupNavBar() {
        val bar = findViewById<View>(R.id.autoBottomNav)
        val goMain = { tab: String ->
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        bar.findViewById<View>(R.id.navTabHome).setOnClickListener {
            goMain("home")
        }
        bar.findViewById<View>(R.id.navTabAuto).setOnClickListener {
            selectNavTab(R.id.navAutomation)
        }
        bar.findViewById<View>(R.id.navTabSettings).setOnClickListener {
            goMain("settings")
        }
        bar.findViewById<View>(R.id.navAdd).setOnClickListener {
            if (flowTab) startActivity(
                Intent(this, FlowEditorActivity::class.java))
            else startActivity(
                Intent(this, RuleEditorActivity::class.java))
        }
        selectNavTab(R.id.navAutomation)
    }

    /** v1.2.0: paint the pill bar — selected tab gets the dark
     *  pill with light text+icon; the others stay sage with dark
     *  text+icon. Fixed sage tokens (ess_bar_*) — not theme
     *  colours — so the bar looks the same in all 8 themes. */
    private fun selectNavTab(id: Int) {
        val bar = findViewById<View>(R.id.autoBottomNav)
        val tabs = listOf(
            Triple(R.id.navTabHome, R.id.navIcoHome, R.id.navTxtHome) to R.id.navHome,
            Triple(R.id.navTabAuto, R.id.navIcoAuto, R.id.navTxtAuto) to R.id.navAutomation,
            Triple(R.id.navTabSettings, R.id.navIcoSettings, R.id.navTxtSettings) to R.id.navSettings
        )
        for ((views, logical) in tabs) {
            val (tabId, icoId, txtId) = views
            val tab = bar.findViewById<View>(tabId)
            val selected = logical == id
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_nav_sel else 0)
            val fg = androidx.core.content.ContextCompat.getColor(
                this,
                if (selected) R.color.ess_bar_sel_text
                else R.color.ess_bar_text)
            (bar.findViewById<ImageView>(icoId)).imageTintList =
                android.content.res.ColorStateList.valueOf(fg)
            (bar.findViewById<TextView>(txtId)).setTextColor(fg)
        }
    }

    // ---- Essentials DIY routine card (v1.2.0) ----
    // A routine card is: sage trigger box | arrow chip(s) | action
    // box(es). Entry actions get a forward arrow (→); exit actions
    // (rules only) get a back arrow (←) and a second action box.
    // Labels ellipsize to one line, exactly like Essentials
    // ("Toggle Ce..."). All views are built in code from the
    // existing catalogs — no new per-frame or per-refresh work.

    /** Small icon for a trigger type, by family. Only drawables
     *  that already exist are used; anything unmapped gets the
     *  automation glyph. */
    private fun triggerIconRes(type: String): Int = when {
        type.startsWith("app_") -> R.drawable.ic_grid
        type.startsWith("wifi_") -> R.drawable.ic_wifi
        type.startsWith("time_") || type == "sunrise" ||
            type == "sunset" || type == "date_at" ||
            type == "stopwatch" -> R.drawable.ic_clock
        type.startsWith("geofence") || type.startsWith("location") ->
            R.drawable.ic_loc
        type.startsWith("screen_") -> R.drawable.ic_eye
        type.startsWith("call_") -> R.drawable.ic_phone
        type.startsWith("charging") || type.startsWith("battery") ->
            R.drawable.ic_bulb
        else -> R.drawable.ic_nav_auto
    }

    /** Sage trigger box: icon + label + param, both lines
     *  ellipsized. */
    private fun diyTriggerBox(
        label: String, param: String, iconRes: Int
    ): LinearLayout {
        val onContainer = themeOnPrimaryContainer()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_trigger_box)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.05f)
        }
        box.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList =
                android.content.res.ColorStateList.valueOf(onContainer)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        box.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onContainer)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4) }
        })
        if (param.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = param
                textSize = 12.5f
                setTextColor(onContainer)
                alpha = 0.85f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
        return box
    }

    /** Dark action box: small play glyph + ellipsized label. */
    private fun diyActionBox(label: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_action_box)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_run)
            imageTintList = android.content.res.ColorStateList
                .valueOf(themeOnSurfaceVariant())
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
        box.addView(TextView(this).apply {
            text = label
            textSize = 13.5f
            setTextColor(themeOnSurface())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(8) }
        })
        return box
    }

    /** Arrow chip column between trigger and actions: → always,
     *  ← below it when the rule has exit actions. */
    private fun diyArrowColumn(hasExit: Boolean): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(4); marginEnd = dp(4) }
        }
        val arrow = { glyph: String ->
            TextView(this).apply {
                text = glyph
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(themeAccentColor())
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_arrow_chip)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                    .apply { topMargin = dp(3); bottomMargin = dp(3) }
            }
        }
        col.addView(arrow("→"))
        if (hasExit) col.addView(arrow("←"))
        return col
    }

    /** Full DIY row: trigger box | arrows | stacked action boxes
     *  (entry, then exit when present). */
    private fun diyRow(
        trigLabel: String, trigParam: String, trigIcon: Int,
        entryLabel: String, exitLabel: String?
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        row.addView(diyTriggerBox(trigLabel, trigParam, trigIcon))
        row.addView(diyArrowColumn(exitLabel != null))
        val acts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }
        acts.addView(diyActionBox(entryLabel))
        if (exitLabel != null) {
            val exitBox = diyActionBox(exitLabel)
            (exitBox.layoutParams as LinearLayout.LayoutParams)
                .topMargin = dp(6)
            acts.addView(exitBox)
        }
        row.addView(acts)
        return row
    }

    /** First action label of a rule/flow action list, with a
     *  " +N" suffix when more follow. Empty list → "—". */
    private fun firstActionLabel(
        acts: org.json.JSONArray, kind: String
    ): String {
        if (acts.length() == 0) return "—"
        val first = acts.optJSONObject(0) ?: return "—"
        val label = if (kind == "flow")
            FlowCatalog.label(this, "a", first.optString("type"))
        else
            AutoCatalog.label(this, "a", first.optString("type"))
        return if (acts.length() > 1) "$label +${acts.length() - 1}"
        else label
    }

    /** First "a" node label of a flow's node list, with a
     *  " +N" suffix when more follow. No "a" node → "—". */
    private fun firstFlowActionLabel(nodes: org.json.JSONArray?): String {
        if (nodes == null) return "—"
        var total = 0
        var first: org.json.JSONObject? = null
        for (k in 0 until nodes.length()) {
            val n = nodes.optJSONObject(k) ?: continue
            if (n.optString("kind") == "a") {
                total++
                if (first == null) first = n
            }
        }
        if (first == null) return "—"
        val label = FlowCatalog.label(this, "a", first.optString("type"))
        return if (total > 1) "$label +${total - 1}" else label
    }

    private fun switchTab(flows: Boolean) {
        flowTab = flows
        // v2.24.1: filled segmented tabs — selected mint fill with
        // dark text, unselected dark fill with light text.
        val fillSelected = android.content.res.ColorStateList.valueOf(themeAccentColor())
        // v2.25.1: unselected tab fill follows the theme — Emerald
        // keeps btn_fill_night exactly; glass themes use the theme's
        // surfaceVariant (translucent, so the gradient shows).
        val fillUnselected = if (Config.appTheme(this) == "emerald_dark") {
            androidx.core.content.ContextCompat.getColorStateList(this, R.color.btn_fill_night)
        } else {
            android.content.res.ColorStateList.valueOf(themeSurfaceVariant())
        }
        val textDark = androidx.core.content.ContextCompat.getColor(this, R.color.tab_text_dark)
        val textLight = androidx.core.content.ContextCompat.getColor(this, R.color.tab_text_light)
        val tabRules = findViewById<TextView>(R.id.tabRules)
        val tabFlows = findViewById<TextView>(R.id.tabFlows)
        tabRules.backgroundTintList = if (flows) fillUnselected else fillSelected
        tabRules.setTextColor(if (flows) textLight else textDark)
        tabRules.setTypeface(tabRules.typeface,
            if (flows) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
        tabFlows.backgroundTintList = if (flows) fillSelected else fillUnselected
        tabFlows.setTextColor(if (flows) textDark else textLight)
        tabFlows.setTypeface(tabFlows.typeface,
            if (flows) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        findViewById<View>(R.id.statusCard).visibility =
            if (flows) View.GONE else View.VISIBLE
        findViewById<View>(R.id.flCard).visibility =
            if (flows) View.GONE else View.VISIBLE
        // v1.2.0: the two old FABs are gone — the bar's + button
        // (see setupNavBar) adds a rule or a flow instead.
        if (flows) renderFlows() else render()
    }

    /** v2.24.0 (Lab merge): Flows tab — service switch with a
     *  liveness line, then one card per flow (toggle + Run now /
     *  Edit / Dup / Log / Delete). Button rows scroll horizontally
     *  so Dup and Log stay reachable on narrow screens. */
    private fun renderFlows() {
        container.removeAllViews()
        fun pillButton(labelRes: Int, mint: Boolean) = Button(this).apply {
            text = getString(labelRes)
            textSize = 12.5f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setBackgroundResource(
                if (mint) R.drawable.bg_action_pill
                else R.drawable.bg_action_ghost)
            setPadding(40, 30, 40, 30)
            setTextColor(
                if (mint) themeAccentColor() else themeOnSurface())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(8, 0, 0, 0) }
        }
        val svcOn = FlowStore.flowServiceOn(this)
        val svcRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 12) }
            setBackgroundResource(R.drawable.card_bg)
        }
        val svcHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        svcHead.addView(TextView(this).apply {
            text = getString(R.string.flow_service_switch)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        svcHead.addView(Switch(this).apply {
            isChecked = svcOn
            setOnCheckedChangeListener { _, on ->
                FlowStore.setFlowServiceOn(this@AutomationActivity, on)
                if (on) {
                    androidx.core.content.ContextCompat.startForegroundService(
                        this@AutomationActivity,
                        Intent(this@AutomationActivity, FlowService::class.java))
                } else {
                    stopService(Intent(this@AutomationActivity, FlowService::class.java))
                }
                renderFlows()
            }
        })
        svcRow.addView(svcHead)
        val tick = FlowStore.serviceLastTick(this)
        val ago = (System.currentTimeMillis() - tick) / 1000
        svcRow.addView(TextView(this).apply {
            text = when {
                !svcOn -> getString(R.string.flow_service_off)
                tick == 0L || ago > 120 -> getString(R.string.flow_service_stale)
                else -> getString(R.string.flow_service_live, ago.toString())
            }
            textSize = 13f
            setPadding(0, 6, 0, 0)
        })
        container.addView(svcRow)
        val flows = FlowStore.flows(this)
        if (flows.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.flow_empty)
                setPadding(8, 24, 8, 24)
            })
            return
        }
        for (flow in flows) {
            val flowId = flow.optString("id")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, 12) }
                setBackgroundResource(R.drawable.card_bg)
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(TextView(this).apply {
                text = flow.optString("name").ifEmpty { getString(R.string.flow_tab_flows) }
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(Switch(this).apply {
                isChecked = flow.optBoolean("enabled", true)
                setOnCheckedChangeListener { _, on ->
                    FlowStore.setFlowEnabled(this@AutomationActivity, flowId, on)
                }
            })
            row.addView(head)
            // v1.2.0: Essentials DIY routine card, same as rules —
            // sage trigger box | → | first action box. The old
            // one-line summary is replaced by the DIY row.
            val nodes = flow.optJSONArray("nodes")
            var trigType = ""
            var trigParam = ""
            if (nodes != null) {
                for (k in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(k) ?: continue
                    if (n.optString("kind") == "t") {
                        trigType = n.optString("type")
                        trigParam = n.optString("param")
                        break
                    }
                }
            }
            val trigParamText = when {
                trigType == "app_launched" || trigType == "app_closed" ->
                    Automation.appLabel(this, trigParam)
                else -> trigParam
            }
            if (trigType.isNotEmpty()) {
                row.addView(diyRow(
                    FlowCatalog.label(this, "t", trigType),
                    trigParamText, triggerIconRes(trigType),
                    firstFlowActionLabel(nodes), null))
            }
            val scroll = android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(pillButton(R.string.flow_card_run, true).apply {
                setOnClickListener {
                    val f = FlowStore.loadFlow(this@AutomationActivity, flowId)
                    if (f != null) FlowEngine.runFlow(this@AutomationActivity, f)
                }
            })
            buttons.addView(pillButton(R.string.flow_card_edit, false).apply {
                setOnClickListener {
                    startActivity(Intent(this@AutomationActivity, FlowEditorActivity::class.java)
                        .putExtra("flow_id", flowId))
                }
            })
            buttons.addView(pillButton(R.string.flow_card_dup, false).apply {
                setOnClickListener {
                    val copy = JSONObject(flow.toString())
                    copy.put("id", "f" + System.currentTimeMillis().toString())
                    copy.put("name", flow.optString("name") + getString(R.string.flow_dup_suffix))
                    FlowStore.saveFlow(this@AutomationActivity, copy)
                    renderFlows()
                }
            })
            buttons.addView(pillButton(R.string.flow_card_share, false).apply {
                setOnClickListener {
                    // Single-flow share: the stored flow JSON is already
                    // the exact single-flow format the PhoneLink import
                    // detects (nodes + non-empty name), so the receiver
                    // can import it with "PhoneLink থেকে আনো".
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_TEXT, flow.toString())
                    startActivity(Intent.createChooser(send, flow.optString("name")))
                }
            })
            buttons.addView(pillButton(R.string.flow_card_log, false).apply {
                setOnClickListener {
                    val lines = FlowStore.flowLog(this@AutomationActivity, flowId)
                    AlertDialog.Builder(this@AutomationActivity)
                        .setTitle(flow.optString("name"))
                        .setMessage(if (lines.isEmpty()) getString(R.string.flow_log_empty)
                            else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            })
            buttons.addView(pillButton(R.string.flow_card_delete, false).apply {
                setOnClickListener {
                    AlertDialog.Builder(this@AutomationActivity)
                        .setTitle(flow.optString("name"))
                        .setMessage(getString(R.string.flow_delete_confirm, flow.optString("name")))
                        .setPositiveButton(R.string.btn_delete) { _, _ ->
                            FlowStore.deleteFlow(this@AutomationActivity, flowId)
                            renderFlows()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            })
            scroll.addView(buttons)
            row.addView(scroll)
            container.addView(row)
        }
    }

    /** v2.24.0 (Lab merge): Flows tab ⋮ menu — import, .flo
     *  import, export, backup, restore, flow log. Token setup is
     *  deliberately NOT here: it stays in relay settings. */
    private fun showFlowMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(0, MENU_FLOW_IMPORT, 0, getString(R.string.flow_menu_import))
        popup.menu.add(0, MENU_FLOW_FLO, 1, getString(R.string.flow_menu_flo_import))
        popup.menu.add(0, MENU_FLOW_EXPORT, 2, getString(R.string.flow_menu_export))
        popup.menu.add(0, MENU_FLOW_BACKUP, 3, getString(R.string.flow_menu_backup))
        popup.menu.add(0, MENU_FLOW_RESTORE, 4, getString(R.string.flow_menu_restore))
        popup.menu.add(0, MENU_FLOW_LOG, 5, getString(R.string.flow_menu_log))
        popup.menu.add(0, MENU_TEMPLATES, 6, getString(R.string.templates_menu))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FLOW_IMPORT -> startActivity(
                    Intent(this, FlowFlowImportActivity::class.java))
                MENU_FLOW_FLO -> startActivity(
                    Intent(this, FlowFloImportActivity::class.java))
                MENU_FLOW_EXPORT -> {
                    val arr = org.json.JSONArray()
                    for (flow in FlowStore.flows(this)) {
                        arr.put(flow)
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_TEXT, arr.toString())
                    startActivity(Intent.createChooser(send, getString(R.string.flow_menu_export)))
                }
                MENU_FLOW_BACKUP -> {
                    if (!FlowIO.exportBackup(this)) {
                        android.widget.Toast.makeText(this,
                            getString(R.string.flow_export_failed),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                MENU_FLOW_RESTORE -> startActivity(
                    Intent(this, FlowRestoreActivity::class.java))
                MENU_FLOW_LOG -> {
                    val lines = FlowStore.recentLog(this)
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.flow_menu_log))
                        .setMessage(if (lines.isEmpty()) getString(R.string.flow_log_empty)
                            else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                MENU_TEMPLATES -> startActivity(
                    Intent(this, TemplatesActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun render() {
        container.removeAllViews()
        val list = Automation.rules(this)
            .sortedWith(compareBy({ it.optString("cat") }, { it.optString("name") }))
        refreshStatusLine()
        if (list.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.auto_empty)
                setPadding(8, 24, 8, 24)
            })
            return
        }
        var lastCat = "\u0000"
        for (rule in list) {
            val cat = rule.optString("cat")
            if (cat != lastCat) {
                lastCat = cat
                if (cat.isNotEmpty()) {
                    container.addView(TextView(this).apply {
                        text = cat
                        textSize = 15f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(4, 10, 4, 6)
                    })
                }
            }
            val trigList = Automation.ruleTriggers(rule)
            val trig = trigList.firstOrNull()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, 12) }
                setBackgroundResource(R.drawable.card_bg)
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(TextView(this).apply {
                text = rule.optString("name").ifEmpty { getString(R.string.auto_title) }
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(Switch(this).apply {
                isChecked = rule.optBoolean("enabled", true)
                setOnCheckedChangeListener { _, on ->
                    Automation.setEnabled(this@AutomationActivity, rule.optString("id"), on)
                }
            })
            row.addView(head)
            // v1.2.0: Essentials DIY routine card — sage trigger
            // box | arrow chip(s) | action box(es). The old IF /
            // THEN inset boxes and the "N actions" line are gone;
            // the same information lives in the boxes (first
            // trigger/action + " +N", exit box when present).
            val acts = rule.optJSONArray("actions") ?: org.json.JSONArray()
            val nActions = acts.length()
            val firstAct = acts.optJSONObject(0)?.optString("type") ?: ""
            val exitActs = Automation.exitActions(rule)
            // v2.20: a Study Guard first trigger reads as its
            // plain summary (list/app + minutes), never raw param.
            // v2.21: a screen-rest first trigger reads as its
            // plain summary (minutes + warn), never raw param.
            val trigType = trig?.optString("type") ?: ""
            val trigParam = trig?.optString("param") ?: ""
            val ifSummary = if (trigType == "study_guard_continuous" ||
                trigType == "study_guard_daily")
                Automation.studyGuardSummary(
                    this@AutomationActivity, trigType, trigParam)
            else if (trigType == "screen_on_continuous")
                Automation.screenRestSummary(
                    this@AutomationActivity, trigParam)
            else if (trigType == "time_at_days")
                Automation.timeAtDaysSummary(
                    this@AutomationActivity, trigParam)
            else ""
            val trigParamText = when {
                ifSummary.isNotEmpty() -> ifSummary
                trigType == "app_launched" || trigType == "app_closed" ->
                    Automation.appLabel(this@AutomationActivity, trigParam)
                trigParam.isNotBlank() -> trigParam
                else -> ""
            }
            // v2.20: a FocusLock first step reads as its plain
            // summary ("YouTube ব্লক 60 মিনিট"), never the raw param.
            // v2.21: an AutoTap first step reads as its plain
            // summary, never the raw param.
            // v2.22: a Redial first step reads as its plain
            // summary, never the raw param.
            val firstSummary = if (firstAct == "focuslock_control")
                Automation.focusLockSummary(this@AutomationActivity,
                    acts.optJSONObject(0)?.optString("param") ?: "")
            else if (firstAct == "autotap_control")
                Automation.autoTapSummary(this@AutomationActivity,
                    acts.optJSONObject(0)?.optString("param") ?: "")
            else if (firstAct == "redial_control")
                Automation.redialSummary(this@AutomationActivity,
                    acts.optJSONObject(0)?.optString("param") ?: "")
            else ""
            val entryLabel = AutoCatalog.label(
                this@AutomationActivity, "a", firstAct) +
                (if (firstSummary.isNotEmpty())
                    " — " + firstSummary else "") +
                (if (nActions > 1) " +${nActions - 1}" else "")
            val trigLabel = AutoCatalog.label(
                this@AutomationActivity, "t", trigType) +
                (if (trigList.size > 1) " +${trigList.size - 1}" else "")
            val exitLabel = if (exitActs.length() > 0)
                firstActionLabel(exitActs, "rule") else null
            row.addView(diyRow(trigLabel, trigParamText,
                triggerIconRes(trigType), entryLabel, exitLabel))
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 8, 0, 0) }
            }
            // v2.17.3: compact pill buttons in the new system
            // (14dp radius, ~36dp tall) instead of the old bg_pill.
            fun pillButton(labelRes: Int, mint: Boolean) = Button(this).apply {
                text = getString(labelRes)
                textSize = 12.5f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                setBackgroundResource(
                    if (mint) R.drawable.bg_action_pill
                    else R.drawable.bg_action_ghost)
                setPadding(40, 30, 40, 30)
                setTextColor(
                    if (mint) themeAccentColor() else themeOnSurface())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(8, 0, 0, 0) }
            }
            buttons.addView(pillButton(R.string.auto_test, true).apply {
                setOnClickListener {
                    val data = JSONObject()
                        .put("title", rule.optString("name")).put("body", "test")
                    Automation.runActions(this@AutomationActivity, rule, data, 0)
                }
            })
            buttons.addView(pillButton(R.string.btn_edit, false).apply {
                setOnClickListener {
                    startActivity(Intent(this@AutomationActivity, RuleEditorActivity::class.java)
                        .putExtra("rule_id", rule.optString("id")))
                }
            })
            buttons.addView(pillButton(R.string.flow_card_dup, false).apply {
                setOnClickListener {
                    // Same duplicate as the flow cards: deep-copy,
                    // new id, " (copy)" suffix, save, re-render.
                    val copy = JSONObject(rule.toString())
                    copy.put("id", "r" + System.currentTimeMillis().toString())
                    copy.put("name", rule.optString("name") + getString(R.string.flow_dup_suffix))
                    Automation.upsert(this@AutomationActivity, copy)
                    render()
                }
            })
            buttons.addView(pillButton(R.string.flow_card_share, false).apply {
                setOnClickListener {
                    // Single-rule share, mirroring the flow share
                    // pill — but as a one-element JSON array: the
                    // rule import (Automation.importJson) only
                    // accepts arrays, so a bare rule object would
                    // fail to import back. This round-trips.
                    val arr = org.json.JSONArray()
                        .put(JSONObject(rule.toString()))
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_TEXT, arr.toString())
                    startActivity(Intent.createChooser(send, rule.optString("name")))
                }
            })
            buttons.addView(pillButton(R.string.flow_card_log, false).apply {
                setOnClickListener {
                    // Same per-item log dialog as the flow cards,
                    // filtered to lines that mention this rule —
                    // rule runs log "▶ <name> (<event>)".
                    val name = rule.optString("name")
                    val lines = if (name.isEmpty()) emptyList()
                    else Automation.recentLog(this@AutomationActivity)
                        .filter { it.contains(name) }
                    AlertDialog.Builder(this@AutomationActivity)
                        .setTitle(rule.optString("name"))
                        .setMessage(if (lines.isEmpty()) getString(R.string.flow_log_empty)
                            else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            })
            buttons.addView(pillButton(R.string.btn_delete, false).apply {
                setOnClickListener {
                    Automation.delete(this@AutomationActivity, rule.optString("id"))
                    render()
                }
            })
            row.addView(buttons)
            container.addView(row)
        }
    }
}
