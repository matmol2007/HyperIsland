package com.hyperisland.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Bilingual helper. Prefs "lang": 0 = Auto, 1 = English, 2 = Persian.
 */
object Lang {

    fun code(): String {
        return when (Prefs.int("lang")) {
            1 -> "en"
            2 -> "fa"
            else -> {
                val sys = Locale.getDefault().language
                if (sys == "fa" || sys == "fa_IR") "fa" else "en"
            }
        }
    }

    fun isFa(): Boolean = code() == "fa"

    fun wrap(base: Context): Context {
        Prefs.init(base)
        val loc = Locale(code())
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(loc)
        conf.setLayoutDirection(loc)
        return base.createConfigurationContext(conf)
    }

    fun s(base: Context, resId: Int): String = try {
        wrap(base).getString(resId)
    } catch (e: Exception) {
        base.getString(resId)
    }

    fun s(base: Context, resId: Int, vararg args: Any): String = try {
        wrap(base).getString(resId, *args)
    } catch (e: Exception) {
        base.getString(resId, *args)
    }
}
