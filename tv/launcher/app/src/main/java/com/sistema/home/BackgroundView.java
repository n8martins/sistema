package com.sistema.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Calendar;

/**
 * The backdrop follows the real sun. Warm glow at sunrise, soft paper light through the day,
 * terracotta at golden hour, deep and cool at night. Sunrise and sunset come from the
 * weather feed, so it matches the light outside the window.
 */
final class BackgroundView extends View {

    static final int NIGHT = 0;
    static final int DAWN = 1;
    static final int DAY = 2;
    static final int GOLDEN = 3;
    static final int DUSK = 4;

    // top, bottom, glow, glow alpha, ember, ember alpha
    private static final int[][] PHASES = {
            {0xFF0F0D10, 0xFF070606, 0xFF42506E, 46, 0xFFB8442B, 12},  // night
            {0xFF2A1812, 0xFF0B0908, 0xFFD97A3C, 82, 0xFFB8442B, 28},  // dawn
            {0xFF1D1A16, 0xFF0B0A09, 0xFFEEE7D8, 26, 0xFFD97A3C, 16},  // day
            {0xFF2B150F, 0xFF0B0807, 0xFFB8442B, 100, 0xFFD97A3C, 38}, // golden hour
            {0xFF1B1320, 0xFF08070A, 0xFF6E3B5C, 60, 0xFFB8442B, 18},  // dusk
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private int phase = -1;
    private int lastW, lastH;
    private Shader base, glow, ember, scrim;

    BackgroundView(Context c) {
        super(c);
    }

    /** Pick the phase from the current time and today's sunrise/sunset (epoch millis, 0 if unknown). */
    void update(long sunrise, long sunset) {
        int p = phaseFor(System.currentTimeMillis(), sunrise, sunset);
        if (p != phase) {
            phase = p;
            base = null;
            invalidate();
        }
    }

    int phase() {
        return phase;
    }

    static int phaseFor(long now, long sunrise, long sunset) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        if (sunrise <= 0 || sunset <= 0 || !sameDay(cal, sunrise) || !sameDay(cal, sunset)) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.set(Calendar.HOUR_OF_DAY, 6);
            c.set(Calendar.MINUTE, 45);
            c.set(Calendar.SECOND, 0);
            sunrise = c.getTimeInMillis();
            c.set(Calendar.HOUR_OF_DAY, 17);
            c.set(Calendar.MINUTE, 50);
            sunset = c.getTimeInMillis();
        }
        long m = 60_000L;
        if (now < sunrise - 60 * m) return NIGHT;
        if (now < sunrise + 90 * m) return DAWN;
        if (now < sunset - 90 * m) return DAY;
        if (now < sunset + 25 * m) return GOLDEN;
        if (now < sunset + 110 * m) return DUSK;
        return NIGHT;
    }

    private static boolean sameDay(Calendar now, long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (phase < 0) phase = phaseFor(System.currentTimeMillis(), 0, 0);
        if (base == null || w != lastW || h != lastH) build(w, h);

        paint.setShader(base);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(glow);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(ember);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(scrim);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void build(int w, int h) {
        lastW = w;
        lastH = h;
        int[] p = PHASES[phase];
        base = new LinearGradient(0, 0, 0, h, p[0], p[1], Shader.TileMode.CLAMP);
        glow = new RadialGradient(w * 0.86f, h * -0.08f, w * 0.72f,
                new int[]{withAlpha(p[2], p[3]), withAlpha(p[2], p[3] / 3), 0},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        ember = new RadialGradient(w * 0.04f, h * 1.05f, w * 0.55f,
                new int[]{withAlpha(p[4], p[5]), 0},
                null, Shader.TileMode.CLAMP);
        // Darkens the lower half so tiles always read cleanly.
        scrim = new LinearGradient(0, h * 0.5f, 0, h,
                new int[]{0, 0x66000000}, null, Shader.TileMode.CLAMP);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
