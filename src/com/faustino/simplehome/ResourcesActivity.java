package com.faustino.simplehome;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/** CPU, RAM and storage meters. Only polls while this screen is open. */
public class ResourcesActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Meter cpuMeter;
    private Meter ramMeter;
    private Meter diskMeter;
    private TextView uptime;
    private long lastCpuTotal = -1;
    private long lastCpuIdle;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(Ui.background());
        column.setPadding(dp(this, 160), dp(this, 48), dp(this, 160), dp(this, 40));

        TextView title = Ui.label(this, t("Resources", "Recursos"), 34, Color.WHITE);
        title.setGravity(Gravity.START);
        column.addView(title);
        column.addView(new View(this), new LinearLayout.LayoutParams(0, dp(this, 36)));

        cpuMeter = new Meter("CPU");
        ramMeter = new Meter(t("Memory (RAM)", "Memoria (RAM)"));
        diskMeter = new Meter(t("Storage", "Almacenamiento"));
        for (Meter m : new Meter[]{cpuMeter, ramMeter, diskMeter}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(this, 30));
            column.addView(m.view, lp);
        }

        uptime = Ui.label(this, "", 16, Ui.TEXT_DIM);
        uptime.setGravity(Gravity.START);
        column.addView(uptime);
        setContentView(column);
    }

    @Override
    protected void onStart() {
        super.onStart();
        lastCpuTotal = -1;
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }

    /** Name and value on one line, a thick bar below. */
    private final class Meter {
        final LinearLayout view;
        final ClipDrawable fill;
        final TextView value;

        Meter(String name) {
            Activity a = ResourcesActivity.this;
            view = new LinearLayout(a);
            view.setOrientation(LinearLayout.VERTICAL);

            LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = Ui.label(a, name, 20, Color.WHITE);
            label.setGravity(Gravity.START);
            top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            value = Ui.label(a, "–", 20, Ui.TEXT_DIM);
            top.addView(value);
            view.addView(top);

            GradientDrawable track = new GradientDrawable();
            track.setColor(0xFF2C3544);
            track.setCornerRadius(dp(a, 6));
            GradientDrawable bar = new GradientDrawable();
            bar.setColor(Color.WHITE);
            bar.setCornerRadius(dp(a, 6));
            fill = new ClipDrawable(bar, Gravity.START, ClipDrawable.HORIZONTAL);
            View meter = new View(a);
            meter.setBackground(new LayerDrawable(new Drawable[]{track, fill}));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 12));
            lp.setMargins(0, dp(a, 12), 0, 0);
            view.addView(meter, lp);
        }

        void set(double fraction, String text) {
            fill.setLevel((int) Math.round(Math.max(0, Math.min(1, fraction)) * 10000));
            fill.setTint(fraction > 0.85 ? 0xFFFF7A6B : Ui.ACCENT);
            value.setText(text);
        }
    }

    private void update() {
        long[] cpu = readCpu();
        if (cpu == null) {
            cpuMeter.view.setVisibility(View.GONE);
        } else {
            if (lastCpuTotal >= 0 && cpu[0] > lastCpuTotal) {
                double busy = 1 - (double) (cpu[1] - lastCpuIdle) / (cpu[0] - lastCpuTotal);
                cpuMeter.set(busy, Math.round(busy * 100) + " %  ·  "
                        + Runtime.getRuntime().availableProcessors() + t(" cores", " núcleos"));
            }
            lastCpuTotal = cpu[0];
            lastCpuIdle = cpu[1];
        }

        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(mem);
        long usedRam = mem.totalMem - mem.availMem;
        ramMeter.set((double) usedRam / mem.totalMem,
                mb(usedRam) + " / " + mb(mem.totalMem) + " MB");

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long total = fs.getTotalBytes();
        long used = total - fs.getAvailableBytes();
        diskMeter.set((double) used / total, gb(used) + " / " + gb(total) + " GB");

        uptime.setText(t("On for ", "Encendido hace ")
                + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
    }

    /** {total, idle} jiffies from /proc/stat, or null when the system hides it. */
    private static long[] readCpu() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String[] parts = reader.readLine().trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < parts.length; i++) total += Long.parseLong(parts[i]);
            long idle = Long.parseLong(parts[4]) + Long.parseLong(parts[5]);
            return new long[]{total, idle};
        } catch (Exception e) {
            return null;
        }
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    private static String gb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f", bytes / 1e9);
    }
}
