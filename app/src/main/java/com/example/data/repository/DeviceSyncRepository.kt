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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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

    suspend fun setNetworkEnabled(enabled: Boolean): Result<Unit> {
        return try {
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
    }

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
    }

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
    }

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
    }

    suspend fun ensureBootstrapData(
        userId: String,
        telemetry: DeviceTelemetrySnapshot,
        isOnline: Boolean
    ): Result<WriteSyncResult> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid != userId) return Result.failure(IllegalStateException("User changed during bootstrap"))

        val profileResult = syncUserProfile(userId, telemetry, isOnline)
        if (profileResult.isFailure) return profileResult

        val settingsRef = settingsDocRef(uid)
        return try {
            val cached = runCatching { settingsRef.get(Source.CACHE).await() }.getOrNull()
            val existing = cached?.takeIf { it.exists() }
                ?: withTimeoutOrNull(2000L) { runCatching { settingsRef.get().await() }.getOrNull() }

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
                    "offlineCacheSizeMb" to 200L,
                    "syncCounter" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitOfflineAware(
                    task = settingsRef.set(payload),
                    operationType = OperationType.CREATE,
                    path = settingsRef.path,
                    expectOnline = isOnline
                )
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
    ): Result<WriteSyncResult> {
        var docPath = "users/$userId"
        return try {
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
                ?: withTimeoutOrNull(2000L) { runCatching { docRef.get().await() }.getOrNull() }

            val task = if (existing == null || !existing.exists()) {
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

            awaitOfflineAware(
                task = task,
                operationType = if (existing == null || !existing.exists()) OperationType.CREATE else OperationType.UPDATE,
                path = docPath,
                expectOnline = isOnline
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDeviceSettings(
        userId: String,
        updated: DeviceSettings,
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

            val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            val existsLocally = cached?.exists() == true || updated.createdAt != null

            if (!existsLocally) {
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
                awaitOfflineAware(docRef.set(createPayload), OperationType.CREATE, docPath, isOnline)
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
                awaitOfflineAware(docRef.update(updatePayload), OperationType.UPDATE, docPath, isOnline)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addTestItem(
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
            awaitOfflineAware(docRef.set(payload), OperationType.CREATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTestItem(
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
            awaitOfflineAware(docRef.update(payload), OperationType.UPDATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTestItem(
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
            awaitOfflineAware(
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

    suspend fun deleteTestItem(
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
            awaitOfflineAware(docRef.delete(), OperationType.DELETE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendDebugLog(
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
            awaitOfflineAware(docRef.set(payload), OperationType.CREATE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDebugLog(
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
            awaitOfflineAware(docRef.delete(), OperationType.DELETE, docPath, isOnline)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun awaitOfflineAware(
        task: com.google.android.gms.tasks.Task<Void>,
        operationType: OperationType,
        path: String,
        expectOnline: Boolean
    ): Result<WriteSyncResult> {
        task.addOnFailureListener { error ->
            handleFirestoreError(error, operationType, path)
        }
        if (!expectOnline) {
            return Result.success(WriteSyncResult.QUEUED_OFFLINE)
        }
        return try {
            val completed = withTimeoutOrNull(2200L) {
                task.await()
                true
            }
            if (completed == true) {
                Result.success(WriteSyncResult.SYNCED_CLOUD)
            } else {
                Result.success(WriteSyncResult.QUEUED_OFFLINE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            Result.failure(e)
        }
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
                            setSizeBytes(100L * 1024L * 1024L)
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
