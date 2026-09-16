package com.threerdi.dfamstyle;

import android.content.Context;
import android.content.SharedPreferences;

final class AudioPreferences {
    static final String FILE = "3rdi_audio_preferences";
    static final String SAMPLE_RATE = "sample_rate";
    static final String BUFFER_PROFILE = "buffer_profile";
    static final String LOW_LATENCY = "low_latency";
    static final String MASTER_GAIN = "master_gain";
    static final String DEFAULT_BPM = "default_bpm";
    static final String KEEP_SCREEN_ON = "keep_screen_on";

    private AudioPreferences() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int sampleRate(Context context) {
        return prefs(context).getInt(SAMPLE_RATE, 0);
    }

    static int bufferProfile(Context context) {
        return prefs(context).getInt(BUFFER_PROFILE, 1);
    }

    static boolean lowLatency(Context context) {
        return prefs(context).getBoolean(LOW_LATENCY, true);
    }

    static float masterGain(Context context) {
        return prefs(context).getFloat(MASTER_GAIN, 0.86f);
    }

    static float defaultBpm(Context context) {
        return prefs(context).getFloat(DEFAULT_BPM, 133f);
    }

    static boolean keepScreenOn(Context context) {
        return prefs(context).getBoolean(KEEP_SCREEN_ON, true);
    }

    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }
}
