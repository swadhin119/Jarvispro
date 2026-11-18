package com.swadhin.jarvis.nlu

import com.swadhin.jarvis.nlu.Command.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Intent detection in Hindi (Devanagari, as Google's hi-IN recogniser returns it), Hinglish (Roman) and English.
 * Every example command from the feature spec is covered.
 */
class IntentParserTest {

    private fun p(s: String) = IntentParser.parse(s)

    private fun music(s: String): PlayMusic {
        val c = p(s)
        assertTrue("'$s' -> $c should be PlayMusic", c is PlayMusic)
        return c as PlayMusic
    }

    private fun eq(expected: Command, vararg inputs: String) {
        for (i in inputs) assertEquals("input: '$i'", expected, p(i))
    }

    // ---------------- spec section 1 ----------------

    @Test fun openYouTube() = eq(OpenApp(AppId.YOUTUBE, "YouTube"),
        "Jarvis, YouTube kholo.", "जार्विस, यूट्यूब खोलो", "Jarvis open YouTube", "YouTube खोलो", "jarvis you tube kholo")

    @Test fun arijitSongHinglish() {
        val m = music("Jarvis, Arijit Singh ka gana chalao.")
        assertEquals("Arijit Singh songs", m.query)
        assertEquals("Arijit Singh", m.artist)
        assertEquals(null, m.app)
    }

    @Test fun arijitSongDevanagari() {
        val m = music("जार्विस, यूट्यूब पर अरिजीत सिंह का गाना चलाओ")
        assertEquals("अरिजीत सिंह songs", m.query)
        assertEquals(MusicApp.YOUTUBE, m.app)
        assertEquals("अरिजीत सिंह", m.artist)
    }

    @Test fun finalRequirement() {
        val m = music("Jarvis, YouTube par Arijit Singh ka gana chalao.")
        assertEquals(MusicApp.YOUTUBE, m.app)
        assertEquals("Arijit Singh songs", m.query)
    }

    @Test fun favourite() = eq(PlayFavourite, "Jarvis, mera favourite song play karo.", "मेरा फेवरेट गाना चलाओ", "play my favourite song")

    @Test fun setFavourite() = eq(SetFavourite, "ye mera favourite gana hai", "is gaane ko favourite bana do", "यह मेरा पसंदीदा गाना है")

    @Test fun volumeUp() = eq(Volume(VolOp.UP), "Jarvis, volume badha do.", "वॉल्यूम बढ़ा दो", "आवाज़ बढ़ाओ", "increase the volume", "volume up", "Volume badhao.")

    @Test fun volumeDown() = eq(Volume(VolOp.DOWN), "volume kam karo", "आवाज कम कर दो", "lower the volume", "volume down please")

    @Test fun volumeSet() = eq(Volume(VolOp.SET, 50), "volume 50 kar do", "वॉल्यूम 50 परसेंट करो", "set volume to 50%")

    @Test fun volumeMuteAndMax() {
        eq(Volume(VolOp.MUTE), "mute karo", "म्यूट कर दो", "mute")
        eq(Volume(VolOp.UNMUTE), "unmute karo", "अनम्यूट करो")
        eq(Volume(VolOp.MAX), "volume full kar do", "वॉल्यूम फुल करो")
    }

    @Test fun nextSong() = eq(Media(MediaAction.NEXT), "Jarvis, ab agla gana bajao.", "agla gana bajao", "अगला गाना बजाओ", "next song", "skip", "next video")

    @Test fun previous() = eq(Media(MediaAction.PREVIOUS), "pichla gana chalao", "पिछला गाना", "previous song")

    @Test fun torchOff() = eq(Torch(false), "Jarvis, light band kar do.", "लाइट बंद कर दो", "turn off the flashlight", "torch off")

    @Test fun torchOn() = eq(Torch(true), "torch jalao", "flashlight on karo", "टॉर्च ऑन करो", "turn on the flashlight")

    @Test fun call() {
        eq(Call("Rahul"), "Jarvis, Rahul ko phone lagao.", "call Rahul", "Rahul ko call karo")
        eq(Call("राहुल"), "राहुल को फोन लगाओ", "राहुल को कॉल करो")
        eq(Call("mummy"), "mummy ko phone karo")
    }

    @Test fun alarm() {
        eq(Alarm(6, 0), "Jarvis, mujhe kal subah 6 baje utha dena.", "मुझे कल सुबह 6 बजे उठा देना", "wake me up at 6 am", "set an alarm for 6 am")
        eq(Alarm(19, 30), "shaam 7:30 ka alarm lagao", "alarm for 7.30 pm", "शाम साढ़े 7 बजे का अलार्म लगाओ")
        eq(Alarm(22, 0), "raat 10 baje alarm laga do")
        eq(Alarm(null, 0), "alarm laga do")
    }

    @Test fun timer() {
        eq(Timer(300), "5 minute ka timer lagao", "5 मिनट का टाइमर लगाओ", "set a timer for 5 minutes")
        eq(Timer(30), "timer for 30 seconds")
        eq(Timer(null), "timer lagao")
    }

    @Test fun weather() {
        eq(Weather(null, false), "Jarvis, aaj mausam kaisa hai?", "आज मौसम कैसा है", "what's the weather today", "weather kaisa hai")
        eq(Weather("Delhi", false), "Delhi ka mausam kaisa hai", "weather in Delhi")
        eq(Weather(null, true), "kal mausam kaisa rahega", "कल बारिश होगी क्या")
    }

    @Test fun googleSearch() {
        eq(WebSearch("", false), "Jarvis, Google par search karo.", "गूगल पर सर्च करो")
        eq(WebSearch("Virat Kohli", false), "Google par Virat Kohli search karo", "search Virat Kohli on Google")
        eq(WebSearch("", true), "Jarvis, Chrome mein Google search karo.")
        eq(WebSearch("best phones 2026", false), "search best phones 2026")
    }

    @Test fun whatsapp() {
        eq(WhatsApp(null, null), "Jarvis, WhatsApp par message bhejo.", "व्हाट्सएप पर मैसेज भेजो", "send a whatsapp message")
        eq(WhatsApp("Rahul", "main late aaunga"), "Rahul ko WhatsApp par message bhejo ki main late aaunga",
            "WhatsApp par Rahul ko message bhejo ki main late aaunga")
        eq(WhatsApp("Rahul", "I am coming"), "send whatsapp message to Rahul saying I am coming")
        eq(WhatsApp("Rahul", null), "Rahul ko WhatsApp karo")
    }

    @Test fun whatsappAsRecognizerWritesIt() {
        // Google often writes "कि" (that) as "की" or "के"
        eq(WhatsApp("राहुल", "मैं लेट आऊंगा"),
            "राहुल को व्हाट्सएप पर मैसेज भेजो कि मैं लेट आऊंगा",
            "राहुल को व्हाट्सएप पर मैसेज भेजो की मैं लेट आऊंगा",
            "जार्विस राहुल को व्हाट्सएप पर मैसेज भेजो के मैं लेट आऊंगा")
        eq(WhatsApp("Rahul", "main late aaunga"), "Rahul ko WhatsApp par message bhejo ke main late aaunga",
            "WhatsApp par Rahul ko message bhejo, main late aaunga")
        // possessive "ki" before the command word is part of the name, not the message
        eq(WhatsApp("Rahul mummy", "khana ready hai"), "Rahul ki mummy ko WhatsApp par message bhejo ki khana ready hai")
        eq(WhatsApp("Rahul", "hello"), "Rahul ko WhatsApp par hello bhejo", "WhatsApp par Rahul ko hello likh ke bhejo")
        eq(WhatsApp("Mummy", "I will be late"), "send a WhatsApp message to Mummy that I will be late", "WhatsApp Mummy that I will be late")
    }

    // ---------------- spec section 2: YouTube ----------------

    @Test fun songByArtistAndTitle() {
        val m = music("Jarvis, Arijit Singh ka Tum Hi Ho gana chalao.")
        assertEquals("Arijit Singh ka Tum Hi Ho song", m.query)
        assertEquals("Arijit Singh", m.artist)
        assertEquals("Tum Hi Ho", m.title)
    }

    @Test fun lataOldSongs() {
        val m = music("Jarvis, Lata Mangeshkar ke old songs play karo.")
        assertEquals("Lata Mangeshkar ke old songs", m.query)
        assertEquals("Lata Mangeshkar", m.artist)
    }

    @Test fun odiaRomantic() {
        val m = music("Jarvis, Odia romantic songs chalao.")
        assertEquals("Odia romantic songs", m.query)
        assertEquals(MusicKind.PLAYLIST, m.kind)
    }

    @Test fun latestBollywoodSearch() = eq(SearchYouTube("latest Bollywood songs"), "Jarvis, YouTube par latest Bollywood songs search karo.")

    @Test fun hanumanChalisa() {
        val m = music("Jarvis, Hanuman Chalisa play karo.")
        assertEquals("Hanuman Chalisa", m.query)
        assertEquals("हनुमान चालीसा", music("हनुमान चालीसा चलाओ").query)
    }

    @Test fun titlesMadeOfCommonWords() {
        assertEquals("Tum Hi Ho song", music("Tum Hi Ho gana chalao").query)
        assertEquals("Kal Ho Naa Ho song", music("Kal Ho Naa Ho gana bajao").query)
        assertEquals("Kesariya", music("play Kesariya").query)
    }

    @Test fun englishByArtist() {
        val m = music("play Tum Hi Ho by Arijit Singh on YouTube")
        assertEquals("Tum Hi Ho by Arijit Singh", m.query)
        assertEquals("Arijit Singh", m.artist)
        assertEquals("Tum Hi Ho", m.title)
        assertEquals(MusicApp.YOUTUBE, m.app)
    }

    @Test fun movieSongs() {
        val m = music("Aashiqui 2 movie ke gane chalao")
        assertEquals("Aashiqui 2 movie songs", m.query)
        assertEquals(null, m.artist)
    }

    @Test fun pause() = eq(Media(MediaAction.PAUSE), "Jarvis, gana pause karo.", "गाना रोको", "pause the music", "ruko", "पॉज़ करो")

    @Test fun replay() = eq(Media(MediaAction.REPLAY), "Jarvis, gana dobara chalao.", "wahi gana dobara chalao", "phir se chalao", "दोबारा चलाओ", "replay", "play it again")

    @Test fun seek() {
        eq(Media(MediaAction.SEEK, 30), "Jarvis, 30 second aage karo.", "30 सेकंड आगे करो", "forward 30 seconds", "skip ahead 30 sec")
        eq(Media(MediaAction.SEEK, -10), "Jarvis, 10 second peeche karo.", "10 सेकंड पीछे करो", "rewind 10 seconds")
        eq(Media(MediaAction.SEEK, 60), "1 minute aage karo", "ek minute aage badhao")
        eq(Media(MediaAction.SEEK, 10), "thoda aage karo")
    }

    @Test fun stop() = eq(Media(MediaAction.STOP), "Jarvis, music band karo.", "गाना बंद करो", "stop the music", "stop")

    @Test fun resume() = eq(Media(MediaAction.RESUME), "resume karo", "gana chalu karo", "play karo", "continue")

    // ---------------- spec section 3: smart music ----------------

    @Test fun moreBySinger() = eq(MoreBySinger, "Jarvis, is singer ke aur gane chalao.", "इस सिंगर के और गाने चलाओ", "aur gane chalao", "play more songs")

    @Test fun moodSongs() {
        assertEquals("sad songs", music("Jarvis, sad songs play karo.").query)
        assertEquals(MusicKind.PLAYLIST, music("sad songs play karo").kind)
        assertEquals("romantic playlist", music("Jarvis, romantic playlist chalao.").query)
        assertEquals("romantic songs", music("ek romantic gana bajao").query)
    }

    @Test fun askWhichSong() {
        eq(AskSong(null), "Jarvis, ek gana chalao.", "koi gana sunao", "एक गाना चलाओ", "play a song")
        eq(AskSong(MusicApp.YOUTUBE), "YouTube par gana chalao")
    }

    @Test fun ytMusicAndSpotify() {
        eq(OpenApp(AppId.YT_MUSIC, "YouTube Music"), "YouTube Music kholo", "open yt music")
        val s = music("Jarvis, Spotify par music chalao.")
        assertEquals(MusicApp.SPOTIFY, s.app)
        assertEquals("", s.query)
        val y = music("YouTube Music par Arijit Singh ke gaane chalao")
        assertEquals(MusicApp.YT_MUSIC, y.app)
        assertEquals("Arijit Singh songs", y.query)
    }

    @Test fun nounPhraseAnswer() {
        // the answer to "Aap kaunsa gana sunna chahenge?"
        val m = music("Arijit Singh ka gana.")
        assertEquals("Arijit Singh songs", m.query)
        assertEquals("Kesariya", IntentParser.musicQueryFromAnswer("Kesariya").query)
    }

    // ---------------- spec section 4: apps ----------------

    @Test fun youtubeOpenAndSearch() = eq(SearchYouTube("cricket"), "Jarvis, YouTube kholo aur cricket search karo.", "YouTube par cricket search karo", "search cricket on YouTube")

    @Test fun apps() {
        eq(OpenApp(AppId.WHATSAPP, "WhatsApp"), "Jarvis, WhatsApp kholo.", "व्हाट्सएप खोलो", "open WhatsApp")
        eq(OpenApp(AppId.INSTAGRAM, "Instagram"), "Instagram kholo", "इंस्टाग्राम खोलो")
        eq(OpenApp(AppId.CHROME, "Chrome"), "Chrome kholo", "open chrome")
        eq(OpenApp(AppId.MAPS, "Google Maps"), "Google Maps kholo", "मैप्स खोलो")
        eq(OpenApp(AppId.CAMERA, "Camera"), "camera kholo", "कैमरा खोलो", "open the camera")
        eq(OpenApp(AppId.GALLERY, "Gallery"), "gallery kholo", "गैलरी खोलो", "photos dikhao")
        eq(OpenApp(AppId.SPOTIFY, "Spotify"), "Spotify kholo")
        eq(OpenApp(AppId.PHONE, "Phone"), "phone kholo", "dialer kholo")
        eq(OpenApp(AppId.SETTINGS, "Settings"), "settings kholo", "सेटिंग्स खोलो")
        eq(OpenApp(null, "Flipkart"), "Flipkart kholo", "open Flipkart app")
    }

    @Test fun maps() {
        eq(Maps("Puri Jagannath mandir", false), "Maps mein Puri Jagannath mandir dikhao")
        eq(Maps("airport", true), "airport ka rasta dikhao", "navigate to airport")
    }

    // ---------------- spec section 5: conversation ----------------

    @Test fun smallTalk() {
        eq(SmallTalk(Talk.WHAT_DOING), "Jarvis, kya kar rahe ho?", "क्या कर रहे हो", "what are you doing")
        eq(SmallTalk(Talk.HOW_ARE_YOU), "kaise ho", "how are you", "कैसे हो जार्विस")
        eq(SmallTalk(Talk.THANKS), "thank you", "shukriya jarvis", "धन्यवाद")
        eq(SmallTalk(Talk.WHO_ARE_YOU), "tum kaun ho", "who are you")
        eq(SmallTalk(Talk.GREETING), "hello jarvis", "नमस्ते")
        eq(SmallTalk(Talk.WAKE_ONLY), "Jarvis", "जार्विस")
    }

    @Test fun info() {
        eq(Info(InfoType.TIME), "time kya hua hai", "kitne baje hain", "what time is it", "टाइम क्या हुआ")
        eq(Info(InfoType.BATTERY), "battery kitni hai", "बैटरी कितनी है")
        eq(Info(InfoType.DATE), "aaj kya date hai", "आज की तारीख क्या है")
    }

    @Test fun cancel() = eq(Cancel, "rehne do", "cancel", "छोड़ो")

    @Test fun complexGoesToAi() {
        assertTrue(p("wifi band karo") is Unknown)
        assertTrue(p("mujhe ek kahani sunao jo dinosaur ke baare mein ho") !is Media)
        assertTrue(p("what is the capital of France") is Unknown)
        assertTrue(p("Rahul ko SMS bhejo ki der ho jayegi") is Unknown)
    }

    @Test fun choices() {
        assertEquals(1, IntentParser.parseChoice("pehla wala"))
        assertEquals(2, IntentParser.parseChoice("doosra"))
        assertEquals(2, IntentParser.parseChoice("दूसरा वाला चलाओ"))
        assertEquals(3, IntentParser.parseChoice("number 3"))
        assertEquals(3, IntentParser.parseChoice("the third one"))
        assertEquals(null, IntentParser.parseChoice("Arijit Singh"))
    }
}
