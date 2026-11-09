package com.hyperisland.app

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager

/**
 * نگهدارنده‌ی یگانه‌ی جزیره. چند «میزبان» می‌توانند آن را اجرا کنند:
 *  ۲ = سرویس دسترسی‌پذیری (ماندگارترین، بدون نیاز به مجوز Overlay)
 *  ۱ = سرویس پیش‌زمینه   ۰ = شنونده‌ی اعلان
 * میزبان با اولویت بالاتر جای پایین‌تر را می‌گیرد.
 */
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

    /** اگر هیچ میزبانی نیست و مجوز Overlay هست، با Context داده‌شده یک میزبان می‌سازد. */
    fun ensureHost(ctx: Context) {
        if (controller != null) return
        if (Settings.canDrawOverlays(ctx)) {
            attach(ctx, ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0)
        }
    }

    fun post(e: IslandEvent) {
        if (Looper_isMain()) controller?.post(e) else mainHandler.post { controller?.post(e) }
    }

    fun remove(key: String) {
        if (Looper_isMain()) controller?.remove(key) else mainHandler.post { controller?.remove(key) }
    }

    private fun Looper_isMain() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    fun onForegroundPackage(pkg: String) { controller?.setForegroundPackage(pkg) }

    fun onCallNotification(title: String?) { callMonitor?.nameFromNotification(title) }

    // ---------------- پیش‌نمایش و تست ----------------

    fun setPreview(on: Boolean) {
        preview = on
        if (on) postPreview() else remove("_preview")
    }

    private fun postPreview() {
        val e = IslandEvent("_preview", Kind.LIVE)
        e.title = "پیش‌نمایش جزیره"
        e.subtitle = "تنظیمات را عوض کن؛ همین‌جا زنده می‌بینی"
        e.iconRes = R.drawable.ic_island
        e.rightText = Fa.digits("12:34", Prefs.bool("faDigits"))
        e.sticky = true
        e.popup = false
        e.priority = 1
        post(e)
    }

    fun demo(ctx: Context, what: String) {
        if (controller == null) ensureHost(ctx)
        if (controller == null) return
        val fa = Prefs.bool("faDigits")
        when (what) {
            "music" -> {
                val e = IslandEvent("demo_music", Kind.MUSIC)
                e.title = "آهنگ نمونه"
                e.subtitle = "خواننده‌ی نمونه"
                e.iconRes = R.drawable.ic_music
                e.playing = true
                e.sticky = true
                e.priority = 41
                e.ttlMs = 5000
                e.reshow = true
                post(e)
                mainHandler.postDelayed({ remove("demo_music") }, 20000)
            }
            "message" -> {
                val e = IslandEvent("demo_msg", Kind.MESSAGE)
                e.title = "علی"
                e.subtitle = "سلام! امشب میای بیرون؟ یه جای خوب سراغ دارم 😄"
                e.iconRes = R.drawable.ic_message
                e.priority = 30
                e.ttlMs = Prefs.int("msgSec") * 1000L
                post(e)
            }
            "call" -> {
                val e = IslandEvent("demo_call", Kind.CALL)
                e.title = "مامان"
                e.subtitle = "تماس ورودی"
                e.iconRes = R.drawable.ic_call
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
                e.title = "در حال شارژ"
                e.subtitle = Fa.digits("67", fa) + "٪"
                e.iconRes = R.drawable.ic_bolt
                e.accent = 0xFF30D158.toInt()
                e.progress = 0.67f
                e.priority = 20
                e.ttlMs = Prefs.int("evtSec") * 1000L
                post(e)
            }
            "timer" -> {
                val e = IslandEvent("demo_timer", Kind.LIVE)
                e.title = "تایمر"
                e.subtitle = "در حال شمارش معکوس"
                e.iconRes = R.drawable.ic_timer
                e.accent = 0xFFFF9F0A.toInt()
                e.chronoBase = SystemClock.elapsedRealtime() + 90_000L
                e.chronoDown = true
                e.sticky = true
                e.priority = 55
                e.ttlMs = 3000
                e.reshow = true
                post(e)
                mainHandler.postDelayed({ remove("demo_timer") }, 30000)
            }
            "volume" -> {
                val e = IslandEvent("demo_vol", Kind.VOLUME)
                e.title = "صدا"
                e.iconRes = R.drawable.ic_volume_up
                e.progress = 0.6f
                e.priority = 15
                e.ttlMs = 1800
                post(e)
            }
        }
    }
}
