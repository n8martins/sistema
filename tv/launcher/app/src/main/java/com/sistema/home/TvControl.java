package com.sistema.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the phone remote can make the TV do.
 *
 * Keys, typing and app launches go through the TV's own ADB shell (see AdbClient), which works
 * inside any app. Where Android allows it, there's a fallback that works without it.
 */
final class TvControl {

    static final String ADB_OK = "ok";
    static final String ADB_WAITING = "waiting";
    static final String ADB_CONNECTING = "connecting";
    static final String ADB_OFF = "off";

    /** Tests point this somewhere else. */
    static int adbPort = 5555;

    static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        KEYS.put("up", 19);
        KEYS.put("down", 20);
        KEYS.put("left", 21);
        KEYS.put("right", 22);
        KEYS.put("ok", 23);
        KEYS.put("back", 4);
        KEYS.put("home", 3);
        KEYS.put("menu", 82);
        KEYS.put("playpause", 85);
        KEYS.put("rewind", 89);
        KEYS.put("forward", 90);
        KEYS.put("next", 87);
        KEYS.put("previous", 88);
        KEYS.put("volup", 24);
        KEYS.put("voldown", 25);
        KEYS.put("mute", 164);
        KEYS.put("power", 26);
        KEYS.put("sleep", 223);
        KEYS.put("wake", 224);
        KEYS.put("delete", 67);
        KEYS.put("enter", 66);
        KEYS.put("search", 84);
        KEYS.put("info", 165);
        KEYS.put("input", 178);
        KEYS.put("settings", 176);
    }

    static final class NotReady extends Exception {
        NotReady(String m) {
            super(m);
        }
    }

    private final Context app;
    private final HandlerThread thread;
    private final Handler worker;
    private AdbClient adb;
    private volatile String adbState = ADB_OFF;
    private volatile String adbError = "";
    private volatile boolean connecting;
    private volatile long sleepAt;
    private final Runnable sleepTask = new Runnable() {
        @Override
        public void run() {
            sleepAt = 0;
            try {
                key("sleep", false);
            } catch (Exception ignored) {
            }
        }
    };

    TvControl(Context context) {
        app = context.getApplicationContext();
        thread = new HandlerThread("tv-control");
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    String adbState() {
        return adbState;
    }

    String adbError() {
        return adbError;
    }

    long sleepAt() {
        return sleepAt;
    }

    void shutdown() {
        worker.removeCallbacksAndMessages(null);
        synchronized (this) {
            if (adb != null) adb.close();
            adb = null;
        }
        thread.quitSafely();
    }

    /** Starts connecting in the background. The first time, the TV shows an approval prompt. */
    void warmUp() {
        if (connecting || ADB_OK.equals(adbState)) return;
        connecting = true;
        adbState = ADB_CONNECTING;
        worker.post(new Runnable() {
            @Override
            public void run() {
                try {
                    KeyPair keys = AdbClient.loadOrCreateKeys(app.getFilesDir());
                    AdbClient c = new AdbClient(keys);
                    c.connect("127.0.0.1", adbPort, 120_000, new AdbClient.Listener() {
                        @Override
                        public void onWaitingForApproval() {
                            adbState = ADB_WAITING;
                        }
                    });
                    synchronized (TvControl.this) {
                        adb = c;
                    }
                    adbState = ADB_OK;
                    adbError = "";
                } catch (Exception e) {
                    adbState = ADB_OFF;
                    adbError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                } finally {
                    connecting = false;
                }
            }
        });
    }

    /** Runs a shell command, reconnecting once if the connection dropped. */
    String shell(String command) throws NotReady {
        for (int attempt = 0; attempt < 2; attempt++) {
            AdbClient c;
            synchronized (this) {
                c = adb;
            }
            if (c == null || !c.isConnected()) {
                warmUp();
                throw new NotReady(ADB_WAITING.equals(adbState)
                        ? "Press Allow on the TV to let your phone control it."
                        : "Connecting to the TV. Try again in a second.");
            }
            try {
                return c.shell(command);
            } catch (IOException e) {
                synchronized (this) {
                    if (adb == c) {
                        c.close();
                        adb = null;
                        adbState = ADB_OFF;
                    }
                }
            }
        }
        warmUp();
        throw new NotReady("Lost the connection to the TV. Try again.");
    }

    // ---------------------------------------------------------------- actions

    void key(String name, boolean longPress) throws NotReady {
        Integer code = KEYS.get(name);
        if (code == null) throw new IllegalArgumentException("Unknown key " + name);
        try {
            shell("input keyevent " + (longPress ? "--longpress " : "") + code);
        } catch (NotReady e) {
            // Volume works without ADB.
            if (code == 24 || code == 25 || code == 164) {
                AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int dir = code == 24 ? AudioManager.ADJUST_RAISE
                            : code == 25 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
                    am.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    return;
                }
            }
            throw e;
        }
    }

    /** Types into whatever field is focused on the TV, like a search box. */
    void text(String text) throws NotReady {
        if (text == null || text.isEmpty()) return;
        shell("input text " + shellQuote(escapeInputText(text)));
    }

    /** input text treats %s as a space and needs everything else literal. */
    static String escapeInputText(String text) {
        StringBuilder b = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch == ' ') b.append("%s");
            else if (ch == '\n' || ch == '\r' || ch == '\t') b.append("%s");
            else if (ch >= 0x20) b.append(ch);
        }
        return b.toString();
    }

    static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    void launch(String pkg) throws NotReady {
        PackageManager pm = app.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null || i.getComponent() == null) throw new IllegalArgumentException(pkg + " isn't installed");
        ComponentName cn = i.getComponent();
        try {
            shell("am start -n " + shellQuote(cn.flattenToShortString()));
        } catch (NotReady e) {
            // Without ADB this only works while the home screen is in front.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            app.startActivity(i);
        }
    }

    void switchInput(String inputId) throws NotReady {
        String uri = TvContract.buildChannelUriForPassthroughInput(inputId).toString();
        try {
            shell("am start -a android.intent.action.VIEW -d " + shellQuote(uri));
        } catch (NotReady e) {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        }
    }

    private static final Pattern RESUMED = Pattern.compile("(?:topResumedActivity|mResumedActivity|ResumedActivity)[^\\n]*?\\s([a-zA-Z0-9_.]+)/");

    /** Package of the app on screen right now, or null if unknown. */
    String foregroundPackage() {
        if (!ADB_OK.equals(adbState)) return null;
        try {
            String out = shell("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity' | head -n 1");
            Matcher m = RESUMED.matcher(out);
            return m.find() ? m.group(1) : null;
        } catch (NotReady e) {
            return null;
        }
    }

    /** Puts the TV to sleep after the given minutes. 0 cancels. */
    void sleepIn(int minutes) {
        worker.removeCallbacks(sleepTask);
        if (minutes <= 0) {
            sleepAt = 0;
            return;
        }
        sleepAt = System.currentTimeMillis() + minutes * 60_000L;
        worker.postDelayed(sleepTask, minutes * 60_000L);
    }
}
