package com.swadhin.jarvis.nlu

/**
 * Everything Jarvis says for locally handled commands, in three registers.
 * Hindi / Hinglish replies keep Hindi words in Devanagari (so the Hindi TTS voice pronounces them correctly)
 * and English words in Latin script, which is how Indians actually speak.
 */
class Replies(private val sir: String = "Sir") {

    private fun t(l: Lang, en: String, hing: String, hi: String) = when (l) {
        Lang.ENGLISH -> en
        Lang.HINGLISH -> hing
        Lang.HINDI -> hi
    }

    // ---- persona ----
    fun wake(l: Lang) = t(l, "Yes, $sir?", "जी $sir?", "जी $sir, बताइए?")
    fun whatDoing(l: Lang) = t(l, "Waiting for your instructions, $sir.", "$sir, मैं आपके instructions का इंतज़ार कर रहा हूँ।", "$sir, मैं आपके आदेश का इंतज़ार कर रहा हूँ।")
    fun howAreYou(l: Lang) = t(l, "All systems running perfectly, $sir. How can I help?", "सब systems perfect चल रहे हैं, $sir. बताइए क्या करूँ?", "मैं बिल्कुल ठीक हूँ, $sir. बताइए क्या सेवा करूँ?")
    fun greeting(l: Lang) = t(l, "Hello $sir. What can I do for you?", "Hello $sir. बताइए क्या करना है?", "नमस्ते $sir. बताइए, क्या करूँ?")
    fun thanks(l: Lang) = t(l, "Always a pleasure, $sir.", "Always a pleasure, $sir.", "आपकी सेवा में हमेशा हाज़िर हूँ, $sir.")
    fun whoAreYou(l: Lang) = t(l, "I am JARVIS, your personal assistant, $sir.", "मैं JARVIS हूँ, $sir. आपका personal assistant.", "मैं JARVIS हूँ, $sir. आपका निजी सहायक।")
    fun okCancel(l: Lang) = t(l, "Alright, $sir.", "ठीक है, $sir.", "ठीक है, $sir.")
    fun notUnderstood(l: Lang) = t(l, "Sorry $sir, I didn't catch that. Please say it again.", "Sorry $sir, समझ नहीं आया. एक बार फिर बोलिए।", "माफ़ कीजिए $sir, समझ नहीं पाया। एक बार फिर बोलिए।")
    fun online(l: Lang) = t(l, "JARVIS online, $sir.", "JARVIS online है, $sir.", "JARVIS तैयार है, $sir.")

    // ---- network / AI fallback ----
    fun noInternet(l: Lang) = t(l, "$sir, there is no internet right now. Basic phone commands still work.", "$sir, अभी internet नहीं है। Basic phone commands फिर भी काम करेंगे।", "$sir, अभी इंटरनेट नहीं है। फ़ोन के basic काम फिर भी हो जाएँगे।")
    fun aiError(l: Lang) = t(l, "$sir, I couldn't reach my AI brain. Please try again.", "$sir, AI से connect नहीं हो पाया। थोड़ी देर में फिर try कीजिए।", "$sir, AI से संपर्क नहीं हो पाया। थोड़ी देर बाद फिर कोशिश कीजिए।")
    fun noApiKey(l: Lang) = t(l, "$sir, please add the Gemini API key in the app for this request.", "$sir, इसके लिए app में Gemini API key डालिए।", "$sir, इसके लिए ऐप में Gemini API key डालिए।")

    // ---- music ----
    fun askSong(l: Lang) = t(l, "Certainly, $sir. Which song would you like to hear?", "Certainly, $sir. आप कौनसा गाना सुनना चाहेंगे?", "ज़रूर $sir. आप कौनसा गाना सुनना चाहेंगे?")
    fun searching(l: Lang, q: String, app: String) = t(l, "Certainly, $sir. Searching $app for $q.", "Certainly, $sir. मैं $app पर $q search कर रहा हूँ।", "ज़रूर $sir. मैं $app पर $q खोज रहा हूँ।")
    fun nowPlaying(l: Lang, title: String, app: String) = t(l, "Playing $title on $app, $sir.", "$sir, $app पर $title चल रहा है।", "$sir, $app पर $title चला दिया है।")
    fun opened(l: Lang, title: String, app: String) = t(l, "$sir, I've opened $title on $app. It should start in a moment.", "$sir, $app पर $title open कर दिया है।", "$sir, $app पर $title खोल दिया है।")
    fun resultsOnly(l: Lang, q: String, app: String) = t(l, "$sir, $app search results for $q are on screen. I couldn't start playback automatically, please tap the one you want.", "$sir, $app पर $q के results खुले हैं, पर playback अपने आप start नहीं हो पाया। जो गाना चाहिए उस पर tap कीजिए।", "$sir, $app पर $q के नतीजे खुले हैं, पर गाना अपने आप शुरू नहीं हो पाया। जो चाहिए उस पर tap कीजिए।")
    fun resultsShown(l: Lang, q: String, app: String) = t(l, "Here are the $app results for $q, $sir.", "$sir, $app पर $q के results दिखा रहा हूँ।", "$sir, $app पर $q के नतीजे दिखा रहा हूँ।")
    fun accOffForPlay(l: Lang) = t(l, "Turn on Phone Control (Accessibility) in the Jarvis app so I can start the video for you.", "Jarvis app में Phone Control (Accessibility) on कर दीजिए, तब मैं video खुद play कर दूँगा।", "Jarvis ऐप में Phone Control (Accessibility) चालू कर दीजिए, तब मैं वीडियो खुद चला दूँगा।")
    fun choices(l: Lang, titles: List<String>): String {
        val list = titles.mapIndexed { i, s -> "${i + 1}: $s" }.joinToString(". ")
        return t(l, "$sir, I found a few matches. $list. Which one should I play?", "$sir, कुछ options मिले हैं। $list. कौनसा चलाऊँ?", "$sir, कुछ गाने मिले हैं। $list. कौनसा चलाऊँ?")
    }
    fun badChoice(l: Lang, n: Int) = t(l, "Please say a number from 1 to $n, $sir.", "$sir, 1 से $n में से कोई number बोलिए।", "$sir, 1 से $n में से कोई नंबर बोलिए।")
    fun notInstalled(l: Lang, app: String) = t(l, "$sir, $app is not installed on this phone.", "$sir, इस phone में $app installed नहीं है।", "$sir, इस फ़ोन में $app नहीं है।")
    fun fallbackToYouTube(l: Lang, app: String) = t(l, "$app is not installed, so I'm using YouTube, $sir.", "$app installed नहीं है, इसलिए YouTube use कर रहा हूँ, $sir.", "$app नहीं है, इसलिए YouTube पर चला रहा हूँ, $sir.")
    fun noHistory(l: Lang) = t(l, "$sir, I don't have a recent song to go by yet. Which song should I play?", "$sir, अभी कोई recent गाना याद नहीं है। कौनसा गाना चलाऊँ?", "$sir, अभी कोई पिछला गाना याद नहीं है। कौनसा गाना चलाऊँ?")
    fun noFavourite(l: Lang) = t(l, "$sir, you haven't told me your favourite song yet. Which one is it?", "$sir, आपने अभी तक अपना favourite गाना नहीं बताया। कौनसा है?", "$sir, आपने अभी तक अपना पसंदीदा गाना नहीं बताया। कौनसा है?")
    fun favouriteSaved(l: Lang, title: String) = t(l, "Saved $title as your favourite, $sir.", "$title को आपका favourite बना दिया, $sir.", "$title को आपका पसंदीदा गाना बना दिया, $sir.")
    fun moreBy(l: Lang, artist: String) = t(l, "Certainly, $sir. More songs by $artist.", "Certainly, $sir. $artist के और गाने चला रहा हूँ।", "ज़रूर $sir. $artist के और गाने चला रहा हूँ।")

    // ---- media controls ----
    fun paused(l: Lang) = t(l, "Paused, $sir.", "Pause कर दिया, $sir.", "रोक दिया, $sir.")
    fun resumed(l: Lang) = t(l, "Resuming, $sir.", "Resume कर दिया, $sir.", "फिर से चालू कर दिया, $sir.")
    fun stopped(l: Lang) = t(l, "Music stopped, $sir.", "Music बंद कर दिया, $sir.", "गाना बंद कर दिया, $sir.")
    fun next(l: Lang, title: String?) = if (title.isNullOrBlank()) t(l, "Next one, $sir.", "अगला गाना, $sir.", "अगला गाना चला दिया, $sir.")
        else t(l, "Next up: $title, $sir.", "अगला: $title, $sir.", "अगला गाना: $title, $sir.")
    fun previous(l: Lang) = t(l, "Going back, $sir.", "पिछला गाना, $sir.", "पिछला गाना चला दिया, $sir.")
    fun replay(l: Lang) = t(l, "Playing it again from the start, $sir.", "फिर से शुरू से चला रहा हूँ, $sir.", "दोबारा शुरू से चला रहा हूँ, $sir.")
    fun seek(l: Lang, secs: Int): String {
        val a = kotlin.math.abs(secs)
        val en = if (a % 60 == 0 && a >= 60) "${a / 60} minute${if (a >= 120) "s" else ""}" else "$a seconds"
        val hi = if (a % 60 == 0 && a >= 60) "${a / 60} मिनट" else "$a सेकंड"
        return if (secs >= 0) t(l, "Skipped ahead $en, $sir.", "$hi आगे कर दिया, $sir.", "$hi आगे कर दिया, $sir.")
        else t(l, "Went back $en, $sir.", "$hi पीछे कर दिया, $sir.", "$hi पीछे कर दिया, $sir.")
    }
    fun noMedia(l: Lang) = t(l, "$sir, nothing is playing right now.", "$sir, अभी कुछ play नहीं हो रहा।", "$sir, अभी कुछ नहीं चल रहा।")
    fun unsupported(l: Lang, app: String, what: String) = t(l,
        "$sir, $app isn't offering the $what control right now, so I can't do that from here.",
        "$sir, $app अभी $what का control नहीं दे रहा, इसलिए ये मैं यहाँ से नहीं कर सकता।",
        "$sir, $app अभी $what की सुविधा नहीं दे रहा, इसलिए यह मैं नहीं कर सकता।")
    fun needNotifAccess(l: Lang) = t(l,
        "$sir, for this control please turn on Notification Access for Jarvis in the app.",
        "$sir, इस control के लिए Jarvis app में Notification Access on कर दीजिए।",
        "$sir, इसके लिए Jarvis ऐप में Notification Access चालू कर दीजिए।")

    // ---- volume / device ----
    fun volume(l: Lang, op: VolOp, pct: Int) = when (op) {
        VolOp.UP -> t(l, "Volume increased to $pct percent, $sir.", "Volume increase कर दिया, $sir. अब $pct percent है।", "आवाज़ बढ़ा दी, $sir. अब $pct प्रतिशत है।")
        VolOp.DOWN -> t(l, "Volume lowered to $pct percent, $sir.", "Volume कम कर दिया, $sir. अब $pct percent है।", "आवाज़ कम कर दी, $sir. अब $pct प्रतिशत है।")
        VolOp.SET -> t(l, "Volume set to $pct percent, $sir.", "Volume $pct percent कर दिया, $sir.", "आवाज़ $pct प्रतिशत कर दी, $sir.")
        VolOp.MAX -> t(l, "Volume at maximum, $sir.", "Volume full कर दिया, $sir.", "आवाज़ पूरी बढ़ा दी, $sir.")
        VolOp.MUTE -> t(l, "Muted, $sir.", "Mute कर दिया, $sir.", "आवाज़ बंद कर दी, $sir.")
        VolOp.UNMUTE -> t(l, "Unmuted, $sir.", "Unmute कर दिया, $sir.", "आवाज़ वापस चालू कर दी, $sir.")
    }
    fun brightness(l: Lang, pct: Int) = t(l, "Brightness set to $pct percent, $sir.", "Brightness $pct percent कर दी, $sir.", "रोशनी $pct प्रतिशत कर दी, $sir.")
    fun torch(l: Lang, on: Boolean) = if (on) t(l, "Flashlight on, $sir.", "Torch on कर दी, $sir.", "टॉर्च जला दी, $sir.")
        else t(l, "Flashlight off, $sir.", "Light बंद कर दी, $sir.", "लाइट बंद कर दी, $sir.")
    fun failed(l: Lang, why: String) = t(l, "$sir, that didn't work: $why", "$sir, ये नहीं हो पाया: $why", "$sir, यह नहीं हो पाया: $why")
    fun permission(l: Lang, what: String) = t(l, "$sir, please turn on $what for Jarvis in the app.", "$sir, Jarvis app में $what permission on कर दीजिए।", "$sir, Jarvis ऐप में $what की अनुमति चालू कर दीजिए।")

    // ---- apps ----
    fun openedApp(l: Lang, app: String) = t(l, "Opening $app, $sir.", "$app open कर रहा हूँ, $sir.", "$app खोल रहा हूँ, $sir.")
    fun appMissing(l: Lang, app: String) = t(l, "$sir, I couldn't find an app called $app.", "$sir, $app नाम का app नहीं मिला।", "$sir, $app नाम का ऐप नहीं मिला।")
    fun webSearch(l: Lang, q: String) = t(l, "Searching Google for $q, $sir.", "Google पर $q search कर रहा हूँ, $sir.", "Google पर $q खोज रहा हूँ, $sir.")
    fun askSearch(l: Lang) = t(l, "What should I search for, $sir?", "क्या search करूँ, $sir?", "क्या खोजूँ, $sir?")
    fun chromeGoogle(l: Lang) = t(l, "Google is open in Chrome, $sir. What should I search?", "Chrome में Google खोल दिया, $sir. क्या search करूँ?", "Chrome में Google खोल दिया, $sir. क्या खोजूँ?")
    fun maps(l: Lang, q: String, nav: Boolean) = if (nav) t(l, "Starting navigation to $q, $sir.", "$q के लिए navigation start कर रहा हूँ, $sir.", "$q का रास्ता दिखा रहा हूँ, $sir.")
        else t(l, "Showing $q on Maps, $sir.", "Maps पर $q दिखा रहा हूँ, $sir.", "नक्शे पर $q दिखा रहा हूँ, $sir.")

    // ---- calls / messages ----
    fun calling(l: Lang, who: String) = t(l, "Calling $who, $sir.", "$who को call लगा रहा हूँ, $sir.", "$who को फ़ोन लगा रहा हूँ, $sir.")
    fun contactMissing(l: Lang, who: String) = t(l, "$sir, I couldn't find $who in your contacts.", "$sir, contacts में $who नहीं मिला।", "$sir, संपर्कों में $who नहीं मिला।")
    fun askWhatsAppTo(l: Lang) = t(l, "Sure $sir, who should I send it to?", "ज़रूर $sir, किसको message भेजना है?", "ज़रूर $sir, किसको संदेश भेजना है?")
    fun askWhatsAppMsg(l: Lang, who: String) = t(l, "What should I tell $who, $sir?", "$who को क्या message भेजूँ, $sir?", "$who को क्या संदेश भेजूँ, $sir?")
    fun whatsappSent(l: Lang, who: String) = t(l, "Message sent to $who on WhatsApp, $sir.", "$who को WhatsApp पर message भेज दिया, $sir.", "$who को WhatsApp पर संदेश भेज दिया, $sir.")
    fun whatsappTyped(l: Lang, who: String) = t(l, "$sir, the message for $who is typed in WhatsApp, please press send.", "$sir, $who के लिए message WhatsApp में type कर दिया है, बस send दबाइए।", "$sir, $who के लिए संदेश WhatsApp में लिख दिया है, बस भेजें दबाइए।")

    // ---- alarm / timer ----
    fun alarm(l: Lang, h: Int, m: Int): String {
        val t12 = "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
        val part = when (h) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
        val hiTime = "$part ${if (h % 12 == 0) 12 else h % 12}${if (m > 0) ":%02d".format(m) else ""} बजे"
        return t(l, "Alarm set for $t12, $sir.", "$hiTime का alarm लगा दिया, $sir.", "$hiTime का अलार्म लगा दिया, $sir.")
    }
    fun askAlarmTime(l: Lang) = t(l, "What time should I set the alarm for, $sir?", "किस time का alarm लगाऊँ, $sir?", "कितने बजे का अलार्म लगाऊँ, $sir?")
    fun timer(l: Lang, secs: Int): String {
        val en = if (secs >= 60 && secs % 60 == 0) "${secs / 60} minute" else "$secs second"
        val hi = if (secs >= 60 && secs % 60 == 0) "${secs / 60} मिनट" else "$secs सेकंड"
        return t(l, "Timer started for $en${if (en.startsWith("1 ")) "" else "s"}, $sir.", "$hi का timer start कर दिया, $sir.", "$hi का टाइमर शुरू कर दिया, $sir.")
    }
    fun askTimer(l: Lang) = t(l, "For how long, $sir?", "कितनी देर का timer, $sir?", "कितनी देर का टाइमर लगाऊँ, $sir?")

    // ---- info ----
    fun time(l: Lang, h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        val part = when (h) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
        return t(l, "It's %d:%02d %s, $sir.".format(h12, m, if (h < 12) "AM" else "PM"), "$sir, अभी $part के $h12 बजकर $m मिनट हुए हैं।", "$sir, अभी $part के $h12 बजकर $m मिनट हुए हैं।")
    }
    fun date(l: Lang, en: String, hi: String) = t(l, "Today is $en, $sir.", "$sir, आज $hi है।", "$sir, आज $hi है।")
    fun battery(l: Lang, pct: Int, charging: Boolean) = t(l,
        "Battery is at $pct percent${if (charging) " and charging" else ""}, $sir.",
        "$sir, battery $pct percent है${if (charging) ", charging चल रही है" else ""}।",
        "$sir, बैटरी $pct प्रतिशत है${if (charging) ", चार्ज हो रही है" else ""}।")
    fun weather(l: Lang, place: String, desc: String, temp: String, feels: String, rain: Int, tomorrow: Boolean, max: String, min: String): String {
        val rainEn = if (rain >= 40) " Chance of rain is $rain percent." else ""
        val rainHi = if (rain >= 40) " बारिश की संभावना $rain प्रतिशत है।" else ""
        return if (tomorrow) t(l,
            "Tomorrow in $place: $desc, between $min and $max degrees.$rainEn",
            "$sir, कल $place में $desc रहेगा, temperature $min से $max degree.$rainHi",
            "$sir, कल $place में $desc रहेगा, तापमान $min से $max डिग्री।$rainHi")
        else t(l,
            "$sir, it's $temp degrees in $place with $desc, feels like $feels.$rainEn",
            "$sir, $place में अभी $temp degree है, मौसम $desc है, feels like $feels.$rainHi",
            "$sir, $place में अभी $temp डिग्री है और मौसम $desc है।$rainHi")
    }
    fun weatherFallback(l: Lang) = t(l, "$sir, I couldn't fetch the weather, so I've opened it on Google.", "$sir, weather fetch नहीं हो पाया, Google पर खोल दिया है।", "$sir, मौसम की जानकारी नहीं मिल पाई, Google पर खोल दिया है।")
}
