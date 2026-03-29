package com.example.smarthome

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var tcpClient: SerialTcpClient? = null
    private var isConnected = false
    private var relayState = false
    private var lastIp: String = ""

    // 历史数据，用于统计
    private val history = ArrayDeque<SensorData>()
    private val maxHistory = 20

    // 控件引用
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvLight: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var etIpAddress: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnToggleRelay: Button
    private lateinit var etTH: EditText
    private lateinit var etTL: EditText
    private lateinit var btnApplyThreshold: Button
    private lateinit var btnSceneNap: Button
    private lateinit var btnSceneMorning: Button

    // 定时按钮
    private lateinit var btnScheduleOn: Button
    private lateinit var btnScheduleOff: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定控件
        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvLight = findViewById(R.id.tvLight)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        etIpAddress = findViewById(R.id.etIpAddress)
        btnConnect = findViewById(R.id.btnConnect)
        btnToggleRelay = findViewById(R.id.btnToggleRelay)
        etTH = findViewById(R.id.etTH)
        etTL = findViewById(R.id.etTL)
        btnApplyThreshold = findViewById(R.id.btnApplyThreshold)
        btnSceneNap = findViewById(R.id.btnSceneNap)
        btnSceneMorning = findViewById(R.id.btnSceneMorning)
        btnScheduleOn = findViewById(R.id.btnScheduleOn)
        btnScheduleOff = findViewById(R.id.btnScheduleOff)

        setControlsEnabled(false)

        // 连接按钮
        btnConnect.setOnClickListener {
            if (!isConnected) {
                val ip = etIpAddress.text.toString().trim()
                if (ip.isEmpty()) {
                    Toast.makeText(this, "Please enter IP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                connectToHost(ip)
            } else {
                tcpClient?.disconnect()
            }
        }

        // 手动切换继电器
        btnToggleRelay.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            relayState = !relayState
            val cmd = if (relayState) "OUT:1\r\n" else "OUT:0\r\n"
            tcpClient?.sendCommand(cmd)
            Toast.makeText(this, "发送: $cmd", Toast.LENGTH_SHORT).show()
            disableButtonShortly(btnToggleRelay)
        }

        // 阈值设置
        btnApplyThreshold.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val th = etTH.text.toString()
            val tl = etTL.text.toString()
            if (th.isNotEmpty()) tcpClient?.sendCommand("SETTH:$th\r\n")
            if (tl.isNotEmpty()) {
                Thread.sleep(100) // 保留原有简易节流
                tcpClient?.sendCommand("SETTL:$tl\r\n")
            }
            Toast.makeText(this, "Settings Applied", Toast.LENGTH_SHORT).show()
            disableButtonShortly(btnApplyThreshold)
        }

        // 场景模式：午休
        btnSceneNap.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            tcpClient?.sendCommand("OUT:0\r\n")
            Thread.sleep(80)
            tcpClient?.sendCommand("SETTL:5\r\n")
            Toast.makeText(this, "午休场景已下发", Toast.LENGTH_SHORT).show()
        }

        // 场景模式：清晨
        btnSceneMorning.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            tcpClient?.sendCommand("OUT:1\r\n")
            Thread.sleep(80)
            tcpClient?.sendCommand("SETTH:80\r\n")
            Toast.makeText(this, "清晨场景已下发", Toast.LENGTH_SHORT).show()
        }

        // 定时任务：弹出时间选择器（定时开）
        btnScheduleOn.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTimePickerAndSchedule(isTurnOn = true, button = btnScheduleOn)
        }

        // 定时任务：弹出时间选择器（定时关）
        btnScheduleOff.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTimePickerAndSchedule(isTurnOn = false, button = btnScheduleOff)
        }

        // 将指令发送函数注册到全局持有器
        CommandSenderHolder.sender = { cmd -> tcpClient?.sendCommand(cmd) }
    }

    override fun onDestroy() {
        super.onDestroy()
        CommandSenderHolder.sender = null
        tcpClient?.disconnect()
    }

    // 建立连接并设置回调
    private fun connectToHost(ip: String) {
        tcpClient?.disconnect()
        tcpClient = SerialTcpClient(
            host = ip,
            port = 5000,
            onData = { data ->
                runOnUiThread {
                    tvTemp.text = data.temp.toString()
                    tvHum.text = data.hum.toString()
                    tvLight.text = data.light.toString()

                    // 统计最近数据
                    history.addLast(data)
                    if (history.size > maxHistory) history.removeFirst()
                    var tMax = Float.MIN_VALUE; var tMin = Float.MAX_VALUE; var tSum = 0f
                    var hMax = Float.MIN_VALUE; var hMin = Float.MAX_VALUE; var hSum = 0f
                    var lMax = Int.MIN_VALUE;   var lMin = Int.MAX_VALUE;   var lSum = 0
                    for (d in history) {
                        tMax = max(tMax, d.temp); tMin = min(tMin, d.temp); tSum += d.temp
                        hMax = max(hMax, d.hum);  hMin = min(hMin, d.hum);  hSum += d.hum
                        lMax = max(lMax, d.light); lMin = min(lMin, d.light); lSum += d.light
                    }
                    val n = history.size.coerceAtLeast(1)
                    val tAvg = tSum / n
                    val hAvg = hSum / n
                    val lAvg = lSum.toFloat() / n // 强制转Float避免整数崩溃

                    tvStats.text = "最近${n}条 | 温:avg=%.1f max=%.1f min=%.1f | 湿:avg=%.1f max=%.1f min=%.1f | 光:avg=%.1f max=%d min=%d"
                        .format(tAvg, tMax, tMin, hAvg, hMax, hMin, lAvg, lMax, lMin)
                }
            },
            onStatus = { statusMsg ->
                runOnUiThread {
                    val wasConnected = isConnected
                    tvStatus.text = statusMsg
                    if (statusMsg.contains("Connected")) {
                        isConnected = true
                        lastIp = ip
                        btnConnect.text = "取消连接"
                        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                        setControlsEnabled(true)

                        // 连接时重置定时按钮文字
                        btnScheduleOn.text = "定时开"
                        btnScheduleOff.text = "定时关"
                    } else {
                        isConnected = false
                        btnConnect.text = "连接"
                        tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                        setControlsEnabled(false)
                        if (wasConnected) showReconnectSnack()
                    }
                }
            }
        )
        tcpClient?.start()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        btnToggleRelay.isEnabled = enabled
        btnApplyThreshold.isEnabled = enabled
        etTH.isEnabled = enabled
        etTL.isEnabled = enabled
        btnSceneNap.isEnabled = enabled
        btnSceneMorning.isEnabled = enabled
        btnScheduleOn.isEnabled = enabled
        btnScheduleOff.isEnabled = enabled
    }

    // 3 秒自动消失的重连提示
    private fun showReconnectSnack() {
        if (lastIp.isEmpty()) return
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, "连接断开，点击重新连接", Snackbar.LENGTH_INDEFINITE)
            .setDuration(3000)
            .setAction("重新连接") { connectToHost(lastIp) }
            .show()
    }

    private fun disableButtonShortly(button: View, millis: Long = 800) {
        button.isEnabled = false
        button.postDelayed({ button.isEnabled = true }, millis)
    }

    // 弹出时间选择器并安排任务
    private fun showTimePickerAndSchedule(isTurnOn: Boolean, button: Button) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hourOfDay, minute ->
            // 计算目标时间
            val targetCalendar = Calendar.getInstance()
            targetCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            targetCalendar.set(Calendar.MINUTE, minute)
            targetCalendar.set(Calendar.SECOND, 0)
            targetCalendar.set(Calendar.MILLISECOND, 0)

            // 如果选择的时间比现在还早，说明是定到了明天
            if (targetCalendar.before(Calendar.getInstance())) {
                targetCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            // 根据是开启还是关闭设定指令
            val cmd = if (isTurnOn) "OUT:1\r\n" else "OUT:0\r\n"
            val actionName = if (isTurnOn) "开启" else "关闭"
            val shortName = if (isTurnOn) "开" else "关"

            // 安排任务
            scheduleCommandAtTime(cmd, targetCalendar.timeInMillis)

            // 格式化时间并提示
            val timeStr = String.format("%02d:%02d", hourOfDay, minute)
            Toast.makeText(this, "已安排在 $timeStr ${actionName}继电器", Toast.LENGTH_SHORT).show()

            // 更新按钮文字，例如变为 "已定 18:00开"
            button.text = "已定 $timeStr$shortName"

        }, currentHour, currentMinute, true).show() // true 表示 24 小时制
    }

    // 绝对时间定时（根据指定的毫秒时间戳来触发）
    private fun scheduleCommandAtTime(cmd: String, timeInMillis: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, CommandReceiver::class.java).apply { putExtra("cmd", cmd) }

        // 使用 cmd.hashCode() 作为 requestCode 保证开/关定时互不冲突
        val pi = PendingIntent.getBroadcast(
            this, cmd.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            pi
        )
    }
}