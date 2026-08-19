package dev.pryxo.joiplayshortcuts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(Context context, int color, int stroke, float radiusDp) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static RippleDrawable ripple(Context context, int fill, int ripple, float radiusDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(ripple),
                rounded(context, fill, radiusDp),
                rounded(context, Color.WHITE, radiusDp)
        );
    }

    public static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static TextView pillButton(Context context, Palette palette, String label, boolean primary) {
        TextView button = text(
                context,
                label,
                13,
                primary ? palette.onPrimary : palette.text,
                true
        );
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 44));
        button.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        button.setBackground(ripple(
                context,
                primary ? palette.primary : palette.surfaceRaised,
                withAlpha(primary ? palette.onPrimary : palette.text, 28),
                15
        ));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static View spacer(Context context, int widthDp, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp)));
        return view;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}

