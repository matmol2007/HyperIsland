package com.hyperisland.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class IslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandRuntime.attach(this, this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 2)
        Keepalive.startService(this)
        Keepalive.scheduleWatchdog(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg == packageName) return
        if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.")) return
        IslandRuntime.onForegroundPackage(pkg)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        IslandRuntime.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        IslandRuntime.detach(this)
        super.onDestroy()
    }
}
