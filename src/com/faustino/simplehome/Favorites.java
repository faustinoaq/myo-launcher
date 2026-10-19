package com.faustino.simplehome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ordered list of packages pinned to the home screen's main row. */
final class Favorites {

    private static final String PREFS = "home";
    private static final String KEY = "favorites";
    private static final String DEFAULT = String.join(",",
            "com.google.android.youtube.tv",
            "com.android.mgstv",
            "com.spotify.tv.android");

    private Favorites() {
    }

    static List<String> load(Context c) {
        String raw = prefs(c).getString(KEY, DEFAULT);
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split(",")));
        return out;
    }

    static boolean contains(Context c, String pkg) {
        return load(c).contains(pkg);
    }

    static void add(Context c, String pkg) {
        List<String> list = load(c);
        if (!list.contains(pkg)) list.add(pkg);
        save(c, list);
    }

    static void remove(Context c, String pkg) {
        List<String> list = load(c);
        list.remove(pkg);
        save(c, list);
    }

    /** Moves pkg by delta positions (-1 = left, +1 = right), clamped to the list. */
    static void move(Context c, String pkg, int delta) {
        List<String> list = load(c);
        int from = list.indexOf(pkg);
        if (from < 0) return;
        int to = Math.max(0, Math.min(list.size() - 1, from + delta));
        list.remove(from);
        list.add(to, pkg);
        save(c, list);
    }

    private static void save(Context c, List<String> list) {
        prefs(c).edit().putString(KEY, String.join(",", list)).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
