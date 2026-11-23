package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DebugEventType
import com.example.data.model.DebugLogEntry
import com.example.data.model.DeviceSettings
import com.example.data.model.DeviceTelemetrySnapshot
import com.example.data.model.SyncMetadataState
import com.example.data.model.SyncedCollection
import com.example.data.model.SyncedDocument
import com.example.data.model.TestCategory
import com.example.data.model.TestProgressItem
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.VaultCategory
import com.example.data.model.VaultItem
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class WriteSyncResult {
    SYNCED_CLOUD,
    QUEUED_OFFLINE
}

class DeviceSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)

    private fun settingsDocRef(uid: String) =
        userDocRef(uid).collection("settings").document(DEFAULT_SETTINGS_DOC_ID)

    private fun testItemsCollection(uid: String) =
        userDocRef(uid).collection("test_items")

    private fun debugLogsCollection(uid: String) =
        userDocRef(uid).collection("debug_logs")

    private fun vaultItemsCollection(uid: String) =
        userDocRef(uid).collection("vault_items")

    suspend fun setNetworkEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                db.enableNetwork().await()
            } else {
                db.disableNetwork().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle Firestore network state", e)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<SyncedDocument<UserProfile>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val docRef = userDocRef(uid)
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val model = snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    SyncedDocument(
                        data = model,
                        exists = snapshot.exists(),
                        metadata = SyncMetadataState(
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }.flowOn(Dispatchers.Default)

    fun observeDeviceSettings(userId: String): Flow<SyncedDocument<DeviceSettings>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val docRef = settingsDocRef(uid)
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val model = snapshot.toObject(
                        DeviceSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    SyncedDocument(
                        data = model,
                        exists = snapshot.exists(),
                        metadata = SyncMetadataState(
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }.flowOn(Dispatchers.Default)

    fun observeTestItems(userId: String): Flow<SyncedCollection<TestProgressItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val colRef = testItemsCollection(uid)
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        TestProgressItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    SyncedCollection(
                        items = items,
                        metadata = SyncMetadataState(
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }.flowOn(Dispatchers.Default)

    fun observeDebugLogs(userId: String): Flow<SyncedCollection<DebugLogEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val colRef = debugLogsCollection(uid)
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val logs = snapshot.toObjects(
                        DebugLogEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    SyncedCollection(
                        items = logs,
                        metadata = SyncMetadataState(
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }.flowOn(Dispatchers.Default)

    fun observeVaultItems(userId: String): Flow<SyncedCollection<VaultItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val colRef = vaultItemsCollection(uid)
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        VaultItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<VaultItem> { it.pinnedOffline }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                    SyncedCollection(
                        items = items,
                        metadata = SyncMetadataState(
                            isFromCache = snapshot.metadata.isFromCache,
                            hasPendingWrites = snapshot.metadata.hasPendingWrites()
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Explicitly warms and verifies local Firestore persistent cache across all collections
     * so everything (settings, accounts, apps, games, files, tasks, logs) is ready offline.
     */
    suspend fun saveAllForOfflineUse(
        userId: String,
        isOnline: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }

            val source = if (isOnline) Source.DEFAULT else Source.CACHE
            coroutineScope {
                val profileJob = async {
                    withTimeoutOrNull(1800L) { runCatching { userDocRef(uid).get(source).await() }.getOrNull() }
                        ?: runCatching { userDocRef(uid).get(Source.CACHE).await() }.getOrNull()
                }
                val settingsJob = async {
                    withTimeoutOrNull(1800L) { runCatching { settingsDocRef(uid).get(source).await() }.getOrNull() }
                        ?: runCatching { settingsDocRef(uid).get(Source.CACHE).await() }.getOrNull()
                }
                val itemsJob = async {
                    withTimeoutOrNull(2000L) { runCatching { testItemsCollection(uid).get(source).await() }.getOrNull() }
                        ?: runCatching { testItemsCollection(uid).get(Source.CACHE).await() }.getOrNull()
                }
                val vaultJob = async {
                    withTimeoutOrNull(2000L) { runCatching { vaultItemsCollection(uid).get(source).await() }.getOrNull() }
                        ?: runCatching { vaultItemsCollection(uid).get(Source.CACHE).await() }.getOrNull()
                }
                val logsJob = async {
                    withTimeoutOrNull(2000L) { runCatching { debugLogsCollection(uid).limit(60).get(source).await() }.getOrNull() }
                        ?: runCatching { debugLogsCollection(uid).limit(60).get(Source.CACHE).await() }.getOrNull()
                }

                val count =
                    (if (profileJob.await()?.exists() == true) 1 else 0) +
                        (if (settingsJob.await()?.exists() == true) 1 else 0) +
                        (itemsJob.await()?.size() ?: 0) +
                        (vaultJob.await()?.size() ?: 0) +
                        (logsJob.await()?.size() ?: 0)
                Result.success(count)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun ensureBootstrapData(
        userId: String,
        telemetry: DeviceTelemetrySnapshot,
        isOnline: Boolean
    ): Result<WriteSyncResult> = withContext(Dispatchers.IO) {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        if (uid != userId) return@withContext Result.failure(IllegalStateException("User changed during bootstrap"))

        val profileResult = syncUserProfile(userId, telemetry, isOnline)
        if (profileResult.isFailure) return@withContext profileResult

        val settingsRef = settingsDocRef(uid)
        try {
            val cached = runCatching { settingsRef.get(Source.CACHE).await() }.getOrNull()
            val existing = cached?.takeIf { it.exists() }
                ?: if (isOnline) withTimeoutOrNull(1500L) { runCatching { settingsRef.get().await() }.getOrNull() } else null

            if (existing == null || !existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "themeMode" to ThemeMode.SYSTEM.name,
                    "dynamicColorEnabled" to false,
                    "compactDensityEnabled" to false,
                    "hapticsEnabled" to true,
                    "autoSyncEnabled" to true,
                    "syncOnMeteredEnabled" to true,
                    "verboseDebugOverlayEnabled" to true,
                    "keepScreenAwakeInDebug" to false,
                    "defaultTestEnvironment" to if (telemetry.isEmulatorOrSdkGphone) "sdk_gphone_api${telemetry.sdkInt}" else "physical_api${telemetry.sdkInt}",
                    "offlineCacheSizeMb" to 250L,
                    "syncCounter" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dispatchWrite(settingsRef.set(payload), OperationType.CREATE, settingsRef.path, isOnline)
            } else {
                Result.success(WriteSyncResult.SYNCED_CLOUD)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, settingsRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncUserProfile(
        userId: String,
        telemetry: DeviceTelemetrySnapshot,
        isOnline: Boolean
    ): Result<WriteSyncResult> = withContext(Dispatchers.IO) {
        var docPath = "users/$userId"
        try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val user = auth.currentUser
            val docRef = userDocRef(uid)
            docPath = docRef.path

            val displayName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Google Tester").take(100)
            val email = user?.email?.takeIf { it.isNotBlank() }?.take(254)
            val deviceModel = telemetry.model.take(100).ifBlank { "Android Device" }
            val androidRelease = telemetry.androidRelease.take(40).ifBlank { "Android 14" }
            val buildType = telemetry.buildType.take(40).ifBlank { "user" }

            val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            val existing = cached?.takeIf { it.exists() }
                ?: if (isOnline) withTimeoutOrNull(1500L) { runCatching { docRef.get().await() }.getOrNull() } else null

            val isCreate = existing == null || !existing.exists()
            val task = if (isCreate) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "lastDeviceModel" to deviceModel,
                    "lastAndroidRelease" to androidRelease,
                    "lastBuildType" to buildType,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload)
            } else {
                val updatePayload = mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "lastDeviceModel" to deviceModel,
                    "lastAndroidRelease" to androidRelease,
                    "lastBuildType" to buildType,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updatePayload)
            }

            dispatchWrite(
                task = task,
                operationType = if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                path = docPath,
                isOnline = isOnline
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun saveDeviceSettings(
        userId: String,
        updated: DeviceSettings,
        existsInFirestore: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/settings/$DEFAULT_SETTINGS_DOC_ID"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = settingsDocRef(uid)
            docPath = docRef.path

            val safeEnv = updated.defaultTestEnvironment.trim().ifBlank { "sdk_gphone_api34" }.take(40)
            val safeCacheMb = updated.offlineCacheSizeMb.coerceIn(50L, 500L)
            val nextCounter = (updated.syncCounter + 1L).coerceAtLeast(1L)

            if (!existsInFirestore && updated.createdAt == null) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "themeMode" to updated.resolvedThemeMode.name,
                    "dynamicColorEnabled" to updated.dynamicColorEnabled,
                    "compactDensityEnabled" to updated.compactDensityEnabled,
                    "hapticsEnabled" to updated.hapticsEnabled,
                    "autoSyncEnabled" to updated.autoSyncEnabled,
                    "syncOnMeteredEnabled" to updated.syncOnMeteredEnabled,
                    "verboseDebugOverlayEnabled" to updated.verboseDebugOverlayEnabled,
                    "keepScreenAwakeInDebug" to updated.keepScreenAwakeInDebug,
                    "defaultTestEnvironment" to safeEnv,
                    "offlineCacheSizeMb" to safeCacheMb,
                    "syncCounter" to nextCounter,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dispatchWrite(docRef.set(createPayload), OperationType.CREATE, docPath, isOnline)
            } else {
                val updatePayload = mapOf(
                    "themeMode" to updated.resolvedThemeMode.name,
                    "dynamicColorEnabled" to updated.dynamicColorEnabled,
                    "compactDensityEnabled" to updated.compactDensityEnabled,
                    "hapticsEnabled" to updated.hapticsEnabled,
                    "autoSyncEnabled" to updated.autoSyncEnabled,
                    "syncOnMeteredEnabled" to updated.syncOnMeteredEnabled,
                    "verboseDebugOverlayEnabled" to updated.verboseDebugOverlayEnabled,
                    "keepScreenAwakeInDebug" to updated.keepScreenAwakeInDebug,
                    "defaultTestEnvironment" to safeEnv,
                    "offlineCacheSizeMb" to safeCacheMb,
                    "syncCounter" to nextCounter,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dispatchWrite(docRef.update(updatePayload), OperationType.UPDATE, docPath, isOnline)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun addVaultItem(
        userId: String,
        title: String,
        subtitle: String,
        notes: String,
        category: VaultCategory,
        sizeLabel: String,
        pinnedOffline: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/vault_items"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty" }

            val docRef = vaultItemsCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "subtitle" to subtitle.trim().take(160),
                "notes" to notes.trim().take(1500),
                "category" to category.name,
                "sizeLabel" to sizeLabel.trim().take(40),
                "pinnedOffline" to pinnedOffline,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            dispatchWrite(docRef.set(payload), OperationType.CREATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun updateVaultItem(
        userId: String,
        itemId: String,
        title: String,
        subtitle: String,
        notes: String,
        category: VaultCategory,
        sizeLabel: String,
        pinnedOffline: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/vault_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Vault item ID required" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty" }

            val docRef = vaultItemsCollection(uid).document(itemId)
            docPath = docRef.path
            val payload = mapOf(
                "title" to safeTitle,
                "subtitle" to subtitle.trim().take(160),
                "notes" to notes.trim().take(1500),
                "category" to category.name,
                "sizeLabel" to sizeLabel.trim().take(40),
                "pinnedOffline" to pinnedOffline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            dispatchWrite(docRef.update(payload), OperationType.UPDATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun toggleVaultPinnedOffline(
        userId: String,
        itemId: String,
        pinnedOffline: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/vault_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Vault item ID required" }
            val docRef = vaultItemsCollection(uid).document(itemId)
            docPath = docRef.path
            dispatchWrite(
                docRef.update(
                    mapOf(
                        "pinnedOffline" to pinnedOffline,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                docPath,
                isOnline
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun deleteVaultItem(
        userId: String,
        itemId: String,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/vault_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Vault item ID required" }
            val docRef = vaultItemsCollection(uid).document(itemId)
            docPath = docRef.path
            dispatchWrite(docRef.delete(), OperationType.DELETE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun addTestItem(
        userId: String,
        title: String,
        notes: String,
        category: TestCategory,
        telemetry: DeviceTelemetrySnapshot,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/test_items"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty" }
            val safeNotes = notes.trim().take(1000)
            val safeDeviceTag = telemetry.model.take(80).ifBlank { "Android" }
            val safeSdk = telemetry.sdkInt.coerceIn(1, 100).toLong()

            val docRef = testItemsCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "notes" to safeNotes,
                "category" to category.name,
                "completed" to false,
                "deviceTag" to safeDeviceTag,
                "sdkInt" to safeSdk,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            dispatchWrite(docRef.set(payload), OperationType.CREATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun updateTestItem(
        userId: String,
        itemId: String,
        title: String,
        notes: String,
        category: TestCategory,
        completed: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/test_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Item ID required" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty" }
            val safeNotes = notes.trim().take(1000)

            val docRef = testItemsCollection(uid).document(itemId)
            docPath = docRef.path
            val payload = mapOf(
                "title" to safeTitle,
                "notes" to safeNotes,
                "category" to category.name,
                "completed" to completed,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            dispatchWrite(docRef.update(payload), OperationType.UPDATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun toggleTestItem(
        userId: String,
        itemId: String,
        completed: Boolean,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/test_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Item ID required" }
            val docRef = testItemsCollection(uid).document(itemId)
            docPath = docRef.path
            dispatchWrite(
                docRef.update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                docPath,
                isOnline
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun deleteTestItem(
        userId: String,
        itemId: String,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/test_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(itemId.isNotBlank()) { "Item ID required" }
            val docRef = testItemsCollection(uid).document(itemId)
            docPath = docRef.path
            dispatchWrite(docRef.delete(), OperationType.DELETE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun appendDebugLog(
        userId: String,
        eventType: DebugEventType,
        summary: String,
        details: String,
        networkState: String,
        telemetry: DeviceTelemetrySnapshot,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/debug_logs"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val safeSummary = summary.trim().take(240).ifBlank { eventType.label }
            val safeDetails = details.trim().take(1000)
            val safeNetwork = networkState.trim().take(40).ifBlank { "UNKNOWN" }
            val safeModel = telemetry.model.take(80).ifBlank { "Android" }
            val safeRelease = telemetry.androidRelease.take(40).ifBlank { "Android 14" }

            val docRef = debugLogsCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "eventType" to eventType.name,
                "summary" to safeSummary,
                "details" to safeDetails,
                "networkState" to safeNetwork,
                "deviceModel" to safeModel,
                "androidVersion" to safeRelease,
                "createdAt" to FieldValue.serverTimestamp()
            )
            dispatchWrite(docRef.set(payload), OperationType.CREATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun deleteDebugLog(
        userId: String,
        logId: String,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId/debug_logs/$logId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(logId.isNotBlank()) { "Log ID required" }
            val docRef = debugLogsCollection(uid).document(logId)
            docPath = docRef.path
            dispatchWrite(docRef.delete(), OperationType.DELETE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Non-blocking local-first write dispatch:
     * Firestore immediately commits to its persistent local cache and emits via snapshots(),
     * eliminating multi-second UI stalls while still logging any deferred backend failures.
     */
    private fun dispatchWrite(
        task: Task<Void>,
        operationType: OperationType,
        path: String,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        task.addOnFailureListener { error ->
            handleFirestoreError(error, operationType, path)
        }
        return Result.success(
            if (isOnline) WriteSyncResult.SYNCED_CLOUD else WriteSyncResult.QUEUED_OFFLINE
        )
    }

    companion object {
        private const val TAG = "DeviceSyncRepository"
        const val DEFAULT_SETTINGS_DOC_ID = "device_preferences"

        private fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val databaseId = context.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(databaseId)
            try {
                instance.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(
                        persistentCacheSettings {
                            setSizeBytes(250L * 1024L * 1024L)
                        }
                    )
                }
            } catch (_: IllegalStateException) {
                // Firestore settings were already applied on this singleton instance.
            }
            return instance
        }
    }
}
