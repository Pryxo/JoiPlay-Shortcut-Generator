package dev.pryxo.joiplayshortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class AppPreferences {
    public enum ThemeMode { SYSTEM, LIGHT, DARK }
    public enum ShortcutFormat { DPT, JOIPLAY }
    public enum SortOrder { TITLE, RECENTLY_ADDED, MOST_PLAYED }
    public enum TapAction { LAUNCH, DETAILS }

    private static final String FILE = "settings";
    private static final String THEME = "theme";
    private static final String FORMAT = "shortcut_format";
    private static final String SORT = "sort_order";
    private static final String TAP = "tap_action";
    private static final String TREE_URI = "output_tree_uri";
    private static final String SHOW_FOLDERS = "show_folder_entries";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public ThemeMode theme() {
        return enumValue(ThemeMode.class, preferences.getString(THEME, ThemeMode.SYSTEM.name()), ThemeMode.SYSTEM);
    }

    public void setTheme(ThemeMode mode) {
        preferences.edit().putString(THEME, mode.name()).apply();
    }

    public ShortcutFormat shortcutFormat() {
        return enumValue(ShortcutFormat.class, preferences.getString(FORMAT, ShortcutFormat.DPT.name()), ShortcutFormat.DPT);
    }

    public void setShortcutFormat(ShortcutFormat format) {
        preferences.edit().putString(FORMAT, format.name()).apply();
    }

    public SortOrder sortOrder() {
        return enumValue(SortOrder.class, preferences.getString(SORT, SortOrder.TITLE.name()), SortOrder.TITLE);
    }

    public void setSortOrder(SortOrder order) {
        preferences.edit().putString(SORT, order.name()).apply();
    }

    public TapAction tapAction() {
        return enumValue(TapAction.class, preferences.getString(TAP, TapAction.LAUNCH.name()), TapAction.LAUNCH);
    }

    public void setTapAction(TapAction action) {
        preferences.edit().putString(TAP, action.name()).apply();
    }

    public Uri outputTree() {
        String value = preferences.getString(TREE_URI, "");
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value);
    }

    public void setOutputTree(Uri uri) {
        SharedPreferences.Editor editor = preferences.edit();
        if (uri == null) editor.remove(TREE_URI); else editor.putString(TREE_URI, uri.toString());
        editor.apply();
    }

    public boolean showFolderEntries() {
        return preferences.getBoolean(SHOW_FOLDERS, false);
    }

    public void setShowFolderEntries(boolean show) {
        preferences.edit().putBoolean(SHOW_FOLDERS, show).apply();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
