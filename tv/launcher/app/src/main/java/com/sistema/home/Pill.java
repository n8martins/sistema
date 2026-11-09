package com.sistema.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Compact control for inputs and system shortcuts. Glass when idle; turns solid paper with
 * ink text when focused, the way Apple TV buttons do.
 */
final class Pill extends LinearLayout {

    static final float H = 76;

    private final GlyphView glyph;
    private final TextView title;
    private final TextView sub;
    private final GradientDrawable bg;

    Pill(Context c, int glyphType, String titleText, String subText) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(Ui.px(26), 0, Ui.px(32), 0);
        setMinimumWidth(Ui.px(200));
        bg = Ui.rounded(Ui.PAPER_08, 20);
        setBackground(bg);

        glyph = new GlyphView(c, glyphType);
        glyph.setColor(Ui.PAPER_90);
        if (glyphType != 0) addView(glyph, Ui.lp(Ui.px(32), Ui.px(32)));

        LinearLayout texts = Ui.column(c);
        LayoutParams tp = Ui.lpWrap();
        tp.leftMargin = glyphType != 0 ? Ui.px(16) : 0;
        addView(texts, tp);

        title = Ui.text(c, Ui.medium, 24, Ui.PAPER_90);
        title.setText(titleText);
        title.setSingleLine(true);
        texts.addView(title, Ui.lpWrap());

        sub = Ui.text(c, Ui.regular, 17, Ui.PAPER_50);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setMaxWidth(Ui.px(220));
        if (!TextUtils.isEmpty(subText)) {
            sub.setText(subText);
            LayoutParams sp = Ui.lpWrap();
            sp.topMargin = Ui.px(5);
            texts.addView(sub, sp);
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        bg.setColor(focused ? Ui.PAPER : Ui.PAPER_08);
        title.setTextColor(focused ? Ui.INK : Ui.PAPER_90);
        sub.setTextColor(focused ? 0x99110E0C : Ui.PAPER_50);
        glyph.setColor(focused ? Ui.INK : Ui.PAPER_90);
        glyph.setWarm(focused ? Ui.ACCENT : Ui.ACCENT_SOFT);
        float s = focused ? 1.06f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(focused ? Ui.pxf(14) : 0f)
                .setDuration(180).setInterpolator(new DecelerateInterpolator(2f)).start();
    }
}
