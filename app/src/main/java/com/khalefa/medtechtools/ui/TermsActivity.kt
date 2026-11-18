package com.khalefa.medtechtools.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
        UiTheme.applyNightMode(this)
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
            setBackgroundColor(UiTheme.bg(this@TermsActivity))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val p = UiTheme.dp(this@TermsActivity, 20)
            setPadding(p, p, p, p)
        }

        val card = UiTheme.createCard(this, goldBorder = true)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.royalBlue())
            gravity = Gravity.CENTER
        }

        val taglineView = TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.goldText(this@TermsActivity))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 20)
        }

        val termsHeader = TextView(this).apply {
            text = getString(R.string.terms_title)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@TermsActivity))
            setPadding(0, 0, 0, 12)
        }

        val termsBody = TextView(this).apply {
            text = getString(R.string.terms_body)
            textSize = 14.5f
            setLineSpacing(8f, 1.15f)
            setTextColor(UiTheme.textPrimary(this@TermsActivity))
            setPadding(0, 0, 0, 22)
        }

        val devFooter = TextView(this).apply {
            text = getString(R.string.subtitle_dev)
            textSize = 13f
            setTextColor(UiTheme.textMuted(this@TermsActivity))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        card.addView(titleView)
        card.addView(taglineView)
        card.addView(termsHeader)
        card.addView(termsBody)
        card.addView(devFooter)

        if (viewOnlyMode) {
            card.addView(UiTheme.primaryButton(this, "OK", topMarginDp = 4) { finish() })
        } else {
            card.addView(UiTheme.primaryButton(this, getString(R.string.terms_agree_btn), topMarginDp = 4) {
                prefs.termsAccepted = true
                startActivity(Intent(this@TermsActivity, MainActivity::class.java))
                finish()
            })
            card.addView(UiTheme.dangerButton(this, getString(R.string.terms_decline_btn), topMarginDp = 10) {
                finishAffinity()
                exitProcess(0)
            })
        }

        root.addView(card)
        scroll.addView(root)
        setContentView(scroll)
    }
}
