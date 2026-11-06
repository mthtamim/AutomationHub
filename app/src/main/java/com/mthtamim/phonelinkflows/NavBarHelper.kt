package com.mthtamim.phonelinkflows

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * v1.4.2: the Essentials pill bottom bar is back (v1.2.0) — the M3
 * NavigationBar + FAB from v1.4.0 is gone. The pill bar is a sage
 * rounded bar (layout/nav_pill_bar) with three tabs (Home / DIY /
 * Settings) and a circular + button at the right end. Selection is
 * painted MANUALLY here (selected tab = dark pill + light
 * text/icon, others = plain + dark text/icon) — there is no
 * NavigationBarView and no setSelectedItemId anywhere, so the
 * v1.4.1 StackOverflowError (a programmatic set re-firing the
 * OnItemSelectedListener in an infinite loop) cannot happen by
 * construction. Every main destination includes the pill bar
 * (id bottomNav in Main/Templates, autoBottomNav in Automation)
 * and calls setupPillBar after setContentView; the + button opens
 * the "new" sheet (Main/Templates) or the editor for the current
 * tab (Automation).
 */
object NavBarHelper {

    /** The three pill tabs, by the page id each one stands for. */
    private data class Tab(
        val pageId: Int,
        val tabView: Int,
        val iconView: Int,
        val textView: Int
    )

    private val TABS = listOf(
        Tab(R.id.navHome, R.id.navTabHome, R.id.navIcoHome, R.id.navTxtHome),
        Tab(R.id.navAutomation, R.id.navTabAuto, R.id.navIcoAuto, R.id.navTxtAuto),
        Tab(R.id.navSettings, R.id.navTabSettings, R.id.navIcoSettings, R.id.navTxtSettings)
    )

    /** Wire the pill bar included as [barId]. [selectedPageId] is
     *  the tab shown selected at setup; a tap on any tab goes to
     *  [onSelect] (each activity navigates its own way — Main
     *  switches pages in place, the others start activities);
     *  the + button goes to [onAdd] with the button as anchor.
     *  Tapping the already-selected tab still calls [onSelect] —
     *  the caller's showPage/selectNavTab treats it as a no-op. */
    fun setupPillBar(
        activity: Activity,
        barId: Int,
        selectedPageId: Int,
        onSelect: (Int) -> Unit,
        onAdd: (View) -> Unit
    ) {
        val root = activity.findViewById<View>(barId) ?: return
        for (tab in TABS) {
            root.findViewById<View>(tab.tabView)?.setOnClickListener {
                onSelect(tab.pageId)
            }
        }
        root.findViewById<View>(R.id.navAdd)?.setOnClickListener { v ->
            onAdd(v)
        }
        paintPillBar(activity, barId, selectedPageId)
    }

    /** Paint the pill selection: exactly one tab carries the dark
     *  pill background + light icon/text, the others are plain
     *  with dark icon/text. Safe to call any time after
     *  setContentView; a missing bar is a silent no-op. */
    fun paintPillBar(activity: Activity, barId: Int, selectedPageId: Int) {
        val root = activity.findViewById<View>(barId) ?: return
        val selBg = R.drawable.bg_nav_sel
        val selColor = ContextCompat.getColor(activity, R.color.ess_bar_sel_text)
        val plainColor = ContextCompat.getColor(activity, R.color.ess_bar_text)
        for (tab in TABS) {
            val selected = tab.pageId == selectedPageId
            val tabView = root.findViewById<View>(tab.tabView) ?: continue
            tabView.setBackgroundResource(if (selected) selBg else 0)
            root.findViewById<ImageView>(tab.iconView)
                ?.setColorFilter(if (selected) selColor else plainColor)
            root.findViewById<TextView>(tab.textView)
                ?.setTextColor(if (selected) selColor else plainColor)
        }
    }

    /** The "নতুন তৈরি করো" sheet (was the FAB sheet in v1.4.0-1.4.1,
     *  now opened from the pill bar's + button). New rule / new flow
     *  open the editor with the container-transform morph from the
     *  anchor; "from template" opens the Templates screen. */
    fun showNewSheet(activity: Activity, anchor: View) {
        val sheet = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.bottomsheet_new, null)
        view.findViewById<View>(R.id.sheetNewRule).setOnClickListener {
            sheet.dismiss()
            PLMotion.openWithTransform(activity,
                Intent(activity, RuleEditorActivity::class.java),
                anchor, "fab_new_rule", ThemeColors.primary(activity))
        }
        view.findViewById<View>(R.id.sheetNewFlow).setOnClickListener {
            sheet.dismiss()
            PLMotion.openWithTransform(activity,
                Intent(activity, FlowEditorActivity::class.java),
                anchor, "fab_new_flow", ThemeColors.primary(activity))
        }
        view.findViewById<View>(R.id.sheetNewTemplate).setOnClickListener {
            sheet.dismiss()
            activity.startActivity(
                Intent(activity, TemplatesActivity::class.java))
        }
        sheet.setContentView(view)
        sheet.show()
    }
}
