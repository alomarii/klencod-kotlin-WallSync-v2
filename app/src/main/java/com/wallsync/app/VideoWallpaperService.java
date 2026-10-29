package com.wallsync.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.io.File;

public class VideoWallpaperService extends WallpaperService {
    static final String RELOAD = "com.wallsync.app.RELOAD";

    @Override public Engine onCreateEngine() { return new VEngine(); }

    class VEngine extends Engine {
        MediaPlayer mp;
        SurfaceHolder holder;
        final BroadcastReceiver rcv = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { load(); }
        };

        @Override public void onCreate(SurfaceHolder s) {
            super.onCreate(s);
            Sync.reg(VideoWallpaperService.this, rcv, new IntentFilter(RELOAD));
        }

        @Override public void onSurfaceCreated(SurfaceHolder s) {
            super.onSurfaceCreated(s);
            holder = s;
            load();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder s) {
            release();
            holder = null;
            super.onSurfaceDestroyed(s);
        }

        @Override public void onVisibilityChanged(boolean v) {
            try {
                if (mp != null) { if (v) mp.start(); else mp.pause(); }
            } catch (Exception ignored) { }
        }

        void load() {
            release();
            if (holder == null) return;
            String path = Prefs.p(getApplicationContext()).getString("video", null);
            if (path == null || !new File(path).exists()) return;
            try {
                mp = new MediaPlayer();
                mp.setSurface(holder.getSurface());
                mp.setDataSource(path);
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(MediaPlayer m) {
                        try {
                            m.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                            if (isVisible()) m.start();
                            new Thread(new Runnable() {
                                @Override public void run() { Sync.ackPending(getApplicationContext()); }
                            }).start();
                        } catch (Exception ignored) { }
                    }
                });
                mp.prepareAsync();
            } catch (Exception e) { release(); }
        }

        void release() {
            if (mp != null) {
                try { mp.release(); } catch (Exception ignored) { }
                mp = null;
            }
        }

        @Override public void onDestroy() {
            release();
            try { unregisterReceiver(rcv); } catch (Exception ignored) { }
            super.onDestroy();
        }
    }
}
