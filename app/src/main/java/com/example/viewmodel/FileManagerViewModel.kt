package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileOperationsRepository
import com.example.data.PreferencesRepository
import com.example.data.UserSettings
import com.example.model.ActiveScreen
import com.example.model.CategorySummary
import com.example.model.CleanSummary
import com.example.model.ClipboardOperation
import com.example.model.ClipboardState
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FolderSizeEntry
import com.example.model.MainTab
import com.example.model.PreviewModal
import com.example.model.SafeFolderRecord
import com.example.model.SortOption
import com.example.model.StorageVolumeInfo
import com.example.model.ThemePreference
import com.example.model.TrashRecord
import com.example.model.ViewMode
import com.example.model.formatFileSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class FileManagerUiState(
    val selectedTab: MainTab = MainTab.BROWSE,
    val screenStack: List<ActiveScreen> = listOf(ActiveScreen.Home),
    val settings: UserSettings = UserSettings(),
    val hasAllFilesAccess: Boolean = false,
    val storageVolumes: List<StorageVolumeInfo> = emptyList(),
    val categorySummaries: List<CategorySummary> = emptyList(),
    val recentFiles: List<FileItem> = emptyList(),
    val allScannedFiles: List<FileItem> = emptyList(),
    val directoryItems: List<FileItem> = emptyList(),
    val isDirectoryLoading: Boolean = false,
    val categoryItems: List<FileItem> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
    val favoriteItems: List<FileItem> = emptyList(),
    val trashRecords: List<TrashRecord> = emptyList(),
    val safeRecords: List<SafeFolderRecord> = emptyList(),
    val hasSafePin: Boolean = false,
    val isSafeUnlocked: Boolean = false,
    val safePinError: String? = null,
    val searchQuery: String = "",
    val searchCategoryFilter: FileCategory? = null,
    val searchResults: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val cleanSummary: CleanSummary = CleanSummary(isScanning = true),
    val topFolderSizes: List<FolderSizeEntry> = emptyList(),
    val isAnalyzingFolders: Boolean = false,
    val isBusy: Boolean = false,
    val activePreview: PreviewModal? = null,
    val activeActionSheetFile: FileItem? = null,
    val showNewFolderDialog: Boolean = false,
    val showNewFileDialog: Boolean = false,
    val renameTarget: FileItem? = null,
    val compressTargetPaths: List<String>? = null,
    val deleteConfirmPaths: List<String>? = null,
    val snackbarMessage: String? = null
) {
    val currentScreen: ActiveScreen get() = screenStack.lastOrNull() ?: ActiveScreen.Home
    val isInSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val currentDirectoryPath: String? get() = (currentScreen as? ActiveScreen.Directory)?.path
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application.applicationContext)
    val fileRepo = FileOperationsRepository(application.applicationContext, prefsRepo)

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var scanJob: Job? = null

    init {
        observePreferences()
        refreshAll()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                prefsRepo.settingsFlow,
                prefsRepo.favoritesFlow,
                prefsRepo.trashRecordsFlow,
                prefsRepo.safeRecordsFlow,
                prefsRepo.hasSafePinFlow
            ) { settings, favorites, trash, safe, hasPin ->
                PrefSnapshot(settings, favorites, trash, safe, hasPin)
            }.collect { snap ->
                val prevSettings = _uiState.value.settings
                val favItems = fileRepo.getFavoriteItems(snap.favorites)
                _uiState.update { state ->
                    state.copy(
                        settings = snap.settings,
                        favoritePaths = snap.favorites,
                        favoriteItems = favItems,
                        trashRecords = snap.trash,
                        safeRecords = snap.safe,
                        hasSafePin = snap.hasPin
                    )
                }
                if (prevSettings.showHiddenFiles != snap.settings.showHiddenFiles ||
                    prevSettings.sortOption != snap.settings.sortOption
                ) {
                    reloadCurrentView()
                }
            }
        }
    }

    private data class PrefSnapshot(
        val settings: UserSettings,
        val favorites: Set<String>,
        val trash: List<TrashRecord>,
        val safe: List<SafeFolderRecord>,
        val hasPin: Boolean
    )

    fun refreshStorageStatusAndReload() {
        val currentAccess = fileRepo.hasAllFilesAccess()
        if (currentAccess != _uiState.value.hasAllFilesAccess) {
            refreshAll()
        } else {
            refreshVolumesOnly()
        }
    }

    private fun refreshVolumesOnly() {
        viewModelScope.launch {
            val volumes = fileRepo.getStorageVolumes()
            _uiState.update { it.copy(storageVolumes = volumes, hasAllFilesAccess = fileRepo.hasAllFilesAccess()) }
        }
    }

    fun refreshAll() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val hasAccess = fileRepo.hasAllFilesAccess()
            val volumes = fileRepo.getStorageVolumes()
            _uiState.update {
                it.copy(
                    hasAllFilesAccess = hasAccess,
                    storageVolumes = volumes,
                    cleanSummary = it.cleanSummary.copy(isScanning = true)
                )
            }

            val showHidden = _uiState.value.settings.showHiddenFiles
            val allFiles = fileRepo.scanAllAccessibleFiles(showHidden)
            val summaries = fileRepo.getCategorySummaries(allFiles)
            val recents = allFiles.sortedByDescending { it.lastModified }.take(15)
            val favItems = fileRepo.getFavoriteItems(_uiState.value.favoritePaths)

            _uiState.update { state ->
                val updatedCategoryItems = (state.currentScreen as? ActiveScreen.Category)?.let { catScreen ->
                    fileRepo.sortFileItems(
                        fileRepo.filterFilesByCategory(allFiles, catScreen.category),
                        state.settings.sortOption
                    )
                } ?: state.categoryItems

                state.copy(
                    allScannedFiles = allFiles,
                    categorySummaries = summaries,
                    recentFiles = recents,
                    favoriteItems = favItems,
                    categoryItems = updatedCategoryItems
                )
            }

            reloadCurrentDirectoryIfActive()

            val clean = fileRepo.scanCleanSummary(allFiles, showHidden)
            _uiState.update { it.copy(cleanSummary = clean) }
        }
    }

    fun selectTab(tab: MainTab) {
        clearSelection()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                screenStack = listOf(ActiveScreen.Home),
                isSafeUnlocked = false
            )
        }
        if (tab == MainTab.TOOLS && _uiState.value.topFolderSizes.isEmpty()) {
            loadFolderAnalyzer()
        }
    }

    fun navigateTo(screen: ActiveScreen) {
        clearSelection()
        _uiState.update { state ->
            state.copy(
                screenStack = state.screenStack + screen,
                safePinError = null
            )
        }
        when (screen) {
            is ActiveScreen.Directory -> loadDirectory(screen.path)
            is ActiveScreen.Category -> loadCategory(screen.category)
            ActiveScreen.StorageAnalyzer -> loadFolderAnalyzer()
            ActiveScreen.Search -> runSearch(_uiState.value.searchQuery, _uiState.value.searchCategoryFilter)
            else -> Unit
        }
    }

    fun openStorageVolume(volume: StorageVolumeInfo) {
        navigateTo(
            ActiveScreen.Directory(
                path = volume.path,
                rootPath = volume.path,
                rootLabel = volume.name
            )
        )
    }

    fun openDirectory(path: String) {
        val current = _uiState.value.currentScreen
        val rootPath = (current as? ActiveScreen.Directory)?.rootPath
            ?: if (path.startsWith(fileRepo.workspaceDir.absolutePath)) fileRepo.workspaceDir.absolutePath
            else fileRepo.getPrimaryExternalRoot().absolutePath
        val rootLabel = (current as? ActiveScreen.Directory)?.rootLabel
            ?: if (rootPath == fileRepo.workspaceDir.absolutePath) "Bansify workspace" else "Internal storage"

        clearSelection()
        _uiState.update { state ->
            val newDir = ActiveScreen.Directory(path = path, rootPath = rootPath, rootLabel = rootLabel)
            val updatedStack = if (current is ActiveScreen.Directory) {
                state.screenStack.dropLast(1) + newDir
            } else {
                state.screenStack + newDir
            }
            state.copy(screenStack = updatedStack)
        }
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        val state = _uiState.value
        if (state.activePreview != null) {
            closePreview()
            return true
        }
        if (state.isInSelectionMode) {
            clearSelection()
            return true
        }
        val current = state.currentScreen
        if (current is ActiveScreen.Directory && current.path != current.rootPath) {
            val parent = File(current.path).parentFile
            if (parent != null && parent.absolutePath.startsWith(current.rootPath)) {
                openDirectory(parent.absolutePath)
                return true
            }
        }
        if (state.screenStack.size > 1) {
            val leavingSafe = current is ActiveScreen.SafeFolder
            _uiState.update {
                it.copy(
                    screenStack = it.screenStack.dropLast(1),
                    isSafeUnlocked = if (leavingSafe) false else it.isSafeUnlocked,
                    safePinError = null
                )
            }
            reloadCurrentView()
            return true
        }
        if (state.selectedTab != MainTab.BROWSE) {
            selectTab(MainTab.BROWSE)
            return true
        }
        return false
    }

    private fun reloadCurrentView() {
        when (val screen = _uiState.value.currentScreen) {
            is ActiveScreen.Directory -> loadDirectory(screen.path)
            is ActiveScreen.Category -> loadCategory(screen.category)
            ActiveScreen.Search -> runSearch(_uiState.value.searchQuery, _uiState.value.searchCategoryFilter)
            ActiveScreen.StorageAnalyzer -> loadFolderAnalyzer()
            else -> Unit
        }
    }

    private fun reloadCurrentDirectoryIfActive() {
        val current = _uiState.value.currentScreen
        if (current is ActiveScreen.Directory) {
            loadDirectory(current.path)
        }
    }

    private fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDirectoryLoading = true) }
            val settings = _uiState.value.settings
            val items = fileRepo.listDirectory(path, settings.showHiddenFiles, settings.sortOption)
            _uiState.update { it.copy(directoryItems = items, isDirectoryLoading = false) }
        }
    }

    private fun loadCategory(category: FileCategory) {
        val state = _uiState.value
        val filtered = fileRepo.filterFilesByCategory(state.allScannedFiles, category)
        val sorted = fileRepo.sortFileItems(filtered, state.settings.sortOption)
        _uiState.update { it.copy(categoryItems = sorted) }
    }

    fun loadFolderAnalyzer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingFolders = true) }
            val rootPath = fileRepo.getDefaultBrowseRoot().absolutePath
            val folders = fileRepo.computeTopFolderSizes(rootPath, _uiState.value.settings.showHiddenFiles)
            _uiState.update { it.copy(topFolderSizes = folders, isAnalyzingFolders = false) }
        }
    }

    // Search
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        runSearch(query, _uiState.value.searchCategoryFilter)
    }

    fun onSearchCategoryFilterChange(category: FileCategory?) {
        _uiState.update { it.copy(searchCategoryFilter = category) }
        runSearch(_uiState.value.searchQuery, category)
    }

    private fun runSearch(query: String, category: FileCategory?) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180)
            if (query.isBlank() && category == null) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }
            _uiState.update { it.copy(isSearching = true) }
            val results = fileRepo.searchFiles(query, category, _uiState.value.settings.showHiddenFiles)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    // Selection
    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedPaths.toMutableSet()
            if (!updated.add(path)) updated.remove(path)
            state.copy(selectedPaths = updated)
        }
    }

    fun selectAll(items: List<FileItem>) {
        _uiState.update { state ->
            val allPaths = items.map { it.path }.toSet()
            val next = if (state.selectedPaths.size == allPaths.size) emptySet() else allPaths
            state.copy(selectedPaths = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    // File actions & preview
    fun openFile(file: FileItem) {
        if (file.isDirectory) {
            openDirectory(file.path)
            return
        }
        when {
            file.isImage -> _uiState.update { it.copy(activePreview = PreviewModal.ImageViewer(file)) }
            file.isAudio -> _uiState.update { it.copy(activePreview = PreviewModal.AudioPlayer(file)) }
            file.isTextEditable -> {
                viewModelScope.launch {
                    fileRepo.readTextFile(file.path)
                        .onSuccess { (text, truncated) ->
                            _uiState.update { it.copy(activePreview = PreviewModal.TextEditor(file, text, truncated)) }
                        }
                        .onFailure { err -> showMessage(err.message ?: "Unable to open text file") }
                }
            }
            file.isZip -> {
                viewModelScope.launch {
                    fileRepo.inspectZipEntries(file.path)
                        .onSuccess { entries ->
                            _uiState.update { it.copy(activePreview = PreviewModal.ZipInspector(file, entries)) }
                        }
                        .onFailure { err -> showMessage(err.message ?: "Unable to read archive") }
                }
            }
            else -> openExternally(file)
        }
    }

    fun openExternally(file: FileItem) {
        val uri = fileRepo.getFileProviderUri(file.path)
        if (uri == null) {
            showMessage("Cannot open this file externally")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            showMessage("No app found to open ${file.extension.uppercase()} files")
        }
    }

    fun shareFiles(paths: List<String>) {
        val uris = paths.filter { !File(it).isDirectory }.mapNotNull { fileRepo.getFileProviderUri(it) }
        if (uris.isEmpty()) {
            showMessage("Select at least one file (folders can be zipped first to share)")
            return
        }
        val intent = if (uris.size == 1) {
            val item = fileRepo.fileToItem(File(paths.first()))
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        runCatching {
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(chooser)
        }.onFailure {
            showMessage("Unable to share selected files")
        }
    }

    fun showProperties(file: FileItem) {
        _uiState.update { it.copy(activeActionSheetFile = null, activePreview = PreviewModal.PropertiesSheet(file)) }
    }

    fun computeChecksumForActiveProperties() {
        val current = _uiState.value.activePreview as? PreviewModal.PropertiesSheet ?: return
        if (current.file.isDirectory) return
        _uiState.update { it.copy(activePreview = current.copy(isComputingChecksum = true)) }
        viewModelScope.launch {
            fileRepo.computeChecksums(current.file.path)
                .onSuccess { checksum ->
                    _uiState.update {
                        it.copy(activePreview = current.copy(checksum = checksum, isComputingChecksum = false))
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(activePreview = current.copy(isComputingChecksum = false)) }
                    showMessage(err.message ?: "Checksum calculation failed")
                }
        }
    }

    fun saveEditedTextFile(file: FileItem, newContent: String) {
        viewModelScope.launch {
            fileRepo.saveTextFile(file.path, newContent)
                .onSuccess {
                    showMessage("Saved ${file.name}")
                    closePreview()
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Failed to save file") }
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(activePreview = null) }
    }

    fun showActionSheet(file: FileItem?) {
        _uiState.update { it.copy(activeActionSheetFile = file) }
    }

    // Create & Rename
    fun setShowNewFolderDialog(show: Boolean) {
        _uiState.update { it.copy(showNewFolderDialog = show) }
    }

    fun setShowNewFileDialog(show: Boolean) {
        _uiState.update { it.copy(showNewFileDialog = show) }
    }

    fun setRenameTarget(file: FileItem?) {
        _uiState.update { it.copy(renameTarget = file, activeActionSheetFile = null) }
    }

    fun createFolder(name: String) {
        val targetDir = _uiState.value.currentDirectoryPath ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            fileRepo.createFolder(targetDir, name)
                .onSuccess {
                    _uiState.update { it.copy(showNewFolderDialog = false) }
                    showMessage("Created folder \"${it.name}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Failed to create folder") }
        }
    }

    fun createTextFile(name: String, content: String) {
        val targetDir = _uiState.value.currentDirectoryPath ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            fileRepo.createTextFile(targetDir, name, content)
                .onSuccess {
                    _uiState.update { it.copy(showNewFileDialog = false) }
                    showMessage("Created \"${it.name}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Failed to create file") }
        }
    }

    fun renameFile(file: FileItem, newName: String) {
        viewModelScope.launch {
            fileRepo.renameFile(file.path, newName)
                .onSuccess {
                    _uiState.update { it.copy(renameTarget = null) }
                    showMessage("Renamed to \"${it.name}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Rename failed") }
        }
    }

    // Copy / Move Clipboard
    fun startCopy(paths: List<String>) {
        if (paths.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(paths = paths, operation = ClipboardOperation.COPY),
                selectedPaths = emptySet(),
                activeActionSheetFile = null,
                snackbarMessage = "Navigate to destination folder and tap Copy here"
            )
        }
    }

    fun startMove(paths: List<String>) {
        if (paths.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(paths = paths, operation = ClipboardOperation.MOVE),
                selectedPaths = emptySet(),
                activeActionSheetFile = null,
                snackbarMessage = "Navigate to destination folder and tap Move here"
            )
        }
    }

    fun cancelClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun executeClipboardPaste() {
        val clip = _uiState.value.clipboard ?: return
        val destDir = _uiState.value.currentDirectoryPath ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val result = when (clip.operation) {
                ClipboardOperation.COPY -> fileRepo.copyFiles(clip.paths, destDir)
                ClipboardOperation.MOVE -> fileRepo.moveFiles(clip.paths, destDir)
            }
            _uiState.update { it.copy(isBusy = false, clipboard = null) }
            result.onSuccess { count ->
                val verb = if (clip.operation == ClipboardOperation.COPY) "Copied" else "Moved"
                showMessage("$verb $count item${if (count == 1) "" else "s"}")
                refreshAll()
            }.onFailure { err ->
                showMessage(err.message ?: "Operation failed")
            }
        }
    }

    // Delete & Trash
    fun requestDelete(paths: List<String>) {
        if (paths.isEmpty()) return
        _uiState.update { it.copy(deleteConfirmPaths = paths, activeActionSheetFile = null) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirmPaths = null) }
    }

    fun moveToTrash(paths: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, deleteConfirmPaths = null, selectedPaths = emptySet()) }
            fileRepo.moveToTrash(paths)
                .onSuccess { count ->
                    showMessage("Moved $count item${if (count == 1) "" else "s"} to Trash")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Could not move to Trash") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun deletePermanently(paths: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, deleteConfirmPaths = null, selectedPaths = emptySet()) }
            fileRepo.deletePermanently(paths)
                .onSuccess { count ->
                    showMessage("Deleted $count item${if (count == 1) "" else "s"} permanently")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Delete failed") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun restoreFromTrash(record: TrashRecord) {
        viewModelScope.launch {
            fileRepo.restoreFromTrash(record)
                .onSuccess {
                    showMessage("Restored \"${record.originalName}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Restore failed") }
        }
    }

    fun deleteFromTrash(record: TrashRecord) {
        viewModelScope.launch {
            fileRepo.deleteFromTrash(record)
                .onSuccess { showMessage("Deleted \"${record.originalName}\" forever") }
                .onFailure { err -> showMessage(err.message ?: "Delete failed") }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            fileRepo.emptyTrash()
                .onSuccess { count -> showMessage("Emptied Trash ($count items removed)") }
                .onFailure { err -> showMessage(err.message ?: "Failed to empty Trash") }
        }
    }

    // Favorites
    fun toggleFavorite(path: String) {
        viewModelScope.launch {
            val added = prefsRepo.toggleFavorite(path)
            _uiState.update { it.copy(activeActionSheetFile = null) }
            showMessage(if (added) "Added to Favorites" else "Removed from Favorites")
        }
    }

    // Safe Folder
    fun setupSafePin(pin: String) {
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _uiState.update { it.copy(safePinError = "Enter a 4-digit PIN") }
            return
        }
        viewModelScope.launch {
            prefsRepo.setSafePin(pin)
            _uiState.update { it.copy(isSafeUnlocked = true, safePinError = null) }
            showMessage("Safe folder PIN created")
        }
    }

    fun verifySafePin(pin: String) {
        viewModelScope.launch {
            val valid = prefsRepo.verifySafePin(pin)
            if (valid) {
                _uiState.update { it.copy(isSafeUnlocked = true, safePinError = null) }
            } else {
                _uiState.update { it.copy(safePinError = "Incorrect PIN. Try again.") }
            }
        }
    }

    fun resetSafePin() {
        viewModelScope.launch {
            prefsRepo.clearSafePin()
            _uiState.update { it.copy(isSafeUnlocked = false, safePinError = null) }
            showMessage("Safe folder PIN reset")
        }
    }

    fun moveToSafeFolder(paths: List<String>) {
        if (!_uiState.value.hasSafePin) {
            _uiState.update { it.copy(activeActionSheetFile = null, selectedPaths = emptySet()) }
            showMessage("Set up your Safe folder PIN first")
            navigateTo(ActiveScreen.SafeFolder)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, activeActionSheetFile = null, selectedPaths = emptySet()) }
            fileRepo.moveToSafeFolder(paths)
                .onSuccess { count ->
                    showMessage("Moved $count item${if (count == 1) "" else "s"} to Safe folder")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Could not move to Safe folder") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun moveOutOfSafeFolder(record: SafeFolderRecord) {
        viewModelScope.launch {
            fileRepo.moveOutOfSafeFolder(record)
                .onSuccess {
                    showMessage("Moved \"${record.originalName}\" out of Safe folder")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Move failed") }
        }
    }

    fun deleteFromSafeFolder(record: SafeFolderRecord) {
        viewModelScope.launch {
            fileRepo.deleteFromSafeFolder(record)
                .onSuccess { showMessage("Deleted \"${record.originalName}\" permanently") }
                .onFailure { err -> showMessage(err.message ?: "Delete failed") }
        }
    }

    fun openSafeRecord(record: SafeFolderRecord) {
        val fileItem = fileRepo.getSafeVaultFileItem(record)
        openFile(fileItem)
    }

    // Archive (ZIP)
    fun requestCompress(paths: List<String>) {
        if (paths.isEmpty()) return
        _uiState.update { it.copy(compressTargetPaths = paths, activeActionSheetFile = null) }
    }

    fun dismissCompressDialog() {
        _uiState.update { it.copy(compressTargetPaths = null) }
    }

    fun compressFiles(paths: List<String>, archiveName: String) {
        val destDir = _uiState.value.currentDirectoryPath
            ?: File(paths.first()).parent
            ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, compressTargetPaths = null, selectedPaths = emptySet()) }
            fileRepo.compressToZip(paths, destDir, archiveName)
                .onSuccess { zip ->
                    showMessage("Created archive \"${zip.name}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Compression failed") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun extractZip(file: FileItem) {
        val destDir = File(file.path).parent ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, activeActionSheetFile = null, activePreview = null) }
            fileRepo.extractZip(file.path, destDir)
                .onSuccess { extracted ->
                    showMessage("Extracted to \"${extracted.name}\"")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Extraction failed") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    // Import from SAF / Photo Picker
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val destDir = _uiState.value.currentDirectoryPath ?: fileRepo.getDefaultBrowseRoot().absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            fileRepo.importUrisToDirectory(uris, destDir)
                .onSuccess { count ->
                    showMessage("Imported $count file${if (count == 1) "" else "s"}")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Import failed") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    // Clean actions
    fun clearAppCache() {
        viewModelScope.launch {
            fileRepo.clearAppCache()
                .onSuccess { freed ->
                    showMessage("Cleared ${formatFileSize(freed)} of app cache")
                    refreshAll()
                }
                .onFailure { err -> showMessage(err.message ?: "Failed to clear cache") }
        }
    }

    fun cleanEmptyFolders() {
        val folders = _uiState.value.cleanSummary.emptyFolders.map { it.path }
        if (folders.isEmpty()) return
        deletePermanently(folders)
    }

    fun cleanDuplicateCopies(keepFirstOfAll: Boolean = true) {
        val pathsToTrash = _uiState.value.cleanSummary.duplicateGroups.flatMap { group ->
            if (keepFirstOfAll) group.files.drop(1).map { it.path } else emptyList()
        }
        if (pathsToTrash.isNotEmpty()) {
            moveToTrash(pathsToTrash)
        }
    }

    // Settings
    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setThemePreference(theme) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowHiddenFiles(show) }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { prefsRepo.setViewMode(mode) }
    }

    fun toggleViewMode() {
        val next = if (_uiState.value.settings.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        setViewMode(next)
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { prefsRepo.setSortOption(option) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setUseDynamicColor(enabled) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
