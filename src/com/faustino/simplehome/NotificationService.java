package com.faustino.simplehome;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * This TV has no SystemUI, so nothing shows notifications; the launcher collects them itself.
 * Granted with: cmd notification allow_listener com.faustino.simplehome/.NotificationService
 */
public class NotificationService extends NotificationListenerService {

    interface Listener {
        void onNotificationsChanged();
    }

    private static NotificationService instance;
    private static final List<Listener> listeners = new ArrayList<>();

    static void addListener(Listener l) {
        listeners.add(l);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Notifications worth showing: no ongoing ones (downloads, media sessions) and no group summaries. */
    static List<StatusBarNotification> visible() {
        List<StatusBarNotification> out = new ArrayList<>();
        NotificationService s = instance;
        if (s == null) return out;
        StatusBarNotification[] active;
        try {
            active = s.getActiveNotifications();
        } catch (Exception e) {
            return out;
        }
        if (active == null) return out;
        for (StatusBarNotification sbn : active) {
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            out.add(sbn);
        }
        out.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    static void dismiss(StatusBarNotification sbn) {
        NotificationService s = instance;
        if (s != null) s.cancelNotification(sbn.getKey());
    }

    static void dismissAll() {
        NotificationService s = instance;
        if (s != null) s.cancelAllNotifications();
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        notifyChanged();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        notifyChanged();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        notifyChanged();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        notifyChanged();
    }

    private static void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onNotificationsChanged();
    }
}
