package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

class StartupActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var audioTrack: AudioTrack? = null
    private var finished = false
    private lateinit var splash: StartupView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        splash = StartupView()
        setContentView(splash)
        playIntroAudio()
        handler.postDelayed({ finishIntro() }, INTRO_MS)
    }

    private fun finishIntro() {
        if (finished) return
        finished = true
        stopIntroAudio()
        startActivity(Intent(this, FxMainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    private fun playIntroAudio() {
        Thread {
            try {
                val sampleRate = 48_000
                val durationSec = INTRO_MS / 1000.0
                val total = (sampleRate * durationSec).roundToInt()
                val pcm = ShortArray(total)

                for (i in 0 until total) {
                    val t = i.toDouble() / sampleRate
                    var sample = 0.0

                    // Four hard industrial kicks.
                    for (kickStart in doubleArrayOf(0.0, 1.0, 2.0, 3.0)) {
                        val kt = t - kickStart
                        if (kt >= 0.0 && kt < 0.48) {
                            val f0 = 165.0
                            val f1 = 42.0
                            val sweepK = kotlin.math.ln(f1 / f0) / 0.22
                            val phase = 2.0 * PI * f0 * (exp(sweepK * kt) - 1.0) / sweepK
                            val env = exp(-kt * 10.5)
                            val body = sin(phase) * env
                            val click = sin(2.0 * PI * 4200.0 * kt) * exp(-kt * 85.0) * 0.24
                            sample += tanh((body + click) * 3.2) * 0.88
                        }
                    }

                    // Distorted screeching bassline underneath the kicks.
                    val fm = 16.0 * sin(2.0 * PI * 5.3 * t) + 5.0 * sin(2.0 * PI * 17.0 * t)
                    val basePhase = 2.0 * PI * (58.0 * t + 0.35 * sin(2.0 * PI * 5.3 * t))
                    val saw = 2.0 * ((basePhase / (2.0 * PI)) - floor(basePhase / (2.0 * PI))) - 1.0
                    val sweepHz = 450.0 + 850.0 * Math.pow(t / durationSec, 1.4)
                    val screech = sin(2.0 * PI * sweepHz * t + 2.8 * sin(basePhase * 1.7 + fm * 0.02))
                    val step = if ((floor(t * 8.0).toInt() % 4) == 3) 0.45 else 1.0
                    val bass = tanh((saw * 0.75 + screech * (0.14 + 0.45 * t / durationSec)) * 4.7) * step
                    sample += bass * 0.42

                    // Fast fade in and short fade out.
                    val fadeIn = min(1.0, t / 0.05)
                    val fadeOut = min(1.0, (durationSec - t) / 0.18)
                    sample = tanh(sample * 1.45) * max(0.0, min(fadeIn, fadeOut))
                    pcm[i] = (sample.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).roundToInt().toShort()
                }

                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer, pcm.size * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                track.play()

                val stereo = ShortArray(pcm.size * 2)
                for (i in pcm.indices) {
                    stereo[i * 2] = pcm[i]
                    stereo[i * 2 + 1] = pcm[i]
                }
                track.write(stereo, 0, stereo.size, AudioTrack.WRITE_BLOCKING)
            } catch (_: Throwable) {
                // Splash visuals still continue if a device refuses AudioTrack creation.
            }
        }.start()
    }

    private fun stopIntroAudio() {
        try { audioTrack?.pause() } catch (_: Throwable) {}
        try { audioTrack?.flush() } catch (_: Throwable) {}
        try { audioTrack?.release() } catch (_: Throwable) {}
        audioTrack = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopIntroAudio()
        super.onDestroy()
    }

    inner class StartupView : View(this) {
        private val start = SystemClock.elapsedRealtime()
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 245, 245)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 92, 0)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 92, 0)
            strokeWidth = 6f
        }
        private val scanPaint = Paint().apply { color = Color.rgb(24, 24, 26) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
            val sec = elapsed / 1000f
            val w = width.toFloat()
            val h = height.toFloat()

            canvas.drawColor(Color.rgb(6, 6, 7))

            // Industrial scan lines.
            var y = 0f
            while (y < h) {
                canvas.drawRect(0f, y, w, y + 2f, scanPaint)
                y += 18f
            }

            // Kick-reactive center line.
            val nearestBeat = kotlin.math.round(sec)
            val distance = kotlin.math.abs(sec - nearestBeat)
            val pulse = kotlin.math.exp(-(distance * distance) / 0.0045f)
            val half = w * (0.08f + 0.39f * pulse)
            val lineY = h * 0.63f
            canvas.drawLine(w / 2f - half, lineY, w / 2f + half, lineY, accentPaint)

            val intro = (sec / 0.75f).coerceIn(0f, 1f)
            val ease = 1f - (1f - intro) * (1f - intro) * (1f - intro)
            val titleSize = 62f + 54f * ease
            val jitter = (4f * kotlin.math.exp(-sec * 1.7f) * sin(sec * 38f))
            titlePaint.textSize = titleSize

            if (((elapsed / 75L) % 9L) <= 1L) {
                val red = Paint(titlePaint).apply { color = Color.rgb(255, 60, 0) }
                val blue = Paint(titlePaint).apply { color = Color.rgb(70, 120, 255) }
                canvas.drawText("3rdi SESSION GRID", w / 2f - 7f, h * 0.43f + 2f, red)
                canvas.drawText("3rdi SESSION GRID", w / 2f + 7f, h * 0.43f - 2f, blue)
            }
            canvas.drawText("3rdi SESSION GRID", w / 2f + jitter, h * 0.43f, titlePaint)

            if (sec > 1.0f) {
                subtitlePaint.textSize = 28f
                canvas.drawText("INDUSTRIAL SESSION ENGINE", w / 2f, h * 0.52f, subtitlePaint)
            }

            if (!finished) {
                postInvalidateOnAnimation()
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                finishIntro()
                return true
            }
            return true
        }
    }

    companion object {
        private const val INTRO_MS = 4000L
    }
}
