package dev.pryxo.joiplayshortcuts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class IisuJsonSupport {
    static final class Update {
        final String emulatorsJson;
        final String supportedEmulatorsJson;
        final boolean changed;

        Update(String emulatorsJson, String supportedEmulatorsJson, boolean changed) {
            this.emulatorsJson = emulatorsJson;
            this.supportedEmulatorsJson = supportedEmulatorsJson;
            this.changed = changed;
        }
    }

    private IisuJsonSupport() {}

    static boolean isInstalled(String emulatorsJson, String supportedEmulatorsJson) throws JSONException {
        ArrayDocument platformsDocument = parsePlatforms(emulatorsJson);
        JSONArray platforms = platformsDocument.entries;
        JSONArray supported = parseArray(supportedEmulatorsJson, "supported_emulators.json");
        int platformIndex = findPlatform(platforms);
        int supportedIndex = findSupported(supported);
        return platformIndex >= 0 && platformMatches(platforms.getJSONObject(platformIndex))
                && supportedIndex >= 0 && supportedMatches(supported.getJSONObject(supportedIndex));
    }

    static Update upsert(String emulatorsJson, String supportedEmulatorsJson) throws JSONException {
        ArrayDocument platformsDocument = parsePlatforms(emulatorsJson);
        JSONArray platforms = platformsDocument.entries;
        JSONArray supported = parseArray(supportedEmulatorsJson, "supported_emulators.json");
        boolean platformCurrent = false;
        boolean supportedCurrent = false;

        int platformIndex = findPlatform(platforms);
        if (platformIndex >= 0) {
            platformCurrent = platformMatches(platforms.getJSONObject(platformIndex));
            if (!platformCurrent) platforms.put(platformIndex, platformEntry());
        } else {
            platforms.put(platformEntry());
        }

        int supportedIndex = findSupported(supported);
        if (supportedIndex >= 0) {
            supportedCurrent = supportedMatches(supported.getJSONObject(supportedIndex));
            if (!supportedCurrent) supported.put(supportedIndex, supportedEntry());
        } else {
            supported.put(supportedEntry());
        }

        boolean changed = platformIndex < 0 || supportedIndex < 0
                || !platformCurrent || !supportedCurrent;
        return new Update(format(platformsDocument.root), format(supported), changed);
    }

    private static ArrayDocument parsePlatforms(String json) throws JSONException {
        String clean = clean(json);
        if (clean.startsWith("[")) {
            JSONArray entries = new JSONArray(clean);
            return new ArrayDocument(entries, entries);
        }
        if (clean.startsWith("{")) {
            JSONObject root = new JSONObject(clean);
            JSONArray entries = root.optJSONArray("consoles");
            if (entries == null) {
                throw new JSONException("emuladores.json must contain a consoles array");
            }
            return new ArrayDocument(root, entries);
        }
        throw new JSONException("emuladores.json must contain a JSON object or array");
    }

    private static JSONArray parseArray(String json, String fileName) throws JSONException {
        String clean = clean(json);
        if (!clean.startsWith("[")) {
            throw new JSONException(fileName + " must contain a top-level JSON array");
        }
        return new JSONArray(clean);
    }

    private static String clean(String json) {
        String clean = json == null ? "" : json.trim();
        if (clean.startsWith("\uFEFF")) clean = clean.substring(1).trim();
        return clean;
    }

    private static int findPlatform(JSONArray entries) throws JSONException {
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) continue;
            if (equalsIgnoreCase(entry.optString("shortName"), "joiplay")) return index;
            JSONArray emulators = entry.optJSONArray("emulators");
            if (emulators == null) continue;
            for (int emulatorIndex = 0; emulatorIndex < emulators.length(); emulatorIndex++) {
                JSONObject emulator = emulators.optJSONObject(emulatorIndex);
                if (emulator != null && (equalsIgnoreCase(emulator.optString("id"), "JOIPLAY")
                        || containsIgnoreCase(emulator.optJSONArray("packages"), "cyou.joiplay.joiplay"))) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int findSupported(JSONArray entries) {
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.optJSONObject(index);
            if (entry != null && (equalsIgnoreCase(entry.optString("name"), "JoiPlay")
                    || containsIgnoreCase(entry.optJSONArray("packages"), "cyou.joiplay.joiplay"))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean platformMatches(JSONObject entry) {
        if (!"joiplay".equals(entry.optString("shortName"))
                || !"JoiPlay".equals(entry.optString("longName"))
                || !"2019".equals(entry.optString("releaseYear"))
                || !"2019-12-16".equals(entry.optString("releaseDate"))
                || !"JoiPlay".equals(entry.optString("manufacturer"))
                || !"NA".equals(entry.optString("retroAchievementsId"))) return false;
        Set<String> extensions = strings(entry.optJSONArray("romExtensions"));
        if (!extensions.contains(".jp") || !extensions.contains(".JP")
                || !extensions.contains(".joiplay") || !extensions.contains(".JOIPLAY")) return false;
        JSONArray emulators = entry.optJSONArray("emulators");
        if (emulators == null || emulators.length() != 1) return false;
        JSONObject emulator = emulators.optJSONObject(0);
        if (emulator == null || !"JOIPLAY".equals(emulator.optString("id"))
                || !"JoiPlay (Standalone)".equals(emulator.optString("name"))
                || !"fileContent".equals(emulator.optString("routeType"))
                || !contains(emulator.optJSONArray("packages"), "cyou.joiplay.joiplay")) return false;
        JSONArray commands = emulator.optJSONArray("commands");
        if (commands == null || commands.length() != 1) return false;
        JSONObject command = commands.optJSONObject(0);
        return command != null
                && "JoiPlay".equals(command.optString("description"))
                && "cyou.joiplay.joiplay/.activities.ShortcutActivity -e id \"%ROM_CONTENT%\""
                .equals(command.optString("command"));
    }

    private static boolean supportedMatches(JSONObject entry) {
        return "JoiPlay".equals(entry.optString("name"))
                && contains(entry.optJSONArray("packages"), "cyou.joiplay.joiplay");
    }

    private static JSONObject platformEntry() throws JSONException {
        JSONObject command = new JSONObject()
                .put("description", "JoiPlay")
                .put("command", "cyou.joiplay.joiplay/.activities.ShortcutActivity -e id \"%ROM_CONTENT%\"");
        JSONObject emulator = new JSONObject()
                .put("id", "JOIPLAY")
                .put("name", "JoiPlay (Standalone)")
                .put("routeType", "fileContent")
                .put("commands", new JSONArray().put(command))
                .put("packages", new JSONArray().put("cyou.joiplay.joiplay"));
        return new JSONObject()
                .put("shortName", "joiplay")
                .put("longName", "JoiPlay")
                .put("releaseYear", "2019")
                .put("releaseDate", "2019-12-16")
                .put("manufacturer", "JoiPlay")
                .put("retroAchievementsId", "NA")
                .put("romExtensions", new JSONArray()
                        .put(".jp").put(".JP").put(".joiplay").put(".JOIPLAY"))
                .put("emulators", new JSONArray().put(emulator));
    }

    private static JSONObject supportedEntry() throws JSONException {
        return new JSONObject()
                .put("name", "JoiPlay")
                .put("packages", new JSONArray().put("cyou.joiplay.joiplay"));
    }

    private static Set<String> strings(JSONArray values) {
        Set<String> result = new HashSet<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            Object value = values.opt(index);
            if (value instanceof String) result.add((String) value);
        }
        return result;
    }

    private static boolean contains(JSONArray values, String wanted) {
        if (values == null) return false;
        for (int index = 0; index < values.length(); index++) {
            if (wanted.equals(values.optString(index))) return true;
        }
        return false;
    }

    private static boolean containsIgnoreCase(JSONArray values, String wanted) {
        if (values == null) return false;
        for (int index = 0; index < values.length(); index++) {
            if (equalsIgnoreCase(values.optString(index), wanted)) return true;
        }
        return false;
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null
                && first.toLowerCase(Locale.ROOT).equals(second.toLowerCase(Locale.ROOT));
    }

    private static String format(Object value) throws JSONException {
        if (value instanceof JSONObject) return ((JSONObject) value).toString(2) + "\n";
        if (value instanceof JSONArray) return ((JSONArray) value).toString(2) + "\n";
        throw new JSONException("Unsupported JSON root");
    }

    private static final class ArrayDocument {
        final Object root;
        final JSONArray entries;

        ArrayDocument(Object root, JSONArray entries) {
            this.root = root;
            this.entries = entries;
        }
    }
}
