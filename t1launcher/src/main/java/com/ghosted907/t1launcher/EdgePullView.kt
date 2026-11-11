package com.ghosted907.t1launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator

/** One renderer and gesture implementation for both mirrored edge buttons. */
internal open class EdgePullView(context: Context, private val fromRight: Boolean) : View(context) {
    var onOpen: (() -> Unit)? = null
    var onWindowWidth: ((Int) -> Unit)? = null
    var windowWidth: Int = EdgePullGesture.TIP_PX
        private set
    private var reveal = EdgePullGesture.TIP_PX.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var touching = false
    private var tracker: VelocityTracker? = null
    private var retreat: ValueAnimator? = null
    private val shape = Path()
    private val arrow = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x80FFFFFF.toInt()
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xB3FFFFFF.toInt()
    }

    init {
        isClickable = true
        isFocusable = false
        contentDescription = if (fromRight) "\u0641\u062a\u062d T1"
            else "\u0641\u062a\u062d \u064a\u0648\u062a\u064a\u0648\u0628"
    }

    private fun updateWindowWidth(value: Int) {
        if (windowWidth == value) return
        windowWidth = value
        onWindowWidth?.invoke(value)
    }

    override fun onDraw(canvas: Canvas) {
        val w = EdgeTab.WIDTH_PX.toFloat()
        val h = height.toFloat()
        if (h <= 0f) return
        val saved = canvas.save()
        // Only a ten-pixel window exists at rest. Expand it only for an active pull/return.
        canvas.translate(if (fromRight) width - reveal else reveal - w, 0f)
        if (!fromRight) {
            canvas.translate(w, 0f)
            canvas.scale(-1f, 1f)
        }
        shape.rewind()
        val curves = EdgeTab.curves(w, h)
        val start = curves.first().a
        shape.moveTo(start.x, start.y)
        for (curve in curves) {
            shape.cubicTo(curve.b.x, curve.b.y, curve.c.x, curve.c.y, curve.d.x, curve.d.y)
        }
        shape.close()
        fill.color = if (isPressed) 0x44FFFFFF else 0x22FFFFFF
        canvas.drawPath(shape, fill)
        canvas.drawPath(shape, stroke)
        val cx = w * 0.60f
        val cy = h * 0.52f
        arrow.rewind()
        arrow.moveTo(cx + 5f, cy - 9f)
        arrow.lineTo(cx - 9f, cy)
        arrow.lineTo(cx + 5f, cy + 9f)
        arrow.close()
        canvas.drawPath(arrow, mark)
        canvas.restoreToCount(saved)
    }

    /** Window resizing changes local event coordinates; velocity must stay in screen coordinates. */
    private fun trackRaw(event: MotionEvent) {
        val raw = MotionEvent.obtain(event)
        raw.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker?.addMovement(raw)
        raw.recycle()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                retreat?.cancel()
                retreat = null
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                trackRaw(event)
                downX = event.rawX
                downY = event.rawY
                touching = true
                isPressed = true
                updateWindowWidth(EdgeTab.WIDTH_PX)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (touching) {
                trackRaw(event)
                reveal = EdgePullGesture.visibleWidth(EdgePullGesture.inward(fromRight, event.rawX - downX))
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (touching) {
                trackRaw(event)
                tracker?.computeCurrentVelocity(1000)
                val inward = EdgePullGesture.inward(fromRight, event.rawX - downX)
                val velocity = EdgePullGesture.inward(fromRight, tracker?.xVelocity ?: 0f)
                val open = EdgePullGesture.opens(inward, event.rawY - downY, velocity)
                touching = false
                tracker?.recycle()
                tracker = null
                isPressed = false
                Log.i("T1Launcher", "edgePull ${if (fromRight) "right" else "left"} dx=$inward velocity=$velocity open=$open")
                animateHome(open)
            }
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                tracker?.recycle()
                tracker = null
                isPressed = false
                animateHome(false)
            }
        }
        return true
    }

    private fun animateHome(open: Boolean) {
        retreat?.cancel()
        retreat = ValueAnimator.ofFloat(reveal, EdgePullGesture.TIP_PX.toFloat()).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    retreat = null
                    reveal = EdgePullGesture.TIP_PX.toFloat()
                    updateWindowWidth(EdgePullGesture.TIP_PX)
                    invalidate()
                    if (open && isAttachedToWindow) performClick()
                }
            })
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        onOpen?.invoke()
        return true
    }

    override fun onDetachedFromWindow() {
        retreat?.cancel()
        retreat = null
        tracker?.recycle()
        tracker = null
        touching = false
        reveal = EdgePullGesture.TIP_PX.toFloat()
        windowWidth = EdgePullGesture.TIP_PX
        super.onDetachedFromWindow()
    }
}