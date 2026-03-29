package com.example.smarthome

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 传感器数据数据类，封装单片机上报的一帧传感器读数。
 *
 * @param hum   湿度（单位：%RH，如 25.0 表示 25.0%RH）
 * @param temp  温度（单位：℃，如 26.0 表示 26.0℃）
 * @param light 光照档位（整数，0~15 档，数值越大光照越强）
 */
data class SensorData(val hum: Float, val temp: Float, val light: Int)

/**
 * TCP 串口客户端，负责与单片机（51 MCU）通过 TCP Socket 进行双向通信。
 *
 * 通信协议：
 *   接收方向（MCU → APP）：每行一帧，格式为 "T:26.0,H:55.0,L:8\r\n"
 *   发送方向（APP → MCU）：文本指令，如 "OUT:1\r\n"、"SETTH:28\r\n"
 *
 * @param host     单片机端的服务器 IP 地址（或通过 WiFi 模块暴露的地址）
 * @param port     服务器监听端口（默认 5000）
 * @param onData   接收到合法传感器数据时的回调，运行在子线程，界面更新需 runOnUiThread
 * @param onStatus 连接状态变化时的回调（连接中/已连接/错误/离线），同样在子线程触发
 */
class SerialTcpClient(
    private val host: String,
    private val port: Int,
    private val onData: (SensorData) -> Unit,
    private val onStatus: (String) -> Unit
) {
    /** 标记当前连接循环是否继续运行 */
    private var running = false
    /** TCP Socket 实例，连接建立后赋值，断开后置 null */
    private var socket: Socket? = null
    /** 输出流，用于向单片机发送指令 */
    private var outputStream: OutputStream? = null

    /**
     * 启动 TCP 连接，在后台子线程中建立 Socket 并持续监听数据。
     * 若已处于运行状态则直接返回，避免重复连接。
     */
    fun start() {
        if (running) return
        running = true

        thread {
            try {
                onStatus("Connecting to $host:$port...")
                socket = Socket(host, port)             /* 建立 TCP 连接 */
                outputStream = socket!!.getOutputStream()
                onStatus("● Connected")

                /* 以行为单位读取单片机上报的数据，readLine 阻塞直到收到换行或连接断开 */
                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                while (running) {
                    val line = reader.readLine() ?: break   /* 返回 null 表示连接已关闭 */
                    parseLine(line.trim())?.let { onData(it) }
                }
            } catch (e: Exception) {
                onStatus("● Error: ${e.message}")
            } finally {
                disconnect()    /* 无论正常结束还是异常，都执行断开清理 */
            }
        }
    }

    /**
     * 在后台子线程中向单片机发送一条文本指令。
     * 使用独立线程避免在主线程（UI 线程）中进行网络 I/O 操作。
     *
     * @param cmd 要发送的指令字符串，例如 "OUT:1\r\n"
     */
    fun sendCommand(cmd: String) {
        thread {
            try {
                outputStream?.write(cmd.toByteArray())  /* 将指令以字节流形式写入 Socket */
                outputStream?.flush()                   /* 强制刷新缓冲区，确保数据立即发出 */
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 断开 TCP 连接，释放 Socket 资源，并通知调用方状态变为离线。
     * 可被主动调用（用户点击断开）或在发生异常时由 start() 内部调用。
     */
    fun disconnect() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        outputStream = null
        onStatus("● Offline")
    }

    /**
     * 解析单片机上报的一行数据字符串，提取温度、湿度、光照值。
     *
     * 数据格式示例："T:26.0,H:55.0,L:8"
     *   - T: 温度（℃）
     *   - H: 湿度（%RH）
     *   - L: 光照档位（整数）
     *
     * @param line 去除首尾空白后的一行文本
     * @return 解析成功返回 [SensorData]；行为空或解析失败返回 null
     */
    private fun parseLine(line: String): SensorData? {
        if (line.isEmpty()) return null
        var h = 0f
        var t = 0f
        var l = 0
        try {
            val parts = line.split(",")         /* 按逗号拆分为多个键值对 */
            for (p in parts) {
                val item = p.trim()
                when {
                    item.startsWith("H:") -> h = item.substringAfter("H:").toFloatOrNull() ?: h
                    item.startsWith("T:") -> t = item.substringAfter("T:").toFloatOrNull() ?: t
                    item.startsWith("L:") -> l = item.substringAfter("L:").toIntOrNull() ?: l
                }
            }
            return SensorData(h, t, l)
        } catch (e: Exception) {
            return null                         /* 解析失败（格式不符）时丢弃该帧 */
        }
    }
}