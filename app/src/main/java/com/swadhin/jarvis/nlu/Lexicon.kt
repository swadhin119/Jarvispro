package com.swadhin.jarvis.nlu

/** Concept ids. Every known word (in any script / spelling) maps to one of these. */
object C {
    const val WAKE = "WAKE"

    // apps
    const val YOUTUBE = "YOUTUBE"; const val WHATSAPP = "WHATSAPP"; const val INSTAGRAM = "INSTAGRAM"
    const val CHROME = "CHROME"; const val GOOGLE = "GOOGLE"; const val MAPS = "MAPS"; const val CAMERA = "CAMERA"
    const val GALLERY = "GALLERY"; const val SPOTIFY = "SPOTIFY"; const val SETTINGS = "SETTINGS"; const val DIALER = "DIALER"
    const val APP = "APP"

    // music nouns
    const val SONG = "SONG"; const val SONGS = "SONGS"; const val MUSIC = "MUSIC"; const val VIDEO = "VIDEO"
    const val PLAYLIST = "PLAYLIST"; const val MOVIE = "MOVIE"; const val SINGER = "SINGER"

    // verbs
    const val PLAY = "PLAY"; const val SEARCH = "SEARCH"; const val OPEN = "OPEN"; const val PAUSE = "PAUSE"
    const val RESUME = "RESUME"; const val OFF = "OFF"; const val ON = "ON"; const val SEND = "SEND"; const val SHOW = "SHOW"

    // media
    const val NEXT = "NEXT"; const val PREV = "PREV"; const val AGAIN = "AGAIN"; const val FORWARD = "FORWARD"
    const val BACKWARD = "BACKWARD"; const val SEEK = "SEEK"
    const val SEC = "SEC"; const val MIN = "MIN"; const val HOUR = "HOUR"

    // volume / device
    const val VOLUME = "VOLUME"; const val UP = "UP"; const val DOWN = "DOWN"; const val MUTE = "MUTE"
    const val UNMUTE = "UNMUTE"; const val FULL = "FULL"; const val TORCH = "TORCH"; const val BRIGHTNESS = "BRIGHTNESS"
    const val PERCENT = "PERCENT"; const val SYSTEM_TOGGLE = "SYSTEM_TOGGLE"

    // communication
    const val CALL = "CALL"; const val MESSAGE = "MESSAGE"; const val THAT = "THAT"; const val TO = "TO"

    // time / info
    const val ALARM = "ALARM"; const val TIMER = "TIMER"; const val WEATHER = "WEATHER"; const val TIME = "TIME"
    const val BAJE = "BAJE"; const val DATE = "DATE"; const val BATTERY = "BATTERY"
    const val TODAY = "TODAY"; const val TOMORROW = "TOMORROW"
    const val MORNING = "MORNING"; const val NOON = "NOON"; const val EVENING = "EVENING"; const val NIGHT = "NIGHT"
    const val AM = "AM"; const val PM = "PM"; const val HALF_PAST = "HALF_PAST"; const val QUARTER_PAST = "QUARTER_PAST"
    const val QUARTER_TO = "QUARTER_TO"; const val NAV = "NAV"

    // memory
    const val FAVOURITE = "FAVOURITE"; const val MORE = "MORE"; const val AND = "AND"; const val SAME = "SAME"
    const val SET_CUE = "SET_CUE"; const val MY = "MY"

    // grammar
    const val POSS = "POSS"       // ka / ke / ki
    const val LOC = "LOC"         // par / pe / mein / on / in
    const val KO = "KO"
    const val FILLER = "FILLER"
    const val NUM = "NUM"
    const val CANCEL = "CANCEL"

    // screen control / voice cursor
    const val CURSOR = "CURSOR"; const val NUMBERS = "NUMBERS"; const val GRID = "GRID"; const val HIDE = "HIDE"
    const val CLICK = "CLICK"; const val TYPE = "TYPE"; const val SCROLL = "SCROLL"
    const val DIR_UP = "DIR_UP"; const val DIR_DOWN = "DIR_DOWN"; const val DIR_LEFT = "DIR_LEFT"; const val DIR_RIGHT = "DIR_RIGHT"
    const val BACKNAV = "BACKNAV"; const val HOME = "HOME"; const val RECENTS = "RECENTS"; const val NOTIF = "NOTIF"
}

object Lexicon {

    private val raw: Map<String, List<String>> = mapOf(
        C.WAKE to listOf("jarvis", "jarvish", "jarwis", "jarvees", "jervis", "जार्विस", "जारविस", "जर्विस", "जार्वीस", "जार्विश", "जारविश", "जाविस", "जरविस"),

        C.YOUTUBE to listOf("youtube", "youtub", "utube", "yt", "यूट्यूब", "यूटयूब", "युट्यूब", "यूटूब", "यूट्यूब्स"),
        C.WHATSAPP to listOf("whatsapp", "whatsap", "watsapp", "watsap", "whatsaap", "व्हाट्सएप", "व्हाट्सऐप", "वॉट्सऐप", "वाट्सएप", "वॉट्सएप", "व्हाट्सअप", "वाट्सऐप"),
        C.INSTAGRAM to listOf("instagram", "insta", "इंस्टाग्राम", "इंस्टा", "इन्स्टाग्राम"),
        C.CHROME to listOf("chrome", "क्रोम", "browser", "ब्राउज़र", "ब्राउजर"),
        C.GOOGLE to listOf("google", "गूगल", "गुगल"),
        C.MAPS to listOf("maps", "map", "मैप", "मैप्स", "नक्शा", "naksha"),
        C.CAMERA to listOf("camera", "कैमरा", "कैमरे", "कमरा", "selfie", "सेल्फी"),
        C.GALLERY to listOf("gallery", "गैलरी", "photos", "फोटोज", "फोटोस", "galary"),
        C.SPOTIFY to listOf("spotify", "स्पॉटिफाई", "स्पोटिफाई", "स्पॉटीफाई", "स्पोटीफाई"),
        C.SETTINGS to listOf("settings", "setting", "सेटिंग", "सेटिंग्स", "सेटिंग्ज"),
        C.DIALER to listOf("dialer", "dialler", "डायलर"),
        C.APP to listOf("app", "application", "ऐप", "एप", "एप्प", "ऐप्प", "apps"),

        C.SONG to listOf("gana", "gaana", "ganaa", "geet", "song", "track", "गाना", "गीत", "सॉन्ग", "सोंग", "ट्रैक", "gaani"),
        C.SONGS to listOf("gane", "gaane", "gaano", "songs", "tracks", "गाने", "गानों", "गानो", "सॉन्ग्स", "सोंग्स", "geeton", "गीतों"),
        C.MUSIC to listOf("music", "म्यूजिक", "म्यूज़िक", "मयूजिक", "संगीत", "sangeet"),
        C.VIDEO to listOf("video", "videos", "वीडियो", "विडियो", "वीडियोज"),
        C.PLAYLIST to listOf("playlist", "प्लेलिस्ट", "mix", "मिक्स", "jukebox", "ज्यूकबॉक्स"),
        C.MOVIE to listOf("movie", "film", "picture", "मूवी", "फिल्म", "फ़िल्म", "पिक्चर"),
        C.SINGER to listOf("singer", "artist", "gayak", "सिंगर", "गायक", "कलाकार", "आर्टिस्ट"),

        C.PLAY to listOf(
            "play", "chalao", "chala", "chalaao", "chalado", "chalaiye", "chalana", "chalaye", "chalayein",
            "bajao", "baja", "bajado", "bajaiye", "bajana", "sunao", "suna", "sunado", "sunaiye", "sunna",
            "lagao", "laga", "lagado", "lagaiye", "start",
            "प्ले", "चलाओ", "चला", "चलाना", "चलाइए", "चलाइये", "चलादो", "चलाएं", "चलाये", "बजाओ", "बजा", "बजाना", "बजाइए",
            "सुनाओ", "सुना", "सुनाइए", "सुनना", "लगाओ", "लगा", "लगाइए", "लगादो", "स्टार्ट", "plays", "playing"
        ),
        C.SEARCH to listOf("search", "dhundo", "dhoondo", "dhundho", "khojo", "find", "look", "सर्च", "ढूंढो", "ढूंढ़ो", "ढूँढो", "खोजो", "सच", "serch"),
        C.OPEN to listOf("open", "kholo", "khol", "kholiye", "kholna", "kholdo", "launch", "खोलो", "खोल", "खोलिए", "खोलिये", "खोलना", "ओपन", "खोलदो"),
        C.SHOW to listOf("dikhao", "dikha", "dikhaiye", "show", "दिखाओ", "दिखा", "दिखाइए", "batao", "bata", "बताओ", "बता", "बताइए", "tell"),
        C.PAUSE to listOf("pause", "ruko", "roko", "rok", "ruk", "thamo", "hold", "पॉज़", "पॉज", "पोज", "रुको", "रोको", "रोक", "रुक", "थामो"),
        C.RESUME to listOf("resume", "continue", "chalu", "unpause", "रिज्यूम", "रिज़्यूम", "कंटिन्यू", "चालू"),
        C.OFF to listOf("band", "bandh", "stop", "off", "bujhao", "bujha", "बंद", "स्टॉप", "स्टाप", "ऑफ", "बुझाओ", "बुझा", "close", "क्लोज", "turnoff"),
        C.ON to listOf("on", "jalao", "jala", "ऑन", "जलाओ", "जला", "chalu_on"),
        C.SEND to listOf("bhejo", "bhej", "bhejdo", "bhejna", "bhejiye", "send", "भेजो", "भेज", "भेजना", "भेजिए", "भेजदो", "सेंड"),

        C.NEXT to listOf("next", "agla", "agle", "agli", "aglaa", "skip", "अगला", "अगले", "अगली", "नेक्स्ट", "स्किप"),
        C.PREV to listOf("previous", "pichla", "pichhla", "pichle", "pichhle", "pichli", "pichhli", "prev", "पिछला", "पिछले", "पिछली", "प्रीवियस", "पहले_वाला"),
        C.AGAIN to listOf("dobara", "dubara", "dobaara", "phir", "fir", "firse", "phirse", "again", "replay", "restart", "dohrao", "दोबारा", "दुबारा", "फिर", "फिरसे", "रिप्ले", "रीप्ले", "रीस्टार्ट"),
        C.FORWARD to listOf("aage", "aagey", "age", "agey", "forward", "fast", "ahead", "आगे", "फॉरवर्ड", "फास्ट"),
        C.BACKWARD to listOf("peeche", "piche", "peechhe", "pichhe", "peechey", "backward", "backwards", "rewind", "पीछे", "पिछे", "रिवाइंड"),
        C.SEEK to listOf("seek", "skip_to"),
        C.SEC to listOf("second", "seconds", "sec", "secs", "s", "सेकंड", "सेकेंड", "सेकण्ड", "सेकंड्स"),
        C.MIN to listOf("minute", "minutes", "min", "mins", "मिनट", "मिनिट", "मिनट्स"),
        C.HOUR to listOf("hour", "hours", "ghanta", "ghante", "hr", "घंटा", "घंटे", "घण्टे"),

        C.VOLUME to listOf("volume", "वॉल्यूम", "वोल्यूम", "वॉल्युम", "आवाज", "awaz", "awaaz", "aawaz", "aawaaz", "sound", "साउंड"),
        C.UP to listOf("badhao", "badha", "badhado", "badhaiye", "badhana", "barhao", "badao", "increase", "up", "zyada", "jyada", "tez", "louder", "raise", "high",
            "बढ़ाओ", "बढ़ा", "बढ़ादो", "बढ़ाइए", "बढ़ाना", "ज्यादा", "ज़्यादा", "तेज", "तेज़", "इंक्रीज", "अप"),
        C.DOWN to listOf("kam", "ghatao", "ghata", "dheere", "dheema", "dhima", "decrease", "down", "lower", "reduce", "low", "कम", "घटाओ", "घटा", "धीमा", "धीमी", "धीरे", "डिक्रीज", "डाउन"),
        C.MUTE to listOf("mute", "म्यूट", "silent", "साइलेंट"),
        C.UNMUTE to listOf("unmute", "अनम्यूट"),
        C.FULL to listOf("full", "maximum", "max", "फुल", "पूरा", "poora", "pura", "मैक्सिमम"),
        C.TORCH to listOf("torch", "flashlight", "flash", "light", "batti", "टॉर्च", "टोर्च", "फ्लैशलाइट", "फ्लैश", "लाइट", "बत्ती"),
        C.BRIGHTNESS to listOf("brightness", "ब्राइटनेस", "roshni", "रोशनी", "chamak"),
        C.PERCENT to listOf("percent", "%", "pratishat", "परसेंट", "प्रतिशत"),
        C.SYSTEM_TOGGLE to listOf("wifi", "wi-fi", "वाईफाई", "वाई-फाई", "bluetooth", "ब्लूटूथ", "hotspot", "हॉटस्पॉट", "data", "डाटा", "डेटा", "airplane", "aeroplane", "location", "लोकेशन", "gps"),

        C.CALL to listOf("call", "phone", "fone", "dial", "कॉल", "काल", "फोन", "फ़ोन", "milao", "मिलाओ", "डायल", "ring"),
        C.MESSAGE to listOf("message", "msg", "messege", "text", "sandesh", "मैसेज", "मेसेज", "मसेज", "संदेश", "टेक्स्ट"),
        C.THAT to listOf("कि", "that", "saying", "likho", "लिखो", "bolo", "बोलो", "says"),
        C.TO to listOf("to"),

        C.ALARM to listOf("alarm", "अलार्म", "utha", "uthana", "uthao", "uthaana", "jagana", "jaga", "jagao", "wake", "उठा", "उठाना", "उठाओ", "जगा", "जगाना", "जगाओ", "अलार्म्स"),
        C.TIMER to listOf("timer", "टाइमर", "countdown"),
        C.WEATHER to listOf("mausam", "mosam", "weather", "मौसम", "मोसम", "वेदर", "temperature", "तापमान", "तापमान", "barish", "baarish", "rain", "बारिश", "garmi", "thand", "गर्मी", "ठंड"),
        C.TIME to listOf("time", "टाइम", "samay", "समय", "waqt", "वक्त", "वक़्त"),
        C.BAJE to listOf("baje", "bje", "baja_", "बजे", "oclock"),
        C.DATE to listOf("date", "tarikh", "tareekh", "डेट", "तारीख", "तारीख़", "din", "दिन", "day"),
        C.BATTERY to listOf("battery", "बैटरी", "charge", "charging", "चार्ज"),
        C.TODAY to listOf("aaj", "aj", "today", "आज"),
        C.TOMORROW to listOf("kal", "tomorrow", "कल"),
        C.MORNING to listOf("subah", "subha", "suba", "morning", "सुबह", "सवेरे", "savere", "prabhat"),
        C.NOON to listOf("dopahar", "dopehar", "dupahar", "afternoon", "दोपहर", "noon"),
        C.EVENING to listOf("shaam", "sham", "evening", "शाम"),
        C.NIGHT to listOf("raat", "rat", "night", "रात", "tonight"),
        C.AM to listOf("am", "a.m", "a.m."),
        C.PM to listOf("pm", "p.m", "p.m."),
        C.HALF_PAST to listOf("saade", "sade", "saadhe", "साढ़े", "साढे"),
        C.QUARTER_PAST to listOf("sawa", "sava", "सवा"),
        C.QUARTER_TO to listOf("paune", "pone", "पौने"),
        C.NAV to listOf("rasta", "raasta", "route", "direction", "directions", "navigate", "navigation", "रास्ता", "नेविगेट", "डायरेक्शन"),

        C.FAVOURITE to listOf("favourite", "favorite", "fav", "fevorite", "pasandida", "pasandeeda", "फेवरेट", "फेवरिट", "फ़ेवरेट", "पसंदीदा", "फेवरेट्स"),
        C.MORE to listOf("more", "other", "dusre", "doosre", "दूसरे", "dusra_more"),
        C.AND to listOf("aur", "or_", "and", "और", "phir_and"),
        C.SAME to listOf("wahi", "vahi", "wohi", "wahee", "same", "yahi", "वही", "यही", "वो_ही"),
        C.SET_CUE to listOf("banao", "bana", "save", "set", "mark", "rakho", "rakh", "बनाओ", "बना", "सेव", "रखो", "रख"),
        C.MY to listOf("mera", "meri", "mere", "my", "मेरा", "मेरी", "मेरे", "humara", "hamara"),

        C.POSS to listOf("ka", "ke", "ki", "का", "के", "की", "wala", "wale", "wali", "वाला", "वाले", "वाली", "of", "by"),
        C.LOC to listOf("par", "pe", "per", "mein", "me", "mai", "main_loc", "on", "in", "at", "से_loc", "पर", "पे", "में", "मे"),
        C.KO to listOf("ko", "को"),
        C.CURSOR to listOf("cursor", "cursur", "curser", "mouse", "pointer", "कर्सर", "करसर", "माउस", "पॉइंटर", "पोइंटर"),
        C.NUMBERS to listOf("numbers", "number", "numbering", "नंबर", "नम्बर", "नंबर्स", "labels", "label"),
        C.GRID to listOf("grid", "ग्रिड", "khane", "खाने"),
        C.HIDE to listOf("hatao", "hata", "hatado", "hataiye", "hide", "remove", "chupao", "chhupao", "हटाओ", "हटा", "हटादो", "हटाइए", "छुपाओ", "छिपाओ"),
        C.CLICK to listOf("click", "klik", "tap", "dabao", "daba", "dabaye", "dabaiye", "dabana", "dabado", "press", "select", "choose",
            "क्लिक", "कलिक", "टैप", "टेप", "दबाओ", "दबा", "दबाएं", "दबाइए", "दबाना", "दबादो", "प्रेस", "सेलेक्ट", "सिलेक्ट"),
        C.TYPE to listOf("type", "typing", "टाइप", "टाईप"),
        C.SCROLL to listOf("scroll", "scrol", "skrol", "scrolling", "स्क्रॉल", "स्क्रोल", "स्क्राल"),
        C.DIR_UP to listOf("upar", "uper", "oopar", "upper", "ऊपर", "उपर", "ऊपरी"),
        C.DIR_DOWN to listOf("neeche", "niche", "nichay", "neche", "नीचे", "निचे", "नीचें"),
        C.DIR_LEFT to listOf("left", "baayein", "bayen", "baaye", "baen", "बाएं", "बाएँ", "बाये", "लेफ्ट", "उल्टा"),
        C.DIR_RIGHT to listOf("right", "daayein", "dayen", "daaye", "daen", "दाएं", "दाएँ", "दाये", "राइट", "सीधा"),
        C.BACKNAV to listOf("back", "wapas", "vapas", "waapas", "वापस", "बैक"),
        C.HOME to listOf("home", "होम"),
        C.RECENTS to listOf("recent", "recents", "रीसेंट", "रिसेंट"),
        C.NOTIF to listOf("notification", "notifications", "नोटिफिकेशन", "नोटिफिकेशंस", "notif"),
        C.CANCEL to listOf("cancel", "chhodo", "chodo", "rehne", "rahne", "nevermind", "कैंसल", "छोड़ो", "छोडो", "रहने"),
        C.FILLER to listOf(
            "please", "plz", "pls", "plis", "kar", "karo", "kardo", "karna", "kariye", "kijiye", "karein", "karen", "kro", "krdo",
            "karde", "kardijiye", "dijiye", "do", "de", "dena", "dedo", "deejiye", "na", "naa", "zara", "jara", "thoda", "thodi",
            "abhi", "ab", "toh", "to_", "yaar", "yar", "bhai", "sir", "boss", "madam", "ji", "jee", "hey", "hi", "hello", "ok", "okay",
            "the", "a", "an", "some", "any", "can", "could", "would", "will", "you", "me_", "i", "want", "wanna", "lets", "let", "us",
            "for", "it", "this", "that_", "is", "are", "be", "now", "just", "kindly", "quickly", "jaldi", "bas_", "ek", "koi", "kuch",
            "sa", "si", "se", "hai", "hain", "ho", "hoga", "hogi", "tha", "raha", "rahe", "rahi", "jaye", "jao", "jaiye", "lo", "lijiye",
            "sakte", "sakta", "sakti", "kya", "kyaa", "kaisa", "kaisi", "kaise", "how", "what", "whats", "whatsthe", "bhi", "mujhe", "mujhko",
            "hume", "humein", "liye", "lie", "please_", "aap", "tum", "tu", "ye", "yeh", "yah", "woh", "wo", "vo", "us_", "is_", "iss", "uss",
            "turn", "set_", "make", "get", "give", "tell_", "with", "from", "ki_", "about", "my_", "up_", "something", "anything", "bhi",
            "करो", "कर", "करदो", "करना", "करिए", "कीजिए", "करें", "दो", "दे", "देना", "दीजिए", "दिजिए", "देदो", "ना", "न", "जरा", "थोड़ा", "थोडा", "थोड़ी",
            "अभी", "अब", "तो", "यार", "भाई", "सर", "जी", "हे", "हाय", "हेलो", "ओके", "एक", "कोई", "कुछ", "सा", "से", "है", "हैं", "हो", "होगा", "होगी",
            "था", "रहा", "रहे", "रही", "जाए", "जाओ", "लो", "लीजिए", "सकते", "क्या", "कैसा", "कैसी", "कैसे", "भी", "मुझे", "मुझको", "हमें", "लिए",
            "आप", "तुम", "ये", "यह", "वो", "वह", "इस", "उस", "इसको", "प्लीज", "प्लीज़", "बस", "कितना", "कितनी", "कितने", "kitna", "kitni", "kitne",
            "chahiye", "चाहिए", "le", "ले", "lao", "laao", "लाओ", "go", "jaao", "जाइए", "page", "पेज", "screen", "स्क्रीन", "baar", "बार", "rahega", "rahegi", "रहेगा", "रहेगी", "hogi_", "lagega", "लगेगा", "hua", "हुआ", "hui", "हुई", "baat", "जल्दी"
        )
    )

    /** folded word -> concept */
    val map: Map<String, String> = HashMap<String, String>().apply {
        for ((concept, words) in raw) for (w in words) {
            val k = Text.fold(w)
            if (!containsKey(k)) put(k, concept)
        }
    }

    fun concept(token: String): String? {
        val f = Text.fold(token)
        if (f.matches(Regex("\\d+([.:]\\d+)?"))) return C.NUM
        return map[f]
    }

    /** Words that describe a mood/genre: they stay in the search query but mark it as a playlist-style request. */
    val MOODS: Set<String> = setOf(
        "romantic", "romance", "sad", "dard", "dardbhare", "dardbhara", "old", "purane", "purana", "puraane", "latest", "new", "naye", "naya",
        "party", "dance", "workout", "gym", "lofi", "lo-fi", "bhakti", "bhajan", "bhajans", "devotional", "sleep", "relax", "relaxing", "chill",
        "hit", "hits", "top", "best", "superhit", "evergreen", "classic", "classical", "90s", "80s", "70s", "2000s", "trending", "slowed", "reverb",
        "love", "happy", "motivational", "sufi", "ghazal", "qawwali", "punjabi", "bollywood", "odia", "hindi", "english", "tamil", "telugu",
        "bhojpuri", "marathi", "bengali", "kannada", "malayalam", "gujarati", "haryanvi", "rajasthani", "instrumental",
        "रोमांटिक", "सैड", "दर्द", "दर्दभरे", "पुराने", "पुराना", "नए", "नये", "लेटेस्ट", "पार्टी", "डांस", "भक्ति", "भजन", "लोफी", "हिट", "हिट्स",
        "सुपरहिट", "बॉलीवुड", "ओड़िया", "ओडिया", "उड़िया", "हिंदी", "पंजाबी", "भोजपुरी", "सूफी", "ग़ज़ल", "गजल", "कव्वाली", "मराठी", "बंगाली", "ट्रेंडिंग"
    ).map { Text.fold(it) }.toSet()

    /** Content that is "a song-like thing" by itself (no need to append "song"). */
    val SELF_DESCRIBING: Set<String> = setOf(
        "chalisa", "aarti", "arti", "bhajan", "bhajans", "mantra", "stotram", "stotra", "jukebox", "mashup", "remix", "lyrics", "podcast",
        "चालीसा", "आरती", "भजन", "मंत्र", "स्तोत्र", "मैशअप", "रीमिक्स"
    ).map { Text.fold(it) }.toSet()

    val NUMBER_WORDS: Map<String, Int> = mapOf(
        "ek" to 1, "एक" to 1, "one" to 1, "do" to 2, "दो" to 2, "two" to 2, "teen" to 3, "tin" to 3, "तीन" to 3, "three" to 3,
        "char" to 4, "chaar" to 4, "चार" to 4, "four" to 4, "paanch" to 5, "panch" to 5, "pach" to 5, "पांच" to 5, "पाँच" to 5, "five" to 5,
        "chhe" to 6, "chhah" to 6, "che" to 6, "chah" to 6, "छह" to 6, "छः" to 6, "छे" to 6, "six" to 6,
        "saat" to 7, "सात" to 7, "seven" to 7, "aath" to 8, "आठ" to 8, "eight" to 8, "nau" to 9, "नौ" to 9, "nine" to 9,
        "das" to 10, "दस" to 10, "ten" to 10, "gyarah" to 11, "gyara" to 11, "ग्यारह" to 11, "eleven" to 11,
        "barah" to 12, "baarah" to 12, "बारह" to 12, "twelve" to 12, "pandrah" to 15, "पंद्रह" to 15, "fifteen" to 15,
        "bees" to 20, "बीस" to 20, "twenty" to 20, "pachchis" to 25, "पच्चीस" to 25, "tees" to 30, "तीस" to 30, "thirty" to 30,
        "chalis" to 40, "चालीस" to 40, "forty" to 40, "pachas" to 50, "पचास" to 50, "fifty" to 50, "sau" to 100, "सौ" to 100, "hundred" to 100,
        "aadha" to -30, "आधा" to -30, "half" to -30, "dedh" to -90, "डेढ़" to -90, "डेढ" to -90
    ).mapKeys { Text.fold(it.key) }

    val ORDINALS: Map<String, Int> = mapOf(
        "pehla" to 1, "pahla" to 1, "pehle" to 1, "pahle" to 1, "first" to 1, "पहला" to 1, "पहले" to 1, "पहली" to 1, "1st" to 1, "pehli" to 1,
        "dusra" to 2, "doosra" to 2, "dusre" to 2, "doosre" to 2, "second" to 2, "दूसरा" to 2, "दूसरे" to 2, "दूसरी" to 2, "2nd" to 2, "dusri" to 2,
        "teesra" to 3, "tisra" to 3, "teesre" to 3, "third" to 3, "तीसरा" to 3, "तीसरे" to 3, "तीसरी" to 3, "3rd" to 3, "teesri" to 3,
        "chautha" to 4, "fourth" to 4, "चौथा" to 4, "चौथे" to 4, "4th" to 4,
        "paanchva" to 5, "panchva" to 5, "fifth" to 5, "पांचवा" to 5, "पाँचवाँ" to 5, "5th" to 5
    ).mapKeys { Text.fold(it.key) }
}
