package com.hyperisland.app

import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.media.session.PlaybackState

enum class Kind { MUSIC, CALL, MESSAGE, LIVE, SYSTEM, VOLUME }

class IslandEvent(val key: String, val kind: Kind) {
    var title: String = ""
    var subtitle: String = ""
    var iconRes: Int = R.drawable.ic_bell
    var avatar: Drawable? = null
    var accent: Int = 0              // 0 = use settings accent
    var priority: Int = 10
    var sticky: Boolean = false      // stays as a compact pill after the expanded card closes
    var popup: Boolean = true        // expand when posted
    var ttlMs: Long = 3000           // expanded duration; 0 = until removed
    var reshow: Boolean = false      // re-show even if the user swiped it away
    var tap: (() -> Unit)? = null

    var progress: Float = -1f        // 0..1 ; negative = no bar
    var chronoBase: Long = 0L        // elapsedRealtime base ; 0 = none
    var chronoDown: Boolean = false
    var rightText: String = ""

    // music
    var playing: Boolean = false
    var media: MediaController? = null
    var durMs: Long = -1L
    var posBase: Long = 0L           // used only when media == null (demo)
    var posAt: Long = 0L

    // call
    var ringing: Boolean = false
    var onAnswer: (() -> Unit)? = null
    var onDecline: (() -> Unit)? = null

    fun positionMs(now: Long): Long {
        val m = media
        if (m != null) {
            val st = m.playbackState ?: return 0L
            var p = st.position
            if (st.state == PlaybackState.STATE_PLAYING) p += ((now - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            return if (durMs > 0) p.coerceIn(0L, durMs) else maxOf(0L, p)
        }
        val p = if (playing) posBase + (now - posAt) else posBase
        return if (durMs > 0) p.coerceIn(0L, durMs) else maxOf(0L, p)
    }
}
