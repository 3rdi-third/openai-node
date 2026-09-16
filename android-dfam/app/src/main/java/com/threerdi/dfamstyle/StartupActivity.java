package com.threerdi.dfamstyle;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class StartupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        UiUtil.applyImmersive(this);
        setContentView(new StartupView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtil.applyImmersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.applyImmersive(this);
    }

    private final class StartupView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap artwork;

        // Coordinates are defined in the 720 x 1279 startup artwork.
        private final RectF startRect = new RectF(195, 610, 525, 716);
        private final RectF presetsRect = new RectF(195, 713, 525, 806);
        private final RectF audioRect = new RectF(195, 802, 525, 892);
        private final RectF coloursRect = new RectF(195, 888, 525, 978);
        private final RectF exitRect = new RectF(605, 1125, 710, 1202);

        StartupView() {
            super(StartupActivity.this);
            setBackgroundColor(Color.BLACK);
            artwork = loadArtwork();
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (artwork == null) {
                canvas.drawColor(Color.BLACK);
                return;
            }

            // Fill the entire screen. This keeps every control in the artwork visible
            // on tall Android displays instead of cropping the edges.
            Rect dst = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(artwork, null, dst, paint);

            // The source artwork says v1.6; cover it with the actual build version.
            float sx = getWidth() / 720f;
            float sy = getHeight() / 1279f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(205, 3, 3, 3));
            canvas.drawRoundRect(new RectF(17 * sx, 12 * sy, 94 * sx, 52 * sy), 5 * sx, 5 * sy, paint);
            paint.setColor(Color.rgb(242, 211, 158));
            paint.setTextSize(17f * Math.min(sx, sy));
            paint.setFakeBoldText(false);
            canvas.drawText("v1.7", 28 * sx, 38 * sy, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP || artwork == null) return true;

            float x = event.getX() * 720f / Math.max(1, getWidth());
            float y = event.getY() * 1279f / Math.max(1, getHeight());

            if (startRect.contains(x, y)) {
                startActivity(new Intent(StartupActivity.this, MainActivity.class));
                return true;
            }
            if (presetsRect.contains(x, y)) {
                Intent intent = new Intent(StartupActivity.this, MainActivity.class);
                intent.putExtra("open_presets", true);
                startActivity(intent);
                return true;
            }
            if (audioRect.contains(x, y)) {
                startActivity(new Intent(StartupActivity.this, SettingsActivity.class));
                return true;
            }
            if (coloursRect.contains(x, y)) {
                startActivity(new Intent(StartupActivity.this, ThemeActivity.class));
                return true;
            }
            if (exitRect.contains(x, y)) {
                finishAffinity();
                return true;
            }
            return true;
        }
    }

    private Bitmap loadArtwork() {
        try (InputStream input = getResources().openRawResource(R.raw.startup_background_b64_v17);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            byte[] jpeg = Base64.decode(output.toByteArray(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        } catch (Exception ignored) {
            return null;
        }
    }
}
