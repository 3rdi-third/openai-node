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
'''        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF colorsRect = new RectF(850, 58, 982, 101);\n        private final RectF prefsRect = new RectF(992, 58, 1140, 101);''')

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
'''            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, colorsRect, "COLOURS", false);\n            drawButton(c, prefsRect, "AUDIO", false);''')

s = s.replace(
'''                if (prefsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));\n                    return true;\n                }''',
'''                if (colorsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), ThemeActivity.class));\n                    return true;\n                }\n                if (prefsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));\n                    return true;\n                }''')

main.write_text(s)

q = settings.read_text()
q = q.replace(
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);''',
'''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        UiUtil.applyImmersive(this);''', 1)
q = q.replace(
'''    private void loadValues() {''',
'''    @Override\n    public void onWindowFocusChanged(boolean hasFocus) {\n        super.onWindowFocusChanged(hasFocus);\n        if (hasFocus) UiUtil.applyImmersive(this);\n    }\n\n    private void loadValues() {''', 1)
settings.write_text(q)

print("Applied v1.3 fullscreen/theme patch")
