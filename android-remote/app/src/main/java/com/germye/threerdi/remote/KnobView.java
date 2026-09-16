package com.germye.threerdi.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class KnobView extends View {
    public interface Listener { void onValueChanged(int value); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private String label = "Control";
    private int cc = 20;
    private int value = 64;
    private float downY;
    private int downValue;
    private Listener listener;

    public KnobView(Context context) { super(context); init(); }
    public KnobView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setMinimumHeight(dp(132));
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(true);
    }

    public void configure(String label, int cc, int value, Listener listener) {
        this.label = label;
        this.cc = cc;
        this.value = Math.max(0, Math.min(127, value));
        this.listener = listener;
        invalidate();
    }

    public int getValue() { return value; }
    public void setValue(int v, boolean notify) {
        value = Math.max(0, Math.min(127, v));
        invalidate();
        if (notify && listener != null) listener.onValueChanged(value);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float centerX = w / 2f;
        float centerY = dp(52);
        float radius = Math.min(w * 0.31f, dp(38));
        arc.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(7));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(54, 54, 54));
        c.drawArc(arc, 135, 270, false, paint);

        paint.setColor(Color.rgb(183, 255, 48));
        c.drawArc(arc, 135, 270f * value / 127f, false, paint);

        float angle = (float) Math.toRadians(135 + 270f * value / 127f);
        float r2 = radius * 0.68f;
        paint.setStrokeWidth(dp(3));
        c.drawLine(centerX, centerY,
                centerX + (float)Math.cos(angle) * r2,
                centerY + (float)Math.sin(angle) * r2, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(13));
        c.drawText(label, centerX, dp(108), paint);
        paint.setColor(Color.rgb(170, 170, 170));
        paint.setTextSize(dp(11));
        c.drawText("CC" + cc + "  " + value, centerX, dp(126), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                downValue = value;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float delta = (downY - e.getY()) / Math.max(1f, dp(1.2f));
                setValue(downValue + Math.round(delta), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
