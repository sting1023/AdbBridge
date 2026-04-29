package com.sting.adbbridge.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.sting.adbbridge.R
import com.sting.adbbridge.adb.AdbInstaller
import com.sting.adbbridge.adb.AdbResult
import com.sting.adbbridge.adb.AdbRunner
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var adbRunner: AdbRunner
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var tvOutput: TextView
    private lateinit var btnClear: Button
    private lateinit var btnConnect: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutOutput: ScrollView
    private lateinit var layoutPresetCommands: LinearLayout
    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button
    private lateinit var btnStep4: Button
    private lateinit var btnReboot: Button

    private var isConnected = false
    private var isAdbInstalled = false

    // 预设命令列表
    private val presetCommands = arrayOf(
        "cmd appops set com.sting.virtualloc android:mock_location allow",
        "settings put global mock_location_enforced 0",
        "settings put secure mock_location_app com.sting.virtualloc",
        "settings put global development_settings_enabled 0"
    )

    private var currentStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initAdb()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        tvOutput = findViewById(R.id.tvOutput)
        btnClear = findViewById(R.id.btnClear)
        btnConnect = findViewById(R.id.btnRefresh)  // 复用刷新按钮
        progressBar = findViewById(R.id.progressBar)
        layoutOutput = findViewById(R.id.layoutOutput)
        layoutPresetCommands = findViewById(R.id.layoutPresetCommands)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnStep4 = findViewById(R.id.btnStep4)
        btnReboot = findViewById(R.id.btnReboot)

        // 连接按钮
        btnConnect.text = "🔗 连接无线调试"
        btnConnect.setOnClickListener { connectWireless() }

        // 执行命令按钮
        btnExecute.setOnClickListener { executeCustomCommand() }

        // 清除按钮
        btnClear.setOnClickListener {
            tvOutput.text = ""
            tvOutput.visibility = View.GONE
            layoutOutput.visibility = View.GONE
        }

        // 预设命令按钮
        btnStep1.setOnClickListener { executePresetCommand(0) }
        btnStep2.setOnClickListener { executePresetCommand(1) }
        btnStep3.setOnClickListener { executePresetCommand(2) }
        btnStep4.setOnClickListener { executePresetCommand(3) }
        btnReboot.setOnClickListener { rebootDevice() }

        // 初始状态
        updatePresetButtons()
    }

    private fun initAdb() {
        adbRunner = AdbRunner(this)

        Thread {
            isAdbInstalled = AdbInstaller.installIfNeeded(this)
            runOnUiThread {
                updateStatus()
            }
        }.start()
    }

    private fun updateStatus() {
        if (!isAdbInstalled) {
            tvStatus.text = "⚠️ ADB未安装"
            tvStatus.setTextColor(0xFFFF9800.toInt())
            btnConnect.isEnabled = false
            btnExecute.isEnabled = false
        } else if (isConnected) {
            tvStatus.text = "✅ 已连接到 localhost:5555"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnConnect.isEnabled = true
            btnConnect.text = "🔗 已连接 ✓"
            btnExecute.isEnabled = true
            enablePresetButtons(true)
        } else {
            tvStatus.text = "⚡ 等待连接无线调试..."
            tvStatus.setTextColor(0xFF2196F3.toInt())
            btnConnect.isEnabled = true
            btnConnect.text = "🔗 连接无线调试"
            btnExecute.isEnabled = false
            enablePresetButtons(false)
        }
    }

    private fun connectWireless() {
        if (isConnected) {
            // 断开连接
            disconnectWireless()
            return
        }

        tvDeviceInfo.text = "正在连接 localhost:5555..."
        btnConnect.isEnabled = false
        progressBar.visibility = View.VISIBLE

        Thread {
            // 先尝试断开（清理旧连接）
            runCommandSync("disconnect localhost:5555")

            // 连接
            val result = runCommandSync("connect localhost:5555")

            isConnected = result.first

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (isConnected) {
                    tvDeviceInfo.text = "✅ 无线调试已连接\n目标: localhost:5555\n\n请确保手机已开启「开发者选项 → 无线调试」"
                    appendOutput("连接成功: localhost:5555\n")
                } else {
                    tvDeviceInfo.text = "❌ 连接失败\n\n请确保：\n1. 手机已开启「开发者选项」\n2. 已开启「无线调试」\n3. 处于无线调试模式"
                    appendOutput("连接失败: ${result.second}\n")
                }
                updateStatus()
            }
        }.start()
    }

    private fun disconnectWireless() {
        Thread {
            runCommandSync("disconnect localhost:5555")
            isConnected = false
            runOnUiThread {
                tvDeviceInfo.text = "未连接到无线调试\n\n请确保手机已开启：\n开发者选项 → 无线调试"
                updateStatus()
            }
        }.start()
    }

    private fun executeCustomCommand() {
        val cmd = etCommand.text.toString().trim()
        if (cmd.isEmpty()) {
            Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isConnected) {
            Toast.makeText(this, "请先连接无线调试", Toast.LENGTH_SHORT).show()
            return
        }

        executeAdbCommand(cmd)
    }

    private fun executePresetCommand(step: Int) {
        if (!isConnected) {
            Toast.makeText(this, "请先连接无线调试", Toast.LENGTH_SHORT).show()
            return
        }

        val cmd = presetCommands[step]
        executeAdbCommand(cmd, step + 1)
    }

    private fun executeAdbCommand(cmd: String, step: Int? = null) {
        layoutOutput.visibility = View.VISIBLE
        tvOutput.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        btnExecute.isEnabled = false
        btnConnect.isEnabled = false
        disablePresetButtons()

        val displayCmd = if (step != null) "【第${step}步】$cmd" else "\n$ cmd"
        appendOutput("$displayCmd\n")

        Thread {
            val result = adbRunner.shell(cmd)

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnExecute.isEnabled = true
                btnConnect.isEnabled = true

                if (result.isSuccess) {
                    appendOutput("✅ 成功\n")
                    if (step != null && currentStep < step) {
                        currentStep = step
                        updatePresetButtons()
                    }
                } else {
                    appendOutput("❌ 失败: ${result.error}\n")
                }

                updateStatus()
                layoutOutput.post {
                    layoutOutput.fullScroll(View.FOCUS_DOWN)
                }
            }
        }.start()
    }

    private fun rebootDevice() {
        appendOutput("\n正在重启手机...\n")
        btnReboot.isEnabled = false

        Thread {
            val result = adbRunner.shell("reboot")
            runOnUiThread {
                if (!result.isSuccess) {
                    appendOutput("❌ 重启命令失败: ${result.error}\n")
                    btnReboot.isEnabled = true
                } else {
                    appendOutput("✅ 重启命令已发送\n")
                }
            }
        }.start()
    }

    private fun runCommandSync(cmd: String): Pair<Boolean, String> {
        return try {
            val adbPath = File(filesDir, "adb/adb").absolutePath
            val process = Runtime.getRuntime().exec(arrayOf(adbPath, cmd))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Pair(true, output.ifEmpty { "OK" })
            } else {
                Pair(false, error.ifEmpty { "exit: $exitCode" })
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "未知错误")
        }
    }

    private fun appendOutput(text: String) {
        tvOutput.append(text)
    }

    private fun updatePresetButtons() {
        btnStep1.isEnabled = isConnected && currentStep < 1
        btnStep2.isEnabled = isConnected && currentStep < 2
        btnStep3.isEnabled = isConnected && currentStep < 3
        btnStep4.isEnabled = isConnected && currentStep < 4

        btnStep1.text = "第1步: mock_location"
        btnStep2.text = "第2步: mock_location_enforced"
        btnStep3.text = "第3步: mock_location_app"
        btnStep4.text = "第4步: development_settings"

        if (currentStep >= 4) {
            btnReboot.isEnabled = true
            appendOutput("\n🎉 全部命令执行完成！可以重启手机了\n")
        }
    }

    private fun enablePresetButtons(enabled: Boolean) {
        val shouldEnable = enabled && when (currentStep) {
            0 -> true
            1 -> currentStep < 1
            2 -> currentStep < 2
            3 -> currentStep < 3
            else -> currentStep < 4
        }
        btnStep1.isEnabled = shouldEnable && currentStep < 1
        btnStep2.isEnabled = shouldEnable && currentStep < 2
        btnStep3.isEnabled = shouldEnable && currentStep < 3
        btnStep4.isEnabled = shouldEnable && currentStep < 4
        btnReboot.isEnabled = shouldEnable && currentStep >= 4
    }

    private fun disablePresetButtons() {
        btnStep1.isEnabled = false
        btnStep2.isEnabled = false
        btnStep3.isEnabled = false
        btnStep4.isEnabled = false
        btnReboot.isEnabled = false
    }
}
