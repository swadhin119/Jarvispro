package com.swadhin.jarvis.nlu

/** One token: original spelling, folded spelling, concept (null = unknown word, e.g. a name or song title). */
data class Tok(val orig: String, val f: String, val c: String?)

object TimeParser {

    private val CLOCK = Regex("^(\\d{1,2})[:.](\\d{2})$")

    /** Positive number at token i (digits or a Hindi/English number word), else null. */
    fun number(t: Tok?): Int? {
        if (t == null) return null
        t.f.toIntOrNull()?.let { return it }
        val v = Lexicon.NUMBER_WORDS[t.f] ?: return null
        return if (v > 0) v else null
    }

    /** "aadha" / "dedh" style multipliers (0.5, 1.5) */
    private fun fraction(t: Tok?): Double? {
        if (t == null) return null
        return when (Lexicon.NUMBER_WORDS[t.f]) {
            -30 -> 0.5
            -90 -> 1.5
            else -> null
        }
    }

    /** "30 second", "1 minute", "dedh minute", "aadha ghanta", "2 ghante 10 minute" -> seconds */
    fun duration(toks: List<Tok>): Int? {
        var total = 0.0
        var found = false
        for (i in toks.indices) {
            val unit = when (toks[i].c) {
                C.SEC -> 1
                C.MIN -> 60
                C.HOUR -> 3600
                else -> continue
            }
            val prev = toks.getOrNull(i - 1)
            val n: Double? = number(prev)?.toDouble() ?: fraction(prev) ?: prev?.f?.toDoubleOrNull()
            if (n != null) { total += n * unit; found = true }
            else if (prev == null || prev.c != C.NUM) { total += unit; found = true } // "ek minute" handled above; bare "minute" = 1
        }
        return if (found && total > 0) total.toInt() else null
    }

    /**
     * Clock time for alarms: "subah 6 baje", "shaam 7:30", "saade 5 baje", "raat 10", "6 am", "7.15 pm".
     * Returns (hour24, minute) or null.
     */
    fun clock(toks: List<Tok>): Pair<Int, Int>? {
        var hour = -1
        var minute = 0
        var idx = -1
        for (i in toks.indices) {
            val m = CLOCK.find(toks[i].f)
            if (m != null) {
                hour = m.groupValues[1].toInt(); minute = m.groupValues[2].toInt(); idx = i; break
            }
        }
        if (hour < 0) {
            // number followed by baje / am / pm, or preceded by "at"/saade/sawa/paune
            for (i in toks.indices) {
                val n = number(toks[i]) ?: continue
                if (n !in 1..24) continue
                val next = toks.getOrNull(i + 1)?.c
                val prev = toks.getOrNull(i - 1)
                val strong = next == C.BAJE || next == C.AM || next == C.PM ||
                    prev?.c == C.HALF_PAST || prev?.c == C.QUARTER_PAST || prev?.c == C.QUARTER_TO ||
                    prev?.f == "at" || prev?.c in setOf(C.MORNING, C.NOON, C.EVENING, C.NIGHT)
                val notDuration = next !in setOf(C.SEC, C.MIN, C.HOUR)
                if ((strong || toks[i].c == C.NUM) && notDuration) {
                    hour = n; idx = i
                    if (strong) break
                }
            }
        }
        if (hour < 0) return null
        val prev = toks.getOrNull(idx - 1)?.c
        when (prev) {
            C.HALF_PAST -> minute = 30
            C.QUARTER_PAST -> minute = 15
            C.QUARTER_TO -> { minute = 45; hour = if (hour == 1) 12 else hour - 1 }
        }
        // minutes after "baje": "6 baje 20 minute"
        val after = toks.drop(idx + 1)
        val bajeAt = after.indexOfFirst { it.c == C.BAJE }
        if (bajeAt >= 0) {
            val mTok = after.getOrNull(bajeAt + 1)
            val unit = after.getOrNull(bajeAt + 2)?.c
            val mm = number(mTok)
            if (mm != null && mm in 1..59 && unit == C.MIN) minute = mm
        }
        val has = { c: String -> toks.any { it.c == c } }
        hour = when {
            has(C.PM) -> if (hour < 12) hour + 12 else hour
            has(C.AM) -> if (hour == 12) 0 else hour
            has(C.MORNING) -> if (hour == 12) 0 else hour
            has(C.NOON) -> if (hour in 1..5) hour + 12 else hour
            has(C.EVENING) -> if (hour < 12) hour + 12 else hour
            has(C.NIGHT) -> when { hour == 12 -> 0; hour in 6..11 -> hour + 12; else -> hour }
            else -> hour
        }
        if (hour == 24) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return Pair(hour, minute)
    }
}
