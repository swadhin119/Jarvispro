package com.swadhin.jarvis.nlu

/**
 * Local, offline intent recognition for Hindi / English / Hinglish.
 * Works on concepts, not fixed sentences: "volume badha do", "आवाज़ बढ़ाओ", "increase the volume" all hit the same rule.
 * Anything it is not sure about becomes [Command.Unknown] and goes to Gemini.
 */
object IntentParser {

    private val PLATFORM = setOf(C.YOUTUBE, C.SPOTIFY, C.GOOGLE, C.CHROME)
    private val MUSIC_WORDS = setOf(C.SONG, C.SONGS, C.MUSIC, C.VIDEO, C.PLAYLIST)
    private val NON_MUSIC_APPS = setOf(C.WHATSAPP, C.INSTAGRAM, C.CHROME, C.MAPS, C.CAMERA, C.GALLERY, C.SETTINGS, C.DIALER, C.GOOGLE)
    private val GRAMMAR = setOf(C.LOC, C.POSS, C.KO, C.AND, C.TO)

    private fun f(vararg w: String) = w.map { Text.fold(it) }.toSet()

    private val LOC_WORDS = f("par", "pe", "per", "mein", "me", "mai", "on", "in", "at", "se", "from", "पर", "पे", "में", "मे", "से")
    private val LEAD_TRIM = f(
        "mujhe", "mujhko", "ek", "koi", "kuch", "the", "a", "an", "some", "any", "please", "plz", "pls", "zara", "jara", "ab", "abhi",
        "hey", "ok", "okay", "sir", "ji", "hume", "humein", "jaldi", "us", "for", "to", "can", "could", "would",
        "will", "i", "want", "wanna", "lets", "let", "just", "now", "karo", "kar", "toh", "boss", "yaar", "bhai", "kindly",
        "मुझे", "मुझको", "एक", "कोई", "कुछ", "प्लीज", "प्लीज़", "जरा", "अब", "अभी", "सर", "जी", "हमें", "जल्दी", "तो", "यार", "भाई"
    )
    private val TRAIL_TRIM = f(
        "karo", "kar", "kardo", "do", "de", "dena", "dedo", "dijiye", "kijiye", "kariye", "karna", "karein", "karen", "kro", "krdo", "please",
        "plz", "pls", "na", "naa", "zara", "jaldi", "abhi", "ab", "sir", "boss", "ji", "now", "for", "me", "yaar", "bhai", "wala", "wali", "wale",
        "chahiye", "suno", "sunna", "lo", "lijiye", "it", "quickly", "likh", "likhkar", "likhke", "लिख", "लिखकर",
        "करो", "कर", "करदो", "दो", "दे", "देना", "दीजिए", "दिजिए", "कीजिए", "करिए", "करना", "करें", "ना", "जरा", "जल्दी", "अभी", "अब", "सर", "जी",
        "यार", "भाई", "प्लीज", "प्लीज़", "वाला", "वाली", "वाले", "चाहिए", "लो"
    )
    private val ARTIST_AFTER = f("by", "of")
    private val CUE_IS = f("hai", "है", "is", "its", "it's")

    fun tokenize(text: String): List<Tok> = Text.tokens(text).map { Tok(it, Text.fold(it), Lexicon.concept(it)) }

    /** Removes the wake word. "Jarvis, YouTube kholo" / "YouTube kholo Jarvis" -> "YouTube kholo". */
    fun stripWake(toks: List<Tok>): List<Tok> {
        val wi = toks.indexOfFirst { it.c == C.WAKE }
        if (wi < 0) return toks
        val after = toks.drop(wi + 1).filter { it.c != C.WAKE }
        return if (after.isNotEmpty()) after else toks.take(wi).filter { it.c != C.WAKE }
    }

    fun parse(text: String): Command {
        val toks = stripWake(tokenize(text))
        if (toks.isEmpty()) return Command.SmallTalk(Talk.WAKE_ONLY)
        val has = { c: String -> toks.any { it.c == c } }
        val content = toks.filter { it.c == null }
        val noContent = content.isEmpty()
        val music = toks.any { it.c in MUSIC_WORDS }
        val ytMusic = isYtMusic(toks)
        val nonMusicApp = toks.any { it.c in NON_MUSIC_APPS }

        if (has(C.CANCEL) && noContent && toks.size <= 4) return Command.Cancel

        // ---- torch: "light band kar do", "torch jalao", "flashlight on" ----
        if (has(C.TORCH) && !music && !has(C.BRIGHTNESS) && noContent) return Command.Torch(on = !has(C.OFF))

        // ---- Wi-Fi / Bluetooth / data: needs the screen-operating AI ----
        if (has(C.SYSTEM_TOGGLE)) return Command.Unknown(text)

        // ---- volume ----
        if (has(C.UNMUTE)) return Command.Volume(VolOp.UNMUTE)
        if (has(C.MUTE) && !has(C.OFF)) return Command.Volume(VolOp.MUTE)
        if (has(C.VOLUME)) {
            val pct = percent(toks)
            return when {
                pct >= 0 -> Command.Volume(VolOp.SET, pct)
                has(C.FULL) -> Command.Volume(VolOp.MAX)
                has(C.MUTE) || has(C.OFF) -> Command.Volume(VolOp.MUTE)
                has(C.DOWN) -> Command.Volume(VolOp.DOWN)
                has(C.UP) || has(C.ON) -> Command.Volume(VolOp.UP)
                else -> Command.Unknown(text)
            }
        }
        if (has(C.BRIGHTNESS)) {
            val pct = percent(toks)
            return when {
                pct >= 0 -> Command.Brightness(VolOp.SET, pct)
                has(C.FULL) -> Command.Brightness(VolOp.MAX)
                has(C.DOWN) -> Command.Brightness(VolOp.DOWN)
                has(C.UP) -> Command.Brightness(VolOp.UP)
                else -> Command.Unknown(text)
            }
        }

        // ---- seek: "30 second aage karo", "10 second peeche", "forward 1 minute" ----
        if ((has(C.FORWARD) || has(C.BACKWARD) || has(C.SEEK)) && noContent && !nonMusicApp) {
            val secs = TimeParser.duration(toks) ?: 10
            return Command.Media(MediaAction.SEEK, if (has(C.BACKWARD)) -secs else secs)
        }

        // ---- track changes ----
        if (has(C.NEXT) && noContent && !nonMusicApp) return Command.Media(MediaAction.NEXT)
        if (has(C.PREV) && noContent && !nonMusicApp) return Command.Media(MediaAction.PREVIOUS)
        if (has(C.AGAIN) && noContent && !nonMusicApp &&
            (has(C.PLAY) || music || has(C.SAME) || has(C.RESUME) || toks.size <= 2)
        ) return Command.Media(MediaAction.REPLAY)

        // ---- memory based ----
        if (has(C.FAVOURITE)) {
            return when {
                has(C.PLAY) -> Command.PlayFavourite
                has(C.SET_CUE) || toks.any { it.f in CUE_IS } -> Command.SetFavourite
                else -> Command.PlayFavourite
            }
        }
        if (noContent && (has(C.SINGER) || ((has(C.MORE) || has(C.AND)) && (has(C.SONG) || has(C.SONGS) || has(C.MUSIC))))) {
            return Command.MoreBySinger
        }

        // ---- pause / stop / resume ----
        if (has(C.PAUSE) && noContent && !nonMusicApp) return Command.Media(MediaAction.PAUSE)
        if (has(C.OFF) && noContent && !nonMusicApp && !has(C.CALL)) return Command.Media(MediaAction.STOP)
        if (has(C.RESUME) && noContent && !nonMusicApp && !has(C.CALL)) return Command.Media(MediaAction.RESUME)

        // ---- WhatsApp message ----
        if (has(C.WHATSAPP) && !has(C.CALL) && (has(C.MESSAGE) || has(C.SEND) || has(C.KO) || has(C.THAT))) {
            return whatsapp(toks)
        }

        // ---- phone call: "Rahul ko phone lagao", "call mummy", "राहुल को कॉल करो" ----
        if (has(C.CALL) && !music && !has(C.WHATSAPP)) {
            val who = phrase(toks, setOf(C.CALL, C.PLAY, C.OPEN, C.FILLER, C.SEND))
            if (who.isNotEmpty() && content.isNotEmpty()) return Command.Call(who)
            if (has(C.OPEN) || toks.size == 1) return Command.OpenApp(AppId.PHONE, AppId.PHONE.label)
            return Command.Unknown(text)
        }

        // ---- alarm / timer ----
        if (has(C.TIMER)) return Command.Timer(TimeParser.duration(toks))
        if (has(C.ALARM)) {
            val t = TimeParser.clock(toks)
            return Command.Alarm(t?.first, t?.second ?: 0)
        }

        // ---- explicit searches ----
        if (has(C.SEARCH)) {
            val q = phrase(toks, setOf(C.SEARCH, C.OPEN, C.APP, C.MAPS, C.SHOW, C.FILLER) + PLATFORM + MUSIC_WORDS.minus(C.MUSIC))
            return when {
                ytMusic -> Command.PlayMusic(q, MusicApp.YT_MUSIC, MusicKind.ANY)
                has(C.YOUTUBE) -> if (q.isEmpty()) Command.OpenApp(AppId.YOUTUBE, "YouTube") else Command.SearchYouTube(searchQuery(toks))
                has(C.SPOTIFY) -> Command.PlayMusic(q, MusicApp.SPOTIFY, MusicKind.ANY)
                has(C.MAPS) -> Command.Maps(q, false)
                else -> Command.WebSearch(q, inChrome = has(C.CHROME))
            }
        }

        // ---- weather / info ----
        if (has(C.WEATHER)) {
            val city = phrase(toks, setOf(C.WEATHER, C.TODAY, C.TOMORROW, C.FILLER, C.SHOW, C.TIME, C.MORNING, C.EVENING, C.NIGHT, C.NOON))
            return Command.Weather(city.ifEmpty { null }, has(C.TOMORROW))
        }
        if (has(C.BATTERY) && noContent) return Command.Info(InfoType.BATTERY)
        if (has(C.DATE) && noContent && (has(C.TODAY) || toks.size <= 4)) return Command.Info(InfoType.DATE)
        if ((has(C.TIME) || has(C.BAJE)) && noContent) return Command.Info(InfoType.TIME)

        // ---- maps ----
        if (has(C.MAPS) || has(C.NAV)) {
            val q = phrase(toks, setOf(C.MAPS, C.NAV, C.SHOW, C.OPEN, C.GOOGLE, C.APP, C.FILLER))
            if (q.isEmpty()) return Command.OpenApp(AppId.MAPS, AppId.MAPS.label)
            return Command.Maps(q, navigate = has(C.NAV))
        }

        // ---- music ----
        val app: MusicApp? = when {
            ytMusic -> MusicApp.YT_MUSIC
            has(C.SPOTIFY) -> MusicApp.SPOTIFY
            has(C.YOUTUBE) -> MusicApp.YOUTUBE
            else -> null
        }
        if ((has(C.PLAY) || (music && !has(C.OPEN))) && !(nonMusicApp && !music)) {
            val mq = musicQuery(toks)
            if (mq.query.isEmpty()) {
                return when {
                    app == MusicApp.SPOTIFY || app == MusicApp.YT_MUSIC -> Command.PlayMusic("", app, MusicKind.ANY)
                    music || app == MusicApp.YOUTUBE -> Command.AskSong(app)
                    else -> Command.Media(MediaAction.RESUME)          // "play karo"
                }
            }
            return Command.PlayMusic(mq.query, app, mq.kind, mq.artist, mq.title)
        }

        // ---- open an app ----
        val known = detectApp(toks)
        if (has(C.OPEN) || has(C.SHOW) || (known != null && (toks.size <= 2 || has(C.PLAY)))) {
            if (known == AppId.YOUTUBE && content.isNotEmpty()) return Command.SearchYouTube(searchQuery(toks))
            if (known != null) return Command.OpenApp(known, known.label)
            val name = phrase(toks, setOf(C.OPEN, C.APP, C.FILLER, C.SHOW))
            if (name.isNotEmpty() && has(C.OPEN)) return Command.OpenApp(null, name)
        }

        smallTalk(toks)?.let { return Command.SmallTalk(it) }
        return Command.Unknown(text)
    }

    /** Number for volume/brightness, only from digits ("do" in "kar do" is not 2). */
    private fun percent(toks: List<Tok>): Int {
        for (t in toks) {
            val n = t.f.removeSuffix("%").toIntOrNull() ?: continue
            if (n in 0..100) return n
        }
        return -1
    }

    private fun isYtMusic(toks: List<Tok>): Boolean {
        for (i in 0 until toks.size - 1) if (toks[i].c == C.YOUTUBE && toks[i + 1].c == C.MUSIC) return true
        return false
    }

    fun detectApp(toks: List<Tok>): AppId? {
        val has = { c: String -> toks.any { it.c == c } }
        return when {
            isYtMusic(toks) -> AppId.YT_MUSIC
            has(C.YOUTUBE) -> AppId.YOUTUBE
            has(C.WHATSAPP) -> AppId.WHATSAPP
            has(C.INSTAGRAM) -> AppId.INSTAGRAM
            has(C.MAPS) -> AppId.MAPS
            has(C.CHROME) -> AppId.CHROME
            has(C.CAMERA) -> AppId.CAMERA
            has(C.GALLERY) -> AppId.GALLERY
            has(C.SPOTIFY) -> AppId.SPOTIFY
            has(C.SETTINGS) -> AppId.SETTINGS
            has(C.DIALER) -> AppId.PHONE
            has(C.CALL) -> AppId.PHONE
            has(C.GOOGLE) -> AppId.GOOGLE
            else -> null
        }
    }

    // ---------------- phrase extraction ----------------

    /** Marks platform words ("YouTube par", "on Spotify") so they can be dropped from a query. */
    private fun platformMask(toks: List<Tok>): BooleanArray {
        val drop = BooleanArray(toks.size)
        for (i in toks.indices) {
            if (toks[i].c in PLATFORM) {
                drop[i] = true
                if (i + 1 < toks.size && toks[i + 1].c == C.MUSIC && toks[i].c == C.YOUTUBE) drop[i + 1] = true
                val after = if (drop.getOrElse(i + 1) { false }) i + 2 else i + 1
                if (after < toks.size && toks[after].f in LOC_WORDS) drop[after] = true
                if (i > 0 && toks[i - 1].f in LOC_WORDS) drop[i - 1] = true
            }
        }
        return drop
    }

    private fun trim(list: List<Tok>): List<Tok> {
        var s = 0
        var e = list.size
        while (s < e && (list[s].f in LEAD_TRIM || list[s].c in GRAMMAR || list[s].c == C.WAKE || list[s].c == C.MY)) s++
        while (e > s && (list[e - 1].f in TRAIL_TRIM || list[e - 1].c in GRAMMAR || list[e - 1].c == C.WAKE)) e--
        return list.subList(s, e)
    }

    private fun join(list: List<Tok>) = list.joinToString(" ") { it.orig }.trim()

    /** Removes the given concepts anywhere (plus platform words) and trims grammar/filler at the edges. */
    fun phrase(toks: List<Tok>, remove: Set<String>): String {
        val drop = platformMask(toks)
        val kept = toks.filterIndexed { i, t -> !drop[i] && t.c !in remove && t.c != C.WAKE }
        return join(trim(kept))
    }

    /** For "search on YouTube": keep song/video words (they help the search) but drop command words. */
    private fun searchQuery(toks: List<Tok>): String {
        val drop = platformMask(toks)
        val kept = toks.filterIndexed { i, t -> !drop[i] && t.c !in setOf(C.SEARCH, C.OPEN, C.SHOW, C.APP, C.WAKE, C.PLAY) }
        // "YouTube kholo aur cricket search karo": drop "aur" joining two commands
        return join(trim(kept.filterIndexed { i, t -> !(t.c == C.AND && i == 0) }))
    }

    data class MusicQuery(val query: String, val kind: MusicKind, val artist: String?, val title: String?)

    fun musicQuery(toks: List<Tok>): MusicQuery {
        val drop = platformMask(toks)
        var kind = MusicKind.ANY
        var songWord: String? = null
        var possBeforeMusic = false
        val kept = ArrayList<Tok>()
        for (i in toks.indices) {
            val t = toks[i]
            if (drop[i]) continue
            when (t.c) {
                C.WAKE, C.PLAY, C.SEARCH, C.OPEN, C.SHOW, C.APP -> continue
                C.SONG -> { if (kind == MusicKind.ANY) kind = MusicKind.SONG; songWord = songWord ?: "song"; possBeforeMusic = dropPossBefore(kept) || possBeforeMusic; continue }
                C.SONGS, C.MUSIC -> { if (kind != MusicKind.PLAYLIST) kind = MusicKind.SONGS; songWord = "songs"; possBeforeMusic = dropPossBefore(kept) || possBeforeMusic; continue }
                C.VIDEO -> { kind = MusicKind.VIDEO; possBeforeMusic = dropPossBefore(kept) || possBeforeMusic; continue }
                C.PLAYLIST -> { kind = MusicKind.PLAYLIST; possBeforeMusic = dropPossBefore(kept) || possBeforeMusic; continue }
                else -> kept.add(t)
            }
        }
        val core = trim(kept)
        if (core.isEmpty()) return MusicQuery("", kind, null, null)

        // artist / title guess
        var artist: String? = null
        var title: String? = null
        val p = core.indexOfFirst { it.c == C.POSS }
        if (p > 0) {
            val left = trim(core.subList(0, p))
            val right = trim(core.subList(p + 1, core.size))
            if (core[p].f in ARTIST_AFTER) {
                if (right.isNotEmpty()) artist = join(right)
                if (left.isNotEmpty()) title = join(left)
            } else if (left.none { it.c == C.MOVIE } && (left.size >= 2 || right.isEmpty())) {
                artist = join(left)
                if (right.isNotEmpty()) title = join(right)
            }
        }
        if (p < 0 && possBeforeMusic && core.size <= 4 && core.none { it.c == C.MOVIE || it.f in Lexicon.MOODS }) artist = join(core)
        val moods = core.any { it.f in Lexicon.MOODS }
        // "sad songs", "romantic gana", "Odia romantic songs" -> a playlist-style request
        if (moods && title == null && kind != MusicKind.VIDEO && kind != MusicKind.ANY) kind = MusicKind.PLAYLIST

        val selfDescribing = core.any { it.f in Lexicon.SELF_DESCRIBING }
        val base = join(core)
        val suffix = when {
            selfDescribing || kind == MusicKind.VIDEO || kind == MusicKind.ANY -> ""
            kind == MusicKind.PLAYLIST -> if (songWord == null) " playlist" else " songs"
            songWord == "songs" -> " songs"
            artist != null && title == null -> " songs"
            else -> " song"
        }
        return MusicQuery(base + suffix, kind, artist, title)
    }

    /** "Arijit Singh ka gana": drop the "ka" and remember that what came before it is probably the singer. */
    private fun dropPossBefore(kept: ArrayList<Tok>): Boolean {
        if (kept.isNotEmpty() && kept.last().c == C.POSS && kept.last().f !in ARTIST_AFTER) {
            kept.removeAt(kept.size - 1)
            return true
        }
        return false
    }

    /** Text the user gave as an answer to "kaunsa gana?" – treat everything as the song request. */
    fun musicQueryFromAnswer(text: String): MusicQuery {
        val toks = stripWake(tokenize(text))
        val mq = musicQuery(toks)
        if (mq.query.isNotEmpty()) return mq
        return MusicQuery(join(trim(toks)), MusicKind.ANY, null, null)
    }

    // ---------------- WhatsApp ----------------

    /** "ki/की/के" right after the send word means "that"; before it ("Rahul ki mummy") it is possessive. */
    private val THAT_AFTER_CMD = f("ki", "kee", "ke", "की", "के", "कि")
    private val WA_FRAME = setOf(C.WHATSAPP, C.MESSAGE, C.LOC, C.APP, C.KO, C.TO)

    private fun whatsapp(toks: List<Tok>): Command.WhatsApp {
        val cmd = toks.indices.filter { toks[it].c == C.WHATSAPP || toks[it].c == C.MESSAGE || toks[it].c == C.SEND }
        if (cmd.isEmpty()) return Command.WhatsApp(null, null)
        val firstCmd = cmd.first()
        val lastCmd = cmd.last()
        fun names(list: List<Tok>) = list.filter { it.c == null }.joinToString(" ") { it.orig }.trim().ifEmpty { null }

        // 1) explicit delimiter: "... bhejo ki/की/के/that/saying <message>"
        var thatIdx = (firstCmd + 1 until toks.size).firstOrNull { toks[it].c == C.THAT } ?: -1
        if (thatIdx < 0) thatIdx = (lastCmd + 1 until toks.size).firstOrNull { toks[it].f in THAT_AFTER_CMD } ?: -1
        if (thatIdx >= 0) {
            val message = join(toks.subList(thatIdx + 1, toks.size)).ifEmpty { null }
            return Command.WhatsApp(names(toks.subList(0, thatIdx)), message)
        }

        // 2) message spoken after the send word: "WhatsApp par Rahul ko message bhejo, main late aaunga"
        val tail = trim(toks.subList(lastCmd + 1, toks.size))
        if (tail.isNotEmpty() && tail.none { it.c == C.KO }) {
            return Command.WhatsApp(names(toks.subList(0, lastCmd)), join(tail))
        }

        // 3) message between the person and the send word: "Rahul ko WhatsApp par hello bhejo"
        val ko = toks.indexOfFirst { it.c == C.KO }
        val send = toks.indexOfLast { it.c == C.SEND }
        if (ko >= 0) {
            var message: String? = null
            if (send > ko) {
                val mid = toks.subList(ko + 1, send).dropWhile { it.c in WA_FRAME }
                message = join(trim(mid)).ifEmpty { null }
            }
            return Command.WhatsApp(names(toks.subList(0, ko)), message)
        }
        return Command.WhatsApp(names(toks), null)
    }

    // ---------------- small talk ----------------

    private val R_DOING = Regex("kya kar rah|what are you doing|क्या कर रह|kya chal raha|क्या चल रहा|what r u doing")
    private val R_HOW = Regex("kaise ho|kaisi ho|kaise hain|how are you|कैसे हो|कैसी हो|कैसे हैं|kya haal|क्या हाल|how r u|how do you do")
    private val R_WHO = Regex("who are you|kaun ho|कौन हो|tumhara naam|your name|तुम्हारा नाम|aapka naam|आपका नाम|introduce yourself")
    private val R_THANKS = Regex("thank|shukriya|dhanyavad|dhanyawad|धन्यवाद|शुक्रिया|थैंक|थेंक")
    private val R_HELLO = Regex("^(hi|hello|hey|hii|namaste|namaskar|नमस्ते|नमस्कार|हेलो|हैलो|हाय|good morning|good evening|good afternoon|good night|suprabhat|gm|ram ram|राम राम)\\b")

    private fun smallTalk(toks: List<Tok>): Talk? {
        val s = toks.joinToString(" ") { it.f }
        return when {
            R_DOING.containsMatchIn(s) -> Talk.WHAT_DOING
            R_HOW.containsMatchIn(s) -> Talk.HOW_ARE_YOU
            R_WHO.containsMatchIn(s) -> Talk.WHO_ARE_YOU
            R_THANKS.containsMatchIn(s) -> Talk.THANKS
            R_HELLO.containsMatchIn(s) && toks.size <= 4 -> Talk.GREETING
            else -> null
        }
    }

    // ---------------- answers to follow-up questions ----------------

    /** "pehla", "doosra wala", "number 2", "second one", "3" -> 1-based index */
    fun parseChoice(text: String): Int? {
        val toks = stripWake(tokenize(text))
        for (t in toks) Lexicon.ORDINALS[t.f]?.let { return it }
        for (t in toks) t.f.toIntOrNull()?.let { if (it in 1..9) return it }
        if (toks.size <= 3) for (t in toks) Lexicon.NUMBER_WORDS[t.f]?.let { if (it in 1..5) return it }
        return null
    }

    private val YES = f("haan", "han", "ha", "haa", "ji", "yes", "yeah", "yep", "ok", "okay", "sure", "bilkul", "theek", "thik", "हाँ", "हां", "जी", "ओके", "बिल्कुल", "ठीक", "kar", "karo", "करो")
    private val NO = f("nahi", "nahin", "na", "no", "nope", "mat", "नहीं", "नही", "ना", "मत", "rehne", "chhodo", "cancel")

    fun isNo(text: String): Boolean = stripWake(tokenize(text)).let { t -> t.isNotEmpty() && t.size <= 4 && t.any { it.f in NO } }
    fun isYes(text: String): Boolean = stripWake(tokenize(text)).let { t -> t.isNotEmpty() && t.size <= 4 && t.any { it.f in YES } && !isNo(text) }
}
