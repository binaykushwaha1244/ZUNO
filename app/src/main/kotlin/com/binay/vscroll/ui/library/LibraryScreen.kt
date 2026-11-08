package com.binay.vscroll.ui.library

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoCollection
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.ui.components.CollectionCard
import com.binay.vscroll.ui.components.FolderCard
import com.binay.vscroll.ui.components.VideoCard
import com.binay.vscroll.ui.home.HomeUiState
import com.binay.vscroll.ui.home.HomeViewModel
import com.binay.vscroll.ui.home.VideoSortDialog
import com.binay.vscroll.ui.permission.GalleryPermissionStatus
import com.binay.vscroll.ui.permission.PartialAccessBanner
import com.binay.vscroll.ui.permission.PermissionHelper
import com.binay.vscroll.ui.permission.PermissionRequiredCard

@Composable
fun LibraryScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onVideoClick: (VideoItem, Int, List<VideoItem>, FolderItem?, VideoCollection?) -> Unit = { _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissions()
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleConsentResult(result.resultCode == Activity.RESULT_OK) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.pendingConsentIntentSender) {
        uiState.pendingConsentIntentSender?.let { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LibraryScreenContent(
        state = uiState,
        viewModel = viewModel,
        onTabSelected = viewModel::onTabSelected,
        onFolderSelected = viewModel::selectFolder,
        onCollectionSelected = viewModel::selectCollection,
        onSearchToggle = viewModel::toggleSearch,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortClick = { viewModel.showSortDialog(true) },
        onRefresh = {
            viewModel.refresh()
            Toast.makeText(context, "Rescanning local storage...", Toast.LENGTH_SHORT).show()
        },
        onRequestPermission = {
            val permissions = PermissionHelper.getRequiredPermissions()
            permissionLauncher.launch(permissions)
        },
        onVideoClick = { video, index ->
            if (uiState.isMultiSelectActive) {
                viewModel.toggleVideoSelection(video.id)
            } else {
                onVideoClick(
                    video,
                    index,
                    uiState.displayedVideos,
                    uiState.selectedFolder,
                    uiState.selectedCollection
                )
            }
        },
        onVideoLongClick = { video ->
            viewModel.enterMultiSelect(video.id)
        },
        modifier = modifier
    )

    // --- Dialogs ---

    if (uiState.isSortDialogVisible) {
        VideoSortDialog(
            currentSortOrder = uiState.sortOrder,
            onSortSelected = { order -> viewModel.setSortOrder(order) },
            onDismissRequest = { viewModel.showSortDialog(false) }
        )
    }

    if (uiState.showCreateFolderDialog) {
        TextInputDialog(
            title = "Create Physical Folder",
            label = "Folder Name",
            confirmButtonText = "Create",
            icon = { Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) },
            onConfirm = { name ->
                viewModel.createPhysicalFolder(name) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showCreateFolderDialog(false) }
        )
    }

    uiState.folderToRename?.let { folder ->
        TextInputDialog(
            title = "Rename Physical Folder",
            initialValue = folder.name,
            label = "New Folder Name",
            confirmButtonText = "Rename",
            icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) },
            onConfirm = { newName ->
                viewModel.renamePhysicalFolder(folder, newName) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showRenameFolderDialog(null) }
        )
    }

    uiState.folderToDelete?.let { folder ->
        DeleteConfirmationDialog(
            title = "Delete Folder '${folder.name}'?",
            message = if (folder.videoCount > 0)
                "This folder contains ${folder.videoCount} videos. Android Scoped Storage does not allow recursive directory deletion. Please delete or move the videos first."
            else
                "Are you sure you want to delete this empty physical folder?",
            confirmButtonText = "Delete Folder",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePhysicalFolder(folder) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showDeleteFolderDialog(null) }
        )
    }

    if (uiState.showCreateCollectionDialog) {
        TextInputDialog(
            title = "New Virtual Collection",
            label = "Collection Name",
            confirmButtonText = "Create",
            icon = { Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.secondary) },
            onConfirm = { name ->
                viewModel.createCollection(name) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showCreateCollectionDialog(false) }
        )
    }

    uiState.collectionToRename?.let { collection ->
        TextInputDialog(
            title = "Rename Collection",
            initialValue = collection.name,
            label = "New Collection Name",
            confirmButtonText = "Rename",
            icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.secondary) },
            onConfirm = { newName ->
                viewModel.renameCollection(collection.id, newName) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showRenameCollectionDialog(null) }
        )
    }

    uiState.collectionToDelete?.let { collection ->
        DeleteConfirmationDialog(
            title = "Delete Collection '${collection.name}'?",
            message = "This will delete the virtual collection. Your actual video files will NOT be deleted from device storage.",
            confirmButtonText = "Delete Collection",
            isDestructive = false,
            onConfirm = {
                viewModel.deleteCollection(collection.id) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showDeleteCollectionDialog(null) }
        )
    }

    if (uiState.showAddToCollectionSheet) {
        AddToCollectionSheet(
            collections = uiState.collections,
            videoCount = uiState.selectedVideoIds.size,
            onCreateNewCollection = {
                viewModel.showAddToCollectionSheet(false)
                viewModel.showCreateCollectionDialog(true)
            },
            onConfirm = { collectionIds ->
                viewModel.addSelectedVideosToCollections(collectionIds) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showAddToCollectionSheet(false) }
        )
    }

    if (uiState.showMoveToFolderSheet) {
        MoveToFolderSheet(
            folders = uiState.folders,
            videoCount = uiState.selectedVideoIds.size,
            onCreateFolderClick = {
                viewModel.showMoveToFolderSheet(false)
                viewModel.showCreateFolderDialog(true)
            },
            onConfirm = { targetFolder ->
                viewModel.requestMoveSelectedVideos(targetFolder) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showMoveToFolderSheet(false) }
        )
    }

    if (uiState.showDeleteConfirmationDialog) {
        val count = uiState.selectedVideoIds.size
        DeleteConfirmationDialog(
            title = "Delete $count Video${if (count == 1) "" else "s"}?",
            message = "This will permanently delete the selected video files from your device storage using Android Scoped Storage.",
            confirmButtonText = "Delete Permanently",
            isDestructive = true,
            onConfirm = {
                viewModel.requestDeleteSelectedVideos { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.showDeleteVideosDialog(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LibraryScreenContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onTabSelected: (Int) -> Unit,
    onFolderSelected: (FolderItem?) -> Unit,
    onCollectionSelected: (VideoCollection?) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSortClick: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val tabs = listOf(
        "All (${state.allVideos.size})",
        "Folders (${state.folders.size})",
        "Collections (${state.collections.size})",
        "4K UHD",
        "Shorts"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.isMultiSelectActive) {
                // Multi-Select Top App Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${state.selectedVideoIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        val allSelected = state.selectedVideoIds.size == state.displayedVideos.size && state.displayedVideos.isNotEmpty()
                        TextButton(
                            onClick = {
                                if (allSelected) viewModel.clearSelection()
                                else viewModel.selectAllVideos()
                            }
                        ) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Standard Top App Bar
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when {
                                state.selectedFolder != null -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.selectedFolder.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                state.selectedCollection != null -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CollectionsBookmark,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.selectedCollection.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Library",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (state.selectedFolder != null) {
                            IconButton(onClick = { onFolderSelected(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Folders")
                            }
                        } else if (state.selectedCollection != null) {
                            IconButton(onClick = { onCollectionSelected(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Collections")
                            }
                        }
                    },
                    actions = {
                        // Search Action
                        IconButton(onClick = onSearchToggle, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Plus / Add Action (Context-sensitive)
                        when {
                            state.selectedFolder != null -> {
                                Box {
                                    IconButton(onClick = { overflowMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Folder Options")
                                    }
                                    DropdownMenu(
                                        expanded = overflowMenuExpanded,
                                        onDismissRequest = { overflowMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename Folder") },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                            onClick = {
                                                overflowMenuExpanded = false
                                                viewModel.showRenameFolderDialog(state.selectedFolder)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                overflowMenuExpanded = false
                                                viewModel.showDeleteFolderDialog(state.selectedFolder)
                                            }
                                        )
                                    }
                                }
                            }
                            state.selectedCollection != null -> {
                                Box {
                                    IconButton(onClick = { overflowMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Collection Options")
                                    }
                                    DropdownMenu(
                                        expanded = overflowMenuExpanded,
                                        onDismissRequest = { overflowMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename Collection") },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                            onClick = {
                                                overflowMenuExpanded = false
                                                viewModel.showRenameCollectionDialog(state.selectedCollection)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Collection", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                overflowMenuExpanded = false
                                                viewModel.showDeleteCollectionDialog(state.selectedCollection)
                                            }
                                        )
                                    }
                                }
                            }
                            state.selectedTabIndex == 1 -> {
                                IconButton(
                                    onClick = { viewModel.showCreateFolderDialog(true) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            state.selectedTabIndex == 2 -> {
                                IconButton(
                                    onClick = { viewModel.showCreateCollectionDialog(true) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New Collection", tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            else -> {
                                IconButton(onClick = onSortClick, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Refresh Action
                        IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        bottomBar = {
            if (state.isMultiSelectActive && state.selectedVideoIds.isNotEmpty()) {
                // Multi-Select Bottom Action Rail
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Add to Collection
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.showAddToCollectionSheet(true) }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text("Collect", style = MaterialTheme.typography.labelSmall)
                        }

                        // 2. Move to Physical Folder
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.showMoveToFolderSheet(true) }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text("Move", style = MaterialTheme.typography.labelSmall)
                        }

                        // 3. Remove from Collection (if in collection drilldown)
                        if (state.selectedCollection != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.removeVideosFromCurrentCollection(state.selectedVideoIds.toList()) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                Text("Remove", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // 4. Delete Physical Files
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.showDeleteVideosDialog(true) }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            AnimatedVisibility(
                visible = state.isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search videos or folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Tabs Row (Hidden during folder or collection drilldown)
            if (state.selectedFolder == null && state.selectedCollection == null) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTabIndex == index,
                            onClick = { onTabSelected(index) },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (state.selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }

            // Permission status check
            when (state.permissionStatus) {
                GalleryPermissionStatus.DENIED -> {
                    PermissionRequiredCard(
                        onRequestPermission = onRequestPermission,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                GalleryPermissionStatus.PARTIAL -> {
                    PartialAccessBanner(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                else -> {}
            }

            // Main Content Area
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when {
                    // 1. Folders Tab (Main level)
                    state.selectedFolder == null && state.selectedCollection == null && state.selectedTabIndex == 1 -> {
                        FoldersTabContent(
                            folders = state.folders,
                            onFolderClick = onFolderSelected,
                            onRenameClick = { viewModel.showRenameFolderDialog(it) },
                            onDeleteClick = { viewModel.showDeleteFolderDialog(it) },
                            onCreateFolderClick = { viewModel.showCreateFolderDialog(true) }
                        )
                    }

                    // 2. Collections Tab (Main level)
                    state.selectedFolder == null && state.selectedCollection == null && state.selectedTabIndex == 2 -> {
                        CollectionsTabContent(
                            collections = state.collections,
                            onCollectionClick = onCollectionSelected,
                            onRenameClick = { viewModel.showRenameCollectionDialog(it) },
                            onDeleteClick = { viewModel.showDeleteCollectionDialog(it) },
                            onCreateCollectionClick = { viewModel.showCreateCollectionDialog(true) }
                        )
                    }

                    // 3. Videos Grid (All Videos, Folder Drilldown, Collection Drilldown, 4K, Shorts)
                    else -> {
                        VideosGridContent(
                            videos = state.displayedVideos,
                            isMultiSelectActive = state.isMultiSelectActive,
                            selectedVideoIds = state.selectedVideoIds,
                            onVideoClick = onVideoClick,
                            onVideoLongClick = onVideoLongClick,
                            emptyMessage = when {
                                state.selectedCollection != null -> "This collection has no videos yet.\nSelect videos from your library to add them here."
                                state.selectedFolder != null -> "No videos found in this folder."
                                else -> "No videos found."
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<FolderItem>,
    onFolderClick: (FolderItem) -> Unit,
    onRenameClick: (FolderItem) -> Unit,
    onDeleteClick: (FolderItem) -> Unit,
    onCreateFolderClick: () -> Unit
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Folders Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCreateFolderClick) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Folder")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(folders, key = { _, f -> f.id }) { _, folder ->
                FolderCard(
                    folder = folder,
                    onClick = { onFolderClick(folder) }
                )
            }
        }
    }
}

@Composable
private fun CollectionsTabContent(
    collections: List<VideoCollection>,
    onCollectionClick: (VideoCollection) -> Unit,
    onRenameClick: (VideoCollection) -> Unit,
    onDeleteClick: (VideoCollection) -> Unit,
    onCreateCollectionClick: () -> Unit
) {
    if (collections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Virtual Collections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create custom playlists to organize your videos without duplicating files on storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCreateCollectionClick) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Collection")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(collections, key = { _, c -> c.id }) { _, collection ->
                CollectionCard(
                    collection = collection,
                    onClick = { onCollectionClick(collection) },
                    onRenameClick = { onRenameClick(collection) },
                    onDeleteClick = { onDeleteClick(collection) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideosGridContent(
    videos: List<VideoItem>,
    isMultiSelectActive: Boolean,
    selectedVideoIds: Set<Long>,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    emptyMessage: String
) {
    if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(videos, key = { _, v -> v.id }) { index, video ->
                val isSelected = selectedVideoIds.contains(video.id)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                ) {
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video, index) },
                        onLongClick = { onVideoLongClick(video) }
                    )

                    // Multi-select Checkmark overlay
                    if (isMultiSelectActive) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(26.dp)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
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
