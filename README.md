# JoiPlay Shortcut Generator

Browse your JoiPlay library, launch games, pin games to the Android Home screen,
and generate shortcut files for Android frontends such as iiSU.

Generated `.jp` and `.joiplay` files contain only the game's JoiPlay library ID;
they do not contain or copy any game files. A compatible Modified JoiPlay build
and the configured games must be installed on the same Android device.

## Why Modified JoiPlay is required

Standard JoiPlay does not expose the integration points this app needs. The
provided Modified JoiPlay builds add:

- A permission-protected, read-only provider that exposes the JoiPlay library
  details used by the generator.
- A read-only artwork endpoint that loads each game's registered icon, including
  artwork in scoped-storage locations, without broad storage access.
- An exported `ShortcutActivity` that accepts a JoiPlay game ID, allowing the
  generator, pinned Home-screen shortcuts, and compatible frontends to launch
  that game directly.

These changes let the generator read and display the existing JoiPlay library;
they do not copy games into the generator. Standard JoiPlay lacks the library
and artwork providers and blocks external access to `ShortcutActivity`, so it is
not compatible.

The setup guide explains how to install the modified build and includes an
important warning about backing up data before replacing an existing JoiPlay
installation.

## Setup guide

### Requirements

- Android 8.0 or newer
- Modified JoiPlay with at least one game added

### Before you start

Make sure you have:

- Your game files ready
- Permission to install APK files from your browser or file manager
- A backup of any existing JoiPlay configuration or other important data

> [!WARNING]
> The APK provided here is a modified, unofficial build of JoiPlay. Install it
> only if you trust its source. If another version of JoiPlay is already
> installed, back up any important data and uninstall it before continuing.
> Android normally cannot replace an app with an APK signed using a different
> key.

### Steps

1. Download the generator APK and the Modified JoiPlay APK for the JoiPlay version you want to use from this repository's **Releases** page.
2. Back up your existing JoiPlay data before replacing an installed JoiPlay app.
3. Install the matching modified JoiPlay APK.
4. Add or restore your games in Modified JoiPlay.
5. Install the JoiPlay Shortcut Generator APK.
6. Open JoiPlay Shortcut Generator and refresh the Library.
7. To create frontend files, select an output folder and generate the missing
   `.jp` or `.joiplay` files. To create an Android launcher shortcut, hold a
   game, open its details, and select **Pin to Home**.

Android might ask you to allow APK installation from your browser or file
manager. If Android reports a signature conflict, back up JoiPlay, uninstall
the existing JoiPlay app, and then install the included APK.

### iiSU setup

1. In JoiPlay Shortcut Generator, open **Settings** or select **Select iiSU
   folder** in the Library header.
2. Select the iiSU root folder that contains the `iiSULauncher` folder.
3. Select **Import JoiPlay Support into iiSU** and wait for **JoiPlay support
   imported** to appear.
4. In iiSU, open the **Platform** tab, choose **Menu** > **Add Console**, and add
   JoiPlay.
5. Scan the output folder containing your generated `.jp` or `.joiplay` files.

## What JoiPlay Shortcut Generator does

- Reads the game list exposed by a compatible modified JoiPlay build.
- Launches games and displays their artwork and useful library details.
- Pins individual games to the Android Home screen with their artwork.
- Generates `.jp` and `.joiplay` shortcut files for compatible frontends.
- Imports JoiPlay platform and emulator support directly into a selected iiSU
  installation, then verifies the integration whenever the library refreshes.
- Rechecks `.jp` and `.joiplay` files by their JoiPlay ID on launch and refresh,
  including format-specific badges and a live generated/total count.
- Offers list/grid views, sorting, themes, custom icons, and configurable tap
  behavior.
- Saves settings immediately, requests a settings-only Android backup after
  every change, and mirrors portable settings to the selected output folder.
  After reinstalling, select the same output folder to restore that snapshot.
  Android revokes folder and custom-image access on uninstall, so those grants
  must be selected again.

## Frontend shortcut formats

- `.jp` and `.joiplay` both write only the JoiPlay library ID.
- The extensions are interchangeable for frontends that launch JoiPlay with a
  `fileContent` route and the `%ROM_CONTENT%` command placeholder.

For iiSU, select the folder containing `iiSULauncher`, then use **Import JoiPlay
Support into iiSU** in the Library header. The generator safely adds the
JoiPlay definitions to:

```text
iiSULauncher/Emuladores/emuladores.json
iiSULauncher/Emuladores/supported_emulators.json
```

The action is duplicate-safe, supports iiSU's `consoles` wrapper format, and
changes to **JoiPlay support imported** after both files have been verified.
The injected platform definition is:

```json
{
  "shortName": "joiplay",
  "longName": "JoiPlay",
  "releaseYear": "2019",
  "releaseDate": "2019-12-16",
  "manufacturer": "JoiPlay",
  "retroAchievementsId": "NA",
  "romExtensions": [
    ".jp",
    ".JP",
    ".joiplay",
    ".JOIPLAY"
  ],
  "emulators": [
    {
      "id": "JOIPLAY",
      "name": "JoiPlay (Standalone)",
      "routeType": "fileContent",
      "commands": [
        {
          "description": "JoiPlay",
          "command": "cyou.joiplay.joiplay/.activities.ShortcutActivity --es id \"%ROM_CONTENT%\""
        }
      ],
      "packages": [
        "cyou.joiplay.joiplay"
      ]
    }
  ]
}
```

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
