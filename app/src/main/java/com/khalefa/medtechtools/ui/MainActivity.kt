package com.khalefa.medtechtools.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import com.khalefa.medtechtools.service.FloatingBubbleService
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

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
        applySavedLocale()
        super.onCreate(savedInstanceState)

        if (!prefs.termsAccepted) {
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiTheme.BG_NAVY)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = UiTheme.dp(this@MainActivity, 16)
            setPadding(p, UiTheme.dp(this@MainActivity, 20), p, UiTheme.dp(this@MainActivity, 32))
        }

        // 1. Header Card (App Icon, Name, Tagline, Developer)
        val headerCard = UiTheme.createCard(this, goldBorder = true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            val sz = UiTheme.dp(this@MainActivity, 72)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                bottomMargin = UiTheme.dp(this@MainActivity, 10)
            }
        }
        headerCard.addView(logoIv)
        headerCard.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.GOLD_LIGHT)
            gravity = Gravity.CENTER
        })
        headerCard.addView(TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.BRIGHT_BLUE)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        })
        headerCard.addView(TextView(this).apply {
            text = getString(R.string.subtitle_dev)
            textSize = 13f
            setTextColor(UiTheme.TEXT_MUTED)
            gravity = Gravity.CENTER
        })
        root.addView(headerCard)

        // 2. Service Controls Card
        val controlCard = UiTheme.createCard(this, goldBorder = false)
        controlCard.addView(UiTheme.goldButton(this, getString(R.string.start_bubble), topMarginDp = 0) {
            startActivity(Intent(this@MainActivity, PermissionBridgeActivity::class.java))
        })
        controlCard.addView(UiTheme.primaryButton(this, getString(R.string.stop_bubble), topMarginDp = 10) {
            stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            Toast.makeText(this@MainActivity, getString(R.string.stop_bubble), Toast.LENGTH_SHORT).show()
        })
        controlCard.addView(UiTheme.secondaryButton(this, getString(R.string.telegram_channel), topMarginDp = 10) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/KhalefaTechMed")))
        })
        controlCard.addView(UiTheme.secondaryButton(this, getString(R.string.view_terms_btn), topMarginDp = 8) {
            startActivity(Intent(this@MainActivity, TermsActivity::class.java).apply {
                putExtra("view_only", true)
            })
        })
        root.addView(controlCard)

        // 3. Display Language Card
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

        // 4. Floating Bubble Size & Logical Transparency Card
        val bubbleCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(bubbleCard, getString(R.string.bubble_settings), topPad = 0)

        val sizeLabel = TextView(this).apply {
            text = "${getString(R.string.bubble_size)}: ${prefs.bubbleSizeDp} dp"
            setTextColor(UiTheme.TEXT_PRIMARY)
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

        // Logical Transparency Slider: 0% = Solid, 80% = Very Transparent
        val transLabel = TextView(this).apply {
            text = "${getString(R.string.bubble_transparency)}: ${prefs.bubbleTransparency}%"
            setTextColor(UiTheme.TEXT_PRIMARY)
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

        bubbleCard.addView(UiTheme.primaryButton(this, getString(R.string.pick_bubble_icon), topMarginDp = 12) {
            pickIconLauncher.launch("image/*")
        })
        bubbleCard.addView(UiTheme.secondaryButton(this, getString(R.string.reset_bubble_icon), topMarginDp = 8) {
            prefs.clearCustomBubbleIcon()
            refreshBubbleService()
        })
        root.addView(bubbleCard)

        // 5. Triple-Tap Behavior & Optional 4-Way Flick Gestures Card
        val actionsCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(actionsCard, getString(R.string.triple_actions), topPad = 0)
        val actionNames = PrefsManager.ALL_ACTIONS.map { getActionDisplayName(it) }

        fun addActionPicker(parent: LinearLayout, label: String, currentKey: String, onSave: (String) -> Unit) {
            parent.addView(TextView(this).apply {
                text = label
                setTextColor(UiTheme.GOLD_LIGHT)
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
            setTextColor(UiTheme.TEXT_PRIMARY)
            isChecked = prefs.swipeGesturesEnabled
            setOnCheckedChangeListener { _, checked ->
                prefs.swipeGesturesEnabled = checked
                swipeContainer.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        actionsCard.addView(swipeToggle)

        addActionPicker(swipeContainer, getString(R.string.swipe_up), prefs.swipeUpAction) { prefs.swipeUpAction = it }
        addActionPicker(swipeContainer, getString(R.string.swipe_down), prefs.swipeDownAction) { prefs.swipeDownAction = it }
        addActionPicker(swipeContainer, getString(R.string.swipe_left), prefs.swipeLeftAction) { prefs.swipeLeftAction = it }
        addActionPicker(swipeContainer, getString(R.string.swipe_right), prefs.swipeRightAction) { prefs.swipeRightAction = it }
        actionsCard.addView(swipeContainer)
        root.addView(actionsCard)

        // 6. Tools Visibility Card
        val visibilityCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(visibilityCard, getString(R.string.visible_tools), topPad = 0)
        val menuTools = PrefsManager.ALL_ACTIONS.filter {
            it != PrefsManager.ACT_NONE && it != PrefsManager.ACT_SHOW_MENU
        }
        for (key in menuTools) {
            visibilityCard.addView(CheckBox(this).apply {
                text = getActionDisplayName(key)
                setTextColor(UiTheme.TEXT_PRIMARY)
                isChecked = prefs.isToolEnabled(key)
                setOnCheckedChangeListener { _, checked -> prefs.setToolEnabled(key, checked) }
            })
        }
        root.addView(visibilityCard)

        // 7. Quick Launch Card
        val quickCard = UiTheme.createCard(this, goldBorder = false)
        addSectionHeader(quickCard, "Quick Launch", topPad = 0)
        quickCard.addView(UiTheme.primaryButton(this, getString(R.string.tool_ai_chat), topMarginDp = 4) {
            startActivity(Intent(this@MainActivity, AiChatActivity::class.java))
        })
        quickCard.addView(UiTheme.secondaryButton(this, getString(R.string.tool_stylus), topMarginDp = 8) {
            if (prefs.getLatestClipFile() != null) {
                startActivity(Intent(this@MainActivity, EditorActivity::class.java))
            } else {
                Toast.makeText(this@MainActivity, "Capture a screenshot first", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(quickCard)

        // 8. Exit Only Button at the Very Bottom
        root.addView(UiTheme.dangerButton(this, getString(R.string.exit_app), topMarginDp = 6) {
            stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
            finishAffinity()
            exitProcess(0)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun getActionDisplayName(key: String): String = when (key) {
        PrefsManager.ACT_NONE -> getString(R.string.tool_none)
        PrefsManager.ACT_SCREENSHOT -> getString(R.string.tool_screenshot)
        PrefsManager.ACT_CROP_COPY -> getString(R.string.tool_crop_copy)
        PrefsManager.ACT_SHOW_MENU -> getString(R.string.tool_show_menu)
        PrefsManager.ACT_CROP_OCR -> getString(R.string.tool_ocr)
        PrefsManager.ACT_STYLUS_EDITOR -> getString(R.string.tool_stylus)
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
            setTextColor(UiTheme.GOLD_LIGHT)
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
