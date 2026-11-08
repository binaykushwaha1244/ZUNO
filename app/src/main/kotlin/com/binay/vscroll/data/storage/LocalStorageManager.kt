package com.binay.vscroll.data.storage

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.binay.vscroll.data.local.dao.VideoInteractionDao
import com.binay.vscroll.data.local.dao.WatchHistoryDao
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface StorageOperationResult {
    data class Success(val affectedCount: Int, val message: String) : StorageOperationResult
    data class RequiresUserConsent(
        val intentSender: IntentSender,
        val pendingAction: PendingStorageAction
    ) : StorageOperationResult
    data class Failure(val error: Throwable) : StorageOperationResult
}

sealed interface PendingStorageAction {
    data class DeleteVideos(val videoIds: List<Long>, val uris: List<Uri>) : PendingStorageAction
    data class MoveVideos(
        val videoIds: List<Long>,
        val uris: List<Uri>,
        val targetRelativePath: String,
        val targetFolderName: String
    ) : PendingStorageAction
}

class LocalStorageManager(
    private val context: Context,
    private val interactionDao: VideoInteractionDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private val INVALID_FOLDER_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

        fun sanitizeFolderName(name: String): String {
            return name.filter { it !in INVALID_FOLDER_CHARS }.trim()
        }

        fun validateFolderName(name: String): Result<String> {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Folder name cannot be empty"))
            }
            if (trimmed.any { it in INVALID_FOLDER_CHARS }) {
                return Result.failure(IllegalArgumentException("Folder name contains invalid characters (/ \\ : * ? \" < > |)"))
            }
            if (trimmed == "." || trimmed == "..") {
                return Result.failure(IllegalArgumentException("Invalid folder name"))
            }
            return Result.success(trimmed)
        }
    }

    /**
     * Creates a new physical folder in the accessible standard Movies directory.
     */
    suspend fun createPhysicalFolder(folderName: String): Result<File> = withContext(ioDispatcher) {
        val validation = validateFolderName(folderName)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull()!!)
        }
        val cleanName = validation.getOrThrow()

        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val targetDir = File(moviesDir, cleanName)
            if (targetDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Folder '$cleanName' already exists"))
            }

            val created = targetDir.mkdirs()
            if (!created && !targetDir.exists()) {
                return@withContext Result.failure(IllegalStateException("Could not create directory at ${targetDir.absolutePath}"))
            }

            // Trigger MediaScanner scan
            MediaScannerConnection.scanFile(context, arrayOf(targetDir.absolutePath), null, null)
            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renames an existing physical folder where permitted by Android storage permissions.
     */
    suspend fun renamePhysicalFolder(
        currentFolder: FolderItem,
        newFolderName: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val validation = validateFolderName(newFolderName)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull()!!)
        }
        val cleanName = validation.getOrThrow()

        if (currentFolder.name.equals(cleanName, ignoreCase = true)) {
            return@withContext Result.success(Unit)
        }

        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val currentDir = File(moviesDir, currentFolder.name)
            val targetDir = File(moviesDir, cleanName)

            if (currentDir.exists()) {
                if (targetDir.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("A folder named '$cleanName' already exists"))
                }
                val renamed = currentDir.renameTo(targetDir)
                if (renamed) {
                    MediaScannerConnection.scanFile(context, arrayOf(targetDir.absolutePath, currentDir.absolutePath), null, null)
                    // Reconcile database records with the new folder name
                    reconcileFolderRename(currentFolder.name, cleanName)
                    return@withContext Result.success(Unit)
                }
            }

            // If direct File.renameTo fails or file is located elsewhere in scoped storage,
            // explain the scoped storage boundary transparently.
            Result.failure(
                SecurityException(
                    "Android Scoped Storage protects system folders. Folder '${currentFolder.name}' could not be renamed directly on device."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an eligible physical folder if empty or confirmed.
     */
    suspend fun deletePhysicalFolder(folder: FolderItem): Result<Unit> = withContext(ioDispatcher) {
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val dir = File(moviesDir, folder.name)
            if (!dir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Folder does not exist"))
            }
            val files = dir.listFiles()
            if (!files.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Folder contains ${files.size} files. Please move or delete files first.")
                )
            }
            val deleted = dir.delete()
            if (deleted) {
                MediaScannerConnection.scanFile(context, arrayOf(dir.absolutePath), null, null)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Could not delete folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prepares video deletion using Android scoped storage best practices.
     * On Android 11+ (API 30+), returns IntentSender to show system confirmation dialog.
     */
    suspend fun requestDeleteVideos(videos: List<VideoItem>): StorageOperationResult = withContext(ioDispatcher) {
        if (videos.isEmpty()) return@withContext StorageOperationResult.Success(0, "No videos selected")

        val uris = videos.map { video ->
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
        }
        val videoIds = videos.map { it.id }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                StorageOperationResult.RequiresUserConsent(
                    intentSender = pendingIntent.intentSender,
                    pendingAction = PendingStorageAction.DeleteVideos(videoIds, uris)
                )
            } catch (e: Exception) {
                StorageOperationResult.Failure(e)
            }
        } else {
            // Android 10 and below
            var deletedCount = 0
            for (uri in uris) {
                try {
                    val count = contentResolver.delete(uri, null, null)
                    deletedCount += count
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        return@withContext StorageOperationResult.RequiresUserConsent(
                            intentSender = e.userAction.actionIntent.intentSender,
                            pendingAction = PendingStorageAction.DeleteVideos(videoIds, uris)
                        )
                    }
                    return@withContext StorageOperationResult.Failure(e)
                }
            }
            StorageOperationResult.Success(deletedCount, "$deletedCount video(s) deleted")
        }
    }

    /**
     * Moves videos to target folder in MediaStore via RELATIVE_PATH on API 29+.
     */
    suspend fun requestMoveVideos(
        videos: List<VideoItem>,
        targetRelativePath: String,
        targetFolderName: String
    ): StorageOperationResult = withContext(ioDispatcher) {
        if (videos.isEmpty()) return@withContext StorageOperationResult.Success(0, "No videos selected")

        val cleanRelPath = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
        val uris = videos.map { ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.id) }
        val videoIds = videos.map { it.id }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                var movedCount = 0
                for (video in videos) {
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.RELATIVE_PATH, cleanRelPath)
                    }
                    val updated = contentResolver.update(uri, values, null, null)
                    if (updated > 0) {
                        movedCount++
                    }
                }

                if (movedCount > 0) {
                    reconcileVideosMoved(videoIds, cleanRelPath, targetFolderName)
                    return@withContext StorageOperationResult.Success(
                        movedCount,
                        "Moved $movedCount video(s) to $targetFolderName"
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Requires write consent
                    val pendingIntent = MediaStore.createWriteRequest(contentResolver, uris)
                    return@withContext StorageOperationResult.RequiresUserConsent(
                        intentSender = pendingIntent.intentSender,
                        pendingAction = PendingStorageAction.MoveVideos(videoIds, uris, cleanRelPath, targetFolderName)
                    )
                }
                StorageOperationResult.Failure(IllegalStateException("Could not update media paths"))
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createWriteRequest(contentResolver, uris)
                    return@withContext StorageOperationResult.RequiresUserConsent(
                        intentSender = pendingIntent.intentSender,
                        pendingAction = PendingStorageAction.MoveVideos(videoIds, uris, cleanRelPath, targetFolderName)
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return@withContext StorageOperationResult.RequiresUserConsent(
                        intentSender = e.userAction.actionIntent.intentSender,
                        pendingAction = PendingStorageAction.MoveVideos(videoIds, uris, cleanRelPath, targetFolderName)
                    )
                }
                StorageOperationResult.Failure(e)
            } catch (e: Exception) {
                StorageOperationResult.Failure(e)
            }
        } else {
            StorageOperationResult.Failure(
                UnsupportedOperationException("Moving videos via Scoped Storage requires Android 10 or higher")
            )
        }
    }

    /**
     * Executes post-consent move operation once user has approved write request.
     */
    suspend fun executePostConsentMove(
        videoIds: List<Long>,
        uris: List<Uri>,
        targetRelativePath: String,
        targetFolderName: String
    ): Result<Int> = withContext(ioDispatcher) {
        val cleanRelPath = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
        var moved = 0
        try {
            for (uri in uris) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.RELATIVE_PATH, cleanRelPath)
                }
                val count = contentResolver.update(uri, values, null, null)
                if (count > 0) moved++
            }
            reconcileVideosMoved(videoIds, cleanRelPath, targetFolderName)
            Result.success(moved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun reconcileFolderRename(oldFolderName: String, newFolderName: String) {
        // Update interactions
        try {
            val allInteractions = interactionDao.getAllInteractionsSync()
            for (interaction in allInteractions) {
                if (interaction.cachedFolderName.equals(oldFolderName, ignoreCase = true)) {
                    interactionDao.insertOrUpdate(
                        interaction.copy(
                            cachedFolderName = newFolderName,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (ignored: Exception) {}

        // Update watch history
        try {
            val allHistory = watchHistoryDao.getAllHistorySync()
            for (entry in allHistory) {
                if (entry.cachedFolderName.equals(oldFolderName, ignoreCase = true)) {
                    watchHistoryDao.insertOrReplace(
                        entry.copy(cachedFolderName = newFolderName)
                    )
                }
            }
        } catch (ignored: Exception) {}
    }

    private suspend fun reconcileVideosMoved(
        videoIds: List<Long>,
        newRelativePath: String,
        newFolderName: String
    ) {
        for (id in videoIds) {
            try {
                val interaction = interactionDao.getInteractionSync(id)
                if (interaction != null) {
                    interactionDao.insertOrUpdate(
                        interaction.copy(
                            cachedFolderName = newFolderName,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                val history = watchHistoryDao.getWatchHistory(id)
                if (history != null) {
                    watchHistoryDao.insertOrReplace(
                        history.copy(cachedFolderName = newFolderName)
                    )
                }
            } catch (ignored: Exception) {}
        }
    }
}
