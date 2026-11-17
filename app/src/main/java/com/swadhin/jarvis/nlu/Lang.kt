package com.swadhin.jarvis.nlu

/**
 * Which language the user is speaking. Decides how Jarvis replies and which TTS voice speaks.
 *  HINDI    – mostly Devanagari (what Google's hi-IN recogniser returns for Hindi speech)
 *  HINGLISH – Roman script with Hindi words ("volume badha do")
 *  ENGLISH  – plain English
 */
enum class Lang { HINDI, HINGLISH, ENGLISH }

object LanguageDetector {

    /** Roman-script words that only make sense in Hindi/Hinglish. Deliberately excludes English look-alikes (do, to, me, hi, par…). */
    private val HINGLISH_MARKERS: Set<String> = setOf(
        "karo", "kar", "kardo", "karna", "kariye", "kijiye", "hai", "hain", "kya", "kyaa", "ka", "ke", "ki", "ko", "mein", "mujhe", "mera",
        "meri", "mere", "chalao", "chala", "bajao", "baja", "sunao", "kholo", "khol", "gana", "gaana", "gane", "gaane", "aur", "nahi", "nahin",
        "haan", "kaise", "kaisa", "kaisi", "bhejo", "lagao", "badhao", "badha", "kam", "band", "dena", "aaj", "wala", "wale", "wali", "baje",
        "subah", "shaam", "raat", "abhi", "ab", "se", "ho", "raha", "rahe", "rahi", "kuch", "thoda", "bhi", "accha", "acha", "theek", "thik",
        "agla", "agle", "pichla", "dobara", "phir", "aage", "peeche", "ruko", "roko", "mausam", "utha", "uthana", "jagana", "batao", "dikhao",
        "yaar", "bhai", "zara", "jaldi", "kitna", "kitne", "kitni", "tum", "aap", "hum", "kaun", "kahan", "kab", "kyun", "dhundo", "khojo",
        "chalu", "bajado", "chalado", "kardijiye", "dijiye", "sakte", "chahiye", "wahi", "dusra", "pehla", "teesra", "awaaz", "awaz", "par",
        "pe", "tha", "thi", "hoga", "hogi", "rakho", "banao", "suno", "bolo", "likho", "dekho", "dhanyavad", "shukriya", "namaste"
    )

    private val ENGLISH_ONLY_MARKERS: Set<String> = setOf(
        "the", "please", "what", "is", "are", "my", "you", "your", "play", "open", "search", "set", "turn", "call", "send", "next",
        "previous", "how", "can", "could", "would", "will", "weather", "today", "tomorrow", "song", "songs", "volume", "and", "for", "with"
    )

    /** Returns null when the text has no evidence either way (e.g. just a name) – caller keeps the previous language. */
    fun detect(text: String): Lang? {
        var deva = 0
        var latin = 0
        for (c in text) {
            if (Text.isDevanagari(c)) deva++ else if (c in 'a'..'z' || c in 'A'..'Z') latin++
        }
        if (deva == 0 && latin == 0) return null
        if (deva > 0 && deva * 10 >= (deva + latin) * 3) return Lang.HINDI

        val words = Text.tokens(text).map { Text.fold(it) }.filter { it.any { c -> c in 'a'..'z' } }
        val wakeless = words.filter { Lexicon.concept(it) != C.WAKE }
        if (wakeless.isEmpty()) return null
        val hindiHits = wakeless.count { it in HINGLISH_MARKERS }
        val englishHits = wakeless.count { it in ENGLISH_ONLY_MARKERS }
        if (hindiHits >= 1 && hindiHits >= englishHits / 2) return Lang.HINGLISH
        if (deva > 0) return Lang.HINGLISH
        if (englishHits >= 1) return Lang.ENGLISH
        // Only unknown words (names, titles): no evidence.
        return if (wakeless.size >= 4) Lang.ENGLISH else null
    }
}
