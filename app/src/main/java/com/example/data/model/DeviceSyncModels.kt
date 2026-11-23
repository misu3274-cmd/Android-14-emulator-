package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun safeValueOf(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class TestCategory(val label: String) {
    AUTH("Auth"),
    OFFLINE("Offline"),
    SETTINGS("Settings"),
    DEVICE("Device"),
    GENERAL("General");

    companion object {
        fun safeValueOf(raw: String?): TestCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class DebugEventType(val label: String) {
    AUTH_CHECK("Auth Check"),
    SYNC_TEST("Sync Test"),
    NETWORK_CHANGE("Network"),
    SETTINGS_SYNC("Settings Sync"),
    DEVICE_SNAPSHOT("Device Snapshot"),
    CUSTOM_NOTE("Debug Note");

    companion object {
        fun safeValueOf(raw: String?): DebugEventType =
            entries.firstOrNull { it.name == raw } ?: CUSTOM_NOTE
    }
}

enum class VaultCategory(val label: String) {
    ACCOUNT("Accounts"),
    APP("Apps"),
    GAME("Games"),
    FILE("Files"),
    OTHER("Other");

    companion object {
        fun safeValueOf(raw: String?): VaultCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class VaultItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val notes: String = "",
    val category: String = VaultCategory.OTHER.name,
    val sizeLabel: String = "",
    val pinnedOffline: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedCategory: VaultCategory
        get() = VaultCategory.safeValueOf(category)
}

data class OfflineSaveSummary(
    val totalCachedItems: Int = 0,
    val pinnedVaultItems: Int = 0,
    val savedAtLabel: String = ""
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String = "",
    val lastDeviceModel: String = "",
    val lastAndroidRelease: String = "",
    val lastBuildType: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColorEnabled: Boolean = false,
    val compactDensityEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val syncOnMeteredEnabled: Boolean = true,
    val verboseDebugOverlayEnabled: Boolean = true,
    val keepScreenAwakeInDebug: Boolean = false,
    val defaultTestEnvironment: String = "sdk_gphone_api34",
    val offlineCacheSizeMb: Long = 200L,
    val syncCounter: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedThemeMode: ThemeMode
        get() = ThemeMode.safeValueOf(themeMode)
}

@Keep
@IgnoreExtraProperties
data class TestProgressItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = TestCategory.GENERAL.name,
    val completed: Boolean = false,
    val deviceTag: String = "",
    val sdkInt: Long = 34L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedCategory: TestCategory
        get() = TestCategory.safeValueOf(category)
}

@Keep
@IgnoreExtraProperties
data class DebugLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = DebugEventType.CUSTOM_NOTE.name,
    val summary: String = "",
    val details: String = "",
    val networkState: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val resolvedEventType: DebugEventType
        get() = DebugEventType.safeValueOf(eventType)
}

data class SyncMetadataState(
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false
)

data class SyncedCollection<T>(
    val items: List<T> = emptyList(),
    val metadata: SyncMetadataState = SyncMetadataState()
)

data class SyncedDocument<T>(
    val data: T? = null,
    val exists: Boolean = false,
    val metadata: SyncMetadataState = SyncMetadataState()
)

data class NetworkStatus(
    val isConnected: Boolean = true,
    val transportLabel: String = "Wi-Fi",
    val isMetered: Boolean = false
) {
    val code: String
        get() = when {
            !isConnected -> "OFFLINE"
            transportLabel == "Wi-Fi" -> "ONLINE_WIFI"
            transportLabel == "Cellular" -> if (isMetered) "ONLINE_CELLULAR_METERED" else "ONLINE_CELLULAR"
            else -> "ONLINE_${transportLabel.uppercase()}"
        }
}

data class DeviceTelemetrySnapshot(
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildType: String,
    val buildTags: String,
    val fingerprint: String,
    val supportedAbis: String,
    val isEmulatorOrSdkGphone: Boolean,
    val isUserDebugOrEng: Boolean,
    val isAndroid14OrHigher: Boolean,
    val totalRamMb: Long,
    val availRamMb: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val screenSummary: String
)

data class AuthDebugInfo(
    val uid: String,
    val displayName: String,
    val email: String,
    val isEmailVerified: Boolean,
    val providers: List<String>,
    val createdAtMillis: Long?,
    val lastSignInMillis: Long?,
    val tokenSignInProvider: String?,
    val tokenExpirationMillis: Long?,
    val lastTokenRefreshStatus: String
)
