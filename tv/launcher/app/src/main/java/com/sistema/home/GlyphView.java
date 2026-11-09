package com.sistema.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Hand drawn line icons. One consistent stroke weight, no icon font, no bitmaps.
 * Drawn on a 24 unit grid and scaled to the view.
 */
final class GlyphView extends View {

    static final int HDMI = 1;
    static final int SLIDERS = 2;
    static final int GRID = 3;
    static final int SUN = 10;
    static final int MOON = 11;
    static final int PARTLY_DAY = 12;
    static final int PARTLY_NIGHT = 13;
    static final int CLOUD = 14;
    static final int RAIN = 15;
    static final int STORM = 16;
    static final int FOG = 17;
    static final int SNOW = 18;
    static final int PLUS = 20;

    private int glyph;
    private int color = Ui.PAPER;
    private int warm = Ui.ACCENT_SOFT;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    GlyphView(Context c, int glyph) {
        super(c);
        this.glyph = glyph;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    void setGlyph(int g) {
        if (g != glyph) {
            glyph = g;
            invalidate();
        }
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    /** Colour used for the sun and lightning. */
    void setWarm(int c) {
        warm = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        if (s <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        canvas.scale(s, s);
        stroke.setStrokeWidth(1.6f);
        stroke.setColor(color);
        fill.setColor(color);
        switch (glyph) {
            case HDMI: drawHdmi(canvas); break;
            case SLIDERS: drawSliders(canvas); break;
            case GRID: drawGrid(canvas); break;
            case PLUS: drawPlus(canvas); break;
            case SUN: drawSun(canvas, 12, 12, 1f); break;
            case MOON: drawMoon(canvas, 12, 12, 1f); break;
            case PARTLY_DAY:
                drawSun(canvas, 9, 9, 0.72f);
                drawCloud(canvas, 2.5f, 3.5f, 0.85f);
                break;
            case PARTLY_NIGHT:
                drawMoon(canvas, 9.5f, 8.5f, 0.7f);
                drawCloud(canvas, 2.5f, 3.5f, 0.85f);
                break;
            case CLOUD: drawCloud(canvas, 0, 1, 1f); break;
            case RAIN:
                drawCloud(canvas, 0, -2, 1f);
                stroke.setColor(color);
                line(canvas, 8.5f, 17.5f, 7.5f, 20.5f);
                line(canvas, 12.5f, 17.5f, 11.5f, 20.5f);
                line(canvas, 16.5f, 17.5f, 15.5f, 20.5f);
                break;
            case STORM:
                drawCloud(canvas, 0, -2.5f, 1f);
                path.reset();
                path.moveTo(13.2f, 14.5f);
                path.lineTo(10.2f, 18.6f);
                path.lineTo(12.6f, 18.6f);
                path.lineTo(11.2f, 22.2f);
                path.lineTo(15f, 17.2f);
                path.lineTo(12.6f, 17.2f);
                path.close();
                fill.setColor(warm);
                canvas.drawPath(path, fill);
                break;
            case SNOW:
                drawCloud(canvas, 0, -2, 1f);
                fill.setColor(color);
                canvas.drawCircle(8.5f, 19f, 1.1f, fill);
                canvas.drawCircle(12.5f, 20.5f, 1.1f, fill);
                canvas.drawCircle(16.5f, 19f, 1.1f, fill);
                break;
            case FOG:
                line(canvas, 4, 8, 20, 8);
                line(canvas, 6, 12, 18, 12);
                line(canvas, 4, 16, 20, 16);
                line(canvas, 8, 20, 16, 20);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void drawHdmi(Canvas c) {
        path.reset();
        path.moveTo(2.5f, 8f);
        path.lineTo(21.5f, 8f);
        path.lineTo(21.5f, 13f);
        path.lineTo(18.5f, 16.5f);
        path.lineTo(5.5f, 16.5f);
        path.lineTo(2.5f, 13f);
        path.close();
        c.drawPath(path, stroke);
        line(c, 7f, 11.8f, 17f, 11.8f);
    }

    private void drawSliders(Canvas c) {
        line(c, 4, 7, 20, 7);
        line(c, 4, 12, 20, 12);
        line(c, 4, 17, 20, 17);
        fill.setColor(color);
        knob(c, 15, 7);
        knob(c, 8, 12);
        knob(c, 13, 17);
    }

    private void knob(Canvas c, float x, float y) {
        c.drawCircle(x, y, 2.3f, fill);
    }

    private void drawGrid(Canvas c) {
        fill.setColor(color);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c.drawCircle(6 + i * 6, 6 + j * 6, 1.7f, fill);
            }
        }
    }

    private void drawPlus(Canvas c) {
        line(c, 12, 5, 12, 19);
        line(c, 5, 12, 19, 12);
    }

    private void drawSun(Canvas c, float cx, float cy, float k) {
        stroke.setColor(warm);
        fill.setColor(warm);
        c.drawCircle(cx, cy, 4.2f * k, fill);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float r1 = 6.8f * k, r2 = 9.2f * k;
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, stroke);
        }
        stroke.setColor(color);
        fill.setColor(color);
    }

    private void drawMoon(Canvas c, float cx, float cy, float k) {
        Path outer = new Path();
        outer.addCircle(cx, cy, 7.5f * k, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + 4.2f * k, cy - 3.2f * k, 6.2f * k, Path.Direction.CW);
        outer.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(color);
        c.drawPath(outer, fill);
    }

    /** Filled cloud silhouette. Offsets shift it on the 24 grid, k scales around the centre. */
    private void drawCloud(Canvas c, float dx, float dy, float k) {
        c.save();
        c.translate(12 + dx, 12 + dy);
        c.scale(k, k);
        c.translate(-12, -12);
        Path p = new Path();
        p.addCircle(8.2f, 14.2f, 3.8f, Path.Direction.CW);
        Path a = new Path();
        a.addCircle(12.8f, 11.6f, 5f, Path.Direction.CW);
        Path b = new Path();
        b.addCircle(17.2f, 14.6f, 3.4f, Path.Direction.CW);
        Path base = new Path();
        base.addRoundRect(new android.graphics.RectF(4.4f, 13.4f, 20.6f, 18f), 2.3f, 2.3f, Path.Direction.CW);
        p.op(a, Path.Op.UNION);
        p.op(b, Path.Op.UNION);
        p.op(base, Path.Op.UNION);
        // A thin dark halo keeps the cloud readable when it sits on the sun.
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(1.6f);
        halo.setColor(0xCC110E0C);
        c.drawPath(p, halo);
        fill.setColor(color);
        c.drawPath(p, fill);
        c.restore();
    }
}
