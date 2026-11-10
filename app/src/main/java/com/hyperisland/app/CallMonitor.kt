package com.hyperisland.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

class CallMonitor(private val ctx: Context) {
    private var number: String? = null
    private var name: String? = null
    private var wasRinging = false
    private var connectedAt = 0L
    private var cur: IslandEvent? = null
    private val green = 0xFF30D158.toInt()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try { handle(i) } catch (e: Exception) { }
        }
    }

    fun start() {
        registerSys(ctx, receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (e: Exception) { }
        IslandRuntime.remove("call")
    }

    private fun lookup(num: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    private fun display(): String = name ?: number ?: "Unknown number"

    private fun handle(i: Intent) {
        if (!Prefs.bool("fCall")) return
        val state = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                connectedAt = 0L
                if (!num.isNullOrBlank()) {
                    number = num
                    name = lookup(num)
                }
                post(true)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (connectedAt == 0L) connectedAt = SystemClock.elapsedRealtime()
                post(false)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val missed = wasRinging && connectedAt == 0L
                val who = display()
                IslandRuntime.remove("call")
                cur = null
                if (missed && Prefs.bool("fMissed")) {
                    val e = IslandEvent("missed", Kind.MESSAGE)
                    e.title = who
                    e.subtitle = "Missed call"
                    e.iconRes = R.drawable.ic_call
                    e.accent = 0xFFFF453A.toInt()
                    e.priority = 35
                    e.ttlMs = 5000
                    e.tap = { dialer() }
                    IslandRuntime.post(e)
                }
                wasRinging = false
                connectedAt = 0L
                number = null
                name = null
            }
        }
    }

    private fun post(ringing: Boolean) {
        val e = cur ?: IslandEvent("call", Kind.CALL)
        cur = e
        e.title = display()
        e.ringing = ringing
        e.subtitle = if (ringing) "Incoming call" else "On call"
        e.iconRes = R.drawable.ic_call
        e.accent = green
        e.sticky = true
        e.priority = 100
        e.ttlMs = if (ringing) 0L else 3000L
        e.reshow = true
        e.chronoBase = if (ringing) 0L else connectedAt
        e.chronoDown = false
        e.onAnswer = { answer() }
        e.onDecline = { decline() }
        e.tap = { dialer() }
        IslandRuntime.post(e)
    }

    fun nameFromNotification(title: String?) {
        val e = cur ?: return
        if (name == null && !title.isNullOrBlank()) {
            name = title
            e.title = title
            IslandRuntime.post(e)
        }
    }

    private fun answer() {
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
        } catch (e: Exception) {
            dialer()
        }
    }

    private fun decline() {
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= 28) tm.endCall() else dialer()
        } catch (e: Exception) {
            dialer()
        }
    }

    private fun dialer() {
        try {
            val i = Intent(Intent.ACTION_DIAL)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) { }
    }
}
