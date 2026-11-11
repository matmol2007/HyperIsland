package com.hyperisland.app

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try { handle(i, isInitialStickyBroadcast) } catch (e: Exception) { }
        }
    }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!Prefs.bool("fFlash")) return
            if (enabled) {
                val e = ev("torch", "Flashlight is on", "Tap to turn off", R.drawable.ic_flashlight, 0xFFFFD60A.toInt())
                e.sticky = true
                e.priority = 30
                e.tap = { try { cam?.setTorchMode(cameraId, false) } catch (x: Exception) { } }
                ctrl.post(e)
            } else {
                ctrl.remove("torch")
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
        updateOrientation()
    }

    fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (e: Exception) { }
        try { cam?.unregisterTorchCallback(torchCb) } catch (e: Exception) { }
        ctrl.remove("torch")
    }

    private fun ev(key: String, title: String, sub: String, icon: Int, accent: Int = 0, progress: Float = -1f, right: String = ""): IslandEvent {
        val e = IslandEvent(key, Kind.SYSTEM)
        e.title = title
        e.subtitle = sub
        e.iconRes = icon
        e.accent = accent
        e.progress = progress
        e.rightText = right
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
                        val kind = when {
                            (pl and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Wireless"
                            (pl and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
                            else -> "Charger"
                        }
                        ctrl.post(ev("charge", "Charging", kind, R.drawable.ic_bolt, 0xFF30D158.toInt(), p / 100f, pct(p)))
                    }
                } else if (!now && plugged == 1) {
                    plugged = 0
                    if (Prefs.bool("fUnplug")) {
                        ctrl.post(ev("charge", "Charger unplugged", "", R.drawable.ic_battery, 0, p / 100f, pct(p)))
                    }
                }
                if (now) {
                    if (p >= 100 && !fullFired) {
                        fullFired = true
                        if (Prefs.bool("fFull")) ctrl.post(ev("full", "Fully charged", "Unplug to save battery health", R.drawable.ic_battery, 0xFF30D158.toInt(), 1f, pct(100)))
                    }
                } else {
                    if (p <= low && !lowFired) {
                        lowFired = true
                        if (Prefs.bool("fLow")) {
                            val e = ev("low", "Low battery", "Plug in your charger soon", R.drawable.ic_battery, 0xFFFF453A.toInt(), p / 100f, pct(p))
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
                    val e = when (m) {
                        AudioManager.RINGER_MODE_SILENT -> ev("ringer", "Silent", "Ringer is off", R.drawable.ic_volume_off, 0xFFFF9F0A.toInt())
                        AudioManager.RINGER_MODE_VIBRATE -> ev("ringer", "Vibrate", "Vibration only", R.drawable.ic_volume_off, 0xFFFF9F0A.toInt())
                        else -> ev("ringer", "Ringer on", "Sound is on", R.drawable.ic_volume_up, 0xFF30D158.toInt())
                    }
                    ctrl.post(e)
                }
                lastRinger = m
            }

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val on = pm.isPowerSaveMode
                if (on != lastSaver && Prefs.bool("fSaver")) {
                    ctrl.post(ev("saver", if (on) "Battery saver on" else "Battery saver off", "", R.drawable.ic_battery, if (on) 0xFFFFD60A.toInt() else 0))
                }
                lastSaver = on
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                if (Prefs.bool("fAirplane")) {
                    val on = i.getBooleanExtra("state", false)
                    ctrl.post(ev("airplane", if (on) "Airplane mode on" else "Airplane mode off", "", R.drawable.ic_airplane, 0xFF64D2FF.toInt()))
                }
            }

            Intent.ACTION_HEADSET_PLUG -> {
                if (!initial && Prefs.bool("fHeadset")) {
                    val on = i.getIntExtra("state", 0) == 1
                    ctrl.post(ev("headset", if (on) "Headphones connected" else "Headphones disconnected", "", R.drawable.ic_headphones, 0xFF0A84FF.toInt()))
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (Prefs.bool("fBt")) {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val major = d?.bluetoothClass?.majorDeviceClass
                    if (d != null && major != null && major != BluetoothClass.Device.Major.AUDIO_VIDEO) return
                    val name = try { d?.name } catch (e: SecurityException) { null } ?: "Bluetooth device"
                    val on = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    ctrl.post(ev("bt", if (on) "Connected" else "Disconnected", name, R.drawable.ic_headphones, 0xFF0A84FF.toInt()))
                }
            }

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                if (on != lastDnd && Prefs.bool("fDnd")) {
                    ctrl.post(ev("dnd", if (on) "Do Not Disturb on" else "Do Not Disturb off", "", R.drawable.ic_dnd, 0xFFBF5AF2.toInt()))
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
                e.title = if (st == AudioManager.STREAM_MUSIC) "Volume" else "Ringer"
                e.iconRes = if (v == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
                e.progress = v.toFloat() / max
                e.priority = 15
                e.ttlMs = 1800
                ctrl.post(e)
            }
        }
    }
}
