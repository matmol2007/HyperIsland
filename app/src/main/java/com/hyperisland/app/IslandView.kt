package com.hyperisland.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The island itself. The pill is DRAWN by this view (rounded rect + clip via outline),
 * so animating it never triggers a layout pass or a WindowManager call per frame.
 */
class IslandView(ctx: Context) : FrameLayout(ctx) {

    interface Callbacks {
        fun onTap()
        fun onLongPress()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeSide(next: Boolean)
    }

    var cb: Callbacks? = null
    var event: IslandEvent? = null
        private set

    private var scale = 1f
    private var pw = 0f
    private var ph = 0f
    private var pr = 0f
    private var amount = -1f
    private var compactIconPx = dp(20)
    private var touchInside = false
    private var bgColorOpaque = Color.BLACK
    private var txtColor = Color.WHITE

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowShader: RadialGradient? = null
    private var glowAcc = 0
    private var strokeW = 0f
    private var ambient = true
    private val rect = RectF()

    // compact capsule
    private val compact = LinearLayout(ctx)
    private val cIcon = ImageView(ctx)
    private val cSpacer = View(ctx)
    private val cEq = EqualizerView(ctx)
    private val cText = TextView(ctx)

    // expanded card
    private val expanded = LinearLayout(ctx)
    private val row = LinearLayout(ctx)
    private val art = ImageView(ctx)
    private val textCol = LinearLayout(ctx)
    private val titleTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val rightBox = FrameLayout(ctx)
    private val eq = EqualizerView(ctx)
    private val rightTv = TextView(ctx)
    private val pbar = ThinBar(ctx)
    private val seekRow = LinearLayout(ctx)
    private val elapsedTv = TextView(ctx)
    private val seek = ThinBar(ctx)
    private val durTv = TextView(ctx)
    private val controls = LinearLayout(ctx)
    private val bPrev = ImageView(ctx)
    private val bPlay = ImageView(ctx)
    private val bNext = ImageView(ctx)
    private val callRow = LinearLayout(ctx)
    private val btnDecline = LinearLayout(ctx)
    private val declineIcon = ImageView(ctx)
    private val declineLabel = TextView(ctx)
    private val btnAnswer = LinearLayout(ctx)
    private val answerIcon = ImageView(ctx)
    private val answerLabel = TextView(ctx)

    // cached values so repeated updates don't redo expensive work
    private var lastAvatar: Drawable? = null
    private var lastIcon = -1
    private var lastAcc = 0
    private var lastKind: Kind? = null
    private var lastTitle = ""
    private var lastSub = ""
    private var lastRt = ""
    private var lastEl = ""
    private var lastDur = ""

    private val ticker = object : Runnable {
        override fun run() {
            updateDynamic()
            postDelayed(this, 500)
        }
    }

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { cb?.onTap(); return true }
        override fun onLongPress(e: MotionEvent) { cb?.onLongPress() }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val dx = e2.x - (e1?.x ?: e2.x)
            val dy = e2.y - (e1?.y ?: e2.y)
            if (Math.abs(dy) > Math.abs(dx)) {
                if (dy < -dp(14) && vy < -500f) { cb?.onSwipeUp(); return true }
                if (dy > dp(14) && vy > 500f) { cb?.onSwipeDown(); return true }
            } else if (Math.abs(dx) > dp(40) && Math.abs(vx) > 600f) {
                cb?.onSwipeSide(dx < 0f)
                return true
            }
            return false
        }
    })

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                val l = (v.width - pw) / 2f
                o.setRoundRect(
                    Math.floor(l.toDouble()).toInt(), 0,
                    Math.ceil((l + pw).toDouble()).toInt(), Math.ceil(ph.toDouble()).toInt(), pr
                )
            }
        }
        clipToOutline = true

        // ---- compact
        compact.orientation = LinearLayout.HORIZONTAL
        compact.gravity = Gravity.CENTER_VERTICAL
        compact.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        cIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        cText.maxLines = 1
        cText.typeface = Typeface.DEFAULT_BOLD
        compact.addView(cIcon)
        compact.addView(cSpacer, LinearLayout.LayoutParams(0, 1, 1f))
        compact.addView(cEq)
        compact.addView(cText)
        addView(compact)

        // ---- expanded
        expanded.orientation = LinearLayout.VERTICAL
        expanded.gravity = Gravity.CENTER_VERTICAL
        expanded.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        textCol.orientation = LinearLayout.VERTICAL
        textCol.gravity = Gravity.CENTER_VERTICAL
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.setSingleLine()
        titleTv.ellipsize = TextUtils.TruncateAt.MARQUEE
        titleTv.marqueeRepeatLimit = -1
        titleTv.isSelected = true
        subTv.ellipsize = TextUtils.TruncateAt.END
        rightTv.typeface = Typeface.DEFAULT_BOLD
        rightTv.maxLines = 1
        elapsedTv.maxLines = 1
        durTv.maxLines = 1
        for (t in listOf(titleTv, subTv, rightTv, cText, elapsedTv, durTv, declineLabel, answerLabel)) {
            t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            t.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            t.includeFontPadding = false
        }
        declineLabel.typeface = Typeface.DEFAULT_BOLD
        answerLabel.typeface = Typeface.DEFAULT_BOLD
        textCol.addView(titleTv)
        textCol.addView(subTv)
        rightBox.addView(eq)
        rightBox.addView(rightTv, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        row.addView(art)
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(rightBox)
        expanded.addView(row)

        expanded.addView(pbar)

        seekRow.orientation = LinearLayout.HORIZONTAL
        seekRow.gravity = Gravity.CENTER_VERTICAL
        seekRow.layoutDirection = View.LAYOUT_DIRECTION_LTR
        seek.seekable = true
        seek.onSeek = { f ->
            val e = event
            if (e != null && e.durMs > 0) {
                val pos = (f * e.durMs).toLong()
                val m = e.media
                if (m != null) m.transportControls.seekTo(pos)
                else { e.posBase = pos; e.posAt = SystemClock.elapsedRealtime() }
            }
            hap()
        }
        seekRow.addView(elapsedTv)
        seekRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        seekRow.addView(durTv)
        expanded.addView(seekRow)

        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.layoutDirection = View.LAYOUT_DIRECTION_LTR
        for (b in listOf(bPrev, bPlay, bNext)) b.scaleType = ImageView.ScaleType.FIT_CENTER
        bPrev.setImageResource(R.drawable.ic_prev)
        bNext.setImageResource(R.drawable.ic_next)
        bPlay.setImageResource(R.drawable.ic_pause)
        controls.addView(bPrev)
        controls.addView(bPlay)
        controls.addView(bNext)
        expanded.addView(controls)

        callRow.orientation = LinearLayout.HORIZONTAL
        callRow.gravity = Gravity.CENTER
        for ((box, ic, lb) in listOf(
            Triple(btnDecline, declineIcon, declineLabel), Triple(btnAnswer, answerIcon, answerLabel)
        )) {
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER
            ic.scaleType = ImageView.ScaleType.FIT_CENTER
            ic.setColorFilter(Color.WHITE)
            lb.setTextColor(Color.WHITE)
            box.addView(ic)
            box.addView(lb)
        }
        declineIcon.setImageResource(R.drawable.ic_call_end)
        answerIcon.setImageResource(R.drawable.ic_call)
        declineLabel.text = "Decline"
        answerLabel.text = "Answer"
        btnDecline.background = pill(0xFFFF453A.toInt())
        btnAnswer.background = pill(0xFF30D158.toInt())
        callRow.addView(btnDecline)
        callRow.addView(btnAnswer)
        expanded.addView(callRow)
        addView(expanded)

        bPlay.setOnClickListener {
            val m = event?.media
            if (m != null) {
                if (m.playbackState?.state == PlaybackState.STATE_PLAYING) m.transportControls.pause()
                else m.transportControls.play()
            } else {
                val e = event
                if (e != null) {
                    val now = SystemClock.elapsedRealtime()
                    e.posBase = e.positionMs(now)
                    e.posAt = now
                    e.playing = !e.playing
                    bind(e)
                }
            }
            hap()
        }
        bNext.setOnClickListener { event?.media?.transportControls?.skipToNext(); hap() }
        bPrev.setOnClickListener { event?.media?.transportControls?.skipToPrevious(); hap() }
        btnAnswer.setOnClickListener { event?.onAnswer?.invoke(); hap() }
        btnDecline.setOnClickListener { event?.onDecline?.invoke(); hap() }

        applyStyle()
        applyAmount(0f)
    }

    private fun pill(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dpF(40f)
        g.setColor(c)
        return g
    }

    private fun oval(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(c)
        return g
    }

    private fun hap() {
        if (Prefs.bool("haptic")) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ------------------------------------------------------------------ drawing

    /** Called every animation frame: only invalidates, never re-lays-out. */
    fun setPill(w: Float, h: Float, r: Float, a: Float) {
        pw = w
        ph = h
        pr = minOf(r, h / 2f)
        if (a != amount) applyAmount(a)
        invalidateOutline()
        invalidate()
    }

    private fun applyAmount(a: Float) {
        amount = a
        val ca = (1f - a * 2.4f).coerceIn(0f, 1f)
        compact.alpha = ca
        compact.visibility = if (ca <= 0.01f) View.INVISIBLE else View.VISIBLE
        val ea = ((a - 0.3f) / 0.6f).coerceIn(0f, 1f)
        expanded.alpha = ea
        expanded.visibility = if (ea <= 0.01f) View.INVISIBLE else View.VISIBLE
    }

    override fun dispatchDraw(c: Canvas) {
        val l = (width - pw) / 2f
        rect.set(l, 0f, l + pw, ph)
        c.drawRoundRect(rect, pr, pr, bgPaint)
        val gs = glowShader
        if (ambient && gs != null) {
            glowPaint.shader = gs
            glowPaint.alpha = ((0.45f + 0.55f * amount.coerceIn(0f, 1f)) * 255f).toInt()
            c.save()
            c.translate(l, 0f)
            c.drawRect(0f, 0f, pw, ph, glowPaint)
            c.restore()
        }
        if (strokeW > 0f) {
            val h = strokeW / 2f
            rect.set(l + h, h, l + pw - h, ph - h)
            c.drawRoundRect(rect, maxOf(0f, pr - h), maxOf(0f, pr - h), strokePaint)
        }
        super.dispatchDraw(c)
    }

    private fun updateGlow(acc: Int) {
        if (acc == glowAcc && glowShader != null) return
        glowAcc = acc
        glowShader = RadialGradient(
            0f, 0f, dpF(230f),
            intArrayOf(acc.withAlpha(105), acc.withAlpha(0)), null, Shader.TileMode.CLAMP
        )
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val l = (width - pw) / 2f
            touchInside = ev.x >= l && ev.x <= l + pw && ev.y >= 0f && ev.y <= ph
        }
        if (!touchInside) return false
        return gd.onTouchEvent(ev) || super.onTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(ticker)
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ sizing / style

    fun expandedHeightPx(e: IslandEvent): Int {
        val h = when (e.kind) {
            Kind.MESSAGE -> 84f
            Kind.CALL -> 146f
            Kind.MUSIC -> 174f
            Kind.LIVE -> if (e.progress >= 0f) 104f else 84f
            Kind.SYSTEM -> if (e.progress >= 0f) 104f else 84f
            Kind.VOLUME -> 96f
        }
        return dp(h * scale)
    }

    fun applyStyle() {
        val s = Prefs.int("scale") / 100f
        scale = s
        val acc = Prefs.int("accent")
        txtColor = Prefs.int("text")
        bgColorOpaque = Prefs.int("bg") or 0xFF000000.toInt()
        bgPaint.color = Prefs.int("bg").withAlpha(Prefs.int("opacity") * 255 / 100)
        ambient = Prefs.bool("ambient")
        strokeW = if (Prefs.bool("glow")) dpF(1.5f) else 0f
        strokePaint.strokeWidth = strokeW
        strokePaint.color = acc.withAlpha(170)
        layoutDirection = when (Prefs.int("layoutDir")) {
            1 -> View.LAYOUT_DIRECTION_LTR
            2 -> View.LAYOUT_DIRECTION_RTL
            else -> View.LAYOUT_DIRECTION_LOCALE
        }

        val cw = dp(Prefs.int("compactW"))
        val ch = dp(Prefs.int("compactH"))
        compact.layoutParams = LayoutParams(cw, ch, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        val padH = (ch * 0.36f).toInt()
        compact.setPadding(padH, 0, padH, 0)
        compactIconPx = (ch * 0.58f).toInt()
        cIcon.layoutParams = LinearLayout.LayoutParams(compactIconPx, compactIconPx)
        cEq.layoutParams = LinearLayout.LayoutParams((compactIconPx * 1.1f).toInt(), (compactIconPx * 0.8f).toInt())
        cText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * s)

        val ew = dp(Prefs.int("expandW"))
        expanded.layoutParams = LayoutParams(ew, dp(174f * s), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        expanded.setPadding(dp(16 * s), dp(14 * s), dp(16 * s), dp(14 * s))
        val artPx = dp(52 * s)
        row.layoutParams = LinearLayout.LayoutParams(-1, -2)
        art.layoutParams = LinearLayout.LayoutParams(artPx, artPx)
        val tcLp = LinearLayout.LayoutParams(0, -2, 1f)
        tcLp.marginStart = dp(12 * s)
        tcLp.marginEnd = dp(8 * s)
        textCol.layoutParams = tcLp
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * s)
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
        elapsedTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * s)
        durTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * s)
        titleTv.setTextColor(txtColor)
        subTv.setTextColor(txtColor.withAlpha(165))
        elapsedTv.setTextColor(txtColor.withAlpha(150))
        durTv.setTextColor(txtColor.withAlpha(150))
        rightTv.setTextColor(txtColor)
        cText.setTextColor(txtColor)
        eq.layoutParams = FrameLayout.LayoutParams(dp(26 * s), dp(20 * s), Gravity.CENTER)

        val pbLp = LinearLayout.LayoutParams(-1, dp(4 * s))
        pbLp.topMargin = dp(12 * s)
        pbar.layoutParams = pbLp
        pbar.track = txtColor.withAlpha(45)
        seek.track = txtColor.withAlpha(45)
        seek.thickness = dpF(4f * s)
        val sLp = LinearLayout.LayoutParams(-1, dp(18 * s))
        sLp.topMargin = dp(10 * s)
        seekRow.layoutParams = sLp
        val timeW = dp(34 * s)
        elapsedTv.layoutParams = LinearLayout.LayoutParams(timeW, -2)
        durTv.layoutParams = LinearLayout.LayoutParams(timeW, -2)
        durTv.gravity = Gravity.END
        seek.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).also {
            it.marginStart = dp(4 * s)
            it.marginEnd = dp(4 * s)
        }

        val cLp = LinearLayout.LayoutParams(-1, -2)
        cLp.topMargin = dp(4 * s)
        controls.layoutParams = cLp
        val sm = dp(46 * s)
        val big = dp(54 * s)
        val gap = dp(18 * s)
        bPrev.layoutParams = LinearLayout.LayoutParams(sm, sm).also { it.marginEnd = gap }
        bPlay.layoutParams = LinearLayout.LayoutParams(big, big)
        bNext.layoutParams = LinearLayout.LayoutParams(sm, sm).also { it.marginStart = gap }
        bPrev.setPadding(dp(11 * s), dp(11 * s), dp(11 * s), dp(11 * s))
        bNext.setPadding(dp(11 * s), dp(11 * s), dp(11 * s), dp(11 * s))
        bPlay.setPadding(dp(14 * s), dp(14 * s), dp(14 * s), dp(14 * s))
        bPrev.setColorFilter(txtColor)
        bNext.setColorFilter(txtColor)
        bPlay.setColorFilter(bgColorOpaque)
        bPlay.background = oval(txtColor)

        val crLp = LinearLayout.LayoutParams(-1, dp(46 * s))
        crLp.topMargin = dp(14 * s)
        callRow.layoutParams = crLp
        val half = LinearLayout.LayoutParams(0, -1, 1f)
        half.marginEnd = dp(5 * s)
        btnDecline.layoutParams = half
        val half2 = LinearLayout.LayoutParams(0, -1, 1f)
        half2.marginStart = dp(5 * s)
        btnAnswer.layoutParams = half2
        val ip = dp(20 * s)
        declineIcon.layoutParams = LinearLayout.LayoutParams(ip, ip).also { it.marginEnd = dp(6 * s) }
        answerIcon.layoutParams = LinearLayout.LayoutParams(ip, ip).also { it.marginEnd = dp(6 * s) }
        declineLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * s)
        answerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * s)

        lastAvatar = null
        lastIcon = -1
        lastAcc = 0
        event?.let { bind(it) }
    }

    private fun setSlot(iv: ImageView, e: IslandEvent, px: Int, acc: Int, roundedSquare: Boolean, copy: Boolean) {
        val av = e.avatar
        if (av != null) {
            iv.background = null
            iv.setPadding(0, 0, 0, 0)
            iv.clearColorFilter()
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val d = if (copy) (av.constantState?.newDrawable(resources)?.mutate() ?: av) else av
            iv.setImageDrawable(d)
        } else {
            iv.background = oval(acc.withAlpha(60))
            val p = (px * 0.26f).toInt()
            iv.setPadding(p, p, p, p)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setColorFilter(acc)
            iv.setImageResource(e.iconRes)
        }
        val r = if (av != null && roundedSquare) px * 0.24f else px / 2f
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, r)
            }
        }
        iv.clipToOutline = true
    }

    fun bind(e: IslandEvent) {
        event = e
        val acc = if (e.accent != 0) e.accent else Prefs.int("accent")
        val k = e.kind
        val music = k == Kind.MUSIC
        val call = k == Kind.CALL

        if (e.avatar !== lastAvatar || e.iconRes != lastIcon || acc != lastAcc || k != lastKind) {
            lastAvatar = e.avatar
            lastIcon = e.iconRes
            lastAcc = acc
            lastKind = k
            setSlot(art, e, dp(52 * scale), acc, music, false)
            setSlot(cIcon, e, compactIconPx, acc, music, true)
            pbar.fill = acc
            seek.fill = acc
            eq.color = acc
            cEq.color = acc
            updateGlow(acc)
            invalidate()
        }

        val eh = expandedHeightPx(e)
        val elp = expanded.layoutParams
        if (elp != null && elp.height != eh) {
            elp.height = eh
            expanded.layoutParams = elp
        }

        if (e.title != lastTitle) { lastTitle = e.title; titleTv.text = e.title }
        if (e.subtitle != lastSub) { lastSub = e.subtitle; subTv.text = e.subtitle }
        subTv.visibility = if (e.subtitle.isEmpty()) View.GONE else View.VISIBLE
        subTv.maxLines = if (k == Kind.MESSAGE) 2 else 1

        val hasDur = music && e.durMs > 0
        pbar.visibility = if (!music && e.progress >= 0f) View.VISIBLE else View.GONE
        seekRow.visibility = if (hasDur) View.VISIBLE else View.GONE
        controls.visibility = if (music) View.VISIBLE else View.GONE
        callRow.visibility = if (call) View.VISIBLE else View.GONE
        btnAnswer.visibility = if (call && e.ringing) View.VISIBLE else View.GONE
        declineLabel.text = if (e.ringing) "Decline" else "Hang up"
        eq.visibility = if (music) View.VISIBLE else View.GONE
        eq.running = e.playing
        rightTv.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (if (k == Kind.CALL) 14f else 20f) * scale
        )
        bPlay.setImageResource(if (e.playing) R.drawable.ic_pause else R.drawable.ic_play)
        updateDynamic()
    }

    fun updateDynamic() {
        val e = event ?: return
        val now = SystemClock.elapsedRealtime()
        var rt = e.rightText
        if (e.chronoBase != 0L) {
            val ms = if (e.chronoDown) e.chronoBase - now else now - e.chronoBase
            rt = fmtTime(ms.coerceAtLeast(0L))
        }
        if (e.kind == Kind.MUSIC) {
            rightTv.visibility = View.GONE
            cEq.visibility = View.VISIBLE
            cEq.running = e.playing
            cText.visibility = View.GONE
            if (e.durMs > 0) {
                val pos = e.positionMs(now)
                if (!seek.dragging) seek.value = pos.toFloat() / e.durMs.toFloat()
                val el = fmtTime(pos)
                val du = fmtTime(e.durMs)
                if (el != lastEl) { lastEl = el; elapsedTv.text = el }
                if (du != lastDur) { lastDur = du; durTv.text = du }
            }
        } else {
            if (rt != lastRt) {
                lastRt = rt
                rightTv.text = rt
                cText.text = rt
            }
            rightTv.visibility = if (rt.isEmpty()) View.GONE else View.VISIBLE
            cEq.visibility = View.GONE
            cText.visibility = if (rt.isEmpty()) View.GONE else View.VISIBLE
            if (e.progress >= 0f) pbar.value = e.progress
        }
    }
}
