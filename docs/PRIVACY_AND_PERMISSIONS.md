# ZUNO — Privacy Philosophy & Android Permissions

## 1. Privacy Philosophy

ZUNO was created around an uncompromising privacy principle: **Your personal media and viewing habits belong exclusively to you.**

- **0% Cloud Uploads:** No analytics, no metrics, no crash reporting sent across the internet.
- **0% Accounts or Logins:** The application requires no email, phone number, password, or third-party authentication.
- **0% Advertisements:** No ad SDKs, banners, interstitials, or tracking identifiers.
- **100% Offline Operation:** Works completely in Airplane Mode without network connectivity.

---

## 2. Android Permissions Explained

ZUNO requests only the minimal permissions required to discover and play local videos and notify you of break intervals:

| Permission | API Level | Purpose | User Justification |
| :--- | :--- | :--- | :--- |
| `READ_MEDIA_VIDEO` | API 33+ (Android 13+) | Discover and index video files stored on device storage | Essential for reading local video metadata, duration, resolution, and thumbnails. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | API 34+ (Android 14+) | Partial media access permission support | Allows users on Android 14+ to grant access to selected videos without full storage access. |
| `READ_EXTERNAL_STORAGE` | API 26–32 | Legacy media access for devices running Android 8 through 12 | Essential for indexing videos on older Android releases. |
| `POST_NOTIFICATIONS` | API 33+ (Android 13+) | Dispatch Viewing Reminder notifications | Optional. Used exclusively to send local break reminders when the user-selected interval is reached. |

---

## 3. Scoped Storage & Security Architecture

1. **Scoped Storage Compliance:**
   - ZUNO interfaces with Android's MediaStore API (`MediaStore.Video.Media.EXTERNAL_CONTENT_URI`) using content URIs.
   - Videos are streamed directly to ExoPlayer via ContentResolver URIs (`content://media/external/video/media/...`).
   - Private user data (notes, likes, collections, watch history) is stored inside the app-private SQLite database (`/data/data/com.binay.vscroll/databases/vscroll_database.db`), which is sandboxed by Linux UID permissions from other apps.
2. **File Sharing via FileProvider:**
   - When sharing a video via the Android sharesheet, temporary URI read permissions are granted strictly to the target application via `androidx.core.content.FileProvider`.
3. **No Unsolicited Background Processing:**
   - Playback ceases immediately upon app backgrounding or screen lock. No foreground services, wakelocks, or persistent background tasks remain active when the app is closed.

