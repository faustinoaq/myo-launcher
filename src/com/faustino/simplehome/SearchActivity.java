package com.faustino.simplehome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static com.faustino.simplehome.Ui.dp;
import static com.faustino.simplehome.Ui.t;

/** Typed search over installed apps and files on internal storage and USB drives. */
public class SearchActivity extends Activity {

    private static final int MAX_FILES_INDEXED = 30000;
    private static final int MAX_APP_RESULTS = 8;
    private static final int MAX_FILE_RESULTS = 40;

    /** Indexed files survive between openings; a fresh scan replaces them in the background. */
    private static volatile List<File> fileIndex = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runSearch = this::search;

    private EditText input;
    private LinearLayout results;
    private TextView status;
    private List<Ui.App> apps = new ArrayList<>();
    private boolean indexing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The TV's media players only accept file:// links, so allow handing those out.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(Ui.background());
        column.setPadding(dp(this, 120), dp(this, 40), dp(this, 120), 0);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Ui.TILE);
        boxBg.setCornerRadius(dp(this, 30));
        box.setBackground(boxBg);
        box.setPadding(dp(this, 22), 0, dp(this, 22), 0);
        ImageView glass = new ImageView(this);
        glass.setImageResource(R.drawable.ic_search);
        box.addView(glass, new LinearLayout.LayoutParams(dp(this, 28), dp(this, 28)));
        input = new EditText(this);
        input.setHint(t("Search apps and files", "Buscar apps y archivos"));
        input.setHintTextColor(Ui.TEXT_DIM);
        input.setTextColor(Color.WHITE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(dp(this, 16), dp(this, 14), 0, dp(this, 14));
        box.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        column.addView(box, new LinearLayout.LayoutParams(-1, dp(this, 60)));

        status = Ui.label(this, "", 15, Ui.TEXT_DIM);
        status.setGravity(Gravity.START);
        status.setPadding(dp(this, 22), dp(this, 12), 0, dp(this, 4));
        column.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(this, 6), 0, dp(this, 24));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(column);

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, 200);
            }
        });
        // "Search" on the keyboard closes it and moves to the first result.
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard();
                if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        apps = Ui.launchableApps(this);
        startIndexing();
        input.requestFocus();
        input.postDelayed(() -> getSystemService(InputMethodManager.class)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT), 250);
        search();
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    // ---- Indexing ----

    private void startIndexing() {
        if (indexing) return;
        indexing = true;
        new Thread(() -> {
            List<File> found = scanFiles();
            fileIndex = found;
            runOnUiThread(() -> {
                indexing = false;
                search();
            });
        }, "file-index").start();
    }

    /** Breadth-first walk of internal storage and any mounted USB drives. */
    private static List<File> scanFiles() {
        List<File> found = new ArrayList<>();
        Deque<File> queue = new ArrayDeque<>();
        queue.add(new File("/storage/emulated/0"));
        File[] volumes = new File("/storage").listFiles();
        if (volumes != null) {
            for (File v : volumes) {
                if (!v.getName().equals("emulated") && !v.getName().equals("self")) queue.add(v);
            }
        }
        while (!queue.isEmpty() && found.size() < MAX_FILES_INDEXED) {
            File dir = queue.poll();
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                String name = child.getName();
                if (name.startsWith(".")) continue;
                if (child.isDirectory()) {
                    if (!(name.equals("Android") && dir.getPath().equals("/storage/emulated/0"))) queue.add(child);
                } else {
                    found.add(child);
                }
            }
        }
        return found;
    }

    // ---- Searching ----

    private void search() {
        String query = normalize(input.getText().toString().trim());
        results.removeAllViews();

        if (query.isEmpty()) {
            status.setText(indexing ? t("Indexing files…", "Indexando archivos…") : "");
            return;
        }

        int appCount = 0;
        for (Ui.App app : apps) {
            if (appCount >= MAX_APP_RESULTS) break;
            if (!normalize(app.label.toString()).contains(query)) continue;
            addRow(Ui.sharpIcon(this, app.info), app.label, t("App", "Aplicación"), false,
                    () -> Ui.launch(this, app.intent));
            appCount++;
        }

        int fileCount = 0;
        for (File file : fileIndex) {
            if (fileCount >= MAX_FILE_RESULTS) break;
            if (!normalize(file.getName()).contains(query)) continue;
            String mime = mimeOf(file);
            addRow(getDrawable(iconFor(mime)), file.getName(), file.getParent(), true, () -> openFile(file, mime));
            fileCount++;
        }

        if (appCount + fileCount == 0) {
            status.setText(indexing ? t("Indexing files…", "Indexando archivos…")
                    : t("No results", "Sin resultados"));
        } else {
            status.setText(indexing ? t("Indexing files… showing results so far",
                    "Indexando archivos… resultados parciales") : "");
        }
    }

    private void addRow(Drawable icon, CharSequence title, String subtitle, boolean tintIcon, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(this, 18), dp(this, 10), dp(this, 18), dp(this, 10));
        Ui.styleFocusable(this, row, 14);
        // Rows are wide, so a gentler lift than the home tiles.
        View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, focused) -> {
            base.onFocusChange(v, focused);
            v.animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(140).start();
        });

        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        if (tintIcon) image.setColorFilter(Ui.TEXT_DIM);
        row.addView(image, new LinearLayout.LayoutParams(dp(this, 40), dp(this, 40)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(this, 18), 0, 0, 0);
        TextView main = Ui.label(this, title, 19, Color.WHITE);
        main.setGravity(Gravity.START);
        text.addView(main);
        TextView sub = Ui.label(this, subtitle, 14, Ui.TEXT_DIM);
        sub.setGravity(Gravity.START);
        text.addView(sub);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(this, 5), 0, dp(this, 5));
        results.addView(row, lp);
    }

    private void openFile(File file, String mime) {
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), mime);
        Intent anyType = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), "*/*");
        Ui.launch(this, view, anyType);
    }

    private static String mimeOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null && (ext.equals("mkv") || ext.equals("ts"))) mime = "video/*";
        return mime != null ? mime : "*/*";
    }

    private static int iconFor(String mime) {
        if (mime.startsWith("video/")) return R.drawable.ic_movie;
        if (mime.startsWith("audio/")) return R.drawable.ic_music;
        if (mime.startsWith("image/")) return R.drawable.ic_image;
        return R.drawable.ic_file;
    }

    /** Lowercase without accents, so "cancion" finds "Canción". */
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
