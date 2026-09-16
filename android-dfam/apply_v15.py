from pathlib import Path

root = Path(__file__).resolve().parent
main = root / "app/src/main/java/com/threerdi/dfamstyle/MainActivity.java"

s = main.read_text()

s = s.replace(
'''        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF colorsRect = new RectF(850, 58, 982, 101);\n        private final RectF prefsRect = new RectF(992, 58, 1140, 101);''',
'''        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF presetRect = new RectF(700, 58, 840, 101);\n        private final RectF colorsRect = new RectF(850, 58, 982, 101);\n        private final RectF prefsRect = new RectF(992, 58, 1140, 101);''')

s = s.replace(
'''            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, colorsRect, "COLOURS", false);\n            drawButton(c, prefsRect, "AUDIO", false);''',
'''            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, presetRect, "PRESETS", false);\n            drawButton(c, colorsRect, "COLOURS", false);\n            drawButton(c, prefsRect, "AUDIO", false);''')

s = s.replace(
'''                if (colorsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), ThemeActivity.class));\n                    return true;\n                }''',
'''                if (presetRect.contains(x, y)) {\n                    showPresetMenu();\n                    return true;\n                }\n                if (colorsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), ThemeActivity.class));\n                    return true;\n                }''')

preset_methods = r'''
        private void showPresetMenu() {
            String[] actions = {"SAVE CURRENT PRESET", "LOAD PRESET", "DELETE PRESET"};
            new AlertDialog.Builder(getContext())
                    .setTitle("Synth Presets")
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) showSavePresetDialog();
                        else if (which == 1) showLoadPresetDialog();
                        else showDeletePresetDialog();
                    })
                    .setNegativeButton("Close", null)
                    .show();
        }

        private void showSavePresetDialog() {
            final EditText edit = new EditText(getContext());
            edit.setSingleLine(true);
            edit.setHint("Preset name");
            edit.setSelectAllOnFocus(true);

            final AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle("Save Preset")
                    .setMessage("Stores the complete synth patch and 8-step sequence.")
                    .setView(edit)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", null)
                    .create();

            dialog.setOnShowListener(ignored -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = edit.getText().toString().trim();
                    if (name.isEmpty()) {
                        edit.setError("Enter a preset name");
                        return;
                    }
                    if (PresetStore.exists(getContext(), name)) {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Overwrite preset?")
                                .setMessage("A preset named “" + name + "” already exists.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Overwrite", (d, w) -> {
                                    PresetStore.save(getContext(), name, this);
                                    android.widget.Toast.makeText(getContext(), "Preset saved: " + name, android.widget.Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                })
                                .show();
                    } else {
                        PresetStore.save(getContext(), name, this);
                        android.widget.Toast.makeText(getContext(), "Preset saved: " + name, android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
                edit.requestFocus();
                edit.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                }, 120);
            });
            dialog.show();
        }

        private void showLoadPresetDialog() {
            java.util.List<String> names = PresetStore.names(getContext());
            if (names.isEmpty()) {
                android.widget.Toast.makeText(getContext(), "No saved presets", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(getContext())
                    .setTitle("Load Preset")
                    .setItems(items, (dialog, which) -> {
                        String name = items[which];
                        if (PresetStore.load(getContext(), name, this)) {
                            android.widget.Toast.makeText(getContext(), "Loaded: " + name, android.widget.Toast.LENGTH_SHORT).show();
                            invalidate();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showDeletePresetDialog() {
            java.util.List<String> names = PresetStore.names(getContext());
            if (names.isEmpty()) {
                android.widget.Toast.makeText(getContext(), "No saved presets", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(getContext())
                    .setTitle("Delete Preset")
                    .setItems(items, (dialog, which) -> {
                        String name = items[which];
                        new AlertDialog.Builder(getContext())
                                .setTitle("Delete “" + name + "”?")
                                .setMessage("This cannot be undone.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> {
                                    PresetStore.delete(getContext(), name);
                                    android.widget.Toast.makeText(getContext(), "Deleted: " + name, android.widget.Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

'''

marker = '        private void drawStep(Canvas c, int index, float x, boolean active) {'
if preset_methods not in s:
    s = s.replace(marker, preset_methods + marker)

main.write_text(s)
print("Applied v1.5 named preset save/load/delete patch")
