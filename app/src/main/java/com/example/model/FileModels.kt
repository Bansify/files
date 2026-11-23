package com.example.model

import java.util.Locale

enum class MainTab {
    CLEAN,
    BROWSE,
    TOOLS
}

enum class FileCategory(val displayName: String) {
    DOWNLOADS("Downloads"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APPS("Apps (APKs)"),
    RECENT("Recent")
}

enum class SortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    TYPE("File type")
}

enum class ViewMode {
    LIST,
    GRID
}

enum class ThemePreference(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ClipboardOperation {
    COPY,
    MOVE
}

data class ClipboardState(
    val paths: List<String>,
    val operation: ClipboardOperation
)

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val childCount: Int = 0,
    val category: FileCategory = FileCategory.DOCUMENTS,
    val isHidden: Boolean = false
) {
    val isImage: Boolean get() = !isDirectory && extension in IMAGE_EXTENSIONS
    val isVideo: Boolean get() = !isDirectory && extension in VIDEO_EXTENSIONS
    val isAudio: Boolean get() = !isDirectory && extension in AUDIO_EXTENSIONS
    val isArchive: Boolean get() = !isDirectory && extension in ARCHIVE_EXTENSIONS
    val isZip: Boolean get() = !isDirectory && extension == "zip"
    val isApk: Boolean get() = !isDirectory && extension == "apk"
    val isTextEditable: Boolean get() = !isDirectory && extension in TEXT_EXTENSIONS

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v")
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus", "amr")
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md",
            "csv", "rtf", "odt", "epub", "json", "xml", "html", "htm", "log",
            "kt", "java", "py", "js", "ts", "c", "cpp", "h", "css", "yaml", "yml", "ini", "sh"
        )
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "csv", "log", "html", "htm", "css",
            "kt", "java", "py", "js", "ts", "c", "cpp", "h", "yaml", "yml", "ini", "cfg", "properties", "sh"
        )

        fun detectCategory(extension: String, parentPath: String): FileCategory {
            val ext = extension.lowercase(Locale.ROOT)
            return when {
                ext in IMAGE_EXTENSIONS -> FileCategory.IMAGES
                ext in VIDEO_EXTENSIONS -> FileCategory.VIDEOS
                ext in AUDIO_EXTENSIONS -> FileCategory.AUDIO
                ext in ARCHIVE_EXTENSIONS -> FileCategory.ARCHIVES
                ext == "apk" -> FileCategory.APPS
                parentPath.lowercase(Locale.ROOT).contains("/download") -> FileCategory.DOWNLOADS
                else -> FileCategory.DOCUMENTS
            }
        }
    }
}

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isAppWorkspace: Boolean = false,
    val isPrimary: Boolean = true
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class CategorySummary(
    val category: FileCategory,
    val count: Int,
    val totalBytes: Long
)

data class TrashRecord(
    val id: String,
    val trashFileName: String,
    val originalPath: String,
    val originalName: String,
    val sizeBytes: Long,
    val deletedAt: Long,
    val isDirectory: Boolean
)

data class SafeFolderRecord(
    val id: String,
    val vaultFileName: String,
    val originalPath: String,
    val originalName: String,
    val sizeBytes: Long,
    val movedAt: Long,
    val isDirectory: Boolean
)

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val files: List<FileItem>
) {
    val reclaimableBytes: Long get() = sizeBytes * (files.size - 1).coerceAtLeast(0)
}

data class FolderSizeEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val itemCount: Int
)

data class CleanSummary(
    val largeFiles: List<FileItem> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val emptyFolders: List<FileItem> = emptyList(),
    val cacheSizeBytes: Long = 0L,
    val oldDownloads: List<FileItem> = emptyList(),
    val isScanning: Boolean = false
) {
    val totalLargeBytes: Long get() = largeFiles.sumOf { it.sizeBytes }
    val totalDuplicateReclaimableBytes: Long get() = duplicateGroups.sumOf { it.reclaimableBytes }
    val totalOldDownloadsBytes: Long get() = oldDownloads.sumOf { it.sizeBytes }
    val totalReclaimableEstimate: Long
        get() = cacheSizeBytes + totalDuplicateReclaimableBytes + totalLargeBytes
}

data class ZipEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean
)

data class FileChecksum(
    val md5: String,
    val sha256: String
)

sealed interface ActiveScreen {
    data object Home : ActiveScreen
    data class Directory(val path: String, val rootPath: String, val rootLabel: String) : ActiveScreen
    data class Category(val category: FileCategory) : ActiveScreen
    data object Search : ActiveScreen
    data object Favorites : ActiveScreen
    data object SafeFolder : ActiveScreen
    data object Trash : ActiveScreen
    data object StorageAnalyzer : ActiveScreen
    data object Settings : ActiveScreen
}

sealed interface PreviewModal {
    data class ImageViewer(val file: FileItem) : PreviewModal
    data class TextEditor(val file: FileItem, val content: String, val isTruncated: Boolean) : PreviewModal
    data class AudioPlayer(val file: FileItem) : PreviewModal
    data class ZipInspector(val file: FileItem, val entries: List<ZipEntryInfo>) : PreviewModal
    data class PropertiesSheet(val file: FileItem, val checksum: FileChecksum? = null, val isComputingChecksum: Boolean = false) : PreviewModal
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}
