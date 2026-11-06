package com.mthtamim.phonelinkflows

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.concurrent.thread

class MainActivity : LocalizedActivity() {

    // v2.17.2: info lines refreshed by refreshStatus().
    private lateinit var tvPinInfo: TextView
    private lateinit var tvAdminInfo: TextView
    private lateinit var tvGeoInfo: TextView
    private lateinit var tvStats: TextView

    private lateinit var pageHome: View
    private lateinit var pageSettings: View
    private var settingsOpen = false

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
    private lateinit var bottomNav: BottomNavigationView


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




    private var pendingBackupUri: Uri? = null

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            pendingBackupUri = uri
            val input = EditText(this).apply {
                hint = getString(R.string.hint_backup_password)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.backup_password_title))
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    writeBackup(input.text.toString())
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun writeBackup(password: String) {
        val uri = pendingBackupUri ?: return
        pendingBackupUri = null
        thread {
            var ok = false
            try {
                var text = SettingsTransfer.export(applicationContext)
                if (password.isNotEmpty()) text = BackupCrypto.encrypt(text, password)
                contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                ok = true
            } catch (_: Exception) { }
            Handler(Looper.getMainLooper()).post {
                toast(getString(if (ok) R.string.toast_backup_done else R.string.toast_backup_failed))
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            thread {
                var text = ""
                try {
                    text = contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.readText().orEmpty()
                } catch (_: Exception) { }
                Handler(Looper.getMainLooper()).post {
                    when {
                        text.isEmpty() -> toast(getString(R.string.toast_backup_failed))
                        BackupCrypto.isEncrypted(text) -> {
                            val input = EditText(this).apply {
                                hint = getString(R.string.hint_backup_password)
                                inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                            }
                            AlertDialog.Builder(this)
                                .setTitle(getString(R.string.backup_password_title))
                                .setView(input)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    val plain = BackupCrypto.decrypt(
                                        text, input.text.toString())
                                    if (plain == null) {
                                        toast(getString(R.string.toast_wrong_password))
                                    } else {
                                        finishRestore(plain)
                                    }
                                }
                                .setNegativeButton(getString(R.string.btn_cancel), null)
                                .show()
                        }
                        else -> finishRestore(text)
                    }
                }
            }
        }
    }

    private fun finishRestore(text: String) {
        thread {
            val ok = try { SettingsTransfer.import(applicationContext, text); true }
                catch (_: Exception) { false }
            Handler(Looper.getMainLooper()).post {
                if (ok) {
                    refreshStatus()
                    toast(getString(R.string.toast_restore_done))
                } else {
                    toast(getString(R.string.toast_backup_failed))
                }
            }
        }
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
        bottomNav = findViewById(R.id.bottomNav)
        applyGlassNavTint(bottomNav)

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
        tvStats = findViewById(R.id.tvStats)
        tvA11yState = findViewById(R.id.tvA11yState)
        tvGeneralDiag = findViewById(R.id.tvGeneralDiag)



        // ----- bottom navigation (v2.17: five tabs) -----
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navAutomation) {
                // Automation is its own activity with the same bar.
                startActivity(Intent(this, AutomationActivity::class.java))
                false
            } else {
                showPage(item.itemId)
                true
            }
        }
        val validTabs = setOf(R.id.navHome, R.id.navSettings)
        val startTab = savedInstanceState?.getInt("tab", R.id.navHome)
            ?: R.id.navHome
        if (startTab in validTabs && startTab != R.id.navHome) {
            bottomNav.selectedItemId = startTab
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

        // ----- v2.2 toggles & actions -----




        findViewById<Button>(R.id.btnSetGeo).setOnClickListener {
            toast(getString(R.string.toast_geo_fetching))
            LocationHelper.fetchCoords(this,
                { lat, lng ->
                    Config.setGeoCenter(this, lat, lng)
                    runOnUiThread { refreshStatus(); toast(getString(R.string.toast_geo_set)) }
                },
                { runOnUiThread { toast(getString(R.string.toast_geo_failed)) } })
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


        findViewById<Button>(R.id.btnBackupInbox).setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "automationhub-backup.json")
            }
            backupLauncher.launch(intent)
        }
        findViewById<Button>(R.id.btnRestoreInbox).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            restoreLauncher.launch(intent)
        }

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
            val keys = arrayOf("emerald_dark", "aurora", "ocean_deep",
                "midnight_sapphire", "sunset_ember", "rose_quartz",
                "forest_mist", "graphite_mono")
            val labels = arrayOf(
                getString(R.string.theme_name_emerald_dark),
                getString(R.string.theme_name_aurora),
                getString(R.string.theme_name_ocean_deep),
                getString(R.string.theme_name_midnight_sapphire),
                getString(R.string.theme_name_sunset_ember),
                getString(R.string.theme_name_rose_quartz),
                getString(R.string.theme_name_forest_mist),
                getString(R.string.theme_name_graphite_mono))
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_app_theme))
                .setSingleChoiceItems(labels, keys.indexOf(Config.appTheme(this))) { dialog, w ->
                    Config.setAppTheme(this, keys[w]); dialog.dismiss(); recreate()
                }
                .show()
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
        outState.putInt("tab", bottomNav.selectedItemId)
    }

    override fun onResume() {
        super.onResume()
        // v2.25.2: re-apply the glass nav tint (see applyGlassNavTint).
        applyGlassNavTint(bottomNav)
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
        bottomNav.selectedItemId = R.id.navSettings
    }

    private fun closeSettings() {
        if (settingsOpen) bottomNav.selectedItemId = R.id.navHome
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
                    } else if (bottomNav.selectedItemId != R.id.navHome) {
                        bottomNav.selectedItemId = R.id.navHome
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

    private fun showPage(itemId: Int) {
        settingsOpen = itemId == R.id.navSettings
        pageHome.visibility = if (itemId == R.id.navHome) View.VISIBLE else View.GONE
        pageSettings.visibility = if (itemId == R.id.navSettings) View.VISIBLE else View.GONE
        closeSubScreen()
    }



    /** AutomationHub: only home/settings tabs exist now. */
    private fun handleTabExtra(intent: Intent?) {
        when (intent?.getStringExtra("tab")) {
            "home" -> bottomNav.selectedItemId = R.id.navHome
            "settings" -> bottomNav.selectedItemId = R.id.navSettings
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
    private fun buildStatsText(): String {
        val rules = Automation.rules(this).size
        val flows = FlowStore.flows(this).size
        val logs = Automation.recentLog(this).size
        return getString(R.string.stats_rules_flows, rules, flows) +
            " • " + getString(R.string.stats_log_lines, logs)
    }


    private fun refreshStatus() {
        // AutomationHub: no relay — the header pill shows the local
        // automation state (rules/flows counts), the hub subtitles
        // show local device/automation status.
        val rules = Automation.rules(this)
        val flows = FlowStore.flows(this)
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
        tvStats.text = buildStatsText()
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
        refreshSettingsHub()
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


    private fun refreshDashboard() {
        tvDashDevice.text = Config.deviceName(this)
        tvDashDeviceSub.text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
        val pct = BatteryHelper.batteryPercent(this)
        pbDashBattery.progress = pct
        tvDashBatteryPct.text = "$pct%"
        val ssid = ownSsid()
        tvDashWifi.text = if (ssid.isNotEmpty()) {
            val band = ownWifiBand()
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


    private fun refreshSettingsHub() {
        val missing = PermChecks.missingCount(this)
        tvSetPill.text = if (missing == 0) getString(R.string.set_status_ok)
            else getString(R.string.set_status_warn, missing)
        tvHubPermsSub.text = if (missing == 0)
            getString(R.string.perm_summary_ok)
        else getString(R.string.perm_summary_missing, missing)
        tvHubSecSub.text = "${tvPinInfo.text} • ${tvAdminInfo.text}"
        tvHubDataSub.text = tvStats.text
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        tvHubDiag.text = "AutomationHub v$ver • " +
            "${Automation.rules(this).size} rules • " +
            "${FlowStore.flows(this).size} flows"
        // v2.17.4: the same version line also lives at the bottom
        // of Appearance & General (inventory category 7).
        tvGeneralDiag.text = tvHubDiag.text
    }



    private fun ownSsid(): String = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
            as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val ssid = wm.connectionInfo?.ssid?.trim('"') ?: ""
        if (ssid == "<unknown ssid>") "" else ssid
    } catch (_: Exception) { "" }

    private fun ownWifiBand(): String = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
            as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val freq = wm.connectionInfo?.frequency ?: 0
        when {
            freq >= 4900 -> "5GHz"
            freq >= 2400 -> "2.4GHz"
            else -> ""
        }
    } catch (_: Exception) { "" }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
