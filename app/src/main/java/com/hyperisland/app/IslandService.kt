package com.hyperisland.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

class IslandService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        IslandRuntime.init(this)
        startFg()
        if (Settings.canDrawOverlays(this)) {
            IslandRuntime.attach(this, this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 1)
        }
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel("island_quiet", "Hyper Island service", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false)
        ch.enableLights(false)
        ch.enableVibration(false)
        ch.setSound(null, null)
        if (Build.VERSION.SDK_INT >= 29) ch.setAllowBubbles(false)
        nm.createNotificationChannel(ch)
        // Remove old louder channel if present
        try { nm.deleteNotificationChannel("island") } catch (e: Exception) { }

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "island_quiet")
            .setSmallIcon(R.drawable.ic_island)
            .setContentTitle("Hyper Island")
            .setContentText("Active")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        Keepalive.schedule(this, 1500)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        IslandRuntime.detach(this)
        Prefs.init(this)
        if (Prefs.bool("enabled")) Keepalive.schedule(this, 3000)
        super.onDestroy()
    }
}
