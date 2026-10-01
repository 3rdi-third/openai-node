package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

private const val SAFE_PICK_AUDIO = 5303

class SafeMainActivity : Activity() {
    private var sessionView: FxSessionView? = null
    private var midiLayout: MidiSyncLayout? = null
    private var pendingTrack = -1
    private var pendingScene = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 5894
            val view = FxSessionView(this, ::pickAudio)
            sessionView = view
            val syncLayout = MidiSyncLayout(this, view)
            midiLayout = syncLayout
            setContentView(syncLayout)
            syncLayout.requestBluetoothPermissions()
        } catch (t: Throwable) {
            showCrashScreen(t)
        }
    }

    private fun pickAudio(track: Int, scene: Int) {
        pendingTrack = track
        pendingScene = scene
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, SAFE_PICK_AUDIO)
        } catch (t: Throwable) {
            showCrashScreen(t)
        }
    }

    @Deprecated("Classic activity result API keeps this build dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAFE_PICK_AUDIO || resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }

        try {
            if (pendingTrack in 0..7 && pendingScene in 0..7) {
                sessionView?.assignClip(pendingTrack, pendingScene, uri)
            }
        } catch (t: Throwable) {
            showCrashScreen(t)
        }
    }

    override fun onDestroy() {
        try {
            midiLayout?.shutdown()
            sessionView?.shutdown()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun showCrashScreen(t: Throwable) {
        val message = buildString {
            append("3rdi Session Grid\n\n")
            append("The audio grid could not start on this device.\n\n")
            append(t.javaClass.simpleName)
            t.message?.let { append(": ").append(it) }
        }

        val errorView = TextView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = message
        }
        setContentView(errorView)
    }
}
