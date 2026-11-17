package com.swadhin.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API se baat karta hai (REST, function calling ke saath).
 * API key aur model app ki settings se aate hain.
 */
class GeminiClient(private val ctx: Context) {

    private var history = JSONArray()

    fun reset() {
        history = JSONArray()
    }

    /**
     * User ka command bhejo. Gemini jo phone-actions maange, [exec] unhe chalata hai
     * aur result wapas bhejta hai. Aakhir me bolne wala jawab return hota hai.
     */
    @Synchronized
    fun chat(userText: String, exec: (String, JSONObject) -> JSONObject): String {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return "Boss, पहले ऐप में Gemini API key डालिए।"

        val backup = JSONArray(history.toString())
        history.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText)))
        )
        try {
            repeat(12) {
                val content = request(key)
                val parts = content.optJSONArray("parts") ?: JSONArray()
                if (parts.length() == 0) {
                    history = backup
                    return "Boss, मैं समझ नहीं पाया, एक बार फिर बोलिए।"
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
                    return clean(sb.toString()).ifBlank { "हो गया Boss।" }
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
            return "Boss, यह काम बहुत लंबा हो गया। जितना हो सका मैंने कर दिया।"
        } catch (e: Exception) {
            history = backup
            return "Boss, दिक्कत आ गई। ${e.message ?: "नेटवर्क चेक कीजिए।"}"
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

    /** Returns candidates[0].content */
    private fun post(key: String, body: JSONObject): JSONObject {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                Prefs.model(ctx) + ":generateContent"
        )
        val con = url.openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 20000
            con.readTimeout = 60000
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
        return """
You are JARVIS, a personal voice assistant living inside the user's Android phone. The user is your Boss; address him as "Boss".
Personality: calm, witty, loyal, confident, like a real human assistant. Speak naturally, never robotic.

LANGUAGE
- The user speaks Hindi, English or Hinglish. Speech recognition may give you Hindi in Devanagari with English words mixed in, sometimes with mistakes. Understand the intent.
- Your reply is spoken aloud by a Hindi text-to-speech voice. Write Hindi words in Devanagari script (never Roman Hinglish). Common English words (WhatsApp, YouTube, call, message) may stay in English.
- If the Boss speaks pure English, reply in English.
- Hindi grammar gender for yourself: $g.
- Keep replies short: 1-2 sentences. No markdown, no emojis, no lists, no symbols.

PHONE CONTROL
- You control the phone through the provided functions. When the Boss asks for something, DO it with functions, then confirm in one short line.
- For anything without a direct function, operate the screen like a human: open_app, then read_screen, then tap / type_text / scroll, and read_screen again to verify. Repeat until done.
- After opening an app or tapping, the screen needs a moment; use wait if read_screen looks stale.
- Contact names may arrive in Devanagari; contacts are usually saved in English. If a contact is not found, retry with an English spelling, and if still not found ask the Boss.
- If a function says a permission or service is off, tell the Boss exactly which switch to turn on in the Jarvis app.
- Before calling or messaging, if the person or the message text is unclear, ask first. Never invent phone numbers.
- For general questions (facts, jokes, advice, calculations) just answer by speaking; no function needed.
""".trim()
    }
}
