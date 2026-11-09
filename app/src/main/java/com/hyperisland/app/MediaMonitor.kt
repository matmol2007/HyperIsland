package com.hyperisland.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/** موزیک: وضعیت پخش، آهنگ، کاور و کنترل‌ها از MediaSession. */
class MediaMonitor(private val ctx: Context, private val comp: ComponentName) {
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val map = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val ev = IslandEvent("music", Kind.MUSIC)
    private var lastTitle = ""
    private var wasPlaying = false
    private var lastPkg = ""
    private val removeRun = Runnable { IslandRuntime.remove("music") }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        sync(list ?: emptyList())
    }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            sync(msm.getActiveSessions(comp))
        } catch (e: SecurityException) { }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { }
        for ((c, cb) in map.values) try { c.unregisterCallback(cb) } catch (e: Exception) { }
        map.clear()
        mainHandler.removeCallbacks(removeRun)
        IslandRuntime.remove("music")
    }

    private fun sync(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val en = it.next()
            if (en.key !in tokens) {
                try { en.value.first.unregisterCallback(en.value.second) } catch (e: Exception) { }
                it.remove()
            }
        }
        for (c in list) {
            val t = c.sessionToken
            if (map.containsKey(t)) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state?.state == PlaybackState.STATE_PLAYING) lastPkg = c.packageName ?: ""
                    update()
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) { update() }
                override fun onSessionDestroyed() { mainHandler.post { sync(try { msm.getActiveSessions(comp) } catch (e: Exception) { emptyList() }) } }
            }
            try { c.registerCallback(cb); map[t] = Pair(c, cb) } catch (e: Exception) { }
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) lastPkg = c.packageName ?: ""
        }
        update()
    }

    private fun pick(): MediaController? {
        val all = map.values.map { it.first }
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: all.firstOrNull { it.packageName == lastPkg && it.metadata != null }
    }

    private fun update() {
        val c = pick()
        if (c == null || !Prefs.bool("fMusic")) {
            mainHandler.removeCallbacks(removeRun)
            IslandRuntime.remove("music")
            return
        }
        val st = c.playbackState?.state
        val playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank() && !playing) {
            IslandRuntime.remove("music")
            return
        }
        val pkg = c.packageName ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val appIcon: Drawable? = try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

        val changed = (title ?: "") + "|" + (artist ?: "") != lastTitle
        lastTitle = (title ?: "") + "|" + (artist ?: "")
        ev.title = if (title.isNullOrBlank()) "در حال پخش" else title
        ev.subtitle = artist ?: ""
        ev.iconRes = R.drawable.ic_music
        ev.avatar = if (art != null) BitmapDrawable(ctx.resources, art) else appIcon
        ev.accent = if (art != null && Prefs.bool("dynAccent")) dominant(art) else 0
        ev.durMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        ev.media = c
        ev.playing = playing
        ev.sticky = true
        ev.priority = 40
        ev.ttlMs = 4000
        ev.popup = Prefs.bool("popupTrack") && playing && (changed || !wasPlaying)
        ev.reshow = changed || (playing && !wasPlaying)
        ev.tap = {
            val pi = c.sessionActivity
            if (!Launch.send(ctx, pi)) Launch.pkg(ctx, pkg)
        }
        wasPlaying = playing
        if (playing) mainHandler.removeCallbacks(removeRun)
        else { mainHandler.removeCallbacks(removeRun); mainHandler.postDelayed(removeRun, 12000) }
        IslandRuntime.post(ev)
    }

    private fun dominant(b: Bitmap): Int {
        return try {
            val s = Bitmap.createScaledBitmap(b, 1, 1, true)
            val hsv = FloatArray(3)
            Color.colorToHSV(s.getPixel(0, 0), hsv)
            hsv[1] = maxOf(hsv[1], 0.5f)
            hsv[2] = maxOf(hsv[2], 0.85f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) { 0 }
    }
}
