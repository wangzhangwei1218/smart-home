package com.example.smarthome

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AiActivity : AppCompatActivity() {

    private val apiKey = "sk-879ff869cce84916ae76649124e071d2"
    private val apiUrl = "https://api.deepseek.com/chat/completions"
    private val aiModel = "deepseek-chat"

    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 根布局
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7F5F0"))
        }

        // 2. 顶部标题
        val title = TextView(this).apply {
            text = "AI 智能管家"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D1B20"))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            setBackgroundColor(Color.parseColor("#FFFCF5"))
            elevation = dp(4).toFloat()
        }

        // 3. 聊天记录滚动区
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            addView(chatContainer)
        }

        // 4. 底部输入区
        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FFFCF5"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(8).toFloat()
        }

        // 输入框
        val etInput = EditText(this).apply {
            hint = "向 AI 提问..."
            setText("当前单片机传回：温度 ℃，湿度 %，光照 Lv。请帮我分析环境舒适度，并给出家电控制建议（限100字）。")
            textSize = 15f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = createRoundDrawable(Color.parseColor("#EFECE5"), dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }

        val btnSend = Button(this).apply {
            text = "发送"
            setTextColor(Color.WHITE)
            background = createRoundDrawable(Color.parseColor("#4285F4"), dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(45))
        }

        bottomLayout.addView(etInput)
        bottomLayout.addView(btnSend)

        rootLayout.addView(title)
        rootLayout.addView(scrollView)
        rootLayout.addView(bottomLayout)

        setContentView(rootLayout)

        addChatBubble("您好！我是您的智能家居环境管家，请告诉我当前的数据或您的需求，我来为您分析。", false)

        btnSend.setOnClickListener {
            val question = etInput.text.toString().trim()
            if (question.isEmpty() || apiKey.contains("xxx")) {
                Toast.makeText(this, "请输入问题，或先配置真实的 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            etInput.setText("")
            addChatBubble(question, true)
            val loadingTextView = addChatBubble("AI 正在思考中...", false)
            btnSend.isEnabled = false

            thread {
                val answer = callRealAI(question)
                runOnUiThread {
                    loadingTextView.text = answer
                    btnSend.isEnabled = true
                    scrollToBottom()
                }
            }
        }
    }

    private fun addChatBubble(text: String, isUser: Boolean): TextView {
        val wrapLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()

            if (isUser) {
                setTextColor(Color.WHITE)
                background = createRoundDrawable(Color.parseColor("#4285F4"), dp(16).toFloat())
            } else {
                setTextColor(Color.parseColor("#212121"))
                // AI 气泡颜色
                background = createRoundDrawable(Color.parseColor("#FFFCF5"), dp(16).toFloat())
                elevation = dp(2).toFloat()
            }
        }

        wrapLayout.addView(textView)
        chatContainer.addView(wrapLayout)
        scrollToBottom()
        return textView
    }

    private fun createRoundDrawable(bgColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
        }
    }

    private fun dp(dpValue: Int): Int {
        val scale = resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun callRealAI(question: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("model", aiModel)

            val messagesArray = JSONArray()
            val messageObj = JSONObject()
            messageObj.put("role", "user")
            messageObj.put("content", question)
            messagesArray.put(messageObj)

            jsonParam.put("messages", messagesArray)

            val os = connection.outputStream
            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "utf-8"))
                val responseStr = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseStr.append(line)
                }
                reader.close()

                val resultJson = JSONObject(responseStr.toString())
                val choicesArray = resultJson.getJSONArray("choices")
                val firstChoice = choicesArray.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                return message.getString("content")
            } else {
                val errorStream = connection.errorStream
                if(errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream, "utf-8"))
                    val errorStr = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorStr.append(line)
                    }
                    return "AI 请求失败，状态码: $responseCode\n错误信息: ${errorStr.toString()}"
                }
                return "AI 请求失败，状态码: $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "网络连接异常: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }
}