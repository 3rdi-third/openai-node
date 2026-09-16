package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class StartupActivity extends Activity {
    private static final float ART_W = 720f;
    private static final float ART_H = 1279f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        UiUtil.applyImmersive(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.startup_screen_v17);
        background.setScaleType(ImageView.ScaleType.FIT_XY);
        background.setAdjustViewBounds(false);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));

        View touchLayer = new View(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) return true;

                float x = event.getX() * ART_W / Math.max(1, getWidth());
                float y = event.getY() * ART_H / Math.max(1, getHeight());

                if (inside(x, y, 195, 610, 525, 716)) {
                    startActivity(new Intent(StartupActivity.this, MainActivity.class));
                    return true;
                }
                if (inside(x, y, 195, 713, 525, 806)) {
                    Intent intent = new Intent(StartupActivity.this, MainActivity.class);
                    intent.putExtra("open_presets", true);
                    startActivity(intent);
                    return true;
                }
                if (inside(x, y, 195, 802, 525, 892)) {
                    startActivity(new Intent(StartupActivity.this, SettingsActivity.class));
                    return true;
                }
                if (inside(x, y, 195, 888, 525, 978)) {
                    startActivity(new Intent(StartupActivity.this, ThemeActivity.class));
                    return true;
                }
                if (inside(x, y, 605, 1125, 710, 1202)) {
                    finishAffinity();
                    return true;
                }
                return true;
            }
        };
        touchLayer.setClickable(true);
        root.addView(touchLayer, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private static boolean inside(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtil.applyImmersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }
}
