package com.hyperisland.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object Keepalive {
    fun startService(ctx: Context): Boolean = try {
        ctx.startForegroundService(Intent(ctx, IslandService::class.java))
        true
    } catch (e: Exception) { false }

    fun schedule(ctx: Context, delayMs: Long) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getForegroundService(
                ctx, 77, Intent(ctx, IslandService::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
        } catch (e: Exception) { }
    }

    fun scheduleWatchdog(ctx: Context) {
        try {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val job = JobInfo.Builder(1001, ComponentName(ctx, WatchdogJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(job)
        } catch (e: Exception) { }
    }
}

class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Prefs.init(this)
        if (Prefs.bool("enabled") && IslandRuntime.controller == null) Keepalive.startService(this)
        return false
    }
    override fun onStopJob(params: JobParameters?): Boolean = false
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Prefs.init(ctx)
        if (Prefs.bool("enabled")) {
            Keepalive.startService(ctx)
            Keepalive.scheduleWatchdog(ctx)
        }
    }
}
