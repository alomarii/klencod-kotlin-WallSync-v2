package com.wallsync.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.UUID;

final class Prefs {
    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("ws", Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return "https://wallpaper-9fa40-default-rtdb.firebaseio.com";
    }

    static String code(Context c) { return p(c).getString("code", "").trim(); }

    static String dev(Context c) {
        String d = p(c).getString("dev", null);
        if (d == null) {
            d = UUID.randomUUID().toString().substring(0, 8);
            p(c).edit().putString("dev", d).apply();
        }
        return d;
    }

    static boolean ready(Context c) { return code(c).length() >= 6; }

    /** True when the other phone has joined the same pairing code. */
    static boolean linked(Context c) { return p(c).getBoolean("linked", false); }

    static String peer(Context c) { return p(c).getString("peer", ""); }

    /** App lock (biometrics / device credential). On by default. */
    static boolean lockOn(Context c) { return p(c).getBoolean("lock", true); }

    /** Forgets the current pairing completely (the device id is kept). */
    static void clearPairing(Context c) {
        p(c).edit().remove("code").remove("joined").remove("linked").remove("peer")
                .remove("sent").remove("ackDone").remove("ackOk").remove("ackAt")
                .remove("pt").remove("applied").remove("pendAck").apply();
        try { new File(c.getApplicationContext().getFilesDir(), "pending.bin").delete(); } catch (Exception ignored) { }
    }
}
