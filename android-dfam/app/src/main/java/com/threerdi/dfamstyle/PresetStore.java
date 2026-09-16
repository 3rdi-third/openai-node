package com.threerdi.dfamstyle;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PresetStore {
    private static final String PREFS = "3rdi_synth_presets";
    private static final String NAMES = "preset_names";
    private static final String PREFIX = "preset::";

    private PresetStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context context, String name, MainActivity.SynthView s) {
        String clean = cleanName(name);
        if (clean.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("tempo", s.tempo);
            o.put("vco1", s.vco1);
            o.put("detune", s.detune);
            o.put("fm", s.fm);
            o.put("noise", s.noise);
            o.put("cutoff", s.cutoff);
            o.put("resonance", s.resonance);
            o.put("vcfDecay", s.vcfDecay);
            o.put("vcaDecay", s.vcaDecay);
            o.put("drive", s.drive);
            o.put("lfoRate", s.lfoRate);
            o.put("lfoPitch", s.lfoPitch);
            o.put("lfoFilter", s.lfoFilter);
            o.put("lfoFm", s.lfoFm);
            o.put("filterEnvAmount", s.filterEnvAmount);
            o.put("pitchEnvAmount", s.pitchEnvAmount);

            JSONArray pitch = new JSONArray();
            JSONArray velocity = new JSONArray();
            for (int i = 0; i < 8; i++) {
                pitch.put(s.stepPitch[i]);
                velocity.put(s.stepVelocity[i]);
            }
            o.put("stepPitch", pitch);
            o.put("stepVelocity", velocity);

            Set<String> names = new HashSet<>(prefs(context).getStringSet(NAMES, Collections.emptySet()));
            names.add(clean);
            prefs(context).edit()
                    .putStringSet(NAMES, names)
                    .putString(PREFIX + clean, o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    static boolean load(Context context, String name, MainActivity.SynthView s) {
        String json = prefs(context).getString(PREFIX + cleanName(name), null);
        if (json == null) return false;
        try {
            JSONObject o = new JSONObject(json);
            s.tempo = clamp((float) o.optDouble("tempo", s.tempo), 40f, 300f);
            s.vco1 = clamp((float) o.optDouble("vco1", s.vco1), 28f, 440f);
            s.detune = clamp((float) o.optDouble("detune", s.detune), -24f, 24f);
            s.fm = clamp((float) o.optDouble("fm", s.fm), 0f, 1f);
            s.noise = clamp((float) o.optDouble("noise", s.noise), 0f, 1f);
            s.cutoff = clamp((float) o.optDouble("cutoff", s.cutoff), 70f, 12000f);
            s.resonance = clamp((float) o.optDouble("resonance", s.resonance), 0f, 0.96f);
            s.vcfDecay = clamp((float) o.optDouble("vcfDecay", s.vcfDecay), 35f, 2400f);
            s.vcaDecay = clamp((float) o.optDouble("vcaDecay", s.vcaDecay), 35f, 2400f);
            s.drive = clamp((float) o.optDouble("drive", s.drive), 1f, 6f);
            s.lfoRate = clamp((float) o.optDouble("lfoRate", s.lfoRate), 0.05f, 30f);
            s.lfoPitch = clamp((float) o.optDouble("lfoPitch", s.lfoPitch), -12f, 12f);
            s.lfoFilter = clamp((float) o.optDouble("lfoFilter", s.lfoFilter), -3f, 3f);
            s.lfoFm = clamp((float) o.optDouble("lfoFm", s.lfoFm), 0f, 1f);
            s.filterEnvAmount = clamp((float) o.optDouble("filterEnvAmount", s.filterEnvAmount), 0f, 10f);
            s.pitchEnvAmount = clamp((float) o.optDouble("pitchEnvAmount", s.pitchEnvAmount), -24f, 24f);

            JSONArray pitch = o.optJSONArray("stepPitch");
            JSONArray velocity = o.optJSONArray("stepVelocity");
            if (pitch != null) {
                for (int i = 0; i < Math.min(8, pitch.length()); i++) {
                    s.stepPitch[i] = clamp((float) pitch.optDouble(i, s.stepPitch[i]), -24f, 24f);
                }
            }
            if (velocity != null) {
                for (int i = 0; i < Math.min(8, velocity.length()); i++) {
                    s.stepVelocity[i] = clamp((float) velocity.optDouble(i, s.stepVelocity[i]), 0f, 1f);
                }
            }
            s.invalidate();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static List<String> names(Context context) {
        Set<String> set = prefs(context).getStringSet(NAMES, Collections.emptySet());
        ArrayList<String> names = new ArrayList<>(set);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static void delete(Context context, String name) {
        String clean = cleanName(name);
        Set<String> names = new HashSet<>(prefs(context).getStringSet(NAMES, Collections.emptySet()));
        names.remove(clean);
        prefs(context).edit()
                .putStringSet(NAMES, names)
                .remove(PREFIX + clean)
                .apply();
    }

    static boolean exists(Context context, String name) {
        return prefs(context).contains(PREFIX + cleanName(name));
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        String clean = name.trim().replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 40) clean = clean.substring(0, 40).trim();
        return clean;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
