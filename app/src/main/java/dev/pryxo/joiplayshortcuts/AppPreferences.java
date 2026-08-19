package dev.pryxo.joiplayshortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppPreferences {
    public enum ThemeMode { SYSTEM, LIGHT, DARK }
    public enum AccentColor { PURPLE, BLUE, PINK, ORANGE, TEAL }
    public enum ShortcutFormat { JP, JOIPLAY }
    public enum SortOrder { TITLE, RECENTLY_ADDED, MOST_PLAYED }
    public enum TapAction { LAUNCH, DETAILS }
    public enum ViewMode { LIST, GRID }

    private static final String FILE = "settings";
    private static final String THEME = "theme";
    private static final String ACCENT = "accent";
    private static final String FORMAT = "shortcut_format";
    private static final String SORT = "sort_order";
    private static final String TAP = "tap_action";
    private static final String TREE_URI = "output_tree_uri";
    private static final String SHOW_FOLDERS = "show_folder_entries";
    private static final String VIEW_MODE = "view_mode";
    private static final String GENERATED_IDS = "generated_ids";
    private static final String CUSTOM_ICON_PREFIX = "custom_icon_";

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

    public AccentColor accentColor() {
        return enumValue(AccentColor.class, preferences.getString(ACCENT, AccentColor.PURPLE.name()), AccentColor.PURPLE);
    }

    public void setAccentColor(AccentColor color) {
        preferences.edit().putString(ACCENT, color.name()).apply();
    }

    public ShortcutFormat shortcutFormat() {
        String stored = preferences.getString(FORMAT, ShortcutFormat.JP.name());
        if ("DPT".equals(stored)) stored = ShortcutFormat.JP.name();
        return enumValue(ShortcutFormat.class, stored, ShortcutFormat.JP);
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

    public ViewMode viewMode() {
        return enumValue(ViewMode.class, preferences.getString(VIEW_MODE, ViewMode.LIST.name()), ViewMode.LIST);
    }

    public void setViewMode(ViewMode mode) {
        preferences.edit().putString(VIEW_MODE, mode.name()).apply();
    }

    public Uri customIcon(String gameId) {
        String value = preferences.getString(CUSTOM_ICON_PREFIX + gameId, "");
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value);
    }

    public void setCustomIcon(String gameId, Uri uri) {
        SharedPreferences.Editor editor = preferences.edit();
        String key = CUSTOM_ICON_PREFIX + gameId;
        if (uri == null) editor.remove(key); else editor.putString(key, uri.toString());
        editor.apply();
    }

    public boolean isShortcutGenerated(String gameId) {
        return generatedShortcutIds().contains(gameId);
    }

    public Set<String> generatedShortcutIds() {
        Set<String> stored = preferences.getStringSet(GENERATED_IDS, Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void markShortcutGenerated(String gameId) {
        if (gameId == null || gameId.isEmpty()) return;
        Set<String> ids = generatedShortcutIds();
        ids.add(gameId);
        preferences.edit().putStringSet(GENERATED_IDS, ids).apply();
    }

    public void markShortcutsGenerated(Set<String> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) return;
        Set<String> ids = generatedShortcutIds();
        ids.addAll(gameIds);
        preferences.edit().putStringSet(GENERATED_IDS, ids).apply();
    }

    public void replaceGeneratedShortcuts(Set<String> gameIds) {
        Set<String> ids = gameIds == null ? new HashSet<>() : new HashSet<>(gameIds);
        ids.remove(null);
        ids.remove("");
        preferences.edit().putStringSet(GENERATED_IDS, ids).apply();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
