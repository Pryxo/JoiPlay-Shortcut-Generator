package dev.pryxo.joiplayshortcuts;

import java.text.Normalizer;

public final class ShortcutFileFactory {
    private ShortcutFileFactory() {}

    public static String extension(AppPreferences.ShortcutFormat format) {
        return format == AppPreferences.ShortcutFormat.JOIPLAY ? "joiplay" : "jp";
    }

    public static String mimeType() {
        // text/plain makes some document providers silently append ".txt" to
        // our .jp and .joiplay display names.
        return "application/octet-stream";
    }

    public static String fileName(Game game, AppPreferences.ShortcutFormat format) {
        return sanitizeFileName(game.title) + "." + extension(format);
    }

    public static String contents(Game game, AppPreferences.ShortcutFormat format) {
        // fileContent routes substitute the complete file into %ROM_CONTENT%,
        // so both supported extensions must contain only the JoiPlay ID.
        return game.id;
    }

    public static AppPreferences.ShortcutFormat formatFromFileName(String fileName) {
        if (fileName == null) return null;
        String normalized = fileName.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith(".txt")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith(".joiplay")) return AppPreferences.ShortcutFormat.JOIPLAY;
        if (normalized.endsWith(".jp")) return AppPreferences.ShortcutFormat.JP;
        return null;
    }

    public static String normalizedId(String contents) {
        if (contents == null) return "";
        String normalized = contents.startsWith("\uFEFF") ? contents.substring(1) : contents;
        return normalized.trim();
    }

    public static String sanitizeFileName(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC);
        String cleaned = normalized.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "")
                .replaceAll("[. ]+$", "")
                .trim();
        if (cleaned.isEmpty()) cleaned = "JoiPlay Game";
        return cleaned.length() > 120 ? cleaned.substring(0, 120).trim() : cleaned;
    }
}
