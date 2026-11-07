package com.mthtamim.phonelinkflows

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.json.JSONObject

/**
 * Quick Settings trigger tile (new v1.4.6): tapping it fires the
 * "qs_tile" trigger into both engines — any rule or flow with a
 * qs_tile trigger runs. Unlike AutomationTileService (master
 * toggle) this tile has no on/off state: it stays INACTIVE and
 * just fires on every tap. The tile never adds itself: on
 * Android 13+ MainActivity offers StatusBarManager.
 * requestAddTileService, otherwise the user adds it from the QS
 * panel's Edit screen (see README).
 */
class TriggerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        try { Automation.onEvent(this, "qs_tile", JSONObject()) } catch (_: Exception) { }
        try { FlowEngine.onEvent(this, "qs_tile", JSONObject()) } catch (_: Exception) { }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.label = "AutomationHub ট্রিগার"
        tile.subtitle = getString(R.string.tile_trigger_hint)
        tile.state = Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nav_auto)
        tile.updateTile()
    }
}
