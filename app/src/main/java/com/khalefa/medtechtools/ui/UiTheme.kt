package com.khalefa.medtechtools.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

object UiTheme {
    // Light Royal Blue & Soft Gold Palette
    val BG_LIGHT = Color.parseColor("#F0F4FA")
    val CARD_WHITE = Color.parseColor("#FFFFFF")
    val SURFACE_TINT = Color.parseColor("#E6EEF9")
    val BORDER_BLUE = Color.parseColor("#CBD8EC")
    val ROYAL_BLUE = Color.parseColor("#2B6CB0")
    val BRIGHT_BLUE = Color.parseColor("#3B82F6")
    val GOLD_SOFT = Color.parseColor("#C9A84C")
    val GOLD_PALE = Color.parseColor("#F5E6B8")
    val GOLD_TEXT = Color.parseColor("#7A5C12")
    val TEXT_PRIMARY = Color.parseColor("#1E293B")
    val TEXT_MUTED = Color.parseColor("#475569")
    val DANGER_SOFT = Color.parseColor("#C05656")
    val DANGER_BORDER = Color.parseColor("#E08E8E")

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    fun createCard(ctx: Context, goldBorder: Boolean = false): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 16)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(CARD_WHITE)
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(dp(ctx, 1), if (goldBorder) GOLD_SOFT else BORDER_BLUE)
            }
            elevation = dp(ctx, 2).toFloat()
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
        }, ROYAL_BLUE, Color.WHITE, BRIGHT_BLUE, topMarginDp)
    }

    fun goldButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, GOLD_PALE, GOLD_TEXT, GOLD_SOFT, topMarginDp)
    }

    fun secondaryButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, SURFACE_TINT, ROYAL_BLUE, BORDER_BLUE, topMarginDp)
    }

    fun dangerButton(ctx: Context, label: String, topMarginDp: Int = 12, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, DANGER_SOFT, Color.WHITE, DANGER_BORDER, topMarginDp)
    }
}
