package com.binay.vscroll.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.binay.vscroll.data.thumbnail.ThumbnailLoader

@Composable
fun VideoThumbnailView(
    videoId: Long,
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    placeholderTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
) {
    val context = LocalContext.current
    var bitmap by remember(videoId) { mutableStateOf(ThumbnailLoader.getCached(videoId)) }

    LaunchedEffect(videoId, uri) {
        if (bitmap == null) {
            val loaded = ThumbnailLoader.loadThumbnail(context, videoId, uri)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = bitmap, label = "ThumbnailFade") { currentBitmap ->
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = placeholderTint
                    )
                }
            }
        }
    }
}

