package com.sistema.home;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.TimeZone;

/**
 * Boots the real launcher on Robolectric at 1920x1080, drives it like a remote would,
 * and writes screenshots. If this passes, the launcher won't crash on the TV.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HomeScreenTest {

    /** Renders land here so you can see the screen without a TV. Gradle sets it to app/build/preview. */
    static final String OUT = System.getProperty("preview.out", "build/preview");

    static final Object[][] FAKE = {
            {"com.google.android.youtube.tv", "YouTube", 0xFFE62117},
            {"com.spotify.tv.android", "Spotify", 0xFF1DB954},
            {"com.cbs.ott", "Paramount+", 0xFF0064FF},
            {"com.netflix.ninja", "Netflix", 0xFFB20710},
            {"com.wbd.stream", "HBO Max", 0xFF5822B4},
            {"com.google.android.youtube.tvunplugged", "YouTube TV", 0xFFCC0000},
            {"com.android.vending", "Google Play Store", 0xFF01875F},
            {"com.plexapp.android", "Plex", 0xFFE5A00D},
    };

    private void install(Context c) {
        ShadowPackageManager spm = shadowOf(c.getPackageManager());
        for (Object[] f : FAKE) {
            String pkg = (String) f[0];
            PackageInfo pi = new PackageInfo();
            pi.packageName = pkg;
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = pkg;
            ai.enabled = true;
            ai.nonLocalizedLabel = (String) f[1];
            pi.applicationInfo = ai;
            spm.installPackage(pi);
            ComponentName cn = new ComponentName(pkg, pkg + ".Main");
            spm.addActivityIfNotPresent(cn);
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            spm.addIntentFilterForActivity(cn, filter);
            spm.setApplicationIcon(pkg, icon(c, (String) f[1], (Integer) f[2]));
        }
    }

    private BitmapDrawable icon(Context c, String label, int color) {
        Bitmap b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        cv.drawRoundRect(new RectF(0, 0, 128, 128), 30, 30, p);
        p.setColor(Color.WHITE);
        p.setTextSize(64);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        cv.drawText(label.substring(0, 1), 64, 86, p);
        return new BitmapDrawable(c.getResources(), b);
    }

    private static Apps.Input input(String id, String title, String device) {
        Apps.Input in = new Apps.Input();
        in.id = id;
        in.title = title;
        in.device = device;
        return in;
    }

    private void fakeInputs() {
        Apps.testInputs = java.util.Arrays.asList(
                input("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", "HDMI 1", "PlayStation 5"),
                input("com.mediatek.tvinput/.hdmi.HDMIInputService/HW6", "HDMI 2", null),
                input("com.mediatek.tvinput/.hdmi.HDMIInputService/HW7", "HDMI 3", "Apple TV"),
                input("com.mediatek.tvinput/.hdmi.HDMIInputService/HW8", "HDMI 4", null));
    }

    @org.junit.After
    public void clearInputs() {
        Apps.testInputs = null;
    }

    private void seedWeather(Context c) {
        String json = "{\"utc_offset_seconds\":-18000,\"current\":{\"temperature_2m\":81.3,\"apparent_temperature\":86.1,"
                + "\"weather_code\":2,\"is_day\":1},\"daily\":{\"weather_code\":[2],\"temperature_2m_max\":[84.2],"
                + "\"temperature_2m_min\":[73.4],\"sunrise\":[\"2026-11-09T06:39\"],\"sunset\":[\"2026-11-09T17:35\"],"
                + "\"precipitation_probability_max\":[20]}}";
        Prefs.get(c).edit().putString(Prefs.WX_JSON, json).putLong(Prefs.WX_TIME, System.currentTimeMillis()).apply();
    }

    private static View focused(android.app.Activity a) {
        return a.getWindow().getDecorView().findFocus();
    }

    /** What the framework does with a D-pad press: geometric focus search from the focused view. */
    private void move(android.app.Activity a, int dir) {
        View f = focused(a);
        assertNotNull("nothing focused before move", f);
        View next = f.focusSearch(dir);
        if (next != null) next.requestFocus(dir);
    }

    private void dpadMode() {
        // A TV remote never puts Android in touch mode. Robolectric starts in it, so turn it off.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
    }

    private void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private void snap(View root, String name) throws Exception {
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File(OUT);
        dir.mkdirs();
        File f = new File(dir, name);
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        System.out.println("wrote " + f + " " + b.getWidth() + "x" + b.getHeight());
    }

    @Test
    public void home() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Context c = RuntimeEnvironment.getApplication();
        install(c);
        seedWeather(c);
        fakeInputs();
        dpadMode();

        ActivityController<HomeActivity> ctl = Robolectric.buildActivity(HomeActivity.class).setup();
        HomeActivity a = ctl.get();
        idle(2500);
        View root = a.getWindow().getDecorView();
        assertTrue(root.getWidth() > 0);
        // The window manager hands the launcher focus on a real TV; Robolectric needs a nudge.
        a.onWindowFocusChanged(true);
        idle(500);
        View focus = focused(a);
        assertNotNull("something must hold focus", focus);
        assertTrue("first favorite should be focused, was " + focus, focus instanceof Tile);
        assertEquals("com.google.android.youtube.tv", ((Tile) focus).app.pkg);
        snap(root, "home.png");

        // Walk right across favorites, then down to inputs, then up to the quote.
        for (int i = 0; i < 3; i++) {
            move(a, View.FOCUS_RIGHT);
            idle(400);
        }
        assertEquals("com.netflix.ninja", ((Tile) focused(a)).app.pkg);
        snap(root, "home_netflix.png");
        move(a, View.FOCUS_DOWN);
        idle(400);
        assertTrue("down from favorites lands on a pill, got " + focused(a), focused(a) instanceof Pill);
        snap(root, "home_inputs.png");
        // Walk left to HDMI 1 and press OK: it must ask the TV to switch to that passthrough input.
        for (int i = 0; i < 8; i++) move(a, View.FOCUS_LEFT);
        focused(a).performClick();
        Intent sent = shadowOf(a).getNextStartedActivity();
        assertNotNull("HDMI pill starts the TV input", sent);
        assertEquals("content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW5",
                String.valueOf(sent.getData()));
        snap(root, "home_hdmi.png");
        move(a, View.FOCUS_RIGHT);
        move(a, View.FOCUS_RIGHT);
        move(a, View.FOCUS_RIGHT);

        // Menu key on a favorite opens the options sheet; back closes it.
        move(a, View.FOCUS_UP);
        idle(300);
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        idle(400);
        assertTrue("sheet pill focused", focused(a) instanceof Pill);
        snap(root, "home_sheet.png");
        a.onBackPressed();
        idle(300);
        assertTrue(focused(a) instanceof Tile);

        // Up twice reaches the quote; OK deals a new one.
        move(a, View.FOCUS_UP);
        idle(300);
        assertTrue("up from favorites reaches the quote, got " + focused(a), focused(a) instanceof android.widget.LinearLayout && !(focused(a) instanceof Tile) && !(focused(a) instanceof Pill));
        snap(root, "home_quote.png");
        focused(a).performClick();
        idle(800);

        // Lifecycle: leave and come back, simulate the screen waking.
        ctl.pause().stop();
        idle(100);
        ctl.restart().start().resume();
        c.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        c.sendBroadcast(new Intent(Intent.ACTION_TIME_TICK));
        idle(2000);
        ctl.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .putExtra("city", "Brickell").putExtra("lat", "25.76").putExtra("lon", "-80.19"));
        idle(500);
        assertEquals("Brickell", Prefs.get(c).getString(Prefs.CITY, null));
        ctl.pause().stop().destroy();
    }

    @Test
    public void allApps() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Context c = RuntimeEnvironment.getApplication();
        install(c);
        seedWeather(c);
        dpadMode();
        ActivityController<AppsActivity> ctl = Robolectric.buildActivity(AppsActivity.class).setup();
        // Apps load on a background thread.
        for (int i = 0; i < 50 && !(focused(ctl.get()) instanceof Tile); i++) {
            ctl.get().onWindowFocusChanged(true);
            Thread.sleep(50);
            idle(100);
        }
        idle(1500);
        assertTrue("grid focused", focused(ctl.get()) instanceof Tile);
        snap(ctl.get().getWindow().getDecorView(), "all_apps.png");
        ctl.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        ctl.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        idle(300);
        focused(ctl.get()).performClick();
        idle(300);
        assertTrue("pinned from All apps", Prefs.get(c).getString(Prefs.FAVORITES, "").contains("com.android.vending"));
        ctl.pause().stop().destroy();
    }

    @Test
    public void weatherParsing() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        seedWeather(c);
        Weather w = Weather.fromCache(Prefs.get(c));
        assertNotNull(w);
        assertEquals(2, w.code);
        assertEquals("Partly cloudy", w.condition());
        assertEquals(GlyphView.PARTLY_DAY, w.glyph());
        assertEquals(20, w.rainChance);
        assertTrue(w.sunset > w.sunrise);
        // 17:35 local at UTC-5 is 22:35 UTC.
        assertEquals(22 * 60 + 35, (w.sunset / 60000) % (24 * 60));
    }

    @Test
    public void quotesDealWithoutRepeats() {
        Context c = RuntimeEnvironment.getApplication();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < Copy.QUOTES.length; i++) seen.add(Copy.nextQuote(Prefs.get(c)));
        assertEquals(Copy.QUOTES.length, seen.size());
        for (String[] q : Copy.QUOTES) {
            assertFalse("no dashes in copy: " + q[0], q[0].contains("—") || q[0].contains("–") || q[0].contains(" - "));
        }
    }
}
