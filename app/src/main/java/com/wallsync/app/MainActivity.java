package com.wallsync.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int REQ_IMG = 1, REQ_VID = 2, REQ_AUTH = 77;
    static final long LOCK_AFTER_MS = 30000;
    static final String DEV_PHONE = "730215825";

    // survive rotation / theme changes
    static boolean sUnlocked, sAskedNotif;
    static long sLeft;

    final Handler h = new Handler(Looper.getMainLooper());
    FrameLayout root, lockView;
    LinearLayout content, pairCard;
    TextView banner, stTitle, stSub, evLine, secNote;
    View dot;
    EditText codeEt;
    Switch lockSw;
    ObjectAnimator pulse;
    String draft = "", sig = "";
    boolean external, authing, ignoreSw, night;
    Runnable afterAuth, onCancel;

    final BroadcastReceiver rcv = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            render();
            String m = i.getStringExtra("msg");
            if (m != null) banner(m, true);
        }
    };

    final Runnable hideBanner = new Runnable() {
        @Override public void run() {
            banner.animate().alpha(0f).translationY(-dp(40)).setDuration(220)
                    .withEndAction(new Runnable() {
                        @Override public void run() { banner.setVisibility(View.GONE); }
                    }).start();
        }
    };

    final Runnable unlockTask = new Runnable() {
        @Override public void run() {
            sUnlocked = true;
            lockView.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                @Override public void run() {
                    lockView.setVisibility(View.GONE);
                    lockView.setAlpha(1f);
                }
            }).start();
            askNotif();
        }
    };

    int dp(int v) { return Ui.dp(this, v); }

    int col(int id) { return Ui.col(this, id); }

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        styleBars();
        root = new FrameLayout(this);
        root.setBackgroundColor(col(R.color.bg));
        build();
        setContentView(root);
        render();
        for (int i = 0; i < content.getChildCount(); i++) Ui.enter(content.getChildAt(i), i);
    }

    @Override protected void onStart() {
        super.onStart();
        Sync.uiVisible = true;
        Sync.reg(this, rcv, new IntentFilter(Sync.STATE));
    }

    @Override protected void onResume() {
        super.onResume();
        if (Prefs.ready(this)) startSync(false);
        if (external) external = false;
        else if (Prefs.lockOn(this) && sUnlocked && sLeft > 0
                && SystemClock.elapsedRealtime() - sLeft > LOCK_AFTER_MS) sUnlocked = false;
        if (!Prefs.lockOn(this) || !isSecure()) sUnlocked = true;
        if (sUnlocked) {
            lockView.setVisibility(View.GONE);
            askNotif();
        } else {
            lockView.setVisibility(View.VISIBLE);
            if (!authing) {
                h.postDelayed(new Runnable() {
                    @Override public void run() { if (!sUnlocked && !authing) authenticate(unlockTask, null); }
                }, 250);
            }
        }
        render();
        new Thread(new Runnable() {
            @Override public void run() { Sync.refreshLink(MainActivity.this); }
        }).start();
    }

    @Override protected void onStop() {
        sLeft = SystemClock.elapsedRealtime();
        Sync.uiVisible = false;
        try { unregisterReceiver(rcv); } catch (Exception ignored) { }
        super.onStop();
    }

    void styleBars() {
        int bg = col(R.color.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!night) {
            int f = getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    // ------------------------------------------------------------------ UI construction

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    LinearLayout column() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        return r;
    }

    void add(View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        content.addView(v, p);
    }

    void build() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = column();
        content.setPadding(dp(16), dp(12), dp(16), dp(28));
        int w = Math.min(getResources().getDisplayMetrics().widthPixels, dp(560));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(w, -2);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        sv.addView(content, clp);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        int text = col(R.color.text), dim = col(R.color.dim);

        // ---- hero + live status
        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(Ui.grad(this, col(R.color.hero_a), col(R.color.hero_b), 28));
        hero.setElevation(dp(4));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setPadding(dp(8), dp(8), dp(8), dp(8));
        logo.setBackground(Ui.shape(this, 0x33FFFFFF, 18, 0));
        top.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout tc = column();
        tc.addView(Ui.tv(this, getString(R.string.app_name), 26, Color.WHITE, true));
        tc.addView(Ui.tv(this, getString(R.string.tagline), 13, 0xDDFFFFFF, false));
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, -2, 1);
        tcp.setMarginStart(dp(14));
        top.addView(tc, tcp);
        hero.addView(top);

        LinearLayout sb = row();
        sb.setGravity(Gravity.CENTER_VERTICAL);
        sb.setPadding(dp(14), dp(14), dp(14), dp(14));
        sb.setBackground(Ui.shape(this, 0x33000000, 18, 0));
        dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotLp.setMarginEnd(dp(12));
        sb.addView(dot, dotLp);
        LinearLayout sc = column();
        stTitle = Ui.tv(this, "", 16, Color.WHITE, true);
        stSub = Ui.tv(this, "", 12.5f, 0xCCFFFFFF, false);
        sc.addView(stTitle);
        sc.addView(stSub);
        sb.addView(sc, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(sb, Ui.lp(-1, -2, dp(18)));

        evLine = Ui.tv(this, "", 12.5f, 0xE6FFFFFF, false);
        hero.addView(evLine, Ui.lp(-1, -2, dp(12)));
        add(hero);

        // ---- pairing card (rebuilt per state)
        pairCard = Ui.card(this);
        add(pairCard);

        // ---- send card
        LinearLayout send = Ui.card(this);
        send.addView(Ui.tv(this, getString(R.string.send_title), 17, text, true));
        send.addView(tile("🖼️", R.string.act_image, R.string.act_image_sub, new View.OnClickListener() {
            @Override public void onClick(View v) { if (check()) pick("image/*", REQ_IMG); }
        }));
        send.addView(tile("✍️", R.string.act_draw, R.string.act_draw_sub, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (check()) { external = true; startActivity(new Intent(MainActivity.this, DrawActivity.class)); }
            }
        }));
        send.addView(tile("🎬", R.string.act_video, R.string.act_video_sub, new View.OnClickListener() {
            @Override public void onClick(View v) { if (check()) pick("video/*", REQ_VID); }
        }));
        add(send);

        // ---- live wallpaper card
        LinearLayout live = Ui.card(this);
        live.addView(Ui.tv(this, getString(R.string.live_title), 17, text, true));
        TextView lh = Ui.tv(this, getString(R.string.live_hint), 13, dim, false);
        lh.setLineSpacing(0, 1.15f);
        live.addView(lh, Ui.lp(-1, -2, dp(6)));
        live.addView(Ui.button(this, getString(R.string.live_btn), 1, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { external = true; startActivity(Sync.liveIntent(MainActivity.this)); }
                catch (Exception e) { external = false; banner(getString(R.string.live_unsupported), false); }
            }
        }), Ui.lp(-1, -2, dp(12)));
        add(live);

        // ---- security card
        LinearLayout sec = Ui.card(this);
        sec.addView(Ui.tv(this, getString(R.string.security_title), 17, text, true));
        LinearLayout sr = row();
        sr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stx = column();
        stx.addView(Ui.tv(this, getString(R.string.lock_switch), 15, text, true));
        stx.addView(Ui.tv(this, getString(R.string.lock_sub), 12.5f, dim, false));
        sr.addView(stx, new LinearLayout.LayoutParams(0, -2, 1));
        lockSw = new Switch(this);
        lockSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { onLockToggle(on); }
        });
        sr.addView(lockSw);
        sec.addView(sr, Ui.lp(-1, -2, dp(10)));
        secNote = Ui.tv(this, getString(R.string.lock_none), 12.5f, col(R.color.warn), false);
        sec.addView(secNote, Ui.lp(-1, -2, dp(8)));
        add(sec);

        // ---- developer credit
        LinearLayout f = Ui.card(this);
        f.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView by = Ui.tv(this, getString(R.string.about_by), 12.5f, dim, false);
        by.setGravity(Gravity.CENTER);
        f.addView(by);
        TextView nm = Ui.tv(this, getString(R.string.dev_name), 19, col(R.color.accent), true);
        nm.setGravity(Gravity.CENTER);
        f.addView(nm, Ui.lp(-2, -2, dp(4)));
        TextView ph = Ui.tv(this, "📞  " + DEV_PHONE, 15, text, true);
        ph.setTextDirection(View.TEXT_DIRECTION_LTR);
        ph.setGravity(Gravity.CENTER);
        ph.setPadding(dp(18), dp(10), dp(18), dp(10));
        ph.setBackground(Ui.ripple(this, Ui.shape(this, col(R.color.tonal), 20, col(R.color.stroke)), 20));
        Ui.tap(ph, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + DEV_PHONE))); }
                catch (Exception ignored) { }
            }
        });
        f.addView(ph, Ui.lp(-2, -2, dp(12)));
        TextView ver = Ui.tv(this, getString(R.string.version, versionName()), 11.5f, dim, false);
        ver.setGravity(Gravity.CENTER);
        f.addView(ver, Ui.lp(-2, -2, dp(12)));
        add(f);

        // ---- banner (in-app notification)
        banner = Ui.tv(this, "", 14.5f, Color.WHITE, true);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(18), dp(14), dp(18), dp(14));
        banner.setElevation(dp(12));
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
        bp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bp.topMargin = dp(10);
        bp.leftMargin = dp(16);
        bp.rightMargin = dp(16);
        root.addView(banner, bp);

        buildLock();
    }

    void buildLock() {
        lockView = new FrameLayout(this);
        lockView.setBackground(Ui.grad(this, col(R.color.hero_a), col(R.color.hero_b), 0));
        lockView.setClickable(true);
        lockView.setVisibility(Prefs.lockOn(this) && !sUnlocked ? View.VISIBLE : View.GONE);
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(32), dp(32), dp(32), dp(32));
        ImageView lg = new ImageView(this);
        lg.setImageResource(R.drawable.ic_logo);
        lg.setPadding(dp(22), dp(22), dp(22), dp(22));
        lg.setBackground(Ui.shape(this, 0x33FFFFFF, 40, 0));
        c.addView(lg, new LinearLayout.LayoutParams(dp(110), dp(110)));
        TextView t = Ui.tv(this, getString(R.string.lock_title), 24, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, Ui.lp(-2, -2, dp(24)));
        TextView s = Ui.tv(this, getString(R.string.lock_hint), 14, 0xDDFFFFFF, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s, Ui.lp(-2, -2, dp(8)));
        c.addView(Ui.button(this, getString(R.string.lock_btn), 1, new View.OnClickListener() {
            @Override public void onClick(View v) { if (!authing) authenticate(unlockTask, null); }
        }), Ui.lp(dp(240), -2, dp(32)));
        lockView.addView(c, new FrameLayout.LayoutParams(-1, -1));
        root.addView(lockView, new FrameLayout.LayoutParams(-1, -1));
    }

    View tile(String emoji, int title, int sub, View.OnClickListener l) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(12), dp(12), dp(12));
        r.setBackground(Ui.ripple(this, Ui.shape(this, col(R.color.tonal), 18, 0), 18));
        TextView ic = Ui.tv(this, emoji, 22, Color.WHITE, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.grad(this, col(R.color.accent), col(R.color.accent2), 14));
        r.addView(ic, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout tc = column();
        tc.addView(Ui.tv(this, getString(title), 15, col(R.color.text), true));
        tc.addView(Ui.tv(this, getString(sub), 12, col(R.color.dim), false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.setMarginStart(dp(12));
        r.addView(tc, tp);
        Ui.tap(r, l);
        r.setLayoutParams(Ui.lp(-1, -2, dp(10)));
        return r;
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "2.0"; }
    }

    // ------------------------------------------------------------------ rendering

    /** 0 = no code yet, 1 = code saved & waiting for the other phone, 2 = both phones linked. */
    int state() { return !Prefs.ready(this) ? 0 : Prefs.linked(this) ? 2 : 1; }

    void render() {
        if (content == null) return;
        int st = state();
        String peer = Prefs.peer(this);
        int dotCol;
        if (st == 2) {
            stTitle.setText(R.string.status_linked);
            stSub.setText(peer.isEmpty() ? getString(R.string.status_linked_sub)
                    : getString(R.string.status_linked_sub_peer, peer));
            dotCol = col(R.color.ok);
        } else if (st == 1) {
            stTitle.setText(R.string.status_waiting);
            stSub.setText(R.string.status_waiting_sub);
            dotCol = col(R.color.warn);
        } else {
            stTitle.setText(R.string.status_unlinked);
            stSub.setText(R.string.status_unlinked_sub);
            dotCol = 0xFFC9C9E6;
        }
        dot.setBackground(Ui.shape(this, dotCol, 6, 0));

        // last wallpaper event line
        SharedPreferences p = Prefs.p(this);
        long sent = p.getLong("sent", 0), done = p.getLong("ackDone", 0);
        String ev;
        if (sent != 0 && sent != done) ev = getString(R.string.ev_waiting);
        else if (sent != 0 && !p.getBoolean("ackOk", false)) ev = getString(R.string.ev_pending);
        else if (sent != 0) ev = getString(R.string.ev_done,
                DateFormat.getTimeFormat(this).format(new Date(p.getLong("ackAt", 0))));
        else ev = getString(R.string.ev_none);
        evLine.setText(ev);
        evLine.setVisibility(st == 0 ? View.GONE : View.VISIBLE);

        String ns = st + "|" + Prefs.code(this) + "|" + peer;
        if (!ns.equals(sig)) {
            sig = ns;
            if (pulse != null) { pulse.cancel(); pulse = null; }
            dot.setAlpha(1f);
            if (st == 1) {
                pulse = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f);
                pulse.setDuration(800);
                pulse.setRepeatCount(ObjectAnimator.INFINITE);
                pulse.setRepeatMode(ObjectAnimator.REVERSE);
                pulse.start();
            }
            renderPair(st);
        }
        boolean secure = isSecure();
        ignoreSw = true;
        lockSw.setChecked(Prefs.lockOn(this) && secure);
        ignoreSw = false;
        secNote.setVisibility(secure ? View.GONE : View.VISIBLE);
    }

    String group(String code) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 5 == 0) sb.append(' ');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    void renderPair(int st) {
        if (codeEt != null) draft = codeEt.getText().toString();
        codeEt = null;
        pairCard.removeAllViews();
        int text = col(R.color.text), dim = col(R.color.dim);
        pairCard.addView(Ui.tv(this, getString(R.string.pair_title), 17, text, true));

        if (st == 0) {
            pairCard.addView(Ui.tv(this, getString(R.string.code_label), 13, dim, false), Ui.lp(-1, -2, dp(10)));
            codeEt = new EditText(this);
            codeEt.setHint(R.string.code_hint);
            codeEt.setSingleLine(true);
            codeEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            codeEt.setFilters(new InputFilter[]{
                    new InputFilter() {
                        @Override public CharSequence filter(CharSequence src, int s, int e, Spanned d, int ds, int de) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = s; i < e; i++) {
                                char ch = src.charAt(i);
                                if (ch < 128 && Character.isLetterOrDigit(ch)) sb.append(Character.toUpperCase(ch));
                            }
                            String out = sb.toString();
                            return out.contentEquals(src.subSequence(s, e)) ? null : out;
                        }
                    }, new InputFilter.LengthFilter(24)});
            codeEt.setGravity(Gravity.CENTER);
            codeEt.setTextSize(22);
            codeEt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            codeEt.setLetterSpacing(0.12f);
            codeEt.setTextColor(text);
            codeEt.setHintTextColor(Ui.alpha(dim, 150));
            codeEt.setTextDirection(View.TEXT_DIRECTION_LTR);
            codeEt.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            codeEt.setPadding(dp(16), dp(16), dp(16), dp(16));
            codeEt.setBackground(Ui.shape(this, col(R.color.tonal), 16, col(R.color.stroke)));
            codeEt.setText(draft);
            pairCard.addView(codeEt, Ui.lp(-1, -2, dp(8)));
            pairCard.addView(Ui.button(this, getString(R.string.btn_generate), 1, new View.OnClickListener() {
                @Override public void onClick(View v) { genAndLink(); }
            }), Ui.lp(-1, -2, dp(12)));
            pairCard.addView(Ui.button(this, getString(R.string.btn_link), 0, new View.OnClickListener() {
                @Override public void onClick(View v) { link(codeEt.getText().toString()); }
            }), Ui.lp(-1, -2, dp(10)));
            return;
        }

        final String code = Prefs.code(this);
        TextView box = Ui.tv(this, group(code), 26, col(R.color.accent), true);
        box.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.setLetterSpacing(0.12f);
        box.setGravity(Gravity.CENTER);
        box.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.setPadding(dp(16), dp(18), dp(16), dp(18));
        box.setBackground(Ui.shape(this, col(R.color.tonal), 18, col(R.color.stroke)));
        pairCard.addView(box, Ui.lp(-1, -2, dp(12)));

        LinearLayout cs = row();
        TextView copy = Ui.button(this, getString(R.string.btn_copy), 1, new View.OnClickListener() {
            @Override public void onClick(View v) { copyCode(code); }
        });
        cs.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (st == 1) {
            TextView share = Ui.button(this, getString(R.string.btn_share), 1, new View.OnClickListener() {
                @Override public void onClick(View v) { shareCode(code); }
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
            sp.setMarginStart(dp(10));
            cs.addView(share, sp);
        }
        pairCard.addView(cs, Ui.lp(-1, -2, dp(12)));

        if (st == 1) {
            // other phone not linked yet: generating a new code is still possible
            TextView hint = Ui.tv(this, getString(R.string.status_waiting_sub), 12.5f, dim, false);
            hint.setGravity(Gravity.CENTER);
            pairCard.addView(hint, Ui.lp(-1, -2, dp(12)));
            pairCard.addView(Ui.button(this, getString(R.string.btn_generate), 1, new View.OnClickListener() {
                @Override public void onClick(View v) { regen(); }
            }), Ui.lp(-1, -2, dp(10)));
        } else {
            // linked: the generate button disappears, "change linked phone" takes its place
            pairCard.addView(Ui.button(this, getString(R.string.btn_change_phone), 2, new View.OnClickListener() {
                @Override public void onClick(View v) { confirmChange(); }
            }), Ui.lp(-1, -2, dp(10)));
        }
    }

    // ------------------------------------------------------------------ banner

    void banner(final String m, final boolean ok) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                banner.setText(m);
                banner.setBackground(Ui.shape(MainActivity.this, col(ok ? R.color.ok : R.color.danger), 18, 0));
                banner.setVisibility(View.VISIBLE);
                banner.setAlpha(0f);
                banner.setTranslationY(-dp(40));
                banner.animate().alpha(1f).translationY(0f).setDuration(260).start();
                h.removeCallbacks(hideBanner);
                h.postDelayed(hideBanner, 3400);
            }
        });
    }

    // ------------------------------------------------------------------ pairing actions

    void genAndLink() {
        String a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 10; i++) s.append(a.charAt(r.nextInt(a.length())));
        link(s.toString());
    }

    void link(String raw) {
        String c = raw.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (c.length() < 6) { banner(getString(R.string.code_short), false); return; }
        Prefs.p(this).edit().putString("code", c).putBoolean("joined", false)
                .putBoolean("linked", false).putString("peer", "").apply();
        draft = "";
        startSync(true);
        render();
        new Thread(new Runnable() {
            @Override public void run() {
                final int r = Sync.join(MainActivity.this);
                if (r == 1) {
                    Prefs.clearPairing(MainActivity.this);
                    stopService(new Intent(MainActivity.this, SyncService.class));
                } else if (r == 0) {
                    Sync.refreshLink(MainActivity.this);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        render();
                        if (r == 1) banner(getString(R.string.code_full), false);
                        else if (r == 2) banner(getString(R.string.link_offline), true);
                        else if (!Prefs.linked(MainActivity.this)) banner(getString(R.string.link_ok), true);
                    }
                });
            }
        }).start();
    }

    /** Current phone is waiting: drop the old room and create a fresh code. */
    void regen() {
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = Sync.unlink(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) { sig = ""; render(); genAndLink(); }
                        else banner(getString(R.string.link_failed), false);
                    }
                });
            }
        }).start();
    }

    void confirmChange() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.change_title)
                .setMessage(R.string.change_msg)
                .setPositiveButton(R.string.change_confirm, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { doUnlink(); }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void doUnlink() {
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = Sync.unlink(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) {
                            draft = "";
                            sig = "";
                            render();
                            banner(getString(R.string.unlinked_done), true);
                        } else {
                            banner(getString(R.string.link_failed), false);
                        }
                    }
                });
            }
        }).start();
    }

    void copyCode(String code) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WallSync", code));
        banner(getString(R.string.copied), true);
    }

    void shareCode(String code) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, code));
        external = true;
        startActivity(Intent.createChooser(i, getString(R.string.btn_share)));
    }

    void startSync(boolean restart) {
        try {
            Intent i = new Intent(this, SyncService.class);
            if (restart) stopService(i);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception ignored) { }
    }

    boolean check() {
        if (!Prefs.ready(this)) { banner(getString(R.string.need_code), false); return false; }
        return true;
    }

    // ------------------------------------------------------------------ app lock

    boolean isSecure() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    void onLockToggle(boolean on) {
        if (ignoreSw) return;
        if (on) {
            if (!isSecure()) { banner(getString(R.string.lock_none), false); render(); return; }
            Prefs.p(this).edit().putBoolean("lock", true).apply();
            sUnlocked = true;
        } else {
            // turning the lock off requires proving it's you
            authenticate(new Runnable() {
                @Override public void run() { Prefs.p(MainActivity.this).edit().putBoolean("lock", false).apply(); render(); }
            }, new Runnable() {
                @Override public void run() { render(); }
            });
        }
    }

    void finishAuth(boolean ok) {
        authing = false;
        Runnable a = afterAuth, c = onCancel;
        afterAuth = null;
        onCancel = null;
        if (ok) { if (a != null) a.run(); }
        else { banner(getString(R.string.auth_failed), false); if (c != null) c.run(); }
    }

    /** Fingerprint / face / device PIN-pattern-password. */
    void authenticate(Runnable ok, Runnable cancel) {
        if (authing) return;
        afterAuth = ok;
        onCancel = cancel;
        if (!isSecure()) { finishAuth(true); return; }   // nothing to ask: no screen lock on the device
        authing = true;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                promptBiometric();
            } else {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                Intent i = km.createConfirmDeviceCredentialIntent(getString(R.string.auth_title), getString(R.string.auth_sub));
                if (i == null) { finishAuth(true); return; }
                external = true;
                startActivityForResult(i, REQ_AUTH);
            }
        } catch (Exception e) {
            authing = false;
        }
    }

    void promptBiometric() {
        BiometricPrompt bp = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.auth_title))
                .setSubtitle(getString(R.string.auth_sub))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        bp.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { finishAuth(true); }
            @Override public void onAuthenticationError(int code, CharSequence msg) { finishAuth(false); }
        });
    }

    void askNotif() {
        if (Build.VERSION.SDK_INT >= 33 && !sAskedNotif
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            sAskedNotif = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    // ------------------------------------------------------------------ sending wallpapers

    void pick(String type, int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        external = true;
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == REQ_AUTH) { finishAuth(res == RESULT_OK); return; }
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final boolean video = req == REQ_VID;
        banner(getString(R.string.sending), true);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File f;
                    if (video) {
                        f = new File(getCacheDir(), "up.mp4");
                        InputStream in = getContentResolver().openInputStream(u);
                        FileOutputStream o = new FileOutputStream(f);
                        byte[] buf = new byte[16384];
                        long total = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > Sync.MAX_VIDEO) {
                                in.close();
                                o.close();
                                banner(getString(R.string.video_big), false);
                                return;
                            }
                            o.write(buf, 0, n);
                        }
                        in.close();
                        o.close();
                    } else {
                        Bitmap bm = Sync.load(MainActivity.this, u, 2200);
                        f = Sync.saveJpeg(MainActivity.this, bm);
                    }
                    boolean sent = Sync.submit(MainActivity.this, video ? "video" : "image", f);
                    banner(getString(sent ? R.string.sent_ok : R.string.sent_queued), true);
                } catch (Exception e) {
                    banner(getString(R.string.read_error), false);
                }
            }
        }).start();
    }
}
