package com.sting.adbbridge.adb

import android.content.Context
import java.io.File

/**
 * ADB命令执行结果
 */
data class AdbResult(
    val isSuccess: Boolean,
    val output: String,
    val error: String = ""
)

/**
 * 执行ADB命令
 */
class AdbRunner(private val context: Context) {

    private val adbPath: String by lazy {
        File(context.filesDir, "adb/adb").absolutePath
    }

    /**
     * 执行单条命令
     * adb -s <device> <command>
     */
    fun execute(command: String, timeoutMs: Long = 30000): AdbResult {
        return try {
            val serial = getFirstDeviceSerial() ?: return AdbResult(false, "", "没有已连接的设备")
            
            // 分割命令参数
            val args = command.trim().split("\\s+".toRegex())
            
            val process = Runtime.getRuntime().exec(arrayOf(adbPath, "-s", serial, *args.toTypedArray()))
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                AdbResult(true, output.trim(), "")
            } else {
                AdbResult(false, output.trim(), error.trim())
            }
        } catch (e: Exception) {
            AdbResult(false, "", e.message ?: "未知错误")
        }
    }

    /**
     * 获取已连接设备的序列号
     */
    fun getFirstDeviceSerial(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(adbPath, "devices"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            // 解析 devices 输出
            // List of devices attached
            // 1234567890    device
            val lines = output.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("List of devices") && !trimmed.startsWith("*")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 2 && parts[1] == "device") {
                        return parts[0]
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查ADB是否可用
     */
    fun isAdbAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(adbPath, "version"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取设备列表
     */
    fun getDevices(): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(adbPath, "devices"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            output.lines()
                .drop(1) // 跳过 "List of devices attached"
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("*") }
                .map { it.split("\\s+".toRegex())[0] }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 安装APK到设备
     */
    fun installApk(apkPath: String): AdbResult {
        return execute("install -r \"$apkPath\"")
    }

    /**
     * 推送文件到设备
     */
    fun push(localPath: String, remotePath: String): AdbResult {
        return execute("push \"$localPath\" \"$remotePath\"")
    }

    /**
     * 从设备拉取文件
     */
    fun pull(remotePath: String, localPath: String): AdbResult {
        return execute("pull \"$remotePath\" \"$localPath\"")
    }

    /**
     * 获取设备shell
     */
    fun shell(command: String): AdbResult {
        return execute("shell \"$command\"")
    }
}
