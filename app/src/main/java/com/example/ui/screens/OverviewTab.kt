package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuthDebugInfo
import com.example.data.model.DeviceSettings
import com.example.data.model.DeviceTelemetrySnapshot
import com.example.data.model.SyncedCollection
import com.example.data.model.SyncedDocument
import com.example.data.model.TestProgressItem
import com.example.data.model.UserProfile
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncCyan
import com.example.ui.theme.SyncEmerald
import com.example.ui.viewmodel.CloudBannerStatus
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    telemetry: DeviceTelemetrySnapshot,
    authDebugInfo: AuthDebugInfo?,
    cloudStatus: CloudBannerStatus,
    profileState: UiState<SyncedDocument<UserProfile>>,
    settingsState: UiState<SyncedDocument<DeviceSettings>>,
    testItemsState: UiState<SyncedCollection<TestProgressItem>>,
    compact: Boolean,
    onRefreshAndCaptureSnapshot: () -> Unit,
    onExportReport: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemSpacing = if (compact) 10.dp else 14.dp
    val items = (testItemsState as? UiState.Success)?.data?.items.orEmpty()
    val completedCount = items.count { it.completed }
    val settings = (settingsState as? UiState.Success)?.data?.data
    val profile = (profileState as? UiState.Success)?.data?.data

    val usedRamMb = (telemetry.totalRamMb - telemetry.availRamMb).coerceAtLeast(0L)
    val ramRatio = if (telemetry.totalRamMb > 0) {
        (usedRamMb.toFloat() / telemetry.totalRamMb.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        // Hero Device & SDK GPhone Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                )
                            )
                        )
                        .padding(if (compact) 14.dp else 18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.PhoneAndroid,
                                            contentDescription = "Device Hardware",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = telemetry.model,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${telemetry.androidRelease} • ${telemetry.product}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = MonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DiagnosticPill(
                                label = if (telemetry.isAndroid14OrHigher) "Android 14+ Ready" else "API ${telemetry.sdkInt}",
                                color = SyncEmerald
                            )
                            DiagnosticPill(
                                label = if (telemetry.isUserDebugOrEng) "build: ${telemetry.buildType} (debuggable)" else "build: ${telemetry.buildType}",
                                color = if (telemetry.isUserDebugOrEng) SyncCyan else MaterialTheme.colorScheme.primary
                            )
                            if (telemetry.isEmulatorOrSdkGphone) {
                                DiagnosticPill(
                                    label = "SDK GPhone / Emulator",
                                    color = SyncAmber
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRefreshAndCaptureSnapshot,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("capture_snapshot_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Snapshot & Verify")
                            }
                            OutlinedButton(
                                onClick = onExportReport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Report")
                            }
                        }
                    }
                }
            }
        }

        // Cloud Sync & Progress Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    onClick = onNavigateToProgress,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overview_progress_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "QA Progress",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("QA Progress", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = if (items.isEmpty()) "0 tasks" else "$completedCount / ${items.size} done",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Auto-saved to Firestore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = "System Memory",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("RAM & Battery", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "${telemetry.availRamMb} MB free",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        LinearProgressIndicator(
                            progress = { ramRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = SyncEmerald
                            )
                            Text(
                                text = if (telemetry.batteryPercent >= 0) {
                                    "${telemetry.batteryPercent}%${if (telemetry.isCharging) " • Charging" else ""}"
                                } else "Battery N/A",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Google Account & User Debug Diagnostics Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Google Auth Diagnostics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Google Auth & User Debug",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = SyncEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "SIGNED IN",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                color = SyncEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    KeyValueRow("Account", authDebugInfo?.displayName ?: profile?.displayName ?: "Google Tester")
                    KeyValueRow("Email", authDebugInfo?.email ?: profile?.email ?: "Unavailable", monospaceValue = true)
                    KeyValueRow("Firebase UID", authDebugInfo?.uid ?: "Loading…", monospaceValue = true)
                    KeyValueRow(
                        "Providers",
                        authDebugInfo?.providers?.joinToString(", ")?.ifBlank { "google.com" } ?: "google.com",
                        monospaceValue = true
                    )
                    KeyValueRow(
                        "Email Verified",
                        if (authDebugInfo?.isEmailVerified == true) "true" else "false",
                        monospaceValue = true
                    )
                    KeyValueRow(
                        "ID Token Status",
                        authDebugInfo?.lastTokenRefreshStatus ?: "Checking…",
                        monospaceValue = true
                    )
                    KeyValueRow(
                        "Token Expires",
                        formatEpochMillis(authDebugInfo?.tokenExpirationMillis),
                        monospaceValue = true
                    )
                    KeyValueRow(
                        "Cloud Sync Counter",
                        "${settings?.syncCounter ?: 0} updates • Last profile sync: ${formatTimestamp(profile?.updatedAt)}",
                        monospaceValue = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshAndCaptureSnapshot,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("refresh_auth_token_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify Token")
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch / Sign Out")
                        }
                    }
                }
            }
        }

        // Detailed SDK Build & Environment Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = "Build Telemetry",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Android SDK & Device Inspector",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    KeyValueRow("Manufacturer / Brand", "${telemetry.manufacturer} / ${telemetry.brand}")
                    KeyValueRow("Device / Hardware", "${telemetry.device} (${telemetry.hardware})", monospaceValue = true)
                    KeyValueRow("Build Type / Tags", "${telemetry.buildType} / ${telemetry.buildTags}", monospaceValue = true)
                    KeyValueRow("Security Patch", telemetry.securityPatch, monospaceValue = true)
                    KeyValueRow("Supported ABIs", telemetry.supportedAbis, monospaceValue = true)
                    KeyValueRow("Display Metrics", telemetry.screenSummary, monospaceValue = true)
                    KeyValueRow("Network Transport", "${cloudStatus.transportLabel} (metered=${cloudStatus.isMetered})", monospaceValue = true)
                    KeyValueRow("Build Fingerprint", telemetry.fingerprint, monospaceValue = true)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticPill(
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
