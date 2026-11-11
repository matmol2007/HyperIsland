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

    // TEMP STUB - full file restore in next commit
    private fun buildHome(): ScrollView = ScrollView(this)
    private fun buildStyle(): ScrollView = ScrollView(this)
    private fun buildFeatures(): ScrollView = ScrollView(this)
    private fun buildKeepAlive(): ScrollView = ScrollView(this)
    private fun buildTabBar(): View = View(this)
    private fun select(i: Int) {}
}
