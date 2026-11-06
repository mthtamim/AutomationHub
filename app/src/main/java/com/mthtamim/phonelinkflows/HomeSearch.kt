package com.mthtamim.phonelinkflows

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.search.SearchBar
import com.google.android.material.textfield.TextInputEditText

/**
 * v1.4.0: the preview's Home SearchBar — tap the bar to open an
 * inline search over rules, flows and templates (the preview's
 * "রুল, ফ্লো বা টেমপ্লেট খোঁজো"). A rule hit opens its editor, a
 * flow hit opens the flow editor, a template hit opens the
 * Templates screen with the query prefilled. The search box lives
 * inside page_home (no overlay activity): Back closes it first
 * (MainActivity's back callback asks closeIfOpen()), and leaving
 * Home closes it too. Data is re-read on every query change, the
 * same way refreshStatus re-parses rules/flows.
 */
class HomeSearch(private val activity: Activity) {

    private data class Hit(val kind: Kind, val key: String, val title: String, val sub: String)
    private enum class Kind { RULE, FLOW, TEMPLATE }

    private lateinit var searchBar: SearchBar
    private lateinit var box: View
    private lateinit var queryField: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var emptyView: TextView
    private val adapter = HitsAdapter { hit -> openHit(hit) }
    private var query = ""

    fun setup(pageHome: View) {
        searchBar = pageHome.findViewById(R.id.searchBar)
        box = pageHome.findViewById(R.id.searchBox)
        queryField = pageHome.findViewById(R.id.etHomeSearch)
        results = pageHome.findViewById(R.id.rvSearchResults)
        emptyView = pageHome.findViewById(R.id.tvSearchEmpty)
        results.layoutManager = LinearLayoutManager(activity)
        results.adapter = adapter
        searchBar.setOnClickListener { open() }
        queryField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                refresh()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    val isOpen: Boolean get() = ::box.isInitialized && box.visibility == View.VISIBLE

    private fun open() {
        if (isOpen) return
        box.visibility = View.VISIBLE
        refresh()
        queryField.requestFocus()
        val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.showSoftInput(queryField, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Hide the search box (and clear the query). True if it was open. */
    fun closeIfOpen(): Boolean {
        if (!isOpen) return false
        box.visibility = View.GONE
        if (queryField.text?.isNotEmpty() == true) queryField.setText("")
        val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(queryField.windowToken, 0)
        return true
    }

    private fun refresh() {
        val q = query.lowercase()
        val hits = mutableListOf<Hit>()
        if (q.isNotEmpty()) {
            for (rule in Automation.rules(activity)) {
                val name = rule.optString("name")
                if (name.lowercase().contains(q)) {
                    hits.add(Hit(Kind.RULE, rule.optString("id"), name,
                        activity.getString(R.string.templates_section_rules)))
                }
            }
            for (flow in FlowStore.flows(activity)) {
                val name = flow.optString("name")
                if (name.lowercase().contains(q)) {
                    hits.add(Hit(Kind.FLOW, flow.optString("id"), name,
                        activity.getString(R.string.templates_section_flows)))
                }
            }
            for (t in Templates.rules) {
                if (t.name.lowercase().contains(q) ||
                    t.desc.lowercase().contains(q)) {
                    hits.add(Hit(Kind.TEMPLATE, t.key, t.name,
                        activity.getString(R.string.nav_templates) + " · " +
                            activity.getString(R.string.templates_section_rules)))
                }
            }
            for (t in Templates.flows) {
                if (t.name.lowercase().contains(q) ||
                    t.desc.lowercase().contains(q)) {
                    hits.add(Hit(Kind.TEMPLATE, t.key, t.name,
                        activity.getString(R.string.nav_templates) + " · " +
                            activity.getString(R.string.templates_section_flows)))
                }
            }
        }
        adapter.submit(hits)
        results.visibility = if (hits.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility =
            if (q.isNotEmpty() && hits.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openHit(hit: Hit) {
        closeIfOpen()
        when (hit.kind) {
            Kind.RULE -> activity.startActivity(
                Intent(activity, RuleEditorActivity::class.java)
                    .putExtra("rule_id", hit.key))
            Kind.FLOW -> activity.startActivity(
                Intent(activity, FlowEditorActivity::class.java)
                    .putExtra("flow_id", hit.key))
            Kind.TEMPLATE -> activity.startActivity(
                Intent(activity, TemplatesActivity::class.java)
                    .putExtra("query", query))
        }
    }

    private inner class HitsAdapter(private val onTap: (Hit) -> Unit) :
        RecyclerView.Adapter<HitsAdapter.Holder>() {

        private var hits: List<Hit> = emptyList()

        fun submit(list: List<Hit>) {
            hits = list
            notifyDataSetChanged()
        }

        inner class Holder(val root: LinearLayout, val title: TextView, val sub: TextView) :
            RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val pad = (16 * density).toInt()
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, (12 * density).toInt(), pad, (12 * density).toInt())
                // Ripple: resolve ?attr/selectableItemBackground (an
                // attr id is not a drawable — resolve it first).
                val out = TypedValue()
                if (ctx.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, out, true)) {
                    setBackgroundResource(out.resourceId)
                }
                isClickable = true
                isFocusable = true
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val title = TextView(ctx).apply {
                textSize = 15.5f
                setTextColor(ThemeColors.onSurface(ctx))
            }
            val sub = TextView(ctx).apply {
                textSize = 13f
                setTextColor(ThemeColors.onSurfaceVariant(ctx))
            }
            root.addView(title)
            root.addView(sub)
            return Holder(root, title, sub)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val hit = hits[position]
            holder.title.text = hit.title
            holder.sub.text = hit.sub
            holder.root.setOnClickListener { onTap(hit) }
        }

        override fun getItemCount(): Int = hits.size
    }
}
