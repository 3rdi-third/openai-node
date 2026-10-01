import Foundation
import SwiftUI

struct StepState: Codable, Hashable {
    var pitch: Double = 0
    var velocity: Double = 0.8
}

struct SynthPatch: Codable, Hashable {
    var name: String = "Init"
    var bpm: Double = 133
    var vco1: Double = 62
    var detune: Double = 7
    var fm: Double = 0.28
    var noise: Double = 0.12
    var cutoff: Double = 780
    var resonance: Double = 0.34
    var vcfDecay: Double = 420
    var vcaDecay: Double = 330
    var drive: Double = 2.2
    var lfoRate: Double = 1.0
    var lfoPitch: Double = 0
    var lfoVCF: Double = 0
    var lfoFM: Double = 0
    var envVCF: Double = 1.0
    var pitchEnv: Double = 0
    var steps: [StepState] = [
        .init(pitch: 0, velocity: 1), .init(pitch: 0, velocity: 0.58),
        .init(pitch: 7, velocity: 0.82), .init(pitch: -5, velocity: 0.52),
        .init(pitch: 0, velocity: 0.96), .init(pitch: 12, velocity: 0.72),
        .init(pitch: -2, velocity: 0.45), .init(pitch: 5, velocity: 0.88)
    ]
}

struct ThemeState: Codable, Hashable {
    var panelHex: String = "D8C3A0"
    var knobHex: String = "E47A22"
    var textHex: String = "111111"
    var accentHex: String = "F2E0B7"

    var panel: Color { Color(hex: panelHex) }
    var knob: Color { Color(hex: knobHex) }
    var text: Color { Color(hex: textHex) }
    var accent: Color { Color(hex: accentHex) }
}

extension Color {
    init(hex: String) {
        let value = UInt64(hex.replacingOccurrences(of: "#", with: ""), radix: 16) ?? 0
        self.init(
            red: Double((value >> 16) & 0xff) / 255,
            green: Double((value >> 8) & 0xff) / 255,
            blue: Double(value & 0xff) / 255
        )
    }
}
