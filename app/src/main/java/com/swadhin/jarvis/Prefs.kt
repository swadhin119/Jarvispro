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

    /** Sunne ki bhasha: hi-IN (Hindi + English mix) ya en-IN */
    fun listenLang(c: Context): String = sp(c).getString("listen_lang", "hi-IN") ?: "hi-IN"
    fun setListenLang(c: Context, v: String) = sp(c).edit().putString("listen_lang", v).apply()

    /** true = sirf "Jarvis" bolne par command le */
    fun wakeWord(c: Context): Boolean = sp(c).getBoolean("wake", true)
    fun setWakeWord(c: Context, v: Boolean) = sp(c).edit().putBoolean("wake", v).apply()
}
