package com.threerdi.dfamstyle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private SynthView synthView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        applyScreenPreference();
        synthView = new SynthView(this);
        setContentView(synthView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyScreenPreference();
        if (synthView != null) synthView.startAudio();
    }

    @Override
    protected void onPause() {
        if (synthView != null) synthView.stopAudio();
        super.onPause();
    }

    private void applyScreenPreference() {
        if (AudioPreferences.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    static final class SynthView extends View {
        private static final float DW = 1200f;
        private static final float DH = 720f;
        private static final int SAND = Color.rgb(218, 198, 165);
        private static final int SAND_LIGHT = Color.rgb(229, 213, 188);
        private static final int SAND_DARK = Color.rgb(188, 164, 128);
        private static final int ORANGE = Color.rgb(224, 111, 36);
        private static final int ORANGE_DARK = Color.rgb(187, 79, 22);
        private static final int BLACK = Color.rgb(18, 18, 16);

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempoRect = new RectF(58, 143, 160, 199);
        private final RectF prefsRect = new RectF(1012, 58, 1140, 101);
        private final RectF runRect = new RectF(890, 618, 985, 671);
        private final RectF trigRect = new RectF(995, 618, 1080, 671);
        private final RectF randomRect = new RectF(1090, 618, 1172, 671);
        private final Random random = new Random();

        volatile float tempo;
        volatile float vco1 = 62f;
        volatile float detune = 7f;
        volatile float fm = 0.28f;
        volatile float noise = 0.12f;
        volatile float cutoff = 780f;
        volatile float resonance = 0.34f;
        volatile float vcfDecay = 420f;
        volatile float vcaDecay = 330f;
        volatile float drive = 2.2f;

        volatile float lfoRate = 3.0f;
        volatile float lfoPitch = 0.0f;
        volatile float lfoFilter = 0.0f;
        volatile float lfoFm = 0.0f;
        volatile float filterEnvAmount = 5.5f;
        volatile float pitchEnvAmount = 0.0f;
        volatile boolean running = true;

        final float[] stepPitch = {0f, 0f, 7f, -5f, 0f, 12f, -2f, 5f};
        final float[] stepVelocity = {1.0f, 0.58f, 0.82f, 0.52f, 0.96f, 0.72f, 0.45f, 0.88f};

        private float canvasScale = 1f;
        private float canvasOffsetX = 0f;
        private float canvasOffsetY = 0f;
        private int activeControl = -1;
        private float lastTouchY;
        private SynthEngine engine;

        SynthView(Context context) {
            super(context);
            tempo = AudioPreferences.defaultBpm(context);
            setBackgroundColor(SAND_DARK);
            setFocusable(true);
        }

        void startAudio() {
            if (engine == null) {
                engine = new SynthEngine(this);
                engine.start();
            }
        }

        void stopAudio() {
            SynthEngine e = engine;
            engine = null;
            if (e != null) e.shutdown();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);
            canvasOffsetX = (getWidth() - DW * canvasScale) * 0.5f;
            canvasOffsetY = (getHeight() - DH * canvasScale) * 0.5f;

            canvas.save();
            canvas.translate(canvasOffsetX, canvasOffsetY);
            canvas.scale(canvasScale, canvasScale);

            p.setStyle(Paint.Style.FILL);
            p.setColor(SAND_DARK);
            canvas.drawRoundRect(new RectF(14, 14, 1186, 706), 22, 22, p);
            p.setColor(SAND);
            canvas.drawRoundRect(new RectF(28, 28, 1172, 692), 17, 17, p);
            p.setColor(SAND_LIGHT);
            canvas.drawRoundRect(new RectF(45, 45, 1155, 675), 12, 12, p);

            drawTitle(canvas);
            drawTempoBox(canvas);

            float[] topX = {220, 345, 470, 595, 720, 845, 970, 1095};
            drawKnob(canvas, topX[0], 170, 34, logNorm(vco1, 28, 440), "VCO 1", formatHz(vco1));
            drawKnob(canvas, topX[1], 170, 34, norm(detune, -24, 24), "VCO 2", String.format(Locale.US, "%+.1f st", detune));
            drawKnob(canvas, topX[2], 170, 34, fm, "FM", String.format(Locale.US, "%.0f%%", fm * 100));
            drawKnob(canvas, topX[3], 170, 34, noise, "NOISE", String.format(Locale.US, "%.0f%%", noise * 100));
            drawKnob(canvas, topX[4], 170, 34, logNorm(cutoff, 70, 12000), "CUTOFF", formatHz(cutoff));
            drawKnob(canvas, topX[5], 170, 34, resonance, "RESONANCE", String.format(Locale.US, "%.0f%%", resonance * 100));
            drawKnob(canvas, topX[6], 170, 34, norm(vcfDecay, 35, 2400), "VCF DECAY", String.format(Locale.US, "%.0f ms", vcfDecay));
            drawKnob(canvas, topX[7], 170, 34, norm(vcaDecay, 35, 2400), "VCA DECAY", String.format(Locale.US, "%.0f ms", vcaDecay));

            p.setColor(BLACK);
            p.setAlpha(80);
            canvas.drawRect(62, 251, 1138, 253, p);
            p.setAlpha(255);

            int currentStep = engine == null ? -1 : engine.currentStep;
            for (int i = 0; i < 8; i++) {
                float x = 112 + i * 139f;
                drawStep(canvas, i, x, currentStep == i);
            }

            p.setColor(BLACK);
            p.setAlpha(70);
            canvas.drawRect(62, 548, 1138, 550, p);
            p.setAlpha(255);

            drawDrive(canvas);
            drawModulation(canvas);
            drawButtons(canvas);

            canvas.restore();
            postInvalidateDelayed(33);
        }

        private void drawTitle(Canvas c) {
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setColor(BLACK);
            p.setTextSize(31);
            c.drawText("3RDI ANALOG PERCUSSION", 67, 88, p);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(11.5f);
            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);
            drawButton(c, prefsRect, "PREFERENCES", false);
        }

        private void drawTempoBox(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(SAND);
            c.drawRoundRect(tempoRect, 8, 8, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            p.setColor(ORANGE);
            c.drawRoundRect(tempoRect, 8, 8, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(BLACK);
            p.setTextSize(25);
            String bpm = Math.abs(tempo - Math.round(tempo)) < 0.01f
                    ? String.format(Locale.US, "%.0f", tempo)
                    : String.format(Locale.US, "%.1f", tempo);
            c.drawText(bpm, tempoRect.centerX(), tempoRect.centerY() + 4, p);
            p.setTextSize(9.5f);
            c.drawText("BPM", tempoRect.centerX(), tempoRect.bottom - 5, p);
            p.setTextSize(11.5f);
            c.drawText("TEMPO", tempoRect.centerX(), 220, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9.5f);
            c.drawText("TAP TO TYPE", tempoRect.centerX(), 235, p);
        }

        private void showTempoDialog() {
            final EditText edit = new EditText(getContext());
            edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            edit.setSingleLine(true);
            edit.setSelectAllOnFocus(true);
            edit.setText(String.format(Locale.US, "%.1f", tempo));
            edit.setHint("40–300");

            final AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle("Tempo (BPM)")
                    .setMessage("Type a value from 40 to 300 BPM")
                    .setView(edit)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Set", null)
                    .create();

            dialog.setOnShowListener(ignored -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        float entered = Float.parseFloat(edit.getText().toString().trim());
                        tempo = clamp(entered, 40f, 300f);
                        invalidate();
                        dialog.dismiss();
                    } catch (NumberFormatException ex) {
                        edit.setError("Enter a number between 40 and 300");
                    }
                });
                edit.requestFocus();
                edit.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                }, 120);
            });
            dialog.show();
        }

        private void drawStep(Canvas c, int index, float x, boolean active) {
            if (active) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(ORANGE);
                c.drawCircle(x, 286, 9, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(BLACK);
                c.drawCircle(x, 286, 7, p);
                p.setStyle(Paint.Style.FILL);
            }

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(BLACK);
            p.setTextSize(13);
            c.drawText(String.valueOf(index + 1), x, 316, p);

            drawMiniKnob(c, x, 370, 21, norm(stepPitch[index], -24, 24), "PITCH", String.format(Locale.US, "%+.1f", stepPitch[index]));
            drawMiniKnob(c, x, 472, 21, stepVelocity[index], "VELOCITY", String.format(Locale.US, "%.0f%%", stepVelocity[index] * 100));
        }

        private void drawKnob(Canvas c, float cx, float cy, float r, float value, String label, String valueText) {
            value = clamp(value, 0f, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORANGE);
            c.drawCircle(cx, cy, r, p);
            p.setColor(ORANGE_DARK);
            c.drawCircle(cx, cy, r * 0.68f, p);

            double a = Math.toRadians(135 + 270 * value);
            p.setStrokeWidth(3.6f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(BLACK);
            c.drawLine(cx, cy,
                    cx + (float) Math.cos(a) * r * 0.57f,
                    cy + (float) Math.sin(a) * r * 0.57f, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(11.5f);
            p.setColor(BLACK);
            c.drawText(label, cx, cy + r + 19, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(10.5f);
            c.drawText(valueText, cx, cy + r + 34, p);
        }

        private void drawMiniKnob(Canvas c, float cx, float cy, float r, float value, String label, String valueText) {
            value = clamp(value, 0f, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORANGE);
            c.drawCircle(cx, cy, r, p);
            p.setColor(ORANGE_DARK);
            c.drawCircle(cx, cy, r * 0.62f, p);
            double a = Math.toRadians(135 + 270 * value);
            p.setStrokeWidth(2.5f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(BLACK);
            c.drawLine(cx, cy,
                    cx + (float) Math.cos(a) * r * 0.54f,
                    cy + (float) Math.sin(a) * r * 0.54f, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(9.5f);
            p.setColor(BLACK);
            c.drawText(label, cx, cy + r + 16, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9.5f);
            c.drawText(valueText, cx, cy + r + 30, p);
        }

        private void drawCompactKnob(Canvas c, float cx, float cy, float value, String label, String valueText) {
            float r = 27f;
            value = clamp(value, 0f, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORANGE);
            c.drawCircle(cx, cy, r, p);
            p.setColor(ORANGE_DARK);
            c.drawCircle(cx, cy, 18f, p);
            double a = Math.toRadians(135 + 270 * value);
            p.setStrokeWidth(3f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(BLACK);
            c.drawLine(cx, cy,
                    cx + (float) Math.cos(a) * 14f,
                    cy + (float) Math.sin(a) * 14f, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(9.5f);
            p.setColor(BLACK);
            c.drawText(label, cx, cy - 40, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9f);
            c.drawText(valueText, cx, cy + 43, p);
        }

        private void drawDrive(Canvas c) {
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(BLACK);
            p.setTextSize(11.5f);
            c.drawText("DRIVE", 68, 594, p);
            RectF bar = new RectF(68, 606, 315, 625);
            p.setColor(SAND_DARK);
            c.drawRoundRect(bar, 10, 10, p);
            float amount = norm(drive, 1f, 6f);
            p.setColor(ORANGE);
            c.drawRoundRect(new RectF(bar.left, bar.top, bar.left + bar.width() * amount, bar.bottom), 10, 10, p);
            p.setColor(BLACK);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(10.5f);
            c.drawText(String.format(Locale.US, "%.1fx", drive), 325, 620, p);
            p.setTextSize(9f);
            c.drawText("DRAG BAR UP / DOWN", 68, 642, p);
        }

        private void drawModulation(Canvas c) {
            float[] x = {410, 485, 560, 635, 710, 785};
            drawCompactKnob(c, x[0], 612, logNorm(lfoRate, 0.05f, 30f), "LFO RATE", formatLfoRate(lfoRate));
            drawCompactKnob(c, x[1], 612, norm(lfoPitch, -12f, 12f), "LFO>PITCH", String.format(Locale.US, "%+.1f st", lfoPitch));
            drawCompactKnob(c, x[2], 612, norm(lfoFilter, -3f, 3f), "LFO>VCF", String.format(Locale.US, "%+.1f oct", lfoFilter));
            drawCompactKnob(c, x[3], 612, lfoFm, "LFO>FM", String.format(Locale.US, "%.0f%%", lfoFm * 100f));
            drawCompactKnob(c, x[4], 612, norm(filterEnvAmount, 0f, 10f), "VCF ENV", String.format(Locale.US, "%.1fx", filterEnvAmount));
            drawCompactKnob(c, x[5], 612, norm(pitchEnvAmount, -24f, 24f), "PITCH ENV", String.format(Locale.US, "%+.1f st", pitchEnvAmount));
        }

        private void drawButtons(Canvas c) {
            drawButton(c, runRect, running ? "STOP" : "RUN", running);
            drawButton(c, trigRect, "TRIG", false);
            drawButton(c, randomRect, "RAND", false);
        }

        private void drawButton(Canvas c, RectF r, String text, boolean lit) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(lit ? ORANGE : SAND);
            c.drawRoundRect(r, 8, 8, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(ORANGE);
            c.drawRoundRect(r, 8, 8, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(12f);
            p.setColor(BLACK);
            c.drawText(text, r.centerX(), r.centerY() + 4, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX() - canvasOffsetX) / canvasScale;
            float y = (event.getY() - canvasOffsetY) / canvasScale;

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (tempoRect.contains(x, y)) {
                    showTempoDialog();
                    return true;
                }
                if (prefsRect.contains(x, y)) {
                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));
                    return true;
                }
                if (runRect.contains(x, y)) {
                    running = !running;
                    if (!running && engine != null) engine.resetTransport();
                    invalidate();
                    return true;
                }
                if (trigRect.contains(x, y)) {
                    if (engine != null) engine.manualTrigger();
                    invalidate();
                    return true;
                }
                if (randomRect.contains(x, y)) {
                    randomizeSteps();
                    invalidate();
                    return true;
                }
                activeControl = hitControl(x, y);
                lastTouchY = y;
                return activeControl >= 0;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && activeControl >= 0) {
                float dy = y - lastTouchY;
                lastTouchY = y;
                adjustControl(activeControl, dy);
                invalidate();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                activeControl = -1;
                return true;
            }
            return true;
        }

        private int hitControl(float x, float y) {
            float[] topX = {220, 345, 470, 595, 720, 845, 970, 1095};
            for (int i = 0; i < topX.length; i++) {
                if (dist2(x, y, topX[i], 170) <= 46 * 46) return i + 1;
            }
            for (int i = 0; i < 8; i++) {
                float sx = 112 + i * 139f;
                if (dist2(x, y, sx, 370) <= 32 * 32) return 20 + i;
                if (dist2(x, y, sx, 472) <= 32 * 32) return 30 + i;
            }
            if (x >= 62 && x <= 350 && y >= 580 && y <= 650) return 10;

            float[] modX = {410, 485, 560, 635, 710, 785};
            for (int i = 0; i < modX.length; i++) {
                if (dist2(x, y, modX[i], 612) <= 38 * 38) return 40 + i;
            }
            return -1;
        }

        private void adjustControl(int id, float dy) {
            float up = -dy;
            switch (id) {
                case 1: vco1 = clamp((float) (vco1 * Math.exp(up * 0.012)), 28, 440); break;
                case 2: detune = clamp(detune + up * 0.08f, -24, 24); break;
                case 3: fm = clamp(fm + up * 0.004f, 0, 1); break;
                case 4: noise = clamp(noise + up * 0.004f, 0, 1); break;
                case 5: cutoff = clamp((float) (cutoff * Math.exp(up * 0.016)), 70, 12000); break;
                case 6: resonance = clamp(resonance + up * 0.004f, 0, 0.96f); break;
                case 7: vcfDecay = clamp((float) (vcfDecay * Math.exp(up * 0.014)), 35, 2400); break;
                case 8: vcaDecay = clamp((float) (vcaDecay * Math.exp(up * 0.014)), 35, 2400); break;
                case 10: drive = clamp(drive + up * 0.02f, 1, 6); break;
                case 40: lfoRate = clamp((float) (lfoRate * Math.exp(up * 0.018)), 0.05f, 30f); break;
                case 41: lfoPitch = clamp(lfoPitch + up * 0.055f, -12f, 12f); break;
                case 42: lfoFilter = clamp(lfoFilter + up * 0.012f, -3f, 3f); break;
                case 43: lfoFm = clamp(lfoFm + up * 0.004f, 0f, 1f); break;
                case 44: filterEnvAmount = clamp(filterEnvAmount + up * 0.025f, 0f, 10f); break;
                case 45: pitchEnvAmount = clamp(pitchEnvAmount + up * 0.10f, -24f, 24f); break;
                default:
                    if (id >= 20 && id < 28) {
                        int s = id - 20;
                        stepPitch[s] = clamp(stepPitch[s] + up * 0.10f, -24, 24);
                    } else if (id >= 30 && id < 38) {
                        int s = id - 30;
                        stepVelocity[s] = clamp(stepVelocity[s] + up * 0.004f, 0, 1);
                    }
                    break;
            }
        }

        private void randomizeSteps() {
            int[] scale = {-12, -7, -5, 0, 2, 5, 7, 10, 12};
            for (int i = 0; i < 8; i++) {
                stepPitch[i] = scale[random.nextInt(scale.length)];
                stepVelocity[i] = 0.35f + random.nextFloat() * 0.65f;
            }
        }

        private static float dist2(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return dx * dx + dy * dy;
        }

        private static String formatHz(float hz) {
            if (hz >= 1000f) return String.format(Locale.US, "%.1f kHz", hz / 1000f);
            return String.format(Locale.US, "%.0f Hz", hz);
        }

        private static String formatLfoRate(float hz) {
            if (hz < 1f) return String.format(Locale.US, "%.2f Hz", hz);
            return String.format(Locale.US, "%.1f Hz", hz);
        }

        private static float norm(float v, float lo, float hi) {
            return clamp((v - lo) / (hi - lo), 0f, 1f);
        }

        private static float logNorm(float v, float lo, float hi) {
            return clamp((float) (Math.log(v / lo) / Math.log(hi / lo)), 0f, 1f);
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    static final class SynthEngine implements Runnable {
        private final SynthView s;
        private final Thread thread;
        private final Random noiseGen = new Random();
        private final int requestedSampleRate;
        private final int bufferProfile;
        private final boolean requestLowLatency;
        private final float masterGain;

        private volatile boolean alive = true;
        private volatile boolean forceTrigger = false;
        volatile int currentStep = -1;

        private AudioTrack track;
        private int sampleRate;
        private double phase1 = 0.0;
        private double phase2 = 0.0;
        private double lfoPhase = 0.0;
        private double samplesToNextStep = 0.0;
        private float envAmp = 0f;
        private float envFilter = 0f;
        private float currentVelocity = 1f;
        private float currentBaseHz = 60f;
        private float z1, z2, z3, z4;
        private int step = 0;

        SynthEngine(SynthView synthView) {
            s = synthView;
            requestedSampleRate = AudioPreferences.sampleRate(s.getContext());
            bufferProfile = AudioPreferences.bufferProfile(s.getContext());
            requestLowLatency = AudioPreferences.lowLatency(s.getContext());
            masterGain = Math.max(0.25f, Math.min(1.2f, AudioPreferences.masterGain(s.getContext())));
            thread = new Thread(this, "3rdi-synth-audio");
            thread.setPriority(Thread.MAX_PRIORITY);
        }

        void start() {
            thread.start();
        }

        void shutdown() {
            alive = false;
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        void manualTrigger() {
            forceTrigger = true;
        }

        void resetTransport() {
            currentStep = -1;
            step = 0;
            samplesToNextStep = 0;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
            if (nativeRate < 22050) nativeRate = 48000;
            sampleRate = requestedSampleRate > 0 ? requestedSampleRate : nativeRate;

            track = buildTrack(sampleRate, requestLowLatency);
            if (track == null && sampleRate != nativeRate) {
                sampleRate = nativeRate;
                track = buildTrack(sampleRate, requestLowLatency);
            }
            if (track == null) {
                sampleRate = nativeRate;
                track = buildTrack(sampleRate, false);
            }
            if (track == null) return;

            short[] out = new short[256];
            try {
                track.play();
                while (alive) {
                    render(out);
                    int written = track.write(out, 0, out.length, AudioTrack.WRITE_BLOCKING);
                    if (written < 0) break;
                }
            } catch (Exception ignored) {
            } finally {
                releaseTrack();
            }
        }

        private AudioTrack buildTrack(int rate, boolean lowLatency) {
            int min = AudioTrack.getMinBufferSize(rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return null;
            int multiplier = bufferProfile <= 0 ? 1 : bufferProfile == 1 ? 2 : 4;
            int bufferBytes = Math.max(min * multiplier, 2048);

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            try {
                AudioTrack.Builder b = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM);
                if (lowLatency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
                }
                return b.build();
            } catch (Exception first) {
                if (lowLatency) {
                    try {
                        return new AudioTrack.Builder()
                                .setAudioAttributes(attrs)
                                .setAudioFormat(format)
                                .setBufferSizeInBytes(bufferBytes)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build();
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        }

        private void releaseTrack() {
            try {
                if (track != null) {
                    track.pause();
                    track.flush();
                    track.stop();
                    track.release();
                }
            } catch (Exception ignored) {
            }
            track = null;
        }

        private void render(short[] out) {
            float ampDecay = decayCoeff(s.vcaDecay);
            float filtDecay = decayCoeff(s.vcfDecay);

            for (int i = 0; i < out.length; i++) {
                if (forceTrigger) {
                    forceTrigger = false;
                    trigger(currentStep >= 0 ? currentStep : 0);
                }

                if (s.running) {
                    if (samplesToNextStep <= 0.0) {
                        currentStep = step;
                        trigger(step);
                        step = (step + 1) & 7;
                        double bpm = Math.max(40.0, Math.min(300.0, s.tempo));
                        samplesToNextStep += sampleRate * 60.0 / bpm / 2.0;
                    }
                    samplesToNextStep -= 1.0;
                }

                lfoPhase += Math.max(0.05f, s.lfoRate) / sampleRate;
                lfoPhase -= Math.floor(lfoPhase);
                float lfo = (float) Math.sin(lfoPhase * Math.PI * 2.0);

                float sample = 0f;
                if (envAmp > 0.00002f) {
                    float pitchModSemis = lfo * s.lfoPitch + envFilter * s.pitchEnvAmount;
                    float modBaseHz = currentBaseHz * (float) Math.pow(2.0, pitchModSemis / 12.0);
                    modBaseHz = Math.max(20f, Math.min(modBaseHz, sampleRate * 0.20f));

                    float f2 = modBaseHz * (float) Math.pow(2.0, s.detune / 12.0);
                    f2 = Math.max(20f, Math.min(f2, sampleRate * 0.20f));
                    phase2 += f2 / sampleRate;
                    phase2 -= Math.floor(phase2);
                    float osc2 = phase2 < 0.5 ? 1f : -1f;

                    float fmDepth = Math.max(0f, Math.min(1.5f, s.fm + lfo * s.lfoFm));
                    float instHz = modBaseHz * (1f + osc2 * fmDepth * 0.22f);
                    instHz = Math.max(20f, Math.min(instHz, sampleRate * 0.20f));
                    phase1 += instHz / sampleRate;
                    phase1 -= Math.floor(phase1);
                    float osc1 = 2f * Math.abs(2f * (float) phase1 - 1f) - 1f;

                    float n = (noiseGen.nextFloat() * 2f - 1f) * s.noise;
                    float mix = osc1 * 0.68f + osc2 * 0.42f + n;

                    float lfoFilterMultiplier = (float) Math.pow(2.0, lfo * s.lfoFilter);
                    float envFilterMultiplier = 1f + envFilter * s.filterEnvAmount;
                    float envCut = s.cutoff * lfoFilterMultiplier * envFilterMultiplier;
                    envCut = Math.max(60f, Math.min(envCut, sampleRate * 0.42f));
                    sample = ladderish(mix, envCut, s.resonance);
                    sample *= envAmp * currentVelocity;
                    sample = (float) Math.tanh(sample * s.drive * 1.45f);

                    envAmp *= ampDecay;
                    envFilter *= filtDecay;
                }

                int v = (int) (sample * 30000f * masterGain);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                out[i] = (short) v;
            }
        }

        private void trigger(int index) {
            int i = index & 7;
            currentVelocity = Math.max(0f, Math.min(1f, s.stepVelocity[i]));
            float pitch = s.stepPitch[i];
            currentBaseHz = s.vco1 * (float) Math.pow(2.0, pitch / 12.0);
            envAmp = 1f;
            envFilter = 1f;
        }

        private float ladderish(float input, float cutoffHz, float resonance) {
            float g = 1f - (float) Math.exp(-2.0 * Math.PI * cutoffHz / sampleRate);
            g = Math.max(0.001f, Math.min(0.78f, g));
            float feedback = z4 * resonance * 4.0f;
            float x = (float) Math.tanh(input - feedback);
            z1 += g * (x - z1);
            z2 += g * ((float) Math.tanh(z1) - z2);
            z3 += g * ((float) Math.tanh(z2) - z3);
            z4 += g * ((float) Math.tanh(z3) - z4);
            return z4;
        }

        private float decayCoeff(float ms) {
            float sec = Math.max(0.015f, ms * 0.001f);
            return (float) Math.exp(-1.0 / (sampleRate * sec));
        }
    }
}
