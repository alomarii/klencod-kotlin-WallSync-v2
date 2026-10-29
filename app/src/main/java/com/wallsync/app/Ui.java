package com.wallsync.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small UI toolkit: rounded cards, gradient buttons, ripples and entrance animations. */
final class Ui {
    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int col(Context c, int id) { return c.getResources().getColor(id, c.getTheme()); }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    static GradientDrawable shape(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    static GradientDrawable grad(Context c, int a, int b, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Drawable ripple(Context c, Drawable content, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x33808080), content, mask);
    }

    static TextView tv(Context c, CharSequence t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = topMargin;
        return p;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        l.setBackground(shape(c, col(c, R.color.card), 24, col(c, R.color.stroke)));
        return l;
    }

    /** kind: 0 = gradient primary, 1 = tonal, 2 = warning outline. */
    static TextView button(Context c, CharSequence t, int kind, View.OnClickListener l) {
        TextView b = new TextView(c);
        b.setText(t);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        Drawable bg;
        if (kind == 0) {
            b.setTextColor(Color.WHITE);
            bg = grad(c, col(c, R.color.accent), col(c, R.color.accent2), 18);
        } else if (kind == 2) {
            int w = col(c, R.color.warn);
            b.setTextColor(w);
            bg = shape(c, alpha(w, 28), 18, alpha(w, 120));
        } else {
            b.setTextColor(col(c, R.color.accent));
            bg = shape(c, col(c, R.color.tonal), 18, col(c, R.color.stroke));
        }
        b.setBackground(ripple(c, bg, 18));
        tap(b, l);
        return b;
    }

    /** Click listener plus a subtle press-scale animation. */
    static void tap(View v, View.OnClickListener l) {
        v.setClickable(true);
        v.setOnClickListener(l);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        x.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    static void enter(View v, int index) {
        v.setAlpha(0f);
        v.setTranslationY(48f);
        v.animate().alpha(1f).translationY(0f).setStartDelay(60L * index).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
