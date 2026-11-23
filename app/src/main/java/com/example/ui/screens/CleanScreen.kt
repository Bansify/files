package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileItem
import com.example.model.formatFileSize
import com.example.ui.components.FileThumbnailBadge
import com.example.ui.components.StorageUsageHeroCard
import com.example.ui.theme.CategoryAmber
import com.example.ui.theme.CategoryBlue
import com.example.ui.theme.CategoryCoral
import com.example.ui.theme.CategoryEmerald
import com.example.ui.theme.CategoryTeal
import com.example.ui.theme.CategoryViolet
import com.example.viewmodel.FileManagerUiState

@Composable
fun CleanScreen(
    state: FileManagerUiState,
    onRefreshScan: () -> Unit,
    onClearCache: () -> Unit,
    onCleanDuplicates: () -> Unit,
    onCleanEmptyFolders: () -> Unit,
    onTrashFile: (FileItem) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandLarge by remember { mutableStateOf(false) }
    var expandDuplicates by remember { mutableStateOf(false) }
    var expandOldDownloads by remember { mutableStateOf(false) }

    val primaryVolume = state.storageVolumes.firstOrNull { it.isPrimary } ?: state.storageVolumes.firstOrNull()
    val clean = state.cleanSummary

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Clean",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep your storage fast and clutter-free",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshScan,
                        modifier = Modifier.testTag("clean_rescan_button")
                    ) {
                        if (clean.isScanning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Rescan storage"
                            )
                        }
                    }
                }
            }

            // Storage Hero Card
            if (primaryVolume != null) {
                item {
                    StorageUsageHeroCard(
                        volume = primaryVolume,
                        categorySummaries = state.categorySummaries,
                        onClick = onOpenStorageAnalyzer
                    )
                }
            }

            // App Cache Cleaner Card
            item {
                CleanActionCard(
                    icon = Icons.Filled.CleaningServices,
                    accent = CategoryBlue,
                    title = "App cache & temporary files",
                    description = if (clean.cacheSizeBytes > 0L) {
                        "Clear ${formatFileSize(clean.cacheSizeBytes)} of temporary thumbnails and cache."
                    } else {
                        "Temporary cache is already clean."
                    },
                    primaryActionLabel = if (clean.cacheSizeBytes > 0L) {
                        "Clean ${formatFileSize(clean.cacheSizeBytes)}"
                    } else null,
                    onPrimaryAction = onClearCache,
                    testTag = "clean_cache_button"
                )
            }

            // Duplicate Files Card
            if (clean.duplicateGroups.isNotEmpty()) {
                val duplicateCopyCount = clean.duplicateGroups.sumOf { (it.files.size - 1).coerceAtLeast(0) }
                item {
                    CleanActionCard(
                        icon = Icons.Filled.ContentCopy,
                        accent = CategoryAmber,
                        title = "Delete duplicates",
                        description = "$duplicateCopyCount exact duplicate file${if (duplicateCopyCount == 1) "" else "s"} verified via SHA-256 • Free up ${formatFileSize(clean.totalDuplicateReclaimableBytes)}.",
                        primaryActionLabel = "Move duplicates to Trash",
                        onPrimaryAction = onCleanDuplicates,
                        secondaryActionLabel = if (expandDuplicates) "Hide files" else "Review files",
                        onSecondaryAction = { expandDuplicates = !expandDuplicates },
                        testTag = "clean_duplicates_button"
                    ) {
                        AnimatedVisibility(visible = expandDuplicates) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                clean.duplicateGroups.take(8).forEach { group ->
                                    Text(
                                        text = "${group.files.size} copies • ${formatFileSize(group.sizeBytes)} each",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CategoryAmber,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    group.files.forEachIndexed { idx, file ->
                                        CleanFileItemRow(
                                            file = file,
                                            badge = if (idx == 0) "Original" else "Duplicate",
                                            onOpen = { onOpenFile(file) },
                                            onTrash = if (idx > 0) ({ onTrashFile(file) }) else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Large Files Card
            if (clean.largeFiles.isNotEmpty()) {
                item {
                    CleanActionCard(
                        icon = Icons.Filled.InsertDriveFile,
                        accent = CategoryCoral,
                        title = "Review large files",
                        description = "${clean.largeFiles.size} file${if (clean.largeFiles.size == 1) "" else "s"} over 5 MB totaling ${formatFileSize(clean.totalLargeBytes)}.",
                        primaryActionLabel = if (expandLarge) "Collapse list" else "Review large files",
                        onPrimaryAction = { expandLarge = !expandLarge },
                        testTag = "clean_large_files_button"
                    ) {
                        AnimatedVisibility(visible = expandLarge) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                clean.largeFiles.take(15).forEach { file ->
                                    CleanFileItemRow(
                                        file = file,
                                        badge = formatFileSize(file.sizeBytes),
                                        onOpen = { onOpenFile(file) },
                                        onTrash = { onTrashFile(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Empty Folders Card
            if (clean.emptyFolders.isNotEmpty()) {
                item {
                    CleanActionCard(
                        icon = Icons.Filled.FolderOff,
                        accent = CategoryViolet,
                        title = "Remove empty folders",
                        description = "Found ${clean.emptyFolders.size} empty folder${if (clean.emptyFolders.size == 1) "" else "s"} taking up directory clutter.",
                        primaryActionLabel = "Delete ${clean.emptyFolders.size} folders",
                        onPrimaryAction = onCleanEmptyFolders,
                        testTag = "clean_empty_folders_button"
                    )
                }
            }

            // Old Downloads Card
            if (clean.oldDownloads.isNotEmpty()) {
                item {
                    CleanActionCard(
                        icon = Icons.Filled.Download,
                        accent = CategoryTeal,
                        title = "Old downloads",
                        description = "${clean.oldDownloads.size} downloaded files older than 30 days (${formatFileSize(clean.totalOldDownloadsBytes)}).",
                        primaryActionLabel = if (expandOldDownloads) "Hide downloads" else "Review downloads",
                        onPrimaryAction = { expandOldDownloads = !expandOldDownloads },
                        testTag = "clean_old_downloads_button"
                    ) {
                        AnimatedVisibility(visible = expandOldDownloads) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                clean.oldDownloads.take(12).forEach { file ->
                                    CleanFileItemRow(
                                        file = file,
                                        badge = formatFileSize(file.sizeBytes),
                                        onOpen = { onOpenFile(file) },
                                        onTrash = { onTrashFile(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean state card if no major suggestions
            if (!clean.isScanning &&
                clean.duplicateGroups.isEmpty() &&
                clean.largeFiles.isEmpty() &&
                clean.emptyFolders.isEmpty() &&
                clean.oldDownloads.isEmpty()
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CategoryEmerald.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = CategoryEmerald,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Everything looks clean!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "No duplicate files, large clutter, or empty folders detected in accessible storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CleanActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    primaryActionLabel: String?,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    testTag: String,
    expandedContent: @Composable () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.13f))
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (primaryActionLabel != null || secondaryActionLabel != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (primaryActionLabel != null) {
                        FilledTonalButton(
                            onClick = onPrimaryAction,
                            modifier = Modifier.testTag(testTag)
                        ) {
                            Text(primaryActionLabel)
                        }
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OutlinedButton(onClick = onSecondaryAction) {
                            Text(secondaryActionLabel)
                        }
                    }
                }
            }

            expandedContent()
        }
    }
}

@Composable
private fun CleanFileItemRow(
    file: FileItem,
    badge: String,
    onOpen: () -> Unit,
    onTrash: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        FileThumbnailBadge(file = file, size = 38.dp, cornerRadius = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onTrash != null) {
            IconButton(onClick = onTrash) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = "Move ${file.name} to Trash",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
