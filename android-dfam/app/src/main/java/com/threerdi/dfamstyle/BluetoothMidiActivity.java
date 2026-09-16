package com.threerdi.dfamstyle;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BluetoothMidiActivity extends Activity implements BleMidiSyncManager.Listener {
    private static final int REQ_BT = 4401;

    private BleMidiSyncManager midi;
    private TextView statusView;
    private TextView syncView;
    private LinearLayout deviceList;
    private int panel;
    private int accent;
    private int text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        UiUtil.applyImmersive(this);
        midi = BleMidiSyncManager.get(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtil.applyImmersive(this);
        midi.setListener(this);
        refreshDevices();
        onStatusChanged(midi.getStatus());
        onSyncChanged(midi.getIncomingBpm(), midi.isIncomingRunning());
    }

    @Override
    protected void onPause() {
        midi.setListener(null);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }

    private void buildUi() {
        panel = ThemePreferences.panelColor(this);
        accent = ThemePreferences.knobColor(this);
        text = ThemePreferences.textColor(this);
        int panelLight = ThemePreferences.lighten(panel, 0.08f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackgroundColor(panel);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(12), dp(8), dp(18), dp(8));
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.05f));

        TextView title = label("BLUETOOTH MIDI SYNC", 27, true);
        left.addView(title);
        TextView subtitle = label("Standard BLE-MIDI • MIDI Clock 24 PPQN • Start / Continue / Stop", 12, false);
        subtitle.setPadding(0, 0, 0, dp(12));
        left.addView(subtitle);

        statusView = label("", 15, true);
        statusView.setBackgroundColor(panelLight);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        left.addView(statusView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        syncView = label("", 18, true);
        syncView.setPadding(0, dp(12), 0, dp(12));
        left.addView(syncView);

        Button master = button("MASTER • SEND CLOCK");
        master.setOnClickListener(v -> {
            if (!ensurePermissions()) return;
            if (!midi.isBluetoothEnabled()) {
                requestEnableBluetooth();
                return;
            }
            if (!midi.canAdvertise()) {
                Toast.makeText(this, "This phone cannot advertise as a BLE MIDI peripheral", Toast.LENGTH_LONG).show();
                return;
            }
            midi.startMaster(AudioPreferences.defaultBpm(this), false);
        });
        left.addView(master, buttonParams());

        Button scan = button("SLAVE • SCAN / RECEIVE CLOCK");
        scan.setOnClickListener(v -> {
            if (!ensurePermissions()) return;
            if (!midi.isBluetoothEnabled()) {
                requestEnableBluetooth();
                return;
            }
            midi.startSlaveScan();
        });
        left.addView(scan, buttonParams());

        Button disconnect = button("DISCONNECT MIDI");
        disconnect.setOnClickListener(v -> midi.disconnect());
        left.addView(disconnect, buttonParams());

        Button btSettings = button("BLUETOOTH SETTINGS");
        btSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        left.addView(btSettings, buttonParams());

        TextView note = label(
                "MASTER: this phone advertises as a BLE-MIDI device. Open the other app's Bluetooth MIDI connection screen and connect to this phone.\n\n" +
                "SLAVE: scan here and select the other phone/app when it advertises BLE-MIDI. Incoming MIDI Clock controls BPM; Start/Continue/Stop controls the sequencer transport.",
                11, false);
        note.setPadding(0, dp(10), 0, 0);
        left.addView(note);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(18), dp(8), dp(12), dp(8));
        root.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f));

        TextView devicesTitle = label("BLE MIDI DEVICES", 18, true);
        right.addView(devicesTitle);

        ScrollView scroll = new ScrollView(this);
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        deviceList.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(deviceList, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        right.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button back = button("BACK TO SYNTH");
        back.setOnClickListener(v -> finish());
        right.addView(back, buttonParams());

        setContentView(root);
    }

    private TextView label(String value, float sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(text);
        tv.setTextSize(sp);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(contrastText(accent));
        b.setTextSize(12f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(accent);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(7);
        return lp;
    }

    private int contrastText(int c) {
        double luminance = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c));
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean ensurePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_BT);
            return false;
        }
        return true;
    }

    private void requestEnableBluetooth() {
        try {
            Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enable);
        } catch (Exception e) {
            Toast.makeText(this, "Enable Bluetooth in Android settings", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) {
            boolean granted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            Toast.makeText(this, granted ? "Bluetooth MIDI permission granted" : "Bluetooth MIDI needs Bluetooth permission", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStatusChanged(String status) {
        runOnUiThread(() -> {
            if (statusView != null) statusView.setText(status);
        });
    }

    @Override
    public void onDevicesChanged() {
        runOnUiThread(this::refreshDevices);
    }

    @Override
    public void onSyncChanged(float bpm, boolean running) {
        runOnUiThread(() -> {
            if (syncView == null) return;
            if (midi.isMasterActive()) {
                syncView.setText("MASTER MODE • clock follows synth BPM and transport");
            } else if (midi.isSlaveActive()) {
                if (bpm > 0f) syncView.setText(String.format(Locale.US, "SLAVE SYNC • %.2f BPM • %s", bpm, running ? "RUNNING" : "STOPPED"));
                else syncView.setText("SLAVE SYNC • waiting for MIDI Clock");
            } else {
                syncView.setText("MIDI SYNC OFF");
            }
        });
    }

    private void refreshDevices() {
        if (deviceList == null) return;
        deviceList.removeAllViews();
        List<BluetoothDevice> devices = midi.getScannedDevices();
        if (devices.isEmpty()) {
            TextView empty = label("No BLE MIDI devices found yet.\nPress SLAVE • SCAN / RECEIVE CLOCK.", 13, false);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            deviceList.addView(empty);
            return;
        }
        for (BluetoothDevice device : devices) {
            Button b = button(midi.displayName(device));
            b.setOnClickListener(v -> midi.connectSlave(device));
            deviceList.addView(b, buttonParams());
        }
    }
}
