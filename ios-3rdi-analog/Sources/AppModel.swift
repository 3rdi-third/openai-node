import Foundation
import SwiftUI
import AVFoundation

@MainActor
final class AppModel: ObservableObject {
    @Published var patch = SynthPatch() { didSet { syncEngine() } }
    @Published var theme = ThemeState()
    @Published var isRunning = true { didSet { synth.isRunning = isRunning } }
    @Published var currentStep = 0
    @Published var showSynth = false
    @Published var midiStatus = "Disconnected"
    @Published var midiRole: BLEClock.Role = .off
    @Published var receivedBPM: Double = 0
    @Published var presets: [SynthPatch?] = Array(repeating: nil, count: 8)
    @Published var preferredSampleRate: Double = 48_000
    @Published var preferredBufferMS: Double = 5.3

    let synth = SynthEngine()
    let midi = BLEClock()

    init() {
        loadStoredState()

        midi.onStatus = { [weak self] value in
            Task { @MainActor in self?.midiStatus = value }
        }
        midi.onTempo = { [weak self] bpm in
            Task { @MainActor in
                guard let self, self.midiRole == .slave else { return }
                self.receivedBPM = bpm
                self.patch.bpm = bpm
            }
        }
        midi.onTransport = { [weak self] running in
            Task { @MainActor in
                guard let self, self.midiRole == .slave else { return }
                self.isRunning = running
            }
        }
        midi.onStep = { [weak self] step in
            Task { @MainActor in
                guard let self, self.midiRole == .slave else { return }
                self.currentStep = step & 7
                self.synth.externalTrigger(step: step & 7)
            }
        }
        synth.onStep = { [weak self] step in
            Task { @MainActor in self?.currentStep = step }
        }

        configureAudio()
        syncEngine()
        synth.start()
    }

    func configureAudio() {
        synth.configure(sampleRate: preferredSampleRate, bufferDuration: preferredBufferMS / 1000)
    }

    func syncEngine() {
        synth.patch = patch
        synth.isRunning = isRunning
        if midiRole == .master {
            midi.masterBPM = patch.bpm
            midi.masterRunning = isRunning
        }
    }

    func setMIDIRole(_ role: BLEClock.Role) {
        midiRole = role
        midi.setRole(role)
        midi.masterBPM = patch.bpm
        midi.masterRunning = isRunning
        if role != .slave { receivedBPM = 0 }
    }

    func savePreset(slot: Int, name: String) {
        guard presets.indices.contains(slot) else { return }
        var p = patch
        p.name = name.isEmpty ? "Preset (slot + 1)" : name
        presets[slot] = p
        saveStoredState()
    }

    func loadPreset(slot: Int) {
        guard presets.indices.contains(slot), let p = presets[slot] else { return }
        patch = p
    }

    func saveTheme() { saveStoredState() }

    private func saveStoredState() {
        let enc = JSONEncoder()
        if let d = try? enc.encode(presets) { UserDefaults.standard.set(d, forKey: "presets") }
        if let d = try? enc.encode(theme) { UserDefaults.standard.set(d, forKey: "theme") }
        UserDefaults.standard.set(preferredSampleRate, forKey: "sampleRate")
        UserDefaults.standard.set(preferredBufferMS, forKey: "bufferMS")
    }

    private func loadStoredState() {
        let dec = JSONDecoder()
        if let d = UserDefaults.standard.data(forKey: "presets"),
           let p = try? dec.decode([SynthPatch?].self, from: d),
           p.count == 8 {
            presets = p
        }
        if let d = UserDefaults.standard.data(forKey: "theme"),
           let t = try? dec.decode(ThemeState.self, from: d) {
            theme = t
        }
        let sr = UserDefaults.standard.double(forKey: "sampleRate")
        if sr > 0 { preferredSampleRate = sr }
        let bf = UserDefaults.standard.double(forKey: "bufferMS")
        if bf > 0 { preferredBufferMS = bf }
    }
}
