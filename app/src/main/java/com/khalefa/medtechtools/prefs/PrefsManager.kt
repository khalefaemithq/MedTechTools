package com.khalefa.medtechtools.prefs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class PrefsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("medtech_tools_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACT_NONE = "none"
        const val ACT_SCREENSHOT = "screenshot"
        const val ACT_CROP_COPY = "crop_copy"
        const val ACT_SHOW_MENU = "show_menu"
        const val ACT_CROP_OCR = "crop_ocr"
        const val ACT_STYLUS_EDITOR = "stylus_editor"
        const val ACT_PIN_IMAGE = "pin_image"
        const val ACT_CLIPBOARD = "clipboard"
        const val ACT_GOOGLE_SEARCH = "google_search"
        const val ACT_GOOGLE_TRANSLATE = "google_translate"
        const val ACT_AI_CHAT = "ai_chat"
        const val ACT_EXIT = "exit"

        val ALL_ACTIONS = listOf(
            ACT_NONE,
            ACT_SCREENSHOT,
            ACT_CROP_COPY,
            ACT_SHOW_MENU,
            ACT_CROP_OCR,
            ACT_STYLUS_EDITOR,
            ACT_PIN_IMAGE,
            ACT_CLIPBOARD,
            ACT_GOOGLE_SEARCH,
            ACT_GOOGLE_TRANSLATE,
            ACT_AI_CHAT,
            ACT_EXIT
        )
    }

    var termsAccepted: Boolean
        get() = prefs.getBoolean("terms_accepted_v1", false)
        set(value) = prefs.edit().putBoolean("terms_accepted_v1", value).apply()

    var userSpecialty: String
        get() = prefs.getString("user_specialty", "general_student") ?: "general_student"
        set(value) = prefs.edit().putString("user_specialty", value).apply()

    var language: String
        get() = prefs.getString("app_lang", "system") ?: "system"
        set(value) = prefs.edit().putString("app_lang", value).apply()

    var bubbleSizeDp: Int
        get() = prefs.getInt("bubble_size", 56)
        set(value) = prefs.edit().putInt("bubble_size", value.coerceIn(36, 96)).apply()

    // Logical Transparency: 0% = fully solid (alpha 1.0), 80% = very transparent (alpha 0.2)
    var bubbleTransparency: Int
        get() = prefs.getInt("bubble_transparency", 15)
        set(value) = prefs.edit().putInt("bubble_transparency", value.coerceIn(0, 80)).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubble_x", 0)
        set(value) = prefs.edit().putInt("bubble_x", value).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", 320)
        set(value) = prefs.edit().putInt("bubble_y", value).apply()

    // Triple-Tap Customizable Defaults:
    // 1. Single Tap -> Instant Screenshot to Private Clipboard
    // 2. Double Tap -> Crop & Extract Text (OCR)
    // 3. Long Press -> Show Customizable Tools Menu
    var singleTapAction: String
        get() = prefs.getString("act_single_v11", ACT_SCREENSHOT) ?: ACT_SCREENSHOT
        set(value) = prefs.edit().putString("act_single_v11", value).apply()

    var doubleTapAction: String
        get() = prefs.getString("act_double", ACT_CROP_OCR) ?: ACT_CROP_OCR
        set(value) = prefs.edit().putString("act_double", value).apply()

    var longPressAction: String
        get() = prefs.getString("act_long", ACT_SHOW_MENU) ?: ACT_SHOW_MENU
        set(value) = prefs.edit().putString("act_long", value).apply()

    var swipeGesturesEnabled: Boolean
        get() = prefs.getBoolean("swipe_gestures_enabled", false)
        set(value) = prefs.edit().putBoolean("swipe_gestures_enabled", value).apply()

    var swipeUpAction: String
        get() = prefs.getString("act_swipe_up", ACT_STYLUS_EDITOR) ?: ACT_STYLUS_EDITOR
        set(value) = prefs.edit().putString("act_swipe_up", value).apply()

    var swipeDownAction: String
        get() = prefs.getString("act_swipe_down", ACT_CLIPBOARD) ?: ACT_CLIPBOARD
        set(value) = prefs.edit().putString("act_swipe_down", value).apply()

    var swipeLeftAction: String
        get() = prefs.getString("act_swipe_left", ACT_GOOGLE_TRANSLATE) ?: ACT_GOOGLE_TRANSLATE
        set(value) = prefs.edit().putString("act_swipe_left", value).apply()

    var swipeRightAction: String
        get() = prefs.getString("act_swipe_right", ACT_GOOGLE_SEARCH) ?: ACT_GOOGLE_SEARCH
        set(value) = prefs.edit().putString("act_swipe_right", value).apply()

    fun isToolEnabled(actionKey: String): Boolean {

        if (actionKey == ACT_NONE || actionKey == ACT_SHOW_MENU) return false
|---|prefs.getBoolean("tool_enabled_$actionKey", true)
    }

    fun setToolEnabled(actionKey: String, enabled: Boolean) {
        prefs.edit().putBoolean("tool_enabled_$actionKey", enabled).apply()
    }

    var aiBaseUrl: String
        get() = prefs.getString("ai_base_url", "https://openrouter.ai/api/v1/chat/completions")
            ?: "https://openrouter.ai/api/v1/chat/completions"
        set(value) = prefs.edit().putString("ai_base_url", value.trim()).apply()

    var aiApiKey: String
        get() = prefs.getString("ai_api_key", "") ?: ""
        set(value) = prefs.edit().putString("ai_api_key", value.trim()).apply()

    var aiModel: String
        get() = prefs.getString("ai_model", "google/gemini-2.0-flash-001")
            ?: "google/gemini-2.0-flash-001"
        set(value) = prefs.edit().putString("ai_model", value.trim()).apply()

    fun saveCustomBubbleIcon(bitmap: Bitmap) {
        val file = File(context.filesDir, "custom_bubble.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
    }

    fun getCustomBubbleIcon(): Bitmap? {
        val file = File(context.filesDir, "custom_bubble.png")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun clearCustomBubbleIcon() {
        val file = File(context.filesDir, "custom_bubble.png")
        if (file.exists()) file.delete()
    }

    fun getClipboardDir(): File {
        val dir = File(context.filesDir, "clipboard")
        if (!dir.exists()) {
            dir.mkdirs()
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) {
                try {
                    noMedia.createNewFile()
                } catch (_: Exception) {
                }
            }
        }
        return dir
    }

    fun saveAndCopyImageToClipboard(bitmap: Bitmap): File {
        val dir = getClipboardDir()
        val file = File(dir, "clip_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val files = dir.listFiles { f -> f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.size > 15) {
            files.drop(15).forEach { it.delete() }
        }
        copyFileUriToSystemClipboard(file)
        return file
    }

    fun copyFileUriToSystemClipboard(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "MedTech Clip", uri)
        clipboard.setPrimaryClip(clip)
    }

    fun copyTextToSystemClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MedTech Text", text))
        prefs.edit().putString("last_extracted_text", text).apply()
    }

    fun getLastExtractedText(): String = prefs.getString("last_extracted_text", "") ?: ""

    fun getLatestClipFile(): File? {
        return getClipboardDir()
            .listFiles { f -> f.name.endsWith(".png") }
            ?.maxByOrNull { it.lastModified() }
    }
}
