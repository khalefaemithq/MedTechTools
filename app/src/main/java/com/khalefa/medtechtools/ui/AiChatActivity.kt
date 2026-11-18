package com.khalefa.medtechtools.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.khalefa.medtechtools.R
import com.khalefa.medtechtools.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiChatActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var chatLog: TextView
    private lateinit var scrollView: ScrollView
    private var attachLatestClip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        UiTheme.applyNightMode(this)
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.bg(this@AiChatActivity))
            val p = UiTheme.dp(this@AiChatActivity, 16)
            setPadding(p, p, p, p)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val p = UiTheme.dp(this, 16)
            v.setPadding(p + sys.left, p + sys.top, p + sys.right, p + sys.bottom)
            insets
        }

        val header = TextView(this).apply {
            text = "${getString(R.string.app_name)} • AI Assistant"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.royalBlue())
            setPadding(0, 0, 0, 12)
        }

        val apiCard = UiTheme.createCard(this, goldBorder = true)
        val urlInput = EditText(this).apply {
            hint = "https://openrouter.ai/api/v1/chat/completions"
            setTextColor(UiTheme.textPrimary(this@AiChatActivity))
            setHintTextColor(UiTheme.textMuted(this@AiChatActivity))
            setText(prefs.aiBaseUrl)
        }
        val keyInput = EditText(this).apply {
            hint = "API Key (sk-...)"
            setTextColor(UiTheme.textPrimary(this@AiChatActivity))
            setHintTextColor(UiTheme.textMuted(this@AiChatActivity))
            setText(prefs.aiApiKey)
        }
        val modelInput = EditText(this).apply {
            hint = "Gemini 4 Hydrogen"
            setTextColor(UiTheme.textPrimary(this@AiChatActivity))
            setHintTextColor(UiTheme.textMuted(this@AiChatActivity))
            setText(prefs.aiModel)
        }
        val btnSaveApi = UiTheme.secondaryButton(this, "Save API Settings", topMarginDp = 6) {
            prefs.aiBaseUrl = urlInput.text.toString()
            prefs.aiApiKey = keyInput.text.toString()
            prefs.aiModel = modelInput.text.toString()
            Toast.makeText(this@AiChatActivity, "API Settings Saved", Toast.LENGTH_SHORT).show()
        }
        apiCard.addView(urlInput)
        apiCard.addView(keyInput)
        apiCard.addView(modelInput)
        apiCard.addView(btnSaveApi)

        scrollView = ScrollView(this)
        chatLog = TextView(this).apply {
            textSize = 15f
            setTextColor(UiTheme.textPrimary(this@AiChatActivity))
            setTextIsSelectable(true)
            setPadding(8, 12, 8, 12)
            text = "MedTech AI Ready.\n"
        }
        scrollView.addView(chatLog)

        val checkClip = CheckBox(this).apply {
            text = "Attach Latest Cropped Screenshot"
            setTextColor(UiTheme.royalBlue())
            setOnCheckedChangeListener { _, checked -> attachLatestClip = checked }
        }

        val promptInput = EditText(this).apply {
            hint = "Ask AI..."
            setTextColor(UiTheme.textPrimary(this@AiChatActivity))
            setHintTextColor(UiTheme.textMuted(this@AiChatActivity))
            setText(prefs.getLastExtractedText())
        }

        val btnSend = UiTheme.primaryButton(this, "Send", topMarginDp = 8) {
            val q = promptInput.text.toString().trim()
            if (q.isEmpty()) return@primaryButton

            if (prefs.aiApiKey.isEmpty() || prefs.aiBaseUrl.isEmpty() || prefs.aiModel.isEmpty()) {
Toast.makeText(this@AiChatActivity, "Enter & Save API Key & Model first", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            chatLog.append("\n🧑 You:\n$q\n")
            promptInput.setText("")
            scrollToBottom()
            sendToAi(q)
        }

        root.addView(header)
        root.addView(apiCard)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(checkClip)
        root.addView(promptInput)
        root.addView(btnSend)

        setContentView(root)
    }

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendToAi(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(prefs.aiBaseUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 45000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${prefs.aiApiKey}")
                    doOutput = true
                }

                val contentArr = JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", prompt))
                    if (attachLatestClip) {
                        prefs.getLatestClipFile()?.let { file ->
                            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put("url", "data:image/png;base64,$b64")
                                    )
                            )
                        }
                    }
                }

                val body = JSONObject().apply {
                    put("model", prefs.aiModel)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", contentArr)
                        )
                    )
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream.bufferedReader().readText()

                if (code !in 200..299) {
                    withContext(Dispatchers.Main) {
                        chatLog.append("\n⚠️ HTTP $code:\n$responseStr\n")
                        scrollToBottom()
                    }
                    return@launch
                }

                val reply = JSONObject(responseStr)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                withContext(Dispatchers.Main) {
                    chatLog.append("\n🤖 AI:\n$reply\n")
                    scrollToBottom()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatLog.append("\n⚠️ Error: ${e.message}\n")
                    scrollToBottom()
                }
            }
        }
    }
}
