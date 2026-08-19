package dev.pryxo.joiplayshortcuts;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IisuJsonSupportTest {
    private static final String OTHER_PLATFORM = "[{\"shortName\":\"nes\",\"emulators\":[]}]";
    private static final String OTHER_SUPPORTED = "[{\"name\":\"RetroArch\",\"packages\":[\"com.retroarch\"]}]";

    @Test
    public void addsJoiPlayToBothArrays() throws Exception {
        IisuJsonSupport.Update update = IisuJsonSupport.upsert(OTHER_PLATFORM, OTHER_SUPPORTED);

        assertTrue(update.changed);
        assertTrue(IisuJsonSupport.isInstalled(update.emulatorsJson, update.supportedEmulatorsJson));
        assertEquals(2, new JSONArray(update.emulatorsJson).length());
        assertEquals(2, new JSONArray(update.supportedEmulatorsJson).length());
    }

    @Test
    public void repeatedImportIsIdempotent() throws Exception {
        IisuJsonSupport.Update first = IisuJsonSupport.upsert(OTHER_PLATFORM, OTHER_SUPPORTED);
        IisuJsonSupport.Update second = IisuJsonSupport.upsert(
                first.emulatorsJson, first.supportedEmulatorsJson);

        assertFalse(second.changed);
        assertEquals(2, new JSONArray(second.emulatorsJson).length());
        assertEquals(2, new JSONArray(second.supportedEmulatorsJson).length());
    }

    @Test
    public void repairsExistingIncompleteEntriesWithoutDuplicatingThem() throws Exception {
        String incompletePlatform = "[{\"shortName\":\"joiplay\",\"longName\":\"Old\"}]";
        String incompleteSupported = "[{\"name\":\"JoiPlay\",\"packages\":[]}]";

        IisuJsonSupport.Update update = IisuJsonSupport.upsert(
                incompletePlatform, incompleteSupported);

        JSONArray platforms = new JSONArray(update.emulatorsJson);
        JSONArray supported = new JSONArray(update.supportedEmulatorsJson);
        assertEquals(1, platforms.length());
        assertEquals(1, supported.length());
        assertEquals("JoiPlay", platforms.getJSONObject(0).getString("longName"));
        assertEquals("cyou.joiplay.joiplay",
                supported.getJSONObject(0).getJSONArray("packages").getString(0));
        assertTrue(IisuJsonSupport.isInstalled(update.emulatorsJson, update.supportedEmulatorsJson));
    }

    @Test
    public void writesRequestedCommandAndExtensions() throws Exception {
        IisuJsonSupport.Update update = IisuJsonSupport.upsert("[]", "[]");
        JSONObject platform = new JSONArray(update.emulatorsJson).getJSONObject(0);
        JSONObject command = platform.getJSONArray("emulators")
                .getJSONObject(0).getJSONArray("commands").getJSONObject(0);

        assertEquals("cyou.joiplay.joiplay/.activities.ShortcutActivity -e id \"%ROM_CONTENT%\"",
                command.getString("command"));
        assertEquals(".jp", platform.getJSONArray("romExtensions").getString(0));
        assertEquals(".JOIPLAY", platform.getJSONArray("romExtensions").getString(3));
    }

    @Test
    public void updatesIisuConsolesArrayAndPreservesWrapperFields() throws Exception {
        String wrappedPlatforms = "{\"schemaVersion\":7,\"consoles\":" + OTHER_PLATFORM + "}";

        IisuJsonSupport.Update update = IisuJsonSupport.upsert(wrappedPlatforms, OTHER_SUPPORTED);

        JSONObject root = new JSONObject(update.emulatorsJson);
        assertEquals(7, root.getInt("schemaVersion"));
        assertEquals(2, root.getJSONArray("consoles").length());
        assertTrue(IisuJsonSupport.isInstalled(update.emulatorsJson, update.supportedEmulatorsJson));
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsObjectWithoutConsolesArrayBeforeWriting() throws Exception {
        IisuJsonSupport.upsert("{}", OTHER_SUPPORTED);
    }
}
