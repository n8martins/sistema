package com.sistema.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** On-brand options menu. Shown over the screen when you hold OK (or press Menu) on an app. */
final class Sheet {

    interface Pick {
        void onPick(int index);
    }

    private final FrameLayout overlay;
    private final ViewGroup root;
    private final View content;
    private final View restoreFocus;

    private Sheet(FrameLayout overlay, ViewGroup root, View content, View restoreFocus) {
        this.overlay = overlay;
        this.root = root;
        this.content = content;
        this.restoreFocus = restoreFocus;
    }

    static Sheet show(Context c, final ViewGroup root, final View content, String title, String[] items, final Pick pick) {
        final View previous = root.findFocus();
        final FrameLayout overlay = new FrameLayout(c);
        overlay.setBackgroundColor(0xB3080706);
        overlay.setClickable(true);

        LinearLayout panel = Ui.column(c);
        panel.setBackground(Ui.rounded(0xF21E1A16, 28));
        panel.setPadding(Ui.px(48), Ui.px(44), Ui.px(48), Ui.px(40));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setElevation(Ui.pxf(30));
        panel.setClipToPadding(false);
        panel.setClipChildren(false);

        TextView eyebrow = Ui.eyebrow(c, "Options", 16, Ui.ACCENT_SOFT);
        panel.addView(eyebrow, Ui.lpWrap());
        TextView t = Ui.text(c, Ui.serif, 44, Ui.PAPER);
        t.setText(title);
        LinearLayout.LayoutParams tp = Ui.lpWrap();
        tp.topMargin = Ui.px(12);
        tp.bottomMargin = Ui.px(30);
        panel.addView(t, tp);

        final Sheet sheet = new Sheet(overlay, root, content, previous);
        View first = null;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            Pill p = new Pill(c, 0, items[i], null);
            p.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.px(520), Ui.px(Pill.H));
            lp.topMargin = i == 0 ? 0 : Ui.px(14);
            panel.addView(p, lp);
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sheet.dismiss();
                    pick.onPick(index);
                }
            });
            if (first == null) first = p;
        }

        overlay.addView(panel, Ui.frame(-2, -2, Gravity.CENTER));
        if (content instanceof ViewGroup) {
            ((ViewGroup) content).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(160).start();
        if (first != null) first.requestFocus();
        return sheet;
    }

    void dismiss() {
        root.removeView(overlay);
        if (content instanceof ViewGroup) {
            ((ViewGroup) content).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        if (restoreFocus != null && restoreFocus.isAttachedToWindow()) restoreFocus.requestFocus();
    }
}
