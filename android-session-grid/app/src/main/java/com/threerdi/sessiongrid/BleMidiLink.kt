package com.threerdi.sessiongrid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.UUID

@SuppressLint("MissingPermission")
class BleMidiLink(
    private val context: Context,
    private val onStateChanged: (State) -> Unit,
    private val onMidi: (ByteArray) -> Unit
) {
    enum class Mode { OFF, HOST, JOIN }
    enum class State { OFF, PERMISSION, HOSTING, SCANNING, HOST_CONNECTED, JOIN_CONNECTED, UNAVAILABLE, ERROR }

    companion object {
        const val PERMISSION_REQUEST_CODE = 6130
        private val MIDI_SERVICE_UUID: UUID = UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700")
        private val MIDI_CHARACTERISTIC_UUID: UUID = UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val main = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    var mode: Mode = Mode.OFF
        private set
    var state: State = State.OFF
        private set

    val isHostMaster: Boolean
        get() = mode == Mode.HOST

    private var gattServer: BluetoothGattServer? = null
    private var hostCharacteristic: BluetoothGattCharacteristic? = null
    private val hostClients = linkedSetOf<BluetoothDevice>()
    private val subscribedClients = linkedSetOf<BluetoothDevice>()
    private var advertiseCallback: AdvertiseCallback? = null

    private var clientGatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null

    fun requestPermissions(activity: Activity): Boolean {
        val missing = requiredPermissions().filter {
            activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        activity.requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        setState(State.PERMISSION)
        return false
    }

    fun cycle(activity: Activity) {
        when (mode) {
            Mode.OFF -> startHost(activity)
            Mode.HOST -> startJoin(activity)
            Mode.JOIN -> stop()
        }
    }

    fun startHost(activity: Activity) {
        closeLinks()
        mode = Mode.HOST
        if (!requestPermissions(activity)) return
        if (!bluetoothAvailable()) {
            setState(State.UNAVAILABLE)
            return
        }

        try {
            val characteristic = BluetoothGattCharacteristic(
                MIDI_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            characteristic.addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
            val service = BluetoothGattService(MIDI_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(characteristic)

            gattServer = bluetoothManager.openGattServer(context, serverCallback)
            hostCharacteristic = characteristic
            if (gattServer?.addService(service) != true) {
                setState(State.ERROR)
                return
            }

            val advertiser = adapter?.bluetoothLeAdvertiser
            if (advertiser == null) {
                setState(State.UNAVAILABLE)
                return
            }
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(MIDI_SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build()
            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    setState(if (hostClients.isEmpty()) State.HOSTING else State.HOST_CONNECTED)
                }

                override fun onStartFailure(errorCode: Int) {
                    setState(State.ERROR)
                }
            }
            advertiseCallback = callback
            advertiser.startAdvertising(settings, data, callback)
            setState(State.HOSTING)
        } catch (_: Throwable) {
            setState(State.ERROR)
        }
    }

    fun startJoin(activity: Activity) {
        closeLinks()
        mode = Mode.JOIN
        if (!requestPermissions(activity)) return
        if (!bluetoothAvailable()) {
            setState(State.UNAVAILABLE)
            return
        }

        try {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                setState(State.UNAVAILABLE)
                return
            }
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MIDI_SERVICE_UUID)).build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    scanCallback = null
                    connectTo(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    setState(State.ERROR)
                }
            }
            scanCallback = callback
            scanner.startScan(listOf(filter), settings, callback)
            setState(State.SCANNING)
        } catch (_: Throwable) {
            setState(State.ERROR)
        }
    }

    fun sendMidi(message: ByteArray) {
        if (message.isEmpty()) return
        val packet = encodeBleMidi(message)
        when (mode) {
            Mode.HOST -> sendFromHost(packet)
            Mode.JOIN -> sendFromClient(packet)
            Mode.OFF -> Unit
        }
    }

    fun stop() {
        closeLinks()
        mode = Mode.OFF
        setState(State.OFF)
    }

    private fun bluetoothAvailable(): Boolean =
        adapter != null && adapter.isEnabled && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun setState(newState: State) {
        state = newState
        main.post { onStateChanged(newState) }
    }

    private fun postMidi(packet: ByteArray) {
        val midi = decodeBleMidi(packet)
        if (midi.isNotEmpty()) main.post { onMidi(midi) }
    }

    private fun encodeBleMidi(midi: ByteArray): ByteArray {
        val timestamp = (SystemClock.elapsedRealtime().toInt() and 0x1fff)
        val header = (0x80 or ((timestamp shr 7) and 0x3f)).toByte()
        val low = (0x80 or (timestamp and 0x7f)).toByte()
        return byteArrayOf(header, low) + midi
    }

    private fun decodeBleMidi(packet: ByteArray): ByteArray {
        if (packet.size < 3) return byteArrayOf()
        var index = 0
        if ((packet[index].toInt() and 0x80) != 0) index++
        if (index < packet.size && (packet[index].toInt() and 0x80) != 0) index++
        return if (index < packet.size) packet.copyOfRange(index, packet.size) else byteArrayOf()
    }

    private fun sendFromHost(packet: ByteArray) {
        val server = gattServer ?: return
        val characteristic = hostCharacteristic ?: return
        characteristic.value = packet
        subscribedClients.toList().forEach { device ->
            try {
                server.notifyCharacteristicChanged(device, characteristic, false)
            } catch (_: Throwable) {
            }
        }
    }

    private fun sendFromClient(packet: ByteArray) {
        val gatt = clientGatt ?: return
        val characteristic = clientCharacteristic ?: return
        try {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = packet
            gatt.writeCharacteristic(characteristic)
        } catch (_: Throwable) {
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        try {
            setState(State.SCANNING)
            clientGatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: Throwable) {
            setState(State.ERROR)
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                hostClients.add(device)
                setState(State.HOST_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                hostClients.remove(device)
                subscribedClients.remove(device)
                setState(if (hostClients.isEmpty()) State.HOSTING else State.HOST_CONNECTED)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MIDI_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value ?: byteArrayOf())
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == MIDI_CHARACTERISTIC_UUID) postMidi(value)
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                if (enabled) subscribedClients.add(device) else subscribedClients.remove(device)
                descriptor.value = value
            }
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                clientGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clientCharacteristic = null
                try {
                    gatt.close()
                } catch (_: Throwable) {
                }
                if (mode == Mode.JOIN) setState(State.SCANNING)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState(State.ERROR)
                return
            }
            val characteristic = gatt.getService(MIDI_SERVICE_UUID)?.getCharacteristic(MIDI_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                setState(State.ERROR)
                return
            }
            clientCharacteristic = characteristic
            try {
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(CCCD_UUID)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
                setState(State.JOIN_CONNECTED)
            } catch (_: Throwable) {
                setState(State.ERROR)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == MIDI_CHARACTERISTIC_UUID) {
                postMidi(characteristic.value ?: byteArrayOf())
            }
        }
    }

    private fun closeLinks() {
        try {
            scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: Throwable) {
        }
        scanCallback = null

        try {
            advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
        } catch (_: Throwable) {
        }
        advertiseCallback = null

        try {
            clientGatt?.disconnect()
            clientGatt?.close()
        } catch (_: Throwable) {
        }
        clientGatt = null
        clientCharacteristic = null

        try {
            gattServer?.close()
        } catch (_: Throwable) {
        }
        gattServer = null
        hostCharacteristic = null
        hostClients.clear()
        subscribedClients.clear()
    }
}
