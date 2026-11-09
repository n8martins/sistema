package com.sistema.home;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** The phone remote's API, end to end over real HTTP, with the TV's ADB unavailable. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public class RemoteServerTest {

    private Context c;
    private TvControl tv;
    private RemoteServer server;
    private String base;

    static final class Res {
        int code;
        String body;
        byte[] raw;
        String type;

        JSONObject json() throws Exception {
            return new JSONObject(body);
        }
    }

    @Before
    public void up() throws Exception {
        c = RuntimeEnvironment.getApplication();
        ShadowPackageManager spm = shadowOf(c.getPackageManager());
        String[][] apps = {{"com.netflix.ninja", "Netflix"}, {"com.google.android.youtube.tv", "YouTube"}, {"com.plexapp.android", "Plex"}};
        for (String[] a : apps) {
            PackageInfo pi = new PackageInfo();
            pi.packageName = a[0];
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = a[0];
            ai.enabled = true;
            ai.nonLocalizedLabel = a[1];
            pi.applicationInfo = ai;
            spm.installPackage(pi);
            ComponentName cn = new ComponentName(a[0], a[0] + ".Main");
            spm.addActivityIfNotPresent(cn);
            IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
            f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            spm.addIntentFilterForActivity(cn, f);
        }
        TvControl.adbPort = 1; // nothing listens there: ADB is "off" for these tests
        tv = new TvControl(c);
        server = new RemoteServer(c, tv);
        server.start(0);
        base = "http://127.0.0.1:" + server.port();
    }

    @After
    public void down() {
        server.stop();
        tv.shutdown();
        TvControl.adbPort = 5555;
    }

    private Res call(String method, String path, String token, String body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(base + path).openConnection();
        h.setRequestMethod(method);
        if (token != null) h.setRequestProperty("X-Sistema-Token", token);
        if (body != null) {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            OutputStream o = h.getOutputStream();
            o.write(body.getBytes(StandardCharsets.UTF_8));
            o.close();
        }
        Res r = new Res();
        r.code = h.getResponseCode();
        r.type = h.getContentType();
        InputStream in = r.code < 400 ? h.getInputStream() : h.getErrorStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        }
        r.raw = b.toByteArray();
        r.body = new String(r.raw, StandardCharsets.UTF_8);
        return r;
    }

    private String pair() throws Exception {
        assertEquals(200, call("POST", "/api/pair/start", null, "{}").code);
        shadowOf(Looper.getMainLooper()).idle();
        String code = Prefs.get(c).getString(Prefs.PAIR_CODE, null);
        assertNotNull("TV holds a pairing code", code);
        assertEquals(4, code.length());
        Res bad = call("POST", "/api/pair", null, "{\"code\":\"" + (code.equals("0000") ? "1111" : "0000") + "\"}");
        assertEquals(403, bad.code);
        Res ok = call("POST", "/api/pair", null, "{\"code\":\"" + code + "\"}");
        assertEquals(200, ok.code);
        String token = ok.json().getString("token");
        assertTrue(token.length() >= 32);
        assertNull("code is used up", Prefs.get(c).getString(Prefs.PAIR_CODE, null));
        return token;
    }

    @Test
    public void servesThePhoneApp() throws Exception {
        Res page = call("GET", "/", null, null);
        assertEquals(200, page.code);
        assertTrue(page.type.startsWith("text/html"));
        assertTrue(page.body.contains("Sistema Remote"));
        assertEquals(200, call("GET", "/app.js", null, null).code);
        assertEquals(200, call("GET", "/style.css", null, null).code);
        assertEquals(200, call("GET", "/manifest.webmanifest", null, null).code);
        Res font = call("GET", "/fonts/fraunces_display.ttf", null, null);
        assertEquals(200, font.code);
        assertEquals("font/ttf", font.type);
        assertEquals(404, call("GET", "/../AndroidManifest.xml", null, null).code);
        assertEquals(404, call("GET", "/fonts/..%2Fremote%2Fapp.js", null, null).code);
    }

    @Test
    public void everythingNeedsPairing() throws Exception {
        assertEquals(200, call("GET", "/api/health", null, null).code);
        assertFalse(call("GET", "/api/health", null, null).json().getBoolean("paired"));
        assertEquals(401, call("GET", "/api/state", null, null).code);
        assertEquals(401, call("POST", "/api/key", "nope-nope-nope-nope", "{\"key\":\"home\"}").code);
        assertEquals(401, call("GET", "/api/icon?pkg=com.netflix.ninja", null, null).code);
        assertEquals(409, call("POST", "/api/pair", null, "{\"code\":\"1234\"}").code);
    }

    @Test
    public void pairedPhoneControlsAndCustomizes() throws Exception {
        String t = pair();
        assertTrue(call("GET", "/api/health", t, null).json().getBoolean("paired"));

        JSONObject s = call("GET", "/api/state", t, null).json();
        assertEquals("Nicolas", s.getString("name"));
        assertEquals("Miami", s.getString("city"));
        assertTrue(s.getJSONArray("apps").length() >= 3);
        assertTrue(s.getJSONArray("favorites").length() >= 6);
        assertTrue(s.getJSONObject("quote").getString("text").length() > 0);

        // Settings land in prefs, which the home screen listens to.
        assertEquals(200, call("POST", "/api/settings", t, "{\"name\":\"Nico\",\"city\":\"Brickell\",\"lat\":\"25.76\",\"lon\":\"-80.19\",\"units\":\"c\"}").code);
        assertEquals("Nico", Prefs.get(c).getString(Prefs.NAME, null));
        assertEquals("Brickell", Prefs.get(c).getString(Prefs.CITY, null));
        assertEquals("c", Prefs.get(c).getString(Prefs.UNITS, null));
        assertEquals(400, call("POST", "/api/settings", t, "{\"lat\":\"999\",\"lon\":\"0\"}").code);
        assertEquals(400, call("POST", "/api/settings", t, "{\"units\":\"k\"}").code);

        // Favorites: order and membership.
        assertEquals(200, call("POST", "/api/favorites", t, "{\"favorites\":[\"com.plexapp.android\",\"com.netflix.ninja\",\"bad pkg!\"]}").code);
        JSONArray favs = call("GET", "/api/state", t, null).json().getJSONArray("favorites");
        assertEquals(2, favs.length());
        assertEquals("com.plexapp.android", favs.getJSONObject(0).getString("pkg"));
        assertEquals("Plex", favs.getJSONObject(0).getString("label"));

        // Your own quote goes straight onto the TV.
        assertEquals(200, call("POST", "/api/quotes", t, "{\"add\":{\"text\":\"Become the man the kid would look up to.\",\"author\":\"\"}}").code);
        JSONObject q = call("GET", "/api/state", t, null).json();
        assertEquals("Become the man the kid would look up to.", q.getJSONObject("quote").getString("text"));
        assertEquals(1, q.getJSONArray("customQuotes").length());
        assertEquals(200, call("POST", "/api/quote/next", t, "{}").code);
        assertEquals(200, call("POST", "/api/quotes", t, "{\"remove\":0}").code);
        assertEquals(0, call("GET", "/api/state", t, null).json().getJSONArray("customQuotes").length());

        // Sleep timer.
        assertEquals(200, call("POST", "/api/sleep", t, "{\"minutes\":30}").code);
        long at = call("GET", "/api/state", t, null).json().getLong("sleepAt");
        assertTrue(at > System.currentTimeMillis() + 29 * 60_000L);
        assertEquals(200, call("POST", "/api/sleep", t, "{\"minutes\":0}").code);
        assertEquals(0, call("GET", "/api/state", t, null).json().getLong("sleepAt"));

        // Without the TV's ADB, D-pad keys explain what to do; volume still works.
        Res k = call("POST", "/api/key", t, "{\"key\":\"right\"}");
        assertEquals(503, k.code);
        assertTrue(k.json().getString("error").length() > 10);
        assertEquals(200, call("POST", "/api/key", t, "{\"key\":\"volup\"}").code);
        assertEquals(400, call("POST", "/api/key", t, "{\"key\":\"selfdestruct\"}").code);

        // Icons render to PNG.
        Res icon = call("GET", "/api/icon?pkg=com.netflix.ninja&t=" + t, null, null);
        assertEquals(200, icon.code);
        assertEquals("image/png", icon.type);
        assertEquals((byte) 0x89, icon.raw[0]);
        assertEquals('P', icon.raw[1]);

        // Forgetting a phone revokes it.
        assertEquals(200, call("POST", "/api/forget", t, "{}").code);
        assertEquals(401, call("GET", "/api/state", t, null).code);
    }

    @Test
    public void textIsEscapedForTheShell() {
        assertEquals("rock%sand%sroll", TvControl.escapeInputText("rock and roll"));
        assertEquals("'it'\\''s'", TvControl.shellQuote("it's"));
        assertEquals("'a;b && rm -rf /'", TvControl.shellQuote("a;b && rm -rf /"));
    }
}
