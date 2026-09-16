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

main.write_text(s)
print("Applied v2.2 BLE MIDI synth integration")
