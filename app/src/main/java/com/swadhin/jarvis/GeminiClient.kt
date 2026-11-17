package com.swadhin.jarvis

import android.content.Context
import com.swadhin.jarvis.nlu.Lang
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Gemini API se baat karta hai (REST, function calling ke saath).
 * API key aur model app ki settings se aate hain.
 */
class GeminiClient(private val ctx: Context) {

    private var history = JSONArray()

    enum class Failure { NO_KEY, NETWORK, API }

    /** [failure] != null means [text] is only a fallback; the caller replaces it with a localized message. */
    data class Reply(val text: String, val failure: Failure? = null)

    class NetworkException(msg: String) : IOException(msg)

    fun reset() {
        history = JSONArray()
    }

    /**
     * Commands handled locally (without Gemini) are added here too, so Gemini knows the context
     * ("is singer ke aur gane", "usko message bhejo") when a later request does reach it.
     */
    @Synchronized
    fun remember(userText: String, reply: String) {
        history.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
        history.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", reply))))
        trim()
    }

    /**
     * User ka command bhejo. Gemini jo phone-actions maange, [exec] unhe chalata hai
     * aur result wapas bhejta hai. Aakhir me bolne wala jawab return hota hai.
     */
    @Synchronized
    fun chat(userText: String, lang: Lang, exec: (String, JSONObject) -> JSONObject): Reply {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return Reply("", Failure.NO_KEY)
        val sir = Prefs.address(ctx)

        val backup = JSONArray(history.toString())
        val tag = when (lang) {
            Lang.HINDI -> "[user language: Hindi]"
            Lang.HINGLISH -> "[user language: Hinglish]"
            Lang.ENGLISH -> "[user language: English]"
        }
        history.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", "$tag $userText")))
        )
        try {
            repeat(12) {
                val content = request(key)
                val parts = content.optJSONArray("parts") ?: JSONArray()
                if (parts.length() == 0) {
                    history = backup
                    return Reply("$sir, मैं समझ नहीं पाया, एक बार फिर बोलिए।")
                }
                // Model ka turn jaisa aaya waisa hi wapas bhejna zaroori hai (thought signatures).
                history.put(content)

                val calls = ArrayList<JSONObject>()
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    p.optJSONObject("functionCall")?.let { calls.add(it) }
                    if (p.has("text") && !p.optBoolean("thought", false)) sb.append(p.optString("text"))
                }
                if (calls.isEmpty()) {
                    trim()
                    return Reply(clean(sb.toString()).ifBlank { "हो गया $sir।" })
                }

                val responses = JSONArray()
                for (c in calls) {
                    val name = c.optString("name")
                    val args = c.optJSONObject("args") ?: JSONObject()
                    val result = try {
                        exec(name, args)
                    } catch (e: Exception) {
                        JSONObject().put("error", e.message ?: e.toString())
                    }
                    val fr = JSONObject().put("name", name).put("response", result)
                    if (c.has("id")) fr.put("id", c.get("id"))
                    responses.put(JSONObject().put("functionResponse", fr))
                }
                history.put(JSONObject().put("role", "user").put("parts", responses))
            }
            history = backup
            return Reply("$sir, यह काम बहुत लंबा हो गया। जितना हो सका मैंने कर दिया।")
        } catch (e: NetworkException) {
            history = backup
            return Reply(e.message ?: "", Failure.NETWORK)
        } catch (e: Exception) {
            history = backup
            JarvisService.log("Gemini error: ${e.message}")
            return Reply(e.message ?: "", Failure.API)
        }
    }

    /** Settings screen ka "Test" button. */
    fun test(): String = try {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) "API key khali hai"
        else {
            val body = JSONObject().put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", "Say: Jarvis online")))
                )
            )
            val c = post(key, body)
            val parts = c.optJSONArray("parts") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            "OK ✓  " + sb.toString().trim()
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private fun request(key: String): JSONObject {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt())))
            )
            .put("contents", history)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", PhoneActions.declarations())))
        return post(key, body)
    }

    /** Returns candidates[0].content. One quick retry on a dropped connection. */
    private fun post(key: String, body: JSONObject): JSONObject {
        try {
            return postOnce(key, body)
        } catch (e: UnknownHostException) {
            throw NetworkException("no internet")
        } catch (e: SocketTimeoutException) {
            throw NetworkException("timeout")
        } catch (e: NetworkException) {
            throw e
        } catch (e: IOException) {
            Thread.sleep(600)
            try {
                return postOnce(key, body)
            } catch (e2: IOException) {
                throw NetworkException(e2.message ?: "network")
            }
        }
    }

    private fun postOnce(key: String, body: JSONObject): JSONObject {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                Prefs.model(ctx) + ":generateContent"
        )
        val con = url.openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 10000
            con.readTimeout = 45000
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            con.setRequestProperty("x-goog-api-key", key)
            con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").optString("message")
                } catch (e: Exception) {
                    text.take(200)
                }
                throw RuntimeException("Gemini $code: $msg")
            }
            val json = JSONObject(text)
            val cands = json.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) throw RuntimeException("Gemini ne jawab nahi diya")
            return cands.getJSONObject(0).optJSONObject("content") ?: JSONObject()
        } finally {
            con.disconnect()
        }
    }

    /** History chhoti rakho, par hamesha user ke text-turn se shuru ho. */
    private fun trim() {
        if (history.length() <= 40) return
        var start = -1
        for (i in history.length() - 24 until history.length()) {
            val t = history.getJSONObject(i)
            val first = t.optJSONArray("parts")?.optJSONObject(0)
            if (t.optString("role") == "user" && first != null && first.has("text")) {
                start = i
                break
            }
        }
        val fresh = JSONArray()
        if (start >= 0) for (i in start until history.length()) fresh.put(history.get(i))
        history = fresh
    }

    private fun clean(s: String): String =
        s.replace(Regex("[*#`_~]"), "").replace(Regex("\\s+"), " ").trim()

    private fun systemPrompt(): String {
        val female = Prefs.gender(ctx) == "female"
        val g = if (female) "महिला (करती हूँ, कर दूँगी)" else "पुरुष (करता हूँ, कर दूँगा)"
        val sir = Prefs.address(ctx)
        return """
You are JARVIS, a personal voice assistant living inside the user's Android phone. Address the user as "$sir".
Personality: calm, witty, loyal, confident, polite, like Tony Stark's JARVIS but Indian-friendly. Speak naturally, never robotic.
Typical lines: "Certainly, $sir." / "$sir, मैं आपके instructions का इंतज़ार कर रहा हूँ।" / "Volume increase कर दिया, $sir."

LANGUAGE
- Each user message starts with a tag like [user language: Hinglish]. Reply in that same style. The user may switch language any time; follow the latest message.
- Hindi: natural spoken Hindi in Devanagari. Hinglish: Hindi words in Devanagari, English words in English (e.g. "YouTube पर गाना play कर दिया, $sir."). English: plain English.
- Speech recognition may contain mistakes or mixed scripts; understand the intent.
- Your reply is spoken aloud by a text-to-speech voice. Never write Roman Hinglish words like "kar diya"; write "कर दिया".
- Hindi grammar gender for yourself: $g.
- Keep replies short: 1-2 sentences. No markdown, no emojis, no lists, no symbols.

PHONE CONTROL
- You control the phone through the provided functions. When asked for something, DO it with functions, then confirm in one short line.
- Music: use play_youtube / play_music. Their results say honestly whether the song is really PLAYING, only opened, or only search results are shown. Never tell the user a song is playing unless the result says PLAYING. If only results are shown, say so.
- Music controls (pause, next, seek, replay) go through media_control.
- For anything without a direct function, operate the screen like a human: open_app, then read_screen, then tap / type_text / scroll, and read_screen again to verify. Repeat until done.
- After opening an app or tapping, the screen needs a moment; use wait if read_screen looks stale.
- Contact names may arrive in Devanagari; the phone already tries English spellings. If a contact is still not found, ask.
- If a function says a permission or service is off, tell the user exactly which switch to turn on in the Jarvis app.
- Before calling or messaging, if the person or the message text is unclear, ask first. Never invent phone numbers.
- Do not bypass app restrictions (paywalls, ads, DRM, banking app protections).
- For general questions (facts, jokes, advice, calculations) just answer by speaking; no function needed.
""".trim()
    }
}
