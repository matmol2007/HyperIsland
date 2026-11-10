package com.hyperisland.app

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Locale

val mainHandler: Handler = Handler(Looper.getMainLooper())

fun dp(v: Float): Int = (v * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
fun dp(v: Int): Int = dp(v.toFloat())
fun dpF(v: Float): Float = v * Resources.getSystem().displayMetrics.density

fun Int.withAlpha(a: Int): Int = (this and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

fun brighten(c: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(c, hsv)
    if (hsv[2] < 0.85f) hsv[2] = 0.85f
    return Color.HSVToColor(hsv)
}

object Fa {
    fun digits(s: String, on: Boolean = true): String {
        return s  // UI is English: keep Western digits
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun registerSys(ctx: Context, r: BroadcastReceiver, f: IntentFilter) {
    if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
    else ctx.registerReceiver(r, f)
}

object Launch {
    fun send(ctx: Context, pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val o = ActivityOptions.makeBasic()
                o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, o.toBundle())
            } else {
                pi.send()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun pkg(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
        }
    }
}
