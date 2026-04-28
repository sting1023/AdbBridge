package com.sting.adbbridge.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.sting.adbbridge.R
import com.sting.adbbridge.adb.AdbInstaller
import com.sting.adbbridge.adb.AdbResult
import com.sting.adbbridge.adb.AdbRunner
import com.sting.adbbridge.usb.UsbDeviceManager

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var deviceManager: UsbDeviceManager
    private lateinit var adbRunner: AdbRunner
    
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var tvOutput: TextView
    private lateinit var btnClear: Button
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutOutput: ScrollView
    
    private var connectedDevice: UsbDevice? = null
    private var isAdbInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initAdb()
        checkDeviceConnection()
        registerUsbReceiver()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        tvOutput = findViewById(R.id.tvOutput)
        btnClear = findViewById(R.id.btnClear)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        layoutOutput = findViewById(R.id.layoutOutput)

        btnExecute.setOnClickListener { executeCommand() }
        btnClear.setOnClickListener { 
            tvOutput.text = ""
            tvOutput.visibility = View.GONE
            layoutOutput.visibility = View.GONE
        }
        btnRefresh.setOnClickListener { checkDeviceConnection() }
    }

    private fun initAdb() {
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        deviceManager = UsbDeviceManager(this)
        adbRunner = AdbRunner(this)
        
        // 检查并安装ADB
        Thread {
            isAdbInstalled = AdbInstaller.installIfNeeded(this)
            runOnUiThread {
                updateStatus()
            }
        }.start()
    }

    private fun updateStatus() {
        if (!isAdbInstalled) {
            tvStatus.text = "⚠️ ADB未安装，请将adb文件放入: internal storage/adbbridge/adb"
            tvStatus.setTextColor(0xFFFF9800.toInt())
        } else if (connectedDevice != null) {
            tvStatus.text = "✅ 已连接设备: ${connectedDevice!!.productName}"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnExecute.isEnabled = true
        } else {
            tvStatus.text = "⚡ 等待USB设备连接..."
            tvStatus.setTextColor(0xFF2196F3.toInt())
            btnExecute.isEnabled = false
        }
    }

    private fun checkDeviceConnection() {
        val device = deviceManager.findAndroidDevice()
        if (device != null) {
            connectedDevice = device
            val hasPermission = usbManager.hasPermission(device)
            if (!hasPermission) {
                tvDeviceInfo.text = "🔓 需要授权USB调试\n设备: ${device.productName}\n点击\"授权\"按钮"
                showPermissionDialog(device)
            } else {
                tvDeviceInfo.text = "📱 ${device.productName}\n设备ID: ${device.vendorId}:${device.productId}"
            }
        } else {
            connectedDevice = null
            tvDeviceInfo.text = "未检测到Android设备\n请用USB线连接另一台手机\n并确保USB调试已开启"
        }
        updateStatus()
    }

    private fun showPermissionDialog(device: UsbDevice) {
        val intent = android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, Intent(intent), 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun executeCommand() {
        val cmd = etCommand.text.toString().trim()
        if (cmd.isEmpty()) {
            Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
            return
        }
        if (connectedDevice == null) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }

        layoutOutput.visibility = View.VISIBLE
        tvOutput.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        btnExecute.isEnabled = false
        tvOutput.append("\n$ $cmd\n")

        Thread {
            val result = adbRunner.execute(cmd)
            runOnUiThread {
                progressBar.visibility = View.GONE
                btnExecute.isEnabled = true
                displayResult(result)
            }
        }.start()
    }

    private fun displayResult(result: AdbResult) {
        if (result.isSuccess) {
            if (result.output.isNotEmpty()) {
                tvOutput.append(result.output + "\n")
            } else {
                tvOutput.append("✅ 命令执行成功\n")
            }
        } else {
            tvOutput.append("❌ ${result.error}\n")
        }
        layoutOutput.post {
            layoutOutput.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && device.vendorId == 0x18D1) { // Google
                        checkDeviceConnection()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    connectedDevice = null
                    tvDeviceInfo.text = "设备已断开"
                    updateStatus()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
    }
}
