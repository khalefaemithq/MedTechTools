package com.khalefa.medtechtools.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.service.FloatingBubbleService

class PermissionBridgeActivity : Activity() {

    companion object {
        private const val REQ_MEDIA_PROJECTION = 1001
        private const val REQ_OVERLAY = 1002
        private const val REQ_NOTIFICATIONS = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PrefsManager(this)
        if (!prefs.termsAccepted) {
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.grant_overlay), Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }

        checkNotificationPermissionAndCapture()
    }

    private fun checkNotificationPermissionAndCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
                return
            }
        }
        requestScreenCaptureOnce()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            requestScreenCaptureOnce()
        }
    }

    private fun requestScreenCaptureOnce() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                checkNotificationPermissionAndCapture()
            } else {
                finish()
            }
        } else if (requestCode == REQ_MEDIA_PROJECTION) {
            val serviceIntent = if (resultCode == RESULT_OK && data != null) {
                Intent(this, FloatingBubbleService::class.java).apply {
                    action = FloatingBubbleService.ACTION_START_WITH_PROJECTION
                    putExtra(FloatingBubbleService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(FloatingBubbleService.EXTRA_RESULT_DATA, data)
                }
            } else {
                Intent(this, FloatingBubbleService::class.java)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }
    }
}
