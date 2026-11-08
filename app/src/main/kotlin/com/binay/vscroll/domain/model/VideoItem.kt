package com.binay.vscroll.domain.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val uriString: String,
    val durationMillis: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "video/mp4",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val folderName: String = "Internal",
    val relativePath: String = ""
) {
    val contentUri: Uri
        get() = Uri.parse(uriString)

    val resolutionLabel: String
        get() {
            val maxDim = maxOf(width, height)
            val minDim = minOf(width, height)
            return when {
                maxDim >= 3840 || minDim >= 2160 -> "4K UHD"
                maxDim >= 2560 || minDim >= 1440 -> "2K QHD"
                maxDim >= 1920 || minDim >= 1080 -> "1080p FHD"
                maxDim >= 1280 || minDim >= 720 -> "720p HD"
                maxDim > 0 && minDim > 0 -> "${width}x${height}"
                else -> "HD"
            }
        }

    val formattedDuration: String
        get() {
            if (durationMillis <= 0) return "00:00"
            val totalSeconds = durationMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
