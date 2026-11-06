package com.mthtamim.phonelinkflows

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/**
 * Templates gallery (new 2026-11-06): the window behind the ⋮ menu's
 * Templates entry on both Automation tabs. One searchable list with
 * two sections — Rules, then Flows. Tapping "যোগ করো" on a row adds
 * that template immediately (no editing needed): a Rule goes through
 * Automation.importJson (collision-safe), a Flow through
 * FlowStore.saveFlow (re-id on collision). The row then shows
 * "যোগ হয়েছে ✓" and disables, so the same tap cannot double-add.
 * Geofence flow templates need a saved geo centre; without one the
 * add is refused with a toast instead of saving a broken flow.
 * Returning to the Automation screen shows the new item at once
 * (AutomationActivity re-renders in onResume).
 */
class TemplatesActivity : LocalizedActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: TemplatesAdapter
    private val added = mutableSetOf<String>()
    private var query = ""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private sealed interface Row
    private data class Header(val kind: String) : Row
    private data class Item(val template: Templates.Template) : Row

    private fun allRows(): List<Row> {
        val q = query.lowercase()
        fun matches(t: Templates.Template): Boolean =
            q.isEmpty() || t.name.lowercase().contains(q) ||
                t.desc.lowercase().contains(q)
        val out = mutableListOf<Row>()
        val rs = Templates.rules.filter(::matches)
        if (rs.isNotEmpty()) {
            out.add(Header("rule"))
            rs.forEach { out.add(Item(it)) }
        }
        val fs = Templates.flows.filter(::matches)
        if (fs.isNotEmpty()) {
            out.add(Header("flow"))
            fs.forEach { out.add(Item(it)) }
        }
        return out
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_templates)

        listView = findViewById(R.id.lvTemplates)
        adapter = TemplatesAdapter()
        listView.adapter = adapter

        findViewById<View>(R.id.btnTemplatesBack).setOnClickListener { finish() }

        findViewById<EditText>(R.id.etTemplatesSearch)
            .addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim()?.lowercase().orEmpty()
                    adapter.refresh()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
    }

    private fun summaryFor(t: Templates.Template): String {
        val trigLabel = if (t.kind == "rule") AutoCatalog.label(this, "t", t.trigType)
            else FlowCatalog.label(this, "t", t.trigType)
        val trigPart = when {
            t.trigParam.isEmpty() -> trigLabel
            // v1.2.0: was hardcoded to "YouTube" for every
            // app_launched template — now the real app label
            // (Brave template etc.), falling back to the package.
            t.trigType == "app_launched" || t.trigType == "app_closed" -> {
                val app = Automation.appLabel(this, t.trigParam)
                    .ifEmpty { t.trigParam }
                "$trigLabel · $app"
            }
            t.trigType == "time_at_days" -> trigLabel
            t.trigParam.length <= 12 -> "$trigLabel · ${t.trigParam}"
            else -> trigLabel
        }
        val actLabels = t.actions.joinToString(" + ") { (type, _) ->
            if (t.kind == "rule") AutoCatalog.label(this, "a", type)
            else FlowCatalog.label(this, "a", type)
        }
        // v1.2.0: exit actions (rules only) read as "↩ <labels>".
        val exitPart = if (t.exitActions.isNotEmpty()) {
            val labels = t.exitActions.joinToString(" + ") { (type, _) ->
                AutoCatalog.label(this, "a", type)
            }
            " ↩ $labels"
        } else ""
        return "$trigPart → $actLabels$exitPart"
    }

    private fun addTemplate(t: Templates.Template) {
        if (t.kind == "rule") {
            val arr = JSONArray().put(Templates.buildRule(t))
            val n = Automation.importJson(this, arr.toString())
            if (n > 0) {
                added.add(t.key)
                adapter.refresh()
                Toast.makeText(this,
                    getString(R.string.template_added, t.name),
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            val flow = Templates.buildFlow(this, t)
            if (flow == null) {
                Toast.makeText(this, getString(R.string.template_geo_needed),
                    Toast.LENGTH_LONG).show()
                return
            }
            var id = flow.optString("id")
            var guard = 0
            while (FlowStore.loadFlow(this, id) != null && guard < 10) {
                id = "f" + System.currentTimeMillis().toString() + "_" + guard
                flow.put("id", id)
                guard++
            }
            FlowStore.saveFlow(this, flow)
            added.add(t.key)
            adapter.refresh()
            Toast.makeText(this,
                getString(R.string.template_added, t.name),
                Toast.LENGTH_SHORT).show()
        }
    }

    private inner class TemplatesAdapter : BaseAdapter() {
        private var rows: List<Row> = allRows()

        fun refresh() {
            rows = allRows()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size
        override fun getItem(pos: Int): Any = rows[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(pos: Int): Int =
            if (rows[pos] is Header) 0 else 1

        override fun getView(pos: Int, convertView: View?,
                             parent: ViewGroup?): View {
            return when (val row = rows[pos]) {
                is Header -> buildHeader(convertView, row)
                is Item -> buildRow(convertView, row.template)
            }
        }

        private fun buildHeader(convertView: View?, row: Header): View {
            val tv = (convertView as? TextView) ?: TextView(this@TemplatesActivity).apply {
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
                setTextColor(themeAccentColor())
                setPadding(dp(16), dp(14), dp(16), dp(4))
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            tv.text = getString(if (row.kind == "rule")
                R.string.templates_section_rules else R.string.templates_section_flows)
            return tv
        }

        private fun buildRow(convertView: View?,
                            t: Templates.Template): LinearLayout {
            val line = (convertView as? LinearLayout) ?: LinearLayout(this@TemplatesActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            // Rebuild children each bind: rows are cheap and the Add
            // button state changes after a tap.
            line.removeAllViews()
            val head = LinearLayout(this@TemplatesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(TextView(this@TemplatesActivity).apply {
                text = t.name
                textSize = 15f
                setTextColor(themeOnSurface())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            val isAdded = added.contains(t.key)
            head.addView(Button(this@TemplatesActivity).apply {
                text = getString(if (isAdded) R.string.templates_added
                    else R.string.templates_add)
                textSize = 12.5f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundResource(if (isAdded) R.drawable.bg_action_ghost
                    else R.drawable.bg_action_pill)
                setTextColor(if (isAdded) themeOnSurfaceVariant()
                    else themeAccentColor())
                isEnabled = !isAdded
                setOnClickListener { addTemplate(t) }
            })
            line.addView(head)
            line.addView(TextView(this@TemplatesActivity).apply {
                text = t.desc
                textSize = 12.5f
                setTextColor(themeOnSurfaceVariant())
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })
            line.addView(TextView(this@TemplatesActivity).apply {
                text = summaryFor(t)
                textSize = 12f
                setTextColor(themeOnSurfaceVariant())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })
            return line
        }
    }
}
