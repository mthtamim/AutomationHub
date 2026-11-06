package com.mthtamim.phonelinkflows

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.animation.TimeInterpolator
import android.view.animation.PathInterpolator
import androidx.core.view.doOnPreDraw
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialSharedAxis

/**
 * v1.2.2: the single place for Material Design 3 motion. View-level
 * animations go through the library's own transition classes
 * (material 1.12.0) with the M3 emphasized easing and the M3
 * duration scale. Window transitions are different: they require
 * framework android.transition.Transition, so they use framework
 * Fade / ChangeBounds with the same M3 timing instead (CI run #16
 * proved the Material classes cannot be assigned to a window).
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

    /** Framework fade for window transitions (M3 medium duration,
     *  emphasized easing). Window transitions require framework
     *  android.transition.Transition — the Material (AndroidX)
     *  transition classes cannot be assigned to them (CI run #16:
     *  type mismatch), so the fade-through here is a framework
     *  Fade with the same M3 timing. */
    private fun frameworkFade(): android.transition.Fade =
        android.transition.Fade().apply {
            duration = DURATION_MEDIUM
            interpolator = EMPHASIZED
        }

    /** Framework bounds-morph for the editor shared element
     *  (ChangeBounds + Fade, M3 large duration, emphasized
     *  easing). Same reason as frameworkFade: window transitions
     *  require framework Transition, so the morph is a bounds
     *  morph + content fade rather than the Material colour
     *  morph — the M3 timing and the morph itself are kept. */
    private fun frameworkMorph(): android.transition.TransitionSet =
        android.transition.TransitionSet().apply {
            ordering = android.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(android.transition.ChangeBounds().apply {
                duration = DURATION_LARGE
                interpolator = EMPHASIZED
            })
            addTransition(android.transition.Fade(android.transition.Fade.MODE_IN).apply {
                duration = DURATION_LARGE
                interpolator = EMPHASIZED
            })
            duration = DURATION_LARGE
            interpolator = EMPHASIZED
        }

    fun sharedAxisX(forward: Boolean): MaterialSharedAxis =
        MaterialSharedAxis(MaterialSharedAxis.X, forward).apply {
            duration = DURATION_MEDIUM
            interpolator = EMPHASIZED
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
            activity.window.enterTransition = frameworkFade()
            activity.window.exitTransition = frameworkFade()
            activity.window.reenterTransition = frameworkFade()
            activity.window.returnTransition = frameworkFade()
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
     *  shared-element morph for the shared element named in the
     *  intent (if the editor was opened without one, e.g. from a
     *  deep link, this is a no-op and the default fade-through
     *  plays instead). The enter transition is postponed until
     *  the first frame is laid out, so the morph never starts on
     *  an unlaid-out screen — that is what keeps a heavy editor
     *  open from dropping frames mid-morph. The morph is a
     *  framework ChangeBounds + Fade (bounds morph + content
     *  fade); the Material colour morph does not apply to window
     *  transitions, so EXTRA_START_COLOR is carried but unused. */
    fun setupEditorTransform(activity: Activity) {
        val name = activity.intent.getStringExtra(EXTRA_SHARED_ELEMENT_NAME) ?: return
        try {
            activity.window.sharedElementEnterTransition = frameworkMorph()
            activity.window.sharedElementReturnTransition = frameworkMorph()
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
