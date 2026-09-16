from pathlib import Path

root = Path(__file__).resolve().parent
main = root / "app/src/main/java/com/threerdi/dfamstyle/MainActivity.java"
settings = root / "app/src/main/java/com/threerdi/dfamstyle/SettingsActivity.java"

s = main.read_text()

s = s.replace(
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        applyScreenPreference();''',
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        UiUtil.applyImmersive(this);\n        applyScreenPreference();''')

s = s.replace(
'''        super.onResume();\n        applyScreenPreference();\n        if (synthView != null) synthView.startAudio();''',
'''        super.onResume();\n        UiUtil.applyImmersive(this);\n        applyScreenPreference();\n        if (synthView != null) {\n            synthView.reloadTheme();\n            synthView.startAudio();\n        }''')

s = s.replace(
'''    @Override\n    protected void onPause() {''',
'''    @Override\n    public void onWindowFocusChanged(boolean hasFocus) {\n        super.onWindowFocusChanged(hasFocus);\n        if (hasFocus) UiUtil.applyImmersive(this);\n    }\n\n    @Override\n    protected void onPause() {''')

s = s.replace(
'''        private static final int SAND = Color.rgb(218, 198, 165);\n        private static final int SAND_LIGHT = Color.rgb(229, 213, 188);\n        private static final int SAND_DARK = Color.rgb(188, 164, 128);\n        private static final int ORANGE = Color.rgb(224, 111, 36);\n        private static final int ORANGE_DARK = Color.rgb(187, 79, 22);\n        private static final int BLACK = Color.rgb(18, 18, 16);''',
'''        private int SAND;\n        private int SAND_LIGHT;\n        private int SAND_DARK;\n        private int ORANGE;\n        private int ORANGE_DARK;\n        private int BLACK;''')

s = s.replace(
'''        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF prefsRect = new RectF(1012, 58, 1140, 101);''',
'''        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF presetRect = new RectF(704, 58, 838, 101);\n        private final RectF colorsRect = new RectF(848, 58, 982, 101);\n        private final RectF prefsRect = new RectF(992, 58, 1140, 101);''')

s = s.replace(
'''        SynthView(Context context) {\n            super(context);\n            tempo = AudioPreferences.defaultBpm(context);\n            setBackgroundColor(SAND_DARK);\n            setFocusable(true);\n        }''',
'''        SynthView(Context context) {\n            super(context);\n            tempo = AudioPreferences.defaultBpm(context);\n            reloadTheme();\n            setFocusable(true);\n        }\n\n        void reloadTheme() {\n            SAND = ThemePreferences.panelColor(getContext());\n            SAND_LIGHT = ThemePreferences.lighten(SAND, 0.10f);\n            SAND_DARK = ThemePreferences.darken(SAND, 0.16f);\n            ORANGE = ThemePreferences.knobColor(getContext());\n            ORANGE_DARK = ThemePreferences.darken(ORANGE, 0.18f);\n            BLACK = ThemePreferences.textColor(getContext());\n            setBackgroundColor(SAND);\n            invalidate();\n        }''')

s = s.replace(
'''            super.onDraw(canvas);\n            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);''',
'''            super.onDraw(canvas);\n            canvas.drawColor(SAND);\n            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);''')

s = s.replace(
'''            p.setStyle(Paint.Style.FILL);\n            p.setColor(SAND_DARK);\n            canvas.drawRoundRect(new RectF(14, 14, 1186, 706), 22, 22, p);\n            p.setColor(SAND);\n            canvas.drawRoundRect(new RectF(28, 28, 1172, 692), 17, 17, p);\n            p.setColor(SAND_LIGHT);\n            canvas.drawRoundRect(new RectF(45, 45, 1155, 675), 12, 12, p);''',
'''            p.setStyle(Paint.Style.FILL);\n            p.setColor(SAND);\n            canvas.drawRect(0, 0, DW, DH, p);\n            p.setColor(SAND_LIGHT);\n            canvas.drawRoundRect(new RectF(24, 24, 1176, 696), 16, 16, p);''')

s = s.replace(
'''            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, prefsRect, "PREFERENCES", false);''',
'''            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, presetRect, "PRESETS", false);\n            drawButton(c, colorsRect, "COLOURS", false);\n            drawButton(c, prefsRect, "AUDIO", false);''')

s = s.replace(
'''                if (prefsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));\n                    return true;\n                }''',
'''                if (presetRect.contains(x, y)) {\n                    showPresetMenu();\n                    return true;\n                }\n                if (colorsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), ThemeActivity.class));\n                    return true;\n                }\n                if (prefsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));\n                    return true;\n                }''')

s = s.replace(
'''        private void drawStep(Canvas c, int index, float x, boolean active) {''',
'''        private void showPresetMenu() {\n            String[] items = {"SAVE CURRENT PRESET", "LOAD PRESET"};\n            new AlertDialog.Builder(getContext())\n                    .setTitle("Presets")\n                    .setItems(items, (dialog, which) -> {\n                        if (which == 0) choosePresetSlot(true);\n                        else choosePresetSlot(false);\n                    })\n                    .setNegativeButton("Cancel", null)\n                    .show();\n        }\n\n        private void choosePresetSlot(boolean save) {\n            String[] slots = new String[8];\n            android.content.SharedPreferences sp = getContext().getSharedPreferences("synth_presets", Context.MODE_PRIVATE);\n            for (int i = 0; i < 8; i++) {\n                String name = sp.getString("name_" + i, "Empty");\n                slots[i] = "Slot " + (i + 1) + "  •  " + name;\n            }\n            new AlertDialog.Builder(getContext())\n                    .setTitle(save ? "Save preset to slot" : "Load preset")\n                    .setItems(slots, (dialog, slot) -> {\n                        if (save) askPresetName(slot);\n                        else loadPreset(slot);\n                    })\n                    .setNegativeButton("Cancel", null)\n                    .show();\n        }\n\n        private void askPresetName(int slot) {\n            final EditText edit = new EditText(getContext());\n            edit.setSingleLine(true);\n            edit.setHint("Preset name");\n            edit.setText("Preset " + (slot + 1));\n            edit.setSelectAllOnFocus(true);\n            new AlertDialog.Builder(getContext())\n                    .setTitle("Name preset")\n                    .setView(edit)\n                    .setNegativeButton("Cancel", null)\n                    .setPositiveButton("SAVE", (d, w) -> {\n                        String name = edit.getText().toString().trim();\n                        if (name.length() == 0) name = "Preset " + (slot + 1);\n                        savePreset(slot, name);\n                    })\n                    .show();\n        }\n\n        private void savePreset(int slot, String name) {\n            StringBuilder data = new StringBuilder();\n            float[] values = {tempo, vco1, detune, fm, noise, cutoff, resonance, vcfDecay, vcaDecay, drive,\n                    lfoRate, lfoPitch, lfoFilter, lfoFm, filterEnvAmount, pitchEnvAmount};\n            for (float v : values) data.append(v).append(',');\n            for (float v : stepPitch) data.append(v).append(',');\n            for (float v : stepVelocity) data.append(v).append(',');\n            data.append(running ? 1 : 0);\n            getContext().getSharedPreferences("synth_presets", Context.MODE_PRIVATE).edit()\n                    .putString("name_" + slot, name)\n                    .putString("data_" + slot, data.toString())\n                    .apply();\n            android.widget.Toast.makeText(getContext(), "Saved: " + name, android.widget.Toast.LENGTH_SHORT).show();\n        }\n\n        private void loadPreset(int slot) {\n            android.content.SharedPreferences sp = getContext().getSharedPreferences("synth_presets", Context.MODE_PRIVATE);\n            String data = sp.getString("data_" + slot, null);\n            if (data == null) {\n                android.widget.Toast.makeText(getContext(), "Preset slot is empty", android.widget.Toast.LENGTH_SHORT).show();\n                return;\n            }\n            try {\n                String[] a = data.split(",");\n                int n = 0;\n                tempo = Float.parseFloat(a[n++]);\n                vco1 = Float.parseFloat(a[n++]);\n                detune = Float.parseFloat(a[n++]);\n                fm = Float.parseFloat(a[n++]);\n                noise = Float.parseFloat(a[n++]);\n                cutoff = Float.parseFloat(a[n++]);\n                resonance = Float.parseFloat(a[n++]);\n                vcfDecay = Float.parseFloat(a[n++]);\n                vcaDecay = Float.parseFloat(a[n++]);\n                drive = Float.parseFloat(a[n++]);\n                lfoRate = Float.parseFloat(a[n++]);\n                lfoPitch = Float.parseFloat(a[n++]);\n                lfoFilter = Float.parseFloat(a[n++]);\n                lfoFm = Float.parseFloat(a[n++]);\n                filterEnvAmount = Float.parseFloat(a[n++]);\n                pitchEnvAmount = Float.parseFloat(a[n++]);\n                for (int i = 0; i < 8; i++) stepPitch[i] = Float.parseFloat(a[n++]);\n                for (int i = 0; i < 8; i++) stepVelocity[i] = Float.parseFloat(a[n++]);\n                if (n < a.length) running = Integer.parseInt(a[n]) != 0;\n                resetTransport();\n                invalidate();\n                String name = sp.getString("name_" + slot, "Preset " + (slot + 1));\n                android.widget.Toast.makeText(getContext(), "Loaded: " + name, android.widget.Toast.LENGTH_SHORT).show();\n            } catch (Exception ex) {\n                android.widget.Toast.makeText(getContext(), "Could not load preset", android.widget.Toast.LENGTH_SHORT).show();\n            }\n        }\n\n        private void drawStep(Canvas c, int index, float x, boolean active) {''')

main.write_text(s)

q = settings.read_text()
q = q.replace(
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);''',
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        UiUtil.applyImmersive(this);''', 1)
q = q.replace(
'''    private void loadValues() {''',
'''    @Override\n    public void onWindowFocusChanged(boolean hasFocus) {\n        super.onWindowFocusChanged(hasFocus);\n        if (hasFocus) UiUtil.applyImmersive(this);\n    }\n\n    private void loadValues() {''', 1)
settings.write_text(q)

print("Applied fullscreen/theme/preset patch")
