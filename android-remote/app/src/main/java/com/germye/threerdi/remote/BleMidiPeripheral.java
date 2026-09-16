package com.germye.threerdi.remote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BleMidiPeripheral {
    public interface Listener {
        void onStatus(String message, boolean connected);
    }

    public static final UUID MIDI_SERVICE_UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
    public static final UUID MIDI_CHARACTERISTIC_UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3");
    private static final UUID CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final Listener listener;
    private final BluetoothManager manager;
    private final BluetoothAdapter adapter;
    private BluetoothGattServer server;
    private BluetoothGattCharacteristic midiCharacteristic;
    private BluetoothLeAdvertiser advertiser;
    private final Set<BluetoothDevice> subscribed = Collections.synchronizedSet(new HashSet<>());
    private boolean advertising;

    public BleMidiPeripheral(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
    }

    public boolean isSupported() {
        return adapter != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                && adapter.isMultipleAdvertisementSupported();
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (!isSupported()) {
            status("BLE peripheral advertising is not supported on this phone.", false);
            return;
        }
        if (!hasPermissions()) {
            status("Bluetooth permission required.", false);
            return;
        }
        if (!adapter.isEnabled()) {
            status("Enable Bluetooth first.", false);
            return;
        }
        if (advertising) return;

        server = manager.openGattServer(context, callback);
        if (server == null) {
            status("Could not open BLE-MIDI server.", false);
            return;
        }

        BluetoothGattService service = new BluetoothGattService(MIDI_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        midiCharacteristic = new BluetoothGattCharacteristic(
                MIDI_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_WRITE
                        | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor ccc = new BluetoothGattDescriptor(
                CCC_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        midiCharacteristic.addDescriptor(ccc);
        service.addCharacteristic(midiCharacteristic);
        server.addService(service);

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            status("BLE advertiser unavailable.", false);
            closeServer();
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(MIDI_SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (advertiser != null && advertising && hasPermissions()) {
            try { advertiser.stopAdvertising(advertiseCallback); } catch (Exception ignored) {}
        }
        advertising = false;
        subscribed.clear();
        closeServer();
        status("BLE-MIDI stopped", false);
    }

    @SuppressLint("MissingPermission")
    private void closeServer() {
        if (server != null) {
            try { server.clearServices(); } catch (Exception ignored) {}
            try { server.close(); } catch (Exception ignored) {}
            server = null;
        }
        midiCharacteristic = null;
    }

    public void sendCC(int cc, int value) {
        cc = clamp(cc, 0, 127);
        value = clamp(value, 0, 127);
        sendMidi(new byte[]{(byte) 0xB0, (byte) cc, (byte) value});
    }

    public void sendNoteOn(int note, int velocity) {
        sendMidi(new byte[]{(byte) 0x90, (byte) clamp(note, 0, 127), (byte) clamp(velocity, 0, 127)});
    }

    public void sendNoteOff(int note) {
        sendMidi(new byte[]{(byte) 0x80, (byte) clamp(note, 0, 127), 0});
    }

    public void panic() {
        sendCC(123, 0);
        sendCC(120, 0);
    }

    @SuppressLint("MissingPermission")
    private void sendMidi(byte[] midi) {
        if (server == null || midiCharacteristic == null || subscribed.isEmpty() || !hasPermissions()) return;
        int timestamp = (int) (System.currentTimeMillis() & 0x1FFF);
        byte[] packet = new byte[midi.length + 2];
        packet[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3F));
        packet[1] = (byte) (0x80 | (timestamp & 0x7F));
        System.arraycopy(midi, 0, packet, 2, midi.length);

        synchronized (subscribed) {
            for (BluetoothDevice device : subscribed) {
                try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        server.notifyCharacteristicChanged(device, midiCharacteristic, false, packet);
                    } else {
                        midiCharacteristic.setValue(packet);
                        server.notifyCharacteristicChanged(device, midiCharacteristic, false);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            advertising = true;
            status("Advertising as BLE-MIDI — connect from macOS Audio MIDI Setup", !subscribed.isEmpty());
        }

        @Override public void onStartFailure(int errorCode) {
            advertising = false;
            status("BLE advertising failed (code " + errorCode + ")", false);
        }
    };

    private final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                status("Mac connected — enable this MIDI input in Ableton", true);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribed.remove(device);
                status("Advertising — waiting for Mac", false);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (server == null) return;
            if (MIDI_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
            } else {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded && server != null) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if (server == null) return;
            byte[] value = subscribed.contains(device)
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (CCC_UUID.equals(descriptor.getUuid())) {
                boolean enable = value != null && value.length >= 2 && (value[0] & 0x01) != 0;
                if (enable) subscribed.add(device); else subscribed.remove(device);
                status(enable ? "BLE-MIDI connected — controls are live" : "Mac connected, MIDI notifications disabled", enable);
            }
            if (responseNeeded && server != null) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
    };

    private void status(String message, boolean connected) {
        if (listener != null) listener.onStatus(message, connected);
    }

    private static int clamp(int x, int min, int max) {
        return Math.max(min, Math.min(max, x));
    }
}
