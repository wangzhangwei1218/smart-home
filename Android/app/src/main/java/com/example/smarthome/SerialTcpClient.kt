package com.example.smarthome

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread


data class SensorData(val hum: Float, val temp: Float, val light: Int)

class SerialTcpClient(
    private val host: String,
    private val port: Int,
    private val onData: (SensorData) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var running = false
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    fun start() {
        if (running) return
        running = true

        thread {
            try {
                onStatus("Connecting to $host:$port...")
                socket = Socket(host, port)
                outputStream = socket!!.getOutputStream()
                onStatus("● Connected")

                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                while (running) {
                    val line = reader.readLine() ?: break
                    parseLine(line.trim())?.let { onData(it) }
                }
            } catch (e: Exception) {
                onStatus("● Error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    // 发送指令给单片机
    fun sendCommand(cmd: String) {
        thread {
            try {
                outputStream?.write(cmd.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        outputStream = null
        onStatus("● Offline")
    }

    // 解析单片机发来的数据：H:15.0,T:25.0,L:6
    private fun parseLine(line: String): SensorData? {
        if (line.isEmpty()) return null
        var h = 0f
        var t = 0f
        var l = 0
        try {
            val parts = line.split(",")
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
            return null
        }
    }
}