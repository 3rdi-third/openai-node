import Foundation
import AVFoundation

final class SynthEngine {
    var patch = SynthPatch()
    var isRunning = true
    var externalClockMode = false
    var onStep: ((Int) -> Void)?

    private let engine = AVAudioEngine()
    private var source: AVAudioSourceNode?
    private var sampleRate = 48_000.0

    private var phase1 = 0.0
    private var phase2 = 0.0
    private var lfoPhase = 0.0
    private var envAmp = 0.0
    private var envFilter = 0.0
    private var envPitch = 0.0
    private var currentVelocity = 1.0
    private var baseHz = 62.0
    private var z1 = 0.0
    private var z2 = 0.0
    private var z3 = 0.0
    private var z4 = 0.0
    private var step = 0
    private var samplesToNextStep = 0.0
    private var lastPostedStep = -1

    func configure(sampleRate requested: Double, bufferDuration: Double) {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? session.setPreferredSampleRate(requested)
        try? session.setPreferredIOBufferDuration(bufferDuration)
        try? session.setActive(true)
        sampleRate = session.sampleRate
    }

    func start() {
        guard source == nil else { return }

        let format = AVAudioFormat(
            standardFormatWithSampleRate: sampleRate,
            channels: 2
        )!

        let node = AVAudioSourceNode(format: format) { [weak self] _, _, frameCount, audioBufferList -> OSStatus in
            guard let self else { return noErr }
            let abl = UnsafeMutableAudioBufferListPointer(audioBufferList)
            self.render(abl: abl, frames: Int(frameCount))
            return noErr
        }

        source = node
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        engine.prepare()
        try? engine.start()
    }

    func externalTrigger(step index: Int) {
        trigger(index: index)
    }

    func resetSequence() {
        step = 0
        samplesToNextStep = 0
    }

    private func trigger(index: Int) {
        let i = index & 7
        guard patch.steps.indices.contains(i) else { return }
        let s = patch.steps[i]
        currentVelocity = max(0, min(1, s.velocity))
        baseHz = patch.vco1 * pow(2, s.pitch / 12)
        envAmp = 1
        envFilter = 1
        envPitch = 1
    }

    private func render(
        abl: UnsafeMutableAudioBufferListPointer,
        frames: Int
    ) {
        let p = patch
        let ampDecay = exp(
            -1 / (sampleRate * max(0.015, p.vcaDecay / 1000))
        )
        let filtDecay = exp(
            -1 / (sampleRate * max(0.015, p.vcfDecay / 1000))
        )

        for n in 0..<frames {
            if isRunning && !externalClockMode {
                if samplesToNextStep <= 0 {
                    let now = step
                    trigger(index: now)

                    if now != lastPostedStep {
                        lastPostedStep = now
                        DispatchQueue.main.async { [weak self] in
                            self?.onStep?(now)
                        }
                    }

                    step = (step + 1) & 7
                    samplesToNextStep +=
                        sampleRate * 60 / max(40, p.bpm) / 2
                }

                samplesToNextStep -= 1
            }

            lfoPhase += p.lfoRate / sampleRate
            lfoPhase -= floor(lfoPhase)

            let lfo = sin(lfoPhase * 2 * .pi)
            let pitchMod =
                p.lfoPitch * lfo + p.pitchEnv * envPitch

            let f2 = baseHz * pow(2, p.detune / 12)
            phase2 += f2 / sampleRate
            phase2 -= floor(phase2)

            let osc2 = phase2 < 0.5 ? 1.0 : -1.0
            let fmDepth = max(
                0,
                p.fm + p.lfoFM * lfo
            )

            var instHz =
                baseHz
                * pow(2, pitchMod / 12)
                * (1 + osc2 * fmDepth * 0.22)

            instHz = max(
                20,
                min(instHz, sampleRate * 0.2)
            )

            phase1 += instHz / sampleRate
            phase1 -= floor(phase1)

            let osc1 =
                2 * abs(2 * phase1 - 1) - 1

            let randomNoise =
                Double.random(in: -1...1) * p.noise

            let mix =
                osc1 * 0.68
                + osc2 * 0.42
                + randomNoise

            var cutoff =
                p.cutoff
                * pow(2, p.lfoVCF * lfo)
                * (1 + envFilter * 5.5 * p.envVCF)

            cutoff = max(
                60,
                min(cutoff, sampleRate * 0.42)
            )

            var sample = ladder(
                mix,
                cutoff: cutoff,
                resonance: p.resonance
            )

            sample *= envAmp * currentVelocity
            sample = tanh(sample * p.drive * 1.45)

            envAmp *= ampDecay
            envFilter *= filtDecay
            envPitch *= ampDecay

            let out = Float(sample * 0.65)

            for buffer in abl {
                if let ptr =
                    buffer.mData?.assumingMemoryBound(to: Float.self) {
                    ptr[n] = out
                }
            }
        }
    }

    private func ladder(
        _ input: Double,
        cutoff: Double,
        resonance: Double
    ) -> Double {
        var g =
            1 - exp(-2 * .pi * cutoff / sampleRate)

        g = max(0.001, min(0.78, g))

        let x =
            tanh(input - z4 * resonance * 4)

        z1 += g * (x - z1)
        z2 += g * (tanh(z1) - z2)
        z3 += g * (tanh(z2) - z3)
        z4 += g * (tanh(z3) - z4)

        return z4
    }
}
