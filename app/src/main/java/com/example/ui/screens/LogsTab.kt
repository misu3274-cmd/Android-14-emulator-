package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DebugEventType
import com.example.data.model.DebugLogEntry
import com.example.data.model.SyncedCollection
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncCyan
import com.example.ui.viewmodel.UiState

@Composable
fun LogsTab(
    debugLogsState: UiState<SyncedCollection<DebugLogEntry>>,
    compact: Boolean,
    onAddLog: (eventType: DebugEventType, summary: String, details: String) -> Unit,
    onDeleteLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_log_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Record Debug Log")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (debugLogsState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = debugLogsState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is UiState.Success -> {
                    val logs = debugLogsState.data.items
                    if (logs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = "No debug logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No diagnostic breadcrumbs yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap + to log a custom test observation or run Snapshot & Verify from Overview.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                        ) {
                            items(
                                items = logs,
                                key = { it.id },
                                contentType = { "debug_log_entry" }
                            ) { entry ->
                                DebugLogCard(
                                    entry = entry,
                                    compact = compact,
                                    onDelete = { onDeleteLog(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDebugLogDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, summary, details ->
                onAddLog(type, summary, details)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun DebugLogCard(
    entry: DebugLogEntry,
    compact: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SyncCyan.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = entry.resolvedEventType.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFontFamily,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = entry.networkState,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.titleSmall
                )
                if (entry.details.isNotBlank()) {
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${entry.deviceModel} • ${entry.androidVersion} • ${formatTimestamp(entry.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_log_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete log entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDebugLogDialog(
    onDismiss: () -> Unit,
    onSave: (eventType: DebugEventType, summary: String, details: String) -> Unit
) {
    var summary by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(DebugEventType.CUSTOM_NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Debug Breadcrumb") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { if (it.length <= 240) summary = it },
                    label = { Text("Event summary") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("log_summary_input")
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 1000) details = it },
                    label = { Text("Payload / details (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("log_details_input")
                )
                Text("Event type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DebugEventType.entries.forEach { type ->
                        FilterChip(
                            selected = eventType == type,
                            onClick = { eventType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(eventType, summary.trim(), details.trim()) },
                enabled = summary.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_log_button")
            ) {
                Text("Save Log")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
