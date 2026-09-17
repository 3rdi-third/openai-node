package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import java.util.ArrayDeque
import kotlin.math.roundToInt

class MidiSyncLayout(
    context: Context,
    private val sessionView: FxSessionView
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("session_grid_project", Context.MODE_PRIVATE)
    private val activity = context as Activity

    private val midiButton = Button(context)
    private val link = BleMidiLink(context, ::onLinkState, ::onMidi)

    private var downAt = 0L
    private var beforePlaying = false

    private var tempoMsb = 0
    private var lastClockNs = 0L
    private var remoteClockCount = 0L
    private val clockIntervals = ArrayDeque<Long>()

    private var nextClockNs = 0L
    private val hostClock = object : Runnable {
        override fun run() {
            if (!link.isHostMaster || !readPlaying()) return
            link.sendMidi(byteArrayOf(0xF8.toByte()))
            val bpm = readBpm().coerceIn(40f, 300f)
            val periodNs = (60_000_000_000.0 / (bpm * 24.0)).toLong().coerceAtLeast(1L)
            if (nextClockNs == 0L) nextClockNs = SystemClock.elapsedRealtimeNanos()
            nextClockNs += periodNs
            val waitNs = (nextClockNs - SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0L)
            handler.postDelayed(this, waitNs / 1_000_000L)
        }
    }

    init {
        addView(sessionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        midiButton.apply {
            text = "BLE OFF"
            textSize = 10f
            setTextColor(Color.WHITE)
            minWidth = 0
            minHeight = 0
            setPadding(4, 0, 4, 0)
            backgroundTintList = ColorStateList.valueOf(0xff454545.toInt())
            setOnClickListener { link.cycle(activity) }
        }
        addView(
            midiButton,
            LayoutParams(dp(102), dp(46), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(205)
                topMargin = dp(7)
            }
        )
    }

    fun requestBluetoothPermissions() {
        link.requestPermissions(activity)
    }

    fun shutdown() {
        handler.removeCallbacks(hostClock)
        link.stop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downAt = SystemClock.elapsedRealtime()
            beforePlaying = readPlaying()
        }

        val handled = super.dispatchTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_UP && !midiButtonHit(ev.x, ev.y)) {
            val held = SystemClock.elapsedRealtime() - downAt
            handleLocalAction(ev.x, ev.y, held >= 650L)
        }
        return handled
    }

    private fun handleLocalAction(x: Float, y: Float, longPress: Boolean) {
        val w = sessionView.width.toFloat()
        val h = sessionView.height.toFloat()
        if (w <= 0f || h <= 0f) return

        val afterPlaying = readPlaying()
        if (afterPlaying != beforePlaying) {
            link.sendMidi(byteArrayOf(if (afterPlaying) 0xFA.toByte() else 0xFC.toByte()))
            if (afterPlaying) startHostClockIfNeeded() else stopHostClock()
        }

        if (y <= 68f) {
            when {
                x >= w - 216f && x < w - 53f -> sendTempo(readBpm())
                x >= w - 53f -> sendQuantize(readQuantize())
            }
            return
        }

        val top = 82f
        val left = 56f
        val right = 64f
        val bottom = 118f
        val gap = 4f
        val tw = (w - left - right - gap * 7f) / 8f
        val sh = (h - top - bottom - gap * 7f) / 8f

        if (x > w - right && y >= top && y < h - bottom) {
            val scene = ((y - top) / (sh + gap)).toInt()
            if (scene in 0..7) {
                link.sendMidi(byteArrayOf(0x9F.toByte(), (112 + scene).toByte(), 127.toByte()))
            }
            return
        }

        val track = ((x - left) / (tw + gap)).toInt()
        if (track !in 0..7 || y < top || y >= h - bottom || longPress) return
        val scene = ((y - top) / (sh + gap)).toInt()
        if (scene !in 0..7) return

        when (readClipState(track, scene)) {
            2, 3 -> link.sendMidi(byteArrayOf(0x9F.toByte(), (track * 8 + scene).toByte(), 127.toByte()))
            4 -> link.sendMidi(byteArrayOf(0x9F.toByte(), (96 + track).toByte(), 127.toByte()))
        }
    }

    private fun onLinkState(state: BleMidiLink.State) {
        midiButton.text = when (state) {
            BleMidiLink.State.OFF -> "BLE OFF"
            BleMidiLink.State.PERMISSION -> "BLE PERM"
            BleMidiLink.State.HOSTING -> "BLE HOST"
            BleMidiLink.State.SCANNING -> "BLE JOIN…"
            BleMidiLink.State.HOST_CONNECTED -> "HOST ✓"
            BleMidiLink.State.JOIN_CONNECTED -> "JOIN ✓"
            BleMidiLink.State.UNAVAILABLE -> "BLE N/A"
            BleMidiLink.State.ERROR -> "BLE ERR"
        }
        midiButton.backgroundTintList = ColorStateList.valueOf(
            when (state) {
                BleMidiLink.State.HOST_CONNECTED, BleMidiLink.State.JOIN_CONNECTED -> 0xff4f7f57.toInt()
                BleMidiLink.State.HOSTING, BleMidiLink.State.SCANNING -> 0xff725d38.toInt()
                BleMidiLink.State.ERROR, BleMidiLink.State.UNAVAILABLE -> 0xff7b3f3f.toInt()
                else -> 0xff454545.toInt()
            }
        )

        if (state == BleMidiLink.State.HOST_CONNECTED) {
            sendTempo(readBpm())
            sendQuantize(readQuantize())
            link.sendMidi(byteArrayOf(if (readPlaying()) 0xFA.toByte() else 0xFC.toByte()))
            startHostClockIfNeeded()
        }
    }

    private fun onMidi(message: ByteArray) {
        if (message.isEmpty()) return
        val status = message[0].toInt() and 0xff
        when (status) {
            0xF8 -> receiveClock()
            0xFA -> setRemotePlaying(true)
            0xFC -> setRemotePlaying(false)
            0x9F -> if (message.size >= 3 && (message[2].toInt() and 0x7f) > 0) {
                val note = message[1].toInt() and 0x7f
                when (note) {
                    in 0..63 -> invokePrivate("queueLaunch", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), note / 8, note % 8)
                    in 96..103 -> invokePrivate("queueStop", arrayOf(Int::class.javaPrimitiveType!!), note - 96)
                    in 112..119 -> invokePrivate("launchScene", arrayOf(Int::class.javaPrimitiveType!!), note - 112)
                }
            }
            0xBF -> if (message.size >= 3) receiveControl(message[1].toInt() and 0x7f, message[2].toInt() and 0x7f)
        }
    }

    private fun receiveControl(cc: Int, value: Int) {
        when (cc) {
            20 -> tempoMsb = value and 0x7f
            52 -> {
                val bpm10 = ((tempoMsb and 0x7f) shl 7) or (value and 0x7f)
                val bpm = (bpm10 / 10f).coerceIn(40f, 300f)
                setBpmDirect(bpm)
                if (link.isHostMaster) startHostClockIfNeeded()
            }
            21 -> setQuantizeDirect(if (value >= 4) 4 else 1)
        }
    }

    private fun receiveClock() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (lastClockNs > 0L) {
            val interval = now - lastClockNs
            if (interval in 2_000_000L..100_000_000L) {
                clockIntervals.addLast(interval)
                while (clockIntervals.size > 48) clockIntervals.removeFirst()
            }
        }
        lastClockNs = now
        remoteClockCount++

        if (clockIntervals.size >= 12 && remoteClockCount % 6L == 0L) {
            val average = clockIntervals.average()
            val bpm = (60_000_000_000.0 / (average * 24.0)).toFloat()
            if (bpm in 40f..300f) setBpmDirect(bpm)
        }

        if (readPlaying() && remoteClockCount % 24L == 0L) {
            val bpm = readBpm().coerceIn(40f, 300f)
            val tickMs = 60_000.0 / (bpm * 24.0)
            val quantize = readQuantize().coerceAtLeast(1)
            val cycleTicks = 24L * quantize
            val phaseTicks = remoteClockCount % cycleTicks
            setLongField("transportStart", SystemClock.elapsedRealtime() - (phaseTicks * tickMs).toLong())
        }
    }

    private fun setRemotePlaying(shouldPlay: Boolean) {
        if (readPlaying() != shouldPlay) invokePrivate("toggleTransport", emptyArray())
        if (shouldPlay) {
            remoteClockCount = 0L
            lastClockNs = 0L
            clockIntervals.clear()
            startHostClockIfNeeded()
        } else {
            stopHostClock()
        }
    }

    private fun sendTempo(bpm: Float) {
        val bpm10 = (bpm.coerceIn(40f, 300f) * 10f).roundToInt().coerceIn(0, 16383)
        link.sendMidi(byteArrayOf(0xBF.toByte(), 20.toByte(), ((bpm10 shr 7) and 0x7f).toByte()))
        link.sendMidi(byteArrayOf(0xBF.toByte(), 52.toByte(), (bpm10 and 0x7f).toByte()))
    }

    private fun sendQuantize(value: Int) {
        link.sendMidi(byteArrayOf(0xBF.toByte(), 21.toByte(), value.coerceIn(0, 127).toByte()))
    }

    private fun startHostClockIfNeeded() {
        handler.removeCallbacks(hostClock)
        if (!link.isHostMaster || !readPlaying()) return
        nextClockNs = SystemClock.elapsedRealtimeNanos()
        handler.post(hostClock)
    }

    private fun stopHostClock() {
        nextClockNs = 0L
        handler.removeCallbacks(hostClock)
    }

    private fun readPlaying(): Boolean = readField("playing") as? Boolean ?: false

    private fun readBpm(): Float = (readField("bpm") as? Float ?: prefs.getFloat("bpm", 133f)).coerceIn(40f, 300f)

    private fun readQuantize(): Int = (readField("quantizeBeats") as? Int ?: prefs.getInt("quantize", 4)).let { if (it == 1) 1 else 4 }

    private fun readClipState(track: Int, scene: Int): Int {
        return try {
            val rows = readField("clips") as Array<*>
            val row = rows[track] as Array<*>
            val clip = row[scene] ?: return 0
            val field = clip.javaClass.getDeclaredField("state")
            field.isAccessible = true
            field.getInt(clip)
        } catch (_: Throwable) {
            0
        }
    }

    private fun setBpmDirect(value: Float) {
        val bpm = value.coerceIn(40f, 300f)
        setField("bpm", bpm)
        prefs.edit().putFloat("bpm", bpm).apply()
        sessionView.invalidate()
    }

    private fun setQuantizeDirect(value: Int) {
        val quantize = if (value == 1) 1 else 4
        setField("quantizeBeats", quantize)
        prefs.edit().putInt("quantize", quantize).apply()
        sessionView.invalidate()
    }

    private fun readField(name: String): Any? {
        return try {
            val field = sessionView.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(sessionView)
        } catch (_: Throwable) {
            null
        }
    }

    private fun setField(name: String, value: Any) {
        try {
            val field = sessionView.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.set(sessionView, value)
        } catch (_: Throwable) {
        }
    }

    private fun setLongField(name: String, value: Long) {
        try {
            val field = sessionView.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.setLong(sessionView, value)
        } catch (_: Throwable) {
        }
    }

    private fun invokePrivate(name: String, types: Array<Class<*>>, vararg args: Any) {
        try {
            val method = sessionView.javaClass.getDeclaredMethod(name, *types)
            method.isAccessible = true
            method.invoke(sessionView, *args)
        } catch (_: Throwable) {
        }
    }

    private fun midiButtonHit(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        midiButton.getLocationOnScreen(location)
        val rootLocation = IntArray(2)
        getLocationOnScreen(rootLocation)
        val left = location[0] - rootLocation[0]
        val top = location[1] - rootLocation[1]
        return x >= left && x <= left + midiButton.width && y >= top && y <= top + midiButton.height
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
