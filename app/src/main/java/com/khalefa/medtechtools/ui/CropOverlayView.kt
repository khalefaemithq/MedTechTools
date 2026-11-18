package com.khalefa.medtechtools.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class CropOverlayView(
    context: Context,
    private val screenshot: Bitmap,
    private val onCropConfirmed: (Bitmap) -> Unit,
    private val onCancel: () -> Unit
) : View(context) {

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5500E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    private val btnConfirmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00897B")
    }
    private val btnCancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val destFullRect = Rect()
    private var cropRect = RectF()
    private var initialized = false
    private var activeCorner = -1 // 0=TL, 1=TR, 2=BL, 3=BR, 4=MOVE
    private var lastX = 0f
    private var lastY = 0f

    private val btnConfirmRect = RectF()
    private val btnCancelRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized && w > 0 && h > 0) {
            destFullRect.set(0, 0, w, h)
            val marginX = w * 0.14f
            val marginY = h * 0.24f
            cropRect.set(marginX, marginY, w - marginX, h - marginY)
            initialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (screenshot.isRecycled) return

        canvas.drawBitmap(screenshot, null, destFullRect, null)

        // Dim area outside the crop selection
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // Rule-of-thirds inner grid
        val thirdW = cropRect.width() / 3f
        val thirdH = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + thirdW * 2, cropRect.top, cropRect.left + thirdW * 2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2, cropRect.right, cropRect.top + thirdH * 2, gridPaint)

        // Border & Corner handles
        canvas.drawRect(cropRect, borderPaint)
        val r = 20f
        canvas.drawCircle(cropRect.left, cropRect.top, r, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, r, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, r, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, r, handlePaint)

        // Floating Confirm / Cancel buttons
        val btnW = 210f
        val btnH = 88f
        val btnTop = if (cropRect.bottom + btnH + 30f < height) {
            cropRect.bottom + 24f
        } else {
            (cropRect.top - btnH - 24f).coerceAtLeast(48f)
        }
        val centerX = cropRect.centerX().coerceIn(btnW + 30f, (width - btnW - 30f).coerceAtLeast(btnW + 31f))

        btnCancelRect.set(centerX - btnW - 14f, btnTop, centerX - 14f, btnTop + btnH)
        btnConfirmRect.set(centerX + 14f, btnTop, centerX + btnW + 14f, btnTop + btnH)

        canvas.drawRoundRect(btnCancelRect, 22f, 22f, btnCancelPaint)
        canvas.drawRoundRect(btnConfirmRect, 22f, 22f, btnConfirmPaint)

        canvas.drawText("✕", btnCancelRect.centerX(), btnCancelRect.centerY() + 15f, textPaint)
        canvas.drawText("✓", btnConfirmRect.centerX(), btnConfirmRect.centerY() + 15f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (btnConfirmRect.contains(x, y)) {
                    performCrop()
                    return true
                }
                if (btnCancelRect.contains(x, y)) {
                    onCancel()
                    return true
                }
                val tol = 70f
                activeCorner = when {
                    abs(x - cropRect.left) < tol && abs(y - cropRect.top) < tol -> 0
                    abs(x - cropRect.right) < tol && abs(y - cropRect.top) < tol -> 1
                    abs(x - cropRect.left) < tol && abs(y - cropRect.bottom) < tol -> 2
                    abs(x - cropRect.right) < tol && abs(y - cropRect.bottom) < tol -> 3
                    cropRect.contains(x, y) -> 4
                    else -> {
                        val initRight = (x + 60f).coerceAtMost(width.toFloat())
                        val initBottom = (y + 60f).coerceAtMost(height.toFloat())
                        cropRect.set(x, y, initRight, initBottom)
                        3
                    }
                }
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                val minSize = 60f
                when (activeCorner) {
                    0 -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSize)
                        cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSize)
                    }
                    1 -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, width.toFloat())
                        cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSize)
                    }
                    2 -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSize)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, height.toFloat())
                    }
                    3 -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, width.toFloat())
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, height.toFloat())
                    }
                    4 -> {
                        val wRect = cropRect.width()
                        val hRect = cropRect.height()
                        val newLeft = (cropRect.left + dx).coerceIn(0f, (width - wRect).coerceAtLeast(0f))
                        val newTop = (cropRect.top + dy).coerceIn(0f, (height - hRect).coerceAtLeast(0f))
                        cropRect.set(newLeft, newTop, newLeft + wRect, newTop + hRect)
                    }
                }
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> activeCorner = -1
        }
        return true
    }

    private fun performCrop() {
        if (width <= 0 || height <= 0) {
            onCancel()
            return
        }
        val scaleX = screenshot.width.toFloat() / width.toFloat()
        val scaleY = screenshot.height.toFloat() / height.toFloat()
        val left = (cropRect.left * scaleX).toInt().coerceIn(0, (screenshot.width - 12).coerceAtLeast(0))
        val top = (cropRect.top * scaleY).toInt().coerceIn(0, (screenshot.height - 12).coerceAtLeast(0))
        val cropW = (cropRect.width() * scaleX).toInt().coerceIn(10, (screenshot.width - left).coerceAtLeast(10))
        val cropH = (cropRect.height() * scaleY).toInt().coerceIn(10, (screenshot.height - top).coerceAtLeast(10))

        val cropped = Bitmap.createBitmap(screenshot, left, top, cropW, cropH)
        onCropConfirmed(cropped)
    }
}
