package com.hyperisland.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/**
 * The brain: keeps events, decides what to show, and animates the pill.
 * Performance: the overlay WINDOW is resized only twice per transition (start / end);
 * every animation frame only moves the drawn pill inside IslandView (no layout, no IPC).
 */
class IslandController(
    private val ctx: Context,
    private val wm: WindowManager,
    private val type: Int,
    val prio: Int
) : IslandView.Callbacks {

    val view = IslandView(ctx)
    private val lp = WindowManager.LayoutParams(
        2, 2, type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    )

    private var attached = false
    private var animator: ValueAnimator? = null
    private val sticky = LinkedHashMap<String, IslandEvent>()
    private val dismissed = HashSet<String>()
    private var popup: IslandEvent? = null
    private var shown: IslandEvent? = null

    private var pillW = 0f
    private var pillH = 0f
    private var pillR = 0f
    private var pillA = 0f
    private var tw = -1
    private var th = -1
    private var texp = false
    private var tex = 0
    private var focusKey: String? = null      // island the user switched to (tap a side bubble / swipe)
    private var pinnedKey: String? = null     // pinned island: never dismissed
    private var secList: List<IslandEvent> = emptyList()
    // Extra room so spring overshoot and inner padding never clip titles/controls
    private val slack = dp(40)

    private var landscape = false
    private var appHidden = false

    private var monitor: SystemMonitor? = null
    private var calls: CallMonitor? = null

    private val popupEnd = Runnable { popup = null; refresh() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mainHandler.post { onPrefsChanged() }
    }

    init {
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.title = "HyperIsland"
        disableWindowMoveAnimation()
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.cb = this
        applyPosition()
    }

    /**
     * The window is centered, so every time its width changes its left edge moves, and the
     * system plays a "move animation" on the whole window surface (decaying slide, ~6 frames).
     * Our pill is already drawn at its final spot inside the window, so that slide shows up as
     * the island jumping sideways and wobbling back to center. PRIVATE_FLAG_NO_MOVE_ANIMATION
     * (0x40) tells WindowManager to snap instead. Hidden API -> reflection, silently ignored if blocked.
     */
    private fun disableWindowMoveAnimation() {
        try {
            val f = WindowManager.LayoutParams::class.java.getField("privateFlags")
            f.setInt(lp, f.getInt(lp) or 0x00000040)
        } catch (t: Throwable) {
            Log.w("HyperIsland", "NO_MOVE_ANIMATION unavailable: $t")
        }
    }

    fun start() {
        Prefs.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        monitor = SystemMonitor(ctx, this).also { it.start() }
        calls = CallMonitor(ctx).also { it.start() }
        IslandRuntime.callMonitor = calls
    }

    fun stop() {
        try { Prefs.prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (e: Exception) { }
        monitor?.stop(); monitor = null
        if (IslandRuntime.callMonitor === calls) IslandRuntime.callMonitor = null
        calls?.stop(); calls = null
        animator?.cancel()
        mainHandler.removeCallbacks(popupEnd)
        view.animate().cancel()
        removeWindow()
    }

    fun snapshot(): List<IslandEvent> = sticky.values.toList()
    fun restore(list: List<IslandEvent>) { for (e in list) { sticky[e.key] = e }; refresh() }

    fun post(e: IslandEvent) {
        if (e.key == pinnedKey) e.sticky = true
        if (e.sticky) {
            val isNew = !sticky.containsKey(e.key)
            sticky[e.key] = e
            if (isNew || e.reshow) dismissed.remove(e.key)
        }
        if (!Prefs.bool("enabled")) return
        val cur = popup
        if (cur != null && cur.key == e.key) {
            popup = e
            if (e.popup) startPopupTimer(e)
        } else if (e.popup && !(e.sticky && e.key in dismissed)) {
            if (cur == null || cur.priority <= e.priority || cur.ttlMs != 0L) {
                popup = e
                startPopupTimer(e)
            }
        }
        refresh()
    }

    fun remove(key: String) {
        sticky.remove(key)
        dismissed.remove(key)
        if (pinnedKey == key) pinnedKey = null
        if (focusKey == key) focusKey = null
        if (popup?.key == key) {
            popup = null
            mainHandler.removeCallbacks(popupEnd)
        }
        refresh()
    }

    private fun startPopupTimer(e: IslandEvent) {
        mainHandler.removeCallbacks(popupEnd)
        if (e.ttlMs > 0) mainHandler.postDelayed(popupEnd, e.ttlMs)
    }

    fun setLandscape(b: Boolean) { if (landscape != b) { landscape = b; refresh() } }
    fun setForegroundPackage(pkg: String) {
        val h = pkg in Prefs.strSet("hiddenApps")
        if (h != appHidden) { appHidden = h; refresh() }
    }

    private fun onPrefsChanged() {
        view.applyStyle()
        applyPosition()
        refresh(instant = true)
    }

    private fun applyPosition() {
        lp.x = dp(Prefs.int("xOff"))
        lp.y = dp(Prefs.int("yOff"))
        if (attached) try { wm.updateViewLayout(view, lp) } catch (e: Exception) { }
    }

    private fun refresh(instant: Boolean = false) {
        val suppress = !Prefs.bool("enabled") || (landscape && Prefs.bool("hideLand")) || appHidden
        val live = sticky.values.filter { it.key !in dismissed }
        val top: IslandEvent? = if (suppress) null
        else popup
            ?: focusKey?.let { k -> live.firstOrNull { it.key == k } }
            ?: pinnedKey?.let { k -> live.firstOrNull { it.key == k } }
            ?: live.maxByOrNull { it.priority }
        if (top == null) {
            shown = null
            secList = emptyList()
            hide()
            return
        }
        shown = top
        secList = if (popup == null && Prefs.bool("multi"))
            live.filter { it.key != top.key }.sortedByDescending { it.priority }.take(2)
        else emptyList()
        showEvent(top, popup != null, instant)
    }

    private fun showEvent(e: IslandEvent, exp: Boolean, instant: Boolean) {
        val w: Int
        val h: Int
        val r: Float
        if (exp) {
            w = dp(Prefs.int("expandW"))
            h = view.expandedHeightPx(e)
            r = minOf(dpF(Prefs.int("radius").toFloat()), h / 2f)
        } else {
            w = dp(Prefs.int("compactW"))
            h = dp(Prefs.int("compactH"))
            r = h / 2f
        }
        // room for the side bubbles (symmetric, so the pill stays centered on the camera)
        val extra = if (!exp && secList.isNotEmpty()) 2 * (dp(Prefs.int("compactH")) + dp(6)) else 0
        view.setSecondary(if (exp) emptyList() else secList)
        view.pinned = e.key == pinnedKey
        if (!attached) {
            lp.width = w + extra + slack
            lp.height = h + slack
            pillW = dp(Prefs.int("compactW")) * 0.5f
            pillH = dp(Prefs.int("compactH")) * 0.5f
            pillR = pillH / 2f
            pillA = 0f
            tw = -1
            view.alpha = 0f
            view.setPill(pillW, pillH, pillR, 0f)
            addWindow()
        }
        view.bind(e)
        view.animate().cancel()
        if (view.alpha < 1f) view.animate().alpha(1f).setDuration(160).start()
        if (exp && !texp && Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        go(w, h, r, exp, instant, extra)
    }

    private fun hide() {
        if (!attached) return
        val w = (dp(Prefs.int("compactW")) * 0.5f).toInt()
        val h = (dp(Prefs.int("compactH")) * 0.5f).toInt()
        view.setSecondary(emptyList())
        go(w, h, h / 2f, false, false, 0)
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(220).withEndAction {
            if (shown == null) removeWindow()
        }.start()
    }

    private fun setWindow(w: Int, h: Int) {
        val nw = maxOf(2, w)
        val nh = maxOf(2, h)
        if (nw == lp.width && nh == lp.height) return
        lp.width = nw
        lp.height = nh
        if (attached) try { wm.updateViewLayout(view, lp) } catch (e: Exception) { }
    }

    private fun setPill(w: Float, h: Float, r: Float, a: Float) {
        pillW = w; pillH = h; pillR = r; pillA = a
        view.setPill(w, h, r, a)
    }

    private fun go(w: Int, h: Int, r: Float, exp: Boolean, instant: Boolean, extra: Int = 0) {
        if (!instant && w == tw && h == th && exp == texp && extra == tex) return
        tw = w; th = h; texp = exp; tex = extra
        animator?.cancel()
        val a1 = if (exp) 1f else 0f
        if (instant || !attached) {
            setWindow(w + extra + slack, h + slack)
            setPill(w.toFloat(), h.toFloat(), r, a1)
            // After settle, shrink window to content + small margin so nothing clips
            setWindow(w + extra + dp(8), h + dp(8))
            return
        }
        // Grow window with slack during spring; settle to content size at the end
        setWindow(maxOf(lp.width, w + extra + slack), maxOf(lp.height, h + slack))
        val w0 = pillW
        val h0 = pillH
        val r0 = pillR
        val a0 = pillA
        val sp = SpringInterpolator(Prefs.int("bounce") / 100f)
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = Prefs.int("animMs").toLong()
        va.interpolator = LinearInterpolator()
        va.addUpdateListener {
            val f = it.animatedFraction
            val p = sp.getInterpolation(f)
            setPill(w0 + (w - w0) * p, h0 + (h - h0) * p, r0 + (r - r0) * f, a0 + (a1 - a0) * f)
        }
        va.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                setPill(w.toFloat(), h.toFloat(), r, a1)
                setWindow(w + extra + dp(8), h + dp(8))
            }
        })
        animator = va
        va.start()
    }

    private fun addWindow() {
        if (attached) return
        try {
            wm.addView(view, lp)
            attached = true
        } catch (e: Exception) {
            attached = false
            Log.w("HyperIsland", "addView failed: $e")
        }
    }

    private fun removeWindow() {
        if (!attached) return
        try { wm.removeViewImmediate(view) } catch (e: Exception) { }
        attached = false
        tw = -1
    }

    private fun expand(e: IslandEvent, ms: Long) {
        popup = e
        mainHandler.removeCallbacks(popupEnd)
        if (ms > 0) mainHandler.postDelayed(popupEnd, ms)
        refresh()
    }

    private fun collapse() {
        popup = null
        mainHandler.removeCallbacks(popupEnd)
        refresh()
    }

    override fun onTap() {
        val e = shown ?: return
        if (popup == null) expand(e, 7000)
        else {
            e.tap?.invoke()
            if (e.kind != Kind.CALL) collapse()
        }
    }

    override fun onLongPress() {
        val e = shown ?: return
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (popup == null) expand(e, 9000) else collapse()
    }

    override fun onSwipeUp() {
        val e = shown ?: return
        if (e.sticky && e.kind != Kind.CALL && popup == null && e.key != pinnedKey) dismissed.add(e.key)
        collapse()
    }

    override fun onSwipeDown() {
        val e = shown ?: return
        if (popup == null) expand(e, 7000)
    }

    /** Double tap: pin / unpin the island (a pinned island is never swiped away or timed out). */
    override fun onDoubleTap() {
        val e = shown ?: return
        if (pinnedKey == e.key) {
            pinnedKey = null
        } else {
            pinnedKey = e.key
            focusKey = e.key
            e.sticky = true
            sticky[e.key] = e
            dismissed.remove(e.key)
            if (popup?.key == e.key) mainHandler.removeCallbacks(popupEnd)
        }
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        refresh()
    }

    /** Quick switch: tap a side bubble to bring that island to the middle. */
    override fun onSecondaryTap(index: Int) {
        val k = secList.getOrNull(index)?.key ?: return
        focusKey = k
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        refresh()
    }

    private fun cycle(next: Boolean) {
        val list = sticky.values.filter { it.key !in dismissed }.sortedByDescending { it.priority }
        if (list.size < 2) return
        val idx = list.indexOfFirst { it.key == shown?.key }
        val ni = if (next) (idx + 1) % list.size else (idx - 1 + list.size) % list.size
        focusKey = list[ni].key
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        refresh()
    }

    override fun onSwipeSide(next: Boolean) {
        val m = shown?.media
        if (m == null) {
            if (popup == null && Prefs.bool("multi")) cycle(next)
            return
        }
        if (next) m.transportControls.skipToNext() else m.transportControls.skipToPrevious()
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
