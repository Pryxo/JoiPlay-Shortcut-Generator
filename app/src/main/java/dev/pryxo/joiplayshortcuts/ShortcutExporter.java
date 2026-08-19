package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShortcutExporter {
    public static final class ExportSummary {
        public final int written;
        public final int failed;
        public final Set<String> writtenGameIds;

        ExportSummary(int written, int failed, Set<String> writtenGameIds) {
            this.written = written;
            this.failed = failed;
            this.writtenGameIds = Collections.unmodifiableSet(new HashSet<>(writtenGameIds));
        }
    }

    private final ContentResolver resolver;

    public ShortcutExporter(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    public void writeDocument(Uri document, Game game) throws IOException {
        try (OutputStream output = resolver.openOutputStream(document, "wt")) {
            if (output == null) throw new IOException("Could not open the selected document");
            output.write(ShortcutFileFactory.contents(game).getBytes(StandardCharsets.UTF_8));
        }
    }

    public Uri writeToTree(Uri tree, Game game, AppPreferences.ShortcutFormat format) throws IOException {
        Uri directory = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
        );
        Uri document = DocumentsContract.createDocument(
                resolver,
                directory,
                ShortcutFileFactory.mimeType(),
                ShortcutFileFactory.fileName(game, format)
        );
        if (document == null) throw new IOException("The folder did not create a document");
        writeDocument(document, game);
        return document;
    }

    public ExportSummary writeAll(Uri tree, List<Game> games, AppPreferences.ShortcutFormat format) {
        int written = 0;
        int failed = 0;
        Set<String> writtenIds = new HashSet<>();
        for (Game game : games) {
            if (game.folderEntry || game.id.isEmpty()) continue;
            try {
                writeToTree(tree, game, format);
                written++;
                writtenIds.add(game.id);
            } catch (IOException | RuntimeException error) {
                failed++;
            }
        }
        return new ExportSummary(written, failed, writtenIds);
    }

    public Set<String> findExisting(Uri tree, List<Game> games, AppPreferences.ShortcutFormat format) {
        if (tree == null || games == null || games.isEmpty()) return Collections.emptySet();
        Map<String, String> idsByFileName = new HashMap<>();
        for (Game game : games) {
            if (!game.folderEntry && !game.id.isEmpty()) {
                idsByFileName.put(ShortcutFileFactory.fileName(game, format), game.id);
            }
        }
        Set<String> found = new HashSet<>();
        try {
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
            try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
                if (cursor == null) return found;
                int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    if (nameColumn < 0 || cursor.isNull(nameColumn)) continue;
                    String gameId = idsByFileName.get(cursor.getString(nameColumn));
                    if (gameId != null) found.add(gameId);
                }
            }
        } catch (RuntimeException ignored) {
            return found;
        }
        return found;
    }
}
