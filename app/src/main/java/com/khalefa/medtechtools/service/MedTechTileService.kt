package com.khalefa.medtechtools.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.ui.PermissionBridgeActivity
import com.khalefa.medtechtools.ui.TermsActivity

class MedTechTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = PrefsManager(this)

        // Enforce Ethical Terms acceptance before first use
        if (!prefs.termsAccepted) {
            launchActivityFromTile(Intent(this, TermsActivity::class.java), 10)
            return
        }

        // Check Overlay Permission first
        if (!Settings.canDrawOverlays(this)) {
            launchActivityFromTile(Intent(this, PermissionBridgeActivity::class.java), 11)
            return
        }

        if (FloatingBubbleService.isRunning) {
            stopService(Intent(this, FloatingBubbleService::class.java))
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        } else {
            launchActivityFromTile(Intent(this, PermissionBridgeActivity::class.java), 12)
        }
    }

    private fun launchActivityFromTile(rawIntent: Intent, requestCode: Int) {
        val intent = rawIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        qsTile?.apply {
            state = if (FloatingBubbleService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
