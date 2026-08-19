package dev.pryxo.joiplayshortcuts;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

public final class Palette {
    public final boolean dark;
    public final int background;
    public final int surface;
    public final int surfaceRaised;
    public final int outline;
    public final int text;
    public final int textMuted;
    public final int primary;
    public final int onPrimary;
    public final int secondary;
    public final int error;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            background = Color.rgb(9, 13, 19);
            surface = Color.rgb(18, 25, 37);
            surfaceRaised = Color.rgb(27, 37, 51);
            outline = Color.rgb(48, 62, 79);
            text = Color.rgb(242, 247, 249);
            textMuted = Color.rgb(159, 174, 188);
            primary = Color.rgb(121, 213, 197);
            onPrimary = Color.rgb(7, 46, 42);
            secondary = Color.rgb(169, 183, 255);
            error = Color.rgb(255, 141, 135);
        } else {
            background = Color.rgb(244, 247, 248);
            surface = Color.WHITE;
            surfaceRaised = Color.rgb(234, 240, 241);
            outline = Color.rgb(210, 220, 222);
            text = Color.rgb(22, 34, 38);
            textMuted = Color.rgb(91, 108, 113);
            primary = Color.rgb(0, 107, 95);
            onPrimary = Color.WHITE;
            secondary = Color.rgb(71, 86, 158);
            error = Color.rgb(181, 48, 44);
        }
    }

    public static Palette from(Context context, AppPreferences.ThemeMode mode) {
        boolean systemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = mode == AppPreferences.ThemeMode.DARK
                || (mode == AppPreferences.ThemeMode.SYSTEM && systemDark);
        return new Palette(dark);
    }
}

