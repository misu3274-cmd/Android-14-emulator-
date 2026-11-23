package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.DeviceSettings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.DeviceSyncViewModel
import com.example.ui.viewmodel.UiState

const val ROUTE_OVERVIEW = "overview"
const val ROUTE_VAULT = "vault"
const val ROUTE_PROGRESS = "progress"
const val ROUTE_LOGS = "logs"
const val ROUTE_SETTINGS = "settings"

private data class WorkbenchNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    WorkbenchNavItem(
        route = ROUTE_OVERVIEW,
        labelRes = R.string.tab_overview,
        selectedIcon = Icons.Filled.DeveloperBoard,
        unselectedIcon = Icons.Outlined.DeveloperBoard,
        testTag = "nav_overview"
    ),
    WorkbenchNavItem(
        route = ROUTE_VAULT,
        labelRes = R.string.tab_vault,
        selectedIcon = Icons.Filled.FolderSpecial,
        unselectedIcon = Icons.Outlined.FolderSpecial,
        testTag = "nav_vault"
    ),
    WorkbenchNavItem(
        route = ROUTE_PROGRESS,
        labelRes = R.string.tab_progress,
        selectedIcon = Icons.Filled.Checklist,
        unselectedIcon = Icons.Outlined.Checklist,
        testTag = "nav_progress"
    ),
    WorkbenchNavItem(
        route = ROUTE_LOGS,
        labelRes = R.string.tab_logs,
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_logs"
    ),
    WorkbenchNavItem(
        route = ROUTE_SETTINGS,
        labelRes = R.string.tab_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchApp(
    viewModel: DeviceSyncViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val authDebugInfo by viewModel.authDebugInfo.collectAsStateWithLifecycle()
    val cloudStatus by viewModel.cloudBannerStatus.collectAsStateWithLifecycle()
    val isSavingOffline by viewModel.isSavingOffline.collectAsStateWithLifecycle()
    val offlineSaveSummary by viewModel.offlineSaveSummary.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val vaultItemsState by viewModel.vaultItemsState.collectAsStateWithLifecycle()
    val testItemsState by viewModel.testItemsState.collectAsStateWithLifecycle()
    val debugLogsState by viewModel.debugLogsState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val settings = (settingsState as? UiState.Success)?.data?.data ?: DeviceSettings()
    val compact = settings.compactDensityEnabled

    fun triggerHaptic() {
        if (settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    DisposableEffect(settings.keepScreenAwakeInDebug, context) {
        val activity = context as? Activity
        if (settings.keepScreenAwakeInDebug) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.clearStatusBannerMessage()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_OVERVIEW

    val navigateTo = remember(navController, settings.hapticsEnabled) {
        { route: String ->
            if (settings.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${telemetry.model} • API ${telemetry.sdkInt} (${telemetry.buildType})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("${item.testTag}_rail")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .padding(horizontal = if (compact) 12.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CloudSyncStatusBar(
                            status = cloudStatus,
                            compact = compact,
                            showVerboseBadges = settings.verboseDebugOverlayEnabled,
                            isSavingOffline = isSavingOffline,
                            offlineSaveSummary = offlineSaveSummary,
                            onSaveForOffline = {
                                triggerHaptic()
                                viewModel.saveForOfflineUse()
                            },
                            onToggleSimulatedOffline = {
                                triggerHaptic()
                                viewModel.toggleSimulatedOffline()
                            }
                        )

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_OVERVIEW,
                            modifier = Modifier.weight(1f),
                            enterTransition = { fadeIn(animationSpec = tween(110)) },
                            exitTransition = { fadeOut(animationSpec = tween(80)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(110)) },
                            popExitTransition = { fadeOut(animationSpec = tween(80)) }
                        ) {
                            composable(ROUTE_OVERVIEW) {
                                OverviewTab(
                                    telemetry = telemetry,
                                    authDebugInfo = authDebugInfo,
                                    cloudStatus = cloudStatus,
                                    profileState = profileState,
                                    settingsState = settingsState,
                                    testItemsState = testItemsState,
                                    vaultItemsState = vaultItemsState,
                                    compact = compact,
                                    onRefreshAndCaptureSnapshot = {
                                        triggerHaptic()
                                        viewModel.refreshDeviceAndAuthTelemetry(forceRefreshToken = true)
                                    },
                                    onExportReport = {
                                        triggerHaptic()
                                        val report = viewModel.buildDiagnosticReportText()
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "DeviceSync Diagnostic Report (${telemetry.model})")
                                            putExtra(Intent.EXTRA_TEXT, report)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Export Diagnostic Report")
                                        )
                                    },
                                    onSignOut = {
                                        triggerHaptic()
                                        onSignOut()
                                    },
                                    onNavigateToProgress = { navigateTo(ROUTE_PROGRESS) },
                                    onNavigateToVault = { navigateTo(ROUTE_VAULT) }
                                )
                            }
                            composable(ROUTE_VAULT) {
                                VaultTab(
                                    vaultItemsState = vaultItemsState,
                                    compact = compact,
                                    isSavingOffline = isSavingOffline,
                                    onSaveAllOffline = {
                                        triggerHaptic()
                                        viewModel.saveForOfflineUse()
                                    },
                                    onImportDeviceFile = { uri ->
                                        triggerHaptic()
                                        viewModel.importDocumentIntoVault(context, uri)
                                    },
                                    onAddVaultItem = { title, subtitle, notes, category, sizeLabel, pinned ->
                                        triggerHaptic()
                                        viewModel.addVaultItem(title, subtitle, notes, category, sizeLabel, pinned)
                                    },
                                    onUpdateVaultItem = { item, title, subtitle, notes, category, sizeLabel, pinned ->
                                        triggerHaptic()
                                        viewModel.updateVaultItem(item.id, title, subtitle, notes, category, sizeLabel, pinned)
                                    },
                                    onTogglePinnedOffline = { item ->
                                        triggerHaptic()
                                        viewModel.toggleVaultPinnedOffline(item)
                                    },
                                    onDeleteVaultItem = { itemId ->
                                        triggerHaptic()
                                        viewModel.deleteVaultItem(itemId)
                                    },
                                    onSeedStarterVault = {
                                        triggerHaptic()
                                        viewModel.seedStarterVaultItems()
                                    }
                                )
                            }
                            composable(ROUTE_PROGRESS) {
                                ProgressTab(
                                    testItemsState = testItemsState,
                                    compact = compact,
                                    onAddItem = { title, notes, category ->
                                        triggerHaptic()
                                        viewModel.addTestItem(title, notes, category)
                                    },
                                    onUpdateItem = { item, title, notes, category ->
                                        triggerHaptic()
                                        viewModel.updateTestItem(item.id, title, notes, category, item.completed)
                                    },
                                    onToggleItem = { item ->
                                        triggerHaptic()
                                        viewModel.toggleTestItem(item)
                                    },
                                    onDeleteItem = { itemId ->
                                        triggerHaptic()
                                        viewModel.deleteTestItem(itemId)
                                    },
                                    onSeedStarterSuite = {
                                        triggerHaptic()
                                        viewModel.seedStandardQaCheckpoints()
                                    }
                                )
                            }
                            composable(ROUTE_LOGS) {
                                LogsTab(
                                    debugLogsState = debugLogsState,
                                    compact = compact,
                                    onAddLog = { type, summary, details ->
                                        triggerHaptic()
                                        viewModel.appendCustomDebugLog(type, summary, details)
                                    },
                                    onDeleteLog = { logId ->
                                        triggerHaptic()
                                        viewModel.deleteDebugLog(logId)
                                    }
                                )
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsTab(
                                    settingsState = settingsState,
                                    compact = compact,
                                    onUpdateSettings = { transform ->
                                        triggerHaptic()
                                        viewModel.updateSettings(transform)
                                    },
                                    onResetSettings = {
                                        triggerHaptic()
                                        viewModel.resetSettingsToDefaults()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
