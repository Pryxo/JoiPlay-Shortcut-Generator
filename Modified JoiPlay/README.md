# Modified JoiPlay builds

The companion app requires a JoiPlay build patched with the project's
read-only library provider and exported shortcut activity.

- Provider: `content://cyou.joiplay.joiplay.library/games`
- Permission: `cyou.joiplay.joiplay.permission.READ_LIBRARY`
- Launcher: `cyou.joiplay.joiplay/.activities.ShortcutActivity`
- Launcher extra: string `id`

These APKs are third-party derivative binaries, not part of the MIT-licensed
companion source. Verify JoiPlay's license and obtain any required permission
before redistributing them publicly. Do not publish private signing keys.

