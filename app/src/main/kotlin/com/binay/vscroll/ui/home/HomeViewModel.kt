package com.binay.vscroll.ui.home

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binay.vscroll.data.local.VScrollDatabase
import com.binay.vscroll.data.preferences.ThemePreferencesRepository
import com.binay.vscroll.data.repository.CollectionRepositoryImpl
import com.binay.vscroll.data.repository.MediaStoreVideoRepository
import com.binay.vscroll.data.storage.LocalStorageManager
import com.binay.vscroll.data.storage.PendingStorageAction
import com.binay.vscroll.data.storage.StorageOperationResult
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoCollection
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.domain.repository.VideoRepository
import com.binay.vscroll.ui.permission.GalleryPermissionStatus
import com.binay.vscroll.ui.permission.PermissionHelper
import com.binay.vscroll.ui.theme.VScrollThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val allVideos: List<VideoItem> = emptyList(),
    val displayedVideos: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val collections: List<VideoCollection> = emptyList(),
    val selectedFolder: FolderItem? = null,
    val selectedCollection: VideoCollection? = null,
    val selectedTabIndex: Int = 0,
    val sortOrder: VideoSortOrder = VideoSortOrder.NEWEST_FIRST,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val permissionStatus: GalleryPermissionStatus = GalleryPermissionStatus.NOT_DETERMINED,
    val isRefreshing: Boolean = false,
    val isThemeDialogVisible: Boolean = false,
    val isSortDialogVisible: Boolean = false,
    val currentTheme: VScrollThemeMode = VScrollThemeMode.ZUNO,
    val isLoading: Boolean = true,
    // Multi-Select Mode
    val isMultiSelectActive: Boolean = false,
    val selectedVideoIds: Set<Long> = emptySet(),
    // Active Dialogs & Sheets
    val showCreateFolderDialog: Boolean = false,
    val folderToRename: FolderItem? = null,
    val folderToDelete: FolderItem? = null,
    val showCreateCollectionDialog: Boolean = false,
    val collectionToRename: VideoCollection? = null,
    val collectionToDelete: VideoCollection? = null,
    val showAddToCollectionSheet: Boolean = false,
    val showMoveToFolderSheet: Boolean = false,
    val showDeleteConfirmationDialog: Boolean = false,
    val pendingConsentIntentSender: IntentSender? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: VideoRepository = MediaStoreVideoRepository(application),
    private val themeRepository: ThemePreferencesRepository = ThemePreferencesRepository(application),
    val collectionRepository: CollectionRepository = CollectionRepositoryImpl(
        VScrollDatabase.getInstance(application).collectionDao()
    ),
    val localStorageManager: LocalStorageManager = LocalStorageManager(
        context = application,
        interactionDao = VScrollDatabase.getInstance(application).videoInteractionDao(),
        watchHistoryDao = VScrollDatabase.getInstance(application).watchHistoryDao()
    )
) : AndroidViewModel(application) {

    private val _sortOrder = MutableStateFlow(VideoSortOrder.NEWEST_FIRST)
    private val _selectedTabIndex = MutableStateFlow(0)
    private val _selectedFolder = MutableStateFlow<FolderItem?>(null)
    private val _selectedCollection = MutableStateFlow<VideoCollection?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _permissionStatus = MutableStateFlow(PermissionHelper.checkPermissionStatus(application))
    private val _isRefreshing = MutableStateFlow(false)
    private val _isThemeDialogVisible = MutableStateFlow(false)
    private val _isSortDialogVisible = MutableStateFlow(false)

    // Multi-Select State
    private val _isMultiSelectActive = MutableStateFlow(false)
    private val _selectedVideoIds = MutableStateFlow<Set<Long>>(emptySet())

    // Dialog & Sheet States
    private val _showCreateFolderDialog = MutableStateFlow(false)
    private val _folderToRename = MutableStateFlow<FolderItem?>(null)
    private val _folderToDelete = MutableStateFlow<FolderItem?>(null)
    private val _showCreateCollectionDialog = MutableStateFlow(false)
    private val _collectionToRename = MutableStateFlow<VideoCollection?>(null)
    private val _collectionToDelete = MutableStateFlow<VideoCollection?>(null)
    private val _showAddToCollectionSheet = MutableStateFlow(false)
    private val _showMoveToFolderSheet = MutableStateFlow(false)
    private val _showDeleteConfirmationDialog = MutableStateFlow(false)
    private val _pendingConsentIntentSender = MutableStateFlow<IntentSender?>(null)
    private var _pendingStorageAction: PendingStorageAction? = null

    // Observe theme mode from DataStore
    val themeMode: StateFlow<VScrollThemeMode> = themeRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, VScrollThemeMode.ZUNO)

    // Observe clean viewing mode from DataStore
    val isCleanViewingMode: StateFlow<Boolean> = themeRepository.isCleanViewingMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Flow of videos dynamically re-queried when sort order changes
    private val videosFlow = _sortOrder.flatMapLatest { sort ->
        repository.getVideos(sort)
    }

    // Videos inside the selected virtual collection (if any)
    private val collectionVideosFlow = _selectedCollection.flatMapLatest { collection ->
        if (collection != null) {
            collectionRepository.observeCollectionVideos(collection.id, videosFlow)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        videosFlow,
        repository.getFolders(),
        collectionRepository.observeCollections(),
        _selectedFolder,
        _selectedCollection,
        collectionVideosFlow,
        _selectedTabIndex,
        _sortOrder,
        _searchQuery,
        _isSearchActive,
        _permissionStatus,
        _isRefreshing,
        _isThemeDialogVisible,
        _isSortDialogVisible,
        themeMode,
        _isMultiSelectActive,
        _selectedVideoIds,
        _showCreateFolderDialog,
        _folderToRename,
        _folderToDelete,
        _showCreateCollectionDialog,
        _collectionToRename,
        _collectionToDelete,
        _showAddToCollectionSheet,
        _showMoveToFolderSheet,
        _showDeleteConfirmationDialog,
        _pendingConsentIntentSender
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val videos = args[0] as List<VideoItem>
        @Suppress("UNCHECKED_CAST")
        val folders = args[1] as List<FolderItem>
        @Suppress("UNCHECKED_CAST")
        val collections = args[2] as List<VideoCollection>
        val currentFolder = args[3] as? FolderItem
        val currentCollection = args[4] as? VideoCollection
        @Suppress("UNCHECKED_CAST")
        val collectionVideos = args[5] as List<VideoItem>
        val tabIndex = args[6] as Int
        val sort = args[7] as VideoSortOrder
        val query = args[8] as String
        val isSearching = args[9] as Boolean
        val permission = args[10] as GalleryPermissionStatus
        val refreshing = args[11] as Boolean
        val themeDialog = args[12] as Boolean
        val sortDialog = args[13] as Boolean
        val theme = args[14] as VScrollThemeMode
        val isMultiSelect = args[15] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[16] as Set<Long>

        // Base items depending on drilldown
        val baseVideos = when {
            currentFolder != null -> {
                videos.filter { video ->
                    val videoPath = video.relativePath.trimEnd('/')
                    val folderPath = currentFolder.relativePath.trimEnd('/')
                    videoPath.equals(folderPath, ignoreCase = true) ||
                            video.folderName.equals(currentFolder.name, ignoreCase = true)
                }
            }
            currentCollection != null -> {
                collectionVideos
            }
            else -> {
                // Tab filtering (0: All, 1: Folders, 2: Collections, 3: 4K UHD, 4: Shorts)
                when (tabIndex) {
                    1, 2 -> emptyList() // Handled in UI as folders or collections grid
                    3 -> videos.filter { it.resolutionLabel.contains("4K", ignoreCase = true) }
                    4 -> videos.filter { it.durationMillis in 1..65000L }
                    else -> videos
                }
            }
        }

        // Search query filtering
        val searchFiltered = if (query.isBlank()) {
            baseVideos
        } else {
            baseVideos.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.folderName.contains(query, ignoreCase = true)
            }
        }

        HomeUiState(
            allVideos = videos,
            displayedVideos = searchFiltered,
            folders = folders,
            collections = collections,
            selectedFolder = currentFolder,
            selectedCollection = currentCollection,
            selectedTabIndex = tabIndex,
            sortOrder = sort,
            searchQuery = query,
            isSearchActive = isSearching,
            permissionStatus = permission,
            isRefreshing = refreshing,
            isThemeDialogVisible = themeDialog,
            isSortDialogVisible = sortDialog,
            currentTheme = theme,
            isLoading = false,
            isMultiSelectActive = isMultiSelect,
            selectedVideoIds = selectedIds,
            showCreateFolderDialog = args[17] as Boolean,
            folderToRename = args[18] as? FolderItem,
            folderToDelete = args[19] as? FolderItem,
            showCreateCollectionDialog = args[20] as Boolean,
            collectionToRename = args[21] as? VideoCollection,
            collectionToDelete = args[22] as? VideoCollection,
            showAddToCollectionSheet = args[23] as Boolean,
            showMoveToFolderSheet = args[24] as Boolean,
            showDeleteConfirmationDialog = args[25] as Boolean,
            pendingConsentIntentSender = args[26] as? IntentSender
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState(
            permissionStatus = PermissionHelper.checkPermissionStatus(application)
        )
    )

    fun checkPermissions() {
        val current = PermissionHelper.checkPermissionStatus(getApplication())
        _permissionStatus.value = current
    }

    fun onTabSelected(index: Int) {
        _selectedTabIndex.value = index
        _selectedFolder.value = null
        _selectedCollection.value = null
        clearSelection()
    }

    fun selectFolder(folder: FolderItem?) {
        _selectedFolder.value = folder
        _selectedCollection.value = null
        clearSelection()
    }

    fun selectCollection(collection: VideoCollection?) {
        _selectedCollection.value = collection
        _selectedFolder.value = null
        clearSelection()
    }

    fun setSortOrder(order: VideoSortOrder) {
        _sortOrder.value = order
        _isSortDialogVisible.value = false
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        val next = !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) {
            _searchQuery.value = ""
        }
    }

    // --- Multi-Select Actions ---

    fun enterMultiSelect(initialVideoId: Long? = null) {
        _isMultiSelectActive.value = true
        if (initialVideoId != null) {
            _selectedVideoIds.value = setOf(initialVideoId)
        }
    }

    fun toggleVideoSelection(videoId: Long) {
        val current = _selectedVideoIds.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        _selectedVideoIds.value = current
        if (current.isEmpty()) {
            _isMultiSelectActive.value = false
        }
    }

    fun selectAllVideos() {
        val allIds = uiState.value.displayedVideos.map { it.id }.toSet()
        _selectedVideoIds.value = allIds
        if (allIds.isNotEmpty()) {
            _isMultiSelectActive.value = true
        }
    }

    fun clearSelection() {
        _selectedVideoIds.value = emptySet()
        _isMultiSelectActive.value = false
    }

    fun getSelectedVideos(): List<VideoItem> {
        val ids = _selectedVideoIds.value
        return uiState.value.allVideos.filter { ids.contains(it.id) }
    }

    // --- Dialog Triggers ---

    fun showCreateFolderDialog(show: Boolean) {
        _showCreateFolderDialog.value = show
    }

    fun showRenameFolderDialog(folder: FolderItem?) {
        _folderToRename.value = folder
    }

    fun showDeleteFolderDialog(folder: FolderItem?) {
        _folderToDelete.value = folder
    }

    fun showCreateCollectionDialog(show: Boolean) {
        _showCreateCollectionDialog.value = show
    }

    fun showRenameCollectionDialog(collection: VideoCollection?) {
        _collectionToRename.value = collection
    }

    fun showDeleteCollectionDialog(collection: VideoCollection?) {
        _collectionToDelete.value = collection
    }

    fun showAddToCollectionSheet(show: Boolean) {
        _showAddToCollectionSheet.value = show
    }

    fun showMoveToFolderSheet(show: Boolean) {
        _showMoveToFolderSheet.value = show
    }

    fun showDeleteVideosDialog(show: Boolean) {
        _showDeleteConfirmationDialog.value = show
    }

    fun clearPendingConsent() {
        _pendingConsentIntentSender.value = null
        _pendingStorageAction = null
    }

    // --- Physical Folder CRUD Operations ---

    fun createPhysicalFolder(name: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = localStorageManager.createPhysicalFolder(name)
            if (result.isSuccess) {
                repository.refreshVideos()
                _showCreateFolderDialog.value = false
                onResult("Folder '${name.trim()}' created in Movies")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to create folder")
            }
        }
    }

    fun renamePhysicalFolder(folder: FolderItem, newName: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = localStorageManager.renamePhysicalFolder(folder, newName)
            if (result.isSuccess) {
                repository.refreshVideos()
                _folderToRename.value = null
                onResult("Folder renamed to '$newName'")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to rename folder")
            }
        }
    }

    fun deletePhysicalFolder(folder: FolderItem, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = localStorageManager.deletePhysicalFolder(folder)
            if (result.isSuccess) {
                repository.refreshVideos()
                _folderToDelete.value = null
                if (_selectedFolder.value?.id == folder.id) {
                    _selectedFolder.value = null
                }
                onResult("Folder '${folder.name}' deleted")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Could not delete folder")
            }
        }
    }

    // --- App-Managed Virtual Collection CRUD Operations ---

    fun createCollection(name: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = collectionRepository.createCollection(name)
            if (result.isSuccess) {
                _showCreateCollectionDialog.value = false
                onResult("Collection '${name.trim()}' created")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to create collection")
            }
        }
    }

    fun renameCollection(collectionId: Long, newName: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = collectionRepository.renameCollection(collectionId, newName)
            if (result.isSuccess) {
                _collectionToRename.value = null
                onResult("Collection renamed to '$newName'")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to rename collection")
            }
        }
    }

    fun deleteCollection(collectionId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = collectionRepository.deleteCollection(collectionId)
            if (result.isSuccess) {
                _collectionToDelete.value = null
                if (_selectedCollection.value?.id == collectionId) {
                    _selectedCollection.value = null
                }
                onResult("Collection deleted (video files preserved)")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to delete collection")
            }
        }
    }

    fun addSelectedVideosToCollections(collectionIds: List<Long>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val selectedIds = _selectedVideoIds.value.toList()
            if (selectedIds.isEmpty()) return@launch

            for (collectionId in collectionIds) {
                collectionRepository.addVideosToCollection(collectionId, selectedIds)
            }
            _showAddToCollectionSheet.value = false
            clearSelection()
            onResult("Added ${selectedIds.size} video(s) to ${collectionIds.size} collection(s)")
        }
    }

    fun removeVideosFromCurrentCollection(videoIds: List<Long>, onResult: (String) -> Unit) {
        val currentCollection = _selectedCollection.value ?: return
        viewModelScope.launch {
            val result = collectionRepository.removeVideosFromCollection(currentCollection.id, videoIds)
            if (result.isSuccess) {
                clearSelection()
                onResult("Removed ${videoIds.size} video(s) from collection")
            } else {
                onResult("Failed to remove videos from collection")
            }
        }
    }

    // --- Scoped Storage Video Move & Delete ---

    fun requestDeleteSelectedVideos(onResult: (String) -> Unit) {
        val selectedVideos = getSelectedVideos()
        if (selectedVideos.isEmpty()) return
        _showDeleteConfirmationDialog.value = false

        viewModelScope.launch {
            val result = localStorageManager.requestDeleteVideos(selectedVideos)
            when (result) {
                is StorageOperationResult.Success -> {
                    repository.refreshVideos()
                    clearSelection()
                    onResult(result.message)
                }
                is StorageOperationResult.RequiresUserConsent -> {
                    _pendingStorageAction = result.pendingAction
                    _pendingConsentIntentSender.value = result.intentSender
                }
                is StorageOperationResult.Failure -> {
                    onResult(result.error.message ?: "Failed to delete videos")
                }
            }
        }
    }

    fun requestMoveSelectedVideos(targetFolder: FolderItem, onResult: (String) -> Unit) {
        val selectedVideos = getSelectedVideos()
        if (selectedVideos.isEmpty()) return
        _showMoveToFolderSheet.value = false

        val targetRelPath = if (targetFolder.relativePath.isNotBlank()) {
            targetFolder.relativePath
        } else {
            "Movies/${targetFolder.name}/"
        }

        viewModelScope.launch {
            val result = localStorageManager.requestMoveVideos(
                videos = selectedVideos,
                targetRelativePath = targetRelPath,
                targetFolderName = targetFolder.name
            )
            when (result) {
                is StorageOperationResult.Success -> {
                    repository.refreshVideos()
                    clearSelection()
                    onResult(result.message)
                }
                is StorageOperationResult.RequiresUserConsent -> {
                    _pendingStorageAction = result.pendingAction
                    _pendingConsentIntentSender.value = result.intentSender
                }
                is StorageOperationResult.Failure -> {
                    onResult(result.error.message ?: "Failed to move videos")
                }
            }
        }
    }

    fun handleConsentResult(isSuccess: Boolean, onResult: (String) -> Unit) {
        val action = _pendingStorageAction
        clearPendingConsent()

        if (!isSuccess) {
            onResult("Storage operation was cancelled or denied by user")
            return
        }

        if (action == null) {
            viewModelScope.launch { repository.refreshVideos() }
            return
        }

        viewModelScope.launch {
            when (action) {
                is PendingStorageAction.DeleteVideos -> {
                    repository.refreshVideos()
                    clearSelection()
                    onResult("${action.videoIds.size} video(s) deleted")
                }
                is PendingStorageAction.MoveVideos -> {
                    val moveResult = localStorageManager.executePostConsentMove(
                        videoIds = action.videoIds,
                        uris = action.uris,
                        targetRelativePath = action.targetRelativePath,
                        targetFolderName = action.targetFolderName
                    )
                    repository.refreshVideos()
                    clearSelection()
                    if (moveResult.isSuccess) {
                        onResult("Moved ${moveResult.getOrThrow()} video(s) to ${action.targetFolderName}")
                    } else {
                        onResult("Failed to complete move: ${moveResult.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }

    // --- Standard Actions ---

    fun showThemeDialog(show: Boolean) {
        _isThemeDialogVisible.value = show
    }

    fun showSortDialog(show: Boolean) {
        _isSortDialogVisible.value = show
    }

    fun setThemeMode(mode: VScrollThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }

    fun setCleanViewingMode(enabled: Boolean) {
        viewModelScope.launch {
            themeRepository.setCleanViewingMode(enabled)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshVideos()
            checkPermissions()
            _isRefreshing.value = false
        }
    }
}
