package com.sistema.home;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Every app on the TV in one calm grid. Hold OK on any of them to pin it to your favorites. */
public class AppsActivity extends Activity {

    private static final int COLUMNS = 6;

    private FrameLayout root;
    private FrameLayout content;
    private GridLayout grid;
    private TextView count;
    private Sheet sheet;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        BackgroundView bg = new BackgroundView(this);
        Weather w = Weather.fromCache(Prefs.get(this));
        bg.update(w != null ? w.sunrise : 0, w != null ? w.sunset : 0);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        // The grid owns focus. The scroller only follows it.
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = Ui.column(this);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.px(120), Ui.px(84), Ui.px(120), Ui.px(120));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));

        count = Ui.eyebrow(this, "Loading", 18, Ui.PAPER_50);
        col.addView(count, Ui.lpWrap());
        TextView title = Ui.text(this, Ui.display, 84, Ui.PAPER);
        title.setText("All apps");
        LinearLayout.LayoutParams tp = Ui.lpWrap();
        tp.topMargin = Ui.px(18);
        col.addView(title, tp);
        TextView hint = Ui.text(this, Ui.light, 26, Ui.PAPER_50);
        hint.setText("Hold OK on any app to pin it to your favorites.");
        LinearLayout.LayoutParams hp = Ui.lpWrap();
        hp.topMargin = Ui.px(16);
        hp.bottomMargin = Ui.px(46);
        col.addView(hint, hp);

        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setUseDefaultMargins(false);
        col.addView(grid, Ui.lpWrap());

        load();
    }

    private void load() {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Apps.App> apps = Apps.all(AppsActivity.this);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) show(apps);
                    }
                });
            }
        }, "apps").start();
    }

    private void show(List<Apps.App> apps) {
        grid.removeAllViews();
        count.setText(apps.size() + " apps installed");
        for (int i = 0; i < apps.size(); i++) {
            final Apps.App a = apps.get(i);
            Tile t = new Tile(this, a, true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.rightMargin = (i % COLUMNS == COLUMNS - 1) ? 0 : Ui.px(28);
            lp.bottomMargin = Ui.px(34);
            grid.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(AppsActivity.this, a);
                }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    options(a);
                    return true;
                }
            });
        }
        if (grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
            grid.post(new Runnable() {
                @Override
                public void run() {
                    View f = getWindow().getDecorView().findFocus();
                    if (!(f instanceof Tile) && grid.getChildCount() > 0 && sheet == null) grid.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private void options(final Apps.App a) {
        final List<String> favs = Apps.favorites(this);
        final boolean pinned = favs.contains(a.pkg);
        final String[] items = {pinned ? "Remove from favorites" : "Add to favorites", "App info"};
        sheet = Sheet.show(this, root, content, a.label, items, new Sheet.Pick() {
            @Override
            public void onPick(int i) {
                sheet = null;
                if (i == 1) {
                    Apps.openAppInfo(AppsActivity.this, a.pkg);
                    return;
                }
                if (pinned) favs.remove(a.pkg);
                else favs.add(a.pkg);
                Apps.saveFavorites(AppsActivity.this, favs);
                android.widget.Toast.makeText(AppsActivity.this,
                        pinned ? a.label + " removed from favorites" : a.label + " added to favorites",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && sheet == null) {
            View f = getWindow().getDecorView().findFocus();
            if (f instanceof Tile) {
                f.performLongClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && sheet == null && getWindow().getDecorView().findFocus() == null && grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
            return;
        }
        super.onBackPressed();
    }
}
