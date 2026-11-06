package com.mthtamim.phonelinkflows

import android.app.Activity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * v2.20: the ONE Study Guard list manager (zero-duplicate
 * rule): multiple named study-app lists, created / renamed /
 * deleted / app-edited here and nowhere else. Two doors open
 * it — the Study Guard card on the Automation screen and the
 * "তালিকা পরিচালনা" button inside the Study Guard trigger
 * card in the rule editor. Storage is Config's (phonelink_cfg
 * "study_lists"); the app multi-picker reuses the App Locker's
 * launchable-apps list, so labels always match what the user
 * sees on the phone.
 */
object StudyGuardUi {

    /** The manager: every list with its app count; tap a list
     *  for its actions, or start a new one. After any change the
     *  manager re-opens on fresh data so the result is visible;
     *  Cancel/back leaves. */
    fun showManager(activity: Activity, onChanged: () -> Unit) {
        val lists = Config.studyLists(activity)
        val b = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sg_manager_title)
        if (lists.isEmpty()) {
            b.setMessage(R.string.sg_manager_empty)
        } else {
            val labels = lists.map {
                it.name + " — " + activity.getString(
                    R.string.sg_apps_count, it.pkgs.size)
            }.toTypedArray()
            b.setItems(labels) { _, which ->
                listActions(activity, lists[which], onChanged)
            }
        }
        b.setPositiveButton(R.string.sg_new_list) { _, _ ->
            createList(activity) {
                onChanged()
                showManager(activity, onChanged)
            }
        }
        b.setNegativeButton(R.string.btn_cancel, null)
        b.show()
    }

    /** Name prompt → app picker → saved list (id minted by
     *  Config on save). Cancelling the app picker aborts the
     *  creation — nothing half-made is left behind. */
    fun createList(activity: Activity,
                   onCreated: (Config.StudyList) -> Unit) {
        namePrompt(activity, null) { named ->
            editApps(activity, named, onCreated)
        }
    }

    private fun listActions(activity: Activity,
                            list: Config.StudyList,
                            onChanged: () -> Unit) {
        val items = arrayOf(
            activity.getString(R.string.sg_action_edit_apps),
            activity.getString(R.string.sg_action_rename),
            activity.getString(R.string.sg_action_delete))
        MaterialAlertDialogBuilder(activity)
            .setTitle(list.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> editApps(activity, list) {
                        onChanged()
                        showManager(activity, onChanged)
                    }
                    1 -> namePrompt(activity, list) {
                        onChanged()
                        showManager(activity, onChanged)
                    }
                    else -> MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.sg_delete_title)
                        .setMessage(activity.getString(
                            R.string.sg_delete_confirm, list.name))
                        .setPositiveButton(
                            R.string.sg_action_delete) { _, _ ->
                            Config.deleteStudyList(activity, list.id)
                            onChanged()
                            showManager(activity, onChanged)
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Name entry. For an EXISTING list the rename saves at
     *  once; for a new one the (still unsaved) list is handed
     *  on so the caller's next step decides persistence. */
    private fun namePrompt(activity: Activity,
                           existing: Config.StudyList?,
                           onSaved: (Config.StudyList) -> Unit) {
        val et = TextInputEditText(activity).apply {
            hint = activity.getString(R.string.sg_list_name_hint)
            setSingleLine()
            if (existing != null) {
                setText(existing.name)
                setSelection(text?.length ?: 0)
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.sg_new_list
                else R.string.sg_action_rename)
            .setView(et)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = et.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        activity.getString(R.string.sg_name_empty),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                if (existing != null) {
                    onSaved(Config.saveStudyList(activity,
                        Config.StudyList(existing.id, name,
                            existing.pkgs)))
                } else {
                    onSaved(Config.StudyList("", name, emptySet()))
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Multi-select over the launchable apps. Packages already
     *  in the list but no longer launchable (uninstalled since)
     *  are preserved on save — they simply never match. */
    private fun editApps(activity: Activity,
                         list: Config.StudyList,
                         onSaved: (Config.StudyList) -> Unit) {
        val apps = AppLocker.launchableApps(activity)
        if (apps.isEmpty()) {
            Snackbar.make(
                activity.findViewById(android.R.id.content),
                activity.getString(R.string.sg_no_apps),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val labels = apps.map { it.label + "\n" + it.pkg }
            .toTypedArray()
        val checked = BooleanArray(apps.size) {
            apps[it].pkg in list.pkgs
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(
                R.string.sg_pick_apps_title, list.name))
            .setMultiChoiceItems(labels, checked) { _, which, on ->
                checked[which] = on
            }
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val picked = apps.filterIndexed { i, _ -> checked[i] }
                    .map { it.pkg }.toMutableSet()
                val launchable = apps.map { it.pkg }.toSet()
                picked.addAll(list.pkgs.filter {
                    it !in launchable })
                onSaved(Config.saveStudyList(activity,
                    Config.StudyList(list.id, list.name, picked)))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
