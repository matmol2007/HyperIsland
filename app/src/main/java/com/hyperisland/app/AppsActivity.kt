package com.hyperisland.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView

class AppsActivity : Activity() {

    private class Item(val label: String, val pkg: String, val ri: ResolveInfo)

    private val BG = 0xFF08080B.toInt()
    private val SURF = 0xFF131318.toInt()
    private val LINE = 0xFF26262F.toInt()
    private val SUB = 0xFF8E8E99.toInt()
    private val GREEN = 0xFF30D158.toInt()

    private lateinit var key: String
    private lateinit var countTv: TextView
    private val all = ArrayList<Item>()
    private val shown = ArrayList<Item>()
    private val selected = HashSet<String>()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val it = shown[p]
            val row = LinearLayout(this@AppsActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(18), dp(9), dp(18), dp(9))
            val ic = ImageView(this@AppsActivity)
            try { ic.setImageDrawable(it.ri.loadIcon(packageManager)) } catch (e: Exception) { }
            ic.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dpF(11f)) }
            }
            ic.clipToOutline = true
            row.addView(ic, LinearLayout.LayoutParams(dp(44), dp(44)))
            val col = LinearLayout(this@AppsActivity)
            col.orientation = LinearLayout.VERTICAL
            val t = TextView(this@AppsActivity)
            t.text = it.label
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            t.setSingleLine()
            t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            val s = TextView(this@AppsActivity)
            s.text = it.pkg
            s.setTextColor(SUB)
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            s.setSingleLine()
            s.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            col.addView(t)
            col.addView(s)
            val clp = LinearLayout.LayoutParams(0, -2, 1f)
            clp.marginStart = dp(14)
            clp.marginEnd = dp(10)
            row.addView(col, clp)
            val sw = Switch(this@AppsActivity)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            sw.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFFB4B4BE.toInt()))
            sw.trackTintList = ColorStateList(states, intArrayOf(GREEN, 0xFF3A3A44.toInt()))
            sw.isChecked = it.pkg in selected
            sw.isClickable = false
            sw.isFocusable = false
            row.addView(sw)
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        key = intent.getStringExtra("key") ?: "notifApps"
        selected.addAll(Prefs.strSet(key))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(14), dp(18), dp(6))
        val back = TextView(this)
        back.text = "‹"
        back.setTextColor(0xFF0A84FF.toInt())
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        back.gravity = Gravity.CENTER
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = intent.getStringExtra("title") ?: "Choose apps"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.typeface = Typeface.DEFAULT_BOLD
        countTv = TextView(this)
        countTv.setTextColor(SUB)
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        tcol.addView(title)
        tcol.addView(countTv)
        top.addView(tcol, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)

        val search = EditText(this)
        search.hint = "Search apps"
        search.setHintTextColor(0xFF6E6E78.toInt())
        search.setTextColor(Color.WHITE)
        search.setSingleLine()
        val g = GradientDrawable()
        g.setColor(SURF)
        g.cornerRadius = dpF(14f)
        g.setStroke(dp(1), LINE)
        search.background = g
        search.setPadding(dp(16), dp(11), dp(16), dp(11))
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.setMargins(dp(18), dp(6), dp(18), dp(8))
        root.addView(search, slp)

        val lv = ListView(this)
        lv.divider = null
        lv.overScrollMode = View.OVER_SCROLL_NEVER
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val pkg = shown[pos].pkg
            if (!selected.add(pkg)) selected.remove(pkg)
            Prefs.setStrSet(key, selected)
            updateCount()
            adapter.notifyDataSetChanged()
        }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateCount()

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        Thread {
            val pm = packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(i, 0)
            val items = list.filter { it.activityInfo.packageName != packageName }
                .map { Item(it.loadLabel(pm).toString(), it.activityInfo.packageName, it) }
                .distinctBy { it.pkg }
                .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))
            runOnUiThread {
                all.clear(); all.addAll(items)
                filter("")
            }
        }.start()
    }

    private fun updateCount() {
        countTv.text = "${selected.size} selected"
    }

    private fun filter(q: String) {
        shown.clear()
        val s = q.trim().lowercase()
        for (it in all) if (s.isEmpty() || it.label.lowercase().contains(s) || it.pkg.contains(s)) shown.add(it)
        adapter.notifyDataSetChanged()
    }
}
