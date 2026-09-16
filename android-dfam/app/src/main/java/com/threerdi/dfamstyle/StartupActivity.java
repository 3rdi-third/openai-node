package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class StartupActivity extends Activity {
    private static final float ART_W = 720f;
    private static final float ART_H = 1279f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        UiUtil.applyImmersive(this);
        setContentView(new StartupLayout());
    }

    private final class StartupLayout extends FrameLayout {
        private final ArtworkView artwork;
        private final TextView startButton;
        private final TextView presetsButton;
        private final TextView audioButton;
        private final TextView themeButton;
        private final TextView exitButton;

        StartupLayout() {
            super(StartupActivity.this);
            setBackgroundColor(Color.rgb(8, 9, 11));

            artwork = new ArtworkView();
            addView(artwork, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            startButton = createButton("S T A R T", false);
            presetsButton = createButton("P R E S E T S", false);
            audioButton = createButton("A U D I O   S E T T I N G S", false);
            themeButton = createButton("C O L O U R   T H E M E", false);
            exitButton = createButton("EXIT", true);

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

        private TextView createButton(String text, boolean outlineOnly) {
            TextView button = new TextView(StartupActivity.this);
            button.setText(text);
            button.setTextColor(outlineOnly ? Color.rgb(239, 177, 72) : Color.rgb(20, 16, 10));
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, outlineOnly ? 10f : 13f);
            button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            button.setGravity(Gravity.CENTER);
            button.setClickable(true);
            button.setFocusable(true);
            button.setIncludeFontPadding(false);

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(outlineOnly ? 3f : 6f));
            if (outlineOnly) {
                shape.setColor(Color.argb(135, 4, 5, 7));
                shape.setStroke((int) dp(1.4f), Color.rgb(239, 177, 72));
            } else {
                shape.setColor(Color.rgb(224, 133, 38));
                shape.setStroke((int) dp(1f), Color.rgb(115, 65, 17));
            }
            button.setBackground(shape);
            return button;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left;
            int h = bottom - top;
            artwork.layout(0, 0, w, h);
            layoutArt(startButton, 195, 610, 525, 716, w, h);
            layoutArt(presetsButton, 195, 713, 525, 806, w, h);
            layoutArt(audioButton, 195, 802, 525, 892, w, h);
            layoutArt(themeButton, 195, 888, 525, 978, w, h);
            layoutArt(exitButton, 604, 1124, 711, 1203, w, h);
        }

        private void layoutArt(View view, float l, float t, float r, float b, int w, int h) {
            view.layout(
                    Math.round(l * w / ART_W),
                    Math.round(t * h / ART_H),
                    Math.round(r * w / ART_W),
                    Math.round(b * h / ART_H));
        }
    }

    private final class ArtworkView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ArtworkView() {
            super(StartupActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / ART_W;
            float sy = getHeight() / ART_H;
            canvas.save();
            canvas.scale(sx, sy);

            p.setShader(new LinearGradient(0, 0, ART_W, ART_H,
                    new int[]{Color.rgb(8, 10, 13), Color.rgb(22, 11, 18), Color.rgb(5, 7, 9)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, ART_W, ART_H, p);
            p.setShader(null);

            drawRibbon(canvas, Color.argb(145, 220, 31, 31), -90, 110, 455, 790, 160);
            drawRibbon(canvas, Color.argb(120, 0, 174, 239), 590, 80, 115, 900, 125);
            drawRibbon(canvas, Color.argb(105, 55, 224, 70), 20, 380, 690, 1100, 95);
            drawRibbon(canvas, Color.argb(120, 255, 126, 18), 690, 260, 120, 1180, 105);
            drawRibbon(canvas, Color.argb(100, 130, 42, 230), 80, 45, 650, 560, 80);

            p.setColor(Color.argb(95, 0, 0, 0));
            canvas.drawRect(0, 0, ART_W, ART_H, p);

            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.rgb(231, 210, 167));
            p.setTextSize(102);
            canvas.drawText("3RDI", ART_W / 2f, 485, p);

            p.setStrokeWidth(5);
            canvas.drawLine(215, 510, 505, 510, p);

            p.setTextSize(37);
            canvas.drawText("ANALOG PERCUSSION", ART_W / 2f, 558, p);

            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(13);
            p.setColor(Color.rgb(215, 202, 178));
            canvas.drawText("DFAM-STYLE PERCUSSION SYNTH", ART_W / 2f, 590, p);

            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(11);
            p.setColor(Color.rgb(220, 210, 190));
            canvas.drawText("v1.9", 20, 34, p);
            canvas.drawText("3RDI AUDIO LABS", 530, 34, p);

            p.setTextSize(10);
            canvas.drawText("R H Y T H M", 20, 1165, p);
            canvas.drawText("E X P E R I M E N T", 20, 1183, p);
            canvas.drawText("E V O L V E", 20, 1201, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(10);
            canvas.drawText("H A R D W A R E   S O U L   —   S O F T W A R E   F R E E D O M", ART_W / 2f, 1250, p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(10);
            canvas.drawText("created by", 590, 1162, p);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText("Ben Bouchnafa", 590, 1182, p);

            canvas.restore();
        }

        private void drawRibbon(Canvas canvas, int color, float x1, float y1, float x2, float y2, float width) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            path.reset();
            path.moveTo(x1, y1);
            path.cubicTo(
                    x1 + (x2 - x1) * 0.25f, y1 + 150,
                    x1 + (x2 - x1) * 0.75f, y2 - 170,
                    x2, y2);
            canvas.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
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
