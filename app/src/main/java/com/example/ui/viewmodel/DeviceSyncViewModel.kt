package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.DeviceInfoProvider
import com.example.data.firebase.NetworkMonitor
import com.example.data.firebase.inspectCurrentUserDebugInfo
import com.example.data.model.AuthDebugInfo
import com.example.data.model.DebugEventType
import com.example.data.model.DebugLogEntry
import com.example.data.model.DeviceSettings
import com.example.data.model.DeviceTelemetrySnapshot
import com.example.data.model.NetworkStatus
import com.example.data.model.OfflineSaveSummary
import com.example.data.model.SyncMetadataState
import com.example.data.model.SyncedCollection
import com.example.data.model.SyncedDocument
import com.example.data.model.TestCategory
import com.example.data.model.TestProgressItem
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.VaultCategory
import com.example.data.model.VaultItem
import com.example.data.repository.DeviceSyncRepository
import com.example.data.repository.WriteSyncResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _telemetry = MutableStateFlow(placeholderTelemetry())
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = _telemetry.asStateFlow()

    private val _authDebugInfo = MutableStateFlow<AuthDebugInfo?>(null)
    val authDebugInfo: StateFlow<AuthDebugInfo?> = _authDebugInfo.asStateFlow()

    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    private val _isSavingOffline = MutableStateFlow(false)
    val isSavingOffline: StateFlow<Boolean> = _isSavingOffline.asStateFlow()

    private val _offlineSaveSummary = MutableStateFlow<OfflineSaveSummary?>(null)
    val offlineSaveSummary: StateFlow<OfflineSaveSummary?> = _offlineSaveSummary.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Instant 0ms optimistic settings override so toggles & themes never lag
    private val _optimisticSettings = MutableStateFlow<DeviceSettings?>(null)

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

    private val remoteSettingsState: StateFlow<UiState<SyncedDocument<DeviceSettings>>> =
        repository.observeDeviceSettings(currentUserId)
            .map<SyncedDocument<DeviceSettings>, UiState<SyncedDocument<DeviceSettings>>> { synced ->
                _optimisticSettings.value = null
                UiState.Success(synced)
            }
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

    val settingsState: StateFlow<UiState<SyncedDocument<DeviceSettings>>> = combine(
        remoteSettingsState,
        _optimisticSettings
    ) { remote, optimistic ->
        if (optimistic != null && remote is UiState.Success) {
            UiState.Success(remote.data.copy(data = optimistic))
        } else if (optimistic != null && remote is UiState.Loading) {
            UiState.Success(
                SyncedDocument(
                    data = optimistic,
                    exists = false,
                    metadata = SyncMetadataState(isFromCache = true, hasPendingWrites = true)
                )
            )
        } else {
            remote
        }
    }.stateIn(
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

    val vaultItemsState: StateFlow<UiState<SyncedCollection<VaultItem>>> =
        repository.observeVaultItems(currentUserId)
            .map<SyncedCollection<VaultItem>, UiState<SyncedCollection<VaultItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing vault items", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe offline vault"))
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
        remoteSettingsState,
        testItemsState,
        vaultItemsState
    ) { net, simOffline, settingsUi, itemsUi, vaultUi ->
        val metas = listOfNotNull(
            (settingsUi as? UiState.Success)?.data?.metadata,
            (itemsUi as? UiState.Success)?.data?.metadata,
            (vaultUi as? UiState.Success)?.data?.metadata
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
            val snap = withContext(Dispatchers.IO) { deviceInfoProvider.captureSnapshot() }
            _telemetry.value = snap
            val authInfo = withContext(Dispatchers.IO) {
                inspectCurrentUserDebugInfo(forceRefreshToken = false)
            }
            _authDebugInfo.value = authInfo
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

    fun saveForOfflineUse() {
        if (_isSavingOffline.value) return
        viewModelScope.launch {
            _isSavingOffline.value = true
            try {
                val res = repository.saveAllForOfflineUse(currentUserId, isEffectivelyOnline())
                val pinnedCount = (vaultItemsState.value as? UiState.Success)
                    ?.data?.items?.count { it.pinnedOffline } ?: 0
                val nowLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                res.onSuccess { cachedCount ->
                    _offlineSaveSummary.value = OfflineSaveSummary(
                        totalCachedItems = cachedCount,
                        pinnedVaultItems = pinnedCount,
                        savedAtLabel = nowLabel
                    )
                    _statusBannerMessage.value =
                        "Saved $cachedCount records & settings locally for offline use"
                }.onFailure { err ->
                    _statusBannerMessage.value =
                        err.localizedMessage ?: "Offline cache check failed"
                }
            } finally {
                _isSavingOffline.value = false
            }
        }
    }

    fun refreshDeviceAndAuthTelemetry(forceRefreshToken: Boolean = true) {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) { deviceInfoProvider.captureSnapshot() }
            _telemetry.value = snap
            val info = withContext(Dispatchers.IO) {
                inspectCurrentUserDebugInfo(forceRefreshToken = forceRefreshToken)
            }
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
                val snap = withContext(Dispatchers.IO) { deviceInfoProvider.captureSnapshot() }
                _telemetry.value = snap
                repository.appendDebugLog(
                    userId = currentUserId,
                    eventType = DebugEventType.SYNC_TEST,
                    summary = if (targetOffline) "Simulated offline mode enabled" else "Network re-enabled; flushing offline queue",
                    details = if (targetOffline) "Writes persist instantaneously to local cache." else "Queued offline mutations will now sync to Cloud Firestore.",
                    networkState = effectiveNetworkCode(),
                    telemetry = snap,
                    isOnline = isEffectivelyOnline()
                )
                _statusBannerMessage.value = if (targetOffline) {
                    "Offline simulation active — changes save locally & auto-sync later"
                } else {
                    "Cloud connection restored — syncing pending changes"
                }
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Could not toggle network mode"
            }
        }
    }

    fun updateSettings(transform: (DeviceSettings) -> DeviceSettings) {
        val currentSuccess = settingsState.value as? UiState.Success
        val current = currentSuccess?.data?.data ?: DeviceSettings(userId = currentUserId)
        val existsInDb = currentSuccess?.data?.exists == true
        val next = transform(current)

        // Immediate 0ms UI update
        _optimisticSettings.value = next

        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.saveDeviceSettings(
                userId = currentUserId,
                updated = next,
                existsInFirestore = existsInDb,
                isOnline = isEffectivelyOnline()
            )
            if (res.isFailure) {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings"
            }
        }
    }

    fun resetSettingsToDefaults() {
        val currentSuccess = settingsState.value as? UiState.Success
        val current = currentSuccess?.data?.data ?: DeviceSettings(userId = currentUserId)
        val existsInDb = currentSuccess?.data?.exists == true
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
            offlineCacheSizeMb = 250L,
            syncCounter = current.syncCounter,
            createdAt = current.createdAt
        )
        _optimisticSettings.value = defaults
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.saveDeviceSettings(currentUserId, defaults, existsInDb, isEffectivelyOnline())
            handleWriteOutcome(res, "Settings reset to defaults")
        }
    }

    fun addVaultItem(
        title: String,
        subtitle: String,
        notes: String,
        category: VaultCategory,
        sizeLabel: String,
        pinnedOffline: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.addVaultItem(
                userId = currentUserId,
                title = title,
                subtitle = subtitle,
                notes = notes,
                category = category,
                sizeLabel = sizeLabel,
                pinnedOffline = pinnedOffline,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Saved ${category.label} item for offline use")
        }
    }

    fun updateVaultItem(
        itemId: String,
        title: String,
        subtitle: String,
        notes: String,
        category: VaultCategory,
        sizeLabel: String,
        pinnedOffline: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.updateVaultItem(
                userId = currentUserId,
                itemId = itemId,
                title = title,
                subtitle = subtitle,
                notes = notes,
                category = category,
                sizeLabel = sizeLabel,
                pinnedOffline = pinnedOffline,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Updated offline vault item")
        }
    }

    fun toggleVaultPinnedOffline(item: VaultItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextPinned = !item.pinnedOffline
            val res = repository.toggleVaultPinnedOffline(
                userId = currentUserId,
                itemId = item.id,
                pinnedOffline = nextPinned,
                isOnline = isEffectivelyOnline()
            )
            if (res.isFailure) {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update offline pin"
            } else {
                _statusBannerMessage.value = if (nextPinned) "Pinned for offline access" else "Unpinned from offline priority"
            }
        }
    }

    fun deleteVaultItem(itemId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.deleteVaultItem(currentUserId, itemId, isEffectivelyOnline())
            handleWriteOutcome(res, "Vault item removed")
        }
    }

    fun importDocumentIntoVault(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val appContext = context.applicationContext
            var fileName = "Attached File"
            var fileSizeBytes = -1L
            val mimeType = appContext.contentResolver.getType(uri) ?: "application/octet-stream"

            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx >= 0) fileSizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            val formattedSize = formatByteSize(fileSizeBytes)
            val res = repository.addVaultItem(
                userId = currentUserId,
                title = fileName,
                subtitle = mimeType,
                notes = "Saved via Android Document Picker for offline reference.\nURI: $uri",
                category = VaultCategory.FILE,
                sizeLabel = formattedSize,
                pinnedOffline = true,
                isOnline = isEffectivelyOnline()
            )
            handleWriteOutcome(res, "Saved file \"$fileName\" offline")
        }
    }

    fun seedStarterVaultItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val online = isEffectivelyOnline()
            val snap = _telemetry.value
            val seeds = listOf(
                Triple(VaultCategory.ACCOUNT, "Google Test Account", "${_authDebugInfo.value?.email ?: "tester@gmail.com"} • Primary Auth"),
                Triple(VaultCategory.APP, "DeviceSync Companion", "com.aistudio.devicesync.kxmpqr • ${snap.androidRelease}"),
                Triple(VaultCategory.GAME, "Emulator Benchmark Game", "Save Slot #1 • Level 18 • 60 FPS Profile"),
                Triple(VaultCategory.FILE, "build_prop_snapshot.txt", "text/plain • ${snap.model} (${snap.buildType})"),
                Triple(VaultCategory.OTHER, "Offline Recovery Checklist", "Local persistent cache verified (${snap.availRamMb} MB RAM free)")
            )
            seeds.forEach { (category, title, subtitle) ->
                repository.addVaultItem(
                    userId = currentUserId,
                    title = title,
                    subtitle = subtitle,
                    notes = "Automatically cached for offline access and synced across devices.",
                    category = category,
                    sizeLabel = if (category == VaultCategory.FILE) "12 KB" else "Offline Ready",
                    pinnedOffline = true,
                    isOnline = online
                )
            }
            _statusBannerMessage.value = "Saved 5 starter items (Account, App, Game, File, Other) offline"
        }
    }

    fun addTestItem(title: String, notes: String, category: TestCategory) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.deleteTestItem(currentUserId, itemId, isEffectivelyOnline())
            handleWriteOutcome(res, "Checkpoint deleted")
        }
    }

    fun seedStandardQaCheckpoints() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = _telemetry.value
            val online = isEffectivelyOnline()
            val seeds = listOf(
                Triple("Verify Google Sign-In & Credential Manager token", "Check GetSignInWithGoogleOption interactive flow and silent GetGoogleIdOption on cold start.", TestCategory.AUTH),
                Triple("Test offline writes & pending sync recovery", "Enable Offline Simulation, save items or toggle settings, then re-enable cloud sync.", TestCategory.OFFLINE),
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        val vault = (vaultItemsState.value as? UiState.Success)?.data?.items.orEmpty()
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
            appendLine("Offline Vault: ${vault.size} items (${vault.count { it.pinnedOffline }} pinned offline)")
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
            _statusBannerMessage.value = "$successLabel (offline queue)"
        } else {
            _statusBannerMessage.value = successLabel
        }
    }

    companion object {
        private const val TAG = "DeviceSyncVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        private fun formatByteSize(bytes: Long): String {
            if (bytes < 0L) return "File"
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f MB", mb)
        }

        private fun placeholderTelemetry(): DeviceTelemetrySnapshot =
            DeviceTelemetrySnapshot(
                model = android.os.Build.MODEL.ifBlank { "Android Device" },
                manufacturer = android.os.Build.MANUFACTURER.ifBlank { "Android" },
                brand = android.os.Build.BRAND.ifBlank { "Android" },
                device = android.os.Build.DEVICE.ifBlank { "generic" },
                product = android.os.Build.PRODUCT.ifBlank { "sdk_gphone" },
                hardware = android.os.Build.HARDWARE.ifBlank { "ranchu" },
                androidRelease = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                sdkInt = android.os.Build.VERSION.SDK_INT,
                securityPatch = "Loading…",
                buildType = android.os.Build.TYPE.ifBlank { "userdebug" },
                buildTags = android.os.Build.TAGS.ifBlank { "dev-keys" },
                fingerprint = android.os.Build.FINGERPRINT.ifBlank { "" },
                supportedAbis = android.os.Build.SUPPORTED_ABIS.joinToString(", "),
                isEmulatorOrSdkGphone = false,
                isUserDebugOrEng = false,
                isAndroid14OrHigher = android.os.Build.VERSION.SDK_INT >= 34,
                totalRamMb = 0L,
                availRamMb = 0L,
                batteryPercent = -1,
                isCharging = false,
                screenSummary = "Loading…"
            )
    }
}
