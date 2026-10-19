package com.faustino.simplehome;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/** Notification list: OK opens one, the button clears them all. */
public class NotificationsActivity extends Activity implements NotificationService.Listener {

    private LinearLayout list;
    private TextView clearAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(Ui.background(this));
        column.setPadding(dp(this, 108), dp(this, 36), dp(this, 108), 0);
        column.setClipChildren(false);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        // Inset so the focused button's zoom and ring aren't cut at the edges.
        header.setPadding(dp(this, 12), 0, dp(this, 12), 0);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        TextView title = Ui.label(this, t("Notifications", "Notificaciones"), 34, Color.WHITE);
        header.addView(title);
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        clearAll = Ui.label(this, t("Clear all", "Borrar todo"), 17, Color.WHITE);
        clearAll.setPadding(dp(this, 22), dp(this, 10), dp(this, 22), dp(this, 10));
        Ui.styleFocusable(this, clearAll, 22);
        clearAll.setOnClickListener(v -> NotificationService.dismissAll());
        header.addView(clearAll);
        column.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(this, 16), 0, dp(this, 24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        // Side inset inside the list (which the scroll view clips to) so a focused row's zoom and ring fit.
        list.setClipToPadding(false);
        list.setPadding(dp(this, 12), 0, dp(this, 12), 0);
        scroll.addView(list);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(column);
    }

    @Override
    protected void onStart() {
        super.onStart();
        NotificationService.addListener(this);
        render();
    }

    @Override
    protected void onStop() {
        NotificationService.removeListener(this);
        super.onStop();
    }

    @Override
    public void onNotificationsChanged() {
        runOnUiThread(this::render);
    }

    private void render() {
        list.removeAllViews();
        List<StatusBarNotification> items = NotificationService.visible();
        clearAll.setVisibility(items.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        if (items.isEmpty()) {
            TextView empty = Ui.label(this, t("No notifications", "No hay notificaciones"), 20, Ui.TEXT_DIM);
            empty.setPadding(0, dp(this, 80), 0, 0);
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (StatusBarNotification sbn : items) list.addView(row(sbn), rowParams());
        list.getChildAt(0).requestFocus();
    }

    private LinearLayout row(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence app = sbn.getPackageName();
        Drawable icon = null;
        try {
            app = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(sbn.getPackageName(), 0));
            icon = getPackageManager().getApplicationIcon(sbn.getPackageName());
        } catch (Exception ignored) {
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(this, 18), dp(this, 12), dp(this, 18), dp(this, 12));
        Ui.styleFocusable(this, row, 14);
        // Rows are wide, so a gentler lift than the home tiles.
        android.view.View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, focused) -> {
            base.onFocusChange(v, focused);
            v.animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(140).start();
        });

        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        row.addView(image, new LinearLayout.LayoutParams(dp(this, 40), dp(this, 40)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(this, 18), 0, 0, 0);
        String when = DateUtils.getRelativeTimeSpanString(sbn.getPostTime()).toString();
        TextView meta = Ui.label(this, app + " · " + when, 14, Ui.TEXT_DIM);
        meta.setGravity(Gravity.START);
        body.addView(meta);
        TextView main = Ui.label(this, title != null ? title : app, 19, Color.WHITE);
        main.setGravity(Gravity.START);
        body.addView(main);
        if (text != null) {
            TextView sub = Ui.label(this, text, 15, Ui.TEXT_DIM);
            sub.setGravity(Gravity.START);
            sub.setSingleLine(false);
            sub.setMaxLines(2);
            body.addView(sub);
        }
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1f));

        row.setOnClickListener(v -> open(sbn));
        return row;
    }

    private void open(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n.contentIntent != null) {
            try {
                n.contentIntent.send();
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
        if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0 || n.contentIntent == null) {
            NotificationService.dismiss(sbn);
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(this, 5), 0, dp(this, 5));
        return lp;
    }
}
