package com.germye.threerdi.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public final class XYPadView extends View {
    public interface Listener { void onXY(int x, int y); }
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float xNorm = 0.5f, yNorm = 0.5f;
    private Listener listener;

    public XYPadView(Context c) { super(c); setMinimumHeight(dp(190)); }
    public void setListener(Listener l) { listener = l; }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(24,24,24));
        c.drawRoundRect(dp(8), dp(8), w-dp(8), h-dp(8), dp(18), dp(18), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.rgb(70,70,70));
        for (int i=1;i<4;i++) {
            float xx = dp(8) + (w-dp(16))*i/4f;
            float yy = dp(8) + (h-dp(16))*i/4f;
            c.drawLine(xx, dp(8), xx, h-dp(8), p);
            c.drawLine(dp(8), yy, w-dp(8), yy, p);
        }
        float x = dp(8) + xNorm * (w-dp(16));
        float y = dp(8) + yNorm * (h-dp(16));
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(183,255,48));
        c.drawCircle(x, y, dp(13), p);
        p.setColor(Color.WHITE); p.setTextSize(dp(12)); p.setTextAlign(Paint.Align.LEFT);
        c.drawText("XY MORPH  •  CC36 / CC37", dp(18), dp(29), p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            xNorm = clamp((e.getX()-dp(8)) / Math.max(1f, getWidth()-dp(16)));
            yNorm = clamp((e.getY()-dp(8)) / Math.max(1f, getHeight()-dp(16)));
            invalidate();
            if (listener != null) listener.onXY(Math.round(xNorm*127), Math.round((1f-yNorm)*127));
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
