package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DeviceSettings
import com.example.data.model.SyncedDocument
import com.example.data.model.ThemeMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(
    settingsState: UiState<SyncedDocument<DeviceSettings>>,
    compact: Boolean,
    onUpdateSettings: ((DeviceSettings) -> DeviceSettings) -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (settingsState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = settingsState.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        is UiState.Success -> {
            val settings = settingsState.data.data ?: DeviceSettings()
            val meta = settingsState.data.metadata
            var cacheSlider by remember(settings.offlineCacheSizeMb) {
                mutableFloatStateOf(settings.offlineCacheSizeMb.toFloat())
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                // Appearance & Phone Display Settings (Auto-saved to cloud)
                item {
                    SettingsSectionCard(
                        icon = Icons.Filled.Palette,
                        title = "Display & Phone Preferences",
                        subtitle = "Changes save automatically to Cloud Firestore (${settings.syncCounter} syncs)",
                        compact = compact
                    ) {
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.resolvedThemeMode == mode,
                                    onClick = {
                                        onUpdateSettings { it.copy(themeMode = mode.name) }
                                    },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        SettingToggleRow(
                            title = "Material You Dynamic Color",
                            subtitle = "Adapt app accents to Android 12+ wallpaper colors",
                            checked = settings.dynamicColorEnabled,
                            testTag = "switch_dynamic_color",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(dynamicColorEnabled = enabled) }
                            }
                        )
                        SettingToggleRow(
                            title = "Compact UI Density",
                            subtitle = "Fit more telemetry rows on screen",
                            checked = settings.compactDensityEnabled,
                            testTag = "switch_compact_density",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(compactDensityEnabled = enabled) }
                            }
                        )
                        SettingToggleRow(
                            title = "Tactile Haptics",
                            subtitle = "Vibrate subtly when toggling checkpoints and settings",
                            checked = settings.hapticsEnabled,
                            testTag = "switch_haptics",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(hapticsEnabled = enabled) }
                            }
                        )
                    }
                }

                // Cloud Sync & Offline Cache Settings
                item {
                    SettingsSectionCard(
                        icon = Icons.Filled.CloudSync,
                        title = "Cloud Sync & Offline Cache",
                        subtitle = if (meta.hasPendingWrites) {
                            "Pending local changes queued for automatic upload"
                        } else {
                            "Synced • Updated ${formatTimestamp(settings.updatedAt)}"
                        },
                        compact = compact
                    ) {
                        SettingToggleRow(
                            title = "Automatic Cloud Backup",
                            subtitle = "Persist every change immediately to your Google account",
                            checked = settings.autoSyncEnabled,
                            testTag = "switch_auto_sync",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(autoSyncEnabled = enabled) }
                            }
                        )
                        SettingToggleRow(
                            title = "Sync on Metered Networks",
                            subtitle = "Allow Firestore sync over cellular data connections",
                            checked = settings.syncOnMeteredEnabled,
                            testTag = "switch_sync_metered",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(syncOnMeteredEnabled = enabled) }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Offline Cache Budget", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${cacheSlider.roundToLong()} MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = cacheSlider,
                            onValueChange = { cacheSlider = it },
                            onValueChangeFinished = {
                                val target = cacheSlider.roundToLong().coerceIn(50L, 500L)
                                onUpdateSettings { it.copy(offlineCacheSizeMb = target) }
                            },
                            valueRange = 50f..500f,
                            steps = 8,
                            modifier = Modifier.testTag("offline_cache_slider")
                        )
                    }
                }

                // Developer & Testing Workbench Settings
                item {
                    SettingsSectionCard(
                        icon = Icons.Filled.Tune,
                        title = "Developer & SDK Debug Options",
                        subtitle = "Settings follow your Google account across test devices",
                        compact = compact
                    ) {
                        SettingToggleRow(
                            title = "Verbose Telemetry Banner",
                            subtitle = "Show live cache, transport, and pending-write flags",
                            checked = settings.verboseDebugOverlayEnabled,
                            testTag = "switch_verbose_overlay",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(verboseDebugOverlayEnabled = enabled) }
                            }
                        )
                        SettingToggleRow(
                            title = "Keep Screen Awake",
                            subtitle = "Prevent display sleep while inspecting logs on SDK GPhone",
                            checked = settings.keepScreenAwakeInDebug,
                            testTag = "switch_keep_awake",
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(keepScreenAwakeInDebug = enabled) }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Text("Default Test Target", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("sdk_gphone_api34", "staging", "production").forEach { env ->
                                FilterChip(
                                    selected = settings.defaultTestEnvironment == env,
                                    onClick = {
                                        onUpdateSettings { it.copy(defaultTestEnvironment = env) }
                                    },
                                    label = {
                                        Text(
                                            text = env,
                                            fontFamily = MonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("env_chip_$env")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = onResetSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Preferences to Defaults")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    compact: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
