package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ShortcutExporter {
    public static final class ExportSummary {
        public final int written;
        public final int failed;

        ExportSummary(int written, int failed) {
            this.written = written;
            this.failed = failed;
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
        for (Game game : games) {
            if (game.folderEntry || game.id.isEmpty()) continue;
            try {
                writeToTree(tree, game, format);
                written++;
            } catch (IOException | RuntimeException error) {
                failed++;
            }
        }
        return new ExportSummary(written, failed);
    }
}

