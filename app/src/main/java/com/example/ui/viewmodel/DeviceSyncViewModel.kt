package com.example.ui.viewmodel

import android.util.Log
import com.example.data.firebase.DeviceInfoProvider
import com.example.data.firebase.NetworkMonitor
import com.example.data.firebase.inspectCurrentUserDebugInfo
import com.example.data.model.AuthDebugInfo
import com.example.data.model.DebugEventType
import com.example.data.model.DebugLogEntry
import com.example.data.model.DeviceSettings
import com.example.data.model.DeviceTelemetrySnapshot
import com.example.data.model.NetworkStatus
import com.example.data.model.SyncMetadataState
import com.example.data.model.SyncedCollection
import com.example.data.model.SyncedDocument
import com.example.data.model.TestCategory
import com.example.data.model.TestProgressItem
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.repository.DeviceSyncRepository
import com.example.data.repository.WriteSyncResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class CloudBannerStatus(
    val isDeviceOnline: Boolean,
    val isSimulatedOffline: Boolean,
    val transportLabel: String,
    val isMetered: Boolean,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean
) {
    val isEffectivelyOnline: Boolean
        get() = isDeviceOnline && !isSimulatedOffline
}

class DeviceSyncViewModel(
    private val repository: DeviceSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val deviceInfoProvider: DeviceInfoProvider,
    val currentUserId: String
) : ViewModel() {

    private val _telemetry = MutableStateFlow(deviceInfoProvider.captureSnapshot())
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = _telemetry.asStateFlow()

    private val _authDebugInfo = MutableStateFlow<AuthDebugInfo?>(null)
    val authDebugInfo: StateFlow<AuthDebugInfo?> = _authDebugInfo.asStateFlow()

    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.observeNetworkStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = networkMonitor.currentStatus()
        )

    val profileState: StateFlow<UiState<SyncedDocument<UserProfile>>> =
        repository.observeUserProfile(currentUserId)
            .map<SyncedDocument<UserProfile>, UiState<SyncedDocument<UserProfile>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val settingsState: StateFlow<UiState<SyncedDocument<DeviceSettings>>> =
        repository.observeDeviceSettings(currentUserId)
            .map<SyncedDocument<DeviceSettings>, UiState<SyncedDocument<DeviceSettings>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing settings", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load settings"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val testItemsState: StateFlow<UiState<SyncedCollection<TestProgressItem>>> =
        repository.observeTestItems(currentUserId)
            .map<SyncedCollection<TestProgressItem>, UiState<SyncedCollection<TestProgressItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing test items", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe checkpoints"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val debugLogsState: StateFlow<UiState<SyncedCollection<DebugLogEntry>>> =
        repository.observeDebugLogs(currentUserId)
            .map<SyncedCollection<DebugLogEntry>, UiState<SyncedCollection<DebugLogEntry>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing debug logs", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe debug logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val cloudBannerStatus: StateFlow<CloudBannerStatus> = combine(
        networkStatus,
        simulatedOffline,
        settingsState,
        testItemsState,
        debugLogsState
    ) { net, simOffline, settingsUi, itemsUi, logsUi ->
        val metas = listOfNotNull(
            (settingsUi as? UiState.Success)?.data?.metadata,
            (itemsUi as? UiState.Success)?.data?.metadata,
            (logsUi as? UiState.Success)?.data?.metadata
        )
        CloudBannerStatus(
            isDeviceOnline = net.isConnected,
            isSimulatedOffline = simOffline,
            transportLabel = if (simOffline) "Simulated Offline" else net.transportLabel,
            isMetered = net.isMetered,
            isFromCache = metas.any { it.isFromCache },
            hasPendingWrites = metas.any { it.hasPendingWrites }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CloudBannerStatus(
            isDeviceOnline = true,
            isSimulatedOffline = false,
            transportLabel = "Wi-Fi",
            isMetered = false,
            isFromCache = false,
            hasPendingWrites = false
        )
    )

    init {
        bootstrapSession()
    }

    private fun isEffectivelyOnline(): Boolean =
        networkStatus.value.isConnected && !_simulatedOffline.value

    private fun effectiveNetworkCode(): String =
        if (_simulatedOffline.value) "SIMULATED_OFFLINE" else networkStatus.value.code

    private fun bootstrapSession() {
        viewModelScope.launch {
            val snap = deviceInfoProvider.captureSnapshot()
            _telemetry.value = snap
            _authDebugInfo.value = inspectCurrentUserDebugInfo(forceRefreshToken = false)
            repository.ensureBootstrapData(
                userId = currentUserId,
                telemetry = snap,
                isOnline = isEffectivelyOnline()
            ).onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Cloud sync bootstrap warning"
            }
        }
    }

    fun clearStatusBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun refreshDeviceAndAuthTelemetry(forceRefreshToken: Boolean = true) {
        viewModelScope.launch {
            val snap = deviceInfoProvider.captureSnapshot()
            _telemetry.value = snap
            val info = inspectCurrentUserDebugInfo(forceRefreshToken = forceRefreshToken)
            _authDebugInfo.value = info
            repository.syncUserProfile(currentUserId, snap, isEffectivelyOnline())
            val res = repository.appendDebugLog(
                userId = currentUserId,
                eventType = if (forceRefreshToken) DebugEventType.AUTH_CHECK else DebugEventType.DEVICE_SNAPSHOT,
                summary = if (forceRefreshToken) "Auth token & device telemetry verified" else "Captured Android ${snap.sdkInt} telemetry snapshot",
                details = "Model=${snap.model} (${snap.buildType}), RAM=${snap.availRamMb}/${snap.totalRamMb}MB, Battery=${snap.batteryPercent}%, Token=${info?.lastTokenRefreshStatus ?: "N/A"}",
                networkState = effectiveNetworkCode(),
                telemetry = snap,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Telemetry & auth debug snapshot saved")
        }
    }

    fun toggleSimulatedOffline() {
        viewModelScope.launch {
            val targetOffline = !_simulatedOffline.value
            val result = repository.setNetworkEnabled(!targetOffline)
            if (result.isSuccess) {
                _simulatedOffline.value = targetOffline
                val snap = deviceInfoProvider.captureSnapshot()
                _telemetry.value = snap
                repository.appendDebugLog(
                    userId = currentUserId,
                    eventType = DebugEventType.SYNC_TEST,
                    summary = if (targetOffline) "Simulated offline mode enabled" else "Network re-enabled; flushing offline queue",
                    details = if (targetOffline) "Writes now persist to local Firestore cache with hasPendingWrites=true." else "Queued offline mutations will now sync to Cloud Firestore.",
                    networkState = effectiveNetworkCode(),
                    telemetry = snap,
                    isOnline = isEffectivelyOnline()
                )
                _statusBannerMessage.value = if (targetOffline) {
                    "Offline simulation active — changes save locally and auto-sync later"
                } else {
                    "Cloud connection restored — syncing pending changes"
                }
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Could not toggle network mode"
            }
        }
    }

    fun updateSettings(transform: (DeviceSettings) -> DeviceSettings) {
        viewModelScope.launch {
            val current = (settingsState.value as? UiState.Success)?.data?.data
                ?: DeviceSettings(userId = currentUserId)
            val next = transform(current)
            val res = repository.saveDeviceSettings(currentUserId, next, isEffectivelyOnline())
            if (res.isFailure) {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings"
            } else if (res.getOrNull() == WriteSyncResult.QUEUED_OFFLINE) {
                _statusBannerMessage.value = "Settings saved locally • pending cloud sync"
            }
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            val current = (settingsState.value as? UiState.Success)?.data?.data
                ?: DeviceSettings(userId = currentUserId)
            val snap = _telemetry.value
            val defaults = DeviceSettings(
                id = current.id,
                userId = currentUserId,
                themeMode = ThemeMode.SYSTEM.name,
                dynamicColorEnabled = false,
                compactDensityEnabled = false,
                hapticsEnabled = true,
                autoSyncEnabled = true,
                syncOnMeteredEnabled = true,
                verboseDebugOverlayEnabled = true,
                keepScreenAwakeInDebug = false,
                defaultTestEnvironment = if (snap.isEmulatorOrSdkGphone) "sdk_gphone_api${snap.sdkInt}" else "physical_api${snap.sdkInt}",
                offlineCacheSizeMb = 200L,
                syncCounter = current.syncCounter,
                createdAt = current.createdAt
            )
            val res = repository.saveDeviceSettings(currentUserId, defaults, isEffectivelyOnline())
            handleWriteOutcome(res, "Settings reset to defaults")
        }
    }

    fun addTestItem(title: String, notes: String, category: TestCategory) {
        viewModelScope.launch {
            val res = repository.addTestItem(
                userId = currentUserId,
                title = title,
                notes = notes,
                category = category,
                telemetry = _telemetry.value,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Checkpoint saved")
        }
    }

    fun updateTestItem(
        itemId: String,
        title: String,
        notes: String,
        category: TestCategory,
        completed: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateTestItem(
                userId = currentUserId,
                itemId = itemId,
                title = title,
                notes = notes,
                category = category,
                completed = completed,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Checkpoint updated")
        }
    }

    fun toggleTestItem(item: TestProgressItem) {
        viewModelScope.launch {
            val res = repository.toggleTestItem(
                userId = currentUserId,
                itemId = item.id,
                completed = !item.completed,
                isOnline = isEffectivelyOnline()
            )
            if (res.isFailure) {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update checkpoint"
            }
        }
    }

    fun deleteTestItem(itemId: String) {
        viewModelScope.launch {
            val res = repository.deleteTestItem(currentUserId, itemId, isEffectivelyOnline())
            handleWriteOutcome(res, "Checkpoint deleted")
        }
    }

    fun seedStandardQaCheckpoints() {
        viewModelScope.launch {
            val snap = _telemetry.value
            val online = isEffectivelyOnline()
            val seeds = listOf(
                Triple("Verify Google Sign-In & Credential Manager token", "Check GetSignInWithGoogleOption interactive flow and silent GetGoogleIdOption on cold start.", TestCategory.AUTH),
                Triple("Test offline writes & pending sync recovery", "Enable Offline Simulation, toggle settings or add a note, then re-enable cloud sync.", TestCategory.OFFLINE),
                Triple("Verify cloud-synced Theme & Density preferences", "Switch Light/Dark mode and Compact Density; confirm persistence across app restarts.", TestCategory.SETTINGS),
                Triple("Inspect ${snap.model} (${snap.buildType}) API ${snap.sdkInt} build", "Confirm Build.FINGERPRINT, RAM headroom, and network transport telemetry.", TestCategory.DEVICE)
            )
            var failed = false
            for ((title, notes, category) in seeds) {
                val res = repository.addTestItem(currentUserId, title, notes, category, snap, online)
                if (res.isFailure) failed = true
            }
            _statusBannerMessage.value = if (failed) {
                "Some starter checkpoints could not be saved"
            } else if (online) {
                "Added 4 Android QA checkpoints to Cloud Firestore"
            } else {
                "Added 4 QA checkpoints offline • will sync automatically"
            }
        }
    }

    fun appendCustomDebugLog(eventType: DebugEventType, summary: String, details: String) {
        viewModelScope.launch {
            val snap = deviceInfoProvider.captureSnapshot()
            _telemetry.value = snap
            val res = repository.appendDebugLog(
                userId = currentUserId,
                eventType = eventType,
                summary = summary,
                details = details,
                networkState = effectiveNetworkCode(),
                telemetry = snap,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Debug log recorded")
        }
    }

    fun deleteDebugLog(logId: String) {
        viewModelScope.launch {
            val res = repository.deleteDebugLog(currentUserId, logId, isEffectivelyOnline())
            if (res.isFailure) {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete log"
            }
        }
    }

    fun buildDiagnosticReportText(): String {
        val snap = _telemetry.value
        val auth = _authDebugInfo.value
        val banner = cloudBannerStatus.value
        val settings = (settingsState.value as? UiState.Success)?.data?.data
        val items = (testItemsState.value as? UiState.Success)?.data?.items.orEmpty()
        val logs = (debugLogsState.value as? UiState.Success)?.data?.items.orEmpty()

        return buildString {
            appendLine("=== DeviceSync Diagnostic Report ===")
            appendLine("Device: ${snap.manufacturer} ${snap.model} (product=${snap.product}, device=${snap.device})")
            appendLine("OS: ${snap.androidRelease} | BuildType=${snap.buildType} | Patch=${snap.securityPatch}")
            appendLine("Fingerprint: ${snap.fingerprint}")
            appendLine("ABIs: ${snap.supportedAbis}")
            appendLine("RAM: ${snap.availRamMb} MB free / ${snap.totalRamMb} MB total | Battery: ${snap.batteryPercent}% (charging=${snap.isCharging})")
            appendLine("Network: ${banner.transportLabel} (online=${banner.isEffectivelyOnline}, metered=${banner.isMetered})")
            appendLine("Firestore Cache: isFromCache=${banner.isFromCache}, hasPendingWrites=${banner.hasPendingWrites}")
            if (auth != null) {
                appendLine("Auth UID: ${auth.uid}")
                appendLine("Auth Email: ${auth.email} (verified=${auth.isEmailVerified})")
                appendLine("Auth Providers: ${auth.providers.joinToString()}")
                appendLine("Token Status: ${auth.lastTokenRefreshStatus}")
            }
            if (settings != null) {
                appendLine("Cloud Settings: theme=${settings.themeMode}, dynamicColor=${settings.dynamicColorEnabled}, compact=${settings.compactDensityEnabled}, autoSync=${settings.autoSyncEnabled}, env=${settings.defaultTestEnvironment}, syncCounter=${settings.syncCounter}")
            }
            appendLine("QA Checkpoints: ${items.count { it.completed }}/${items.size} completed")
            appendLine("Recent Debug Logs (${logs.size}):")
            logs.take(10).forEach { log ->
                appendLine(" - [${log.eventType}] ${log.summary} (${log.networkState})")
            }
        }
    }

    private fun handleWriteOutcome(result: Result<WriteSyncResult>, successLabel: String) {
        if (result.isFailure) {
            _statusBannerMessage.value =
                result.exceptionOrNull()?.localizedMessage ?: "Cloud write failed"
        } else if (result.getOrNull() == WriteSyncResult.QUEUED_OFFLINE) {
            _statusBannerMessage.value = "$successLabel offline • pending cloud sync"
        } else {
            _statusBannerMessage.value = "$successLabel to cloud"
        }
    }

    companion object {
        private const val TAG = "DeviceSyncVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
