package com.example.smarthome  // <-- 如果你的包名不一样，保留你最顶上的这行

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 注册界面 Activity。
 *
 * 功能：
 *   1. 用户填写账号、密码、确认密码，点击注册按钮完成本地账号注册。
 *   2. 注册信息以键值对形式持久化到 SharedPreferences（key=用户名，value=密码）。
 *   3. 点击"返回登录"按钮结束当前页面，回到登录界面。
 *
 * 校验规则：
 *   - 三个输入框均不能为空。
 *   - 两次输入的密码必须一致。
 *   - 账号不能已存在（避免覆盖已有账号）。
 *
 * 数据存储：
 *   SharedPreferences 文件名为 "UserPrefs"，与 LoginActivity 共享同一存储文件。
 */
class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* 绑定注册界面布局文件 */
        setContentView(R.layout.activity_register)

        /* 获取各输入控件和按钮的引用 */
        val etRegUsername = findViewById<EditText>(R.id.etRegUsername)
        val etRegPassword = findViewById<EditText>(R.id.etRegPassword)
        val etRegConfirmPassword = findViewById<EditText>(R.id.etRegConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnBackLogin = findViewById<Button>(R.id.btnBackLogin)

        /* ---- 注册按钮点击逻辑 ---- */
        btnRegister.setOnClickListener {
            val username = etRegUsername.text.toString().trim()
            val password = etRegPassword.text.toString().trim()
            val confirmPwd = etRegConfirmPassword.text.toString().trim()

            /* 校验1：所有字段不能为空 */
            if (username.isEmpty() || password.isEmpty() || confirmPwd.isEmpty()) {
                Toast.makeText(this, "请填写完整的注册信息！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            /* 校验2：两次输入的密码必须相同 */
            if (password != confirmPwd) {
                Toast.makeText(this, "两次输入的密码不一致！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            /* 获取 SharedPreferences 实例，准备读写账号数据 */
            val sp = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

            /* 校验3：账号不能已存在（防止覆盖他人密码） */
            if (sp.contains(username)) {
                Toast.makeText(this, "该账号已存在，请换一个！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            /* 所有校验通过，将账号密码以键值对形式写入 SharedPreferences */
            sp.edit().putString(username, password).apply()
            Toast.makeText(this, "注册成功，快去登录吧！", Toast.LENGTH_SHORT).show()
            finish()    /* 关闭注册页面，自动回到上一个 Activity（登录页） */
        }

        /* ---- 返回登录按钮：结束注册页面，回到登录界面 ---- */
        btnBackLogin.setOnClickListener {
            finish()
        }
    }
}