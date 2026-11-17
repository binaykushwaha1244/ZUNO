package com.binay.vscroll.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.data.player.PlaybackTransitionTracer
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.ui.components.VideoThumbnailView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerticalVideoPager(
    videos: List<VideoItem>,
    initialIndex: Int,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onIndexChanged: (Int) -> Unit = {},
    onDoubleTapLike: ((VideoItem, Offset) -> Unit)? = null,
    overlayContent: @Composable (VideoItem, Int) -> Unit
) {
    if (videos.isEmpty()) return

    val validInitial = initialIndex.coerceIn(0, videos.size - 1)
    val pagerState = rememberPagerState(
        initialPage = validInitial,
        pageCount = { videos.size }
    )

    // Synchronize current page changes with single ExoPlayer instance
    LaunchedEffect(pagerState) {
        var lastPage = pagerState.currentPage
        snapshotFlow { Pair(pagerState.isScrollInProgress, pagerState.targetPage) }
            .collect { (inProgress, target) ->
                if (inProgress && target != lastPage) {
                    PlaybackTransitionTracer.startTransition(lastPage, target)
                    PlaybackTransitionTracer.markEvent("2. PAGER_TARGET_PAGE_CHANGED", "target=$target")
                }
                if (!inProgress) {
                    lastPage = pagerState.currentPage
                }
            }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                val video = videos.getOrNull(page)
                if (video != null) {
                    PlaybackTransitionTracer.markEvent("2. PAGER_CURRENT_PAGE_SETTLED", "page=$page")
                    playerViewModel.playVideo(video)
                    playerViewModel.preloadNextVideo(videos.getOrNull(page + 1))
                    onIndexChanged(page)
                }
            }
    }

    // Scroll back to page 0 if queue is reshuffled
    LaunchedEffect(videos) {
        if (validInitial == 0 && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val animState by playerViewModel.animState.collectAsStateWithLifecycle()
    val isCleanMode by playerViewModel.isCleanViewingMode.collectAsStateWithLifecycle()

    LaunchedEffect(playbackState.isFirstFrameRendered) {
        if (playbackState.isFirstFrameRendered) {
            PlaybackTransitionTracer.markEvent("11. THUMBNAIL_HIDDEN")
        }
    }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        key = { page -> videos.getOrNull(page)?.id ?: page.toLong() },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) { page ->
        val video = videos[page]
        val isCurrentPage = (page == pagerState.currentPage)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Layer 1: Persistent Background Thumbnail View (prevents black flash during swipes)
            // Displays with exact ContentScale.Fit until live frame is rendered
            val isLiveRendered = isCurrentPage && playbackState.isFirstFrameRendered
            if (!isLiveRendered) {
                VideoThumbnailView(
                    videoId = video.id,
                    uri = video.contentUri,
                    contentScale = ContentScale.Fit,
                    backgroundColor = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 2: Live Video Surface (attached only when page is current active item)
            if (isCurrentPage) {
                VideoPlayerSurface(
                    exoPlayer = playerViewModel.playerManager.player,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 3: Transparent Tap & Double-Tap Layer on top of video surface
            var doubleTapHeartOffset by remember(video.id) { mutableStateOf<Offset?>(null) }
            var doubleTapTrigger by remember(video.id) { mutableIntStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(video.id) {
                        detectTapGestures(
                            onTap = {
                                playerViewModel.toggleControlsVisibility()
                            },
                            onDoubleTap = { offset ->
                                doubleTapHeartOffset = offset
                                doubleTapTrigger++
                                onDoubleTapLike?.invoke(video, offset)
                            }
                        )
                    }
            )

            // Double-Tap Animated Heart Effect
            doubleTapHeartOffset?.let { tapOffset ->
                key(doubleTapTrigger) {
                    DoubleTapHeartAnimation(
                        offset = tapOffset,
                        onAnimationEnd = { doubleTapHeartOffset = null }
                    )
                }
            }

            // Layer 3: Minimal Buffering Indicator
            if (isCurrentPage && playbackState.isBuffering && playbackState.playbackError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 3.dp
                    )
                }
            }

            // Layer 4: Playback Error Card
            if (isCurrentPage && playbackState.playbackError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Unable to play video",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playbackState.playbackError ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Swipe up for next video",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Layer 5: Left & Right Gesture Strips for Brightness & Volume
            if (isCurrentPage) {
                BrightnessAndVolumeGestureStrips(
                    playerViewModel = playerViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 6: Subtle Tap Feedback Animation
            AnimatedVisibility(
                visible = animState.isVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 1.15f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (animState.isPlayingIcon) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            // Layer 7: Playback Controls Overlay (Visible on tap, auto-hides after 3s when playing)
            if (isCurrentPage) {
                PlaybackControlsOverlay(
                    playbackState = playbackState,
                    isCleanMode = isCleanMode,
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onSeekTo = { playerViewModel.seekTo(it) },
                    onSkipBackward = { playerViewModel.skipBackward10Seconds() },
                    onSkipForward = { playerViewModel.skipForward10Seconds() },
                    onSpeedSelected = { playerViewModel.setPlaybackSpeed(it) },
                    onToggleMute = { playerViewModel.toggleMute() },
                    onToggleCleanMode = { playerViewModel.toggleCleanViewingMode() },
                    onToggleControls = { playerViewModel.toggleControlsVisibility() },
                    onInteracted = { playerViewModel.onControlsInteracted() }
                )

                // Layer 8: Floating HUD feedback for volume and brightness
                GestureHudIndicator(
                    playerViewModel = playerViewModel,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Layer 9: Overlay Content (Header badge, Video metadata, Social action rail & Reaction picker)
            overlayContent(video, page)
        }
    }
}

/**
 * Animated double-tap heart with smooth scale-up, settling, and fade-out.
 */
@Composable
private fun DoubleTapHeartAnimation(
    offset: Offset,
    onAnimationEnd: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val rotation = remember { -10f + (Math.random().toFloat() * 20f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            delay(360)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing)
            )
            onAnimationEnd()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFFF2A55),
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (offset.x - 48.dp.toPx()).toInt(),
                        y = (offset.y - 48.dp.toPx()).toInt()
                    )
                }
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    rotationZ = rotation
                    shadowElevation = 18f
                }
        )
    }
}
