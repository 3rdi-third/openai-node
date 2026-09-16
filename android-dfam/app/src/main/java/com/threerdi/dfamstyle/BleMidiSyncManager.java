package com.threerdi.dfamstyle;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Small BLE-MIDI transport focused on MIDI Clock (24 PPQN) and transport.
 * MASTER advertises the standard BLE-MIDI service and sends Clock/Start/Stop.
 * SLAVE scans for a BLE-MIDI peripheral and follows incoming Clock/Start/Stop.
 */
public final class BleMidiSyncManager {
    public static final UUID MIDI_SERVICE_UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
    public static final UUID MIDI_CHAR_UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final int MODE_OFF = 0;
    public static final int MODE_MASTER = 1;
    public static final int MODE_SLAVE = 2;

    public interface Listener {
        void onStatusChanged(String status);
        void onDevicesChanged();
        void onSyncChanged(float bpm, boolean running);
    }

    private static volatile BleMidiSyncManager instance;

    public static BleMidiSyncManager get(Context context) {
        if (instance == null) {
            synchronized (BleMidiSyncManager.class) {
                if (instance == null) instance = new BleMidiSyncManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter adapter;
    private final Map<String, BluetoothDevice> scannedDevices = new LinkedHashMap<>();
    private final Set<BluetoothDevice> subscribers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean clockThreadActive = new AtomicBoolean(false);

    private volatile Listener listener;
    private volatile int mode = MODE_OFF;
    private volatile String status = "MIDI sync off";
    private volatile float masterBpm = 133f;
    private volatile boolean masterRunning = false;
    private volatile float incomingBpm = 0f;
    private volatile boolean incomingRunning = false;
    private volatile long lastIncomingClockNs = 0L;
    private volatile double avgClockIntervalNs = 0.0;
    private volatile long syncRevision = 0L;

    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic serverCharacteristic;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGatt clientGatt;
    private BluetoothGattCharacteristic clientCharacteristic;
    private Thread clockThread;

    private BleMidiSyncManager(Context appContext) {
        context = appContext;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            main.post(() -> {
                Listener l = this.listener;
                if (l != null) {
                    l.onStatusChanged(status);
                    l.onDevicesChanged();
                    l.onSyncChanged(incomingBpm, incomingRunning);
                }
            });
        }
    }

    public int getMode() { return mode; }
    public boolean isMasterActive() { return mode == MODE_MASTER; }
    public boolean isSlaveActive() { return mode == MODE_SLAVE; }
    public String getStatus() { return status; }
    public float getIncomingBpm() { return incomingBpm; }
    public boolean isIncomingRunning() { return incomingRunning; }
    public long getSyncRevision() { return syncRevision; }
    public boolean hasIncomingClock() { return lastIncomingClockNs != 0L && (System.nanoTime() - lastIncomingClockNs) < 2_000_000_000L; }

    public boolean isBluetoothAvailable() {
        return adapter != null;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public boolean canAdvertise() {
        try {
            return adapter != null && adapter.isMultipleAdvertisementSupported() && adapter.getBluetoothLeAdvertiser() != null;
        } catch (SecurityException e) {
            return false;
        }
    }

    public synchronized List<BluetoothDevice> getScannedDevices() {
        return new ArrayList<>(scannedDevices.values());
    }

    public void setMasterState(float bpm, boolean running) {
        masterBpm = Math.max(20f, Math.min(300f, bpm));
        boolean changed = masterRunning != running;
        masterRunning = running;
        if (mode == MODE_MASTER && changed) {
            sendMidiRealtime(running ? (byte) 0xFA : (byte) 0xFC);
        }
    }

    @SuppressLint("MissingPermission")
    public void startMaster(float bpm, boolean running) {
        stopSlaveInternal();
        stopMasterInternal();
        mode = MODE_MASTER;
        masterBpm = Math.max(20f, Math.min(300f, bpm));
        masterRunning = running;
        incomingBpm = 0f;
        lastIncomingClockNs = 0L;

        if (adapter == null) {
            setStatus("Bluetooth is not available on this device");
            return;
        }
        if (!adapter.isEnabled()) {
            setStatus("Enable Bluetooth first");
            return;
        }
        try {
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null || !adapter.isMultipleAdvertisementSupported()) {
                setStatus("BLE peripheral advertising is not supported on this phone");
                return;
            }

            gattServer = bluetoothManager.openGattServer(context, serverCallback);
            if (gattServer == null) {
                setStatus("Could not open BLE MIDI server");
                return;
            }

            BluetoothGattService service = new BluetoothGattService(MIDI_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
            serverCharacteristic = new BluetoothGattCharacteristic(
                    MIDI_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ |
                            BluetoothGattCharacteristic.PROPERTY_WRITE |
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE |
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
            BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
            serverCharacteristic.addDescriptor(cccd);
            service.addCharacteristic(serverCharacteristic);
            gattServer.addService(service);
            setStatus("Starting BLE MIDI master…");
            startClockThread();
        } catch (SecurityException e) {
            setStatus("Bluetooth permission required");
        } catch (Exception e) {
            setStatus("BLE MIDI master error: " + safeMessage(e));
        }
    }

    @SuppressLint("MissingPermission")
    private void beginAdvertising() {
        if (advertiser == null) return;
        try {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(new ParcelUuid(MIDI_SERVICE_UUID))
                    .build();
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException e) {
            setStatus("Bluetooth advertise permission required");
        }
    }

    @SuppressLint("MissingPermission")
    public void startSlaveScan() {
        stopMasterInternal();
        stopSlaveInternal();
        mode = MODE_SLAVE;
        incomingBpm = 0f;
        lastIncomingClockNs = 0L;
        avgClockIntervalNs = 0.0;
        synchronized (this) { scannedDevices.clear(); }
        notifyDevices();

        if (adapter == null) {
            setStatus("Bluetooth is not available on this device");
            return;
        }
        if (!adapter.isEnabled()) {
            setStatus("Enable Bluetooth first");
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                setStatus("BLE scanner unavailable");
                return;
            }
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(MIDI_SERVICE_UUID)).build();
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(java.util.Collections.singletonList(filter), settings, scanCallback);
            setStatus("Scanning for BLE MIDI devices…");
        } catch (SecurityException e) {
            setStatus("Bluetooth scan permission required");
        }
    }

    @SuppressLint("MissingPermission")
    public void connectSlave(BluetoothDevice device) {
        if (device == null) return;
        stopScanOnly();
        mode = MODE_SLAVE;
        try {
            setStatus("Connecting to " + displayName(device) + "…");
            clientGatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            setStatus("Bluetooth connect permission required");
        }
    }

    public void disconnect() {
        stopMasterInternal();
        stopSlaveInternal();
        mode = MODE_OFF;
        incomingBpm = 0f;
        incomingRunning = false;
        lastIncomingClockNs = 0L;
        avgClockIntervalNs = 0.0;
        syncRevision++;
        setStatus("MIDI sync off");
        notifySync();
    }

    @SuppressLint("MissingPermission")
    private void stopMasterInternal() {
        clockThreadActive.set(false);
        Thread t = clockThread;
        clockThread = null;
        if (t != null) t.interrupt();
        subscribers.clear();
        try {
            if (advertiser != null) advertiser.stopAdvertising(advertiseCallback);
        } catch (Exception ignored) { }
        advertiser = null;
        try {
            if (gattServer != null) gattServer.close();
        } catch (Exception ignored) { }
        gattServer = null;
        serverCharacteristic = null;
    }

    @SuppressLint("MissingPermission")
    private void stopSlaveInternal() {
        stopScanOnly();
        try {
            if (clientGatt != null) {
                clientGatt.disconnect();
                clientGatt.close();
            }
        } catch (Exception ignored) { }
        clientGatt = null;
        clientCharacteristic = null;
    }

    @SuppressLint("MissingPermission")
    private void stopScanOnly() {
        try {
            if (scanner != null) scanner.stopScan(scanCallback);
        } catch (Exception ignored) { }
        scanner = null;
    }

    private void startClockThread() {
        if (clockThreadActive.getAndSet(true)) return;
        clockThread = new Thread(() -> {
            long next = System.nanoTime();
            while (clockThreadActive.get()) {
                if (mode != MODE_MASTER || !masterRunning) {
                    next = System.nanoTime();
                    LockSupport.parkNanos(5_000_000L);
                    continue;
                }
                long period = (long) (60_000_000_000.0 / (Math.max(20f, masterBpm) * 24.0));
                next += period;
                sendMidiRealtime((byte) 0xF8);
                long wait = next - System.nanoTime();
                if (wait > 0) LockSupport.parkNanos(wait);
                else next = System.nanoTime();
            }
        }, "BLE-MIDI-Clock");
        clockThread.setDaemon(true);
        clockThread.start();
    }

    @SuppressLint("MissingPermission")
    private void sendMidiRealtime(byte midiByte) {
        BluetoothGattServer server = gattServer;
        BluetoothGattCharacteristic characteristic = serverCharacteristic;
        if (server == null || characteristic == null || subscribers.isEmpty()) return;
        byte[] packet = bleMidiPacket(midiByte);
        for (BluetoothDevice device : subscribers) {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    server.notifyCharacteristicChanged(device, characteristic, false, packet);
                } else {
                    characteristic.setValue(packet);
                    server.notifyCharacteristicChanged(device, characteristic, false);
                }
            } catch (Exception ignored) { }
        }
    }

    private static byte[] bleMidiPacket(byte midiByte) {
        int ts = (int) (System.currentTimeMillis() & 0x1FFF);
        return new byte[] {
                (byte) (0x80 | ((ts >> 7) & 0x3F)),
                (byte) (0x80 | (ts & 0x7F)),
                midiByte
        };
    }

    private void parseBleMidi(byte[] value) {
        if (value == null) return;
        for (byte raw : value) {
            int b = raw & 0xFF;
            if (b == 0xF8) handleClock();
            else if (b == 0xFA || b == 0xFB) {
                incomingRunning = true;
                syncRevision++;
                notifySync();
            } else if (b == 0xFC) {
                incomingRunning = false;
                syncRevision++;
                notifySync();
            }
        }
    }

    private void handleClock() {
        long now = System.nanoTime();
        long last = lastIncomingClockNs;
        lastIncomingClockNs = now;
        if (last != 0L) {
            long dt = now - last;
            if (dt > 4_000_000L && dt < 150_000_000L) {
                if (avgClockIntervalNs == 0.0) avgClockIntervalNs = dt;
                else avgClockIntervalNs = avgClockIntervalNs * 0.90 + dt * 0.10;
                float bpm = (float) (60_000_000_000.0 / (avgClockIntervalNs * 24.0));
                if (bpm >= 20f && bpm <= 300f) incomingBpm = bpm;
            }
        }
        syncRevision++;
        if ((syncRevision & 3L) == 0L) notifySync();
    }

    private void setStatus(String newStatus) {
        status = newStatus;
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onStatusChanged(status);
        });
    }

    private void notifyDevices() {
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onDevicesChanged();
        });
    }

    private void notifySync() {
        final float bpm = incomingBpm;
        final boolean running = incomingRunning;
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onSyncChanged(bpm, running);
        });
    }

    @SuppressLint("MissingPermission")
    public String displayName(BluetoothDevice device) {
        if (device == null) return "Unknown device";
        try {
            String name = device.getName();
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (SecurityException ignored) { }
        String address = device.getAddress();
        return address == null ? "BLE MIDI device" : "BLE MIDI • " + address;
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            setStatus(subscribers.isEmpty() ? "MASTER • advertising BLE MIDI • waiting for connection" : "MASTER • connected");
        }
        @Override public void onStartFailure(int errorCode) {
            setStatus("BLE MIDI advertising failed (" + errorCode + ")");
        }
    };

    private final BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {
        @Override public void onServiceAdded(int statusCode, BluetoothGattService service) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS && MIDI_SERVICE_UUID.equals(service.getUuid())) beginAdvertising();
            else setStatus("Could not publish BLE MIDI service");
        }

        @Override public void onConnectionStateChange(BluetoothDevice device, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setStatus("MASTER • connected to " + displayName(device));
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribers.remove(device);
                setStatus("MASTER • advertising BLE MIDI • waiting for connection");
            }
        }

        @SuppressLint("MissingPermission")
        @Override public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if (gattServer == null) return;
            byte[] value = subscribers.contains(device) ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @SuppressLint("MissingPermission")
        @Override public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                                       boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (CCCD_UUID.equals(descriptor.getUuid())) {
                boolean enabled = java.util.Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (enabled) subscribers.add(device); else subscribers.remove(device);
                descriptor.setValue(value);
                if (enabled && masterRunning) sendMidiRealtime((byte) 0xFA);
                setStatus(enabled ? "MASTER • MIDI Clock connected to " + displayName(device) : "MASTER • connection present, notifications off");
            }
            if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @SuppressLint("MissingPermission")
        @Override public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (gattServer == null) return;
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
        }

        @SuppressLint("MissingPermission")
        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                                                           boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            parseBleMidi(value);
            if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null) return;
            synchronized (BleMidiSyncManager.this) {
                scannedDevices.put(device.getAddress(), device);
            }
            setStatus("SLAVE • found " + getScannedDevices().size() + " BLE MIDI device(s)");
            notifyDevices();
        }

        @Override public void onScanFailed(int errorCode) {
            setStatus("BLE MIDI scan failed (" + errorCode + ")");
        }
    };

    private final BluetoothGattCallback clientCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onConnectionStateChange(BluetoothGatt gatt, int statusCode, int newState) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                setStatus("BLE MIDI connection error (" + statusCode + ")");
                try { gatt.close(); } catch (Exception ignored) { }
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setStatus("SLAVE • connected • discovering MIDI service…");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setStatus("SLAVE • disconnected");
                try { gatt.close(); } catch (Exception ignored) { }
            }
        }

        @SuppressLint("MissingPermission")
        @Override public void onServicesDiscovered(BluetoothGatt gatt, int statusCode) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                setStatus("Could not discover BLE MIDI service");
                return;
            }
            BluetoothGattService service = gatt.getService(MIDI_SERVICE_UUID);
            if (service == null) {
                setStatus("Connected device does not expose BLE MIDI");
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(MIDI_CHAR_UUID);
            if (characteristic == null) {
                setStatus("BLE MIDI characteristic missing");
                return;
            }
            clientCharacteristic = characteristic;
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(cccd);
                }
            }
            setStatus("SLAVE • BLE MIDI connected • waiting for clock");
        }

        @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            parseBleMidi(characteristic.getValue());
        }

        @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            parseBleMidi(value);
        }
    };
}
