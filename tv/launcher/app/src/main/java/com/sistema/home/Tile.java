package com.sistema.home;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An app card in the Apple TV spirit: a 16:9 banner that lifts toward you when focused,
 * with the name fading in underneath.
 */
final class Tile extends LinearLayout {

    static final float W = 256, H = 144, RADIUS = 18;

    final Apps.App app;
    private final FrameLayout card;
    private final TextView name;
    private final View ring;
    private final boolean alwaysShowName;
    private boolean nameInsideCard;

    Tile(Context c, Apps.App app, boolean alwaysShowName) {
        super(c);
        this.app = app;
        this.alwaysShowName = alwaysShowName;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);
        setClipToPadding(false);

        final float r = Ui.pxf(RADIUS);
        card = new FrameLayout(c);
        card.setBackground(Ui.rounded(Ui.INK_RAISED, RADIUS));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(Ui.pxf(2));
        addView(card, Ui.lp(Ui.px(W), Ui.px(H)));

        if (!app.installed) {
            buildMissing(c);
        } else if (app.banner != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(app.banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            buildIconCard(c, app.icon);
        }

        ring = new View(c);
        ring.setBackground(Ui.ring(Ui.PAPER_90, 3, RADIUS));
        ring.setAlpha(0f);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));

        name = Ui.text(c, Ui.medium, 24, Ui.PAPER);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        // Icon cards already print the name inside, so don't repeat it underneath.
        if (nameInsideCard) name.setVisibility(INVISIBLE);
        name.setAlpha(alwaysShowName ? 0.7f : 0f);
        LayoutParams np = Ui.lp(Ui.px(W), -2);
        np.topMargin = Ui.px(18);
        addView(name, np);
    }

    private void buildIconCard(Context c, Drawable icon) {
        nameInsideCard = true;
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setImageDrawable(icon);
        col.addView(img, Ui.lp(Ui.px(64), Ui.px(64)));
        TextView t = Ui.text(c, Ui.medium, 20, Ui.PAPER_70);
        t.setText(app.label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(Ui.px(W - 32));
        LayoutParams tp = Ui.lpWrap();
        tp.topMargin = Ui.px(12);
        col.addView(t, tp);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildMissing(Context c) {
        nameInsideCard = true;
        card.setBackground(Ui.rounded(Ui.PAPER_08, RADIUS));
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER);
        TextView t = Ui.text(c, Ui.serif, 30, Ui.PAPER_70);
        t.setText(app.label);
        col.addView(t, Ui.lpWrap());
        TextView s = Ui.eyebrow(c, "OK to install", 15, Ui.PAPER_35);
        LayoutParams sp = Ui.lpWrap();
        sp.topMargin = Ui.px(12);
        col.addView(s, sp);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? 1.1f : 1f;
        card.animate().scaleX(s).scaleY(s).translationZ(focused ? Ui.pxf(26) : 0f)
                .setDuration(focused ? 220 : 160).setInterpolator(new DecelerateInterpolator(2f)).start();
        ring.animate().alpha(focused ? 1f : 0f).setDuration(180).start();
        name.animate().alpha(focused ? 1f : (alwaysShowName ? 0.7f : 0f))
                .translationY(focused ? Ui.pxf(4) : 0f).setDuration(200).start();
    }
}
