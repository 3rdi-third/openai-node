package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class StartupActivity extends Activity {
    private int panel;
    private int knobs;
    private int text;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        UiUtil.applyImmersive(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtil.applyImmersive(this);
        if (root != null) buildUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }

    private void buildUi() {
        panel = ThemePreferences.panelColor(this);
        knobs = ThemePreferences.knobColor(this);
        text = ThemePreferences.textColor(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(40), dp(20), dp(40), dp(20));
        root.setBackgroundColor(panel);

        TextView mark = new TextView(this);
        mark.setText("●  ●  ●  ●");
        mark.setTextColor(knobs);
        mark.setTextSize(28);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView title = new TextView(this);
        title.setText("3RDI ANALOG PERCUSSION");
        title.setTextColor(text);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DUAL OSCILLATOR • FILTER • MODULATION • 8-STEP PERCUSSION SYNTH");
        subtitle.setTextColor(text);
        subtitle.setAlpha(0.72f);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(34)));

        int nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        TextView status = new TextView(this);
        status.setText("AUDIO ENGINE READY  •  DEVICE OUTPUT " + nativeRate + " Hz");
        status.setTextColor(text);
        status.setAlpha(0.62f);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));

        Space spacer = new Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(10)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = makeButton("START SYNTH");
        Button audio = makeButton("AUDIO SETTINGS");
        Button colours = makeButton("COLOURS");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(54));
        bp.setMargins(dp(7), 0, dp(7), 0);
        buttons.addView(start, bp);
        buttons.addView(audio, bp);
        buttons.addView(colours, bp);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(68)));

        TextView footer = new TextView(this);
        footer.setText("3rdi Analog Percussion v1.4");
        footer.setTextColor(text);
        footer.setAlpha(0.5f);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(26)));

        start.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        audio.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        colours.setOnClickListener(v -> startActivity(new Intent(this, ThemeActivity.class)));

        setContentView(root);
    }

    private Button makeButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(contrast(knobs));
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(knobs);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private int contrast(int color) {
        double y = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return y > 150 ? Color.BLACK : Color.WHITE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
