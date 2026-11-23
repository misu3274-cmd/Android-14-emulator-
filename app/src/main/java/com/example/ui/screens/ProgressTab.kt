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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncedCollection
import com.example.data.model.TestCategory
import com.example.data.model.TestProgressItem
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncEmerald
import com.example.ui.viewmodel.UiState

@Composable
fun ProgressTab(
    testItemsState: UiState<SyncedCollection<TestProgressItem>>,
    compact: Boolean,
    onAddItem: (title: String, notes: String, category: TestCategory) -> Unit,
    onUpdateItem: (item: TestProgressItem, title: String, notes: String, category: TestCategory) -> Unit,
    onToggleItem: (TestProgressItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onSeedStarterSuite: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<TestCategory?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TestProgressItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showEditorDialog = true
                },
                modifier = Modifier.testTag("add_checkpoint_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add QA Checkpoint")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                        modifier = Modifier.testTag("filter_category_all")
                    )
                }
                items(TestCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text(category.label) },
                        modifier = Modifier.testTag("filter_category_${category.name.lowercase()}")
                    )
                }
            }

            when (testItemsState) {
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
                            text = testItemsState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is UiState.Success -> {
                    val allItems = testItemsState.data.items
                    val filtered = remember(allItems, selectedCategory) {
                        if (selectedCategory == null) {
                            allItems
                        } else {
                            allItems.filter { it.resolvedCategory == selectedCategory }
                        }
                    }
                    val meta = testItemsState.data.metadata

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
                                    imageVector = Icons.Filled.Checklist,
                                    contentDescription = "Empty Checkpoints",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (allItems.isEmpty()) {
                                        "No saved test checkpoints yet"
                                    } else {
                                        "No items in ${selectedCategory?.label}"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Checkpoints and test notes auto-save to Cloud Firestore and stay editable offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (allItems.isEmpty()) {
                                    Button(
                                        onClick = onSeedStarterSuite,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("seed_qa_suite_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Load Android 14 QA Suite")
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
                                contentType = { "test_progress_item" }
                            ) { item ->
                                TestProgressCard(
                                    item = item,
                                    hasPendingWrites = meta.hasPendingWrites,
                                    compact = compact,
                                    onToggle = { onToggleItem(item) },
                                    onEdit = {
                                        editingItem = item
                                        showEditorDialog = true
                                    },
                                    onDelete = { onDeleteItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        TestItemEditorDialog(
            initialItem = editingItem,
            onDismiss = { showEditorDialog = false },
            onSave = { title, notes, category ->
                val current = editingItem
                if (current == null) {
                    onAddItem(title, notes, category)
                } else {
                    onUpdateItem(current, title, notes, category)
                }
                showEditorDialog = false
            }
        )
    }
}

@Composable
private fun TestProgressCard(
    item: TestProgressItem,
    hasPendingWrites: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkpoint_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = item.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("checkpoint_checkbox_${item.id}")
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
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
                    Text(
                        text = "${item.deviceTag} • API ${item.sdkInt} • ${formatTimestamp(item.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = if (hasPendingWrites) SyncAmber else SyncEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("checkpoint_edit_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit checkpoint",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("checkpoint_delete_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete checkpoint",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestItemEditorDialog(
    initialItem: TestProgressItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, category: TestCategory) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initialItem?.notes.orEmpty()) }
    var category by remember { mutableStateOf(initialItem?.resolvedCategory ?: TestCategory.GENERAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialItem == null) "New QA Checkpoint" else "Edit Checkpoint")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Checkpoint title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkpoint_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 1000) notes = it },
                    label = { Text("Verification notes (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkpoint_notes_input")
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TestCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), notes.trim(), category) },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_checkpoint_button")
            ) {
                Text("Save to Cloud")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
