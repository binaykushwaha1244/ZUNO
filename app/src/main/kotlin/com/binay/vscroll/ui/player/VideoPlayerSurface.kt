package com.binay.vscroll.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.binay.vscroll.data.player.PlaybackTransitionTracer

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlaybackTransitionTracer.markEvent("9. SURFACE_ATTACHED (new PlayerView factory)")
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = exoPlayer
            }
        },
        update = { view ->
            PlaybackTransitionTracer.markEvent("9. SURFACE_ATTACHED (update PlayerView)")
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )

    DisposableEffect(exoPlayer) {
        onDispose {
            // Player detachment handled by manager
        }
    }
}

