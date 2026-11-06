package com.mthtamim.phonelinkflows

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Log screen (new v1.4.0): the preview's dedicated "কার্যকলাপের
 * লগ" sub-screen — NOT a nav tab, so it has a back button and no
 * nav bar / FAB. Two sources, newest first:
 *  - Automation log (timestamped, via Automation.recentLogRaw):
 *    shown under আজ / গতকাল / সব, each row with its time.
 *  - FlowStore log (plain strings, NO timestamps, via its existing
 *    recentLog()): shown ONLY under সব, with no time shown.
 * Day filters use the device default timezone — the same basis as
 * the stored millis. "মুছে ফেলো" asks first, then clears BOTH logs.
 */
class LogActivity : LocalizedActivity() {

    private enum class DayFilter { TODAY, YESTERDAY, ALL }

    private data class Entry(val time: Long?, val msg: String)

    private lateinit var listView: ListView
    private lateinit var adapter: LogAdapter
    private var filter = DayFilter.TODAY

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val timeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    private fun isSameDay(t: Long, ref: Calendar): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == ref.get(Calendar.DAY_OF_YEAR)
    }

    private fun loadEntries(): List<Entry> {
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        val auto = Automation.recentLogRaw(this) // newest first already
        return when (filter) {
            DayFilter.TODAY -> auto
                .filter { isSameDay(it.first, now) }
                .map { Entry(it.first, it.second) }
            DayFilter.YESTERDAY -> auto
                .filter { isSameDay(it.first, yesterday) }
                .map { Entry(it.first, it.second) }
            DayFilter.ALL -> {
                val out = auto.map { Entry(it.first, it.second) }.toMutableList()
                // FlowStore lines carry no timestamp: newest first
                // means the stored order reversed, time left blank.
                FlowStore.recentLog(this).asReversed()
                    .forEach { out.add(Entry(null, it)) }
                out
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        listView = findViewById(R.id.lvLog)
        adapter = LogAdapter()
        listView.adapter = adapter
        listView.emptyView = findViewById(R.id.tvLogEmpty)

        findViewById<View>(R.id.btnLogBack).setOnClickListener { finish() }

        findViewById<ChipGroup>(R.id.cgLogFilter)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                filter = when (checkedIds.firstOrNull()) {
                    R.id.chipLogYesterday -> DayFilter.YESTERDAY
                    R.id.chipLogAll -> DayFilter.ALL
                    else -> DayFilter.TODAY
                }
                refresh()
            }

        findViewById<View>(R.id.chipLogClear).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_clear_title)
                .setMessage(R.string.log_clear_confirm)
                .setPositiveButton(R.string.log_clear_yes) { _, _ ->
                    Automation.clearLog(this)
                    FlowStore.clearLog(this)
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.setEntries(loadEntries())
    }

    private inner class LogAdapter : BaseAdapter() {
        private var entries: List<Entry> = emptyList()

        fun setEntries(next: List<Entry>) {
            entries = next
            notifyDataSetChanged()
        }

        override fun getCount(): Int = entries.size
        override fun getItem(pos: Int): Any = entries[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?,
                             parent: ViewGroup?): View {
            val entry = entries[pos]
            val line = (convertView as? android.widget.LinearLayout)
                ?: android.widget.LinearLayout(this@LogActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    layoutParams = android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            line.removeAllViews()
            line.addView(TextView(this@LogActivity).apply {
                text = entry.msg
                textSize = 14.5f
                setTextColor(themeOnSurface())
            })
            if (entry.time != null) {
                line.addView(TextView(this@LogActivity).apply {
                    text = timeFmt.format(Date(entry.time))
                    textSize = 12f
                    setTextColor(themeOnSurfaceVariant())
                    setPadding(0, dp(3), 0, 0)
                })
            }
            return line
        }
    }
}
