# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/android-sdk/tools/proguard/proguard-android.txt

# Keep ADB binary
-keep class com.sting.adbbridge.** { *; }
