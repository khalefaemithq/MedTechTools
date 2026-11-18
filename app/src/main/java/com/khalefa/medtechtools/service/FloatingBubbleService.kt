package com.khalefa.medtechtools.service

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
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.ui.*
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs
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
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForegroundCompat(true)
                    setupMediaProjection(resultCode, data)
                }
            }
            ACTION_REFRESH_BUBBLE -> {
                applyBubbleAppearance()
            }
            ACTION_STOP_EXIT -> {
                forceExitEverything()
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

            // Required on Android 14+ before createVirtualDisplay()
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    virtualDisplay?.release()
                    imageReader?.close()
                    mediaProjection = null
                }
            }, handler)

            imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "MedTechCapture",
                screenW, screenH, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
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

        val params = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX
            y = prefs.bubbleY
        }
        bubbleParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchDownTime = 0L
        var isDragging = false
        var tapCount = 0
        var longPressTriggered = false

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
                executeAction(prefs.singleTapAction)
            }
        }

        iv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchDownTime = SystemClock.uptimeMillis()
                    isDragging = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, 440)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 16 || abs(dy) > 16) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(singleTapRunnable)
                        tapCount = 0
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(iv, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val duration = SystemClock.uptimeMillis() - touchDownTime

                    if (isDragging) {
                        // Quick short flick gesture (when enabled) snaps bubble back & runs directional action
                        if (prefs.swipeGesturesEnabled && duration < 260 && (abs(dx) in 45f..240f || abs(dy) in 45f..240f)) {
                            params.x = initialX
                            params.y = initialY
                            windowManager.updateViewLayout(iv, params)
                            iv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                            val flickAction = if (abs(dx) > abs(dy)) {
                                if (dx > 0) prefs.swipeRightAction else prefs.swipeLeftAction
                            } else {
                                if (dy > 0) prefs.swipeDownAction else prefs.swipeUpAction
                            }
                            executeAction(flickAction)
                        } else {
                            // Standard drag to reposition bubble
                            prefs.bubbleX = params.x
                            prefs.bubbleY = params.y
                        }
                    } else if (!longPressTriggered) {
                        tapCount++
                        if (tapCount == 1) {
                            handler.postDelayed(singleTapRunnable, 230)
                        } else if (tapCount >= 2) {
                            handler.removeCallbacks(singleTapRunnable)
                            tapCount = 0
                            executeAction(prefs.doubleTapAction)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(iv, params)
    }

    fun applyBubbleAppearance() {
        val iv = bubbleView ?: return
        val sizePx = dpToPx(prefs.bubbleSizeDp)
        iv.alpha = prefs.bubbleOpacity / 100f

        val customBmp = prefs.getCustomBubbleIcon()
        if (customBmp != null) {
            iv.setImageBitmap(getCircularBitmap(customBmp, sizePx))
        } else {
            iv.setImageBitmap(createDefaultMedTechBubble(sizePx))
        }

        bubbleParams?.let { params ->
            params.width = sizePx
            params.height = sizePx
            if (iv.isAttachedToWindow) {
                windowManager.updateViewLayout(iv, params)
            }
        }
    }

    private fun executeAction(actionKey: String) {
        if (actionKey == PrefsManager.ACT_NONE) return
        dismissMenu()

        when (actionKey) {
            PrefsManager.ACT_SHOW_MENU -> showToolsMenu()
            PrefsManager.ACT_CROP_COPY -> captureAndCrop { cropped ->
                prefs.saveAndCopyImageToClipboard(cropped)
                Toast.makeText(this, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
            }
            PrefsManager.ACT_CROP_OCR -> captureAndCrop { cropped ->
                runOfflineOcr(cropped) { extracted ->
                    if (extracted.isEmpty()) {
                        Toast.makeText(this, getString(R.string.ocr_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.copyTextToSystemClipboard(extracted)
                        Toast.makeText(this, "OCR:\n${extracted.take(90)}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PrefsManager.ACT_STYLUS_EDITOR -> captureAndCrop { cropped ->
                val file = prefs.saveAndCopyImageToClipboard(cropped)
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra("image_path", file.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            PrefsManager.ACT_PIN_IMAGE -> captureAndCrop { cropped ->
                showPinnedImage(cropped)
            }
            PrefsManager.ACT_CLIPBOARD -> showMiniClipboardOverlay()
            PrefsManager.ACT_GOOGLE_SEARCH -> captureAndCrop { cropped ->
                val file = prefs.saveAndCopyImageToClipboard(cropped)
                shareToGoogleSearch(file)
            }
            PrefsManager.ACT_GOOGLE_TRANSLATE -> captureAndCrop { cropped ->
                runOfflineOcr(cropped) { extracted ->
                    val targetUrl = if (extracted.isNotEmpty()) {
                        prefs.copyTextToSystemClipboard(extracted)
                        val encoded = URLEncoder.encode(extracted, "UTF-8")
                        "https://translate.google.com/?sl=auto&tl=ar&text=$encoded"
                    } else {
                        "https://translate.google.com/"
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
            PrefsManager.ACT_AI_CHAT -> {
                startActivity(Intent(this, AiChatActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            PrefsManager.ACT_EXIT -> forceExitEverything()
        }
    }

    private fun captureAndCrop(onResult: (Bitmap) -> Unit) {
        if (mediaProjection == null || imageReader == null) {
            startActivity(Intent(this, PermissionBridgeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }

        bubbleView?.visibility = View.INVISIBLE
        handler.postDelayed({
            val image = imageReader?.acquireLatestImage()
            bubbleView?.visibility = View.VISIBLE
            if (image == null) {
                Toast.makeText(this, "Tap again to capture", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
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
            image.close()

            val cleanW = screenW.coerceAtMost(fullBmp.width)
            val cleanH = screenH.coerceAtMost(fullBmp.height)
            val cleanBmp = Bitmap.createBitmap(fullBmp, 0, 0, cleanW, cleanH)
            if (cleanBmp !== fullBmp) fullBmp.recycle()

            showCropOverlay(cleanBmp, onResult)
        }, 110)
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

    private fun runOfflineOcr(bitmap: Bitmap, onDone: (String) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                onDone(result.text.trim())
            }
            .addOnFailureListener {
                onDone("")
            }
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
            val chooser = Intent.createChooser(fallback, "Google Search").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        }
    }

    private fun showToolsMenu() {
        dismissMenu()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2171D24"))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(2, Color.parseColor("#00897B"))
            }
        }

        val title = TextView(this).apply {
            text = "${getString(R.string.app_name)} • ${getString(R.string.app_tagline)}"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(title)

        val orderedMenuKeys = listOf(
            PrefsManager.ACT_CROP_COPY to getString(R.string.tool_crop_copy),
            PrefsManager.ACT_STYLUS_EDITOR to getString(R.string.tool_stylus),
            PrefsManager.ACT_CROP_OCR to getString(R.string.tool_ocr),
            PrefsManager.ACT_PIN_IMAGE to getString(R.string.tool_pin),
            PrefsManager.ACT_CLIPBOARD to getString(R.string.tool_clipboard),
            PrefsManager.ACT_GOOGLE_SEARCH to getString(R.string.tool_google_search),
            PrefsManager.ACT_GOOGLE_TRANSLATE to getString(R.string.tool_google_translate),
            PrefsManager.ACT_AI_CHAT to getString(R.string.tool_ai_chat),
            PrefsManager.ACT_EXIT to getString(R.string.tool_exit)
        )

        for ((key, label) in orderedMenuKeys) {
            if (!prefs.isToolEnabled(key)) continue
            val btn = TextView(this).apply {
                text = "• $label"
                setTextColor(if (key == PrefsManager.ACT_EXIT) Color.parseColor("#FF5252") else Color.WHITE)
                textSize = 14f
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setOnClickListener {
                    dismissMenu()
                    executeAction(key)
                }
            }
            container.addView(btn)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.LTGRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8), 0, 0)
            setOnClickListener { dismissMenu() }
        }
        container.addView(closeBtn)

        menuView = container
        val params = WindowManager.LayoutParams(
            dpToPx(260),
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
            setBackgroundColor(Color.parseColor("#CC00897B"))
            setPadding(5, 5, 5, 5)
        }
        val iv = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
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
                setColor(Color.parseColor("#F5171D24"))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(2, Color.parseColor("#00897B"))
            }
        }

        val header = TextView(this).apply {
            text = getString(R.string.tool_clipboard)
            setTextColor(Color.parseColor("#00E5FF"))
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(74), dpToPx(52))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val btnCopy = Button(this).apply {
                text = "Copy"
                setOnClickListener {
                    prefs.copyFileUriToSystemClipboard(file)
                    Toast.makeText(this@FloatingBubbleService, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                }
            }
            val btnEdit = Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
                    clipboardDialogView = null
                    startActivity(Intent(this@FloatingBubbleService, EditorActivity::class.java).apply {
                        putExtra("image_path", file.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
            row.addView(thumb)
            row.addView(btnCopy)
            row.addView(btnEdit)
            list.addView(row)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(320)))

        val closeBtn = Button(this).apply {
            text = "✕"
            setOnClickListener {
                clipboardDialogView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
                clipboardDialogView = null
            }
        }
        root.addView(closeBtn)

        clipboardDialogView = root
        val params = WindowManager.LayoutParams(
            dpToPx(310),
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(root, params)
    }

    // Native Kotlin Full Process & Service Shutdown
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
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00897B") }
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = size * 0.12f
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
