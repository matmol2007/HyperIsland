package com.hyperisland.app

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Damped spring (iOS-like). zeta 0.35 = very bouncy, 1.0 = no overshoot. */
class SpringInterpolator(zeta: Float) : TimeInterpolator {
    private val z = zeta.coerceIn(0.35f, 1f)
    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        if (z >= 0.999f) {
            val w = 6f
            return 1f - exp(-w * t) * (1f + w * t)
        }
        val w = 4.6f / z
        val wd = w * sqrt(1f - z * z)
        return 1f - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }
}

class EqualizerView(ctx: Context) : View(ctx) {
    var running: Boolean = false
        set(v) {
            val was = field
            field = v
            if (v && !was) invalidate()
        }
    var color: Int = Color.WHITE
        set(v) { field = v; paint.color = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val n = 4
        val gap = width * 0.12f
        val bw = (width - gap * (n - 1)) / n
        val t = SystemClock.uptimeMillis() / 1000f
        for (i in 0 until n) {
            val a = if (running) 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (5f + i * 1.7f) + i * 1.3f)) else 0.22f
            val h = height * a
            val l = i * (bw + gap)
            rect.set(l, (height - h) / 2f, l + bw, (height + h) / 2f)
            c.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
        if (running) postInvalidateOnAnimation()
    }
}

/** Thin progress bar. If seekable, the user can drag it (used for music). */
class ThinBar(ctx: Context) : View(ctx) {
    var value: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var fill: Int = Color.WHITE
        set(v) { field = v; fp.color = v; invalidate() }
    var track: Int = 0x33FFFFFF
        set(v) { field = v; tp.color = v; invalidate() }
    var thickness: Float = 0f          // 0 = full view height
    var seekable: Boolean = false
    var dragging: Boolean = false
        private set
    var onSeek: ((Float) -> Unit)? = null

    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val t0 = if (thickness > 0f) thickness else height.toFloat()
        val t = if (dragging) t0 * 1.7f else t0
        r.set(0f, cy - t / 2f, width.toFloat(), cy + t / 2f)
        c.drawRoundRect(r, t / 2f, t / 2f, tp)
        val w = width * value
        if (w > 0f) {
            r.set(0f, cy - t / 2f, maxOf(w, t), cy + t / 2f)
            c.drawRoundRect(r, t / 2f, t / 2f, fp)
        }
        if (seekable && dragging) c.drawCircle(maxOf(w, t / 2f), cy, t * 0.95f, fp)
    }

    private fun setFromX(x: Float) {
        if (width > 0) value = x / width
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!seekable) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                setFromX(ev.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> { setFromX(ev.x); return true }
            MotionEvent.ACTION_UP -> {
                setFromX(ev.x)
                dragging = false
                invalidate()
                onSeek?.invoke(value)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate(); return true }
        }
        return true
    }
}
