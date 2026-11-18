package com.khalefa.medtechtools.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.ui.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.system.exitProcess

class FloatingBubbleService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_START_WITH_PROJECTION = "START_PROJECTION"
        const val ACTION_REFRESH_BUBBLE = "REFRESH_BUBBLE"
        const val ACTION_STOP_EXIT = "STOP_EXIT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "medtech_tools_channel"
        private const val NOTIF_ID = 707
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager

    private var bubbleView: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var cropView: View? = null
    private var pinnedView: View? = null
    private var clipboardDialogView: View? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var pendingCaptureCallback: ((Bitmap) -> Unit)? = null
    private var captureTimeoutRunnable: Runnable? = null

    private var screenW = 1080
    private var screenH = 2400
    private var screenDensity = 320

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = PrefsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateDisplayMetrics()
        createNotificationChannel()
        startForegroundCompat(false)
        setupBubbleView()
    }

    private fun updateDisplayMetrics() {
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun startForegroundCompat(hasProjection: Boolean) {
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_EXIT
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 1, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.app_tagline))
            .setSmallIcon(R.drawable.ic_medtech_tile)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.exit_app),
                stopPending
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fgsType = if (hasProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIF_ID, notification, fgsType)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WITH_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val pendingAct = intent.getStringExtra(PermissionBridgeActivity.EXTRA_PENDING_ACTION)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForegroundCompat(true)
                    setupMediaProjection(resultCode, data)
                    if (!pendingAct.isNullOrEmpty()) {
                        handler.postDelayed({ executeAction(pendingAct) }, 220)
                    }
                }
            }
            ACTION_REFRESH_BUBBLE -> {
                applyBubbleAppearance()
            }
            ACTION_STOP_EXIT -> {
                forceExitEverything()
            }
            else -> {
                val pendingAct = intent?.getStringExtra(PermissionBridgeActivity.EXTRA_PENDING_ACTION)
                if (!pendingAct.isNullOrEmpty()) {
                    handler.postDelayed({ executeAction(pendingAct) }, 200)
                }
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            updateDisplayMetrics()
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()

            val projection = mpm.getMediaProjection(resultCode, data)
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    virtualDisplay?.release()
                    virtualDisplay = null
                    imageReader?.close()
                    imageReader = null
                    mediaProjection = null
                }
            }, handler)

            val reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener({ r ->
                val callback = pendingCaptureCallback
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener

                if (callback == null) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                pendingCaptureCallback = null
                captureTimeoutRunnable?.let { handler.removeCallbacks(it) }
                try {
                    virtualDisplay?.setSurface(null)
                } catch (_: Exception) {
                }

                val bmp = try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenW

                    val fullBmp = Bitmap.createBitmap(
                        screenW + rowPadding / pixelStride,
                        screenH,
                        Bitmap.Config.ARGB_8888
                    )
                    fullBmp.copyPixelsFromBuffer(buffer)
                    val cleanW = screenW.coerceAtMost(fullBmp.width)
                    val cleanH = screenH.coerceAtMost(fullBmp.height)
                    val cleanBmp = Bitmap.createBitmap(fullBmp, 0, 0, cleanW, cleanH)
                    if (cleanBmp !== fullBmp) fullBmp.recycle()
                    cleanBmp
                } catch (_: Exception) {
                    null
                } finally {
                    image.close()
                }

                handler.post {
                    bubbleView?.visibility = View.VISIBLE
                    if (bmp != null) {
                        callback(bmp)
                    } else {
                        Toast.makeText(this, "Tap again to capture", Toast.LENGTH_SHORT).show()
                    }
                }
            }, handler)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "MedTechCapture",
                screenW, screenH, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, handler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleView() {
        val iv = ImageView(this)
        bubbleView = iv
        applyBubbleAppearance()

        val sizePx = dpToPx(prefs.bubbleSizeDp)
        val type = getOverlayWindowType()
        val initialEdgeX = if (prefs.bubbleX + sizePx / 2 < screenW / 2) 0 else (screenW - sizePx).coerceAtLeast(0)

        val params = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialEdgeX
            y = prefs.bubbleY.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
        }
        bubbleParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var tapCount = 0
        var longPressTriggered = false
        val gesturePoints = mutableListOf<PointF>()

        val longPressRunnable = Runnable {
            if (!isDragging) {
                longPressTriggered = true
                tapCount = 0
                iv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                executeAction(prefs.longPressAction)
            }
        }

        val singleTapRunnable = Runnable {
            if (tapCount == 1 && !longPressTriggered && !isDragging) {
                tapCount = 0
                if (menuView != null) {
                    dismissMenu()
                } else {
                    executeAction(prefs.singleTapAction)
                }
            }
        }

        iv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressTriggered = false
                    gesturePoints.clear()
                    gesturePoints.add(PointF(event.rawX, event.rawY))
                    handler.postDelayed(longPressRunnable, 440)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 14 || abs(dy) > 14) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(singleTapRunnable)
                        tapCount = 0
                        val curSize = params.width
                        params.x = (initialX + dx).coerceIn(0, (screenW - curSize).coerceAtLeast(0))
                        params.y = (initialY + dy).coerceIn(0, (screenH - curSize).coerceAtLeast(0))
                        windowManager.updateViewLayout(iv, params)

                        val last = gesturePoints.last()
                        if (hypot((event.rawX - last.x).toDouble(), (event.rawY - last.y).toDouble()) > 8.0) {
                            gesturePoints.add(PointF(event.rawX, event.rawY))
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)

                    if (isDragging) {
                        val recognizedAction = if (prefs.swipeGesturesEnabled) {
                            detectBubbleGesture(gesturePoints)
                        } else null

                        if (recognizedAction != null) {
                            params.x = initialX
                            params.y = initialY
                            windowManager.updateViewLayout(iv, params)
                            iv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            executeAction(recognizedAction)
                        } else {
                            val curSize = params.width
                            val targetX = if (params.x + curSize / 2 < screenW / 2) 0 else (screenW - curSize).coerceAtLeast(0)
                            val targetY = params.y.coerceIn(0, (screenH - curSize).coerceAtLeast(0))
                            params.y = targetY
                            prefs.bubbleX = targetX
                            prefs.bubbleY = targetY

                            ValueAnimator.ofInt(params.x, targetX).apply {
                                this.duration = 180
                                interpolator = DecelerateInterpolator()
                                addUpdateListener { anim ->
                                    if (iv.isAttachedToWindow) {
                                        params.x = anim.animatedValue as Int
                                        windowManager.updateViewLayout(iv, params)
                                    }
                                }
                                start()
                            }
                        }
                    } else if (!longPressTriggered) {
                        if (menuView != null) {
                            tapCount = 0
                            dismissMenu()
                        } else {
                            tapCount++
                            if (tapCount == 1) {
                                handler.postDelayed(singleTapRunnable, 230)
                            } else if (tapCount >= 2) {
                                handler.removeCallbacks(singleTapRunnable)
                                tapCount = 0
                                executeAction(prefs.doubleTapAction)
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(iv, params)
    }

    private fun detectBubbleGesture(pts: List<PointF>): String? {
        if (pts.size < 6) return null
        val start = pts.first()
        val end = pts.last()

        var perimeter = 0.0
        var minX = start.x
        var maxX = start.x
        var minY = start.y
        var maxY = start.y
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            perimeter += hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            if (b.x < minX) minX = b.x
            if (b.x > maxX) maxX = b.x
            if (b.y < minY) minY = b.y
            if (b.y > maxY) maxY = b.y
        }
        if (perimeter < dpToPx(85)) return null

        val width = maxX - minX
        val height = maxY - minY
        val endToStartDist = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())

        var area2 = 0.0
        for (i in pts.indices) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % pts.size]
            area2 += (p1.x * p2.y - p2.x * p1.y).toDouble()
        }
        val signedArea = area2 / 2.0
        val iq = (4.0 * PI * abs(signedArea)) / (perimeter * perimeter)

        if (endToStartDist < dpToPx(85) && width > dpToPx(45) && height > dpToPx(45) && iq > 0.36) {
            return if (signedArea > 0) prefs.gestureCircleCwAction else prefs.gestureCircleCcwAction
        }

        val startedOnLeft = start.x < screenW / 2f
        val maxInward = if (startedOnLeft) (maxX - start.x) else (start.x - minX)

        if (abs(end.x - start.x) < dpToPx(70) && maxInward >= dpToPx(40)) {
            if (abs(end.y - start.y) >= dpToPx(55)) {
                return if (end.y < start.y) prefs.gestureHalfUpAction else prefs.gestureHalfDownAction
            }
            val upReach = start.y - minY
            val downReach = maxY - start.y
            return if (upReach > downReach) prefs.gestureTriUpAction else prefs.gestureTriDownAction
        }

        if (maxInward >= dpToPx(45) && abs(end.x - start.x) >= dpToPx(40) && abs(end.y - start.y) >= dpToPx(45)) {
            return if (end.y < start.y) prefs.gestureLUpAction else prefs.gestureLDownAction
        }

        return null
    }

    fun applyBubbleAppearance() {
        val iv = bubbleView ?: return
        val sizePx = dpToPx(prefs.bubbleSizeDp)
        iv.alpha = (100 - prefs.bubbleTransparency) / 100f

        val customBmp = prefs.getCustomBubbleIcon()
        if (customBmp != null) {
            iv.setImageBitmap(getCircularBitmap(customBmp, sizePx))
        } else {
            val launcherBmp = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
            if (launcherBmp != null) {
                iv.setImageBitmap(getCircularBitmap(launcherBmp, sizePx))
            } else {
                iv.setImageBitmap(createDefaultMedTechBubble(sizePx))
            }
        }

        bubbleParams?.let { params ->
            params.width = sizePx
            params.height = sizePx
            params.x = if (params.x + sizePx / 2 < screenW / 2) 0 else (screenW - sizePx).coerceAtLeast(0)
            params.y = params.y.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
            if (iv.isAttachedToWindow) {
                windowManager.updateViewLayout(iv, params)
            }
        }
    }

    private fun launchActivitySafe(intent: Intent, requestCode: Int = 200) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            val pi = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                opts.toBundle()
            )
            try {
                pi.send()
            } catch (_: Exception) {
                startActivity(intent)
            }
        } else {
            startActivity(intent)
        }
    }

    private fun executeAction(actionKey: String) {
        if (actionKey == PrefsManager.ACT_NONE) return
        dismissMenu()

        when (actionKey) {
            PrefsManager.ACT_SHOW_MENU -> showToolsMenu()
            PrefsManager.ACT_CROP_COPY -> captureAndCrop(actionKey) { cropped ->
                if (prefs.saveToGallery) {
                    val ok = prefs.saveBitmapToGallery(cropped)
                    val msg = if (ok) getString(R.string.saved_gallery_toast) else "Failed to save to Gallery"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    prefs.saveAndCopyImageToClipboard(cropped)
                    Toast.makeText(this, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                }
            }
            PrefsManager.ACT_CROP_OCR -> captureAndCrop(actionKey) { cropped ->
                runOcrSmart(cropped) { extracted ->
                    if (extracted.isEmpty()) {
                        Toast.makeText(this, getString(R.string.translate_offline_note), Toast.LENGTH_LONG).show()
                    } else {
                        prefs.copyTextToSystemClipboard(extracted)
                        Toast.makeText(this, "تم النسخ:\n${extracted.take(90)}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PrefsManager.ACT_PIN_IMAGE -> captureAndCrop(actionKey) { cropped ->
                showPinnedImage(cropped)
            }
            PrefsManager.ACT_CLIPBOARD -> showMiniClipboardOverlay()
            PrefsManager.ACT_GOOGLE_SEARCH -> captureAndCrop(actionKey) { cropped ->
                val file = prefs.saveAndCopyImageToClipboard(cropped)
                shareToGoogleSearch(file)
            }
            PrefsManager.ACT_GOOGLE_TRANSLATE -> captureAndCrop(actionKey) { cropped ->
                runOcrSmart(cropped) { extracted ->
                    if (extracted.isEmpty()) {
                        Toast.makeText(this, getString(R.string.translate_offline_note), Toast.LENGTH_LONG).show()
                    }
                    val targetUrl = if (extracted.isNotEmpty()) {
                        prefs.copyTextToSystemClipboard(extracted)
                        val encoded = URLEncoder.encode(extracted, "UTF-8")
                        "https://translate.google.com/?sl=auto&tl=ar&text=$encoded"
                    } else {
                        "https://translate.google.com/"
                    }
                    launchActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)), 202)
                }
            }
            PrefsManager.ACT_AI_CHAT -> {
                launchActivitySafe(Intent(this, AiChatActivity::class.java), 203)
            }
            PrefsManager.ACT_EXIT -> forceExitEverything()
        }
    }

    private fun captureAndCrop(pendingActionKey: String, onResult: (Bitmap) -> Unit) {
        // Primary: Universal cross-app Accessibility Screenshot (Android 11+)
        val acc = MedTechAccessibilityService.instance
        if (acc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bubbleView?.visibility = View.INVISIBLE
            handler.postDelayed({
                acc.captureFullScreen { bmp ->
                    handler.post {
                        bubbleView?.visibility = View.VISIBLE
                        if (bmp != null) {
                            showCropOverlay(bmp, onResult)
                        } else {
                            Toast.makeText(this, "Tap again to capture", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, 90)
            return
        }

        // Fallback: MediaProjection
        if (mediaProjection == null || virtualDisplay == null || imageReader == null) {
            launchActivitySafe(Intent(this, PermissionBridgeActivity::class.java).apply {
                putExtra(PermissionBridgeActivity.EXTRA_PENDING_ACTION, pendingActionKey)
            }, 204)
            return
        }

        updateDisplayMetrics()
        pendingCaptureCallback = { cleanBmp ->
            showCropOverlay(cleanBmp, onResult)
        }
        bubbleView?.visibility = View.INVISIBLE

        captureTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            if (pendingCaptureCallback != null) {
                pendingCaptureCallback = null
                try {
                    virtualDisplay?.setSurface(null)
                } catch (_: Exception) {
                }
                bubbleView?.visibility = View.VISIBLE
                Toast.makeText(this, "Tap again to capture", Toast.LENGTH_SHORT).show()
            }
        }
        captureTimeoutRunnable = timeout
        handler.postDelayed(timeout, 750)

        handler.postDelayed({
            try {
                virtualDisplay?.setSurface(imageReader?.surface)
            } catch (_: Exception) {
                pendingCaptureCallback = null
                bubbleView?.visibility = View.VISIBLE
            }
        }, 90)
    }

    private fun showCropOverlay(screenshot: Bitmap, onResult: (Bitmap) -> Unit) {
        removeCropOverlay()
        val overlay = CropOverlayView(
            context = this,
            screenshot = screenshot,
            onCropConfirmed = { cropped ->
                removeCropOverlay()
                onResult(cropped)
            },
            onCancel = {
                removeCropOverlay()
            }
        )
        cropView = overlay

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlay, params)
    }

    private fun removeCropOverlay() {
        cropView?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
            cropView = null
        }
    }

    private fun runOcrSmart(bitmap: Bitmap, onDone: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    onDone(text)
                } else if (prefs.aiApiKey.isNotEmpty() && prefs.aiBaseUrl.isNotEmpty() && prefs.aiModel.isNotEmpty()) {
                    runVisionAiOcr(bitmap, onDone)
                } else {
                    onDone("")
                }
            }
            .addOnFailureListener {
                try {
                    val installReq = ModuleInstallRequest.newBuilder().addApi(recognizer).build()
                    ModuleInstall.getClient(this).installModules(installReq)
                } catch (_: Exception) {
                }
                if (prefs.aiApiKey.isNotEmpty() && prefs.aiBaseUrl.isNotEmpty() && prefs.aiModel.isNotEmpty()) {
                    runVisionAiOcr(bitmap, onDone)
                } else {
                    onDone("")
                }
            }
    }

    private fun runVisionAiOcr(bitmap: Bitmap, onDone: (String) -> Unit) {
        Thread {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val conn = (URL(prefs.aiBaseUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${prefs.aiApiKey}")
                    doOutput = true
                }

                val contentArr = JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", "Extract all text (Arabic and/or English) from this image verbatim. Return ONLY the extracted text without any extra commentary."))
                    put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/png;base64,$b64")))
                }
                val body = JSONObject().apply {
                    put("model", prefs.aiModel)
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", contentArr)))
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
                val extracted = if (code in 200..299) {
                    JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                } else ""

                handler.post { onDone(extracted) }
            } catch (_: Exception) {
                handler.post { onDone("") }
            }
        }.start()
    }

    private fun shareToGoogleSearch(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val googleIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(googleIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(fallback, getString(R.string.tool_google_search)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        }
    }

    private fun getGestureHintForAction(actionKey: String): String {
        val tags = mutableListOf<String>()
        if (prefs.singleTapAction == actionKey) tags.add(getString(R.string.single_tap))
        if (prefs.doubleTapAction == actionKey) tags.add(getString(R.string.double_tap))
        if (prefs.longPressAction == actionKey) tags.add(getString(R.string.long_press))
        if (prefs.swipeGesturesEnabled) {
            if (prefs.gestureHalfUpAction == actionKey) tags.add(getString(R.string.gesture_half_up))
            if (prefs.gestureHalfDownAction == actionKey) tags.add(getString(R.string.gesture_half_down))
            if (prefs.gestureLUpAction == actionKey) tags.add(getString(R.string.gesture_l_up))
            if (prefs.gestureLDownAction == actionKey) tags.add(getString(R.string.gesture_l_down))
            if (prefs.gestureTriUpAction == actionKey) tags.add(getString(R.string.gesture_tri_up))
            if (prefs.gestureTriDownAction == actionKey) tags.add(getString(R.string.gesture_tri_down))
            if (prefs.gestureCircleCwAction == actionKey) tags.add(getString(R.string.gesture_circle_cw))
            if (prefs.gestureCircleCcwAction == actionKey) tags.add(getString(R.string.gesture_circle_ccw))
        }
        return tags.joinToString(" • ")
    }

    private fun getActionLabel(key: String): String = when (key) {
        PrefsManager.ACT_CROP_COPY -> getString(R.string.tool_crop_copy)
        PrefsManager.ACT_CROP_OCR -> getString(R.string.tool_ocr)
        PrefsManager.ACT_PIN_IMAGE -> getString(R.string.tool_pin)
        PrefsManager.ACT_CLIPBOARD -> getString(R.string.tool_clipboard)
        PrefsManager.ACT_GOOGLE_SEARCH -> getString(R.string.tool_google_search)
        PrefsManager.ACT_GOOGLE_TRANSLATE -> getString(R.string.tool_google_translate)
        PrefsManager.ACT_AI_CHAT -> getString(R.string.tool_ai_chat)
        PrefsManager.ACT_EXIT -> getString(R.string.tool_exit)
        else -> key
    }

    private fun showToolsMenu() {
        dismissMenu()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(14))
            background = GradientDrawable().apply {
                setColor(UiTheme.card(this@FloatingBubbleService))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(2), UiTheme.goldSoft())
            }
        }

        val title = TextView(this).apply {
            text = "${getString(R.string.app_name)} • ${getString(R.string.app_tagline)}"
            setTextColor(UiTheme.royalBlue())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(title)

        for (key in prefs.menuOrder) {
            if (!prefs.isToolEnabled(key)) continue
            val label = getActionLabel(key)
            val gestureHint = getGestureHintForAction(key)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(8), dpToPx(7), dpToPx(8), dpToPx(7))
                setOnClickListener {
                    dismissMenu()
                    executeAction(key)
                }
            }

            itemLayout.addView(TextView(this).apply {
                text = "• $label"
                setTextColor(if (key == PrefsManager.ACT_EXIT) UiTheme.dangerSoft() else UiTheme.textPrimary(this@FloatingBubbleService))
                textSize = 14.5f
                setTypeface(null, if (key == PrefsManager.ACT_EXIT) Typeface.BOLD else Typeface.NORMAL)
            })

            if (gestureHint.isNotEmpty()) {
                itemLayout.addView(TextView(this).apply {
                    text = gestureHint
                    setTextColor(UiTheme.textMuted(this@FloatingBubbleService))
                    textSize = 11.5f
                    setPadding(dpToPx(12), dpToPx(1), 0, 0)
                })
            }

            container.addView(itemLayout)
        }

        menuView = container
        val params = WindowManager.LayoutParams(
            dpToPx(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(container, params)
    }

    private fun dismissMenu() {
        menuView?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
            menuView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPinnedImage(bitmap: Bitmap) {
        pinnedView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }

        val frame = FrameLayout(this).apply {
            setBackgroundColor(UiTheme.goldSoft())
            setPadding(5, 5, 5, 5)
        }
        val iv = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(UiTheme.dangerSoft())
            setPadding(18, 8, 18, 8)
            setOnClickListener {
                if (frame.isAttachedToWindow) windowManager.removeView(frame)
                pinnedView = null
            }
        }
        frame.addView(iv, FrameLayout.LayoutParams(dpToPx(230), FrameLayout.LayoutParams.WRAP_CONTENT))
        frame.addView(
            close,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 180
        }

        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (e.rawX - touchX).toInt()
                    params.y = initY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(frame, params)
                    true
                }
                else -> false
            }
        }
        pinnedView = frame
        windowManager.addView(frame, params)
    }

    private fun showMiniClipboardOverlay() {
        clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = GradientDrawable().apply {
                setColor(UiTheme.card(this@FloatingBubbleService))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(2), UiTheme.goldSoft())
            }
        }

        val header = TextView(this).apply {
            text = getString(R.string.tool_clipboard)
            setTextColor(UiTheme.royalBlue())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(10))
        }
        root.addView(header)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val files = prefs.getClipboardDir()
            .listFiles { f -> f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        for (file in files.take(12)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(6), 0, dpToPx(6))
            }
            val thumb = ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                layoutParams = LinearLayout.LayoutParams(dpToPx(84), dpToPx(56))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val btnCopy = Button(this).apply {
                text = "Copy"
                setTextColor(Color.WHITE)
                setBackgroundColor(UiTheme.royalBlue())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(10)
                }
                setOnClickListener {
                    prefs.copyFileUriToSystemClipboard(file)
                    Toast.makeText(this@FloatingBubbleService, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(thumb)
            row.addView(btnCopy)
            list.addView(row)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(320)))

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(UiTheme.royalBlue())
            setBackgroundColor(UiTheme.surface(this@FloatingBubbleService))
            setOnClickListener {
                clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
                clipboardDialogView = null
            }
        }
        root.addView(closeBtn)

        clipboardDialogView = root
        val params = WindowManager.LayoutParams(
            dpToPx(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(root, params)
    }

    fun forceExitEverything() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            bubbleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            menuView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            cropView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            pinnedView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        exitProcess(0)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            bubbleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            dismissMenu()
            removeCropOverlay()
            pinnedView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "MedTech Tools Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun createDefaultMedTechBubble(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UiTheme.royalBlue() }
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiTheme.goldSoft()
            style = Paint.Style.STROKE
            strokeWidth = size * 0.07f
        }
        c.drawCircle(size / 2f, size / 2f, size * 0.45f, ring)
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = size * 0.11f
            strokeCap = Paint.Cap.ROUND
        }
        val pad = size * 0.28f
        c.drawLine(size / 2f, pad, size / 2f, size - pad, cross)
        c.drawLine(pad, size / 2f, size - pad, size / 2f, cross)
        return bmp
    }

    private fun getCircularBitmap(src: Bitmap, size: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return out
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
