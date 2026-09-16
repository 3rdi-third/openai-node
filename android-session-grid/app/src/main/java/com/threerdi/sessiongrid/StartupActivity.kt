package com.threerdi.sessiongrid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.VideoView

class StartupActivity : Activity() {
    private var finished = false
    private lateinit var videoView: VideoView

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
                // Never trap the user on the splash screen if a device codec refuses playback.
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

        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.startup_intro}")
        videoView.setVideoURI(videoUri)
        videoView.start()
    }

    private fun finishIntro() {
        if (finished) return
        finished = true
        try {
            videoView.stopPlayback()
        } catch (_: Throwable) {
        }
        startActivity(Intent(this, FxMainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        try {
            videoView.stopPlayback()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
