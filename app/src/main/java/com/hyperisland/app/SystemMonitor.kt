package com.hyperisland.app

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.app.NotificationManager

class SystemMonitor(private val ctx: Context, private val ctrl: IslandController) {

    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val cam: CameraManager? = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var plugged = -1
    private var lowFired = false
    private var fullFired = false
    private var lastRinger = am.ringerMode
    private var lastSaver = pm.isPowerSaveMode
    private var lastDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    private fun t(id: Int): String = Lang.s(ctx, id)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try { handle(i, isInitialStickyBroadcast) } catch (e: Exception) { }
        }
    }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!Prefs.bool("fFlash")) return
            if (enabled) {
                val e = ev("torch", t(R.string.evt_torch_on), t(R.string.evt_torch_sub), R.drawable.ic_flashlight, 0xFFFFD60A.toInt())
                e.sticky = true
                e.priority = 30
                e.tap = { try { cam?.setTorchMode(cameraId, false) } catch (x: Exception) { } }
                ctrl.post(e)
            } else {
                ctrl.remove("torch")
            }
        }
    }

    private var recStart = 0L

    /** Fires when any app starts/stops using the microphone (configs are anonymized, but the count is visible). */
    private val recCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            if (!Prefs.bool("fRecord")) {
                if (recStart != 0L) { recStart = 0L; ctrl.remove("rec") }
                return
            }
            val active = !configs.isNullOrEmpty()
            if (active && recStart == 0L) {
                recStart = SystemClock.elapsedRealtime()
                val e = ev("rec", t(R.string.evt_recording), t(R.string.evt_recording_sub), R.drawable.ic_mic, 0xFFFF453A.toInt())
                e.sticky = true
                e.priority = 35
                e.chronoBase = recStart
                ctrl.post(e)
            } else if (!active && recStart != 0L) {
                recStart = 0L
                ctrl.remove("rec")
            }
        }
    }

    fun start() {
        val f = IntentFilter()
        f.addAction(Intent.ACTION_BATTERY_CHANGED)
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        f.addAction(Intent.ACTION_HEADSET_PLUG)
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        f.addAction("android.media.VOLUME_CHANGED_ACTION")
        f.addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        registerSys(ctx, receiver, f)
        try { cam?.registerTorchCallback(torchCb, mainHandler) } catch (e: Exception) { }
        try { am.registerAudioRecordingCallback(recCb, mainHandler) } catch (e: Exception) { }
        updateOrientation()
    }

    fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (e: Exception) { }
        try { cam?.unregisterTorchCallback(torchCb) } catch (e: Exception) { }
        try { am.unregisterAudioRecordingCallback(recCb) } catch (e: Exception) { }
        recStart = 0L
        ctrl.remove("torch")
        ctrl.remove("rec")
    }

    /**
     * Sample-style system pills: short title in compact (icon + text),
     * optional rightText for %, progress bar when expanded.
     */
    private fun ev(
        key: String,
        title: String,
        sub: String,
        icon: Int,
        accent: Int = 0,
        progress: Float = -1f,
        right: String = "",
        /** When true, compact shows title (not only %). Matches Silent/Battery saver samples. */
        compactTitle: Boolean = true
    ): IslandEvent {
        val e = IslandEvent(key, Kind.SYSTEM)
        e.title = title
        e.subtitle = sub
        e.iconRes = icon
        e.accent = accent
        e.progress = progress
        // Prefer title on the pill like the sample; keep % as rightText for expanded
        e.rightText = if (compactTitle) "" else right
        if (!compactTitle && right.isNotEmpty()) e.rightText = right
        // Store % in subtitle area when we want title-only compact but still show % expanded
        if (compactTitle && right.isNotEmpty() && sub.isEmpty()) {
            e.subtitle = right
        }
        e.priority = 20
        e.ttlMs = Prefs.int("evtSec") * 1000L
        return e
    }

    private fun updateOrientation() {
        val dm = ctx.resources.displayMetrics
        ctrl.setLandscape(dm.widthPixels > dm.heightPixels)
    }

    private fun pct(n: Int): String = "$n%"

    private fun handle(i: Intent, initial: Boolean) {
        when (i.action) {
            Intent.ACTION_CONFIGURATION_CHANGED -> updateOrientation()

            Intent.ACTION_BATTERY_CHANGED -> {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level < 0 || scale <= 0) return
                val p = level * 100 / scale
                val pl = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val now = pl != 0
                val low = Prefs.int("lowPct")
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val fast = status == BatteryManager.BATTERY_STATUS_CHARGING && p < 80

                if (plugged == -1 || initial) {
                    plugged = if (now) 1 else 0
                    lowFired = p <= low
                    fullFired = p >= 100
                    return
                }
                if (now && plugged == 0) {
                    plugged = 1
                    lowFired = false
                    fullFired = p >= 100
                    if (Prefs.bool("fCharge")) {
                        val title = if (fast) t(R.string.evt_quick_charge) else t(R.string.evt_charging)
                        // Sample: "Quick charging" as title; % on the right when expanded
                        val e = IslandEvent("charge", Kind.SYSTEM)
                        e.title = title
                        e.subtitle = pct(p)
                        e.iconRes = R.drawable.ic_battery_charging
                        e.accent = 0xFF30D158.toInt()
                        e.progress = p / 100f
                        e.rightText = pct(p)
                        e.priority = 20
                        e.ttlMs = Prefs.int("evtSec") * 1000L
                        ctrl.post(e)
                    }
                } else if (!now && plugged == 1) {
                    plugged = 0
                    if (Prefs.bool("fUnplug")) {
                        val e = IslandEvent("charge", Kind.SYSTEM)
                        e.title = t(R.string.evt_unplugged)
                        e.subtitle = pct(p)
                        e.iconRes = R.drawable.ic_battery
                        e.progress = p / 100f
                        e.rightText = pct(p)
                        e.priority = 20
                        e.ttlMs = Prefs.int("evtSec") * 1000L
                        ctrl.post(e)
                    }
                }
                if (now) {
                    if (p >= 100 && !fullFired) {
                        fullFired = true
                        if (Prefs.bool("fFull")) {
                            val e = IslandEvent("full", Kind.SYSTEM)
                            e.title = t(R.string.evt_fully_charged)
                            e.subtitle = t(R.string.evt_fully_sub)
                            e.iconRes = R.drawable.ic_battery_charging
                            e.accent = 0xFF30D158.toInt()
                            e.progress = 1f
                            e.rightText = pct(100)
                            e.priority = 20
                            e.ttlMs = Prefs.int("evtSec") * 1000L
                            ctrl.post(e)
                        }
                    }
                } else {
                    if (p <= low && !lowFired) {
                        lowFired = true
                        if (Prefs.bool("fLow")) {
                            val e = IslandEvent("low", Kind.SYSTEM)
                            e.title = t(R.string.evt_low_battery)
                            e.subtitle = t(R.string.evt_low_sub)
                            e.iconRes = R.drawable.ic_battery
                            e.accent = 0xFFFF453A.toInt()
                            e.progress = p / 100f
                            e.rightText = pct(p)
                            e.priority = 20
                            e.ttlMs = 4000
                            ctrl.post(e)
                        }
                    }
                    if (p > low + 3) lowFired = false
                }
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val m = am.ringerMode
                if (m != lastRinger && Prefs.bool("fSilent")) {
                    // Sample: "Silent mode is off" / on — title only in compact
                    val e = when (m) {
                        AudioManager.RINGER_MODE_SILENT -> {
                            val x = IslandEvent("ringer", Kind.SYSTEM)
                            x.title = t(R.string.evt_silent)
                            x.iconRes = R.drawable.ic_volume_off
                            x.accent = 0xFFFF9F0A.toInt()
                            x
                        }
                        AudioManager.RINGER_MODE_VIBRATE -> {
                            val x = IslandEvent("ringer", Kind.SYSTEM)
                            x.title = t(R.string.evt_vibrate)
                            x.subtitle = t(R.string.evt_vibrate_sub)
                            x.iconRes = R.drawable.ic_volume_off
                            x.accent = 0xFFFF9F0A.toInt()
                            x
                        }
                        else -> {
                            val x = IslandEvent("ringer", Kind.SYSTEM)
                            x.title = t(R.string.evt_silent_off)
                            x.iconRes = R.drawable.ic_volume_up
                            x.accent = 0xFF30D158.toInt()
                            x
                        }
                    }
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
                lastRinger = m
            }

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val on = pm.isPowerSaveMode
                if (on != lastSaver && Prefs.bool("fSaver")) {
                    // Sample: yellow "Battery saver" title
                    val e = IslandEvent("saver", Kind.SYSTEM)
                    e.title = if (on) t(R.string.evt_saver_on) else t(R.string.evt_saver_off)
                    e.iconRes = R.drawable.ic_battery
                    e.accent = if (on) 0xFFFFD60A.toInt() else 0
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
                lastSaver = on
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                if (Prefs.bool("fAirplane")) {
                    val on = i.getBooleanExtra("state", false)
                    val e = IslandEvent("airplane", Kind.SYSTEM)
                    e.title = if (on) t(R.string.evt_airplane_on) else t(R.string.evt_airplane_off)
                    e.iconRes = R.drawable.ic_airplane
                    e.accent = 0xFF64D2FF.toInt()
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
            }

            Intent.ACTION_HEADSET_PLUG -> {
                if (!initial && Prefs.bool("fHeadset")) {
                    val on = i.getIntExtra("state", 0) == 1
                    val e = IslandEvent("headset", Kind.SYSTEM)
                    e.title = if (on) t(R.string.evt_headset_on) else t(R.string.evt_headset_off)
                    e.iconRes = R.drawable.ic_headphones
                    e.accent = 0xFF0A84FF.toInt()
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (Prefs.bool("fBt")) {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val major = d?.bluetoothClass?.majorDeviceClass
                    if (d != null && major != null && major != BluetoothClass.Device.Major.AUDIO_VIDEO) return
                    val name = try { d?.name } catch (e: SecurityException) { null } ?: "Bluetooth"
                    val on = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    val e = IslandEvent("bt", Kind.SYSTEM)
                    e.title = if (on) t(R.string.evt_bt_on) else t(R.string.evt_bt_off)
                    e.subtitle = name
                    e.iconRes = R.drawable.ic_headphones
                    e.accent = 0xFF0A84FF.toInt()
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
            }

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                if (on != lastDnd && Prefs.bool("fDnd")) {
                    val e = IslandEvent("dnd", Kind.SYSTEM)
                    e.title = if (on) t(R.string.evt_dnd_on) else t(R.string.evt_dnd_off)
                    e.iconRes = R.drawable.ic_dnd
                    e.accent = 0xFFBF5AF2.toInt()
                    e.priority = 20
                    e.ttlMs = Prefs.int("evtSec") * 1000L
                    ctrl.post(e)
                }
                lastDnd = on
            }

            "android.media.VOLUME_CHANGED_ACTION" -> {
                if (!Prefs.bool("fVolume")) return
                val st = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (st != AudioManager.STREAM_MUSIC && st != AudioManager.STREAM_RING) return
                val v = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                val max = am.getStreamMaxVolume(st).coerceAtLeast(1)
                val e = IslandEvent("volume", Kind.VOLUME)
                e.title = if (st == AudioManager.STREAM_MUSIC) t(R.string.evt_volume) else t(R.string.evt_ringer)
                e.iconRes = if (v == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
                e.progress = v.toFloat() / max
                e.rightText = "${(v * 100 / max)}%"
                e.priority = 15
                e.ttlMs = 1800
                ctrl.post(e)
            }
        }
    }
}
