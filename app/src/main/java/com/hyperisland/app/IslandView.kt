package com.hyperisland.app

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

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
    private var faDigits = true
    private var radiusPx = 0f
    private var compactIconPx = dp(20)
    private val bg = GradientDrawable()

    // حالت کوچک (کپسول)
    private val compact = LinearLayout(ctx)
    private val cIcon = ImageView(ctx)
    private val cSpacer = View(ctx)
    private val cEq = EqualizerView(ctx)
    private val cText = TextView(ctx)

    // حالت بزرگ
    private val expanded = LinearLayout(ctx)
    private val row = LinearLayout(ctx)
    private val art = ImageView(ctx)
    private val textCol = LinearLayout(ctx)
    private val titleTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val rightBox = FrameLayout(ctx)
    private val eq = EqualizerView(ctx)
    private val rightTv = TextView(ctx)
    private val bar = ThinBar(ctx)
    private val controls = LinearLayout(ctx)
    private val bPrev = ImageView(ctx)
    private val bPlay = ImageView(ctx)
    private val bNext = ImageView(ctx)
    private val bDecline = ImageView(ctx)
    private val callSpacer = View(ctx)
    private val bAnswer = ImageView(ctx)

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
        bg.shape = GradientDrawable.RECTANGLE
        background = bg
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        clipToOutline = true

        compact.orientation = LinearLayout.HORIZONTAL
        compact.gravity = Gravity.CENTER_VERTICAL
        cIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        cText.maxLines = 1
        cText.typeface = Typeface.DEFAULT_BOLD
        compact.addView(cIcon)
        compact.addView(cSpacer, LinearLayout.LayoutParams(0, 1, 1f))
        compact.addView(cEq)
        compact.addView(cText)
        addView(compact)

        expanded.orientation = LinearLayout.VERTICAL
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        textCol.orientation = LinearLayout.VERTICAL
        textCol.gravity = Gravity.CENTER_VERTICAL
        titleTv.maxLines = 1
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.ellipsize = android.text.TextUtils.TruncateAt.END
        subTv.ellipsize = android.text.TextUtils.TruncateAt.END
        rightTv.typeface = Typeface.DEFAULT_BOLD
        rightTv.maxLines = 1
        for (t in listOf(titleTv, subTv, rightTv, cText)) {
            t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            t.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            t.includeFontPadding = false
        }
        textCol.addView(titleTv)
        textCol.addView(subTv)
        rightBox.addView(eq)
        rightBox.addView(rightTv, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        row.addView(art)
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(rightBox)
        expanded.addView(row)
        expanded.addView(bar)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.layoutDirection = View.LAYOUT_DIRECTION_LTR
        for (b in listOf(bPrev, bPlay, bNext, bDecline, bAnswer)) {
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.setColorFilter(0xFFFFFFFF.toInt())
        }
        bPrev.setImageResource(R.drawable.ic_prev)
        bNext.setImageResource(R.drawable.ic_next)
        bPlay.setImageResource(R.drawable.ic_pause)
        bDecline.setImageResource(R.drawable.ic_call_end)
        bAnswer.setImageResource(R.drawable.ic_call)
        bDecline.background = oval(0xFFFF453A.toInt())
        bAnswer.background = oval(0xFF30D158.toInt())
        controls.addView(bPrev)
        controls.addView(bPlay)
        controls.addView(bNext)
        controls.addView(bDecline)
        controls.addView(callSpacer, LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(bAnswer)
        expanded.addView(controls)
        addView(expanded)

        bPlay.setOnClickListener {
            val m = event?.media
            if (m != null) {
                if (m.playbackState?.state == PlaybackState.STATE_PLAYING) m.transportControls.pause()
                else m.transportControls.play()
            }
            hap()
        }
        bNext.setOnClickListener { event?.media?.transportControls?.skipToNext(); hap() }
        bPrev.setOnClickListener { event?.media?.transportControls?.skipToPrevious(); hap() }
        bAnswer.setOnClickListener { event?.onAnswer?.invoke(); hap() }
        bDecline.setOnClickListener { event?.onDecline?.invoke(); hap() }

        applyStyle()
        setExpandAmount(0f)
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

    override fun onTouchEvent(ev: MotionEvent): Boolean = gd.onTouchEvent(ev) || super.onTouchEvent(ev)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(ticker)
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    fun setRadius(r: Float) {
        radiusPx = r
        bg.cornerRadius = r
        invalidateOutline()
    }

    /** 0 = کپسول، 1 = کارت بزرگ ؛ فقط شفافیت محتوا را کنترل می‌کند. */
    fun setExpandAmount(a: Float) {
        val ca = (1f - a * 2.4f).coerceIn(0f, 1f)
        compact.alpha = ca
        compact.visibility = if (ca <= 0.01f) View.INVISIBLE else View.VISIBLE
        val ea = ((a - 0.3f) / 0.6f).coerceIn(0f, 1f)
        expanded.alpha = ea
        expanded.visibility = if (ea <= 0.01f) View.INVISIBLE else View.VISIBLE
    }

    fun expandedHeightPx(e: IslandEvent): Int {
        val h = when (e.kind) {
            Kind.MESSAGE -> 80f
            Kind.CALL -> 146f
            Kind.MUSIC -> 158f
            Kind.LIVE -> if (e.progress >= 0f) 98f else 80f
            Kind.SYSTEM -> if (e.progress >= 0f) 94f else 76f
            Kind.VOLUME -> 92f
        }
        return dp(h * scale)
    }

    fun applyStyle() {
        val s = Prefs.int("scale") / 100f
        scale = s
        faDigits = Prefs.bool("faDigits")
        val acc = Prefs.int("accent")
        val txt = Prefs.int("text")
        bg.setColor(Prefs.int("bg").withAlpha(Prefs.int("opacity") * 255 / 100))
        if (Prefs.bool("glow")) bg.setStroke(dp(1.5f), acc.withAlpha(170)) else bg.setStroke(0, 0)
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
        cText.setTextColor(txt)

        val ew = dp(Prefs.int("expandW"))
        expanded.layoutParams = LayoutParams(ew, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        expanded.setPadding(dp(16 * s), dp(14 * s), dp(16 * s), dp(14 * s))
        val artPx = dp(48 * s)
        row.layoutParams = LinearLayout.LayoutParams(-1, -2)
        art.layoutParams = LinearLayout.LayoutParams(artPx, artPx)
        val tcLp = LinearLayout.LayoutParams(0, -2, 1f)
        tcLp.marginStart = dp(12 * s)
        tcLp.marginEnd = dp(8 * s)
        textCol.layoutParams = tcLp
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * s)
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
        rightTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * s)
        titleTv.setTextColor(txt)
        subTv.setTextColor(txt.withAlpha(175))
        rightTv.setTextColor(txt)
        eq.layoutParams = FrameLayout.LayoutParams(dp(26 * s), dp(20 * s), Gravity.CENTER)
        val barLp = LinearLayout.LayoutParams(-1, dp(4 * s))
        barLp.topMargin = dp(12 * s)
        bar.layoutParams = barLp
        bar.track = txt.withAlpha(50)
        val cLp = LinearLayout.LayoutParams(-1, -2)
        cLp.topMargin = dp(6 * s)
        controls.layoutParams = cLp
        val sm = dp(44 * s)
        val big = dp(52 * s)
        val call = dp(54 * s)
        val gap = dp(10 * s)
        bPrev.layoutParams = LinearLayout.LayoutParams(sm, sm).also { it.marginEnd = gap }
        bPlay.layoutParams = LinearLayout.LayoutParams(big, big)
        bNext.layoutParams = LinearLayout.LayoutParams(sm, sm).also { it.marginStart = gap }
        bPrev.setPadding(dp(8 * s), dp(8 * s), dp(8 * s), dp(8 * s))
        bNext.setPadding(dp(8 * s), dp(8 * s), dp(8 * s), dp(8 * s))
        bPlay.setPadding(dp(10 * s), dp(10 * s), dp(10 * s), dp(10 * s))
        bDecline.layoutParams = LinearLayout.LayoutParams(call, call)
        bAnswer.layoutParams = LinearLayout.LayoutParams(call, call)
        bDecline.setPadding(dp(14 * s), dp(14 * s), dp(14 * s), dp(14 * s))
        bAnswer.setPadding(dp(14 * s), dp(14 * s), dp(14 * s), dp(14 * s))
        controls.setPadding(dp(20 * s), 0, dp(20 * s), 0)
        event?.let { bind(it) }
    }

    private fun setSlot(iv: ImageView, e: IslandEvent, px: Int, acc: Int, roundedSquare: Boolean) {
        val av = e.avatar
        if (av != null) {
            iv.background = null
            iv.setPadding(0, 0, 0, 0)
            iv.clearColorFilter()
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(av)
        } else {
            iv.background = oval(acc.withAlpha(60))
            val p = (px * 0.26f).toInt()
            iv.setPadding(p, p, p, p)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setColorFilter(acc)
            iv.setImageResource(e.iconRes)
        }
        val r = if (av != null && roundedSquare) px * 0.22f else px / 2f
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
        bar.fill = acc
        setSlot(art, e, dp(48 * scale), acc, k == Kind.MUSIC)
        setSlot(cIcon, e, compactIconPx, acc, k == Kind.MUSIC)

        titleTv.text = e.title
        subTv.text = e.subtitle
        subTv.visibility = if (e.subtitle.isEmpty()) View.GONE else View.VISIBLE
        subTv.maxLines = if (k == Kind.MESSAGE) 2 else 1

        val music = k == Kind.MUSIC
        val call = k == Kind.CALL
        bar.visibility = if ((music && e.durMs > 0) || (!music && e.progress >= 0f)) View.VISIBLE else View.GONE
        controls.visibility = if (music || call) View.VISIBLE else View.GONE
        val mv = if (music) View.VISIBLE else View.GONE
        bPrev.visibility = mv
        bPlay.visibility = mv
        bNext.visibility = mv
        bDecline.visibility = if (call) View.VISIBLE else View.GONE
        bAnswer.visibility = if (call && e.ringing) View.VISIBLE else View.GONE
        callSpacer.visibility = if (call && e.ringing) View.VISIBLE else View.GONE
        eq.visibility = mv
        eq.color = acc
        eq.running = e.playing
        cEq.color = acc
        cText.setTextColor(acc)
        bPlay.setImageResource(if (e.playing) R.drawable.ic_pause else R.drawable.ic_play)
        updateDynamic()
    }

    fun updateDynamic() {
        val e = event ?: return
        val now = SystemClock.elapsedRealtime()
        var rt = e.rightText
        if (e.chronoBase != 0L) {
            val ms = if (e.chronoDown) e.chronoBase - now else now - e.chronoBase
            rt = Fa.digits(fmtTime(ms.coerceAtLeast(0L)), faDigits)
        }
        if (e.kind == Kind.MUSIC) {
            rightTv.visibility = View.GONE
            cEq.visibility = View.VISIBLE
            cEq.running = e.playing
            cText.visibility = View.GONE
            val m = e.media
            val st = m?.playbackState
            if (st != null && e.durMs > 0) {
                var pos = st.position
                if (st.state == PlaybackState.STATE_PLAYING) {
                    pos += ((now - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
                }
                bar.value = pos.toFloat() / e.durMs.toFloat()
            }
        } else {
            rightTv.text = rt
            rightTv.visibility = if (rt.isEmpty()) View.GONE else View.VISIBLE
            cEq.visibility = View.GONE
            cText.text = rt
            cText.visibility = if (rt.isEmpty()) View.GONE else View.VISIBLE
            if (e.progress >= 0f) bar.value = e.progress
        }
    }
}
