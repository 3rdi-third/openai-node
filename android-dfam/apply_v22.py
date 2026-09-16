from pathlib import Path

root = Path(__file__).resolve().parent
main = root / "app/src/main/java/com/threerdi/dfamstyle/MainActivity.java"
s = main.read_text()

old_rects = '''        private final RectF presetRect = new RectF(704, 58, 838, 101);
        private final RectF colorsRect = new RectF(848, 58, 982, 101);
        private final RectF prefsRect = new RectF(992, 58, 1140, 101);'''
new_rects = '''        private final RectF midiRect = new RectF(560, 58, 690, 101);
        private final RectF presetRect = new RectF(700, 58, 830, 101);
        private final RectF colorsRect = new RectF(840, 58, 970, 101);
        private final RectF prefsRect = new RectF(980, 58, 1140, 101);'''
if old_rects not in s:
    raise RuntimeError("Could not find v2.1 top-button rectangles in MainActivity")
s = s.replace(old_rects, new_rects, 1)

old_draw = '            drawButton(c,presetRect,"PRESETS",false); drawButton(c,colorsRect,"COLOURS",false); drawButton(c,prefsRect,"AUDIO",false);'
new_draw = '            BleMidiSyncManager midi=BleMidiSyncManager.get(getContext()); drawButton(c,midiRect,midi.getMode()==BleMidiSyncManager.MODE_OFF?"MIDI":"MIDI ON",midi.getMode()!=BleMidiSyncManager.MODE_OFF); drawButton(c,presetRect,"PRESETS",false); drawButton(c,colorsRect,"COLOURS",false); drawButton(c,prefsRect,"AUDIO",false);'
if old_draw not in s:
    raise RuntimeError("Could not find v2.1 top-button drawing in MainActivity")
s = s.replace(old_draw, new_draw, 1)

old_touch = '                if (presetRect.contains(x,y)) { showPresetMenu(); return true; }\n                if (colorsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),ThemeActivity.class)); return true; }\n                if (prefsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),SettingsActivity.class)); return true; }'
new_touch = '                if (midiRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),BluetoothMidiActivity.class)); return true; }\n                if (presetRect.contains(x,y)) { showPresetMenu(); return true; }\n                if (colorsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),ThemeActivity.class)); return true; }\n                if (prefsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),SettingsActivity.class)); return true; }'
if old_touch not in s:
    raise RuntimeError("Could not find v2.1 top-button touch handling in MainActivity")
s = s.replace(old_touch, new_touch, 1)

old_canvas = '            canvas.drawColor(SAND);\n            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);'
new_canvas = '''            canvas.drawColor(SAND);
            BleMidiSyncManager midiSync = BleMidiSyncManager.get(getContext());
            if (midiSync.isSlaveActive() && midiSync.hasIncomingClock()) {
                float incoming = midiSync.getIncomingBpm();
                if (incoming >= 20f && incoming <= 300f) tempo = incoming;
                running = midiSync.isIncomingRunning();
            } else if (midiSync.isMasterActive()) {
                midiSync.setMasterState(tempo, running);
            }
            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);'''
if old_canvas not in s:
    raise RuntimeError("Could not find v2.1 onDraw entry in MainActivity")
s = s.replace(old_canvas, new_canvas, 1)

old_engine_fields = '''        private int step = 0;

        SynthEngine(SynthView synthView) {'''
new_engine_fields = '''        private int step = 0;
        private long lastExternalClockCount = -1L;
        private long lastExternalStepBoundary = -1L;
        private boolean lastExternalRunning = false;

        SynthEngine(SynthView synthView) {'''
if old_engine_fields not in s:
    raise RuntimeError("Could not find SynthEngine step field")
s = s.replace(old_engine_fields, new_engine_fields, 1)

old_render_head = '''        private void render(short[] out) {
            float ampDecay = decayCoeff(s.vcaDecay);
            float filtDecay = decayCoeff(s.vcfDecay);

            for (int i = 0; i < out.length; i++) {'''
new_render_head = '''        private void render(short[] out) {
            float ampDecay = decayCoeff(s.vcaDecay);
            float filtDecay = decayCoeff(s.vcfDecay);

            BleMidiSyncManager midiClock = BleMidiSyncManager.get(s.getContext());
            boolean externalClockMode = midiClock.isSlaveActive() && midiClock.hasIncomingClock();
            boolean externalRunning = externalClockMode && midiClock.isIncomingRunning();
            long externalClockCount = externalClockMode ? midiClock.getIncomingClockCount() : -1L;

            if (externalClockMode) {
                float externalBpm = midiClock.getIncomingBpm();
                if (externalBpm >= 20f && externalBpm <= 300f) s.tempo = externalBpm;
                s.running = externalRunning;

                if (externalRunning && !lastExternalRunning) {
                    // MIDI Start: fire step 1 immediately and establish phase zero.
                    step = 0;
                    currentStep = 0;
                    trigger(0);
                    step = 1;
                    samplesToNextStep = 0.0;
                    lastExternalStepBoundary = externalClockCount / 12L;
                    lastExternalClockCount = externalClockCount;
                } else if (externalRunning) {
                    long boundary = externalClockCount / 12L;
                    if (lastExternalStepBoundary < 0L) lastExternalStepBoundary = boundary;
                    if (boundary > lastExternalStepBoundary) {
                        long stepsDue = Math.min(8L, boundary - lastExternalStepBoundary);
                        for (long n = 0; n < stepsDue; n++) {
                            currentStep = step;
                            if (n == stepsDue - 1L) trigger(step);
                            step = (step + 1) & 7;
                        }
                        lastExternalStepBoundary = boundary;
                    }
                    lastExternalClockCount = externalClockCount;
                } else if (lastExternalRunning) {
                    currentStep = -1;
                }
                lastExternalRunning = externalRunning;
            } else {
                lastExternalRunning = false;
                lastExternalClockCount = -1L;
                lastExternalStepBoundary = -1L;
            }

            for (int i = 0; i < out.length; i++) {'''
if old_render_head not in s:
    raise RuntimeError("Could not find SynthEngine render head")
s = s.replace(old_render_head, new_render_head, 1)

old_internal_clock = '''                if (s.running) {
                    if (samplesToNextStep <= 0.0) {
                        currentStep = step;
                        trigger(step);
                        step = (step + 1) & 7;
                        double bpm = Math.max(40.0, Math.min(300.0, s.tempo));
                        samplesToNextStep += sampleRate * 60.0 / bpm / 2.0;
                    }
                    samplesToNextStep -= 1.0;
                }
'''
new_internal_clock = '''                if (s.running && !externalClockMode) {
                    if (samplesToNextStep <= 0.0) {
                        currentStep = step;
                        trigger(step);
                        step = (step + 1) & 7;
                        double bpm = Math.max(40.0, Math.min(300.0, s.tempo));
                        samplesToNextStep += sampleRate * 60.0 / bpm / 2.0;
                    }
                    samplesToNextStep -= 1.0;
                }
'''
if old_internal_clock not in s:
    raise RuntimeError("Could not find internal sequencer timing block")
s = s.replace(old_internal_clock, new_internal_clock, 1)

main.write_text(s)
print("Applied v2.6 BLE MIDI timestamp + phase-lock synth integration")
