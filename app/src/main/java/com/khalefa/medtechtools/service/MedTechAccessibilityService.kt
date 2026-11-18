package com.khalefa.medtechtools.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class MedTechAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MedTechAccessibilityService? = null
            private set

        fun isAvailable(): Boolean = instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used; service exists solely for universal cross-app takeScreenshot()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureFullScreen(onResult: (Bitmap?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hwBuffer = screenshot.hardwareBuffer
                    val colorSpace = screenshot.colorSpace
                    val hwBmp = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                    val softBmp = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBmp?.recycle()
                    hwBuffer.close()
                    onResult(softBmp)
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null)
                }
            }
        )
    }
}
