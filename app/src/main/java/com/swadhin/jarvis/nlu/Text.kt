package com.swadhin.jarvis.nlu

/**
 * Text normalisation shared by the whole language layer.
 * Pure Kotlin (no Android imports) so it can be unit-tested on the JVM.
 */
object Text {

    private const val NUKTA = '़'
    private const val CHANDRABINDU = 'ँ'
    private const val ANUSVARA = 'ं'

    fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

    /** Lowercase, Devanagari digits -> ASCII, nukta/ZWJ removed, chandrabindu -> anusvara. */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            when {
                ch == NUKTA || ch == '‌' || ch == '‍' -> {}
                ch == CHANDRABINDU -> sb.append(ANUSVARA)
                ch in '०'..'९' -> sb.append('0' + (ch - '०'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val PHRASES = listOf(
        "you tube" to "youtube", "यू ट्यूब" to "यूट्यूब", "यु ट्यूब" to "यूट्यूब",
        "whats app" to "whatsapp", "what's app" to "whatsapp", "what's up" to "whatsapp",
        "व्हाट्स ऐप" to "व्हाट्सऐप", "व्हाट्स एप" to "व्हाट्सएप", "वाट्स एप" to "वाट्सएप",
        "insta gram" to "instagram", "flash light" to "flashlight", "o'clock" to "oclock",
        "it's" to "its", "what's" to "whats", "let's" to "lets", "i'm" to "im",
        "play list" to "playlist", "प्ले लिस्ट" to "प्लेलिस्ट", "un mute" to "unmute",
        "you tube music" to "youtube music", "yt music" to "youtube music"
    )

    /** Splits into tokens, each keeping the original spelling (for queries / names). */
    fun tokens(raw: String): List<String> {
        var s = raw
        for ((a, b) in PHRASES) s = s.replace(a, b, ignoreCase = true)
        // Devanagari digits -> ASCII in the original as well (YouTube / AlarmClock want digits).
        s = buildString { for (ch in s) append(if (ch in '०'..'९') '0' + (ch - '०') else ch) }
        // "30sec" / "10s" -> "30 sec"
        s = s.replace(Regex("(\\d)(sec|secs|second|seconds|min|mins|minute|minutes|s|m|h|hr|%)\\b", RegexOption.IGNORE_CASE), "$1 $2")
        s = s.replace(Regex("[,!?।॥|\"“”‘’()\\[\\]{}<>;]"), " ")
        // a full stop is noise unless it is a decimal point
        s = s.replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        return s.split(Regex("\\s+")).map { it.trim('\'', '-', '_', '*') }.filter { it.isNotEmpty() }
    }

    fun norm(token: String): String = fold(token)
}

/** Very small Devanagari -> Latin transliterator (good enough for names: राहुल -> rahul). */
object Translit {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l"
    )
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o", 'ऍ' to "e"
    )
    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e",
        'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ृ' to "ri", 'ॉ' to "o", 'ॅ' to "e"
    )

    fun toLatin(input: String): String {
        val s = Text.fold(input)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val cons = consonants[c]
            when {
                cons != null -> {
                    out.append(cons)
                    val next = s.getOrNull(i + 1)
                    when {
                        next == '्' -> i++                       // virama: no vowel
                        next != null && matras.containsKey(next) -> { out.append(matras[next]); i++ }
                        next != null && Text.isDevanagari(next) -> out.append('a') // inherent vowel
                        else -> {}                                 // schwa deletion at word end
                    }
                }
                vowels.containsKey(c) -> out.append(vowels[c])
                c == 'ं' -> out.append('n')
                c == 'ः' -> out.append('h')
                matras.containsKey(c) || c == '्' -> {}
                else -> out.append(c)
            }
            i++
        }
        // "rahul" not "raahul"; collapse inherent-a before n/m at word end handled by schwa rule
        return out.toString()
    }
}
