# Glide Developer and Environment Guide

Comprehensive guide for building, running, debugging, and contributing to Glide on Android.

---

## Table of Contents
1. [Architecture and System Overview](#architecture-and-system-overview)
2. [Prerequisites](#prerequisites)
3. [Architecture Support (x86_64 vs. aarch64/ARM64)](#architecture-support-x86_64-vs-aarch64arm64)
4. [Dev vs. Production Build Variants](#dev-vs-production-build-variants)
5. [IDE and Editor Setup (VS Code / VSCodium)](#ide-and-editor-setup-vs-code--vscodium)
6. [Quick Start and Makefile Reference](#quick-start-and-makefile-reference)
7. [On-Device Setup and Required Permissions](#on-device-setup-and-required-permissions)
8. [Common Gotchas and Troubleshooting](#common-gotchas-and-troubleshooting)

---

## Architecture and System Overview

Glide is an edge panel and clipboard manager for Android written in Kotlin and Jetpack Compose.

```
+-------------------------------------------------------------+
|                      Android System                         |
|  * ClipboardManager Events   * Window State Changes (A11y)  |
+----------------------+-----------------------+--------------+
                       |                       |
                       v                       v
           +----------------------+  +-----------------------+
           |   ClipboardService   |  | GlideAccessibility-   |
           | (Foreground Service) |  |        Service        |
           +-----------+----------+  +-----------+-----------+
                       |                         |
                       +-----------+-------------+
                                   |
                                   v
                       +-----------------------+
                       |  ClipboardRepository  |
                       | * Deduplication       |
                       | * Cooldown (500ms)    |
                       | * FIFO Eviction (50)  |
                       | * Image File Storage  |
                       +-----------+-----------+
                                   |
                                   v
                       +-----------------------+
                       |   Room Database /     |
                       |   Internal Storage    |
                       +-----------+-----------+
                                   | Flow<List<ClipboardEntity>>
                                   v
                       +-----------------------+
                       |  ClipboardPanelView   |
                       |   (Compose in Overlay)|
                       +-----------------------+
```

### Key Technical Details
* **Compose in WindowManager Overlays:** `ClipboardPanelView` implements `LifecycleOwner` and `SavedStateRegistryOwner` manually so Jetpack Compose runs inside a system overlay window (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`) without an active Activity.
* **Android 10+ Background Clipboard Access:** Android 10+ restricts non-IME apps from reading the system clipboard in the background. Glide utilizes an `AccessibilityService` (`GlideAccessibilityService`) listening to window change events to capture clips across apps.
* **Storage and Eviction:** Up to 50 items stored via Room. Bitmaps are saved to `filesDir/clipboard_images/` and exposed across apps via AndroidX `FileProvider`.

---

## Prerequisites

### 1. JDK 17 (Recommended via SDKMAN!)
The project uses Kotlin 2.0 and AGP 8.13, which require Java 17.

```bash
# Install SDKMAN! if needed
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install and set default Java 17
sdk install java 17.0.14-tem
sdk default java 17.0.14-tem

# Expose Java to GUI apps and subshells
mkdir -p ~/.local/bin
ln -sf "$HOME/.sdkman/candidates/java/current/bin/java" ~/.local/bin/java
ln -sf "$HOME/.sdkman/candidates/java/current/bin/javac" ~/.local/bin/javac
```

### 2. Android SDK Command-Line Tools
Ensure the Android SDK is installed at `$HOME/Android/Sdk` (Linux) or `$HOME/Library/Android/sdk` (macOS), with:
* **Platform:** `platforms;android-36`
* **Build Tools:** `build-tools;35.0.0` or `36.0.0`
* **Platform Tools:** `platform-tools` (provides `adb`)

```bash
# Accept all SDK licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```

### 3. Physical Device or Emulator
* Enable **Developer Options** and **USB Debugging** on the target device.
* Connect via USB and accept the debugging prompt on the device screen ("Always allow from this computer").
* Verify connection: `make devices` or `adb devices -l`.

---

## Architecture Support (x86_64 vs. aarch64/ARM64)

### Standard Environments (x86_64 Linux, Windows, macOS)
Standard x86_64 systems and Apple Silicon Macs (via Google-provided macOS aarch64 binaries) compile without additional configuration using `./gradlew assembleDebug`.

---

### Linux aarch64 / ARM64 (Asahi Linux, Arch ARM, Raspberry Pi, Ampere)
> [!WARNING]
> Google does not distribute native Linux `aarch64` binaries for `aapt2` on Google Maven. Running the standard x86_64 `aapt2` under `qemu-user` on Linux ARM64 fails with:
> `AAPT2 Daemon #0: Unexpected error output: qemu-x86_64: Cannot allocate vsyscall page`
> `AAPT2 Daemon startup failed`

#### Solution for Linux aarch64:
1. Download native ARM64 build tools (compiled from AOSP):
   ```bash
   curl -L -o /tmp/arm-build-tools.tar.xz https://github.com/Commit451/android-arm-build-tools/releases/download/platform-tools-37.0.0/android-build-tools-37.0.0-linux-arm64-20260618.tar.xz
   tar -xf /tmp/arm-build-tools.tar.xz -C $HOME/Android/Sdk/build-tools/35.0.0/
   rm /tmp/arm-build-tools.tar.xz
   ```

2. **Automatic Makefile Handling:**
   The project `Makefile` automatically detects Linux `aarch64` / `arm64` via `uname -m` and passes:
   `-Pandroid.aapt2override=$(ANDROID_HOME)/build-tools/35.0.0/aapt2 -Pandroid.aapt2FromMavenOverride=$(ANDROID_HOME)/build-tools/35.0.0/aapt2`

3. **Global Machine Environment Configuration:**
   To ensure standalone tools and IDEs also resolve native AAPT2, add the following exports to your shell configuration (`~/.bashrc`, `~/.zshrc`, or `config.fish`):
   ```bash
   export ORG_GRADLE_PROJECT_android.aapt2override="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"
   export ORG_GRADLE_PROJECT_android.aapt2FromMavenOverride="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"
   ```

---

## Dev vs. Production Build Variants

Glide is configured so that development and production builds can be installed simultaneously on the same physical phone without collision.

| Setting | Development (`MODE=dev` / default) | Production (`MODE=prod`) |
| :--- | :--- | :--- |
| **Application ID** | `com.dagimg.glide.dev` | `com.dagimg.glide` |
| **App Grid Label** | `Glide Dev` | `Glide` |
| **FileProvider Authority** | `com.dagimg.glide.dev.fileprovider` | `com.dagimg.glide.fileprovider` |
| **APK Output** | `app-debug.apk` | `app-release-unsigned.apk` |
| **Build Command** | `make build` | `make build MODE=prod` |

* Dynamic label is handled via `manifestPlaceholders["appLabel"]` in `build.gradle.kts` and `${appLabel}` in `AndroidManifest.xml`.
* Dynamic `FileProvider` authority is handled via `${applicationId}.fileprovider` in `AndroidManifest.xml` and `${context.packageName}.fileprovider` in code.

---

## IDE and Editor Setup (VS Code / VSCodium)

### Recommended Extension: `fwcd.kotlin` (Kotlin Language Server)
The standard and stable extension for Kotlin development in VS Code / VSCodium is **`fwcd.kotlin`**.

```bash
# Install the extension via CLI
codium --install-extension fwcd.kotlin
```

### Workspace Configuration (`.vscode/settings.json`)
The repository includes `.vscode/settings.json` configured with:
```json
{
  "kotlin.languageServer.enabled": true,
  "kotlin.java.home": "/home/jd/.sdkman/candidates/java/current",
  "kotlin.compiler.jvmTarget": "11",
  "files.associations": {
    "*.gradle.kts": "kotlin"
  }
}
```

### Why Avoid `jetbrains.kotlin-server` on Android Modules
The experimental `jetbrains.kotlin-server` extension currently has a known upstream classloader leak when importing Android Gradle projects with Kotlin 2.0 (`ClassCastException: IdeaKotlinResolvedBinaryDependency cannot be cast to IdeaKotlinDependency`). If using VSCodium for Android code, use `fwcd.kotlin` or Android Studio.

---

## Quick Start and Makefile Reference

The `Makefile` contains all workflow shortcuts with automatic environment discovery.

| Target | Command | Description |
| :--- | :--- | :--- |
| `make dev` | Fast compile + install + launch + stream filtered logs (Fastest loop) |
| `make build` | Build debug APK (`MODE=dev`) |
| `make build MODE=prod` | Build production APK |
| `make install` | Build and install APK to connected device (`MODE=dev` by default) |
| `make install MODE=prod` | Build and install production release APK |
| `make launch` | Launch MainActivity on device |
| `make fast-run-no-debug` | Force-stop, deploy update, and launch without debugger |
| `make applogs` | Stream logcat logs filtered by the active package (`com.dagimg.glide.dev`) |
| `make force-stop` | Force-stop the active package on the device |
| `make ps` | Check if Glide process is running on the device |
| `make devices` | List attached ADB devices |

---

## On-Device Setup and Required Permissions

When launching Glide for the first time, grant the following permissions in the dashboard:

1. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):**
   * Required to draw the floating edge handle pill and overlay panel.
2. **Accessibility Service (`GlideAccessibilityService`):**
   * Required for background clipboard monitoring on Android 10+.
   * Tap the permission card in the app, locate **Glide** (or **Glide Dev**) under *Downloaded Apps / Accessibility*, and toggle it **ON**.
3. **Notifications (`POST_NOTIFICATIONS`):**
   * Required on Android 13+ to maintain the foreground service lifecycle and prevent Android from killing the background listener.
4. **Battery Optimization (OEM Specific):**
   * On device vendors with aggressive task managers, set the app battery usage to **Unrestricted** in App Info.

---

## Common Gotchas and Troubleshooting

### 1. ADB Shows `unauthorized`
```
00151155C000444    unauthorized
```
* **Cause:** The phone is connected, but the RSA key has not been accepted on the device.
* **Fix:** Unlock the phone and tap **Allow USB debugging** (check "Always allow from this computer").

### 2. Activity Class Does Not Exist On Launch
```
Error: Activity class {com.dagimg.glide.dev/com.dagimg.glide.dev.MainActivity} does not exist.
```
* **Cause:** `applicationIdSuffix = ".dev"` alters the package identifier, but the Kotlin class namespace remains `com.dagimg.glide`.
* **Fix:** Launch with the fully qualified class path:
  `adb shell am start -n com.dagimg.glide.dev/com.dagimg.glide.MainActivity` (configured in `Makefile`).

### 3. Missing `keystore.properties`
```
null cannot be cast to non-null type kotlin.String
```
* **Cause:** `app/build.gradle.kts` attempting to cast null keystore properties during release configuration.
* **Fix:** Handled dynamically via `val hasReleaseKeystore = keystorePropertiesFile.exists() && keystoreProperties.containsKey("storeFile")`.

### 4. Language Server `JAVA_HOME is not set` Crash
```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
The Kotlin Language Client server crashed 5 times in the last 3 minutes.
```
* **Cause:** VSCodium launched from a desktop session does not source interactive `.bashrc`/`.zshrc` SDKMAN exports into background extension subshells.
* **Fix:** Symlink Java into `~/.local/bin` (`ln -sf $HOME/.sdkman/candidates/java/current/bin/java ~/.local/bin/java`) and set `kotlin.java.home` in `.vscode/settings.json`.

### 5. `ClassCastException` in Language Server Importer
```
java.lang.ClassCastException: class IdeaKotlinResolvedBinaryDependency cannot be cast to class IdeaKotlinDependency
```
* **Cause:** The experimental `jetbrains.kotlin-server` extension has an internal classloader conflict with Kotlin 2.0 Android Gradle tooling client sync (`tcs`).
* **Fix:** Uninstall `jetbrains.kotlin-server` and install `fwcd.kotlin`.

### 6. Stale Gradle Metadata Cache Lock
```
Could not add entry ... to cache module-metadata.bin
```
* **Fix:** Stop daemon and clear metadata cache:
  ```bash
  JAVA_HOME=$HOME/.sdkman/candidates/java/current ./gradlew --stop
  rm -rf ~/.gradle/caches/modules-2/metadata-*
  ```
