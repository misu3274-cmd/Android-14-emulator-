package com.example.data.firebase

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.model.DeviceTelemetrySnapshot

class DeviceInfoProvider(context: Context) {
    private val appContext = context.applicationContext

    fun captureSnapshot(): DeviceTelemetrySnapshot {
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val metrics = appContext.resources.displayMetrics
        val screenSummary = "${metrics.widthPixels}x${metrics.heightPixels} px (${metrics.densityDpi} dpi)"

        val model = Build.MODEL.ifBlank { "Android Device" }
        val product = Build.PRODUCT.ifBlank { "unknown" }
        val hardware = Build.HARDWARE.ifBlank { "unknown" }
        val fingerprint = Build.FINGERPRINT.ifBlank { "unknown" }
        val buildType = Build.TYPE.ifBlank { "user" }
        val buildTags = Build.TAGS.ifBlank { "release-keys" }

        val isEmulatorOrGphone = product.contains("sdk_gphone", ignoreCase = true) ||
            model.contains("sdk_gphone", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            fingerprint.contains("generic", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true) ||
            hardware.contains("goldfish", ignoreCase = true)

        val isUserDebugOrEng = buildType.equals("userdebug", ignoreCase = true) ||
            buildType.equals("eng", ignoreCase = true) ||
            buildTags.contains("test-keys", ignoreCase = true)

        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "N/A"
        }

        return DeviceTelemetrySnapshot(
            model = model,
            manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" },
            brand = Build.BRAND.ifBlank { "Unknown" },
            device = Build.DEVICE.ifBlank { "Unknown" },
            product = product,
            hardware = hardware,
            androidRelease = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = securityPatch,
            buildType = buildType,
            buildTags = buildTags,
            fingerprint = fingerprint,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unknown" },
            isEmulatorOrSdkGphone = isEmulatorOrGphone,
            isUserDebugOrEng = isUserDebugOrEng,
            isAndroid14OrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            screenSummary = screenSummary
        )
    }
}
