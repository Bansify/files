package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.CategorySummary
import com.example.model.ClipboardOperation
import com.example.model.ClipboardState
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortOption
import com.example.model.StorageVolumeInfo
import com.example.model.formatFileSize
import com.example.ui.theme.CategoryAmber
import com.example.ui.theme.CategoryBlue
import com.example.ui.theme.CategoryCoral
import com.example.ui.theme.CategoryEmerald
import com.example.ui.theme.CategoryIndigo
import com.example.ui.theme.CategorySlate
import com.example.ui.theme.CategoryTeal
import com.example.ui.theme.CategoryViolet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategoryVisual(
    val icon: ImageVector,
    val accent: Color
)

fun categoryVisual(category: FileCategory): CategoryVisual = when (category) {
    FileCategory.DOWNLOADS -> CategoryVisual(Icons.Filled.Download, CategoryTeal)
    FileCategory.IMAGES -> CategoryVisual(Icons.Filled.Image, CategoryEmerald)
    FileCategory.VIDEOS -> CategoryVisual(Icons.Filled.VideoFile, CategoryCoral)
    FileCategory.AUDIO -> CategoryVisual(Icons.Filled.AudioFile, CategoryAmber)
    FileCategory.DOCUMENTS -> CategoryVisual(Icons.Filled.Description, CategoryBlue)
    FileCategory.ARCHIVES -> CategoryVisual(Icons.Filled.FolderZip, CategoryViolet)
    FileCategory.APPS -> CategoryVisual(Icons.Filled.Android, CategoryIndigo)
    FileCategory.RECENT -> CategoryVisual(Icons.Filled.Schedule, CategorySlate)
}

fun fileItemVisual(file: FileItem): CategoryVisual {
    if (file.isDirectory) return CategoryVisual(Icons.Filled.Folder, CategoryBlue)
    return when {
        file.isImage -> CategoryVisual(Icons.Filled.Image, CategoryEmerald)
        file.isVideo -> CategoryVisual(Icons.Filled.VideoFile, CategoryCoral)
        file.isAudio -> CategoryVisual(Icons.Filled.AudioFile, CategoryAmber)
        file.isArchive -> CategoryVisual(Icons.Filled.FolderZip, CategoryViolet)
        file.isApk -> CategoryVisual(Icons.Filled.Android, CategoryIndigo)
        file.extension == "pdf" -> CategoryVisual(Icons.Filled.PictureAsPdf, CategoryCoral)
        file.extension in setOf("kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "sh") ->
            CategoryVisual(Icons.Filled.Code, CategoryTeal)
        file.extension in FileItem.DOCUMENT_EXTENSIONS -> CategoryVisual(Icons.Filled.Description, CategoryBlue)
        else -> CategoryVisual(Icons.AutoMirrored.Filled.InsertDriveFile, CategorySlate)
    }
}

fun formatShortDate(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(millis))
}

@Composable
fun FileThumbnailBadge(
    file: FileItem,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val visual = fileItemVisual(file)
    val shape = RoundedCornerShape(cornerRadius)

    if (file.isImage) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(file.path))
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(visual.accent.copy(alpha = 0.12f))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(visual.accent.copy(alpha = 0.13f))
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = file.name,
                tint = visual.accent,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    subtitleOverride: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box {
            FileThumbnailBadge(file = file, size = 46.dp, cornerRadius = 12.dp)
            if (isFavorite) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = CategoryAmber,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val meta = subtitleOverride ?: buildString {
                if (file.isDirectory) {
                    append("${file.childCount} item${if (file.childCount == 1) "" else "s"}")
                } else {
                    append(formatFileSize(file.sizeBytes))
                }
                val date = formatShortDate(file.lastModified)
                if (date.isNotEmpty()) {
                    append(" • ")
                    append(date)
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(24.dp)
            )
        } else {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("file_more_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options for ${file.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = fileItemVisual(file)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(visual.accent.copy(alpha = 0.1f))
            ) {
                if (file.isImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path))
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = file.name,
                        tint = visual.accent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                    )
                } else {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = if (file.isImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = CategoryAmber,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (file.isDirectory) "${file.childCount} items" else formatFileSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StorageUsageHeroCard(
    volume: StorageVolumeInfo,
    categorySummaries: List<CategorySummary>,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val totalUsed = volume.usedBytes.coerceAtLeast(1L)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${formatFileSize(volume.usedBytes)} used",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatFileSize(volume.freeBytes)} free of ${formatFileSize(volume.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(volume.usedFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))

            // Multi-segment storage progress bar
            val segments = listOf(
                FileCategory.IMAGES to CategoryEmerald,
                FileCategory.VIDEOS to CategoryCoral,
                FileCategory.AUDIO to CategoryAmber,
                FileCategory.DOCUMENTS to CategoryBlue,
                FileCategory.ARCHIVES to CategoryViolet
            )
            val categorizedTotal = categorySummaries.sumOf { it.totalBytes }
            val otherUsed = (volume.usedBytes - categorizedTotal).coerceAtLeast(0L)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (volume.totalBytes > 0L) {
                    segments.forEach { (cat, color) ->
                        val catBytes = categorySummaries.firstOrNull { it.category == cat }?.totalBytes ?: 0L
                        val fraction = (catBytes.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
                        if (fraction > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .weight(fraction)
                                    .fillMaxSize()
                                    .background(color)
                            )
                        }
                    }
                    val otherFraction = (otherUsed.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
                    if (otherFraction > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(otherFraction)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                        )
                    }
                    val freeFraction = (volume.freeBytes.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
                    if (freeFraction > 0.005f) {
                        Spacer(modifier = Modifier.weight(freeFraction))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(totalUsed.toFloat())
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionAccessCard(
    onGrantAccess: () -> Unit,
    onImportFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Access all device files",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Allow Bansify Files to browse Internal Storage, Downloads, Documents, and Media folders, or import files directly into your workspace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGrantAccess,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("grant_all_files_button")
                ) {
                    Text("Grant access")
                }
                OutlinedButton(
                    onClick = onImportFiles,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("import_files_banner_button")
                ) {
                    Text("Import files")
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    rootPath: String,
    rootLabel: String,
    onNavigatePath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = buildList {
        add(rootLabel to rootPath)
        if (currentPath.startsWith(rootPath) && currentPath.length > rootPath.length) {
            val relative = currentPath.removePrefix(rootPath).trimStart('/')
            var running = rootPath
            relative.split('/').filter { it.isNotEmpty() }.forEach { part ->
                running = "$running/$part"
                add(part to running)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        segments.forEachIndexed { index, (label, path) ->
            val isLast = index == segments.lastIndex
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isLast) { onNavigatePath(path) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("clear_selection_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("select_all_button")
            ) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun SelectionBottomActionBar(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onShare: () -> Unit,
    onMoveToSafe: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            SelectionActionItem(Icons.Filled.ContentCopy, "Copy", onCopy)
            SelectionActionItem(Icons.Filled.DriveFileMove, "Move", onMove)
            SelectionActionItem(Icons.Filled.FolderZip, "Zip", onCompress)
            SelectionActionItem(Icons.Filled.Share, "Share", onShare)
            SelectionActionItem(Icons.Filled.Lock, "Safe", onMoveToSafe)
            SelectionActionItem(Icons.Filled.DeleteOutline, "Delete", onDelete, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelectionActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun ClipboardBanner(
    clipboard: ClipboardState?,
    onPasteHere: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = clipboard != null, modifier = modifier) {
        if (clipboard != null) {
            val actionLabel = if (clipboard.operation == ClipboardOperation.COPY) "Copy here" else "Move here"
            val verb = if (clipboard.operation == ClipboardOperation.COPY) "Copying" else "Moving"
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$verb ${clipboard.paths.size} item${if (clipboard.paths.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    FilledTonalButton(
                        onClick = onPasteHere,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("paste_here_button")
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun SortMenuDropdown(
    expanded: Boolean,
    currentSort: SortOption,
    onDismiss: () -> Unit,
    onSelectSort: (SortOption) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = option.label,
                        fontWeight = if (option == currentSort) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (option == currentSort) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onSelectSort(option)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
