package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

final class SettingsFileStore {
    private static final String FILE_NAME = ".joiplay-shortcut-generator-settings";
    private final ContentResolver resolver;

    SettingsFileStore(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    Map<String, String> read(Uri tree) throws IOException {
        Uri document = findChild(tree);
        if (document == null) return Collections.emptyMap();
        try (InputStream input = resolver.openInputStream(document)) {
            if (input == null) throw new IOException("Could not open settings backup");
            return PortableSettingsCodec.decode(input);
        }
    }

    void write(Uri tree, Map<String, String> settings) throws IOException {
        Uri document = findChild(tree);
        if (document == null) {
            Uri directory = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            document = DocumentsContract.createDocument(
                    resolver, directory, "application/octet-stream", FILE_NAME);
        }
        if (document == null) throw new IOException("Could not create settings backup");
        try (OutputStream output = resolver.openOutputStream(document, "wt")) {
            if (output == null) throw new IOException("Could not write settings backup");
            output.write(PortableSettingsCodec.encode(settings));
        }
    }

    private Uri findChild(Uri tree) {
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
                if (FILE_NAME.equalsIgnoreCase(cursor.getString(nameColumn))) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                            tree, cursor.getString(idColumn));
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}
