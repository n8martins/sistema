package com.sistema.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny web server on the TV. It serves the phone remote (assets/remote) and a JSON API the
 * remote calls. Only phones on your Wi-Fi can reach it, and only after pairing with the code
 * the TV shows on screen.
 */
final class RemoteServer {

    static final int PORT = 8737;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long PAIR_WINDOW_MS = 120_000L;
    private static final int MAX_BODY = 64 * 1024;

    private final Context app;
    private final TvControl tv;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final Map<String, byte[]> iconCache = Collections.synchronizedMap(new LinkedHashMap<String, byte[]>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 80;
        }
    });
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private int wrongCodes;

    RemoteServer(Context context, TvControl tv) {
        this.app = context.getApplicationContext();
        this.tv = tv;
        this.prefs = Prefs.get(app);
    }

    void start(int port) throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        pool = Executors.newFixedThreadPool(4);
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket s = server.accept();
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                serve(s);
                            }
                        });
                    } catch (IOException e) {
                        if (!running) return;
                    }
                }
            }
        }, "remote-server");
        t.setDaemon(true);
        t.start();
    }

    int port() {
        return server == null ? -1 : server.getLocalPort();
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        if (pool != null) pool.shutdownNow();
    }

    /** The TV's address on your home network, for showing on screen. */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- http

    static final class Request {
        String method, path;
        Map<String, String> query = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];

        JSONObject json() {
            try {
                return body.length == 0 ? new JSONObject() : new JSONObject(new String(body, UTF8));
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }

    static final class Response {
        int status = 200;
        String type = "application/json; charset=utf-8";
        byte[] body = new byte[0];
        String cache = "no-store";

        static Response json(int status, JSONObject o) {
            Response r = new Response();
            r.status = status;
            r.body = o.toString().getBytes(UTF8);
            return r;
        }

        static Response error(int status, String message) {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", false).put("error", message);
            } catch (JSONException ignored) {
            }
            return json(status, o);
        }
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(10_000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            Request req = parse(in);
            Response res = req == null ? Response.error(400, "Bad request") : route(req);
            write(s.getOutputStream(), res);
        } catch (Exception e) {
            try {
                write(s.getOutputStream(), Response.error(500, "Something went wrong on the TV"));
            } catch (Exception ignored) {
            }
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("Line too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), UTF8);
    }

    static Request parse(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) return null;
        String[] parts = line.split(" ");
        if (parts.length < 2) return null;
        Request r = new Request();
        r.method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = q >= 0 ? target.substring(0, q) : target;
        if (q >= 0) {
            for (String pair : target.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                r.query.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            }
        }
        int headers = 0;
        String h;
        while ((h = readLine(in)) != null && h.length() > 0) {
            if (++headers > 100) return null;
            int c = h.indexOf(':');
            if (c > 0) r.headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
        }
        String len = r.headers.get("content-length");
        if (len != null) {
            int n = Integer.parseInt(len.trim());
            if (n < 0 || n > MAX_BODY) return null;
            byte[] body = new byte[n];
            int off = 0;
            while (off < n) {
                int got = in.read(body, off, n - off);
                if (got < 0) break;
                off += got;
            }
            r.body = body;
        }
        return r;
    }

    private static void write(OutputStream out, Response r) throws IOException {
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\n"
                + "Content-Type: " + r.type + "\r\n"
                + "Content-Length: " + r.body.length + "\r\n"
                + "Cache-Control: " + r.cache + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(r.body);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 503: return "Service Unavailable";
            default: return "Error";
        }
    }

    // ---------------------------------------------------------------- routes

    Response route(Request r) {
        String p = r.path;
        if ("GET".equals(r.method) && !p.startsWith("/api/")) return staticFile(p);
        try {
            if (p.equals("/api/health")) return health(r);
            if (p.equals("/api/pair/start") && "POST".equals(r.method)) return pairStart();
            if (p.equals("/api/pair") && "POST".equals(r.method)) return pair(r.json());
            if (!authorized(r)) return Response.error(401, "Pair this phone first");
            if (p.equals("/api/state")) return state();
            if (p.equals("/api/icon")) return icon(r.query.get("pkg"), "banner".equals(r.query.get("kind")));
            if (!"POST".equals(r.method)) return Response.error(404, "Not found");
            JSONObject b = r.json();
            switch (p) {
                case "/api/key":
                    tv.key(b.optString("key"), b.optBoolean("long"));
                    return ok();
                case "/api/text":
                    tv.text(b.optString("text"));
                    return ok();
                case "/api/launch":
                    tv.launch(b.optString("pkg"));
                    return ok();
                case "/api/input":
                    tv.switchInput(b.optString("id"));
                    return ok();
                case "/api/settings":
                    return settings(b);
                case "/api/favorites":
                    return favorites(b);
                case "/api/quote/next":
                    Copy.nextQuote(prefs);
                    return ok();
                case "/api/quotes":
                    return quotes(b);
                case "/api/sleep":
                    tv.sleepIn(b.optInt("minutes"));
                    return ok();
                case "/api/forget":
                    forget(r.headers.get("x-sistema-token"));
                    return ok();
                default:
                    return Response.error(404, "Not found");
            }
        } catch (TvControl.NotReady e) {
            return Response.error(503, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Response.error(400, e.getMessage());
        } catch (JSONException e) {
            return Response.error(500, "Couldn't build the response");
        }
    }

    private static Response ok() {
        try {
            return Response.json(200, new JSONObject().put("ok", true));
        } catch (JSONException e) {
            return Response.error(500, "json");
        }
    }

    private Response staticFile(String path) {
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        String name = path.substring(1);
        String asset;
        if (name.startsWith("fonts/")) asset = name;
        else asset = "remote/" + name;
        if (!asset.matches("(remote|fonts)/[A-Za-z0-9_.-]+")) return Response.error(404, "Not found");
        try {
            InputStream in = app.getAssets().open(asset);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            in.close();
            Response r = new Response();
            r.body = o.toByteArray();
            r.type = mime(asset);
            r.cache = asset.endsWith(".ttf") || asset.endsWith(".png") ? "public, max-age=604800" : "no-cache";
            return r;
        } catch (IOException e) {
            return Response.error(404, "Not found");
        }
    }

    private static String mime(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".webmanifest")) return "application/manifest+json";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- pairing

    private boolean authorized(Request r) {
        String token = r.headers.get("x-sistema-token");
        // Image tags can't send headers, so icons may carry the token in the URL instead.
        if (token == null && "/api/icon".equals(r.path)) token = r.query.get("t");
        if (token == null || token.length() < 16) return false;
        for (String t : prefs.getString(Prefs.TOKENS, "").split(",")) {
            if (t.equals(token)) return true;
        }
        return false;
    }

    private Response health(Request r) throws JSONException {
        if ("1".equals(r.query.get("warm"))) tv.warmUp();
        JSONObject o = new JSONObject()
                .put("ok", true)
                .put("app", "sistema-home")
                .put("paired", authorized(r))
                .put("adb", tv.adbState())
                .put("adbError", tv.adbError());
        return Response.json(200, o);
    }

    private synchronized Response pairStart() throws JSONException {
        String code = prefs.getString(Prefs.PAIR_CODE, null);
        long until = prefs.getLong(Prefs.PAIR_UNTIL, 0);
        if (code == null || until < System.currentTimeMillis()) {
            code = String.format(Locale.ROOT, "%04d", random.nextInt(10000));
            until = System.currentTimeMillis() + PAIR_WINDOW_MS;
            wrongCodes = 0;
            prefs.edit().putString(Prefs.PAIR_CODE, code).putLong(Prefs.PAIR_UNTIL, until).apply();
        }
        final String shown = code;
        main.post(new Runnable() {
            @Override
            public void run() {
                // Shows over any app. The home screen also shows it large while it's open.
                Toast.makeText(app, "Phone remote code: " + shown, Toast.LENGTH_LONG).show();
            }
        });
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (shown.equals(prefs.getString(Prefs.PAIR_CODE, null))) {
                    Toast.makeText(app, "Phone remote code: " + shown, Toast.LENGTH_LONG).show();
                }
            }
        }, 3800);
        return Response.json(200, new JSONObject().put("ok", true).put("seconds", PAIR_WINDOW_MS / 1000));
    }

    private synchronized Response pair(JSONObject b) throws JSONException {
        String code = prefs.getString(Prefs.PAIR_CODE, null);
        long until = prefs.getLong(Prefs.PAIR_UNTIL, 0);
        if (code == null || until < System.currentTimeMillis()) return Response.error(409, "The code expired. Ask for a new one.");
        if (!code.equals(b.optString("code").trim())) {
            if (++wrongCodes >= 5) prefs.edit().remove(Prefs.PAIR_CODE).remove(Prefs.PAIR_UNTIL).apply();
            return Response.error(403, "That code doesn't match the TV.");
        }
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        StringBuilder token = new StringBuilder();
        for (byte x : raw) token.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        List<String> tokens = new ArrayList<>();
        for (String t : prefs.getString(Prefs.TOKENS, "").split(",")) if (t.length() > 0) tokens.add(t);
        tokens.add(token.toString());
        while (tokens.size() > 8) tokens.remove(0);
        prefs.edit()
                .putString(Prefs.TOKENS, android.text.TextUtils.join(",", tokens))
                .remove(Prefs.PAIR_CODE).remove(Prefs.PAIR_UNTIL)
                .apply();
        tv.warmUp();
        return Response.json(200, new JSONObject().put("ok", true).put("token", token.toString()));
    }

    private void forget(String token) {
        List<String> keep = new ArrayList<>();
        for (String t : prefs.getString(Prefs.TOKENS, "").split(",")) if (t.length() > 0 && !t.equals(token)) keep.add(t);
        prefs.edit().putString(Prefs.TOKENS, android.text.TextUtils.join(",", keep)).apply();
    }

    // ---------------------------------------------------------------- state

    private Response state() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("name", prefs.getString(Prefs.NAME, Prefs.DEFAULT_NAME));
        o.put("city", prefs.getString(Prefs.CITY, Prefs.DEFAULT_CITY));
        o.put("lat", prefs.getString(Prefs.LAT, Prefs.DEFAULT_LAT));
        o.put("lon", prefs.getString(Prefs.LON, Prefs.DEFAULT_LON));
        o.put("units", prefs.getString(Prefs.UNITS, "f"));

        JSONArray favs = new JSONArray();
        List<String> favPkgs = Apps.favorites(app);
        for (String pkg : favPkgs) {
            Apps.App a = Apps.load(app, pkg, false);
            if (!a.installed && Apps.knownName(pkg).equals(pkg)) continue;
            favs.put(new JSONObject().put("pkg", pkg).put("label", a.label).put("installed", a.installed));
        }
        o.put("favorites", favs);

        JSONArray all = new JSONArray();
        for (Apps.App a : Apps.all(app, false)) {
            all.put(new JSONObject().put("pkg", a.pkg).put("label", a.label).put("favorite", favPkgs.contains(a.pkg)));
        }
        o.put("apps", all);

        JSONArray inputs = new JSONArray();
        for (Apps.Input in : Apps.hdmiInputs(app)) {
            JSONObject i = new JSONObject().put("id", in.id).put("title", in.title);
            if (in.device != null) i.put("device", in.device);
            inputs.put(i);
        }
        o.put("inputs", inputs);

        String[] q = Copy.quote(prefs, Copy.currentQuote(prefs));
        o.put("quote", new JSONObject().put("text", q[0]).put("author", q[1]));
        JSONArray mine = new JSONArray();
        for (String[] c : Copy.custom(prefs)) mine.put(new JSONObject().put("text", c[0]).put("author", c[1]));
        o.put("customQuotes", mine);

        o.put("sleepAt", tv.sleepAt());
        o.put("now", System.currentTimeMillis());
        o.put("adb", tv.adbState());
        o.put("adbError", tv.adbError());
        String fg = tv.foregroundPackage();
        if (fg != null) {
            String label = fg;
            if (fg.equals(app.getPackageName())) label = "Home";
            else {
                Apps.App a = Apps.load(app, fg, false);
                if (a.installed) label = a.label;
            }
            o.put("foreground", new JSONObject().put("pkg", fg).put("label", label));
        }
        String ip = localIp();
        o.put("address", ip == null ? "" : ip + ":" + PORT);
        return Response.json(200, o);
    }

    private Response settings(JSONObject b) throws JSONException {
        SharedPreferences.Editor e = prefs.edit();
        boolean location = false;
        if (b.has("name")) {
            String n = b.optString("name").trim();
            if (n.length() == 0 || n.length() > 40) return Response.error(400, "Name should be 1 to 40 characters.");
            e.putString(Prefs.NAME, n);
        }
        if (b.has("city")) {
            String c = b.optString("city").trim();
            if (c.length() == 0 || c.length() > 60) return Response.error(400, "City name is too long.");
            e.putString(Prefs.CITY, c);
        }
        if (b.has("lat") && b.has("lon")) {
            double lat, lon;
            try {
                lat = Double.parseDouble(b.optString("lat"));
                lon = Double.parseDouble(b.optString("lon"));
            } catch (NumberFormatException x) {
                return Response.error(400, "Bad coordinates.");
            }
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return Response.error(400, "Bad coordinates.");
            e.putString(Prefs.LAT, String.valueOf(lat)).putString(Prefs.LON, String.valueOf(lon));
            location = true;
        }
        if (b.has("units")) {
            String u = b.optString("units");
            if (!"f".equals(u) && !"c".equals(u)) return Response.error(400, "Units are f or c.");
            e.putString(Prefs.UNITS, u);
            location = true;
        }
        if (location) e.remove(Prefs.WX_JSON).remove(Prefs.WX_TIME);
        e.apply();
        return ok();
    }

    private Response favorites(JSONObject b) {
        JSONArray a = b.optJSONArray("favorites");
        if (a == null) return Response.error(400, "favorites must be a list");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length() && out.size() < 24; i++) {
            String pkg = a.optString(i).trim();
            if (pkg.matches("[A-Za-z0-9_.]+") && !out.contains(pkg)) out.add(pkg);
        }
        Apps.saveFavorites(app, out);
        return ok();
    }

    private Response quotes(JSONObject b) {
        JSONObject add = b.optJSONObject("add");
        if (add != null) {
            String text = add.optString("text").trim();
            String author = add.optString("author").trim();
            if (text.length() < 2 || text.length() > 200) return Response.error(400, "Quotes should be 2 to 200 characters.");
            if (author.length() > 40) return Response.error(400, "Author is too long.");
            if (Copy.custom(prefs).size() >= 100) return Response.error(400, "That's 100 quotes. Remove one first.");
            Copy.addCustom(prefs, text, author);
            Copy.nextQuote(prefs);
            return ok();
        }
        if (b.has("remove")) {
            Copy.removeCustom(prefs, b.optInt("remove", -1));
            return ok();
        }
        return Response.error(400, "Nothing to do");
    }

    // ---------------------------------------------------------------- icons

    private Response icon(String pkg, boolean banner) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return Response.error(400, "pkg");
        String key = pkg + (banner ? "#b" : "#i");
        byte[] png = iconCache.get(key);
        if (png == null) {
            Apps.App a = Apps.load(app, pkg);
            if (!a.installed) return Response.error(404, "Not installed");
            Drawable d = banner ? (a.banner != null ? a.banner : a.icon) : (a.icon != null ? a.icon : a.banner);
            if (d == null) {
                try {
                    d = app.getPackageManager().getApplicationIcon(pkg);
                } catch (Exception e) {
                    return Response.error(404, "No icon");
                }
            }
            Bitmap bmp;
            if (banner && a.banner == null) {
                // No TV banner: center the icon on a dark card instead of stretching it.
                bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                c.drawColor(0xFF26211D);
                d.setBounds(112, 42, 208, 138);
                d.draw(c);
            } else {
                int w = banner ? 320 : 160, h = banner ? 180 : 160;
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                d.setBounds(0, 0, w, h);
                d.draw(c);
            }
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
            bmp.recycle();
            png = o.toByteArray();
            iconCache.put(key, png);
        }
        Response r = new Response();
        r.type = "image/png";
        r.body = png;
        r.cache = "private, max-age=86400";
        return r;
    }
}
