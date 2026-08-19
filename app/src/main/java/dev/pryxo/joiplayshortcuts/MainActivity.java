package dev.pryxo.joiplayshortcuts;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_CREATE_DOCUMENT = 40;
    private static final int REQUEST_OUTPUT_TREE = 41;
    private static final int REQUEST_CUSTOM_ICON = 42;
    private static final int REQUEST_IISU_TREE = 43;
    private static final String PROJECT_URL = "https://github.com/Pryxo/JoiPlay-Shortcut-Generator";

    private enum Screen { LIBRARY, SETTINGS }

    private AppPreferences preferences;
    private Palette palette;
    private JoiPlayRepository repository;
    private ShortcutExporter exporter;
    private SettingsFileStore settingsFileStore;
    private IisuSupportManager iisuSupportManager;
    private GameArtLoader artLoader;
    private ExecutorService executor;
    private ExecutorService artExecutor;
    private FrameLayout content;
    private TextView libraryNav;
    private TextView settingsNav;
    private FrameLayout navTrack;
    private View navSelection;
    private Screen currentScreen = Screen.LIBRARY;
    private JoiPlayRepository.Result libraryResult;
    private boolean loading;
    private Game pendingDocumentGame;
    private Game pendingCustomIconGame;
    private boolean exportAllAfterFolderSelection;
    private boolean rememberOutputFolderSelection;
    private View refreshButton;
    private TextView refreshIcon;
    private TextView iisuActionButton;
    private ObjectAnimator refreshAnimator;
    private IisuSupportManager.Status iisuStatus = IisuSupportManager.Status.NOT_CONFIGURED;
    private String iisuStatusMessage = "Select the iiSU folder";
    private boolean iisuWorking;
    private LinearLayout libraryContent;
    private int nextTransitionDirection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        artExecutor = Executors.newFixedThreadPool(2);
        preferences = new AppPreferences(this);
        palette = Palette.from(this, preferences.theme(), preferences.accentColor());
        repository = new JoiPlayRepository(this);
        exporter = new ShortcutExporter(this);
        settingsFileStore = new SettingsFileStore(this);
        iisuSupportManager = new IisuSupportManager(this);
        artLoader = new GameArtLoader(this, preferences);
        preferences.setPortableSettingsChangedListener(this::scheduleSettingsBackup);
        scheduleSettingsBackup();
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
        stopRefreshAnimation();
        if (executor != null) executor.shutdownNow();
        if (artExecutor != null) artExecutor.shutdownNow();
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
        navTrack = new FrameLayout(this);
        navTrack.setBackground(Ui.outlined(this, palette.surface, palette.outline, 22));

        navSelection = new View(this);
        navSelection.setBackground(Ui.rounded(this, palette.primary, 17));
        FrameLayout.LayoutParams selectionParams = new FrameLayout.LayoutParams(0, Ui.dp(this, 46));
        selectionParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        selectionParams.setMarginStart(Ui.dp(this, 5));
        navTrack.addView(navSelection, selectionParams);

        LinearLayout navItems = Ui.horizontal(this);

        libraryNav = navItem("▦  Library (0/0)", true);
        settingsNav = navItem("⚙  Settings", false);
        libraryNav.setOnClickListener(view -> showLibrary());
        settingsNav.setOnClickListener(view -> showSettings());
        navItems.addView(libraryNav, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        navItems.addView(settingsNav, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        itemParams.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        navTrack.addView(navItems, itemParams);
        int navWidth = Math.min(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32),
                Ui.dp(this, 680)
        );
        navWrap.addView(navTrack, new LinearLayout.LayoutParams(
                navWidth,
                Ui.dp(this, 56)
        ));
        navTrack.addOnLayoutChangeListener((view, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) -> positionNavSelection(false));
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
        item.setBackground(Ui.ripple(this, Color.TRANSPARENT,
                Ui.withAlpha(selected ? palette.onPrimary : palette.text, 24), 17));
    }

    private void selectScreen(Screen screen) {
        if (currentScreen != screen) nextTransitionDirection = screen == Screen.SETTINGS ? 1 : -1;
        currentScreen = screen;
        animateNavText(libraryNav, screen == Screen.LIBRARY);
        animateNavText(settingsNav, screen == Screen.SETTINGS);
        positionNavSelection(true);
    }

    private void animateNavText(TextView item, boolean selected) {
        int target = selected ? palette.onPrimary : palette.textMuted;
        int start = item.getCurrentTextColor();
        item.setBackground(Ui.ripple(this, Color.TRANSPARENT,
                Ui.withAlpha(selected ? palette.onPrimary : palette.text, 24), 17));
        if (start == target) return;
        ValueAnimator color = ValueAnimator.ofObject(new ArgbEvaluator(), start, target);
        color.setDuration(220);
        color.addUpdateListener(animation -> item.setTextColor((Integer) animation.getAnimatedValue()));
        color.start();
    }

    private void positionNavSelection(boolean animate) {
        if (navTrack == null || navSelection == null || navTrack.getWidth() == 0) return;
        int laneWidth = navTrack.getWidth() - Ui.dp(this, 10);
        int itemWidth = laneWidth / 2;
        ViewGroup.LayoutParams rawParams = navSelection.getLayoutParams();
        if (rawParams.width != itemWidth) {
            rawParams.width = itemWidth;
            navSelection.setLayoutParams(rawParams);
        }
        float target = currentScreen == Screen.SETTINGS ? itemWidth : 0f;
        navSelection.animate().cancel();
        if (animate) {
            navSelection.animate()
                    .translationX(target)
                    .setDuration(280)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        } else {
            navSelection.setTranslationX(target);
        }
    }

    private void showLibrary() {
        selectScreen(Screen.LIBRARY);
        renderLibrary();
        if (libraryResult == null && !loading) refreshLibrary();
    }

    private void refreshLibrary() {
        if (loading) return;
        loading = true;
        animateRefresh();
        boolean includeFolders = preferences.showFolderEntries();
        AppPreferences.SortOrder sort = preferences.sortOrder();
        iisuStatus = preferences.iisuTree() == null
                ? IisuSupportManager.Status.NOT_CONFIGURED : IisuSupportManager.Status.CHECKING;
        updateIisuActionButton();
        executor.execute(() -> {
            IisuSupportManager.Result iisuResult = iisuSupportManager.scan(preferences.iisuTree());
            JoiPlayRepository.Result result = repository.load(includeFolders, sort);
            if (result.isReady()) {
                Uri outputTree = preferences.outputTree();
                if (outputTree != null) {
                    ShortcutExporter.ScanResult existing =
                            exporter.findExisting(outputTree, result.games);
                    if (existing.successful) {
                        // File contents are authoritative, even when a title or
                        // filename changed since the shortcut was generated.
                        preferences.replaceGeneratedShortcuts(existing.idsByFormat);
                    }
                }
            }
            runOnUiThread(() -> {
                loading = false;
                libraryResult = result;
                iisuStatus = iisuResult.status;
                iisuStatusMessage = iisuResult.message;
                stopRefreshAnimation();
                updateIisuActionButton();
                if (currentScreen == Screen.LIBRARY) {
                    if (libraryContent != null && libraryContent.isAttachedToWindow()) {
                        renderLibraryContent();
                    } else {
                        renderLibrary();
                    }
                }
            });
        });
    }

    private void renderLibrary() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 28));
        attachCenteredPage(scroll, page, 1240);

        page.addView(libraryHeader());
        page.addView(Ui.spacer(this, 1, 18));

        libraryContent = Ui.vertical(this);
        page.addView(libraryContent, Ui.matchWrap());
        renderLibraryContent();

        installContent(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void renderLibraryContent() {
        if (libraryContent == null) return;
        updateLibraryNavLabel();
        libraryContent.removeAllViews();

        if (libraryResult == null) {
            libraryContent.addView(loadingCard());
        } else if (!libraryResult.isReady()) {
            libraryContent.addView(connectionErrorCard(libraryResult));
        } else if (libraryResult.games.isEmpty()) {
            libraryContent.addView(emptyLibraryCard());
        } else {
            libraryContent.addView(librarySummary(libraryResult.games));
            libraryContent.addView(Ui.spacer(this, 1, 14));
            if (preferences.viewMode() == AppPreferences.ViewMode.GRID) {
                libraryContent.addView(gameGrid(libraryResult.games));
            } else {
                for (Game game : libraryResult.games) {
                    libraryContent.addView(gameCard(game));
                    libraryContent.addView(Ui.spacer(this, 1, 10));
                }
            }
        }
    }

    private void updateLibraryNavLabel() {
        int generated = 0;
        int total = 0;
        if (libraryResult != null && libraryResult.isReady()) {
            for (Game game : libraryResult.games) {
                if (game.folderEntry) continue;
                total++;
                if (preferences.isShortcutGenerated(game.id)) generated++;
            }
        }
        libraryNav.setText(String.format(Locale.ROOT, "▦  Library (%d/%d)", generated, total));
    }

    private View libraryHeader() {
        LinearLayout header = Ui.vertical(this);
        LinearLayout row = Ui.horizontal(this);
        ImageView mark = new ImageView(this);
        mark.setImageDrawable(artLoader.joiPlayDrawable());
        mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mark.setBackground(Ui.rounded(this, palette.surfaceRaised, 15));
        mark.setClipToOutline(true);
        mark.setContentDescription("JoiPlay icon");
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        row.addView(Ui.spacer(this, 12, 1));

        LinearLayout labels = Ui.vertical(this);
        labels.addView(Ui.text(this, "JoiPlay Shortcut Generator", 20, palette.text, true));
        labels.addView(Ui.text(this, "Your library, ready for every frontend", 12, palette.textMuted, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        iisuActionButton = Ui.pillButton(this, palette, iisuActionLabel(), true);
        iisuActionButton.setTextSize(12);
        iisuActionButton.setMaxLines(2);
        iisuActionButton.setOnClickListener(view -> handleIisuAction());
        updateIisuActionButton();
        boolean wideHeader = getResources().getConfiguration().screenWidthDp >= 700;
        if (wideHeader) {
            row.addView(Ui.spacer(this, 12, 1));
            row.addView(iisuActionButton,
                    new LinearLayout.LayoutParams(Ui.dp(this, 270), Ui.dp(this, 44)));
            row.addView(Ui.spacer(this, 8, 1));
        }

        TextView viewMode = Ui.pillButton(this, palette,
                preferences.viewMode() == AppPreferences.ViewMode.LIST ? "▦" : "☷", false);
        viewMode.setTextSize(19);
        viewMode.setContentDescription(preferences.viewMode() == AppPreferences.ViewMode.LIST
                ? "Switch to grid view" : "Switch to list view");
        viewMode.setPadding(0, 0, 0, 0);
        viewMode.setOnClickListener(view -> {
            AppPreferences.ViewMode mode = preferences.viewMode() == AppPreferences.ViewMode.LIST
                    ? AppPreferences.ViewMode.GRID : AppPreferences.ViewMode.LIST;
            preferences.setViewMode(mode);
            renderLibrary();
        });
        row.addView(viewMode, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        row.addView(Ui.spacer(this, 7, 1));

        FrameLayout refresh = new FrameLayout(this);
        refresh.setBackground(Ui.ripple(
                this, palette.surfaceRaised, Ui.withAlpha(palette.text, 28), 15));
        refresh.setClickable(true);
        refresh.setFocusable(true);
        refreshIcon = Ui.text(this, "↻", 23, palette.text, true);
        refreshIcon.setGravity(Gravity.CENTER);
        refresh.addView(refreshIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshButton = refresh;
        refreshButton.setContentDescription("Refresh library");
        refreshButton.setEnabled(!loading);
        refreshButton.setOnClickListener(view -> refreshLibrary());
        row.addView(refreshButton, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        if (loading) refreshButton.post(this::animateRefresh);
        header.addView(row, Ui.matchWrap());
        if (!wideHeader) {
            header.addView(Ui.spacer(this, 1, 10));
            header.addView(iisuActionButton, Ui.matchWrap());
        }
        return header;
    }

    private View librarySummary(List<Game> games) {
        int playable = 0;
        int launches = 0;
        int generated = 0;
        for (Game game : games) {
            if (!game.folderEntry) {
                playable++;
                if (preferences.isShortcutGenerated(game.id)) generated++;
            }
            launches += Math.max(0, game.playCount);
        }

        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        card.setBackground(Ui.rounded(this, blend(palette.surface, palette.primary, palette.dark ? 0.22f : 0.13f), 22));
        card.addView(Ui.text(this, "LIBRARY CONNECTED", 11, palette.primary, true));
        card.addView(Ui.spacer(this, 1, 6));
        TextView totals = Ui.text(this,
                "Total Games: " + playable + " • Total Launches: " + launches,
                19,
                palette.text,
                true);
        totals.setMaxLines(1);
        totals.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(totals);
        card.addView(Ui.spacer(this, 1, 6));
        String shortcutStatus = generated == 0
                ? "No shortcut files generated yet"
                : "✓ Shortcuts generated: " + generated + " of " + playable;
        card.addView(Ui.text(this, shortcutStatus, 13,
                generated > 0 ? palette.primary : palette.textMuted, generated > 0));
        card.addView(Ui.spacer(this, 1, 5));
        card.addView(Ui.text(this, "Tap to launch. Hold any game for details and shortcut tools.", 12, palette.textMuted, false));
        card.addView(Ui.spacer(this, 1, 16));
        String bulkAction = preferences.outputTree() == null
                ? "Select JoiPlay folder"
                : "Generate all missing ." + ShortcutFileFactory.extension(preferences.shortcutFormat())
                        + " shortcut files";
        TextView exportAll = Ui.pillButton(this, palette, bulkAction, true);
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

        card.addView(gameArt(game, 58, false), new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
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
        if (!game.folderEntry && preferences.isShortcutGenerated(game.id)) {
            details.addView(Ui.spacer(this, 1, 5));
            details.addView(Ui.text(this, generatedStatusLabel(game.id), 11, palette.primary, true));
        }
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

    private View gameGrid(List<Game> games) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int laneWidth = Math.min(screenWidth - Ui.dp(this, 60), Ui.dp(this, 1204));
        int gap = Ui.dp(this, 10);
        int columns = Math.max(2, Math.min(5,
                (laneWidth + gap) / (Ui.dp(this, 220) + gap)));
        int cardWidth = (laneWidth - gap * (columns - 1)) / columns;

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        for (int index = 0; index < games.size(); index++) {
            Game game = games.get(index);
            int row = index / columns;
            int column = index % columns;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(row), GridLayout.spec(column));
            params.width = cardWidth;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (column > 0) params.leftMargin = gap;
            params.bottomMargin = gap;
            grid.addView(gridGameCard(game, cardWidth), params);
        }
        return grid;
    }

    private View gridGameCard(Game game, int cardWidth) {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 13));
        card.setBackground(Ui.ripple(this, palette.surface, Ui.withAlpha(palette.primary, 32), 20));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(game.title + ", " + game.runtimeLabel() + ". Tap to launch; hold for details.");

        int artHeight = Math.min(Ui.dp(this, 186), Math.round(cardWidth * 0.76f));
        card.addView(gameArt(game, Math.max(cardWidth, artHeight), true),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, artHeight));
        card.addView(Ui.spacer(this, 1, 12));
        TextView title = Ui.text(this, game.title, 15, palette.text, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title, Ui.matchWrap());
        card.addView(Ui.spacer(this, 1, 7));
        String plays = game.playCount == 1 ? "1 launch" : game.playCount + " launches";
        TextView metadata = Ui.text(this, game.runtimeLabel() + "  ·  " + plays, 11, palette.textMuted, false);
        metadata.setMaxLines(1);
        metadata.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(metadata, Ui.matchWrap());
        if (!game.folderEntry && preferences.isShortcutGenerated(game.id)) {
            card.addView(Ui.spacer(this, 1, 8));
            card.addView(Ui.text(this, generatedStatusLabel(game.id), 11, palette.primary, true));
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

    private View gameArt(Game game, int targetPixels, boolean wide) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.rounded(this, game.folderEntry ? palette.secondary : palette.surfaceRaised, wide ? 15 : 16));
        frame.setClipToOutline(true);

        ImageView image = new ImageView(this);
        image.setScaleType(wide ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_CROP);
        image.setImageDrawable(artLoader.joiPlayDrawable());
        image.setContentDescription(game.title + " artwork");
        String requestKey = game.id + ":" + String.valueOf(preferences.customIcon(game.id));
        image.setTag(requestKey);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!game.folderEntry) {
            boolean generated = preferences.isShortcutGenerated(game.id);
            if (generated) {
                TextView badge = Ui.text(this, "✓", 12, Color.WHITE, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.rounded(this, palette.primary, 12));
                badge.setContentDescription(generatedStatusLabel(game.id));
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        Ui.dp(this, 26), Ui.dp(this, 26), Gravity.TOP | Gravity.END);
                badgeParams.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
                frame.addView(badge, badgeParams);
            }
        }

        artExecutor.execute(() -> {
            Bitmap bitmap = artLoader.load(game, Math.max(Ui.dp(this, 96), targetPixels));
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (requestKey.equals(image.getTag())) image.setImageBitmap(bitmap);
            });
        });
        return frame;
    }

    private void showSettings() {
        selectScreen(Screen.SETTINGS);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 30));
        attachCenteredPage(scroll, page, 860);

        page.addView(Ui.text(this, "Settings", 27, palette.text, true));
        page.addView(Ui.spacer(this, 1, 4));
        page.addView(Ui.text(this, "Make shortcut creation feel like yours.", 13, palette.textMuted, false));
        page.addView(Ui.spacer(this, 1, 24));

        page.addView(sectionLabel("APPEARANCE"));
        page.addView(settingsGroup(Arrays.asList(
                settingRow("App theme", "Follow the device or choose a look", themeLabel(), () -> chooseTheme()),
                settingRow("Color", "Accent color used throughout the app", accentLabel(), () -> chooseAccentColor())
        )));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("SHORTCUTS"));
        ArrayList<View> shortcutRows = new ArrayList<>();
        shortcutRows.add(settingRow("File type", "Generated Shortcut Output", formatLabel(), () -> chooseFormat()));
        shortcutRows.add(settingRow("Output folder", outputPathLabel(), outputLabel(), () -> chooseOutputFolder(false, true)));
        if (preferences.outputTree() != null) {
            shortcutRows.add(settingRow("Forget output folder", "Return to choosing a destination when generating", "Clear", () -> {
                preferences.setOutputTree(null);
                Toast.makeText(this, "Default output folder cleared", Toast.LENGTH_SHORT).show();
                showSettings();
            }));
        }
        shortcutRows.add(settingRow("Tap behavior", "What happens when you tap a library game", tapLabel(), () -> chooseTapAction()));
        page.addView(settingsGroup(shortcutRows));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("IISU INTEGRATION"));
        ArrayList<View> iisuRows = new ArrayList<>();
        iisuRows.add(settingRow("iiSU folder", iisuPathLabel(), iisuSettingValue(), this::chooseIisuFolder));
        if (preferences.iisuTree() != null) {
            iisuRows.add(settingRow("Forget iiSU folder", "Remove access to the selected iiSU installation", "Clear", () -> {
                preferences.setIisuTree(null);
                iisuStatus = IisuSupportManager.Status.NOT_CONFIGURED;
                iisuStatusMessage = "Select the iiSU folder";
                Toast.makeText(this, "iiSU folder cleared", Toast.LENGTH_SHORT).show();
                showSettings();
            }));
        }
        page.addView(settingsGroup(iisuRows));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("LIBRARY"));
        ArrayList<View> libraryRows = new ArrayList<>();
        libraryRows.add(settingRow("Library layout", "Switch between compact rows and box art", viewModeLabel(), () -> chooseViewMode()));
        libraryRows.add(settingRow("Sort games", "Choose the order used in Library", sortLabel(), () -> chooseSortOrder()));
        libraryRows.add(settingRow("Custom game icons", "Upload or reset artwork for any game", "Manage  ›", this::showIconManager));
        libraryRows.add(folderEntriesSwitch());
        page.addView(settingsGroup(libraryRows));
        page.addView(Ui.spacer(this, 1, 20));

        page.addView(sectionLabel("ABOUT"));
        page.addView(settingsGroup(Arrays.asList(
                settingRow("Project page", "Source, releases, setup, and issues", "GitHub  ↗", this::openProject),
                settingRow("Privacy", "No network access, ads, or analytics", "Read", this::showPrivacy),
                settingRow("Version", "JoiPlay Shortcut Generator", getVersionName(), null)
        )));

        installContent(scroll, new FrameLayout.LayoutParams(
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
        heading.addView(gameArt(game, 64, false), new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
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
        if (!game.folderEntry) sheet.addView(metadataLine("Shortcut",
                preferences.isShortcutGenerated(game.id)
                        ? generatedFormatsLabel(game.id) + " generated ✓" : "Not generated"));
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

            TextView customIcon = Ui.pillButton(this, palette,
                    preferences.customIcon(game.id) == null ? "Choose custom icon" : "Change custom icon", false);
            customIcon.setOnClickListener(view -> {
                dialog.dismiss();
                chooseCustomIcon(game);
            });
            sheet.addView(customIcon, Ui.matchWrap());
            if (preferences.customIcon(game.id) != null) {
                sheet.addView(Ui.spacer(this, 1, 8));
                TextView resetIcon = Ui.pillButton(this, palette, "Use JoiPlay icon again", false);
                resetIcon.setOnClickListener(view -> {
                    preferences.setCustomIcon(game.id, null);
                    dialog.dismiss();
                    if (currentScreen == Screen.LIBRARY) renderLibrary();
                    Toast.makeText(this, "Custom icon removed", Toast.LENGTH_SHORT).show();
                });
                sheet.addView(resetIcon, Ui.matchWrap());
            }
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

    private void attachCenteredPage(ScrollView scroll, LinearLayout page, int maxWidthDp) {
        FrameLayout lane = new FrameLayout(this);
        int pageWidth = Math.min(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24),
                Ui.dp(this, maxWidthDp)
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

    private void chooseAccentColor() {
        AppPreferences.AccentColor[] values = AppPreferences.AccentColor.values();
        showChoice("App color", new String[]{"Purple", "Blue", "Pink", "Orange", "Teal"},
                preferences.accentColor().ordinal(), index -> {
                    preferences.setAccentColor(values[index]);
                    recreate();
                });
    }

    private void chooseFormat() {
        AppPreferences.ShortcutFormat[] values = AppPreferences.ShortcutFormat.values();
        showChoice("Shortcut file type", new String[]{".jp · Raw JoiPlay ID", ".joiplay · Raw JoiPlay ID"}, preferences.shortcutFormat().ordinal(), index -> {
            preferences.setShortcutFormat(values[index]);
            showSettings();
        });
    }

    private void chooseViewMode() {
        AppPreferences.ViewMode[] values = AppPreferences.ViewMode.values();
        showChoice("Library layout", new String[]{"List view", "Box view"}, preferences.viewMode().ordinal(), index -> {
            preferences.setViewMode(values[index]);
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

    private void chooseOutputFolder(boolean exportAfterSelection, boolean rememberSelection) {
        exportAllAfterFolderSelection = exportAfterSelection;
        rememberOutputFolderSelection = rememberSelection;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri existing = preferences.outputTree();
        if (existing != null) intent.putExtra("android.provider.extra.INITIAL_URI", existing);
        startActivityForResult(intent, REQUEST_OUTPUT_TREE);
    }

    private void chooseIisuFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri existing = preferences.iisuTree();
        if (existing != null) intent.putExtra("android.provider.extra.INITIAL_URI", existing);
        startActivityForResult(intent, REQUEST_IISU_TREE);
    }

    private void handleIisuAction() {
        if (preferences.iisuTree() == null) {
            chooseIisuFolder();
        } else if (iisuStatus == IisuSupportManager.Status.IMPORTED) {
            Toast.makeText(this, "JoiPlay support is already imported into iiSU", Toast.LENGTH_SHORT).show();
        } else {
            importIisuSupport();
        }
    }

    private void importIisuSupport() {
        Uri tree = preferences.iisuTree();
        if (tree == null || iisuWorking) return;
        iisuWorking = true;
        updateIisuActionButton();
        executor.execute(() -> {
            IisuSupportManager.Result result = iisuSupportManager.inject(tree);
            runOnUiThread(() -> {
                iisuWorking = false;
                iisuStatus = result.status;
                iisuStatusMessage = result.message;
                if (currentScreen == Screen.LIBRARY) renderLibrary(); else showSettings();
                Toast.makeText(this, result.message,
                        result.status == IisuSupportManager.Status.ERROR
                                ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void scanIisuSupportAfterSelection() {
        Uri tree = preferences.iisuTree();
        if (tree == null) return;
        iisuStatus = IisuSupportManager.Status.CHECKING;
        iisuWorking = true;
        if (currentScreen == Screen.LIBRARY) renderLibrary(); else showSettings();
        executor.execute(() -> {
            IisuSupportManager.Result result = iisuSupportManager.scan(tree);
            runOnUiThread(() -> {
                iisuWorking = false;
                iisuStatus = result.status;
                iisuStatusMessage = result.message;
                if (currentScreen == Screen.LIBRARY) renderLibrary(); else showSettings();
                if (result.status == IisuSupportManager.Status.ERROR) {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                }
            });
        });
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
                runOnUiThread(() -> {
                    preferences.markShortcutGenerated(game.id, preferences.shortcutFormat());
                    Toast.makeText(this, "Shortcut generated", Toast.LENGTH_SHORT).show();
                    if (currentScreen == Screen.LIBRARY) renderLibrary();
                });
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
            // Remember the selected folder so future launches and refreshes can
            // verify which generated files still exist.
            chooseOutputFolder(true, true);
            return;
        }
        exportAllToConfiguredFolder(games);
    }

    private void exportAllToConfiguredFolder(List<Game> games) {
        Uri tree = preferences.outputTree();
        if (tree == null) return;
        exportAllToFolder(tree, games);
    }

    private void exportAllToFolder(Uri tree, List<Game> games) {
        Toast.makeText(this, "Generating " + games.size() + " shortcuts…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            AppPreferences.ShortcutFormat format = preferences.shortcutFormat();
            ShortcutExporter.ScanResult existing = exporter.findExisting(tree, games);
            if (existing.successful) {
                preferences.replaceGeneratedShortcuts(existing.idsByFormat);
            }
            ArrayList<Game> missing = new ArrayList<>();
            for (Game game : games) {
                if (game.folderEntry || game.id.isEmpty()) continue;
                if (!existing.successful || !preferences.isShortcutGenerated(game.id, format)) {
                    missing.add(game);
                }
            }
            ShortcutExporter.ExportSummary summary = exporter.writeAll(tree, missing, format);
            runOnUiThread(() -> {
                preferences.markShortcutsGenerated(summary.writtenGameIds, format);
                String extension = "." + ShortcutFileFactory.extension(format);
                String message = missing.isEmpty()
                        ? "All " + extension + " shortcuts already exist"
                        : summary.written + " " + extension + " shortcuts generated";
                if (summary.failed > 0) message += " · " + summary.failed + " failed";
                Toast.makeText(this, message, summary.failed > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                if (currentScreen == Screen.LIBRARY) renderLibrary();
            });
        });
    }

    private void chooseCustomIcon(Game game) {
        pendingCustomIconGame = game;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CUSTOM_ICON);
    }

    private void showIconManager() {
        if (libraryResult == null || !libraryResult.isReady() || libraryResult.games.isEmpty()) {
            Toast.makeText(this, "Open Library and refresh your games first", Toast.LENGTH_LONG).show();
            showLibrary();
            return;
        }

        LinearLayout sheet = Ui.vertical(this);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 26));
        sheet.setBackground(Ui.rounded(this, palette.surface, 26));
        sheet.addView(Ui.text(this, "Custom game icons", 20, palette.text, true));
        sheet.addView(Ui.spacer(this, 1, 5));
        sheet.addView(Ui.text(this, "Choose a game, then select an image from your device.", 12, palette.textMuted, false));
        sheet.addView(Ui.spacer(this, 1, 14));

        ScrollView scroll = new ScrollView(this);
        LinearLayout choices = Ui.vertical(this);
        scroll.addView(choices, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxHeight = Math.min(getResources().getDisplayMetrics().heightPixels / 2, Ui.dp(this, 420));
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));
        Dialog dialog = bottomDialog(sheet);

        for (Game game : libraryResult.games) {
            if (game.folderEntry) continue;
            String status = preferences.customIcon(game.id) == null ? "JoiPlay artwork" : "Custom artwork ✓";
            LinearLayout row = Ui.vertical(this);
            row.setPadding(Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 12));
            row.addView(Ui.text(this, game.title, 14, palette.text, true));
            row.addView(Ui.spacer(this, 1, 3));
            row.addView(Ui.text(this, status, 11,
                    preferences.customIcon(game.id) == null ? palette.textMuted : palette.primary,
                    preferences.customIcon(game.id) != null));
            row.setBackground(Ui.ripple(this, palette.surface, Ui.withAlpha(palette.primary, 28), 14));
            row.setClickable(true);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                chooseCustomIcon(game);
            });
            choices.addView(row, Ui.matchWrap());
        }
        dialog.show();
        sizeBottomDialog(dialog);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingDocumentGame = null;
            pendingCustomIconGame = null;
            exportAllAfterFolderSelection = false;
            rememberOutputFolderSelection = false;
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_CREATE_DOCUMENT && pendingDocumentGame != null) {
            Game game = pendingDocumentGame;
            pendingDocumentGame = null;
            executor.execute(() -> {
                try {
                    exporter.writeDocument(uri, game, preferences.shortcutFormat());
                    runOnUiThread(() -> {
                        preferences.markShortcutGenerated(game.id, preferences.shortcutFormat());
                        Toast.makeText(this, "Shortcut generated", Toast.LENGTH_SHORT).show();
                        if (currentScreen == Screen.LIBRARY) renderLibrary();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(this, "Could not write the shortcut", Toast.LENGTH_LONG).show());
                }
            });
        } else if (requestCode == REQUEST_OUTPUT_TREE) {
            boolean shouldExport = exportAllAfterFolderSelection;
            boolean shouldRemember = rememberOutputFolderSelection;
            boolean shouldRestore = preferences.outputTree() == null
                    && !preferences.hasSavedPortableSettings();
            exportAllAfterFolderSelection = false;
            rememberOutputFolderSelection = false;
            if (shouldRemember) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {
                    Toast.makeText(this, "This folder cannot be remembered", Toast.LENGTH_LONG).show();
                    return;
                }
                restoreAndRememberOutputFolder(uri, shouldRestore, shouldExport);
            } else if (shouldExport && libraryResult != null && libraryResult.isReady()) {
                exportAllToFolder(uri, new ArrayList<>(libraryResult.games));
            }
        } else if (requestCode == REQUEST_IISU_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            } catch (SecurityException ignored) {
                Toast.makeText(this, "This iiSU folder cannot be remembered", Toast.LENGTH_LONG).show();
                return;
            }
            preferences.setIisuTree(uri);
            Toast.makeText(this, "iiSU folder selected", Toast.LENGTH_SHORT).show();
            scanIisuSupportAfterSelection();
        } else if (requestCode == REQUEST_CUSTOM_ICON && pendingCustomIconGame != null) {
            Game game = pendingCustomIconGame;
            pendingCustomIconGame = null;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The current grant remains usable for this session even if it cannot be persisted.
            }
            preferences.setCustomIcon(game.id, uri);
            Toast.makeText(this, "Custom icon saved for " + game.title, Toast.LENGTH_SHORT).show();
            if (currentScreen == Screen.LIBRARY) renderLibrary(); else showSettings();
        }
    }

    private void restoreAndRememberOutputFolder(Uri uri, boolean shouldRestore, boolean shouldExport) {
        List<Game> games = shouldExport && libraryResult != null && libraryResult.isReady()
                ? new ArrayList<>(libraryResult.games) : null;
        executor.execute(() -> {
            boolean restored = false;
            if (shouldRestore) {
                try {
                    restored = preferences.restorePortableSettings(settingsFileStore.read(uri));
                } catch (Exception ignored) {
                    // A missing or unreadable snapshot should not prevent folder selection.
                }
            }
            preferences.setOutputTree(uri);
            try {
                settingsFileStore.write(uri, preferences.portableSettings());
            } catch (Exception ignored) {
                // Android's backup service remains available if the provider rejects this file.
            }
            boolean restoredSettings = restored;
            runOnUiThread(() -> {
                if (!shouldExport) libraryResult = null;
                if (restoredSettings) {
                    rebuildCurrentScreen();
                    Toast.makeText(this, "Settings restored from the JoiPlay folder", Toast.LENGTH_SHORT).show();
                } else if (currentScreen == Screen.SETTINGS) {
                    showSettings();
                } else if (currentScreen == Screen.LIBRARY) {
                    renderLibrary();
                }
                if (games != null) exportAllToFolder(uri, games);
            });
        });
    }

    private void rebuildCurrentScreen() {
        Screen destination = currentScreen;
        palette = Palette.from(this, preferences.theme(), preferences.accentColor());
        setContentView(buildShell());
        configureSystemBars();
        if (destination == Screen.SETTINGS) showSettings(); else showLibrary();
    }

    private void scheduleSettingsBackup() {
        Uri tree = preferences == null ? null : preferences.outputTree();
        if (tree == null || settingsFileStore == null || executor == null || executor.isShutdown()) return;
        Map<String, String> snapshot = preferences.portableSettings();
        executor.execute(() -> {
            try {
                settingsFileStore.write(tree, snapshot);
            } catch (Exception ignored) {
                // Folder access can be revoked independently; cloud backup still applies.
            }
        });
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
        Toast.makeText(this, "Preparing home shortcut…", Toast.LENGTH_SHORT).show();
        artExecutor.execute(() -> {
            Bitmap bitmap = artLoader.load(game, 192);
            if (bitmap == null) bitmap = shortcutIcon(game);
            Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                Intent launch = new Intent(Intent.ACTION_VIEW);
                launch.setComponent(new ComponentName(JoiPlayRepository.JOIPLAY_PACKAGE, JoiPlayRepository.SHORTCUT_ACTIVITY));
                launch.putExtra("id", game.id);
                ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "joiplay-" + game.id)
                        .setShortLabel(game.title)
                        .setLongLabel("Launch " + game.title + " in JoiPlay")
                        .setIcon(Icon.createWithBitmap(finalBitmap))
                        .setIntent(launch)
                        .build();
                boolean requested = manager.requestPinShortcut(shortcut, null);
                Toast.makeText(this, requested ? "Pin request sent" : "Could not request the shortcut", Toast.LENGTH_SHORT).show();
            });
        });
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
                "JoiPlay Shortcut Generator has no Internet permission, analytics, ads, or accounts. It reads the sanitized game list exposed by the modified JoiPlay provider and writes shortcut files only to a folder or document you choose. When device backup is enabled, Android can back up and restore the app settings.",
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

    private String accentLabel() {
        String name = preferences.accentColor().name().toLowerCase(Locale.ROOT);
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "  ›";
    }

    private String formatLabel() {
        return "." + ShortcutFileFactory.extension(preferences.shortcutFormat()) + "  ›";
    }

    private String generatedStatusLabel(String gameId) {
        return "✓ Shortcut generated (" + generatedFormatsLabel(gameId) + ")";
    }

    private String generatedFormatsLabel(String gameId) {
        ArrayList<String> labels = new ArrayList<>();
        for (AppPreferences.ShortcutFormat format : AppPreferences.ShortcutFormat.values()) {
            if (preferences.isShortcutGenerated(gameId, format)) {
                labels.add("." + ShortcutFileFactory.extension(format));
            }
        }
        return TextUtils.join(", ", labels);
    }

    private String sortLabel() {
        if (preferences.sortOrder() == AppPreferences.SortOrder.RECENTLY_ADDED) return "Recent  ›";
        if (preferences.sortOrder() == AppPreferences.SortOrder.MOST_PLAYED) return "Most played  ›";
        return "Title  ›";
    }

    private String viewModeLabel() {
        return preferences.viewMode() == AppPreferences.ViewMode.GRID ? "Box  ›" : "List  ›";
    }

    private String tapLabel() {
        return preferences.tapAction() == AppPreferences.TapAction.LAUNCH ? "Launch  ›" : "Details  ›";
    }

    private String outputLabel() {
        return preferences.outputTree() == null ? "Optional  ›" : "Change  ›";
    }

    private String iisuActionLabel() {
        if (preferences.iisuTree() == null) return "Select iiSU folder";
        if (iisuWorking || iisuStatus == IisuSupportManager.Status.CHECKING) {
            return "Checking iiSU support…";
        }
        if (iisuStatus == IisuSupportManager.Status.IMPORTED) {
            return "✓ JoiPlay support imported";
        }
        return "Import JoiPlay Support into iiSU";
    }

    private void updateIisuActionButton() {
        if (iisuActionButton == null) return;
        iisuActionButton.setText(iisuActionLabel());
        iisuActionButton.setEnabled(!iisuWorking && iisuStatus != IisuSupportManager.Status.CHECKING);
        iisuActionButton.setAlpha(iisuActionButton.isEnabled() ? 1f : 0.72f);
        iisuActionButton.setContentDescription(iisuActionLabel());
    }

    private String iisuSettingValue() {
        if (preferences.iisuTree() == null) return "Select  ›";
        if (iisuWorking || iisuStatus == IisuSupportManager.Status.CHECKING) return "Checking…";
        if (iisuStatus == IisuSupportManager.Status.IMPORTED) return "Imported  ✓";
        if (iisuStatus == IisuSupportManager.Status.ERROR) return "Needs attention";
        return "Change  ›";
    }

    private String outputPathLabel() {
        return treePathLabel(preferences.outputTree(), "No default folder — you can still generate files");
    }

    private String iisuPathLabel() {
        if (preferences.iisuTree() != null && iisuStatus == IisuSupportManager.Status.ERROR) {
            return iisuStatusMessage;
        }
        return treePathLabel(preferences.iisuTree(), "Select the iiSU folder that contains iiSULauncher");
    }

    private String treePathLabel(Uri tree, String fallback) {
        if (tree == null) return fallback;
        try {
            String documentId = DocumentsContract.getTreeDocumentId(tree);
            int separator = documentId.indexOf(':');
            if (separator >= 0) {
                String volume = documentId.substring(0, separator);
                String path = documentId.substring(separator + 1).replace(':', '/');
                if ("primary".equalsIgnoreCase(volume)) return "/storage/emulated/0/" + path;
                return "/storage/" + volume + "/" + path;
            }
            return Uri.decode(documentId);
        } catch (RuntimeException ignored) {
            return Uri.decode(tree.toString());
        }
    }

    private void installContent(View view, FrameLayout.LayoutParams params) {
        View outgoing = content.getChildCount() == 0 ? null : content.getChildAt(content.getChildCount() - 1);
        content.addView(view, params);
        int direction = nextTransitionDirection;
        nextTransitionDirection = 0;
        view.setAlpha(0f);
        view.setTranslationX(direction == 0 ? 0f : Ui.dp(this, 26) * direction);
        view.setTranslationY(direction == 0 ? Ui.dp(this, 8) : 0f);

        AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
        view.animate().alpha(1f).translationX(0f).translationY(0f)
                .setDuration(direction == 0 ? 220 : 290)
                .setInterpolator(interpolator)
                .start();
        if (outgoing != null && outgoing != view) {
            outgoing.animate().cancel();
            outgoing.animate()
                    .alpha(0f)
                    .translationX(direction == 0 ? 0f : Ui.dp(this, -18) * direction)
                    .setDuration(direction == 0 ? 170 : 240)
                    .setInterpolator(interpolator)
                    .withEndAction(() -> content.removeView(outgoing))
                    .start();
        }
    }

    private void animateRefresh() {
        if (refreshButton == null || refreshIcon == null) return;
        if (refreshAnimator != null && refreshAnimator.isRunning()
                && refreshAnimator.getTarget() == refreshIcon) return;
        if (refreshAnimator != null) refreshAnimator.cancel();
        refreshButton.setEnabled(false);
        refreshAnimator = ObjectAnimator.ofFloat(refreshIcon, View.ROTATION, 0f, 360f);
        refreshAnimator.setDuration(720);
        refreshAnimator.setRepeatCount(ValueAnimator.INFINITE);
        refreshAnimator.setInterpolator(new LinearInterpolator());
        refreshAnimator.start();
    }

    private void stopRefreshAnimation() {
        if (refreshAnimator != null) {
            refreshAnimator.cancel();
            refreshAnimator = null;
        }
        if (refreshIcon != null) refreshIcon.setRotation(0f);
        if (refreshButton != null) refreshButton.setEnabled(true);
    }

    private static int blend(int base, int overlay, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * inverse + Color.red(overlay) * amount),
                Math.round(Color.green(base) * inverse + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount));
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.2.0";
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
