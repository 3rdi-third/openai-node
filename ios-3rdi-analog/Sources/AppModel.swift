import Foundation
import SwiftUI
import AVFoundation

@MainActor
final class AppModel: ObservableObject {
    @Published var patch = SynthPatch() {
        didSet { syncEngine() }
    }

    @Published var theme = ThemeState()

    @Published var isRunning = true {
        didSet {
            synth.isRunning = isRunning

            if midiRole == .master {
                midi.masterRunning = isRunning
            }
        }
    }

    @Published var currentStep = 0
    @Published var showSynth = false

    @Published var midiStatus =
        "Disconnected"

    @Published var midiRole:
        BLEClock.Role = .off

    @Published var receivedBPM:
        Double = 0

    @Published var presets:
        [SynthPatch?] =
        Array(
            repeating: nil,
            count: 8
        )

    @Published var preferredSampleRate:
        Double = 48_000

    @Published var preferredBufferMS:
        Double = 5.3

    let synth = SynthEngine()
    let midi = BLEClock()

    init() {
        loadStoredState()

        midi.onStatus = {
            [weak self] value in

            Task { @MainActor in
                self?.midiStatus = value
            }
        }

        midi.onTempo = {
            [weak self] bpm in

            Task { @MainActor in
                guard let self,
                      self.midiRole
                        == .slave
                else { return }

                self.receivedBPM = bpm
                self.patch.bpm = bpm
            }
        }

        midi.onTransport = {
            [weak self] running in

            Task { @MainActor in
                guard let self,
                      self.midiRole
                        == .slave
                else { return }

                self.isRunning = running

                if running {
                    self.synth
                        .resetSequence()
                }
            }
        }

        midi.onStep = {
            [weak self] step in

            Task { @MainActor in
                guard let self,
                      self.midiRole
                        == .slave
                else { return }

                self.currentStep =
                    step & 7

                self.synth
                    .externalTrigger(
                        step:
                            step & 7
                    )
            }
        }

        synth.onStep = {
            [weak self] step in

            Task { @MainActor in
                self?.currentStep = step
            }
        }

        configureAudio()
        syncEngine()
        synth.start()
    }

    func configureAudio() {
        synth.configure(
            sampleRate:
                preferredSampleRate,
            bufferDuration:
                preferredBufferMS
                / 1000
        )
    }

    func syncEngine() {
        synth.patch = patch
        synth.isRunning = isRunning

        if midiRole == .master {
            midi.masterBPM = patch.bpm
            midi.masterRunning =
                isRunning
        }
    }

    func setMIDIRole(
        _ role: BLEClock.Role
    ) {
        midiRole = role

        synth.externalClockMode =
            role == .slave

        if role != .slave {
            synth.resetSequence()
        }

        midi.setRole(role)

        midi.masterBPM =
            patch.bpm

        midi.masterRunning =
            isRunning

        if role != .slave {
            receivedBPM = 0
        }
    }

    func savePreset(
        slot: Int,
        name: String
    ) {
        guard presets.indices
                .contains(slot)
        else { return }

        var value = patch

        value.name =
            name.isEmpty
            ? "Preset (slot + 1)"
            : name

        presets[slot] = value

        saveStoredState()
    }

    func loadPreset(
        slot: Int
    ) {
        guard presets.indices
                .contains(slot),
              let value =
                presets[slot]
        else { return }

        patch = value
    }

    func saveTheme() {
        saveStoredState()
    }

    private func saveStoredState() {
        let encoder =
            JSONEncoder()

        if let data =
            try? encoder.encode(
                presets
            ) {
            UserDefaults
                .standard
                .set(
                    data,
                    forKey: "presets"
                )
        }

        if let data =
            try? encoder.encode(
                theme
            ) {
            UserDefaults
                .standard
                .set(
                    data,
                    forKey: "theme"
                )
        }

        UserDefaults.standard.set(
            preferredSampleRate,
            forKey: "sampleRate"
        )

        UserDefaults.standard.set(
            preferredBufferMS,
            forKey: "bufferMS"
        )
    }

    private func loadStoredState() {
        let decoder =
            JSONDecoder()

        if let data =
            UserDefaults
                .standard
                .data(
                    forKey: "presets"
                ),
           let stored =
            try? decoder.decode(
                [SynthPatch?].self,
                from: data
            ),
           stored.count == 8 {

            presets = stored
        }

        if let data =
            UserDefaults
                .standard
                .data(
                    forKey: "theme"
                ),
           let stored =
            try? decoder.decode(
                ThemeState.self,
                from: data
            ) {

            theme = stored
        }

        let sampleRate =
            UserDefaults
                .standard
                .double(
                    forKey:
                        "sampleRate"
                )

        if sampleRate > 0 {
            preferredSampleRate =
                sampleRate
        }

        let buffer =
            UserDefaults
                .standard
                .double(
                    forKey:
                        "bufferMS"
                )

        if buffer > 0 {
            preferredBufferMS =
                buffer
        }
    }
}
