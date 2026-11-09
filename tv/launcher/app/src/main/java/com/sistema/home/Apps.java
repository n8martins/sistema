package com.sistema.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Installed apps, favorites and HDMI inputs. */
final class Apps {
    private Apps() {}

    static final class App {
        String pkg;
        String label;
        Drawable banner;
        Drawable icon;
        boolean installed = true;
    }

    static final class Input {
        String id;
        String title;
        String device;
    }

    /**
     * Your lineup, in order. Each slot lists the TV package first, then fallbacks
     * (older or phone builds) so the right one is picked on whatever is installed.
     */
    static final String[][] DEFAULT_FAVORITES = {
            {"YouTube", "com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.google.android.youtube"},
            {"Spotify", "com.spotify.tv.android", "com.spotify.music"},
            {"Paramount+", "com.cbs.ott", "com.cbs.app"},
            {"Netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
            {"HBO Max", "com.wbd.stream", "com.hbo.hbonow"},
            {"YouTube TV", "com.google.android.youtube.tvunplugged"},
    };

    static List<String> favorites(Context c) {
        SharedPreferences p = Prefs.get(c);
        String saved = p.getString(Prefs.FAVORITES, null);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String s : saved.split(",")) {
                s = s.trim();
                if (s.length() > 0 && !out.contains(s)) out.add(s);
            }
            return out;
        }
        PackageManager pm = c.getPackageManager();
        for (String[] slot : DEFAULT_FAVORITES) {
            String pick = slot[1];
            for (int i = 1; i < slot.length; i++) {
                if (isInstalled(pm, slot[i])) {
                    pick = slot[i];
                    break;
                }
            }
            out.add(pick);
        }
        return out;
    }

    static void saveFavorites(Context c, List<String> favs) {
        Prefs.get(c).edit().putString(Prefs.FAVORITES, TextUtils.join(",", favs)).apply();
    }

    static String knownName(String pkg) {
        for (String[] slot : DEFAULT_FAVORITES) {
            for (int i = 1; i < slot.length; i++) if (slot[i].equals(pkg)) return slot[0];
        }
        return pkg;
    }

    static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Builds one entry, or a placeholder if the app is not installed. */
    static App load(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        App a = new App();
        a.pkg = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ai.enabled) throw new PackageManager.NameNotFoundException();
            a.label = String.valueOf(pm.getApplicationLabel(ai));
            Intent tv = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (tv != null && tv.getComponent() != null) {
                try {
                    a.banner = pm.getActivityBanner(tv.getComponent());
                    if (a.banner == null) a.icon = pm.getActivityIcon(tv.getComponent());
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (a.banner == null) a.banner = pm.getApplicationBanner(ai);
            if (a.banner == null && a.icon == null) a.icon = pm.getApplicationIcon(ai);
        } catch (PackageManager.NameNotFoundException e) {
            a.installed = false;
            a.label = knownName(pkg);
        }
        return a;
    }

    /** Every launchable app on the TV except this launcher, sorted by name. */
    static List<App> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Boolean> pkgs = new LinkedHashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) pkgs.put(ri.activityInfo.packageName, Boolean.TRUE);
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) {
            if (!pkgs.containsKey(ri.activityInfo.packageName)) pkgs.put(ri.activityInfo.packageName, Boolean.FALSE);
        }
        pkgs.remove(c.getPackageName());
        List<App> out = new ArrayList<>();
        for (String pkg : pkgs.keySet()) {
            App a = load(c, pkg);
            if (a.installed) out.add(a);
        }
        Collections.sort(out, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.toLowerCase(Locale.ROOT).compareTo(y.label.toLowerCase(Locale.ROOT));
            }
        });
        return out;
    }

    static void launch(Context c, App a) {
        if (!a.installed) {
            openStore(c, a.pkg);
            return;
        }
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(a.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(a.pkg);
        if (i == null) {
            Toast.makeText(c, a.label + " can't be opened from here", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, a.label + " didn't open", Toast.LENGTH_SHORT).show();
        }
    }

    static void openStore(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, "Google Play isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    static void openAppInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, "App info isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    static void openSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, "Settings isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * HDMI ports from the TV input framework. Devices plugged in over HDMI-CEC show up as
     * child inputs; their names become the subtitle (for example "PlayStation 5").
     */
    /** Tests only: stands in for the TV input service, which doesn't exist off-device. */
    static List<Input> testInputs;

    static List<Input> hdmiInputs(Context c) {
        if (testInputs != null) return new ArrayList<>(testInputs);
        List<Input> out = new ArrayList<>();
        TvInputManager tim;
        try {
            tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        } catch (RuntimeException e) {
            return out;
        }
        if (tim == null) return out;
        List<TvInputInfo> list;
        try {
            list = tim.getTvInputList();
        } catch (RuntimeException e) {
            return out;
        }
        if (list == null) return out;

        Map<String, String> childNames = new HashMap<>();
        for (TvInputInfo info : list) {
            if (info.getParentId() != null && !TextUtils.isEmpty(safeLabel(c, info))) {
                childNames.put(info.getParentId(), safeLabel(c, info));
            }
        }
        Set<String> seen = new HashSet<>();
        for (TvInputInfo info : list) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || info.getParentId() != null) continue;
            if (isHidden(c, info) || !seen.add(info.getId())) continue;
            Input in = new Input();
            in.id = info.getId();
            String custom = safeCustomLabel(c, info);
            String base = safeLabel(c, info);
            in.title = !TextUtils.isEmpty(custom) ? custom : (!TextUtils.isEmpty(base) ? base : "HDMI");
            String dev = childNames.get(info.getId());
            if (dev != null && !dev.equalsIgnoreCase(in.title)) in.device = dev;
            out.add(in);
        }
        Collections.sort(out, new Comparator<Input>() {
            @Override
            public int compare(Input a, Input b) {
                return naturalKey(a.title).compareTo(naturalKey(b.title));
            }
        });
        return out;
    }

    private static String naturalKey(String s) {
        StringBuilder b = new StringBuilder();
        StringBuilder digits = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else {
                if (digits.length() > 0) {
                    b.append(String.format(Locale.ROOT, "%06d", Long.parseLong(digits.toString())));
                    digits.setLength(0);
                }
                b.append(Character.toLowerCase(ch));
            }
        }
        if (digits.length() > 0) b.append(String.format(Locale.ROOT, "%06d", Long.parseLong(digits.toString())));
        return b.toString();
    }

    private static String safeLabel(Context c, TvInputInfo info) {
        try {
            CharSequence l = info.loadLabel(c);
            return l == null ? null : l.toString().trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeCustomLabel(Context c, TvInputInfo info) {
        try {
            CharSequence l = info.loadCustomLabel(c);
            return l == null ? null : l.toString().trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isHidden(Context c, TvInputInfo info) {
        try {
            return info.isHidden(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void switchTo(Context c, Input in) {
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, "This TV won't switch inputs from here. Use the Input button.", Toast.LENGTH_LONG).show();
        }
    }
}
