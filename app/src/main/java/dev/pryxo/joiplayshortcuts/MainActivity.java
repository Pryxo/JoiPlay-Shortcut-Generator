package dev.pryxo.joiplayshortcuts;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_CREATE_DOCUMENT = 40;
    private static final int REQUEST_OUTPUT_TREE = 41;
    private static final String PROJECT_URL = "https://github.com/Pryxo/JoiPlay-Shortcut-Generator";

    private enum Screen { LIBRARY, SETTINGS }

    private AppPreferences preferences;
    private Palette palette;
    private JoiPlayRepository repository;
    private ShortcutExporter exporter;
    private ExecutorService executor;
    private FrameLayout content;
    private TextView libraryNav;
    private TextView settingsNav;
    private Screen currentScreen = Screen.LIBRARY;
    private JoiPlayRepository.Result libraryResult;
    private boolean loading;
    private Game pendingDocumentGame;
    private boolean exportAllAfterFolderSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        palette = Palette.from(this, preferences.theme());
        repository = new JoiPlayRepository(this);
        exporter = new ShortcutExporter(this);
        executor = Executors.newSingleThreadExecutor();
        setContentView(buildShell());
        configureSystemBars();
        showLibrary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentScreen == Screen.LIBRARY && libraryResult != null && !loading) refreshLibrary();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (preferences.theme() == AppPreferences.ThemeMode.SYSTEM) recreate();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    private View buildShell() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(palette.background);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout navWrap = Ui.horizontal(this);
        navWrap.setGravity(Gravity.CENTER);
        navWrap.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 14));
        LinearLayout nav = Ui.horizontal(this);
        nav.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        nav.setBackground(Ui.outlined(this, palette.surface, palette.outline, 22));

        libraryNav = navItem("▦  Library", true);
        settingsNav = navItem("⚙  Settings", false);
        libraryNav.setOnClickListener(view -> showLibrary());
        settingsNav.setOnClickListener(view -> showSettings());
        nav.addView(libraryNav, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        nav.addView(settingsNav, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        int navWidth = Math.min(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32),
                Ui.dp(this, 560)
        );
        navWrap.addView(nav, new LinearLayout.LayoutParams(
                navWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(navWrap);
        return root;
    }

    private TextView navItem(String label, boolean selected) {
        TextView item = Ui.text(this, label, 13, selected ? palette.onPrimary : palette.textMuted, true);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        applyNavState(item, selected);
        return item;
    }

    private void applyNavState(TextView item, boolean selected) {
        item.setTextColor(selected ? palette.onPrimary : palette.textMuted);
        item.setBackground(selected
                ? Ui.ripple(this, palette.primary, Ui.withAlpha(palette.onPrimary, 24), 17)
                : Ui.ripple(this, palette.surface, Ui.withAlpha(palette.text, 20), 17));
    }

    private void selectScreen(Screen screen) {
        currentScreen = screen;
        applyNavState(libraryNav, screen == Screen.LIBRARY);
        applyNavState(settingsNav, screen == Screen.SETTINGS);
    }

    private void showLibrary() {
        selectScreen(Screen.LIBRARY);
        renderLibrary();
        if (libraryResult == null && !loading) refreshLibrary();
    }

    private void refreshLibrary() {
        if (loading) return;
        loading = true;
        if (currentScreen == Screen.LIBRARY) renderLibrary();
        boolean includeFolders = preferences.showFolderEntries();
        AppPreferences.SortOrder sort = preferences.sortOrder();
        executor.execute(() -> {
            JoiPlayRepository.Result result = repository.load(includeFolders, sort);
            runOnUiThread(() -> {
                loading = false;
                libraryResult = result;
                if (currentScreen == Screen.LIBRARY) renderLibrary();
            });
        });
    }

    private void renderLibrary() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 28));
        attachCenteredPage(scroll, page);

        page.addView(libraryHeader());
        page.addView(Ui.spacer(this, 1, 18));

        if (loading) {
            page.addView(loadingCard());
        } else if (libraryResult == null) {
            page.addView(loadingCard());
        } else if (!libraryResult.isReady()) {
            page.addView(connectionErrorCard(libraryResult));
        } else if (libraryResult.games.isEmpty()) {
            page.addView(emptyLibraryCard());
        } else {
            page.addView(librarySummary(libraryResult.games));
            page.addView(Ui.spacer(this, 1, 14));
            for (Game game : libraryResult.games) {
                page.addView(gameCard(game));
                page.addView(Ui.spacer(this, 1, 10));
            }
        }

        content.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private View libraryHeader() {
        LinearLayout row = Ui.horizontal(this);
        TextView mark = Ui.text(this, "J", 20, palette.onPrimary, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.rounded(this, palette.primary, 15));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        row.addView(Ui.spacer(this, 12, 1));

        LinearLayout labels = Ui.vertical(this);
        labels.addView(Ui.text(this, "JoiPlay Shortcuts", 22, palette.text, true));
        labels.addView(Ui.text(this, "Your library, ready for every frontend", 12, palette.textMuted, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = Ui.pillButton(this, palette, "↻", false);
        refresh.setTextSize(23);
        refresh.setContentDescription("Refresh library");
        refresh.setPadding(0, 0, 0, 0);
        refresh.setOnClickListener(view -> refreshLibrary());
        row.addView(refresh, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        return row;
    }

    private View librarySummary(List<Game> games) {
        int playable = 0;
        int launches = 0;
        for (Game game : games) {
            if (!game.folderEntry) playable++;
            launches += Math.max(0, game.playCount);
        }

        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        card.setBackground(Ui.rounded(this, palette.dark ? 0xFF173B39 : 0xFFD9F3EE, 22));
        card.addView(Ui.text(this, "LIBRARY CONNECTED", 11, palette.primary, true));
        card.addView(Ui.spacer(this, 1, 6));
        card.addView(Ui.text(this,
                playable + (playable == 1 ? " game" : " games") + "  ·  " + launches + " launches",
                19,
                palette.text,
                true
        ));
        card.addView(Ui.spacer(this, 1, 6));
        card.addView(Ui.text(this, "Tap to launch. Hold any game for details and shortcut tools.", 13, palette.textMuted, false));
        card.addView(Ui.spacer(this, 1, 16));
        TextView exportAll = Ui.pillButton(this, palette, "Generate all shortcut files", true);
        exportAll.setOnClickListener(view -> exportAll(games));
        card.addView(exportAll, Ui.matchWrap());
        return card;
    }

    private View loadingCard() {
        LinearLayout card = centeredCard();
        ProgressBar progress = new ProgressBar(this);
        card.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        card.addView(Ui.spacer(this, 1, 16));
        card.addView(Ui.text(this, "Reading your JoiPlay library…", 16, palette.text, true));
        card.addView(Ui.spacer(this, 1, 5));
        card.addView(Ui.text(this, "This stays entirely on your device.", 12, palette.textMuted, false));
        return card;
    }

    private View emptyLibraryCard() {
        LinearLayout card = centeredCard();
        TextView icon = Ui.text(this, "◇", 48, palette.secondary, false);
        card.addView(icon);
        card.addView(Ui.spacer(this, 1, 8));
        card.addView(Ui.text(this, "Your library is quiet", 18, palette.text, true));
        card.addView(Ui.spacer(this, 1, 6));
        TextView message = Ui.text(this, "Add a game in JoiPlay, then come back and refresh.", 13, palette.textMuted, false);
        message.setGravity(Gravity.CENTER);
        card.addView(message);
        card.addView(Ui.spacer(this, 1, 18));
        TextView refresh = Ui.pillButton(this, palette, "Refresh library", true);
        refresh.setOnClickListener(view -> refreshLibrary());
        card.addView(refresh, new LinearLayout.LayoutParams(Ui.dp(this, 190), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View connectionErrorCard(JoiPlayRepository.Result result) {
        String title;
        String message;
        if (result.status == JoiPlayRepository.Status.JOIPLAY_NOT_INSTALLED) {
            title = "JoiPlay is not installed";
            message = "Install the compatible modified JoiPlay build, add your games, and refresh here.";
        } else if (result.status == JoiPlayRepository.Status.PATCH_REQUIRED) {
            title = "Library connection unavailable";
            message = "This JoiPlay build does not expose the read-only library provider. Install the compatible modified build from this project.";
        } else if (result.status == JoiPlayRepository.Status.ACCESS_DENIED) {
            title = "Library access was denied";
            message = "The installed JoiPlay build rejected the app permission. Reinstall matching public-release builds and try again.";
        } else {
            title = "Could not read the library";
            message = "JoiPlay is present, but its game list could not be read. Nothing was changed.";
        }

        LinearLayout card = centeredCard();
        card.addView(Ui.text(this, "!", 38, palette.error, true));
        card.addView(Ui.spacer(this, 1, 8));
        card.addView(Ui.text(this, title, 18, palette.text, true));
        card.addView(Ui.spacer(this, 1, 6));
        TextView body = Ui.text(this, message, 13, palette.textMuted, false);
        body.setGravity(Gravity.CENTER);
        card.addView(body);
        card.addView(Ui.spacer(this, 1, 18));
        TextView retry = Ui.pillButton(this, palette, "Try again", true);
        retry.setOnClickListener(view -> refreshLibrary());
        card.addView(retry, new LinearLayout.LayoutParams(Ui.dp(this, 160), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout centeredCard() {
        LinearLayout card = Ui.vertical(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 52), Ui.dp(this, 24), Ui.dp(this, 52));
        card.setBackground(Ui.outlined(this, palette.surface, palette.outline, 22));
        return card;
    }

    private View gameCard(Game game) {
        LinearLayout card = Ui.horizontal(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));
        card.setBackground(Ui.ripple(this, palette.surface, Ui.withAlpha(palette.primary, 30), 19));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(game.title + ", " + game.runtimeLabel() + ". Tap to launch; hold for details.");

        TextView art = Ui.text(this, initial(game.title), 22, palette.onPrimary, true);
        art.setGravity(Gravity.CENTER);
        int artColor = game.folderEntry ? palette.secondary : palette.primary;
        art.setBackground(Ui.rounded(this, artColor, 16));
        card.addView(art, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
        card.addView(Ui.spacer(this, 13, 1));

        LinearLayout details = Ui.vertical(this);
        TextView title = Ui.text(this, game.title, 16, palette.text, true);
        title.setMaxLines(1);
        details.addView(title);
        details.addView(Ui.spacer(this, 1, 5));
        String plays = game.playCount == 1 ? "1 launch" : game.playCount + " launches";
        details.addView(Ui.text(this, game.runtimeLabel() + "  ·  " + plays, 12, palette.textMuted, false));
        details.addView(Ui.spacer(this, 1, 4));
        TextView path = Ui.text(this, game.locationLabel(), 11, Ui.withAlpha(palette.textMuted, 190), false);
        path.setMaxLines(1);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        details.addView(path);
        card.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!game.folderEntry) {
            TextView play = Ui.pillButton(this, palette, "▶", false);
            play.setTextColor(palette.primary);
            play.setTextSize(16);
            play.setContentDescription("Launch " + game.title);
            play.setPadding(0, 0, 0, 0);
            play.setOnClickListener(view -> launchGame(game));
            card.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        }

        card.setOnClickListener(view -> {
            if (preferences.tapAction() == AppPreferences.TapAction.DETAILS || game.folderEntry) {
                showGameDetails(game);
            } else {
                launchGame(game);
            }
        });
        card.setOnLongClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showGameDetails(game);
            return true;
        });
        return card;
    }

    private void showSettings() {
        selectScreen(Screen.SETTINGS);
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 30));
        attachCenteredPage(scroll, page);

        page.addView(Ui.text(this, "Settings", 27, palette.text, true));
        page.addView(Ui.spacer(this, 1, 4));
        page.addView(Ui.text(this, "Make shortcut creation feel like yours.", 13, palette.textMuted, false));
        page.addView(Ui.spacer(this, 1, 24));

        page.addView(sectionLabel("APPEARANCE"));
        page.addView(settingsGroup(Arrays.asList(
                settingRow("App theme", "Follow the device or choose a look", themeLabel(), () -> chooseTheme())
        )));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("SHORTCUTS"));
        ArrayList<View> shortcutRows = new ArrayList<>();
        shortcutRows.add(settingRow("File type", "Daijishō player-template output", formatLabel(), () -> chooseFormat()));
        shortcutRows.add(settingRow("Output folder", "Used for one-tap and bulk generation", outputLabel(), () -> chooseOutputFolder(false)));
        shortcutRows.add(settingRow("Tap behavior", "What happens when you tap a library game", tapLabel(), () -> chooseTapAction()));
        page.addView(settingsGroup(shortcutRows));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("LIBRARY"));
        ArrayList<View> libraryRows = new ArrayList<>();
        libraryRows.add(settingRow("Sort games", "Choose the order used in Library", sortLabel(), () -> chooseSortOrder()));
        libraryRows.add(folderEntriesSwitch());
        page.addView(settingsGroup(libraryRows));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("ABOUT"));
        page.addView(settingsGroup(Arrays.asList(
                settingRow("Project page", "Source, releases, setup, and issues", "GitHub  ↗", this::openProject),
                settingRow("Privacy", "No network access, ads, or analytics", "Read", this::showPrivacy),
                settingRow("Version", "JoiPlay Shortcuts", getVersionName(), null)
        )));

        content.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private TextView sectionLabel(String value) {
        TextView label = Ui.text(this, value, 11, palette.primary, true);
        label.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        return label;
    }

    private View settingsGroup(List<View> rows) {
        LinearLayout group = Ui.vertical(this);
        group.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        group.setBackground(Ui.outlined(this, palette.surface, palette.outline, 19));
        for (int index = 0; index < rows.size(); index++) {
            group.addView(rows.get(index), Ui.matchWrap());
            if (index + 1 < rows.size()) {
                View divider = new View(this);
                divider.setBackgroundColor(palette.outline);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 1)
                );
                dividerParams.setMarginStart(Ui.dp(this, 16));
                dividerParams.setMarginEnd(Ui.dp(this, 16));
                group.addView(divider, dividerParams);
            }
        }
        return group;
    }

    private View settingRow(String title, String subtitle, String value, Runnable action) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 12), Ui.dp(this, 15));
        LinearLayout labels = Ui.vertical(this);
        labels.addView(Ui.text(this, title, 15, palette.text, true));
        labels.addView(Ui.spacer(this, 1, 4));
        labels.addView(Ui.text(this, subtitle, 11, palette.textMuted, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView selected = Ui.text(this, value, 12, palette.primary, true);
        selected.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selected.setMaxWidth(Ui.dp(this, 130));
        selected.setMaxLines(2);
        row.addView(selected);
        if (action != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(Ui.ripple(this, palette.surface, Ui.withAlpha(palette.primary, 24), 15));
            row.setOnClickListener(view -> action.run());
        }
        return row;
    }

    @SuppressWarnings("deprecation")
    private View folderEntriesSwitch() {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 8), Ui.dp(this, 13));
        LinearLayout labels = Ui.vertical(this);
        labels.addView(Ui.text(this, "Show folder entries", 15, palette.text, true));
        labels.addView(Ui.spacer(this, 1, 4));
        labels.addView(Ui.text(this, "Include JoiPlay organizational folders", 11, palette.textMuted, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(preferences.showFolderEntries());
        toggle.setContentDescription("Show JoiPlay folder entries");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.setShowFolderEntries(checked);
            libraryResult = null;
        });
        row.addView(toggle);
        return row;
    }

    private void showGameDetails(Game game) {
        LinearLayout sheet = Ui.vertical(this);
        sheet.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 28));
        sheet.setBackground(Ui.rounded(this, palette.surface, 26));

        TextView handle = new TextView(this);
        handle.setBackground(Ui.rounded(this, palette.outline, 3));
        LinearLayout handleRow = Ui.horizontal(this);
        handleRow.setGravity(Gravity.CENTER);
        handleRow.addView(handle, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 4)));
        sheet.addView(handleRow);
        sheet.addView(Ui.spacer(this, 1, 18));

        LinearLayout heading = Ui.horizontal(this);
        TextView art = Ui.text(this, initial(game.title), 24, palette.onPrimary, true);
        art.setGravity(Gravity.CENTER);
        art.setBackground(Ui.rounded(this, game.folderEntry ? palette.secondary : palette.primary, 18));
        heading.addView(art, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        heading.addView(Ui.spacer(this, 14, 1));
        LinearLayout text = Ui.vertical(this);
        TextView title = Ui.text(this, game.title, 21, palette.text, true);
        title.setMaxLines(2);
        text.addView(title);
        text.addView(Ui.spacer(this, 1, 5));
        text.addView(Ui.text(this, game.runtimeLabel() + "  ·  " + game.playCount + " launches", 12, palette.textMuted, false));
        heading.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sheet.addView(heading);
        sheet.addView(Ui.spacer(this, 1, 18));

        sheet.addView(metadataLine("Game ID", game.id));
        sheet.addView(metadataLine("Location", game.locationLabel()));
        if (!game.version.isEmpty()) sheet.addView(metadataLine("Version", game.version));
        if (game.date > 0) sheet.addView(metadataLine("Added", formatDate(game.date)));
        sheet.addView(Ui.spacer(this, 1, 18));

        Dialog dialog = bottomDialog(sheet);
        if (!game.folderEntry) {
            TextView launch = Ui.pillButton(this, palette, "Launch in JoiPlay", true);
            launch.setOnClickListener(view -> {
                dialog.dismiss();
                launchGame(game);
            });
            sheet.addView(launch, Ui.matchWrap());
            sheet.addView(Ui.spacer(this, 1, 10));

            LinearLayout actions = Ui.horizontal(this);
            TextView generate = Ui.pillButton(this, palette, "Generate file", false);
            generate.setOnClickListener(view -> {
                dialog.dismiss();
                generateShortcut(game);
            });
            TextView pin = Ui.pillButton(this, palette, "Pin to Home", false);
            pin.setOnClickListener(view -> {
                dialog.dismiss();
                pinToHome(game);
            });
            actions.addView(generate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            actions.addView(Ui.spacer(this, 8, 1));
            actions.addView(pin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            sheet.addView(actions);
            sheet.addView(Ui.spacer(this, 1, 10));
        }
        TextView copy = Ui.pillButton(this, palette, "Copy JoiPlay ID", false);
        copy.setOnClickListener(view -> {
            copyText("JoiPlay game ID", game.id);
            dialog.dismiss();
        });
        sheet.addView(copy, Ui.matchWrap());
        dialog.show();
        sizeBottomDialog(dialog);
    }

    private View metadataLine(String label, String value) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        TextView key = Ui.text(this, label, 12, palette.textMuted, true);
        row.addView(key, new LinearLayout.LayoutParams(Ui.dp(this, 82), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView content = Ui.text(this, value.isEmpty() ? "—" : value, 12, palette.text, false);
        content.setTextIsSelectable(true);
        content.setMaxLines(3);
        content.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private Dialog bottomDialog(View body) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(body);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private void sizeBottomDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.setDimAmount(0.55f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 20),
                Ui.dp(this, 720)
        );
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        window.setAttributes(params);
    }

    private void attachCenteredPage(ScrollView scroll, LinearLayout page) {
        FrameLayout lane = new FrameLayout(this);
        int pageWidth = Math.min(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24),
                Ui.dp(this, 780)
        );
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(
                pageWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        lane.addView(page, pageParams);
        scroll.addView(lane, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void showChoice(String title, String[] options, int selected, ChoiceHandler handler) {
        LinearLayout sheet = Ui.vertical(this);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 28));
        sheet.setBackground(Ui.rounded(this, palette.surface, 26));
        sheet.addView(Ui.text(this, title, 20, palette.text, true));
        sheet.addView(Ui.spacer(this, 1, 14));
        Dialog dialog = bottomDialog(sheet);
        for (int index = 0; index < options.length; index++) {
            String prefix = index == selected ? "●  " : "○  ";
            TextView option = Ui.text(this, prefix + options[index], 15,
                    index == selected ? palette.primary : palette.text, index == selected);
            option.setPadding(Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15));
            option.setBackground(Ui.ripple(this, palette.surface, Ui.withAlpha(palette.primary, 25), 14));
            option.setClickable(true);
            int chosen = index;
            option.setOnClickListener(view -> {
                dialog.dismiss();
                handler.onChoice(chosen);
            });
            sheet.addView(option, Ui.matchWrap());
        }
        dialog.show();
        sizeBottomDialog(dialog);
    }

    private void chooseTheme() {
        AppPreferences.ThemeMode[] values = AppPreferences.ThemeMode.values();
        showChoice("App theme", new String[]{"Use device setting", "Light", "Dark"}, preferences.theme().ordinal(), index -> {
            preferences.setTheme(values[index]);
            recreate();
        });
    }

    private void chooseFormat() {
        AppPreferences.ShortcutFormat[] values = AppPreferences.ShortcutFormat.values();
        showChoice("Shortcut file type", new String[]{".dpt · Generic template", ".joiplay · Dedicated platform"}, preferences.shortcutFormat().ordinal(), index -> {
            preferences.setShortcutFormat(values[index]);
            showSettings();
        });
    }

    private void chooseSortOrder() {
        AppPreferences.SortOrder[] values = AppPreferences.SortOrder.values();
        showChoice("Sort games", new String[]{"Title", "Recently added", "Most played"}, preferences.sortOrder().ordinal(), index -> {
            preferences.setSortOrder(values[index]);
            libraryResult = null;
            showSettings();
        });
    }

    private void chooseTapAction() {
        AppPreferences.TapAction[] values = AppPreferences.TapAction.values();
        showChoice("When a game is tapped", new String[]{"Launch in JoiPlay", "Show game details"}, preferences.tapAction().ordinal(), index -> {
            preferences.setTapAction(values[index]);
            showSettings();
        });
    }

    private void chooseOutputFolder(boolean exportAfterSelection) {
        exportAllAfterFolderSelection = exportAfterSelection;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri existing = preferences.outputTree();
        if (existing != null) intent.putExtra("android.provider.extra.INITIAL_URI", existing);
        startActivityForResult(intent, REQUEST_OUTPUT_TREE);
    }

    private void generateShortcut(Game game) {
        Uri outputTree = preferences.outputTree();
        if (outputTree == null) {
            pendingDocumentGame = game;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(ShortcutFileFactory.mimeType());
            intent.putExtra(Intent.EXTRA_TITLE, ShortcutFileFactory.fileName(game, preferences.shortcutFormat()));
            startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
            return;
        }
        Toast.makeText(this, "Generating shortcut…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                exporter.writeToTree(outputTree, game, preferences.shortcutFormat());
                runOnUiThread(() -> Toast.makeText(this, "Shortcut generated", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> {
                    preferences.setOutputTree(null);
                    Toast.makeText(this, "The output folder is no longer writable. Choose it again.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exportAll(List<Game> games) {
        if (preferences.outputTree() == null) {
            chooseOutputFolder(true);
            return;
        }
        exportAllToConfiguredFolder(games);
    }

    private void exportAllToConfiguredFolder(List<Game> games) {
        Uri tree = preferences.outputTree();
        if (tree == null) return;
        Toast.makeText(this, "Generating " + games.size() + " shortcuts…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            ShortcutExporter.ExportSummary summary = exporter.writeAll(tree, games, preferences.shortcutFormat());
            runOnUiThread(() -> {
                String message = summary.written + " shortcuts generated";
                if (summary.failed > 0) message += " · " + summary.failed + " failed";
                Toast.makeText(this, message, summary.failed > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingDocumentGame = null;
            exportAllAfterFolderSelection = false;
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_CREATE_DOCUMENT && pendingDocumentGame != null) {
            Game game = pendingDocumentGame;
            pendingDocumentGame = null;
            executor.execute(() -> {
                try {
                    exporter.writeDocument(uri, game);
                    runOnUiThread(() -> Toast.makeText(this, "Shortcut generated", Toast.LENGTH_SHORT).show());
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(this, "Could not write the shortcut", Toast.LENGTH_LONG).show());
                }
            });
        } else if (requestCode == REQUEST_OUTPUT_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            } catch (SecurityException ignored) {
                Toast.makeText(this, "This folder cannot be remembered", Toast.LENGTH_LONG).show();
            }
            preferences.setOutputTree(uri);
            boolean shouldExport = exportAllAfterFolderSelection;
            exportAllAfterFolderSelection = false;
            if (shouldExport && libraryResult != null && libraryResult.isReady()) {
                exportAllToConfiguredFolder(libraryResult.games);
            }
            if (currentScreen == Screen.SETTINGS) showSettings();
        }
    }

    private void launchGame(Game game) {
        if (game.folderEntry || game.id.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setComponent(new ComponentName(JoiPlayRepository.JOIPLAY_PACKAGE, JoiPlayRepository.SHORTCUT_ACTIVITY));
        intent.putExtra("id", game.id);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "This JoiPlay build cannot launch external shortcuts", Toast.LENGTH_LONG).show();
        }
    }

    private void pinToHome(Game game) {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(this, "Your launcher does not support pinned shortcuts", Toast.LENGTH_LONG).show();
            return;
        }
        Intent launch = new Intent(Intent.ACTION_VIEW);
        launch.setComponent(new ComponentName(JoiPlayRepository.JOIPLAY_PACKAGE, JoiPlayRepository.SHORTCUT_ACTIVITY));
        launch.putExtra("id", game.id);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "joiplay-" + game.id)
                .setShortLabel(game.title)
                .setLongLabel("Launch " + game.title + " in JoiPlay")
                .setIcon(Icon.createWithBitmap(shortcutIcon(game)))
                .setIntent(launch)
                .build();
        boolean requested = manager.requestPinShortcut(shortcut, null);
        Toast.makeText(this, requested ? "Pin request sent" : "Could not request the shortcut", Toast.LENGTH_SHORT).show();
    }

    private Bitmap shortcutIcon(Game game) {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(palette.primary);
        canvas.drawRoundRect(new RectF(0, 0, size, size), 44, 44, paint);
        paint.setColor(palette.onPrimary);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(92);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(initial(game.title), size / 2f, baseline, paint);
        return bitmap;
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void showPrivacy() {
        LinearLayout sheet = Ui.vertical(this);
        sheet.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 28));
        sheet.setBackground(Ui.rounded(this, palette.surface, 26));
        sheet.addView(Ui.text(this, "Privacy by design", 21, palette.text, true));
        sheet.addView(Ui.spacer(this, 1, 12));
        TextView text = Ui.text(this,
                "JoiPlay Shortcuts has no Internet permission, analytics, ads, or accounts. It reads the sanitized game list exposed by the modified JoiPlay provider and writes shortcut files only to a folder or document you choose. Settings remain on this device.",
                14,
                palette.textMuted,
                false
        );
        text.setLineSpacing(0, 1.16f);
        sheet.addView(text);
        sheet.addView(Ui.spacer(this, 1, 18));
        TextView close = Ui.pillButton(this, palette, "Got it", true);
        sheet.addView(close, Ui.matchWrap());
        Dialog dialog = bottomDialog(sheet);
        close.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
        sizeBottomDialog(dialog);
    }

    private void openProject() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, PROJECT_URL, Toast.LENGTH_LONG).show();
        }
    }

    private String themeLabel() {
        if (preferences.theme() == AppPreferences.ThemeMode.LIGHT) return "Light  ›";
        if (preferences.theme() == AppPreferences.ThemeMode.DARK) return "Dark  ›";
        return "System  ›";
    }

    private String formatLabel() {
        return "." + ShortcutFileFactory.extension(preferences.shortcutFormat()) + "  ›";
    }

    private String sortLabel() {
        if (preferences.sortOrder() == AppPreferences.SortOrder.RECENTLY_ADDED) return "Recent  ›";
        if (preferences.sortOrder() == AppPreferences.SortOrder.MOST_PLAYED) return "Most played  ›";
        return "Title  ›";
    }

    private String tapLabel() {
        return preferences.tapAction() == AppPreferences.TapAction.LAUNCH ? "Launch  ›" : "Details  ›";
    }

    private String outputLabel() {
        return preferences.outputTree() == null ? "Choose  ›" : "Selected  ›";
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "0.1.0";
        }
    }

    private String formatDate(long raw) {
        long milliseconds = raw < 10_000_000_000L ? raw * 1000L : raw;
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(milliseconds));
    }

    private static String initial(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) return "?";
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        if (android.os.Build.VERSION.SDK_INT >= 28) window.setNavigationBarDividerColor(palette.background);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(palette.dark ? 0 : mask, mask);
            }
        } else {
            int flags = 0;
            if (!palette.dark) {
                flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private interface ChoiceHandler {
        void onChoice(int index);
    }
}
