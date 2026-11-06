package com.mthtamim.phonelinkflows

/** Process-wide flags for the app lock. (AutomationHub: the
 *  SnapService one-shot flags are gone with SnapService.) */
object SessionState {
    @Volatile var unlocked: Boolean = false
}
