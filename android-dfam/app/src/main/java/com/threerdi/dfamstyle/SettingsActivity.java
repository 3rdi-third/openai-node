package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private static final int SAND = Color.rgb(218, 198, 165);
    private static final int ORANGE = Color.rgb(224, 111, 36);
    private static final int BLACK = Color.rgb(18, 18, 16);

    private Spinner sampleRate;
    private Spinner bufferProfile;
    private Switch lowLatency;
    private Switch keepScreenOn;
    private SeekBar masterGain;
    private TextView masterGainValue;
    private EditText defaultBpm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(SAND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(24), dp(34), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("AUDIO PREFERENCES", 28, true);
        root.addView(title);
        TextView sub = text("Configure the realtime Android audio engine. Changes are applied when the synth screen starts again.", 12, false);
        sub.setAlpha(0.68f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp(2), 0, dp(20));
        root.addView(sub, subParams);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);
        root.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col = new LinearLayout.LayoutParams(0, -2, 1f);
        col.setMargins(dp(8), 0, dp(8), 0);
        columns.addView(left, col);
        columns.addView(right, col);

        left.addView(section("AUDIO DEVICE"));
        left.addView(label("Sample rate"));
        sampleRate = new Spinner(this);
        String[] rateItems = {"Auto / device native", "44,100 Hz", "48,000 Hz", "96,000 Hz"};
        ArrayAdapter<String> rateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rateItems);
        rateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleRate.setAdapter(rateAdapter);
        left.addView(sampleRate, fieldParams());

        left.addView(label("Buffer profile"));
        bufferProfile = new Spinner(this);
        String[] bufferItems = {"Low latency", "Balanced", "Stable"};
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bufferItems);
        bufferAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bufferProfile.setAdapter(bufferAdapter);
        left.addView(bufferProfile, fieldParams());

        lowLatency = makeSwitch("Request Android low-latency performance mode");
        left.addView(lowLatency, fieldParams());

        right.addView(section("OUTPUT & BEHAVIOUR"));
        right.addView(label("Master output"));
        masterGainValue = text("86%", 12, true);
        right.addView(masterGainValue);
        masterGain = new SeekBar(this);
        masterGain.setMax(120);
        masterGain.setMin(25);
        masterGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                masterGainValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        right.addView(masterGain, fieldParams());

        right.addView(label("Default BPM"));
        defaultBpm = new EditText(this);
        defaultBpm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        defaultBpm.setSingleLine(true);
        defaultBpm.setTextColor(BLACK);
        defaultBpm.setHintTextColor(Color.rgb(90, 80, 70));
        defaultBpm.setHint("40–300");
        right.addView(defaultBpm, fieldParams());

        keepScreenOn = makeSwitch("Keep display awake while synth is open");
        right.addView(keepScreenOn, fieldParams());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams buttonRow = new LinearLayout.LayoutParams(-1, dp(62));
        buttonRow.setMargins(0, dp(22), 0, 0);
        root.addView(buttons, buttonRow);

        Button save = button("SAVE & BACK");
        Button reset = button("RESET DEFAULTS");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(52));
        bp.setMargins(dp(8), 0, dp(8), 0);
        buttons.addView(save, bp);
        buttons.addView(reset, bp);

        save.setOnClickListener(v -> saveAndClose());
        reset.setOnClickListener(v -> {
            AudioPreferences.reset(this);
            loadValues();
            Toast.makeText(this, "Audio settings reset", Toast.LENGTH_SHORT).show();
        });

        loadValues();
        setContentView(scroll);
    }

    private void loadValues() {
        int sr = AudioPreferences.sampleRate(this);
        int rateIndex = sr == 44100 ? 1 : sr == 48000 ? 2 : sr == 96000 ? 3 : 0;
        sampleRate.setSelection(rateIndex);
        bufferProfile.setSelection(Math.max(0, Math.min(2, AudioPreferences.bufferProfile(this))));
        lowLatency.setChecked(AudioPreferences.lowLatency(this));
        int gain = Math.round(AudioPreferences.masterGain(this) * 100f);
        gain = Math.max(25, Math.min(120, gain));
        masterGain.setProgress(gain);
        masterGainValue.setText(gain + "%");
        defaultBpm.setText(String.valueOf(AudioPreferences.defaultBpm(this)));
        keepScreenOn.setChecked(AudioPreferences.keepScreenOn(this));
    }

    private void saveAndClose() {
        float bpm;
        try {
            bpm = Float.parseFloat(defaultBpm.getText().toString().trim());
        } catch (Exception ignored) {
            bpm = 133f;
        }
        bpm = Math.max(40f, Math.min(300f, bpm));
        int[] rates = {0, 44100, 48000, 96000};
        int sr = rates[Math.max(0, Math.min(3, sampleRate.getSelectedItemPosition()))];

        SharedPreferences.Editor e = AudioPreferences.prefs(this).edit();
        e.putInt(AudioPreferences.SAMPLE_RATE, sr);
        e.putInt(AudioPreferences.BUFFER_PROFILE, Math.max(0, Math.min(2, bufferProfile.getSelectedItemPosition())));
        e.putBoolean(AudioPreferences.LOW_LATENCY, lowLatency.isChecked());
        e.putFloat(AudioPreferences.MASTER_GAIN, masterGain.getProgress() / 100f);
        e.putFloat(AudioPreferences.DEFAULT_BPM, bpm);
        e.putBoolean(AudioPreferences.KEEP_SCREEN_ON, keepScreenOn.isChecked());
        e.apply();
        Toast.makeText(this, "Audio settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView section(String value) {
        TextView t = text(value, 15, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(9));
        t.setLayoutParams(p);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 12, true);
        t.setAlpha(0.78f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(2));
        t.setLayoutParams(p);
        return t;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(BLACK);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Switch makeSwitch(String value) {
        Switch s = new Switch(this);
        s.setText(value);
        s.setTextColor(BLACK);
        s.setTextSize(12);
        return s;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(BLACK);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackgroundColor(ORANGE);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, 0, 0, dp(5));
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
