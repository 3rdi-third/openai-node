package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private SynthView synthView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        synthView = new SynthView(this);
        setContentView(synthView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (synthView != null) synthView.startAudio();
    }

    @Override
    protected void onPause() {
        if (synthView != null) synthView.stopAudio();
        super.onPause();
    }

    static final class SynthView extends View {
        private static final float DW = 1200f;
        private static final float DH = 720f;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF runRect = new RectF(865, 610, 980, 675);
        private final RectF trigRect = new RectF(995, 610, 1085, 675);
        private final RectF randomRect = new RectF(1095, 610, 1180, 675);
        private final Random random = new Random();

        volatile float tempo = 132f;
        volatile float vco1 = 62f;
        volatile float detune = 7f;
        volatile float fm = 0.28f;
        volatile float noise = 0.12f;
        volatile float cutoff = 780f;
        volatile float resonance = 0.34f;
        volatile float vcfDecay = 420f;
        volatile float vcaDecay = 330f;
        volatile float drive = 2.2f;
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
            setBackgroundColor(Color.rgb(10, 10, 10));
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
            p.setColor(Color.rgb(19, 20, 21));
            canvas.drawRoundRect(new RectF(16, 16, 1184, 704), 20, 20, p);

            p.setColor(Color.rgb(235, 233, 220));
            canvas.drawRoundRect(new RectF(34, 34, 1166, 686), 15, 15, p);

            p.setColor(Color.rgb(18, 18, 18));
            canvas.drawRoundRect(new RectF(48, 48, 1152, 672), 12, 12, p);

            drawTitle(canvas);

            drawKnob(canvas, 90, 175, 54, norm(tempo, 40, 300), "TEMPO", String.format(Locale.US, "%.0f BPM", tempo));
            drawKnob(canvas, 225, 175, 54, logNorm(vco1, 28, 440), "VCO 1", formatHz(vco1));
            drawKnob(canvas, 360, 175, 54, norm(detune, -24, 24), "VCO 2", String.format(Locale.US, "%+.1f st", detune));
            drawKnob(canvas, 495, 175, 54, fm, "FM", String.format(Locale.US, "%.0f%%", fm * 100));
            drawKnob(canvas, 630, 175, 54, noise, "NOISE", String.format(Locale.US, "%.0f%%", noise * 100));
            drawKnob(canvas, 765, 175, 54, logNorm(cutoff, 70, 12000), "CUTOFF", formatHz(cutoff));
            drawKnob(canvas, 900, 175, 54, resonance, "RESONANCE", String.format(Locale.US, "%.0f%%", resonance * 100));
            drawKnob(canvas, 1035, 175, 54, norm(vcfDecay, 35, 2400), "VCF DECAY", String.format(Locale.US, "%.0f ms", vcfDecay));
            drawKnob(canvas, 1130, 175, 43, norm(vcaDecay, 35, 2400), "VCA", String.format(Locale.US, "%.0f", vcaDecay));

            p.setColor(Color.rgb(70, 70, 70));
            canvas.drawRect(64, 272, 1136, 275, p);

            int currentStep = engine == null ? -1 : engine.currentStep;
            for (int i = 0; i < 8; i++) {
                float x = 118 + i * 133f;
                drawStep(canvas, i, x, currentStep == i);
            }

            drawDrive(canvas);
            drawButtons(canvas);
            drawFooter(canvas);

            canvas.restore();
            postInvalidateDelayed(33);
        }

        private void drawTitle(Canvas c) {
            p.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Color.rgb(236, 236, 226));
            p.setTextSize(35);
            c.drawText("3RDI ANALOG PERCUSSION", 70, 92, p);
            p.setTextSize(16);
            p.setColor(Color.rgb(145, 145, 140));
            c.drawText("DFAM-STYLE DUAL OSCILLATOR / FILTER / 8-STEP SYNTH", 70, 118, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.rgb(210, 75, 42));
            p.setTextSize(18);
            c.drawText("LIVE ENGINE", 1128, 92, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawStep(Canvas c, int index, float x, boolean active) {
            if (active) {
                p.setColor(Color.rgb(214, 72, 37));
                c.drawCircle(x, 305, 13, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(Color.rgb(95, 95, 95));
                c.drawCircle(x, 305, 11, p);
                p.setStyle(Paint.Style.FILL);
            }

            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.rgb(200, 200, 194));
            p.setTextSize(18);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(String.valueOf(index + 1), x, 311, p);

            drawMiniKnob(c, x, 390, 39, norm(stepPitch[index], -24, 24), "PITCH", String.format(Locale.US, "%+.1f", stepPitch[index]));
            drawMiniKnob(c, x, 515, 39, stepVelocity[index], "VEL", String.format(Locale.US, "%.0f%%", stepVelocity[index] * 100));
        }

        private void drawKnob(Canvas c, float cx, float cy, float r, float value, String label, String valueText) {
            value = clamp(value, 0f, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(44, 44, 44));
            c.drawCircle(cx, cy, r, p);
            p.setColor(Color.rgb(23, 23, 23));
            c.drawCircle(cx, cy, r * 0.77f, p);

            double a = Math.toRadians(135 + 270 * value);
            float px = cx + (float) Math.cos(a) * r * 0.58f;
            float py = cy + (float) Math.sin(a) * r * 0.58f;
            p.setStrokeWidth(5);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(225, 225, 213));
            c.drawLine(cx, cy, px, py, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.rgb(213, 213, 204));
            p.setTextSize(15);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(label, cx, cy + r + 27, p);
            p.setTextSize(13);
            p.setColor(Color.rgb(137, 137, 132));
            c.drawText(valueText, cx, cy + r + 47, p);
        }

        private void drawMiniKnob(Canvas c, float cx, float cy, float r, float value, String label, String valueText) {
            value = clamp(value, 0f, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(54, 54, 54));
            c.drawCircle(cx, cy, r, p);
            p.setColor(Color.rgb(21, 21, 21));
            c.drawCircle(cx, cy, r * 0.72f, p);
            double a = Math.toRadians(135 + 270 * value);
            p.setStrokeWidth(4);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(229, 229, 216));
            c.drawLine(cx, cy,
                    cx + (float) Math.cos(a) * r * 0.55f,
                    cy + (float) Math.sin(a) * r * 0.55f, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(13);
            p.setColor(Color.rgb(188, 188, 181));
            c.drawText(label, cx, cy + r + 20, p);
            p.setTextSize(12);
            p.setColor(Color.rgb(125, 125, 121));
            c.drawText(valueText, cx, cy + r + 37, p);
        }

        private void drawDrive(Canvas c) {
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Color.rgb(175, 175, 168));
            p.setTextSize(14);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText("DRIVE", 70, 632, p);
            RectF bar = new RectF(135, 614, 520, 638);
            p.setColor(Color.rgb(46, 46, 46));
            c.drawRoundRect(bar, 12, 12, p);
            float amount = norm(drive, 1f, 6f);
            p.setColor(Color.rgb(213, 74, 40));
            c.drawRoundRect(new RectF(bar.left, bar.top, bar.left + bar.width() * amount, bar.bottom), 12, 12, p);
            p.setColor(Color.rgb(160, 160, 155));
            p.setTextSize(13);
            c.drawText(String.format(Locale.US, "%.1fx", drive), 535, 632, p);
        }

        private void drawButtons(Canvas c) {
            drawButton(c, runRect, running ? "STOP" : "RUN", running);
            drawButton(c, trigRect, "TRIG", false);
            drawButton(c, randomRect, "RAND", false);
        }

        private void drawButton(Canvas c, RectF r, String text, boolean lit) {
            p.setColor(lit ? Color.rgb(178, 55, 29) : Color.rgb(48, 48, 48));
            c.drawRoundRect(r, 10, 10, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(Color.rgb(100, 100, 96));
            c.drawRoundRect(r, 10, 10, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextSize(16);
            p.setColor(Color.rgb(235, 235, 224));
            c.drawText(text, r.centerX(), r.centerY() + 6, p);
        }

        private void drawFooter(Canvas c) {
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(android.graphics.Typeface.DEFAULT);
            p.setTextSize(12);
            p.setColor(Color.rgb(100, 100, 96));
            c.drawText("DRAG KNOBS UP/DOWN • NO SAMPLES • REAL-TIME SYNTHESIS", 70, 664, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX() - canvasOffsetX) / canvasScale;
            float y = (event.getY() - canvasOffsetY) / canvasScale;

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
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
            float[] topX = {90, 225, 360, 495, 630, 765, 900, 1035, 1130};
            for (int i = 0; i < topX.length; i++) {
                float r = i == 8 ? 56 : 68;
                if (dist2(x, y, topX[i], 175) <= r * r) return i;
            }
            for (int i = 0; i < 8; i++) {
                float sx = 118 + i * 133f;
                if (dist2(x, y, sx, 390) <= 55 * 55) return 20 + i;
                if (dist2(x, y, sx, 515) <= 55 * 55) return 30 + i;
            }
            if (x >= 120 && x <= 555 && y >= 595 && y <= 655) return 10;
            return -1;
        }

        private void adjustControl(int id, float dy) {
            float up = -dy;
            switch (id) {
                case 0: tempo = clamp(tempo + up * 0.65f, 40, 300); break;
                case 1: vco1 = clamp((float) (vco1 * Math.exp(up * 0.012)), 28, 440); break;
                case 2: detune = clamp(detune + up * 0.08f, -24, 24); break;
                case 3: fm = clamp(fm + up * 0.004f, 0, 1); break;
                case 4: noise = clamp(noise + up * 0.004f, 0, 1); break;
                case 5: cutoff = clamp((float) (cutoff * Math.exp(up * 0.016)), 70, 12000); break;
                case 6: resonance = clamp(resonance + up * 0.004f, 0, 0.96f); break;
                case 7: vcfDecay = clamp((float) (vcfDecay * Math.exp(up * 0.014)), 35, 2400); break;
                case 8: vcaDecay = clamp((float) (vcaDecay * Math.exp(up * 0.014)), 35, 2400); break;
                case 10: drive = clamp(drive + up * 0.02f, 1, 6); break;
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

        private volatile boolean alive = true;
        private volatile boolean forceTrigger = false;
        volatile int currentStep = -1;

        private AudioTrack track;
        private int sampleRate;
        private double phase1 = 0.0;
        private double phase2 = 0.0;
        private double samplesToNextStep = 0.0;
        private float envAmp = 0f;
        private float envFilter = 0f;
        private float currentVelocity = 1f;
        private float currentBaseHz = 60f;
        private float z1, z2, z3, z4;
        private int step = 0;

        SynthEngine(SynthView synthView) {
            s = synthView;
            thread = new Thread(this, "3rdi-synth-audio");
            thread.setPriority(Thread.MAX_PRIORITY);
        }

        void start() {
            thread.start();
        }

        void shutdown() {
            alive = false;
            try {
                thread.join(400);
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
            sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
            if (sampleRate < 22050) sampleRate = 48000;
            int min = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(min * 2, 4096);

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioTrack.Builder b = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }
            track = b.build();
            short[] out = new short[256];

            try {
                track.play();
                while (alive) {
                    render(out);
                    track.write(out, 0, out.length, AudioTrack.WRITE_BLOCKING);
                }
            } catch (Exception ignored) {
                // Keep app alive if an OEM audio driver rejects low-latency mode.
            } finally {
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
                        double bpm = Math.max(40.0, s.tempo);
                        samplesToNextStep += sampleRate * 60.0 / bpm / 2.0;
                    }
                    samplesToNextStep -= 1.0;
                }

                float sample = 0f;
                if (envAmp > 0.00002f) {
                    float f2 = currentBaseHz * (float) Math.pow(2.0, s.detune / 12.0);
                    phase2 += f2 / sampleRate;
                    phase2 -= Math.floor(phase2);
                    float osc2 = phase2 < 0.5 ? 1f : -1f;

                    float instHz = currentBaseHz * (1f + osc2 * s.fm * 0.22f);
                    instHz = Math.max(20f, Math.min(instHz, sampleRate * 0.20f));
                    phase1 += instHz / sampleRate;
                    phase1 -= Math.floor(phase1);
                    float osc1 = 2f * Math.abs(2f * (float) phase1 - 1f) - 1f;

                    float n = (noiseGen.nextFloat() * 2f - 1f) * s.noise;
                    float mix = osc1 * 0.68f + osc2 * 0.42f + n;

                    float envCut = s.cutoff * (1f + envFilter * 5.5f);
                    envCut = Math.max(60f, Math.min(envCut, sampleRate * 0.42f));
                    sample = ladderish(mix, envCut, s.resonance);
                    sample *= envAmp * currentVelocity;
                    sample = (float) Math.tanh(sample * s.drive * 1.45f);

                    envAmp *= ampDecay;
                    envFilter *= filtDecay;
                }

                int v = (int) (sample * 30000f);
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
