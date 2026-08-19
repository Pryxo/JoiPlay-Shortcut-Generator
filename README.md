# JoiPlay Shortcut Generator

Create a small Android launcher APK that opens a specific JoiPlay game. This is
useful with launchers and android frontends that display installed apps but do
not support JoiPlay's native Android shortcuts.

The generated APK contains no game files. The provided Modified JoiPlay build
and the configured game must already be installed on the same Android device.

## Why Modified JoiPlay is required

The generated launcher starts JoiPlay's `ShortcutActivity` with the selected
game ID. Standard JoiPlay does not allow another installed app to start this
activity, so the launcher requires the provided modified build.

The setup guide explains how to install the modified build and includes an
important warning about backing up data before replacing an existing JoiPlay
installation.

# Setup guide
## Requirements

- Android 8.0 or newer
- Modified JoiPlay with at least one game added

## Before you start

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

1. Download the generator APK and the modified JoiPlay APK matching your JoiPlay version from this repository's **Releases** page.
2. Back up your existing JoiPlay data before replacing an installed JoiPlay app.
3. Install the matching modified JoiPlay APK.
4. Add your games back into the new Modified JoiPlay.
5. Install the JoiPlay Shortcut Generator APK.
6. Select the folder where it generates the shortcut files for the frontends.
7. Open JoiPlay Shortcut Generator and refresh the Library.

Android might ask you to allow APK installation from your browser or file
manager. If Android reports a signature conflict, back up JoiPlay, uninstall
the existing JoiPlay app, and then install the included APK.

### iiSU Extra Steps

1. Open JoiPlay Shortcut Generator and either go to settings or press the big button on the header that says "Select iiSU folder"
2. Select the iiSU folder that is named "iiSU" and has "iiSULauncher" as a folder inside.
3. After that it should have added the support into iiSU
4. Go to the Platform Tab and press Menu, then select "Add Console" and search for JoiPlay
5. After its added all your generated games should pop up.

## What JoiPlay Shortcut Generator does

- Reads the game list exposed by a compatible modified JoiPlay build.
- Launches games and displays their artwork and useful library details.
- Generates `.jp` and `.joiplay` shortcut files for compatible frontends.
- Imports JoiPlay platform and emulator support directly into a selected iiSU
  installation, then verifies the integration whenever the library refreshes.
- Rechecks `.jp` and `.joiplay` files by their JoiPlay ID on launch and refresh,
  including format-specific badges and a live generated/total count.
- Offers list/grid views, sorting, themes, custom icons, and configurable tap
  behavior.
- Saves settings immediately, requests a settings-only Android backup after
  every change, and mirrors portable settings to the selected JoiPlay folder.
  After reinstalling, select the same JoiPlay folder to restore that snapshot.
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
