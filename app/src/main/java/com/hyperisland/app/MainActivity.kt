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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bgC = 0xFF0B0B0F.toInt()
    private val cardC = 0xFF17171C.toInt()
    private val subC = 0xFF9A9AA5.toInt()
    private val accC = 0xFF0A84FF.toInt()

    private lateinit var root: LinearLayout
    private val updaters = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        IslandRuntime.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val sv = ScrollView(this)
        sv.setBackgroundColor(bgC)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(20), dp(16), dp(40))
        sv.addView(root)
        setContentView(sv)

        buildHeader()
        buildSetup()
        buildSurvival()
        buildTest()
        buildThemes()
        buildLayout()
        buildStyle()
        buildAnim()
        buildBehavior()
        buildFeatures()
        buildAbout()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.bool("enabled")) Keepalive.startService(this)
        IslandRuntime.ensureHost(this)
        IslandRuntime.setPreview(true)
        updaters.forEach { it() }
    }

    override fun onPause() {
        IslandRuntime.setPreview(false)
        super.onPause()
    }

    // ---------------------------------------------------------------- ساخت اجزا

    private fun round(c: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = r.toFloat()
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

    private fun card(title: String, sub: String = ""): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(cardC, dp(20))
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(12)
        c.layoutParams = lp
        if (title.isNotEmpty()) {
            c.addView(tv(title, 16f, Color.WHITE, true))
            if (sub.isNotEmpty()) {
                val s = tv(sub, 12.5f, subC)
                s.setPadding(0, dp(3), 0, dp(4))
                c.addView(s)
            }
        }
        root.addView(c)
        return c
    }

    private fun gap(h: Int = 10): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(-1, dp(h))
        return v
    }

    private fun LinearLayout.btn(text: String, onClick: () -> Unit): Button {
        val b = Button(this@MainActivity)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        b.background = round(accC, dp(12))
        b.stateListAnimator = null
        b.minHeight = dp(40)
        b.minimumHeight = dp(40)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        addView(b, lp)
        return b
    }

    private fun LinearLayout.toggle(key: String, title: String, sub: String = "") {
        val r = LinearLayout(this@MainActivity)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(8), 0, dp(8))
        val col = LinearLayout(this@MainActivity)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 14.5f, Color.WHITE))
        if (sub.isNotEmpty()) col.addView(tv(sub, 11.5f, subC))
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this@MainActivity)
        sw.isChecked = Prefs.bool(key)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.setBool(key, v)
            if (key == "enabled" && v) Keepalive.startService(this@MainActivity)
        }
        r.addView(sw)
        addView(r)
    }

    private fun LinearLayout.slider(key: String, title: String, min: Int, max: Int, unit: String = "") {
        val head = LinearLayout(this@MainActivity)
        head.orientation = LinearLayout.HORIZONTAL
        head.setPadding(0, dp(8), 0, 0)
        val t = tv(title, 14.5f, Color.WHITE)
        val v = tv("", 13f, accC, true)
        head.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(v)
        addView(head)
        val sb = SeekBar(this@MainActivity)
        sb.max = max - min
        sb.progress = Prefs.int(key) - min
        sb.progressTintList = ColorStateList.valueOf(accC)
        sb.thumbTintList = ColorStateList.valueOf(accC)
        sb.layoutDirection = View.LAYOUT_DIRECTION_LTR
        v.text = Fa.digits("${Prefs.int(key)}") + " " + unit
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                v.text = Fa.digits("${p + min}") + " " + unit
                if (fromUser) Prefs.setInt(key, p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        addView(sb)
        updaters.add { sb.progress = Prefs.int(key) - min }
    }

    private fun LinearLayout.colorPick(key: String, title: String, swatches: IntArray) {
        addView(tv(title, 14.5f, Color.WHITE).also { it.setPadding(0, dp(10), 0, dp(6)) })
        val rowL = LinearLayout(this@MainActivity)
        rowL.orientation = LinearLayout.HORIZONTAL
        val dots = ArrayList<Pair<View, Int>>()
        fun refresh() {
            val cur = Prefs.int(key)
            for ((d, c) in dots) {
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(c)
                g.setStroke(dp(2.5f), if (c == cur) Color.WHITE else 0x33FFFFFF)
                d.background = g
            }
        }
        for (c in swatches) {
            val d = View(this@MainActivity)
            val lp = LinearLayout.LayoutParams(dp(30), dp(30))
            lp.marginEnd = dp(8)
            d.setOnClickListener { Prefs.setInt(key, c); refresh() }
            rowL.addView(d, lp)
            dots.add(Pair(d, c))
        }
        val hex = tv("#", 14f, Color.WHITE, true)
        hex.gravity = Gravity.CENTER
        hex.background = round(0xFF2C2C33.toInt(), dp(15))
        hex.setOnClickListener {
            val et = EditText(this@MainActivity)
            et.hint = "RRGGBB"
            et.inputType = InputType.TYPE_CLASS_TEXT
            et.layoutDirection = View.LAYOUT_DIRECTION_LTR
            AlertDialog.Builder(this@MainActivity)
                .setTitle("کد رنگ (هگز)")
                .setView(et)
                .setPositiveButton("اعمال") { _, _ ->
                    try {
                        Prefs.setInt(key, Color.parseColor("#" + et.text.toString().trim().removePrefix("#")))
                        refresh()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "کد رنگ نامعتبر است", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("لغو", null)
                .show()
        }
        rowL.addView(hex, LinearLayout.LayoutParams(dp(30), dp(30)))
        val hs = android.widget.HorizontalScrollView(this@MainActivity)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(rowL)
        addView(hs)
        refresh()
        updaters.add { refresh() }
    }

    private fun LinearLayout.choice(key: String, title: String, options: List<String>) {
        addView(tv(title, 14.5f, Color.WHITE).also { it.setPadding(0, dp(10), 0, dp(6)) })
        val rowL = LinearLayout(this@MainActivity)
        rowL.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()
        fun refresh() {
            for ((i, c) in chips.withIndex()) {
                val sel = Prefs.int(key) == i
                c.background = round(if (sel) accC else 0xFF2C2C33.toInt(), dp(10))
                c.setTextColor(if (sel) Color.WHITE else subC)
            }
        }
        for ((i, o) in options.withIndex()) {
            val c = tv(o, 13f, subC)
            c.gravity = Gravity.CENTER
            c.setPadding(dp(6), dp(9), dp(6), dp(9))
            c.setOnClickListener { Prefs.setInt(key, i); refresh() }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginEnd = dp(6)
            rowL.addView(c, lp)
            chips.add(c)
        }
        addView(rowL)
        refresh()
        updaters.add { refresh() }
    }

    private fun LinearLayout.statusRow(title: String, desc: String, ok: () -> Boolean, action: String, onClick: () -> Unit) {
        val r = LinearLayout(this@MainActivity)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(8), 0, dp(8))
        val dot = View(this@MainActivity)
        val dl = LinearLayout.LayoutParams(dp(10), dp(10))
        dl.marginEnd = dp(10)
        r.addView(dot, dl)
        val col = LinearLayout(this@MainActivity)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 14f, Color.WHITE))
        col.addView(tv(desc, 11.5f, subC))
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val b = Button(this@MainActivity)
        b.text = action
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        b.setTextColor(Color.WHITE)
        b.background = round(0xFF2C2C33.toInt(), dp(10))
        b.stateListAnimator = null
        b.minHeight = dp(36)
        b.minimumHeight = dp(36)
        b.setOnClickListener { onClick() }
        r.addView(b)
        addView(r)
        updaters.add {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(if (ok()) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
            dot.background = g
        }
    }

    // ---------------------------------------------------------------- بخش‌ها

    private fun buildHeader() {
        root.addView(tv("جزیره هایپر", 28f, Color.WHITE, true))
        val s = tv("جزیره‌ی پویا برای اندروید؛ موزیک، تماس، پیام و ...", 13f, subC)
        s.setPadding(0, dp(2), 0, dp(14))
        root.addView(s)
        val c = card("")
        c.toggle("enabled", "فعال بودن جزیره", "با خاموش کردن، همه چیز مخفی می‌شود")
        val st = tv("", 12.5f, subC)
        c.addView(st)
        updaters.add {
            val on = IslandRuntime.controller != null
            st.text = if (on) "● جزیره در حال اجراست" else "● جزیره هنوز راه نیفتاده؛ مجوزهای پایین را بده"
            st.setTextColor(if (on) 0xFF30D158.toInt() else 0xFFFF9F0A.toInt())
        }
    }

    private fun safeStart(vararg intents: Intent) {
        for (i in intents) {
            try { startActivity(i); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "این صفحه روی گوشی شما پیدا نشد", Toast.LENGTH_SHORT).show()
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

    private fun buildSetup() {
        val c = card("راه‌اندازی", "این سه مورد اصلی‌ترین‌هاست؛ همه را سبز کن")
        c.statusRow("سرویس دسترسی‌پذیری", "اصلی‌ترین میزبان جزیره و ماندگارترین", { a11yOn() }, "فعال‌سازی") {
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c.statusRow("دسترسی اعلان‌ها", "برای موزیک، پیام‌ها، تایمر و ...", { listenerOn() }, "فعال‌سازی") {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        c.statusRow("نمایش روی برنامه‌ها", "جایگزین؛ اگر دسترسی‌پذیری نخواستی", { Settings.canDrawOverlays(this) }, "مجوز") {
            safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        c.statusRow("تماس و مخاطبین", "نام مخاطب، پاسخ و رد تماس", {
            granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.ANSWER_PHONE_CALLS)
        }, "مجوز") {
            val list = arrayListOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS, Manifest.permission.ANSWER_PHONE_CALLS
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) list.add(Manifest.permission.BLUETOOTH_CONNECT)
            requestPermissions(list.toTypedArray(), 1)
        }
        val tip = tv(
            "اگر روی اندروید ۱۳ به بالا گزینه‌ی دسترسی‌پذیری یا اعلان‌ها خاکستری بود: " +
                    "«اطلاعات برنامه» ← منوی سه‌نقطه ← «اجازه‌ی تنظیمات محدودشده» را بزن و دوباره تلاش کن.",
            11.5f, 0xFFFF9F0A.toInt()
        )
        tip.setPadding(0, dp(6), 0, 0)
        c.addView(tip)
        c.btn("باز کردن اطلاعات برنامه") { safeStart(appDetails()) }
    }

    private fun buildSurvival() {
        val c = card("ماندگاری در پس‌زمینه (شیائومی / HyperOS)", "تا دکمه‌ی پاک‌کن (Clean) جزیره را نکشد")
        c.statusRow("بدون محدودیت باتری", "جلوگیری از کشته شدن سرویس", {
            (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        }, "تنظیم") {
            safeStart(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", packageName).putExtra("package_label", getString(R.string.app_name)),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
        c.statusRow("شروع خودکار (Autostart)", "بعد از ریستارت و بسته شدن دوباره بالا بیاید", { false }, "تنظیم") {
            safeStart(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                appDetails()
            )
        }
        val t = tv(
            "مهم‌ترین کار: در صفحه‌ی برنامه‌های اخیر (Recents)، کارت «جزیره هایپر» را نگه دار " +
                    "(یا روی آیکون ⋮ بالای کارت بزن) و «قفل» را فعال کن. برنامه‌ی قفل‌شده را دکمه‌ی پاک‌کن نمی‌بندد.\n\n" +
                    "اگر هم چیزی بسته شد، چون سرویس دسترسی‌پذیری و دسترسی اعلان‌ها را خود سیستم زنده نگه می‌دارد، " +
                    "معمولاً خودش دوباره وصل می‌شود. فقط «توقف اجباری» (Force stop) همه‌ی این‌ها را قطع می‌کند و باید یک بار برنامه را باز کنی.",
            12f, subC
        )
        t.setPadding(0, dp(8), 0, 0)
        c.addView(t)
    }

    private fun buildTest() {
        val c = card("تست و پیش‌نمایش", "تا اینجا جزیره را روی صفحه می‌بینی؛ تنظیمات زنده اعمال می‌شود")
        val names = listOf("موزیک" to "music", "پیام" to "message", "تماس" to "call", "شارژ" to "charge", "تایمر" to "timer", "صدا" to "volume")
        var i = 0
        while (i < names.size) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (j in i until minOf(i + 3, names.size)) {
                val (label, what) = names[j]
                val b = Button(this)
                b.text = label
                b.isAllCaps = false
                b.setTextColor(Color.WHITE)
                b.background = round(0xFF2C2C33.toInt(), dp(10))
                b.stateListAnimator = null
                b.setOnClickListener {
                    IslandRuntime.demo(this, what)
                    if (IslandRuntime.controller == null) {
                        Toast.makeText(this, "اول دسترسی‌پذیری یا مجوز نمایش را فعال کن", Toast.LENGTH_SHORT).show()
                    }
                }
                val lp = LinearLayout.LayoutParams(0, -2, 1f)
                lp.marginEnd = dp(6)
                r.addView(b, lp)
            }
            c.addView(r)
            i += 3
        }
    }

    private fun buildThemes() {
        val c = card("تم‌های آماده")
        val r = android.widget.HorizontalScrollView(this)
        r.isHorizontalScrollBarEnabled = false
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        for ((i, n) in Prefs.presetNames.withIndex()) {
            val b = Button(this)
            b.text = n
            b.isAllCaps = false
            b.setTextColor(Color.WHITE)
            b.background = round(0xFF2C2C33.toInt(), dp(10))
            b.stateListAnimator = null
            b.setOnClickListener { Prefs.applyPreset(i); updaters.forEach { u -> u() } }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.marginEnd = dp(6)
            l.addView(b, lp)
        }
        r.addView(l)
        c.addView(r)
    }

    private fun buildLayout() {
        val c = card("اندازه و موقعیت", "با اسلایدر «فاصله از بالا» جزیره را روی دوربین جلو تنظیم کن")
        c.slider("yOff", "فاصله از بالا", -10, 60, "dp")
        c.slider("xOff", "جابه‌جایی افقی", -100, 100, "dp")
        c.slider("compactW", "عرض کپسول", 90, 260, "dp")
        c.slider("compactH", "ارتفاع کپسول", 24, 60, "dp")
        c.slider("expandW", "عرض کارت بزرگ", 240, 420, "dp")
        c.slider("radius", "گردی گوشه‌های کارت", 10, 48, "dp")
        c.slider("scale", "اندازه‌ی محتوا", 70, 130, "٪")
    }

    private fun buildStyle() {
        val c = card("ظاهر و رنگ‌ها")
        c.colorPick("bg", "رنگ پس‌زمینه", intArrayOf(
            0xFF000000.toInt(), 0xFF1C1C1E.toInt(), 0xFF0A1A3A.toInt(), 0xFF14002E.toInt(),
            0xFF06260F.toInt(), 0xFF3A0A0A.toInt(), 0xFFF2F2F7.toInt()))
        c.slider("opacity", "شفافیت پس‌زمینه", 30, 100, "٪")
        c.colorPick("accent", "رنگ تأکیدی", intArrayOf(
            0xFF0A84FF.toInt(), 0xFF30D158.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF453A.toInt(),
            0xFFBF5AF2.toInt(), 0xFFFF2D92.toInt(), 0xFF64D2FF.toInt(), 0xFFFFD60A.toInt(), 0xFFFFFFFF.toInt()))
        c.colorPick("text", "رنگ متن", intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFE5E5EA.toInt(), 0xFFFFD60A.toInt(), 0xFF64D2FF.toInt(), 0xFF111111.toInt()))
        c.toggle("glow", "حاشیه‌ی درخشان", "یک خط نازک رنگی دور جزیره")
        c.toggle("dynAccent", "رنگ پویا از کاور آهنگ و برنامه", "رنگ تأکیدی از تصویر گرفته شود")
        c.choice("layoutDir", "جهت چیدمان", listOf("خودکار", "چپ‌چین", "راست‌چین"))
        c.toggle("faDigits", "ارقام فارسی")
    }

    private fun buildAnim() {
        val c = card("انیمیشن")
        c.slider("animMs", "مدت انیمیشن", 200, 900, "میلی‌ثانیه")
        c.slider("bounce", "نرمی فنر (کمتر = فنری‌تر)", 35, 100, "٪")
        c.toggle("haptic", "لرزش لمسی")
    }

    private fun buildBehavior() {
        val c = card("رفتار")
        c.slider("msgSec", "مدت نمایش پیام", 2, 15, "ثانیه")
        c.slider("evtSec", "مدت نمایش رویدادهای سیستمی", 1, 8, "ثانیه")
        c.toggle("popupTrack", "با عوض شدن آهنگ، کارت بزرگ شود")
        c.toggle("hideMsgText", "مخفی کردن متن پیام (حریم خصوصی)")
        c.toggle("hideLand", "مخفی شدن در حالت افقی")
        c.btn("برنامه‌هایی که جزیره در آن‌ها مخفی شود (بازی/ویدیو)") { openApps("hiddenApps", "مخفی در این برنامه‌ها") }
        val t = tv("لمس: باز/بسته · نگه‌داشتن: تغییر حالت · کشیدن به بالا: کنار زدن · کشیدن به چپ/راست روی موزیک: آهنگ بعدی/قبلی", 11.5f, subC)
        t.setPadding(0, dp(8), 0, 0)
        c.addView(t)
    }

    private fun openApps(key: String, title: String) {
        val i = Intent(this, AppsActivity::class.java)
        i.putExtra("key", key)
        i.putExtra("title", title)
        startActivity(i)
    }

    private fun buildFeatures() {
        val c = card("قابلیت‌ها", "هر کدام را جدا روشن/خاموش کن")
        c.toggle("fMusic", "موزیک", "کاور، کنترل پخش، نوار پیشرفت")
        c.toggle("fCall", "تماس", "پاسخ و رد تماس، زمان مکالمه")
        c.toggle("fMissed", "تماس از دست رفته")
        c.toggle("fMsg", "پیام‌ها")
        c.toggle("catMsg", "هر اعلان از نوع پیام (حتی برنامه‌های انتخاب‌نشده)")
        c.btn("انتخاب برنامه‌های پیام‌رسان") { openApps("notifApps", "برنامه‌های پیام") }
        c.toggle("fLive", "فعالیت‌های زنده", "دانلود، مسیریابی، تایمر، اسنپ و ...")
        c.toggle("fTimer", "تایمر و کرونومتر")
        c.toggle("fNav", "مسیریابی")
        c.toggle("fAlarm", "زنگ ساعت و یادآور")
        c.btn("انتخاب برنامه‌های «فعالیت زنده» (اسنپ، تپسی، نقشه...)") { openApps("liveApps", "برنامه‌های فعالیت زنده") }
        c.addView(gap(6))
        c.toggle("fCharge", "اتصال شارژر")
        c.toggle("fUnplug", "جدا شدن شارژر")
        c.toggle("fFull", "شارژ کامل")
        c.toggle("fLow", "باتری ضعیف")
        c.slider("lowPct", "آستانه‌ی باتری ضعیف", 5, 40, "٪")
        c.toggle("fSilent", "تغییر حالت زنگ (بی‌صدا/لرزش)")
        c.toggle("fSaver", "صرفه‌جویی باتری")
        c.toggle("fAirplane", "حالت پرواز")
        c.toggle("fHeadset", "هدفون سیمی")
        c.toggle("fBt", "بلوتوث (هدفون/اسپیکر)")
        c.toggle("fDnd", "مزاحم نشوید")
        c.toggle("fFlash", "چراغ‌قوه", "با لمس جزیره خاموش می‌شود")
        c.toggle("fVolume", "نوار صدا", "هنگام تغییر ولوم")
    }

    private fun buildAbout() {
        val c = card("درباره")
        c.addView(tv("جزیره هایپر نسخه‌ی ۱٫۰ · بدون وابستگی خارجی · بدون اینترنت · داده‌ها فقط روی گوشی می‌مانند", 12f, subC))
    }
}
