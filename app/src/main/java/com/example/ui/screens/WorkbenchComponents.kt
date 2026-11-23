package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OfflineSaveSummary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncEmerald
import com.example.ui.theme.SyncRose
import com.example.ui.viewmodel.CloudBannerStatus
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timestampFormatLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
}

private val epochFormatLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

@Composable
fun CloudSyncStatusBar(
    status: CloudBannerStatus,
    compact: Boolean,
    showVerboseBadges: Boolean,
    isSavingOffline: Boolean,
    offlineSaveSummary: OfflineSaveSummary?,
    onSaveForOffline: () -> Unit,
    onToggleSimulatedOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        !status.isEffectivelyOnline -> SyncAmber
        status.hasPendingWrites -> SyncAmber
        status.isFromCache -> SyncAmber
        else -> SyncEmerald
    }

    val containerColor = when {
        !status.isEffectivelyOnline -> SyncAmber.copy(alpha = 0.14f)
        status.hasPendingWrites -> SyncAmber.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    }

    val statusIcon = when {
        !status.isDeviceOnline -> Icons.Filled.WifiOff
        status.isSimulatedOffline -> Icons.Filled.CloudOff
        status.hasPendingWrites -> Icons.Filled.CloudQueue
        else -> Icons.Filled.CloudDone
    }

    val statusText = when {
        !status.isDeviceOnline -> "Offline • Local Cache Active"
        status.isSimulatedOffline -> "Simulated Offline • Queuing Locally"
        status.hasPendingWrites -> "Syncing Pending Changes…"
        status.isFromCache -> "Loaded from Offline Cache"
        else -> "Cloud Synced"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = dotColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusText,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showVerboseBadges) {
                            val savedNote = offlineSaveSummary?.let {
                                " • Saved ${it.totalCachedItems} offline @ ${it.savedAtLabel}"
                            }.orEmpty()
                            Text(
                                text = "${status.transportLabel} • cache=${status.isFromCache}$savedNote",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onSaveForOffline,
                    enabled = !isSavingOffline,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_offline_bar_button"),
                    label = {
                        Text(
                            text = when {
                                isSavingOffline -> "Saving Offline…"
                                offlineSaveSummary != null -> "Saved Offline (${offlineSaveSummary.totalCachedItems})"
                                else -> "Save Offline"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        if (isSavingOffline) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (offlineSaveSummary != null) {
                                    Icons.Filled.DownloadDone
                                } else {
                                    Icons.Filled.SaveAlt
                                },
                                contentDescription = "Save all data for offline use",
                                tint = if (offlineSaveSummary != null) SyncEmerald else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                AssistChip(
                    onClick = onToggleSimulatedOffline,
                    modifier = Modifier.testTag("toggle_simulated_offline_chip"),
                    label = {
                        Text(
                            text = if (status.isSimulatedOffline) "Go Online" else "Test Offline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (status.isSimulatedOffline) Icons.Filled.Sync else Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (status.isSimulatedOffline) {
                            SyncRose.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    monospaceValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = if (monospaceValue) {
                MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily)
            } else {
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Cached…"
    return timestampFormatLocal.get()?.format(timestamp.toDate()) ?: "Cached"
}

fun formatEpochMillis(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Unavailable"
    return epochFormatLocal.get()?.format(Date(millis)) ?: "Unavailable"
}
