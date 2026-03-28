package com.example.smarthome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        CommandSenderHolder.sendCommand(cmd)
    }
}

// 简单的全局指令发送持有器，由 MainActivity 赋值
object CommandSenderHolder {
    @Volatile var sender: ((String) -> Unit)? = null
    fun sendCommand(cmd: String) { sender?.invoke(cmd) }
}