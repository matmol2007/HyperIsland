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
        // the service may need a moment to attach; refresh the status once more
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

    // ================================================================ helpers

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
        Toast.makeText(this, "This screen isn't available on your phone", Toast.LENGTH_SHORT).show()
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

    // ================================================================ building blocks

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
        r.addView(tv("›", 24f, SUB))
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

    // ================================================================ pages

    private fun buildHome(): ScrollView = page("Hyper Island", "Dynamic Island for Android") {
        hero()
        section("").toggle("enabled", "Island enabled", "Turn the whole island on or off", R.drawable.ic_island, ACC)

        val s = section(
            "Setup",
            "Enable the first two to get started. The overlay permission is only needed if you skip the Accessibility service."
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
        s.status("Phone & contacts", "Caller names, answer and decline", {
            granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.ANSWER_PHONE_CALLS)
        }, "Allow") {
            val list = arrayListOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS, Manifest.permission.ANSWER_PHONE_CALLS
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) list.add(Manifest.permission.BLUETOOTH_CONNECT)
            requestPermissions(list.toTypedArray(), 1)
        }

        section("Try it", "Tap a tile to preview that island. Changes in the Style tab apply live.").testGrid()
    }

    private fun LinearLayout.hero() {
        val c = LinearLayout(this@MainActivity)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1B2C52.toInt(), 0xFF101018.toInt()))
        g.cornerRadius = dpF(24f)
        g.setStroke(dp(1), 0xFF2B3656.toInt())
        c.background = g
        c.setPadding(dp(18), dp(18), dp(18), dp(18))
        c.addView(iconTile(R.drawable.ic_island, ACC, 48))
        val col = LinearLayout(this@MainActivity)
        col.orientation = LinearLayout.VERTICAL
        val t1 = tv("", 19f, Color.WHITE, true)
        val t2 = tv("", 13f, 0xFFB5BCD0.toInt())
        t2.setPadding(0, dp(3), 0, 0)
        col.addView(t1)
        col.addView(t2)
        c.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(16)
        addView(c, lp)
        updaters.add {
            val live = IslandRuntime.controller != null
            val listener = listenerOn()
            t1.text = if (live && listener) "Everything is set" else if (live) "Island is running" else "Setup needed"
            t2.text = when {
                live && listener -> "Music, calls and messages are ready."
                live -> "Enable Notification access to get music and messages."
                else -> "Enable the Accessibility service (or overlay permission) below."
            }
        }
    }

    private fun LinearLayout.testGrid() {
        val items = listOf(
            Triple("Music", R.drawable.ic_music, "music"),
            Triple("Message", R.drawable.ic_message, "message"),
            Triple("Call", R.drawable.ic_call, "call"),
            Triple("Charging", R.drawable.ic_bolt, "charge"),
            Triple("Timer", R.drawable.ic_timer, "timer"),
            Triple("Volume", R.drawable.ic_volume_up, "volume")
        )
        val tints = listOf(PINK, ACC, GREEN, GREEN, ORANGE, PURPLE)
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this@MainActivity)
            row.setPadding(dp(8), dp(10), dp(8), dp(10))
            for (j in i until minOf(i + 3, items.size)) {
                val (label, icon, what) = items[j]
                val tile = LinearLayout(this@MainActivity)
                tile.orientation = LinearLayout.VERTICAL
                tile.gravity = Gravity.CENTER
                tile.background = rounded(SURF2, dp(16))
                tile.setPadding(0, dp(14), 0, dp(12))
                val ic = FrameLayout(this@MainActivity)
                ic.background = oval(tints[j])
                val iv = ImageView(this@MainActivity)
                iv.setImageResource(icon)
                iv.setColorFilter(Color.WHITE)
                ic.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                tile.addView(ic, LinearLayout.LayoutParams(dp(42), dp(42)))
                val lb = tv(label, 12.5f, Color.WHITE)
                lb.gravity = Gravity.CENTER
                lb.setPadding(0, dp(8), 0, 0)
                tile.addView(lb)
                tile.setOnClickListener {
                    IslandRuntime.demo(this@MainActivity, what)
                    if (IslandRuntime.controller == null) {
                        Toast.makeText(this@MainActivity, "Enable the Accessibility service or overlay permission first", Toast.LENGTH_SHORT).show()
                    }
                }
                val lp = LinearLayout.LayoutParams(0, -2, 1f)
                lp.setMargins(dp(4), 0, dp(4), 0)
                row.addView(tile, lp)
            }
            addRow(row)
            i += 3
        }
    }

    private fun LinearLayout.themes() {
        val r = rowBox()
        r.setPadding(dp(12), dp(12), dp(12), dp(12))
        val sc = HorizontalScrollView(this@MainActivity)
        sc.isHorizontalScrollBarEnabled = false
        val line = LinearLayout(this@MainActivity)
        for ((i, n) in Prefs.presetNames.withIndex()) {
            val cell = LinearLayout(this@MainActivity)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.background = rounded(SURF2, dp(16), LINE)
            cell.setPadding(dp(14), dp(14), dp(14), dp(10))
            val mini = View(this@MainActivity)
            val g = GradientDrawable()
            g.setColor(Prefs.presetBg[i].toInt())
            g.cornerRadius = dpF(20f)
            g.setStroke(dp(2), Prefs.presetAcc[i].toInt())
            mini.background = g
            cell.addView(mini, LinearLayout.LayoutParams(dp(64), dp(22)))
            val lb = tv(n, 12.5f, Color.WHITE)
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, dp(8), 0, 0)
            cell.addView(lb)
            cell.setOnClickListener { Prefs.applyPreset(i); updaters.forEach { u -> u() } }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.marginEnd = dp(10)
            line.addView(cell, lp)
        }
        sc.addView(line)
        r.addView(sc, LinearLayout.LayoutParams(-1, -2))
        addRow(r)
    }

    private fun buildStyle(): ScrollView = page("Style", "Everything updates live on the island") {
        section("Themes").themes()

        section("Position & size", "Use “Top offset” to line the island up with your front camera.").apply {
            slider("yOff", "Top offset", -10, 60, " dp")
            slider("xOff", "Horizontal offset", -100, 100, " dp")
            slider("compactW", "Capsule width", 90, 260, " dp")
            slider("compactH", "Capsule height", 24, 60, " dp")
            slider("expandW", "Card width", 240, 420, " dp")
            slider("radius", "Card corner radius", 10, 48, " dp")
            slider("scale", "Content size", 70, 130, "%")
        }

        section("Colors").apply {
            colorRow("bg", "Background", intArrayOf(
                0xFF000000.toInt(), 0xFF1C1C1E.toInt(), 0xFF0A1A3A.toInt(), 0xFF14002E.toInt(),
                0xFF06260F.toInt(), 0xFF3A0A0A.toInt(), 0xFFF2F2F7.toInt()))
            slider("opacity", "Background opacity", 30, 100, "%")
            colorRow("accent", "Accent", intArrayOf(
                0xFF0A84FF.toInt(), 0xFF30D158.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF453A.toInt(),
                0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFF64D2FF.toInt(), 0xFFFFD60A.toInt(), 0xFFFFFFFF.toInt()))
            colorRow("text", "Text", intArrayOf(
                0xFFFFFFFF.toInt(), 0xFFE5E5EA.toInt(), 0xFFFFD60A.toInt(), 0xFF64D2FF.toInt(), 0xFF111111.toInt()))
            toggle("dynAccent", "Adaptive color", "Take the accent from album art and app colors", R.drawable.ic_tab_style, PURPLE)
            toggle("ambient", "Ambient glow", "Soft colored light inside the island", R.drawable.ic_bolt, ORANGE)
            toggle("glow", "Glowing border", "A thin accent outline", R.drawable.ic_island, TEAL)
        }

        section("Animation").apply {
            slider("animMs", "Duration", 200, 900, " ms")
            slider("bounce", "Damping (lower = bouncier)", 35, 100, "%")
            toggle("haptic", "Haptic feedback", "Tiny vibration on expand and tap", R.drawable.ic_bell, PINK)
        }

        section("Behavior", "Tap: expand / open · Long-press: toggle · Swipe up: dismiss · Swipe sideways on music: next / previous.").apply {
            slider("msgSec", "Message duration", 2, 15, " s")
            slider("evtSec", "System event duration", 1, 8, " s")
            segmented("layoutDir", "Layout direction", listOf("Auto", "Left to right", "Right to left"))
        }
    }

    private fun buildFeatures(): ScrollView = page("Features", "Pick exactly what shows up") {
        section("Media & calls").apply {
            toggle("fMusic", "Music", "Album art, seek bar and playback controls", R.drawable.ic_music, PINK)
            toggle("popupTrack", "Expand on track change", "", R.drawable.ic_next, PURPLE)
            toggle("fCall", "Phone calls", "Answer, decline and call timer", R.drawable.ic_call, GREEN)
            toggle("fMissed", "Missed calls", "", R.drawable.ic_call, RED)
        }

        section("Messages").apply {
            toggle("fMsg", "Messages", "Telegram, WhatsApp, SMS and more", R.drawable.ic_message, ACC)
            toggle("catMsg", "All message notifications", "Any app that marks a notification as a message", R.drawable.ic_bell, TEAL)
            toggle("hideMsgText", "Hide message text", "Show “New message” only", R.drawable.ic_dnd, ORANGE)
            link("Choose messaging apps", "Which apps can show messages", R.drawable.ic_tab_features, ACC) {
                openApps("notifApps", "Messaging apps")
            }
        }

        section("Live activities").apply {
            toggle("fLive", "Live activities", "Downloads, navigation, rides and timers", R.drawable.ic_download, ORANGE)
            toggle("fTimer", "Timers & stopwatch", "", R.drawable.ic_timer, ORANGE)
            toggle("fNav", "Navigation", "", R.drawable.ic_navigation, GREEN)
            toggle("fAlarm", "Alarms & reminders", "", R.drawable.ic_alarm, YELLOW)
            link("Choose live-activity apps", "Ride, delivery and map apps", R.drawable.ic_tab_features, ORANGE) {
                openApps("liveApps", "Live-activity apps")
            }
        }

        section("System events").apply {
            toggle("fCharge", "Charging", "", R.drawable.ic_bolt, GREEN)
            toggle("fUnplug", "Charger unplugged", "", R.drawable.ic_battery, ORANGE)
            toggle("fFull", "Fully charged", "", R.drawable.ic_battery, GREEN)
            toggle("fLow", "Low battery", "", R.drawable.ic_battery, RED)
            slider("lowPct", "Low battery threshold", 5, 40, "%")
            toggle("fSilent", "Ringer mode", "Silent and vibrate", R.drawable.ic_volume_off, ORANGE)
            toggle("fSaver", "Battery saver", "", R.drawable.ic_battery, YELLOW)
            toggle("fAirplane", "Airplane mode", "", R.drawable.ic_airplane, TEAL)
            toggle("fHeadset", "Wired headphones", "", R.drawable.ic_headphones, ACC)
            toggle("fBt", "Bluetooth audio", "Headphones and speakers", R.drawable.ic_headphones, ACC)
            toggle("fDnd", "Do Not Disturb", "", R.drawable.ic_dnd, PURPLE)
            toggle("fFlash", "Flashlight", "Tap the island to turn it off", R.drawable.ic_flashlight, YELLOW)
            toggle("fVolume", "Volume HUD", "Shows a bar when you change the volume", R.drawable.ic_volume_up, PINK)
        }

        section("Visibility").apply {
            toggle("hideLand", "Hide in landscape", "", R.drawable.ic_tab_home, ACC)
            link("Hide in specific apps", "Games and video players", R.drawable.ic_tab_shield, RED) {
                openApps("hiddenApps", "Hide in these apps")
            }
        }
    }

    private fun buildKeepAlive(): ScrollView = page("Keep alive", "Make it survive the cleaner") {
        section(
            "Do these once",
            "On Xiaomi (HyperOS / MIUI) the cleaner can stop background apps. These steps prevent that."
        ).apply {
            guide(
                1, "Lock the app in Recents",
                "Open Recents, long-press the Hyper Island card (or tap the ⋮ above it) and choose Lock. Locked apps are skipped by the cleaner."
            )
            status("Battery: No restrictions", "Stops the system from killing the service", { batteryOk() }, "Set") {
                safeStart(
                    Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                        .putExtra("package_name", packageName).putExtra("package_label", getString(R.string.app_name)),
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }
            status("Autostart", "Lets the service start again by itself", null, "Allow") {
                safeStart(
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    appDetails()
                )
            }
            status("Accessibility service", "Android re-binds it automatically", { a11yOn() }, "Enable") {
                safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        section("How it stays alive").apply {
            info("Accessibility service", "Hosts the island; the system restarts it", R.drawable.ic_tab_shield, GREEN)
            info("Notification listener", "Reconnects itself when it drops", R.drawable.ic_bell, ACC)
            info("Foreground service", "Schedules its own restart when closed", R.drawable.ic_bolt, ORANGE)
            info("Boot + watchdog", "Starts after reboot and checks every 15 min", R.drawable.ic_timer, PURPLE)
        }

        section(
            "Good to know",
            "Force stop disconnects everything. Open the app once and it reconnects."
        ).apply {
            link(
                "Restricted settings",
                "Accessibility greyed out? App info → ⋮ → Allow restricted settings",
                R.drawable.ic_tab_features, ORANGE
            ) { safeStart(appDetails()) }
            link("Open app info", "Permissions, battery, notifications", R.drawable.ic_tab_home, ACC) { safeStart(appDetails()) }
        }

        section("About").apply {
            info("Hyper Island 1.0", "No internet, no ads, no tracking. Everything stays on your phone.", R.drawable.ic_island, ACC)
        }
    }

    // ================================================================ tab bar

    private fun buildTabBar(): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(SURF)
        val line = View(this)
        line.setBackgroundColor(LINE)
        wrap.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        val items = listOf(
            Pair("Home", R.drawable.ic_tab_home), Pair("Style", R.drawable.ic_tab_style),
            Pair("Features", R.drawable.ic_tab_features), Pair("Keep alive", R.drawable.ic_tab_shield)
        )
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(0, dp(8), 0, dp(8))
            val holder = FrameLayout(this)
            val pill = View(this)
            pill.background = rounded(ACC.withAlpha(48), dp(16))
            pill.alpha = 0f
            val icon = ImageView(this)
            icon.setImageResource(item.second)
            icon.setColorFilter(SUB)
            holder.addView(pill, FrameLayout.LayoutParams(dp(60), dp(32), Gravity.CENTER))
            holder.addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            cell.addView(holder, LinearLayout.LayoutParams(dp(60), dp(32)))
            val label = tv(item.first, 11f, SUB, true)
            label.gravity = Gravity.CENTER
            label.setPadding(0, dp(3), 0, 0)
            cell.addView(label)
            cell.setOnClickListener { select(i) }
            bar.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            tabIcons.add(icon)
            tabLabels.add(label)
            tabPills.add(pill)
        }
        wrap.addView(bar)
        return wrap
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
