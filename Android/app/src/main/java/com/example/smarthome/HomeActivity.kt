package com.example.smarthome

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/**
 * 主页（导航中枢）Activity，完全通过代码动态构建 UI。
 *
 * 本页面以卡片列表的形式展示三大功能模块的入口：
 *   1. 环境监控  → MainActivity（传感器数据、继电器控制）
 *   2. 红外智能遥控 → vivo 智慧生活（外部 App，不存在则提示下载）
 *   3. AI 智能管家 → AiActivity（接入大模型获取居家建议）
 *
 * UI 设计：
 *   - 整体背景为奶油米灰色（#F7F5F0），视觉上柔和不刺眼。
 *   - 每张卡片有圆角、阴影以及不同的主题色区分功能。
 *   - 标题居中、加粗，副标题半透明，整体风格简洁。
 */
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ---- 根布局：可滚动的 ScrollView ---- */
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F7F5F0"))  /* 奶油米灰背景 */
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        /* 垂直方向线性布局，内含标题和各功能卡片 */
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 100, 64, 64)
        }

        /* ---- 页面大标题 ---- */
        val title = TextView(this).apply {
            text = "智能家居中枢"
            textSize = 28f
            setTextColor(Color.parseColor("#1D1B20"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 80)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        /* ---- 卡片1：环境监控模块入口 ---- */
        layout.addView(createMenuCard(
            title = "环境监控",
            subtitle = "实时查看温湿度与光照",
            bgColor = "#EADDFF",    /* 浅紫色，视觉上区分环境监控功能 */
            iconColor = "#4F378B"
        ) {
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
        })

        /* ---- 卡片2：红外遥控模块入口（跳转至 vivo 智慧生活 App） ---- */
        layout.addView(createMenuCard(
            title = "红外智能遥控",
            subtitle = "一键唤起 vivo 智慧生活",
            bgColor = "#C2E7FF",    /* 浅蓝色，区分遥控功能 */
            iconColor = "#004C6D"
        ) {
            val vivoRemotePackage = "com.vivo.vhome"
            val launchIntent = packageManager.getLaunchIntentForPackage(vivoRemotePackage)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
            } else {
                /* 未安装 vivo 智慧生活时给出提示 */
                Toast.makeText(
                    this@HomeActivity,
                    "未检测到 vivo 智慧生活，请确认您的手机型号或先前往应用商店下载",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        /* ---- 卡片3：AI 智能管家模块入口 ---- */
        layout.addView(createMenuCard(
            title = "AI智能环境管家",
            subtitle = "接入大模型获取居家建议",
            bgColor = "#C4EED0",    /* 浅绿色，区分 AI 功能 */
            iconColor = "#0D652D"
        ) {
            startActivity(Intent(this@HomeActivity, AiActivity::class.java))
        })

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    /**
     * 辅助方法：动态创建一张功能入口卡片（MaterialCardView）。
     *
     * 卡片结构：
     *   MaterialCardView（圆角、阴影、背景色）
     *     └── LinearLayout（垂直排列）
     *           ├── TextView（标题，加粗，大字号）
     *           └── TextView（副标题，小字号，半透明）
     *
     * @param title     卡片主标题文字
     * @param subtitle  卡片副标题描述文字
     * @param bgColor   卡片背景颜色（十六进制颜色字符串）
     * @param iconColor 标题和副标题文字颜色（与背景形成对比）
     * @param onClick   用户点击卡片时执行的操作（Lambda）
     * @return          构建完成的 MaterialCardView 实例
     */
    private fun createMenuCard(title: String, subtitle: String, bgColor: String, iconColor: String, onClick: () -> Unit): MaterialCardView {
        /* 外层卡片容器 */
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 48) }    /* 卡片底部间距 */
            radius = 48f                            /* 圆角半径 */
            cardElevation = 8f                      /* 阴影高度，产生立体感 */
            setCardBackgroundColor(Color.parseColor(bgColor))
            isClickable = true
            setOnClickListener { onClick() }
        }

        /* 卡片内容区：垂直布局，内边距使内容不贴边 */
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        /* 主标题文字 */
        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor(iconColor))
            setTypeface(null, Typeface.BOLD)
        }

        /* 副标题文字（小字号、半透明，用于简短描述该模块功能） */
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(Color.parseColor(iconColor))
            alpha = 0.8f                /* 80% 不透明度，视觉上略淡于标题 */
            setPadding(0, 16, 0, 0)
        }

        cardContent.addView(titleView)
        cardContent.addView(subtitleView)
        card.addView(cardContent)

        return card
    }
}