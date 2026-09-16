# Germye's 3rdi Remote for Android

Android BLE-MIDI remote controller for **Germye's 3rdi Synth** in Ableton Live on macOS.

## Features
- Standard Bluetooth LE MIDI peripheral.
- 16 touch knobs on CC20–CC35.
- XY morph pad on CC36/CC37.
- 13-key MIDI keyboard on channel 1.
- Randomize, Center and Panic controls.

## Connect to macOS
1. Launch the app and press **START BLE-MIDI**.
2. Open **Audio MIDI Setup → MIDI Studio → Configure Bluetooth** on the Mac.
3. Connect to the Android phone's BLE-MIDI peripheral.
4. In Ableton Live **Settings → Link, Tempo & MIDI**, enable **Track** and **Remote** for that Bluetooth MIDI input.
5. Load **Germye's 3rdi Synth**.
6. Enter **MIDI Map Mode** (`Cmd-M`), click a synth parameter, then move the desired control on the phone.

The app sends standard MIDI CC and note data, so it can also control other Ableton devices and VST3 instruments.
