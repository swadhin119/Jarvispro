package com.swadhin.jarvis.nlu

/** Script-independent comparison key: "राहुल" and "Rahul" both become "rahul". */
object Phonetic {
    fun key(s: String): String {
        var x = Translit.toLatin(s).lowercase()
        x = x.replace(Regex("[^a-z ]"), " ")
        x = x.replace("aa", "a").replace("ee", "i").replace("oo", "u").replace("ph", "f").replace("w", "v")
            .replace("z", "j").replace("sh", "s").replace("ou", "u").replace("q", "k").replace("y ", "i ")
        x = x.replace(Regex("(.)\\1+"), "$1")
        return x.replace(Regex("\\s+"), " ").trim()
    }

    fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    }
}

/** Finds the best contact for a spoken name, across scripts and common relation words. */
object ContactMatcher {

    private val ALIASES: List<Set<String>> = listOf(
        setOf("mummy", "mumy", "mom", "mommy", "maa", "ma", "mumma", "mother", "ammi", "mammi", "mamma", "mataji"),
        setOf("papa", "dad", "daddy", "pitaji", "father", "abbu", "pappa", "baba"),
        setOf("bhaiya", "bhaiyya", "bhai", "brother", "bro"),
        setOf("didi", "di", "sister", "behen", "dii"),
        setOf("wife", "biwi", "patni", "jaan"),
        setOf("husband", "pati")
    ).map { g -> g.map { Phonetic.key(it) }.toSet() }

    /** Returns the best matching name from [names] or null. */
    fun best(spoken: String, names: List<String>): String? {
        val q = Phonetic.key(spoken)
        if (q.isEmpty()) return null
        val alternatives = HashSet<String>().apply {
            add(q)
            ALIASES.firstOrNull { q in it }?.let { addAll(it) }
        }
        var bestName: String? = null
        var bestScore = 0.0
        for (n in names) {
            val k = Phonetic.key(n)
            if (k.isEmpty()) continue
            val words = k.split(' ')
            for (alt in alternatives) {
                val whole = Phonetic.similarity(alt, k)
                val word = words.maxOf { Phonetic.similarity(alt, it) }
                val prefix = if (k.startsWith(alt) && alt.length >= 3) 0.92 else 0.0
                val score = maxOf(whole, word * 0.97, prefix)
                if (score > bestScore) { bestScore = score; bestName = n }
            }
        }
        return if (bestScore >= 0.75) bestName else null
    }
}

/** Ignores speech that is just Jarvis hearing its own voice from the speaker. */
object Echo {
    fun isEcho(heard: String, lastSpoken: String, msSinceSpoken: Long): Boolean {
        if (lastSpoken.isBlank() || msSinceSpoken > 6000) return false
        val a = words(heard)
        val b = words(lastSpoken)
        if (a.size < 2 || b.isEmpty()) return false
        val inter = a.count { it in b }
        return inter.toDouble() / a.size >= 0.6
    }

    private fun words(s: String): Set<String> =
        Phonetic.key(s).split(' ').filter { it.length > 1 }.toSet()
}

/** Parses YouTube's accessibility descriptions of search results and decides whether to ask "which one?". */
object YouTubeResults {

    data class Item(val title: String, val channel: String, val isAd: Boolean, val isShort: Boolean)

    private val DURATION = Regex(
        "^(\\d+ (hours?|minutes?|seconds?)(, )?)+$|^\\d{1,2}:\\d{2}(:\\d{2})?$|मिनट|सेकंड|घंटे|घंटा",
        RegexOption.IGNORE_CASE
    )
    private val AD = Regex("(^|\\s|-)(sponsored|ad|प्रायोजित|विज्ञापन)(\\s|$|·|-)", RegexOption.IGNORE_CASE)

    /** e.g. "Arijit Singh: Tum Hi Ho - 4 minutes, 22 seconds - Go to channel - T-Series - 1.2B views - 11 years ago - play video" */
    fun parse(desc: String): Item? {
        val parts = desc.split(" - ").map { it.trim() }
        if (parts.size < 2) return null
        val d = parts.indexOfFirst { DURATION.containsMatchIn(it) }
        val playVideo = desc.contains("play video", true) || desc.contains("वीडियो चलाएं", true) || desc.contains("play short", true)
        if (d <= 0 && !playVideo) return null
        val title = (if (d > 0) parts.subList(0, d) else parts.subList(0, 1)).joinToString(" - ").trim()
        if (title.length < 2) return null
        val ch = parts.indexOfFirst { it.equals("Go to channel", true) || it.contains("चैनल पर जाएं") }
        val channel = if (ch >= 0) parts.getOrNull(ch + 1).orEmpty() else parts.getOrNull(d + 1).orEmpty()
        val isShort = desc.contains("play short", true) || desc.contains("#shorts", true)
        val isAd = AD.containsMatchIn(desc)
        return Item(title, channel, isAd, isShort)
    }

    private fun core(title: String): String {
        var t = title.lowercase()
        for (sep in listOf(" | ", " - ", "(", "[", " : ", " ft", " feat", "｜")) {
            val i = t.indexOf(sep)
            if (i > 0) t = t.substring(0, i)
        }
        t = t.substringAfter(": ", t)
        return t.replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Ask the user only when the request was short and the top results are clearly *different* songs that
     * all match it ("love" -> "Love Me Like You Do", "Love Dose", "Love Story"). Same song with different uploads -> no question.
     */
    fun needsChoice(query: String, titles: List<String>, artistGiven: Boolean): Boolean {
        if (artistGiven || titles.size < 2) return false
        val q = core(query).replace(Regex("\\b(song|songs|gana|gaana|video)\\b"), "").trim()
        val qWords = q.split(' ').filter { it.isNotEmpty() }
        if (qWords.isEmpty() || qWords.size > 2) return false
        val cores = titles.take(3).map { core(it) }.filter { it.isNotEmpty() }
        if (cores.any { it == q }) return false
        val matching = cores.filter { c -> qWords.all { w -> c.contains(w) } }.distinct()
        return matching.size >= 2
    }
}
