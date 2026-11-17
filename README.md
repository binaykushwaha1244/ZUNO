<p align="center">
  <img src="docs/assets/branding/zuno-horizontal-lockup.png" alt="ZUNO Banner" width="480">
</p>

<p align="center">
  <strong>The Offline-First Social Video Discovery Platform for Android</strong>
</p>

<p align="center">
  <a href="#technology-stack"><img src="https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat-square" alt="Kotlin"></a>
  <a href="#technology-stack"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg?style=flat-square" alt="Jetpack Compose"></a>
  <a href="#technology-stack"><img src="https://img.shields.io/badge/Media3-ExoPlayer-orange.svg?style=flat-square" alt="Media3"></a>
  <a href="#technology-stack"><img src="https://img.shields.io/badge/Room_Database-v5-emerald.svg?style=flat-square" alt="Room Database"></a>
  <a href="#technology-stack"><img src="https://img.shields.io/badge/Target_SDK-37-red.svg?style=flat-square" alt="Target SDK"></a>
  <a href="#privacy-and-offline-philosophy"><img src="https://img.shields.io/badge/Privacy-100%25_Offline-success.svg?style=flat-square" alt="100% Offline"></a>
  <a href="#automated-testing"><img src="https://img.shields.io/badge/Tests-121%20Passed-brightgreen.svg?style=flat-square" alt="121 Tests Passed"></a>
</p>

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Product Vision](#product-vision)
3. [Offline-First Philosophy & Privacy](#offline-first-philosophy--privacy)
4. [Visual Previews & Screenshots](#visual-previews--screenshots)
5. [Key Features](#key-features)
6. [ZUNO Brand Identity & Color Palette](#zuno-brand-identity--color-palette)
7. [Technology Stack](#technology-stack)
8. [Architecture Overview](#architecture-overview)
9. [Project Folder Structure](#project-folder-structure)
10. [Application Navigation](#application-navigation)
11. [Playback Architecture & Isolated Queues](#playback-architecture--isolated-queues)
12. [Media3 & ExoPlayer Lifecycle](#media3--exoplayer-lifecycle)
13. [MediaStore Integration & Local Media Access](#mediastore-integration--local-media-access)
14. [Room Database & Schema Migrations](#room-database--schema-migrations)
15. [Likes, Bookmarks & Private Notes](#likes-bookmarks--private-notes)
16. [Watch History & Activity Analytics](#watch-history--activity-analytics)
17. [Viewing Reminders & Notification Behavior](#viewing-reminders--notification-behavior)
18. [Folder & Virtual Collection Management](#folder--virtual-collection-management)
19. [Theme System & DataStore Persistence](#theme-system--datastore-persistence)
20. [Permissions & Android Scoped Storage](#permissions--android-scoped-storage)
21. [Build Requirements & Setup](#build-requirements--setup)
22. [How to Build & Test](#how-to-build--test)
23. [Installation & Safe In-Place Updates](#installation--safe-in-place-updates)
24. [Troubleshooting & Limitations](#troubleshooting--limitations)
25. [Contribution Guidelines](#contribution-guidelines)
26. [License Placeholder](#license-placeholder)
27. [Detailed Documentation Links](#detailed-documentation-links)

---

## 1. Project Overview

**ZUNO** is a high-performance, offline-first native Android application designed to transform how users experience their local short-form videos. Built entirely with modern Android technologies—Kotlin Coroutines, Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and Room SQLite Database—ZUNO delivers the addictive, frictionless fluid swipe experience of modern social media platforms directly to your private local video library.

No feeds dictated by ad algorithms. No cloud logins. No background tracking. Just your videos, beautifully organized and instantly playable.

---

## 2. Product Vision

Modern video platforms offer fluid, immersive interfaces, but lock users into engagement algorithms, tracking networks, and cloud dependencies. ZUNO extracts the finest interaction paradigms of modern short-form discovery—vertical swipe feeds, explore discovery grids, double-tap hearts, and volume/brightness scrubbing—and applies them to local device storage. 

ZUNO treats short video viewing with respect: rather than gamifying your watch time with addictive streaks or goals, ZUNO provides transparent viewing analytics and health-conscious **Viewing Reminders** to help you stay mindful of your digital balance.

---

## 3. Offline-First Philosophy & Privacy

- **100% Offline & Private:** Zero internet permissions required for core features. All discovery, playback, interactions, and analytics run solely on-device.
- **No Cloud Synchronization or Remote Telemetry:** Your watch history, notes, and liked videos never leave your local hardware.
- **Scoped Storage Compliance:** Accesses only video files using Android's modern `MediaStore` content provider APIs, keeping user documents and files inaccessible.
- **Airplane Mode Ready:** Verified for full operational reliability in complete offline environments.

---

## 4. Visual Previews & Screenshots

| Shorts Explore Grid | Immersive Video Player | Activity & Break Reminders |
| :---: | :---: | :---: |
| <img src="docs/assets/screenshots/zuno-shorts-grid.png" width="240" alt="Shorts Explore Grid"> | <img src="docs/assets/screenshots/zuno-player.png" width="240" alt="Video Player"> | <img src="docs/assets/screenshots/zuno-activity-reminders.png" width="240" alt="Activity Analytics"> |

| Library & Collections | Settings & About ZUNO | Launcher Icon Preview |
| :---: | :---: | :---: |
| <img src="docs/assets/screenshots/zuno-library.png" width="240" alt="Library All Tab"> | <img src="docs/assets/screenshots/zuno-settings-about.png" width="240" alt="Settings & About"> | <img src="docs/assets/branding/zuno-launcher-preview.png" width="240" alt="Launcher Icon"> |

---

## 5. Key Features

- **Shorts Discovery Grid:** A 3-column discovery feed with dynamic thumbnail generation, duration badges, and HD resolution tags.
- **Randomized Shuffle Feed:** Session-stable Fisher-Yates shuffle engine ensuring fresh discovery without immediate repeats.
- **Gesture-Driven Playback & Double-Tap Heart:** Vertical swipe to change videos, single tap to play/pause, double tap to like with animated heart pop, left vertical slide for brightness, right vertical slide for volume, and horizontal drag to seek.
- **6 Expressive Emoji Reactions & Clean Overlay:** Long-press reaction button to reveal floating emoji bar (`❤️`, `👍`, `😂`, `😮`, `😢`, `🔥`). Automatically hides Notes, Save, and Share controls while active to prevent visual clutter, with full-screen outside-tap and back gesture dismissal.
- **Clean Viewing Mode:** One-tap distraction-free view hiding all HUD overlays, buttons, and navigation bars.
- **Offline Interactions & Instagram-Inspired Collections:** Like, bookmark, and write private notes for any local video; manage collections via a refined 2-column square grid, dedicated drilldown view, and an Instagram-inspired bottom sheet for instant multi-collection toggling.
- **About ZUNO & Technical Specifications:** Dedicated screen inside Settings detailing app version (1.0, Build 1), creator profile, 9 core architectural features, 100% offline privacy commitments, open-source acknowledgements, and expandable technical build parameters.
- **Unified Theme-Aware Empty States (`EmptyStateView`):** Polished, branded empty state surfaces with tinted circular icons, clear guidance, and contextual call-to-actions across all tabs.
- **Detailed Video Metadata Sheet:** Replaced cluttered card metadata with clean video titles and an exhaustive `VideoDetailsBottomSheet` detailing resolution, dimensions, duration, folder, size, path, and date added.
- **Health-Conscious Viewing Reminders:** Fully customizable reminder durations (arbitrary hours and minutes via direct numeric typing or synchronized steppers, plus quick presets `15m, 18m, 30m, 45m, 1h, 1h 20m, 2h`) that deliver an audible, heads-up local Android notification (`zuno_viewing_reminders_v2`) with vibration when accumulated daily watch time reaches the threshold.
- **Fluid Screen Rotation & Orientation Handling:** Seamlessly supports rotating between portrait and landscape during video playback without restarting the activity, reloading the video, or resetting the active feed.
- **Activity Analytics:** 7-day watch activity breakdown, session counts, lifetime stats, and most-rewatched ranking.
- **Physical Folders & Virtual Collections:** Manage physical device storage directories or curate virtual multi-folder playlists.
- **Multi-Select Batch Actions:** Batch like, batch add to collection, batch share via native sharesheet, or batch delete with Android system confirmation.
- **Double-Tap Animated Heart & Emoji Reactions:** Double-tap anywhere during video playback to trigger an animated heart pop at the exact tap coordinate and register a Like reaction; long-press the social rail to select from 6 expressive emoji reactions (❤️ Love, 👍 Like, 😂 Haha, 😮 Wow, 😢 Sad, 🔥 Fire).
- **Dedicated Daily Screen-Time Experience:** Interactive 7-day activity chart with active day highlights, full day-by-day navigation (`<` / `>`), jump-to-Today, hero screen time, 3-metric breakdown, top video of the day, and a chronological session timeline.
- **Directional Horizontal Tab Swiping:** Swipe horizontally anywhere between the five bottom tabs (Shorts ↔ Liked ↔ Activity ↔ Library ↔ Settings) with smooth slide animations, aspect-ratio gesture protection preventing interference with vertical Shorts playback, and state preservation via `SaveableStateProvider`.
- **Five Curated Design Themes:** Featuring signature brand **ZUNO** theme plus Sunset Glow, Fresh Mint, Lavender Dream, and Midnight Dark, laid out in an ergonomic single row in Settings.

---

## 6. ZUNO Brand Identity & Color Palette

<p align="center">
  <img src="docs/assets/branding/zuno-master-symbol.png" width="120" alt="ZUNO Symbol">
</p>

ZUNO is built around an intentional three-color signature palette engineered for accessible contrast, visual warmth, and optical equilibrium:

| Color Name | Hex Code | Compose Token | Role & Usage |
| :--- | :--- | :--- | :--- |
| **Rich Violet** | `#5935A8` | `ZunoPrimary` | Primary brand actions, active icons, progress bars |
| **Midnight Navy** | `#202443` | `ZunoSecondary` | Dark structural elements, primary text, headings |
| **Warm Ivory** | `#FAF9F6` | `ZunoBackground` | Canvas background, card containers, neutral surfaces |

For design rationale, geometry specifications, and Android adaptive icon details, see [docs/BRANDING.md](docs/BRANDING.md).

---

## 7. Technology Stack

- **Language:** Kotlin 2.0.21 (JVM Toolchain 17)
- **UI Framework:** Jetpack Compose & Material 3 (1.3.x)
- **Architecture:** Clean Architecture + Unidirectional Data Flow (UDF) + MVVM
- **Video Engine:** AndroidX Media3 ExoPlayer 1.5.1
- **Local Database:** Android Room 2.7.0-alpha13 (SQLite) with safe migrations
- **Preferences:** Jetpack DataStore Preferences 1.1.2
- **Image Loading:** Coil 2.7.0 (Compose video frame decoding)
- **Build System:** Gradle 9.1.0 + Android Gradle Plugin (AGP) 8.9.0 + KSP 2.0.21-1.0.28
- **Testing:** JUnit 4, Kotlin Coroutines Test, Mockito

---

## 8. Architecture Overview

```mermaid
graph TD
    subgraph UI ["Presentation Layer (Jetpack Compose)"]
        Nav[MainNavigation]
        ShortsUI[ShortsScreen]
        PlayerUI[PlayerScreen]
        ActivityUI[ActivityScreen]
        LibraryUI[LibraryScreen]
        SettingsUI[SettingsScreen]
    end

    subgraph VM ["ViewModel Layer"]
        HomeVM[HomeViewModel]
        PlayerVM[PlayerViewModel]
        ActivityVM[ActivityViewModel]
    end

    subgraph Domain ["Domain Layer"]
        FeedQueue[FeedQueueManager]
        Models[VideoItem / FolderItem / ActivityData]
        RepoInt[Repository Interfaces]
    end

    subgraph Data ["Data Layer (Offline-First)"]
        MediaStoreRepo[MediaStoreVideoRepository]
        RoomDB[(VScrollDatabase)]
        WatchTracker[PlaybackWatchTimeTracker]
        PlayerMgr[VScrollPlayerManager]
        Reminders[ViewingReminderManager & Notifier]
        Prefs[DataStore Preferences]
    end

    UI --> VM
    VM --> Domain
    Domain --> Data
```

For full architectural details, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 9. Project Folder Structure

```
D:\Projects\VScroll/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── kotlin/com/binay/vscroll/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MainNavigation.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/              # Room Database, Entities, DAOs, Migrations
│   │   │   │   │   ├── notification/       # ViewingReminderManager & Notifier
│   │   │   │   │   ├── player/             # ExoPlayer Manager, WatchTimeTracker
│   │   │   │   │   ├── preferences/        # Theme & Activity DataStore
│   │   │   │   │   └── repository/         # MediaStore & WatchHistory Repositories
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/              # Immutable domain data models
│   │   │   │   │   └── repository/         # Repository contracts & FeedQueueManager
│   │   │   │   └── ui/
│   │   │   │       ├── activity/           # Activity analytics & viewing reminders
│   │   │   │       ├── components/         # Reusable Compose widgets
│   │   │   │       ├── home/               # Home screen & navigation tabs
│   │   │   │       ├── library/            # Library, folders, collections
│   │   │   │       ├── liked/              # Likes, saves, private notes
│   │   │   │       ├── player/             # Fullscreen video player & gestures
│   │   │   │       ├── settings/           # App settings & about section
│   │   │   │       └── theme/              # Color schemes, typography, theme dialog
│   │   │   └── res/                        # Drawables, mipmaps, values, xml paths
│   │   └── test/                           # 100 automated unit test cases
│   └── build.gradle.kts
├── docs/                                   # Detailed documentation & visual assets
│   ├── assets/                             # Branding assets & screenshots
│   ├── ARCHITECTURE.md
│   ├── BRANDING.md
│   ├── BUILD_AND_INSTALLATION.md
│   ├── DATABASE.md
│   ├── FEATURES.md
│   ├── PLAYBACK.md
│   ├── PRIVACY_AND_PERMISSIONS.md
│   └── TESTING.md
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 10. Application Navigation

Navigation is managed via Compose Navigation across five bottom destinations:

1. **Shorts:** Explore 3-column discovery grid opening into fullscreen immersive playback.
2. **Liked:** Interaction hub with segment filters for Liked videos, Bookmarks, and Private Notes.
3. **Activity:** Viewing reminders, daily watch time gauge, 7-day breakdown, and watch history list.
4. **Library:** Comprehensive video catalog partitioned into *All Videos*, *Physical Folders*, and *Virtual Collections*.
5. **Settings:** Active theme selector, clean viewing mode toggle, MediaStore rescan trigger, and About ZUNO dialog.

---

## 11. Playback Architecture & Isolated Queues

ZUNO strictly enforces **playback queue isolation** via `FeedQueueManager`. When entering playback, the active queue is isolated to that specific source context:

- **`GlobalRandomFeed`:** Plays from the entire local video library in a randomized shuffle order.
- **`FolderSequential`:** Confined strictly to videos within that physical storage folder. Swiping next or previous cannot escape into unrelated videos.
- **`CollectionSequential`:** Confined strictly to videos tagged within that virtual collection.
- **`InteractionSequential`:** Dedicated isolated sequential queue for Liked Videos, Saved Videos, and Video Notes. Starts at the exact selected item with no numeric position badges to maintain a clean social UI.

---

## 12. Media3, ExoPlayer Lifecycle & Screen Rotation

- **Single Active ExoPlayer:** Maintains exactly one hardware-accelerated playback instance to conserve memory.
- **Uninterrupted Rotation:** Declares `orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden` in `AndroidManifest.xml`. Rotating between portrait and landscape preserves the playing video, current playback position, and active queue without activity destruction.
- **Audio Focus:** Handles focus loss automatically (pausing during phone calls, resuming smoothly).
- **Background Release:** Immediately ceases decoder activity on app backgrounding or screen off.
- **Aspect Scaling:** Automatically fits portrait and widescreen aspect ratios with clean letterboxing or pillarboxing.

---

## 13. MediaStore Integration & Local Media Access

- **ContentResolver Querying:** Queries `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` to discover video files.
- **Dynamic Content Observers:** Reactively updates when new videos are downloaded or deleted outside the app.
- **Cached Metadata:** Extracts resolution, duration, folder name, file size, and timestamps directly into immutable domain items.

---

## 14. Room Database & Schema Migrations

The SQLite database (`vscroll_database.db`) is managed via Room v4 with strict, non-destructive migration scripts:

- **`MIGRATION_1_2`:** Added `liked_videos`, `saved_videos`, and `video_notes` tables.
- **`MIGRATION_2_3`:** Added `watch_history` and `daily_watch_records` tables.
- **`MIGRATION_3_4`:** Added `collections`, `collection_items`, and `folder_custom_names` tables.

For full database schema diagrams and SQL definitions, see [docs/DATABASE.md](docs/DATABASE.md).

---

## 15. Likes, Bookmarks & Private Notes

- **Likes:** Stored in `liked_videos`. Triggered via heart button or double-tap heart gesture.
- **Bookmarks:** Stored in `saved_videos`. Instant offline saving for later viewing.
- **Private Notes:** Stored in `video_notes`. Write personal thoughts or time-codes attached to any video.
- All interactions remain strictly on-device in SQLite.

---

## 16. Watch History & Activity Analytics

- **`watch_history` Table:** Maintains lifetime aggregate metrics per video (first watched, last watched, session counts, total elapsed time).
- **`daily_watch_records` Table:** Granular records partitioned by date (`YYYY-MM-DD`). Automatically splits sessions that span across midnight.
- **7-Day Bar Chart:** Visualizes viewing trends across the preceding week.

---

## 17. Viewing Reminders & Notification Behavior

Replacing gamified goals and streaks, ZUNO features a mindful **Viewing Reminder**:

- **Flexible Interval Configuration:** Select from quick presets (`15m, 18m, 30m, 45m, 1h, 1h 20m, 2h`) or manually enter arbitrary durations (e.g., 1 hour 36 minutes, 18 minutes, 2 hours 7 minutes) via synchronized numeric inputs and steppers.
- **Eligible Watch Time:** `PlaybackWatchTimeTracker` counts only foreground active playback (excluding pause, buffering, background, or lock screen time).
- **Heads-Up Notification with Sound:** When accumulated daily time reaches the threshold, ZUNO delivers a high-priority heads-up notification on channel `zuno_viewing_reminders_v2` with the system default notification sound and double-pulse vibration:
  - *Title:* `Time for a little break`
  - *Body:* `You've been watching for 1 hour 36 minutes. Consider taking a short break.`
- **Deduplication:** Notifies only once per threshold window per calendar day. Re-arms cleanly on date change.

---

## 18. Folder & Virtual Collection Management

- **Physical Folders:** Mirrors real storage directories. Supports physical folder creation and folder renaming where Android storage permissions permit.
- **Instagram-Inspired Virtual Collections:**
  - **Minimal Header & Dual Sub-Navigation:** Header displays `Saved` count with a `+` button to create new collections, alongside dual tabs for `All Saved` and `Collections`.
  - **2-Column Square Grid:** Displays 1:1 aspect ratio square collection cards with dynamic cover thumbnails from the latest added video, collection title, video count, and a 3-dots management menu (Rename, Delete). Empty collections display the subtle ZUNO master brand symbol.
  - **Dedicated Collection Drilldown:** Tap into any collection to view a dedicated 2-column video grid with simplified cards (resolution badge, duration pill, and title without distracting play buttons or folder names).
  - **Instagram-Inspired Save Bottom Sheet:** Save any video to one or multiple collections instantly via checkmark toggles, or create a new collection inline with automatic assignment.
  - **Collection Playback:** Swiping inside a collection is 100% isolated to that collection (`CollectionSequential`) without counter badges.

---

## 19. Theme System & DataStore Persistence

ZUNO features six cohesive color themes:
1. **ZUNO (Default):** Rich violet, midnight navy, warm ivory.
2. **Ocean Pop:** Electric blue, sky blue, crisp white.
3. **Sunset Glow:** Vibrant coral, orange, warm peach.
4. **Fresh Mint:** Mint green, emerald, lively white.
5. **Lavender Dream:** Royal lavender, violet, soft pink.
6. **Midnight (Dark):** Obsidian black, electric cyan, violet.

Theme selection is persisted in Jetpack DataStore (`vscroll_settings.preferences_pb`) and animated dynamically upon selection.

---

## 20. Permissions & Android Scoped Storage

ZUNO requests minimal permissions:
- `READ_MEDIA_VIDEO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 8–12): Index local video files.
- `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+): Partial media access support.
- `POST_NOTIFICATIONS` (Android 13+): Deliver local viewing reminders.

For a full security and permission breakdown, see [docs/PRIVACY_AND_PERMISSIONS.md](docs/PRIVACY_AND_PERMISSIONS.md).

---

## 21. Build Requirements & Setup

- **JDK:** Version 17
- **Android SDK:** Compile SDK 37, Target SDK 37, Min SDK 26
- **Gradle:** 9.1.0 with AGP 8.9.0

For step-by-step setup instructions, see [docs/BUILD_AND_INSTALLATION.md](docs/BUILD_AND_INSTALLATION.md).

---

## 22. How to Build & Test

### Assemble Debug APK
```powershell
.\gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Run Automated Tests
```powershell
.\gradlew.bat test
```
Result: **114 / 114 tests passing (100% pass rate)**. See [docs/TESTING.md](docs/TESTING.md) for details.

---

## 23. Installation & Safe In-Place Updates

### Install via ADB
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
> [!IMPORTANT]
> Always use the `-r` flag to perform an **in-place update**. Never uninstall the app prior to updating, as Android will delete the private SQLite database and DataStore preferences.

### Standalone Distributable APK
A pre-built standalone APK is available at:
`D:\Projects\ZUNO-Release\ZUNO-debug.apk`

- **File Size:** 25,657,623 bytes (24.47 MB)
- **SHA-256:** `28FE934DC18BEAE3EF3A66167ED5A3790C56958C97C07E72CD867B0CA5F00081`

---

## 24. Troubleshooting & 4K Playback Limitations

- **4K UHD Hardware Decoding:** Landscape 4K videos (e.g., 3840×2160 up to 60fps in WebM VP9, MP4 AVC/HEVC) decode smoothly in hardware via MediaTek decoders (`c2.mtk.*`).
- **Portrait 4K Hardware Boundary:** Extreme portrait 4K recordings (e.g. 2160×3840) exceed the chipset's physical hardware limit of 2176 vertical lines on this device. ZUNO displays a graceful, informative error card rather than failing silently or causing native driver crashes.
- **Storage Permission Required:** Videos will not populate until storage access is granted in Android system settings.
- **Android Scoped Storage Restrictions:** File renaming and deletion on external SD cards require Android system SAF consent prompts.

---

## 25. Contribution Guidelines

Contributions are welcome! Please follow these standards:
1. Ensure all code conforms to Kotlin coding conventions.
2. Run `.\gradlew.bat test` to confirm all 121 tests pass without regression.
3. Preserve the offline-first, zero-tracking philosophy.

---

## 26. License Placeholder

*Note: An open-source license has not yet been selected for this project.*

---

## 27. Detailed Documentation Links

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — System layers, state flow, and component relationships.
- [docs/DATABASE.md](docs/DATABASE.md) — Room database schemas, indices, and migration history.
- [docs/PLAYBACK.md](docs/PLAYBACK.md) — Media3 ExoPlayer lifecycle, gestures, and isolated queues.
- [docs/FEATURES.md](docs/FEATURES.md) — Complete feature catalog and user flows.
- [docs/BUILD_AND_INSTALLATION.md](docs/BUILD_AND_INSTALLATION.md) — Step-by-step compilation, ADB deployment, and sideloading guide.
- [docs/PRIVACY_AND_PERMISSIONS.md](docs/PRIVACY_AND_PERMISSIONS.md) — Privacy manifesto and Scoped Storage security model.
- [docs/BRANDING.md](docs/BRANDING.md) — Brand guidelines, geometry, and design tokens.
- [docs/TESTING.md](docs/TESTING.md) — Automated test suite specifications and device verification matrix.

