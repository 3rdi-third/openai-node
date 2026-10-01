import Foundation
import SwiftUI

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Group {
            if model.showSynth {
                SynthView()
            } else {
                StartupView()
            }
        }
    }
}

private enum AppSheet: Int, Identifiable {
    case presets, audio, theme, midi
    var id: Int { rawValue }
}

struct StartupView: View {
    @EnvironmentObject var model: AppModel
    @State private var sheet: AppSheet?

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    .black,
                    Color(red: 0.08, green: 0.03, blue: 0.01),
                    Color(red: 0.22, green: 0.07, blue: 0.01)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                Spacer()

                Text("3RDI")
                    .font(.system(size: 76, weight: .black, design: .rounded))
                    .foregroundStyle(.white)

                Text("ANALOG PERCUSSION")
                    .font(.title.bold())
                    .foregroundStyle(.white)

                Text("DFAM-STYLE PERCUSSION SYNTH • iOS")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))

                Spacer().frame(height: 14)

                Button("START SYNTH") {
                    model.showSynth = true
                }
                .buttonStyle(PrimaryButtonStyle())

                Button("PRESETS") {
                    sheet = .presets
                }
                .buttonStyle(PrimaryButtonStyle())

                Button("AUDIO SETTINGS") {
                    sheet = .audio
                }
                .buttonStyle(PrimaryButtonStyle())

                Button("COLOUR THEME") {
                    sheet = .theme
                }
                .buttonStyle(PrimaryButtonStyle())

                Button("BLE MIDI") {
                    sheet = .midi
                }
                .buttonStyle(PrimaryButtonStyle())

                Spacer()

                HStack {
                    Spacer()
                    Text("created by Ben Bouchnafa")
                        .font(.caption.bold())
                        .foregroundStyle(.white)
                }
            }
            .padding(28)
        }
        .sheet(item: $sheet) { selected in
            switch selected {
            case .presets:
                PresetsView()
            case .audio:
                AudioSettingsView()
            case .theme:
                ThemeView()
            case .midi:
                MIDIView()
            }
        }
    }
}

struct SynthView: View {
    @EnvironmentObject var model: AppModel
    @State private var sheet: AppSheet?

    var body: some View {
        GeometryReader { geometry in
            ScrollView([.horizontal, .vertical], showsIndicators: false) {
                VStack(spacing: 12) {
                    HStack {
                        Text("3RDI ANALOG PERCUSSION")
                            .font(.title2.bold())

                        Spacer()

                        Button("PRESETS") { sheet = .presets }
                        Button("MIDI") { sheet = .midi }
                        Button("AUDIO") { sheet = .audio }
                        Button("COLOURS") { sheet = .theme }
                    }
                    .foregroundStyle(model.theme.text)

                    HStack(spacing: 13) {
                        BPMField(value: $model.patch.bpm)

                        Knob(
                            value: $model.patch.vco1,
                            range: 28...440,
                            label: "VCO 1",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.detune,
                            range: -24...24,
                            label: "VCO 2",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.fm,
                            range: 0...1,
                            label: "FM",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.noise,
                            range: 0...1,
                            label: "NOISE",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.cutoff,
                            range: 70...12000,
                            label: "CUTOFF",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.resonance,
                            range: 0...0.96,
                            label: "RES",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.vcfDecay,
                            range: 35...2400,
                            label: "VCF DEC",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.vcaDecay,
                            range: 35...2400,
                            label: "VCA DEC",
                            color: model.theme.knob
                        )
                    }

                    HStack(spacing: 13) {
                        Knob(
                            value: $model.patch.lfoRate,
                            range: 0.05...30,
                            label: "LFO RATE",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.lfoPitch,
                            range: -12...12,
                            label: "LFO→PITCH",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.lfoVCF,
                            range: -3...3,
                            label: "LFO→VCF",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.lfoFM,
                            range: -1...1,
                            label: "LFO→FM",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.envVCF,
                            range: 0...2,
                            label: "ENV→VCF",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.pitchEnv,
                            range: -24...24,
                            label: "PITCH ENV",
                            color: model.theme.knob
                        )

                        Knob(
                            value: $model.patch.drive,
                            range: 1...6,
                            label: "DRIVE",
                            color: model.theme.knob
                        )
                    }

                    HStack(spacing: 16) {
                        ForEach(0..<8, id: \.self) { index in
                            VStack(spacing: 5) {
                                Text("\(index + 1)")
                                    .font(.caption.bold())
                                    .padding(5)
                                    .background(
                                        model.currentStep == index
                                        ? model.theme.knob
                                        : Color.clear,
                                        in: Circle()
                                    )

                                Knob(
                                    value: Binding(
                                        get: {
                                            model.patch.steps[index].pitch
                                        },
                                        set: {
                                            model.patch.steps[index].pitch = $0
                                        }
                                    ),
                                    range: -24...24,
                                    label: "PITCH",
                                    size: 36,
                                    color: model.theme.knob
                                )

                                Knob(
                                    value: Binding(
                                        get: {
                                            model.patch.steps[index].velocity
                                        },
                                        set: {
                                            model.patch.steps[index].velocity = $0
                                        }
                                    ),
                                    range: 0...1,
                                    label: "VEL",
                                    size: 36,
                                    color: model.theme.knob
                                )
                            }
                        }
                    }

                    HStack {
                        Button(model.isRunning ? "STOP" : "RUN") {
                            model.isRunning.toggle()
                            model.syncEngine()
                        }
                        .buttonStyle(
                            PrimaryButtonStyle(color: model.theme.knob)
                        )

                        Button("TRIG") {
                            model.synth.externalTrigger(step: model.currentStep)
                        }
                        .buttonStyle(
                            PrimaryButtonStyle(color: model.theme.knob)
                        )

                        Button("START SCREEN") {
                            model.showSynth = false
                        }
                        .buttonStyle(
                            PrimaryButtonStyle(color: model.theme.knob)
                        )
                    }
                }
                .padding(16)
                .frame(
                    minWidth: max(geometry.size.width, 1050),
                    minHeight: geometry.size.height
                )
                .background(model.theme.panel)
            }
        }
        .sheet(item: $sheet) { selected in
            switch selected {
            case .presets:
                PresetsView()
            case .audio:
                AudioSettingsView()
            case .theme:
                ThemeView()
            case .midi:
                MIDIView()
            }
        }
    }
}

struct BPMField: View {
    @Binding var value: Double
    @State private var text = "133"

    var body: some View {
        VStack(spacing: 4) {
            TextField("BPM", text: $text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .frame(width: 70, height: 48)
                .background(
                    .white,
                    in: RoundedRectangle(cornerRadius: 8)
                )
                .foregroundStyle(.black)
                .onAppear {
                    text = String(Int(value.rounded()))
                }
                .onChange(of: text) { newValue in
                    if let parsed = Double(newValue) {
                        value = min(300, max(40, parsed))
                    }
                }

            Text("BPM")
                .font(.caption2.bold())
                .foregroundStyle(.black)
        }
    }
}

struct PresetsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(0..<8, id: \.self) { index in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(
                                model.presets[index]?.name
                                ?? "Empty Slot \(index + 1)"
                            )

                            Text(
                                model.presets[index]
                                    .map { "\(Int($0.bpm)) BPM" }
                                ?? ""
                            )
                            .font(.caption)
                        }

                        Spacer()

                        Button("LOAD") {
                            model.loadPreset(slot: index)
                            dismiss()
                        }

                        Button("SAVE") {
                            model.savePreset(slot: index, name: name)
                        }
                    }
                }
            }
            .navigationTitle("Presets")
            .safeAreaInset(edge: .bottom) {
                TextField("Preset name", text: $name)
                    .textFieldStyle(.roundedBorder)
                    .padding()
            }
        }
    }
}

struct AudioSettingsView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            Form {
                Picker(
                    "Sample Rate",
                    selection: $model.preferredSampleRate
                ) {
                    Text("44.1 kHz").tag(44100.0)
                    Text("48 kHz").tag(48000.0)
                    Text("96 kHz").tag(96000.0)
                }

                Picker(
                    "Buffer",
                    selection: $model.preferredBufferMS
                ) {
                    Text("2.7 ms").tag(2.7)
                    Text("5.3 ms").tag(5.3)
                    Text("10.7 ms").tag(10.7)
                }

                Button("APPLY AUDIO SETTINGS") {
                    model.configureAudio()
                }
            }
            .navigationTitle("Audio Settings")
        }
    }
}

private struct ThemePreset: Identifiable {
    let id: String
    let panel: String
    let knob: String
    let text: String
}

struct ThemeView: View {
    @EnvironmentObject var model: AppModel

    private let colors = [
        "111111", "FFFFFF", "D8C3A0", "E47A22", "FF1744",
        "FFEA00", "00E676", "00B0FF", "651FFF", "D500F9",
        "795548", "607D8B", "263238", "B0BEC5", "80CBC4",
        "FF8A65", "8BC34A", "1DE9B6", "FF4081", "673AB7"
    ]

    private let presets = [
        ThemePreset(id: "Original", panel: "D8C3A0", knob: "E47A22", text: "111111"),
        ThemePreset(id: "Industrial", panel: "252525", knob: "FF6D00", text: "F5F5F5"),
        ThemePreset(id: "Acid", panel: "151515", knob: "B6FF00", text: "FFFFFF"),
        ThemePreset(id: "Cyberpunk", panel: "120028", knob: "00E5FF", text: "FF2DAA"),
        ThemePreset(id: "Blood Red", panel: "160000", knob: "FF1744", text: "FFFFFF"),
        ThemePreset(id: "Ice", panel: "D9F7FF", knob: "00A9E8", text: "071C2C"),
        ThemePreset(id: "Toxic", panel: "0D1B00", knob: "7CFC00", text: "E8FFD8"),
        ThemePreset(id: "Steel", panel: "303840", knob: "B0BEC5", text: "FFFFFF")
    ]

    @State private var target = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Picker("Target", selection: $target) {
                        Text("PANEL").tag(0)
                        Text("KNOBS").tag(1)
                        Text("TEXT").tag(2)
                        Text("ACCENT").tag(3)
                    }
                    .pickerStyle(.segmented)

                    LazyVGrid(
                        columns: Array(
                            repeating: GridItem(.flexible()),
                            count: 5
                        )
                    ) {
                        ForEach(colors, id: \.self) { hex in
                            Button {
                                set(hex)
                            } label: {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color(hex: hex))
                                    .frame(height: 48)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(.white.opacity(0.35))
                                    )
                            }
                        }
                    }

                    Divider()
                    Text("THEME PRESETS")
                        .font(.headline)

                    ForEach(presets) { preset in
                        Button(preset.id) {
                            model.theme.panelHex = preset.panel
                            model.theme.knobHex = preset.knob
                            model.theme.textHex = preset.text
                            model.saveTheme()
                        }
                        .buttonStyle(
                            PrimaryButtonStyle(
                                color: Color(hex: preset.knob)
                            )
                        )
                    }

                    Button("SAVE THEME") {
                        model.saveTheme()
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
                .padding()
            }
            .navigationTitle("Colour Theme")
        }
    }

    private func set(_ hex: String) {
        switch target {
        case 0:
            model.theme.panelHex = hex
        case 1:
            model.theme.knobHex = hex
        case 2:
            model.theme.textHex = hex
        default:
            model.theme.accentHex = hex
        }
    }
}

struct MIDIView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Text(model.midiStatus)
                        .font(.headline)

                    if model.receivedBPM > 0 {
                        Text(
                            String(
                                format: "LOCKED %.1f BPM",
                                model.receivedBPM
                            )
                        )
                        .font(.title2.monospacedDigit())
                    }

                    ForEach(
                        BLEClock.Role.allCases,
                        id: \.rawValue
                    ) { role in
                        Button(
                            role == .master
                            ? "MASTER • SEND CLOCK"
                            : role == .slave
                                ? "SLAVE • RECEIVE CLOCK"
                                : "MIDI OFF"
                        ) {
                            model.setMIDIRole(role)
                        }
                        .buttonStyle(
                            PrimaryButtonStyle(
                                color: role == model.midiRole
                                ? .green
                                : .orange
                            )
                        )
                    }

                    Text(
                        "BLE MIDI uses the standard Apple-compatible MIDI service. Slave tempo uses a 48-clock PLL to reduce Bluetooth timestamp jitter."
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
                .padding()
            }
            .navigationTitle("Bluetooth MIDI")
        }
    }
}
