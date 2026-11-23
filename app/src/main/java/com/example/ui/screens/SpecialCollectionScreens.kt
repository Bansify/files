package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SafeFolderRecord
import com.example.model.SortOption
import com.example.model.TrashRecord
import com.example.model.ViewMode
import com.example.model.formatFileSize
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListRow
import com.example.ui.components.SelectionBottomActionBar
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.SortMenuDropdown
import com.example.ui.components.categoryVisual
import com.example.ui.components.formatShortDate
import com.example.viewmodel.FileManagerUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowserScreen(
    category: FileCategory,
    state: FileManagerUiState,
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoreFile: (FileItem) -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelectedToSafe: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (state.isInSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedPaths.size,
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(category.displayName)
                            Text(
                                text = "${state.categoryItems.size} items • ${
                                    formatFileSize(state.categoryItems.sumOf { it.sizeBytes })
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("category_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleViewMode) {
                            Icon(
                                imageVector = if (state.settings.viewMode == ViewMode.LIST) Icons.Filled.GridView
                                else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Toggle view mode"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            SortMenuDropdown(
                                expanded = showSortMenu,
                                currentSort = state.settings.sortOption,
                                onDismiss = { showSortMenu = false },
                                onSelectSort = onSelectSort
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.isInSelectionMode) {
                SelectionBottomActionBar(
                    onCopy = onCopySelected,
                    onMove = onMoveSelected,
                    onCompress = onCompressSelected,
                    onShare = onShareSelected,
                    onMoveToSafe = onMoveSelectedToSafe,
                    onDelete = onDeleteSelected
                )
            }
        }
    ) { padding ->
        if (state.categoryItems.isEmpty()) {
            EmptyStateView(
                icon = categoryVisual(category).icon,
                title = "No ${category.displayName.lowercase()} found",
                subtitle = "Files matching this category will appear here automatically.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else if (state.settings.viewMode == ViewMode.LIST) {
            LazyColumn(
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.categoryItems, key = { it.path }) { item ->
                    val isSelected = state.selectedPaths.contains(item.path)
                    FileListRow(
                        file = item,
                        isSelected = isSelected,
                        isSelectionMode = state.isInSelectionMode,
                        isFavorite = state.favoritePaths.contains(item.path),
                        onClick = {
                            if (state.isInSelectionMode) onToggleSelect(item.path) else onOpenFile(item)
                        },
                        onLongClick = { onToggleSelect(item.path) },
                        onMoreClick = { onMoreFile(item) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.categoryItems, key = { it.path }) { item ->
                    val isSelected = state.selectedPaths.contains(item.path)
                    FileGridCard(
                        file = item,
                        isSelected = isSelected,
                        isSelectionMode = state.isInSelectionMode,
                        isFavorite = state.favoritePaths.contains(item.path),
                        onClick = {
                            if (state.isInSelectionMode) onToggleSelect(item.path) else onOpenFile(item)
                        },
                        onLongClick = { onToggleSelect(item.path) },
                        onMoreClick = { onMoreFile(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: FileManagerUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryFilterChange: (FileCategory?) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onMoreFile: (FileItem) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("search_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search files and folders") },
                            singleLine = true,
                            shape = CircleShape,
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .testTag("search_input_field")
                        )
                    }

                    // Category filter chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        FilterChip(
                            selected = state.searchCategoryFilter == null,
                            onClick = { onCategoryFilterChange(null) },
                            label = { Text("All") }
                        )
                        listOf(
                            FileCategory.IMAGES,
                            FileCategory.VIDEOS,
                            FileCategory.AUDIO,
                            FileCategory.DOCUMENTS,
                            FileCategory.ARCHIVES,
                            FileCategory.DOWNLOADS
                        ).forEach { cat ->
                            FilterChip(
                                selected = state.searchCategoryFilter == cat,
                                onClick = {
                                    onCategoryFilterChange(if (state.searchCategoryFilter == cat) null else cat)
                                },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isSearching -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    CircularProgressIndicator()
                }
            }
            state.searchQuery.isBlank() && state.searchCategoryFilter == null -> {
                EmptyStateView(
                    icon = Icons.Filled.Search,
                    title = "Search Bansify Files",
                    subtitle = "Find documents, photos, folders, archives, and downloads instantly.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            state.searchResults.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Filled.Search,
                    title = "No matching files",
                    subtitle = "Try a different keyword or adjust the category filter.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(state.searchResults, key = { it.path }) { item ->
                        val parentName = File(item.path).parentFile?.name ?: ""
                        FileListRow(
                            file = item,
                            isSelected = false,
                            isSelectionMode = false,
                            isFavorite = state.favoritePaths.contains(item.path),
                            subtitleOverride = "${if (item.isDirectory) "Folder" else formatFileSize(item.sizeBytes)} • $parentName",
                            onClick = { onOpenFile(item) },
                            onLongClick = { onMoreFile(item) },
                            onMoreClick = { onMoreFile(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: FileManagerUiState,
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onMoreFile: (FileItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("favorites_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.favoriteItems.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.Star,
                title = "No favorites yet",
                subtitle = "Star important files or folders from the file menu to access them quickly here.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.favoriteItems, key = { it.path }) { item ->
                    FileListRow(
                        file = item,
                        isSelected = false,
                        isSelectionMode = false,
                        isFavorite = true,
                        onClick = { onOpenFile(item) },
                        onLongClick = { onMoreFile(item) },
                        onMoreClick = { onMoreFile(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    records: List<TrashRecord>,
    onBack: () -> Unit,
    onRestore: (TrashRecord) -> Unit,
    onDeleteForever: (TrashRecord) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trash")
                        Text(
                            text = "${records.size} items • ${formatFileSize(records.sumOf { it.sizeBytes })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("trash_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        TextButton(
                            onClick = { confirmEmpty = true },
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            Text("Empty trash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.DeleteOutline,
                title = "Trash is empty",
                subtitle = "Items moved to Trash stay here until you restore or permanently delete them.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(records, key = { it.id }) { rec ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.originalName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(rec.sizeBytes)} • Deleted ${formatShortDate(rec.deletedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onRestore(rec) },
                                modifier = Modifier.testTag("restore_trash_${rec.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restore,
                                    contentDescription = "Restore ${rec.originalName}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onDeleteForever(rec) },
                                modifier = Modifier.testTag("delete_trash_${rec.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = "Delete forever",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("All ${records.size} items in Trash will be permanently deleted and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        onEmptyTrash()
                    }
                ) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeFolderScreen(
    hasPin: Boolean,
    isUnlocked: Boolean,
    pinError: String?,
    records: List<SafeFolderRecord>,
    onBack: () -> Unit,
    onSetupPin: (String) -> Unit,
    onVerifyPin: (String) -> Unit,
    onOpenRecord: (SafeFolderRecord) -> Unit,
    onMoveOut: (SafeFolderRecord) -> Unit,
    onDeleteForever: (SafeFolderRecord) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe folder") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("safe_folder_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!isUnlocked) {
            SafePinLockContent(
                isSetupMode = !hasPin,
                errorMessage = pinError,
                onSubmitPin = { pin ->
                    if (hasPin) onVerifyPin(pin) else onSetupPin(pin)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else if (records.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.Shield,
                title = "Safe folder is unlocked",
                subtitle = "Move private files here from any folder's menu to keep them hidden behind your 4-digit PIN.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(records, key = { it.id }) { rec ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenRecord(rec) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.originalName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(rec.sizeBytes)} • Locked ${formatShortDate(rec.movedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onMoveOut(rec) }) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = "Move out of Safe folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteForever(rec) }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = "Delete permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafePinLockContent(
    isSetupMode: Boolean,
    errorMessage: String?,
    onSubmitPin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember(isSetupMode) { mutableStateOf("") }
    var firstStepPin by remember(isSetupMode) { mutableStateOf<String?>(null) }
    var localError by remember(errorMessage) { mutableStateOf(errorMessage) }

    val heading = when {
        !isSetupMode -> "Enter your 4-digit PIN"
        firstStepPin == null -> "Create a 4-digit PIN"
        else -> "Confirm your 4-digit PIN"
    }

    fun onDigit(digit: String) {
        if (enteredPin.length >= 4) return
        localError = null
        val next = enteredPin + digit
        enteredPin = next
        if (next.length == 4) {
            if (!isSetupMode) {
                onSubmitPin(next)
                enteredPin = ""
            } else if (firstStepPin == null) {
                firstStepPin = next
                enteredPin = ""
            } else if (firstStepPin == next) {
                onSubmitPin(next)
                enteredPin = ""
                firstStepPin = null
            } else {
                localError = "PINs did not match. Try again."
                enteredPin = ""
                firstStepPin = null
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isSetupMode) "Protect sensitive files in an isolated private folder."
                else "Unlock your protected files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // 4 PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) { idx ->
                    val filled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                }
            }

            if (localError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = localError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(28.dp))

            // Keypad 1..9, 0, Backspace
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(Modifier.size(68.dp))
                                "DEL" -> {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            .testTag("pin_key_backspace")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace"
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .clickable { onDigit(key) }
                                            .testTag("pin_key_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
