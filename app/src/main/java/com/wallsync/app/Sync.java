package com.wallsync.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

final class Sync {
    static final int CH = 900000;           // chunk size (chars of base64)
    static final long MAX_VIDEO = 8L * 1024 * 1024;
    static final String STATE = "com.wallsync.app.STATE";

    /** True while the main screen is on top (then banners are used instead of system notifications). */
    static volatile boolean uiVisible;

    static String base(Context c) { return Prefs.url(c) + "/wp/" + Prefs.code(c); }

    // ---------------------------------------------------------------- helpers

    static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    static void copy(File a, File b) throws IOException {
        FileInputStream in = new FileInputStream(a);
        FileOutputStream o = new FileOutputStream(b);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        o.close();
    }

    /** Registers a receiver that works on every Android version (API 34 needs an explicit export flag). */
    static void reg(Context c, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(r, f);
    }

    /** Tells the open UI (if any) to refresh, optionally with a message to show as a banner. */
    static void pushState(Context c, String msg) {
        Intent i = new Intent(STATE).setPackage(c.getPackageName());
        if (msg != null) i.putExtra("msg", msg);
        c.sendBroadcast(i);
    }

    // ---------------------------------------------------------------- sending

    /** Uploads a file; the "ver" node is written last so the other phone never sees half data. */
    static void send(Context c, String type, File f) throws Exception {
        String base = base(c);
        String b64 = Base64.encodeToString(readAll(f), Base64.NO_WRAP);
        int n = (b64.length() + CH - 1) / CH;
        Net.req("DELETE", base + "/ack.json", null);
        Net.req("DELETE", base + "/data.json", null);
        for (int i = 0; i < n; i++) {
            String part = b64.substring(i * CH, Math.min(b64.length(), (i + 1) * CH));
            Net.req("PUT", base + "/data/" + i + ".json", "\"" + part + "\"");
        }
        long id = System.currentTimeMillis();
        Net.req("PUT", base + "/ver.json", "{\"id\":" + id + ",\"type\":\"" + type + "\",\"n\":" + n
                + ",\"by\":\"" + Prefs.dev(c) + "\"}");
        Prefs.p(c).edit().putLong("applied", id).putLong("sent", id).apply();
        pushState(c, null);
    }

    /** Saves as pending, tries to send now. Returns true if sent. */
    static boolean submit(Context c, String type, File src) {
        try {
            File pend = new File(c.getFilesDir(), "pending.bin");
            copy(src, pend);
            Prefs.p(c).edit().putString("pt", type).apply();
        } catch (Exception e) { return false; }
        return flushPending(c);
    }

    static synchronized boolean flushPending(Context c) {
        String t = Prefs.p(c).getString("pt", null);
        if (t == null) return true;
        File f = new File(c.getFilesDir(), "pending.bin");
        if (!f.exists()) { Prefs.p(c).edit().remove("pt").apply(); return true; }
        if (!Prefs.ready(c)) return false;
        try {
            send(c, t, f);
            Prefs.p(c).edit().remove("pt").apply();
            return true;
        } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- receiving

    static String unq(String s) {
        s = s.trim();
        if (s.equals("null")) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.replace("\\/", "/");
    }

    /** Checks the server for a new wallpaper and applies it, then confirms to the sender. */
    static synchronized void check(Context c) throws Exception {
        if (!Prefs.ready(c)) return;
        String base = base(c);
        String v = Net.req("GET", base + "/ver.json", null).trim();
        if (v.isEmpty() || v.equals("null")) return;
        JSONObject j = new JSONObject(v);
        long id = j.getLong("id");
        SharedPreferences p = Prefs.p(c);
        if (id == p.getLong("applied", -1)) return;
        String type = j.getString("type");
        int n = j.getInt("n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String s = unq(Net.req("GET", base + "/data/" + i + ".json", null));
            if (s.isEmpty()) throw new IOException("missing chunk");
            sb.append(s);
        }
        byte[] bytes = Base64.decode(sb.toString(), Base64.DEFAULT);
        boolean video = type.equals("video");
        File f = new File(c.getFilesDir(), video ? "wall.mp4" : "wall.jpg");
        FileOutputStream o = new FileOutputStream(f);
        o.write(bytes);
        o.close();
        SharedPreferences.Editor ed = p.edit().putLong("applied", id);
        if (video) ed.putLong("pendAck", id);
        ed.apply();

        int r = apply(c, type, f, false);
        if (r == 1) {
            p.edit().remove("pendAck").apply();
            writeAck(c, id, true);
            String t = c.getString(R.string.notif_received_title);
            notifyUser(c, 4, t, c.getString(R.string.notif_received_text));
            pushState(c, t);
        } else if (r == 0) {
            writeAck(c, id, false);   // video delivered, waiting for the one-time confirmation
        }
    }

    /** @return 1 applied, 0 waiting for the user to confirm a live wallpaper, -1 failed. */
    static int apply(Context c, String type, File f, boolean fromUi) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c.getApplicationContext());
            if (type.equals("image")) {
                Bitmap bm = BitmapFactory.decodeFile(f.getPath());
                if (bm == null) return -1;
                wm.setBitmap(bm, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                return 1;
            }
            Prefs.p(c).edit().putString("video", f.getPath()).apply();
            WallpaperInfo wi = wm.getWallpaperInfo();
            if (wi != null && wi.getPackageName().equals(c.getPackageName())) {
                c.sendBroadcast(new Intent(VideoWallpaperService.RELOAD).setPackage(c.getPackageName()));
                return 1;
            }
            if (fromUi) {
                Intent i = liveIntent(c);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } else {
                notifyVideo(c, liveIntent(c));
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    static Intent liveIntent(Context c) {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(c, VideoWallpaperService.class));
        return i;
    }

    // ---------------------------------------------------------------- acknowledgements

    static void writeAck(Context c, long id, boolean ok) {
        try {
            Net.req("PUT", base(c) + "/ack.json", "{\"id\":" + id + ",\"by\":\"" + Prefs.dev(c)
                    + "\",\"ok\":" + (ok ? 1 : 0) + ",\"t\":" + System.currentTimeMillis() + "}");
        } catch (Exception ignored) { }
    }

    /** Called by the live-wallpaper engine once the video really plays. */
    static void ackPending(Context c) {
        if (!Prefs.ready(c)) return;
        SharedPreferences p = Prefs.p(c);
        long id = p.getLong("pendAck", 0);
        if (id == 0) return;
        p.edit().remove("pendAck").apply();
        writeAck(c, id, true);
    }

    /** Sender side: the other phone confirmed that the wallpaper was changed. */
    static synchronized void checkAck(Context c) throws Exception {
        if (!Prefs.ready(c)) return;
        String s = Net.req("GET", base(c) + "/ack.json", null).trim();
        if (s.isEmpty() || s.equals("null")) return;
        JSONObject j = new JSONObject(s);
        long id = j.getLong("id");
        if (j.optString("by").equals(Prefs.dev(c))) return;
        SharedPreferences p = Prefs.p(c);
        if (id != p.getLong("sent", -1)) return;
        boolean ok = j.optInt("ok", 1) == 1;
        if (id == p.getLong("ackDone", -1) && (p.getBoolean("ackOk", false) || !ok)) return;
        p.edit().putLong("ackDone", id).putBoolean("ackOk", ok)
                .putLong("ackAt", System.currentTimeMillis()).apply();
        String t = c.getString(ok ? R.string.notif_changed_title : R.string.notif_pending_title);
        String x = c.getString(ok ? R.string.notif_changed_text : R.string.notif_pending_text);
        notifyUser(c, 3, t, x);
        pushState(c, t);
    }

    // ---------------------------------------------------------------- pairing

    private static String modelName() {
        String m = Build.MODEL == null ? "" : Build.MODEL.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
        return m.length() > 30 ? m.substring(0, 30) : m;
    }

    private static void register(Context c) throws IOException {
        Net.req("PUT", base(c) + "/devices/" + Prefs.dev(c) + ".json",
                "{\"t\":" + System.currentTimeMillis() + ",\"n\":\"" + modelName() + "\"}");
        Prefs.p(c).edit().putBoolean("joined", true).apply();
    }

    private static JSONObject devices(Context c) throws Exception {
        String s = Net.req("GET", base(c) + "/devices.json", null).trim();
        if (s.isEmpty() || s.equals("null")) return new JSONObject();
        return new JSONObject(s);
    }

    /**
     * Joins the room of the current code.
     * @return 0 joined, 1 the code is already used by two other phones, 2 network problem.
     */
    static int join(Context c) {
        try {
            JSONObject d = devices(c);
            String me = Prefs.dev(c);
            if (!d.has(me) && d.length() >= 2) return 1;
            register(c);
            return 0;
        } catch (Exception e) {
            return 2;
        }
    }

    /** Re-reads who is in the room: updates "linked", detects a removed phone. */
    static synchronized void refreshLink(Context c) {
        if (!Prefs.ready(c)) return;
        try {
            JSONObject d = devices(c);
            String me = Prefs.dev(c);
            SharedPreferences p = Prefs.p(c);
            boolean has = d.has(me);
            if (!has && p.getBoolean("joined", false)) { kicked(c); return; }
            if (!has) { register(c); }
            String peer = null;
            boolean other = false;
            Iterator<String> it = d.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals(me)) continue;
                other = true;
                JSONObject o = d.optJSONObject(k);
                peer = o == null ? "" : o.optString("n", "");
                break;
            }
            boolean was = Prefs.linked(c);
            String oldPeer = Prefs.peer(c);
            String np = other && peer != null ? peer : "";
            if (other != was || !np.equals(oldPeer)) {
                p.edit().putBoolean("linked", other).putString("peer", np).apply();
                if (other && !was) {
                    String t = c.getString(R.string.notif_linked_title);
                    notifyUser(c, 5, t, c.getString(R.string.notif_linked_text));
                    pushState(c, t);
                } else {
                    pushState(c, null);
                }
            }
        } catch (Exception ignored) { }
    }

    /** The other phone removed this one: forget everything and stop. */
    static void kicked(Context c) {
        Prefs.clearPairing(c);
        c.stopService(new Intent(c, SyncService.class));
        String t = c.getString(R.string.kicked_title);
        notifyUser(c, 6, t, c.getString(R.string.kicked_text));
        pushState(c, t);
    }

    /** User chose "change linked phone": delete the room, forget the code. Needs internet. */
    static boolean unlink(Context c) {
        try {
            Net.req("DELETE", base(c) + ".json", null);
        } catch (Exception e) {
            return false;
        }
        Prefs.clearPairing(c);
        c.stopService(new Intent(c, SyncService.class));
        return true;
    }

    // ---------------------------------------------------------------- notifications

    static void channel(Context c, String id, String name, int imp) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(id, name, imp));
        }
    }

    static Notification.Builder builder(Context c, String ch) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** System notification (skipped while the app is on screen: a banner is shown there instead). */
    static void notifyUser(Context c, int id, String title, String text) {
        if (uiVisible) return;
        try {
            channel(c, "wp", c.getString(R.string.ch_wp), NotificationManager.IMPORTANCE_HIGH);
            Notification.Builder b = builder(c, "wp")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(Ui.col(c, R.color.accent))
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openApp(c))
                    .setAutoCancel(true);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
        } catch (Exception ignored) { }
    }

    static void notifyVideo(Context c, Intent i) {
        channel(c, "wp", c.getString(R.string.ch_wp), NotificationManager.IMPORTANCE_HIGH);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = builder(c, "wp")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Ui.col(c, R.color.accent))
                .setContentTitle(c.getString(R.string.notif_video_title))
                .setContentText(c.getString(R.string.notif_video_text))
                .setContentIntent(pi).setAutoCancel(true).build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2, n);
    }

    // ---------------------------------------------------------------- images

    static Bitmap load(Context c, Uri u, int max) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > max) s *= 2;
        BitmapFactory.Options p = new BitmapFactory.Options();
        p.inSampleSize = s;
        in = c.getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, p);
        in.close();
        if (b == null) throw new IOException("bad image");
        int ori = 1;
        try {
            in = c.getContentResolver().openInputStream(u);
            ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            in.close();
        } catch (Exception ignored) { }
        int deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        return b;
    }

    static File saveJpeg(Context c, Bitmap b) throws IOException {
        File f = new File(c.getCacheDir(), "up.jpg");
        for (int q = 92; q >= 50; q -= 10) {
            FileOutputStream o = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, q, o);
            o.close();
            if (f.length() <= 6000000) break;
        }
        return f;
    }
}
