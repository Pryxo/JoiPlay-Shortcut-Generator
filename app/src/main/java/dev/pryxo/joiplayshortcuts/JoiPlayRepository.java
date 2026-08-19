package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class JoiPlayRepository {
    public static final String JOIPLAY_PACKAGE = "cyou.joiplay.joiplay";
    public static final String SHORTCUT_ACTIVITY = "cyou.joiplay.joiplay.activities.ShortcutActivity";
    public static final Uri GAMES_URI = Uri.parse("content://cyou.joiplay.joiplay.library/games");

    public enum Status {
        READY,
        JOIPLAY_NOT_INSTALLED,
        PATCH_REQUIRED,
        ACCESS_DENIED,
        READ_ERROR
    }

    public static final class Result {
        public final Status status;
        public final List<Game> games;
        public final String technicalMessage;

        private Result(Status status, List<Game> games, String technicalMessage) {
            this.status = status;
            this.games = Collections.unmodifiableList(games);
            this.technicalMessage = technicalMessage == null ? "" : technicalMessage;
        }

        public boolean isReady() {
            return status == Status.READY;
        }
    }

    private final Context context;

    public JoiPlayRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public Result load(boolean includeFolders, AppPreferences.SortOrder sortOrder) {
        if (!isJoiPlayInstalled()) {
            return new Result(Status.JOIPLAY_NOT_INSTALLED, Collections.emptyList(), "JoiPlay package not found");
        }

        ContentResolver resolver = context.getContentResolver();
        ArrayList<Game> games = new ArrayList<>();
        try (Cursor cursor = resolver.query(GAMES_URI, null, null, null, null)) {
            if (cursor == null) {
                return new Result(Status.PATCH_REQUIRED, Collections.emptyList(), "Library provider returned no cursor");
            }
            while (cursor.moveToNext()) {
                Game game = readGame(cursor);
                android.util.Log.d("JoiPlayArtProbe", game.title + " | folder=" + game.folder + " | icon=" + game.icon);
                if (!includeFolders && game.folderEntry) continue;
                games.add(game);
            }
        } catch (SecurityException error) {
            return new Result(Status.ACCESS_DENIED, Collections.emptyList(), error.getMessage());
        } catch (IllegalArgumentException error) {
            return new Result(Status.PATCH_REQUIRED, Collections.emptyList(), error.getMessage());
        } catch (RuntimeException error) {
            return new Result(Status.READ_ERROR, Collections.emptyList(), error.toString());
        }

        sort(games, sortOrder);
        return new Result(Status.READY, games, "");
    }

    private boolean isJoiPlayInstalled() {
        try {
            context.getPackageManager().getPackageInfo(JOIPLAY_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static Game readGame(Cursor cursor) {
        return new Game(
                string(cursor, "id"),
                string(cursor, "title"),
                string(cursor, "folder"),
                string(cursor, "execFile"),
                string(cursor, "path"),
                string(cursor, "icon"),
                string(cursor, "version"),
                string(cursor, "type"),
                integer(cursor, "scoped") != 0,
                longValue(cursor, "date"),
                integer(cursor, "playCount"),
                integer(cursor, "isFolder") != 0,
                string(cursor, "parentGame"),
                string(cursor, "launchIntentUri")
        );
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private static void sort(List<Game> games, AppPreferences.SortOrder order) {
        Comparator<Game> comparator;
        if (order == AppPreferences.SortOrder.MOST_PLAYED) {
            comparator = Comparator.comparingInt((Game game) -> game.playCount)
                    .reversed()
                    .thenComparing(game -> game.title, String.CASE_INSENSITIVE_ORDER);
        } else if (order == AppPreferences.SortOrder.RECENTLY_ADDED) {
            comparator = Comparator.comparingLong((Game game) -> game.date)
                    .reversed()
                    .thenComparing(game -> game.title, String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparing(game -> game.title, String.CASE_INSENSITIVE_ORDER);
        }
        games.sort(comparator);
    }
}
