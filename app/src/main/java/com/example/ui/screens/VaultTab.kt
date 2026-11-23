package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncedCollection
import com.example.data.model.VaultCategory
import com.example.data.model.VaultItem
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncEmerald
import com.example.ui.viewmodel.UiState

@Composable
fun VaultTab(
    vaultItemsState: UiState<SyncedCollection<VaultItem>>,
    compact: Boolean,
    isSavingOffline: Boolean,
    onSaveAllOffline: () -> Unit,
    onImportDeviceFile: (Uri) -> Unit,
    onAddVaultItem: (title: String, subtitle: String, notes: String, category: VaultCategory, sizeLabel: String, pinnedOffline: Boolean) -> Unit,
    onUpdateVaultItem: (item: VaultItem, title: String, subtitle: String, notes: String, category: VaultCategory, sizeLabel: String, pinnedOffline: Boolean) -> Unit,
    onTogglePinnedOffline: (VaultItem) -> Unit,
    onDeleteVaultItem: (String) -> Unit,
    onSeedStarterVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<VaultItem?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onImportDeviceFile(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showEditorDialog = true
                },
                modifier = Modifier.testTag("add_vault_item_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Save Item for Offline Use")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSaveAllOffline,
                    enabled = !isSavingOffline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("vault_save_all_offline_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSavingOffline) "Caching…" else "Save Offline")
                }

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("vault_attach_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save File")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                        modifier = Modifier.testTag("vault_filter_all")
                    )
                }
                items(VaultCategory.entries, key = { it.name }) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text(category.label) },
                        modifier = Modifier.testTag("vault_filter_${category.name.lowercase()}")
                    )
                }
            }

            when (vaultItemsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = vaultItemsState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is UiState.Success -> {
                    val allItems = vaultItemsState.data.items
                    val filtered = remember(allItems, selectedCategory) {
                        if (selectedCategory == null) allItems
                        else allItems.filter { it.resolvedCategory == selectedCategory }
                    }
                    val hasPendingWrites = vaultItemsState.data.metadata.hasPendingWrites

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderSpecial,
                                    contentDescription = "Empty Offline Vault",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (allItems.isEmpty()) {
                                        "Offline Vault is empty"
                                    } else {
                                        "No saved ${selectedCategory?.label}"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Save accounts, apps, games, files, and custom items for instant offline access and cloud sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (allItems.isEmpty()) {
                                    Button(
                                        onClick = onSeedStarterVault,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("seed_vault_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Load Starter Offline Vault")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                        ) {
                            items(
                                items = filtered,
                                key = { it.id },
                                contentType = { "vault_item" }
                            ) { item ->
                                VaultItemCard(
                                    item = item,
                                    hasPendingWrites = hasPendingWrites,
                                    compact = compact,
                                    onTogglePinned = { onTogglePinnedOffline(item) },
                                    onEdit = {
                                        editingItem = item
                                        showEditorDialog = true
                                    },
                                    onDelete = { onDeleteVaultItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        VaultItemEditorDialog(
            initialItem = editingItem,
            defaultCategory = selectedCategory ?: VaultCategory.APP,
            onDismiss = { showEditorDialog = false },
            onSave = { title, subtitle, notes, category, sizeLabel, pinnedOffline ->
                val current = editingItem
                if (current == null) {
                    onAddVaultItem(title, subtitle, notes, category, sizeLabel, pinnedOffline)
                } else {
                    onUpdateVaultItem(current, title, subtitle, notes, category, sizeLabel, pinnedOffline)
                }
                showEditorDialog = false
            }
        )
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    hasPendingWrites: Boolean,
    compact: Boolean,
    onTogglePinned: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryIcon = remember(item.category) { iconForCategory(item.resolvedCategory) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = item.resolvedCategory.label,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = item.resolvedCategory.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (item.sizeLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.sizeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (item.pinnedOffline) "Offline Saved" else "Cloud",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = if (hasPendingWrites) SyncAmber else SyncEmerald
                    )
                }
            }

            IconButton(
                onClick = onTogglePinned,
                modifier = Modifier.testTag("vault_pin_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.pinnedOffline) Icons.Filled.OfflinePin else Icons.Outlined.OfflinePin,
                    contentDescription = if (item.pinnedOffline) "Saved offline" else "Pin for offline",
                    tint = if (item.pinnedOffline) SyncEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("vault_edit_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit vault item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("vault_delete_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete vault item",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun iconForCategory(category: VaultCategory): ImageVector = when (category) {
    VaultCategory.ACCOUNT -> Icons.Filled.Person
    VaultCategory.APP -> Icons.Filled.Apps
    VaultCategory.GAME -> Icons.Filled.SportsEsports
    VaultCategory.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
    VaultCategory.OTHER -> Icons.Filled.Inventory2
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultItemEditorDialog(
    initialItem: VaultItem?,
    defaultCategory: VaultCategory,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, notes: String, category: VaultCategory, sizeLabel: String, pinnedOffline: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title.orEmpty()) }
    var subtitle by remember { mutableStateOf(initialItem?.subtitle.orEmpty()) }
    var notes by remember { mutableStateOf(initialItem?.notes.orEmpty()) }
    var sizeLabel by remember { mutableStateOf(initialItem?.sizeLabel.orEmpty()) }
    var category by remember { mutableStateOf(initialItem?.resolvedCategory ?: defaultCategory) }
    var pinnedOffline by remember { mutableStateOf(initialItem?.pinnedOffline ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialItem == null) "Save Item for Offline Use" else "Edit Offline Item")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Name / Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_title_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { if (it.length <= 160) subtitle = it },
                    label = {
                        Text(
                            when (category) {
                                VaultCategory.ACCOUNT -> "Account handle / server"
                                VaultCategory.APP -> "Package name / version"
                                VaultCategory.GAME -> "Game ID / save slot"
                                VaultCategory.FILE -> "File type / path"
                                VaultCategory.OTHER -> "Tag / reference"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_subtitle_input")
                )
                OutlinedTextField(
                    value = sizeLabel,
                    onValueChange = { if (it.length <= 40) sizeLabel = it },
                    label = { Text("Size / Level / Badge (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_size_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 1500) notes = it },
                    label = { Text("Offline notes / progress details") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_notes_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pin for Offline Access", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = pinnedOffline,
                        onCheckedChange = { pinnedOffline = it },
                        modifier = Modifier.testTag("vault_pin_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.trim(),
                        subtitle.trim(),
                        notes.trim(),
                        category,
                        sizeLabel.trim(),
                        pinnedOffline
                    )
                },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_vault_item_button")
            ) {
                Text("Save Offline")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
