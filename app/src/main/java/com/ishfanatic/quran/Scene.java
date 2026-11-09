package com.ishfanatic.quran;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The page the screensaver and the app share: one ayah in Arabic with its translation and reference, a clock in
 * the corner, the next ayah fading in after seconds_per_ayah. Against burn-in everything drifts together, a few dp
 * round a small circle, one step every two minutes. The last ayah is kept for when the TV is offline.
 */
final class Scene {
    private static final String TAG = "QuranScene";
    private static final int AYAT = 6236;
    private static final long RETRY_MS = 60_000;
    private static final long DRIFT_MS = 120_000;
    private static final int GOLD = 0xFFD4B26A;

    final FrameLayout root;
    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final LinearLayout column;
    private final TextView arabic, translation, reference, clock, status;
    private boolean running;
    private boolean shown;
    private int driftStep;

    Scene(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE);
        Typeface font = ctx.getResources().getFont(R.font.outfit);

        root = new FrameLayout(ctx);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF1A160D, 0xFF0A0906, 0xFF050505}));

        column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(120), dp(40), dp(120), dp(40));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        column.addView(new View(ctx), new LinearLayout.LayoutParams(-1, 0, 0.8f));
        arabic = text(null, 0, 44, 0xFFF3E9D2);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        arabic.setLineSpacing(dp(10), 1f);
        arabic.setMaxLines(5);
        arabic.setAutoSizeTextTypeUniformWithConfiguration(22, 44, 1, TypedValue.COMPLEX_UNIT_SP);
        column.addView(arabic, new LinearLayout.LayoutParams(-1, 0, 3f));

        translation = text(font, 300, 24, 0xC8FFFFFF);
        translation.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        translation.setLineSpacing(dp(4), 1f);
        translation.setMaxLines(6);
        translation.setPadding(0, dp(28), 0, 0);
        translation.setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP);
        column.addView(translation, new LinearLayout.LayoutParams(-1, 0, 2.2f));

        reference = text(font, 500, 15, GOLD);
        reference.setLetterSpacing(0.12f);
        reference.setGravity(Gravity.CENTER);
        column.addView(reference, new LinearLayout.LayoutParams(-1, -2));
        column.addView(new View(ctx), new LinearLayout.LayoutParams(-1, 0, 0.6f));

        clock = text(font, 300, 22, 0x99FFFFFF);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(36), dp(48), 0);
        root.addView(clock, clp);

        status = text(font, 400, 14, 0x80FFFFFF);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.setMargins(0, 0, 0, dp(28));
        root.addView(status, slp);

        Ayah last = cached();
        if (last != null) {
            show(last, false);
        } else {
            status.setText(R.string.loading);
        }
    }

    void start() {
        if (running) return;
        running = true;
        tickClock();
        main.postDelayed(drift, DRIFT_MS);
        next();
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** The next ayah now (Right or OK on the remote), and the timer starts again. */
    void next() {
        main.removeCallbacks(advance);
        fetch(0);
    }

    private final Runnable advance = this::next;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            driftStep++;
            double angle = driftStep * Math.PI / 4;
            float r = dp(6);
            column.setTranslationX((float) (r * Math.cos(angle)));
            column.setTranslationY((float) (r * Math.sin(angle)));
            clock.setTranslationX(column.getTranslationX());
            clock.setTranslationY(column.getTranslationY());
            main.postDelayed(this, DRIFT_MS);
        }
    };

    private void tickClock() {
        clock.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));
        main.postDelayed(this::tickClock, 30_000);
    }

    /** A reference from the ayat list, never the one just shown, or any ayah of the Quran when the list is empty. */
    private String pick() {
        String list = ctx.getString(R.string.ayat).trim();
        if (list.isEmpty()) return String.valueOf(1 + random.nextInt(AYAT));
        String[] refs = list.split("\\s*,\\s*");
        String ref = refs[random.nextInt(refs.length)];
        if (ref.equals(lastRef) && refs.length > 1) ref = refs[(java.util.Arrays.asList(refs).indexOf(ref) + 1) % refs.length];
        lastRef = ref;
        return ref;
    }

    private String lastRef;

    private void fetch(int attempt) {
        String n = pick();
        String edition = ctx.getString(R.string.translation).trim();
        String url = "https://api.alquran.cloud/v1/ayah/" + n + "/editions/quran-uthmani" + (edition.isEmpty() ? "" : "," + edition);
        try {
            io.execute(() -> {
                Ayah got = null;
                try {
                    got = Ayah.parse(new JSONObject(get(url)));
                } catch (Exception e) {
                    Log.w(TAG, "fetch: " + e);
                }
                Ayah ayah = got;
                main.post(() -> {
                    if (!running) return;
                    if (ayah == null) {
                        if (!shown) status.setText(R.string.offline);
                        main.postDelayed(advance, RETRY_MS);
                        return;
                    }
                    int max = ctx.getResources().getInteger(R.integer.max_ayah_length);
                    if (ayah.arabic.length() > max && attempt < 5) {
                        fetch(attempt + 1);   // too long to read from the sofa: pick another
                        return;
                    }
                    save(ayah);
                    show(ayah, true);
                    int seconds = Math.max(20, Math.min(3600, ctx.getResources().getInteger(R.integer.seconds_per_ayah)));
                    main.postDelayed(advance, seconds * 1000L);
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // The screensaver is closing.
        }
    }

    private void show(Ayah ayah, boolean fade) {
        shown = true;
        status.setText("");
        Runnable set = () -> {
            arabic.setText(ayah.arabic);
            translation.setText(ayah.translation);
            reference.setText(ayah.reference());
        };
        if (!fade) {
            set.run();
            return;
        }
        column.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            set.run();
            column.animate().alpha(1f).setDuration(900).start();
        }).start();
    }

    private void save(Ayah ayah) {
        try {
            prefs.edit().putString("last", ayah.toJson().toString()).apply();
        } catch (Exception ignored) {
            // Only a cache.
        }
    }

    private Ayah cached() {
        try {
            String s = prefs.getString("last", null);
            return s == null ? null : Ayah.fromJson(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[4096];
            for (int k; (k = in.read(buf)) > 0; ) out.write(buf, 0, k);
        } finally {
            c.disconnect();
        }
        return out.toString("UTF-8");
    }

    private TextView text(Typeface font, int weight, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        if (font != null) t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
