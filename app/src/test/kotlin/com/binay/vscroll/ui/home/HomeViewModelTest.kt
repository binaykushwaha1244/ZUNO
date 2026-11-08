package com.binay.vscroll.ui.home

import com.binay.vscroll.data.repository.FakeVideoRepository
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.ui.theme.VScrollThemeMode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun fakeVideoRepository_emitsSampleVideos() = runTest {
        val repository = FakeVideoRepository()
        val videos = repository.getVideos(VideoSortOrder.NEWEST_FIRST).first()

        assertEquals(5, videos.size)
    }

    @Test
    fun fakeVideoRepository_sortsByName() = runTest {
        val repository = FakeVideoRepository()
        val videos = repository.getVideos(VideoSortOrder.NAME_A_TO_Z).first()

        assertEquals("Cinematic Drone 4K - Mountain Sunset", videos[0].title)
        assertEquals("Cyberpunk City Nights Reel", videos[1].title)
    }

    @Test
    fun fakeVideoRepository_sortsByLargest() = runTest {
        val repository = FakeVideoRepository()
        val videos = repository.getVideos(VideoSortOrder.LARGEST_FIRST).first()

        assertEquals("Deep Ocean Whales & Bioluminescence", videos[0].title)
    }

    @Test
    fun videoItem_formattingAndResolution() {
        val video4K = VideoItem(
            id = 100L,
            title = "Test 4K",
            uriString = "content://test",
            durationMillis = 3665000L, // 1h 1m 5s
            sizeBytes = 1500L * 1024 * 1024,
            width = 3840,
            height = 2160
        )

        assertEquals("1:01:05", video4K.formattedDuration)
        assertEquals("1.5 GB", video4K.formattedSize)
        assertEquals("4K UHD", video4K.resolutionLabel)

        val videoShort = VideoItem(
            id = 101L,
            title = "Short Video",
            uriString = "content://test",
            durationMillis = 45000L, // 45s
            sizeBytes = 15L * 1024 * 1024,
            width = 1080,
            height = 1920
        )

        assertEquals("00:45", videoShort.formattedDuration)
        assertEquals("15.0 MB", videoShort.formattedSize)
        assertEquals("1080p FHD", videoShort.resolutionLabel)
    }

    @Test
    fun themeModes_allSixThemesExist() {
        val themes = VScrollThemeMode.entries
        assertEquals(6, themes.size)

        assertEquals(VScrollThemeMode.ZUNO, themes[0])
        assertEquals(VScrollThemeMode.OCEAN_POP, themes[1])
        assertEquals(VScrollThemeMode.SUNSET_GLOW, themes[2])
        assertEquals(VScrollThemeMode.FRESH_MINT, themes[3])
        assertEquals(VScrollThemeMode.LAVENDER_DREAM, themes[4])
        assertEquals(VScrollThemeMode.MIDNIGHT, themes[5])

        themes.forEach { mode ->
            assertNotNull(mode.displayName)
            assertNotNull(mode.primaryPreviewColor)
        }
    }
}
