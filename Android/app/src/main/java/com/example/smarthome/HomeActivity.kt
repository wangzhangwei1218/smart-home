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

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 背景 ScrollView (替换为奶油米灰)
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F7F5F0"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 100, 64, 64)
        }

        // 大标题
        val title = TextView(this).apply {
            text = "智能家居中枢"
            textSize = 28f
            setTextColor(Color.parseColor("#1D1B20"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 80)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        // --- 卡片1：环境监控 ---
        layout.addView(createMenuCard(
            title = "环境监控",
            subtitle = "实时查看温湿度与光照",
            bgColor = "#EADDFF", // 保持浅紫功能区分
            iconColor = "#4F378B"
        ) {
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
        })

        // --- 卡片2：红外智能遥控 ---
        layout.addView(createMenuCard(
            title = "红外智能遥控",
            subtitle = "一键唤起 vivo 智慧生活",
            bgColor = "#C2E7FF", // 保持浅蓝功能区分
            iconColor = "#004C6D"
        ) {
            val vivoRemotePackage = "com.vivo.vhome"
            val launchIntent = packageManager.getLaunchIntentForPackage(vivoRemotePackage)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
            } else {
                Toast.makeText(
                    this@HomeActivity,
                    "未检测到 vivo 智慧生活，请确认您的手机型号或先前往应用商店下载",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        // --- 卡片3：AI智能管家 ---
        layout.addView(createMenuCard(
            title = "AI智能环境管家",
            subtitle = "接入大模型获取居家建议",
            bgColor = "#C4EED0", // 保持浅绿功能区分
            iconColor = "#0D652D"
        ) {
            startActivity(Intent(this@HomeActivity, AiActivity::class.java))
        })

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    // 辅助方法：生成精美卡片
    private fun createMenuCard(title: String, subtitle: String, bgColor: String, iconColor: String, onClick: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 48) }
            radius = 48f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor(bgColor))
            isClickable = true
            setOnClickListener { onClick() }
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor(iconColor))
            setTypeface(null, Typeface.BOLD)
        }

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(Color.parseColor(iconColor))
            alpha = 0.8f
            setPadding(0, 16, 0, 0)
        }

        cardContent.addView(titleView)
        cardContent.addView(subtitleView)
        card.addView(cardContent)

        return card
    }
}