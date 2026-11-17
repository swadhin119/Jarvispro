package com.swadhin.jarvis.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndHelpersTest {

    @Test fun detectsHindi() {
        for (s in listOf("यूट्यूब पर अरिजीत सिंह का गाना चलाओ", "आज मौसम कैसा है", "राहुल को फोन लगाओ", "YouTube पर गाना चलाओ"))
            assertEquals(s, Lang.HINDI, LanguageDetector.detect(s))
    }

    @Test fun detectsHinglish() {
        for (s in listOf("Jarvis, YouTube kholo", "volume badha do", "Arijit Singh ka gana chalao", "kya kar rahe ho", "aaj mausam kaisa hai", "agla gana bajao"))
            assertEquals(s, Lang.HINGLISH, LanguageDetector.detect(s))
    }

    @Test fun detectsEnglish() {
        for (s in listOf("open YouTube", "play my favourite song", "what is the weather today", "increase the volume", "call Rahul"))
            assertEquals(s, Lang.ENGLISH, LanguageDetector.detect(s))
    }

    @Test fun unknownWhenNoEvidence() {
        assertNull(LanguageDetector.detect("Arijit Singh"))
        assertNull(LanguageDetector.detect("Jarvis"))
        assertNull(LanguageDetector.detect("123"))
    }

    @Test fun transliteration() {
        assertEquals("rahul", Translit.toLatin("राहुल"))
        assertEquals("arijit", Translit.toLatin("अरिजीत"))
        assertEquals("amit", Translit.toLatin("अमित"))
        assertEquals("priya", Translit.toLatin("प्रिया"))
    }

    @Test fun contactMatching() {
        val contacts = listOf("Rahul Sharma", "Rohit", "Mummy", "Papa Ji", "Priya College", "Amit Kumar")
        assertEquals("Rahul Sharma", ContactMatcher.best("राहुल", contacts))
        assertEquals("Rahul Sharma", ContactMatcher.best("rahul", contacts))
        assertEquals("Mummy", ContactMatcher.best("मम्मी", contacts))
        assertEquals("Mummy", ContactMatcher.best("mom", contacts))
        assertEquals("Papa Ji", ContactMatcher.best("papa", contacts))
        assertEquals("Priya College", ContactMatcher.best("प्रिया", contacts))
        assertEquals("Amit Kumar", ContactMatcher.best("अमित", contacts))
        assertNull(ContactMatcher.best("Suresh", contacts))
    }

    @Test fun echoGuard() {
        assertTrue(Echo.isEcho("volume increase कर दिया सर", "Volume increase कर दिया, Sir.", 1500))
        assertFalse(Echo.isEcho("agla gana bajao", "Volume increase कर दिया, Sir.", 1500))
        assertFalse(Echo.isEcho("volume increase kar diya sir", "Volume increase कर दिया, Sir.", 20000))
    }

    @Test fun youtubeDescriptionParsing() {
        val r = YouTubeResults.parse("Arijit Singh: Tum Hi Ho - 4 minutes, 22 seconds - Go to channel - T-Series - 1.2B views - 11 years ago - play video")
        assertNotNull(r)
        assertEquals("Arijit Singh: Tum Hi Ho", r!!.title)
        assertEquals("T-Series", r.channel)
        assertFalse(r.isAd)
        assertFalse(r.isShort)
        val ad = YouTubeResults.parse("Buy now - Sponsored - 0:30 - play video")
        assertTrue(ad!!.isAd)
        assertNull(YouTubeResults.parse("Search YouTube"))
        assertNull(YouTubeResults.parse("4 minutes, 22 seconds"))
    }

    @Test fun ambiguity() {
        assertTrue(YouTubeResults.needsChoice("love song", listOf("Love Me Like You Do (Official)", "Love Dose - Yo Yo Honey Singh", "Love Story | Taylor Swift"), false))
        assertFalse(YouTubeResults.needsChoice("Tum Hi Ho song", listOf("Tum Hi Ho - Aashiqui 2 | Arijit Singh", "Tum Hi Ho (Lyrics)", "Tum Hi Ho cover"), false))
        assertFalse(YouTubeResults.needsChoice("love song", listOf("Love Me Like You Do", "Love Dose"), true))
        assertFalse(YouTubeResults.needsChoice("Arijit Singh romantic songs", listOf("A", "B", "C"), false))
    }

    @Test fun timeParser() {
        val t = IntentParser.tokenize("paune 8 baje subah")
        assertEquals(Pair(7, 45), TimeParser.clock(t))
        assertEquals(Pair(14, 0), TimeParser.clock(IntentParser.tokenize("dopahar 2 baje")))
        assertEquals(Pair(0, 0), TimeParser.clock(IntentParser.tokenize("raat 12 baje")))
        assertEquals(Pair(6, 15), TimeParser.clock(IntentParser.tokenize("सवा 6 बजे")))
        assertEquals(90, TimeParser.duration(IntentParser.tokenize("dedh minute")))
        assertEquals(1800, TimeParser.duration(IntentParser.tokenize("aadha ghanta")))
    }

    @Test fun musicMemory() {
        var store = ""
        val m = MusicMemory({ store }, { store = it })
        m.record("Arijit Singh songs", "Arijit Singh", null, "youtube", 1)
        m.record("Kesariya", null, "Kesariya - Brahmastra", "youtube", 2)
        assertEquals("Kesariya", m.last()!!.query)
        assertEquals("Arijit Singh", m.lastArtist())
        m.setFavourite(m.last()!!)
        val reloaded = MusicMemory({ store }, { store = it })
        assertEquals("Kesariya", reloaded.favourite()!!.query)
        assertEquals(2, reloaded.recent().size)
        reloaded.record("Kesariya", null, null, "youtube", 3)
        assertEquals(2, reloaded.last()!!.count)
        assertEquals("Kesariya - Brahmastra", reloaded.last()!!.title)
    }

    @Test fun repliesAllLanguages() {
        val r = Replies("Sir")
        for (l in Lang.values()) {
            assertTrue(r.askSong(l).contains("Sir"))
            assertTrue(r.volume(l, VolOp.UP, 60).isNotBlank())
            assertTrue(r.alarm(l, 6, 0).isNotBlank())
        }
        assertEquals("Certainly, Sir. आप कौनसा गाना सुनना चाहेंगे?", r.askSong(Lang.HINGLISH))
        assertEquals("Waiting for your instructions, Sir.", r.whatDoing(Lang.ENGLISH))
        assertTrue(r.alarm(Lang.HINDI, 6, 0).startsWith("सुबह 6 बजे"))
    }
}
