# ZUNO — Playback Architecture & Queue Isolation

## 1. Playback Architecture Overview

Video playback in ZUNO is powered by **AndroidX Media3 ExoPlayer** wrapped inside `VScrollPlayerManager` and observed by `PlaybackWatchTimeTracker`.

```mermaid
flowchart TD
    A[User Enters Playback] --> B{Playback Context}
    B -->|Shorts Tab| C[GlobalRandomFeed]
    B -->|Folder View| D[FolderSequential]
    B -->|Collection View| E[CollectionSequential]

    C --> F[FeedQueueManager]
    D --> F
    E --> F

    F --> G[VScrollPlayerManager / ExoPlayer]
    G --> H[PlaybackWatchTimeTracker]
    H -->|Eligible Active Playback| I[(Room Database)]
    H -->|Break Threshold| J[ViewingReminderManager]
    J --> K[Gentle Android Notification]
```

---

## 2. The Three Isolated Playback Contexts

A critical architectural feature of ZUNO is strict queue isolation. When viewing a specific folder or collection, swiping to the next video **never** pulls videos from outside that specific container.

| Playback Context | Trigger Destination | Queue Sorting & Shuffling | Boundary Behavior |
| :--- | :--- | :--- | :--- |
| **`GlobalRandomFeed`** | Shorts Explore Grid | Fisher-Yates shuffle with session persistence across app restarts | Loops indefinitely or scrolls through entire local library |
| **`FolderSequential`** | Folder Detail Screen | Respects user's folder sort order (Date, Name, Size, Duration) | Isolated strictly to videos inside that specific physical storage folder |
| **`CollectionSequential`** | Virtual Collection Screen | Ordered by collection insertion sequence | Isolated strictly to videos tagged within that virtual collection |

### `FeedQueueManager` Guarantees
- **Isolation Enforcement:** A swipe in a folder view cannot escape into the global explore feed.
- **Index Safety:** Queue indices are verified and clamped on insertion, deletion, or sorting changes.
- **Seamless Resume:** Opening a video from history or search sets the appropriate context without corrupting other queues.

---

## 3. Media3 ExoPlayer Lifecycle & Resource Management

- **Single Active Player Instance:** Exactly one ExoPlayer instance exists per playback surface to preserve device RAM and hardware decoder limits.
- **Audio Focus Management:** Automatically pauses playback when audio focus is lost (e.g. incoming phone call) and resumes smoothly when focus is regained.
- **Video Surface Scaling:** Automatically adjusts between `RESIZE_MODE_FIT` (for full aspect preservation) and `RESIZE_MODE_ZOOM` (for full-screen immersive filling).
- **Background Release:** When the application is sent to the background or the screen locks, playback is paused immediately and decoders are safely idled.

---

## 4. Advanced Interaction & Gesture Controls

| Gesture | Region | Action |
| :--- | :--- | :--- |
| **Vertical Swipe** | Entire Screen | Navigate to Next / Previous video in the isolated queue |
| **Single Tap** | Center | Toggle Play / Pause with animated feedback icon |
| **Double Tap** | Center | Instantly Like video with animated heart burst |
| **Left Vertical Drag** | Left 35% of screen | Adjust screen brightness with real-time HUD indicator |
| **Right Vertical Drag** | Right 35% of screen | Adjust media volume with real-time HUD indicator |
| **Horizontal Drag** | Center 30% of screen | Scrub playback position forward / backward with timestamp overlay |
| **Long Press** | Right Panel Toggle | Enable Clean Viewing Mode (hides all overlays and bottom nav) |

---

## 5. Clean Viewing Mode

Clean Viewing Mode removes all distracting UI elements (interaction buttons, video titles, folder badges, and navigation bars) to provide an unobstructed video watching experience.
- Can be toggled from the player overlay button or via the Settings tab.
- Preference is persistently saved in DataStore.

---

## 6. Video Quality & Track Selection Optimization

To guarantee that local videos play with 100% original fidelity without downscaling or artifacting:
1. **Explicit Track Selection:** `VScrollPlayerManager` configures `DefaultTrackSelector` with:
   - `setForceHighestSupportedBitrate(true)`: Forces ExoPlayer to always choose the maximum bitrate stream.
   - `setExceedRendererCapabilitiesIfNecessary(true)` and `setExceedVideoConstraintsIfNecessary(true)`: Ensures the player does not throttle or clamp resolution based on conservative default thresholds.
2. **Direct MediaStore Content URIs:** MediaItems are constructed directly from Android MediaStore content URIs (`content://media/external/video/media/{id}`) without compression, re-encoding, or proxy layers.
3. **Aspect-Ratio-Preserving Fit:** `PlayerView` utilizes `AspectRatioFrameLayout.RESIZE_MODE_FIT` with a deep black background (`Color.Black`), ensuring videos are letterboxed or pillarboxed cleanly without blur bleeding from cropped thumbnails.
4. **Verified Codec & Resolution Tiers:** Tested and confirmed natively on physical device hardware:
   - **480p SD:** 480x864 AVC (H.264)
   - **720p HD:** 720x1280 AVC (H.264)
   - **1080p FHD:** 1920x1080 HEVC (H.265) / AVC
   - **4K UHD (2160p):** 3840x2160 AV1 (`video/av01`) hardware decoded natively at 100% resolution in ~302ms cold start.

---

## 7. Swipe Transition Fluidity & Latency Elimination

Swiping between videos is engineered for instant, zero-flicker transitions:
1. **Decoder Retention:** Eliminated `activePlayer.stop()` calls prior to `setMediaItem(mediaItem, resetPosition = true)`. This avoids tearing down and rebuilding native MediaCodec hardware decoder instances on every page switch.
2. **Premature Buffering Removal:** Removed synchronous `isBuffering = true` state overrides. Buffering spinners are triggered strictly when ExoPlayer reports `Player.STATE_BUFFERING`, preventing brief spinner flickers on local fast-reading storage.
3. **Preloading & Viewport Caching:**
   - `VerticalPager` specifies `beyondViewportPageCount = 1`, ensuring adjacent page surfaces and compose nodes are pre-composed before a swipe gesture begins.
   - Next-video thumbnail is pre-fetched into memory LRU cache via `ThumbnailLoader` when a page activates, providing zero-latency image availability.
4. **Frame-Accurate First Frame Synchronization:**
   - `PlayerView.setShutterBackgroundColor(Color.TRANSPARENT)` allows the thumbnail placeholder to remain visible during decoder initialization.
   - The thumbnail placeholder hides dynamically only when `Player.Listener.onRenderedFirstFrame()` fires, guaranteeing zero black frames or visual tearing between items.

