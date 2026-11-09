package com.sistema.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Settings live in SharedPreferences and can be changed over ADB without rebuilding:
 *
 *   adb shell am start -n com.sistema.home/.HomeActivity \
 *       --es name "Nicolas" --es city "Miami" --es lat 25.7617 --es lon -80.1918 --es units f \
 *       --es favorites "com.google.android.youtube.tv,com.spotify.tv.android"
 */
final class Prefs {
    private Prefs() {}

    static final String FILE = "sistema";

    static final String NAME = "name";
    static final String CITY = "city";
    static final String LAT = "lat";
    static final String LON = "lon";
    static final String UNITS = "units";
    static final String FAVORITES = "favorites";
    static final String WX_JSON = "wx_json";
    static final String WX_TIME = "wx_time";
    static final String Q_ORDER = "q_order";
    static final String Q_POS = "q_pos";
    static final String Q_TIME = "q_time";
    static final String LINE_SEED = "line_seed";
    static final String CUSTOM_QUOTES = "custom_quotes";
    static final String TOKENS = "remote_tokens";
    static final String PAIR_CODE = "pair_code";
    static final String PAIR_UNTIL = "pair_until";

    static final String DEFAULT_NAME = "Nicolas";
    static final String DEFAULT_CITY = "Miami";
    static final String DEFAULT_LAT = "25.7617";
    static final String DEFAULT_LON = "-80.1918";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Applies any overrides passed as intent extras. Returns true if the location changed. */
    static boolean applyExtras(Context c, Intent intent) {
        if (intent == null || intent.getExtras() == null) return false;
        SharedPreferences p = get(c);
        SharedPreferences.Editor e = p.edit();
        boolean locationChanged = false;
        String name = intent.getStringExtra("name");
        if (name != null && name.trim().length() > 0) e.putString(NAME, name.trim());
        String city = intent.getStringExtra("city");
        if (city != null && city.trim().length() > 0) e.putString(CITY, city.trim());
        String lat = intent.getStringExtra("lat");
        String lon = intent.getStringExtra("lon");
        if (isNumber(lat) && isNumber(lon)) {
            e.putString(LAT, lat.trim()).putString(LON, lon.trim());
            locationChanged = true;
        }
        String units = intent.getStringExtra("units");
        if ("c".equalsIgnoreCase(units) || "f".equalsIgnoreCase(units)) {
            e.putString(UNITS, units.toLowerCase());
            locationChanged = true;
        }
        String favs = intent.getStringExtra("favorites");
        if (favs != null && favs.trim().length() > 0) e.putString(FAVORITES, favs.replace(" ", ""));
        if (locationChanged) e.remove(WX_JSON).remove(WX_TIME);
        e.apply();
        return locationChanged;
    }

    private static boolean isNumber(String s) {
        if (s == null) return false;
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
