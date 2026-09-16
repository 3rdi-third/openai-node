package com.germye.threerdi.remote;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

public final class MainActivity extends Activity implements BleMidiPeripheral.Listener {
    private static final int REQ_BT = 3;
    private final int acidGreen = Color.rgb(183,255,48);
    private BleMidiPeripheral midi;
    private TextView status;
    private final KnobView[] knobs = new KnobView[16];
    private final Random random = new Random();

    private static final String[] LABELS = {
            "A X", "A Y", "A Z", "B X", "B Y", "B Z",
            "FM A←B", "FM B←A", "A/B MIX", "DECIMATE", "DRIVE",
            "FILTER", "RESONANCE", "LFO RATE", "LFO XYZ", "OUTPUT"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        midi = new BleMidiPeripheral(this, this);
        setContentView(buildUi());
        requestBtThenStart();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8,8,8));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = text("GERMYE'S 3RDI REMOTE", 23, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView subtitle = text("BLE-MIDI controller for Ableton Live", 13, Color.rgb(165,165,165));
        root.addView(subtitle, lp(-1, dp(31)));

        status = text("Starting Bluetooth MIDI…", 13, Color.WHITE);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackgroundColor(Color.rgb(30,30,30));
        root.addView(status, lp(-1,-2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(9), 0, dp(8));
        Button startButton = button("START BLE-MIDI");
        startButton.setOnClickListener(v -> requestBtThenStart());
        Button stop = button("STOP"); stop.setOnClickListener(v -> midi.stop());
        Button panic = button("PANIC"); panic.setOnClickListener(v -> midi.panic());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, dp(48), 1.5f));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1f));
        buttons.addView(panic, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(buttons);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (int i=0; i<knobs.length; i++) {
            final int index = i;
            KnobView k = new KnobView(this);
            knobs[i] = k;
            k.configure(LABELS[i], 20+i, i==8 ? 64 : (i<6 ? 48 : 32), v -> midi.sendCC(20+index, v));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0; gp.height = dp(136); gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(k, gp);
        }
        root.addView(grid, lp(-1,-2));

        LinearLayout performance = new LinearLayout(this);
        performance.setOrientation(LinearLayout.HORIZONTAL);
        performance.setPadding(0, dp(4), 0, dp(10));
        Button randomBtn = button("RANDOMIZE");
        randomBtn.setOnClickListener(v -> {
            for (KnobView k : knobs) k.setValue(random.nextInt(128), true);
        });
        Button centerBtn = button("CENTER");
        centerBtn.setOnClickListener(v -> {
            for (KnobView k : knobs) k.setValue(64, true);
        });
        performance.addView(randomBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        performance.addView(centerBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(performance);

        XYPadView xy = new XYPadView(this);
        xy.setListener((x,y) -> { midi.sendCC(36, x); midi.sendCC(37, y); });
        root.addView(xy, lp(-1, dp(205)));

        TextView keyboardTitle = text("PERFORMANCE KEYS", 14, Color.WHITE);
        keyboardTitle.setPadding(0, dp(12), 0, dp(8));
        root.addView(keyboardTitle);
        root.addView(buildKeyboard(), lp(-1, dp(76)));

        TextView help = text(
                "MAC SETUP\n1  Open Audio MIDI Setup → MIDI Studio → Configure Bluetooth.\n" +
                "2  Connect to this Android phone's BLE-MIDI service.\n" +
                "3  In Ableton: Settings → Link, Tempo & MIDI → enable Remote for the Bluetooth MIDI input.\n" +
                "4  Enter MIDI Map mode, click a Germye's 3rdi Synth parameter, then move the matching phone control.\n\n" +
                "CC MAP  20–35 = 16 knobs  •  36/37 = XY pad  •  keyboard = MIDI channel 1",
                12, Color.rgb(170,170,170));
        help.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(help);
        return scroll;
    }

    private View buildKeyboard() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B","C+"};
        for (int i=0;i<names.length;i++) {
            int note = 60+i;
            Button key = button(names[i]);
            key.setTextColor((names[i].contains("#")) ? acidGreen : Color.WHITE);
            key.setOnTouchListener((v,e) -> {
                if (e.getActionMasked()==MotionEvent.ACTION_DOWN) { midi.sendNoteOn(note, 105); v.setAlpha(0.55f); return true; }
                if (e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) { midi.sendNoteOff(note); v.setAlpha(1f); return true; }
                return true;
            });
            row.addView(key, new LinearLayout.LayoutParams(dp(58), -1));
        }
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-1));
        return hsv;
    }

    private void requestBtThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean a = checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
            boolean c = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (!a || !c) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
                return;
            }
        }
        midi.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) {
            boolean ok = true;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) midi.start(); else onStatus("Bluetooth permission denied", false);
        }
    }

    @Override public void onStatus(String message, boolean connected) {
        runOnUiThread(() -> {
            if (status != null) {
                status.setText((connected ? "●  " : "○  ") + message);
                status.setTextColor(connected ? acidGreen : Color.WHITE);
            }
        });
    }

    @Override protected void onDestroy() {
        if (midi != null) midi.stop();
        super.onDestroy();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(11); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(42,42,42)); b.setAllCaps(false); return b;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
