package com.sting.adbbridge.adb

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * ADB安装器 - 从assets目录复制adb二进制文件到应用私有目录
 */
object AdbInstaller {

    private const val ADB_DIR = "adb"
    private const val ADB_BINARY = "adb"
    private const val ASSET_PATH = "adb/adb"

    /**
     * 检查ADB是否已安装，如果没有则从assets复制
     * @return true 如果ADB可用
     */
    fun installIfNeeded(context: Context): Boolean {
        val adbFile = File(context.filesDir, "$ADB_DIR/$ADB_BINARY")
        
        if (adbFile.exists() && adbFile.canExecute()) {
            return true
        }

        // 确保目录存在
        val adbDir = File(context.filesDir, ADB_DIR)
        if (!adbDir.exists()) {
            adbDir.mkdirs()
        }

        return try {
            // 尝试从assets读取
            val assetManager = context.assets
            try {
                val inputStream = assetManager.open(ASSET_PATH)
                copyFile(inputStream, adbFile)
                makeExecutable(adbFile)
                true
            } catch (e: Exception) {
                // 尝试备用路径
                try {
                    val inputStream = assetManager.open(ADB_BINARY)
                    copyFile(inputStream, adbFile)
                    makeExecutable(adbFile)
                    true
                } catch (e2: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 复制文件
     */
    private fun copyFile(input: InputStream, output: File) {
        input.use { inputStream ->
            FileOutputStream(output).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /**
     * 设置可执行权限
     */
    private fun makeExecutable(file: File) {
        try {
            if (file.exists()) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
                file.setWritable(true, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取ADB版本信息
     */
    fun getVersion(context: Context): String? {
        val adbFile = File(context.filesDir, "$ADB_DIR/$ADB_BINARY")
        if (!adbFile.exists()) return null

        return try {
            val process = Runtime.getRuntime().exec(arrayOf(adbFile.absolutePath, "version"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取ADB文件路径
     */
    fun getAdbPath(context: Context): String? {
        val adbFile = File(context.filesDir, "$ADB_DIR/$ADB_BINARY")
        return if (adbFile.exists()) adbFile.absolutePath else null
    }
}
