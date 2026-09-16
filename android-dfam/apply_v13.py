from pathlib import Path

root = Path(__file__).resolve().parent
main = root / "app/src/main/java/com/threerdi/dfamstyle/MainActivity.java"
settings = root / "app/src/main/java/com/threerdi/dfamstyle/SettingsActivity.java"

s = main.read_text()

s = s.replace('        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        applyScreenPreference();','        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        UiUtil.applyImmersive(this);\n        applyScreenPreference();')
s = s.replace('        super.onResume();\n        applyScreenPreference();\n        if (synthView != null) synthView.startAudio();','        super.onResume();\n        UiUtil.applyImmersive(this);\n        applyScreenPreference();\n        if (synthView != null) { synthView.reloadTheme(); synthView.startAudio(); }')
s = s.replace('    @Override\n    protected void onPause() {','    @Override\n    public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) UiUtil.applyImmersive(this); }\n\n    @Override\n    protected void onPause() {')
s = s.replace('        private static final int SAND = Color.rgb(218, 198, 165);\n        private static final int SAND_LIGHT = Color.rgb(229, 213, 188);\n        private static final int SAND_DARK = Color.rgb(188, 164, 128);\n        private static final int ORANGE = Color.rgb(224, 111, 36);\n        private static final int ORANGE_DARK = Color.rgb(187, 79, 22);\n        private static final int BLACK = Color.rgb(18, 18, 16);','        private int SAND; private int SAND_LIGHT; private int SAND_DARK; private int ORANGE; private int ORANGE_DARK; private int BLACK;')
s = s.replace('        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF prefsRect = new RectF(1012, 58, 1140, 101);','        private final RectF tempoRect = new RectF(58, 143, 160, 199);\n        private final RectF presetRect = new RectF(704, 58, 838, 101);\n        private final RectF colorsRect = new RectF(848, 58, 982, 101);\n        private final RectF prefsRect = new RectF(992, 58, 1140, 101);')
s = s.replace('        SynthView(Context context) {\n            super(context);\n            tempo = AudioPreferences.defaultBpm(context);\n            setBackgroundColor(SAND_DARK);\n            setFocusable(true);\n        }','        SynthView(Context context) { super(context); tempo = AudioPreferences.defaultBpm(context); reloadTheme(); setFocusable(true); }\n\n        void reloadTheme() { SAND=ThemePreferences.panelColor(getContext()); SAND_LIGHT=ThemePreferences.lighten(SAND,0.10f); SAND_DARK=ThemePreferences.darken(SAND,0.16f); ORANGE=ThemePreferences.knobColor(getContext()); ORANGE_DARK=ThemePreferences.darken(ORANGE,0.18f); BLACK=ThemePreferences.textColor(getContext()); setBackgroundColor(SAND); invalidate(); }')
s = s.replace('            super.onDraw(canvas);\n            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);','            super.onDraw(canvas);\n            canvas.drawColor(SAND);\n            canvasScale = Math.min(getWidth() / DW, getHeight() / DH);')
s = s.replace('            p.setStyle(Paint.Style.FILL);\n            p.setColor(SAND_DARK);\n            canvas.drawRoundRect(new RectF(14, 14, 1186, 706), 22, 22, p);\n            p.setColor(SAND);\n            canvas.drawRoundRect(new RectF(28, 28, 1172, 692), 17, 17, p);\n            p.setColor(SAND_LIGHT);\n            canvas.drawRoundRect(new RectF(45, 45, 1155, 675), 12, 12, p);','            p.setStyle(Paint.Style.FILL); p.setColor(SAND); canvas.drawRect(0,0,DW,DH,p); p.setColor(SAND_LIGHT); canvas.drawRoundRect(new RectF(24,24,1176,696),16,16,p);')
s = s.replace('            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c, prefsRect, "PREFERENCES", false);','            c.drawText("DUAL OSCILLATOR / FILTER / MODULATION / 8-STEP PERCUSSION SYNTH", 68, 111, p);\n            drawButton(c,presetRect,"PRESETS",false); drawButton(c,colorsRect,"COLOURS",false); drawButton(c,prefsRect,"AUDIO",false);')
s = s.replace('                if (prefsRect.contains(x, y)) {\n                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));\n                    return true;\n                }','                if (presetRect.contains(x,y)) { showPresetMenu(); return true; }\n                if (colorsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),ThemeActivity.class)); return true; }\n                if (prefsRect.contains(x,y)) { getContext().startActivity(new Intent(getContext(),SettingsActivity.class)); return true; }')

methods = r'''
        private void showPresetMenu() {
            String[] items={"SAVE CURRENT PRESET","LOAD PRESET"};
            new AlertDialog.Builder(getContext()).setTitle("Presets").setItems(items,(d,w)->choosePresetSlot(w==0)).setNegativeButton("Cancel",null).show();
        }
        private void choosePresetSlot(boolean save) {
            String[] slots=new String[8]; android.content.SharedPreferences sp=getContext().getSharedPreferences("synth_presets",Context.MODE_PRIVATE);
            for(int i=0;i<8;i++) slots[i]="Slot "+(i+1)+"  •  "+sp.getString("name_"+i,"Empty");
            new AlertDialog.Builder(getContext()).setTitle(save?"Save preset to slot":"Load preset").setItems(slots,(d,slot)->{if(save)askPresetName(slot);else loadPreset(slot);}).setNegativeButton("Cancel",null).show();
        }
        private void askPresetName(int slot) {
            final EditText edit=new EditText(getContext()); edit.setSingleLine(true); edit.setHint("Preset name"); edit.setText("Preset "+(slot+1)); edit.setSelectAllOnFocus(true);
            new AlertDialog.Builder(getContext()).setTitle("Name preset").setView(edit).setNegativeButton("Cancel",null).setPositiveButton("SAVE",(d,w)->{String name=edit.getText().toString().trim();if(name.length()==0)name="Preset "+(slot+1);savePreset(slot,name);}).show();
        }
        private void savePreset(int slot,String name) {
            StringBuilder data=new StringBuilder(); float[] values={tempo,vco1,detune,fm,noise,cutoff,resonance,vcfDecay,vcaDecay,drive,lfoRate,lfoPitch,lfoFilter,lfoFm,filterEnvAmount,pitchEnvAmount};
            for(float v:values)data.append(v).append(','); for(float v:stepPitch)data.append(v).append(','); for(float v:stepVelocity)data.append(v).append(','); data.append(running?1:0);
            getContext().getSharedPreferences("synth_presets",Context.MODE_PRIVATE).edit().putString("name_"+slot,name).putString("data_"+slot,data.toString()).apply();
            android.widget.Toast.makeText(getContext(),"Saved: "+name,android.widget.Toast.LENGTH_SHORT).show();
        }
        private void loadPreset(int slot) {
            android.content.SharedPreferences sp=getContext().getSharedPreferences("synth_presets",Context.MODE_PRIVATE); String data=sp.getString("data_"+slot,null);
            if(data==null){android.widget.Toast.makeText(getContext(),"Preset slot is empty",android.widget.Toast.LENGTH_SHORT).show();return;}
            try { String[] a=data.split(","); int n=0; tempo=Float.parseFloat(a[n++]);vco1=Float.parseFloat(a[n++]);detune=Float.parseFloat(a[n++]);fm=Float.parseFloat(a[n++]);noise=Float.parseFloat(a[n++]);cutoff=Float.parseFloat(a[n++]);resonance=Float.parseFloat(a[n++]);vcfDecay=Float.parseFloat(a[n++]);vcaDecay=Float.parseFloat(a[n++]);drive=Float.parseFloat(a[n++]);lfoRate=Float.parseFloat(a[n++]);lfoPitch=Float.parseFloat(a[n++]);lfoFilter=Float.parseFloat(a[n++]);lfoFm=Float.parseFloat(a[n++]);filterEnvAmount=Float.parseFloat(a[n++]);pitchEnvAmount=Float.parseFloat(a[n++]);for(int i=0;i<8;i++)stepPitch[i]=Float.parseFloat(a[n++]);for(int i=0;i<8;i++)stepVelocity[i]=Float.parseFloat(a[n++]);if(n<a.length)running=Integer.parseInt(a[n])!=0; invalidate(); String name=sp.getString("name_"+slot,"Preset "+(slot+1));android.widget.Toast.makeText(getContext(),"Loaded: "+name,android.widget.Toast.LENGTH_SHORT).show(); } catch(Exception ex){android.widget.Toast.makeText(getContext(),"Could not load preset",android.widget.Toast.LENGTH_SHORT).show();}
        }

'''
s=s.replace('        private void drawStep(Canvas c, int index, float x, boolean active) {',methods+'        private void drawStep(Canvas c, int index, float x, boolean active) {')
main.write_text(s)
q=settings.read_text();q=q.replace('        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);','        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n        UiUtil.applyImmersive(this);',1);q=q.replace('    private void loadValues() {','    @Override\n    public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)UiUtil.applyImmersive(this);}\n\n    private void loadValues() {',1);settings.write_text(q)
print("Applied fullscreen/theme/preset patch")
