package com.wallsync.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Keeps three Firebase live streams open:
 *  - ver     : a new wallpaper arrived from the other phone
 *  - ack     : the other phone confirmed that OUR wallpaper was applied
 *  - devices : the other phone joined / left the pairing
 */
public class SyncService extends Service {
    private volatile boolean run;
    private final ArrayList<Watch> watchers = new ArrayList<Watch>();

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Sync.channel(this, "svc", getString(R.string.ch_svc), NotificationManager.IMPORTANCE_LOW);
        Notification n = Sync.builder(this, "svc")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Ui.col(this, R.color.accent))
                .setContentTitle(getString(R.string.svc_title))
                .setContentText(getString(R.string.svc_text))
                .setContentIntent(Sync.openApp(this))
                .setOngoing(true)
                .build();
        startForeground(1, n);
        if (!run) {
            run = true;
            watchers.add(new Watch(0, "ver"));
            watchers.add(new Watch(1, "ack"));
            watchers.add(new Watch(2, "devices"));
            for (Watch w : watchers) w.start();
        }
        return START_STICKY;
    }

    private class Watch extends Thread {
        final int kind;
        final String path;
        volatile HttpURLConnection cur;

        Watch(int kind, String path) {
            this.kind = kind;
            this.path = path;
            setDaemon(true);
        }

        @Override public void run() {
            Context c = SyncService.this;
            while (run) {
                try {
                    if (Prefs.ready(c)) {
                        handle(c);
                        stream(c);
                    }
                } catch (Exception ignored) { }
                try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
            }
        }

        void handle(Context c) {
            try {
                if (kind == 0) { Sync.flushPending(c); Sync.check(c); }
                else if (kind == 1) Sync.checkAck(c);
                else Sync.refreshLink(c);
            } catch (Exception ignored) { }
        }

        /** Firebase server-sent events: fires instantly when something changes. */
        void stream(Context c) throws Exception {
            HttpURLConnection h = (HttpURLConnection) new URL(Sync.base(c) + "/" + path + ".json").openConnection();
            cur = h;
            h.setRequestProperty("Accept", "text/event-stream");
            h.setConnectTimeout(15000);
            h.setReadTimeout(75000);
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            String ev = "", line;
            while (run && (line = r.readLine()) != null) {
                if (line.startsWith("event:")) {
                    ev = line.substring(6).trim();
                } else if (line.startsWith("data:") && (ev.equals("put") || ev.equals("patch"))) {
                    handle(c);
                    if (kind == 0) Sync.flushPending(c);
                }
            }
            h.disconnect();
        }
    }

    @Override public void onDestroy() {
        run = false;
        for (final Watch w : watchers) {
            final HttpURLConnection h = w.cur;
            if (h != null) new Thread(new Runnable() {
                @Override public void run() { try { h.disconnect(); } catch (Exception e) { } }
            }).start();
        }
        watchers.clear();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
