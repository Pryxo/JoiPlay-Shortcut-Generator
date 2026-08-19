package dev.pryxo.joiplayshortcuts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PortableSettingsCodecTest {
    @Test
    public void settingsRoundTrip() throws Exception {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("theme", "DARK");
        settings.put("accent", "TEAL");
        settings.put("show_folder_entries", "true");

        Map<String, String> decoded = PortableSettingsCodec.decode(
                new ByteArrayInputStream(PortableSettingsCodec.encode(settings)));

        assertEquals("1", decoded.get("schema"));
        assertEquals("DARK", decoded.get("theme"));
        assertEquals("TEAL", decoded.get("accent"));
        assertEquals("true", decoded.get("show_folder_entries"));
    }

    @Test
    public void malformedAndUnsafeEntriesAreIgnored() throws Exception {
        String contents = "schema=1\nmissing-separator\ntheme=DARK\n";
        Map<String, String> decoded = PortableSettingsCodec.decode(
                new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));

        assertEquals("DARK", decoded.get("theme"));
        assertFalse(decoded.containsKey("missing-separator"));
    }
}
