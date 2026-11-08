package com.binay.vscroll.ui.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.binay.vscroll.domain.model.VideoItem
import java.io.File

data class ShareIntentSpec(
    val uriString: String,
    val mimeType: String,
    val title: String,
    val isGrantReadPermission: Boolean
)

object VideoShareHelper {

    fun createShareSpec(video: VideoItem): ShareIntentSpec {
        val mime = video.mimeType.takeIf { it.isNotBlank() && it.startsWith("video/") } ?: "video/mp4"
        return ShareIntentSpec(
            uriString = video.uriString,
            mimeType = mime,
            title = video.title,
            isGrantReadPermission = true
        )
    }

    fun shareVideo(context: Context, video: VideoItem) {
        try {
            val shareUri: Uri = if (video.contentUri.scheme == "content") {
                video.contentUri
            } else {
                // If it is a file URI or path, use FileProvider safely
                val file = File(video.contentUri.path ?: "")
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    video.contentUri
                }
            }

            val spec = createShareSpec(video)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = spec.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TITLE, spec.title)
                putExtra(Intent.EXTRA_SUBJECT, spec.title)
                clipData = ClipData.newUri(context.contentResolver, spec.title, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share video").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to share video: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
