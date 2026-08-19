package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShortcutExporter {
    public static final class ScanResult {
        public final boolean successful;
        public final Map<AppPreferences.ShortcutFormat, Set<String>> idsByFormat;

        private ScanResult(boolean successful,
                           Map<AppPreferences.ShortcutFormat, Set<String>> idsByFormat) {
            this.successful = successful;
            EnumMap<AppPreferences.ShortcutFormat, Set<String>> copy =
                    new EnumMap<>(AppPreferences.ShortcutFormat.class);
            for (AppPreferences.ShortcutFormat format : AppPreferences.ShortcutFormat.values()) {
                Set<String> ids = idsByFormat.get(format);
                copy.put(format, Collections.unmodifiableSet(
                        ids == null ? new HashSet<>() : new HashSet<>(ids)));
            }
            this.idsByFormat = Collections.unmodifiableMap(copy);
        }
    }

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

    public void writeDocument(Uri document, Game game, AppPreferences.ShortcutFormat format) throws IOException {
        try (OutputStream output = resolver.openOutputStream(document, "wt")) {
            if (output == null) throw new IOException("Could not open the selected document");
            output.write(ShortcutFileFactory.contents(game, format).getBytes(StandardCharsets.UTF_8));
        }
    }

    public Uri writeToTree(Uri tree, Game game, AppPreferences.ShortcutFormat format) throws IOException {
        Uri directory = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
        );
        String fileName = ShortcutFileFactory.fileName(game, format);
        Uri document = findChild(tree, fileName);
        if (document == null) {
            document = DocumentsContract.createDocument(
                    resolver,
                    directory,
                    ShortcutFileFactory.mimeType(),
                    fileName
            );
        }
        if (document == null) throw new IOException("The folder did not create a document");
        writeDocument(document, game, format);
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

    public ScanResult findExisting(Uri tree, List<Game> games) {
        EnumMap<AppPreferences.ShortcutFormat, Set<String>> found = emptyFormatMap();
        if (tree == null || games == null) return new ScanResult(false, found);
        Set<String> knownIds = new HashSet<>();
        for (Game game : games) {
            if (!game.folderEntry && !game.id.isEmpty()) {
                knownIds.add(game.id);
            }
        }
        try {
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            };
            try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
                if (cursor == null) return new ScanResult(false, found);
                int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    if (idColumn < 0 || nameColumn < 0
                            || cursor.isNull(idColumn) || cursor.isNull(nameColumn)) continue;
                    String displayName = cursor.getString(nameColumn);
                    AppPreferences.ShortcutFormat format =
                            ShortcutFileFactory.formatFromFileName(displayName);
                    if (format == null) continue;
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(
                            tree, cursor.getString(idColumn));
                    String gameId = readShortcutId(document);
                    if (knownIds.contains(gameId)) found.get(format).add(gameId);
                }
            }
        } catch (RuntimeException ignored) {
            return new ScanResult(false, found);
        }
        return new ScanResult(true, found);
    }

    private String readShortcutId(Uri document) {
        try (InputStream input = resolver.openInputStream(document)) {
            if (input == null) return "";
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            char[] buffer = new char[4096];
            int length = 0;
            int read;
            while (length < buffer.length
                    && (read = reader.read(buffer, length, buffer.length - length)) > 0) {
                length += read;
            }
            if (length == buffer.length && reader.read() != -1) return "";
            return ShortcutFileFactory.normalizedId(new String(buffer, 0, length));
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    private static EnumMap<AppPreferences.ShortcutFormat, Set<String>> emptyFormatMap() {
        EnumMap<AppPreferences.ShortcutFormat, Set<String>> result =
                new EnumMap<>(AppPreferences.ShortcutFormat.class);
        for (AppPreferences.ShortcutFormat format : AppPreferences.ShortcutFormat.values()) {
            result.put(format, new HashSet<>());
        }
        return result;
    }

    private Uri findChild(Uri tree, String wantedName) {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) return null;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (idColumn < 0 || nameColumn < 0) return null;
            while (cursor.moveToNext()) {
                if (cursor.isNull(idColumn) || cursor.isNull(nameColumn)) continue;
                if (wantedName.equalsIgnoreCase(cursor.getString(nameColumn))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn));
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}
