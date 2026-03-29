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

/**
 * AI 智能管家界面 Activity，提供与 DeepSeek 大模型的对话功能。
 *
 * 功能说明：
 *   1. 聊天气泡 UI：用户消息显示在右侧（蓝色），AI 回复显示在左侧（米白色）。
 *   2. 默认问题模板：输入框预填了一段包含传感器数据占位符的问题，
 *      方便用户填入实际数值后直接向 AI 获取家居控制建议。
 *   3. 网络请求：在后台子线程中通过 HTTP POST 调用 DeepSeek API，
 *      成功后将回答更新到等待气泡，失败则显示错误信息。
 *   4. 整体 UI 完全通过代码动态构建，无需 XML 布局文件。
 *
 * API 配置：
 *   - apiKey：DeepSeek 平台的鉴权密钥
 *   - apiUrl：DeepSeek 兼容 OpenAI 格式的 Chat Completions 接口地址
 *   - aiModel：使用的模型标识符
 */
class AiActivity : AppCompatActivity() {

    /** DeepSeek API 鉴权密钥（请替换为实际有效的 Key） */
    private val apiKey = "sk-879ff869cce84916ae76649124e071d2"
    /** DeepSeek Chat Completions 接口地址（兼容 OpenAI 协议） */
    private val apiUrl = "https://api.deepseek.com/chat/completions"
    /** 使用的 AI 模型名称 */
    private val aiModel = "deepseek-chat"

    /** 聊天气泡的容器布局，所有消息气泡都添加到此 LinearLayout */
    private lateinit var chatContainer: LinearLayout
    /** 包裹 chatContainer 的 ScrollView，实现聊天历史滚动 */
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ---- 1. 根布局：垂直线性布局 ---- */
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7F5F0"))  /* 奶油米灰背景，与主页风格统一 */
        }

        /* ---- 2. 顶部标题栏 ---- */
        val title = TextView(this).apply {
            text = "AI 智能管家"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D1B20"))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            setBackgroundColor(Color.parseColor("#FFFCF5"))  /* 标题栏背景色，略区别于聊天区 */
            elevation = dp(4).toFloat()                      /* 轻微阴影，制造层次感 */
        }

        /* ---- 3. 聊天记录区域（可滚动） ---- */
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f    /* 权重1，占据除标题和输入栏之外的全部剩余高度 */
            )
            addView(chatContainer)
        }

        /* ---- 4. 底部输入区：输入框 + 发送按钮 ---- */
        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FFFCF5"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(8).toFloat()     /* 阴影使底部栏视觉上"浮"在聊天内容上方 */
        }

        /* 多行输入框，预填包含传感器数据占位符的问题模板 */
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

        /* 发送按钮 */
        val btnSend = Button(this).apply {
            text = "发送"
            setTextColor(Color.WHITE)
            background = createRoundDrawable(Color.parseColor("#4285F4"), dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(45))
        }

        bottomLayout.addView(etInput)
        bottomLayout.addView(btnSend)

        /* 将各区域组装到根布局 */
        rootLayout.addView(title)
        rootLayout.addView(scrollView)
        rootLayout.addView(bottomLayout)

        setContentView(rootLayout)

        /* 页面初始化完成后，显示 AI 的欢迎消息气泡 */
        addChatBubble("您好！我是您的智能家居环境管家，请告诉我当前的数据或您的需求，我来为您分析。", false)

        /* ---- 发送按钮点击逻辑 ---- */
        btnSend.setOnClickListener {
            val question = etInput.text.toString().trim()
            /* 空内容或 apiKey 仍为占位符时拦截，提示用户 */
            if (question.isEmpty() || apiKey.contains("xxx")) {
                Toast.makeText(this, "请输入问题，或先配置真实的 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            etInput.setText("")                             /* 清空输入框 */
            addChatBubble(question, true)                   /* 添加用户消息气泡（右侧蓝色） */
            val loadingTextView = addChatBubble("AI 正在思考中...", false)   /* 添加等待气泡 */
            btnSend.isEnabled = false                       /* 禁用发送按钮，等待 AI 回复 */

            /* 在子线程中发起网络请求，避免阻塞主线程（UI 线程） */
            thread {
                val answer = callRealAI(question)
                runOnUiThread {
                    loadingTextView.text = answer           /* 将等待气泡的文字替换为 AI 回答 */
                    btnSend.isEnabled = true                /* 恢复发送按钮 */
                    scrollToBottom()                        /* 滚动到底部展示最新消息 */
                }
            }
        }
    }

    /**
     * 向聊天容器添加一个消息气泡。
     *
     * 气泡样式：
     *   - 用户消息（isUser=true）：靠右对齐，蓝色背景，白色文字
     *   - AI 回复（isUser=false）：靠左对齐，米白色背景，深色文字，带轻微阴影
     *
     * @param text   气泡中要显示的文字内容
     * @param isUser true=用户消息，false=AI 消息
     * @return       新建的 TextView 引用（可在之后修改其 text 属性，用于更新等待状态）
     */
    private fun addChatBubble(text: String, isUser: Boolean): TextView {
        /* 外层行容器，控制气泡在水平方向上的对齐方式 */
        val wrapLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)   /* 气泡之间的垂直间距 */
            }
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        /* 消息气泡主体 */
        val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()  /* 最大宽度为屏幕75%，防止气泡过宽 */

            if (isUser) {
                setTextColor(Color.WHITE)
                background = createRoundDrawable(Color.parseColor("#4285F4"), dp(16).toFloat())
            } else {
                setTextColor(Color.parseColor("#212121"))
                background = createRoundDrawable(Color.parseColor("#FFFCF5"), dp(16).toFloat())
                elevation = dp(2).toFloat()     /* AI 气泡轻微阴影 */
            }
        }

        wrapLayout.addView(textView)
        chatContainer.addView(wrapLayout)
        scrollToBottom()    /* 每次添加气泡后自动滚动到底部 */
        return textView
    }

    /**
     * 创建一个圆角矩形的 [GradientDrawable]，用于设置气泡或按钮的背景。
     *
     * @param bgColor 背景填充颜色（ARGB 整数）
     * @param radius  圆角半径（像素）
     * @return        构建完成的 GradientDrawable 实例
     */
    private fun createRoundDrawable(bgColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
        }
    }

    /**
     * 将 dp 单位换算为屏幕像素（px）。
     * 根据当前设备的屏幕密度进行计算，保证不同分辨率下 UI 尺寸一致。
     *
     * @param dpValue 要换算的 dp 值
     * @return        对应的 px 像素值（四舍五入）
     */
    private fun dp(dpValue: Int): Int {
        val scale = resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 将聊天记录 ScrollView 滚动到最底部，使最新消息可见。
     * 使用 post 延迟执行，确保在视图布局完成后再滚动。
     */
    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    /**
     * 调用 DeepSeek Chat Completions API，获取 AI 对用户问题的回答。
     *
     * 请求格式（遵循 OpenAI 兼容协议）：
     * ```json
     * {
     *   "model": "deepseek-chat",
     *   "messages": [{"role": "user", "content": "<用户问题>"}]
     * }
     * ```
     * 响应解析路径：choices[0].message.content
     *
     * 此方法在子线程中调用，会阻塞直到网络请求完成或发生异常。
     *
     * @param question 用户输入的问题字符串
     * @return         AI 回答的文本；若请求失败则返回包含错误信息的字符串
     */
    private fun callRealAI(question: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")    /* Bearer Token 鉴权 */
            connection.doOutput = true      /* 允许向服务器写入请求体 */

            /* 构建请求 JSON 体 */
            val jsonParam = JSONObject()
            jsonParam.put("model", aiModel)

            val messagesArray = JSONArray()
            val messageObj = JSONObject()
            messageObj.put("role", "user")
            messageObj.put("content", question)
            messagesArray.put(messageObj)

            jsonParam.put("messages", messagesArray)

            /* 将 JSON 请求体以 UTF-8 字节流写入连接 */
            val os = connection.outputStream
            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                /* 请求成功：读取响应体并解析 AI 回答 */
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "utf-8"))
                val responseStr = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseStr.append(line)
                }
                reader.close()

                /* 按 OpenAI 协议解析：取 choices[0].message.content */
                val resultJson = JSONObject(responseStr.toString())
                val choicesArray = resultJson.getJSONArray("choices")
                val firstChoice = choicesArray.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                return message.getString("content")
            } else {
                /* 请求失败：读取错误流并返回带状态码的错误信息 */
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
            return "网络连接异常: ${e.message}"     /* 捕获所有异常并以友好文字返回 */
        } finally {
            connection?.disconnect()               /* 无论成功与否，释放 HTTP 连接资源 */
        }
    }
}