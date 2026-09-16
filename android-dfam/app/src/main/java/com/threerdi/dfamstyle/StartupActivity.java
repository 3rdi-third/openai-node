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
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class StartupActivity extends Activity {
    private static final int SAND = Color.rgb(218, 198, 165);
    private static final int ORANGE = Color.rgb(224, 111, 36);
    private static final int BLACK = Color.rgb(18, 18, 16);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(SAND);

        TextView mark = new TextView(this);
        mark.setText("●  ●  ●  ●");
        mark.setTextColor(ORANGE);
        mark.setTextSize(28);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView title = new TextView(this);
        title.setText("3RDI ANALOG PERCUSSION");
        title.setTextColor(BLACK);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DUAL OSCILLATOR • FILTER • MODULATION • 8-STEP PERCUSSION SYNTH");
        subtitle.setTextColor(BLACK);
        subtitle.setAlpha(0.72f);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(36)));

        int nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        TextView status = new TextView(this);
        status.setText("AUDIO ENGINE READY  •  DEVICE OUTPUT " + nativeRate + " Hz");
        status.setTextColor(BLACK);
        status.setAlpha(0.62f);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(34)));

        Space spacer = new Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(12)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = makeButton("START SYNTH");
        Button settings = makeButton("PREFERENCES");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(210), dp(56));
        bp.setMargins(dp(8), 0, dp(8), 0);
        buttons.addView(start, bp);
        buttons.addView(settings, bp);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView footer = new TextView(this);
        footer.setText("3rdi Analog Percussion v1.2");
        footer.setTextColor(BLACK);
        footer.setAlpha(0.5f);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));

        start.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BLACK);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(ORANGE);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
