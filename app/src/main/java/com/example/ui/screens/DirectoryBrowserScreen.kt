package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActiveScreen
import com.example.model.FileItem
import com.example.model.SortOption
import com.example.model.ViewMode
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.ClipboardBanner
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListRow
import com.example.ui.components.SelectionBottomActionBar
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.SortMenuDropdown
import com.example.viewmodel.FileManagerUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    screen: ActiveScreen.Directory,
    state: FileManagerUiState,
    onBack: () -> Unit,
    onNavigatePath: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoreFile: (FileItem) -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleHiddenFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onNewFolder: () -> Unit,
    onNewTextFile: () -> Unit,
    onImportPhotos: () -> Unit,
    onImportDocuments: () -> Unit,
    onPasteClipboard: () -> Unit,
    onCancelClipboard: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelectedToSafe: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    val folderTitle = if (screen.path == screen.rootPath) {
        screen.rootLabel
    } else {
        File(screen.path).name.ifEmpty { screen.rootLabel }
    }

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
                        Text(
                            text = folderTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("directory_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.testTag("directory_search_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = onToggleViewMode,
                            modifier = Modifier.testTag("directory_view_toggle")
                        ) {
                            Icon(
                                imageVector = if (state.settings.viewMode == ViewMode.LIST) {
                                    Icons.Filled.GridView
                                } else {
                                    Icons.AutoMirrored.Filled.ViewList
                                },
                                contentDescription = "Switch view mode"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("directory_sort_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            SortMenuDropdown(
                                expanded = showSortMenu,
                                currentSort = state.settings.sortOption,
                                onDismiss = { showSortMenu = false },
                                onSelectSort = onSelectSort
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.settings.showHiddenFiles) "Hide hidden files"
                                            else "Show hidden files"
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onToggleHiddenFiles()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select all") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onSelectAll()
                                    }
                                )
                            }
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
        },
        floatingActionButton = {
            if (!state.isInSelectionMode) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("directory_add_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Create or import")
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onNewFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New text file") },
                            leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onNewTextFile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import photos") },
                            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onImportPhotos()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import files") },
                            leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onImportDocuments()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BreadcrumbBar(
                currentPath = screen.path,
                rootPath = screen.rootPath,
                rootLabel = screen.rootLabel,
                onNavigatePath = onNavigatePath
            )

            ClipboardBanner(
                clipboard = state.clipboard,
                onPasteHere = onPasteClipboard,
                onCancel = onCancelClipboard
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            when {
                state.isDirectoryLoading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }
                state.directoryItems.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Filled.FolderOpen,
                        title = "This folder is empty",
                        subtitle = "Create a new folder, add a text note, or import files from your device.",
                        actionLabel = "New folder",
                        onAction = onNewFolder,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.settings.viewMode == ViewMode.LIST -> {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.directoryItems, key = { it.path }) { item ->
                            val isSelected = state.selectedPaths.contains(item.path)
                            FileListRow(
                                file = item,
                                isSelected = isSelected,
                                isSelectionMode = state.isInSelectionMode,
                                isFavorite = state.favoritePaths.contains(item.path),
                                onClick = {
                                    if (state.isInSelectionMode) onToggleSelect(item.path)
                                    else onOpenFile(item)
                                },
                                onLongClick = { onToggleSelect(item.path) },
                                onMoreClick = { onMoreFile(item) }
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.directoryItems, key = { it.path }) { item ->
                            val isSelected = state.selectedPaths.contains(item.path)
                            FileGridCard(
                                file = item,
                                isSelected = isSelected,
                                isSelectionMode = state.isInSelectionMode,
                                isFavorite = state.favoritePaths.contains(item.path),
                                onClick = {
                                    if (state.isInSelectionMode) onToggleSelect(item.path)
                                    else onOpenFile(item)
                                },
                                onLongClick = { onToggleSelect(item.path) },
                                onMoreClick = { onMoreFile(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
