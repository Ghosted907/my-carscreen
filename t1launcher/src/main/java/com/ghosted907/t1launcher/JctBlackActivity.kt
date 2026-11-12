package com.ghosted907.t1launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextClock

/** A dim, distraction-free screen with an optional clock. Tap anywhere to exit. */
class JctBlackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.also {
            it.screenBrightness = JctStore.int(this, KEY_BRIGHTNESS, 10).coerceIn(1, 100) / 100f
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        if (JctStore.bool(this, KEY_CLOCK, true)) {
            root.addView(TextClock(this).apply {
                format24Hour = "HH:mm"
                format12Hour = if (JctStore.bool(this@JctBlackActivity, KEY_CLOCK_24, true)) "HH:mm" else "h:mm a"
                setTextColor(Color.rgb(70, 78, 74))
                textSize = 96f
                textDirection = View.TEXT_DIRECTION_LTR
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        setContentView(root)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    companion object {
        const val KEY_CLOCK = "black_clock"
        const val KEY_CLOCK_24 = "black_clock_24"
        const val KEY_BRIGHTNESS = "black_brightness"
    }
}
