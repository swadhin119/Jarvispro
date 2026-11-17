package com.swadhin.jarvis

import android.content.Context
import android.content.SharedPreferences

/** Saari settings yahin save hoti hain (sirf phone ke andar). */
object Prefs {
    const val DEFAULT_MODEL = "gemini-flash-latest"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = sp(c).getString("api_key", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("api_key", v.trim()).apply()

    fun model(c: Context): String =
        (sp(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
    fun setModel(c: Context, v: String) = sp(c).edit().putString("model", v.trim()).apply()

    /** "male" ya "female" */
    fun gender(c: Context): String = sp(c).getString("gender", "male") ?: "male"
    fun setGender(c: Context, v: String) = sp(c).edit().putString("gender", v).apply()

    /** TTS voice ka naam, khali = auto */
    fun voice(c: Context): String = sp(c).getString("voice", "") ?: ""
    fun setVoice(c: Context, v: String) = sp(c).edit().putString("voice", v).apply()

    /**
     * Sunne ki bhasha: "auto" (Hindi + English + Hinglish, default), "hi-IN" ya "en-IN".
     * Auto me hi-IN recogniser chalta hai (jo Hinglish bhi samajhta hai) aur Android 14+ par English
     * auto-detect bhi on hota hai.
     */
    fun listenLang(c: Context): String = sp(c).getString("listen_lang", "auto") ?: "auto"
    fun setListenLang(c: Context, v: String) = sp(c).edit().putString("listen_lang", v).apply()

    /** true = sirf "Jarvis" bolne par command le */
    fun wakeWord(c: Context): Boolean = sp(c).getBoolean("wake", true)
    fun setWakeWord(c: Context, v: Boolean) = sp(c).edit().putBoolean("wake", v).apply()

    /** Jarvis aapko kya bulaye: "Sir" (default) ya "Boss" */
    fun address(c: Context): String = (sp(c).getString("address", "Sir") ?: "Sir").ifBlank { "Sir" }
    fun setAddress(c: Context, v: String) = sp(c).edit().putString("address", v).apply()

    /** Optional YouTube Data API v3 key: exact video dhoondh kar seedha play karne ke liye. */
    fun youtubeKey(c: Context): String = sp(c).getString("yt_key", "") ?: ""
    fun setYoutubeKey(c: Context, v: String) = sp(c).edit().putString("yt_key", v.trim()).apply()

    /** Default music app: "youtube", "ytmusic" ya "spotify" */
    fun musicApp(c: Context): String = sp(c).getString("music_app", "youtube") ?: "youtube"
    fun setMusicApp(c: Context, v: String) = sp(c).edit().putString("music_app", v).apply()

    fun musicMemory(c: Context): String = sp(c).getString("music_memory", "") ?: ""
    fun setMusicMemory(c: Context, v: String) = sp(c).edit().putString("music_memory", v).apply()
}
