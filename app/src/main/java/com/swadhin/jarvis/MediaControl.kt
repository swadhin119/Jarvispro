package com.swadhin.jarvis

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.swadhin.jarvis.nlu.MediaAction
import com.swadhin.jarvis.nlu.VolOp

/**
 * Playback control through Android's official MediaSession / MediaController APIs.
 * Works with YouTube, YouTube Music, Spotify and any app that publishes a media session.
 * Needs Notification Access for Jarvis (that is how Android grants access to other apps' sessions).
 * Without it, falls back to plain media key events (play/pause/next/previous only).
 */
class MediaControl(private val ctx: Context) {

    enum class Status { OK, NO_ACCESS, NO_SESSION, UNSUPPORTED }

    data class Result(val status: Status, val detail: String = "", val app: String = "")

    companion object {
        val MUSIC_PACKAGES = listOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.spotify.music"
        )

        fun appLabel(pkg: String): String = when (pkg) {
            "com.google.android.youtube" -> "YouTube"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "com.spotify.music" -> "Spotify"
            else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private val am get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** null = Notification Access is off */
    fun sessions(): List<MediaController>? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm.getActiveSessions(ComponentName(ctx, NotifListener::class.java))
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }

    fun hasAccess(): Boolean = sessions() != null

    /** The session the user most likely means: the one playing, else a music app, else the most recent. */
    fun current(): MediaController? {
        val list = sessions() ?: return null
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName in MUSIC_PACKAGES }
            ?: list.firstOrNull()
    }

    fun title(c: MediaController?): String {
        val m = c?.metadata ?: return ""
        return (m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "").trim()
    }

    fun artist(c: MediaController?): String {
        val m = c?.metadata ?: return ""
        return (m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "").trim()
    }

    fun isPlaying(c: MediaController?) = c?.playbackState?.state == PlaybackState.STATE_PLAYING

    /**
     * Waits until [pkg] is actually playing (used to verify that a song really started).
     * Returns the controller, or null on timeout / no access.
     */
    fun waitForPlaying(pkg: String, timeoutMs: Long, notTitle: String? = null): MediaController? {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            val list = sessions() ?: return null
            val c = list.firstOrNull { it.packageName == pkg && isPlaying(it) }
            if (c != null && (notTitle == null || title(c) != notTitle)) return c
            Thread.sleep(400)
        }
        return null
    }

    private fun position(st: PlaybackState): Long {
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return pos.coerceAtLeast(0)
    }

    fun perform(action: MediaAction, seconds: Int = 0): Result {
        if (sessions() == null) return keyFallback(action)
        val c = current() ?: return Result(Status.NO_SESSION)
        val app = MediaControl.appLabel(c.packageName)
        val st = c.playbackState
        val acts = st?.actions ?: 0L
        // Some apps report 0 actions; then just try.
        fun has(a: Long) = acts == 0L || (acts and a) != 0L
        val tc = c.transportControls
        return when (action) {
            MediaAction.PAUSE -> {
                if (!has(PlaybackState.ACTION_PAUSE) && !has(PlaybackState.ACTION_PLAY_PAUSE)) Result(Status.UNSUPPORTED, "pause", app)
                else { tc.pause(); Result(Status.OK, title(c), app) }
            }
            MediaAction.PLAY, MediaAction.RESUME -> {
                if (!has(PlaybackState.ACTION_PLAY) && !has(PlaybackState.ACTION_PLAY_PAUSE)) Result(Status.UNSUPPORTED, "play", app)
                else { tc.play(); Result(Status.OK, title(c), app) }
            }
            MediaAction.STOP -> {
                if ((acts and PlaybackState.ACTION_STOP) != 0L) tc.stop() else tc.pause()
                Result(Status.OK, title(c), app)
            }
            MediaAction.NEXT -> {
                if (!has(PlaybackState.ACTION_SKIP_TO_NEXT)) return Result(Status.UNSUPPORTED, "next", app)
                val before = title(c)
                tc.skipToNext()
                Result(Status.OK, waitForTitleChange(c.packageName, before), app)
            }
            MediaAction.PREVIOUS -> {
                if (!has(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) return Result(Status.UNSUPPORTED, "previous", app)
                tc.skipToPrevious()
                Result(Status.OK, "", app)
            }
            MediaAction.SEEK -> {
                if (st == null || !has(PlaybackState.ACTION_SEEK_TO)) return Result(Status.UNSUPPORTED, "seek", app)
                val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                var target = position(st) + seconds * 1000L
                if (dur > 0) target = target.coerceAtMost(dur - 1000)
                tc.seekTo(target.coerceAtLeast(0))
                Result(Status.OK, title(c), app)
            }
            MediaAction.REPLAY -> {
                when {
                    st != null && has(PlaybackState.ACTION_SEEK_TO) -> { tc.seekTo(0); tc.play() }
                    has(PlaybackState.ACTION_SKIP_TO_PREVIOUS) -> tc.skipToPrevious()
                    else -> return Result(Status.UNSUPPORTED, "replay", app)
                }
                Result(Status.OK, title(c), app)
            }
        }
    }

    private fun waitForTitleChange(pkg: String, before: String): String {
        repeat(8) {
            Thread.sleep(300)
            val c = sessions()?.firstOrNull { it.packageName == pkg } ?: return ""
            val t = title(c)
            if (t.isNotEmpty() && t != before) return t
        }
        return ""
    }

    /** No Notification Access: media keys reach whichever app holds the media button. */
    private fun keyFallback(action: MediaAction): Result {
        val key = when (action) {
            MediaAction.PLAY, MediaAction.RESUME -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.STOP -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.SEEK, MediaAction.REPLAY -> return Result(Status.NO_ACCESS)
        }
        if (!am.isMusicActive && action != MediaAction.PLAY && action != MediaAction.RESUME) return Result(Status.NO_SESSION)
        sendKey(key)
        return Result(Status.OK, "", "")
    }

    fun sendKey(key: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    /** Returns the new music volume in percent. */
    fun volume(op: VolOp, percent: Int = -1): Int {
        val s = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        val max = am.getStreamMaxVolume(s)
        when (op) {
            VolOp.UP -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, flags) }
            VolOp.DOWN -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, flags) }
            VolOp.SET -> am.setStreamVolume(s, (percent.coerceIn(0, 100) * max + 50) / 100, flags)
            VolOp.MAX -> am.setStreamVolume(s, max, flags)
            VolOp.MUTE -> am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, flags)
            VolOp.UNMUTE -> am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, flags)
        }
        return if (max == 0) 0 else am.getStreamVolume(s) * 100 / max
    }

    fun musicActive(): Boolean = am.isMusicActive
}
