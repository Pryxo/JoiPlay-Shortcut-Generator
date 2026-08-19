package dev.pryxo.joiplayshortcuts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class PortableSettingsCodec {
    private static final int MAX_LINE_LENGTH = 1024;

    private PortableSettingsCodec() {}

    static byte[] encode(Map<String, String> settings) {
        StringBuilder result = new StringBuilder("schema=1\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(settings).entrySet()) {
            if (isSafe(entry.getKey()) && isSafe(entry.getValue())) {
                result.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Map<String, String> decode(InputStream input) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE_LENGTH) throw new IOException("Settings line is too long");
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (isSafe(key) && isSafe(value)) result.put(key, value);
            }
        }
        return result;
    }

    private static boolean isSafe(String value) {
        return value != null && value.indexOf('\n') < 0 && value.indexOf('\r') < 0
                && value.indexOf('=') < 0;
    }
}
