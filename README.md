# JoiPlay Shortcut Generator

## Install

### Requirements

- Android 8.0 or newer
- JoiPlay with at least one game added

### Steps

1. Download the latest release package from this repository's **Releases** page.
2. Back up your existing JoiPlay data before replacing an installed JoiPlay app.
3. Install the JoiPlay APK included with the release package.
4. Install the JoiPlay Shortcut Generator APK.
5. Open JoiPlay Shortcut Generator and refresh the Library.

Android might ask you to allow APK installation from your browser or file
manager. If Android reports a signature conflict, back up JoiPlay, uninstall
the existing JoiPlay app, and then install the included APK.

## Build from source

### Requirements

- JDK 21
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0

Clone the repository, open a terminal in its root directory, and run:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On Linux or macOS, run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds are unsigned unless Android signing is configured locally.
