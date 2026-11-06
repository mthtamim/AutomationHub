package com.mthtamim.phonelinkflows

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView

/**
 * v1.4.0: the preview's 4-item M3 NavigationBar + FAB, shared by the
 * four main destinations (MainActivity, AutomationActivity,
 * TemplatesActivity, LogActivity). Every destination includes
 * layout/nav_m3_bar (id bottomNav in Main/Templates/Log,
 * autoBottomNav in Automation) and calls setupBar + setupFab after
 * setContentView. The bar's selection is painted by the
 * NavigationBarView itself (M3 secondaryContainer indicator) — no
 * manual paint code anywhere; the old pill painter
 * (MainActivity.selectNavTab / AutomationActivity selectNavTab) is
 * gone with the pill bar. The FAB opens the preview's "new" bottom
 * sheet (new rule / new flow / from template); the editors open
 * with the same container-transform morph the old + button used.
 */
object NavBarHelper {

    /** Wire the NavigationBarView included as [barId]. [selectedId]
     *  is one of R.id.nav_home / nav_diy / nav_templates /
     *  nav_settings (menu/nav_main.xml). A tap on the already
     *  selected item is a no-op; any other tap goes to [onSelect]
     *  (each activity navigates its own way — MainActivity switches
     *  pages in place, the others start activities). */
    fun setupBar(activity: Activity, barId: Int, selectedId: Int,
                 onSelect: (Int) -> Unit) {
        val bar = activity.findViewById<NavigationBarView>(barId) ?: return
        bar.selectedItemId = selectedId
        bar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedId) return@setOnItemSelectedListener true
            onSelect(item.itemId)
            true
        }
    }

    /** Wire the FAB inside the nav_m3_bar include. Returns the FAB
     *  so MainActivity can hide it while a sub-screen is open (the
     *  preview hides the FAB on sub-screens). */
    fun setupFab(activity: Activity): FloatingActionButton? {
        val fab = activity.findViewById<FloatingActionButton>(R.id.navFab)
            ?: return null
        fab.setOnClickListener { v -> showNewSheet(activity, v) }
        return fab
    }

    /** The preview's "নতুন তৈরি করো" sheet. New rule / new flow open
     *  the editor with the container-transform morph from the FAB;
     *  "from template" opens the Templates screen. */
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
