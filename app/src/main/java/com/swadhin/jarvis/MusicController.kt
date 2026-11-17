package com.swadhin.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.swadhin.jarvis.nlu.MusicApp
import com.swadhin.jarvis.nlu.MusicKind
import com.swadhin.jarvis.nlu.YouTubeResults
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds and plays music using only supported mechanisms:
 *  - YouTube: official YouTube Data API v3 (if the user added a key) + youtube.com/watch app link, or
 *    the app's own search-results deep link + tapping the first real result through Accessibility (like a person would).
 *  - YouTube Music / Spotify: Android's standard MEDIA_PLAY_FROM_SEARCH intent (the same one Google Assistant uses).
 * Playback is then VERIFIED through the media session. We never claim "playing" unless Android says so.
 */
class MusicController(
    private val ctx: Context,
    private val media: MediaControl,
    private val launch: (Intent) -> Unit
) {

    data class Video(val id: String, val title: String, val channel: String)

    sealed class Outcome {
        /** Verified: the media session of [app] reports PLAYING. */
        data class Playing(val title: String, val app: MusicApp) : Outcome()
        /** The video / song was opened (tapped or deep-linked) but we could not verify (no Notification Access). */
        data class Opened(val title: String, val app: MusicApp) : Outcome()
        /** Only the search results are on screen. */
        data class ResultsOnly(val query: String, val app: MusicApp, val accessibilityOff: Boolean) : Outcome()
        /** Several different songs match – ask the user. */
        data class Choices(val options: List<Video>, val viaScreen: Boolean) : Outcome()
        data class NotInstalled(val app: MusicApp) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    private val acc: JarvisAccessibility? get() = JarvisAccessibility.instance

    companion object {
        const val YT = "com.google.android.youtube"
        const val YTM = "com.google.android.apps.youtube.music"
        const val SPOTIFY = "com.spotify.music"
    }

    fun pkg(app: MusicApp) = when (app) {
        MusicApp.YOUTUBE -> YT
        MusicApp.YT_MUSIC -> YTM
        MusicApp.SPOTIFY -> SPOTIFY
    }

    fun installed(app: MusicApp): Boolean = installed(pkg(app))

    fun installed(p: String): Boolean = try {
        ctx.packageManager.getLaunchIntentForPackage(p) != null
    } catch (e: Exception) {
        false
    }

    /** Main entry. [allowChoice] = may answer with [Outcome.Choices] instead of playing. */
    fun play(query: String, app: MusicApp, kind: MusicKind, artistGiven: Boolean, allowChoice: Boolean): Outcome {
        if (!installed(app)) return Outcome.NotInstalled(app)
        return when (app) {
            MusicApp.YOUTUBE -> playYouTube(query, kind, artistGiven, allowChoice)
            MusicApp.YT_MUSIC, MusicApp.SPOTIFY -> playFromSearch(query, app)
        }
    }

    // ---------------- YouTube ----------------

    private fun playYouTube(query: String, kind: MusicKind, artistGiven: Boolean, allowChoice: Boolean): Outcome {
        val key = Prefs.youtubeKey(ctx)
        if (key.isNotBlank()) {
            val found = try { apiSearch(query, key, kind) } catch (e: Exception) { null }
            if (!found.isNullOrEmpty()) {
                if (allowChoice && YouTubeResults.needsChoice(query, found.map { it.title }, artistGiven)) {
                    return Outcome.Choices(found.take(3), viaScreen = false)
                }
                return playVideo(found[0])
            }
            // API failed (quota / network) -> fall through to the on-screen method
        }
        return playViaScreen(query, artistGiven, allowChoice)
    }

    /** Official YouTube Data API v3 search. */
    private fun apiSearch(query: String, key: String, kind: MusicKind): List<Video> {
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=5&regionCode=IN" +
            (if (kind != MusicKind.VIDEO) "&videoCategoryId=10" else "") +
            "&q=" + Uri.encode(query) + "&key=" + Uri.encode(key)
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 6000
            con.readTimeout = 8000
            if (con.responseCode !in 200..299) throw RuntimeException("YouTube API ${con.responseCode}")
            val json = JSONObject(con.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val items = json.optJSONArray("items") ?: return emptyList()
            val out = ArrayList<Video>()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val id = it.optJSONObject("id")?.optString("videoId").orEmpty()
                val sn = it.optJSONObject("snippet") ?: continue
                if (id.isEmpty() || sn.optString("liveBroadcastContent") == "upcoming") continue
                out.add(Video(id, unescape(sn.optString("title")), unescape(sn.optString("channelTitle"))))
            }
            if (out.isEmpty() && kind != MusicKind.VIDEO) return apiSearch(query, key, MusicKind.VIDEO)
            return out
        } finally {
            con.disconnect()
        }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    /** Opens a specific video in the YouTube app (autoplays) and verifies. */
    fun playVideo(v: Video): Outcome {
        val before = media.title(media.sessions()?.firstOrNull { it.packageName == YT })
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + v.id)).setPackage(YT))
        return verify(MusicApp.YOUTUBE, v.title, before)
    }

    /** Without an API key: YouTube's own search page, then tap the first real video like a person would. */
    private fun playViaScreen(query: String, artistGiven: Boolean, allowChoice: Boolean): Outcome {
        // If YouTube is already showing an older results page, don't tap a stale video: wait for the list to change.
        val stale = acc?.youtubeResults()?.map { it.item.title }.orEmpty()
        openYouTubeSearch(query)
        val a = acc ?: return Outcome.ResultsOnly(query, MusicApp.YOUTUBE, accessibilityOff = true)
        var results: List<JarvisAccessibility.YtResult> = emptyList()
        for (i in 0 until 16) {          // up to ~9 s for the app + results to load
            Thread.sleep(550)
            val now = a.youtubeResults() ?: continue
            if (now.isEmpty()) continue
            results = now
            if (stale.isEmpty() || now.map { it.item.title } != stale || i >= 10) break
        }
        if (results.isEmpty()) return Outcome.ResultsOnly(query, MusicApp.YOUTUBE, accessibilityOff = false)

        if (allowChoice && YouTubeResults.needsChoice(query, results.map { it.item.title }, artistGiven)) {
            return Outcome.Choices(results.take(3).map { Video("", it.item.title, it.item.channel) }, viaScreen = true)
        }
        return tapResult(results[0])
    }

    /** After the user picked option N from on-screen results. */
    fun playScreenChoice(title: String): Outcome {
        val a = acc ?: return Outcome.Failed("accessibility off")
        val r = a.youtubeResults()?.firstOrNull { it.item.title == title }
            ?: a.youtubeResults()?.firstOrNull { it.item.title.startsWith(title.take(15)) }
            ?: return Outcome.Failed("results not on screen any more")
        return tapResult(r)
    }

    private fun tapResult(r: JarvisAccessibility.YtResult): Outcome {
        val a = acc ?: return Outcome.Failed("accessibility off")
        val before = media.title(media.sessions()?.firstOrNull { it.packageName == YT })
        if (!a.tapNode(r.node)) return Outcome.ResultsOnly(r.item.title, MusicApp.YOUTUBE, accessibilityOff = false)
        return verify(MusicApp.YOUTUBE, r.item.title, before)
    }

    fun openYouTubeSearch(query: String) {
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(YT))
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // ---------------- YouTube Music / Spotify ----------------

    /** Android's standard "play from search" intent. Empty query = "play something". */
    private fun playFromSearch(query: String, app: MusicApp): Outcome {
        val p = pkg(app)
        val before = media.title(media.sessions()?.firstOrNull { it.packageName == p })
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(p)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        val ok = ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty()
        if (ok) {
            try {
                launch(i)
                return verify(app, query, before, timeoutMs = 12000)
            } catch (e: Exception) { /* fall back to search page below */ }
        }
        val url = if (app == MusicApp.SPOTIFY) "spotify:search:" + Uri.encode(query)
        else "https://music.youtube.com/search?q=" + Uri.encode(query)
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(p))
        return Outcome.ResultsOnly(query, app, accessibilityOff = acc == null)
    }

    // ---------------- verification ----------------

    private fun verify(app: MusicApp, fallbackTitle: String, before: String, timeoutMs: Long = 10000): Outcome {
        if (!media.hasAccess()) return Outcome.Opened(fallbackTitle, app)
        // Wait for a *new* track if something was already playing in that app.
        val c = media.waitForPlaying(pkg(app), timeoutMs, before.ifBlank { null })
        if (c != null) return Outcome.Playing(media.title(c).ifBlank { fallbackTitle }, app)
        // Same title as before (e.g. the same song requested again) but it is playing.
        val now = media.sessions()?.firstOrNull { it.packageName == pkg(app) && media.isPlaying(it) }
        if (now != null) return Outcome.Playing(media.title(now).ifBlank { fallbackTitle }, app)
        return Outcome.Opened(fallbackTitle, app)
    }
}
