package com.khalefa.medtechtools.prefs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class PrefsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("medtech_tools_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACT_NONE = "none"
        const val ACT_CROP_COPY = "crop_copy"
        const val ACT_SHOW_MENU = "show_menu"
        const val ACT_CROP_OCR = "crop_ocr"
        const val ACT_PIN_IMAGE = "pin_image"
        const val ACT_CLIPBOARD = "clipboard"
        const val ACT_GOOGLE_SEARCH = "google_search"
        const val ACT_GOOGLE_TRANSLATE = "google_translate"
        const val ACT_AI_CHAT = "ai_chat"
        const val ACT_EXIT = "exit"

        val ALL_ACTIONS = listOf(
            ACT_NONE,
            ACT_CROP_COPY,
            ACT_SHOW_MENU,
            ACT_CROP_OCR,
            ACT_PIN_IMAGE,
            ACT_CLIPBOARD,
            ACT_GOOGLE_SEARCH,
            ACT_GOOGLE_TRANSLATE,
            ACT_AI_CHAT,
            ACT_EXIT
        )

        val DEFAULT_MENU_ORDER = listOf(
            ACT_CROP_COPY,
            ACT_CROP_OCR,
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

    var language: String
        get() = prefs.getString("app_lang", "system") ?: "system"
        set(value) = prefs.edit().putString("app_lang", value).apply()

    var appTheme: String
        get() = prefs.getString("app_theme_v14", "light") ?: "light"
        set(value) = prefs.edit().putString("app_theme_v14", value).apply()

    var bubbleSizeDp: Int
        get() = prefs.getInt("bubble_size", 56)
        set(value) = prefs.edit().putInt("bubble_size", value.coerceIn(36, 96)).apply()

    var bubbleTransparency: Int
        get() = prefs.getInt("bubble_transparency", 15)
        set(value) = prefs.edit().putInt("bubble_transparency", value.coerceIn(0, 80)).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubble_x", 0)
        set(value) = prefs.edit().putInt("bubble_x", value).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", 320)
        set(value) = prefs.edit().putInt("bubble_y", value).apply()

    var saveToGallery: Boolean
        get() = prefs.getBoolean("save_to_gallery", false)
        set(value) = prefs.edit().putBoolean("save_to_gallery", value).apply()

    var singleTapAction: String
        get() = prefs.getString("act_single_v13", ACT_CROP_COPY) ?: ACT_CROP_COPY
        set(value) = prefs.edit().putString("act_single_v13", value).apply()

    var doubleTapAction: String
        get() = prefs.getString("act_double_v13", ACT_CROP_OCR) ?: ACT_CROP_OCR
        set(value) = prefs.edit().putString("act_double_v13", value).apply()

    var longPressAction: String
        get() = prefs.getString("act_long_v13", ACT_SHOW_MENU) ?: ACT_SHOW_MENU
        set(value) = prefs.edit().putString("act_long_v13", value).apply()

    var swipeGesturesEnabled: Boolean
        get() = prefs.getBoolean("swipe_gestures_enabled_v13", true)
        set(value) = prefs.edit().putBoolean("swipe_gestures_enabled_v13", value).apply()

    var gestureHalfUpAction: String
        get() = prefs.getString("act_g_half_up", ACT_NONE) ?: ACT_NONE
        set(value) = prefs.edit().putString("act_g_half_up", value).apply()

    var gestureHalfDownAction: String
        get() = prefs.getString("act_g_half_down", ACT_NONE) ?: ACT_NONE
        set(value) = prefs.edit().putString("act_g_half_down", value).apply()

    var gestureLUpAction: String
        get() = prefs.getString("act_g_l_up", ACT_GOOGLE_TRANSLATE) ?: ACT_GOOGLE_TRANSLATE
        set(value) = prefs.edit().putString("act_g_l_up", value).apply()

    var gestureLDownAction: String
        get() = prefs.getString("act_g_l_down", ACT_GOOGLE_SEARCH) ?: ACT_GOOGLE_SEARCH
        set(value) = prefs.edit().putString("act_g_l_down", value).apply()

    var gestureTriUpAction: String
        get() = prefs.getString("act_g_tri_up", ACT_PIN_IMAGE) ?: ACT_PIN_IMAGE
        set(value) = prefs.edit().putString("act_g_tri_up", value).apply()

    var gestureTriDownAction: String
        get() = prefs.getString("act_g_tri_down", ACT_CROP_OCR) ?: ACT_CROP_OCR
        set(value) = prefs.edit().putString("act_g_tri_down", value).apply()

    var gestureCircleCwAction: String
        get() = prefs.getString("act_g_circle_cw_v13", ACT_NONE) ?: ACT_NONE
        set(value) = prefs.edit().putString("act_g_circle_cw_v13", value).apply()

    var gestureCircleCcwAction: String
        get() = prefs.getString("act_g_circle_ccw_v13", ACT_NONE) ?: ACT_NONE
        set(value) = prefs.edit().putString("act_g_circle_ccw_v13", value).apply()

    var menuOrder: List<String>
        get() {
            val saved = prefs.getString("menu_order_v13", null) ?: return DEFAULT_MENU_ORDER
            val parsed = saved.split(",").filter { it in DEFAULT_MENU_ORDER }
            val missing = DEFAULT_MENU_ORDER.filter { it !in parsed }
            return parsed + missing
        }
        set(value) = prefs.edit().putString("menu_order_v13", value.joinToString(",")).apply()

    fun isToolEnabled(actionKey: String): Boolean {

        if (actionKey == ACT_NONE || actionKey == ACT_SHOW_MENU) return false
        return prefs.getBoolean("tool_enabled_$actionKey", true)
    }

    fun setToolEnabled(actionKey: String, enabled: Boolean) {
        prefs.edit().putBoolean("tool_enabled_$actionKey", enabled).apply()
    }

    fun resetGesturesAndMenuToDefaults() {
        swipeGesturesEnabled = true
        singleTapAction = ACT_CROP_COPY
        doubleTapAction = ACT_CROP_OCR
        longPressAction = ACT_SHOW_MENU
        gestureHalfUpAction = ACT_NONE
        gestureHalfDownAction = ACT_NONE
        gestureLUpAction = ACT_GOOGLE_TRANSLATE
        gestureLDownAction = ACT_GOOGLE_SEARCH
        gestureTriUpAction = ACT_PIN_IMAGE
        gestureTriDownAction = ACT_CROP_OCR
        gestureCircleCwAction = ACT_NONE
        gestureCircleCcwAction = ACT_NONE
        menuOrder = DEFAULT_MENU_ORDER
        for (key in DEFAULT_MENU_ORDER) {
            setToolEnabled(key, true)
        }
    }

    var aiBaseUrl: String
        get() = prefs.getString("ai_base_url_v13", "") ?: ""
        set(value) = prefs.edit().putString("ai_base_url_v13", value.trim()).apply()

    var aiApiKey: String
        get() = prefs.getString("ai_api_key", "") ?: ""
        set(value) = prefs.edit().putString("ai_api_key", value.trim()).apply()

    var aiModel: String
        get() = prefs.getString("ai_model_v13", "") ?: ""
        set(value) = prefs.edit().putString("ai_model_v13", value.trim()).apply()

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

    fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        return try {
            val filename = "MedTech_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MedTechTools")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val picDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MedTechTools")
                if (!picDir.exists()) picDir.mkdirs()
                val outFile = File(picDir, filename)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
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
