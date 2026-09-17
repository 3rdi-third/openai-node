package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val FX_PICK_AUDIO = 5203

class FxMainActivity : Activity() {
    private lateinit var sessionView: FxSessionView
    private var pendingTrack = -1
    private var pendingScene = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894
        sessionView = FxSessionView(this, ::pickAudio)
        setContentView(sessionView)
    }

    private fun pickAudio(track: Int, scene: Int) {
        pendingTrack = track
        pendingScene = scene
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, FX_PICK_AUDIO)
    }

    @Deprecated("Uses classic activity result API to keep the project dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FX_PICK_AUDIO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        if (pendingTrack in 0..7 && pendingScene in 0..7) {
            sessionView.assignClip(pendingTrack, pendingScene, uri)
        }
    }

    override fun onDestroy() {
        if (::sessionView.isInitialized) sessionView.shutdown()
        super.onDestroy()
    }
}

private data class FxClip(
    var uri: String? = null,
    var name: String = "",
    var state: Int = 0
)

private enum class FxKind(val label: String, val shortLabel: String) {
    NONE("NONE", "NONE"),
    BASS("BASS BOOST", "BASS"),
    WIDTH("STEREO WIDTH", "WIDTH"),
    LOW_EQ("LOW EQ", "LOW"),
    HIGH_EQ("HIGH EQ", "HIGH"),
    MID_CUT("MID CUT", "MID-"),
    REVERB("REVERB", "VERB"),
    LOUDNESS("LOUDNESS", "LOUD")
}

private data class FxSlot(
    var kind: FxKind = FxKind.NONE,
    var amount: Float = 0.5f,
    var enabled: Boolean = true
)

@Suppress("DEPRECATION")
class FxSessionView(
    ctx: Context,
    private val requestAudio: (Int, Int) -> Unit
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("session_grid_project", Context.MODE_PRIVATE)

    private val tracks = arrayOf("KICK", "DRUMS", "BASS", "PERC", "SYNTH", "DRONE", "FX", "VOX")
    private val colors = intArrayOf(
        0xffd65b43.toInt(), 0xffe08b39.toInt(), 0xffd1b13c.toInt(), 0xff65a86d.toInt(),
        0xff4f9fbf.toInt(), 0xff7772c5.toInt(), 0xffa963b3.toInt(), 0xff858585.toInt()
    )
    private val clips = Array(8) { Array(8) { FxClip() } }
    private val players = arrayOfNulls<MediaPlayer>(8)
    private val pending = arrayOfNulls<Runnable>(8)
    private val activeScene = IntArray(8) { -1 }
    private val armed = BooleanArray(8)
    private val muted = BooleanArray(8)
    private val volumes = FloatArray(8) { 0.86f }

    private val fxSlots = Array(8) { Array(2) { FxSlot() } }
    private val liveEffects = Array(8) { mutableListOf<AudioEffect>() }
    private var fxMenuTrack = -1
    private var fxMenuSlot = 0

    private var bpm = 133f
    private var playing = false
    private var transportStart = 0L
    private var quantizeBeats = 4
    private var downAt = 0L
    private var lastTapAt = 0L
    private val tapIntervals = ArrayDeque<Long>()

    private val top = 82f
    private val left = 56f
    private val right = 64f
    private val bottom = 118f
    private val gap = 4f

    init {
        isFocusable = true
        loadProject()
    }

    private fun loadProject() {
        bpm = prefs.getFloat("bpm", 133f).coerceIn(40f, 300f)
        quantizeBeats = prefs.getInt("quantize", 4).let { if (it == 1) 1 else 4 }
        for (t in 0..7) {
            volumes[t] = prefs.getFloat("volume_$t", 0.86f).coerceIn(0f, 1f)
            armed[t] = prefs.getBoolean("armed_$t", false)
            muted[t] = prefs.getBoolean("muted_$t", false)
            for (slot in 0..1) {
                val stored = prefs.getString("fx_${t}_${slot}_kind", FxKind.NONE.name) ?: FxKind.NONE.name
                fxSlots[t][slot].kind = try {
                    FxKind.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    FxKind.NONE
                }
                fxSlots[t][slot].amount = prefs.getFloat("fx_${t}_${slot}_amount", 0.5f).coerceIn(0f, 1f)
                fxSlots[t][slot].enabled = prefs.getBoolean("fx_${t}_${slot}_enabled", true)
            }
            for (s in 0..7) {
                val uri = prefs.getString("clip_${t}_$s", null)
                if (uri != null) {
                    clips[t][s].uri = uri
                    clips[t][s].name = prefs.getString("name_${t}_$s", "Clip ${s + 1}") ?: "Clip ${s + 1}"
                    clips[t][s].state = 1
                }
            }
        }
    }

    private fun saveProject() {
        val e = prefs.edit().putFloat("bpm", bpm).putInt("quantize", quantizeBeats)
        for (t in 0..7) {
            e.putFloat("volume_$t", volumes[t])
                .putBoolean("armed_$t", armed[t])
                .putBoolean("muted_$t", muted[t])
            for (slot in 0..1) {
                val fx = fxSlots[t][slot]
                e.putString("fx_${t}_${slot}_kind", fx.kind.name)
                    .putFloat("fx_${t}_${slot}_amount", fx.amount)
                    .putBoolean("fx_${t}_${slot}_enabled", fx.enabled)
            }
            for (s in 0..7) {
                val clip = clips[t][s]
                if (clip.uri == null) {
                    e.remove("clip_${t}_$s").remove("name_${t}_$s")
                } else {
                    e.putString("clip_${t}_$s", clip.uri).putString("name_${t}_$s", clip.name)
                }
            }
        }
        e.apply()
    }

    fun assignClip(track: Int, scene: Int, uri: Uri) {
        stopTrackNow(track)
        val clip = clips[track][scene]
        clip.uri = uri.toString()
        clip.name = displayName(uri).take(22)
        clip.state = 1
        saveProject()
        invalidate()
    }

    private fun displayName(uri: Uri): String {
        var result = "Audio clip"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = cursor.getString(index) ?: result
            }
        }
        if (result == "Audio clip") result = File(uri.path ?: "").name.ifBlank { result }
        return result.substringBeforeLast('.')
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.rgb(27, 27, 27))
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 300f || h < 240f) return
        val tw = (w - left - right - gap * 7f) / 8f
        val sh = (h - top - bottom - gap * 7f) / 8f

        drawHeader(c, w)
        for (t in 0..7) {
            val x = left + t * (tw + gap)
            drawTrackHeader(c, t, x, tw)
            for (s in 0..7) drawClip(c, t, s, x, top + s * (sh + gap), tw, sh)
            drawMixer(c, t, x, tw, h - bottom + 12f)
        }
        for (s in 0..7) drawSceneButton(c, s, w - 31f, top + s * (sh + gap) + sh / 2f)

        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
        p.textSize = 10f
        p.color = 0xff858585.toInt()
        c.drawText("EMPTY: LOAD • TAP: LAUNCH/STOP • HOLD: REPLACE • FX: OPEN CHANNEL RACK • SCENE ▶: LAUNCH ROW", 14f, h - 7f, p)

        if (fxMenuTrack in 0..7) drawFxMenu(c, w, h)
    }

    private fun drawHeader(c: Canvas, w: Float) {
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 21f
        p.color = 0xffeeeeee.toInt()
        c.drawText("3rdi SESSION GRID", 16f, 29f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = 12f
        p.color = 0xff9b9b9b.toInt()
        val loaded = clips.sumOf { row -> row.count { it.uri != null } }
        val status = if (playing) "RUNNING" else "STOPPED"
        c.drawText("${formatBpm()} BPM • 4/4 • ${quantizeBeats} BEAT QUANTIZE • $loaded CLIPS • $status", 16f, 52f, p)

        button(c, RectF(w - 278f, 10f, w - 221f, 61f), if (playing) "STOP" else "PLAY", if (playing) 0xff5c9c64.toInt() else 0xff4a4a4a.toInt())
        button(c, RectF(w - 214f, 10f, w - 171f, 61f), "-", 0xff454545.toInt())
        button(c, RectF(w - 165f, 10f, w - 105f, 61f), formatBpm(), 0xff363636.toInt())
        button(c, RectF(w - 99f, 10f, w - 56f, 61f), "+", 0xff454545.toInt())
        button(c, RectF(w - 50f, 10f, w - 7f, 61f), if (quantizeBeats == 4) "1B" else "1/4", 0xff5a4838.toInt())
    }

    private fun drawTrackHeader(c: Canvas, t: Int, x: Float, tw: Float) {
        p.color = 0xff3b3b3b.toInt()
        c.drawRect(x, top - 30f, x + tw, top - 4f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 11f
        p.color = 0xffededed.toInt()
        c.drawText(tracks[t], x + tw / 2f, top - 13f, p)
    }

    private fun drawClip(c: Canvas, t: Int, s: Int, x: Float, y: Float, tw: Float, sh: Float) {
        val clip = clips[t][s]
        val r = RectF(x, y, x + tw, y + sh)
        p.style = Paint.Style.FILL
        p.color = when (clip.state) {
            2, 4 -> blend(colors[t], 0xff2a2a2a.toInt(), 0.42f)
            3 -> colors[t]
            1 -> blend(colors[t], 0xff303030.toInt(), 0.70f)
            else -> 0xff343434.toInt()
        }
        c.drawRoundRect(r, 4f, 4f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (clip.state == 3) 2.3f else 1f
        p.color = if (clip.state == 3) 0xffeeeeee.toInt() else 0xff606060.toInt()
        c.drawRoundRect(r, 4f, 4f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.CENTER
        p.typeface = if (clip.state == 3) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textSize = (sh * 0.23f).coerceIn(8f, 11f)
        p.color = 0xffe1e1e1.toInt()
        val title = when {
            clip.uri == null -> "+"
            clip.state == 2 -> "QUEUED ▶"
            clip.state == 4 -> "QUEUED ■"
            else -> clip.name
        }
        c.drawText(ellipsize(title, 14), r.centerX(), r.centerY() + 3f, p)
        if (clip.state == 3) {
            p.color = 0xfff4f4f4.toInt()
            c.drawCircle(r.right - 8f, r.top + 8f, 3f, p)
        }
    }

    private fun drawSceneButton(c: Canvas, scene: Int, cx: Float, cy: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xff444444.toInt()
        c.drawCircle(cx, cy, 14f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 9f
        p.color = 0xffdddddd.toInt()
        c.drawText("▶${scene + 1}", cx, cy + 3f, p)
    }

    private fun drawMixer(c: Canvas, t: Int, x: Float, tw: Float, y: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xff333333.toInt()
        c.drawRoundRect(RectF(x, y, x + tw, y + 77f), 4f, 4f, p)

        val buttonGap = 3f
        val buttonW = ((tw - 16f - buttonGap * 2f) / 3f).coerceAtLeast(16f)
        val aRect = RectF(x + 5f, y + 6f, x + 5f + buttonW, y + 28f)
        val mRect = RectF(aRect.right + buttonGap, y + 6f, aRect.right + buttonGap + buttonW, y + 28f)
        val fxRect = RectF(mRect.right + buttonGap, y + 6f, x + tw - 5f, y + 28f)
        smallButton(c, aRect, "A", armed[t], 0xffc9554e.toInt())
        smallButton(c, mRect, "M", muted[t], 0xffd2ad45.toInt())
        val activeFxCount = fxSlots[t].count { it.enabled && it.kind != FxKind.NONE }
        smallButton(c, fxRect, if (activeFxCount > 0) "FX$activeFxCount" else "FX", activeFxCount > 0, colors[t])

        val bx = x + 6f
        val by = y + 42f
        val bw = tw - 12f
        p.color = 0xff505050.toInt()
        c.drawRoundRect(RectF(bx, by, bx + bw, by + 9f), 4f, 4f, p)
        p.color = colors[t]
        c.drawRoundRect(RectF(bx, by, bx + bw * volumes[t], by + 9f), 4f, 4f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT
        p.textSize = 9f
        p.color = 0xffa9a9a9.toInt()
        c.drawText("${(volumes[t] * 100f).roundToInt()}%", x + tw / 2f, y + 68f, p)
    }

    private fun drawFxMenu(c: Canvas, w: Float, h: Float) {
        val track = fxMenuTrack
        if (track !in 0..7) return
        p.style = Paint.Style.FILL
        p.color = Color.argb(205, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, p)

        val panel = fxPanel(w, h)
        p.color = 0xff242424.toInt()
        c.drawRoundRect(panel, 10f, 10f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = colors[track]
        c.drawRoundRect(panel, 10f, 10f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 20f
        p.color = 0xffeeeeee.toInt()
        c.drawText("CHANNEL FX • ${tracks[track]}", panel.left + 18f, panel.top + 30f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 10f
        p.color = 0xff999999.toInt()
        c.drawText("Two insert slots • changes apply immediately to the playing channel", panel.left + 18f, panel.top + 48f, p)

        button(c, RectF(panel.right - 74f, panel.top + 10f, panel.right - 14f, panel.top + 44f), "CLOSE", 0xff4a4a4a.toInt())

        val slotTop = panel.top + 62f
        val slotWidth = (panel.width() - 52f) / 2f
        for (slot in 0..1) {
            val r = RectF(panel.left + 18f + slot * (slotWidth + 16f), slotTop, panel.left + 18f + slot * (slotWidth + 16f) + slotWidth, slotTop + 38f)
            val fx = fxSlots[track][slot]
            val label = "SLOT ${slot + 1}: ${fx.kind.shortLabel}"
            button(c, r, label, if (fxMenuSlot == slot) colors[track] else 0xff414141.toInt())
        }

        val gridTop = slotTop + 52f
        val options = FxKind.entries
        val cols = 4
        val rows = ceil(options.size / cols.toFloat()).toInt()
        val cellGap = 8f
        val cellW = (panel.width() - 36f - cellGap * (cols - 1)) / cols
        val cellH = 48f
        options.forEachIndexed { index, kind ->
            val col = index % cols
            val row = index / cols
            val l = panel.left + 18f + col * (cellW + cellGap)
            val t = gridTop + row * (cellH + cellGap)
            val r = RectF(l, t, l + cellW, t + cellH)
            val selected = fxSlots[track][fxMenuSlot].kind == kind
            button(c, r, kind.label, if (selected) colors[track] else 0xff3c3c3c.toInt())
        }

        val amountY = gridTop + rows * (cellH + cellGap) + 16f
        val slot = fxSlots[track][fxMenuSlot]
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 11f
        p.color = 0xffdddddd.toInt()
        c.drawText("AMOUNT ${(slot.amount * 100f).roundToInt()}%", panel.left + 18f, amountY, p)
        val slider = fxAmountRect(panel, rows, gridTop, cellH, cellGap)
        p.color = 0xff515151.toInt()
        c.drawRoundRect(slider, 6f, 6f, p)
        p.color = colors[track]
        c.drawRoundRect(RectF(slider.left, slider.top, slider.left + slider.width() * slot.amount, slider.bottom), 6f, 6f, p)
        p.color = 0xfff0f0f0.toInt()
        c.drawCircle(slider.left + slider.width() * slot.amount, slider.centerY(), 8f, p)

        val onRect = RectF(panel.left + 18f, panel.bottom - 45f, panel.left + 135f, panel.bottom - 12f)
        button(c, onRect, if (slot.enabled) "SLOT ON" else "SLOT BYPASS", if (slot.enabled) 0xff5a765d.toInt() else 0xff5a4545.toInt())
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
        p.textSize = 10f
        p.color = 0xff989898.toInt()
        c.drawText("FX are saved per channel and restored on next launch.", panel.left + 150f, panel.bottom - 24f, p)
    }

    private fun fxPanel(w: Float, h: Float): RectF = RectF(58f, 58f, w - 58f, h - 42f)

    private fun fxAmountRect(panel: RectF, rows: Int, gridTop: Float, cellH: Float, cellGap: Float): RectF {
        val topY = gridTop + rows * (cellH + cellGap) + 24f
        return RectF(panel.left + 18f, topY, panel.right - 18f, topY + 14f)
    }

    private fun button(c: Canvas, r: RectF, label: String, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(r, 5f, 5f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = when {
            label.length > 13 -> 8f
            label.length > 7 -> 9f
            label.length > 4 -> 10f
            else -> 15f
        }
        p.color = 0xfff0f0f0.toInt()
        c.drawText(label, r.centerX(), r.centerY() + 4f, p)
    }

    private fun smallButton(c: Canvas, r: RectF, label: String, enabled: Boolean, onColor: Int) {
        p.style = Paint.Style.FILL
        p.color = if (enabled) onColor else 0xff4c4c4c.toInt()
        c.drawRoundRect(r, 3f, 3f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = if (label.length > 2) 7.5f else 9f
        p.color = 0xffeeeeee.toInt()
        c.drawText(label, r.centerX(), r.centerY() + 3f, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = SystemClock.elapsedRealtime()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val held = SystemClock.elapsedRealtime() - downAt
                if (fxMenuTrack in 0..7) handleFxTap(e.x, e.y) else handleTap(e.x, e.y, held >= 650L)
                return true
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float, longPress: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tw = (w - left - right - gap * 7f) / 8f
        val sh = (h - top - bottom - gap * 7f) / 8f

        if (y <= 68f) {
            when {
                x >= w - 285f && x < w - 216f -> toggleTransport()
                x >= w - 216f && x < w - 168f -> setBpm(bpm - 1f)
                x >= w - 168f && x < w - 102f -> tapTempo()
                x >= w - 102f && x < w - 53f -> setBpm(bpm + 1f)
                x >= w - 53f -> {
                    quantizeBeats = if (quantizeBeats == 4) 1 else 4
                    saveProject()
                    invalidate()
                }
            }
            return
        }

        if (x > w - right && y >= top && y < h - bottom) {
            val scene = ((y - top) / (sh + gap)).toInt()
            if (scene in 0..7) launchScene(scene)
            return
        }

        val t = ((x - left) / (tw + gap)).toInt()
        if (t !in 0..7) return

        if (y >= top && y < h - bottom) {
            val s = ((y - top) / (sh + gap)).toInt()
            if (s in 0..7) {
                val clip = clips[t][s]
                if (longPress && clip.uri != null) requestAudio(t, s)
                else if (clip.uri == null) requestAudio(t, s)
                else if (activeScene[t] == s && clip.state in 2..4) queueStop(t)
                else queueLaunch(t, s)
            }
            return
        }

        val mixerY = h - bottom + 12f
        if (y in mixerY..(mixerY + 78f)) {
            val localX = x - (left + t * (tw + gap))
            val buttonGap = 3f
            val buttonW = ((tw - 16f - buttonGap * 2f) / 3f).coerceAtLeast(16f)
            val aRight = 5f + buttonW
            val mLeft = aRight + buttonGap
            val mRight = mLeft + buttonW
            val fxLeft = mRight + buttonGap
            when {
                y < mixerY + 34f && localX in 5f..aRight -> {
                    armed[t] = !armed[t]
                    saveProject()
                    invalidate()
                }
                y < mixerY + 34f && localX in mLeft..mRight -> {
                    muted[t] = !muted[t]
                    applyTrackVolume(t)
                    saveProject()
                    invalidate()
                }
                y < mixerY + 34f && localX >= fxLeft -> {
                    fxMenuTrack = t
                    fxMenuSlot = 0
                    invalidate()
                }
                y >= mixerY + 34f -> {
                    volumes[t] = ((localX - 6f) / (tw - 12f)).coerceIn(0f, 1f)
                    applyTrackVolume(t)
                    saveProject()
                    invalidate()
                }
            }
        }
    }

    private fun handleFxTap(x: Float, y: Float) {
        val track = fxMenuTrack
        if (track !in 0..7) return
        val w = width.toFloat()
        val h = height.toFloat()
        val panel = fxPanel(w, h)

        if (!panel.contains(x, y)) {
            fxMenuTrack = -1
            invalidate()
            return
        }
        if (x >= panel.right - 82f && y <= panel.top + 52f) {
            fxMenuTrack = -1
            invalidate()
            return
        }

        val slotTop = panel.top + 62f
        val slotWidth = (panel.width() - 52f) / 2f
        for (slot in 0..1) {
            val r = RectF(panel.left + 18f + slot * (slotWidth + 16f), slotTop, panel.left + 18f + slot * (slotWidth + 16f) + slotWidth, slotTop + 38f)
            if (r.contains(x, y)) {
                fxMenuSlot = slot
                invalidate()
                return
            }
        }

        val gridTop = slotTop + 52f
        val options = FxKind.entries
        val cols = 4
        val rows = ceil(options.size / cols.toFloat()).toInt()
        val cellGap = 8f
        val cellW = (panel.width() - 36f - cellGap * (cols - 1)) / cols
        val cellH = 48f
        options.forEachIndexed { index, kind ->
            val col = index % cols
            val row = index / cols
            val l = panel.left + 18f + col * (cellW + cellGap)
            val t = gridTop + row * (cellH + cellGap)
            val r = RectF(l, t, l + cellW, t + cellH)
            if (r.contains(x, y)) {
                val slot = fxSlots[track][fxMenuSlot]
                slot.kind = kind
                if (kind == FxKind.NONE) slot.enabled = false else if (!slot.enabled) slot.enabled = true
                rebuildTrackEffects(track)
                saveProject()
                invalidate()
                return
            }
        }

        val slider = fxAmountRect(panel, rows, gridTop, cellH, cellGap)
        if (y in (slider.top - 16f)..(slider.bottom + 18f)) {
            fxSlots[track][fxMenuSlot].amount = ((x - slider.left) / slider.width()).coerceIn(0f, 1f)
            rebuildTrackEffects(track)
            saveProject()
            invalidate()
            return
        }

        val onRect = RectF(panel.left + 18f, panel.bottom - 45f, panel.left + 135f, panel.bottom - 12f)
        if (onRect.contains(x, y)) {
            val slot = fxSlots[track][fxMenuSlot]
            if (slot.kind != FxKind.NONE) slot.enabled = !slot.enabled
            rebuildTrackEffects(track)
            saveProject()
            invalidate()
        }
    }

    private fun toggleTransport() {
        if (playing) {
            playing = false
            for (t in 0..7) {
                pending[t]?.let { handler.removeCallbacks(it) }
                pending[t] = null
                stopTrackNow(t)
            }
        } else {
            playing = true
            transportStart = SystemClock.elapsedRealtime()
        }
        invalidate()
    }

    private fun tapTempo() {
        val now = SystemClock.elapsedRealtime()
        if (lastTapAt > 0L) {
            val interval = now - lastTapAt
            if (interval in 200L..1500L) {
                tapIntervals.addLast(interval)
                while (tapIntervals.size > 4) tapIntervals.removeFirst()
                val average = tapIntervals.average()
                setBpm((60000.0 / average).toFloat())
            } else {
                tapIntervals.clear()
            }
        }
        lastTapAt = now
    }

    private fun setBpm(value: Float) {
        bpm = value.coerceIn(40f, 300f)
        if (playing) transportStart = SystemClock.elapsedRealtime()
        saveProject()
        invalidate()
    }

    private fun launchScene(scene: Int) {
        if (!playing) {
            playing = true
            transportStart = SystemClock.elapsedRealtime()
        }
        for (t in 0..7) if (clips[t][scene].uri != null) queueLaunch(t, scene)
    }

    private fun queueLaunch(track: Int, scene: Int) {
        if (!playing) {
            playing = true
            transportStart = SystemClock.elapsedRealtime()
        }
        pending[track]?.let { handler.removeCallbacks(it) }
        for (s in 0..7) if (clips[track][s].state == 2 || clips[track][s].state == 4) clips[track][s].state = 1
        clips[track][scene].state = 2
        activeScene[track] = scene
        val job = Runnable { launchNow(track, scene) }
        pending[track] = job
        handler.postDelayed(job, delayToBoundary())
        invalidate()
    }

    private fun queueStop(track: Int) {
        val scene = activeScene[track]
        if (scene !in 0..7) return
        pending[track]?.let { handler.removeCallbacks(it) }
        clips[track][scene].state = 4
        val job = Runnable {
            stopTrackNow(track)
            invalidate()
        }
        pending[track] = job
        handler.postDelayed(job, delayToBoundary())
        invalidate()
    }

    private fun delayToBoundary(): Long {
        if (!playing) return 0L
        val beatMs = 60000.0 / bpm
        val quantum = (beatMs * quantizeBeats).toLong().coerceAtLeast(1L)
        val elapsed = (SystemClock.elapsedRealtime() - transportStart).coerceAtLeast(0L)
        val phase = elapsed % quantum
        return if (phase < 20L) 0L else quantum - phase
    }

    private fun launchNow(track: Int, scene: Int) {
        val uriString = clips[track][scene].uri ?: return
        pending[track] = null
        stopTrackNow(track)
        try {
            val player = MediaPlayer.create(context, Uri.parse(uriString)) ?: run {
                clips[track][scene].state = 1
                invalidate()
                return
            }
            player.isLooping = true
            players[track] = player
            activeScene[track] = scene
            clips[track][scene].state = 3
            applyTrackVolume(track)
            applyTrackEffects(track, player)
            player.start()
        } catch (_: Exception) {
            clips[track][scene].state = 1
            activeScene[track] = -1
        }
        invalidate()
    }

    private fun stopTrackNow(track: Int) {
        releaseTrackEffects(track)
        try {
            players[track]?.stop()
        } catch (_: Exception) {
        }
        players[track]?.release()
        players[track] = null
        for (s in 0..7) clips[track][s].state = if (clips[track][s].uri != null) 1 else 0
        activeScene[track] = -1
    }

    private fun applyTrackVolume(track: Int) {
        val gain = if (muted[track]) 0f else volumes[track]
        try {
            players[track]?.setVolume(gain, gain)
        } catch (_: Exception) {
        }
    }

    private fun rebuildTrackEffects(track: Int) {
        val player = players[track] ?: return
        releaseTrackEffects(track)
        applyTrackEffects(track, player)
    }

    private fun releaseTrackEffects(track: Int) {
        liveEffects[track].forEach { effect ->
            try {
                effect.enabled = false
                effect.release()
            } catch (_: Exception) {
            }
        }
        liveEffects[track].clear()
    }

    private fun applyTrackEffects(track: Int, player: MediaPlayer) {
        val sessionId = player.audioSessionId
        fxSlots[track].forEach { slot ->
            if (!slot.enabled || slot.kind == FxKind.NONE) return@forEach
            val effect = createEffect(slot, sessionId)
            if (effect != null) liveEffects[track].add(effect)
        }
    }

    private fun createEffect(slot: FxSlot, sessionId: Int): AudioEffect? {
        val amount = slot.amount.coerceIn(0f, 1f)
        return try {
            when (slot.kind) {
                FxKind.NONE -> null
                FxKind.BASS -> BassBoost(0, sessionId).apply {
                    setStrength((amount * 1000f).roundToInt().toShort())
                    enabled = true
                }
                FxKind.WIDTH -> Virtualizer(0, sessionId).apply {
                    setStrength((amount * 1000f).roundToInt().toShort())
                    enabled = true
                }
                FxKind.LOW_EQ -> Equalizer(0, sessionId).apply {
                    shapeEq(this, amount, FxKind.LOW_EQ)
                    enabled = true
                }
                FxKind.HIGH_EQ -> Equalizer(0, sessionId).apply {
                    shapeEq(this, amount, FxKind.HIGH_EQ)
                    enabled = true
                }
                FxKind.MID_CUT -> Equalizer(0, sessionId).apply {
                    shapeEq(this, amount, FxKind.MID_CUT)
                    enabled = true
                }
                FxKind.REVERB -> PresetReverb(0, sessionId).apply {
                    preset = when {
                        amount < 0.25f -> PresetReverb.PRESET_SMALLROOM
                        amount < 0.50f -> PresetReverb.PRESET_MEDIUMROOM
                        amount < 0.75f -> PresetReverb.PRESET_LARGEROOM
                        else -> PresetReverb.PRESET_LARGEHALL
                    }
                    enabled = true
                }
                FxKind.LOUDNESS -> LoudnessEnhancer(sessionId).apply {
                    setTargetGain((amount * 1200f).roundToInt())
                    enabled = true
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun shapeEq(eq: Equalizer, amount: Float, kind: FxKind) {
        val range = eq.bandLevelRange
        val maxBoost = range[1].toInt().coerceAtLeast(0)
        val minCut = range[0].toInt().coerceAtMost(0)
        for (band in 0 until eq.numberOfBands.toInt()) {
            val b = band.toShort()
            val centerHz = eq.getCenterFreq(b) / 1000
            val level = when (kind) {
                FxKind.LOW_EQ -> if (centerHz <= 500) (maxBoost * amount * 0.8f).roundToInt() else 0
                FxKind.HIGH_EQ -> if (centerHz >= 3000) (maxBoost * amount * 0.8f).roundToInt() else 0
                FxKind.MID_CUT -> if (centerHz in 500..3500) (minCut * amount * 0.65f).roundToInt() else 0
                else -> 0
            }.coerceIn(minCut, maxBoost)
            eq.setBandLevel(b, level.toShort())
        }
    }

    fun shutdown() {
        for (t in 0..7) {
            pending[t]?.let { handler.removeCallbacks(it) }
            pending[t] = null
            stopTrackNow(t)
        }
        saveProject()
    }

    private fun formatBpm(): String =
        if (bpm % 1f == 0f) bpm.toInt().toString() else String.format("%.1f", bpm)

    private fun ellipsize(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"

    private fun blend(a: Int, b: Int, amountB: Float): Int {
        val k = amountB.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1f - k) + Color.red(b) * k).roundToInt()
        val g = (Color.green(a) * (1f - k) + Color.green(b) * k).roundToInt()
        val bl = (Color.blue(a) * (1f - k) + Color.blue(b) * k).roundToInt()
        return Color.rgb(r, g, bl)
    }
}
