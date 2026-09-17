package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.VideoView

class StartupActivity : Activity() {
    private var finished = false
    private lateinit var videoView: VideoView
    private val handler = Handler(Looper.getMainLooper())
    private val failSafe = Runnable { finishIntro() }

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

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnPreparedListener { player ->
                player.isLooping = false
                start()
            }
            setOnCompletionListener {
                finishIntro()
            }
            setOnErrorListener { _, _, _ ->
                // A codec failure must never prevent the main app from opening.
                finishIntro()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    finishIntro()
                }
                true
            }
        }

        root.addView(videoView)
        setContentView(root)

        // Guarantees entry to the main app even on devices with a broken/stalled codec callback.
        handler.postDelayed(failSafe, 6500L)

        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.startup_intro}")
        videoView.setVideoURI(videoUri)
    }

    private fun finishIntro() {
        if (finished || isFinishing || isDestroyed) return
        finished = true
        handler.removeCallbacks(failSafe)
        try {
            videoView.stopPlayback()
        } catch (_: Throwable) {
        }
        startActivity(Intent(this, FxMainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        finished = true
        handler.removeCallbacks(failSafe)
        if (::videoView.isInitialized) {
            try {
                videoView.stopPlayback()
            } catch (_: Throwable) {
            }
        }
        super.onDestroy()
    }
}
