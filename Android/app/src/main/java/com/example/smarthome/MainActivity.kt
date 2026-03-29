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

/**
 * 主控制界面 Activity，负责以下核心功能：
 *
 * 1. TCP 连接管理：通过 [SerialTcpClient] 与单片机建立/断开 TCP Socket 连接。
 * 2. 传感器数据展示：接收单片机上报的温度、湿度、光照数据并实时更新 UI。
 * 3. 历史统计：保留最近 [maxHistory] 条数据，计算并显示温湿度光照的最大值、最小值、平均值。
 * 4. 继电器手动控制：向单片机发送 OUT:1 / OUT:0 指令开关继电器。
 * 5. 温度阈值设置：发送 SETTH / SETTL 指令设置单片机自动控制的温度上下限。
 * 6. 场景模式：预设"午休"（关灯关窗）和"清晨"（开灯开窗）场景指令组合。
 * 7. 定时任务：使用 AlarmManager 在指定时间自动发送开/关指令。
 * 8. 断线重连提示：连接断开时显示 Snackbar 提示，支持一键重连。
 */
class MainActivity : AppCompatActivity() {

    /** TCP 客户端实例，负责与单片机通信；未连接时为 null */
    private var tcpClient: SerialTcpClient? = null
    /** 当前是否处于已连接状态 */
    private var isConnected = false
    /** 继电器当前状态：true=开，false=关 */
    private var relayState = false
    /** 上一次成功连接的 IP 地址，用于断线后一键重连 */
    private var lastIp: String = ""

    /** 历史传感器数据队列，用于统计最大/最小/平均值 */
    private val history = ArrayDeque<SensorData>()
    /** 历史队列最大容量，超出后移除最旧的一条 */
    private val maxHistory = 20

    /* ---- UI 控件引用 ---- */
    /** 显示当前温度的文本控件 */
    private lateinit var tvTemp: TextView
    /** 显示当前湿度的文本控件 */
    private lateinit var tvHum: TextView
    /** 显示当前光照档位的文本控件 */
    private lateinit var tvLight: TextView
    /** 显示连接状态的文本控件 */
    private lateinit var tvStatus: TextView
    /** 显示历史统计数据的文本控件 */
    private lateinit var tvStats: TextView
    /** IP 地址输入框 */
    private lateinit var etIpAddress: EditText
    /** 连接/断开按钮 */
    private lateinit var btnConnect: Button
    /** 手动切换继电器状态按钮 */
    private lateinit var btnToggleRelay: Button
    /** 温度上限阈值输入框（SETTH 指令） */
    private lateinit var etTH: EditText
    /** 温度下限阈值输入框（SETTL 指令） */
    private lateinit var etTL: EditText
    /** 应用阈值设置按钮 */
    private lateinit var btnApplyThreshold: Button
    /** 午休场景按钮（关灯关窗） */
    private lateinit var btnSceneNap: Button
    /** 清晨场景按钮（开灯开窗） */
    private lateinit var btnSceneMorning: Button
    /** 定时开启按钮（选择时间后定时发送 OUT:1） */
    private lateinit var btnScheduleOn: Button
    /** 定时关闭按钮（选择时间后定时发送 OUT:0） */
    private lateinit var btnScheduleOff: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* ---- 初始化控件引用 ---- */
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

        /* 未连接时禁用所有控制按钮，防止误操作 */
        setControlsEnabled(false)

        /* ---- 连接/断开按钮 ---- */
        btnConnect.setOnClickListener {
            if (!isConnected) {
                val ip = etIpAddress.text.toString().trim()
                if (ip.isEmpty()) {
                    Toast.makeText(this, "Please enter IP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                connectToHost(ip)   /* 发起 TCP 连接 */
            } else {
                tcpClient?.disconnect() /* 主动断开连接 */
            }
        }

        /* ---- 手动切换继电器状态按钮 ---- */
        btnToggleRelay.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            relayState = !relayState    /* 翻转继电器状态 */
            val cmd = if (relayState) "OUT:1\r\n" else "OUT:0\r\n"
            tcpClient?.sendCommand(cmd)
            Toast.makeText(this, "发送: $cmd", Toast.LENGTH_SHORT).show()
            disableButtonShortly(btnToggleRelay)    /* 防止连续点击造成指令堆积 */
        }

        /* ---- 温度阈值设置按钮 ---- */
        btnApplyThreshold.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val th = etTH.text.toString()
            val tl = etTL.text.toString()
            if (th.isNotEmpty()) tcpClient?.sendCommand("SETTH:$th\r\n")   /* 发送温度上限 */
            if (tl.isNotEmpty()) {
                Thread.sleep(100)   /* 两条指令间短暂等待，避免单片机接收缓冲区溢出 */
                tcpClient?.sendCommand("SETTL:$tl\r\n") /* 发送温度下限 */
            }
            Toast.makeText(this, "Settings Applied", Toast.LENGTH_SHORT).show()
            disableButtonShortly(btnApplyThreshold)
        }

        /* ---- 场景模式：午休（关灯关窗，触发低阈值自动关） ---- */
        btnSceneNap.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            tcpClient?.sendCommand("OUT:0\r\n")     /* 关闭继电器（关灯/关窗帘电源） */
            Thread.sleep(80)                        /* 等待单片机处理第一条指令 */
            tcpClient?.sendCommand("SETTL:5\r\n")  /* 设低阈值为5，光照低时自动关窗 */
            Toast.makeText(this, "午休场景已下发", Toast.LENGTH_SHORT).show()
        }

        /* ---- 场景模式：清晨（开灯开窗，触发高阈值自动开） ---- */
        btnSceneMorning.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            tcpClient?.sendCommand("OUT:1\r\n")     /* 打开继电器（开灯/开窗帘电源） */
            Thread.sleep(80)
            tcpClient?.sendCommand("SETTH:80\r\n") /* 设高阈值为80，光照强时自动开窗 */
            Toast.makeText(this, "清晨场景已下发", Toast.LENGTH_SHORT).show()
        }

        /* ---- 定时开启按钮：弹出时间选择器，到时自动发送 OUT:1 ---- */
        btnScheduleOn.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTimePickerAndSchedule(isTurnOn = true, button = btnScheduleOn)
        }

        /* ---- 定时关闭按钮：弹出时间选择器，到时自动发送 OUT:0 ---- */
        btnScheduleOff.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTimePickerAndSchedule(isTurnOn = false, button = btnScheduleOff)
        }

        /* 将 TCP 发送函数注册到全局持有器，供 CommandReceiver（定时任务广播）使用 */
        CommandSenderHolder.sender = { cmd -> tcpClient?.sendCommand(cmd) }
    }

    override fun onDestroy() {
        super.onDestroy()
        /* Activity 销毁时清理全局引用，防止内存泄漏 */
        CommandSenderHolder.sender = null
        tcpClient?.disconnect()
    }

    /**
     * 建立 TCP 连接，并设置数据接收和状态变化的回调。
     * 若已有连接则先断开，再重新连接到指定 IP。
     *
     * @param ip 目标单片机（服务端）的 IP 地址
     */
    private fun connectToHost(ip: String) {
        tcpClient?.disconnect()
        tcpClient = SerialTcpClient(
            host = ip,
            port = 5000,
            onData = { data ->
                /* 在主线程更新 UI（onData 回调来自子线程） */
                runOnUiThread {
                    tvTemp.text = data.temp.toString()
                    tvHum.text = data.hum.toString()
                    tvLight.text = data.light.toString()

                    /* 将新数据加入历史队列，超出上限时移除最旧的一条 */
                    history.addLast(data)
                    if (history.size > maxHistory) history.removeFirst()

                    /* 遍历历史队列，计算温湿度和光照的最大值、最小值、求和 */
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
                    val lAvg = lSum.toFloat() / n   /* 强制转 Float，避免整数除法截断 */

                    /* 格式化并显示统计信息 */
                    tvStats.text = "最近${n}条 | 温:avg=%.1f max=%.1f min=%.1f | 湿:avg=%.1f max=%.1f min=%.1f | 光:avg=%.1f max=%d min=%d"
                        .format(tAvg, tMax, tMin, hAvg, hMax, hMin, lAvg, lMax, lMin)
                }
            },
            onStatus = { statusMsg ->
                /* 在主线程更新连接状态 UI */
                runOnUiThread {
                    val wasConnected = isConnected
                    tvStatus.text = statusMsg
                    if (statusMsg.contains("Connected")) {
                        isConnected = true
                        lastIp = ip
                        btnConnect.text = "取消连接"
                        tvStatus.setTextColor(Color.parseColor("#4CAF50"))  /* 绿色表示已连接 */
                        setControlsEnabled(true)

                        /* 连接成功后重置定时按钮文字，清除上次设置的时间显示 */
                        btnScheduleOn.text = "定时开"
                        btnScheduleOff.text = "定时关"
                    } else {
                        isConnected = false
                        btnConnect.text = "连接"
                        tvStatus.setTextColor(Color.parseColor("#9E9E9E"))  /* 灰色表示离线 */
                        setControlsEnabled(false)
                        if (wasConnected) showReconnectSnack()  /* 若是从已连接变为断线，弹出重连提示 */
                    }
                }
            }
        )
        tcpClient?.start()
    }

    /**
     * 批量设置所有控制相关按钮和输入框的可用状态。
     * 连接时启用，断开时禁用，防止在未连接状态下操作控件。
     *
     * @param enabled true=启用，false=禁用
     */
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

    /**
     * 连接断开时显示一个持续 3 秒的 Snackbar 提示，
     * 包含"重新连接"操作按钮，点击后使用上次的 IP 地址重连。
     */
    private fun showReconnectSnack() {
        if (lastIp.isEmpty()) return
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, "连接断开，点击重新连接", Snackbar.LENGTH_INDEFINITE)
            .setDuration(3000)
            .setAction("重新连接") { connectToHost(lastIp) }
            .show()
    }

    /**
     * 短暂禁用指定按钮，防止在网络延迟期间被重复点击。
     * 默认 800ms 后自动恢复可用状态。
     *
     * @param button 要禁用的按钮控件
     * @param millis 禁用持续时间（毫秒）
     */
    private fun disableButtonShortly(button: View, millis: Long = 800) {
        button.isEnabled = false
        button.postDelayed({ button.isEnabled = true }, millis)
    }

    /**
     * 弹出 24 小时制时间选择对话框，用户选择时间后自动通过 AlarmManager 安排定时任务。
     * 若选择的时间早于当前时间，则自动顺延到明天同一时刻执行。
     *
     * @param isTurnOn true=定时开（发送 OUT:1），false=定时关（发送 OUT:0）
     * @param button   触发本次选择的按钮，选择完成后更新其显示文字
     */
    private fun showTimePickerAndSchedule(isTurnOn: Boolean, button: Button) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hourOfDay, minute ->
            /* 构建目标触发时间的 Calendar 对象 */
            val targetCalendar = Calendar.getInstance()
            targetCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            targetCalendar.set(Calendar.MINUTE, minute)
            targetCalendar.set(Calendar.SECOND, 0)
            targetCalendar.set(Calendar.MILLISECOND, 0)

            /* 若选择的时间已过（比当前时间早），则自动延到明天执行 */
            if (targetCalendar.before(Calendar.getInstance())) {
                targetCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            /* 根据开/关选择确定指令和显示文字 */
            val cmd = if (isTurnOn) "OUT:1\r\n" else "OUT:0\r\n"
            val actionName = if (isTurnOn) "开启" else "关闭"
            val shortName = if (isTurnOn) "开" else "关"

            /* 注册定时任务，到达指定时间触发 CommandReceiver 广播 */
            scheduleCommandAtTime(cmd, targetCalendar.timeInMillis)

            /* 格式化显示时间并提示用户 */
            val timeStr = String.format("%02d:%02d", hourOfDay, minute)
            Toast.makeText(this, "已安排在 $timeStr ${actionName}继电器", Toast.LENGTH_SHORT).show()

            /* 更新按钮文字，如"已定 18:00开"，让用户知道当前定时计划 */
            button.text = "已定 $timeStr$shortName"

        }, currentHour, currentMinute, true).show()     /* true = 24 小时制 */
    }

    /**
     * 使用 AlarmManager 在绝对时间点触发一次性定时任务。
     * 任务通过 PendingIntent 广播触发 [CommandReceiver]，由其读取指令并转发给单片机。
     *
     * 使用 setExactAndAllowWhileIdle 保证在 Doze 模式（低功耗休眠）下也能准时触发。
     *
     * @param cmd          触发时要发送的指令字符串，例如 "OUT:1\r\n"
     * @param timeInMillis 触发时刻的 Unix 时间戳（毫秒）
     */
    private fun scheduleCommandAtTime(cmd: String, timeInMillis: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, CommandReceiver::class.java).apply { putExtra("cmd", cmd) }

        /* 使用 cmd.hashCode() 作为 requestCode，确保"开"和"关"各自独立，互不覆盖 */
        val pi = PendingIntent.getBroadcast(
            this, cmd.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,    /* 使用真实时钟触发，并在需要时唤醒设备 */
            timeInMillis,
            pi
        )
    }
}