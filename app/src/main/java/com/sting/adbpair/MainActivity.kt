package com.sting.adbpair

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnExecute: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnExecute = findViewById(R.id.btnExecute)
        
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        
        btnStart.setOnClickListener {
            // 启动服务
            val intent = Intent(this, PairingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "✅ 服务已启动\n\n请打开:\n设置 → 开发者选项 → 无线调试\n点击「使用配对码配对设备」\n\n然后查看通知栏"
            btnExecute.isEnabled = true
        }
        
        btnExecute.setOnClickListener {
            // 执行4条命令
            val intent = Intent(this, PairingService::class.java)
            intent.action = "EXECUTE_COMMANDS"
            startService(intent)
        }
    }
}
