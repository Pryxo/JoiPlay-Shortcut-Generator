package dev.pryxo.joiplayshortcuts;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/** Keeps app settings available to Android's restore service after reinstall. */
public final class SettingsBackupAgent extends BackupAgentHelper {
    private static final String BACKUP_KEY = "settings_v1";

    @Override
    public void onCreate() {
        addHelper(BACKUP_KEY, new SharedPreferencesBackupHelper(this, AppPreferences.FILE));
    }
}
