package com.binay.vscroll.ui.saved

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.R
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.domain.model.InteractionSource
import com.binay.vscroll.domain.model.UserVideoItem
import com.binay.vscroll.domain.model.VideoCollection
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.ui.components.CollectionCard
import com.binay.vscroll.ui.components.VideoDetailsBottomSheet
import com.binay.vscroll.ui.components.VideoThumbnailView
import com.binay.vscroll.ui.liked.LikedViewModel
import kotlinx.coroutines.launch

/**
 * Instagram-Inspired Saved & Collections Screen:
 * - Header: "Saved" with top-right "+" button to create collection.
 * - Subtle navigation between "All Saved" and "Collections".
 * - "All Saved": Complete library of bookmarked videos with sequential playback.
 * - "Collections": 2-column responsive grid with 1:1 square cover thumbnails.
 * - Dedicated collection screen with 2-column simplified video cards.
 * - Sequential isolated playback within collections (`CollectionSequential`).
 * - Full collection management: Create, Rename, Delete (without deleting video files), and Remove from collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedVideosScreen(
    viewModel: LikedViewModel,
    collectionRepository: CollectionRepository,
    feedQueueManager: FeedQueueManager,
    onBack: () -> Unit,
    onPlayInteraction: (InteractionSource, List<VideoItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedVideos by viewModel.savedVideos.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val collections by collectionRepository.observeCollections()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Filter collections by search query
    val filteredCollections = remember(collections, searchQuery) {
        if (searchQuery.isBlank()) collections
        else collections.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // 0: All Saved, 1: Collections
    var selectedSavedTab by remember { mutableStateOf(0) }
    var selectedCollection by remember { mutableStateOf<VideoCollection?>(null) }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var collectionToRename by remember { mutableStateOf<VideoCollection?>(null) }
    var collectionToDelete by remember { mutableStateOf<VideoCollection?>(null) }
    var activeDetailsVideo by remember { mutableStateOf<VideoItem?>(null) }

    // System Back Handler
    if (selectedCollection != null) {
        BackHandler {
            selectedCollection = null
        }
    } else if (showTopBar) {
        BackHandler {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showTopBar) {
            // --- Standalone Header Section ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = {
                            if (selectedCollection != null) {
                                selectedCollection = null
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCollection?.name ?: "Saved",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedCollection != null) {
                                    "${selectedCollection?.videoCount ?: 0} videos"
                                } else if (selectedSavedTab == 0) {
                                    "${savedVideos.size} saved bookmarks"
                                } else {
                                    "${collections.size} collections"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Top-right actions
                        if (selectedCollection != null) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Collection menu",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename Collection") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = {
                                            menuExpanded = false
                                            collectionToRename = selectedCollection
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Collection", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            collectionToDelete = selectedCollection
                                        }
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Collection",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    if (selectedCollection == null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Minimal Search Bar
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = if (selectedSavedTab == 0) "Search saved bookmarks..." else "Search collections...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Segmented bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedSavedTab == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedSavedTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { selectedSavedTab = 0 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (selectedSavedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Saved (${savedVideos.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selectedSavedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedSavedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedSavedTab == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedSavedTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { selectedSavedTab = 1 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (selectedSavedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Collections (${collections.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selectedSavedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedSavedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- Embedded Mode Header (when shown inside LikedScreen tab) ---
            if (selectedCollection != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedCollection = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Collections",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCollection?.name ?: "Collection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedCollection?.videoCount ?: 0} videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Collection menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename Collection") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        collectionToRename = selectedCollection
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Collection", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        collectionToDelete = selectedCollection
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Subtle Segmented Pills for Tab inside LikedScreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selectedSavedTab == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (selectedSavedTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clickable { selectedSavedTab = 0 }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "All Saved (${savedVideos.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedSavedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedSavedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selectedSavedTab == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (selectedSavedTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clickable { selectedSavedTab = 1 }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Collections (${collections.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedSavedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedSavedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Collection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // --- Main Content Area ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                // 1. Dedicated Collection Screen
                selectedCollection != null -> {
                    val currentCollection = selectedCollection!!
                    val collectionVideos by collectionRepository.observeCollectionVideos(
                        currentCollection.id,
                        viewModel.allVideosFlow
                    ).collectAsStateWithLifecycle(initialValue = emptyList())

                    if (collectionVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "This Collection is Empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Tap the Save icon while watching any video or bookmark it to add it to '${currentCollection.name}'.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // 2-column clean video grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(collectionVideos, key = { _, v -> v.id }) { index, video ->
                                SimplifiedCollectionVideoCard(
                                    video = video,
                                    onClick = {
                                        feedQueueManager.startCollectionPlayback(
                                            collectionId = currentCollection.id,
                                            collectionName = currentCollection.name,
                                            collectionVideos = collectionVideos,
                                            initialIndex = index,
                                            sortOrder = VideoSortOrder.NEWEST_FIRST
                                        )
                                    },
                                    onRemove = {
                                        scope.launch {
                                            collectionRepository.removeVideosFromCollection(
                                                currentCollection.id,
                                                listOf(video.id)
                                            )
                                            Toast.makeText(
                                                context,
                                                "Removed from '${currentCollection.name}'",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onDetails = { activeDetailsVideo = video }
                                )
                            }
                        }
                    }
                }

                // 2. Collections Grid Tab
                selectedSavedTab == 1 -> {
                    if (filteredCollections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.size(76.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No Matching Collections" else "No Collections Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No collection matches '$searchQuery'." else "Organize your saved videos into custom playlists without duplicating files.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { showCreateDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Collection")
                                }
                            }
                        }
                    } else {
                        // 2-column Instagram-inspired square collection cards
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCollections, key = { it.id }) { collection ->
                                CollectionCard(
                                    collection = collection,
                                    onClick = { selectedCollection = collection },
                                    onRenameClick = { collectionToRename = collection },
                                    onDeleteClick = { collectionToDelete = collection }
                                )
                            }
                        }
                    }
                }

                // 3. All Saved Tab
                else -> {
                    if (savedVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(76.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No Matching Saved Videos" else "No Saved Videos Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No saved videos match '$searchQuery'." else "Tap the bookmark icon in the video player or Shorts screen to bookmark videos for quick offline access.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    } else {
                        val eligibleSavedVideos = remember(savedVideos) { savedVideos.mapNotNull { it.videoItem } }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(savedVideos, key = { it.mediaStoreId }) { item ->
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clickable(enabled = item.isAvailable, onClick = {
                                            val idx = eligibleSavedVideos.indexOfFirst { v -> v.id == item.mediaStoreId }
                                            if (idx >= 0) {
                                                onPlayInteraction(InteractionSource.SAVED, eligibleSavedVideos, idx)
                                            }
                                        })
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (item.isAvailable && item.videoItem != null) {
                                            VideoThumbnailView(
                                                videoId = item.mediaStoreId,
                                                uri = item.videoItem.contentUri,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFF221A1A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "File Removed",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom Gradient
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )

                                        // Duration
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = item.formattedDuration,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Top Right Remove Bookmark Button
                                        IconButton(
                                            onClick = { viewModel.removeBookmark(item) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Remove Bookmark",
                                                tint = Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Top Left Bookmark Badge
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Create Collection Dialog ---
    if (showCreateDialog) {
        var newCollectionName by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Collection", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter a name for your virtual collection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = {
                            newCollectionName = it
                            errorMessage = null
                        },
                        label = { Text("Collection name") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCollectionName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Name cannot be empty"
                            return@Button
                        }
                        scope.launch {
                            val result = collectionRepository.createCollection(trimmed)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Collection '$trimmed' created", Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to create collection"
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Rename Collection Dialog ---
    collectionToRename?.let { col ->
        var renameName by remember { mutableStateOf(col.name) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { collectionToRename = null },
            title = { Text("Rename Collection", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = {
                            renameName = it
                            errorMessage = null
                        },
                        label = { Text("Collection name") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Name cannot be empty"
                            return@Button
                        }
                        scope.launch {
                            val result = collectionRepository.renameCollection(col.id, trimmed)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Renamed to '$trimmed'", Toast.LENGTH_SHORT).show()
                                if (selectedCollection?.id == col.id) {
                                    selectedCollection = selectedCollection?.copy(name = trimmed)
                                }
                                collectionToRename = null
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to rename"
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { collectionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Delete Collection Dialog ---
    collectionToDelete?.let { col ->
        AlertDialog(
            onDismissRequest = { collectionToDelete = null },
            title = { Text("Delete Collection '${col.name}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will delete the virtual collection. Your original video files will NOT be deleted from device storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = collectionRepository.deleteCollection(col.id)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Collection deleted", Toast.LENGTH_SHORT).show()
                                if (selectedCollection?.id == col.id) {
                                    selectedCollection = null
                                }
                                collectionToDelete = null
                            } else {
                                Toast.makeText(context, "Failed to delete collection", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Collection")
                }
            },
            dismissButton = {
                TextButton(onClick = { collectionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Active Video Details Bottom Sheet ---
    activeDetailsVideo?.let { video ->
        VideoDetailsBottomSheet(
            video = video,
            onDismissRequest = { activeDetailsVideo = null },
            onPlay = {
                activeDetailsVideo = null
                val eligibleSaved = savedVideos.mapNotNull { it.videoItem }
                val idx = eligibleSaved.indexOfFirst { it.id == video.id }
                if (idx >= 0) {
                    onPlayInteraction(InteractionSource.SAVED, eligibleSaved, idx)
                }
            }
        )
    }
}

/**
 * Simplified clean video card for Collection Drilldown:
 * - 16:9 thumbnail with resolution tag and duration pill.
 * - Video title below thumbnail with subtle 3-dots menu.
 * - No central play button overlay.
 * - No folder names or poorly wrapped file sizes.
 */
@Composable
private fun SimplifiedCollectionVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                VideoThumbnailView(
                    videoId = video.id,
                    uri = video.contentUri,
                    modifier = Modifier.fillMaxSize()
                )

                // Resolution Tag (top-left)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = video.resolutionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Duration badge (bottom-right)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Title and 3-dots Menu Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from collection") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video Details") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDetails()
                            }
                        )
                    }
                }
            }
        }
    }
}
