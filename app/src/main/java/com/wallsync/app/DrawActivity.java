package com.wallsync.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Full-screen canvas for handwriting / emoji wallpapers, with a floating tool panel. */
public class DrawActivity extends Activity {
    DrawView dv;
    LinearLayout panel, topBar;
    View[] penSw, bgSw;
    static final int[] COLORS = {Color.BLACK, Color.WHITE, Color.RED, Color.rgb(255, 140, 0), Color.YELLOW,
            Color.rgb(0, 170, 0), Color.rgb(0, 150, 255), Color.BLUE, Color.rgb(160, 32, 240), Color.rgb(255, 105, 180)};
    static final String[] EMOJIS = {"😀", "😍", "😂", "🥰", "😎", "🤩", "😘", "🥳", "😇", "🤗",
            "❤️", "💖", "💙", "💚", "🔥", "⭐", "🌟", "✨", "🌹", "🌸",
            "🌈", "☀️", "🌙", "🦋", "🐱", "🐶", "🎉", "🎈", "👑", "💎", "👍", "🙏"};

    int dp(int v) { return Ui.dp(this, v); }

    int col(int id) { return Ui.col(this, id); }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    TextView roundBtn(String t, View.OnClickListener l) {
        TextView b = Ui.tv(this, t, 18, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.shape(this, 0x99000000, 22, 0));
        Ui.tap(b, l);
        return b;
    }

    TextView small(String t, View.OnClickListener l) {
        TextView b = Ui.tv(this, t, 13, col(R.color.accent), true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setBackground(Ui.ripple(this, Ui.shape(this, col(R.color.tonal), 14, col(R.color.stroke)), 14));
        Ui.tap(b, l);
        return b;
    }

    void paint(View v, int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(selected ? 3 : 1), selected ? col(R.color.accent) : col(R.color.stroke));
        v.setBackground(g);
    }

    void select(View[] arr, int idx) {
        for (int i = 0; i < arr.length; i++) paint(arr[i], COLORS[i], i == idx);
    }

    HorizontalScrollView colorRow(final boolean background) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        final View[] arr = new View[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            final int idx = i;
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    if (background) { dv.bgColor = COLORS[idx]; dv.bg = null; select(bgSw, idx); }
                    else { dv.color = COLORS[idx]; dv.emojiMode = false; select(penSw, idx); }
                    dv.invalidate();
                }
            });
            arr[i] = v;
            r.addView(v, lp);
        }
        if (background) { bgSw = arr; select(arr, 1); } else { penSw = arr; select(arr, 0); }
        hs.addView(r);
        return hs;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // edge-to-edge so the exported image matches the real screen size
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        dv = new DrawView(this);
        root.addView(dv, new FrameLayout.LayoutParams(-1, -1));

        int text = col(R.color.text), dim = col(R.color.dim);

        // ---- bottom tool panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        float rr = dp(28);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Ui.alpha(col(R.color.card), 0xF2));
        pbg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        panel.setBackground(pbg);
        panel.setElevation(dp(12));

        panel.addView(Ui.tv(this, getString(R.string.draw_pen_color), 12, dim, true));
        panel.addView(colorRow(false));
        panel.addView(Ui.tv(this, getString(R.string.draw_bg_color), 12, dim, true), Ui.lp(-1, -2, dp(6)));
        panel.addView(colorRow(true));

        panel.addView(Ui.tv(this, getString(R.string.draw_size), 12, dim, true), Ui.lp(-1, -2, dp(6)));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(20);
        sb.setProgressTintList(ColorStateList.valueOf(col(R.color.accent)));
        sb.setThumbTintList(ColorStateList.valueOf(col(R.color.accent)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                dv.width = 4 + p * 0.6f;
                dv.emoSize = 60 + p * 3f;
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        panel.addView(sb);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout er = row();
        for (final String e : EMOJIS) {
            TextView tv = new TextView(this);
            tv.setText(e);
            tv.setTextSize(28);
            tv.setPadding(dp(8), dp(2), dp(8), dp(2));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dv.emoji = e; dv.emojiMode = true; }
            });
            er.addView(tv);
        }
        hs.addView(er);
        panel.addView(hs, Ui.lp(-1, -2, dp(4)));

        LinearLayout tools = row();
        View[] bs = {
            small(getString(R.string.tool_pen), new View.OnClickListener() {
                @Override public void onClick(View v) { dv.emojiMode = false; }
            }),
            small(getString(R.string.tool_photo), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, 3);
                }
            }),
            small(getString(R.string.tool_undo), new View.OnClickListener() {
                @Override public void onClick(View v) { dv.undo(); }
            }),
            small(getString(R.string.tool_clear), new View.OnClickListener() {
                @Override public void onClick(View v) { dv.clearAll(); }
            })
        };
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) lp.setMarginStart(dp(8));
            tools.addView(bs[i], lp);
        }
        panel.addView(tools, Ui.lp(-1, -2, dp(10)));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2);
        plp.gravity = Gravity.BOTTOM;
        root.addView(panel, plp);

        // ---- top bar: close | palette toggle | send
        topBar = row();
        TextView close = roundBtn("✕", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        topBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView tg = roundBtn("🎨", new View.OnClickListener() {
            @Override public void onClick(View v) {
                panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        LinearLayout.LayoutParams tgp = new LinearLayout.LayoutParams(dp(44), dp(44));
        tgp.setMarginEnd(dp(10));
        topBar.addView(tg, tgp);
        TextView sd = Ui.button(this, getString(R.string.btn_send), 0, new View.OnClickListener() {
            @Override public void onClick(View v) { send(); }
        });
        sd.setMinHeight(dp(44));
        sd.setPadding(dp(18), dp(8), dp(18), dp(8));
        topBar.addView(sd, new LinearLayout.LayoutParams(-2, dp(44)));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2);
        tlp.gravity = Gravity.TOP;
        root.addView(topBar, tlp);

        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                topBar.setPadding(dp(12), in.getSystemWindowInsetTop() + dp(8), dp(12), dp(8));
                panel.setPadding(dp(16), dp(16), dp(16), in.getSystemWindowInsetBottom() + dp(16));
                return in;
            }
        });
        setContentView(root);
        root.requestApplyInsets();
    }

    void send() {
        final Bitmap bm = dv.export();
        Toast.makeText(this, R.string.draw_sending, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try { ok = Sync.submit(DrawActivity.this, "image", Sync.saveJpeg(DrawActivity.this, bm)); } catch (Exception ignored) { }
                final boolean sent = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(DrawActivity.this, sent ? R.string.sent_ok : R.string.sent_queued, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        }).start();
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == 3 && res == RESULT_OK && d != null && d.getData() != null) {
            try {
                dv.bg = Sync.load(this, d.getData(), 2000);
                dv.invalidate();
            } catch (Exception e) {
                Toast.makeText(this, R.string.draw_open_fail, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
