package com.sistema.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The home screen.
 *
 *   Hello Nicolas, a line for the moment, and a quote dealt fresh every time the TV wakes.
 *   Time, date and today's weather on the right.
 *   Favorites and HDMI inputs along the bottom. Nothing else.
 */
public class HomeActivity extends Activity {

    private static final long QUOTE_EVERY_MS = 20 * 60_000L;

    private SharedPreferences prefs;
    private FrameLayout root;
    private FrameLayout content;
    private BackgroundView background;

    private LinearLayout hero;
    private LinearLayout status;
    private TextView dayText, daysLeft, greeting, subline, quoteText, quoteAuthor, quoteHint, clock, ampm, date;
    private View progressFill, quoteBar;
    private LinearLayout quoteBox;
    private GlyphView wxGlyph;
    private TextView wxTemp, wxCond, wxDetails, wxSun;

    private LinearLayout favRow;
    private HorizontalScrollView favScroll;
    private LinearLayout inputRow;
    private HorizontalScrollView inputScroll;

    private Weather weather;
    private Sheet sheet;
    private boolean resumed;
    private boolean started;
    private String favSignature = "";
    private String inputSignature = "";
    private int lastSlotHour = -1;
    private int shownQuote = -1;
    private TextView remoteHint;
    private View pairOverlay;
    private TextView pairDigits;

    /** Changes made from the phone remote land in prefs; the screen follows them live. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    if (key == null) return;
                    if (Prefs.Q_TIME.equals(key)) {
                        animateQuote(Copy.currentQuote(prefs));
                    } else if (Prefs.NAME.equals(key)) {
                        setGreeting();
                    } else if (Prefs.FAVORITES.equals(key)) {
                        refreshFavorites(true);
                    } else if (Prefs.LAT.equals(key) || Prefs.LON.equals(key) || Prefs.UNITS.equals(key)) {
                        weather = Weather.fromCache(prefs);
                        applyWeather();
                        Weather.resetBackoff();
                        Weather.fetch(prefs, wxListener);
                    } else if (Prefs.CITY.equals(key)) {
                        applyWeather();
                    } else if (Prefs.PAIR_CODE.equals(key)) {
                        showPairing(prefs.getString(Prefs.PAIR_CODE, null));
                    } else if (Prefs.TOKENS.equals(key)) {
                        showPairing(null);
                        updateRemoteHint();
                    }
                }
            };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            onMinute();
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) onWake();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshFavorites(false);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        prefs = Prefs.get(this);
        Prefs.applyExtras(this, getIntent());

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        background = new BackgroundView(this);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        buildHero();
        buildStatus();
        buildBottom();

        weather = Weather.fromCache(prefs);
        applyWeather();
        updateClock();
        setGreeting();
        showQuote(Copy.currentQuote(prefs));
        subline.setText(Copy.line(Calendar.getInstance(), prefs.getLong(Prefs.LINE_SEED, 0)));
        refreshFavorites(true);
        refreshInputs();
        if (System.currentTimeMillis() - prefs.getLong(Prefs.Q_TIME, 0) > QUOTE_EVERY_MS) deal();
        entrance();

        IntentFilter t = new IntentFilter();
        t.addAction(Intent.ACTION_TIME_TICK);
        t.addAction(Intent.ACTION_TIME_CHANGED);
        t.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        t.addAction(Intent.ACTION_DATE_CHANGED);
        register(tick, t);
        IntentFilter s = new IntentFilter();
        s.addAction(Intent.ACTION_SCREEN_ON);
        register(screen, s);
        IntentFilter p = new IntentFilter();
        p.addAction(Intent.ACTION_PACKAGE_ADDED);
        p.addAction(Intent.ACTION_PACKAGE_REMOVED);
        p.addAction(Intent.ACTION_PACKAGE_CHANGED);
        p.addAction(Intent.ACTION_PACKAGE_REPLACED);
        p.addDataScheme("package");
        register(packages, p);

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        RemoteService.start(this);
        updateRemoteHint();
        focusFirst();
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        for (BroadcastReceiver r : new BroadcastReceiver[]{tick, screen, packages}) {
            try {
                unregisterReceiver(r);
            } catch (IllegalArgumentException ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Some builds tag the Home intent with their own extras. Only react to ours.
        boolean hadExtras = intent != null && (intent.hasExtra("name") || intent.hasExtra("city")
                || intent.hasExtra("favorites") || intent.hasExtra("lat") || intent.hasExtra("units"));
        if (Prefs.applyExtras(this, intent)) {
            weather = null;
            applyWeather();
            Weather.resetBackoff();
            Weather.fetch(prefs, wxListener);
        }
        if (hadExtras) {
            setGreeting();
            refreshFavorites(true);
        }
        // Pressing Home while already home takes you back to the start of the row.
        if (resumed && sheet == null) {
            favScroll.smoothScrollTo(0, 0);
            inputScroll.smoothScrollTo(0, 0);
            focusFirst();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        RemoteService.start(this);
        updateRemoteHint();
        updateClock();
        onMinute();
        refreshFavorites(false);
        refreshInputs();
        if (System.currentTimeMillis() - prefs.getLong(Prefs.Q_TIME, 0) > QUOTE_EVERY_MS) onWake();
        else if (Weather.isStale(prefs)) Weather.fetch(prefs, wxListener);
    }

    @Override
    protected void onStop() {
        started = false;
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Never leave the remote with nothing selected.
        if (hasFocus && sheet == null && getWindow().getDecorView().findFocus() == null) focusFirst();
    }

    @Override
    public void onBackPressed() {
        if (sheet != null) {
            closeSheet();
            return;
        }
        // A launcher has nowhere to go back to. Back just returns focus to your first app.
        favScroll.smoothScrollTo(0, 0);
        focusFirst();
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

    // ---------------------------------------------------------------- layout

    private void buildHero() {
        hero = Ui.column(this);
        hero.setClipChildren(false);
        FrameLayout.LayoutParams hp = Ui.frame(Ui.px(1120), -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = Ui.px(120);
        hp.topMargin = Ui.px(96);
        content.addView(hero, hp);

        LinearLayout eyebrow = Ui.row(this);
        dayText = Ui.eyebrow(this, "", 19, Ui.PAPER_50);
        eyebrow.addView(dayText, Ui.lpWrap());
        FrameLayout track = new FrameLayout(this);
        track.setBackground(Ui.rounded(Ui.PAPER_15, 2));
        progressFill = new View(this);
        progressFill.setBackground(Ui.rounded(Ui.ACCENT_SOFT, 2));
        track.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tp = Ui.lp(Ui.px(150), Math.max(2, Ui.px(3)));
        tp.leftMargin = Ui.px(22);
        eyebrow.addView(track, tp);
        daysLeft = Ui.eyebrow(this, "", 19, Ui.ACCENT_SOFT);
        LinearLayout.LayoutParams dp = Ui.lpWrap();
        dp.leftMargin = Ui.px(22);
        eyebrow.addView(daysLeft, dp);
        hero.addView(eyebrow, Ui.lpWrap());

        greeting = Ui.text(this, Ui.display, 118, Ui.PAPER);
        greeting.setLetterSpacing(-0.015f);
        greeting.setSingleLine(true);
        LinearLayout.LayoutParams gp = Ui.lpWrap();
        gp.topMargin = Ui.px(30);
        hero.addView(greeting, gp);

        subline = Ui.text(this, Ui.light, 34, Ui.PAPER_70);
        LinearLayout.LayoutParams sp = Ui.lpWrap();
        sp.topMargin = Ui.px(22);
        hero.addView(subline, sp);

        quoteBox = Ui.row(this);
        quoteBox.setGravity(Gravity.TOP);
        quoteBox.setFocusable(true);
        quoteBox.setClickable(true);
        quoteBar = new View(this);
        quoteBar.setBackgroundColor(Ui.ACCENT_SOFT);
        quoteBar.setAlpha(0.75f);
        quoteBox.addView(quoteBar, Ui.lp(Math.max(2, Ui.px(3)), -1));
        LinearLayout qcol = Ui.column(this);
        LinearLayout.LayoutParams qcp = Ui.lpWrap();
        qcp.leftMargin = Ui.px(30);
        quoteBox.addView(qcol, qcp);
        quoteText = Ui.text(this, Ui.italic, 38, Ui.PAPER_90);
        quoteText.setLineSpacing(0, 1.16f);
        quoteText.setMaxWidth(Ui.px(1010));
        qcol.addView(quoteText, Ui.lpWrap());
        LinearLayout arow = Ui.row(this);
        quoteAuthor = Ui.eyebrow(this, "", 18, Ui.ACCENT_SOFT);
        arow.addView(quoteAuthor, Ui.lpWrap());
        quoteHint = Ui.eyebrow(this, "·  OK for another", 15, Ui.PAPER_35);
        quoteHint.setAlpha(0f);
        LinearLayout.LayoutParams qhp = Ui.lpWrap();
        qhp.leftMargin = Ui.px(18);
        arow.addView(quoteHint, qhp);
        LinearLayout.LayoutParams ap = Ui.lpWrap();
        ap.topMargin = Ui.px(20);
        qcol.addView(arow, ap);
        LinearLayout.LayoutParams qp = Ui.lpWrap();
        qp.topMargin = Ui.px(46);
        hero.addView(quoteBox, qp);

        quoteBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                quoteBar.animate().alpha(has ? 1f : 0.75f).scaleX(has ? 1.8f : 1f).setDuration(180).start();
                quoteText.setTextColor(has ? Ui.PAPER : Ui.PAPER_90);
                quoteHint.animate().alpha(has ? 1f : 0f).setDuration(180).start();
            }
        });
        quoteBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The prefs listener animates the change, same as when the phone asks for one.
                Copy.nextQuote(prefs);
            }
        });
    }

    private void buildStatus() {
        status = Ui.column(this);
        status.setGravity(Gravity.END);
        FrameLayout.LayoutParams sp = Ui.frame(-2, -2, Gravity.TOP | Gravity.END);
        sp.rightMargin = Ui.px(120);
        sp.topMargin = Ui.px(76);
        content.addView(status, sp);

        LinearLayout clockRow = Ui.row(this);
        clockRow.setGravity(Gravity.BOTTOM | Gravity.END);
        clockRow.setBaselineAligned(true);
        clock = Ui.text(this, Ui.thin, 132, Ui.PAPER);
        clock.setLetterSpacing(-0.02f);
        clock.setFontFeatureSettings("tnum");
        clockRow.addView(clock, Ui.lpWrap());
        ampm = Ui.text(this, Ui.light, 34, Ui.PAPER_70);
        LinearLayout.LayoutParams ap = Ui.lpWrap();
        ap.leftMargin = Ui.px(12);
        clockRow.addView(ampm, ap);
        status.addView(clockRow, Ui.lpWrap());

        date = Ui.text(this, Ui.regular, 30, Ui.PAPER_70);
        LinearLayout.LayoutParams dp = Ui.lpWrap();
        dp.topMargin = Ui.px(8);
        status.addView(date, dp);

        View hair = new View(this);
        hair.setBackgroundColor(Ui.PAPER_15);
        LinearLayout.LayoutParams hp = Ui.lp(Ui.px(380), Math.max(1, Ui.px(1.5f)));
        hp.topMargin = Ui.px(30);
        hp.bottomMargin = Ui.px(28);
        status.addView(hair, hp);

        LinearLayout wx = Ui.row(this);
        wxGlyph = new GlyphView(this, GlyphView.CLOUD);
        wx.addView(wxGlyph, Ui.lp(Ui.px(64), Ui.px(64)));
        wxTemp = Ui.text(this, Ui.light, 62, Ui.PAPER);
        LinearLayout.LayoutParams tp = Ui.lpWrap();
        tp.leftMargin = Ui.px(16);
        wx.addView(wxTemp, tp);
        LinearLayout wcol = Ui.column(this);
        LinearLayout.LayoutParams wcp = Ui.lpWrap();
        wcp.leftMargin = Ui.px(22);
        wx.addView(wcol, wcp);
        wxCond = Ui.text(this, Ui.regular, 26, Ui.PAPER_90);
        wcol.addView(wxCond, Ui.lpWrap());
        wxDetails = Ui.eyebrow(this, "", 16, Ui.PAPER_50);
        wxDetails.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams wdp = Ui.lpWrap();
        wdp.topMargin = Ui.px(8);
        wcol.addView(wxDetails, wdp);
        status.addView(wx, Ui.lpWrap());

        wxSun = Ui.eyebrow(this, "", 16, Ui.PAPER_35);
        wxSun.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams sunp = Ui.lpWrap();
        sunp.topMargin = Ui.px(20);
        status.addView(wxSun, sunp);

        remoteHint = Ui.eyebrow(this, "", 15, Ui.ACCENT_SOFT);
        remoteHint.setLetterSpacing(0.14f);
        remoteHint.setAlpha(0.8f);
        LinearLayout.LayoutParams rp = Ui.lpWrap();
        rp.topMargin = Ui.px(40);
        status.addView(remoteHint, rp);
    }

    /** Until a phone is paired, the screen says where the remote lives. */
    private void updateRemoteHint() {
        if (remoteHint == null) return;
        boolean paired = prefs.getString(Prefs.TOKENS, "").length() > 0;
        String ip = RemoteServer.localIp();
        if (paired || ip == null) {
            remoteHint.setVisibility(View.GONE);
        } else {
            remoteHint.setVisibility(View.VISIBLE);
            remoteHint.setText("Phone remote  \u00B7  " + ip + ":" + RemoteServer.PORT);
        }
    }

    /** Big pairing code in the corner while a phone is connecting. Doesn't take focus. */
    private void showPairing(String code) {
        if (code == null || code.length() == 0) {
            if (pairOverlay != null) {
                final View o = pairOverlay;
                pairOverlay = null;
                o.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.removeView(o);
                    }
                }).start();
            }
            return;
        }
        if (pairOverlay == null) {
            LinearLayout card = Ui.column(this);
            card.setBackground(Ui.rounded(Ui.INK_RAISED, 28));
            card.setPadding(Ui.px(44), Ui.px(36), Ui.px(44), Ui.px(36));
            card.setElevation(Ui.pxf(30));
            card.setFocusable(false);
            card.addView(Ui.eyebrow(this, "Phone remote", 16, Ui.ACCENT_SOFT), Ui.lpWrap());
            TextView t = Ui.text(this, Ui.light, 28, Ui.PAPER_90);
            t.setText("Enter this code on your iPhone");
            LinearLayout.LayoutParams tp = Ui.lpWrap();
            tp.topMargin = Ui.px(12);
            card.addView(t, tp);
            pairDigits = Ui.text(this, Ui.display, 120, Ui.PAPER);
            pairDigits.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams dp = Ui.lpWrap();
            dp.topMargin = Ui.px(14);
            card.addView(pairDigits, dp);
            FrameLayout.LayoutParams lp = Ui.frame(-2, -2, Gravity.TOP | Gravity.END);
            lp.topMargin = Ui.px(64);
            lp.rightMargin = Ui.px(100);
            root.addView(card, lp);
            card.setAlpha(0f);
            card.animate().alpha(1f).setDuration(220).start();
            pairOverlay = card;
        }
        pairDigits.setText(code);
        long until = prefs.getLong(Prefs.PAIR_UNTIL, 0);
        long left = Math.max(1000, until - System.currentTimeMillis());
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (prefs.getLong(Prefs.PAIR_UNTIL, 0) <= System.currentTimeMillis()) showPairing(null);
            }
        }, left + 500);
    }

    private void buildBottom() {
        LinearLayout bottom = Ui.column(this);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        FrameLayout.LayoutParams bp = Ui.frame(-1, -2, Gravity.BOTTOM);
        bp.bottomMargin = Ui.px(52);
        content.addView(bottom, bp);

        bottom.addView(sectionLabel("Favorites"), labelParams(0));

        favScroll = scroller();
        favRow = Ui.row(this);
        favRow.setGravity(Gravity.TOP);
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        favRow.setPadding(Ui.px(120), Ui.px(18), Ui.px(120), Ui.px(8));
        favScroll.addView(favRow, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(favScroll, new LinearLayout.LayoutParams(-1, -2));

        bottom.addView(sectionLabel("Inputs  \u00B7  System"), labelParams(14));

        inputScroll = scroller();
        inputRow = Ui.row(this);
        inputRow.setClipChildren(false);
        inputRow.setClipToPadding(false);
        inputRow.setPadding(Ui.px(120), Ui.px(14), Ui.px(120), Ui.px(14));
        inputScroll.addView(inputRow, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(inputScroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView sectionLabel(String s) {
        return Ui.eyebrow(this, s, 17, Ui.PAPER_50);
    }

    private LinearLayout.LayoutParams labelParams(float top) {
        LinearLayout.LayoutParams p = Ui.lpWrap();
        p.leftMargin = Ui.px(120);
        p.topMargin = Ui.px(top);
        return p;
    }

    private HorizontalScrollView scroller() {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setOverScrollMode(View.OVER_SCROLL_NEVER);
        h.setClipChildren(false);
        h.setClipToPadding(false);
        h.setSmoothScrollingEnabled(true);
        return h;
    }

    // ---------------------------------------------------------------- content

    private void setGreeting() {
        String name = prefs.getString(Prefs.NAME, Prefs.DEFAULT_NAME);
        SpannableString s = new SpannableString("Hello " + name + ".");
        s.setSpan(new ForegroundColorSpan(Ui.ACCENT_SOFT), s.length() - 1, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        greeting.setText(s);
    }

    private void animateQuote(final int i) {
        if (i == shownQuote) return;
        shownQuote = i;
        quoteText.animate().cancel();
        quoteText.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
            @Override
            public void run() {
                showQuote(i);
                quoteText.setTranslationY(Ui.pxf(10));
                quoteText.animate().alpha(1f).translationY(0f).setDuration(320)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }).start();
    }

    private void showQuote(int i) {
        shownQuote = i;
        String[] q = Copy.quote(prefs, i);
        int len = q[0].length();
        float size = len > 118 ? 32 : (len > 96 ? 35 : 38);
        quoteText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.pxf(size));
        quoteText.setText("“" + q[0] + "”");
        // Your own quotes without an author are signed with your name.
        quoteAuthor.setText(q[1].length() > 0 ? q[1] : prefs.getString(Prefs.NAME, Prefs.DEFAULT_NAME));
    }

    /** New quote and a new line. Runs each time the TV wakes up. */
    private void deal() {
        showQuote(Copy.nextQuote(prefs));
        long seed = System.nanoTime();
        prefs.edit().putLong(Prefs.LINE_SEED, seed).apply();
        subline.setText(Copy.line(Calendar.getInstance(), seed));
    }

    private void onWake() {
        deal();
        updateClock();
        entrance();
        if (Weather.isStale(prefs)) Weather.fetch(prefs, wxListener);
    }

    private void onMinute() {
        // Nothing to update while an app is on screen. onStart catches up.
        if (!started) return;
        updateClock();
        background.update(weather != null ? weather.sunrise : 0, weather != null ? weather.sunset : 0);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int slot = h < 4 ? 4 : (h < 7 ? 0 : (h < 12 ? 1 : (h < 17 ? 2 : (h < 21 ? 3 : 4))));
        if (lastSlotHour != -1 && slot != lastSlotHour) {
            subline.setText(Copy.line(Calendar.getInstance(), prefs.getLong(Prefs.LINE_SEED, 0)));
        }
        lastSlotHour = slot;
        if (Weather.isStale(prefs)) Weather.fetch(prefs, wxListener);
        if (weather != null) applyWeather();
    }

    private void updateClock() {
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(h24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now));
        ampm.setText(h24 ? "" : new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US));
        ampm.setVisibility(h24 ? View.GONE : View.VISIBLE);
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));

        Calendar c = Calendar.getInstance();
        int day = Copy.dayOfYear(c), total = Copy.daysInYear(c);
        int left = total - day;
        dayText.setText("Day " + day + " of " + total);
        daysLeft.setText(left == 1 ? "1 day left" : left + " days left");
        final float frac = day / (float) total;
        View track = (View) progressFill.getParent();
        ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
        lp.width = Math.max(Ui.px(4), Math.round(Ui.px(150) * frac));
        progressFill.setLayoutParams(lp);
        track.requestLayout();
    }

    private final Weather.Listener wxListener = new Weather.Listener() {
        @Override
        public void onWeather(Weather w) {
            weather = w;
            applyWeather();
        }
    };

    private void applyWeather() {
        String city = prefs.getString(Prefs.CITY, Prefs.DEFAULT_CITY);
        if (weather == null || !weather.valid()) {
            wxGlyph.setGlyph(GlyphView.CLOUD);
            wxGlyph.setAlpha(0.4f);
            wxTemp.setText("");
            wxCond.setText("Checking the sky");
            wxDetails.setText(city);
            wxSun.setText("");
            background.update(0, 0);
            return;
        }
        Weather w = weather;
        wxGlyph.setAlpha(1f);
        wxGlyph.setGlyph(w.glyph());
        wxTemp.setText(Weather.deg(w.temp));
        wxCond.setText(w.condition());
        wxDetails.setText("H " + Weather.deg(w.high) + "   L " + Weather.deg(w.low) + "   ·   " + city);
        long now = System.currentTimeMillis();
        SimpleDateFormat hm = new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm a", Locale.US);
        String sun;
        if (w.sunrise > 0 && now < w.sunrise) sun = "Sunrise " + hm.format(new Date(w.sunrise));
        else if (w.sunset > 0 && now < w.sunset) sun = "Sunset " + hm.format(new Date(w.sunset));
        else sun = "Feels like " + Weather.deg(w.feelsLike);
        if (w.rainChance >= 20) sun += "   ·   Rain " + w.rainChance + "%";
        wxSun.setText(sun);
        background.update(w.sunrise, w.sunset);
    }

    private void entrance() {
        View[] views = {hero.getChildAt(0), greeting, subline, quoteBox, status};
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(Ui.pxf(18));
            v.animate().alpha(1f).translationY(0f).setStartDelay(90L * i).setDuration(650)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    // ---------------------------------------------------------------- rows

    private void refreshFavorites(boolean force) {
        List<String> favs = Apps.favorites(this);
        List<Apps.App> apps = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (String pkg : favs) {
            Apps.App a = Apps.load(this, pkg);
            // Pinned apps you've since uninstalled disappear, unless they're part of your core lineup.
            if (!a.installed && Apps.knownName(pkg).equals(pkg)) continue;
            apps.add(a);
            sig.append(pkg).append(a.installed ? '+' : '-').append(',');
        }
        if (!force && sig.toString().equals(favSignature)) return;
        favSignature = sig.toString();

        String focusedPkg = null;
        View f = getWindow().getDecorView().findFocus();
        if (f instanceof Tile) focusedPkg = ((Tile) f).app.pkg;

        favRow.removeAllViews();
        Tile refocus = null;
        for (int i = 0; i < apps.size(); i++) {
            final Apps.App a = apps.get(i);
            final Tile t = new Tile(this, a, false);
            LinearLayout.LayoutParams lp = Ui.lpWrap();
            if (i < apps.size() - 1) lp.rightMargin = Ui.px(28);
            favRow.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.launch(HomeActivity.this, a);
                }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    favoriteOptions(a);
                    return true;
                }
            });
            if (a.pkg.equals(focusedPkg)) refocus = t;
        }
        if (refocus != null) refocus.requestFocus();
        else if (f instanceof Tile || f == null) focusFirst();
    }

    private void favoriteOptions(final Apps.App a) {
        final List<String> favs = Apps.favorites(this);
        final int idx = favs.indexOf(a.pkg);
        final String[] items = a.installed
                ? new String[]{"Move left", "Move right", "Remove from favorites", "App info"}
                : new String[]{"Install from Google Play", "Remove from favorites"};
        sheet = Sheet.show(this, root, content, a.label, items, new Sheet.Pick() {
            @Override
            public void onPick(int i) {
                sheet = null;
                String choice = items[i];
                if (choice.startsWith("Install")) {
                    Apps.openStore(HomeActivity.this, a.pkg);
                } else if (choice.equals("App info")) {
                    Apps.openAppInfo(HomeActivity.this, a.pkg);
                } else if (choice.startsWith("Remove") && idx >= 0) {
                    favs.remove(idx);
                    Apps.saveFavorites(HomeActivity.this, favs);
                    refreshFavorites(true);
                    focusFirst();
                } else if (choice.equals("Move left") && idx > 0) {
                    favs.remove(idx);
                    favs.add(idx - 1, a.pkg);
                    Apps.saveFavorites(HomeActivity.this, favs);
                    refreshFavorites(true);
                } else if (choice.equals("Move right") && idx >= 0 && idx < favs.size() - 1) {
                    favs.remove(idx);
                    favs.add(idx + 1, a.pkg);
                    Apps.saveFavorites(HomeActivity.this, favs);
                    refreshFavorites(true);
                }
            }
        });
    }

    private void closeSheet() {
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
        }
    }

    private void refreshInputs() {
        List<Apps.Input> inputs = Apps.hdmiInputs(this);
        StringBuilder sig = new StringBuilder();
        for (Apps.Input in : inputs) sig.append(in.id).append('|').append(in.title).append('|').append(in.device).append(',');
        if (sig.toString().equals(inputSignature) && inputRow.getChildCount() > 0) return;
        inputSignature = sig.toString();
        inputRow.removeAllViews();

        for (final Apps.Input in : inputs) {
            Pill p = new Pill(this, GlyphView.HDMI, in.title, in.device);
            addPill(p);
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.switchTo(HomeActivity.this, in);
                }
            });
        }
        if (!inputs.isEmpty()) {
            View div = new View(this);
            div.setBackgroundColor(Ui.PAPER_15);
            LinearLayout.LayoutParams dp = Ui.lp(Math.max(1, Ui.px(1.5f)), Ui.px(40));
            dp.leftMargin = Ui.px(10);
            dp.rightMargin = Ui.px(30);
            inputRow.addView(div, dp);
        } else {
            // This TV doesn't share its inputs with apps. Settings > Inputs still works.
            Pill p = new Pill(this, GlyphView.HDMI, "Inputs", "Opens TV settings");
            addPill(p);
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Apps.openSettings(HomeActivity.this);
                }
            });
        }

        Pill all = new Pill(this, GlyphView.GRID, "All apps", null);
        addPill(all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, AppsActivity.class));
            }
        });
        Pill settings = new Pill(this, GlyphView.SLIDERS, "Settings", null);
        addPill(settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.openSettings(HomeActivity.this);
            }
        });
    }

    private void addPill(Pill p) {
        LinearLayout.LayoutParams lp = Ui.lp(-2, Ui.px(Pill.H));
        lp.rightMargin = Ui.px(20);
        inputRow.addView(p, lp);
    }

    private void focusFirst() {
        favRow.post(new Runnable() {
            @Override
            public void run() {
                if (sheet != null) return;
                if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
                else if (inputRow.getChildCount() > 0) inputRow.getChildAt(0).requestFocus();
            }
        });
    }
}
