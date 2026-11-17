# ZUNO — Build, Installation & Deployment Guide

## 1. System Requirements & Prerequisites

- **Host Operating System:** Windows 10/11, macOS, or Linux.
- **Java Development Kit (JDK):** JDK 17 (Eclipse Temurin, OpenJDK, or Android Studio bundled JDK).
- **Android SDK:**
  - `compileSdk`: **37**
  - `targetSdk`: **37**
  - `minSdk`: **26** (Android 8.0 Oreo or higher)
- **Android Build Tools:** Android Gradle Plugin (AGP) 8.9.0 / Gradle 9.1.0.
- **Android Device / Emulator:** Running Android 8.0 (API 26) through Android 15 (API 35).

---

## 2. Environment Setup

1. **Clone the Repository:**
   ```bash
   git clone <repository-url>
   cd VScroll
   ```

2. **Configure SDK Path (`local.properties`):**
   Ensure `local.properties` points to your local Android SDK location:
   - **Windows:**
     ```properties
     sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
     ```
   - **macOS/Linux:**
     ```properties
     sdk.dir=/Users/<YourUsername>/Library/Android/sdk
     ```

---

## 3. How to Build the Application

### Assemble Debug APK
To compile and assemble the debug APK:
- **Windows (PowerShell):**
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **macOS / Linux:**
  ```bash
  ./gradlew assembleDebug
  ```

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4. How to Run Automated Unit Tests

To run the complete test suite (122 unit tests covering Room migrations, queue management, player watch telemetry, viewing reminders, first-frame playback transitions, 6 emoji reactions, dismissal overlay management, and theme modes):
- **Windows (PowerShell):**
  ```powershell
  .\gradlew.bat test
  ```
- **macOS / Linux:**
  ```bash
  ./gradlew test
  ```

HTML test reports are generated at:
`app/build/reports/tests/testDebugUnitTest/index.html`

---

## 5. How to Install the APK on a Device

### Initial Installation via ADB
Connect your Android phone with USB debugging enabled:
```powershell
adb devices
adb install app\build\outputs\apk\debug\app-debug.apk
```

### In-Place Update (Zero Data Loss)
To update an existing installation **without losing Room database records, likes, watch history, or settings**, use the `-r` (reinstall) flag:
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> [!WARNING]
> Never use `adb uninstall com.binay.vscroll` prior to updating, as Android will delete the private application directory containing the Room database (`vscroll_database.db`) and DataStore preferences.

---

## 6. Sideloading on Another Android Device

To install ZUNO on any other physical Android device without a development environment:

1. Copy the assembled APK (`app/build/outputs/apk/debug/app-debug.apk`) or GitHub Release asset to the target device (via USB cable, Local Send, or file manager).
   - **File Size:** 25,657,623 bytes (24.47 MB)
   - **SHA-256:** `28FE934DC18BEAE3EF3A66167ED5A3790C56958C97C07E72CD867B0CA5F00081`
2. On the target device, tap the APK file in the Files application.
3. If prompted by Android, enable **Install unknown apps** for the file manager.
4. Tap **Install**.
5. Grant the **Photos and Videos** (or **Storage**) permission when prompted upon first launch so ZUNO can index local videos.

---

## 7. Troubleshooting

| Issue | Cause | Resolution |
| :--- | :--- | :--- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The existing app on device was signed with a different debug certificate. | Verify the debug keystore is consistent. |
| Videos not appearing in grid | Storage permission not granted. | Open Android Settings $\rightarrow$ Apps $\rightarrow$ ZUNO $\rightarrow$ Permissions $\rightarrow$ Enable "Photos and Videos". |
| Viewing reminder notification not showing | Notification permission denied on Android 13+. | Open Android Settings $\rightarrow$ Apps $\rightarrow$ ZUNO $\rightarrow$ Notifications $\rightarrow$ Toggle ON. |

