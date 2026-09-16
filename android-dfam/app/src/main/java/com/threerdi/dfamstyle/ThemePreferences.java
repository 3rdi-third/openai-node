package com.threerdi.dfamstyle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class ThemePreferences {
    static final String PREFS = "3rdi_theme_preferences";
    static final String PANEL = "panel_color";
    static final String KNOBS = "knob_color";
    static final String TEXT = "text_color";

    static final int DEFAULT_PANEL = Color.rgb(218, 198, 165);
    static final int DEFAULT_KNOBS = Color.rgb(224, 111, 36);
    static final int DEFAULT_TEXT = Color.rgb(18, 18, 16);

    private ThemePreferences() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int panelColor(Context context) {
        return prefs(context).getInt(PANEL, DEFAULT_PANEL);
    }

    static int knobColor(Context context) {
        return prefs(context).getInt(KNOBS, DEFAULT_KNOBS);
    }

    static int textColor(Context context) {
        return prefs(context).getInt(TEXT, DEFAULT_TEXT);
    }

    static void save(Context context, int panel, int knobs, int text) {
        prefs(context).edit()
                .putInt(PANEL, panel)
                .putInt(KNOBS, knobs)
                .putInt(TEXT, text)
                .apply();
    }

    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    static int parseHex(String value, int fallback) {
        try {
            String s = value == null ? "" : value.trim();
            if (!s.startsWith("#")) s = "#" + s;
            if (s.length() == 7) return Color.parseColor(s);
        } catch (Exception ignored) {}
        return fallback;
    }

    static String toHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    static int darken(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(color) * (1f - amount));
        int g = Math.round(Color.green(color) * (1f - amount));
        int b = Math.round(Color.blue(color) * (1f - amount));
        return Color.rgb(r, g, b);
    }

    static int lighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(color) + (255 - Color.red(color)) * amount);
        int g = Math.round(Color.green(color) + (255 - Color.green(color)) * amount);
        int b = Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount);
        return Color.rgb(r, g, b);
    }
}