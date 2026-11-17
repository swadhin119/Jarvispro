package com.swadhin.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.swadhin.jarvis.nlu.Echo
import com.swadhin.jarvis.nlu.Lang
import com.swadhin.jarvis.nlu.LanguageDetector
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Background me chalne wala Jarvis: sunta hai -> Assistant (local intents, zarurat ho to Gemini) -> phone par kaam -> bolta hai.
 */
class JarvisService : Service() {

    companion object {
        const val ACTION_STOP = "com.swadhin.jarvis.STOP"
        private const val CHANNEL = "jarvis"
        private const val AWAKE_MS = 12000L

        @Volatile
        var instance: JarvisService? = null

        val logs = ArrayList<String>()
        var onLog: (() -> Unit)? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun log(msg: String) {
            synchronized(logs) {
                logs.add(0, msg)
                while (logs.size > 60) logs.removeAt(logs.size - 1)
            }
            mainHandler.post { onLog?.invoke() }
        }

        private val WAKE = Regex(
            "(jarvis|jarvish|jarwis|जार्विस|जारविस|जर्विस|जार्वीस|जार्विश|जारविश|जाविस)",
            RegexOption.IGNORE_CASE
        )

        /** Auto voice chunna: bhasha (Hindi / English-India) aur gender ke hisaab se. */
        fun pickVoice(tts: TextToSpeech, wanted: String, gender: String, english: Boolean = false): Voice? {
            val all = try { tts.voices } catch (e: Exception) { null } ?: return null
            val langCode = if (english) "en" else "hi"
            if (wanted.isNotBlank()) all.firstOrNull { it.name == wanted && it.locale.language == langCode }?.let { return it }
            val pool = if (english) all.filter { it.locale.language == "en" && it.locale.country == "IN" }.ifEmpty { all.filter { it.locale.language == "en" } }
            else all.filter { it.locale.language == "hi" }
            val hints = when {
                english && gender == "female" -> listOf("ena", "enb", "female")
                english -> listOf("end", "enc", "male")
                gender == "female" -> listOf("hia", "hic", "female")
                else -> listOf("hie", "hid", "male")
            }
            val offline = pool.sortedBy { it.isNetworkConnectionRequired }   // offline voices first: faster, work without internet
            for (h in hints) {
                offline.firstOrNull { v ->
                    val n = v.name.lowercase()
                    if (h == "male") n.contains("male") && !n.contains("female") else n.contains(h)
                }?.let { return it }
            }
            return offline.firstOrNull()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var appliedVoice = "?"
    private lateinit var gemini: GeminiClient
    private lateinit var actions: PhoneActions
    private lateinit var assistant: Assistant
    private var running = false
    private var keepAwakeAfter = true     // false after music starts: lyrics must not be taken as commands
    private var lastSpoken = ""
    private var lastSpokenAt = 0L
    private var busy = false       // soch raha hai ya bol raha hai
    private var listening = false
    private var awakeUntil = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        gemini = GeminiClient(this)
        actions = PhoneActions(this)
        assistant = Assistant(this, actions, gemini)
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            log("Service start nahi hua: ${e.message}")
            stopSelf()
            return
        }
        running = true
        instance = this

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply {
            setReferenceCounted(false)
            acquire()
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    // "p…" = progress line ("searching…"), more speech follows; "f…" = final reply
                    override fun onDone(utteranceId: String?) {
                        main.post { lastSpokenAt = System.currentTimeMillis(); if (utteranceId?.startsWith("f") == true) doneSpeaking() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { if (utteranceId?.startsWith("f") == true) doneSpeaking() } }
                })
                val g = assistant.greeting()
                speak(g.text, g.lang)
            } else {
                log("TTS engine nahi mila. Google 'Speech Services' install karo.")
                listen()
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("Speech recognition nahi mila. Google app install/enable karo.")
        }
        log("Jarvis chalu ✓")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        main.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) {}
        worker.shutdownNow()
        try { wakeLock?.release() } catch (e: Exception) {}
        log("Jarvis band")
        super.onDestroy()
    }

    // ---------- Sunna ----------

    private fun listen() {
        if (!running || busy || listening) return
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                    it.setRecognitionListener(listener)
                }
            }
            val pref = Prefs.listenLang(this)
            // hi-IN recogniser understands Hindi, Hinglish and Indian English; "auto" also lets Android 14+ switch to en-IN.
            val lang = if (pref == "auto") "hi-IN" else pref
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (pref == "auto") {
                val both = arrayListOf("hi-IN", "en-IN")
                i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN"))
                if (Build.VERSION.SDK_INT >= 34) {
                    i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, both)
                    i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, both)
                }
            }
            recognizer?.startListening(i)
            listening = true
        } catch (e: Exception) {
            listening = false
            log("Mic error: ${e.message}")
            main.postDelayed({ listen() }, 2000)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            listening = false
            if (!running) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                log("Mic permission nahi hai. App me permission do.")
                return
            }
            var delay = 250L
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                try { recognizer?.destroy() } catch (e: Exception) {}
                recognizer = null
                delay = 900L
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_SERVER) {
                delay = 2500L
            }
            main.postDelayed({ listen() }, delay)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val alts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: ArrayList<String>()
            heard(alts)
        }
    }

    private fun heard(alts: List<String>) {
        if (!running) return
        val first = alts.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) { listen(); return }
        // Feedback-loop guard: the mic picked up Jarvis's own last sentence from the speaker.
        if (Echo.isEcho(first, lastSpoken, System.currentTimeMillis() - lastSpokenAt)) {
            log("(echo ignored)")
            listen(); return
        }

        val awake = System.currentTimeMillis() < awakeUntil
        if (!Prefs.wakeWord(this) || awake) {
            handle(first)
            return
        }
        // Wake word dhundo (kisi bhi alternative me)
        for (a in alts) {
            val m = WAKE.find(a) ?: continue
            val after = a.substring(m.range.last + 1).trim(' ', ',', '.', '?', '!', '।')
            val before = a.substring(0, m.range.first).trim(' ', ',', '.', '?', '!', '।')
            val cmd = if (after.isNotEmpty()) after else before
            handle(if (cmd.length < 2) "Jarvis" else cmd)
            return
        }
        listen()
    }

    // ---------- Sochna + kaam karna ----------

    /** App ke text box se bhi command aa sakta hai. */
    fun submitText(text: String) {
        main.post {
            if (busy) log("Ruko ${Prefs.address(this)}, pehla kaam chal raha hai…")
            else {
                try { recognizer?.cancel() } catch (e: Exception) {}
                listening = false
                handle(text)
            }
        }
    }

    private fun handle(cmd: String) {
        busy = true
        log("🗣 ${Prefs.address(this)}: $cmd")
        worker.execute {
            val reply = assistant.handle(cmd) { text, lang -> main.post { if (running) speak(text, lang, final = false) } }
            main.post {
                if (!running) return@post
                keepAwakeAfter = reply.keepAwake || reply.expectAnswer
                speak(reply.text, reply.lang)
            }
        }
    }

    // ---------- Bolna ----------

    private fun speak(text: String, lang: Lang = Lang.HINGLISH, final: Boolean = true) {
        busy = true
        try { recognizer?.cancel() } catch (e: Exception) {}
        listening = false
        log("🤖 Jarvis: $text")
        lastSpoken = text
        val t = tts
        if (!ttsReady || t == null) { if (final) doneSpeaking(); return }
        // English reply -> English (India) voice; anything with Hindi -> Hindi voice (reads English words fine too).
        val english = lang == Lang.ENGLISH && LanguageDetector.detect(text) == Lang.ENGLISH
        applyVoice(t, english)
        val id = (if (final) "f" else "p") + System.nanoTime()
        val r = t.speak(text.take(3500), TextToSpeech.QUEUE_ADD, null, id)
        if (r != TextToSpeech.SUCCESS && final) doneSpeaking()
    }

    private fun applyVoice(t: TextToSpeech, english: Boolean) {
        val key = Prefs.voice(this) + "|" + Prefs.gender(this) + "|" + english
        if (key == appliedVoice) return
        appliedVoice = key
        val male = Prefs.gender(this) != "female"
        try {
            t.language = if (english) Locale("en", "IN") else Locale("hi", "IN")
            pickVoice(t, Prefs.voice(this), Prefs.gender(this), english)?.let { t.voice = it }
            t.setPitch(if (male) 0.85f else 1.05f)
            t.setSpeechRate(1.05f)
        } catch (e: Exception) {
            log("Voice set nahi hui: ${e.message}")
        }
    }

    private fun doneSpeaking() {
        if (!running) return
        busy = false
        // itni der bina "Jarvis" bole baat kar sakte ho; music chalne ke baad nahi (gaane ke bol command na ban jaayein)
        awakeUntil = if (keepAwakeAfter) System.currentTimeMillis() + AWAKE_MS else 0L
        keepAwakeAfter = true
        lastSpokenAt = System.currentTimeMillis()
        main.postDelayed({ listen() }, 350)   // speaker ki goonj khatam hone do
    }

    // ---------- Notification ----------

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis sun raha hai")
            .setContentText("Bolo: \"Jarvis, …\"")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Band karo", stop).build())
            .build()
    }
}
