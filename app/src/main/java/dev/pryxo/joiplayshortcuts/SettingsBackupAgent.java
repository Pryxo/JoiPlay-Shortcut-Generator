package dev.pryxo.joiplayshortcuts;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/** Keeps portable app settings available to Android's restore service after reinstall. */
public final class SettingsBackupAgent extends BackupAgentHelper {
    private static final String BACKUP_KEY = "portable_settings_v2";

    @Override
    public void onCreate() {
        addHelper(BACKUP_KEY, new SharedPreferencesBackupHelper(this, AppPreferences.PORTABLE_FILE));
    }
}
