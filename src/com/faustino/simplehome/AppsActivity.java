package com.faustino.simplehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/** Grid of every launchable app. */
public class AppsActivity extends Activity {

    /** When true, picking an app pins it to the home screen instead of opening it. */
    static final String EXTRA_PICK = "pick";

    private static final int COLUMNS = 6;

    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(Ui.background(this));
        column.setPadding(dp(this, 56), dp(this, 36), dp(this, 56), 0);

        boolean pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        TextView title = Ui.label(this, pick ? t("Add to home", "Añadir al inicio")
                : t("All apps", "Todas las apps"), 34, Color.WHITE);
        title.setGravity(android.view.Gravity.START);
        column.addView(title);
        TextView hint = Ui.label(this, pick ? t("Choose an app", "Elige una app")
                : t("Hold OK on an app to add it to or remove it from home",
                "Mantén OK sobre una app para añadirla o quitarla del inicio"), 15, Ui.TEXT_DIM);
        hint.setGravity(android.view.Gravity.START);
        column.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        int pad = dp(this, 18);
        scroll.setPadding(0, pad, 0, pad);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        scroll.addView(grid);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(column);
    }

    @Override
    protected void onStart() {
        super.onStart();
        grid.removeAllViews();
        List<Ui.App> apps = Ui.launchableApps(this);
        boolean pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        for (Ui.App app : apps) {
            if (pick && Favorites.contains(this, app.pkg)) continue;
            LinearLayout tile = Ui.iconTile(this, Ui.sharpIcon(this, app.info), app.label, 64, 16);
            if (pick) {
                tile.setOnClickListener(v -> {
                    Favorites.add(this, app.pkg);
                    finish();
                });
            } else {
                tile.setOnClickListener(v -> Ui.launch(this, app.intent));
                tile.setOnLongClickListener(v -> {
                    togglePin(app);
                    return true;
                });
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(this, 168);
            lp.height = dp(this, 140);
            lp.setMargins(dp(this, 10), dp(this, 10), dp(this, 10), dp(this, 10));
            grid.addView(tile, lp);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void togglePin(Ui.App app) {
        boolean pinned = Favorites.contains(this, app.pkg);
        String action = pinned ? t("Remove from home", "Quitar del inicio") : t("Add to home", "Añadir al inicio");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(new String[]{action}, (dialog, which) -> {
                    if (pinned) {
                        Favorites.remove(this, app.pkg);
                    } else {
                        Favorites.add(this, app.pkg);
                    }
                    Toast.makeText(this, pinned ? t("Removed from home", "Quitada del inicio")
                            : t("Added to home", "Añadida al inicio"), Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
