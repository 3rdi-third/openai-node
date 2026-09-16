package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public final class StartupActivity extends Activity {
    private static final float ART_W = 720f;
    private static final float ART_H = 1279f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        UiUtil.applyImmersive(this);

        StartupLayout root = new StartupLayout();
        setContentView(root);
    }

    private final class StartupLayout extends FrameLayout {
        private final ImageView background;
        private final TextView startButton;
        private final TextView presetsButton;
        private final TextView audioButton;
        private final TextView themeButton;
        private final TextView exitButton;
        private final TextView versionBadge;

        StartupLayout() {
            super(StartupActivity.this);
            setBackgroundColor(Color.rgb(12, 12, 12));
            setClipChildren(false);

            background = new ImageView(StartupActivity.this);
            background.setImageResource(R.drawable.startup_screen_v17);
            background.setScaleType(ImageView.ScaleType.FIT_XY);
            background.setAdjustViewBounds(false);
            addView(background, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            startButton = createButton("S T A R T", false);
            presetsButton = createButton("P R E S E T S", false);
            audioButton = createButton("A U D I O   S E T T I N G S", false);
            themeButton = createButton("C O L O U R   T H E M E", false);
            exitButton = createButton("EXIT", true);
            versionBadge = createVersionBadge();

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
            addView(versionBadge);
        }

        private TextView createButton(String text, boolean outlineOnly) {
            TextView button = new TextView(StartupActivity.this);
            button.setText(text);
            button.setTextColor(outlineOnly ? Color.rgb(239, 177, 72) : Color.rgb(22, 18, 12));
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, outlineOnly ? 11f : 13f);
            button.setGravity(Gravity.CENTER);
            button.setClickable(true);
            button.setFocusable(true);
            button.setIncludeFontPadding(false);

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(outlineOnly ? 3f : 5f));
            if (outlineOnly) {
                shape.setColor(Color.argb(88, 0, 0, 0));
                shape.setStroke((int) dp(1.4f), Color.rgb(239, 177, 72));
            } else {
                shape.setColor(Color.rgb(224, 133, 38));
                shape.setStroke((int) dp(1f), Color.rgb(112, 65, 19));
            }
            button.setBackground(shape);
            return button;
        }

        private TextView createVersionBadge() {
            TextView badge = new TextView(StartupActivity.this);
            badge.setText("v1.9");
            badge.setTextColor(Color.rgb(235, 220, 190));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            badge.setGravity(Gravity.CENTER);
            badge.setBackgroundColor(Color.argb(185, 5, 5, 5));
            return badge;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left;
            int h = bottom - top;

            background.layout(0, 0, w, h);
            layoutArt(startButton, 195, 610, 525, 716, w, h);
            layoutArt(presetsButton, 195, 713, 525, 806, w, h);
            layoutArt(audioButton, 195, 802, 525, 892, w, h);
            layoutArt(themeButton, 195, 888, 525, 978, w, h);
            layoutArt(exitButton, 604, 1124, 711, 1203, w, h);
            layoutArt(versionBadge, 8, 8, 78, 42, w, h);
        }

        private void layoutArt(View view, float l, float t, float r, float b, int w, int h) {
            int pxL = Math.round(l * w / ART_W);
            int pxT = Math.round(t * h / ART_H);
            int pxR = Math.round(r * w / ART_W);
            int pxB = Math.round(b * h / ART_H);
            view.layout(pxL, pxT, pxR, pxB);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
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
