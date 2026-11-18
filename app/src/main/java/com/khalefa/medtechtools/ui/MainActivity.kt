package com.khalefa.medtechtools.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.service.FloatingBubbleService
import com.khalefa.medtechtools.service.MedTechAccessibilityService
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var toggleBubbleBtn: Button
    private var accessibilityBtn: Button? = null

    private val pickIconLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    prefs.saveCustomBubbleIcon(bmp)
                    refreshBubbleService()
                    Toast.makeText(this, "Bubble Icon Updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsManager(this)
        UiTheme.applyNightMode(this)
        applySavedLocale()
        super.onCreate(savedInstanceState)

        if (!prefs.termsAccepted) {
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiTheme.bg(this@MainActivity))
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = UiTheme.dp(this@MainActivity, 16)
            setPadding(p, UiTheme.dp(this@MainActivity, 20), p, UiTheme.dp(this@MainActivity, 32))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val p = UiTheme.dp(this, 16)
            root.setPadding(p + sys.left, p + sys.top, p + sys.right, UiTheme.dp(this, 24) + sys.bottom)
            insets
        }

        // 1. Header Card with Top Sun/Moon Theme Toggle Button & Rounded Logo
        val headerCard = UiTheme.createCard(this, goldBorder = true)

        val topBar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val themeToggleBtn = TextView(this).apply {
            text = if (UiTheme.isDark(this@MainActivity)) "☀️" else "🌙"
            textSize = 22f
            gravity = Gravity.CENTER
            val btnSize = UiTheme.dp(this@MainActivity, 44)
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize, Gravity.TOP or Gravity.END)
            background = GradientDrawable().apply {
                setColor(UiTheme.surface(this@MainActivity))
                shape = GradientDrawable.OVAL
                setStroke(UiTheme.dp(this@MainActivity, 1), UiTheme.goldSoft())
            }
            setOnClickListener {
                prefs.appTheme = if (UiTheme.isDark(this@MainActivity)) "light" else "dark"
                UiTheme.applyNightMode(this@MainActivity)
                recreate()
            }
        }
        topBar.addView(themeToggleBtn)

        val logoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, UiTheme.dp(this@MainActivity, 8), 0, 0)
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = UiTheme.dp(this@MainActivity, 22).toFloat()
            }
            clipToOutline = true
            val sz = UiTheme.dp(this@MainActivity, 76)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                bottomMargin = UiTheme.dp(this@MainActivity, 10)
            }
        }
        logoContainer.addView(logoIv)
        logoContainer.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.royalBlue())
            gravity = Gravity.CENTER
        })
        logoContainer.addView(TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.goldText(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 2)
        })
        topBar.addView(logoContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        headerCard.addView(topBar)
        root.addView(headerCard)

        // 2. Service Controls Card (Single Toggle Button + Universal Screenshot Permission)
        val controlCard = UiTheme.createCard(this, goldBorder = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accessibilityBtn = UiTheme.goldButton(this, getString(R.string.grant_accessibility), topMarginDp = 0) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            controlCard.addView(accessibilityBtn)
        }
        toggleBubbleBtn = UiTheme.primaryButton(this, getString(R.string.start_bubble), topMarginDp = 10) {
            if (FloatingBubbleService.isRunning) {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                FloatingBubbleService.isRunning = false
                updateServiceButtons()
            } else {
                startActivity(Intent(this@MainActivity, PermissionBridgeActivity::class.java))
            }
        }
        controlCard.addView(toggleBubbleBtn)

        controlCard.addView(UiTheme.secondaryButton(this, getString(R.string.telegram_channel), topMarginDp = 10) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/KhalefaTechMed")))
        })
        controlCard.addView(UiTheme.secondaryButton(this, getString(R.string.view_terms_btn), topMarginDp = 8) {
            startActivity(Intent(this@MainActivity, TermsActivity::class.java).apply {
                putExtra("view_only", true)
            })
        })
        root.addView(controlCard)

        // 3. Quick How-To Guide Card
        val guideCard = UiTheme.createCard(this, goldBorder = true)
        addSectionHeader(guideCard, getString(R.string.how_to_title), topPad = 0)
        guideCard.addView(TextView(this).apply {
            text = getString(R.string.how_to_body)
            textSize = 13.5f
            setLineSpacing(6f, 1.15f)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        root.addView(guideCard)

        // 4. Display Language Card
        val langCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(langCard, getString(R.string.language_label), topPad = 0)
        val langSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("System Default", "العربية", "English")
            )
            setSelection(
                when (prefs.language) {
                    "ar" -> 1
                    "en" -> 2
                    else -> 0
                }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val chosen = when (pos) {
                        1 -> "ar"
                        2 -> "en"
                        else -> "system"
                    }
                    if (chosen != prefs.language) {
                        prefs.language = chosen
                        applySavedLocale()
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        langCard.addView(langSpinner)
        root.addView(langCard)

        // 5. Floating Bubble Size, Transparency & Gallery Save Card
        val bubbleCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(bubbleCard, getString(R.string.bubble_settings), topPad = 0)

        val sizeLabel = TextView(this).apply {
            text = "${getString(R.string.bubble_size)}: ${prefs.bubbleSizeDp} dp"
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        }
        bubbleCard.addView(sizeLabel)
        bubbleCard.addView(SeekBar(this).apply {
            max = 60
            progress = prefs.bubbleSizeDp - 36
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) {
                        prefs.bubbleSizeDp = p + 36
                        sizeLabel.text = "${getString(R.string.bubble_size)}: ${prefs.bubbleSizeDp} dp"
                        refreshBubbleService()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val transLabel = TextView(this).apply {
            text = "${getString(R.string.bubble_transparency)}: ${prefs.bubbleTransparency}%"
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(0, UiTheme.dp(this@MainActivity, 10), 0, 0)
        }
        bubbleCard.addView(transLabel)
        bubbleCard.addView(SeekBar(this).apply {
            max = 80
            progress = prefs.bubbleTransparency
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) {
                        prefs.bubbleTransparency = p
                        transLabel.text = "${getString(R.string.bubble_transparency)}: $p%"
                        refreshBubbleService()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val galleryCheck = CheckBox(this).apply {
            text = getString(R.string.save_to_gallery)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            isChecked = prefs.saveToGallery
            setPadding(0, UiTheme.dp(this@MainActivity, 8), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                prefs.saveToGallery = checked
            }
        }
        bubbleCard.addView(galleryCheck)

        bubbleCard.addView(UiTheme.primaryButton(this, getString(R.string.pick_bubble_icon), topMarginDp = 12) {
            pickIconLauncher.launch("image/*")
        })
        bubbleCard.addView(UiTheme.secondaryButton(this, getString(R.string.reset_bubble_icon), topMarginDp = 8) {
            prefs.clearCustomBubbleIcon()
            refreshBubbleService()
        })
        root.addView(bubbleCard)

        // 6. Tap Behavior & 8-Gesture System Card
        val actionsCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(actionsCard, getString(R.string.triple_actions), topPad = 0)
        val actionNames = PrefsManager.ALL_ACTIONS.map { getActionDisplayName(it) }

        fun addActionPicker(parent: LinearLayout, label: String, currentKey: String, onSave: (String) -> Unit) {
            parent.addView(TextView(this).apply {
                text = label
                setTextColor(UiTheme.goldText(this@MainActivity))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 10, 0, 2)
            })
            parent.addView(Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    actionNames
                )
                setSelection(PrefsManager.ALL_ACTIONS.indexOf(currentKey).coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        onSave(PrefsManager.ALL_ACTIONS[pos])
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            })
        }

        addActionPicker(actionsCard, getString(R.string.single_tap), prefs.singleTapAction) { prefs.singleTapAction = it }
        addActionPicker(actionsCard, getString(R.string.double_tap), prefs.doubleTapAction) { prefs.doubleTapAction = it }
        addActionPicker(actionsCard, getString(R.string.long_press), prefs.longPressAction) { prefs.longPressAction = it }

        val swipeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.swipeGesturesEnabled) View.VISIBLE else View.GONE
        }

        val swipeToggle = CheckBox(this).apply {
            text = getString(R.string.enable_swipe_gestures)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            isChecked = prefs.swipeGesturesEnabled
            setOnCheckedChangeListener { _, checked ->
                prefs.swipeGesturesEnabled = checked
                swipeContainer.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        actionsCard.addView(swipeToggle)

        addActionPicker(swipeContainer, getString(R.string.gesture_half_up), prefs.gestureHalfUpAction) { prefs.gestureHalfUpAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_half_down), prefs.gestureHalfDownAction) { prefs.gestureHalfDownAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_l_up), prefs.gestureLUpAction) { prefs.gestureLUpAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_l_down), prefs.gestureLDownAction) { prefs.gestureLDownAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_tri_up), prefs.gestureTriUpAction) { prefs.gestureTriUpAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_tri_down), prefs.gestureTriDownAction) { prefs.gestureTriDownAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_circle_cw), prefs.gestureCircleCwAction) { prefs.gestureCircleCwAction = it }
        addActionPicker(swipeContainer, getString(R.string.gesture_circle_ccw), prefs.gestureCircleCcwAction) { prefs.gestureCircleCcwAction = it }
        actionsCard.addView(swipeContainer)
        root.addView(actionsCard)

        // 7. Tools Order, Visibility & Restore Defaults Card
        val visibilityCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(visibilityCard, getString(R.string.visible_tools), topPad = 0)
        val orderListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun rebuildOrderList() {
            orderListLayout.removeAllViews()
            val currentOrder = prefs.menuOrder.toMutableList()
            for (i in currentOrder.indices) {
                val key = currentOrder[i]
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, UiTheme.dp(this@MainActivity, 3), 0, UiTheme.dp(this@MainActivity, 3))
                }
                val cb = CheckBox(this).apply {
                    text = getActionDisplayName(key)
                    setTextColor(UiTheme.textPrimary(this@MainActivity))
                    isChecked = prefs.isToolEnabled(key)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnCheckedChangeListener { _, checked -> prefs.setToolEnabled(key, checked) }
                }
                val btnUp = Button(this).apply {
                    text = "▲"
                    isEnabled = i > 0
                    setOnClickListener {
                        if (i > 0) {
                            val tmp = currentOrder[i - 1]
                            currentOrder[i - 1] = currentOrder[i]
                            currentOrder[i] = tmp
                            prefs.menuOrder = currentOrder
                            rebuildOrderList()
                        }
                    }
                }
                val btnDown = Button(this).apply {
                    text = "▼"
                    isEnabled = i < currentOrder.size - 1
                    setOnClickListener {
                        if (i < currentOrder.size - 1) {
                            val tmp = currentOrder[i + 1]
                            currentOrder[i + 1] = currentOrder[i]
                            currentOrder[i] = tmp
                            prefs.menuOrder = currentOrder
                            rebuildOrderList()
                        }
                    }
                }
                row.addView(cb)
                row.addView(btnUp, LinearLayout.LayoutParams(UiTheme.dp(this, 48), UiTheme.dp(this, 42)))
                row.addView(btnDown, LinearLayout.LayoutParams(UiTheme.dp(this, 48), UiTheme.dp(this, 42)))
                orderListLayout.addView(row)
            }
        }
        rebuildOrderList()
        visibilityCard.addView(orderListLayout)

        visibilityCard.addView(UiTheme.goldButton(this, getString(R.string.restore_defaults), topMarginDp = 12) {
            prefs.resetGesturesAndMenuToDefaults()
            Toast.makeText(this@MainActivity, getString(R.string.defaults_restored), Toast.LENGTH_SHORT).show()
            recreate()
        })
        root.addView(visibilityCard)

        // 8. Quick Launch Card
        val quickCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(quickCard, "Quick Launch", topPad = 0)
        quickCard.addView(UiTheme.primaryButton(this, getString(R.string.tool_ai_chat), topMarginDp = 4) {
            startActivity(Intent(this@MainActivity, AiChatActivity::class.java))
        })
        root.addView(quickCard)

        // 9. Soft Red Exit Button at the Very Bottom
        root.addView(UiTheme.dangerButton(this, getString(R.string.exit_app), topMarginDp = 6) {
            stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            finishAffinity()
            exitProcess(0)
        })

        // 10. Developer Credit at the Bottom
        root.addView(TextView(this).apply {
            text = getString(R.string.subtitle_dev)
            textSize = 13f
            setTextColor(UiTheme.textMuted(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(0, UiTheme.dp(this@MainActivity, 14), 0, UiTheme.dp(this@MainActivity, 6))
        })

        scroll.addView(root)
        setContentView(scroll)
        updateServiceButtons()
    }

    override fun onResume() {
        super.onResume()
        updateServiceButtons()
    }

    private fun updateServiceButtons() {
        toggleBubbleBtn.text = if (FloatingBubbleService.isRunning) {
            getString(R.string.stop_bubble)
        } else {
            getString(R.string.start_bubble)
        }
        accessibilityBtn?.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !MedTechAccessibilityService.isAvailable()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun getActionDisplayName(key: String): String = when (key) {
        PrefsManager.ACT_NONE -> getString(R.string.tool_none)
        PrefsManager.ACT_CROP_COPY -> getString(R.string.tool_crop_copy)
        PrefsManager.ACT_SHOW_MENU -> getString(R.string.tool_show_menu)
        PrefsManager.ACT_CROP_OCR -> getString(R.string.tool_ocr)
        PrefsManager.ACT_PIN_IMAGE -> getString(R.string.tool_pin)
        PrefsManager.ACT_CLIPBOARD -> getString(R.string.tool_clipboard)
        PrefsManager.ACT_GOOGLE_SEARCH -> getString(R.string.tool_google_search)
        PrefsManager.ACT_GOOGLE_TRANSLATE -> getString(R.string.tool_google_translate)
        PrefsManager.ACT_AI_CHAT -> getString(R.string.tool_ai_chat)
        PrefsManager.ACT_EXIT -> getString(R.string.tool_exit)
        else -> key
    }

    private fun addSectionHeader(parent: LinearLayout, title: String, topPad: Int = 20) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.royalBlue())
            setPadding(0, topPad, 0, UiTheme.dp(this@MainActivity, 8))
        })
    }

    private fun refreshBubbleService() {
        if (FloatingBubbleService.isRunning) {
            startService(Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_REFRESH_BUBBLE
            })
        }
    }

    private fun applySavedLocale() {
        val locales = when (prefs.language) {
            "ar" -> LocaleListCompat.forLanguageTags("ar")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
