package com.faustino.simplehome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/**
 * Remote-friendly entry point to Android settings. The phone-style Settings home page can't be
 * navigated with the D-pad on this TV, but its individual pages can, so this grid opens them directly.
 */
public class SettingsMenuActivity extends Activity {

    private static final int COLUMNS = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(Ui.background(this));
        column.setPadding(dp(this, 40), dp(this, 36), dp(this, 40), 0);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        TextView title = Ui.label(this, t("Settings", "Ajustes"), 34, Color.WHITE);
        title.setGravity(Gravity.START);
        column.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        // Room around the grid so the focused tile's 1.08 zoom and ring aren't cut at the edges.
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(this, 16), dp(this, 22), dp(this, 16), dp(this, 16));
        column.addView(grid, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) grid.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        add(grid, R.drawable.ic_tv, t("TV picture & sound", "Imagen y sonido del TV"),
                Ui.explicit("com.cultraview.setting", "com.cultraview.setting.SettingActivity"));
        add(grid, R.drawable.ic_wifi, t("Network & internet", "Redes e Internet"),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        add(grid, R.drawable.ic_apps, t("Apps", "Aplicaciones"),
                new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        add(grid, R.drawable.ic_bell, t("Notifications", "Notificaciones"),
                new Intent("android.settings.NOTIFICATION_SETTINGS"));
        add(grid, R.drawable.ic_storage, t("Storage", "Almacenamiento"),
                new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        add(grid, R.drawable.ic_volume, t("Sound", "Sonido"),
                new Intent(Settings.ACTION_SOUND_SETTINGS));
        add(grid, R.drawable.ic_brightness, t("Display", "Pantalla"),
                new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        add(grid, R.drawable.ic_clock, t("Date & time", "Fecha y hora"),
                new Intent(Settings.ACTION_DATE_SETTINGS));
        add(grid, R.drawable.ic_language, t("Language", "Idioma"),
                new Intent(Settings.ACTION_LOCALE_SETTINGS));
        add(grid, R.drawable.ic_accessibility, t("Accessibility", "Accesibilidad"),
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        add(grid, R.drawable.ic_location, t("Location", "Ubicación"),
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        add(grid, R.drawable.ic_account, t("Accounts", "Cuentas"),
                new Intent(Settings.ACTION_SYNC_SETTINGS));
        add(grid, R.drawable.ic_settings, t("System", "Sistema"),
                Ui.explicit("com.android.settings", "com.android.settings.Settings$SystemDashboardActivity"));
        add(grid, R.drawable.ic_info, t("About", "Información"),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        add(grid, R.drawable.ic_more, t("All settings", "Todos los ajustes"),
                Ui.explicit("com.android.settings", "com.android.settings.Settings"));

        setContentView(column);
        grid.getChildAt(0).requestFocus();
    }

    private void add(GridLayout grid, int iconRes, String text, Intent intent) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(this, 18), 0, dp(this, 12), 0);
        Ui.styleFocusable(this, tile, 16);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(this, 28), dp(this, 28)));
        TextView label = Ui.label(this, text, 17, Color.WHITE);
        label.setGravity(Gravity.START);
        label.setPadding(dp(this, 14), 0, 0, 0);
        tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        tile.setOnClickListener(v -> Ui.launch(this, intent));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(this, 268);
        lp.height = dp(this, 92);
        lp.setMargins(dp(this, 8), dp(this, 8), dp(this, 8), dp(this, 8));
        grid.addView(tile, lp);
    }
}
