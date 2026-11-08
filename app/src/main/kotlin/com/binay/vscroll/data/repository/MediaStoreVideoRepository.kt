package com.binay.vscroll.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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

    @Volatile
    private var cachedVideos: List<VideoItem>? = null
    @Volatile
    private var cachedSortOrder: VideoSortOrder? = null
    @Volatile
    private var lastQueryTimestamp: Long = 0L

    // Flow that observes MediaStore content changes and emits whenever local storage updates
    private val mediaStoreChangeFlow: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                cachedVideos = null
                lastQueryTimestamp = 0L
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
        // Invalidate in-memory cache to force a fresh query across active subscribers
        cachedVideos = null
        lastQueryTimestamp = 0L
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun sortVideos(videos: List<VideoItem>, sortOrder: VideoSortOrder): List<VideoItem> {
        return when (sortOrder) {
            VideoSortOrder.NEWEST_FIRST -> videos.sortedByDescending { it.dateAdded }
            VideoSortOrder.OLDEST_FIRST -> videos.sortedBy { it.dateAdded }
            VideoSortOrder.NAME_A_TO_Z -> videos.sortedBy { it.title.lowercase() }
            VideoSortOrder.LARGEST_FIRST -> videos.sortedByDescending { it.sizeBytes }
            VideoSortOrder.DURATION_LONGEST -> videos.sortedByDescending { it.durationMillis }
        }
    }

    private fun queryMediaStoreVideos(sortOrder: VideoSortOrder): List<VideoItem> {
        val now = System.currentTimeMillis()
        val cached = cachedVideos
        if (cached != null && (now - lastQueryTimestamp) < 1200L) {
            return if (cachedSortOrder == sortOrder) cached else sortVideos(cached, sortOrder)
        }

        val startTime = System.currentTimeMillis()

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
        var videoList: ArrayList<VideoItem> = ArrayList()

        try {
            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                null,
                sqlSortOrder
            )

            cursor?.use { c ->
                val count = c.count
                videoList = ArrayList(count)
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

        val elapsed = System.currentTimeMillis() - startTime
        Log.d("ZUNO_PERF", "MediaStore query: ${videoList.size} videos retrieved in ${elapsed}ms (sort=$sortOrder)")
        cachedVideos = videoList
        cachedSortOrder = sortOrder
        lastQueryTimestamp = System.currentTimeMillis()

        return videoList
    }

    private fun groupVideosIntoFolders(videos: List<VideoItem>): List<FolderItem> {
        val groupStart = System.currentTimeMillis()
        class FolderAccumulator(
            val id: String,
            val folderName: String,
            var count: Int = 0,
            var coverVideoId: Long? = null,
            var coverVideoUri: String? = null
        )

        val folderMap = LinkedHashMap<String, FolderAccumulator>()

        for (video in videos) {
            val key = if (video.relativePath.isNotBlank()) {
                video.relativePath.trimEnd('/')
            } else {
                video.folderName
            }
            val acc = folderMap.getOrPut(key) {
                val folderName = video.folderName.ifBlank { key.substringAfterLast('/') }.ifBlank { "Internal" }
                FolderAccumulator(
                    id = key,
                    folderName = folderName,
                    count = 0,
                    coverVideoId = video.id,
                    coverVideoUri = video.uriString
                )
            }
            acc.count++
        }

        val result = folderMap.values.map { acc ->
            FolderItem(
                id = acc.id,
                name = acc.folderName,
                relativePath = acc.id,
                videoCount = acc.count,
                coverVideoId = acc.coverVideoId,
                coverVideoUri = acc.coverVideoUri
            )
        }.sortedByDescending { it.videoCount }

        val elapsed = System.currentTimeMillis() - groupStart
        Log.d("ZUNO_PERF", "Grouped ${videos.size} videos into ${result.size} folders in ${elapsed}ms")
        return result
    }
}

