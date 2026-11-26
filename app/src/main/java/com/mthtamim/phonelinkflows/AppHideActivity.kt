package com.mthtamim.phonelinkflows

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.snackbar.Snackbar
import kotlin.concurrent.thread

/**
 * App Hide vault (v1.4.14): the hidden apps live here.
 *  - tap a row to open the app (it is enabled, launched, and
 *    hidden again once closed when auto re-hide is on);
 *  - the row dialog also offers "Show again" (unhide + remove
 *    from the hidden set) and, for uninstalled apps, removal
 *    from the list;
 *  - "Hide all again" re-applies hiding to the whole set — for
 *    icons that came back after an app update.
 * All Shizuku work runs off the main thread.
 */
class AppHideActivity : LocalizedActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: VaultAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var tvShizuku: TextView
    private var shown: List<Row> = emptyList()

    private data class Row(val pkg: String, val label: String,
                           val installed: Boolean, val hidden: Boolean)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_hide)

        listView = findViewById(R.id.lvAhApps)
        tvEmpty = findViewById(R.id.tvAhEmpty)
        tvShizuku = findViewById(R.id.tvAhShizuku)
        adapter = VaultAdapter()
        listView.adapter = adapter

        findViewById<View>(R.id.btnAhBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnAhAdd).setOnClickListener {
            startActivity(android.content.Intent(this, AppHidePickerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnAhRehideAll).setOnClickListener {
            thread {
                val n = AppHide.rehideAll(this)
                runOnUiThread {
                    toast(getString(R.string.ah_rehide_done, n))
                    refresh()
                }
            }
        }
        findViewById<SwitchMaterial>(R.id.swAhAutoRehide).apply {
            isChecked = Config.hideAutoRehide(this@AppHideActivity)
            setOnCheckedChangeListener { _, checked ->
                Config.setHideAutoRehide(this@AppHideActivity, checked)
            }
        }
        listView.setOnItemClickListener { _, _, pos, _ -> onRowTap(shown[pos]) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = ShizukuHelper.granted()
        tvShizuku.text = getString(if (granted) R.string.ah_shizuku_on
            else R.string.ah_shizuku_off)
        thread {
            val rows = Config.hidePkgs(this).sorted().map { pkg ->
                Row(pkg, AppLocker.appLabel(this, pkg),
                    AppLocker.isInstalled(this, pkg),
                    AppHide.isHidden(this, pkg))
            }
            runOnUiThread {
                shown = rows
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onRowTap(row: Row) {
        val status = getString(when {
            !row.installed -> R.string.ah_not_installed
            row.hidden -> R.string.ah_hidden
            else -> R.string.ah_visible
        })
        val dlg = AlertDialog.Builder(this)
            .setTitle(row.label)
            .setMessage(row.pkg + "\n" + status)
        if (row.installed) {
            dlg.setPositiveButton(R.string.ah_open_btn) { _, _ ->
                thread {
                    val ok = AppHide.openHidden(this, row.pkg)
                    runOnUiThread {
                        if (!ok) toast(getString(R.string.ah_open_failed, row.label))
                        refresh()
                    }
                }
            }
            dlg.setNeutralButton(R.string.ah_unhide_btn) { _, _ ->
                thread {
                    AppHide.unhide(this, row.pkg)
                    val set = Config.hidePkgs(this).toMutableSet()
                    if (set.remove(row.pkg))
                        Config.setHidePkgs(this, set.joinToString(","))
                    runOnUiThread { refresh() }
                }
            }
        } else {
            dlg.setPositiveButton(R.string.ah_remove_btn) { _, _ ->
                val set = Config.hidePkgs(this).toMutableSet()
                if (set.remove(row.pkg))
                    Config.setHidePkgs(this, set.joinToString(","))
                refresh()
            }
        }
        dlg.setNegativeButton(android.R.string.cancel, null)
        dlg.show()
    }

    private inner class VaultAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(pos: Int): Any = shown[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?,
                             parent: ViewGroup?): View {
            val row = shown[pos]
            val line = (convertView as? LinearLayout) ?: buildRow()
            val iv = line.getChildAt(0) as ImageView
            val texts = line.getChildAt(1) as LinearLayout
            val labelTv = texts.getChildAt(0) as TextView
            val statusTv = texts.getChildAt(1) as TextView
            val icon = AppLocker.appIcon(this@AppHideActivity, row.pkg)
            if (icon != null) iv.setImageDrawable(icon)
            else iv.setImageResource(R.drawable.ic_grid)
            labelTv.text = row.label
            statusTv.text = getString(when {
                !row.installed -> R.string.ah_not_installed
                row.hidden -> R.string.ah_hidden
                else -> R.string.ah_visible
            })
            return line
        }

        private fun buildRow(): LinearLayout {
            val line = LinearLayout(this@AppHideActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(12), dp(9))
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            line.addView(ImageView(this@AppHideActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            val texts = LinearLayout(this@AppHideActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }
            texts.addView(TextView(this@AppHideActivity).apply {
                textSize = 15f
                setTextColor(themeOnSurface())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            texts.addView(TextView(this@AppHideActivity).apply {
                textSize = 12f
                setTextColor(themeOnSurfaceVariant())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            line.addView(texts)
            return line
        }
    }

    private fun toast(msg: String) =
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
}
