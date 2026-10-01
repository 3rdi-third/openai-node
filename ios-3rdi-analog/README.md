# 3rdi Analog Percussion — iOS

Native iOS port of 3rdi Analog Percussion.

## Included
- SwiftUI interface
- AVAudioEngine real-time dual-oscillator percussion synth
- FM, noise, resonant 4-stage filter, envelopes, drive and LFO modulation
- 8-step pitch/velocity sequencer
- typed BPM control
- 8 preset slots
- colour themes
- sample-rate and audio-buffer settings
- BLE-MIDI master/slave clock sync
- 48-clock slave PLL and direct 12-clock sequencer phase lock

## Build on a Mac
1. Install Xcode.
2. Install XcodeGen with Homebrew: `brew install xcodegen`
3. Run `xcodegen generate` inside this folder.
4. Open `AnalogPercussion.xcodeproj`.
5. Select your Apple Developer Team to sign for a physical iPhone.

The GitHub workflow also creates:
- a simulator build
- an unsigned iPhone device .ipa for signing/repackaging
- a complete Xcode source zip

A normal iPhone cannot install the unsigned IPA until it is signed with an Apple Developer certificate/profile.
