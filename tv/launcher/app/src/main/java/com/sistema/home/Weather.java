package com.sistema.home;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Today's weather from Open-Meteo (free, no API key, no account).
 * The last good response is cached so the screen never shows a blank on boot.
 */
final class Weather {

    static final long STALE_MS = 30 * 60_000L;

    interface Listener {
        void onWeather(Weather w);
    }

    // Parsed values
    double temp;
    double feelsLike;
    int code = -1;
    boolean isDay = true;
    double high;
    double low;
    int rainChance = -1;
    long sunrise;
    long sunset;
    long fetchedAt;
    boolean fahrenheit = true;

    boolean valid() {
        return code >= 0;
    }

    static Weather fromCache(SharedPreferences prefs) {
        String json = prefs.getString(Prefs.WX_JSON, null);
        if (json == null) return null;
        try {
            Weather w = parse(json);
            w.fetchedAt = prefs.getLong(Prefs.WX_TIME, 0);
            w.fahrenheit = !"c".equals(prefs.getString(Prefs.UNITS, "f"));
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isStale(SharedPreferences prefs) {
        return System.currentTimeMillis() - prefs.getLong(Prefs.WX_TIME, 0) > STALE_MS;
    }

    private static volatile boolean inFlight;
    private static long lastAttempt;

    /** Call when the location changes so the next fetch runs right away. */
    static void resetBackoff() {
        lastAttempt = 0;
    }

    /** Fetches on a background thread and reports on the main thread. Failures are silent. */
    static void fetch(final SharedPreferences prefs, final Listener listener) {
        long now = System.currentTimeMillis();
        // At most one try every two minutes, so a dead connection doesn't hammer the API.
        if (inFlight || now - lastAttempt < 120_000L) return;
        inFlight = true;
        lastAttempt = now;
        final String lat = prefs.getString(Prefs.LAT, Prefs.DEFAULT_LAT);
        final String lon = prefs.getString(Prefs.LON, Prefs.DEFAULT_LON);
        final boolean f = !"c".equals(prefs.getString(Prefs.UNITS, "f"));
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.open-meteo.com/v1/forecast"
                            + "?latitude=" + lat + "&longitude=" + lon
                            + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
                            + "&temperature_unit=" + (f ? "fahrenheit" : "celsius")
                            + "&timezone=auto&forecast_days=1";
                    final String json = get(url);
                    final Weather w = parse(json);
                    w.fahrenheit = f;
                    w.fetchedAt = System.currentTimeMillis();
                    prefs.edit().putString(Prefs.WX_JSON, json).putLong(Prefs.WX_TIME, w.fetchedAt).apply();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onWeather(w);
                        }
                    });
                } catch (Exception ignored) {
                    // Offline or API hiccup. The cached forecast stays on screen.
                } finally {
                    inFlight = false;
                }
            }
        }, "weather").start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "SistemaHome/1.0 (Android TV launcher)");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static Weather parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        int offset = root.optInt("utc_offset_seconds", 0);
        Weather w = new Weather();
        w.temp = cur.getDouble("temperature_2m");
        w.feelsLike = cur.optDouble("apparent_temperature", w.temp);
        w.code = cur.getInt("weather_code");
        w.isDay = cur.optInt("is_day", 1) == 1;
        w.high = first(daily, "temperature_2m_max");
        w.low = first(daily, "temperature_2m_min");
        JSONArray rain = daily.optJSONArray("precipitation_probability_max");
        if (rain != null && rain.length() > 0 && !rain.isNull(0)) w.rainChance = rain.getInt(0);
        w.sunrise = localIsoToEpoch(daily.getJSONArray("sunrise").getString(0), offset);
        w.sunset = localIsoToEpoch(daily.getJSONArray("sunset").getString(0), offset);
        return w;
    }

    private static double first(JSONObject o, String key) throws Exception {
        return o.getJSONArray(key).getDouble(0);
    }

    /** Open-Meteo returns local wall time like 2026-11-09T06:38 plus a UTC offset. */
    private static long localIsoToEpoch(String iso, int offsetSeconds) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime() - offsetSeconds * 1000L;
        } catch (Exception e) {
            return 0;
        }
    }

    int glyph() {
        switch (code) {
            case 0:
            case 1:
                return isDay ? GlyphView.SUN : GlyphView.MOON;
            case 2:
                return isDay ? GlyphView.PARTLY_DAY : GlyphView.PARTLY_NIGHT;
            case 3:
                return GlyphView.CLOUD;
            case 45:
            case 48:
                return GlyphView.FOG;
            case 95:
            case 96:
            case 99:
                return GlyphView.STORM;
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86:
                return GlyphView.SNOW;
            default:
                return code >= 51 && code <= 82 ? GlyphView.RAIN : GlyphView.CLOUD;
        }
    }

    String condition() {
        switch (code) {
            case 0: return isDay ? "Clear skies" : "Clear night";
            case 1: return isDay ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66:
            case 67: return "Freezing rain";
            case 71:
            case 73:
            case 75:
            case 77: return "Snow";
            case 80: return "Passing showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85:
            case 86: return "Snow showers";
            case 95: return "Thunderstorms";
            case 96:
            case 99: return "Storms with hail";
            default: return "";
        }
    }

    static String deg(double v) {
        return Math.round(v) + "°";
    }
}
