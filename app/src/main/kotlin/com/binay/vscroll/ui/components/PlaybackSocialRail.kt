package com.binay.vscroll.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.domain.model.ReactionType
import com.binay.vscroll.domain.model.UserVideoInteraction
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.VideoInteractionRepository
import com.binay.vscroll.ui.player.VideoShareHelper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Unified playback social interaction rail providing:
 * 1. ReactionActionItem with double-tap like, long-press 6-emoji selector, and pop animations.
 * 2. Automated hiding of Notes, Save, and Share controls when emoji selector is open.
 * 3. Full-screen outside-tap dismissal scrim that absorbs clicks to prevent accidental triggers.
 * 4. Hardware/gesture BackHandler dismissal of emoji selector without navigating back.
 * 5. Automatic dismissal on video swipe/index change.
 */
@Composable
fun PlaybackSocialRail(
    video: VideoItem,
    interactionRepository: VideoInteractionRepository?,
    onOpenNotes: (VideoItem, String?) -> Unit,
    onSaveToCollection: (VideoItem) -> Unit,
    hasCollectionRepo: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isReactionPickerOpen by remember { mutableStateOf(false) }

    // Auto-dismiss selector when switching videos
    LaunchedEffect(video.id) {
        isReactionPickerOpen = false
    }

    val interaction by (interactionRepository?.getInteraction(video.id) ?: flowOf(UserVideoInteraction(video.id)))
        .collectAsStateWithLifecycle(UserVideoInteraction(video.id))

    Box(modifier = modifier) {
        // Intercept back button and outside taps when reaction selector is open
        if (isReactionPickerOpen) {
            BackHandler {
                isReactionPickerOpen = false
            }

            // Outside tap interceptor: full-screen transparent barrier that consumes clicks and dismisses picker
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(video.id) {
                        detectTapGestures {
                            isReactionPickerOpen = false
                        }
                    }
            )
        }

        // Action Rail placed at BottomEnd (above filename and seekbar area)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 145.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Reaction / Like Action Button (with horizontal emoji selector expanding to the left)
            ReactionActionItem(
                currentReaction = interaction.reaction,
                isLiked = interaction.isLiked,
                onReactionSelected = { newReaction ->
                    if (interactionRepository != null) {
                        scope.launch {
                            interactionRepository.setReaction(video, newReaction)
                            val msg = when {
                                newReaction != null -> "Reacted ${newReaction.emoji} ${newReaction.label}"
                                else -> "Removed reaction"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Likes synced with offline library", Toast.LENGTH_SHORT).show()
                    }
                },
                isSelectorOpen = isReactionPickerOpen,
                onSelectorOpenChange = { isReactionPickerOpen = it }
            )

            // Notes, Save to Collection, and Share (Smoothly hidden when reaction picker is open)
            AnimatedVisibility(
                visible = !isReactionPickerOpen,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(180)
                ),
                exit = fadeOut(tween(150)) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(150)
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    // Notes Action
                    val hasNote = !interaction.note.isNullOrBlank()
                    PlaybackSocialActionItem(
                        icon = Icons.Default.EditNote,
                        label = if (hasNote) "Note" else "Notes",
                        tint = if (hasNote) MaterialTheme.colorScheme.primary else Color.White,
                        onClick = {
                            if (interactionRepository != null) {
                                onOpenNotes(video, interaction.note)
                            } else {
                                Toast.makeText(context, "Notes synced with offline library", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Bookmark / Save Action
                    PlaybackSocialActionItem(
                        icon = if (interaction.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        label = if (interaction.isBookmarked) "Saved" else "Save",
                        tint = if (interaction.isBookmarked) MaterialTheme.colorScheme.primary else Color.White,
                        onClick = {
                            if (interactionRepository != null) {
                                scope.launch {
                                    if (hasCollectionRepo) {
                                        if (!interaction.isBookmarked) {
                                            interactionRepository.setBookmarked(video, true)
                                        }
                                        onSaveToCollection(video)
                                    } else {
                                        val newBookmarked = interactionRepository.toggleBookmark(video)
                                        Toast.makeText(
                                            context,
                                            if (newBookmarked) "Saved to Bookmarks" else "Removed from Bookmarks",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Bookmarks synced with offline library", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Share Action
                    PlaybackSocialActionItem(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = Color.White,
                        onClick = {
                            VideoShareHelper.shareVideo(context, video)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSocialActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}
