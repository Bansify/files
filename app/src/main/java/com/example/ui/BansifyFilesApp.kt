package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ActiveScreen
import com.example.model.MainTab
import com.example.model.PreviewModal
import com.example.model.ThemePreference
import com.example.ui.screens.AudioPlayerBottomSheet
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.CategoryBrowserScreen
import com.example.ui.screens.CleanScreen
import com.example.ui.screens.CompressDialog
import com.example.ui.screens.DeleteConfirmDialog
import com.example.ui.screens.DirectoryBrowserScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FileActionBottomSheet
import com.example.ui.screens.FilePropertiesBottomSheet
import com.example.ui.screens.ImageViewerModal
import com.example.ui.screens.NewFolderDialog
import com.example.ui.screens.NewTextFileDialog
import com.example.ui.screens.RenameDialog
import com.example.ui.screens.SafeFolderScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TextEditorModal
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.screens.ZipInspectorBottomSheet
import com.example.ui.theme.BansifyFilesTheme
import com.example.viewmodel.FileManagerViewModel
import java.io.File

@Composable
fun BansifyFilesApp(
    viewModel: FileManagerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val darkTheme = when (state.settings.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    // Refresh permissions & storage stats when returning from system Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStorageStatusAndReload()
    }

    // Snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Activity result launchers
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStorageStatusAndReload()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    fun launchAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            }.getOrElse {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            runCatching { allFilesSettingsLauncher.launch(intent) }
                .onFailure { viewModel.showMessage("Open Settings > Apps > Bansify Files > Permissions") }
        } else {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { allFilesSettingsLauncher.launch(intent) }
        }
    }

    fun launchPhotoImport() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    fun launchDocumentImport() {
        documentPickerLauncher.launch(arrayOf("*/*"))
    }

    // Intercept system back whenever on a sub-screen, preview modal, selection mode, or non-Browse tab
    val shouldHandleBack = state.screenStack.size > 1 ||
        state.activePreview != null ||
        state.isInSelectionMode ||
        state.selectedTab != MainTab.BROWSE

    BackHandler(enabled = shouldHandleBack) {
        viewModel.navigateBack()
    }

    BansifyFilesTheme(
        darkTheme = darkTheme,
        dynamicColor = state.settings.useDynamicColor
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp
                val isHome = state.currentScreen is ActiveScreen.Home

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (isHome && !useNavRail) {
                            NavigationBar {
                                MainTab.entries.forEach { tab ->
                                    val selected = state.selectedTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tabLabel(tab)
                                            )
                                        },
                                        label = { Text(tabLabel(tab)) },
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
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
                        if (isHome && useNavRail) {
                            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                MainTab.entries.forEach { tab ->
                                    val selected = state.selectedTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tabLabel(tab)
                                            )
                                        },
                                        label = { Text(tabLabel(tab)) },
                                        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (val screen = state.currentScreen) {
                                ActiveScreen.Home -> {
                                    when (state.selectedTab) {
                                        MainTab.CLEAN -> CleanScreen(
                                            state = state,
                                            onRefreshScan = { viewModel.refreshAll() },
                                            onClearCache = { viewModel.clearAppCache() },
                                            onCleanDuplicates = { viewModel.cleanDuplicateCopies() },
                                            onCleanEmptyFolders = { viewModel.cleanEmptyFolders() },
                                            onTrashFile = { viewModel.moveToTrash(listOf(it.path)) },
                                            onOpenFile = { viewModel.openFile(it) },
                                            onOpenStorageAnalyzer = { viewModel.selectTab(MainTab.TOOLS) }
                                        )

                                        MainTab.BROWSE -> BrowseScreen(
                                            state = state,
                                            onSearchClick = { viewModel.navigateTo(ActiveScreen.Search) },
                                            onSettingsClick = { viewModel.navigateTo(ActiveScreen.Settings) },
                                            onRefreshClick = { viewModel.refreshAll() },
                                            onGrantAllFilesAccess = ::launchAllFilesAccess,
                                            onImportDocuments = ::launchDocumentImport,
                                            onImportPhotos = ::launchPhotoImport,
                                            onOpenCategory = { viewModel.navigateTo(ActiveScreen.Category(it)) },
                                            onOpenRecentFile = { viewModel.openFile(it) },
                                            onOpenFavorites = { viewModel.navigateTo(ActiveScreen.Favorites) },
                                            onOpenSafeFolder = { viewModel.navigateTo(ActiveScreen.SafeFolder) },
                                            onOpenTrash = { viewModel.navigateTo(ActiveScreen.Trash) },
                                            onOpenVolume = { viewModel.openStorageVolume(it) }
                                        )

                                        MainTab.TOOLS -> ToolsScreen(
                                            state = state,
                                            onCreateNewFolder = { viewModel.setShowNewFolderDialog(true) },
                                            onCreateNewTextFile = { viewModel.setShowNewFileDialog(true) },
                                            onImportPhotos = ::launchPhotoImport,
                                            onImportDocuments = ::launchDocumentImport,
                                            onOpenFolder = { viewModel.openDirectory(it) },
                                            onOpenCategory = { viewModel.navigateTo(ActiveScreen.Category(it)) },
                                            onOpenSettings = { viewModel.navigateTo(ActiveScreen.Settings) },
                                            onRefreshAnalyzer = { viewModel.loadFolderAnalyzer() }
                                        )
                                    }
                                }

                                is ActiveScreen.Directory -> DirectoryBrowserScreen(
                                    screen = screen,
                                    state = state,
                                    onBack = { viewModel.navigateBack() },
                                    onNavigatePath = { viewModel.openDirectory(it) },
                                    onOpenFile = { viewModel.openFile(it) },
                                    onToggleSelect = { viewModel.toggleSelection(it) },
                                    onSelectAll = { viewModel.selectAll(state.directoryItems) },
                                    onClearSelection = { viewModel.clearSelection() },
                                    onMoreFile = { viewModel.showActionSheet(it) },
                                    onToggleViewMode = { viewModel.toggleViewMode() },
                                    onSelectSort = { viewModel.setSortOption(it) },
                                    onToggleHiddenFiles = { viewModel.setShowHiddenFiles(!state.settings.showHiddenFiles) },
                                    onOpenSearch = { viewModel.navigateTo(ActiveScreen.Search) },
                                    onNewFolder = { viewModel.setShowNewFolderDialog(true) },
                                    onNewTextFile = { viewModel.setShowNewFileDialog(true) },
                                    onImportPhotos = ::launchPhotoImport,
                                    onImportDocuments = ::launchDocumentImport,
                                    onPasteClipboard = { viewModel.executeClipboardPaste() },
                                    onCancelClipboard = { viewModel.cancelClipboard() },
                                    onCopySelected = { viewModel.startCopy(state.selectedPaths.toList()) },
                                    onMoveSelected = { viewModel.startMove(state.selectedPaths.toList()) },
                                    onCompressSelected = { viewModel.requestCompress(state.selectedPaths.toList()) },
                                    onShareSelected = { viewModel.shareFiles(state.selectedPaths.toList()) },
                                    onMoveSelectedToSafe = { viewModel.moveToSafeFolder(state.selectedPaths.toList()) },
                                    onDeleteSelected = { viewModel.requestDelete(state.selectedPaths.toList()) }
                                )

                                is ActiveScreen.Category -> CategoryBrowserScreen(
                                    category = screen.category,
                                    state = state,
                                    onBack = { viewModel.navigateBack() },
                                    onOpenFile = { viewModel.openFile(it) },
                                    onToggleSelect = { viewModel.toggleSelection(it) },
                                    onSelectAll = { viewModel.selectAll(state.categoryItems) },
                                    onClearSelection = { viewModel.clearSelection() },
                                    onMoreFile = { viewModel.showActionSheet(it) },
                                    onToggleViewMode = { viewModel.toggleViewMode() },
                                    onSelectSort = { viewModel.setSortOption(it) },
                                    onCopySelected = { viewModel.startCopy(state.selectedPaths.toList()) },
                                    onMoveSelected = { viewModel.startMove(state.selectedPaths.toList()) },
                                    onCompressSelected = { viewModel.requestCompress(state.selectedPaths.toList()) },
                                    onShareSelected = { viewModel.shareFiles(state.selectedPaths.toList()) },
                                    onMoveSelectedToSafe = { viewModel.moveToSafeFolder(state.selectedPaths.toList()) },
                                    onDeleteSelected = { viewModel.requestDelete(state.selectedPaths.toList()) }
                                )

                                ActiveScreen.Search -> SearchScreen(
                                    state = state,
                                    onBack = { viewModel.navigateBack() },
                                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                                    onCategoryFilterChange = { viewModel.onSearchCategoryFilterChange(it) },
                                    onOpenFile = { viewModel.openFile(it) },
                                    onMoreFile = { viewModel.showActionSheet(it) }
                                )

                                ActiveScreen.Favorites -> FavoritesScreen(
                                    state = state,
                                    onBack = { viewModel.navigateBack() },
                                    onOpenFile = { viewModel.openFile(it) },
                                    onMoreFile = { viewModel.showActionSheet(it) }
                                )

                                ActiveScreen.Trash -> TrashScreen(
                                    records = state.trashRecords,
                                    onBack = { viewModel.navigateBack() },
                                    onRestore = { viewModel.restoreFromTrash(it) },
                                    onDeleteForever = { viewModel.deleteFromTrash(it) },
                                    onEmptyTrash = { viewModel.emptyTrash() }
                                )

                                ActiveScreen.SafeFolder -> SafeFolderScreen(
                                    hasPin = state.hasSafePin,
                                    isUnlocked = state.isSafeUnlocked,
                                    pinError = state.safePinError,
                                    records = state.safeRecords,
                                    onBack = { viewModel.navigateBack() },
                                    onSetupPin = { viewModel.setupSafePin(it) },
                                    onVerifyPin = { viewModel.verifySafePin(it) },
                                    onOpenRecord = { viewModel.openSafeRecord(it) },
                                    onMoveOut = { viewModel.moveOutOfSafeFolder(it) },
                                    onDeleteForever = { viewModel.deleteFromSafeFolder(it) }
                                )

                                ActiveScreen.StorageAnalyzer -> ToolsScreen(
                                    state = state,
                                    onCreateNewFolder = { viewModel.setShowNewFolderDialog(true) },
                                    onCreateNewTextFile = { viewModel.setShowNewFileDialog(true) },
                                    onImportPhotos = ::launchPhotoImport,
                                    onImportDocuments = ::launchDocumentImport,
                                    onOpenFolder = { viewModel.openDirectory(it) },
                                    onOpenCategory = { viewModel.navigateTo(ActiveScreen.Category(it)) },
                                    onOpenSettings = { viewModel.navigateTo(ActiveScreen.Settings) },
                                    onRefreshAnalyzer = { viewModel.loadFolderAnalyzer() }
                                )

                                ActiveScreen.Settings -> SettingsScreen(
                                    state = state,
                                    onBack = { viewModel.navigateBack() },
                                    onThemeChange = { viewModel.setThemePreference(it) },
                                    onDynamicColorChange = { viewModel.setUseDynamicColor(it) },
                                    onShowHiddenChange = { viewModel.setShowHiddenFiles(it) },
                                    onViewModeChange = { viewModel.setViewMode(it) },
                                    onGrantAllFilesAccess = ::launchAllFilesAccess,
                                    onResetSafePin = { viewModel.resetSafePin() }
                                )
                            }

                            if (state.isBusy) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }

            // Single-file action bottom sheet
            state.activeActionSheetFile?.let { file ->
                FileActionBottomSheet(
                    file = file,
                    isFavorite = state.favoritePaths.contains(file.path),
                    onDismiss = { viewModel.showActionSheet(null) },
                    onOpen = {
                        viewModel.showActionSheet(null)
                        viewModel.openFile(file)
                    },
                    onOpenWith = { viewModel.openExternally(file) },
                    onToggleFavorite = { viewModel.toggleFavorite(file.path) },
                    onRename = { viewModel.setRenameTarget(file) },
                    onCopy = { viewModel.startCopy(listOf(file.path)) },
                    onMove = { viewModel.startMove(listOf(file.path)) },
                    onCompress = { viewModel.requestCompress(listOf(file.path)) },
                    onExtract = { viewModel.extractZip(file) },
                    onShare = { viewModel.shareFiles(listOf(file.path)) },
                    onMoveToSafe = { viewModel.moveToSafeFolder(listOf(file.path)) },
                    onInfo = { viewModel.showProperties(file) },
                    onDelete = { viewModel.requestDelete(listOf(file.path)) }
                )
            }

            // Built-in preview modals & sheets
            when (val preview = state.activePreview) {
                is PreviewModal.ImageViewer -> ImageViewerModal(
                    file = preview.file,
                    onDismiss = { viewModel.closePreview() },
                    onShare = { viewModel.shareFiles(listOf(preview.file.path)) },
                    onInfo = { viewModel.showProperties(preview.file) },
                    onOpenWith = { viewModel.openExternally(preview.file) }
                )

                is PreviewModal.TextEditor -> TextEditorModal(
                    file = preview.file,
                    initialContent = preview.content,
                    isTruncated = preview.isTruncated,
                    onDismiss = { viewModel.closePreview() },
                    onSave = { viewModel.saveEditedTextFile(preview.file, it) }
                )

                is PreviewModal.AudioPlayer -> AudioPlayerBottomSheet(
                    file = preview.file,
                    onDismiss = { viewModel.closePreview() }
                )

                is PreviewModal.ZipInspector -> ZipInspectorBottomSheet(
                    file = preview.file,
                    entries = preview.entries,
                    onDismiss = { viewModel.closePreview() },
                    onExtractHere = { viewModel.extractZip(preview.file) }
                )

                is PreviewModal.PropertiesSheet -> FilePropertiesBottomSheet(
                    file = preview.file,
                    checksum = preview.checksum,
                    isComputingChecksum = preview.isComputingChecksum,
                    onComputeChecksum = { viewModel.computeChecksumForActiveProperties() },
                    onCopyText = { viewModel.showMessage(it) },
                    onDismiss = { viewModel.closePreview() }
                )

                null -> Unit
            }

            // Creation / Rename / Compress / Delete dialogs
            if (state.showNewFolderDialog) {
                NewFolderDialog(
                    onDismiss = { viewModel.setShowNewFolderDialog(false) },
                    onCreate = { viewModel.createFolder(it) }
                )
            }

            if (state.showNewFileDialog) {
                NewTextFileDialog(
                    onDismiss = { viewModel.setShowNewFileDialog(false) },
                    onCreate = { name, content -> viewModel.createTextFile(name, content) }
                )
            }

            state.renameTarget?.let { target ->
                RenameDialog(
                    file = target,
                    onDismiss = { viewModel.setRenameTarget(null) },
                    onRename = { viewModel.renameFile(target, it) }
                )
            }

            state.compressTargetPaths?.let { paths ->
                val suggested = if (paths.size == 1) {
                    File(paths.first()).nameWithoutExtension.ifBlank { "archive" }
                } else {
                    "Bansify_Archive"
                }
                CompressDialog(
                    itemCount = paths.size,
                    suggestedName = suggested,
                    onDismiss = { viewModel.dismissCompressDialog() },
                    onCompress = { archiveName -> viewModel.compressFiles(paths, archiveName) }
                )
            }

            state.deleteConfirmPaths?.let { paths ->
                DeleteConfirmDialog(
                    count = paths.size,
                    onDismiss = { viewModel.dismissDeleteConfirm() },
                    onMoveToTrash = { viewModel.moveToTrash(paths) },
                    onDeletePermanently = { viewModel.deletePermanently(paths) }
                )
            }
        }
    }
}

private fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.CLEAN -> "Clean"
    MainTab.BROWSE -> "Browse"
    MainTab.TOOLS -> "Tools"
}

private fun tabIcon(tab: MainTab, selected: Boolean) = when (tab) {
    MainTab.CLEAN -> if (selected) Icons.Filled.CleaningServices else Icons.Outlined.CleaningServices
    MainTab.BROWSE -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    MainTab.TOOLS -> if (selected) Icons.Filled.Build else Icons.Outlined.Build
}
