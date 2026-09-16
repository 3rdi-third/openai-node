package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class StartupActivity extends Activity {
    private static final float ART_W = 360f;
    private static final float ART_H = 640f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        UiUtil.applyImmersive(this);
        setContentView(new StartupLayout());
    }

    private final class StartupLayout extends FrameLayout {
        private final ImageView background;
        private final View startButton;
        private final View presetsButton;
        private final View audioButton;
        private final View themeButton;
        private final View exitButton;

        StartupLayout() {
            super(StartupActivity.this);
            setBackgroundColor(Color.BLACK);

            background = new ImageView(StartupActivity.this);
            background.setImageResource(com.threerdi.dfamstyle.R.drawable.startup_screen_final);
            background.setScaleType(ImageView.ScaleType.FIT_XY);
            background.setContentDescription("3rdi Analog Percussion startup artwork");
            addView(background, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            startButton = touchZone();
            presetsButton = touchZone();
            audioButton = touchZone();
            themeButton = touchZone();
            exitButton = touchZone();

            startButton.setOnClickListener(v -> startActivity(new Intent(StartupActivity.this, MainActivity.class)));
            presetsButton.setOnClickListener(v -> {
                Intent intent = new Intent(StartupActivity.this, MainActivity.class);
                intent.putExtra("open_presets", true);
                startActivity(intent);
            });
            audioButton.setOnClickListener(v -> startActivity(new Intent(StartupActivity.this, SettingsActivity.class)));
            themeButton.setOnClickListener(v -> startActivity(new Intent(StartupActivity.this, ThemeActivity.class)));
            exitButton.setOnClickListener(v -> finishAffinity());

            addView(startButton);
            addView(presetsButton);
            addView(audioButton);
            addView(themeButton);
            addView(exitButton);
        }

        private View touchZone() {
            View v = new View(StartupActivity.this);
            v.setBackgroundColor(Color.TRANSPARENT);
            v.setClickable(true);
            v.setFocusable(true);
            return v;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left;
            int h = bottom - top;
            background.layout(0, 0, w, h);
            layoutArt(startButton, 104, 311, 258, 354, w, h);
            layoutArt(presetsButton, 104, 360, 258, 401, w, h);
            layoutArt(audioButton, 104, 407, 258, 449, w, h);
            layoutArt(themeButton, 104, 455, 258, 497, w, h);
            layoutArt(exitButton, 310, 570, 352, 606, w, h);
        }

        private void layoutArt(View view, float l, float t, float r, float b, int w, int h) {
            view.layout(Math.round(l * w / ART_W), Math.round(t * h / ART_H),
                    Math.round(r * w / ART_W), Math.round(b * h / ART_H));
        }
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
