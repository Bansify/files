package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.model.CategorySummary
import com.example.model.CleanSummary
import com.example.model.DuplicateGroup
import com.example.model.FileCategory
import com.example.model.FileChecksum
import com.example.model.FileItem
import com.example.model.FolderSizeEntry
import com.example.model.SafeFolderRecord
import com.example.model.SortOption
import com.example.model.StorageVolumeInfo
import com.example.model.TrashRecord
import com.example.model.ZipEntryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileOperationsRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository
) {

    private val trashDir: File
        get() = File(context.filesDir, "bansify_trash").apply { if (!exists()) mkdirs() }

    private val safeVaultDir: File
        get() = File(context.filesDir, "bansify_safe_vault").apply { if (!exists()) mkdirs() }

    val workspaceDir: File
        get() {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "Bansify Workspace").apply { if (!exists()) mkdirs() }
        }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val ext = Environment.getExternalStorageDirectory()
            ext != null && ext.canRead() && ext.canWrite()
        }
    }

    fun getPrimaryExternalRoot(): File = Environment.getExternalStorageDirectory()

    fun getDefaultBrowseRoot(): File {
        return if (hasAllFilesAccess()) getPrimaryExternalRoot() else workspaceDir
    }

    suspend fun getStorageVolumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val extRoot = getPrimaryExternalRoot()
        val extStat = runCatching { StatFs(extRoot.absolutePath) }.getOrNull()
        val total = extStat?.totalBytes ?: 0L
        val free = extStat?.availableBytes ?: 0L

        volumes.add(
            StorageVolumeInfo(
                id = "internal",
                name = "Internal storage",
                path = extRoot.absolutePath,
                totalBytes = total,
                freeBytes = free,
                isAppWorkspace = false,
                isPrimary = true
            )
        )

        val ws = workspaceDir
        val wsUsed = calculateDirectorySize(ws, maxDepth = 4)
        volumes.add(
            StorageVolumeInfo(
                id = "workspace",
                name = "Bansify workspace",
                path = ws.absolutePath,
                totalBytes = total,
                freeBytes = (total - wsUsed).coerceAtLeast(0L),
                isAppWorkspace = true,
                isPrimary = false
            )
        )

        // Discover any additional removable external storage volumes
        context.getExternalFilesDirs(null)
            .drop(1)
            .filterNotNull()
            .forEachIndexed { idx, appDir ->
                val sdRoot = findStorageVolumeRoot(appDir)
                if (sdRoot != null && sdRoot.exists() && sdRoot.absolutePath != extRoot.absolutePath) {
                    val sdStat = runCatching { StatFs(sdRoot.absolutePath) }.getOrNull()
                    if (sdStat != null) {
                        volumes.add(
                            StorageVolumeInfo(
                                id = "sd_$idx",
                                name = "SD Card ${if (idx > 0) idx + 1 else ""}".trim(),
                                path = sdRoot.absolutePath,
                                totalBytes = sdStat.totalBytes,
                                freeBytes = sdStat.availableBytes,
                                isAppWorkspace = false,
                                isPrimary = false
                            )
                        )
                    }
                }
            }

        volumes
    }

    private fun findStorageVolumeRoot(appExternalDir: File): File? {
        val path = appExternalDir.absolutePath
        val marker = "/Android/data/"
        val index = path.indexOf(marker)
        return if (index > 0) File(path.substring(0, index)) else null
    }

    suspend fun listDirectory(
        dirPath: String,
        showHidden: Boolean,
        sortOption: SortOption
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val children = dir.listFiles() ?: return@withContext emptyList()
        val items = children
            .filter { showHidden || !it.name.startsWith(".") }
            .map { fileToItem(it) }
        sortFileItems(items, sortOption)
    }

    fun sortFileItems(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
        val comparator: Comparator<FileItem> = when (sortOption) {
            SortOption.NAME_ASC -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
            SortOption.NAME_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase(Locale.ROOT) }
            SortOption.DATE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified }
            SortOption.DATE_ASC -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified }
            SortOption.SIZE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.sizeBytes }
            SortOption.TYPE -> compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.extension.lowercase(Locale.ROOT) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        }
        return items.sortedWith(comparator)
    }

    suspend fun scanAllAccessibleFiles(showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val roots = buildList {
            if (hasAllFilesAccess()) {
                add(getPrimaryExternalRoot())
            } else {
                add(workspaceDir)
                // Also include public standard folders if readable without full access
                listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_MUSIC
                ).forEach { dirType ->
                    val pubDir = Environment.getExternalStoragePublicDirectory(dirType)
                    if (pubDir != null && pubDir.exists() && pubDir.canRead()) {
                        add(pubDir)
                    }
                }
            }
        }.distinctBy { it.absolutePath }

        val collected = mutableListOf<FileItem>()
        val seenPaths = HashSet<String>()
        val maxFiles = 6000

        fun walk(dir: File, depth: Int) {
            if (depth > 8 || collected.size >= maxFiles) return
            val name = dir.name
            if (!showHidden && name.startsWith(".") && depth > 0) return
            // Skip Android/data and Android/obb restricted dirs to keep scan crisp & fast
            if (dir.absolutePath.endsWith("/Android/data") || dir.absolutePath.endsWith("/Android/obb")) return

            val children = dir.listFiles() ?: return
            for (child in children) {
                if (collected.size >= maxFiles) break
                if (!showHidden && child.name.startsWith(".")) continue
                val abs = child.absolutePath
                if (child.isDirectory) {
                    walk(child, depth + 1)
                } else if (seenPaths.add(abs)) {
                    collected.add(fileToItem(child))
                }
            }
        }

        roots.forEach { walk(it, 0) }
        collected
    }

    suspend fun getCategorySummaries(allFiles: List<FileItem>): List<CategorySummary> = withContext(Dispatchers.Default) {
        val categories = listOf(
            FileCategory.DOWNLOADS,
            FileCategory.IMAGES,
            FileCategory.VIDEOS,
            FileCategory.AUDIO,
            FileCategory.DOCUMENTS,
            FileCategory.ARCHIVES,
            FileCategory.APPS
        )
        categories.map { cat ->
            val matched = filterFilesByCategory(allFiles, cat)
            CategorySummary(
                category = cat,
                count = matched.size,
                totalBytes = matched.sumOf { it.sizeBytes }
            )
        }
    }

    fun filterFilesByCategory(allFiles: List<FileItem>, category: FileCategory): List<FileItem> {
        return when (category) {
            FileCategory.DOWNLOADS -> allFiles.filter {
                it.path.lowercase(Locale.ROOT).contains("/download")
            }
            FileCategory.IMAGES -> allFiles.filter { it.isImage }
            FileCategory.VIDEOS -> allFiles.filter { it.isVideo }
            FileCategory.AUDIO -> allFiles.filter { it.isAudio }
            FileCategory.ARCHIVES -> allFiles.filter { it.isArchive }
            FileCategory.APPS -> allFiles.filter { it.isApk }
            FileCategory.DOCUMENTS -> allFiles.filter {
                !it.isImage && !it.isVideo && !it.isAudio && !it.isArchive && !it.isApk
            }
            FileCategory.RECENT -> allFiles.sortedByDescending { it.lastModified }.take(50)
        }
    }

    suspend fun searchFiles(
        query: String,
        categoryFilter: FileCategory?,
        showHidden: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty() && categoryFilter == null) return@withContext emptyList()
        val roots = if (hasAllFilesAccess()) listOf(getPrimaryExternalRoot()) else listOf(workspaceDir)
        val results = mutableListOf<FileItem>()
        val maxResults = 300

        fun walk(dir: File, depth: Int) {
            if (depth > 8 || results.size >= maxResults) return
            if (dir.absolutePath.endsWith("/Android/data") || dir.absolutePath.endsWith("/Android/obb")) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (results.size >= maxResults) break
                if (!showHidden && child.name.startsWith(".")) continue
                val matchesName = trimmed.isEmpty() || child.name.lowercase(Locale.ROOT).contains(trimmed)
                if (matchesName) {
                    val item = fileToItem(child)
                    val matchesCategory = categoryFilter == null || when (categoryFilter) {
                        FileCategory.IMAGES -> item.isImage
                        FileCategory.VIDEOS -> item.isVideo
                        FileCategory.AUDIO -> item.isAudio
                        FileCategory.ARCHIVES -> item.isArchive
                        FileCategory.APPS -> item.isApk
                        FileCategory.DOWNLOADS -> item.path.lowercase(Locale.ROOT).contains("/download")
                        FileCategory.DOCUMENTS -> !item.isDirectory && !item.isImage && !item.isVideo && !item.isAudio && !item.isArchive && !item.isApk
                        FileCategory.RECENT -> true
                    }
                    if (matchesCategory) {
                        results.add(item)
                    }
                }
                if (child.isDirectory) {
                    walk(child, depth + 1)
                }
            }
        }

        roots.forEach { walk(it, 0) }
        results.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
    }

    suspend fun createFolder(parentPath: String, folderName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = folderName.trim().replace("/", "_")
            require(sanitized.isNotEmpty()) { "Folder name cannot be empty" }
            val target = File(parentPath, sanitized)
            require(!target.exists()) { "A file or folder with that name already exists" }
            check(target.mkdirs()) { "Could not create folder here. Check storage permissions." }
            fileToItem(target)
        }
    }

    suspend fun createTextFile(parentPath: String, fileName: String, initialContent: String = ""): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = fileName.trim().replace("/", "_")
            require(sanitized.isNotEmpty()) { "File name cannot be empty" }
            val finalName = if (sanitized.contains('.')) sanitized else "$sanitized.txt"
            val target = File(parentPath, finalName)
            require(!target.exists()) { "A file with that name already exists" }
            target.writeText(initialContent, Charsets.UTF_8)
            fileToItem(target)
        }
    }

    suspend fun renameFile(filePath: String, newName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(filePath)
            require(source.exists()) { "Item no longer exists" }
            val sanitized = newName.trim().replace("/", "_")
            require(sanitized.isNotEmpty()) { "Name cannot be empty" }
            val target = File(source.parentFile, sanitized)
            require(!target.exists()) { "An item named \"$sanitized\" already exists" }
            check(source.renameTo(target)) { "Rename failed. Check storage permissions." }
            prefsRepo.updateFavoritePath(source.absolutePath, target.absolutePath)
            fileToItem(target)
        }
    }

    suspend fun copyFiles(sourcePaths: List<String>, destinationDirPath: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = File(destinationDirPath)
            require(destDir.exists() && destDir.isDirectory) { "Destination folder does not exist" }
            var count = 0
            for (srcPath in sourcePaths) {
                val src = File(srcPath)
                if (!src.exists()) continue
                if (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath)) {
                    throw IllegalArgumentException("Cannot copy a folder into itself")
                }
                val target = resolveUniqueTarget(destDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(target, overwrite = false)
                } else {
                    src.copyTo(target, overwrite = false)
                }
                count++
            }
            count
        }
    }

    suspend fun moveFiles(sourcePaths: List<String>, destinationDirPath: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = File(destinationDirPath)
            require(destDir.exists() && destDir.isDirectory) { "Destination folder does not exist" }
            var count = 0
            for (srcPath in sourcePaths) {
                val src = File(srcPath)
                if (!src.exists()) continue
                if (src.parentFile?.canonicalPath == destDir.canonicalPath) continue
                if (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath)) {
                    throw IllegalArgumentException("Cannot move a folder into itself")
                }
                val target = resolveUniqueTarget(destDir, src.name)
                val moved = src.renameTo(target)
                if (!moved) {
                    if (src.isDirectory) {
                        src.copyRecursively(target, overwrite = false)
                        src.deleteRecursively()
                    } else {
                        src.copyTo(target, overwrite = false)
                        src.delete()
                    }
                }
                prefsRepo.updateFavoritePath(src.absolutePath, target.absolutePath)
                count++
            }
            count
        }
    }

    suspend fun moveToTrash(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val records = prefsRepo.getTrashRecords().toMutableList()
            var movedCount = 0
            for (path in paths) {
                val src = File(path)
                if (!src.exists()) continue
                val id = UUID.randomUUID().toString()
                val trashName = "${id}_${src.name}"
                val trashTarget = File(trashDir, trashName)
                val size = if (src.isDirectory) calculateDirectorySize(src) else src.length()
                val renamed = src.renameTo(trashTarget)
                if (!renamed) {
                    if (src.isDirectory) {
                        src.copyRecursively(trashTarget, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.copyTo(trashTarget, overwrite = true)
                        src.delete()
                    }
                }
                records.add(
                    0,
                    TrashRecord(
                        id = id,
                        trashFileName = trashName,
                        originalPath = src.absolutePath,
                        originalName = src.name,
                        sizeBytes = size,
                        deletedAt = System.currentTimeMillis(),
                        isDirectory = trashTarget.isDirectory
                    )
                )
                prefsRepo.removeFavorite(src.absolutePath)
                movedCount++
            }
            prefsRepo.saveTrashRecords(records)
            movedCount
        }
    }

    suspend fun restoreFromTrash(record: TrashRecord): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val trashFile = File(trashDir, record.trashFileName)
            require(trashFile.exists()) { "Trashed item no longer exists on disk" }
            val origParent = File(record.originalPath).parentFile ?: workspaceDir
            if (!origParent.exists()) origParent.mkdirs()
            val targetDir = if (origParent.canWrite()) origParent else workspaceDir
            val target = resolveUniqueTarget(targetDir, record.originalName)
            val moved = trashFile.renameTo(target)
            if (!moved) {
                if (trashFile.isDirectory) {
                    trashFile.copyRecursively(target, overwrite = true)
                    trashFile.deleteRecursively()
                } else {
                    trashFile.copyTo(target, overwrite = true)
                    trashFile.delete()
                }
            }
            val updated = prefsRepo.getTrashRecords().filterNot { it.id == record.id }
            prefsRepo.saveTrashRecords(updated)
            target.absolutePath
        }
    }

    suspend fun deleteFromTrash(record: TrashRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trashFile = File(trashDir, record.trashFileName)
            if (trashFile.exists()) {
                if (trashFile.isDirectory) trashFile.deleteRecursively() else trashFile.delete()
            }
            val updated = prefsRepo.getTrashRecords().filterNot { it.id == record.id }
            prefsRepo.saveTrashRecords(updated)
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val records = prefsRepo.getTrashRecords()
            val count = records.size
            trashDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            prefsRepo.saveTrashRecords(emptyList())
            count
        }
    }

    suspend fun deletePermanently(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var deleted = 0
            for (path in paths) {
                val file = File(path)
                if (!file.exists()) continue
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (ok) {
                    prefsRepo.removeFavorite(path)
                    deleted++
                }
            }
            deleted
        }
    }

    suspend fun moveToSafeFolder(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val records = prefsRepo.getSafeRecords().toMutableList()
            var movedCount = 0
            for (path in paths) {
                val src = File(path)
                if (!src.exists()) continue
                val id = UUID.randomUUID().toString()
                val vaultName = "${id}_${src.name}"
                val vaultTarget = File(safeVaultDir, vaultName)
                val size = if (src.isDirectory) calculateDirectorySize(src) else src.length()
                val renamed = src.renameTo(vaultTarget)
                if (!renamed) {
                    if (src.isDirectory) {
                        src.copyRecursively(vaultTarget, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.copyTo(vaultTarget, overwrite = true)
                        src.delete()
                    }
                }
                records.add(
                    0,
                    SafeFolderRecord(
                        id = id,
                        vaultFileName = vaultName,
                        originalPath = src.absolutePath,
                        originalName = src.name,
                        sizeBytes = size,
                        movedAt = System.currentTimeMillis(),
                        isDirectory = vaultTarget.isDirectory
                    )
                )
                prefsRepo.removeFavorite(src.absolutePath)
                movedCount++
            }
            prefsRepo.saveSafeRecords(records)
            movedCount
        }
    }

    suspend fun moveOutOfSafeFolder(record: SafeFolderRecord): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val vaultFile = File(safeVaultDir, record.vaultFileName)
            require(vaultFile.exists()) { "Safe item no longer exists" }
            val origParent = File(record.originalPath).parentFile ?: workspaceDir
            if (!origParent.exists()) origParent.mkdirs()
            val targetDir = if (origParent.canWrite()) origParent else workspaceDir
            val target = resolveUniqueTarget(targetDir, record.originalName)
            val moved = vaultFile.renameTo(target)
            if (!moved) {
                if (vaultFile.isDirectory) {
                    vaultFile.copyRecursively(target, overwrite = true)
                    vaultFile.deleteRecursively()
                } else {
                    vaultFile.copyTo(target, overwrite = true)
                    vaultFile.delete()
                }
            }
            val updated = prefsRepo.getSafeRecords().filterNot { it.id == record.id }
            prefsRepo.saveSafeRecords(updated)
            target.absolutePath
        }
    }

    suspend fun deleteFromSafeFolder(record: SafeFolderRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val vaultFile = File(safeVaultDir, record.vaultFileName)
            if (vaultFile.exists()) {
                if (vaultFile.isDirectory) vaultFile.deleteRecursively() else vaultFile.delete()
            }
            val updated = prefsRepo.getSafeRecords().filterNot { it.id == record.id }
            prefsRepo.saveSafeRecords(updated)
        }
    }

    fun getSafeVaultFileItem(record: SafeFolderRecord): FileItem {
        val vaultFile = File(safeVaultDir, record.vaultFileName)
        val ext = record.originalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return FileItem(
            path = vaultFile.absolutePath,
            name = record.originalName,
            isDirectory = record.isDirectory,
            sizeBytes = if (vaultFile.exists() && !vaultFile.isDirectory) vaultFile.length() else record.sizeBytes,
            lastModified = record.movedAt,
            extension = ext,
            mimeType = getMimeType(ext, record.isDirectory),
            category = FileItem.detectCategory(ext, record.originalPath)
        )
    }

    suspend fun compressToZip(
        sourcePaths: List<String>,
        destinationDirPath: String,
        archiveName: String
    ): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = archiveName.trim().replace("/", "_")
            require(cleanName.isNotEmpty()) { "Archive name cannot be empty" }
            val finalName = if (cleanName.endsWith(".zip", ignoreCase = true)) cleanName else "$cleanName.zip"
            val destDir = File(destinationDirPath)
            require(destDir.exists() && destDir.isDirectory) { "Invalid destination directory" }
            val zipFile = resolveUniqueTarget(destDir, finalName)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                fun addFileToZip(file: File, baseName: String) {
                    if (file.isDirectory) {
                        val children = file.listFiles() ?: emptyArray()
                        if (children.isEmpty()) {
                            zos.putNextEntry(ZipEntry("$baseName/"))
                            zos.closeEntry()
                        } else {
                            children.forEach { child ->
                                addFileToZip(child, "$baseName/${child.name}")
                            }
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(baseName))
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            bis.copyTo(zos, bufferSize = 8192)
                        }
                        zos.closeEntry()
                    }
                }

                for (path in sourcePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        addFileToZip(file, file.name)
                    }
                }
            }
            fileToItem(zipFile)
        }
    }

    suspend fun inspectZipEntries(zipPath: String): Result<List<ZipEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = mutableListOf<ZipEntryInfo>()
            ZipInputStream(BufferedInputStream(FileInputStream(File(zipPath)))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && entries.size < 500) {
                    entries.add(
                        ZipEntryInfo(
                            name = entry.name,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory
                        )
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            entries
        }
    }

    suspend fun extractZip(zipPath: String, destinationDirPath: String? = null): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = File(zipPath)
            require(zipFile.exists()) { "Archive file not found" }
            val parentDir = destinationDirPath?.let { File(it) } ?: zipFile.parentFile ?: workspaceDir
            val folderBase = zipFile.nameWithoutExtension.ifBlank { "Extracted" }
            val extractRoot = resolveUniqueTarget(parentDir, folderBase)
            check(extractRoot.mkdirs()) { "Could not create extraction folder" }
            val canonicalRoot = extractRoot.canonicalPath + File.separator

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(extractRoot, entry.name)
                    val canonicalOut = outFile.canonicalPath
                    // Strict Zip Slip traversal protection
                    if (!canonicalOut.startsWith(canonicalRoot) && canonicalOut != extractRoot.canonicalPath) {
                        throw SecurityException("Blocked unsafe zip entry path: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                            zis.copyTo(bos, bufferSize = 8192)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            fileToItem(extractRoot)
        }
    }

    suspend fun importUrisToDirectory(uris: List<Uri>, destinationDirPath: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = File(destinationDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            var imported = 0
            for (uri in uris) {
                val displayName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
                val safeName = displayName.replace("/", "_")
                val target = resolveUniqueTarget(destDir, safeName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                    imported++
                }
            }
            imported
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    suspend fun scanCleanSummary(allFiles: List<FileItem>, showHidden: Boolean): CleanSummary = withContext(Dispatchers.IO) {
        // 1. Large files (> 5 MB, or top files > 1 MB if few large files exist)
        val largeThreshold = 5L * 1024L * 1024L
        val largeFiles = allFiles
            .filter { it.sizeBytes >= largeThreshold }
            .sortedByDescending { it.sizeBytes }
            .take(50)

        // 2. Old downloads (older than 30 days in Download folder)
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        val oldDownloads = allFiles
            .filter {
                it.path.lowercase(Locale.ROOT).contains("/download") &&
                    it.lastModified < thirtyDaysAgo
            }
            .sortedByDescending { it.sizeBytes }
            .take(40)

        // 3. Exact duplicate files (group by exact non-zero size, then verify by SHA-256)
        val candidatesBySize = allFiles
            .filter { it.sizeBytes > 0L && it.sizeBytes <= 100L * 1024L * 1024L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        for ((size, sameSizeFiles) in candidatesBySize) {
            val byHash = sameSizeFiles.groupBy { item ->
                runCatching { computeSingleHash(File(item.path), "SHA-256") }.getOrNull()
            }
            for ((hash, group) in byHash) {
                if (hash != null && group.size > 1) {
                    duplicateGroups.add(
                        DuplicateGroup(
                            hash = hash,
                            sizeBytes = size,
                            files = group.sortedBy { it.lastModified }
                        )
                    )
                }
            }
        }
        duplicateGroups.sortByDescending { it.reclaimableBytes }

        // 4. Empty folders
        val root = getDefaultBrowseRoot()
        val emptyFolders = mutableListOf<FileItem>()
        fun findEmpty(dir: File, depth: Int) {
            if (depth > 6 || emptyFolders.size >= 100) return
            if (dir.absolutePath.endsWith("/Android/data") || dir.absolutePath.endsWith("/Android/obb")) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    val sub = child.listFiles()
                    if (sub != null && sub.isEmpty()) {
                        emptyFolders.add(fileToItem(child))
                    } else if (sub != null) {
                        findEmpty(child, depth + 1)
                    }
                }
            }
        }
        findEmpty(root, 0)

        // 5. App cache size
        val cacheSize = calculateDirectorySize(context.cacheDir, maxDepth = 5) +
            (context.externalCacheDir?.let { calculateDirectorySize(it, maxDepth = 5) } ?: 0L)

        CleanSummary(
            largeFiles = largeFiles,
            duplicateGroups = duplicateGroups,
            emptyFolders = emptyFolders,
            cacheSizeBytes = cacheSize,
            oldDownloads = oldDownloads,
            isScanning = false
        )
    }

    suspend fun clearAppCache(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val before = calculateDirectorySize(context.cacheDir, maxDepth = 5) +
                (context.externalCacheDir?.let { calculateDirectorySize(it, maxDepth = 5) } ?: 0L)
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            before
        }
    }

    suspend fun computeTopFolderSizes(rootPath: String, showHidden: Boolean): List<FolderSizeEntry> = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val children = root.listFiles() ?: return@withContext emptyList()
        children
            .filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }
            .filterNot { it.name == "Android" }
            .map { folder ->
                var itemCount = 0
                val size = calculateDirectorySizeAndCount(folder, maxDepth = 5) { itemCount++ }
                FolderSizeEntry(
                    path = folder.absolutePath,
                    name = folder.name,
                    sizeBytes = size,
                    itemCount = itemCount
                )
            }
            .sortedByDescending { it.sizeBytes }
            .take(12)
    }

    suspend fun readTextFile(path: String, maxBytes: Int = 256 * 1024): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            require(file.exists() && file.isFile) { "File not found" }
            val truncated = file.length() > maxBytes
            val text = FileInputStream(file).use { fis ->
                val buffer = ByteArray(minOf(file.length().toInt(), maxBytes))
                val read = fis.read(buffer)
                if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
            }
            text to truncated
        }
    }

    suspend fun saveTextFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            file.writeText(content, Charsets.UTF_8)
        }
    }

    suspend fun computeChecksums(path: String): Result<FileChecksum> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            require(file.exists() && file.isFile) { "Cannot hash a folder or missing file" }
            val md5 = MessageDigest.getInstance("MD5")
            val sha256 = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val buffer = ByteArray(8192)
                var read = bis.read(buffer)
                while (read != -1) {
                    md5.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    read = bis.read(buffer)
                }
            }
            FileChecksum(
                md5 = md5.digest().joinToString("") { "%02x".format(it) },
                sha256 = sha256.digest().joinToString("") { "%02x".format(it) }
            )
        }
    }

    fun getFileProviderUri(path: String): Uri? {
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }.getOrNull()
    }

    fun fileToItem(file: File): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
        val childCount = if (isDir) file.list()?.size ?: 0 else 0
        return FileItem(
            path = file.absolutePath,
            name = file.name.ifEmpty { file.absolutePath },
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            extension = ext,
            mimeType = getMimeType(ext, isDir),
            childCount = childCount,
            category = FileItem.detectCategory(ext, file.parent ?: ""),
            isHidden = file.name.startsWith(".")
        )
    }

    fun getFavoriteItems(favoritePaths: Set<String>): List<FileItem> {
        return favoritePaths
            .mapNotNull { path ->
                val f = File(path)
                if (f.exists()) fileToItem(f) else null
            }
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    private fun computeSingleHash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(8192)
            var read = bis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = bis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateDirectorySize(dir: File, maxDepth: Int = 5): Long {
        return calculateDirectorySizeAndCount(dir, maxDepth) {}
    }

    private fun calculateDirectorySizeAndCount(
        dir: File,
        maxDepth: Int,
        onItem: () -> Unit
    ): Long {
        if (maxDepth < 0 || !dir.exists()) return 0L
        if (!dir.isDirectory) return dir.length()
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            onItem()
            total += if (child.isDirectory) {
                calculateDirectorySizeAndCount(child, maxDepth - 1, onItem)
            } else {
                child.length()
            }
        }
        return total
    }

    private fun resolveUniqueTarget(destDir: File, originalName: String): File {
        var candidate = File(destDir, originalName)
        if (!candidate.exists()) return candidate
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(destDir, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun getMimeType(ext: String, isDir: Boolean): String {
        if (isDir) return "inode/directory"
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "md" -> "text/markdown"
                "json" -> "application/json"
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "rar" -> "application/vnd.rar"
                "7z" -> "application/x-7z-compressed"
                else -> "application/octet-stream"
            }
    }
}
