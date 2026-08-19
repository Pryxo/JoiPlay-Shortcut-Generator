package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class IisuSupportManager {
    enum Status { NOT_CONFIGURED, CHECKING, NOT_IMPORTED, IMPORTED, ERROR }

    static final class Result {
        final Status status;
        final String message;

        Result(Status status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private final ContentResolver resolver;

    IisuSupportManager(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    Result scan(Uri iisuTree) {
        if (iisuTree == null) return new Result(Status.NOT_CONFIGURED, "Select the iiSU folder");
        try {
            Files files = locateFiles(iisuTree);
            boolean installed = IisuJsonSupport.isInstalled(
                    read(files.emulators), read(files.supportedEmulators));
            return installed
                    ? new Result(Status.IMPORTED, "JoiPlay support is present in both iiSU files")
                    : new Result(Status.NOT_IMPORTED, "JoiPlay support is not imported yet");
        } catch (IOException | JSONException | RuntimeException error) {
            return new Result(Status.ERROR, readableMessage(error));
        }
    }

    Result inject(Uri iisuTree) {
        if (iisuTree == null) return new Result(Status.NOT_CONFIGURED, "Select the iiSU folder first");
        try {
            Files files = locateFiles(iisuTree);
            String originalEmulators = read(files.emulators);
            String originalSupported = read(files.supportedEmulators);
            IisuJsonSupport.Update update = IisuJsonSupport.upsert(originalEmulators, originalSupported);
            if (!update.changed) {
                return new Result(Status.IMPORTED, "JoiPlay support is already imported");
            }

            write(files.emulators, update.emulatorsJson);
            try {
                write(files.supportedEmulators, update.supportedEmulatorsJson);
            } catch (IOException | RuntimeException secondWriteError) {
                try {
                    write(files.emulators, originalEmulators);
                } catch (IOException | RuntimeException ignored) {
                    // Best-effort rollback; the next scan will expose any partial update.
                }
                throw secondWriteError;
            }
            return new Result(Status.IMPORTED, "JoiPlay support imported into iiSU");
        } catch (IOException | JSONException | RuntimeException error) {
            return new Result(Status.ERROR, readableMessage(error));
        }
    }

    private Files locateFiles(Uri tree) throws IOException {
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException error) {
            throw new IOException("The selected iiSU folder is no longer accessible", error);
        }
        String launcherId = findChildId(tree, rootId, "iiSULauncher", true);
        String emulatorsDirectoryId = findChildId(tree, launcherId, "Emuladores", true);
        String emulatorsId = findChildId(tree, emulatorsDirectoryId, "emuladores.json", false);
        String supportedId = findChildId(tree, emulatorsDirectoryId, "supported_emulators.json", false);
        return new Files(
                DocumentsContract.buildDocumentUriUsingTree(tree, emulatorsId),
                DocumentsContract.buildDocumentUriUsingTree(tree, supportedId));
    }

    private String findChildId(Uri tree, String parentId, String wantedName, boolean directory)
            throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) throw new IOException("Could not read " + wantedName);
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                if (idColumn < 0 || nameColumn < 0 || cursor.isNull(idColumn) || cursor.isNull(nameColumn)) {
                    continue;
                }
                if (!wantedName.equalsIgnoreCase(cursor.getString(nameColumn))) continue;
                boolean isDirectory = mimeColumn >= 0 && !cursor.isNull(mimeColumn)
                        && DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeColumn));
                if (directory != isDirectory) continue;
                return cursor.getString(idColumn);
            }
        } catch (SecurityException error) {
            throw new IOException("Permission to the iiSU folder was revoked", error);
        } catch (RuntimeException error) {
            throw new IOException("Could not read " + wantedName, error);
        }
        throw new IOException("Could not find " + wantedName + " inside the selected iiSU folder");
    }

    private String read(Uri document) throws IOException {
        try (InputStream input = resolver.openInputStream(document)) {
            if (input == null) throw new IOException("Could not open an iiSU JSON file");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_JSON_BYTES) throw new IOException("An iiSU JSON file is too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (SecurityException error) {
            throw new IOException("Permission to the iiSU folder was revoked", error);
        }
    }

    private void write(Uri document, String contents) throws IOException {
        try (OutputStream output = resolver.openOutputStream(document, "wt")) {
            if (output == null) throw new IOException("Could not write an iiSU JSON file");
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        } catch (SecurityException error) {
            throw new IOException("The selected iiSU folder is not writable", error);
        }
    }

    private static String readableMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Could not update the selected iiSU folder" : message;
    }

    private static final class Files {
        final Uri emulators;
        final Uri supportedEmulators;

        Files(Uri emulators, Uri supportedEmulators) {
            this.emulators = emulators;
            this.supportedEmulators = supportedEmulators;
        }
    }
}
