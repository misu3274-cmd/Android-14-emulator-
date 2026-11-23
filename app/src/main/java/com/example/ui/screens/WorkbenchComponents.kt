package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncEmerald
import com.example.ui.theme.SyncRose
import com.example.ui.viewmodel.CloudBannerStatus
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncStatusBar(
    status: CloudBannerStatus,
    compact: Boolean,
    showVerboseBadges: Boolean,
    onToggleSimulatedOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            !status.isEffectivelyOnline -> SyncAmber
            status.hasPendingWrites -> SyncAmber
            status.isFromCache -> SyncAmber
            else -> SyncEmerald
        },
        label = "syncDotColor"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            !status.isEffectivelyOnline -> SyncAmber.copy(alpha = 0.14f)
            status.hasPendingWrites -> SyncAmber.copy(alpha = 0.14f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        label = "syncBannerColor"
    )

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
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
                        Text(
                            text = "${status.transportLabel} • cache=${status.isFromCache} • pending=${status.hasPendingWrites}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

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
    if (timestamp == null) return "Pending…"
    val formatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    return formatter.format(timestamp.toDate())
}

fun formatEpochMillis(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Unavailable"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
