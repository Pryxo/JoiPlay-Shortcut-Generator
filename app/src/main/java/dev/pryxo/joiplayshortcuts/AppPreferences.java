package dev.pryxo.joiplayshortcuts;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AppPreferences {
    public enum ThemeMode { SYSTEM, LIGHT, DARK }
    public enum AccentColor { PURPLE, BLUE, PINK, ORANGE, TEAL }
    public enum ShortcutFormat { JP, JOIPLAY }
    public enum SortOrder { TITLE, RECENTLY_ADDED, MOST_PLAYED }
    public enum TapAction { LAUNCH, DETAILS }
    public enum ViewMode { LIST, GRID }

    static final String FILE = "settings";
    private static final String THEME = "theme";
    private static final String ACCENT = "accent";
    private static final String FORMAT = "shortcut_format";
    private static final String SORT = "sort_order";
    private static final String TAP = "tap_action";
    private static final String TREE_URI = "output_tree_uri";
    private static final String SHOW_FOLDERS = "show_folder_entries";
    private static final String VIEW_MODE = "view_mode";
    private static final String GENERATED_IDS = "generated_ids";
    private static final String GENERATED_JP_IDS = "generated_jp_ids";
    private static final String GENERATED_JOIPLAY_IDS = "generated_joiplay_ids";
    private static final String CUSTOM_ICON_PREFIX = "custom_icon_";

    private final SharedPreferences preferences;
    private final BackupManager backupManager;

    public AppPreferences(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        backupManager = new BackupManager(appContext);
        migrateGeneratedIds();
    }

    public ThemeMode theme() {
        return enumValue(ThemeMode.class, preferences.getString(THEME, ThemeMode.SYSTEM.name()), ThemeMode.SYSTEM);
    }

    public void setTheme(ThemeMode mode) {
        save(preferences.edit().putString(THEME, mode.name()));
    }

    public AccentColor accentColor() {
        return enumValue(AccentColor.class, preferences.getString(ACCENT, AccentColor.PURPLE.name()), AccentColor.PURPLE);
    }

    public void setAccentColor(AccentColor color) {
        save(preferences.edit().putString(ACCENT, color.name()));
    }

    public ShortcutFormat shortcutFormat() {
        String stored = preferences.getString(FORMAT, ShortcutFormat.JP.name());
        if ("DPT".equals(stored)) stored = ShortcutFormat.JP.name();
        return enumValue(ShortcutFormat.class, stored, ShortcutFormat.JP);
    }

    public void setShortcutFormat(ShortcutFormat format) {
        save(preferences.edit().putString(FORMAT, format.name()));
    }

    public SortOrder sortOrder() {
        return enumValue(SortOrder.class, preferences.getString(SORT, SortOrder.TITLE.name()), SortOrder.TITLE);
    }

    public void setSortOrder(SortOrder order) {
        save(preferences.edit().putString(SORT, order.name()));
    }

    public TapAction tapAction() {
        return enumValue(TapAction.class, preferences.getString(TAP, TapAction.LAUNCH.name()), TapAction.LAUNCH);
    }

    public void setTapAction(TapAction action) {
        save(preferences.edit().putString(TAP, action.name()));
    }

    public Uri outputTree() {
        String value = preferences.getString(TREE_URI, "");
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value);
    }

    public void setOutputTree(Uri uri) {
        SharedPreferences.Editor editor = preferences.edit();
        if (uri == null) editor.remove(TREE_URI); else editor.putString(TREE_URI, uri.toString());
        save(editor);
    }

    public boolean showFolderEntries() {
        return preferences.getBoolean(SHOW_FOLDERS, false);
    }

    public void setShowFolderEntries(boolean show) {
        save(preferences.edit().putBoolean(SHOW_FOLDERS, show));
    }

    public ViewMode viewMode() {
        return enumValue(ViewMode.class, preferences.getString(VIEW_MODE, ViewMode.LIST.name()), ViewMode.LIST);
    }

    public void setViewMode(ViewMode mode) {
        save(preferences.edit().putString(VIEW_MODE, mode.name()));
    }

    public Uri customIcon(String gameId) {
        String value = preferences.getString(CUSTOM_ICON_PREFIX + gameId, "");
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value);
    }

    public void setCustomIcon(String gameId, Uri uri) {
        SharedPreferences.Editor editor = preferences.edit();
        String key = CUSTOM_ICON_PREFIX + gameId;
        if (uri == null) editor.remove(key); else editor.putString(key, uri.toString());
        save(editor);
    }

    public boolean isShortcutGenerated(String gameId) {
        return !generatedFormats(gameId).isEmpty();
    }

    public boolean isShortcutGenerated(String gameId, ShortcutFormat format) {
        return generatedShortcutIds(format).contains(gameId);
    }

    public EnumSet<ShortcutFormat> generatedFormats(String gameId) {
        EnumSet<ShortcutFormat> formats = EnumSet.noneOf(ShortcutFormat.class);
        if (gameId == null || gameId.isEmpty()) return formats;
        for (ShortcutFormat format : ShortcutFormat.values()) {
            if (isShortcutGenerated(gameId, format)) formats.add(format);
        }
        return formats;
    }

    public Set<String> generatedShortcutIds(ShortcutFormat format) {
        Set<String> stored = preferences.getStringSet(generatedKey(format), Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void markShortcutGenerated(String gameId, ShortcutFormat format) {
        if (gameId == null || gameId.isEmpty()) return;
        Set<String> ids = generatedShortcutIds(format);
        ids.add(gameId);
        save(preferences.edit().putStringSet(generatedKey(format), ids));
    }

    public void markShortcutsGenerated(Set<String> gameIds, ShortcutFormat format) {
        if (gameIds == null || gameIds.isEmpty()) return;
        Set<String> ids = generatedShortcutIds(format);
        ids.addAll(gameIds);
        save(preferences.edit().putStringSet(generatedKey(format), ids));
    }

    public void replaceGeneratedShortcuts(Map<ShortcutFormat, Set<String>> idsByFormat) {
        SharedPreferences.Editor editor = preferences.edit();
        for (ShortcutFormat format : ShortcutFormat.values()) {
            Set<String> source = idsByFormat == null ? null : idsByFormat.get(format);
            Set<String> ids = source == null ? new HashSet<>() : new HashSet<>(source);
            ids.remove(null);
            ids.remove("");
            editor.putStringSet(generatedKey(format), ids);
        }
        editor.remove(GENERATED_IDS);
        save(editor);
    }

    private void migrateGeneratedIds() {
        if (!preferences.contains(GENERATED_IDS)) return;
        Set<String> legacy = preferences.getStringSet(GENERATED_IDS, Collections.emptySet());
        SharedPreferences.Editor editor = preferences.edit().remove(GENERATED_IDS);
        if (legacy != null && !legacy.isEmpty()) {
            Set<String> current = generatedShortcutIds(shortcutFormat());
            current.addAll(legacy);
            editor.putStringSet(generatedKey(shortcutFormat()), current);
        }
        save(editor);
    }

    private static String generatedKey(ShortcutFormat format) {
        return format == ShortcutFormat.JOIPLAY ? GENERATED_JOIPLAY_IDS : GENERATED_JP_IDS;
    }

    private void save(SharedPreferences.Editor editor) {
        if (editor.commit()) backupManager.dataChanged();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
