import Foundation
import CoreBluetooth
import Combine

final class BLEClock: NSObject, ObservableObject,
    CBCentralManagerDelegate,
    CBPeripheralDelegate,
    CBPeripheralManagerDelegate {

    enum Role: String, CaseIterable {
        case off = "OFF"
        case master = "MASTER"
        case slave = "SLAVE"
    }

    static let serviceUUID =
        CBUUID(string: "03B80E5A-EDE8-4B33-A751-6CE34EC4C700")

    static let charUUID =
        CBUUID(string: "7772E5DB-3868-4112-A1A9-F2669D106BF3")

    var onStatus: ((String) -> Void)?
    var onTempo: ((Double) -> Void)?
    var onTransport: ((Bool) -> Void)?
    var onStep: ((Int) -> Void)?

    var masterBPM = 133.0

    var masterRunning = true {
        didSet {
            guard role == .master,
                  oldValue != masterRunning
            else { return }

            sendRealtime(
                masterRunning ? 0xFA : 0xFC
            )
        }
    }

    private var role: Role = .off

    private var central: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?

    private var target: CBPeripheral?
    private var remoteChar: CBCharacteristic?
    private var localChar: CBMutableCharacteristic?

    private var timer: DispatchSourceTimer?

    private var timestamp13 = 0

    private var slaveClockCount = 0

    private var lastPacketSignature = 0

    private var windowStartTS: Int?
    private var windowClocks = 0
    private var filteredBPM = 0.0

    func setRole(_ newRole: Role) {
        stopAll()
        role = newRole

        switch newRole {
        case .off:
            onStatus?("MIDI off")

        case .master:
            peripheralManager =
                CBPeripheralManager(
                    delegate: self,
                    queue: DispatchQueue(
                        label: "3rdi.ble.master"
                    )
                )

            onStatus?(
                "Starting BLE MIDI master…"
            )

        case .slave:
            central =
                CBCentralManager(
                    delegate: self,
                    queue: DispatchQueue(
                        label: "3rdi.ble.slave"
                    )
                )

            onStatus?(
                "Starting BLE MIDI scan…"
            )
        }
    }

    private func stopAll() {
        timer?.cancel()
        timer = nil

        central?.stopScan()

        if let target {
            central?.cancelPeripheralConnection(
                target
            )
        }

        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()

        central = nil
        peripheralManager = nil
        target = nil
        remoteChar = nil
        localChar = nil

        windowStartTS = nil
        windowClocks = 0
        filteredBPM = 0
        slaveClockCount = 0
        lastPacketSignature = 0
    }

    func peripheralManagerDidUpdateState(
        _ peripheral: CBPeripheralManager
    ) {
        guard role == .master else { return }

        guard peripheral.state == .poweredOn
        else {
            onStatus?(
                "Bluetooth unavailable"
            )
            return
        }

        let characteristic =
            CBMutableCharacteristic(
                type: Self.charUUID,
                properties: [
                    .notify,
                    .writeWithoutResponse,
                    .read
                ],
                value: nil,
                permissions: [
                    .readable,
                    .writeable
                ]
            )

        let service =
            CBMutableService(
                type: Self.serviceUUID,
                primary: true
            )

        service.characteristics = [
            characteristic
        ]

        localChar = characteristic

        peripheral.add(service)
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        didAdd service: CBService,
        error: Error?
    ) {
        guard error == nil
        else {
            onStatus?(
                "BLE MIDI service error"
            )
            return
        }

        peripheral.startAdvertising(
            [
                CBAdvertisementDataServiceUUIDsKey:
                    [Self.serviceUUID],

                CBAdvertisementDataLocalNameKey:
                    "3rdi Analog Percussion"
            ]
        )

        onStatus?(
            "MASTER • advertising"
        )

        startMasterTimer()

        if masterRunning {
            sendRealtime(0xFA)
        }
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeTo characteristic:
            CBCharacteristic
    ) {
        onStatus?(
            "MASTER • device connected"
        )

        if masterRunning {
            sendRealtime(0xFA)
        }
    }

    private func startMasterTimer() {
        timer?.cancel()

        let queue =
            DispatchQueue(
                label: "3rdi.ble.clock",
                qos: .userInteractive
            )

        let timer =
            DispatchSource.makeTimerSource(
                queue: queue
            )

        timer.schedule(
            deadline: .now(),
            repeating: .milliseconds(4),
            leeway: .milliseconds(1)
        )

        var accumulator = 0.0

        var last =
            DispatchTime
                .now()
                .uptimeNanoseconds

        timer.setEventHandler {
            [weak self] in

            guard let self,
                  self.role == .master,
                  self.masterRunning,
                  let manager =
                    self.peripheralManager,
                  let characteristic =
                    self.localChar
            else { return }

            let now =
                DispatchTime
                    .now()
                    .uptimeNanoseconds

            let elapsed =
                Double(now - last)
                / 1_000_000_000

            last = now
            accumulator += elapsed

            let tick =
                60.0
                / max(
                    40.0,
                    self.masterBPM
                )
                / 24.0

            var events:
                [(Int, UInt8)] = []

            while accumulator >= tick,
                  events.count < 6 {

                accumulator -= tick

                self.timestamp13 =
                    (
                        self.timestamp13
                        + Int(
                            (tick * 1000)
                                .rounded()
                        )
                    )
                    & 0x1FFF

                events.append(
                    (
                        self.timestamp13,
                        0xF8
                    )
                )
            }

            guard !events.isEmpty
            else { return }

            var group:
                [(Int, UInt8)] = []

            var groupHigh:
                Int?

            func flush() {
                guard !group.isEmpty
                else { return }

                manager.updateValue(
                    self.packet(group),
                    for: characteristic,
                    onSubscribedCentrals: nil
                )

                group.removeAll(
                    keepingCapacity: true
                )
            }

            for event in events {
                let high =
                    (event.0 >> 7)
                    & 0x3F

                if let current = groupHigh,
                   current != high {

                    flush()
                }

                groupHigh = high
                group.append(event)
            }

            flush()
        }

        self.timer = timer
        timer.resume()
    }

    private func sendRealtime(
        _ status: UInt8
    ) {
        guard let manager =
                peripheralManager,
              let characteristic =
                localChar
        else { return }

        timestamp13 =
            (
                timestamp13 + 1
            )
            & 0x1FFF

        manager.updateValue(
            packet(
                [
                    (
                        timestamp13,
                        status
                    )
                ]
            ),
            for: characteristic,
            onSubscribedCentrals: nil
        )
    }

    private func packet(
        _ events:
            [(Int, UInt8)]
    ) -> Data {
        guard let first =
            events.first
        else { return Data() }

        let high =
            UInt8(
                (first.0 >> 7)
                & 0x3F
            )

        var bytes:
            [UInt8] = [
                0x80 | high
            ]

        for (timestamp, status)
            in events {

            bytes.append(
                0x80
                | UInt8(
                    timestamp & 0x7F
                )
            )

            bytes.append(status)
        }

        return Data(bytes)
    }

    func centralManagerDidUpdateState(
        _ central: CBCentralManager
    ) {
        guard role == .slave
        else { return }

        guard central.state == .poweredOn
        else {
            onStatus?(
                "Bluetooth unavailable"
            )
            return
        }

        central.scanForPeripherals(
            withServices: [
                Self.serviceUUID
            ],
            options: [
                CBCentralManagerScanOptionAllowDuplicatesKey:
                    false
            ]
        )

        onStatus?(
            "SLAVE • scanning…"
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral:
            CBPeripheral,
        advertisementData:
            [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard target == nil
        else { return }

        target = peripheral
        peripheral.delegate = self

        central.stopScan()

        central.connect(peripheral)

        onStatus?(
            "Connecting to "
            + (
                peripheral.name
                ?? "BLE MIDI"
            )
            + "…"
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didConnect peripheral:
            CBPeripheral
    ) {
        onStatus?(
            "Connected • discovering MIDI…"
        )

        peripheral.discoverServices(
            [Self.serviceUUID]
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral:
            CBPeripheral,
        error: Error?
    ) {
        target = nil
        remoteChar = nil

        guard role == .slave,
              central.state == .poweredOn
        else { return }

        central.scanForPeripherals(
            withServices: [
                Self.serviceUUID
            ]
        )

        onStatus?(
            "SLAVE • reconnecting…"
        )
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverServices error: Error?
    ) {
        guard error == nil
        else { return }

        peripheral.services?
            .filter {
                $0.uuid
                == Self.serviceUUID
            }
            .forEach {
                peripheral
                    .discoverCharacteristics(
                        [Self.charUUID],
                        for: $0
                    )
            }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor
            service: CBService,
        error: Error?
    ) {
        guard error == nil
        else { return }

        guard let characteristic =
            service.characteristics?
                .first(
                    where: {
                        $0.uuid
                        == Self.charUUID
                    }
                )
        else { return }

        remoteChar = characteristic

        peripheral.setNotifyValue(
            true,
            for: characteristic
        )

        onStatus?(
            "SLAVE • clock connected"
        )
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic:
            CBCharacteristic,
        error: Error?
    ) {
        guard error == nil,
              let data =
                characteristic.value
        else { return }

        let bytes =
            [UInt8](data)

        guard bytes.count >= 3
        else { return }

        let signature =
            bytes.reduce(17) {
                (
                    $0 &* 31
                )
                &+ Int($1)
            }

        if signature
            == lastPacketSignature {
            return
        }

        lastPacketSignature =
            signature

        let high =
            Int(
                bytes[0] & 0x3F
            )
            << 7

        var index = 1

        while index + 1
                < bytes.count {

            guard bytes[index]
                    & 0x80
                    != 0
            else {
                index += 1
                continue
            }

            let timestamp =
                (
                    high
                    | Int(
                        bytes[index]
                        & 0x7F
                    )
                )
                & 0x1FFF

            let status =
                bytes[index + 1]

            switch status {
            case 0xF8:
                handleClock(
                    timestamp:
                        timestamp
                )

            case 0xFA:
                slaveClockCount = 0
                windowStartTS = nil
                windowClocks = 0
                onTransport?(true)

            case 0xFB:
                onTransport?(true)

            case 0xFC:
                onTransport?(false)

            default:
                break
            }

            index += 2
        }
    }

    private func handleClock(
        timestamp: Int
    ) {
        slaveClockCount += 1

        if slaveClockCount % 12 == 0 {
            let step =
                (
                    slaveClockCount
                    / 12
                    - 1
                )
                & 7

            onStep?(step)
        }

        if windowStartTS == nil {
            windowStartTS =
                timestamp

            windowClocks = 0
            return
        }

        windowClocks += 1

        guard windowClocks >= 48,
              let start =
                windowStartTS
        else { return }

        var elapsed =
            timestamp - start

        if elapsed <= 0 {
            elapsed += 8192
        }

        if elapsed > 100 {
            let bpm =
                60_000.0
                * Double(
                    windowClocks
                )
                / (
                    24.0
                    * Double(elapsed)
                )

            if (40...300)
                .contains(bpm) {

                filteredBPM =
                    filteredBPM == 0
                    ? bpm
                    : (
                        filteredBPM
                        * 0.85
                        + bpm * 0.15
                    )

                let locked =
                    (
                        filteredBPM * 10
                    )
                    .rounded()
                    / 10

                onTempo?(locked)
            }
        }

        windowStartTS = timestamp
        windowClocks = 0
    }
}
