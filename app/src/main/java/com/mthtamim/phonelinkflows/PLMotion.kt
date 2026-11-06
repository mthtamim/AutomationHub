package com.mthtamim.phonelinkflows

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.PathInterpolator
import android.view.animation.TimeInterpolator
import androidx.core.view.doOnPreDraw
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

/**
 * v1.2.2: the single place for Material Design 3 motion. Every
 * animation in the app goes through the library's own transition
 * classes (material 1.12.0) with the M3 emphasized easing and the
 * M3 duration scale — no hand-rolled curves, no generic slide/fade.
 *
 * Duration scale (M3): small 50–100 ms, medium 200–300 ms,
 * large 400–500 ms. Easing: emphasized cubic-bezier(0.2, 0, 0, 1).
 *
 * Motion reference for this pass: the real Essentials app
 * (Compose + M3 Expressive) as the user showed it in a screen
 * recording. The target he corrected us to is its PERFORMANCE
 * and SMOOTHNESS — stable frame rate, no dropped frames on
 * scroll or screen opens, instant touch response — not its nav
 * bar: the morphing bottom bar was explicitly demoted to
 * nice-to-have (2026-11-06), so it is NOT in this pass.
 */
object PLMotion {

    /** M3 emphasized easing: cubic-bezier(0.2, 0, 0, 1). */
    val EMPHASIZED: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    const val DURATION_SMALL = 100L
    const val DURATION_MEDIUM = 300L
    const val DURATION_LARGE = 450L

    const val EXTRA_SHARED_ELEMENT_NAME = "pl_shared_element_name"
    const val EXTRA_START_COLOR = "pl_start_color"

    fun fadeThrough(): MaterialFadeThrough = MaterialFadeThrough().apply {
        duration = DURATION_MEDIUM
        interpolator = EMPHASIZED
    }

    fun sharedAxisX(forward: Boolean): MaterialSharedAxis =
        MaterialSharedAxis(MaterialSharedAxis.X, forward).apply {
            duration = DURATION_MEDIUM
            interpolator = EMPHASIZED
        }

    fun containerTransform(): MaterialContainerTransform =
        MaterialContainerTransform().apply {
            duration = DURATION_LARGE
            interpolator = EMPHASIZED
            fadeMode = MaterialContainerTransform.FADE_MODE_IN
            fitMode = MaterialContainerTransform.FIT_MODE_AUTO
        }

    /** Default window transitions for every activity (called from
     *  LocalizedActivity.onCreate, after super.onCreate and before
     *  setContentView): fade-through in/out, M3 medium duration,
     *  emphasized easing. This replaces the generic 200 ms slide /
     *  150 ms fade that windowAnimationStyle (PL.ActivityAnim) gave
     *  Theme.PhoneLink in v1.2.1. */
    fun applyDefaultWindowTransitions(activity: Activity) {
        try {
            activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            activity.window.enterTransition = fadeThrough()
            activity.window.exitTransition = fadeThrough()
            activity.window.reenterTransition = fadeThrough()
            activity.window.returnTransition = fadeThrough()
        } catch (_: Exception) { }
    }

    /** Open an activity with a container-transform morph from a
     *  shared view (a rule/flow card or the + FAB). The shared
     *  view's transition name is unique per item so the morph
     *  finds its way back. [startColor] is the shared view's fill
     *  (surface for a card, primary for the FAB); 0 = surface. */
    fun openWithTransform(
        activity: Activity,
        intent: Intent,
        sharedView: View,
        transitionName: String,
        startColor: Int = 0
    ) {
        sharedView.transitionName = transitionName
        intent.putExtra(EXTRA_SHARED_ELEMENT_NAME, transitionName)
        if (startColor != 0) intent.putExtra(EXTRA_START_COLOR, startColor)
        val options = ActivityOptions.makeSceneTransitionAnimation(
            activity, sharedView, transitionName)
        activity.startActivity(intent, options.toBundle())
    }

    /** Editor side — call right after setContentView. Installs the
     *  container transform for the shared element named in the
     *  intent (if the editor was opened without one, e.g. from a
     *  deep link, this is a no-op and the default fade-through
     *  plays instead). The enter transition is postponed until
     *  the first frame is laid out, so the morph never starts on
     *  an unlaid-out screen — that is what keeps a heavy editor
     *  open from dropping frames mid-morph. Enter: card/FAB
     *  colour -> window background; return: the same transform
     *  reversed. Caveat: on glass themes the window background
     *  is a gradient drawable, so the morph's end colour is the
     *  flat colorBackground and the gradient fades in behind it
     *  via the fade-through — phone check needed. */
    fun setupEditorTransform(activity: Activity) {
        val name = activity.intent.getStringExtra(EXTRA_SHARED_ELEMENT_NAME) ?: return
        try {
            val startColor = activity.intent.getIntExtra(EXTRA_START_COLOR, 0)
                .takeIf { it != 0 } ?: ThemeColors.surface(activity)
            val endColor = ThemeColors.background(activity)
            val enter = containerTransform().apply {
                startContainerColor = startColor
                endContainerColor = endColor
            }
            val ret = containerTransform().apply {
                startContainerColor = endColor
                endContainerColor = startColor
            }
            activity.window.sharedElementEnterTransition = enter
            activity.window.sharedElementReturnTransition = ret
            activity.findViewById<View>(android.R.id.content).transitionName = name
            activity.postponeEnterTransition()
            activity.findViewById<View>(android.R.id.content).doOnPreDraw {
                activity.startPostponedEnterTransition()
            }
        } catch (_: Exception) { }
    }

    /** Run a change inside a shared-axis transition on a scene
     *  root (tab switch, in-activity page switch). The change
     *  runs synchronously; the transition animates it. */
    fun beginSharedAxisX(sceneRoot: ViewGroup, forward: Boolean, change: () -> Unit) {
        TransitionManager.beginDelayedTransition(sceneRoot, sharedAxisX(forward))
        change()
    }
}
