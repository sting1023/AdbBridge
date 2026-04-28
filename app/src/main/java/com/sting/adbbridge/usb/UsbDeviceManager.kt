package com.sting.adbbridge.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * USB设备管理器
 */
class UsbDeviceManager(private val context: Context) {

    companion object {
        // Google VID (用于Android设备)
        const val GOOGLE_VID = 0x18D1
        // 常用Android设备VID
        val ANDROID_VIDS = setOf(
            0x18D1,  // Google
            0x04E8,  // Samsung
            0x12D1,  // Huawei
            0x0BB4,  // HTC
            0x05C6,  // Qualcomm
            0x1EBF,  // OnePlus
            0x19D1,  // Sony
            0x2A04,  // LG
            0x0489,  // Foxconn
            0x1F3A,  // Elephone
            0x2B4C,  // Lenovo
            0x17EF,  // Lenovo (另一种)
            0x2207,  // Xiaomi
            0x0408,  // Xiaomi (另一种)
            0x1A86,  // Qin (小厂商)
            0x1D91,  // LGE
            0x24E3,  // Motorola
        )
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /**
     * 查找已连接的Android设备
     */
    fun findAndroidDevice(): UsbDevice? {
        val devices = usbManager.deviceList
        for ((_, device) in devices) {
            if (isAndroidDevice(device)) {
                return device
            }
        }
        return null
    }

    /**
     * 检查是否是Android设备
     */
    fun isAndroidDevice(device: UsbDevice): Boolean {
        val vid = device.vendorId
        // 检查是否是已知的Android设备VID
        if (vid in ANDROID_VIDS) {
            return true
        }
        // Google Android ADB Interface
        if (vid == GOOGLE_VID) {
            return true
        }
        return false
    }

    /**
     * 获取所有USB设备信息
     */
    fun getAllDevices(): List<UsbDeviceInfo> {
        return usbManager.deviceList.map { (_, device) ->
            UsbDeviceInfo(
                name = device.deviceName,
                productName = device.productName ?: "Unknown",
                manufacturer = device.manufacturerName ?: "Unknown",
                vid = device.vendorId,
                pid = device.productId,
                isAndroid = isAndroidDevice(device)
            )
        }
    }

    /**
     * 检查是否有USB设备连接
     */
    fun hasDeviceConnected(): Boolean {
        return usbManager.deviceList.isNotEmpty()
    }

    /**
     * 获取设备授权状态
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * 请求设备授权
     */
    fun requestPermission(device: UsbDevice) {
        val intent = android.app.PendingIntent.getBroadcast(
            context, 0,
            android.content.Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, intent)
    }
}

/**
 * USB设备信息数据类
 */
data class UsbDeviceInfo(
    val name: String,
    val productName: String,
    val manufacturer: String,
    val vid: Int,
    val pid: Int,
    val isAndroid: Boolean
)
