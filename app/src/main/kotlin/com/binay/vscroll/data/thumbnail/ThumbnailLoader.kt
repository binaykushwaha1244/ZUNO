package com.binay.vscroll.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailLoader {

    // Allocate 1/8th of available runtime memory for the thumbnail cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(16 * 1024, 64 * 1024) // 16MB to 64MB

    private val memoryCache = object : LruCache<Long, Bitmap>(cacheSize) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCached(videoId: Long): Bitmap? {
        return memoryCache.get(videoId)
    }

    suspend fun loadThumbnail(
        context: Context,
        videoId: Long,
        uri: Uri,
        width: Int = 720,
        height: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Fast path: memory cache
        memoryCache.get(videoId)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Primary: Use Android 10+ (API 29+) OS ContentResolver thumbnail engine
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(width, height), null)
            } catch (e: Exception) {
                // Fall back to MediaMetadataRetriever if ContentResolver thumbnail isn't indexed yet
            }
        }

        // 2. Fallback: Extract keyframe using MediaMetadataRetriever
        if (bitmap == null) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        -1,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        width,
                        height
                    )
                } else {
                    retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                // Ignore decoding errors for corrupt/deleted videos
            } finally {
                try {
                    retriever?.release()
                } catch (ignored: Exception) {}
            }
        }

        if (bitmap != null) {
            memoryCache.put(videoId, bitmap)
        }

        return@withContext bitmap
    }
}

