package com.waenhancer.app.tiles

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.waenhancer.app.R
import com.waenhancer.config.PreferenceStores
import com.waenhancer.core.preferences.WaexPreferenceManager
import com.waenhancer.utils.UiText
import com.waenhancer.utils.WhatsAppPackageDetector
import com.waenhancer.xposed.utils.AppRestartHelper

/**
 * A Quick Settings switch for one WhatsApp-side mode. These modes are read when WhatsApp starts,
 * so a change stops the hooked WhatsApp processes; they pick it up when they start again.
 */
abstract class WaexToggleTile : TileService() {
    /** Preference the WhatsApp hooks read. */
    protected abstract val activeKey: String

    /** Feature switch that has to be on for [activeKey] to do anything. */
    protected abstract val featureKey: String
    protected abstract val iconOn: Int
    protected abstract val iconOff: Int
    protected abstract fun label(): String

    protected open fun isActive(prefs: WaexPreferenceManager): Boolean =
        prefs.getBoolean(featureKey, false) && prefs.getBoolean(activeKey, false)

    protected open fun setActive(prefs: WaexPreferenceManager, active: Boolean) {
        if (active) prefs.putBoolean(featureKey, true)
        prefs.putBoolean(activeKey, active)
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val prefs = WaexPreferenceManager(applicationContext)
        setActive(prefs, !isActive(prefs))
        refresh()
        applyToWhatsApp(applicationContext)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = isActive(WaexPreferenceManager(applicationContext))
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, if (active) iconOn else iconOff)
        tile.label = label()
        tile.updateTile()
    }

    private fun applyToWhatsApp(context: Context) {
        // A WhatsApp call runs in communication mode; stopping the process would drop it.
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.mode == AudioManager.MODE_IN_COMMUNICATION) {
            toast(context, UiText.get(R.string.waex_ui_tile_applies_after_call,
                "Applies after the call, when WhatsApp restarts"))
            return
        }
        val targets = LinkedHashSet<String>()
        targets.add("com.whatsapp")
        targets.addAll(PreferenceStores.publicStore(context)
            .getStringSet("hooked_whatsapp_packages", emptySet()).orEmpty())
        targets.retainAll(WhatsAppPackageDetector.getSupportedPackages(context))
        for (pkg in targets) {
            try {
                context.sendBroadcast(Intent(RESTART_ACTION).setPackage(pkg).putExtra("PKG", pkg))
            } catch (_: Throwable) {
            }
        }
        toast(context, UiText.get(R.string.waex_ui_tile_applies_on_next_open,
            "Applies the next time you open WhatsApp"))
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val RESTART_ACTION = "com.waenhancer.WHATSAPP.RESTART"
    }
}

class GhostModeTileService : WaexToggleTile() {
    override val activeKey = "ghostmode_active"
    override val featureKey = "ghostmode"
    override val iconOn = R.drawable.ic_waex_ghost_on
    override val iconOff = R.drawable.ic_waex_ghost_off
    override fun label() = UiText.get(R.string.waex_ui_ghost_mode, "Ghost Mode")
}

class FreezeLastSeenTileService : WaexToggleTile() {
    override val activeKey = "freeze_last_seen_active"
    override val featureKey = "freezelastseen"
    override val iconOn = R.drawable.ic_waex_freeze_on
    override val iconOff = R.drawable.ic_waex_freeze_off
    override fun label() = UiText.get(R.string.feat_freezelastseen_title, "Freeze Last Seen")
}

class DndModeTileService : WaexToggleTile() {
    override val activeKey = "dnd_mode"
    override val featureKey = "show_dndmode"
    override val iconOn = R.drawable.ic_waex_dnd_on
    override val iconOff = R.drawable.ic_waex_dnd_off
    override fun label() = UiText.get(R.string.waex_ui_dnd_mode, "DND Mode")

    // The DND switch works without the toolbar shortcut feature, so only its own key counts.
    override fun isActive(prefs: WaexPreferenceManager) = prefs.getBoolean(activeKey, false)
    override fun setActive(prefs: WaexPreferenceManager, active: Boolean) =
        prefs.putBoolean(activeKey, active)
}

/** Restarts WhatsApp, the same as the Restart WhatsApp button in WAEX. */
class RestartWhatsAppTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_waex_restart)
        tile.label = UiText.get(R.string.waex_ui_restart_whatsapp, "Restart WhatsApp")
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        AppRestartHelper.restartPackage(applicationContext, "com.whatsapp", "WhatsApp")
    }
}
