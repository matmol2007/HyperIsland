package com.hyperisland.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private var sp: SharedPreferences? = null
    val prefs: SharedPreferences get() = sp!!

    private val D: Map<String, Any> = mapOf(
        "enabled" to true,
        // Slightly taller/wider compact pill + more top offset so it clears the punch-hole camera
        "compactW" to 156, "compactH" to 36, "expandW" to 360,
        "radius" to 32, "yOff" to 12, "xOff" to 0, "scale" to 100,
        "bg" to 0xFF000000.toInt(), "opacity" to 96,
        "accent" to 0xFF0A84FF.toInt(), "text" to 0xFFFFFFFF.toInt(),
        "glow" to true, "ambient" to true, "dynAccent" to true, "layoutDir" to 1,
        "animMs" to 420, "bounce" to 55,
        "hideLand" to true, "haptic" to true, "faDigits" to false,
        "msgSec" to 5, "evtSec" to 3, "popupTrack" to true, "hideMsgText" to false,
        "suppressNotif" to true,
        "fMusic" to true, "fCall" to true, "fMissed" to true, "fMsg" to true, "catMsg" to true,
        "fLive" to true, "fTimer" to true, "fNav" to true, "fAlarm" to true,
        "fCharge" to true, "fUnplug" to false, "fFull" to true, "fLow" to true, "lowPct" to 15,
        "fSilent" to true, "fSaver" to true, "fAirplane" to true,
        "fHeadset" to true, "fBt" to true, "fDnd" to true, "fFlash" to true, "fVolume" to false
    )

    fun init(ctx: Context) {
        if (sp == null) {
            synchronized(this) {
                if (sp == null) sp = ctx.applicationContext.getSharedPreferences("hyper_island", Context.MODE_PRIVATE)
            }
        }
    }

    fun int(k: String): Int = prefs.getInt(k, D[k] as Int)
    fun bool(k: String): Boolean = prefs.getBoolean(k, D[k] as Boolean)
    fun setInt(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    fun setBool(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }
    fun strSet(k: String): Set<String> = prefs.getStringSet(k, emptySet()) ?: emptySet()
    fun setStrSet(k: String, s: Set<String>) { prefs.edit().putStringSet(k, HashSet(s)).apply() }

    private val knownMessengers = setOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.whatsapp", "com.whatsapp.w4b", "ir.nasim", "ir.resaneh1.iptv",
        "com.instagram.android", "com.google.android.gm", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.discord"
    )
    private val knownLive = setOf(
        "cab.snapp.passenger", "taxi.tap30.passenger", "com.google.android.apps.maps",
        "ir.balad", "org.rajman.neshan.traffic.tehran", "com.waze"
    )

    fun ensureDefaults(ctx: Context) {
        init(ctx)
        val e = prefs.edit()
        if (!prefs.contains("notifApps")) e.putStringSet("notifApps", HashSet(knownMessengers))
        if (!prefs.contains("liveApps")) e.putStringSet("liveApps", HashSet(knownLive))
        if (!prefs.contains("hiddenApps")) e.putStringSet("hiddenApps", HashSet<String>())
        // One-time bump of yOff for users who still have the old too-low default
        if (!prefs.contains("_notchBump")) {
            if (prefs.getInt("yOff", 4) <= 6) e.putInt("yOff", 12)
            e.putBoolean("_notchBump", true)
        }
        e.apply()
    }

    val presetNames = listOf("Classic", "Hyper", "Glass", "Neon", "Minimal", "Light")
    val presetBg = listOf(0xFF000000L, 0xFF000000L, 0xFF1C1C1EL, 0xFF0A0014L, 0xFF000000L, 0xFFF2F2F7L)
    val presetAcc = listOf(0xFF0A84FFL, 0xFF30D158L, 0xFF64D2FFL, 0xFFBF5AF2L, 0xFFFFFFFFL, 0xFF0A84FFL)

    fun applyPreset(i: Int) {
        val e = prefs.edit()
        fun c(bg: Long, acc: Long, txt: Long, op: Int, glow: Boolean, rad: Int, cw: Int, ch: Int) {
            e.putInt("bg", bg.toInt()).putInt("accent", acc.toInt()).putInt("text", txt.toInt())
                .putInt("opacity", op).putBoolean("glow", glow).putInt("radius", rad)
                .putInt("compactW", cw).putInt("compactH", ch)
        }
        when (i) {
            0 -> c(0xFF000000, 0xFF0A84FF, 0xFFFFFFFF, 96, true, 32, 156, 36)
            1 -> c(0xFF000000, 0xFF30D158, 0xFFFFFFFF, 100, true, 32, 160, 36)
            2 -> c(0xFF1C1C1E, 0xFF64D2FF, 0xFFFFFFFF, 78, true, 34, 150, 34)
            3 -> c(0xFF0A0014, 0xFFBF5AF2, 0xFFFFFFFF, 100, true, 30, 150, 34)
            4 -> c(0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 100, false, 26, 118, 30)
            else -> c(0xFFF2F2F7, 0xFF0A84FF, 0xFF111111, 100, false, 30, 150, 34)
        }
        e.apply()
    }
}
