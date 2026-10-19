package com.faustino.simplehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/** Minimal TV home screen: clock, editable main apps, utilities, status buttons. */
public class HomeActivity extends Activity {

    /** Usable row width in dp: 1280 screen minus side padding. */
    private static final int ROW_WIDTH_DP = 1168;

    private ImageView networkIcon;
    private TextView badge;
    private View firstTile;
    private LinearLayout mainRow;
    private FrameLayout root;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final NotificationService.Listener notificationListener =
            () -> runOnUiThread(this::updateBadge);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivity = getSystemService(ConnectivityManager.class);
        setContentView(buildUi());
    }

    @Override
    protected void onStart() {
        super.onStart();
        root.setBackground(Ui.background(this));
        fillMainRow(null);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                runOnUiThread(() -> showNetwork(caps));
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> showNetwork(null));
            }
        };
        showNetwork(null);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        NotificationService.addListener(notificationListener);
        updateBadge();
    }

    @Override
    protected void onStop() {
        connectivity.unregisterNetworkCallback(networkCallback);
        NotificationService.removeListener(notificationListener);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home jumps back to the first app.
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }

    // ---- UI ----

    private View buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(this, 56), dp(this, 36), dp(this, 56), dp(this, 40));
        column.setClipChildren(false);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        column.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));
        column.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        mainRow = row();
        column.addView(mainRow, new LinearLayout.LayoutParams(-1, -2));

        column.addView(new View(this), new LinearLayout.LayoutParams(0, dp(this, 36)));

        LinearLayout utilRow = row();
        utilRow.addView(utilTile(R.drawable.ic_apps, t("All apps", "Todas las apps"),
                new Intent(this, AppsActivity.class)), spaced(150, 116, 10));
        utilRow.addView(utilTile(R.drawable.ic_settings, t("Settings", "Ajustes"),
                Ui.explicit("com.android.settings", "com.android.settings.Settings"),
                new Intent(Settings.ACTION_SETTINGS)), spaced(150, 116, 10));
        utilRow.addView(utilTile(R.drawable.ic_folder, t("Files", "Archivos"),
                Ui.explicit("com.cultraview.filemanager", "com.cultraview.filemanager.FileManager")),
                spaced(150, 116, 10));
        utilRow.addView(utilTile(R.drawable.ic_store, "Play Store",
                getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending")),
                spaced(150, 116, 10));
        utilRow.addView(utilTile(R.drawable.ic_input, t("Sources", "Fuentes"),
                Ui.explicit("com.cultraview.setting", "com.cultraview.setting.SourceActivity")),
                spaced(150, 116, 10));
        column.addView(utilRow, new LinearLayout.LayoutParams(-1, -2));

        column.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1.2f));
        return root;
    }

    /**
     * Rebuilds the favorite tiles plus the trailing "+" tile. Called on every start so
     * installs, removals and edits show up. focusPkg, when set, keeps focus on that app.
     */
    private void fillMainRow(String focusPkg) {
        boolean hadFocus = mainRow.hasFocus() || firstTile == null || focusPkg != null;
        mainRow.removeAllViews();
        firstTile = null;
        View focusTarget = null;

        List<Ui.App> apps = new ArrayList<>();
        List<Ui.App> all = Ui.launchableApps(this);
        for (String pkg : Favorites.load(this)) {
            for (Ui.App app : all) if (app.pkg.equals(pkg)) apps.add(app);
        }

        // Tiles shrink as more apps are pinned so the row always fits.
        int addWidth = 110;
        int margin = 14;
        int width = Math.min(230, (ROW_WIDTH_DP - addWidth - 2 * margin) / Math.max(1, apps.size()) - 2 * margin);
        int height = Math.min(176, Math.round(width * 0.77f));
        int icon = Math.min(88, Math.round(width * 0.38f));

        for (Ui.App app : apps) {
            LinearLayout tile = Ui.iconTile(this, Ui.sharpIcon(this, app.info), app.label, icon,
                    width < 190 ? 17 : 20);
            tile.setOnClickListener(v -> Ui.launch(this, app.intent));
            tile.setOnLongClickListener(v -> {
                editFavorite(app);
                return true;
            });
            if (firstTile == null) firstTile = tile;
            if (app.pkg.equals(focusPkg)) focusTarget = tile;
            mainRow.addView(tile, spaced(width, height, margin));
        }

        LinearLayout add = Ui.iconTile(this, getDrawable(R.drawable.ic_add), t("Add", "Añadir"), 36, 15);
        add.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)
                .putExtra(AppsActivity.EXTRA_PICK, true)));
        mainRow.addView(add, spaced(addWidth, height, margin));
        if (firstTile == null) firstTile = add;

        View target = focusTarget != null ? focusTarget : firstTile;
        if (hadFocus) target.post(target::requestFocus);
    }

    /** Long-press menu on a main tile: reorder or unpin. */
    private void editFavorite(Ui.App app) {
        List<String> favorites = Favorites.load(this);
        int index = favorites.indexOf(app.pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (index > 0) {
            labels.add(t("Move left", "Mover a la izquierda"));
            actions.add(() -> Favorites.move(this, app.pkg, -1));
        }
        if (index < favorites.size() - 1) {
            labels.add(t("Move right", "Mover a la derecha"));
            actions.add(() -> Favorites.move(this, app.pkg, +1));
        }
        labels.add(t("Remove from home", "Quitar del inicio"));
        actions.add(() -> Favorites.remove(this, app.pkg));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                    fillMainRow(Favorites.contains(this, app.pkg) ? app.pkg : null);
                })
                .show();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setShadowLayer(dp(this, 6), 0, dp(this, 2), 0x99000000);
        clock.addView(time);
        TextClock date = new TextClock(this);
        String datePattern = Ui.SPANISH ? "EEEE, d 'de' MMMM" : "EEEE, MMMM d";
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        date.setTextColor(Ui.TEXT_DIM);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        date.setShadowLayer(dp(this, 4), 0, dp(this, 1), 0x99000000);
        clock.addView(date);
        bar.addView(clock);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        ImageView resources = iconButton(R.drawable.ic_speed,
                v -> startActivity(new Intent(this, ResourcesActivity.class)));
        networkIcon = iconButton(R.drawable.ic_wifi, v -> openNetworkSettings());
        ImageView search = iconButton(R.drawable.ic_search,
                v -> startActivity(new Intent(this, SearchActivity.class)));
        bar.addView(resources, spaced(56, 56, 8));
        bar.addView(buildBell(), spaced(56, 56, 8));
        bar.addView(networkIcon, spaced(56, 56, 8));
        bar.addView(search, spaced(56, 56, 8));
        return bar;
    }

    /** Bell with a count badge; this TV has no notification shade of its own. */
    private View buildBell() {
        FrameLayout bell = new FrameLayout(this);
        Ui.styleFocusable(this, bell, 28);
        bell.setClipToOutline(false);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_bell);
        int pad = dp(this, 14);
        icon.setPadding(pad, pad, pad, pad);
        bell.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        badge = Ui.label(this, "", 12, Color.WHITE);
        GradientDrawable dot = new GradientDrawable();
        dot.setColor(0xFFE5484D);
        dot.setCornerRadius(dp(this, 10));
        badge.setBackground(dot);
        badge.setMinWidth(dp(this, 20));
        badge.setPadding(dp(this, 5), 0, dp(this, 5), 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(this, 20), Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(this, 6), dp(this, 6), 0);
        bell.addView(badge, lp);

        bell.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        return bell;
    }

    private void updateBadge() {
        int count = NotificationService.visible().size();
        badge.setText(count > 9 ? "9+" : String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private View utilTile(int iconRes, String text, Intent... intents) {
        LinearLayout tile = Ui.iconTile(this, getDrawable(iconRes), text, 40, 17);
        tile.setOnClickListener(v -> Ui.launch(this, intents));
        return tile;
    }

    private ImageView iconButton(int iconRes, View.OnClickListener click) {
        ImageView button = new ImageView(this);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(this, 14);
        button.setPadding(pad, pad, pad, pad);
        Ui.styleFocusable(this, button, 28);
        button.setOnClickListener(click);
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private LinearLayout.LayoutParams spaced(int wDp, int hDp, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(this, wDp), dp(this, hDp));
        lp.setMargins(dp(this, marginDp), 0, dp(this, marginDp), 0);
        return lp;
    }

    // ---- Network ----

    private void showNetwork(NetworkCapabilities caps) {
        boolean ethernet = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        networkIcon.setImageResource(ethernet ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        networkIcon.setImageAlpha(caps != null ? 255 : 90);
    }

    private void openNetworkSettings() {
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            Ui.launch(this, Ui.explicit("com.cultraview.setting", "com.cultraview.setting.NetworkActivity"),
                    new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } else {
            Ui.launch(this, Ui.explicit("com.cultraview.setting", "com.cultraview.setting.WifiActivity"),
                    new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }
}
