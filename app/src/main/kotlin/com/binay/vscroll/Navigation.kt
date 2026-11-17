package com.binay.vscroll

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binay.vscroll.data.local.VScrollDatabase
import com.binay.vscroll.data.preferences.ThemePreferencesRepository
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.data.repository.MediaStoreVideoRepository
import com.binay.vscroll.data.repository.VideoInteractionRepositoryImpl
import com.binay.vscroll.domain.model.InteractionSource
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.ui.activity.ActivityScreen
import com.binay.vscroll.ui.home.HomeViewModel
import com.binay.vscroll.ui.library.LibraryScreen
import com.binay.vscroll.ui.liked.LikedScreen
import com.binay.vscroll.ui.liked.LikedViewModel
import com.binay.vscroll.ui.navigation.NavDestination
import com.binay.vscroll.ui.player.FolderPlaybackScreen
import com.binay.vscroll.ui.player.InteractionPlaybackScreen
import com.binay.vscroll.ui.player.PlayerViewModel
import com.binay.vscroll.ui.player.SystemUiImmersiveMode
import com.binay.vscroll.ui.saved.SavedVideosScreen
import com.binay.vscroll.ui.settings.SettingsScreen
import com.binay.vscroll.ui.shorts.ShortsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    homeViewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDestination by rememberSaveable { mutableStateOf(NavDestination.SHORTS) }
    var isShortsPlayerOpen by rememberSaveable { mutableStateOf(false) }

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val themePreferencesRepo = remember { ThemePreferencesRepository(context.applicationContext) }
    val isShuffleEnabled by themePreferencesRepo.isShuffleEnabled.collectAsStateWithLifecycle(initialValue = true)

    val videoRepository = remember { MediaStoreVideoRepository(context.applicationContext) }
    val feedQueueManager = remember { FeedQueueManager(videoRepository, initialShuffleEnabled = isShuffleEnabled) }
    val database = remember { VScrollDatabase.getInstance(context.applicationContext) }
    val videoInteractionRepository = remember {
        VideoInteractionRepositoryImpl(
            interactionDao = database.videoInteractionDao(),
            noteDao = database.videoNoteDao()
        )
    }
    val likedViewModel = remember {
        LikedViewModel(
            interactionRepository = videoInteractionRepository,
            videoRepository = videoRepository
        )
    }

    var isSavedVideosOpen by rememberSaveable { mutableStateOf(false) }

    val onPlayInteraction: (InteractionSource, List<VideoItem>, Int) -> Unit = { source, videos, initialIndex ->
        isSavedVideosOpen = false
        feedQueueManager.startInteractionPlayback(source, videos, initialIndex)
    }

    val onPlaySingleVideo: (VideoItem) -> Unit = { video ->
        isSavedVideosOpen = false
        feedQueueManager.startInteractionPlayback(InteractionSource.SAVED, listOf(video), 0)
    }

    val folderQueueState by feedQueueManager.folderQueue.collectAsStateWithLifecycle()
    val interactionQueueState by feedQueueManager.interactionQueue.collectAsStateWithLifecycle()

    // Synchronize persistent shuffle preference with FeedQueueManager
    LaunchedEffect(isShuffleEnabled) {
        feedQueueManager.setShuffleEnabled(isShuffleEnabled)
    }

    val destinations = NavDestination.entries
    val saveableStateHolder = rememberSaveableStateHolder()

    val navigateToDestination: (NavDestination) -> Unit = { destination ->
        if (selectedDestination != destination) {
            isShortsPlayerOpen = false
            isSavedVideosOpen = false
            playerViewModel.pause()
            selectedDestination = destination
        }
    }

    val onSwipeNextTab: () -> Unit = {
        val currentIdx = selectedDestination.ordinal
        if (currentIdx < destinations.size - 1) {
            navigateToDestination(destinations[currentIdx + 1])
        }
    }

    val onSwipePrevTab: () -> Unit = {
        val currentIdx = selectedDestination.ordinal
        if (currentIdx > 0) {
            navigateToDestination(destinations[currentIdx - 1])
        }
    }

    // Determine whether immersive full-screen playback is active (Shorts player, Folder player, or Interaction player)
    val isImmersive = isShortsPlayerOpen || (folderQueueState != null) || (interactionQueueState != null)

    // Apply Sticky Immersive Mode: hides Android status & navigation bars for true edge-to-edge playback
    SystemUiImmersiveMode(enabled = isImmersive)

    // Pause playback whenever navigating away from Shorts (unless folder or interaction playback is actively showing)
    LaunchedEffect(selectedDestination, folderQueueState, interactionQueueState, isShortsPlayerOpen) {
        if (folderQueueState == null && interactionQueueState == null && (!isShortsPlayerOpen || selectedDestination != NavDestination.SHORTS)) {
            playerViewModel.pause()
        }
    }

    // Full-screen Mode C: Interaction Sequential Playback (Liked, Saved, Notes)
    if (interactionQueueState != null) {
        InteractionPlaybackScreen(
            feedQueueState = interactionQueueState!!,
            playerViewModel = playerViewModel,
            interactionRepository = videoInteractionRepository,
            collectionRepository = homeViewModel.collectionRepository,
            onIndexChanged = { index ->
                feedQueueManager.updateInteractionIndex(index)
            },
            onClose = {
                feedQueueManager.closeInteractionPlayback()
                playerViewModel.pause()
            }
        )
    } else if (folderQueueState != null) {
        FolderPlaybackScreen(
            feedQueueState = folderQueueState!!,
            playerViewModel = playerViewModel,
            interactionRepository = videoInteractionRepository,
            collectionRepository = homeViewModel.collectionRepository,
            onIndexChanged = { index ->
                feedQueueManager.updateFolderIndex(index)
            },
            onClose = {
                feedQueueManager.closeFolderPlayback()
                playerViewModel.pause()
            }
        )
    } else {
        // Back navigation behavior:
        // 0. If saved videos screen is open in Shorts tab, Back returns to Shorts Explore Grid
        // 1. If shorts player is open, Back closes the player and returns to Shorts Explore Grid
        // 2. If viewing inside a folder in Library, Back exits the folder drilldown
        // 3. If on any tab other than SHORTS, Back returns to the primary SHORTS explore grid
        if (isSavedVideosOpen && selectedDestination == NavDestination.SHORTS) {
            BackHandler {
                isSavedVideosOpen = false
            }
        } else if (isShortsPlayerOpen && selectedDestination == NavDestination.SHORTS) {
            BackHandler {
                isShortsPlayerOpen = false
                playerViewModel.pause()
            }
        } else if (selectedDestination == NavDestination.LIBRARY && uiState.isMultiSelectActive) {
            BackHandler {
                homeViewModel.clearSelection()
            }
        } else if (selectedDestination == NavDestination.LIBRARY && (uiState.selectedFolder != null || uiState.selectedCollection != null)) {
            BackHandler {
                homeViewModel.selectFolder(null)
                homeViewModel.selectCollection(null)
            }
        } else if (selectedDestination != NavDestination.SHORTS) {
            BackHandler {
                selectedDestination = NavDestination.SHORTS
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .horizontalTabSwipe(
                    enabled = !isImmersive && !uiState.isMultiSelectActive && !isSavedVideosOpen,
                    onSwipeNext = onSwipeNextTab,
                    onSwipePrev = onSwipePrevTab
                ),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Completely omit the bottom bar when immersive player is open
                if (!isImmersive) {
                    VScrollBottomNavigationBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = navigateToDestination
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // When player is open, omit innerPadding for true full-screen edge-to-edge playback
                    .then(if (isImmersive) Modifier else Modifier.padding(innerPadding))
            ) {
                AnimatedContent(
                    targetState = selectedDestination,
                    transitionSpec = {
                        val targetIdx = targetState.ordinal
                        val initialIdx = initialState.ordinal
                        if (targetIdx > initialIdx) {
                            (slideInHorizontally(animationSpec = tween(280)) { width -> width } + fadeIn(animationSpec = tween(280)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { width -> -width } + fadeOut(animationSpec = tween(280)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(280)) { width -> -width } + fadeIn(animationSpec = tween(280)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { width -> width } + fadeOut(animationSpec = tween(280)))
                        }
                    },
                    label = "TabContentAnimation"
                ) { destination ->
                    saveableStateHolder.SaveableStateProvider(key = destination.name) {
                        when (destination) {
                            NavDestination.SHORTS -> {
                                if (isSavedVideosOpen) {
                                    SavedVideosScreen(
                                        viewModel = likedViewModel,
                                        collectionRepository = homeViewModel.collectionRepository,
                                        feedQueueManager = feedQueueManager,
                                        onBack = { isSavedVideosOpen = false },
                                        onPlayInteraction = onPlayInteraction
                                    )
                                } else {
                                    ShortsScreen(
                                        feedQueueManager = feedQueueManager,
                                        interactionRepository = videoInteractionRepository,
                                        collectionRepository = homeViewModel.collectionRepository,
                                        isFeedActive = (selectedDestination == NavDestination.SHORTS),
                                        playerViewModel = playerViewModel,
                                        isPlayerOpen = isShortsPlayerOpen,
                                        onPlayerOpenChanged = { isShortsPlayerOpen = it },
                                        isShuffleEnabled = isShuffleEnabled,
                                        onToggleShuffle = {
                                            scope.launch {
                                                themePreferencesRepo.setShuffleEnabled(!isShuffleEnabled)
                                            }
                                        },
                                        onNavigateToLibrary = { navigateToDestination(NavDestination.LIBRARY) },
                                        onNavigateToSaved = { isSavedVideosOpen = true }
                                    )
                                }
                            }
                            NavDestination.LIKED -> {
                                LikedScreen(
                                    viewModel = likedViewModel,
                                    collectionRepository = homeViewModel.collectionRepository,
                                    feedQueueManager = feedQueueManager,
                                    onPlayInteraction = onPlayInteraction
                                )
                            }
                            NavDestination.ACTIVITY -> {
                                ActivityScreen(
                                    onPlayVideo = onPlaySingleVideo
                                )
                            }
                            NavDestination.LIBRARY -> {
                                LibraryScreen(
                                    viewModel = homeViewModel,
                                    onVideoClick = { _, index, displayedVideos, selectedFolder, selectedCollection ->
                                        when {
                                            selectedCollection != null -> {
                                                feedQueueManager.startCollectionPlayback(
                                                    collectionId = selectedCollection.id,
                                                    collectionName = selectedCollection.name,
                                                    collectionVideos = displayedVideos,
                                                    initialIndex = index,
                                                    sortOrder = uiState.sortOrder
                                                )
                                            }
                                            else -> {
                                                val folderId = selectedFolder?.relativePath ?: "all_videos"
                                                val folderName = selectedFolder?.name ?: "All Videos"
                                                feedQueueManager.startFolderPlayback(
                                                    folderId = folderId,
                                                    folderName = folderName,
                                                    folderVideos = displayedVideos,
                                                    initialIndex = index,
                                                    sortOrder = uiState.sortOrder
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            NavDestination.SETTINGS -> {
                                SettingsScreen(viewModel = homeViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern, compact, and balanced bottom navigation bar:
 * - Height: 58dp with navigation bar insets padding.
 * - Clean hairline top divider border.
 * - Outlined icons for unselected tabs; filled, primary-accented icons for selected tab.
 * - Avoids the oversized pale-blue container pill from default M3 NavigationBar.
 */
@Composable
private fun VScrollBottomNavigationBar(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavDestination.entries.forEach { destination ->
                val isSelected = selectedDestination == destination
                val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onDestinationSelected(destination)
                        }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = destination.title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Directional horizontal swipe gesture detector for seamless tab switching.
 * Strictly avoids gesture conflicts with vertical video playback in Shorts:
 * - Requires horizontal delta to exceed vertical delta by 1.5x (aspect ratio check).
 * - Requires drag to exceed touchSlop before locking to horizontal direction.
 * - If vertical motion starts first, locks to vertical and never triggers tab navigation.
 */
fun Modifier.horizontalTabSwipe(
    enabled: Boolean = true,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val touchSlop = viewConfiguration.touchSlop
    val swipeThreshold = 70.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var isLockedHorizontal = false
        var isLockedVertical = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                // Finger released
                if (isLockedHorizontal && kotlin.math.abs(totalX) >= swipeThreshold) {
                    if (totalX < 0) {
                        onSwipeNext()
                    } else {
                        onSwipePrev()
                    }
                }
                break
            }

            val dragX = change.position.x - change.previousPosition.x
            val dragY = change.position.y - change.previousPosition.y
            totalX += dragX
            totalY += dragY

            val absX = kotlin.math.abs(totalX)
            val absY = kotlin.math.abs(totalY)

            if (!isLockedHorizontal && !isLockedVertical) {
                if (absY > touchSlop && absY > absX) {
                    // Vertical drag initiated - lock to vertical, DO NOT intercept
                    isLockedVertical = true
                } else if (absX > touchSlop && absX > absY * 1.5f) {
                    // Clearly horizontal drag initiated
                    isLockedHorizontal = true
                }
            }

            if (isLockedHorizontal) {
                change.consume()
            }
        }
    }
}
