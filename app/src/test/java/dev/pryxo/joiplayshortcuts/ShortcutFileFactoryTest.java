package dev.pryxo.joiplayshortcuts;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ShortcutFileFactoryTest {
    private static Game game(String id, String title) {
        return new Game(id, title, "", "", "", "", "", "rpgm", false, 0, 0, false, "", "");
    }

    @Test
    public void createsDocumentedDaijishouTemplate() {
        String output = ShortcutFileFactory.contents(game("3b6d-123", "A Game"));
        assertEquals("# Daijishou Player Template\n[joiplay_id] 3b6d-123\n...\n", output);
    }

    @Test
    public void createsFormatSpecificNames() {
        Game game = game("id", "Moon / Star: Redux?");
        assertEquals("Moon  Star Redux.dpt", ShortcutFileFactory.fileName(game, AppPreferences.ShortcutFormat.DPT));
        assertEquals("Moon  Star Redux.joiplay", ShortcutFileFactory.fileName(game, AppPreferences.ShortcutFormat.JOIPLAY));
    }

    @Test
    public void sanitizesWindowsAndAndroidUnsafeCharacters() {
        String output = ShortcutFileFactory.sanitizeFileName("  <Bad>|Name.*  ");
        assertFalse(output.contains("<"));
        assertFalse(output.contains("|"));
        assertFalse(output.endsWith("."));
        assertTrue(output.startsWith("Bad"));
    }

    @Test
    public void suppliesFallbackAndLengthLimit() {
        assertEquals("JoiPlay Game", ShortcutFileFactory.sanitizeFileName("<>:*?"));
        assertTrue(ShortcutFileFactory.sanitizeFileName("x".repeat(200)).length() <= 120);
    }
}

