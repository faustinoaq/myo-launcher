package com.faustino.simplehome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared look and launching helpers for the launcher's screens. */
final class Ui {

    static final int BG_TOP = 0xFF10141B;
    static final int BG_BOTTOM = 0xFF1B2230;
    static final int TILE = 0xC0232B38;
    static final int TILE_FOCUSED = 0xE834404F;
    static final int TEXT_DIM = 0xFFB4BDC9;
    static final int ACCENT = 0xFF5B9BFF;

    /** Developer tools that shouldn't show up as apps. */
    private static final java.util.Set<String> HIDDEN = java.util.Set.of("com.android.traceur");

    static final boolean SPANISH = "es".equals(Locale.getDefault().getLanguage());

    private Ui() {
    }

    static String t(String en, String es) {
        return SPANISH ? es : en;
    }

    static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    private static Bitmap wallpaper;
    private static long wallpaperStamp;

    /**
     * Screen background: background.jpg from the app's external files dir when present
     * (pushed over adb, kept out of the repo), otherwise a dark gradient.
     */
    static Drawable background(Activity a) {
        File file = new File(a.getExternalFilesDir(null), "background.jpg");
        if (file.isFile()) {
            if (wallpaper == null || wallpaperStamp != file.lastModified()) {
                wallpaper = BitmapFactory.decodeFile(file.getPath());
                wallpaperStamp = file.lastModified();
            }
            if (wallpaper != null) {
                BitmapDrawable d = new BitmapDrawable(a.getResources(), wallpaper);
                d.setGravity(Gravity.FILL);
                return d;
            }
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
    }

    /** Rounded tile that lifts and gets a white ring when focused. */
    static void styleFocusable(Activity a, View view, int radiusDp) {
        int radius = dp(a, radiusDp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(TILE);
        bg.setCornerRadius(radius);
        view.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        view.setForeground(ring);
        view.setClipToOutline(true);
        view.setFocusable(true);
        view.setClickable(true);
        View.OnFocusChangeListener highlight = (v, on) -> {
            bg.setColor(on ? TILE_FOCUSED : TILE);
            ring.setStroke(on ? dp(a, 3) : 0, Color.WHITE);
            v.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f)
                    .translationZ(on ? dp(a, 8) : 0).setDuration(140).start();
        };
        view.setOnFocusChangeListener(highlight);
        // The remote also works as an air mouse, which hides focus; highlight under the pointer too.
        view.setOnHoverListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_HOVER_ENTER) {
                v.getOnFocusChangeListener().onFocusChange(v, true);
            } else if (action == MotionEvent.ACTION_HOVER_EXIT && !v.isFocused()) {
                v.getOnFocusChangeListener().onFocusChange(v, false);
            }
            return false;
        });
    }

    static TextView label(Activity a, CharSequence text, int sp, int color) {
        TextView view = new TextView(a);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setShadowLayer(dp(a, 3), 0, dp(a, 1), 0x99000000);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    /** Vertical tile: icon on top, label below. */
    static LinearLayout iconTile(Activity a, Drawable icon, CharSequence text, int iconDp, int textSp) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        styleFocusable(a, tile, 18);
        ImageView image = new ImageView(a);
        image.setImageDrawable(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(image, new LinearLayout.LayoutParams(dp(a, iconDp), dp(a, iconDp)));
        TextView caption = label(a, text, textSp, Color.WHITE);
        caption.setPadding(0, dp(a, 10), 0, 0);
        tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    // ---- Apps ----

    static final class App {
        final String pkg;
        final CharSequence label;
        final Intent intent;
        final ActivityInfo info;

        App(String pkg, CharSequence label, Intent intent, ActivityInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.intent = intent;
            this.info = info;
        }
    }

    /** Every launchable app, TV entry point preferred, sorted by name. */
    static List<App> launchableApps(Activity a) {
        PackageManager pm = a.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                ActivityInfo info = ri.activityInfo;
                if (byPkg.containsKey(info.packageName) || info.packageName.equals(a.getPackageName())) continue;
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setComponent(new ComponentName(info.packageName, info.name));
                if (HIDDEN.contains(info.packageName)) continue;
                CharSequence label = info.packageName.equals("com.cultraview.setting")
                        ? t("TV settings", "Ajustes de TV") : ri.loadLabel(pm);
                byPkg.put(info.packageName, new App(info.packageName, label, intent, info));
            }
        }
        List<App> apps = new ArrayList<>(byPkg.values());
        Collator collator = Collator.getInstance();
        apps.sort((x, y) -> collator.compare(x.label.toString(), y.label.toString()));
        return apps;
    }

    static App findApp(Activity a, String pkg) {
        for (App app : launchableApps(a)) if (app.pkg.equals(pkg)) return app;
        return null;
    }

    /** The app's icon at the highest density it ships, so it stays sharp when drawn large. */
    static Drawable sharpIcon(Activity a, ActivityInfo info) {
        PackageManager pm = a.getPackageManager();
        int res = info.getIconResource();
        if (res != 0) {
            try {
                Resources r = pm.getResourcesForApplication(info.applicationInfo);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            } catch (Exception ignored) {
            }
        }
        return info.loadIcon(pm);
    }

    // ---- Launching ----

    static void launch(Activity a, Intent... candidates) {
        for (Intent intent : candidates) {
            if (intent == null) continue;
            try {
                a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(a, t("Not available", "No disponible"), Toast.LENGTH_SHORT).show();
    }

    static Intent explicit(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls));
    }
}
