package com.khalefa.medtechtools.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import com.khalefa.medtechtools.prefs.PrefsManager

object UiTheme {
    fun isDark(ctx: Context): Boolean = PrefsManager(ctx).appTheme == "dark"

    fun applyNightMode(ctx: Context) {
        val mode = if (isDark(ctx)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun bg(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#0A1628") else Color.parseColor("#F0F4FA")
    fun card(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#112240") else Color.parseColor("#FFFFFF")
    fun surface(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#162D50") else Color.parseColor("#E6EEF9")
    fun border(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#233554") else Color.parseColor("#CBD8EC")
    fun royalBlue(): Int = Color.parseColor("#2B6CB0")
    fun brightBlue(): Int = Color.parseColor("#3B82F6")
    fun goldSoft(): Int = Color.parseColor("#C9A84C")
    fun goldPale(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#2A2310") else Color.parseColor("#F5E6B8")
    fun goldText(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#E2C878") else Color.parseColor("#7A5C12")
    fun textPrimary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#F1F5F9") else Color.parseColor("#1E293B")
    fun textMuted(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
    fun dangerSoft(): Int = Color.parseColor("#C05656")
    fun dangerBorder(): Int = Color.parseColor("#E08E8E")

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    fun createCard(ctx: Context, goldBorder: Boolean = false): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 16)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(card(ctx))
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(dp(ctx, 1), if (goldBorder) goldSoft() else border(ctx))
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
        }, royalBlue(), Color.WHITE, brightBlue(), topMarginDp)
    }

    fun goldButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, goldPale(ctx), goldText(ctx), goldSoft(), topMarginDp)
    }

    fun secondaryButton(ctx: Context, label: String, topMarginDp: Int = 8, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, surface(ctx), if (isDark(ctx)) Color.WHITE else royalBlue(), border(ctx), topMarginDp)
    }

    fun dangerButton(ctx: Context, label: String, topMarginDp: Int = 12, onClick: () -> Unit): Button {
        return styleButton(ctx, Button(ctx).apply {
            text = label
            setOnClickListener { onClick() }
        }, dangerSoft(), Color.WHITE, dangerBorder(), topMarginDp)
    }
}
