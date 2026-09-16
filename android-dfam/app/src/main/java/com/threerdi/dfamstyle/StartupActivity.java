package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class StartupActivity extends Activity {
    private int panel;
    private int knobs;
    private int text;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        UiUtil.applyImmersive(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtil.applyImmersive(this);
        if (root != null) buildUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }

    private void buildUi() {
        panel = ThemePreferences.panelColor(this);
        knobs = ThemePreferences.knobColor(this);
        text = ThemePreferences.textColor(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(panel);

        Bitmap backgroundBitmap = loadStartupBackground();
        if (backgroundBitmap != null) {
            ImageView background = new ImageView(this);
            background.setImageBitmap(backgroundBitmap);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        }

        View tint = new View(this);
        tint.setBackgroundColor(Color.argb(
                138,
                Color.red(panel),
                Color.green(panel),
                Color.blue(panel)));
        root.addView(tint, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(40), dp(20), dp(40), dp(20));

        TextView mark = new TextView(this);
        mark.setText("●  ●  ●  ●");
        mark.setTextColor(knobs);
        mark.setTextSize(28);
        mark.setGravity(Gravity.CENTER);
        applyTextShadow(mark);
        content.addView(mark, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView title = new TextView(this);
        title.setText("3RDI ANALOG PERCUSSION");
        title.setTextColor(text);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        applyTextShadow(title);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DUAL OSCILLATOR • FILTER • MODULATION • 8-STEP PERCUSSION SYNTH");
        subtitle.setTextColor(text);
        subtitle.setAlpha(0.82f);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        applyTextShadow(subtitle);
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(34)));

        int nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        TextView status = new TextView(this);
        status.setText("AUDIO ENGINE READY  •  DEVICE OUTPUT " + nativeRate + " Hz");
        status.setTextColor(text);
        status.setAlpha(0.76f);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        applyTextShadow(status);
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));

        Space spacer = new Space(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, dp(10)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = makeButton("START SYNTH");
        Button audio = makeButton("AUDIO SETTINGS");
        Button colours = makeButton("COLOURS");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(54));
        bp.setMargins(dp(7), 0, dp(7), 0);
        buttons.addView(start, bp);
        buttons.addView(audio, bp);
        buttons.addView(colours, bp);
        content.addView(buttons, new LinearLayout.LayoutParams(-1, dp(68)));

        TextView footer = new TextView(this);
        footer.setText("3rdi Analog Percussion v1.7");
        footer.setTextColor(text);
        footer.setAlpha(0.66f);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        applyTextShadow(footer);
        content.addView(footer, new LinearLayout.LayoutParams(-1, dp(26)));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.gravity = Gravity.CENTER;
        root.addView(content, cp);

        LinearLayout creatorBar = new LinearLayout(this);
        creatorBar.setOrientation(LinearLayout.HORIZONTAL);
        creatorBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

        TextView creator = new TextView(this);
        creator.setText("created by Ben Bouchnafa");
        creator.setTextColor(text);
        creator.setAlpha(0.82f);
        creator.setTextSize(10);
        creator.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        applyTextShadow(creator);
        LinearLayout.LayoutParams creatorParams = new LinearLayout.LayoutParams(-2, dp(36));
        creatorParams.setMargins(0, 0, dp(9), 0);
        creatorBar.addView(creator, creatorParams);

        Button exit = makeSmallButton("EXIT");
        creatorBar.addView(exit, new LinearLayout.LayoutParams(dp(72), dp(34)));

        FrameLayout.LayoutParams creatorBarParams = new FrameLayout.LayoutParams(-2, dp(42));
        creatorBarParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        creatorBarParams.setMargins(dp(12), dp(12), dp(18), dp(14));
        root.addView(creatorBar, creatorBarParams);

        start.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        audio.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        colours.setOnClickListener(v -> startActivity(new Intent(this, ThemeActivity.class)));
        exit.setOnClickListener(v -> finishAffinity());

        setContentView(root);
    }

    private Bitmap loadStartupBackground() {
        try (InputStream input = getResources().openRawResource(R.raw.startup_background_b64_v17);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            byte[] encoded = output.toByteArray();
            byte[] jpeg = Base64.decode(encoded, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyTextShadow(TextView view) {
        double y = 0.299 * Color.red(text) + 0.587 * Color.green(text) + 0.114 * Color.blue(text);
        int shadow = y > 150 ? Color.BLACK : Color.WHITE;
        view.setShadowLayer(dp(2), dp(1), dp(1), shadow);
    }

    private Button makeButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(contrast(knobs));
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(knobs);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private Button makeSmallButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(contrast(knobs));
        b.setTextSize(10);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(knobs);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        return b;
    }

    private int contrast(int color) {
        double y = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return y > 150 ? Color.BLACK : Color.WHITE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
