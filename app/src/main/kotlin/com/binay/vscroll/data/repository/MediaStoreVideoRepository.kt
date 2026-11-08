package com.binay.vscroll.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class MediaStoreVideoRepository(
    private val context: Context
) : VideoRepository {

    private val contentResolver = context.contentResolver
    private val refreshTrigger = MutableStateFlow(0L)

    // Flow that observes MediaStore content changes and emits whenever local storage updates
    private val mediaStoreChangeFlow: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (ignored: Exception) {}

        // Initial emission
        trySend(Unit)

        awaitClose {
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (ignored: Exception) {}
        }
    }

    // Merged stream of OS ContentObserver events and explicit user rescan requests
    private val updatesFlow: Flow<Unit> = merge(mediaStoreChangeFlow, refreshTrigger.map { Unit })

    override fun getVideos(sortOrder: VideoSortOrder): Flow<List<VideoItem>> {
        return updatesFlow.map {
            queryMediaStoreVideos(sortOrder)
        }.flowOn(Dispatchers.IO)
    }

    override fun getFolders(): Flow<List<FolderItem>> {
        return updatesFlow.map {
            val allVideos = queryMediaStoreVideos(VideoSortOrder.NEWEST_FIRST)
            groupVideosIntoFolders(allVideos)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun refreshVideos() = withContext(Dispatchers.IO) {
        // Triggers query update across active subscribers immediately
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun queryMediaStoreVideos(sortOrder: VideoSortOrder): List<VideoItem> {
        val videoList = mutableListOf<VideoItem>()

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Video.Media.DATA)
        }

        val sqlSortOrder = when (sortOrder) {
            VideoSortOrder.NEWEST_FIRST -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            VideoSortOrder.OLDEST_FIRST -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
            VideoSortOrder.NAME_A_TO_Z -> "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
            VideoSortOrder.LARGEST_FIRST -> "${MediaStore.Video.Media.SIZE} DESC"
            VideoSortOrder.DURATION_LONGEST -> "${MediaStore.Video.Media.DURATION} DESC"
        }

        val selection = "${MediaStore.Video.Media.SIZE} > 0"

        try {
            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                null,
                sqlSortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateAddedCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedCol = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketCol = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1
                val dataCol = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    c.getColumnIndex(MediaStore.Video.Media.DATA)
                } else -1

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = if (nameCol != -1) c.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val duration = if (durationCol != -1) c.getLong(durationCol) else 0L
                    val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                    val dateAdded = if (dateAddedCol != -1) c.getLong(dateAddedCol) else 0L
                    val dateModified = if (dateModifiedCol != -1) c.getLong(dateModifiedCol) else 0L
                    val mimeType = if (mimeCol != -1) c.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val width = if (widthCol != -1) c.getInt(widthCol) else 0
                    val height = if (heightCol != -1) c.getInt(heightCol) else 0
                    val bucketName = if (bucketCol != -1) c.getString(bucketCol) else null

                    val relativePath = when {
                        relPathCol != -1 -> c.getString(relPathCol) ?: ""
                        dataCol != -1 -> {
                            val dataPath = c.getString(dataCol) ?: ""
                            val parent = java.io.File(dataPath).parentFile
                            parent?.name ?: ""
                        }
                        else -> ""
                    }

                    // Extract a clean folder name from relativePath or bucket display name
                    val folderName = when {
                        !bucketName.isNullOrBlank() -> bucketName
                        relativePath.isNotBlank() -> {
                            val cleanPath = relativePath.trimEnd('/')
                            cleanPath.substringAfterLast('/')
                        }
                        else -> "Internal"
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videoList.add(
                        VideoItem(
                            id = id,
                            title = name,
                            uriString = contentUri.toString(),
                            durationMillis = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            folderName = folderName,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Gracefully return whatever was read if security exception or query failure
        }

        return videoList
    }

    private fun groupVideosIntoFolders(videos: List<VideoItem>): List<FolderItem> {
        val folderMap = mutableMapOf<String, MutableList<VideoItem>>()

        for (video in videos) {
            val key = if (video.relativePath.isNotBlank()) {
                video.relativePath.trimEnd('/')
            } else {
                video.folderName
            }
            folderMap.getOrPut(key) { mutableListOf() }.add(video)
        }

        return folderMap.map { (key, folderVideos) ->
            val firstVideo = folderVideos.firstOrNull()
            val folderName = firstVideo?.folderName?.ifBlank { key.substringAfterLast('/') }
                ?: key.substringAfterLast('/')

            FolderItem(
                id = key,
                name = folderName,
                relativePath = key,
                videoCount = folderVideos.size,
                coverVideoId = firstVideo?.id,
                coverVideoUri = firstVideo?.uriString
            )
        }.sortedByDescending { it.videoCount }
    }
}

