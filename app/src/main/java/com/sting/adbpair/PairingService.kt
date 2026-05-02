package com.sting.adbpair

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class PairingService : Service() {
    
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var pairingPort: Int = 0
    private var adbPort: Int = 0
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "adb_pair",
                "ADB配对",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        
        startForeground(1, createNotification("正在搜索配对服务..."))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "EXECUTE_COMMANDS" -> executeCommands()
            else -> startDiscovery()
        }
        return START_STICKY
    }
    
    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                updateNotification("搜索失败")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            
            override fun onDiscoveryStarted(serviceType: String?) {
                updateNotification("正在搜索配对服务...")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {}
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    if (it.serviceName.contains("adb-tls-pairing")) {
                        nsdManager.resolveService(it, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {}
                            
                            override fun onServiceResolved(info: NsdServiceInfo?) {
                                info?.let { service ->
                                    pairingPort = service.port
                                    showPairingCodeInput()
                                }
                            }
                        })
                    } else if (it.serviceName.contains("adb-tls-connect")) {
                        nsdManager.resolveService(it, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {}
                            
                            override fun onServiceResolved(info: NsdServiceInfo?) {
                                info?.let { service ->
                                    adbPort = service.port
                                }
                            }
                        })
                    }
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
        }
        
        nsdManager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
    
    private fun showPairingCodeInput() {
        // 创建输入对话框的PendingIntent
        val intent = Intent(this, PairingInputActivity::class.java)
        intent.putExtra("port", pairingPort)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "adb_pair")
            .setContentTitle("检测到配对服务")
            .setContentText("点击输入配对码 (端口: $pairingPort)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(2, notification)
    }
    
    private fun executeCommands() {
        if (adbPort == 0) {
            updateNotification("错误：未连接ADB")
            return
        }
        
        val commands = arrayOf(
            "shell cmd appops set com.sting.virtualloc android:mock_location allow",
            "shell settings put global development_enabled 0",
            "shell settings put secure mock_location_app com.sting.virtualloc",
            "shell settings put global development_settings_enabled 0"
        )
        
        val adbPath = File(filesDir, "adb/adb").absolutePath
        val results = StringBuilder()
        
        for (cmd in commands) {
            val cmdParts = cmd.split(" ").filter { it.isNotEmpty() }
            val fullCmd = mutableListOf(adbPath, "-s", "127.0.0.1:$adbPort")
            fullCmd.addAll(cmdParts)
            
            val process = Runtime.getRuntime().exec(fullCmd.toTypedArray())
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            
            results.append("✓ $cmd\n")
        }
        
        updateNotification("✅ 全部命令执行完成\n\n请重启手机使设置生效")
    }
    
    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "adb_pair")
            .setContentTitle("ADB配对助手")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(text))
    }
    
    override fun onDestroy() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        super.onDestroy()
    }
}
