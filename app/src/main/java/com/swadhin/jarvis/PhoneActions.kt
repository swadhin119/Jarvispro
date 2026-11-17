package com.swadhin.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wo saare kaam jo Jarvis phone par kar sakta hai.
 * Naya kaam jodna ho: 1) declarations() me function likho  2) run() me uska case likho.
 */
class PhoneActions(private val ctx: Context) {

    private val acc: JarvisAccessibility? get() = JarvisAccessibility.instance

    private fun ok(msg: String): JSONObject = JSONObject().put("result", msg)
    private fun err(msg: String): JSONObject = JSONObject().put("error", msg)

    private val noAcc =
        "Accessibility service is OFF. Tell the Boss to open the Jarvis app and turn on 'Phone Control (Accessibility)'."

    fun run(name: String, a: JSONObject): JSONObject = when (name) {
        "open_app" -> openApp(a.optString("name"))
        "make_call" -> makeCall(a.optString("to"))
        "send_sms" -> sendSms(a.optString("to"), a.optString("message"))
        "send_whatsapp" -> sendWhatsApp(a.optString("to"), a.optString("message"))
        "set_alarm" -> setAlarm(a.optInt("hour", -1), a.optInt("minute", 0), a.optString("label"))
        "set_timer" -> setTimer(a.optInt("seconds", 0), a.optString("label"))
        "flashlight" -> flashlight(a.optBoolean("on", true))
        "set_volume" -> setVolume(a.optInt("percent", 50), a.optString("stream", "media"))
        "set_brightness" -> setBrightness(a.optInt("percent", 50))
        "media_control" -> media(a.optString("action"))
        "open_url" -> openUrl(a.optString("url"))
        "web_search" -> openUrl("https://www.google.com/search?q=" + Uri.encode(a.optString("query")))
        "play_youtube" -> youtube(a.optString("query"))
        "open_settings" -> openSettings(a.optString("panel"))
        "system_button" -> systemButton(a.optString("action"))
        "read_screen" -> acc?.let { ok(it.readScreen()) } ?: err(noAcc)
        "tap" -> tap(a.optString("text"))
        "type_text" -> typeText(a.optString("text"), a.optBoolean("press_enter", false))
        "scroll" -> scroll(a.optString("direction", "down"))
        "wait" -> { Thread.sleep((a.optInt("seconds", 1).coerceIn(1, 8)) * 1000L); ok("waited") }
        "device_info" -> deviceInfo()
        "read_notifications" -> notifications()
        else -> err("Unknown function: $name")
    }

    // ---------- Apps ----------

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Accessibility service on ho to background se bhi app khul jaati hai.
        val c: Context = acc ?: ctx
        c.startActivity(i)
    }

    private fun openApp(name: String): JSONObject {
        val want = name.trim().lowercase()
        if (want.isEmpty()) return err("App name missing")
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0).map {
            Triple(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name)
        }
        val hit = apps.firstOrNull { it.first.lowercase() == want }
            ?: apps.firstOrNull { it.first.lowercase().startsWith(want) }
            ?: apps.firstOrNull { it.first.lowercase().contains(want) }
            ?: apps.firstOrNull { want.contains(it.first.lowercase()) }
            ?: apps.firstOrNull { it.second.lowercase().contains(want.replace(" ", "")) }
        if (hit == null) {
            val names = apps.map { it.first }.sorted().joinToString(", ").take(1500)
            return err("App '$name' not found. Installed apps: $names")
        }
        return try {
            launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(hit.second, hit.third))
            Thread.sleep(1200)
            ok("Opened ${hit.first}")
        } catch (e: Exception) {
            err("Could not open ${hit.first}: ${e.message}. Accessibility on hona chahiye.")
        }
    }

    private fun openUrl(url: String): JSONObject {
        var u = url.trim()
        if (u.isEmpty()) return err("URL missing")
        if (!u.contains("://")) u = "https://$u"
        return try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            ok("Opened $u")
        } catch (e: Exception) {
            err("Could not open: ${e.message}")
        }
    }

    private fun youtube(query: String): JSONObject {
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        return try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
            Thread.sleep(2500)
            ok("YouTube search results opened for '$query'. Use read_screen and tap on a video title to play it.")
        } catch (e: Exception) {
            openUrl(url)
        }
    }

    private fun openSettings(panel: String): JSONObject {
        val action = when (panel.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "mobile_data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            launch(Intent(action))
            Thread.sleep(1200)
            ok("Settings '$panel' opened. Use read_screen and tap to change a switch.")
        } catch (e: Exception) {
            err("Could not open settings: ${e.message}")
        }
    }

    // ---------- Call / SMS / WhatsApp ----------

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Returns (number, displayName) */
    private fun resolve(who: String): Pair<String, String>? {
        val t = who.trim()
        if (t.isEmpty()) return null
        if (t.matches(Regex("[+0-9 ()-]{3,}"))) return Pair(t.replace(Regex("[^+0-9]"), ""), t)
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        ctx.contentResolver.query(phone, cols, sel, arrayOf("%$t%"), null)?.use { c ->
            if (c.moveToFirst()) {
                val nm = c.getString(0) ?: t
                val num = c.getString(1) ?: return null
                return Pair(num.replace(Regex("[^+0-9]"), ""), nm)
            }
        }
        return null
    }

    private fun contactError(who: String): JSONObject =
        if (!granted(Manifest.permission.READ_CONTACTS)) err("Contacts permission is off. Ask Boss to grant permissions in the Jarvis app.")
        else err("Contact '$who' not found. Try an English spelling of the name, or ask the Boss.")

    private fun makeCall(to: String): JSONObject {
        if (!granted(Manifest.permission.CALL_PHONE)) return err("Call permission is off. Ask Boss to grant permissions in the Jarvis app.")
        val r = resolve(to) ?: return contactError(to)
        return try {
            launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(r.first))))
            ok("Calling ${r.second}")
        } catch (e: Exception) {
            err("Call failed: ${e.message}")
        }
    }

    private fun sendSms(to: String, message: String): JSONObject {
        if (message.isBlank()) return err("Message text missing")
        if (!granted(Manifest.permission.SEND_SMS)) return err("SMS permission is off. Ask Boss to grant permissions in the Jarvis app.")
        val r = resolve(to) ?: return contactError(to)
        return try {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(r.first, null, sms.divideMessage(message), null, null)
            ok("SMS sent to ${r.second}")
        } catch (e: Exception) {
            err("SMS failed: ${e.message}")
        }
    }

    private fun sendWhatsApp(to: String, message: String): JSONObject {
        if (message.isBlank()) return err("Message text missing")
        val r = resolve(to) ?: return contactError(to)
        var num = r.first.replace("+", "")
        if (num.length == 10) num = "91$num"
        if (num.length == 11 && num.startsWith("0")) num = "91" + num.substring(1)
        return try {
            val uri = Uri.parse("https://wa.me/$num?text=" + Uri.encode(message))
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
            val a = acc ?: return ok("WhatsApp chat with ${r.second} opened with the message typed, but Accessibility is off so Send was not pressed.")
            var sent = false
            for (i in 0 until 6) {
                Thread.sleep(1000)
                if (a.tapText("Send")) { sent = true; break }
            }
            if (sent) ok("WhatsApp message sent to ${r.second}")
            else ok("WhatsApp chat opened with the message typed, but the Send button was not found. Use read_screen and tap.")
        } catch (e: Exception) {
            err("WhatsApp failed (installed hai?): ${e.message}")
        }
    }

    // ---------- Alarm / timer ----------

    private fun setAlarm(hour: Int, minute: Int, label: String): JSONObject {
        if (hour !in 0..23 || minute !in 0..59) return err("hour must be 0-23 and minute 0-59")
        return try {
            launch(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis" })
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            ok("Alarm set for %02d:%02d".format(hour, minute))
        } catch (e: Exception) {
            err("Alarm failed: ${e.message}")
        }
    }

    private fun setTimer(seconds: Int, label: String): JSONObject {
        if (seconds <= 0) return err("seconds must be > 0")
        return try {
            launch(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis" })
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            ok("Timer started for $seconds seconds")
        } catch (e: Exception) {
            err("Timer failed: ${e.message}")
        }
    }

    // ---------- Hardware ----------

    private fun flashlight(on: Boolean): JSONObject = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) err("No flash on this phone")
        else {
            cm.setTorchMode(id, on)
            ok(if (on) "Flashlight ON" else "Flashlight OFF")
        }
    } catch (e: Exception) {
        err("Flashlight failed: ${e.message}")
    }

    private fun setVolume(percent: Int, stream: String): JSONObject = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = when (stream.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(s)
        am.setStreamVolume(s, percent.coerceIn(0, 100) * max / 100, AudioManager.FLAG_SHOW_UI)
        ok("$stream volume set to $percent%")
    } catch (e: Exception) {
        err("Volume failed: ${e.message}")
    }

    private fun setBrightness(percent: Int): JSONObject {
        if (!Settings.System.canWrite(ctx)) return err("'Modify system settings' permission is off. Ask Boss to turn it on in the Jarvis app.")
        return try {
            val cr = ctx.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (percent.coerceIn(1, 100) * 255 / 100))
            ok("Brightness set to $percent%")
        } catch (e: Exception) {
            err("Brightness failed: ${e.message}")
        }
    }

    private fun media(action: String): JSONObject {
        val key = when (action.lowercase()) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return ok("Media: $action")
    }

    // ---------- Screen control ----------

    private fun systemButton(action: String): JSONObject {
        val a = acc ?: return err(noAcc)
        return if (a.global(action)) { Thread.sleep(600); ok("Done: $action") } else err("Could not do: $action")
    }

    private fun tap(text: String): JSONObject {
        val a = acc ?: return err(noAcc)
        return if (a.tapText(text)) { Thread.sleep(900); ok("Tapped '$text'") }
        else err("'$text' not found on screen. Call read_screen to see exact labels, or scroll.")
    }

    private fun typeText(text: String, enter: Boolean): JSONObject {
        val a = acc ?: return err(noAcc)
        return if (a.typeText(text, enter)) { Thread.sleep(500); ok("Typed") }
        else err("No text box found. Tap on the input box first.")
    }

    private fun scroll(direction: String): JSONObject {
        val a = acc ?: return err(noAcc)
        return if (a.swipe(direction)) { Thread.sleep(700); ok("Scrolled $direction") } else err("Scroll failed")
    }

    // ---------- Info ----------

    private fun deviceInfo(): JSONObject {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val now = Date()
        return JSONObject()
            .put("battery_percent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("charging", bm.isCharging)
            .put("time", SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(now))
            .put("date", SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now))
            .put("phone", Build.MANUFACTURER + " " + Build.MODEL)
    }

    private fun notifications(): JSONObject {
        if (!NotifListener.connected) return err("Notification access is OFF. Ask Boss to turn on 'Notification Access' in the Jarvis app.")
        val list = NotifListener.latest(10)
        return if (list.isEmpty()) ok("No new notifications") else JSONObject().put("notifications", JSONArray(list))
    }

    // ---------- Gemini ko batana ki kya-kya kar sakte hain ----------

    companion object {
        private fun fn(name: String, desc: String, vararg params: Array<String>): JSONObject {
            val f = JSONObject().put("name", name).put("description", desc)
            if (params.isNotEmpty()) {
                val props = JSONObject()
                val req = JSONArray()
                // har param: [name, type, description, "req" ya ""]
                for (p in params) {
                    props.put(p[0], JSONObject().put("type", p[1]).put("description", p[2]))
                    if (p[3] == "req") req.put(p[0])
                }
                val schema = JSONObject().put("type", "OBJECT").put("properties", props)
                if (req.length() > 0) schema.put("required", req)
                f.put("parameters", schema)
            }
            return f
        }

        private fun p(name: String, type: String, desc: String, required: Boolean = true) =
            arrayOf(name, type, desc, if (required) "req" else "")

        fun declarations(): JSONArray = JSONArray()
            .put(fn("open_app", "Open an installed app by its name.", p("name", "STRING", "App name, e.g. WhatsApp, Camera, Instagram")))
            .put(fn("make_call", "Make a phone call.", p("to", "STRING", "Contact name or phone number")))
            .put(fn("send_sms", "Send an SMS text message.", p("to", "STRING", "Contact name or phone number"), p("message", "STRING", "Message text")))
            .put(fn("send_whatsapp", "Send a WhatsApp message.", p("to", "STRING", "Contact name or phone number"), p("message", "STRING", "Message text")))
            .put(fn("set_alarm", "Set an alarm.", p("hour", "INTEGER", "Hour in 24h format, 0-23"), p("minute", "INTEGER", "Minute 0-59"), p("label", "STRING", "Alarm label", false)))
            .put(fn("set_timer", "Start a countdown timer.", p("seconds", "INTEGER", "Length in seconds"), p("label", "STRING", "Timer label", false)))
            .put(fn("flashlight", "Turn the torch on or off.", p("on", "BOOLEAN", "true = on, false = off")))
            .put(fn("set_volume", "Set volume level.", p("percent", "INTEGER", "0-100"), p("stream", "STRING", "media, ring, alarm or call", false)))
            .put(fn("set_brightness", "Set screen brightness.", p("percent", "INTEGER", "1-100")))
            .put(fn("media_control", "Control music/video that is playing.", p("action", "STRING", "play, pause, toggle, next, previous or stop")))
            .put(fn("open_url", "Open a website in the browser.", p("url", "STRING", "Website address")))
            .put(fn("web_search", "Search Google and show the results on screen.", p("query", "STRING", "Search text")))
            .put(fn("play_youtube", "Search YouTube for a song or video.", p("query", "STRING", "What to play")))
            .put(fn("open_settings", "Open a phone settings page (to switch Wi-Fi, Bluetooth etc. on/off, then use tap).", p("panel", "STRING", "wifi, bluetooth, mobile_data, hotspot, location, airplane, display, sound, battery, apps or main")))
            .put(fn("system_button", "Press a system button.", p("action", "STRING", "home, back, recents, notifications, quick_settings, power_menu, lock_screen or screenshot")))
            .put(fn("read_screen", "Read all text and buttons currently visible on the phone screen."))
            .put(fn("tap", "Tap a button or item on the screen by its visible text or label.", p("text", "STRING", "Visible text or label of the item")))
            .put(fn("type_text", "Type text into the focused or first text box on screen.", p("text", "STRING", "Text to type"), p("press_enter", "BOOLEAN", "Press enter/search after typing", false)))
            .put(fn("scroll", "Scroll the screen.", p("direction", "STRING", "down (see more), up, left or right")))
            .put(fn("wait", "Wait for the screen to load.", p("seconds", "INTEGER", "1-8")))
            .put(fn("device_info", "Get battery level, charging state, current time and date."))
            .put(fn("read_notifications", "Get the latest notifications received on the phone."))
    }
}
