package com.khalefa.medtechtools.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

object UiTheme {
    val BG_NAVY = Color.parseColor("#0A1628")
    val CARD_BLUE = Color.parseColor("#112240")
    val SURFACE_BLUE = Color.parseColor("#162D50")
    val BORDER_BLUE = Color.parseColor("#233554")
    val ROYAL_BLUE = Color.parseColor("#1565C0")
    val BRIGHT_BLUE = Color.parseColor("#1E88E5")
    val GOLD = Color.parseColor("#D4AF37")
    val GOLD_LIGHT = Color.parseColor("#FFD54F")
    val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    val TEXT_MUTED = Color.parseColor("#94A3B8")
    val DANGER_RED = Color.parseColor("#B71C1C")
    val DANGER_BORDER = Color.parseColor("#EF5350")

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    fun createCard(ctx: Context, goldBorder: Boolean = false): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 16)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(CARD_BLUE)
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(dp(ctx, 1), if (goldBorder) GOLD else BORDER_BLUE)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 14)
            }
        }
    }

    fun styleButton(
        ctx: Context,
        btn: Button,
        bgColor: Int,
        textColor: Int,
        strokeColor: Int,
        topMarginDp: Int = 8
    ): Button {
        btn.isAllCaps = false
        btn.setTextColor(textColor)
        btn.setTypeface(null, Typeface.BOLD)
        btn.textSize = 14.5f
        btn.gravity = Gravity.CENTER
        btn.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        btn.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(ctx, 12).toFloat()
            setStroke(dp(ctx, 1), strokeColor)
        }
        btn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(ctx, topMarginDp)
        }
        return btn
    }

    fun primaryButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, ROYAL_BLUE, TEXT_PRIMARY, GOLD, topMarginDp)
    }

    fun goldButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, GOLD, BG_NAVY, GOLD_LIGHT, topMarginDp)
    }

    fun secondaryButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, SURFACE_BLUE, GOLD_LIGHT, BORDER_BLUE, topMarginDp)
    }

    fun dangerButton(ctx: Context, label: String, topMarginDp: Int = 12, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, DANGER_RED, TEXT_PRIMARY, DANGER_BORDER, topMarginDp)
    }
}
