# AdbBridge - USB OTG ADB控制器

让安卓手机通过USB OTG连接另一台手机，充当电脑来执行ADB命令，无需root。

## 原理

- **手机A（主机）**: 运行AdbBridge app，通过USB OTG连接手机B
- **手机B（目标）**: 只需开启开发者选项 → USB调试

## 使用场景

- 不想root手机，但需要执行需要adb的命令
- 没有电脑，只有两台安卓手机
- 通过USB线调试目标手机

## 技术方案

- USB Host API 检测OTG设备
- 内置adb二进制执行命令
- 实时输出显示

## 依赖

- Android 8.0+ (API 26+)
- USB Host支持
- 需要用户提供adb二进制文件（放入assets/adb/）
