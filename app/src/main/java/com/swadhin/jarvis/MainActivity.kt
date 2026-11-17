package com.swadhin.jarvis

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0B1220")
    private val card = Color.parseColor("#131C2E")
    private val cyan = Color.parseColor("#22D3EE")
    private val textCol = Color.parseColor("#E2E8F0")
    private val dim = Color.parseColor("#94A3B8")

    private lateinit var keyBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var ytKeyBox: EditText
    private lateinit var status: TextView
    private lateinit var permStatus: TextView
    private lateinit var logView: TextView
    private lateinit var voiceSpinner: Spinner
    private var tts: TextToSpeech? = null
    private var voiceNames = ArrayList<String>()
    private var uiReady = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"
            setTextColor(cyan)
            textSize = 30f
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        })
        status = label("", dim).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12)) }
        root.addView(status)

        // ---- Start / Stop ----
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("▶  START JARVIS", true) { startJarvis() }, weightParams())
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(button("■  STOP", false) {
            stopService(Intent(this, JarvisService::class.java))
            root.postDelayed({ refresh() }, 400)
        }, weightParams())
        root.addView(row)

        // ---- Gemini API ----
        val api = section(root, "GEMINI API")
        api.addView(label("API Key", dim))
        keyBox = input("AIza… yahan paste karo", true).apply { setText(Prefs.apiKey(this@MainActivity)) }
        api.addView(keyBox)
        api.addView(label("Model", dim))
        modelBox = input(Prefs.DEFAULT_MODEL, false).apply { setText(Prefs.model(this@MainActivity)) }
        api.addView(modelBox)
        val apiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        apiRow.addView(button("SAVE", true) { saveApi(); toast("Save ho gaya ✓") }, weightParams())
        apiRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        apiRow.addView(button("TEST", false) { testApi() }, weightParams())
        api.addView(apiRow)
        api.addView(label("Free key yahan milegi → aistudio.google.com/apikey  (tap)", cyan).apply {
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
        })

        // ---- Voice ----
        val voice = section(root, "VOICE")
        voice.addView(label("Awaaz", dim))
        voice.addView(spinner(listOf("Male (Jarvis)", "Female"), if (Prefs.gender(this) == "female") 1 else 0) { pos ->
            Prefs.setGender(this, if (pos == 1) "female" else "male")
        })
        voice.addView(label("Voice (phone me installed)", dim))
        voiceSpinner = Spinner(this)
        voice.addView(voiceSpinner)
        voice.addView(label("Sunne ki bhasha", dim))
        val langs = listOf("auto", "hi-IN", "en-IN")
        voice.addView(spinner(listOf("Auto: Hindi + English + Hinglish (recommended)", "Hindi / Hinglish only", "English (India) only"),
            langs.indexOf(Prefs.listenLang(this)).coerceAtLeast(0)) { pos ->
            Prefs.setListenLang(this, langs[pos])
        })
        voice.addView(label("Jarvis aapko kya bulaye", dim))
        val titles = listOf("Sir", "Boss", "Madam")
        voice.addView(spinner(titles, titles.indexOf(Prefs.address(this)).coerceAtLeast(0)) { pos ->
            Prefs.setAddress(this, titles[pos])
        })
        voice.addView(CheckBox(this).apply {
            text = "Sirf \"Jarvis\" bolne par command le (wake word)"
            setTextColor(textCol)
            isChecked = Prefs.wakeWord(this@MainActivity)
            setOnCheckedChangeListener { _, c -> Prefs.setWakeWord(this@MainActivity, c) }
        })
        voice.addView(button("🔊  VOICE TEST", false) { testVoice() })

        // ---- Music ----
        val mus = section(root, "MUSIC / YOUTUBE")
        mus.addView(label("Default music app (jab app ka naam na bolo)", dim))
        val apps = listOf("youtube", "ytmusic", "spotify")
        mus.addView(spinner(listOf("YouTube", "YouTube Music", "Spotify"), apps.indexOf(Prefs.musicApp(this)).coerceAtLeast(0)) { pos ->
            Prefs.setMusicApp(this, apps[pos])
        })
        mus.addView(label("YouTube Data API key (optional)", dim))
        ytKeyBox = input("khali chhodo to Jarvis screen se pehla video tap karega", true).apply { setText(Prefs.youtubeKey(this@MainActivity)) }
        mus.addView(ytKeyBox)
        mus.addView(label("Key ke saath Jarvis exact gaana dhoondh ke seedha chalata hai aur kai match hon to poochta hai. " +
            "Bina key ke YouTube ke search results me se pehla video Accessibility se tap hota hai. " +
            "Pause / next / seek ke liye Notification Access on hona chahiye.", dim).apply { textSize = 12f })

        // ---- Permissions ----
        val perm = section(root, "PHONE CONTROL / PERMISSIONS")
        permStatus = label("", textCol)
        perm.addView(permStatus)
        perm.addView(button("1. Mic, Contacts, Call, SMS", false) { requestPermissions(runtimePerms(), 1) })
        perm.addView(button("2. Phone Control (Accessibility)", false) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        perm.addView(button("3. Notification Access (music controls + notifications)", false) { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        perm.addView(button("4. Modify System Settings (brightness)", false) {
            open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        })
        perm.addView(button("5. Battery: background me band na ho", false) {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        perm.addView(label("Accessibility grey dikhe to: Settings > Apps > Jarvis > ⋮ > Allow restricted settings.", dim))

        // ---- Type command ----
        val cmd = section(root, "COMMAND LIKH KE BHEJO")
        val cmdBox = input("jaise: YouTube par Arijit Singh ka gana chalao", false)
        cmd.addView(cmdBox)
        cmd.addView(button("SEND", true) {
            val t = cmdBox.text.toString().trim()
            val s = JarvisService.instance
            if (t.isEmpty()) return@button
            if (s == null) toast("Pehle START dabao") else { s.submitText(t); cmdBox.setText("") }
        })

        // ---- Log ----
        val log = section(root, "LOG")
        logView = label("", dim).apply { textSize = 12f; setTextIsSelectable(true) }
        log.addView(logView)

        uiReady = true
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) runOnUiThread { fillVoices() } }
    }

    override fun onResume() {
        super.onResume()
        JarvisService.onLog = { showLogs() }
        refresh()
        showLogs()
    }

    override fun onPause() {
        JarvisService.onLog = null
        saveApi()
        super.onPause()
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // ---------- Actions ----------

    private fun saveApi() {
        if (!uiReady) return
        Prefs.setApiKey(this, keyBox.text.toString())
        Prefs.setModel(this, modelBox.text.toString())
        Prefs.setYoutubeKey(this, ytKeyBox.text.toString())
    }

    private fun testApi() {
        saveApi()
        toast("Testing…")
        Thread {
            val r = GeminiClient(applicationContext).test()
            runOnUiThread { Toast.makeText(this, r, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun startJarvis() {
        saveApi()
        if (Prefs.apiKey(this).isBlank()) toast("Gemini key nahi hai: basic commands chalenge, mushkil sawal nahi")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(runtimePerms(), 1)
            toast("Mic permission do, phir START dabao")
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        status.postDelayed({ refresh() }, 600)
    }

    private fun testVoice() {
        val t = tts ?: return
        try {
            t.language = Locale("hi", "IN")
            JarvisService.pickVoice(t, Prefs.voice(this), Prefs.gender(this))?.let { t.voice = it }
            t.setPitch(if (Prefs.gender(this) == "female") 1.05f else 0.85f)
            t.setSpeechRate(1.05f)
            t.speak("नमस्ते ${Prefs.address(this)}, मैं Jarvis हूँ। आपके लिए क्या कर सकता हूँ?", TextToSpeech.QUEUE_FLUSH, null, "test")
        } catch (e: Exception) {
            toast("Voice error: ${e.message}")
        }
    }

    private fun fillVoices() {
        val t = tts ?: return
        val all = try { t.voices } catch (e: Exception) { null } ?: emptySet()
        voiceNames = ArrayList(
            all.filter { it.locale.language == "hi" || (it.locale.language == "en" && it.locale.country == "IN") }
                .map { it.name }.sorted()
        )
        val items = ArrayList<String>()
        items.add("Auto (best Hindi voice)")
        items.addAll(voiceNames)
        voiceSpinner.adapter = adapter(items)
        val saved = voiceNames.indexOf(Prefs.voice(this))
        voiceSpinner.setSelection(if (saved >= 0) saved + 1 else 0)
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Prefs.setVoice(this@MainActivity, if (position == 0) "" else voiceNames[position - 1])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun runtimePerms(): Array<String> {
        val list = arrayListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.toTypedArray()
    }

    private fun refresh() {
        if (!uiReady) return
        val on = JarvisService.instance != null
        status.text = if (on) "● ONLINE — bolo \"Jarvis, …\"" else "○ OFFLINE"
        status.setTextColor(if (on) cyan else dim)

        fun mark(b: Boolean) = if (b) "✅" else "❌"
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val call = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val sms = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val accOn = isAccessibilityOn()
        val notif = isNotifListenerOn()
        val write = Settings.System.canWrite(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batt = pm.isIgnoringBatteryOptimizations(packageName)
        permStatus.text = "${mark(mic)} Mic   ${mark(contacts)} Contacts   ${mark(call)} Call   ${mark(sms)} SMS\n" +
            "${mark(accOn)} Phone Control   ${mark(notif)} Notifications\n" +
            "${mark(write)} System Settings   ${mark(batt)} Battery"
    }

    private fun isAccessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, JarvisAccessibility::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun isNotifListenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun showLogs() {
        if (!uiReady) return
        logView.text = synchronized(JarvisService.logs) { JarvisService.logs.joinToString("\n") }
        refresh()
    }

    private fun open(i: Intent) {
        try { startActivity(i) } catch (e: Exception) { toast("Ye screen is phone me nahi khuli") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Chhote UI helpers ----------

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun rounded(color: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun section(root: LinearLayout, title: String): LinearLayout {
        root.addView(TextView(this).apply {
            text = title
            setTextColor(cyan)
            textSize = 12f
            letterSpacing = 0.15f
            setPadding(dp(4), dp(22), 0, dp(8))
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        root.addView(box)
        return box
    }

    private fun label(t: String, color: Int) = TextView(this).apply {
        text = t
        setTextColor(color)
        textSize = 14f
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun input(hintText: String, secret: Boolean) = EditText(this).apply {
        hint = hintText
        setTextColor(textCol)
        setHintTextColor(dim)
        textSize = 15f
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        background = rounded(bg, Color.parseColor("#1E293B"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun button(t: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) bg else cyan)
        background = if (primary) rounded(cyan) else rounded(bg, cyan)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun adapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(this).apply {
        adapter = adapter(items)
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onPick(position)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
