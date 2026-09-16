package com.thirdi.sessiongrid;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.util.Arrays;

public class MainActivity extends Activity {
    private SessionGridView gridView;
    private final AudioEngine audio = new AudioEngine();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Color.rgb(17, 19, 21));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.hide(WindowInsets.Type.statusBars());
        }
        gridView = new SessionGridView();
        setContentView(gridView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gridView != null) gridView.forceStop();
    }

    @Override
    protected void onDestroy() {
        audio.stop();
        super.onDestroy();
    }

    private final class SessionGridView extends View {
        private static final int TRACKS = 6;
        private static final int SCENES = 8;
        private static final long INVALID = -1L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int[] active = new int[TRACKS];
        private final int[] queued = new int[TRACKS];
        private final int[] trackColors = {
                Color.rgb(236, 105, 74), Color.rgb(239, 198, 73),
                Color.rgb(109, 217, 116), Color.rgb(89, 196, 227),
                Color.rgb(175, 120, 236), Color.rgb(238, 115, 186)
        };
        private final String[] trackNames = {"KICK", "HATS", "BASS", "SYNTH", "NOISE", "PERC"};
        private boolean playing;
        private float bpm = 133f;
        private long playStarted = INVALID;
        private long lastBar = -1;

        SessionGridView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(17, 19, 21));
            text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            Arrays.fill(active, -1);
            Arrays.fill(queued, -1);
            handler.post(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (playing && playStarted != INVALID) {
                    double elapsedSec = (SystemClock.elapsedRealtime() - playStarted) / 1000.0;
                    double beat = elapsedSec * bpm / 60.0;
                    long bar = (long) Math.floor(beat / 4.0);
                    if (bar != lastBar) {
                        lastBar = bar;
                        commitQueued();
                    }
                }
                invalidate();
                handler.postDelayed(this, 33);
            }
        };

        private void commitQueued() {
            for (int t = 0; t < TRACKS; t++) {
                if (queued[t] != Integer.MIN_VALUE) {
                    active[t] = queued[t];
                    audio.setActiveScene(t, queued[t]);
                    queued[t] = Integer.MIN_VALUE;
                }
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            float top = Math.max(72f, h * 0.11f);
            float footer = Math.max(58f, h * 0.09f);
            float left = Math.max(58f, w * 0.07f);
            float sceneCol = Math.max(54f, w * 0.07f);
            float gridW = w - left - sceneCol - 12f;
            float gridH = h - top - footer - 10f;
            float cellW = gridW / TRACKS;
            float cellH = gridH / SCENES;

            drawTopBar(c, w, top);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.min(20f, cellW * 0.18f));
            for (int t = 0; t < TRACKS; t++) {
                float x = left + t * cellW;
                paint.setColor(darken(trackColors[t], 0.36f));
                c.drawRoundRect(new RectF(x + 3, top - 34, x + cellW - 3, top - 5), 7, 7, paint);
                text.setColor(Color.WHITE);
                c.drawText(trackNames[t], x + cellW / 2, top - 14, text);
            }

            for (int s = 0; s < SCENES; s++) {
                float y = top + s * cellH;
                paint.setColor(Color.rgb(38, 41, 44));
                c.drawRoundRect(new RectF(8, y + 3, left - 7, y + cellH - 3), 7, 7, paint);
                text.setColor(Color.rgb(180, 185, 188));
                text.setTextSize(Math.min(18f, cellH * 0.35f));
                c.drawText(String.valueOf(s + 1), (left - 1) / 2, y + cellH * 0.62f, text);

                for (int t = 0; t < TRACKS; t++) {
                    float x = left + t * cellW;
                    boolean isActive = active[t] == s;
                    boolean isQueued = queued[t] == s;
                    int base = trackColors[t];
                    paint.setColor(isActive ? base : (isQueued ? lighten(base, 0.20f) : darken(base, 0.64f)));
                    float inset = Math.max(3f, Math.min(cellW, cellH) * 0.05f);
                    RectF r = new RectF(x + inset, y + inset, x + cellW - inset, y + cellH - inset);
                    c.drawRoundRect(r, 9, 9, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(isQueued ? 4f : 1.5f);
                    paint.setColor(isQueued ? Color.WHITE : darken(base, 0.30f));
                    c.drawRoundRect(r, 9, 9, paint);
                    paint.setStyle(Paint.Style.FILL);

                    text.setTextSize(Math.min(15f, cellW * 0.14f));
                    text.setColor(isActive ? Color.BLACK : Color.rgb(226, 229, 231));
                    c.drawText(shortName(t, s), x + cellW / 2, y + cellH * 0.56f, text);
                    if (isActive) {
                        paint.setColor(Color.BLACK);
                        c.drawCircle(x + cellW - inset - 10, y + 12, 4.5f, paint);
                    }
                }

                float sx0 = left + gridW + 5;
                paint.setColor(Color.rgb(49, 53, 56));
                RectF sr = new RectF(sx0, y + 3, w - 8, y + cellH - 3);
                c.drawRoundRect(sr, 7, 7, paint);
                text.setTextSize(Math.min(14f, cellH * 0.27f));
                text.setColor(Color.rgb(184, 255, 87));
                c.drawText("▶ " + (s + 1), sx0 + (w - 8 - sx0) / 2, y + cellH * 0.60f, text);
            }

            drawFooter(c, w, h, left, cellW, footer);
        }

        private void drawTopBar(Canvas c, float w, float top) {
            paint.setColor(Color.rgb(28, 31, 33));
            c.drawRect(0, 0, w, top - 41, paint);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(22f);
            text.setColor(Color.WHITE);
            c.drawText("3rdi SESSION GRID", 18, 34, text);

            float transportX = w * 0.42f;
            paint.setColor(playing ? Color.rgb(184, 255, 87) : Color.rgb(61, 66, 69));
            c.drawRoundRect(new RectF(transportX, 10, transportX + 72, 48), 8, 8, paint);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(18f);
            text.setColor(playing ? Color.BLACK : Color.WHITE);
            c.drawText(playing ? "PAUSE" : "PLAY", transportX + 36, 35, text);

            paint.setColor(Color.rgb(61, 66, 69));
            c.drawRoundRect(new RectF(transportX + 80, 10, transportX + 148, 48), 8, 8, paint);
            text.setColor(Color.WHITE);
            c.drawText("STOP", transportX + 114, 35, text);

            float bpmX = w - 245;
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(13f);
            text.setColor(Color.rgb(150, 155, 158));
            c.drawText("TEMPO", bpmX + 45, 19, text);
            text.setTextSize(23f);
            text.setColor(Color.WHITE);
            c.drawText(String.format(java.util.Locale.US, "%.0f BPM", bpm), bpmX + 45, 43, text);

            paint.setColor(Color.rgb(61, 66, 69));
            c.drawRoundRect(new RectF(w - 185, 10, w - 135, 48), 8, 8, paint);
            c.drawRoundRect(new RectF(w - 125, 10, w - 75, 48), 8, 8, paint);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(25f);
            text.setColor(Color.WHITE);
            c.drawText("−", w - 160, 37, text);
            c.drawText("+", w - 100, 37, text);
        }

        private void drawFooter(Canvas c, float w, float h, float left, float cellW, float footer) {
            float y0 = h - footer + 6;
            text.setTextAlign(Paint.Align.CENTER);
            for (int t = 0; t < TRACKS; t++) {
                float x = left + t * cellW;
                paint.setColor(Color.rgb(45, 48, 51));
                c.drawRoundRect(new RectF(x + 4, y0, x + cellW - 4, h - 8), 8, 8, paint);
                text.setTextSize(13f);
                text.setColor(Color.rgb(180, 185, 188));
                c.drawText(active[t] >= 0 ? "■ STOP" : "—", x + cellW / 2, y0 + (h - 8 - y0) * 0.60f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(11f);
            text.setColor(Color.rgb(110, 116, 120));
            c.drawText("Tap clips to queue • Launch a scene on the right • Changes lock to the next bar", 12, h - 3, text);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY(), w = getWidth(), h = getHeight();
            float top = Math.max(72f, h * 0.11f);
            float footer = Math.max(58f, h * 0.09f);
            float left = Math.max(58f, w * 0.07f);
            float sceneCol = Math.max(54f, w * 0.07f);
            float gridW = w - left - sceneCol - 12f;
            float gridH = h - top - footer - 10f;
            float cellW = gridW / TRACKS;
            float cellH = gridH / SCENES;

            if (y < top - 41) {
                float transportX = w * 0.42f;
                if (x >= transportX && x <= transportX + 72 && y >= 10 && y <= 48) togglePlay();
                else if (x >= transportX + 80 && x <= transportX + 148 && y >= 10 && y <= 48) forceStop();
                else if (x >= w - 185 && x <= w - 135 && y >= 10 && y <= 48) changeBpm(-1);
                else if (x >= w - 125 && x <= w - 75 && y >= 10 && y <= 48) changeBpm(1);
                return true;
            }

            if (y >= top && y < top + gridH) {
                int scene = Math.max(0, Math.min(SCENES - 1, (int) ((y - top) / cellH)));
                if (x >= left && x < left + gridW) {
                    int track = Math.max(0, Math.min(TRACKS - 1, (int) ((x - left) / cellW)));
                    queueClip(track, scene);
                } else if (x >= left + gridW) {
                    queueScene(scene);
                }
                return true;
            }

            if (y >= h - footer && x >= left && x < left + gridW) {
                int track = Math.max(0, Math.min(TRACKS - 1, (int) ((x - left) / cellW)));
                queued[track] = -1;
                if (!playing) commitQueuedImmediate();
                invalidate();
            }
            return true;
        }

        private void queueClip(int track, int scene) {
            queued[track] = (active[track] == scene) ? -1 : scene;
            if (!playing) commitQueuedImmediate();
            invalidate();
        }

        private void queueScene(int scene) {
            for (int t = 0; t < TRACKS; t++) queued[t] = scene;
            if (!playing) commitQueuedImmediate();
            invalidate();
        }

        private void commitQueuedImmediate() {
            for (int t = 0; t < TRACKS; t++) {
                if (queued[t] != Integer.MIN_VALUE) {
                    active[t] = queued[t];
                    audio.setActiveScene(t, active[t]);
                    queued[t] = Integer.MIN_VALUE;
                }
            }
        }

        private void togglePlay() {
            if (playing) {
                playing = false;
                audio.stop();
                playStarted = INVALID;
            } else {
                playing = true;
                playStarted = SystemClock.elapsedRealtime();
                lastBar = -1;
                audio.setBpm(bpm);
                for (int t = 0; t < TRACKS; t++) audio.setActiveScene(t, active[t]);
                audio.start();
            }
            invalidate();
        }

        private void changeBpm(float delta) {
            bpm = Math.max(60, Math.min(220, bpm + delta));
            audio.setBpm(bpm);
            if (playing) {
                playStarted = SystemClock.elapsedRealtime();
                lastBar = -1;
            }
            invalidate();
        }

        void forceStop() {
            playing = false;
            playStarted = INVALID;
            lastBar = -1;
            Arrays.fill(active, -1);
            Arrays.fill(queued, Integer.MIN_VALUE);
            audio.clearAll();
            audio.stop();
            invalidate();
        }

        private String shortName(int track, int scene) {
            switch (track) {
                case 0: return scene % 2 == 0 ? "4X4" : "PUNCH";
                case 1: return scene % 3 == 0 ? "OPEN" : "16TH";
                case 2: return "BASS " + (scene + 1);
                case 3: return scene % 2 == 0 ? "FM" : "STAB";
                case 4: return scene % 2 == 0 ? "AIR" : "RUST";
                default: return scene % 2 == 0 ? "TOM" : "METAL";
            }
        }

        private int darken(int color, float factor) {
            return Color.rgb((int) (Color.red(color) * factor), (int) (Color.green(color) * factor), (int) (Color.blue(color) * factor));
        }

        private int lighten(int color, float amount) {
            return Color.rgb(
                    (int) (Color.red(color) + (255 - Color.red(color)) * amount),
                    (int) (Color.green(color) + (255 - Color.green(color)) * amount),
                    (int) (Color.blue(color) + (255 - Color.blue(color)) * amount));
        }
    }
}
