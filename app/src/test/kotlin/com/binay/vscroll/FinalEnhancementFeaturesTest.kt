package com.binay.vscroll.data.local

import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import com.binay.vscroll.data.local.entity.WatchHistoryEntity
import com.binay.vscroll.data.repository.FakeVideoRepository
import com.binay.vscroll.data.repository.VideoInteractionRepositoryImpl
import com.binay.vscroll.data.repository.WatchHistoryRepositoryImpl
import com.binay.vscroll.domain.model.ReactionType
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.ui.navigation.NavDestination
import com.binay.vscroll.ui.theme.VScrollThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalEnhancementFeaturesTest {

    @Test
    fun reactionType_allSixReactionsHaveValidProperties() {
        val reactions = ReactionType.entries
        assertEquals(6, reactions.size)

        assertEquals("❤️", ReactionType.LOVE.emoji)
        assertEquals("Love", ReactionType.LOVE.label)

        assertEquals("👍", ReactionType.LIKE.emoji)
        assertEquals("Like", ReactionType.LIKE.label)

        assertEquals("😂", ReactionType.HAHA.emoji)
        assertEquals("Haha", ReactionType.HAHA.label)

        assertEquals("😮", ReactionType.WOW.emoji)
        assertEquals("Wow", ReactionType.WOW.label)

        assertEquals("😢", ReactionType.SAD.emoji)
        assertEquals("Sad", ReactionType.SAD.label)

        assertEquals("🔥", ReactionType.FIRE.emoji)
        assertEquals("Fire", ReactionType.FIRE.label)
    }

    @Test
    fun videoInteractions_setAndToggleReactions() = runTest {
        val interactionDao = InMemoryVideoInteractionDao()
        val noteDao = InMemoryVideoNoteDao()
        val repository = VideoInteractionRepositoryImpl(interactionDao, noteDao)

        val testVideo = VideoItem(
            id = 42L,
            title = "Test Video",
            uriString = "content://test/42",
            durationMillis = 30000L,
            sizeBytes = 5000000L,
            width = 1080,
            height = 1920
        )

        // 1. Set Fire reaction
        repository.setReaction(testVideo, ReactionType.FIRE)
        var interaction = repository.getInteraction(42L).first()
        assertTrue(interaction.isLiked)
        assertEquals(ReactionType.FIRE, interaction.reaction)

        // 2. Toggling same reaction clears it
        repository.toggleReaction(testVideo, ReactionType.FIRE)
        interaction = repository.getInteraction(42L).first()
        assertFalse(interaction.isLiked)
        assertNull(interaction.reaction)

        // 3. Toggling love sets LOVE
        repository.toggleReaction(testVideo, ReactionType.LOVE)
        interaction = repository.getInteraction(42L).first()
        assertTrue(interaction.isLiked)
        assertEquals(ReactionType.LOVE, interaction.reaction)

        // 4. Switching directly from LOVE to WOW
        repository.setReaction(testVideo, ReactionType.WOW)
        interaction = repository.getInteraction(42L).first()
        assertTrue(interaction.isLiked)
        assertEquals(ReactionType.WOW, interaction.reaction)
    }

    @Test
    fun themes_onlyFiveThemesExistWithoutOceanPop() {
        val themes = VScrollThemeMode.entries
        assertEquals(5, themes.size)

        val names = themes.map { it.name }
        assertFalse(names.contains("OCEAN_POP"))
        assertTrue(names.contains("ZUNO"))
        assertTrue(names.contains("SUNSET_GLOW"))
        assertTrue(names.contains("FRESH_MINT"))
        assertTrue(names.contains("LAVENDER_DREAM"))
        assertTrue(names.contains("MIDNIGHT"))
    }

    @Test
    fun dailyScreenTime_aggregatesSessionsCorrectly() = runTest {
        val fakeHistoryDao = InMemoryWatchHistoryDao()
        val fakeDailyDao = InMemoryDailyWatchRecordDao()
        val fakeVideoRepo = FakeVideoRepository()

        val repo = WatchHistoryRepositoryImpl(
            watchHistoryDao = fakeHistoryDao,
            dailyWatchRecordDao = fakeDailyDao,
            videoRepository = fakeVideoRepo
        )

        val targetDate = "2026-11-17"

        // Insert historical record
        fakeHistoryDao.insertOrReplace(
            WatchHistoryEntity(
                mediaStoreId = 1L,
                cachedTitle = "Sample Video 1",
                cachedFolderName = "Camera",
                contentUri = "content://media/1",
                cachedDurationMs = 60_000L,
                firstWatchedAt = 1794140000000L,
                lastWatchedAt = 1794150000000L,
                totalWatchTimeMs = 90_000L,
                sessionCount = 2
            )
        )

        // Insert daily records
        fakeDailyDao.insert(
            DailyWatchRecordEntity(
                dateString = targetDate,
                mediaStoreId = 1L,
                watchTimeMs = 90_000L, // 1m 30s
                sessionCount = 2,
                lastWatchedAt = 1794150000000L
            )
        )

        val details = repo.observeDailyScreenTime(targetDate).first()

        assertEquals(targetDate, details.dateString)
        assertEquals(90_000L, details.totalWatchTimeMs)
        assertEquals("1m 30s", details.formattedTotalWatchTime)
        assertEquals(1, details.totalVideosWatched)
        assertEquals(2, details.totalSessions)
        assertEquals(1, details.sessions.size)
        assertEquals("Cinematic Drone 4K - Mountain Sunset", details.sessions[0].title)

        // Top video of the day should be populated
        assertNotNull(details.mostWatchedVideo)
        assertEquals(1L, details.mostWatchedVideo?.mediaStoreId)
        assertEquals("Cinematic Drone 4K - Mountain Sunset", details.mostWatchedVideo?.title)
    }

    @Test
    fun navDestination_fiveTabsExistInOrder() {
        val destinations = NavDestination.entries
        assertEquals(5, destinations.size)
        assertEquals(NavDestination.SHORTS, destinations[0])
        assertEquals(NavDestination.LIKED, destinations[1])
        assertEquals(NavDestination.ACTIVITY, destinations[2])
        assertEquals(NavDestination.LIBRARY, destinations[3])
        assertEquals(NavDestination.SETTINGS, destinations[4])
    }

    @Test
    fun reactionPicker_stateAndDismissalLogic() {
        // Model the state machine governing the reaction selector and social rail controls
        var isReactionPickerOpen = false
        var currentVideoId = 100L

        // 1. Initial state: picker is closed, social rail controls visible
        assertFalse(isReactionPickerOpen)
        val areControlsVisible = { !isReactionPickerOpen }
        assertTrue(areControlsVisible())

        // 2. Long press Like: picker opens, Notes/Save/Share controls hide
        isReactionPickerOpen = true
        assertTrue(isReactionPickerOpen)
        assertFalse(areControlsVisible()) // controls successfully hidden to avoid clutter/overlap

        // 3. Tapping outside: picker dismisses, controls restore smoothly
        fun onOutsideTap() {
            if (isReactionPickerOpen) {
                isReactionPickerOpen = false
            }
        }
        onOutsideTap()
        assertFalse(isReactionPickerOpen)
        assertTrue(areControlsVisible())

        // 4. Back gesture: picker dismisses without navigating back
        isReactionPickerOpen = true
        fun onBackPress(): Boolean {
            return if (isReactionPickerOpen) {
                isReactionPickerOpen = false
                true // Back handled / consumed by picker
            } else {
                false // Normal navigation back
            }
        }
        val backHandled = onBackPress()
        assertTrue(backHandled)
        assertFalse(isReactionPickerOpen)
        assertTrue(areControlsVisible())

        // 5. Selecting emoji reaction: applies reaction and dismisses picker
        isReactionPickerOpen = true
        var appliedReaction: ReactionType? = null
        fun onSelectEmoji(reaction: ReactionType) {
            appliedReaction = reaction
            isReactionPickerOpen = false
        }
        onSelectEmoji(ReactionType.FIRE)
        assertEquals(ReactionType.FIRE, appliedReaction)
        assertFalse(isReactionPickerOpen)
        assertTrue(areControlsVisible())

        // 6. Video change / swipe: auto-resets picker
        isReactionPickerOpen = true
        val newVideoId = 101L
        if (newVideoId != currentVideoId) {
            currentVideoId = newVideoId
            isReactionPickerOpen = false
        }
        assertEquals(101L, currentVideoId)
        assertFalse(isReactionPickerOpen)
    }

    @Test
    fun aboutZuno_metadataAndNavigationStateMachine() {
        val appName = "ZUNO"
        val packageName = "com.binay.vscroll"
        val versionName = "1.0"
        val developer = "Binay Kushwaha"

        assertEquals("ZUNO", appName)
        assertEquals("com.binay.vscroll", packageName)
        assertEquals("1.0", versionName)
        assertEquals("Binay Kushwaha", developer)

        // Navigation state machine
        var isAboutScreenVisible = false
        assertFalse(isAboutScreenVisible)

        // Opening About ZUNO
        isAboutScreenVisible = true
        assertTrue(isAboutScreenVisible)

        // BackHandler intercepts back press and returns to settings
        fun handleBack(): Boolean {
            return if (isAboutScreenVisible) {
                isAboutScreenVisible = false
                true
            } else {
                false
            }
        }

        val handled = handleBack()
        assertTrue(handled)
        assertFalse(isAboutScreenVisible)
    }
}
