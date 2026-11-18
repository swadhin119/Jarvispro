package com.swadhin.jarvis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.swadhin.jarvis.nlu.Command
import com.swadhin.jarvis.nlu.InfoType
import com.swadhin.jarvis.nlu.IntentParser
import com.swadhin.jarvis.nlu.Lang
import com.swadhin.jarvis.nlu.LanguageDetector
import com.swadhin.jarvis.nlu.MediaAction
import com.swadhin.jarvis.nlu.MusicApp
import com.swadhin.jarvis.nlu.MusicKind
import com.swadhin.jarvis.nlu.MusicMemory
import com.swadhin.jarvis.nlu.Replies
import com.swadhin.jarvis.nlu.ScreenAction
import com.swadhin.jarvis.nlu.Talk
import com.swadhin.jarvis.nlu.TimeParser
import com.swadhin.jarvis.nlu.VolOp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The brain: local intent recognition first (fast, offline, no API cost), Gemini only for complex requests.
 * Keeps short-term conversation state ("kaunsa gana?", "kisko message?", "pehla ya doosra?") and music memory.
 * All methods run on the service's single worker thread.
 */
class Assistant(
    private val ctx: Context,
    private val actions: PhoneActions,
    private val gemini: GeminiClient
) {

    /**
     * [keepAwake] = false after music starts, so Jarvis needs "Jarvis" again and doesn't treat song lyrics as commands.
     * [expectAnswer] = Jarvis asked a question; listen for the answer without the wake word.
     */
    data class Reply(val text: String, val lang: Lang, val keepAwake: Boolean = true, val expectAnswer: Boolean = false)

    private sealed class Pending {
        val until = System.currentTimeMillis() + 40_000
        class AwaitSong(val app: MusicApp?) : Pending()
        class AwaitChoice(val options: List<MusicController.Video>, val viaScreen: Boolean, val query: String, val artist: String?) : Pending()
        class AwaitWhatsAppTo(val message: String?) : Pending()
        class AwaitWhatsAppMsg(val to: String) : Pending()
        object AwaitAlarm : Pending()
        object AwaitTimer : Pending()
        class AwaitSearch(val inChrome: Boolean) : Pending()
        object AwaitFavourite : Pending()
    }

    private val memory = MusicMemory({ Prefs.musicMemory(ctx) }, { Prefs.setMusicMemory(ctx, it) })
    private val media get() = actions.media
    private val music get() = actions.music
    private val say get() = Replies(Prefs.address(ctx))
    private var pending: Pending? = null

    /** Language of the conversation; follows the user whenever they switch. */
    var lang: Lang = if (Prefs.listenLang(ctx) == "en-IN") Lang.ENGLISH else Lang.HINGLISH
        private set

    /** [progress] speaks a short "working on it" line while slow actions (YouTube) run. */
    fun handle(raw: String, progress: (String, Lang) -> Unit): Reply {
        LanguageDetector.detect(raw)?.let { lang = it }
        val l = lang
        val p = pending?.takeIf { System.currentTimeMillis() < it.until }
        pending = null
        var usedAi = false
        val reply = try {
            (if (p != null) answerPending(p, raw, l, progress) else null)
                ?: IntentParser.parse(raw).let { cmd ->
                    JarvisService.log("🧠 $cmd [${l.name.lowercase()}]")
                    if (cmd is Command.Unknown) usedAi = true
                    route(cmd, raw, l, progress)
                }
        } catch (e: Exception) {
            JarvisService.log("Error: ${e.message}")
            Reply(say.failed(l, e.message ?: e.javaClass.simpleName), l)
        }
        if (!usedAi && reply.text.isNotBlank()) gemini.remember(raw, reply.text)
        return reply
    }

    fun greeting(): Reply = Reply(say.online(lang), lang)

    // ------------------------------------------------------------------ follow-up answers

    private fun answerPending(p: Pending, raw: String, l: Lang, progress: (String, Lang) -> Unit): Reply? {
        val cmd = IntentParser.parse(raw)
        if (cmd is Command.Cancel || (IntentParser.isNo(raw) && p !is Pending.AwaitWhatsAppMsg)) return Reply(say.okCancel(l), l)
        val clean = IntentParser.phrase(IntentParser.stripWake(IntentParser.tokenize(raw)), emptySet())
        return when (p) {
            is Pending.AwaitSong -> {
                // A full new command ("volume badhao") wins over treating the words as a song name.
                if (cmd !is Command.Unknown && cmd !is Command.AskSong) return null
                val mq = IntentParser.musicQueryFromAnswer(raw)
                if (mq.query.isBlank()) return Reply(say.askSong(l), l, expectAnswer = true).also { pending = Pending.AwaitSong(p.app) }
                playMusic(mq.query, p.app, mq.kind, mq.artist, mq.title, l, progress)
            }
            is Pending.AwaitChoice -> {
                val n = IntentParser.parseChoice(raw)
                if (n == null) {
                    if (cmd !is Command.Unknown) return null
                    pending = p
                    return Reply(say.badChoice(l, p.options.size), l, expectAnswer = true)
                }
                if (n !in 1..p.options.size) { pending = p; return Reply(say.badChoice(l, p.options.size), l, expectAnswer = true) }
                val v = p.options[n - 1]
                val outcome = if (p.viaScreen) music.playScreenChoice(v.title) else music.playVideo(v)
                musicReply(outcome, p.query, l)
            }
            is Pending.AwaitWhatsAppTo -> {
                if (clean.isBlank()) return null
                if (p.message == null) {
                    pending = Pending.AwaitWhatsAppMsg(clean)
                    Reply(say.askWhatsAppMsg(l, clean), l, expectAnswer = true)
                } else sendWhatsApp(clean, p.message, l)
            }
            is Pending.AwaitWhatsAppMsg -> {
                val msg = IntentParser.stripWake(IntentParser.tokenize(raw)).joinToString(" ") { it.orig }
                if (msg.isBlank()) return null
                sendWhatsApp(p.to, msg, l)
            }
            Pending.AwaitAlarm -> {
                val t = TimeParser.clock(IntentParser.tokenize(raw)) ?: return null
                alarm(t.first, t.second, l)
            }
            Pending.AwaitTimer -> {
                val s = TimeParser.duration(IntentParser.tokenize(raw)) ?: return null
                timer(s, l)
            }
            is Pending.AwaitSearch -> {
                if (cmd !is Command.Unknown && cmd !is Command.WebSearch) return null
                val q = (cmd as? Command.WebSearch)?.query?.ifBlank { null } ?: clean
                if (q.isBlank()) return null
                actions.webSearch(q, p.inChrome)
                Reply(say.webSearch(l, q), l)
            }
            Pending.AwaitFavourite -> {
                if (cmd !is Command.Unknown && cmd !is Command.PlayMusic) return null
                val mq = (cmd as? Command.PlayMusic)?.let { IntentParser.MusicQuery(it.query, it.kind, it.artist, it.title) }
                    ?: IntentParser.musicQueryFromAnswer(raw)
                if (mq.query.isBlank()) return null
                memory.setFavourite(MusicMemory.Entry(mq.query, mq.artist.orEmpty(), mq.title.orEmpty(), defaultApp().name, System.currentTimeMillis()))
                progress(say.favouriteSaved(l, mq.query), l)
                playMusic(mq.query, null, mq.kind, mq.artist, mq.title, l, progress = { _, _ -> })
            }
        }
    }

    // ------------------------------------------------------------------ routing

    private fun route(cmd: Command, raw: String, l: Lang, progress: (String, Lang) -> Unit): Reply = when (cmd) {
        is Command.SmallTalk -> Reply(
            when (cmd.kind) {
                Talk.WAKE_ONLY -> say.wake(l)
                Talk.GREETING -> say.greeting(l)
                Talk.WHAT_DOING -> say.whatDoing(l)
                Talk.HOW_ARE_YOU -> say.howAreYou(l)
                Talk.THANKS -> say.thanks(l)
                Talk.WHO_ARE_YOU -> say.whoAreYou(l)
            }, l, expectAnswer = cmd.kind == Talk.WAKE_ONLY
        )
        Command.Cancel -> Reply(say.okCancel(l), l)

        is Command.OpenApp -> openApp(cmd, l)
        is Command.PlayMusic -> playMusic(cmd.query, cmd.app, cmd.kind, cmd.artist, cmd.title, l, progress)
        is Command.SearchYouTube -> {
            if (!music.installed(MusicApp.YOUTUBE)) actions.webSearch("site:youtube.com " + cmd.query, false)
            else music.openYouTubeSearch(cmd.query)
            Reply(say.resultsShown(l, cmd.query, "YouTube"), l)
        }
        is Command.AskSong -> {
            pending = Pending.AwaitSong(cmd.app)
            Reply(say.askSong(l), l, expectAnswer = true)
        }
        is Command.Media -> mediaCommand(cmd.action, cmd.seconds, l, progress)
        Command.MoreBySinger -> {
            val artist = memory.lastArtist() ?: media.artist(media.current()).takeIf { it.isNotBlank() }
            if (artist == null) {
                pending = Pending.AwaitSong(null)
                Reply(say.noHistory(l), l, expectAnswer = true)
            } else {
                progress(say.moreBy(l, artist), l)
                playMusic("$artist songs", null, MusicKind.PLAYLIST, artist, null, l, progress = { _, _ -> })
            }
        }
        Command.PlayFavourite -> {
            val fav = memory.favourite()
            if (fav == null) {
                pending = Pending.AwaitFavourite
                Reply(say.noFavourite(l), l, expectAnswer = true)
            } else {
                val app = runCatching { MusicApp.valueOf(fav.app) }.getOrNull()
                playMusic(fav.query, app, MusicKind.ANY, fav.artist.ifBlank { null }, fav.title.ifBlank { null }, l, progress)
            }
        }
        Command.SetFavourite -> {
            val now = media.title(media.current())
            val last = memory.last()
            when {
                now.isNotBlank() -> {
                    memory.setFavourite(MusicMemory.Entry(now, media.artist(media.current()), now, defaultApp().name, System.currentTimeMillis()))
                    Reply(say.favouriteSaved(l, now), l)
                }
                last != null -> { memory.setFavourite(last); Reply(say.favouriteSaved(l, last.title.ifBlank { last.query }), l) }
                else -> { pending = Pending.AwaitFavourite; Reply(say.noFavourite(l), l, expectAnswer = true) }
            }
        }

        is Command.Volume -> {
            val pct = media.volume(cmd.op, cmd.percent)
            Reply(say.volume(l, cmd.op, if (cmd.op == VolOp.SET) cmd.percent else pct), l)
        }
        is Command.Brightness -> brightness(cmd.op, cmd.percent, l)
        is Command.Torch -> {
            val r = actions.flashlight(cmd.on)
            if (r.has("error")) Reply(say.failed(l, r.optString("error")), l) else Reply(say.torch(l, cmd.on), l)
        }
        is Command.Call -> call(cmd.who, l)
        is Command.WhatsApp -> when {
            cmd.to == null -> { pending = Pending.AwaitWhatsAppTo(cmd.message); Reply(say.askWhatsAppTo(l), l, expectAnswer = true) }
            cmd.message == null -> { pending = Pending.AwaitWhatsAppMsg(cmd.to); Reply(say.askWhatsAppMsg(l, cmd.to), l, expectAnswer = true) }
            else -> sendWhatsApp(cmd.to, cmd.message, l)
        }
        is Command.Alarm -> if (cmd.hour == null) {
            pending = Pending.AwaitAlarm
            Reply(say.askAlarmTime(l), l, expectAnswer = true)
        } else alarm(cmd.hour, cmd.minute, l)
        is Command.Timer -> if (cmd.seconds == null) {
            pending = Pending.AwaitTimer
            Reply(say.askTimer(l), l, expectAnswer = true)
        } else timer(cmd.seconds, l)
        is Command.Weather -> weather(cmd.city, cmd.tomorrow, l)
        is Command.WebSearch -> when {
            cmd.query.isNotBlank() -> { actions.webSearch(cmd.query, cmd.inChrome); Reply(say.webSearch(l, cmd.query), l) }
            cmd.inChrome -> {
                actions.webSearch("", true)
                pending = Pending.AwaitSearch(true)
                Reply(say.chromeGoogle(l), l, expectAnswer = true)
            }
            else -> { pending = Pending.AwaitSearch(false); Reply(say.askSearch(l), l, expectAnswer = true) }
        }
        is Command.Maps -> {
            val r = actions.maps(cmd.query, cmd.navigate)
            if (r.has("error")) Reply(say.failed(l, r.optString("error")), l) else Reply(say.maps(l, cmd.query, cmd.navigate), l)
        }
        is Command.Info -> info(cmd.what, l)
        is Command.Screen -> screen(cmd, l)
        is Command.Unknown -> ai(raw, l)
    }

    // ------------------------------------------------------------------ music

    private fun defaultApp(): MusicApp = when (Prefs.musicApp(ctx)) {
        "ytmusic" -> MusicApp.YT_MUSIC
        "spotify" -> MusicApp.SPOTIFY
        else -> MusicApp.YOUTUBE
    }

    private fun playMusic(
        query: String, requested: MusicApp?, kind: MusicKind, artist: String?, title: String?, l: Lang,
        progress: (String, Lang) -> Unit
    ): Reply {
        var app = requested ?: defaultApp()
        if (!music.installed(app)) {
            if (app != MusicApp.YOUTUBE && music.installed(MusicApp.YOUTUBE)) {
                progress(say.fallbackToYouTube(l, app.app.label), l)
                app = MusicApp.YOUTUBE
            } else return Reply(say.notInstalled(l, app.app.label), l)
        }
        if (query.isBlank() && app == MusicApp.YOUTUBE) {
            pending = Pending.AwaitSong(requested)
            return Reply(say.askSong(l), l, expectAnswer = true)
        }
        if (query.isNotBlank()) progress(say.searching(l, query, app.app.label), l)
        memory.record(query, artist, title, app.name)
        val outcome = music.play(query, app, kind, artistGiven = artist != null, allowChoice = true)
        if (outcome is MusicController.Outcome.Choices) {
            pending = Pending.AwaitChoice(outcome.options, outcome.viaScreen, query, artist)
            return Reply(say.choices(l, outcome.options.map { shortTitle(it.title) }), l, expectAnswer = true)
        }
        return musicReply(outcome, query, l)
    }

    private fun musicReply(o: MusicController.Outcome, query: String, l: Lang): Reply = when (o) {
        is MusicController.Outcome.Playing -> {
            memory.updatePlayedTitle(o.title)
            Reply(say.nowPlaying(l, shortTitle(o.title), o.app.app.label), l, keepAwake = false)
        }
        is MusicController.Outcome.Opened -> {
            memory.updatePlayedTitle(o.title)
            Reply(say.opened(l, shortTitle(o.title), o.app.app.label), l, keepAwake = false)
        }
        is MusicController.Outcome.ResultsOnly -> Reply(
            say.resultsOnly(l, query, o.app.app.label) + if (o.accessibilityOff) " " + say.accOffForPlay(l) else "", l
        )
        is MusicController.Outcome.NotInstalled -> Reply(say.notInstalled(l, o.app.app.label), l)
        is MusicController.Outcome.Failed -> Reply(say.failed(l, o.reason), l)
        is MusicController.Outcome.Choices -> Reply(say.choices(l, o.options.map { shortTitle(it.title) }), l, expectAnswer = true)
    }

    /** "Tum Hi Ho Full Video Song | Aashiqui 2 | Aditya Roy Kapur..." -> "Tum Hi Ho" (nicer to hear) */
    private fun shortTitle(t: String): String {
        var s = t
        for (sep in listOf(" | ", "｜", " (", " [", " - Official", " Official", " Full Video", " Full Song", " Lyrical", " #")) {
            val i = s.indexOf(sep, ignoreCase = true)
            if (i >= 4) s = s.substring(0, i)
        }
        return s.trim().take(70)
    }

    private fun mediaCommand(action: MediaAction, seconds: Int, l: Lang, progress: (String, Lang) -> Unit): Reply {
        val r = media.perform(action, seconds)
        return when (r.status) {
            MediaControl.Status.OK -> when (action) {
                MediaAction.PAUSE -> Reply(say.paused(l), l)
                MediaAction.PLAY, MediaAction.RESUME -> Reply(say.resumed(l), l, keepAwake = false)
                MediaAction.STOP -> Reply(say.stopped(l), l)
                MediaAction.NEXT -> { if (r.detail.isNotBlank()) memory.updatePlayedTitle(r.detail); Reply(say.next(l, shortTitle(r.detail)), l, keepAwake = false) }
                MediaAction.PREVIOUS -> Reply(say.previous(l), l, keepAwake = false)
                MediaAction.SEEK -> Reply(say.seek(l, seconds), l, keepAwake = false)
                MediaAction.REPLAY -> Reply(say.replay(l), l, keepAwake = false)
            }
            MediaControl.Status.NO_SESSION -> {
                // Nothing is playing: "dobara chalao" / "play karo" -> play the last requested song again.
                val last = memory.last()
                if (last != null && (action == MediaAction.REPLAY || action == MediaAction.PLAY || action == MediaAction.RESUME)) {
                    val app = runCatching { MusicApp.valueOf(last.app) }.getOrNull()
                    playMusic(last.query, app, MusicKind.ANY, last.artist.ifBlank { null }, last.title.ifBlank { null }, l, progress)
                } else Reply(say.noMedia(l), l)
            }
            MediaControl.Status.NO_ACCESS -> Reply(say.needNotifAccess(l), l)
            MediaControl.Status.UNSUPPORTED -> Reply(say.unsupported(l, r.app.ifBlank { "App" }, r.detail), l)
        }
    }

    // ------------------------------------------------------------------ phone actions

    private fun openApp(cmd: Command.OpenApp, l: Lang): Reply {
        if (cmd.app != null) {
            val r = actions.openKnown(cmd.app)
            return if (r.has("error")) {
                if (r.optString("error").contains("not installed")) Reply(say.notInstalled(l, cmd.app.label), l)
                else Reply(say.failed(l, r.optString("error")), l)
            } else Reply(say.openedApp(l, cmd.app.label), l)
        }
        val r = actions.openApp(cmd.name)
        return if (r.has("error")) Reply(say.appMissing(l, cmd.name), l)
        else Reply(say.openedApp(l, r.optString("result").removePrefix("Opened ").ifBlank { cmd.name }), l)
    }

    private fun call(who: String, l: Lang): Reply {
        val r = actions.makeCall(who)
        val e = r.optString("error")
        return when {
            e.isEmpty() -> Reply(say.calling(l, r.optString("result").removePrefix("Calling ").ifBlank { who }), l, keepAwake = false)
            e.contains("permission", true) -> Reply(say.permission(l, if (e.contains("Contacts")) "Contacts" else "Call"), l)
            e.contains("not found", true) -> Reply(say.contactMissing(l, who), l)
            else -> Reply(say.failed(l, e), l)
        }
    }

    private fun sendWhatsApp(to: String, message: String, l: Lang): Reply {
        val r = actions.sendWhatsApp(to, message)
        val e = r.optString("error")
        val res = r.optString("result")
        return when {
            e.contains("permission", true) -> Reply(say.permission(l, "Contacts"), l)
            e.contains("not found", true) -> Reply(say.contactMissing(l, to), l)
            e.contains("not on WhatsApp") -> Reply(say.notOnWhatsApp(l, to), l)
            e.isNotEmpty() -> Reply(say.failed(l, e), l)
            res.startsWith("WhatsApp message sent") -> Reply(say.whatsappSent(l, res.substringAfter("sent to ").ifBlank { to }), l)
            res.contains("Accessibility is off") -> Reply(say.whatsappTyped(l, to) + " " + say.permission(l, "Phone Control (Accessibility)"), l)
            else -> Reply(say.whatsappTyped(l, to), l)
        }
    }

    private fun alarm(h: Int, m: Int, l: Lang): Reply {
        val r = actions.setAlarm(h, m, "Jarvis")
        return if (r.has("error")) Reply(say.failed(l, r.optString("error")), l) else Reply(say.alarm(l, h, m), l)
    }

    private fun timer(s: Int, l: Lang): Reply {
        val r = actions.setTimer(s, "Jarvis")
        return if (r.has("error")) Reply(say.failed(l, r.optString("error")), l) else Reply(say.timer(l, s), l)
    }

    private fun brightness(op: VolOp, percent: Int, l: Lang): Reply {
        val cur = try {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255
        } catch (e: Exception) { 50 }
        val target = when (op) {
            VolOp.UP -> cur + 25
            VolOp.DOWN -> cur - 25
            VolOp.MAX -> 100
            else -> percent
        }.coerceIn(5, 100)
        val r = actions.setBrightness(target)
        return if (r.has("error")) Reply(say.permission(l, "Modify System Settings"), l) else Reply(say.brightness(l, target), l)
    }

    private fun info(what: InfoType, l: Lang): Reply {
        val now = Calendar.getInstance()
        return when (what) {
            InfoType.TIME -> Reply(say.time(l, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), l)
            InfoType.DATE -> Reply(
                say.date(
                    l, SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date()),
                    SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(Date())
                ), l
            )
            InfoType.BATTERY -> {
                val d = actions.deviceInfo()
                Reply(say.battery(l, d.optInt("battery_percent"), d.optBoolean("charging")), l)
            }
        }
    }

    private fun weather(city: String?, tomorrow: Boolean, l: Lang): Reply {
        if (!online()) return Reply(say.noInternet(l), l)
        val w = actions.weather(city.orEmpty(), tomorrow)
        if (w.has("error")) {
            actions.webSearch("weather " + city.orEmpty() + if (tomorrow) " tomorrow" else "", false)
            return Reply(say.weatherFallback(l), l)
        }
        val desc = if (l == Lang.ENGLISH) w.optString("description") else w.optString("description_hi")
        return Reply(
            say.weather(
                l, w.optString("place"), desc.lowercase(Locale.ROOT).ifBlank { w.optString("description") }, w.optString("temp_c"),
                w.optString("feels_like_c"), w.optInt("chance_of_rain"), tomorrow, w.optString("max_c"), w.optString("min_c")
            ), l
        )
    }

    // ------------------------------------------------------------------ screen control (voice mouse)

    /**
     * An empty reply text means "done, nothing to say": the service just plays a short tick, so moving the
     * cursor step by step stays fast.
     */
    private fun screen(cmd: Command.Screen, l: Lang): Reply {
        val a = JarvisAccessibility.instance ?: return Reply(say.needAccessibility(l), l)
        val ok = Reply("", l)
        val fail = Reply(say.actionFailed(l), l)
        fun repeatCount() = if (cmd.step >= 0.25f) 3 else 1
        fun scrollAt(dir: String): Boolean {
            val pos = a.cursorPos()
            var done = false
            repeat(repeatCount()) { if (a.scroll(dir, pos?.first, pos?.second)) done = true; Thread.sleep(250) }
            if (a.overlayMode() == ScreenOverlay.Mode.NUMBERS) { Thread.sleep(500); a.showNumbers() }
            return done
        }
        fun afterTap() {
            if (a.overlayMode() == ScreenOverlay.Mode.NUMBERS) { Thread.sleep(1000); a.showNumbers() }
        }
        return when (cmd.action) {
            ScreenAction.CURSOR_SHOW -> { a.cursorShow(); Reply(say.cursorShown(l), l) }
            ScreenAction.MOVE_CURSOR -> { a.cursorMove(cmd.dir ?: "down", cmd.step); ok }
            ScreenAction.MOVE -> if (a.cursorVisible()) { a.cursorMove(cmd.dir ?: "down", cmd.step); ok }
                else if (scrollAt(cmd.dir ?: "down")) ok else Reply(say.cantScroll(l), l)
            ScreenAction.SCROLL -> if (scrollAt(cmd.dir ?: "down")) ok else Reply(say.cantScroll(l), l)
            ScreenAction.CURSOR_TO -> {
                val n = a.findByText(cmd.text) ?: return Reply(say.notOnScreen(l, cmd.text), l)
                val (x, y) = a.center(n)
                a.cursorTo(x, y); ok
            }
            ScreenAction.OVERLAY_HIDE -> { a.hideOverlay(); Reply(say.overlayHidden(l), l) }
            ScreenAction.NUMBERS_SHOW -> {
                val n = a.showNumbers()
                if (n == 0) Reply(say.notOnScreen(l, "buttons"), l) else Reply(say.numbersShown(l, n), l)
            }
            ScreenAction.GRID_SHOW -> Reply(say.gridShown(l, a.showGrid()), l)
            ScreenAction.CLICK, ScreenAction.DOUBLE_CLICK, ScreenAction.LONG_PRESS -> {
                val pos = a.cursorPos()
                val target = if (cmd.text.isNotBlank()) a.findByText(cmd.text) else null
                if (cmd.text.isNotBlank() && target == null) return Reply(say.notOnScreen(l, cmd.text), l)
                val (x, y) = when {
                    target != null -> a.center(target)
                    pos != null -> pos
                    else -> {
                        // No cursor yet: show it so the user can point.
                        a.cursorShow()
                        return Reply(say.cursorShown(l), l)
                    }
                }
                val done = when (cmd.action) {
                    ScreenAction.DOUBLE_CLICK -> a.doubleTapAt(x, y)
                    ScreenAction.LONG_PRESS -> a.longPressAt(x, y)
                    else -> a.tapAt(x, y)
                }
                if (done) { afterTap(); ok } else fail
            }
            ScreenAction.TAP_TEXT -> {
                if (a.tapText(cmd.text)) { afterTap(); ok } else Reply(say.notOnScreen(l, cmd.text), l)
            }
            ScreenAction.PICK -> {
                val mode = a.overlayMode()
                if (mode != ScreenOverlay.Mode.NUMBERS && mode != ScreenOverlay.Mode.GRID) {
                    // no numbers on screen: maybe a button literally labelled "5"
                    return if (a.tapText(cmd.number.toString())) ok else Reply(say.noNumbers(l), l)
                }
                if (a.pickNumber(cmd.number, cmd.text)) { if (cmd.text != "cursor") afterTap(); ok }
                else Reply(say.badNumber(l, cmd.number), l)
            }
            ScreenAction.TYPE -> {
                if (cmd.text.isBlank()) return Reply(say.askSearch(l), l)
                if (a.typeText(cmd.text, cmd.enter)) Reply(say.typed(l, cmd.text), l) else Reply(say.noTextBox(l), l)
            }
            ScreenAction.ENTER -> if (a.pressEnter()) ok else fail
            ScreenAction.BACK -> if (a.global("back")) ok else fail
            ScreenAction.HOME -> if (a.global("home")) ok else fail
            ScreenAction.RECENTS -> if (a.global("recents")) ok else fail
            ScreenAction.NOTIF_SHADE -> if (a.global("notifications")) ok else fail
        }
    }

    // ------------------------------------------------------------------ AI fallback

    private fun online(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        true
    }

    private fun ai(raw: String, l: Lang): Reply {
        if (Prefs.apiKey(ctx).isBlank()) return Reply(say.noApiKey(l), l)
        if (!online()) return Reply(say.noInternet(l), l)
        val r = gemini.chat(raw, l) { name, args ->
            JarvisService.log("⚙ $name $args")
            val res: JSONObject = actions.run(name, args)
            JarvisService.log("   ↳ ${res.toString().take(140)}")
            res
        }
        return when (r.failure) {
            GeminiClient.Failure.NO_KEY -> Reply(say.noApiKey(l), l)
            GeminiClient.Failure.NETWORK -> Reply(say.noInternet(l), l)
            GeminiClient.Failure.API -> Reply(say.aiError(l), l)
            null -> Reply(r.text, l, keepAwake = !media.isPlaying(media.current()))
        }
    }
}
