package com.wallsync.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

public class DrawView extends View {
    static class St { Path p = new Path(); int c; float w; }
    static class Em { String s; float x, y, z; }

    final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final ArrayList<Object> hist = new ArrayList<Object>();
    Bitmap bg;
    int bgColor = Color.WHITE;
    int color = Color.BLACK;
    float width = 16, emoSize = 120;
    boolean emojiMode;
    String emoji = "😀";
    St cur;
    Em sel;
    float lx, ly;

    public DrawView(Context c) {
        super(c);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        tp.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onDraw(Canvas cv) {
        cv.drawColor(bgColor);
        if (bg != null) {
            float s = Math.max((float) getWidth() / bg.getWidth(), (float) getHeight() / bg.getHeight());
            cv.save();
            cv.translate((getWidth() - bg.getWidth() * s) / 2f, (getHeight() - bg.getHeight() * s) / 2f);
            cv.scale(s, s);
            cv.drawBitmap(bg, 0, 0, null);
            cv.restore();
        }
        for (Object o : hist) {
            if (o instanceof St) {
                St t = (St) o;
                sp.setColor(t.c);
                sp.setStrokeWidth(t.w);
                cv.drawPath(t.p, sp);
            } else {
                Em e = (Em) o;
                tp.setTextSize(e.z);
                cv.drawText(e.s, e.x, e.y + e.z * 0.35f, tp);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (emojiMode) {
                    sel = null;
                    for (int i = hist.size() - 1; i >= 0; i--) {
                        Object o = hist.get(i);
                        if (o instanceof Em) {
                            Em e = (Em) o;
                            if (Math.hypot(x - e.x, y - e.y) < e.z * 0.6f) { sel = e; break; }
                        }
                    }
                    if (sel == null) {
                        sel = new Em();
                        sel.s = emoji; sel.x = x; sel.y = y; sel.z = emoSize;
                        hist.add(sel);
                    }
                } else {
                    cur = new St();
                    cur.c = color; cur.w = width;
                    cur.p.moveTo(x, y);
                    cur.p.lineTo(x + 0.1f, y);
                    hist.add(cur);
                }
                lx = x; ly = y;
                break;
            case MotionEvent.ACTION_MOVE:
                if (emojiMode) {
                    if (sel != null) { sel.x += x - lx; sel.y += y - ly; }
                } else if (cur != null) {
                    cur.p.quadTo(lx, ly, (x + lx) / 2f, (y + ly) / 2f);
                }
                lx = x; ly = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cur = null; sel = null;
                break;
        }
        invalidate();
        return true;
    }

    void undo() { if (!hist.isEmpty()) { hist.remove(hist.size() - 1); invalidate(); } }
    void clearAll() { hist.clear(); invalidate(); }

    Bitmap export() {
        Bitmap b = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b));
        return b;
    }
}
