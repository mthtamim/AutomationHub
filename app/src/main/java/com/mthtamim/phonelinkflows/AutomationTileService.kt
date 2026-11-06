package com.mthtamim.phonelinkflows

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile (new 2026-11-06): toggles the Automation
 * master switch (Config "automation_on") from the notification
 * shade, without opening the app. The tile state mirrors the
 * switch: ACTIVE = automation on. This writes exactly the same
 * preference the Settings master toggle writes (Config.setToggle),
 * so the engine picks it up the same way — no service restart,
 * no extra behaviour. The tile never adds itself: on Android 13+
 * MainActivity offers StatusBarManager.requestAddTileService, otherwise the
 * user adds it from the QS panel's Edit screen (see README).
 */
class AutomationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val on = !Config.automationOn(this)
        Config.setToggle(this, "automation_on", on)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Config.automationOn(this)
        tile.label = "AutomationHub"
        tile.subtitle = getString(if (on) R.string.tile_state_on
            else R.string.tile_state_off)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nav_auto)
        tile.updateTile()
    }
}
