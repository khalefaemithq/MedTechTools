package com.khalefa.medtechtools.ui

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager

class EditorActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var canvasView: StylusCanvasView
    private var selectedColor = Color.parseColor("#E53935")
    private var updatingColorFields = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        val path = intent.getStringExtra("image_path") ?: prefs.getLatestClipFile()?.absolutePath
        if (path == null) {
            finish()
            return
        }
        val bmp = BitmapFactory.decodeFile(path) ?: run {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1419"))
        }

        // Top Action Toolbar
        val toolbarScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A222B"))
            isHorizontalScrollBarEnabled = false
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
        }

        val btnColor = Button(this).apply {
            text = "🎨 Color Studio"
            setOnClickListener { openFullColorStudio() }
        }

        val switchStylus = Switch(this).apply {
            text = "✒️ Stylus Only "
            setTextColor(Color.WHITE)
            setPadding(16, 0, 16, 0)
            setOnCheckedChangeListener { _, isChecked ->
                canvasView.stylusOnlyMode = isChecked
            }
        }

        val btnUndo = Button(this).apply {
            text = "↩"
            setOnClickListener { canvasView.undo() }
        }

        val btnClear = Button(this).apply {
            text = "🗑"
            setOnClickListener { canvasView.clearAll() }
        }

        val btnCopy = Button(this).apply {
            text = "✓ Copy"
            setBackgroundColor(Color.parseColor("#00897B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val merged = canvasView.exportMergedBitmap()
                prefs.saveAndCopyImageToClipboard(merged)
                Toast.makeText(this@EditorActivity, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        toolbar.addView(btnColor)
        toolbar.addView(switchStylus)
        toolbar.addView(btnUndo)
        toolbar.addView(btnClear)
        toolbar.addView(btnCopy)
        toolbarScroll.addView(toolbar)
        root.addView(toolbarScroll)

        // Brush Size Bar
        val sizeBar = SeekBar(this).apply {
            max = 55
            progress = 8
            setPadding(32, 14, 32, 14)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    canvasView.strokeWidthPx = (p + 2).toFloat()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(sizeBar)

        canvasView = StylusCanvasView(this, bmp)
        root.addView(
            canvasView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
    }

    private fun openFullColorStudio() {
        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        var alphaVal = Color.alpha(selectedColor)

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 16)
        }

        val preview = View(this).apply {
            background = GradientDrawable().apply {
                setColor(selectedColor)
                cornerRadius = 16f
                setStroke(2, Color.GRAY)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 96)
        }

        val hexInput = EditText(this).apply {
            setText(String.format("#%08X", selectedColor))
            hint = "#AARRGGBB"
        }

        // Quick Swatches Row
        val swatchesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        val presets = listOf(
            "#FFE53935", // Red Pen
            "#FF1E88E5", // Blue Pen
            "#FF00897B", // MedTech Teal
            "#88FFEB3B", // Translucent Yellow Highlighter
            "#8800E676", // Translucent Green Highlighter
            "#FF000000", // Black
            "#FFFFFFFF"  // White
        )

        lateinit var hueBar: SeekBar
        lateinit var satBar: SeekBar
        lateinit var valBar: SeekBar
        lateinit var alphaBar: SeekBar

        fun syncUiFromColor(newColor: Int, updateHex: Boolean) {
            selectedColor = newColor
            (preview.background as? GradientDrawable)?.setColor(selectedColor)
            Color.colorToHSV(selectedColor, hsv)
            alphaVal = Color.alpha(selectedColor)
            updatingColorFields = true
            hueBar.progress = hsv[0].toInt()
            satBar.progress = (hsv[1] * 100).toInt()
            valBar.progress = (hsv[2] * 100).toInt()
            alphaBar.progress = alphaVal
            if (updateHex) {
                hexInput.setText(String.format("#%08X", selectedColor))
            }
            updatingColorFields = false
        }

        for (hex in presets) {
            val c = Color.parseColor(hex)
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(2, Color.DKGRAY)
                }
                layoutParams = LinearLayout.LayoutParams(68, 68).apply {
                    setMargins(8, 0, 8, 0)
                }
                setOnClickListener { syncUiFromColor(c, true) }
            }
            swatchesRow.addView(swatch)
        }

        fun updateFromSliders() {
            if (updatingColorFields) return
            selectedColor = Color.HSVToColor(alphaVal, hsv)
            (preview.background as? GradientDrawable)?.setColor(selectedColor)
            updatingColorFields = true
            hexInput.setText(String.format("#%08X", selectedColor))
            updatingColorFields = false
        }

        fun createSlider(label: String, maxVal: Int, initVal: Int, onChange: (Int) -> Unit): SeekBar {
            box.addView(TextView(this).apply {
                text = label
                setPadding(0, 10, 0, 2)
            })
            val sb = SeekBar(this).apply {
                max = maxVal
                progress = initVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            onChange(p)
                            updateFromSliders()
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            box.addView(sb)
            return sb
        }

        box.addView(preview)
        box.addView(swatchesRow)
        box.addView(hexInput)
        hueBar = createSlider("Hue (0° - 360°)", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        satBar = createSlider("Saturation (%)", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        valBar = createSlider("Brightness (%)", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        alphaBar = createSlider("Opacity / Highlighter Transparency", 255, alphaVal) {
            alphaVal = it.coerceAtLeast(25)
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (updatingColorFields) return
                try {
                    val parsed = Color.parseColor(s.toString().trim())
                    syncUiFromColor(parsed, false)
                } catch (_: Exception) {
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Full Color Studio")
            .setView(scroll)
            .setPositiveButton("Apply") { _, _ ->
                canvasView.currentColor = selectedColor
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
