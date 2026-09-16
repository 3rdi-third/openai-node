package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ThemeActivity extends Activity {
    private static final int PANEL = 0;
    private static final int KNOBS = 1;
    private static final int TEXT = 2;

    private LinearLayout root;
    private TextView preview;
    private TextView panelSwatch;
    private TextView knobSwatch;
    private TextView textSwatch;
    private Button panelTab;
    private Button knobsTab;
    private Button textTab;
    private Button saveButton;
    private Button backButton;
    private Button resetButton;
    private ColorSlider hueSlider;
    private ColorSlider saturationSlider;
    private ColorSlider brightnessSlider;

    private int panelColor;
    private int knobColor;
    private int textColor;
    private int selectedTarget = PANEL;
    private final float[] hsv = new float[]{30f, 0.24f, 0.85f};

    private static final int[] PALETTE = new int[]{
            Color.rgb(250,250,248), Color.rgb(241,233,216), Color.rgb(218,198,165), Color.rgb(196,164,122), Color.rgb(142,104,66),
            Color.rgb(64,57,49), Color.rgb(30,30,28), Color.rgb(8,8,9), Color.rgb(94,98,102), Color.rgb(54,63,69),
            Color.rgb(190,27,40), Color.rgb(232,45,58), Color.rgb(224,111,36), Color.rgb(245,145,30), Color.rgb(255,190,39),
            Color.rgb(244,224,66), Color.rgb(185,230,52), Color.rgb(114,214,55), Color.rgb(31,179,89), Color.rgb(38,216,157),
            Color.rgb(28,190,184), Color.rgb(30,205,232), Color.rgb(46,160,232), Color.rgb(46,96,212), Color.rgb(25,47,126),
            Color.rgb(73,48,168), Color.rgb(114,57,192), Color.rgb(159,66,211), Color.rgb(219,57,179), Color.rgb(242,92,151),
            Color.rgb(255,84,109), Color.rgb(167,255,35), Color.rgb(255,70,20), Color.rgb(0,240,210), Color.rgb(120,92,255),
            Color.rgb(202,122,55), Color.rgb(164,113,72), Color.rgb(117,129,139), Color.rgb(82,94,104), Color.rgb(190,198,205)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        UiUtil.applyImmersive(this);

        panelColor = ThemePreferences.panelColor(this);
        knobColor = ThemePreferences.knobColor(this);
        textColor = ThemePreferences.textColor(this);

        buildUi();
        selectTarget(PANEL);
        refreshUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(12), dp(24), dp(16));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = makeText("SYNTH COLOURS", 25, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView subtitle = makeText("Choose PANEL, KNOBS or TEXT. Use the palette for instant colours or fine-tune with HUE / SATURATION / BRIGHTNESS.", 10.5f, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setAlpha(0.75f);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(46)));

        panelTab = tabButton("PANEL");
        knobsTab = tabButton("KNOBS");
        textTab = tabButton("TEXT");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(145), dp(38));
        tp.setMargins(dp(5), 0, dp(5), 0);
        tabs.addView(panelTab, tp);
        tabs.addView(knobsTab, tp);
        tabs.addView(textTab, tp);
        panelTab.setOnClickListener(v -> selectTarget(PANEL));
        knobsTab.setOnClickListener(v -> selectTarget(KNOBS));
        textTab.setOnClickListener(v -> selectTarget(TEXT));

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, dp(74));
        prp.setMargins(0, dp(2), 0, dp(5));
        root.addView(previewRow, prp);

        preview = new TextView(this);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(13);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(190), dp(62));
        pp.setMargins(dp(4), 0, dp(16), 0);
        previewRow.addView(preview, pp);

        panelSwatch = swatch("PANEL");
        knobSwatch = swatch("KNOBS");
        textSwatch = swatch("TEXT");
        LinearLayout.LayoutParams sw = new LinearLayout.LayoutParams(dp(88), dp(52));
        sw.setMargins(dp(4), 0, dp(4), 0);
        previewRow.addView(panelSwatch, sw);
        previewRow.addView(knobSwatch, sw);
        previewRow.addView(textSwatch, sw);

        TextView fineTitle = makeText("FINE COLOUR CONTROL", 11, true);
        fineTitle.setGravity(Gravity.CENTER);
        root.addView(fineTitle, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout sliders = new LinearLayout(this);
        sliders.setOrientation(LinearLayout.VERTICAL);
        root.addView(sliders, new LinearLayout.LayoutParams(-1, dp(178)));

        sliders.addView(sliderLabel("HUE"));
        hueSlider = new ColorSlider(this, ColorSlider.HUE);
        sliders.addView(hueSlider, sliderParams());
        sliders.addView(sliderLabel("SATURATION"));
        saturationSlider = new ColorSlider(this, ColorSlider.SATURATION);
        sliders.addView(saturationSlider, sliderParams());
        sliders.addView(sliderLabel("BRIGHTNESS"));
        brightnessSlider = new ColorSlider(this, ColorSlider.BRIGHTNESS);
        sliders.addView(brightnessSlider, sliderParams());

        ColorSlider.Listener listener = (h, s, v) -> {
            hsv[0] = h; hsv[1] = s; hsv[2] = v;
            applySelectedColour(Color.HSVToColor(hsv), false);
        };
        hueSlider.setListener(listener);
        saturationSlider.setListener(listener);
        brightnessSlider.setListener(listener);

        TextView paletteTitle = makeText("QUICK COLOUR PALETTE • 40 COLOURS", 11, true);
        paletteTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(-1, dp(26));
        ptl.setMargins(0, dp(3), 0, 0);
        root.addView(paletteTitle, ptl);

        GridLayout palette = new GridLayout(this);
        palette.setColumnCount(10);
        palette.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        palette.setUseDefaultMargins(false);
        root.addView(palette, new LinearLayout.LayoutParams(-1, dp(104)));
        for (int color : PALETTE) addPaletteSwatch(palette, color);

        TextView presetsTitle = makeText("FULL THEME PRESETS", 11, true);
        presetsTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams presetsTitleLp = new LinearLayout.LayoutParams(-1, dp(26));
        presetsTitleLp.setMargins(0, dp(6), 0, 0);
        root.addView(presetsTitle, presetsTitleLp);

        GridLayout presets = new GridLayout(this);
        presets.setColumnCount(4);
        presets.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        root.addView(presets, new LinearLayout.LayoutParams(-1, dp(144)));

        addPreset(presets, "ORIGINAL", 0xFFDAC6A5, 0xFFE06F24, 0xFF121210);
        addPreset(presets, "INDUSTRIAL", 0xFF242321, 0xFFD86B1F, 0xFFF1E9D8);
        addPreset(presets, "ACID", 0xFF11150D, 0xFFA7FF23, 0xFFF4F0D7);
        addPreset(presets, "MONO", 0xFFD7D2C8, 0xFF373532, 0xFF111111);
        addPreset(presets, "CYBERPUNK", 0xFF10131A, 0xFFFF2AA1, 0xFF47F3FF);
        addPreset(presets, "NEON BLUE", 0xFF0B111C, 0xFF1E9BFF, 0xFFE7F6FF);
        addPreset(presets, "BLOOD RED", 0xFF160B0D, 0xFFD91E36, 0xFFF2D6D8);
        addPreset(presets, "PURPLE HAZE", 0xFF171021, 0xFF8F55FF, 0xFFF1E8FF);
        addPreset(presets, "FOREST", 0xFF101812, 0xFF31B15B, 0xFFE2F0E5);
        addPreset(presets, "OCEAN", 0xFF0D1A20, 0xFF1CBEC0, 0xFFE0F7F7);
        addPreset(presets, "COPPER", 0xFF211914, 0xFFCA7A37, 0xFFF2DED0);
        addPreset(presets, "ICE", 0xFFE8F1F4, 0xFF4AA3CF, 0xFF10242D);
        addPreset(presets, "SUNSET", 0xFF26111A, 0xFFFF6B2C, 0xFFFFD7B5);
        addPreset(presets, "TOXIC", 0xFF0D120C, 0xFF7CFF23, 0xFFE9FFD7);
        addPreset(presets, "STEEL", 0xFF20262B, 0xFF7A8D99, 0xFFE5EBEF);
        addPreset(presets, "VIOLET", 0xFF160F20, 0xFFB044DE, 0xFFF3E6FA);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(56));
        actionsParams.setMargins(0, dp(7), 0, dp(3));
        root.addView(actions, actionsParams);

        saveButton = actionButton("SAVE THEME");
        backButton = actionButton("BACK TO SYNTH");
        resetButton = actionButton("RESET DEFAULTS");
        LinearLayout.LayoutParams ab = new LinearLayout.LayoutParams(dp(190), dp(46));
        ab.setMargins(dp(7), 0, dp(7), 0);
        actions.addView(saveButton, ab);
        actions.addView(backButton, ab);
        actions.addView(resetButton, ab);

        saveButton.setOnClickListener(v -> {
            ThemePreferences.save(this, panelColor, knobColor, textColor);
            Toast.makeText(this, "Theme saved", Toast.LENGTH_SHORT).show();
            refreshUi();
        });
        backButton.setOnClickListener(v -> finish());
        resetButton.setOnClickListener(v -> {
            panelColor = ThemePreferences.DEFAULT_PANEL;
            knobColor = ThemePreferences.DEFAULT_KNOBS;
            textColor = ThemePreferences.DEFAULT_TEXT;
            loadSelectedColour();
            refreshUi();
        });

        setContentView(scroll);
    }

    private void selectTarget(int target) {
        selectedTarget = target;
        loadSelectedColour();
        refreshUi();
    }

    private void applySelectedColour(int color, boolean updateHsv) {
        if (selectedTarget == PANEL) panelColor = color;
        else if (selectedTarget == KNOBS) knobColor = color;
        else textColor = color;
        if (updateHsv) loadSelectedColour();
        else syncSliders();
        refreshUi();
    }

    private void loadSelectedColour() {
        int colour = selectedTarget == PANEL ? panelColor : selectedTarget == KNOBS ? knobColor : textColor;
        Color.colorToHSV(colour, hsv);
        syncSliders();
    }

    private void syncSliders() {
        if (hueSlider == null) return;
        hueSlider.setHsv(hsv[0], hsv[1], hsv[2]);
        saturationSlider.setHsv(hsv[0], hsv[1], hsv[2]);
        brightnessSlider.setHsv(hsv[0], hsv[1], hsv[2]);
    }

    private void refreshUi() {
        if (root == null) return;
        root.setBackgroundColor(panelColor);
        recolourTextTree(root, textColor);

        int selectedColour = selectedTarget == PANEL ? panelColor : selectedTarget == KNOBS ? knobColor : textColor;
        String name = selectedTarget == PANEL ? "PANEL" : selectedTarget == KNOBS ? "KNOBS" : "TEXT";
        preview.setText(name + "\nCOLOUR");
        preview.setBackgroundColor(selectedColour);
        preview.setTextColor(contrast(selectedColour));

        panelSwatch.setBackgroundColor(panelColor); panelSwatch.setTextColor(contrast(panelColor));
        knobSwatch.setBackgroundColor(knobColor); knobSwatch.setTextColor(contrast(knobColor));
        textSwatch.setBackgroundColor(textColor); textSwatch.setTextColor(contrast(textColor));

        styleTab(panelTab, selectedTarget == PANEL);
        styleTab(knobsTab, selectedTarget == KNOBS);
        styleTab(textTab, selectedTarget == TEXT);
        styleAction(saveButton, true);
        styleAction(backButton, false);
        styleAction(resetButton, false);
    }

    private void styleTab(Button b, boolean selected) {
        int bg = selected ? knobColor : ThemePreferences.lighten(panelColor, 0.08f);
        b.setBackgroundColor(bg);
        b.setTextColor(selected ? contrast(knobColor) : textColor);
    }

    private void styleAction(Button b, boolean primary) {
        int bg = primary ? knobColor : ThemePreferences.lighten(panelColor, 0.08f);
        b.setBackgroundColor(bg);
        b.setTextColor(primary ? contrast(knobColor) : textColor);
    }

    private void addPaletteSwatch(GridLayout grid, int color) {
        TextView swatch = new TextView(this);
        swatch.setBackgroundColor(color);
        swatch.setContentDescription("Set selected theme colour");
        swatch.setOnClickListener(v -> applySelectedColour(color, true));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(42);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(swatch, lp);
    }

    private void addPreset(GridLayout grid, String label, int panel, int knobs, int text) {
        Button b = actionButton(label);
        b.setTextSize(10f);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(34);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        grid.addView(b, lp);
        b.setOnClickListener(v -> {
            panelColor = panel;
            knobColor = knobs;
            textColor = text;
            loadSelectedColour();
            refreshUi();
        });
    }

    private TextView sliderLabel(String value) {
        TextView t = makeText(value, 10.5f, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(20));
        p.setMargins(dp(7), 0, 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout.LayoutParams sliderParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(36));
        p.setMargins(0, 0, 0, dp(3));
        return p;
    }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        return b;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        return b;
    }

    private TextView swatch(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(9.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView makeText(String value, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int contrast(int color) {
        double y = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return y > 150 ? Color.BLACK : Color.WHITE;
    }

    private void recolourTextTree(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button)
                && view != preview && view != panelSwatch && view != knobSwatch && view != textSwatch) {
            ((TextView) view).setTextColor(color);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) recolourTextTree(g.getChildAt(i), color);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static final class ColorSlider extends View {
        static final int HUE = 0;
        static final int SATURATION = 1;
        static final int BRIGHTNESS = 2;

        interface Listener { void onChanged(float hue, float saturation, float brightness); }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final int mode;
        private final float density;
        private float hue = 0f;
        private float saturation = 1f;
        private float brightness = 1f;
        private Listener listener;

        ColorSlider(Context context, int mode) {
            super(context);
            this.mode = mode;
            density = getResources().getDisplayMetrics().density;
            setFocusable(true);
        }

        void setListener(Listener listener) { this.listener = listener; }

        void setHsv(float h, float s, float v) {
            hue = clamp(h, 0f, 360f);
            saturation = clamp(s, 0f, 1f);
            brightness = clamp(v, 0f, 1f);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = 14f * density;
            float right = getWidth() - 14f * density;
            float cy = getHeight() * 0.5f;
            float half = 7f * density;
            track.set(left, cy - half, right, cy + half);

            Shader shader;
            if (mode == HUE) {
                int[] colours = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
                shader = new LinearGradient(left, 0, right, 0, colours, null, Shader.TileMode.CLAMP);
            } else if (mode == SATURATION) {
                int grey = Color.HSVToColor(new float[]{hue, 0f, brightness});
                int full = Color.HSVToColor(new float[]{hue, 1f, brightness});
                shader = new LinearGradient(left, 0, right, 0, grey, full, Shader.TileMode.CLAMP);
            } else {
                int black = Color.BLACK;
                int bright = Color.HSVToColor(new float[]{hue, saturation, 1f});
                shader = new LinearGradient(left, 0, right, 0, black, bright, Shader.TileMode.CLAMP);
            }
            paint.setShader(shader);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(track, 7f * density, 7f * density, paint);
            paint.setShader(null);

            float fraction = mode == HUE ? hue / 360f : mode == SATURATION ? saturation : brightness;
            float x = left + fraction * (right - left);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, cy, 10f * density, paint);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            canvas.drawCircle(x, cy, 10f * density, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float left = 14f * density;
                float right = getWidth() - 14f * density;
                float f = clamp((event.getX() - left) / Math.max(1f, right - left), 0f, 1f);
                if (mode == HUE) hue = f * 360f;
                else if (mode == SATURATION) saturation = f;
                else brightness = f;
                invalidate();
                if (listener != null) listener.onChanged(hue, saturation, brightness);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_UP || super.onTouchEvent(event);
        }

        private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    }
}
