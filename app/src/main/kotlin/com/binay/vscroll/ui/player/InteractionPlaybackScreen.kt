package com.binay.vscroll.ui.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.domain.model.FeedQueueState
import com.binay.vscroll.domain.model.InteractionSource
import com.binay.vscroll.domain.model.PlaybackContext
import com.binay.vscroll.domain.model.ReactionType
import com.binay.vscroll.domain.model.UserVideoInteraction
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.domain.repository.VideoInteractionRepository
import com.binay.vscroll.ui.components.PlaybackSocialRail
import com.binay.vscroll.ui.components.ReactionActionItem
import com.binay.vscroll.ui.components.SaveToCollectionSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionPlaybackScreen(
    feedQueueState: FeedQueueState,
    playerViewModel: PlayerViewModel,
    interactionRepository: VideoInteractionRepository,
    onIndexChanged: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    collectionRepository: CollectionRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isCleanMode by playerViewModel.isCleanViewingMode.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    val interactionContext = feedQueueState.context as? PlaybackContext.InteractionSequential
    val source = interactionContext?.source ?: InteractionSource.LIKED

    var activeNoteVideo by remember { mutableStateOf<VideoItem?>(null) }
    var activeNoteInitialText by remember { mutableStateOf<String?>(null) }
    var activeSaveVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Intercept hardware/gesture back to exit back to Liked / Saved / Notes list
    BackHandler {
        onClose()
    }

    // Lifecycle observer to pause when activity pauses/stops and resume when returns
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerViewModel.onAppForegroundChanged(false)
                    playerViewModel.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    playerViewModel.onAppForegroundChanged(true)
                    playerViewModel.resume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerViewModel.pause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalVideoPager(
            videos = feedQueueState.items,
            initialIndex = feedQueueState.currentIndex,
            playerViewModel = playerViewModel,
            onIndexChanged = onIndexChanged,
            onDoubleTapLike = { video, _ ->
                scope.launch {
                    interactionRepository.setReaction(video, ReactionType.LOVE)
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { video: VideoItem, pageIndex: Int ->
            // Subtle gradient overlay when controls or headers are visible
            if (!isCleanMode || playbackState.isControlsVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // Top Header: Back Button + Source Context Pill + Counter Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button to exit to interaction list
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (!isCleanMode) {
                        Spacer(modifier = Modifier.width(10.dp))

                        // Source Context Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                val (icon, tint) = when (source) {
                                    InteractionSource.LIKED -> Icons.Default.Favorite to Color(0xFFFF2A55)
                                    InteractionSource.SAVED -> Icons.Default.Bookmark to MaterialTheme.colorScheme.primary
                                    InteractionSource.NOTES -> Icons.Default.EditNote to MaterialTheme.colorScheme.primary
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                    }
                }
            }

            if (!isCleanMode) {
                // Bottom Video Metadata Overlay (Clean social format: Resolution tag + Title only)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 44.dp, end = 90.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = video.resolutionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Unified Social Interaction Rail & Outside Dismissal Scrim
                PlaybackSocialRail(
                    video = video,
                    interactionRepository = interactionRepository,
                    onOpenNotes = { v, currentNoteText ->
                        activeNoteVideo = v
                        activeNoteInitialText = currentNoteText
                    },
                    onSaveToCollection = { v ->
                        activeSaveVideo = v
                    },
                    hasCollectionRepo = collectionRepository != null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Active Note Bottom Sheet Editor
        if (activeNoteVideo != null) {
            NotesBottomSheet(
                video = activeNoteVideo,
                initialNote = activeNoteInitialText,
                onDismissRequest = {
                    activeNoteVideo = null
                    activeNoteInitialText = null
                },
                onSaveNote = { newText ->
                    activeNoteVideo?.let { v ->
                        scope.launch {
                            interactionRepository.saveNote(v, newText)
                            Toast.makeText(context, "Note saved offline", Toast.LENGTH_SHORT).show()
                        }
                    }
                    activeNoteVideo = null
                    activeNoteInitialText = null
                },
                onDeleteNote = {
                    activeNoteVideo?.let { v ->
                        scope.launch {
                            interactionRepository.deleteNote(v.id)
                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    activeNoteVideo = null
                    activeNoteInitialText = null
                }
            )
        }
        // Active Save to Collection Bottom Sheet
        if (activeSaveVideo != null && collectionRepository != null) {
            SaveToCollectionSheet(
                video = activeSaveVideo!!,
                collectionRepository = collectionRepository,
                interactionRepository = interactionRepository,
                onDismissRequest = { activeSaveVideo = null }
            )
        }
    }
}



