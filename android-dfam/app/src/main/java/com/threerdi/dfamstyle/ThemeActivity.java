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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ThemeActivity extends Activity {
    private static final int PANEL = 0;
    private static final int KNOBS = 1;
    private static final int TEXT = 2;

    private LinearLayout root;
    private TextView title;
    private TextView subtitle;
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(30), dp(18), dp(30), dp(18));

        title = makeText("SYNTH COLOURS", 27, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        subtitle = makeText("Choose PANEL, KNOBS or TEXT, then move the colour sliders. No RGB numbers required.", 11.5f, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setAlpha(0.72f);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(52));
        tabsParams.setMargins(0, dp(4), 0, dp(7));
        root.addView(tabs, tabsParams);

        panelTab = tabButton("PANEL");
        knobsTab = tabButton("KNOBS");
        textTab = tabButton("TEXT");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(150), dp(44));
        tp.setMargins(dp(6), 0, dp(6), 0);
        tabs.addView(panelTab, tp);
        tabs.addView(knobsTab, tp);
        tabs.addView(textTab, tp);

        panelTab.setOnClickListener(v -> selectTarget(PANEL));
        knobsTab.setOnClickListener(v -> selectTarget(KNOBS));
        textTab.setOnClickListener(v -> selectTarget(TEXT));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout previewCol = new LinearLayout(this);
        previewCol.setOrientation(LinearLayout.VERTICAL);
        previewCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(0, -1, 0.34f);
        pc.setMargins(0, 0, dp(18), 0);
        middle.addView(previewCol, pc);

        TextView previewLabel = makeText("LIVE PREVIEW", 12, true);
        previewLabel.setGravity(Gravity.CENTER);
        previewCol.addView(previewLabel, new LinearLayout.LayoutParams(-1, dp(28)));

        preview = new TextView(this);
        preview.setText("SELECTED\nCOLOUR");
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(16);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(210), dp(112));
        pp.setMargins(0, dp(2), 0, dp(12));
        previewCol.addView(preview, pp);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER);
        previewCol.addView(swatches, new LinearLayout.LayoutParams(-1, dp(64)));

        panelSwatch = swatch("PANEL");
        knobSwatch = swatch("KNOBS");
        textSwatch = swatch("TEXT");
        LinearLayout.LayoutParams sw = new LinearLayout.LayoutParams(dp(82), dp(52));
        sw.setMargins(dp(4), 0, dp(4), 0);
        swatches.addView(panelSwatch, sw);
        swatches.addView(knobSwatch, sw);
        swatches.addView(textSwatch, sw);

        LinearLayout sliders = new LinearLayout(this);
        sliders.setOrientation(LinearLayout.VERTICAL);
        sliders.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(0, -1, 0.66f);
        sc.setMargins(dp(10), 0, 0, 0);
        middle.addView(sliders, sc);

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
            hsv[0] = h;
            hsv[1] = s;
            hsv[2] = v;
            int colour = Color.HSVToColor(hsv);
            if (selectedTarget == PANEL) panelColor = colour;
            else if (selectedTarget == KNOBS) knobColor = colour;
            else textColor = colour;
            syncSliders();
            refreshUi();
        };
        hueSlider.setListener(listener);
        saturationSlider.setListener(listener);
        brightnessSlider.setListener(listener);

        TextView presetsTitle = makeText("PRESETS", 11.5f, true);
        presetsTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(-1, dp(25));
        ptl.setMargins(0, dp(2), 0, 0);
        root.addView(presetsTitle, ptl);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        root.addView(presets, new LinearLayout.LayoutParams(-1, dp(50)));
        addPreset(presets, "ORIGINAL", Color.rgb(218, 198, 165), Color.rgb(224, 111, 36), Color.rgb(18, 18, 16));
        addPreset(presets, "INDUSTRIAL", Color.rgb(36, 35, 33), Color.rgb(216, 107, 31), Color.rgb(241, 233, 216));
        addPreset(presets, "ACID", Color.rgb(17, 21, 13), Color.rgb(167, 255, 35), Color.rgb(244, 240, 215));
        addPreset(presets, "MONO", Color.rgb(215, 210, 200), Color.rgb(55, 53, 50), Color.rgb(17, 17, 17));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(61));
        actionsParams.setMargins(0, dp(5), 0, 0);
        root.addView(actions, actionsParams);

        saveButton = actionButton("SAVE THEME");
        backButton = actionButton("BACK TO SYNTH");
        resetButton = actionButton("RESET DEFAULTS");
        LinearLayout.LayoutParams ab = new LinearLayout.LayoutParams(dp(190), dp(50));
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

        setContentView(root);
    }

    private void selectTarget(int target) {
        selectedTarget = target;
        loadSelectedColour();
        refreshUi();
    }

    private void loadSelectedColour() {
        int colour = selectedTarget == PANEL ? panelColor : selectedTarget == KNOBS ? knobColor : textColor;
        Color.colorToHSV(colour, hsv);
        syncSliders();
    }

    private void syncSliders() {
        hueSlider.setHsv(hsv[0], hsv[1], hsv[2]);
        saturationSlider.setHsv(hsv[0], hsv[1], hsv[2]);
        brightnessSlider.setHsv(hsv[0], hsv[1], hsv[2]);
    }

    private void refreshUi() {
        root.setBackgroundColor(panelColor);
        recolourTextTree(root, textColor);

        int selectedColour = selectedTarget == PANEL ? panelColor : selectedTarget == KNOBS ? knobColor : textColor;
        String name = selectedTarget == PANEL ? "PANEL" : selectedTarget == KNOBS ? "KNOBS" : "TEXT";
        preview.setText(name + "\nCOLOUR");
        preview.setBackgroundColor(selectedColour);
        preview.setTextColor(contrast(selectedColour));

        panelSwatch.setBackgroundColor(panelColor);
        panelSwatch.setTextColor(contrast(panelColor));
        knobSwatch.setBackgroundColor(knobColor);
        knobSwatch.setTextColor(contrast(knobColor));
        textSwatch.setBackgroundColor(textColor);
        textSwatch.setTextColor(contrast(textColor));

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

    private void addPreset(LinearLayout parent, String label, int panel, int knobs, int text) {
        Button b = actionButton(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(140), dp(42));
        p.setMargins(dp(5), 0, dp(5), 0);
        parent.addView(b, p);
        b.setOnClickListener(v -> {
            panelColor = panel;
            knobColor = knobs;
            textColor = text;
            loadSelectedColour();
            refreshUi();
        });
    }

    private TextView sliderLabel(String value) {
        TextView t = makeText(value, 11.5f, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(24));
        p.setMargins(dp(7), dp(1), 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout.LayoutParams sliderParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, 0, 0, dp(7));
        return p;
    }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        return b;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        return b;
    }

    private TextView swatch(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(10);
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

        interface Listener {
            void onChanged(float hue, float saturation, float brightness);
        }

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

        void setListener(Listener listener) {
            this.listener = listener;
        }

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
            float half = 9f * density;
            track.set(left, cy - half, right, cy + half);

            Shader shader;
            if (mode == HUE) {
                int[] colours = {
                        Color.RED,
                        Color.YELLOW,
                        Color.GREEN,
                        Color.CYAN,
                        Color.BLUE,
                        Color.MAGENTA,
                        Color.RED
                };
                shader = new LinearGradient(left, 0, right, 0, colours, null, Shader.TileMode.CLAMP);
            } else if (mode == SATURATION) {
                int start = Color.HSVToColor(new float[]{hue, 0f, brightness});
                int end = Color.HSVToColor(new float[]{hue, 1f, brightness});
                shader = new LinearGradient(left, 0, right, 0, start, end, Shader.TileMode.CLAMP);
            } else {
                int start = Color.BLACK;
                int end = Color.HSVToColor(new float[]{hue, saturation, 1f});
                shader = new LinearGradient(left, 0, right, 0, start, end, Shader.TileMode.CLAMP);
            }

            paint.setShader(shader);
            canvas.drawRoundRect(track, half, half, paint);
            paint.setShader(null);

            float normalized = mode == HUE ? hue / 360f : mode == SATURATION ? saturation : brightness;
            float x = left + normalized * (right - left);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, cy, 11f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, cy, 11f * density, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float left = 14f * density;
                float right = getWidth() - 14f * density;
                float n = (event.getX() - left) / Math.max(1f, right - left);
                n = clamp(n, 0f, 1f);
                if (mode == HUE) hue = n * 360f;
                else if (mode == SATURATION) saturation = n;
                else brightness = n;
                if (listener != null) listener.onChanged(hue, saturation, brightness);
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) return true;
            return super.onTouchEvent(event);
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
