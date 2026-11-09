package com.hyperisland.app

import android.app.Notification
import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class HyperNotificationListener : NotificationListenerService() {

    private var media: MediaMonitor? = null
    private val seen = HashSet<String>()
    private val lastMsg = HashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        IslandRuntime.init(applicationContext)
        IslandRuntime.ensureHost(this)
        media?.stop()
        val m = MediaMonitor(this, ComponentName(this, HyperNotificationListener::class.java))
        media = m
        IslandRuntime.media = m
        m.start()
        try {
            activeNotifications?.forEach { handle(it, true) }
        } catch (e: Exception) { }
    }

    override fun onListenerDisconnected() {
        media?.stop()
        media = null
        IslandRuntime.media = null
        try { requestRebind(ComponentName(this, HyperNotificationListener::class.java)) } catch (e: Exception) { }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try { handle(sbn, false) } catch (e: Exception) { }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = "n:" + sbn.key
        seen.remove(key)
        lastMsg.remove(key)
        IslandRuntime.remove(key)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun avatarOf(n: Notification, pkg: String): Drawable? {
        try {
            val d = n.getLargeIcon()?.loadDrawable(this)
            if (d != null) return d
        } catch (e: Exception) { }
        return try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    private fun fill(e: IslandEvent, n: Notification, pkg: String, title: String?, text: String?) {
        e.title = if (title.isNullOrBlank()) appLabel(pkg) else title
        e.subtitle = text ?: ""
        e.avatar = avatarOf(n, pkg)
        e.accent = if (n.color != 0 && Prefs.bool("dynAccent")) brighten(n.color) else 0
        e.tap = {
            if (!Launch.send(this, n.contentIntent)) Launch.pkg(this, pkg)
        }
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val ex = n.extras ?: return
        val cat = n.category
        if (cat == Notification.CATEGORY_TRANSPORT || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()

        if (cat == Notification.CATEGORY_CALL) {
            IslandRuntime.onCallNotification(title)
            return
        }

        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val key = "n:" + sbn.key
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val hasProg = pMax > 0 && !ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val isNav = cat == "navigation"

        if (chrono && ongoing && Prefs.bool("fLive") && Prefs.bool("fTimer")) {
            live(key, n, pkg, title, text, initial, 55, R.drawable.ic_timer, hasProg, pMax, ex.getInt(Notification.EXTRA_PROGRESS, 0), true)
        } else if (isNav && Prefs.bool("fLive") && Prefs.bool("fNav")) {
            live(key, n, pkg, title, text, initial, 60, R.drawable.ic_navigation, false, 0, 0, false)
        } else if (hasProg && Prefs.bool("fLive")) {
            live(key, n, pkg, title, text, initial, 50, R.drawable.ic_download, true, pMax, ex.getInt(Notification.EXTRA_PROGRESS, 0), false)
        } else if (ongoing && pkg in Prefs.strSet("liveApps") && Prefs.bool("fLive")) {
            live(key, n, pkg, title, text, initial, 45, R.drawable.ic_bell, false, 0, 0, false)
        } else if (!ongoing && !initial && Prefs.bool("fAlarm") && (cat == Notification.CATEGORY_ALARM || cat == Notification.CATEGORY_REMINDER)) {
            val e = IslandEvent(key, Kind.MESSAGE)
            fill(e, n, pkg, title, text)
            e.iconRes = R.drawable.ic_alarm
            e.priority = 50
            e.ttlMs = 10000
            IslandRuntime.post(e)
        } else if (!ongoing && !initial && Prefs.bool("fMsg") &&
            (pkg in Prefs.strSet("notifApps") || (Prefs.bool("catMsg") && cat == Notification.CATEGORY_MESSAGE))
        ) {
            if (title.isNullOrBlank() && text.isNullOrBlank()) return
            val sig = (title ?: "") + "|" + (text ?: "")
            if (lastMsg[key] == sig) return
            lastMsg[key] = sig
            val e = IslandEvent(key, Kind.MESSAGE)
            fill(e, n, pkg, title, text)
            if (Prefs.bool("hideMsgText")) e.subtitle = "پیام جدید"
            e.iconRes = R.drawable.ic_message
            e.priority = 30
            e.ttlMs = Prefs.int("msgSec") * 1000L
            IslandRuntime.post(e)
        }
    }

    private fun live(
        key: String, n: Notification, pkg: String, title: String?, text: String?, initial: Boolean,
        prio: Int, icon: Int, hasProg: Boolean, max: Int, cur: Int, chrono: Boolean
    ) {
        val e = IslandEvent(key, Kind.LIVE)
        fill(e, n, pkg, title, text)
        e.iconRes = icon
        e.sticky = true
        e.priority = prio
        e.popup = !initial && key !in seen
        e.ttlMs = 3000
        seen.add(key)
        if (hasProg) {
            e.progress = cur.toFloat() / max
            if (!chrono) e.rightText = Fa.digits("${cur * 100 / max}", Prefs.bool("faDigits")) + "٪"
        }
        if (chrono) {
            val nowEl = SystemClock.elapsedRealtime()
            e.chronoBase = nowEl + (n.`when` - System.currentTimeMillis())
            e.chronoDown = Build.VERSION.SDK_INT >= 24 &&
                    n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        }
        IslandRuntime.post(e)
    }
}
