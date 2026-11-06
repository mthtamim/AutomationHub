package com.mthtamim.phonelinkflows

import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.autotap.app.RunnerService
import com.autotap.app.Store
import com.autotap.app.TapAccessibilityService
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.concurrent.thread
import org.json.JSONObject

class MainActivity : LocalizedActivity() {

    // v2.17.2: info lines refreshed by refreshStatus().
    private lateinit var tvPinInfo: TextView
    private lateinit var tvAdminInfo: TextView
    private lateinit var tvGeoInfo: TextView
    private lateinit var tvFocusGuardInfo: TextView
    private lateinit var tvFocusAppsInfo: TextView
    private lateinit var btnFocusStart: Button
    private lateinit var btnFocusEnd: Button
    private var focusGuardUpdating = false
    private lateinit var tvStats: TextView

    private lateinit var pageHome: View
    private lateinit var pageSettings: View
    private var settingsOpen = false

    // Smoothness (2026-11-06): the app version never changes while
    // the process lives, but refreshSettingsHub() called
    // getPackageInfo (a Binder call) on EVERY refresh. Resolved once.
    private val appVersionName: String by lazy {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
    }

    // v2.17 redesign views
    private lateinit var tvConnPill: TextView
    private lateinit var tvDashDevice: TextView
    private lateinit var tvDashDeviceSub: TextView
    private lateinit var tvDashWifi: TextView
    private lateinit var pbDashBattery: android.widget.ProgressBar
    private lateinit var tvDashBatteryPct: TextView
    // v2.21 AutoTap dashboard card views
    private lateinit var tvDashAutoTapStatus: TextView
    private lateinit var etDashAutoTapScript: EditText
    private lateinit var btnDashAutoTapStart: Button
    private lateinit var btnDashAutoTapStop: Button
    private lateinit var btnDashAutoTapOpen: Button
    // v2.22 Redial dashboard card views
    private lateinit var tvDashRedialStatus: TextView
    private lateinit var etDashRedialNumber: EditText
    private lateinit var etDashRedialGap: EditText
    private lateinit var etDashRedialMax: EditText
    private lateinit var btnDashRedialSimDef: Button
    private lateinit var btnDashRedialSim1: Button
    private lateinit var btnDashRedialSim2: Button
    private lateinit var btnDashRedialStart: Button
    private lateinit var btnDashRedialStop: Button
    private var dashRedialSim: String = "default"
    // v2.23 Redial dashboard card views
    private lateinit var etDashRedialRing: EditText
    private lateinit var btnDashRedialSpeaker: Button
    private lateinit var btnDashRedialOnanswerStop: Button
    private lateinit var btnDashRedialOnanswerResume: Button
    private var dashRedialOnanswer: String = "stop"
    private var dashRedialSpeaker: Boolean = false
    private lateinit var tvSetPill: TextView
    private lateinit var tvHubPermsSub: TextView
    private lateinit var tvHubSecSub: TextView
    private lateinit var tvHubDataSub: TextView
    private lateinit var tvHubDiag: TextView
    // v2.17.4: state lines for accesses granted in Permissions, the
    // Automation Studio info line and the General version line
    private lateinit var tvA11yState: TextView
    private lateinit var tvGeneralDiag: TextView
    // v1.2.0: the pill bottom bar (nav_pill_bar) replaces the old
    // BottomNavigationView. navSelectedId is the single source of
    // truth for the selected tab; selectNavTab() paints the bar.
    private var navSelectedId: Int = R.id.navHome


    // v2.11 voice commands (system speech recognizer)


    // ------------- v2.4 launchers -------------
    private val cfgExportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            thread {
                var ok = false
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(SettingsTransfer.export(applicationContext)
                            .toByteArray(Charsets.UTF_8))
                    }
                    ok = true
                } catch (_: Exception) { }
                Handler(Looper.getMainLooper()).post {
                    toast(getString(if (ok) R.string.toast_cfg_exported
                        else R.string.toast_cfg_failed))
                }
            }
        }
    }

    private val cfgImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            thread {
                var ok = false
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.readText().orEmpty()
                    ok = SettingsTransfer.import(applicationContext, text)
                } catch (_: Exception) { }
                Handler(Looper.getMainLooper()).post {
                    toast(getString(if (ok) R.string.toast_cfg_imported
                        else R.string.toast_cfg_failed))
                    if (ok) recreate()
                }
            }
        }
    }


    // ----- PhoneLink import (E): one entry for Rules + Flows from
    // PhoneLink. Picks one or more JSON files and detects each:
    // full backup (Rules, merged), flows backup (Flows, merged by
    // id), a single flow JSON, or a bare rules array. Hub settings
    // are deliberately NOT overwritten with PhoneLink's cfg.
    private val phonelinkImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        var rulesAdded = 0
        var flowsAdded = 0
        var skipped = 0
        for (uri in uris) {
            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                val r = importPhonelinkText(text)
                if (r == null) skipped++
                else { rulesAdded += r.first; flowsAdded += r.second }
            } catch (_: Exception) { skipped++ }
        }
        toast(getString(R.string.import_phonelink_done,
            rulesAdded, flowsAdded, skipped))
        refreshStatus()
    }

    private fun importPhonelinkText(text: String): Pair<Int, Int>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("[")) {
            val n = Automation.importJson(this, t)
            return if (n < 0) null else Pair(n, 0)
        }
        return try {
            val root = JSONObject(t)
            when {
                root.optString("kind") == "full-backup" -> {
                    val rulesStr = root.optJSONObject("auto")
                        ?.optString("rules", "") ?: ""
                    if (rulesStr.isEmpty()) return null
                    val n = Automation.importJson(this, rulesStr)
                    if (n < 0) null else Pair(n, 0)
                }
                root.optInt("formatVersion", -1) == 1 && root.has("flows") -> {
                    val arr = root.optJSONArray("flows") ?: return null
                    var added = 0
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i) ?: continue
                        if (f.optString("id").isEmpty()
                            || f.optString("name").isEmpty()) continue
                        FlowStore.saveFlow(this, f)
                        added++
                    }
                    if (added == 0) null else Pair(0, added)
                }
                root.has("nodes") && root.optString("name").isNotEmpty() -> {
                    var id = root.optString("id")
                    if (id.isEmpty() || FlowStore.loadFlow(this, id) != null) {
                        id = "f" + System.currentTimeMillis().toString()
                        root.put("id", id)
                    }
                    FlowStore.saveFlow(this, root)
                    Pair(0, 1)
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v2.15.1: repair any wrongly-typed values a restore left
        // behind before anything reads them with a typed getter.
        SettingsTransfer.heal(this)


        // App lock gate.
        if (Config.appLockOn(this) && !SessionState.unlocked) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
            return
        }


        setContentView(R.layout.activity_main)

        pageHome = findViewById(R.id.pageHome)
        pageSettings = findViewById(R.id.pageSettings)
        // (pill bottom bar is wired in setupNavBar() below,
        //  in the navigation block; selection is re-painted
        //  in onResume too)

        tvConnPill = findViewById(R.id.tvConnPill)
        tvDashDevice = findViewById(R.id.tvDashDevice)
        tvDashDeviceSub = findViewById(R.id.tvDashDeviceSub)
        tvDashWifi = findViewById(R.id.tvDashWifi)
        pbDashBattery = findViewById(R.id.pbDashBattery)
        tvDashBatteryPct = findViewById(R.id.tvDashBatteryPct)
        tvDashAutoTapStatus = findViewById(R.id.tvDashAutoTapStatus)
        etDashAutoTapScript = findViewById(R.id.etDashAutoTapScript)
        btnDashAutoTapStart = findViewById(R.id.btnDashAutoTapStart)
        btnDashAutoTapStop = findViewById(R.id.btnDashAutoTapStop)
        btnDashAutoTapOpen = findViewById(R.id.btnDashAutoTapOpen)
        tvDashRedialStatus = findViewById(R.id.tvDashRedialStatus)
        etDashRedialNumber = findViewById(R.id.etDashRedialNumber)
        etDashRedialGap = findViewById(R.id.etDashRedialGap)
        etDashRedialMax = findViewById(R.id.etDashRedialMax)
        btnDashRedialSimDef = findViewById(R.id.btnDashRedialSimDef)
        btnDashRedialSim1 = findViewById(R.id.btnDashRedialSim1)
        btnDashRedialSim2 = findViewById(R.id.btnDashRedialSim2)
        btnDashRedialStart = findViewById(R.id.btnDashRedialStart)
        btnDashRedialStop = findViewById(R.id.btnDashRedialStop)
        etDashRedialRing = findViewById(R.id.etDashRedialRing)
        btnDashRedialSpeaker = findViewById(R.id.btnDashRedialSpeaker)
        btnDashRedialOnanswerStop =
            findViewById(R.id.btnDashRedialOnanswerStop)
        btnDashRedialOnanswerResume =
            findViewById(R.id.btnDashRedialOnanswerResume)
        tvSetPill = findViewById(R.id.tvSetPill)
        tvHubPermsSub = findViewById(R.id.tvHubPermsSub)
        tvHubSecSub = findViewById(R.id.tvHubSecSub)
        tvHubDataSub = findViewById(R.id.tvHubDataSub)
        tvHubDiag = findViewById(R.id.tvHubDiag)

        tvPinInfo = findViewById(R.id.tvPinInfo)
        tvAdminInfo = findViewById(R.id.tvAdminInfo)
        tvGeoInfo = findViewById(R.id.tvGeoInfo)
        tvFocusGuardInfo = findViewById(R.id.tvFocusGuardInfo)
        tvFocusAppsInfo = findViewById(R.id.tvFocusAppsInfo)
        btnFocusStart = findViewById(R.id.btnFocusStart)
        btnFocusEnd = findViewById(R.id.btnFocusEnd)
        tvStats = findViewById(R.id.tvStats)
        tvA11yState = findViewById(R.id.tvA11yState)
        tvGeneralDiag = findViewById(R.id.tvGeneralDiag)


        // ----- pill bottom bar (v1.2.0): same three destinations
        //  as the old BottomNavigationView. DIY opens the
        //  Automation activity (selection stays where it was,
        //  exactly like the old listener returning false); the
        //  bar's + button opens it too.
        setupNavBar()
        val validTabs = setOf(R.id.navHome, R.id.navSettings)
        val startTab = savedInstanceState?.getInt("tab", R.id.navHome)
            ?: R.id.navHome
        if (startTab in validTabs && startTab != R.id.navHome) {
            selectNavTab(startTab)
        } else {
            showPage(R.id.navHome)
        }
        handleTabExtra(intent)

        // ----- permissions entry points -----

        // ----- app lock -----
        val swAppLock = findViewById<CompoundButton>(R.id.swAppLock)
        swAppLock.isChecked = Config.appLockOn(this)
        swAppLock.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Config.pinHash(this).isEmpty()) {
                    showSetPinDialog { ok -> if (!ok) swAppLock.isChecked = false }
                } else {
                    Config.setAppLockOn(this, true)
                }
            } else {
                Config.setAppLockOn(this, false)
            }
        }
        findViewById<Button>(R.id.btnChangePin).setOnClickListener {
            showSetPinDialog { }
        }

        // ----- v2.18 app locker (managed on its own screen) -----
        findViewById<Button>(R.id.btnAppLocker).setOnClickListener {
            startActivity(Intent(this, AppLockerActivity::class.java))
        }

        // ----- more features (v2.1) -----
        bindToggle(R.id.swGeo, "geo_on", Config.geoOn(this))

        // ----- Focus Guard card (new 2026-11-06): the on/off switch
        // lives in the dedicated card now (same ids). It turns a
        // saved schedule off (or back on); with no saved packages
        // it cannot turn on and says so. Setup (apps, window,
        // presets) is wired just below.
        findViewById<CompoundButton>(R.id.swFocusGuard).apply {
            isChecked = Config.focusOn(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                if (focusGuardUpdating) return@setOnCheckedChangeListener
                if (checked && Config.focusPkgs(this@MainActivity).isEmpty()) {
                    focusGuardUpdating = true
                    isChecked = false
                    focusGuardUpdating = false
                    toast(getString(R.string.focus_guard_no_schedule))
                    refreshFocusGuard()
                    return@setOnCheckedChangeListener
                }
                Config.setFocusOn(this@MainActivity, checked)
                refreshFocusGuard()
            }
        }
        refreshFocusGuard()

        // ----- Focus Guard setup card (new 2026-11-06): app picker,
        // time window and preset chips. Saving applies at once —
        // the picker enables the guard when apps are saved, and
        // refreshFocusGuard() redraws the summary below.
        findViewById<Button>(R.id.btnFocusApps).setOnClickListener {
            startActivity(Intent(this, FocusGuardPickerActivity::class.java))
        }
        findViewById<Button>(R.id.btnFocusStart).setOnClickListener {
            showFocusTimePicker(isStart = true)
        }
        findViewById<Button>(R.id.btnFocusEnd).setOnClickListener {
            showFocusTimePicker(isStart = false)
        }
        findViewById<Button>(R.id.chipFocusStudy).setOnClickListener {
            applyFocusPreset(18 * 60, 22 * 60)
        }
        findViewById<Button>(R.id.chipFocusNight).setOnClickListener {
            applyFocusPreset(22 * 60, 6 * 60)
        }
        findViewById<Button>(R.id.chipFocusWork).setOnClickListener {
            applyFocusPreset(9 * 60, 17 * 60)
        }

        // ----- v2.2 toggles & actions -----


        findViewById<Button>(R.id.btnSetGeo).setOnClickListener {
            toast(getString(R.string.toast_geo_fetching))
            LocationHelper.fetchCoords(this,
                { lat, lng ->
                    Config.setGeoCenter(this, lat, lng)
                    runOnUiThread { refreshStatus(); toast(getString(R.string.toast_geo_set)) }
                },
                { runOnUiThread {
                    // Tell "location switched off" apart from "no fix
                    // yet": only the first one gets a settings offer.
                    if (!LocationHelper.anyProviderEnabled(this)) {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.geo_no_provider_title))
                            .setMessage(getString(R.string.geo_no_provider_msg))
                            .setPositiveButton(getString(R.string.geo_open_settings)) { _, _ ->
                                try {
                                    startActivity(Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (_: Exception) { }
                            }
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show()
                    } else {
                        toast(getString(R.string.toast_geo_failed))
                    }
                } })
        }
        // ----- v2.17.4 settings reorganization wiring -----
        // Usage Access is granted ONLY in Permissions & Access now;
        // the Smart panel keeps a state line (refreshStatus) + link.
        findViewById<Button>(R.id.btnSmartPermsLink).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        // Same for Device Admin / Accessibility from Security.
        findViewById<Button>(R.id.btnSecPermsLink).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        // automation_on finally gets its master toggle (was an
        // orphan key changeable only via settings import).
        bindToggle(R.id.swAutoMaster, "automation_on", Config.automationOn(this))
        // Quick Settings tile (new 2026-11-06): Android 13+ can ask
        // the system to add the AutomationHub tile; older versions
        // (and a declined request) fall back to the manual QS Edit
        // route, which the toast names.
        // NOTE: the API lives on StatusBarManager, NOT TileService:
        // StatusBarManager.requestAddTileService(component, label,
        // icon, executor, callback). There is no
        // TileService.requestAddTile and no TILE_ADD_REQUEST_OK —
        // success is TILE_ADD_REQUEST_RESULT_TILE_ADDED (2) or
        // TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED (1).
        findViewById<Button>(R.id.btnAddQsTile).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sbm = getSystemService(StatusBarManager::class.java)
                if (sbm != null) {
                    sbm.requestAddTileService(
                        ComponentName(this, AutomationTileService::class.java),
                        "AutomationHub",
                        Icon.createWithResource(this, R.drawable.ic_nav_auto),
                        mainExecutor,
                        java.util.function.Consumer<Int> { result ->
                            toast(getString(if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                                R.string.tile_requested else R.string.tile_add_manually))
                        }
                    )
                } else {
                    toast(getString(R.string.tile_add_manually))
                }
            } else {
                toast(getString(R.string.tile_add_manually))
            }
        }
        // geo_radius finally gets a UI (was stuck at 200 m).
        findViewById<Button>(R.id.btnGeoRadius).setOnClickListener {
            val options = intArrayOf(100, 200, 300, 500, 1000)
            val labels = options.map { "$it m" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_geo_radius))
                .setItems(labels) { _, which ->
                    Config.setGeoRadius(this, options[which]); refreshStatus()
                }
                .show()
        }
        // ----- data & statistics (v2.1) -----


        // ----- v2.11 remote additions -----


        findViewById<Button>(R.id.btnAccent).setOnClickListener {
            val names = arrayOf("emerald", "blue", "purple", "amber", "rose")
            val labels = arrayOf(
                getString(R.string.accent_emerald), getString(R.string.accent_blue),
                getString(R.string.accent_purple), getString(R.string.accent_amber),
                getString(R.string.accent_rose))
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_accent))
                .setItems(labels) { _, w ->
                    Config.setAccent(this, names[w]); recreate()
                }
                .show()
        }
        // v2.25.0: optional app theme picker. A non-default theme carries
        // its own accent (see LocalizedActivity); the accent picker above
        // still applies when Emerald Dark is selected.
        findViewById<Button>(R.id.btnAppTheme).setOnClickListener {
            showThemePicker()
        }
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            toast(getString(R.string.update_checking))
            Update.check(this, true) { newer, url ->
                if (newer != null) {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.update_found, newer))
                        .setMessage(getString(R.string.update_found_msg))
                        .setPositiveButton(getString(R.string.btn_open)) { _, _ ->
                            if (!url.isNullOrEmpty()) try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) { }
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                } else toast(getString(R.string.update_latest))
            }
        }
        val swAutoUpd = findViewById<CompoundButton>(R.id.swAutoUpdate)
        swAutoUpd.isChecked = Config.autoUpdate(this)
        swAutoUpd.setOnCheckedChangeListener { _, checked ->
            Config.setAutoUpdate(this, checked)
        }

        // v2.11: once-a-day silent update check
        if (System.currentTimeMillis() - Config.updateLastCheck(this) >
            24 * 3600_000L) Update.check(this, false)

        wireV24()
        wireV217()

        // ----- v2.3 wiring -----

        // ----- v2.17 dashboard tiles -----


        // ----- v2.21 AutoTap dashboard card (bottom of Dashboard):
        // the merged engine, controlled directly — no broadcast,
        // no separate app. The name is remembered in prefs;
        // Start saves it first.
        btnDashAutoTapStart.setOnClickListener {
            val name = etDashAutoTapScript.text.toString().trim()
            if (name.isEmpty()) {
                toast(getString(R.string.dash_autotap_no_name))
                return@setOnClickListener
            }
            Config.setAutoTapScript(this, name)
            runAutoTapDirect(true, name)
        }
        btnDashAutoTapStop.setOnClickListener {
            runAutoTapDirect(false, "")
        }
        btnDashAutoTapOpen.setOnClickListener {
            startActivity(Intent(this,
                com.autotap.app.MainActivity::class.java))
        }
        btnDashRedialSimDef.setOnClickListener {
            dashRedialSim = "default"
            refreshRedialSimButtons()
        }
        btnDashRedialSim1.setOnClickListener {
            dashRedialSim = "sim1"
            refreshRedialSimButtons()
        }
        btnDashRedialSim2.setOnClickListener {
            dashRedialSim = "sim2"
            refreshRedialSimButtons()
        }
        btnDashRedialSpeaker.setOnClickListener {
            dashRedialSpeaker = !dashRedialSpeaker
            refreshRedialSpeakerButton()
        }
        btnDashRedialOnanswerStop.setOnClickListener {
            dashRedialOnanswer = "stop"
            refreshRedialOnanswerButtons()
        }
        btnDashRedialOnanswerResume.setOnClickListener {
            dashRedialOnanswer = "resume"
            refreshRedialOnanswerButtons()
        }
        btnDashRedialStart.setOnClickListener {
            val number = etDashRedialNumber.text.toString().trim()
            if (number.isEmpty()) {
                toast(getString(R.string.dash_redial_no_number))
                return@setOnClickListener
            }
            val gap = etDashRedialGap.text.toString().trim()
                .toIntOrNull() ?: 30
            val max = etDashRedialMax.text.toString().trim()
                .toIntOrNull() ?: 20
            val ring = etDashRedialRing.text.toString().trim()
                .toIntOrNull() ?: 0
            when (Redial.start(this, number, dashRedialSim, gap, max,
                ring, dashRedialOnanswer, dashRedialSpeaker)) {
                Redial.OK -> {
                    toast(getString(R.string.dash_redial_started, number))
                    refreshRedialCard()
                }
                Redial.ERR_NUMBER ->
                    toast(getString(R.string.dash_redial_no_number))
                else ->
                    toast(getString(R.string.dash_redial_no_perm))
            }
        }
        btnDashRedialStop.setOnClickListener {
            Redial.stop(this, "user")
            toast(getString(R.string.dash_redial_stopped))
            refreshRedialCard()
        }


        // ----- security card -----
        bindToggle(R.id.swIntruder, "intruder_on", Config.intruderOn(this))
        findViewById<Button>(R.id.btnSetPin).setOnClickListener { showSetPinDialog { } }
        // v2.17.4: the Device Admin grant button moved to
        // Permissions & Access — the Security panel keeps the
        // tvAdminInfo state line and the btnSecPermsLink link.

        // ----- appearance: language & theme -----
        val toggleLang = findViewById<MaterialButtonToggleGroup>(R.id.toggleLang)
        toggleLang.check(if (Config.language(this) == "en") R.id.btnLangEn else R.id.btnLangBn)
        toggleLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = if (checkedId == R.id.btnLangEn) "en" else "bn"
            if (target != Config.language(this)) {
                Config.setLanguage(this, target)
                recreate()
            }
        }
        val toggleTheme = findViewById<MaterialButtonToggleGroup>(R.id.toggleTheme)
        toggleTheme.check(
            when (Config.themeMode(this)) {
                "light" -> R.id.btnThemeLight
                "dark" -> R.id.btnThemeDark
                else -> R.id.btnThemeSystem
            }
        )
        toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = when (checkedId) {
                R.id.btnThemeLight -> "light"
                R.id.btnThemeDark -> "dark"
                else -> "system"
            }
            if (target != Config.themeMode(this)) {
                Config.setThemeMode(this, target)
                AppCompatDelegate.setDefaultNightMode(
                    when (target) {
                        "light" -> AppCompatDelegate.MODE_NIGHT_NO
                        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }

        refreshStatus()

        // First launch ever: if anything is missing, open the permission
        // wizard once. The flag is set when shown, so it never nags again.
        if (!Config.wizardShown(this) && PermChecks.missingCount(this) > 0) {
            Config.setWizardShown(this)
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", navSelectedId)
    }

    override fun onResume() {
        super.onResume()
        // v1.2.0: re-paint the pill bar selection (the old
        // applyGlassNavTint call is gone with the BottomNavigationView).
        selectNavTab(navSelectedId)
        refreshStatus()
        Automation.checkClipboard(this)
        // v2.4 NFC tag trigger (foreground dispatch).
        try {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
            if (adapter != null && adapter.isEnabled) {
                val pi = android.app.PendingIntent.getActivity(this, 0,
                    Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                adapter.enableForegroundDispatch(this, pi, null, null)
            }
        } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        try {
            android.nfc.NfcAdapter.getDefaultAdapter(this)
                ?.disableForegroundDispatch(this)
        } catch (_: Exception) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTabExtra(intent)
        when (intent.action) {
            android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED,
            android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED,
            android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                Automation.onEvent(this, "nfc_tag", org.json.JSONObject())
                toast(getString(R.string.toast_nfc))
            }
        }
    }

    // ------------- v2.4 wiring -------------

    // ------------- v2.4.1 sub-screens -------------
    // Everything that used to pile onto Home/Settings now opens as a
    // full-screen sub-screen (overlay) with its own title and back.

    // v2.17.4: panelGroup abolished — its items live in the Data
    // (backup trio, charts) panel; the Relay panel is gone.
    // AutomationHub: panelForward (forwarding toggles) and panelFamily
    // (lost-mode / family check-in) were removed with forwarding.
    private val subGroups = listOf(
        R.id.panelGeneral, R.id.panelSmart,
        R.id.panelSecurity, R.id.panelData
    )

    private fun openSubScreen(groupId: Int, titleRes: Int) {
        findViewById<View>(R.id.subRoot).visibility = View.VISIBLE
        findViewById<View>(R.id.bottomNav).visibility = View.GONE
        findViewById<View>(R.id.toolbar).visibility = View.GONE
        findViewById<TextView>(R.id.tvSubTitle).setText(titleRes)
        for (id in subGroups) {
            findViewById<View>(id).visibility =
                if (id == groupId) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.subScroll).scrollTo(0, 0)
    }

    private fun subScreenOpen(): Boolean =
        findViewById<View>(R.id.subRoot).visibility == View.VISIBLE

    private fun closeSubScreen() {
        findViewById<View>(R.id.subRoot).visibility = View.GONE
        findViewById<View>(R.id.bottomNav).visibility = View.VISIBLE
        findViewById<View>(R.id.toolbar).visibility = View.VISIBLE
    }

    /** v2.17: Settings is a bottom-nav page now, not an overlay. */
    private fun openSettings() {
        selectNavTab(R.id.navSettings)
    }

    private fun closeSettings() {
        if (settingsOpen) selectNavTab(R.id.navHome)
    }


    private fun wireV24() {
        // --- sub-screen navigation (v2.4.1) ---
        findViewById<View>(R.id.btnSubBack).setOnClickListener { closeSubScreen() }
        // v2.17.3: no back arrow on the Settings tab page any more
        // (btnSettingsBack removed from page_settings.xml) — Settings
        // is a top-level tab; system Back handles navigation.
        // v2.17.2: the settings hub tiles are the only entry points
        // (Perms/Security/Data/General/Smart are wired in wireV217)
        // -- no legacy menu, no dashboard shortcut boxes: every
        // panel opens from one place.
        findViewById<View>(R.id.tileHubGeneral).setOnClickListener {
            openSubScreen(R.id.panelGeneral, R.string.cat_general) }
        findViewById<View>(R.id.tileHubSmart).setOnClickListener {
            openSubScreen(R.id.panelSmart, R.string.cat_smart) }

        // v2.17.3 back map (Back never falls out of the app from a
        // panel or a non-Dashboard tab):
        //   sub-screen open -> close it (back to the Settings hub
        //   that opened it); config/pairing page open -> close it
        //   (Settings hub); any other tab -> Dashboard; Dashboard
        //   with nothing open -> exit.
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (subScreenOpen()) {
                        closeSubScreen()
                    } else if (navSelectedId != R.id.navHome) {
                        selectNavTab(R.id.navHome)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })


        // --- security panel ---
        // v2.17.4: the Accessibility grant button moved to
        // Permissions & Access — Security keeps the tvA11yState
        // state line and the btnSecPermsLink link (wired in onCreate).

        // --- former group panel items (now in Data) ---


        // v2.17.4: the Shizuku connect/test button moved to
        // Permissions & Access (its state line lives there too).

        findViewById<Button>(R.id.btnExportConfig).setOnClickListener {
            cfgExportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "automationhub-backup.json"))
        }
        findViewById<Button>(R.id.btnImportConfig).setOnClickListener {
            cfgImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        findViewById<Button>(R.id.btnImportPhonelink).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_phonelink_title))
                .setMessage(getString(R.string.import_phonelink_msg))
                .setPositiveButton(getString(R.string.import_phonelink_pick)) { _, _ ->
                    phonelinkImportLauncher.launch(
                        arrayOf("application/json", "text/plain"))
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
        findViewById<Button>(R.id.btnConfigCode).setOnClickListener {
            val input = EditText(this).apply {
                setText(SettingsTransfer.export(this@MainActivity))
                setSelection(0)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_config_code))
                .setView(input)
                .setPositiveButton(getString(R.string.menu_copy)) { _, _ ->
                    copyToClipboard(input.text.toString()) }
                .setNeutralButton(getString(R.string.btn_import_config)) { _, _ ->
                    val ok = SettingsTransfer.import(this, input.text.toString())
                    toast(getString(if (ok) R.string.toast_cfg_imported
                        else R.string.toast_cfg_failed))
                    if (ok) recreate()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        // --- data panel ---


        // --- home additions ---


    }

    /** v1.2.0: wire the Essentials pill bottom bar. Home and
     *  Settings switch the dashboard page; DIY opens the
     *  Automation activity (selection stays, as before); the
     *  bar's + button opens the Automation activity too. */
    private fun setupNavBar() {
        val bar = findViewById<View>(R.id.bottomNav)
        bar.findViewById<View>(R.id.navTabHome).setOnClickListener {
            if (navSelectedId != R.id.navHome) {
                selectNavTab(R.id.navHome)
                showPage(R.id.navHome)
            }
        }
        bar.findViewById<View>(R.id.navTabAuto).setOnClickListener {
            startActivity(Intent(this, AutomationActivity::class.java))
        }
        bar.findViewById<View>(R.id.navTabSettings).setOnClickListener {
            if (navSelectedId != R.id.navSettings) {
                selectNavTab(R.id.navSettings)
                showPage(R.id.navSettings)
            }
        }
        bar.findViewById<View>(R.id.navAdd).setOnClickListener {
            startActivity(Intent(this, AutomationActivity::class.java))
        }
        selectNavTab(navSelectedId)
    }

    /** v1.2.0: paint the pill bar — selected tab gets the dark
     *  pill with light text+icon; the others stay sage with dark
     *  text+icon. Fixed sage tokens (ess_bar_*) — not theme
     *  colours — so the bar looks the same in all 8 themes. */
    private fun selectNavTab(id: Int) {
        navSelectedId = id
        val bar = findViewById<View>(R.id.bottomNav)
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

    private fun showPage(itemId: Int) {
        // v1.2.2: Home <-> Settings is a MaterialSharedAxis (X,
        // 300 ms, emphasized easing) — the page slides/fades along
        // the axis in one motion. Skipped on the very first call
        // (onCreate, before the first layout) so the first frame
        // is not animated from an unlaid-out state.
        val change = {
            settingsOpen = itemId == R.id.navSettings
            pageHome.visibility = if (itemId == R.id.navHome) View.VISIBLE else View.GONE
            pageSettings.visibility = if (itemId == R.id.navSettings) View.VISIBLE else View.GONE
            closeSubScreen()
        }
        if (pageHome.isLaidOut) {
            PLMotion.beginSharedAxisX(pageHome.parent as android.view.ViewGroup,
                itemId == R.id.navSettings, change)
            // v1.2.3: the incoming page slides+fades in directly —
            // the scene transition alone did not show on 1.2.2, so
            // the visible motion must not depend on it. The
            // isLaidOut guard (first call, before the first layout)
            // and the setupNavBar same-tab guard mean this only
            // runs on a real user-initiated switch.
            PLMotion.slideFadeIn(
                if (itemId == R.id.navSettings) pageSettings else pageHome,
                itemId == R.id.navSettings)
        } else {
            change()
        }
    }


    /** AutomationHub: only home/settings tabs exist now. */
    private fun handleTabExtra(intent: Intent?) {
        when (intent?.getStringExtra("tab")) {
            "home" -> selectNavTab(R.id.navHome)
            "settings" -> selectNavTab(R.id.navSettings)
        }
    }


    private fun bindToggle(id: Int, key: String, initial: Boolean) {
        val sw = findViewById<CompoundButton>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> Config.setToggle(this, key, checked) }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AutomationHub", text))
        toast(getString(R.string.toast_copied))
    }


    // ---------------- v2.3 dialogs ----------------


    private fun showSetPinDialog(onDone: (Boolean) -> Unit) {
        val pin1 = EditText(this).apply {
            hint = getString(R.string.hint_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val pin2 = EditText(this).apply {
            hint = getString(R.string.hint_pin_again)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(pin1); addView(pin2)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lock_set_title))
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val a = pin1.text.toString(); val b = pin2.text.toString()
                if (a.length >= 4 && a == b) {
                    Config.setPinHash(this, Crypto.pinHash(a))
                    Config.setAppLockOn(this, true)
                    toast(getString(R.string.toast_pin_set))
                    onDone(true)
                } else {
                    toast(getString(R.string.toast_pin_mismatch))
                    onDone(false)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ -> onDone(false) }
            .show()
    }

    /** AutomationHub: rules/flows/automation-log counts (no relay stats). */
    // 2026-11-06: takes the pre-parsed lists — refreshStatus
    // parses rules/flows/log ONCE per refresh and passes them
    // down, instead of every helper re-parsing them.
    private fun buildStatsText(
        rules: List<JSONObject>, flows: List<JSONObject>, logs: List<String>
    ): String {
        return getString(R.string.stats_rules_flows, rules.size, flows.size) +
            " • " + getString(R.string.stats_log_lines, logs.size)
    }


    private fun refreshStatus() {
        // AutomationHub: no relay — the header pill shows the local
        // automation state (rules/flows counts), the hub subtitles
        // show local device/automation status.
        // 2026-11-06: parse rules/flows/log ONCE per refresh and
        // pass them down — buildStatsText and refreshSettingsHub
        // used to re-parse the same JSON a second and third time.
        val rules = Automation.rules(this)
        val flows = FlowStore.flows(this)
        val logs = Automation.recentLog(this)
        val autoOn = Config.automationOn(this)
        tvConnPill.text = if (autoOn) {
            getString(R.string.conn_automation_on, rules.size, flows.size)
        } else {
            getString(R.string.conn_automation_off)
        }
        tvConnPill.setTextColor(ContextCompat.getColor(this,
            if (autoOn) R.color.status_ok else R.color.status_bad))
        // Smart-control / data info lines
        tvGeoInfo.text = if (Config.geoIsSet(this)) {
            getString(R.string.geo_set, Config.geoLat(this), Config.geoLng(this), Config.geoRadius(this))
        } else getString(R.string.geo_not_set)
        refreshFocusGuard()
        tvStats.text = buildStatsText(rules, flows, logs)
        tvPinInfo.text = getString(
            if (Config.pinHash(this).isNotEmpty()) R.string.pin_state_set else R.string.pin_state_unset)
        tvAdminInfo.text = getString(
            if (AdminReceiver.isActive(this)) R.string.admin_state_on else R.string.admin_state_off)
        // v2.17.4: display-only state lines for the accesses that
        // are granted in Permissions & Access (no grant path here).
        tvA11yState.text = getString(R.string.a11y_state_fmt,
            getString(if (PermChecks.a11yEnabled(this)) R.string.state_on
                else R.string.state_off))
        refreshDashboard()
        refreshSettingsHub(rules, flows)
    }


    // ------------- v2.17 redesign wiring -------------

    /** v2.17 Settings hub: only the surviving tiles (Perms, Security,
     *  Data, General, Smart) get click handlers now. */
    private fun wireV217() {
        findViewById<View>(R.id.tileHubPerms).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java)) }
        findViewById<View>(R.id.tileHubSecurity).setOnClickListener {
            openSubScreen(R.id.panelSecurity, R.string.cat_security) }
        findViewById<View>(R.id.tileHubData).setOnClickListener {
            openSubScreen(R.id.panelData, R.string.cat_data) }
        findViewById<View>(R.id.tileHubGeneral).setOnClickListener {
            openSubScreen(R.id.panelGeneral, R.string.cat_general) }
        findViewById<View>(R.id.tileHubSmart).setOnClickListener {
            openSubScreen(R.id.panelSmart, R.string.cat_smart) }
        refreshSettingsHub()
    }


    private fun refreshFocusGuard() {
        val on = Config.focusOn(this)
        val pkgs = Config.focusPkgs(this)
        val (fs, fe) = Config.focusWindow(this)
        val fmt = { m: Int -> "%02d:%02d".format(m / 60, m % 60) }
        focusGuardUpdating = true
        findViewById<CompoundButton>(R.id.swFocusGuard).isChecked = on
        focusGuardUpdating = false
        tvFocusGuardInfo.text = if (!on) {
            getString(R.string.focus_guard_info_off)
        } else {
            getString(R.string.focus_guard_info_on, fmt(fs), fmt(fe), pkgs.size)
        }
        tvFocusAppsInfo.text = if (pkgs.isEmpty()) {
            getString(R.string.focus_apps_none)
        } else {
            getString(R.string.focus_apps_chosen, pkgs.size)
        }
        btnFocusStart.text = getString(R.string.focus_time_start, fmt(fs))
        btnFocusEnd.text = getString(R.string.focus_time_end, fmt(fe))
    }

    /** Focus Guard card: start/end time picker (24-hour). Saving the
     *  window applies at once via refreshFocusGuard(). */
    private fun showFocusTimePicker(isStart: Boolean) {
        val (fs, fe) = Config.focusWindow(this)
        val cur = if (isStart) fs else fe
        try {
            TimePickerDialog(this, { _, h, m ->
                val v = h * 60 + m
                val (s, e) = Config.focusWindow(this)
                if (isStart) Config.setFocusWindow(this, v, e)
                else Config.setFocusWindow(this, s, v)
                refreshFocusGuard()
            }, cur / 60, cur % 60, true).show()
        } catch (_: Exception) { }
    }

    /** Focus Guard card: preset windows (minutes since midnight). */
    private fun applyFocusPreset(start: Int, end: Int) {
        Config.setFocusWindow(this, start, end)
        refreshFocusGuard()
    }


    // ----- professional theme picker (F): all 8 themes as live
    // preview cards with their REAL palette colours (taken from
    // values[-night]/themes.xml — the glass palettes are static,
    // identical in light and dark mode; Emerald Dark follows the
    // light/dark palettes, so its card shows the dark look).
    // Tapping a card saves the choice and recreates the activity,
    // so the whole app follows instantly. The light/dark/system
    // toggle in General settings is untouched and independent.
    private data class ThemePreview(
        val key: String, val nameRes: Int,
        val primary: String, val background: String, val surface: String,
        val onSurface: String, val onSurfaceVariant: String, val outline: String
    )

    private fun themePreviews() = listOf(
        ThemePreview("emerald_dark", R.string.theme_name_emerald_dark,
            "#10B981", "#101415", "#171D1F", "#EAF4EE", "#8FA79B", "#2A3336"),
        ThemePreview("aurora", R.string.theme_name_aurora,
            "#E879C6", "#160B36", "#2EFFFFFF", "#FDF2FA", "#D8B4D4", "#33FFFFFF"),
        ThemePreview("ocean_deep", R.string.theme_name_ocean_deep,
            "#38BDF8", "#04222F", "#2EFFFFFF", "#EFF9FF", "#9CC8D8", "#33FFFFFF"),
        ThemePreview("midnight_sapphire", R.string.theme_name_midnight_sapphire,
            "#8EA2FF", "#0A1130", "#2EFFFFFF", "#EEF2FF", "#A8B4D8", "#33FFFFFF"),
        ThemePreview("sunset_ember", R.string.theme_name_sunset_ember,
            "#FB923C", "#2A1008", "#2EFFFFFF", "#FFF7ED", "#E8C4A8", "#33FFFFFF"),
        ThemePreview("rose_quartz", R.string.theme_name_rose_quartz,
            "#F9A8D4", "#2B0A1E", "#2EFFFFFF", "#FFF1F2", "#E3B8C8", "#33FFFFFF"),
        ThemePreview("forest_mist", R.string.theme_name_forest_mist,
            "#6EE7B7", "#071A12", "#2EFFFFFF", "#F0FDF4", "#A8C8B4", "#33FFFFFF"),
        ThemePreview("graphite_mono", R.string.theme_name_graphite_mono,
            "#E5E7EB", "#17171A", "#2EFFFFFF", "#FAFAFA", "#A1A1AA", "#33FFFFFF")
    )

    private fun compositeOver(fg: Int, bg: Int): Int {
        val a = android.graphics.Color.alpha(fg) / 255f
        val r = (android.graphics.Color.red(fg) * a
            + android.graphics.Color.red(bg) * (1 - a)).toInt()
        val g = (android.graphics.Color.green(fg) * a
            + android.graphics.Color.green(bg) * (1 - a)).toInt()
        val b = (android.graphics.Color.blue(fg) * a
            + android.graphics.Color.blue(bg) * (1 - a)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun buildThemeCard(
        t: ThemePreview, selected: Boolean, dp: (Int) -> Int
    ): com.google.android.material.card.MaterialCardView {
        val bg = android.graphics.Color.parseColor(t.background)
        val primary = android.graphics.Color.parseColor(t.primary)
        val surface = compositeOver(
            android.graphics.Color.parseColor(t.surface), bg)
        val onSurface = android.graphics.Color.parseColor(t.onSurface)
        val onSurfaceVariant =
            android.graphics.Color.parseColor(t.onSurfaceVariant)
        val outline = android.graphics.Color.parseColor(t.outline)
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(bg)
            strokeWidth = dp(if (selected) 2 else 1)
            strokeColor = if (selected) primary else outline
            useCompatPadding = true
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(primary)
            }
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
        }
        header.addView(dot)
        val name = TextView(this).apply {
            text = getString(t.nameRes)
            setTextColor(onSurface)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(8) }
        }
        header.addView(name)
        if (selected) {
            val check = TextView(this).apply {
                text = "✓"
                setTextColor(bg)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(primary)
                }
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            header.addView(check)
        }
        inner.addView(header)
        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(surface)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        mini.addView(TextView(this).apply {
            text = "AutomationHub"
            setTextColor(onSurface)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        mini.addView(TextView(this).apply {
            text = getString(R.string.conn_automation_on, 0, 0)
            setTextColor(onSurfaceVariant)
            textSize = 11f
        })
        inner.addView(mini)
        card.addView(inner)
        return card
    }

    private fun showThemePicker() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_theme_picker, null)
        sheet.setContentView(view)
        val grid = view.findViewById<LinearLayout>(R.id.themeGrid)
        val current = Config.appTheme(this)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        for (row in themePreviews().chunked(2)) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            for ((i, t) in row.withIndex()) {
                val card = buildThemeCard(t, t.key == current, ::dp)
                card.setOnClickListener {
                    Config.setAppTheme(this, t.key)
                    sheet.dismiss()
                    recreate()
                }
                val lp = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (i == 0) lp.marginEnd = dp(4) else lp.marginStart = dp(4)
                rowLayout.addView(card, lp)
            }
            grid.addView(rowLayout)
        }
        view.findViewById<View>(R.id.btnThemeSheetClose).setOnClickListener {
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun refreshDashboard() {
        tvDashDevice.text = Config.deviceName(this)
        tvDashDeviceSub.text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
        val pct = BatteryHelper.batteryPercent(this)
        pbDashBattery.progress = pct
        tvDashBatteryPct.text = "$pct%"
        val (ssid, band) = ownWifi()
        tvDashWifi.text = if (ssid.isNotEmpty()) {
            "Wi-Fi \u2022 $ssid" + if (band.isNotEmpty()) " \u2022 $band" else ""
        } else "Wi-Fi \u2022 \u2014"
        refreshAutoTapCard()
        refreshRedialCard()
    }


    /** v2.21: run the merged AutoTap engine by hand (no rule
     *  involved). A STOP always goes through; a START needs
     *  an exact script name, a script with steps, and the
     *  merged TapAccessibilityService enabled — each failure
     *  toasts its reason instead of failing silently. */
    private fun runAutoTapDirect(start: Boolean, name: String) {
        try {
            if (!start) {
                RunnerService.sendCmd(this, RunnerService.CMD_STOP)
                toast(getString(R.string.dash_autotap_stopped))
                return
            }
            val script = Store.getScripts(this)
                .firstOrNull { it.name == name }
            if (script == null || script.steps.isEmpty()) {
                toast(getString(R.string.dash_autotap_not_found))
                return
            }
            if (!TapAccessibilityService.isEnabled()) {
                toast(getString(R.string.dash_autotap_a11y_off))
                return
            }
            RunnerService.start(this, script.id)
            toast(getString(R.string.dash_autotap_started, name))
        } catch (e: Exception) {
            toast(e.message ?: e.javaClass.simpleName)
        }
    }

    /** v2.21: AutoTap card state. The engine is built in, so
     *  the status line shows the real precondition — whether
     *  the merged Accessibility service is enabled — and the
     *  name field is prefilled from the remembered name only
     *  while empty, so a refresh never clobbers typed text. */
    private fun refreshAutoTapCard() {
        tvDashAutoTapStatus.text = getString(
            if (TapAccessibilityService.isEnabled())
                R.string.dash_autotap_a11y_on
            else R.string.dash_autotap_a11y_off)
        if (etDashAutoTapScript.text.toString().isEmpty())
            etDashAutoTapScript.setText(Config.autoTapScript(this))
    }

    /** v2.22: Redial card state. The status line shows the
     *  live session (attempt x/y) or the last result; the
     *  fields prefill from the remembered session only while
     *  empty, so a refresh never clobbers typed text. While
     *  a session runs, the SIM chips follow the session. */
    private fun refreshRedialCard() {
        tvDashRedialStatus.text = Redial.statusText(this)
        if (Redial.isActive(this)) {
            dashRedialSim = Redial.sessionSim(this)
            dashRedialOnanswer = Redial.sessionOnanswer(this)
            dashRedialSpeaker = Redial.sessionSpeaker(this)
        }
        if (etDashRedialNumber.text.toString().isEmpty())
            etDashRedialNumber.setText(Redial.sessionNumber(this))
        if (etDashRedialGap.text.toString().isEmpty())
            etDashRedialGap.setText(Redial.sessionGap(this).toString())
        if (etDashRedialMax.text.toString().isEmpty())
            etDashRedialMax.setText(Redial.sessionMax(this).toString())
        if (etDashRedialRing.text.toString().isEmpty())
            etDashRedialRing.setText(Redial.sessionRing(this).toString())
        refreshRedialSimButtons()
        refreshRedialOnanswerButtons()
        refreshRedialSpeakerButton()
    }

    private fun refreshRedialOnanswerButtons() {
        btnDashRedialOnanswerStop.alpha =
            if (dashRedialOnanswer == "stop") 1.0f else 0.45f
        btnDashRedialOnanswerResume.alpha =
            if (dashRedialOnanswer == "resume") 1.0f else 0.45f
    }

    private fun refreshRedialSpeakerButton() {
        btnDashRedialSpeaker.alpha =
            if (dashRedialSpeaker) 1.0f else 0.45f
    }

    private fun refreshRedialSimButtons() {
        btnDashRedialSimDef.alpha =
            if (dashRedialSim == "default") 1.0f else 0.45f
        btnDashRedialSim1.alpha =
            if (dashRedialSim == "sim1") 1.0f else 0.45f
        btnDashRedialSim2.alpha =
            if (dashRedialSim == "sim2") 1.0f else 0.45f
    }


    private fun refreshSettingsHub(rules: List<JSONObject>, flows: List<JSONObject>) {
        val missing = PermChecks.missingCount(this)
        tvSetPill.text = if (missing == 0) getString(R.string.set_status_ok)
            else getString(R.string.set_status_warn, missing)
        tvHubPermsSub.text = if (missing == 0)
            getString(R.string.perm_summary_ok)
        else getString(R.string.perm_summary_missing, missing)
        tvHubSecSub.text = "${tvPinInfo.text} • ${tvAdminInfo.text}"
        tvHubDataSub.text = tvStats.text
        tvHubDiag.text = "AutomationHub v$appVersionName • " +
            "${rules.size} rules • " +
            "${flows.size} flows"
        // v2.17.4: the same version line also lives at the bottom
        // of Appearance & General (inventory category 7).
        tvGeneralDiag.text = tvHubDiag.text
    }

    // One-time callers (Settings hub wiring) parse once and
    // delegate; the per-refresh path passes pre-parsed data.
    private fun refreshSettingsHub() {
        refreshSettingsHub(Automation.rules(this), FlowStore.flows(this))
    }


    // Smoothness (2026-11-06): SSID + band from ONE connectionInfo
    // read. refreshDashboard() used to call WifiManager.connectionInfo
    // twice (ownSsid + ownWifiBand) on every refresh — each read is a
    // Binder call.
    private fun ownWifi(): Pair<String, String> = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
            as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo
        val ssid = info?.ssid?.trim('"') ?: ""
        val clean = if (ssid == "<unknown ssid>") "" else ssid
        @Suppress("DEPRECATION")
        val freq = info?.frequency ?: 0
        val band = when {
            freq >= 4900 -> "5GHz"
            freq >= 2400 -> "2.4GHz"
            else -> ""
        }
        clean to band
    } catch (_: Exception) { "" to "" }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
