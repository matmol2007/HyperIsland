package com.hyperisland.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val BG = 0xFF08080B.toInt()
    private val SURF = 0xFF131318.toInt()
    private val SURF2 = 0xFF1D1D25.toInt()
    private val LINE = 0xFF26262F.toInt()
    private val SUB = 0xFF8E8E99.toInt()
    private val ACC = 0xFF0A84FF.toInt()
    private val GREEN = 0xFF30D158.toInt()
    private val ORANGE = 0xFFFF9F0A.toInt()
    private val RED = 0xFFFF453A.toInt()
    private val PURPLE = 0xFFBF5AF2.toInt()
    private val PINK = 0xFFFF375F.toInt()
    private val TEAL = 0xFF64D2FF.toInt()
    private val YELLOW = 0xFFFFD60A.toInt()

    private val updaters = ArrayList<() -> Unit>()
    private lateinit var pages: List<ScrollView>
    private val tabIcons = ArrayList<ImageView>()
    private val tabLabels = ArrayList<TextView>()
    private val tabPills = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.ensureDefaults(this)
        IslandRuntime.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR

        val rootV = LinearLayout(this)
        rootV.orientation = LinearLayout.VERTICAL
        rootV.setBackgroundColor(BG)
        val host = FrameLayout(this)
        pages = listOf(buildHome(), buildStyle(), buildFeatures(), buildKeepAlive())
        for (p in pages) host.addView(p, FrameLayout.LayoutParams(-1, -1))
        rootV.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        rootV.addView(buildTabBar(), LinearLayout.LayoutParams(-1, -2))
        setContentView(rootV)
        select(0)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.bool("enabled")) Keepalive.startService(this)
        IslandRuntime.ensureHost(this)
        IslandRuntime.setPreview(true)
        updaters.forEach { it() }
        mainHandler.postDelayed({ updaters.forEach { it() } }, 900)
    }

    override fun onPause() {
        IslandRuntime.setPreview(false)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updaters.forEach { it() }
    }

    private fun rounded(c: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = r.toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun oval(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(c)
        return g
    }

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        return t
    }

    private fun safeStart(vararg intents: Intent) {
        for (i in intents) {
            try { startActivity(i); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "This screen is not available on your phone", Toast.LENGTH_SHORT).show()
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun a11yOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(this, IslandAccessibilityService::class.java)
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString())
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun openApps(key: String, title: String) {
        val i = Intent(this, AppsActivity::class.java)
        i.putExtra("key", key)
        i.putExtra("title", title)
        startActivity(i)
    }

    private fun page(title: String, sub: String, build: LinearLayout.() -> Unit): ScrollView {
        val sv = ScrollView(this)
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        sv.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(24), dp(18), dp(32))
        col.addView(tv(title, 32f, Color.WHITE, true))
        if (sub.isNotEmpty()) {
            val s = tv(sub, 14f, SUB)
            s.setPadding(0, dp(4), 0, dp(2))
            col.addView(s)
        }
        col.build()
        sv.addView(col)
        return sv
    }

    private fun LinearLayout.section(title: String, footer: String = ""): LinearLayout {
        if (title.isNotEmpty()) {
            val h = tv(title.uppercase(), 11.5f, SUB, true)
            h.letterSpacing = 0.1f
            h.setPadding(dp(6), dp(22), 0, dp(8))
            addView(h)
        } else {
            addView(View(this@MainActivity), LinearLayout.LayoutParams(-1, dp(14)))
        }
        val card = LinearLayout(this@MainActivity)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(SURF, dp(20), LINE)
        addView(card, LinearLayout.LayoutParams(-1, -2))
        if (footer.isNotEmpty()) {
            val f = tv(footer, 12f, SUB)
            f.setPadding(dp(6), dp(8), dp(6), 0)
            addView(f)
        }
        return card
    }

    private fun LinearLayout.addRow(v: View) {
        if (childCount > 0) {
            val d = View(this@MainActivity)
            d.setBackgroundColor(LINE)
            val lp = LinearLayout.LayoutParams(-1, dp(1))
            lp.marginStart = dp(16)
            addView(d, lp)
        }
        addView(v, LinearLayout.LayoutParams(-1, -2))
    }

    private fun rowBox(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(16), dp(13), dp(16), dp(13))
        return r
    }

    private fun textCol(title: String, sub: String): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 15f, Color.WHITE))
        if (sub.isNotEmpty()) {
            val s = tv(sub, 12f, SUB)
            s.setPadding(0, dp(2), 0, 0)
            col.addView(s)
        }
        return col
    }

    private fun iconTile(icon: Int, tint: Int, size: Int = 34): View {
        val f = FrameLayout(this)
        f.background = rounded(tint, dp(10))
        val iv = ImageView(this)
        iv.setImageResource(icon)
        iv.setColorFilter(Color.WHITE)
        f.addView(iv, FrameLayout.LayoutParams(dp(size * 0.53f), dp(size * 0.53f), Gravity.CENTER))
        val lp = LinearLayout.LayoutParams(dp(size), dp(size))
        lp.marginEnd = dp(12)
        f.layoutParams = lp
        return f
    }

    private fun styleSwitch(sw: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFFB4B4BE.toInt()))
        sw.trackTintList = ColorStateList(states, intArrayOf(GREEN, 0xFF3A3A44.toInt()))
    }

    private fun LinearLayout.toggle(key: String, title: String, sub: String = "", icon: Int = 0, tint: Int = ACC) {
        val r = rowBox()
        if (icon != 0) r.addView(iconTile(icon, tint))
        r.addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this@MainActivity)
        styleSwitch(sw)
        sw.isChecked = Prefs.bool(key)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.setBool(key, v)
            if (key == "enabled" && v) Keepalive.startService(this@MainActivity)
        }
        r.addView(sw)
        r.setOnClickListener { sw.toggle() }
        addRow(r)
        updaters.add { if (sw.isChecked != Prefs.bool(key)) sw.isChecked = Prefs.bool(key) }
    }

    private fun LinearLayout.slider(key: String, title: String, min: Int, max: Int, unit: String = "") {
        val r = rowBox()
        r.orientation = LinearLayout.VERTICAL
        r.gravity = Gravity.START
        val head = LinearLayout(this@MainActivity)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(tv(title, 15f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
        val chip = tv("", 12.5f, ACC, true)
        chip.background = rounded(SURF2, dp(10))
        chip.setPadding(dp(10), dp(4), dp(10), dp(4))
        head.addView(chip)
        r.addView(head, LinearLayout.LayoutParams(-1, -2))
        val sb = SeekBar(this@MainActivity)
        sb.max = max - min
        sb.progress = Prefs.int(key) - min
        sb.progressTintList = ColorStateList.valueOf(ACC)
        sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sb.progressBackgroundTintList = ColorStateList.valueOf(0xFF3A3A44.toInt())
        sb.layoutDirection = View.LAYOUT_DIRECTION_LTR
        fun label(v: Int) { chip.text = "$v$unit" }
        label(Prefs.int(key))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                label(p + min)
                if (fromUser) Prefs.setInt(key, p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        r.addView(sb, LinearLayout.LayoutParams(-1, -2))
        addRow(r)
        updaters.add { sb.progress = Prefs.int(key) - min; label(Prefs.int(key)) }
    }

    private fun LinearLayout.colorRow(key: String, title: String, swatches: IntArray) {
        val r = rowBox()
        r.orientation = LinearLayout.VERTICAL
        r.gravity = Gravity.START
        r.addView(tv(title, 15f, Color.WHITE))
        val sc = HorizontalScrollView(this@MainActivity)
        sc.isHorizontalScrollBarEnabled = false
        val line = LinearLayout(this@MainActivity)
        val dots = ArrayList<Pair<View, Int>>()
        fun refresh() {
            val cur = Prefs.int(key)
            for ((d, c) in dots) {
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(c)
                g.setStroke(dp(if (c == cur) 3 else 1), if (c == cur) Color.WHITE else 0x44FFFFFF)
                d.background = g
            }
        }
        for (c in swatches) {
            val d = View(this@MainActivity)
            val lp = LinearLayout.LayoutParams(dp(34), dp(34))
            lp.marginEnd = dp(10)
            d.setOnClickListener { Prefs.setInt(key, c); refresh() }
            line.addView(d, lp)
            dots.add(Pair(d, c))
        }
        val hex = tv("#", 15f, Color.WHITE, true)
        hex.gravity = Gravity.CENTER
        hex.background = rounded(SURF2, dp(17), LINE)
        hex.setOnClickListener {
            val et = EditText(this@MainActivity)
            et.hint = "RRGGBB"
            et.inputType = InputType.TYPE_CLASS_TEXT
            et.layoutDirection = View.LAYOUT_DIRECTION_LTR
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Custom color (hex)")
                .setView(et)
                .setPositiveButton("Apply") { _, _ ->
                    try {
                        Prefs.setInt(key, Color.parseColor("#" + et.text.toString().trim().removePrefix("#")))
                        refresh()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Invalid color code", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        line.addView(hex, LinearLayout.LayoutParams(dp(34), dp(34)))
        sc.addView(line)
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.topMargin = dp(12)
        r.addView(sc, slp)
        addRow(r)
        refresh()
        updaters.add { refresh() }
    }

    private fun LinearLayout.segmented(key: String, title: String, options: List<String>) {
        val r = rowBox()
        r.orientation = LinearLayout.VERTICAL
        r.gravity = Gravity.START
        r.addView(tv(title, 15f, Color.WHITE))
        val box = LinearLayout(this@MainActivity)
        box.background = rounded(SURF2, dp(12))
        box.setPadding(dp(3), dp(3), dp(3), dp(3))
        val chips = ArrayList<TextView>()
        fun refresh() {
            for ((i, c) in chips.withIndex()) {
                val sel = Prefs.int(key) == i
                c.background = if (sel) rounded(ACC, dp(10)) else null
                c.setTextColor(if (sel) Color.WHITE else SUB)
            }
        }
        for ((i, o) in options.withIndex()) {
            val c = tv(o, 13.5f, SUB, true)
            c.gravity = Gravity.CENTER
            c.setPadding(dp(6), dp(9), dp(6), dp(9))
            c.setOnClickListener { Prefs.setInt(key, i); refresh() }
            box.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            chips.add(c)
        }
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.topMargin = dp(12)
        r.addView(box, blp)
        addRow(r)
        refresh()
        updaters.add { refresh() }
    }

    private fun LinearLayout.link(title: String, sub: String, icon: Int, tint: Int, onClick: () -> Unit) {
        val r = rowBox()
        if (icon != 0) r.addView(iconTile(icon, tint))
        r.addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(tv(">", 24f, SUB))
        r.setOnClickListener { onClick() }
        addRow(r)
    }

    private fun LinearLayout.info(title: String, sub: String, icon: Int, tint: Int) {
        val r = rowBox()
        r.addView(iconTile(icon, tint))
        r.addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        addRow(r)
    }

    private fun LinearLayout.guide(n: Int, title: String, sub: String) {
        val r = rowBox()
        r.gravity = Gravity.TOP
        val badge = tv("$n", 13f, Color.WHITE, true)
        badge.gravity = Gravity.CENTER
        badge.background = oval(ACC)
        val lp = LinearLayout.LayoutParams(dp(26), dp(26))
        lp.marginEnd = dp(12)
        r.addView(badge, lp)
        r.addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        addRow(r)
    }

    private fun LinearLayout.status(title: String, sub: String, ok: (() -> Boolean)?, action: String, onClick: () -> Unit) {
        val r = rowBox()
        val dot = View(this@MainActivity)
        val dl = LinearLayout.LayoutParams(dp(10), dp(10))
        dl.marginEnd = dp(12)
        r.addView(dot, dl)
        r.addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        val b = tv(action, 13f, Color.WHITE, true)
        b.gravity = Gravity.CENTER
        b.background = rounded(ACC, dp(16))
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.setOnClickListener { onClick() }
        r.addView(b)
        addRow(r)
        updaters.add {
            dot.background = oval(if (ok == null) 0xFF5A5A66.toInt() else if (ok()) GREEN else RED)
        }
    }

    private fun hero() {
        // preview area handled by IslandRuntime preview mode
    }

    private fun buildHome(): ScrollView = page("Hyper Island", "Dynamic Island for Android") {
        hero()
        section("").toggle("enabled", "Island enabled", "Turn the whole island on or off", R.drawable.ic_island, ACC)

        val s = section(
            "Setup",
            "Enable the first two to get started. Overlay is optional if Accessibility is on."
        )
        s.status("Accessibility service", "Hosts the island and keeps it alive", { a11yOn() }, "Enable") {
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        s.status("Notification access", "Music, messages, timers and live activities", { listenerOn() }, "Enable") {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        s.status("Display over other apps", "Optional fallback host", { Settings.canDrawOverlays(this@MainActivity) }, "Allow") {
            safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        s.status("Phone and contacts", "Caller names, answer and decline", {
            granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.ANSWER_PHONE_CALLS)
        }, "Allow") {
            val list = arrayListOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS, Manifest.permission.ANSWER_PHONE_CALLS
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(list.toTypedArray(), 10)
        }
        s.status("Battery unrestricted", "Prevents the system from killing the island", { batteryOk() }, "Allow") {
            safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")), appDetails())
        }
    }

    private fun buildStyle(): ScrollView = page("Style", "Look and feel of the island") {
        section("Size").apply {
            slider("scale", "Overall scale", 70, 130, "%")
            slider("compactW", "Compact width", 100, 220, "dp")
            slider("compactH", "Compact height", 26, 44, "dp")
            slider("expandW", "Expanded width", 280, 400, "dp")
            slider("radius", "Corner radius", 16, 40, "dp")
            slider("yOff", "Distance from top", 0, 40, "dp")
        }
        section("Colors").apply {
            colorRow("bg", "Background", intArrayOf(0xFF000000.toInt(), 0xFF1C1C1E.toInt(), 0xFF0A0014.toInt(), 0xFFF2F2F7.toInt()))
            colorRow("accent", "Accent", intArrayOf(0xFF0A84FF.toInt(), 0xFF30D158.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF453A.toInt()))
            colorRow("text", "Text", intArrayOf(0xFFFFFFFF.toInt(), 0xFFE5E5EA.toInt(), 0xFF111111.toInt()))
            slider("opacity", "Background opacity", 40, 100, "%")
        }
        section("Effects").apply {
            toggle("glow", "Glow border", "Thin accent stroke around the pill")
            toggle("ambient", "Ambient light", "Soft radial glow inside the island")
            toggle("dynAccent", "Dynamic accent from apps", "Use notification color when available")
            toggle("haptic", "Haptic feedback", "Vibrate on tap and expand")
            slider("animMs", "Animation duration", 200, 800, "ms")
            slider("bounce", "Spring bounce", 0, 100, "%")
        }
    }

    private fun buildFeatures(): ScrollView = page("Features", "What the island reacts to") {
        section("Media and calls").apply {
            toggle("fMusic", "Music", "Now playing with controls", R.drawable.ic_music, ACC)
            toggle("fCall", "Calls", "Incoming and ongoing calls", R.drawable.ic_call, GREEN)
            toggle("fMissed", "Missed calls", "", R.drawable.ic_call, RED)
        }
        section("Messages").apply {
            toggle("fMsg", "Messages", "Telegram, WhatsApp, SMS and more", R.drawable.ic_message, ACC)
            toggle("catMsg", "All message notifications", "Any app marked as message", R.drawable.ic_bell, TEAL)
            toggle("hideMsgText", "Hide message text", "Show New message only", R.drawable.ic_dnd, ORANGE)
            toggle("suppressNotif", "Hide from status bar", "Show only on the island, not as a normal notification", R.drawable.ic_island, PURPLE)
            link("Choose messaging apps", "Which apps can show messages", R.drawable.ic_message, ACC) {
                openApps("notifApps", "Messaging apps")
            }
            slider("msgSec", "Message display time", 2, 15, "s")
        }
        section("Live activities").apply {
            toggle("fLive", "Live activities", "Timers, downloads, navigation", R.drawable.ic_timer, TEAL)
            toggle("fTimer", "Timers", "", R.drawable.ic_timer, ACC)
            toggle("fNav", "Navigation", "", R.drawable.ic_navigation, GREEN)
            toggle("fAlarm", "Alarms and reminders", "", R.drawable.ic_alarm, ORANGE)
            link("Choose live apps", "Apps that can pin live pills", R.drawable.ic_bell, TEAL) {
                openApps("liveApps", "Live apps")
            }
        }
        section("System").apply {
            toggle("fCharge", "Charging", "Battery icon while charging", R.drawable.ic_battery_charging, GREEN)
            toggle("fFull", "Fully charged", "", R.drawable.ic_battery, GREEN)
            toggle("fLow", "Low battery", "", R.drawable.ic_battery, RED)
            toggle("fSilent", "Ringer mode", "", R.drawable.ic_volume_off, ORANGE)
            toggle("fAirplane", "Airplane mode", "", R.drawable.ic_airplane, TEAL)
            toggle("fDnd", "Do Not Disturb", "", R.drawable.ic_dnd, PURPLE)
            toggle("fFlash", "Flashlight", "", R.drawable.ic_flashlight, YELLOW)
            toggle("fHeadset", "Headphones", "", R.drawable.ic_headphones, ACC)
            toggle("fBt", "Bluetooth audio", "", R.drawable.ic_headphones, ACC)
            toggle("fVolume", "Volume HUD", "Show volume changes on the island", R.drawable.ic_volume_up, TEAL)
            slider("evtSec", "System event time", 2, 10, "s")
            slider("lowPct", "Low battery threshold", 5, 30, "%")
        }
    }

    private fun buildKeepAlive(): ScrollView = page("Keep alive", "Stay running in the background") {
        section("Protection").apply {
            toggle("hideLand", "Hide in landscape", "Avoid overlapping games and video")
            link("Autostart (Xiaomi / OEM)", "Allow the app to start after reboot", R.drawable.ic_tab_shield, ACC) {
                safeStart(appDetails())
            }
            link("Battery settings", "No restrictions", R.drawable.ic_battery, GREEN) {
                safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails())
            }
        }
        section("How to lock the app").apply {
            guide(1, "Open recent apps", "Swipe up and hold, or tap the square button")
            guide(2, "Lock Hyper Island", "Pull down on the app card or tap the lock icon")
            guide(3, "Done", "The island will survive app switches and light sleep")
        }
    }

    private fun buildTabBar(): View {
        val outer = FrameLayout(this)
        outer.setPadding(dp(16), dp(10), dp(16), dp(18))
        outer.setBackgroundColor(BG)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.background = rounded(SURF, dp(28), LINE)
        bar.setPadding(dp(6), dp(8), dp(6), dp(8))
        bar.layoutDirection = View.LAYOUT_DIRECTION_LTR

        val items = listOf(
            Pair("Home", R.drawable.ic_tab_home),
            Pair("Style", R.drawable.ic_tab_style),
            Pair("Features", R.drawable.ic_tab_features),
            Pair("Keep alive", R.drawable.ic_tab_shield)
        )
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(4), dp(4), dp(4), dp(4))

            val holder = FrameLayout(this)
            val pill = View(this)
            pill.background = rounded(ACC.withAlpha(55), dp(18))
            pill.alpha = 0f
            val icon = ImageView(this)
            icon.setImageResource(item.second)
            icon.setColorFilter(SUB)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.addView(pill, FrameLayout.LayoutParams(dp(52), dp(30), Gravity.CENTER))
            holder.addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            cell.addView(holder, LinearLayout.LayoutParams(dp(56), dp(30)))

            val label = tv(item.first, 10.5f, SUB, true)
            label.gravity = Gravity.CENTER
            label.setPadding(0, dp(4), 0, 0)
            label.maxLines = 1
            label.layoutDirection = View.LAYOUT_DIRECTION_LTR
            cell.addView(label)

            cell.setOnClickListener { select(i) }
            bar.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            tabIcons.add(icon)
            tabLabels.add(label)
            tabPills.add(pill)
        }
        outer.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        return outer
    }

    private fun select(i: Int) {
        for ((k, p) in pages.withIndex()) {
            if (k == i) {
                if (p.visibility != View.VISIBLE) {
                    p.visibility = View.VISIBLE
                    p.alpha = 0f
                    p.animate().alpha(1f).setDuration(150).start()
                }
            } else {
                p.animate().cancel()
                p.visibility = View.GONE
            }
            tabIcons[k].setColorFilter(if (k == i) ACC else SUB)
            tabLabels[k].setTextColor(if (k == i) ACC else SUB)
            tabPills[k].animate().alpha(if (k == i) 1f else 0f).setDuration(150).start()
        }
        updaters.forEach { it() }
    }
}
