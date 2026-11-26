package com.mthtamim.phonelinkflows

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.snackbar.Snackbar
import kotlin.concurrent.thread

/**
 * App Hide picker (v1.4.14): mirrors the Focus Guard picker —
 *  lists EVERY installed package (user apps by default; system
 *  apps behind the "Show system apps" toggle), searchable by
 *  label + package, with icon + name + checkbox rows;
 *  - checkboxes only edit a local set — nothing is applied
 *    until Save is tapped;
 *  - Save hides the newly checked apps and unhides the newly
 *    unchecked ones (both via Shizuku), then writes the set.
 * Safety exclusions are the same as the App Locker picker:
 * this app itself, Settings and the locker's hard exclusions
 * never appear, so the vault can never hide its own UI.
 */
class AppHidePickerActivity : LocalizedActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: PickerAdapter
    private lateinit var btnSave: View
    private var all: List<AppLocker.AppEntry> = emptyList()
    private var shown: List<AppLocker.AppEntry> = emptyList()
    private val checked = mutableSetOf<String>()
    private var query = ""
    private var showSystem = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_hide_picker)

        checked.addAll(Config.hidePkgs(this))

        listView = findViewById(R.id.lvAhPickerApps)
        adapter = PickerAdapter()
        listView.adapter = adapter
        btnSave = findViewById(R.id.btnAhPickerSave)

        findViewById<View>(R.id.btnAhPickerBack).setOnClickListener { finish() }

        findViewById<TextInputEditText>(R.id.etAhPickerSearch)
            .addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim()?.lowercase().orEmpty()
                    applyFilter()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })

        findViewById<SwitchMaterial>(R.id.swAhShowSystem)
            .setOnCheckedChangeListener { _, checkedShow ->
                showSystem = checkedShow
                applyFilter()
            }

        btnSave.setOnClickListener { save() }
        updateSaveLabel()

        thread {
            val apps = AppLocker.allInstalled(this)
                .filter { !AppLocker.pickerExcluded(this, it.pkg) }
            runOnUiThread {
                all = apps
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        shown = all.filter { e ->
            (showSystem || !e.system) &&
                (query.isEmpty() || e.label.lowercase().contains(query) ||
                    e.pkg.lowercase().contains(query))
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateSaveLabel() {
        (btnSave as? TextView)?.text =
            getString(R.string.btn_save) + " (" + checked.size + ")"
    }

    private fun save() {
        if (!ShizukuHelper.granted()) {
            toast(getString(R.string.ah_shizuku_off))
            return
        }
        btnSave.isEnabled = false
        thread {
            val old = Config.hidePkgs(this)
            val added = checked - old
            val removed = old - checked
            var ok = 0
            for (pkg in added) if (AppHide.hide(this, pkg)) ok++
            for (pkg in removed) AppHide.unhide(this, pkg)
            // Keep only what is actually hidden now (a failed hide
            // must not be recorded as hidden).
            val kept = checked.filter { pkg ->
                pkg !in added || AppHide.isHidden(this, pkg)
            }.toSet()
            Config.setHidePkgs(this, kept.joinToString(","))
            runOnUiThread {
                toast(getString(R.string.ah_saved_count, ok))
                finish()
            }
        }
    }

    private inner class PickerAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(pos: Int): Any = shown[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?,
                             parent: ViewGroup?): View {
            val e = shown[pos]
            val line = (convertView as? LinearLayout) ?: buildRow()
            val iv = line.getChildAt(0) as ImageView
            val texts = line.getChildAt(1) as LinearLayout
            val labelTv = texts.getChildAt(0) as TextView
            val pkgTv = texts.getChildAt(1) as TextView
            val check = line.getChildAt(2) as MaterialCheckBox
            val icon = AppLocker.appIcon(this@AppHidePickerActivity, e.pkg)
            if (icon != null) iv.setImageDrawable(icon)
            else iv.setImageResource(R.drawable.ic_grid)
            labelTv.text = e.label
            pkgTv.text = e.pkg
            check.setOnCheckedChangeListener(null)
            check.isChecked = checked.contains(e.pkg)
            check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checked.add(e.pkg) else checked.remove(e.pkg)
                updateSaveLabel()
            }
            line.setOnClickListener { check.isChecked = !check.isChecked }
            return line
        }

        private fun buildRow(): LinearLayout {
            val line = LinearLayout(this@AppHidePickerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(12), dp(9))
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            line.addView(ImageView(this@AppHidePickerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            val texts = LinearLayout(this@AppHidePickerActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }
            texts.addView(TextView(this@AppHidePickerActivity).apply {
                textSize = 15f
                setTextColor(themeOnSurface())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            texts.addView(TextView(this@AppHidePickerActivity).apply {
                textSize = 12f
                setTextColor(themeOnSurfaceVariant())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            line.addView(texts)
            line.addView(MaterialCheckBox(this@AppHidePickerActivity))
            return line
        }
    }

    private fun toast(msg: String) =
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
}
