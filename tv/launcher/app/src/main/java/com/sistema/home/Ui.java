package com.sistema.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Design system for the launcher.
 *
 * Everything is laid out on a 1920x1080 canvas and scaled to the real screen, so the
 * TV looks identical whether it reports 720p, 1080p or 4K.
 *
 * Palette and type come straight from the SISTEMA site (paper, ink, terracotta;
 * Fraunces, IBM Plex Sans, JetBrains Mono), flipped dark so it sits right in a room at night.
 */
final class Ui {
    private Ui() {}

    // SISTEMA palette, dark room edition.
    static final int INK = 0xFF110E0C;
    static final int INK_RAISED = 0xFF1E1A16;
    static final int PAPER = 0xFFEEE7D8;
    static final int PAPER_90 = 0xE6EEE7D8;
    static final int PAPER_70 = 0xB3EEE7D8;
    static final int PAPER_50 = 0x80EEE7D8;
    static final int PAPER_35 = 0x59EEE7D8;
    static final int PAPER_15 = 0x26EEE7D8;
    static final int PAPER_08 = 0x14EEE7D8;
    static final int ACCENT = 0xFFB8442B;
    static final int ACCENT_SOFT = 0xFFD97A3C;

    static float scale = 1f;

    static Typeface display;     // Fraunces Light, optical size 144
    static Typeface serif;       // Fraunces Medium
    static Typeface italic;      // Fraunces Italic
    static Typeface thin;        // IBM Plex Sans 200
    static Typeface light;       // IBM Plex Sans 300
    static Typeface regular;     // IBM Plex Sans 400
    static Typeface medium;      // IBM Plex Sans 500
    static Typeface mono;        // JetBrains Mono 400
    static Typeface monoMedium;  // JetBrains Mono 500

    static void init(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        scale = Math.min(w / 1920f, h / 1080f);
        if (scale <= 0f) scale = 1f;
        if (display == null) loadFonts(activity.getApplicationContext());
    }

    private static void loadFonts(Context c) {
        display = font(c, "fraunces_display.ttf", Typeface.SERIF);
        serif = font(c, "fraunces_medium.ttf", Typeface.SERIF);
        italic = font(c, "fraunces_italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        thin = font(c, "sans_thin.ttf", Typeface.create("sans-serif-thin", Typeface.NORMAL));
        light = font(c, "sans_light.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL));
        regular = font(c, "sans_regular.ttf", Typeface.SANS_SERIF);
        medium = font(c, "sans_medium.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mono = font(c, "mono_regular.ttf", Typeface.MONOSPACE);
        monoMedium = font(c, "mono_medium.ttf", Typeface.MONOSPACE);
    }

    private static Typeface font(Context c, String file, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Design pixels (1920x1080 canvas) to real pixels. */
    static int px(float design) {
        return Math.round(design * scale);
    }

    static float pxf(float design) {
        return design * scale;
    }

    static TextView text(Context c, Typeface tf, float size, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small uppercase mono label, the SISTEMA eyebrow style. */
    static TextView eyebrow(Context c, String s, float size, int color) {
        TextView t = text(c, mono, size, color);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        t.setText(s);
        return t;
    }

    static GradientDrawable rounded(int color, float designRadius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(pxf(designRadius));
        return d;
    }

    static GradientDrawable ring(int color, float designStroke, float designRadius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        d.setStroke(Math.max(1, px(designStroke)), color);
        d.setCornerRadius(pxf(designRadius));
        return d;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.gravity = gravity;
        return p;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static View spacer(Context c, float designW, float designH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(designW), px(designH)));
        return v;
    }
}
