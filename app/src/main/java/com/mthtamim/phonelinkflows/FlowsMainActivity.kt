package com.mthtamim.phonelinkflows

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject

/**
 * Flows home screen — standalone-app mirror of the Flows tab that
 * used to live inside PhoneLink's Automation screen (renderFlows /
 * showFlowMenu in PhoneLink v2.25.2). Same cards, same buttons,
 * same service row, same overflow menu; only the Rules chrome
 * around it is gone.
 *
 * Skeleton note: this is a code-only extraction. It has not been
 * compiled by CI and has not run on a phone.
 */
class FlowsMainActivity : LocalizedActivity() {

    private lateinit var container: LinearLayout

    private companion object {
        const val MENU_IMPORT = 1
        const val MENU_FLO = 2
        const val MENU_EXPORT = 3
        const val MENU_BACKUP = 4
        const val MENU_RESTORE = 5
        const val MENU_LOG = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply {
            setBackgroundColor(themeOnBackground())
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(container)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 24, 24, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(themeOnSurface())
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(themeOnSurface())
            setOnClickListener {
                startActivity(Intent(this@FlowsMainActivity,
                    FlowsSettingsActivity::class.java))
            }
        })
        header.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(themeOnSurface())
            setOnClickListener { showFlowMenu(it) }
        })
        stack.addView(header)
        stack.addView(scroll)
        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setOnClickListener {
                startActivity(Intent(this@FlowsMainActivity,
                    FlowEditorActivity::class.java))
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, 32, 32)
                }
        }
        root.addView(stack)
        root.addView(fab)
        setContentView(root)
        renderFlows()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) renderFlows()
    }

    private fun pillButton(labelRes: Int, mint: Boolean) = Button(this).apply {
        text = getString(labelRes)
        textSize = 12.5f
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        setBackgroundResource(
            if (mint) R.drawable.bg_action_pill
            else R.drawable.bg_action_ghost)
        setPadding(40, 30, 40, 30)
        setTextColor(
            if (mint) themeAccentColor() else themeOnSurface())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(8, 0, 0, 0) }
    }

    private fun renderFlows() {
        container.removeAllViews()
        val svcOn = FlowStore.flowServiceOn(this)
        val svcRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 12) }
            setBackgroundResource(R.drawable.card_bg)
        }
        val svcHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        svcHead.addView(TextView(this).apply {
            text = getString(R.string.flow_service_switch)
            textSize = 16f
            setTextColor(themeOnSurface())
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        svcHead.addView(Switch(this).apply {
            isChecked = svcOn
            setOnCheckedChangeListener { _, on ->
                FlowStore.setFlowServiceOn(this@FlowsMainActivity, on)
                if (on) {
                    ContextCompat.startForegroundService(
                        this@FlowsMainActivity,
                        Intent(this@FlowsMainActivity, FlowService::class.java))
                } else {
                    stopService(Intent(this@FlowsMainActivity,
                        FlowService::class.java))
                }
                renderFlows()
            }
        })
        svcRow.addView(svcHead)
        val tick = FlowStore.serviceLastTick(this)
        val ago = (System.currentTimeMillis() - tick) / 1000
        svcRow.addView(TextView(this).apply {
            text = when {
                !svcOn -> getString(R.string.flow_service_off)
                tick == 0L || ago > 120 -> getString(R.string.flow_service_stale)
                else -> getString(R.string.flow_service_live, ago.toString())
            }
            textSize = 13f
            setTextColor(themeOnSurfaceVariant())
            setPadding(0, 6, 0, 0)
        })
        container.addView(svcRow)
        val flows = FlowStore.flows(this)
        if (flows.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.flow_empty)
                setTextColor(themeOnSurfaceVariant())
                setPadding(8, 24, 8, 24)
            })
            return
        }
        for (flow in flows) {
            val flowId = flow.optString("id")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, 12) }
                setBackgroundResource(R.drawable.card_bg)
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            head.addView(TextView(this).apply {
                text = flow.optString("name")
                    .ifEmpty { getString(R.string.flow_tab_flows) }
                textSize = 16f
                setTextColor(themeOnSurface())
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(Switch(this).apply {
                isChecked = flow.optBoolean("enabled", true)
                setOnCheckedChangeListener { _, on ->
                    FlowStore.setFlowEnabled(
                        this@FlowsMainActivity, flowId, on)
                }
            })
            row.addView(head)
            val nodes = flow.optJSONArray("nodes")
            var summary = ""
            if (nodes != null) {
                for (k in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(k) ?: continue
                    if (n.optString("kind") == "t") {
                        val label = FlowCatalog.label(
                            this, "t", n.optString("type"))
                        val param = n.optString("param")
                        summary = if (param.isEmpty()) label
                            else label + " · " + param
                        break
                    }
                }
            }
            if (summary.isNotEmpty()) {
                row.addView(TextView(this).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(themeOnSurfaceVariant())
                    setPadding(0, 6, 0, 4)
                })
            }
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            buttons.addView(pillButton(R.string.flow_card_run, true).apply {
                setOnClickListener {
                    val f = FlowStore.loadFlow(
                        this@FlowsMainActivity, flowId)
                    if (f != null) FlowEngine.runFlow(
                        this@FlowsMainActivity, f)
                }
            })
            buttons.addView(pillButton(R.string.flow_card_edit, false).apply {
                setOnClickListener {
                    startActivity(Intent(this@FlowsMainActivity,
                        FlowEditorActivity::class.java)
                        .putExtra("flow_id", flowId))
                }
            })
            buttons.addView(pillButton(R.string.flow_card_dup, false).apply {
                setOnClickListener {
                    val copy = JSONObject(flow.toString())
                    copy.put("id", "f" + System.currentTimeMillis().toString())
                    copy.put("name", flow.optString("name") +
                        getString(R.string.flow_dup_suffix))
                    FlowStore.saveFlow(this@FlowsMainActivity, copy)
                    renderFlows()
                }
            })
            buttons.addView(pillButton(R.string.flow_card_log, false).apply {
                setOnClickListener {
                    val lines = FlowStore.flowLog(
                        this@FlowsMainActivity, flowId)
                    AlertDialog.Builder(this@FlowsMainActivity)
                        .setTitle(flow.optString("name"))
                        .setMessage(if (lines.isEmpty())
                            getString(R.string.flow_log_empty)
                        else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            })
            buttons.addView(pillButton(R.string.flow_card_delete, false).apply {
                setOnClickListener {
                    AlertDialog.Builder(this@FlowsMainActivity)
                        .setTitle(flow.optString("name"))
                        .setMessage(getString(R.string.flow_delete_confirm,
                            flow.optString("name")))
                        .setPositiveButton(R.string.btn_delete) { _, _ ->
                            FlowStore.deleteFlow(
                                this@FlowsMainActivity, flowId)
                            renderFlows()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            })
            scroll.addView(buttons)
            row.addView(scroll)
            container.addView(row)
        }
    }

    private fun showFlowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_IMPORT, 0, getString(R.string.flow_menu_import))
        popup.menu.add(0, MENU_FLO, 1, getString(R.string.flow_menu_flo_import))
        popup.menu.add(0, MENU_EXPORT, 2, getString(R.string.flow_menu_export))
        popup.menu.add(0, MENU_BACKUP, 3, getString(R.string.flow_menu_backup))
        popup.menu.add(0, MENU_RESTORE, 4, getString(R.string.flow_menu_restore))
        popup.menu.add(0, MENU_LOG, 5, getString(R.string.flow_menu_log))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_IMPORT -> startActivity(
                    Intent(this, FlowFlowImportActivity::class.java))
                MENU_FLO -> startActivity(
                    Intent(this, FlowFloImportActivity::class.java))
                MENU_EXPORT -> {
                    val arr = org.json.JSONArray()
                    for (flow in FlowStore.flows(this)) {
                        arr.put(flow)
                    }
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TEXT, arr.toString())
                    startActivity(Intent.createChooser(
                        send, getString(R.string.flow_menu_export)))
                }
                MENU_BACKUP -> {
                    if (!FlowIO.exportBackup(this)) {
                        android.widget.Toast.makeText(this,
                            getString(R.string.flow_export_failed),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                MENU_RESTORE -> startActivity(
                    Intent(this, FlowRestoreActivity::class.java))
                MENU_LOG -> {
                    val lines = FlowStore.recentLog(this)
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.flow_menu_log))
                        .setMessage(if (lines.isEmpty())
                            getString(R.string.flow_log_empty)
                        else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }
}
