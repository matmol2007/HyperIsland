package com.hyperisland.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager

object IslandRuntime {
    lateinit var app: Context
    var controller: IslandController? = null
        private set
    var callMonitor: CallMonitor? = null
    var media: MediaMonitor? = null

    private var hostTag: Any? = null
    private var preview = false

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Prefs.ensureDefaults(app)
    }

    fun attach(host: Context, tag: Any, type: Int, prio: Int) {
        init(host)
        val cur = controller
        if (cur != null && hostTag === tag) return
        if (cur != null && cur.prio >= prio) return
        val old = cur?.snapshot()
        cur?.stop()
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val c = IslandController(host, wm, type, prio)
        controller = c
        hostTag = tag
        c.start()
        if (old != null) c.restore(old)
        if (preview) postPreview()
    }

    fun detach(tag: Any) {
        if (hostTag === tag) {
            controller?.stop()
            controller = null
            hostTag = null
        }
    }

    fun ensureHost(ctx: Context) {
        if (controller != null) return
        if (Settings.canDrawOverlays(ctx)) {
            attach(ctx, ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0)
        }
    }

    fun post(e: IslandEvent) {
        if (isMain()) controller?.post(e) else mainHandler.post { controller?.post(e) }
    }

    fun remove(key: String) {
        if (isMain()) controller?.remove(key) else mainHandler.post { controller?.remove(key) }
    }

    private fun isMain() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    fun onForegroundPackage(pkg: String) { controller?.setForegroundPackage(pkg) }

    fun onCallNotification(title: String?) { callMonitor?.nameFromNotification(title) }

    fun setPreview(on: Boolean) {
        preview = on
        if (on) postPreview() else remove("_preview")
    }

    private fun postPreview() {
        val e = IslandEvent("_preview", Kind.SYSTEM)
        e.title = Lang.s(app, R.string.preview_title)
        e.subtitle = ""
        e.iconRes = R.drawable.ic_island
        e.rightText = ""
        e.sticky = true
        e.popup = false
        e.priority = 1
        e.ttlMs = 0
        post(e)
    }

    private fun gradient(c1: Int, c2: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))

    fun demo(ctx: Context, what: String) {
        if (controller == null) ensureHost(ctx)
        if (controller == null) return
        val now = SystemClock.elapsedRealtime()
        when (what) {
            "music" -> {
                val e = IslandEvent("demo_music", Kind.MUSIC)
                e.title = "Midnight Drive"
                e.subtitle = "Neon Skyline"
                e.iconRes = R.drawable.ic_music
                e.avatar = gradient(0xFFFF375F.toInt(), 0xFF5E5CE6.toInt())
                e.accent = 0xFFFF375F.toInt()
                e.playing = true
                e.durMs = 214_000L
                e.posBase = 62_000L
                e.posAt = now
                e.sticky = true
                e.priority = 41
                e.ttlMs = 9000
                e.reshow = true
                post(e)
                mainHandler.postDelayed({ remove("demo_music") }, 40000)
            }
            "message" -> {
                val e = IslandEvent("demo_msg", Kind.MESSAGE)
                e.title = "Alex"
                e.subtitle = "Hey! Are you free tonight?"
                e.iconRes = R.drawable.ic_message
                e.avatar = gradient(0xFF30D158.toInt(), 0xFF0A84FF.toInt())
                e.priority = 30
                e.ttlMs = Prefs.int("msgSec") * 1000L
                post(e)
            }
            "call" -> {
                val e = IslandEvent("demo_call", Kind.CALL)
                e.title = "Mom"
                e.subtitle = if (Lang.isFa()) "تماس ورودی" else "Incoming call"
                e.iconRes = R.drawable.ic_call
                e.avatar = gradient(0xFFFF9F0A.toInt(), 0xFFFF375F.toInt())
                e.accent = 0xFF30D158.toInt()
                e.ringing = true
                e.sticky = true
                e.priority = 100
                e.ttlMs = 0
                e.onAnswer = { remove("demo_call") }
                e.onDecline = { remove("demo_call") }
                post(e)
                mainHandler.postDelayed({ remove("demo_call") }, 9000)
            }
            "charge" -> {
                val e = IslandEvent("demo_charge", Kind.SYSTEM)
                e.title = Lang.s(ctx, R.string.evt_charging)
                e.subtitle = Lang.s(ctx, R.string.evt_charger)
                e.iconRes = R.drawable.ic_battery_charging
                e.accent = 0xFF30D158.toInt()
                e.progress = 0.67f
                e.rightText = "67%"
                e.priority = 20
                e.ttlMs = Prefs.int("evtSec") * 1000L
                post(e)
            }
            "timer" -> {
                val e = IslandEvent("demo_timer", Kind.LIVE)
                e.title = if (Lang.isFa()) "تایمر" else "Timer"
                e.subtitle = if (Lang.isFa()) "در حال شمارش" else "Counting down"
                e.iconRes = R.drawable.ic_timer
                e.accent = 0xFFFF9F0A.toInt()
                e.chronoBase = now + 90_000L
                e.chronoDown = true
                e.sticky = true
                e.priority = 55
                e.ttlMs = 4000
                e.reshow = true
                post(e)
                mainHandler.postDelayed({ remove("demo_timer") }, 30000)
            }
            "volume" -> {
                val e = IslandEvent("demo_vol", Kind.VOLUME)
                e.title = Lang.s(ctx, R.string.evt_volume)
                e.iconRes = R.drawable.ic_volume_up
                e.progress = 0.6f
                e.rightText = "60%"
                e.priority = 15
                e.ttlMs = 1800
                post(e)
            }
        }
    }
}
