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
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Background me chalne wala Jarvis: sunta hai -> Gemini se sochta hai -> phone par kaam karta hai -> bolta hai.
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

        /** Auto voice chunna: Hindi voices me se gender ke hisaab se. */
        fun pickVoice(tts: TextToSpeech, wanted: String, gender: String): Voice? {
            val all = try { tts.voices } catch (e: Exception) { null } ?: return null
            if (wanted.isNotBlank()) all.firstOrNull { it.name == wanted }?.let { return it }
            val hindi = all.filter { it.locale.language == "hi" }
            val hints = if (gender == "female") listOf("hia", "hic", "female") else listOf("hie", "hid", "male")
            for (h in hints) {
                hindi.firstOrNull { v ->
                    val n = v.name.lowercase()
                    if (h == "male") n.contains("male") && !n.contains("female") else n.contains(h)
                }?.let { return it }
            }
            return null
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
    private var running = false
    private var busy = false       // soch raha hai ya bol raha hai
    private var listening = false
    private var awakeUntil = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        gemini = GeminiClient(this)
        actions = PhoneActions(this)
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
                    override fun onDone(utteranceId: String?) { main.post { doneSpeaking() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { doneSpeaking() } }
                })
                speak("Jarvis online है Boss।")
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
            val lang = Prefs.listenLang(this)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
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
            if (cmd.length < 2) speak("जी Boss?") else handle(cmd)
            return
        }
        listen()
    }

    // ---------- Sochna + kaam karna ----------

    /** App ke text box se bhi command aa sakta hai. */
    fun submitText(text: String) {
        main.post {
            if (busy) log("Ruko Boss, pehla kaam chal raha hai…")
            else {
                try { recognizer?.cancel() } catch (e: Exception) {}
                listening = false
                handle(text)
            }
        }
    }

    private fun handle(cmd: String) {
        busy = true
        log("🗣 Boss: $cmd")
        worker.execute {
            val reply = gemini.chat(cmd) { name, args ->
                log("⚙ $name $args")
                val r = actions.run(name, args)
                log("   ↳ ${r.toString().take(140)}")
                r
            }
            main.post { if (running) speak(reply) }
        }
    }

    // ---------- Bolna ----------

    private fun speak(text: String) {
        busy = true
        try { recognizer?.cancel() } catch (e: Exception) {}
        listening = false
        log("🤖 Jarvis: $text")
        val t = tts
        if (!ttsReady || t == null) { doneSpeaking(); return }
        applyVoice(t)
        val r = t.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        if (r != TextToSpeech.SUCCESS) doneSpeaking()
    }

    private fun applyVoice(t: TextToSpeech) {
        val key = Prefs.voice(this) + "|" + Prefs.gender(this)
        if (key == appliedVoice) return
        appliedVoice = key
        val male = Prefs.gender(this) != "female"
        try {
            t.language = Locale("hi", "IN")
            pickVoice(t, Prefs.voice(this), Prefs.gender(this))?.let { t.voice = it }
            t.setPitch(if (male) 0.85f else 1.05f)
            t.setSpeechRate(1.05f)
        } catch (e: Exception) {
            log("Voice set nahi hui: ${e.message}")
        }
    }

    private fun doneSpeaking() {
        if (!running) return
        busy = false
        awakeUntil = System.currentTimeMillis() + AWAKE_MS   // itni der bina "Jarvis" bole baat kar sakte ho
        main.postDelayed({ listen() }, 200)
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
