package com.hyperisland.app

import android.graphics.drawable.Drawable
import android.media.session.MediaController

enum class Kind { MUSIC, CALL, MESSAGE, LIVE, SYSTEM, VOLUME }

class IslandEvent(val key: String, val kind: Kind) {
    var title: String = ""
    var subtitle: String = ""
    var iconRes: Int = R.drawable.ic_bell
    var avatar: Drawable? = null
    var accent: Int = 0              // 0 = رنگ تنظیمات
    var priority: Int = 10
    var sticky: Boolean = false      // بعد از پایان نمایش بزرگ، به شکل کپسول کوچک می‌ماند
    var popup: Boolean = true        // هنگام ارسال، بزرگ نمایش داده شود
    var ttlMs: Long = 3000           // مدت نمایش بزرگ؛ 0 = تا زمان حذف
    var reshow: Boolean = false      // اگر کاربر قبلاً کنار زده، دوباره نشان بده
    var tap: (() -> Unit)? = null

    var progress: Float = -1f        // 0..1 ؛ منفی = بدون نوار
    var chronoBase: Long = 0L        // پایه‌ی زمان (elapsedRealtime) ؛ 0 = ندارد
    var chronoDown: Boolean = false
    var rightText: String = ""

    // موزیک
    var playing: Boolean = false
    var media: MediaController? = null
    var durMs: Long = -1L

    // تماس
    var ringing: Boolean = false
    var onAnswer: (() -> Unit)? = null
    var onDecline: (() -> Unit)? = null
}
