package com.khalefa.medtechtools.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class StylusCanvasView(context: Context, private val baseBitmap: Bitmap) : View(context) {

    data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null

    var currentColor: Int = Color.parseColor("#E53935")
    var strokeWidthPx: Float = 8f
    var stylusOnlyMode: Boolean = false

    private val destRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0 || baseBitmap.isRecycled) return

        val scale = minOf(width.toFloat() / baseBitmap.width, height.toFloat() / baseBitmap.height)
        val drawW = baseBitmap.width * scale
        val drawH = baseBitmap.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        destRect.set(left, top, left + drawW, top + drawH)

        canvas.drawBitmap(baseBitmap, null, destRect, null)
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
        currentPath?.let { p ->
            currentPaint?.let { pt -> canvas.drawPath(p, pt) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stylusOnlyMode && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        val pressureFactor = if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            (0.6f + event.pressure * 0.85f).coerceIn(0.5f, 1.6f)
        } else {
            1.0f
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(event.x, event.y) }
                currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = currentColor
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = strokeWidthPx * pressureFactor
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val p = currentPath
                val pt = currentPaint
                if (p != null && pt != null) {
                    strokes.add(Stroke(p, pt))
                }
                currentPath = null
                currentPaint = null
                invalidate()
            }
        }
        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
            invalidate()
        }
    }

    fun clearAll() {
        strokes.clear()
        currentPath = null
        currentPaint = null
        invalidate()
    }

    fun exportMergedBitmap(): Bitmap {
        val out = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (destRect.width() <= 0f || destRect.height() <= 0f) return out

        val canvas = Canvas(out)
        val scaleX = baseBitmap.width.toFloat() / destRect.width()
        val scaleY = baseBitmap.height.toFloat() / destRect.height()

        canvas.save()
        canvas.scale(scaleX, scaleY)
        canvas.translate(-destRect.left, -destRect.top)
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
        canvas.restore()
        return out
    }
}
