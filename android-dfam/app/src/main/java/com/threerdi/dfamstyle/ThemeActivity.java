package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ThemeActivity extends Activity {
    private EditText panelHex;
    private EditText knobHex;
    private EditText textHex;
    private TextView panelPreview;
    private TextView knobPreview;
    private TextView textPreview;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        UiUtil.applyImmersive(this);
        buildUi();
        loadCurrent();
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
        root.setPadding(dp(34), dp(22), dp(34), dp(22));

        TextView title = text("SYNTH COLOURS", 28, true);
        root.addView(title);
        TextView sub = text("Use hexadecimal RGB values. Example: #DAC6A5. Changes are stored for the synth UI.", 12, false);
        sub.setAlpha(0.68f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(2), 0, dp(18));
        root.addView(sub, sp);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        rows.setGravity(Gravity.CENTER);
        root.addView(rows, new LinearLayout.LayoutParams(-1, -2));

        panelPreview = preview();
        knobPreview = preview();
        textPreview = preview();
        panelHex = field();
        knobHex = field();
        textHex = field();
        rows.addView(colourColumn("PANEL / BACKGROUND", panelPreview, panelHex), columnParams());
        rows.addView(colourColumn("KNOBS / ACCENT", knobPreview, knobHex), columnParams());
        rows.addView(colourColumn("TEXT / MARKINGS", textPreview, textHex), columnParams());

        TextView presetsTitle = text("PRESETS", 13, true);
        LinearLayout.LayoutParams pt = new LinearLayout.LayoutParams(-1, -2);
        pt.setMargins(0, dp(16), 0, dp(6));
        presetsTitle.setGravity(Gravity.CENTER);
        root.addView(presetsTitle, pt);

        LinearLayout presets = new LinearLayout(this);
        presets.setGravity(Gravity.CENTER);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(presets, new LinearLayout.LayoutParams(-1, dp(55)));
        addPreset(presets, "ORIGINAL", "#DAC6A5", "#E06F24", "#121210");
        addPreset(presets, "INDUSTRIAL", "#242321", "#D86B1F", "#F1E9D8");
        addPreset(presets, "ACID", "#11150D", "#A7FF23", "#F4F0D7");
        addPreset(presets, "MONO", "#D7D2C8", "#373532", "#111111");

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(64));
        ap.setMargins(0, dp(10), 0, 0);
        root.addView(actions, ap);

        Button save = button("SAVE & BACK");
        Button reset = button("RESET");
        Button cancel = button("CANCEL");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(170), dp(50));
        bp.setMargins(dp(7), 0, dp(7), 0);
        actions.addView(save, bp);
        actions.addView(reset, bp);
        actions.addView(cancel, bp);

        save.setOnClickListener(v -> saveTheme());
        reset.setOnClickListener(v -> {
            setFields(ThemePreferences.DEFAULT_PANEL, ThemePreferences.DEFAULT_KNOBS, ThemePreferences.DEFAULT_TEXT);
            updatePreviews();
        });
        cancel.setOnClickListener(v -> finish());

        panelHex.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updatePreviews(); });
        knobHex.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updatePreviews(); });
        textHex.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updatePreviews(); });

        setContentView(root);
    }

    private LinearLayout colourColumn(String label, TextView preview, EditText field) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView l = text(label, 12, true);
        l.setGravity(Gravity.CENTER);
        col.addView(l, new LinearLayout.LayoutParams(-1, dp(30)));
        col.addView(preview, new LinearLayout.LayoutParams(dp(125), dp(72)));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(165), dp(48));
        fp.setMargins(0, dp(8), 0, 0);
        col.addView(field, fp);
        return col;
    }

    private LinearLayout.LayoutParams columnParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(10), 0, dp(10), 0);
        return p;
    }

    private TextView preview() {
        TextView v = new TextView(this);
        v.setText("COLOUR");
        v.setTextSize(11);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private EditText field() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setGravity(Gravity.CENTER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        e.setSelectAllOnFocus(true);
        return e;
    }

    private void addPreset(LinearLayout parent, String label, String panel, String knobs, String text) {
        Button b = button(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(145), dp(45));
        p.setMargins(dp(5), 0, dp(5), 0);
        parent.addView(b, p);
        b.setOnClickListener(v -> {
            panelHex.setText(panel);
            knobHex.setText(knobs);
            textHex.setText(text);
            updatePreviews();
        });
    }

    private void loadCurrent() {
        setFields(ThemePreferences.panelColor(this), ThemePreferences.knobColor(this), ThemePreferences.textColor(this));
        updatePreviews();
    }

    private void setFields(int panel, int knobs, int text) {
        panelHex.setText(ThemePreferences.toHex(panel));
        knobHex.setText(ThemePreferences.toHex(knobs));
        textHex.setText(ThemePreferences.toHex(text));
    }

    private void updatePreviews() {
        int panel = ThemePreferences.parseHex(panelHex.getText().toString(), ThemePreferences.panelColor(this));
        int knobs = ThemePreferences.parseHex(knobHex.getText().toString(), ThemePreferences.knobColor(this));
        int text = ThemePreferences.parseHex(textHex.getText().toString(), ThemePreferences.textColor(this));
        panelPreview.setBackgroundColor(panel);
        knobPreview.setBackgroundColor(knobs);
        textPreview.setBackgroundColor(text);
        panelPreview.setTextColor(contrast(panel));
        knobPreview.setTextColor(contrast(knobs));
        textPreview.setTextColor(contrast(text));
        root.setBackgroundColor(panel);
        recolourTextTree(root, text);
    }

    private void saveTheme() {
        String ps = panelHex.getText().toString().trim();
        String ks = knobHex.getText().toString().trim();
        String ts = textHex.getText().toString().trim();
        if (!valid(ps) || !valid(ks) || !valid(ts)) {
            Toast.makeText(this, "Use colours in #RRGGBB format", Toast.LENGTH_SHORT).show();
            return;
        }
        int panel = Color.parseColor(ps);
        int knobs = Color.parseColor(ks);
        int text = Color.parseColor(ts);
        ThemePreferences.save(this, panel, knobs, text);
        Toast.makeText(this, "Synth colours saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean valid(String s) {
        return s != null && s.matches("#[0-9A-Fa-f]{6}");
    }

    private int contrast(int color) {
        double y = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return y > 150 ? Color.BLACK : Color.WHITE;
    }

    private void recolourTextTree(android.view.View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && view != panelPreview && view != knobPreview && view != textPreview) {
            ((TextView) view).setTextColor(color);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) recolourTextTree(g.getChildAt(i), color);
        }
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setBackgroundColor(ThemePreferences.knobColor(this));
        b.setTextColor(contrast(ThemePreferences.knobColor(this)));
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}