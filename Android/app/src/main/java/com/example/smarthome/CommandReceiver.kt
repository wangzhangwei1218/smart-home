package com.example.smarthome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 广播接收器，用于接收系统 AlarmManager 在预定时间触发的定时指令。
 *
 * 工作流程：
 *   1. MainActivity 通过 AlarmManager 设置一个定时任务，指定触发时间和指令内容。
 *   2. 到达触发时间时，系统广播该 Intent，CommandReceiver.onReceive 被调用。
 *   3. 从 Intent 中取出指令字符串，交由 CommandSenderHolder 转发给单片机。
 *
 * 需要在 AndroidManifest.xml 中注册此接收器。
 */
class CommandReceiver : BroadcastReceiver() {
    /**
     * 接收到广播时由系统调用。
     * 从 Intent 附加数据中读取指令字符串（key 为 "cmd"），然后通过全局持有器发送。
     *
     * @param context 应用上下文
     * @param intent  携带指令数据的 Intent（extra key: "cmd"）
     */
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return    /* 若没有指令则直接返回 */
        CommandSenderHolder.sendCommand(cmd)
    }
}

/**
 * 全局指令发送持有器（单例对象）。
 *
 * 目的：让 CommandReceiver 能在没有 Activity 引用的情况下，
 *       仍能将指令发送到当前连接的 TCP 客户端。
 *
 * 使用方式：
 *   - MainActivity 连接建立后，将 TCP 发送 Lambda 赋值给 sender。
 *   - MainActivity 销毁时将 sender 置 null，防止内存泄漏。
 *   - CommandReceiver 通过 sendCommand() 间接调用 sender，实现跨组件通信。
 *
 * 注意：sender 使用 @Volatile 修饰，确保在多线程场景下（广播接收在主线程，发送在子线程）
 *       对 sender 的写入对其他线程立即可见。
 */
object CommandSenderHolder {
    /** TCP 指令发送函数，由 MainActivity 在连接成功后注入，断开或销毁时置 null */
    @Volatile var sender: ((String) -> Unit)? = null

    /**
     * 发送指令，若当前 sender 为 null（未连接）则忽略。
     *
     * @param cmd 要发送的指令字符串，例如 "OUT:1\r\n"
     */
    fun sendCommand(cmd: String) { sender?.invoke(cmd) }
}