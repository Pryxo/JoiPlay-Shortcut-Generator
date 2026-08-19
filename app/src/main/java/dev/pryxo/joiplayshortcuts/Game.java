package dev.pryxo.joiplayshortcuts;

import java.util.Locale;

public final class Game {
    public final String id;
    public final String title;
    public final String folder;
    public final String execFile;
    public final String path;
    public final String icon;
    public final String version;
    public final String type;
    public final boolean scoped;
    public final long date;
    public final int playCount;
    public final boolean folderEntry;
    public final String parentGame;
    public final String launchIntentUri;

    public Game(
            String id,
            String title,
            String folder,
            String execFile,
            String path,
            String icon,
            String version,
            String type,
            boolean scoped,
            long date,
            int playCount,
            boolean folderEntry,
            String parentGame,
            String launchIntentUri
    ) {
        this.id = clean(id);
        this.title = clean(title).isEmpty() ? "Untitled game" : clean(title);
        this.folder = clean(folder);
        this.execFile = clean(execFile);
        this.path = clean(path);
        this.icon = clean(icon);
        this.version = clean(version);
        this.type = clean(type);
        this.scoped = scoped;
        this.date = date;
        this.playCount = playCount;
        this.folderEntry = folderEntry;
        this.parentGame = clean(parentGame);
        this.launchIntentUri = clean(launchIntentUri);
    }

    public String runtimeLabel() {
        if (folderEntry) return "Folder";
        if (type.isEmpty()) return "JoiPlay game";
        return type.replace('-', ' ').toUpperCase(Locale.ROOT);
    }

    public String locationLabel() {
        if (!path.isEmpty()) return path;
        if (!folder.isEmpty()) return folder;
        return "Location unavailable";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

