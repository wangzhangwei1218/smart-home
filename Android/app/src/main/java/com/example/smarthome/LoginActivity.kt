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

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 注意：这里的 etUsername 类型换成了 AutoCompleteTextView
        val etUsername = findViewById<AutoCompleteTextView>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoRegister = findViewById<Button>(R.id.btnGoRegister)

        // 获取 SharedPreferences
        val sp = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // ================= 新增：账号记忆与自动填充逻辑 =================
        // 获取本地所有保存的账号 (键值对的 key)
        val savedUsernames = sp.all.keys.toTypedArray()

        // 创建下拉列表的适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, savedUsernames)
        etUsername.setAdapter(adapter)

        // 核心：点击输入框 或 获得焦点时，直接弹出下拉列表
        etUsername.setOnClickListener {
            if (savedUsernames.isNotEmpty()) etUsername.showDropDown()
        }
        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && savedUsernames.isNotEmpty()) etUsername.showDropDown()
        }

        // 核心：点击下拉列表中的某个账号后，自动填充对应的密码
        etUsername.setOnItemClickListener { parent, _, position, _ ->
            val selectedUsername = parent.getItemAtPosition(position) as String
            val savedPassword = sp.getString(selectedUsername, "")
            etPassword.setText(savedPassword) // 自动填入密码
        }
        // ==============================================================

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "账号或密码不能为空！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val savedPassword = sp.getString(username, null)

            if (savedPassword == null) {
                Toast.makeText(this, "账号不存在，请先注册！", Toast.LENGTH_SHORT).show()
            } else if (savedPassword == password) {
                Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "密码错误，请重试！", Toast.LENGTH_SHORT).show()
            }
        }

        // 跳转到注册页
        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}