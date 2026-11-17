package com.binay.vscroll.ui.shorts

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.domain.model.ReactionType
import com.binay.vscroll.domain.model.UserVideoInteraction
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.domain.repository.VideoInteractionRepository
import com.binay.vscroll.ui.components.PlaybackSocialRail
import com.binay.vscroll.ui.components.ReactionActionItem
import com.binay.vscroll.ui.components.SaveToCollectionSheet
import com.binay.vscroll.ui.permission.GalleryPermissionStatus
import com.binay.vscroll.ui.permission.PermissionHelper
import com.binay.vscroll.ui.player.NotesBottomSheet
import com.binay.vscroll.ui.player.PlayerViewModel
import com.binay.vscroll.ui.player.VerticalVideoPager
import com.binay.vscroll.ui.player.VideoShareHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    feedQueueManager: FeedQueueManager,
    interactionRepository: VideoInteractionRepository,
    isFeedActive: Boolean = true,
    playerViewModel: PlayerViewModel = viewModel(),
    isPlayerOpen: Boolean = false,
    onPlayerOpenChanged: (Boolean) -> Unit = {},
    isShuffleEnabled: Boolean = true,
    onToggleShuffle: () -> Unit = {},
    modifier: Modifier = Modifier,
    collectionRepository: CollectionRepository? = null,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val queueState by feedQueueManager.shortsQueue.collectAsState()
    val isCleanMode by playerViewModel.isCleanViewingMode.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedVideoIndex by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    var activeNoteVideo by remember { mutableStateOf<VideoItem?>(null) }
    var activeNoteInitialText by remember { mutableStateOf<String?>(null) }
    var activeSaveVideo by remember { mutableStateOf<VideoItem?>(null) }

    var permissionStatus by remember {
        mutableStateOf(PermissionHelper.checkPermissionStatus(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = PermissionHelper.checkPermissionStatus(context)
    }

    // Filter displayed videos by search query (case-insensitive filename match)
    val displayedVideos = remember(queueState.items, searchQuery) {
        if (searchQuery.isBlank()) {
            queueState.items
        } else {
            val q = searchQuery.trim()
            queueState.items.filter { it.title.contains(q, ignoreCase = true) }
        }
    }

    // Lifecycle observer to pause when activity pauses/stops and resume when returns
    DisposableEffect(lifecycleOwner, isFeedActive, isPlayerOpen) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerViewModel.onAppForegroundChanged(false)
                    playerViewModel.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    playerViewModel.onAppForegroundChanged(true)
                    permissionStatus = PermissionHelper.checkPermissionStatus(context)
                    if (isFeedActive && isPlayerOpen) {
                        playerViewModel.resume()
                    }
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

    // Handle back button when player is open: returns to Shorts Explore Grid
    if (isPlayerOpen) {
        BackHandler {
            onPlayerOpenChanged(false)
            playerViewModel.pause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionStatus != GalleryPermissionStatus.GRANTED && permissionStatus != GalleryPermissionStatus.PARTIAL) {
            // Permission needed fallback card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ZUNO plays videos stored locally on your device in a full-screen vertical feed. Please grant video access to start scrolling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val perms = PermissionHelper.getRequiredPermissions()
                                permissionLauncher.launch(perms)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Video Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (!isPlayerOpen) {
            // Destination A: Shorts Explore Grid (Default Shorts Landing Screen)
            ShortsExploreGrid(
                videos = displayedVideos,
                isShuffleEnabled = isShuffleEnabled,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleShuffle = onToggleShuffle,
                onReshuffle = { feedQueueManager.shuffleGlobalShorts() },
                onVideoClick = { index ->
                    selectedVideoIndex = index
                    if (searchQuery.isBlank()) {
                        feedQueueManager.updateShortsIndex(index)
                    }
                    onPlayerOpenChanged(true)
                },
                gridState = gridState,
                onNavigateToSaved = onNavigateToSaved,
                isLoading = queueState.items.isEmpty(),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Destination B: Immersive Full-Screen Video Player
            VerticalVideoPager(
                videos = displayedVideos,
                initialIndex = selectedVideoIndex,
                playerViewModel = playerViewModel,
                onIndexChanged = { index ->
                    selectedVideoIndex = index
                    // Update index in the main queue as well if not searching
                    if (searchQuery.isBlank()) {
                        feedQueueManager.updateShortsIndex(index)
                    }
                },
                onDoubleTapLike = { video, _ ->
                    scope.launch {
                        interactionRepository.setReaction(video, ReactionType.LOVE)
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { video: VideoItem, page: Int ->
                // Top Header Overlay: Back Button (to Explore Grid) + Context Pill
                if (!isCleanMode || playbackState.isControlsVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button to exit to Shorts Explore Grid
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(onClick = {
                                onPlayerOpenChanged(false)
                                playerViewModel.pause()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Explore Grid",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        if (!isCleanMode) {
                            Spacer(modifier = Modifier.weight(1f))

                            // Quick Reshuffle Action (when shuffle is enabled)
                            if (isShuffleEnabled && searchQuery.isBlank()) {
                                IconButton(
                                    onClick = {
                                        feedQueueManager.shuffleGlobalShorts()
                                        Toast.makeText(context, "Reshuffled video feed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle Feed",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isCleanMode) {
                    // Bottom Video Information Overlay (Clean social format: Resolution tag + Title only)
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
                        onOpenNotes = { v, initialText ->
                            activeNoteVideo = v
                            activeNoteInitialText = initialText
                        },
                        onSaveToCollection = { v ->
                            activeSaveVideo = v
                        },
                        hasCollectionRepo = collectionRepository != null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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


