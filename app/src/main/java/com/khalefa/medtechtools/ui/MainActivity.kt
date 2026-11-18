package com.khalefa.medtechtools.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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

        // Enforce Ethical & Islamic Compliance Terms Gate on First Launch
        if (!prefs.termsAccepted) {
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 56)
        }

        // App Title, Tagline ("أداة كل الطلاب") & Developer Info
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00897B"))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            setPadding(0, 4, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.subtitle_dev)
            textSize = 13f
            setPadding(0, 2, 0, 14)
        })

        // Telegram Channel & Terms Buttons
        root.addView(Button(this).apply {
            text = getString(R.string.telegram_channel)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/KhalefaTechMed")))
            }
        })

        root.addView(Button(this).apply {
            text = getString(R.string.view_terms_btn)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, TermsActivity::class.java).apply {
                    putExtra("view_only", true)
                })
            }
        })

        // Service Controls: Start, Stop, Native Kotlin Force Exit
        root.addView(Button(this).apply {
            text = getString(R.string.start_bubble)
            setBackgroundColor(Color.parseColor("#00897B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PermissionBridgeActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = getString(R.string.stop_bubble)
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                Toast.makeText(this@MainActivity, getString(R.string.stop_bubble), Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = getString(R.string.exit_app)
            setBackgroundColor(Color.parseColor("#C62828"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                finishAffinity()
                exitProcess(0)
            }
        })

        // Display Language Switcher
        addSectionHeader(root, getString(R.string.language_label))
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
        root.addView(langSpinner)

        // Floating Bubble Size, Opacity & Custom Icon
        addSectionHeader(root, getString(R.string.bubble_settings))
        root.addView(TextView(this).apply { text = getString(R.string.bubble_size) })
        root.addView(SeekBar(this).apply {
            max = 60
            progress = prefs.bubbleSizeDp - 36
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) {
                        prefs.bubbleSizeDp = p + 36
                        refreshBubbleService()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        root.addView(TextView(this).apply { text = getString(R.string.bubble_opacity) })
        root.addView(SeekBar(this).apply {
            max = 80
            progress = prefs.bubbleOpacity - 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) {
                        prefs.bubbleOpacity = p + 20
                        refreshBubbleService()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        root.addView(Button(this).apply {
            text = getString(R.string.pick_bubble_icon)
            setOnClickListener { pickIconLauncher.launch("image/*") }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.reset_bubble_icon)
            setOnClickListener {
                prefs.clearCustomBubbleIcon()
                refreshBubbleService()
            }
        })

        // Triple-Tap Behavior & Optional 4-Way Flick Gestures
        addSectionHeader(root, getString(R.string.triple_actions))
        val actionNames = PrefsManager.ALL_ACTIONS.map { getActionDisplayName(it) }

        fun addActionPicker(label: String, currentKey: String, onSave: (String) -> Unit) {
            root.addView(TextView(this).apply {
                text = label
                setPadding(0, 10, 0, 2)
            })
            root.addView(Spinner(this).apply {
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

        addActionPicker(getString(R.string.single_tap), prefs.singleTapAction) { prefs.singleTapAction = it }
        addActionPicker(getString(R.string.double_tap), prefs.doubleTapAction) { prefs.doubleTapAction = it }
        addActionPicker(getString(R.string.long_press), prefs.longPressAction) { prefs.longPressAction = it }

        val swipeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.swipeGesturesEnabled) View.VISIBLE else View.GONE
        }

        val swipeToggle = CheckBox(this).apply {
            text = getString(R.string.enable_swipe_gestures)
            isChecked = prefs.swipeGesturesEnabled
            setOnCheckedChangeListener { _, checked ->
                prefs.swipeGesturesEnabled = checked
                swipeContainer.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        root.addView(swipeToggle)

        fun addSwipeActionPicker(label: String, currentKey: String, onSave: (String) -> Unit) {
            swipeContainer.addView(TextView(this).apply {
                text = label
                setPadding(0, 8, 0, 2)
            })
            swipeContainer.addView(Spinner(this).apply {
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

        addSwipeActionPicker(getString(R.string.swipe_up), prefs.swipeUpAction) { prefs.swipeUpAction = it }
        addSwipeActionPicker(getString(R.string.swipe_down), prefs.swipeDownAction) { prefs.swipeDownAction = it }
        addSwipeActionPicker(getString(R.string.swipe_left), prefs.swipeLeftAction) { prefs.swipeLeftAction = it }
        addSwipeActionPicker(getString(R.string.swipe_right), prefs.swipeRightAction) { prefs.swipeRightAction = it }
        root.addView(swipeContainer)

        // Tools Visibility Checkboxes
        addSectionHeader(root, getString(R.string.visible_tools))
        val menuTools = PrefsManager.ALL_ACTIONS.filter {
            it != PrefsManager.ACT_NONE && it != PrefsManager.ACT_SHOW_MENU
        }
        for (key in menuTools) {
            root.addView(CheckBox(this).apply {
                text = getActionDisplayName(key)
                isChecked = prefs.isToolEnabled(key)
                setOnCheckedChangeListener { _, checked -> prefs.setToolEnabled(key, checked) }
            })
        }

        // Quick Launch Buttons for AI Chat & Stylus Studio
        addSectionHeader(root, "Quick Launch")
        root.addView(Button(this).apply {
            text = getString(R.string.tool_ai_chat)
            setOnClickListener { startActivity(Intent(this@MainActivity, AiChatActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.tool_stylus)
            setOnClickListener {
                if (prefs.getLatestClipFile() != null) {
                    startActivity(Intent(this@MainActivity, EditorActivity::class.java))
                } else {
                    Toast.makeText(this@MainActivity, "Capture a screenshot clip first", Toast.LENGTH_SHORT).show()
                }
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun getActionDisplayName(key: String): String = when (key) {
        PrefsManager.ACT_NONE -> getString(R.string.tool_none)
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

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00897B"))
            setPadding(0, 28, 0, 8)
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
