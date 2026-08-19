package dev.pryxo.joiplayshortcuts;

import java.text.Normalizer;

public final class ShortcutFileFactory {
    private ShortcutFileFactory() {}

    public static String extension(AppPreferences.ShortcutFormat format) {
        return format == AppPreferences.ShortcutFormat.JOIPLAY ? "joiplay" : "jp";
    }

    public static String mimeType() {
        return "text/plain";
    }

    public static String fileName(Game game, AppPreferences.ShortcutFormat format) {
        return sanitizeFileName(game.title) + "." + extension(format);
    }

    public static String contents(Game game) {
        return "# Daijishou Player Template\n"
                + "[joiplay_id] " + game.id + "\n"
                + "...\n";
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
