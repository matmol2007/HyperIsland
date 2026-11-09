package com.hyperisland.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class AppsActivity : Activity() {

    private class Item(val label: String, val pkg: String, val ri: ResolveInfo)

    private lateinit var key: String
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
            row.setPadding(dp(14), dp(8), dp(14), dp(8))
            val ic = ImageView(this@AppsActivity)
            try { ic.setImageDrawable(it.ri.loadIcon(packageManager)) } catch (e: Exception) { }
            row.addView(ic, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = TextView(this@AppsActivity)
            t.text = it.label
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginStart = dp(12)
            row.addView(t, lp)
            val cb = CheckBox(this@AppsActivity)
            cb.isChecked = it.pkg in selected
            cb.isClickable = false
            cb.isFocusable = false
            row.addView(cb)
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        key = intent.getStringExtra("key") ?: "notifApps"
        selected.addAll(Prefs.strSet(key))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0B0B0F.toInt())
        val title = TextView(this)
        title.text = intent.getStringExtra("title") ?: "انتخاب برنامه‌ها"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.setPadding(dp(16), dp(16), dp(16), dp(8))
        root.addView(title)
        val search = EditText(this)
        search.hint = "جستجو..."
        search.setHintTextColor(0xFF77777F.toInt())
        search.setTextColor(Color.WHITE)
        search.setSingleLine()
        val g = GradientDrawable()
        g.setColor(0xFF17171C.toInt())
        g.cornerRadius = dp(12).toFloat()
        search.background = g
        search.setPadding(dp(14), dp(10), dp(14), dp(10))
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.setMargins(dp(14), 0, dp(14), dp(8))
        root.addView(search, slp)
        val lv = ListView(this)
        lv.divider = null
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val pkg = shown[pos].pkg
            if (!selected.add(pkg)) selected.remove(pkg)
            Prefs.setStrSet(key, selected)
            adapter.notifyDataSetChanged()
        }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

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

    private fun filter(q: String) {
        shown.clear()
        val s = q.trim().lowercase()
        for (it in all) if (s.isEmpty() || it.label.lowercase().contains(s) || it.pkg.contains(s)) shown.add(it)
        adapter.notifyDataSetChanged()
    }
}
