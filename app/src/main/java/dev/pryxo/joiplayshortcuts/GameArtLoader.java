package dev.pryxo.joiplayshortcuts;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class GameArtLoader {
    private final Context context;
    private final ContentResolver resolver;
    private final AppPreferences preferences;

    public GameArtLoader(Context context, AppPreferences preferences) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.preferences = preferences;
    }

    public Bitmap load(Game game, int targetPixels) {
        Uri custom = preferences.customIcon(game.id);
        if (custom != null) {
            Bitmap bitmap = decode(custom, targetPixels);
            if (bitmap != null) return bitmap;
        }

        Bitmap bitmap = decodeGameIcon(game, targetPixels);
        if (bitmap != null) return bitmap;
        return joiPlayIcon(targetPixels);
    }

    public Drawable joiPlayDrawable() {
        try {
            return context.getPackageManager().getApplicationIcon(JoiPlayRepository.JOIPLAY_PACKAGE);
        } catch (Exception ignored) {
            return context.getDrawable(R.mipmap.ic_launcher);
        }
    }

    private Bitmap decodeGameIcon(Game game, int targetPixels) {
        if (game.icon.isEmpty()) return null;

        // The provider reads the registered icon in JoiPlay's process. This avoids scoped-storage
        // failures for games kept outside Download without granting this app broad file access.
        if (!game.id.isEmpty()) {
            Uri providerIcon = JoiPlayRepository.ICONS_URI.buildUpon().appendPath(game.id).build();
            Bitmap provided = decode(providerIcon, targetPixels);
            if (provided != null) return provided;
        }

        // Keep the legacy paths as compatibility fallbacks for older patched JoiPlay builds and
        // for shared-storage locations that remain directly readable.
        Uri uri = Uri.parse(game.icon);
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) {
            return decode(uri, targetPixels);
        }

        File direct = new File(game.icon);
        if (direct.isFile()) return decode(direct, targetPixels);
        if (!game.folder.isEmpty()) {
            File relative = new File(game.folder, game.icon);
            if (relative.isFile()) return decode(relative, targetPixels);
        }
        return null;
    }

    private Bitmap decode(Uri uri, int targetPixels) {
        try {
            return decode(new StreamFactory() {
                @Override public InputStream open() throws IOException {
                    InputStream stream = resolver.openInputStream(uri);
                    if (stream == null) throw new IOException("Image stream unavailable");
                    return stream;
                }
            }, targetPixels);
        } catch (IOException | SecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private Bitmap decode(File file, int targetPixels) {
        try {
            return decode(() -> new FileInputStream(file), targetPixels);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private Bitmap decode(StreamFactory factory, int targetPixels) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = factory.open()) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (largest / (sample * 2) >= targetPixels) sample *= 2;
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = factory.open()) {
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

    private Bitmap joiPlayIcon(int size) {
        Drawable drawable = joiPlayDrawable();
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private interface StreamFactory {
        InputStream open() throws IOException;
    }
}
