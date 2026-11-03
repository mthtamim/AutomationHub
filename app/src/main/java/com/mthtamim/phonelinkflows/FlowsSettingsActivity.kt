package com.mthtamim.phonelinkflows

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Minimal Settings for the standalone Flows app: the theme picker
 * (same 8 themes as PhoneLink, same Config.appTheme key) and the
 * Flows foreground-service switch. Everything else PhoneLink's
 * Settings had — relay/pairing, forwarding, AppLock, Inbox —
 * was removed with the split and is deliberately not here.
 *
 * Flows stored in this app are separate from PhoneLink's: bring
 * flows over with home screen ⋮ → Import / .flo import.
 */
class FlowsSettingsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(themeOnBackground())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(themeOnSurface())
            setPadding(0, 0, 0, 24)
        })
        root.addView(Button(this).apply {
            text = getString(R.string.btn_app_theme)
            setOnClickListener { showThemePicker() }
        })
        val svcOn = FlowStore.flowServiceOn(this)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
            addView(TextView(this).apply {
                text = getString(R.string.flow_service_switch)
                textSize = 16f
                setTextColor(themeOnSurface())
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Switch(this).apply {
                isChecked = svcOn
                setOnCheckedChangeListener { _, on ->
                    FlowStore.setFlowServiceOn(
                        this@FlowsSettingsActivity, on)
                    if (on) {
                        ContextCompat.startForegroundService(
                            this@FlowsSettingsActivity,
                            Intent(this@FlowsSettingsActivity,
                                FlowService::class.java))
                    } else {
                        stopService(Intent(this@FlowsSettingsActivity,
                            FlowService::class.java))
                    }
                }
            })
        })
        root.addView(TextView(this).apply {
            text = "Flows 1.0.0 — standalone skeleton extracted from " +
                "PhoneLink v2.25.2. Not yet built by CI, not phone-proven."
            textSize = 13f
            setTextColor(themeOnSurfaceVariant())
            setPadding(0, 32, 0, 0)
        })
        setContentView(root)
    }

    private fun showThemePicker() {
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
            .setSingleChoiceItems(labels,
                keys.indexOf(Config.appTheme(this))) { dialog, w ->
                Config.setAppTheme(this, keys[w])
                dialog.dismiss()
                recreate()
            }
            .show()
    }
}
