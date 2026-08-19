# Daijishō setup

## Import the included platform

1. Copy `platforms/JoiPlay.json` to the Android device.
2. In Daijishō, open **Settings → Library → Import Platform**.
3. Select `JoiPlay.json`.
4. Open the new JoiPlay platform, add the folder containing generated
   `.joiplay` or `.dpt` files, and synchronize.
5. Select **joiplay - JoiPlay** as the player if it is not selected already.

## Manual player

Create a Daijishō player with:

- Name: `joiplay - JoiPlay`
- Accepted filename regex: `^(.*)\.(?:joiplay|dpt)$`
- Kill package processes: disabled
- AM start arguments:

```text
-n cyou.joiplay.joiplay/cyou.joiplay.joiplay.activities.ShortcutActivity
 --es id {tags.joiplay_id}
 --activity-clear-top
```

The modified JoiPlay build is required because the original `ShortcutActivity`
is not externally launchable and the library IDs are private.

