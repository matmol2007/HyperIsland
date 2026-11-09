package com.hyperisland.app

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
 * مغز جزیره: رویدادها را نگه می‌دارد، انتخاب می‌کند چه چیزی نمایش داده شود
 * و پنجره‌ی Overlay را با انیمیشن فنری تغییر اندازه می‌دهد.
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

    private var curW = 0f
    private var curH = 0f
    private var curR = 0f
    private var curA = 0f
    private var tw = -1
    private var th = -1
    private var texp = false

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
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.cb = this
        applyPosition()
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

    // ---------------- API رویدادها ----------------

    fun post(e: IslandEvent) {
        if (e.sticky) {
            val isNew = !sticky.containsKey(e.key)
            sticky[e.key] = e
            if (isNew || e.reshow) dismissed.remove(e.key)
        }
        if (!Prefs.bool("enabled")) return
        val cur = popup
        if (cur != null && cur.key == e.key) {
            popup = e
            if (!e.popup && cur.ttlMs == 0L) { /* همان کارت در حال نمایش است؛ فقط به‌روز می‌شود */ }
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

    // ---------------- نمایش ----------------

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
        val top: IslandEvent? = if (suppress) null
        else popup ?: sticky.values.filter { it.key !in dismissed }.maxByOrNull { it.priority }
        if (top == null) {
            shown = null
            hide()
            return
        }
        shown = top
        showEvent(top, popup != null, instant)
    }

    private fun showEvent(e: IslandEvent, exp: Boolean, instant: Boolean) {
        if (!attached) {
            curW = dp(Prefs.int("compactW")) * 0.5f
            curH = dp(Prefs.int("compactH")) * 0.5f
            curR = curH / 2f
            curA = 0f
            tw = -1
            view.alpha = 0f
            addWindow()
            setFrame(curW, curH, curR, 0f)
        }
        view.bind(e)
        view.animate().cancel()
        if (view.alpha < 1f) view.animate().alpha(1f).setDuration(160).start()

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
        if (exp && !texp && Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        go(w, h, r, exp, instant)
    }

    private fun hide() {
        if (!attached) return
        go((dp(Prefs.int("compactW")) * 0.5f).toInt(), (dp(Prefs.int("compactH")) * 0.5f).toInt(),
            dp(Prefs.int("compactH")) * 0.25f, false, false)
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(220).withEndAction {
            if (shown == null) removeWindow()
        }.start()
    }

    private fun go(w: Int, h: Int, r: Float, exp: Boolean, instant: Boolean) {
        if (!instant && w == tw && h == th && exp == texp) return
        tw = w; th = h; texp = exp
        animator?.cancel()
        val a1 = if (exp) 1f else 0f
        if (instant || !attached) {
            setFrame(w.toFloat(), h.toFloat(), r, a1)
            return
        }
        val w0 = curW
        val h0 = curH
        val r0 = curR
        val a0 = curA
        val sp = SpringInterpolator(Prefs.int("bounce") / 100f)
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = Prefs.int("animMs").toLong()
        va.interpolator = LinearInterpolator()
        va.addUpdateListener {
            val f = it.animatedFraction
            val p = sp.getInterpolation(f)
            setFrame(w0 + (w - w0) * p, h0 + (h - h0) * p, r0 + (r - r0) * f, a0 + (a1 - a0) * f)
        }
        animator = va
        va.start()
    }

    private fun setFrame(w: Float, h: Float, r: Float, a: Float) {
        curW = w; curH = h; curR = r; curA = a
        lp.width = maxOf(2, w.toInt())
        lp.height = maxOf(2, h.toInt())
        view.setRadius(minOf(r, lp.height / 2f))
        view.setExpandAmount(a)
        if (attached) try { wm.updateViewLayout(view, lp) } catch (e: Exception) { }
    }

    private fun addWindow() {
        if (attached) return
        try {
            lp.width = maxOf(2, curW.toInt())
            lp.height = maxOf(2, curH.toInt())
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

    // ---------------- ژست‌ها ----------------

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
        if (popup == null) expand(e, 6000)
        else {
            e.tap?.invoke()
            if (e.kind != Kind.CALL) collapse()
        }
    }

    override fun onLongPress() {
        val e = shown ?: return
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (popup == null) expand(e, 8000) else collapse()
    }

    override fun onSwipeUp() {
        val e = shown ?: return
        if (e.sticky && e.kind != Kind.CALL && popup == null) dismissed.add(e.key)
        collapse()
    }

    override fun onSwipeDown() {
        val e = shown ?: return
        if (popup == null) expand(e, 6000)
    }

    override fun onSwipeSide(next: Boolean) {
        val m = shown?.media ?: return
        if (next) m.transportControls.skipToNext() else m.transportControls.skipToPrevious()
        if (Prefs.bool("haptic")) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
