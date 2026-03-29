package com.example.smarthome // <-- 保留你的包名

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 登录界面 Activity。
 *
 * 功能：
 *   1. 账号自动补全：从 SharedPreferences 读取本地已注册的账号列表，
 *      在用户点击或聚焦账号输入框时弹出下拉候选列表。
 *   2. 密码自动填充：用户从下拉列表选中账号后，自动填入对应的已保存密码。
 *   3. 登录验证：将用户输入的账号和密码与 SharedPreferences 中存储的记录比对，
 *      验证通过后跳转到 HomeActivity。
 *   4. 跳转注册：点击"去注册"按钮跳转到 RegisterActivity。
 *
 * 数据存储：
 *   使用 SharedPreferences（文件名 "UserPrefs"）以键值对形式存储账号密码：
 *   key=用户名，value=密码。
 */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        /* 注意：账号输入框使用 AutoCompleteTextView，支持下拉自动补全 */
        val etUsername = findViewById<AutoCompleteTextView>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoRegister = findViewById<Button>(R.id.btnGoRegister)

        /* 获取本应用专属的 SharedPreferences 实例（私有模式，其他应用不可读） */
        val sp = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        /* ---- 账号记忆与自动填充逻辑 ---- */

        /* 从 SharedPreferences 获取所有已保存的账号名（即所有键名） */
        val savedUsernames = sp.all.keys.toTypedArray()

        /* 创建下拉列表适配器，使用系统默认单行下拉样式 */
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, savedUsernames)
        etUsername.setAdapter(adapter)

        /* 用户点击账号输入框时，若有已保存账号则立即弹出下拉候选列表 */
        etUsername.setOnClickListener {
            if (savedUsernames.isNotEmpty()) etUsername.showDropDown()
        }
        /* 用户通过 Tab / 触摸聚焦到账号输入框时，也弹出下拉候选列表 */
        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && savedUsernames.isNotEmpty()) etUsername.showDropDown()
        }

        /* 用户从下拉列表点选某个账号后，自动将对应的已保存密码填入密码框 */
        etUsername.setOnItemClickListener { parent, _, position, _ ->
            val selectedUsername = parent.getItemAtPosition(position) as String
            val savedPassword = sp.getString(selectedUsername, "")
            etPassword.setText(savedPassword)   /* 自动填入密码，方便用户一键登录 */
        }

        /* ---- 登录按钮点击逻辑 ---- */
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            /* 基本校验：账号和密码不能为空 */
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "账号或密码不能为空！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            /* 从 SharedPreferences 中查询该账号保存的密码 */
            val savedPassword = sp.getString(username, null)

            if (savedPassword == null) {
                /* 账号不存在 */
                Toast.makeText(this, "账号不存在，请先注册！", Toast.LENGTH_SHORT).show()
            } else if (savedPassword == password) {
                /* 密码匹配，登录成功，跳转到主页 */
                Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()    /* 结束登录界面，防止返回键回到登录页 */
            } else {
                /* 密码错误 */
                Toast.makeText(this, "密码错误，请重试！", Toast.LENGTH_SHORT).show()
            }
        }

        /* ---- 跳转注册页按钮 ---- */
        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}