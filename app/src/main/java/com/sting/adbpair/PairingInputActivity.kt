package com.sting.adbpair

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class PairingInputActivity : AppCompatActivity() {
    
    private lateinit var etPairingCode: EditText
    private lateinit var btnPair: Button
    private var port: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_input)
        
        port = intent.getIntExtra("port", 0)
        
        etPairingCode = findViewById(R.id.etPairingCode)
        btnPair = findViewById(R.id.btnPair)
        
        btnPair.setOnClickListener {
            val code = etPairingCode.text.toString().trim()
            if (code.isEmpty()) {
                return@setOnClickListener
            }
            
            // 执行配对
            Thread {
                val result = executePairing(code)
                runOnUiThread {
                    if (result) {
                        // 关闭通知
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.cancel(2)
                        
                        // 启动服务执行连接
                        val intent = Intent(this, PairingService::class.java)
                        intent.action = "CONNECT"
                        startService(intent)
                        
                        finish()
                    }
                }
            }.start()
        }
    }
    
    private fun executePairing(code: String): Boolean {
        return try {
            val adbPath = File(filesDir, "adb/adb").absolutePath
            val process = Runtime.getRuntime().exec(
                arrayOf(adbPath, "pair", "127.0.0.1:$port", code)
            )
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
