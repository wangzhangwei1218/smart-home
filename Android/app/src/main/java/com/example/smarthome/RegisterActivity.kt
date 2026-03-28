package com.example.smarthome  // <-- 如果你的包名不一样，保留你最顶上的这行

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 直接绑定布局文件
        setContentView(R.layout.activity_register)

        // 找控件
        val etRegUsername = findViewById<EditText>(R.id.etRegUsername)
        val etRegPassword = findViewById<EditText>(R.id.etRegPassword)
        val etRegConfirmPassword = findViewById<EditText>(R.id.etRegConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnBackLogin = findViewById<Button>(R.id.btnBackLogin)

        // 注册按钮逻辑
        btnRegister.setOnClickListener {
            val username = etRegUsername.text.toString().trim()
            val password = etRegPassword.text.toString().trim()
            val confirmPwd = etRegConfirmPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || confirmPwd.isEmpty()) {
                Toast.makeText(this, "请填写完整的注册信息！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPwd) {
                Toast.makeText(this, "两次输入的密码不一致！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sp = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            if (sp.contains(username)) {
                Toast.makeText(this, "该账号已存在，请换一个！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sp.edit().putString(username, password).apply()
            Toast.makeText(this, "注册成功，快去登录吧！", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 返回登录逻辑
        btnBackLogin.setOnClickListener {
            finish()
        }
    }
}