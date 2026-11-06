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
 * Focus Guard app picker (new 2026-11-06): the setup UI for the
 * Focus Guard card in the Smart panel. It is a NEW screen, not the
 * old setup dialog restored.
 *  - lists EVERY installed package (user apps by default; system
 *    apps behind the "Show system apps" toggle), searchable by
 *    label + package, with icon + name + checkbox rows;
 *  - checkboxes only edit a local set — nothing is written until
 *    Save is tapped, so browsing never changes the guard;
 *  - Save writes Config.setFocusPkgs; a non-empty list turns the
 *    guard on, an empty list turns it off (same rule as the card
 *    switch: on with no apps is impossible).
 * Safety exclusions are the same as the App Locker picker: this
 * app itself, Settings and the locker's hard exclusions never
 * appear, so the guard can never block its own management UI.
 */
class FocusGuardPickerActivity : LocalizedActivity() {

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
        setContentView(R.layout.activity_focus_guard_picker)

        checked.addAll(Config.focusPkgs(this))

        listView = findViewById(R.id.lvFocusApps)
        adapter = PickerAdapter()
        listView.adapter = adapter
        btnSave = findViewById(R.id.btnFocusSave)

        findViewById<View>(R.id.btnFocusPickerBack).setOnClickListener { finish() }

        findViewById<TextInputEditText>(R.id.etFocusPickerSearch)
            .addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim()?.lowercase().orEmpty()
                    applyFilter()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })

        findViewById<SwitchMaterial>(R.id.swFocusShowSystem)
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
        Config.setFocusPkgs(this, checked.joinToString(","))
        if (checked.isEmpty()) {
            Config.setFocusOn(this, false)
            toast(getString(R.string.focus_cleared))
        } else {
            Config.setFocusOn(this, true)
            toast(getString(R.string.focus_saved_count, checked.size))
        }
        finish()
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
            val icon = AppLocker.appIcon(this@FocusGuardPickerActivity, e.pkg)
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
            val line = LinearLayout(this@FocusGuardPickerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(12), dp(9))
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            line.addView(ImageView(this@FocusGuardPickerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            val texts = LinearLayout(this@FocusGuardPickerActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }
            texts.addView(TextView(this@FocusGuardPickerActivity).apply {
                textSize = 15f
                setTextColor(themeOnSurface())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            texts.addView(TextView(this@FocusGuardPickerActivity).apply {
                textSize = 12f
                setTextColor(themeOnSurfaceVariant())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            line.addView(texts)
            line.addView(MaterialCheckBox(this@FocusGuardPickerActivity))
            return line
        }
    }

    private fun toast(msg: String) =
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
}
