package com.khalefa.medtechtools.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import kotlin.system.exitProcess

class TermsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var viewOnlyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        viewOnlyMode = intent.getBooleanExtra("view_only", false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewOnlyMode || prefs.termsAccepted) {
                    finish()
                } else {
                    finishAffinity()
                    exitProcess(0)
                }
            }
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F1419"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 48, 40, 48)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A222B"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#00897B"))
            }
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        val taglineView = TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 24)
        }

        val termsHeader = TextView(this).apply {
            text = getString(R.string.terms_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        val termsBody = TextView(this).apply {
            text = getString(R.string.terms_body)
            textSize = 15f
            setLineSpacing(8f, 1.15f)
            setTextColor(Color.parseColor("#E0E6ED"))
            setPadding(0, 0, 0, 28)
        }

        val devFooter = TextView(this).apply {
            text = getString(R.string.subtitle_dev)
            textSize = 13f
            setTextColor(Color.parseColor("#90A4AE"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        card.addView(titleView)
        card.addView(taglineView)
        card.addView(termsHeader)
        card.addView(termsBody)
        card.addView(devFooter)

        if (viewOnlyMode) {
            val closeBtn = Button(this).apply {
                text = "OK"
                setBackgroundColor(Color.parseColor("#00897B"))
                setTextColor(Color.WHITE)
                setOnClickListener { finish() }
            }
            card.addView(closeBtn)
        } else {
            val agreeBtn = Button(this).apply {
                text = getString(R.string.terms_agree_btn)
                setBackgroundColor(Color.parseColor("#00897B"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    prefs.termsAccepted = true
                    startActivity(Intent(this@TermsActivity, MainActivity::class.java))
                    finish()
                }
            }

            val declineBtn = Button(this).apply {
                text = getString(R.string.terms_decline_btn)
                setBackgroundColor(Color.parseColor("#B71C1C"))
                setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }
                layoutParams = lp
                setOnClickListener {
                    finishAffinity()
                    exitProcess(0)
                }
            }

            card.addView(agreeBtn)
            card.addView(declineBtn)
        }

        root.addView(card)
        scroll.addView(root)
        setContentView(scroll)
    }
}
