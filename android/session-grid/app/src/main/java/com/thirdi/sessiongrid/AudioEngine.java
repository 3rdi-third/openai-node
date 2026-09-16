package com.thirdi.sessiongrid;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

public final class AudioEngine {
    private static final int SAMPLE_RATE = 44100;
    private static final int TRACKS = 6;

    private final int[] activeScenes = {-1, -1, -1, -1, -1, -1};
    private final Random random = new Random(0x33D1);
    private volatile boolean running;
    private volatile float bpm = 133f;
    private Thread thread;
    private AudioTrack audioTrack;
    private long sampleCursor;

    public synchronized void start() {
        if (running) return;
        running = true;
        int min = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferBytes = Math.max(min, 4096 * 4);
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build();
        audioTrack.play();
        thread = new Thread(this::renderLoop, "SessionGridAudio");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
        if (audioTrack != null) {
            try { audioTrack.pause(); } catch (Exception ignored) { }
            try { audioTrack.flush(); } catch (Exception ignored) { }
            try { audioTrack.release(); } catch (Exception ignored) { }
            audioTrack = null;
        }
        sampleCursor = 0;
    }

    public void setBpm(float value) {
        bpm = Math.max(60f, Math.min(220f, value));
    }

    public synchronized void setActiveScene(int track, int scene) {
        if (track >= 0 && track < TRACKS) activeScenes[track] = scene;
    }

    public synchronized void clearAll() {
        for (int i = 0; i < TRACKS; i++) activeScenes[i] = -1;
    }

    private void renderLoop() {
        final int frames = 512;
        final short[] out = new short[frames * 2];
        while (running) {
            int[] scenes = new int[TRACKS];
            synchronized (this) {
                System.arraycopy(activeScenes, 0, scenes, 0, TRACKS);
            }
            float localBpm = bpm;
            for (int i = 0; i < frames; i++) {
                double t = (sampleCursor + i) / (double) SAMPLE_RATE;
                double beat = t * localBpm / 60.0;
                double mix = 0.0;
                if (scenes[0] >= 0) mix += kick(beat, scenes[0]) * 0.95;
                if (scenes[1] >= 0) mix += hats(beat, scenes[1]) * 0.28;
                if (scenes[2] >= 0) mix += bass(beat, scenes[2], t) * 0.34;
                if (scenes[3] >= 0) mix += synth(beat, scenes[3], t) * 0.23;
                if (scenes[4] >= 0) mix += texture(beat, scenes[4], t) * 0.18;
                if (scenes[5] >= 0) mix += percussion(beat, scenes[5], t) * 0.27;
                mix = Math.tanh(mix * 1.15) * 0.75;
                short s = (short) (Math.max(-1.0, Math.min(1.0, mix)) * 32767.0);
                out[i * 2] = s;
                out[i * 2 + 1] = s;
            }
            sampleCursor += frames;
            AudioTrack track = audioTrack;
            if (track != null) track.write(out, 0, out.length, AudioTrack.WRITE_BLOCKING);
        }
    }

    private static double frac(double x) { return x - Math.floor(x); }

    private double kick(double beat, int scene) {
        int div = (scene == 5 || scene == 7) ? 2 : 1;
        double phaseBeat = frac(beat * div);
        double sec = phaseBeat * 60.0 / bpm / div;
        if (sec > 0.23) return 0;
        double env = Math.exp(-sec * (18.0 + scene * 0.5));
        double f0 = 155.0 + scene * 3;
        double f = 46.0 + (f0 - 46.0) * Math.exp(-sec * 28.0);
        double body = Math.sin(2 * Math.PI * f * sec);
        double click = sec < 0.012 ? (1.0 - sec / 0.012) * 0.35 : 0.0;
        return body * env + click;
    }

    private double hats(double beat, int scene) {
        int steps = 2 + (scene % 4) * 2;
        double p = frac(beat * steps);
        double sec = p * 60.0 / bpm / steps;
        if (sec > 0.045 + (scene % 3) * 0.01) return 0;
        double env = Math.exp(-sec * 75.0);
        double noise = random.nextDouble() * 2.0 - 1.0;
        return noise * env;
    }

    private double bass(double beat, int scene, double t) {
        int step = ((int) Math.floor(beat * 2.0)) & 7;
        int[] pattern = {0, 0, 3, 0, 5, 3, 7, 2};
        int semitone = pattern[(step + scene) & 7] + (scene % 3) * 2;
        double base = 46.25 * Math.pow(2.0, semitone / 12.0);
        double p = frac(beat * 2.0);
        double env = Math.exp(-p * (2.2 + (scene % 4) * 0.35));
        double saw = 2.0 * frac(t * base) - 1.0;
        double sine = Math.sin(2 * Math.PI * base * t);
        return (saw * 0.55 + sine * 0.45) * env;
    }

    private double synth(double beat, int scene, double t) {
        double p = frac(beat / (scene % 2 == 0 ? 2.0 : 1.0));
        double env = Math.pow(Math.sin(Math.PI * p), 2.0);
        double carrier = 110.0 * Math.pow(2.0, ((scene * 3) % 12) / 12.0);
        double mod = Math.sin(2 * Math.PI * carrier * 1.51 * t) * (2.0 + scene * 0.4);
        return Math.sin(2 * Math.PI * carrier * t + mod) * env;
    }

    private double texture(double beat, int scene, double t) {
        double bar = frac(beat / 4.0);
        double env = Math.pow(Math.max(0.0, Math.sin(Math.PI * bar)), 2.0);
        double noise = random.nextDouble() * 2.0 - 1.0;
        double wobble = Math.sin(2 * Math.PI * (0.25 + scene * 0.07) * t);
        return noise * env * (0.35 + 0.65 * Math.abs(wobble));
    }

    private double percussion(double beat, int scene, double t) {
        int steps = 4;
        double p = frac((beat + 0.5 + scene * 0.125) * steps);
        double sec = p * 60.0 / bpm / steps;
        if (sec > 0.11) return 0;
        double env = Math.exp(-sec * 35.0);
        double f = 180.0 + scene * 22.0;
        return Math.sin(2 * Math.PI * f * t) * env;
    }
}
