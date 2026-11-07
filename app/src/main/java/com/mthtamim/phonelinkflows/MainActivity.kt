package com.mthtamim.phonelinkflows

import android.app.StatusBarManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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
import android.view.ViewStub
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    // v1.2.6: the Settings hub and the four sub-screen panels inflate
    // lazily (ViewStubs) — NOT at cold start. pageSettings is null
    // until the first visit to the Settings tab; a panel id is absent
    // from the tree until its first open. hubReady / inflatedPanels
    // gate every touch of those views, so a refresh before first
    // visit simply skips them — the values are re-read from Config
    // when the view finally inflates (ensureSettings / ensurePanel
    // both end with refreshStatus()).
    private var pageSettings: View? = null
    private var hubReady = false
    private val inflatedPanels = mutableSetOf<Int>()
    private fun isPanelReady(id: Int) = id in inflatedPanels
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
    // v1.4.0: the M3 NavigationBar (nav_m3_bar) replaces the
    // M3 NavigationBar. v1.2.4: the SHOWN PAGE is the
    // single source of truth — showPage() is the only tab switcher
    // and paints the nav selection with the page, in the same step;
    // navSelectedId only mirrors it. Never paint the bar from
    // anywhere else.
    private var navSelectedId: Int = R.id.navHome

    // v1.4.0: Home SearchBar (preview) — inline search over rules,
    // flows and templates. Created in onCreate after pageHome is
    // bound; Back closes it first (see wireV24).
    private var homeSearch: HomeSearch? = null


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
        // v1.4.0: Home SearchBar wiring (page_home holds the bar +
        // results box; the include is inflated at setContentView).
        homeSearch = HomeSearch(this).also { it.setup(pageHome) }
        // v1.2.6: pageSettings is a ViewStub now — inflated on the
        // first Settings visit by ensureSettings() (from showPage).
        // (NavigationBar is wired in setupNavBar() below,
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
        // v1.2.6: the settings/hub/panel fields above are bound in
        // ensureSettings() / ensurePanel() now — the views do not
        // exist at cold start. (Home fields stay bound here.)


        // ----- M3 NavigationBar + FAB (v1.4.0): Home / Settings
        //  switch the page in place; DIY opens the Automation
        //  activity and Templates opens TemplatesActivity (the
        //  bar selection stays on the current tab for both, like
        //  the old listener returning false); the FAB opens the
        //  "new" bottom sheet (NavBarHelper).
        setupNavBar()
        val validTabs = setOf(R.id.navHome, R.id.navSettings)
        val startTab = savedInstanceState?.getInt("tab", R.id.navHome)
            ?: R.id.navHome
        if (startTab in validTabs && startTab != R.id.navHome) {
            showPage(startTab)
        } else {
            showPage(R.id.navHome)
        }
        handleTabExtra(intent)

        // ----- permissions entry points -----

        // v1.2.6: app lock / app locker wiring moved to
        // bindPanelSecurity() — the Security panel inflates lazily.

        // ----- more features (v2.1) -----
        // v1.2.6: swGeo moved to bindPanelSmart().

        // ----- Focus Guard card (new 2026-11-06): the on/off switch
        // lives in the dedicated card now (same ids). It turns a
        // saved schedule off (or back on); with no saved packages
        // it cannot turn on and says so. Setup (apps, window,
        // presets) is wired just below.
        // v1.2.6: Focus Guard switch moved to bindPanelSmart().

        // ----- Focus Guard setup card (new 2026-11-06): app picker,
        // time window and preset chips. Saving applies at once —
        // the picker enables the guard when apps are saved, and
        // refreshFocusGuard() redraws the summary below.
        // v1.2.6: Focus Guard setup wiring moved to bindPanelSmart().

        // ----- v2.2 toggles & actions -----


        // v1.2.6: btnSetGeo moved to bindPanelSmart().
        // ----- v2.17.4 settings reorganization wiring -----
        // Usage Access is granted ONLY in Permissions & Access now;
        // the Smart panel keeps a state line (refreshStatus) + link.
        // v1.2.6: btnSmartPermsLink moved to bindPanelSmart(),
        // btnSecPermsLink moved to bindPanelSecurity().
        // automation_on finally gets its master toggle (was an
        // orphan key changeable only via settings import).
        // v1.2.6: swAutoMaster moved to bindPanelSmart().
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
        // v1.2.6: btnAddQsTile moved to bindPanelSmart().
        // geo_radius finally gets a UI (was stuck at 200 m).
        // v1.2.6: btnGeoRadius moved to bindPanelSmart().
        // ----- data & statistics (v2.1) -----


        // ----- v2.11 remote additions -----


        // v1.2.6: accent / theme picker / update check / auto-update
        // wiring moved to bindPanelGeneral() — the General panel
        // inflates lazily.

        // v2.11: once-a-day silent update check
        if (System.currentTimeMillis() - Config.updateLastCheck(this) >
            24 * 3600_000L) Update.check(this, false)

        wireV24()
        // v1.2.6: wireV217() is gone — the hub tiles are wired in
        // ensureSettings() when the hub inflates.

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
                    // v1.2.4: when the exact-alarm grant is
                    // missing the session still starts, but on
                    // the 60 s tick — slow. Say so at once.
                    if (Redial.exactAlarmAllowed(this))
                        toast(getString(R.string.dash_redial_started,
                            number))
                    else
                        toast(getString(
                            R.string.dash_redial_no_exact_alarm))
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


        // v1.2.6: swIntruder / btnSetPin moved to bindPanelSecurity().
        // v2.17.4: the Device Admin grant button moved to
        // Permissions & Access — the Security panel keeps the
        // tvAdminInfo state line and the btnSecPermsLink link.

        // ----- appearance: language & theme -----
        // v1.2.6: language / theme toggles moved to
        // bindPanelGeneral().

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
        // v1.2.4: heal the nav selection from the SHOWN page, not from the
        // remembered tab — if anything ever desyncs them (a switch
        // interrupted mid-flight, a recreate), resume re-syncs
        // them instead of re-painting a stale selection.
        // v1.2.6: pageSettings may not exist yet (hub not visited) —
        // settingsOpen is the same truth showPage() maintains.
        selectNavTab(if (settingsOpen) R.id.navSettings else R.id.navHome)
        refreshStatus()
        // v1.4.4: watchdog — if the Flows switch is ON but the
        // service hasn't ticked for >10 min (XOS killed it),
        // start it again. Re-entering a running service is a
        // no-op (FlowService.onStartCommand is idempotent).
        try {
            if (FlowStore.flowServiceOn(this) &&
                System.currentTimeMillis() - FlowStore.serviceLastTick(this) > 10 * 60_000L
            ) {
                startForegroundService(Intent(this, FlowService::class.java))
            }
        } catch (_: Exception) { }
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
                // v1.4.4: nfc_tag was rules-only — feed flows too.
                FlowEngine.onEvent(this, "nfc_tag", org.json.JSONObject())
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
        // v1.2.6: the panel inflates on its first open.
        ensurePanel(groupId)
        findViewById<View>(R.id.subRoot).visibility = View.VISIBLE
        findViewById<View>(R.id.bottomNav).visibility = View.GONE
        findViewById<View>(R.id.toolbar).visibility = View.GONE
        findViewById<TextView>(R.id.tvSubTitle).setText(titleRes)
        for (id in subGroups) {
            // Panels that never opened are still ViewStubs —
            // findViewById returns null for them; only the
            // inflated ones get a visibility.
            findViewById<View>(id)?.let {
                it.visibility = if (id == groupId) View.VISIBLE else View.GONE
            }
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

    /** v2.17: Settings is a bottom-nav page now, not an overlay.
     *  v1.2.4: these go through showPage() like every other
     *  switch — painting the bar alone desyncs it from the page. */
    private fun openSettings() {
        showPage(R.id.navSettings)
    }

    private fun closeSettings() {
        if (settingsOpen) showPage(R.id.navHome)
    }


    // ------------- v1.2.6 lazy inflation -------------
    // The Settings hub (page_settings) and the four sub-screen
    // panels inflate on first use, not at cold start. Each
    // ensure*() runs once: it inflates the ViewStub, binds the
    // lateinit fields that live in that subtree, wires its
    // listeners (verbatim from onCreate / wireV24 / wireV217),
    // and ends with refreshStatus() so the new views show the
    // current values at once.

    /** Inflate the Settings hub on first visit to the Settings tab. */
    private fun ensureSettings() {
        if (hubReady) return
        val stub = findViewById<ViewStub>(R.id.stubSettings)
        pageSettings = stub.inflate()
        tvSetPill = findViewById(R.id.tvSetPill)
        tvHubPermsSub = findViewById(R.id.tvHubPermsSub)
        tvHubSecSub = findViewById(R.id.tvHubSecSub)
        tvHubDataSub = findViewById(R.id.tvHubDataSub)
        tvHubDiag = findViewById(R.id.tvHubDiag)
        // Hub tiles — the only entry points to the panels.
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
        hubReady = true
        refreshSettingsHub()
    }

    /** Inflate one sub-screen panel on its first open. */
    private fun ensurePanel(groupId: Int) {
        if (groupId in inflatedPanels) return
        when (groupId) {
            R.id.panelGeneral -> {
                findViewById<ViewStub>(R.id.stubPanelGeneral).inflate()
                bindPanelGeneral()
            }
            R.id.panelSmart -> {
                findViewById<ViewStub>(R.id.stubPanelSmart).inflate()
                bindPanelSmart()
            }
            R.id.panelSecurity -> {
                findViewById<ViewStub>(R.id.stubPanelSecurity).inflate()
                bindPanelSecurity()
            }
            R.id.panelData -> {
                findViewById<ViewStub>(R.id.stubPanelData).inflate()
                bindPanelData()
            }
            else -> return
        }
        inflatedPanels.add(groupId)
        // Fill the new views with the current values at once.
        refreshStatus()
    }

    /** General panel: language/theme, accent, theme picker, update. */
    private fun bindPanelGeneral() {
        tvGeneralDiag = findViewById(R.id.tvGeneralDiag)
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
        findViewById<Button>(R.id.btnAccent).setOnClickListener {
            val names = arrayOf("emerald", "blue", "purple", "amber", "rose")
            val labels = arrayOf(
                getString(R.string.accent_emerald), getString(R.string.accent_blue),
                getString(R.string.accent_purple), getString(R.string.accent_amber),
                getString(R.string.accent_rose))
            MaterialAlertDialogBuilder(this)
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
            Update.check(this, true) { newer, url, apkUrl ->
                if (newer != null) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.update_found, newer))
                        .setMessage(getString(R.string.update_found_msg))
                        .setPositiveButton(getString(R.string.btn_download_install)) { _, _ ->
                            // v1.4.11: download and install in-app —
                            // the old code just opened the release
                            // page in the browser. The browser is
                            // only the fallback when no APK link came back.
                            if (!apkUrl.isNullOrEmpty()) {
                                toast(getString(R.string.update_downloading))
                                Update.downloadAndInstall(this, apkUrl, newer)
                            } else if (!url.isNullOrEmpty()) try {
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
    }

    /** Smart panel: geo, Focus Guard, master toggle, QS tile. */
    private fun bindPanelSmart() {
        tvGeoInfo = findViewById(R.id.tvGeoInfo)
        tvFocusGuardInfo = findViewById(R.id.tvFocusGuardInfo)
        tvFocusAppsInfo = findViewById(R.id.tvFocusAppsInfo)
        btnFocusStart = findViewById(R.id.btnFocusStart)
        btnFocusEnd = findViewById(R.id.btnFocusEnd)
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
                        MaterialAlertDialogBuilder(this)
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
        findViewById<Button>(R.id.btnAddQsTriggerTile).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sbm = getSystemService(StatusBarManager::class.java)
                if (sbm != null) {
                    sbm.requestAddTileService(
                        ComponentName(this, TriggerTileService::class.java),
                        "AutomationHub ট্রিগার",
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
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.btn_geo_radius))
                .setItems(labels) { _, which ->
                    Config.setGeoRadius(this, options[which]); refreshStatus()
                }
                .show()
        }
        // ----- FocusLock card (moved from the DIY Rules list,
        // v1.4.2): one-time token save + install/token status +
        // open-FocusLock. Same ids and strings as before. -----
        findViewById<Button>(R.id.btnFlToken).setOnClickListener {
            showFocusLockTokenDialog()
        }
        findViewById<Button>(R.id.btnFlOpen).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage(
                Automation.FOCUSLOCK_PKG)
            if (launch != null) startActivity(launch)
            else com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.fl_not_installed_toast),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }
        renderFocusLock()
        // ----- Webhook token card (v1.4.12): the LAN webhook
        // needs a token now — show it (copyable) + regenerate.
        // Unlike the FocusLock token, this one MUST be visible:
        // the caller has to put it in the webhook URL/header.
        findViewById<TextView>(R.id.tvWebhookToken).text =
            WebhookServer.token(this)
        findViewById<Button>(R.id.btnWebhookCopy).setOnClickListener {
            copyToClipboard(WebhookServer.token(this))
            toast(getString(R.string.webhook_token_copied))
        }
        findViewById<Button>(R.id.btnWebhookRegen).setOnClickListener {
            findViewById<TextView>(R.id.tvWebhookToken).text =
                WebhookServer.regenerateToken(this)
            toast(getString(R.string.webhook_token_regen_done))
        }
        refreshFocusGuard()
    }

    /** Security panel: app lock, PIN, intruder, admin state. */
    private fun bindPanelSecurity() {
        tvPinInfo = findViewById(R.id.tvPinInfo)
        tvAdminInfo = findViewById(R.id.tvAdminInfo)
        tvA11yState = findViewById(R.id.tvA11yState)
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

        // ----- security card -----
        bindToggle(R.id.swIntruder, "intruder_on", Config.intruderOn(this))
        findViewById<Button>(R.id.btnSetPin).setOnClickListener { showSetPinDialog { } }
        // v2.17.4: the Device Admin grant button moved to
        // Permissions & Access — the Security panel keeps the
        // tvAdminInfo state line and the btnSecPermsLink link.
        findViewById<Button>(R.id.btnSecPermsLink).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    /** Data panel: stats line, backup / import / config code. */
    private fun bindPanelData() {
        tvStats = findViewById(R.id.tvStats)
        // v1.4.0: log lines live on their own Log screen now.
        findViewById<Button>(R.id.btnOpenLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
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
            MaterialAlertDialogBuilder(this)
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
            MaterialAlertDialogBuilder(this)
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
    }


    private fun wireV24() {
        // --- sub-screen navigation (v2.4.1) ---
        findViewById<View>(R.id.btnSubBack).setOnClickListener { closeSubScreen() }
        // v2.17.3: no back arrow on the Settings tab page any more
        // (btnSettingsBack removed from page_settings.xml) — Settings
        // is a top-level tab; system Back handles navigation.
        // v2.17.2: the settings hub tiles are the only entry points
        // (all five are wired in ensureSettings()) -- no legacy
        // menu, no dashboard shortcut boxes: every panel opens
        // from one place.

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
                    } else if (homeSearch?.closeIfOpen() == true) {
                        // Search box was open — closed it, stay.
                    } else if (navSelectedId != R.id.navHome) {
                        showPage(R.id.navHome)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })


        // --- security panel ---
        // v2.17.4: the Accessibility grant button moved to
        // Permissions & Access — Security keeps the tvA11yState
        // state line and the btnSecPermsLink link (wired in
        // bindPanelSecurity, v1.2.6).

        // --- former group panel items (now in Data) ---


        // v2.17.4: the Shizuku connect/test button moved to
        // Permissions & Access (its state line lives there too).

        // v1.2.6: Data panel buttons moved to bindPanelData().

        // --- data panel ---


        // --- home additions ---


    }

    /** v1.4.2: wire the Essentials pill bottom bar (NavBarHelper).
     *  Home and Settings switch the dashboard page; DIY opens the
     *  Automation activity and the pill selection stays on the
     *  current tab. The + button opens the "new" sheet. */
    private fun setupNavBar() {
        NavBarHelper.setupPillBar(this, R.id.bottomNav,
            if (navSelectedId == R.id.navSettings) R.id.navSettings
            else R.id.navHome,
            onSelect = { id ->
                when (id) {
                    R.id.navHome -> showPage(R.id.navHome)
                    R.id.navAutomation ->
                        startActivity(Intent(this, AutomationActivity::class.java))
                    R.id.navSettings -> showPage(R.id.navSettings)
                }
            },
            onAdd = { anchor -> NavBarHelper.showNewSheet(this, anchor) })
    }

    /** v1.4.2: paint the pill bar — NavBarHelper paints the
     *  selection manually (no setSelectedItemId anywhere, so the
     *  v1.4.1 StackOverflowError cannot recur). Kept the
     *  selectNavTab name so the showPage / onResume call sites
     *  do not change. */
    private fun selectNavTab(id: Int) {
        navSelectedId = id
        NavBarHelper.paintPillBar(this, R.id.bottomNav, id)
    }

    // v1.2.4: THE tab switcher — the only function that changes
    // the page, and it paints the nav bar in the same step, so the
    // two can never disagree. A repeat tap on the current tab
    // (including a second tap mid-transition) is a no-op: it only
    // re-asserts the bar selection, never replays the transition.
    private fun showPage(itemId: Int) {
        // v1.2.6: the Settings hub inflates on first visit — before
        // anything below touches pageSettings.
        if (itemId == R.id.navSettings) ensureSettings()
        if (pageHome.isLaidOut &&
            settingsOpen == (itemId == R.id.navSettings)) {
            selectNavTab(itemId)
            return
        }
        // Paint first, before the transition captures the scene —
        // the bar is already in its end state when the change runs.
        selectNavTab(itemId)
        // v1.2.2: Home <-> Settings is a MaterialSharedAxis (X,
        // 300 ms, emphasized easing) — the page slides/fades along
        // the axis in one motion. Skipped on the very first call
        // (onCreate, before the first layout) so the first frame
        // is not animated from an unlaid-out state.
        val change = {
            // v1.4.0: leaving Home closes the search box.
            homeSearch?.closeIfOpen()
            settingsOpen = itemId == R.id.navSettings
            pageHome.visibility = if (itemId == R.id.navHome) View.VISIBLE else View.GONE
            // v1.2.6: pageSettings is null until its first inflate —
            // a null-safe set keeps the Home switch working.
            pageSettings?.visibility = if (itemId == R.id.navSettings) View.VISIBLE else View.GONE
            closeSubScreen()
        }
        if (pageHome.isLaidOut) {
            PLMotion.beginSharedAxisX(pageHome.parent as android.view.ViewGroup,
                itemId == R.id.navSettings, change)
            // v1.2.3: the incoming page slides+fades in directly —
            // the scene transition alone did not show on 1.2.2, so
            // the visible motion must not depend on it. v1.2.4:
            // the same-tab guard at the top of showPage() means
            // this only runs on a real user-initiated switch.
            // v1.2.6: pageSettings is non-null here when switching
            // to Settings (ensureSettings ran above); the fallback
            // only guards the unreachable null.
            val target = if (itemId == R.id.navSettings) pageSettings else pageHome
            if (target != null) PLMotion.slideFadeIn(target,
                itemId == R.id.navSettings)
        } else {
            change()
        }
    }


    /** AutomationHub: only home/settings tabs exist now. */
    // v1.2.4: the tab extra switches the PAGE, not just the bar —
    // painting the bar without switching the page was the proven
    // desync (DIY Home/Settings round-trip: bar said Home while
    // the page showed Settings, and vice versa).
    private fun handleTabExtra(intent: Intent?) {
        when (intent?.getStringExtra("tab")) {
            "home" -> showPage(R.id.navHome)
            "settings" -> showPage(R.id.navSettings)
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
        MaterialAlertDialogBuilder(this)
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


    // v1.2.5: changed-value guards — assigning the same text (or
    // progress, or colour) again still requests a full re-layout
    // of the Home ScrollView tree, and its nested weights then
    // re-measure twice. refreshStatus() runs on every onResume,
    // so refreshes that change nothing must not touch the views
    // at all. Behaviour is identical: a changed value is still
    // assigned exactly as before.
    private fun setTextIfChanged(tv: TextView, value: String) {
        if (tv.text.toString() != value) tv.text = value
    }

    private fun setProgressIfChanged(pb: ProgressBar, value: Int) {
        if (pb.progress != value) pb.progress = value
    }

    private fun setTextColorIfChanged(tv: TextView, color: Int) {
        if (tv.currentTextColor != color) tv.setTextColor(color)
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
        setTextIfChanged(tvConnPill, if (autoOn) {
            getString(R.string.conn_automation_on, rules.size, flows.size)
        } else {
            getString(R.string.conn_automation_off)
        })
        setTextColorIfChanged(tvConnPill, ContextCompat.getColor(this,
            if (autoOn) R.color.status_ok else R.color.status_bad))
        // v1.2.6: the settings views below only exist once their
        // panel has inflated — before that, skip them. The values
        // come from Config, so nothing is lost: the first
        // ensureSettings()/ensurePanel() ends with refreshStatus(),
        // which fills the newly-inflated views then.
        if (isPanelReady(R.id.panelSmart)) {
            // Smart-control / data info lines
            setTextIfChanged(tvGeoInfo, if (Config.geoIsSet(this)) {
                getString(R.string.geo_set, Config.geoLat(this), Config.geoLng(this), Config.geoRadius(this))
            } else getString(R.string.geo_not_set))
            refreshFocusGuard()
            renderFocusLock()
        }
        if (isPanelReady(R.id.panelData)) {
            setTextIfChanged(tvStats, buildStatsText(rules, flows, logs))
        }
        if (isPanelReady(R.id.panelSecurity)) {
            setTextIfChanged(tvPinInfo, getString(
                if (Config.pinHash(this).isNotEmpty()) R.string.pin_state_set else R.string.pin_state_unset))
            setTextIfChanged(tvAdminInfo, getString(
                if (AdminReceiver.isActive(this)) R.string.admin_state_on else R.string.admin_state_off))
            // v2.17.4: display-only state lines for the accesses that
            // are granted in Permissions & Access (no grant path here).
            setTextIfChanged(tvA11yState, getString(R.string.a11y_state_fmt,
                getString(if (PermChecks.a11yEnabled(this)) R.string.state_on
                    else R.string.state_off)))
        }
        refreshDashboard()
        if (hubReady) refreshSettingsHub(rules, flows, logs)
    }


    // ------------- v2.17 redesign wiring -------------

    // v1.2.6: wireV217() is gone — its hub-tile wiring lives in
    // ensureSettings() now (the hub inflates lazily).


    private fun refreshFocusGuard() {
        // v1.2.6: the Focus Guard views live in the Smart panel —
        // before its first inflate there is nothing to refresh.
        if (!isPanelReady(R.id.panelSmart)) return
        val on = Config.focusOn(this)
        val pkgs = Config.focusPkgs(this)
        val (fs, fe) = Config.focusWindow(this)
        val fmt = { m: Int -> "%02d:%02d".format(m / 60, m % 60) }
        focusGuardUpdating = true
        findViewById<CompoundButton>(R.id.swFocusGuard).isChecked = on
        focusGuardUpdating = false
        setTextIfChanged(tvFocusGuardInfo, if (!on) {
            getString(R.string.focus_guard_info_off)
        } else {
            getString(R.string.focus_guard_info_on, fmt(fs), fmt(fe), pkgs.size)
        })
        setTextIfChanged(tvFocusAppsInfo, if (pkgs.isEmpty()) {
            getString(R.string.focus_apps_none)
        } else {
            getString(R.string.focus_apps_chosen, pkgs.size)
        })
        setTextIfChanged(btnFocusStart, getString(R.string.focus_time_start, fmt(fs)))
        setTextIfChanged(btnFocusEnd, getString(R.string.focus_time_end, fmt(fe)))
    }

    /** v1.4.2: FocusLock card — moved from the DIY Rules list into
     *  the Smart panel (Tamim, 2026-11-07). Install state from the
     *  PackageManager, token state from Config. The token itself
     *  is NEVER shown; the status only carries the •••• marker. */
    private fun renderFocusLock() {
        if (!isPanelReady(R.id.panelSmart)) return
        val installed = Automation.focusLockInstalled(this)
        val hasToken = Config.focusLockToken(this).isNotEmpty()
        setTextIfChanged(findViewById<TextView>(R.id.tvFlStatus),
            getString(if (installed) R.string.fl_status_connected
                else R.string.fl_status_missing) + " • " +
            getString(if (hasToken) R.string.fl_status_token_set
                else R.string.fl_status_token_unset))
        findViewById<Button>(R.id.btnFlToken).text =
            getString(if (hasToken) R.string.fl_btn_token_change
                else R.string.fl_btn_token_set)
    }

    /** v1.4.2: one-time token save (moved with the card). The field
     *  always starts EMPTY (the saved token is never displayed
     *  anywhere); saving an empty field clears the token. */
    private fun showFocusLockTokenDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.fl_token_hint)
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.fl_token_dialog_title))
            .setMessage(getString(R.string.fl_token_dialog_msg))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val v = input.text.toString().trim()
                Config.setFocusLockToken(this, v)
                com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                    getString(if (v.isEmpty()) R.string.fl_token_cleared
                        else R.string.fl_token_saved),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                renderFocusLock()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    /** Focus Guard card: start/end time picker (24-hour). Saving the
     *  window applies at once via refreshFocusGuard().
     *  v1.3.0: M3 MaterialTimePicker (was the framework
     *  TimePickerDialog) — same 24-hour clock, same save. */
    private fun showFocusTimePicker(isStart: Boolean) {
        val (fs, fe) = Config.focusWindow(this)
        val cur = if (isStart) fs else fe
        try {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(cur / 60)
                .setMinute(cur % 60)
                .build()
            picker.addOnPositiveButtonClickListener {
                val v = picker.hour * 60 + picker.minute
                val (s, e) = Config.focusWindow(this)
                if (isStart) Config.setFocusWindow(this, v, e)
                else Config.setFocusWindow(this, s, v)
                refreshFocusGuard()
            }
            picker.show(supportFragmentManager, "focus_time")
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
            "#4edeB4", "#0a120e", "#101a15", "#ddefe4", "#a9bcb1", "#5d6f65"),
        ThemePreview("aurora", R.string.theme_name_aurora,
            "#a8c8ff", "#101322", "#B81E2440", "#e2e6ff", "#aab2cc", "#5f6884"),
        ThemePreview("ocean_deep", R.string.theme_name_ocean_deep,
            "#6fd3f7", "#071318", "#0d1e24", "#daeff7", "#a3bcc7", "#5a707b"),
        ThemePreview("midnight_sapphire", R.string.theme_name_midnight_sapphire,
            "#9db9ff", "#0a0f1e", "#111828", "#e0e6f7", "#a7b0c7", "#5c657c"),
        ThemePreview("sunset_ember", R.string.theme_name_sunset_ember,
            "#ffb59a", "#180d08", "#221209", "#f7e3d8", "#c7ab9c", "#7d6557"),
        ThemePreview("rose_quartz", R.string.theme_name_rose_quartz,
            "#ffb1c7", "#190a12", "#221019", "#f7e0e9", "#c7a9b6", "#7d5f6c"),
        ThemePreview("forest_mist", R.string.theme_name_forest_mist,
            "#a5d69b", "#0d130c", "#151d14", "#e2eedb", "#aebba9", "#67725f"),
        ThemePreview("graphite_mono", R.string.theme_name_graphite_mono,
            "#d7d9dc", "#101214", "#0DFFFFFF", "#e8e9eb", "#b0b3b7", "#63676b")
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
        setTextIfChanged(tvDashDevice, Config.deviceName(this))
        setTextIfChanged(tvDashDeviceSub, "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}")
        val pct = BatteryHelper.batteryPercent(this)
        setProgressIfChanged(pbDashBattery, pct)
        setTextIfChanged(tvDashBatteryPct, "$pct%")
        val (ssid, band) = ownWifi()
        setTextIfChanged(tvDashWifi, if (ssid.isNotEmpty()) {
            "Wi-Fi \u2022 $ssid" + if (band.isNotEmpty()) " \u2022 $band" else ""
        } else "Wi-Fi \u2022 \u2014")
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
        setTextIfChanged(tvDashAutoTapStatus, getString(
            if (TapAccessibilityService.isEnabled())
                R.string.dash_autotap_a11y_on
            else R.string.dash_autotap_a11y_off))
        if (etDashAutoTapScript.text.toString().isEmpty())
            etDashAutoTapScript.setText(Config.autoTapScript(this))
    }

    /** v2.22: Redial card state. The status line shows the
     *  live session (attempt x/y) or the last result; the
     *  fields prefill from the remembered session only while
     *  empty, so a refresh never clobbers typed text. While
     *  a session runs, the SIM chips follow the session. */
    private fun refreshRedialCard() {
        setTextIfChanged(tvDashRedialStatus, Redial.statusText(this))
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


    // v1.2.6: the hub lines are computed from Config/data now, NOT
    // read back from other settings views — tvPinInfo / tvStats live
    // in panels that may not have inflated yet, and reading a
    // lateinit field of an uninflated panel would crash. The strings
    // are identical to what refreshStatus() writes into those views
    // when they exist.
    private fun refreshSettingsHub(rules: List<JSONObject>, flows: List<JSONObject>, logs: List<String>) {
        if (!hubReady) return
        val missing = PermChecks.missingCount(this)
        setTextIfChanged(tvSetPill, if (missing == 0) getString(R.string.set_status_ok)
            else getString(R.string.set_status_warn, missing))
        setTextIfChanged(tvHubPermsSub, if (missing == 0)
            getString(R.string.perm_summary_ok)
        else getString(R.string.perm_summary_missing, missing))
        val pinState = getString(
            if (Config.pinHash(this).isNotEmpty()) R.string.pin_state_set else R.string.pin_state_unset)
        val adminState = getString(
            if (AdminReceiver.isActive(this)) R.string.admin_state_on else R.string.admin_state_off)
        setTextIfChanged(tvHubSecSub, "$pinState • $adminState")
        setTextIfChanged(tvHubDataSub, buildStatsText(rules, flows, logs))
        setTextIfChanged(tvHubDiag, "AutomationHub v$appVersionName • " +
            "${rules.size} rules • " +
            "${flows.size} flows")
        // v2.17.4: the same version line also lives at the bottom
        // of Appearance & General (inventory category 7).
        if (isPanelReady(R.id.panelGeneral)) {
            setTextIfChanged(tvGeneralDiag, tvHubDiag.text.toString())
        }
    }

    // One-time callers (Settings hub wiring) parse once and
    // delegate; the per-refresh path passes pre-parsed data.
    private fun refreshSettingsHub() {
        refreshSettingsHub(Automation.rules(this), FlowStore.flows(this), Automation.recentLog(this))
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

    /** v1.4.0: short confirmations are Snackbars now (same text,
     *  same call sites) — every caller runs on the main thread,
     *  so the content-view anchor is always safe here. */
    private fun toast(msg: String) =
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content), msg,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
}
