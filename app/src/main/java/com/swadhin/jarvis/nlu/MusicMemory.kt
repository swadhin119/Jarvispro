package com.swadhin.jarvis.nlu

/**
 * Remembers recent song requests so "wahi gana dobara chalao", "is singer ke aur gane chalao" and
 * "mera favourite song play karo" work. Storage is a plain string (SharedPreferences on the phone).
 */
class MusicMemory(private val load: () -> String, private val save: (String) -> Unit) {

    data class Entry(
        val query: String,
        val artist: String,
        val title: String,     // what actually played (from the media session / YouTube), may be empty
        val app: String,
        val time: Long,
        val count: Int = 1
    )

    private var entries: MutableList<Entry> = decode(load())
    private var favourite: Entry? = null

    init {
        favourite = entries.firstOrNull { it.app.startsWith(FAV) }?.let { it.copy(app = it.app.removePrefix(FAV)) }
        entries = entries.filterNot { it.app.startsWith(FAV) }.toMutableList()
    }

    fun record(query: String, artist: String?, title: String?, app: String, now: Long = System.currentTimeMillis()) {
        if (query.isBlank()) return
        val key = query.trim().lowercase()
        val old = entries.firstOrNull { it.query.lowercase() == key }
        entries.removeAll { it.query.lowercase() == key }
        entries.add(0, Entry(query.trim(), artist?.trim().orEmpty().ifEmpty { old?.artist.orEmpty() },
            title?.trim().orEmpty().ifEmpty { old?.title.orEmpty() }, app, now, (old?.count ?: 0) + 1))
        while (entries.size > MAX) entries.removeAt(entries.size - 1)
        persist()
    }

    /** Update what actually started playing for the latest request. */
    fun updatePlayedTitle(title: String) {
        val e = entries.firstOrNull() ?: return
        if (title.isBlank()) return
        entries[0] = e.copy(title = title.trim())
        persist()
    }

    fun last(): Entry? = entries.firstOrNull()

    fun lastArtist(): String? = entries.firstOrNull { it.artist.isNotBlank() }?.artist

    fun recent(n: Int = 5): List<Entry> = entries.take(n)

    /** Explicit favourite, else the most requested song (asked at least 3 times). */
    fun favourite(): Entry? = favourite ?: entries.filter { it.count >= 3 }.maxByOrNull { it.count }

    fun setFavourite(e: Entry) {
        favourite = e
        persist()
    }

    private fun persist() {
        val all = ArrayList<Entry>(entries)
        favourite?.let { all.add(it.copy(app = FAV + it.app)) }
        save(encode(all))
    }

    companion object {
        private const val MAX = 25
        private const val FAV = "fav:"
        private const val SEP = "\u001F"

        private fun clean(s: String) = s.replace(SEP, " ").replace("\n", " ")

        fun encode(list: List<Entry>): String = list.joinToString("\n") {
            listOf(clean(it.query), clean(it.artist), clean(it.title), clean(it.app), it.time.toString(), it.count.toString()).joinToString(SEP)
        }

        fun decode(s: String): MutableList<Entry> {
            val out = ArrayList<Entry>()
            for (line in s.split('\n')) {
                val p = line.split(SEP)
                if (p.size < 6) continue
                out.add(Entry(p[0], p[1], p[2], p[3], p[4].toLongOrNull() ?: 0L, p[5].toIntOrNull() ?: 1))
            }
            return out
        }
    }
}
