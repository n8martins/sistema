package com.sistema.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the phone remote reachable while you're inside other apps. A foreground service, so
 * Android doesn't stop it while Netflix or YouTube has the screen.
 */
public class RemoteService extends Service {

    private static final String CHANNEL = "remote";
    private static RemoteService running;

    private TvControl tv;
    private RemoteServer server;

    static void start(Context c) {
        try {
            Intent i = new Intent(c, RemoteService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            // Android refused (for example while the screen is off). The next Home press retries.
        }
    }

    static TvControl control() {
        RemoteService s = running;
        return s == null ? null : s.tv;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tv = new TvControl(this);
        server = new RemoteServer(this, tv);
        try {
            server.start(RemoteServer.PORT);
        } catch (Exception e) {
            server = null;
        }
        // Ask the TV for permission once, right away, so the prompt shows during setup.
        tv.warmUp();
        running = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, n);
            }
        } catch (RuntimeException e) {
            // Android won't let it run in the foreground right now. Stop cleanly rather than
            // risk the system killing the launcher for it; the next Home press tries again.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (server == null) {
            try {
                server = new RemoteServer(this, tv);
                server.start(RemoteServer.PORT);
            } catch (Exception e) {
                server = null;
            }
        }
        return START_STICKY;
    }

    private Notification notification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Phone remote", NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Sistema phone remote")
                .setContentText("Listening on your Wi-Fi")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        running = null;
        if (server != null) server.stop();
        if (tv != null) tv.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
