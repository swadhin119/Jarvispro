package com.swadhin.jarvis.nlu

import com.swadhin.jarvis.nlu.Command.Screen
import com.swadhin.jarvis.nlu.ScreenAction.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice mouse / numbers / grid / scroll / tap / type, in Hinglish, Hindi and English. */
class ScreenControlTest {

    private fun eq(expected: Command, vararg inputs: String) {
        for (i in inputs) assertEquals("input: '$i'", expected, IntentParser.parse(i))
    }

    @Test fun cursorShowHide() {
        eq(Screen(CURSOR_SHOW), "Jarvis, cursor dikhao", "mouse on karo", "कर्सर दिखाओ", "show the cursor", "mouse lao")
        eq(Screen(OVERLAY_HIDE), "cursor hatao", "mouse band karo", "कर्सर हटाओ", "hide the cursor", "numbers hatao", "grid hatao")
    }

    @Test fun cursorMove() {
        eq(Screen(MOVE_CURSOR, "down", 0.1f), "cursor neeche karo", "mouse neeche le jao", "कर्सर नीचे करो", "move the cursor down")
        eq(Screen(MOVE_CURSOR, "up", 0.04f), "cursor thoda upar karo", "कर्सर थोड़ा ऊपर")
        eq(Screen(MOVE_CURSOR, "right", 0.25f), "cursor bahut right le jao")
        eq(Screen(MOVE_CURSOR, "left", 0.1f), "mouse left karo", "कर्सर बाएं करो")
    }

    @Test fun bareDirection() {
        eq(Screen(MOVE, "down", 0.1f), "neeche", "neeche karo", "नीचे करो", "aur neeche", "page neeche karo")
        eq(Screen(MOVE, "up", 0.04f), "thoda upar", "थोड़ा ऊपर करो")
        eq(Screen(MOVE, "right", 0.1f), "right", "daayein jao")
    }

    @Test fun scroll() {
        eq(Screen(SCROLL, "down", 0.1f), "Jarvis, neeche scroll karo", "scroll karo", "scroll down", "नीचे स्क्रॉल करो")
        eq(Screen(SCROLL, "up", 0.1f), "upar scroll karo", "scroll up", "ऊपर स्क्रॉल करो")
        eq(Screen(SCROLL, "down", 0.25f), "bahut neeche scroll karo")
        eq(Screen(SCROLL, "right", 0.1f), "right scroll karo")
    }

    @Test fun clickAtCursor() {
        eq(Screen(CLICK), "click karo", "क्लिक करो", "tap", "dabao", "click")
        eq(Screen(DOUBLE_CLICK), "double click karo", "डबल क्लिक करो", "double tap")
        eq(Screen(LONG_PRESS), "long press karo", "dabaye rakho", "लॉन्ग प्रेस करो")
    }

    @Test fun tapByText() {
        eq(Screen(TAP_TEXT, text = "Subscribe"), "Subscribe par click karo", "Subscribe button dabao", "click on Subscribe", "tap Subscribe")
        eq(Screen(TAP_TEXT, text = "सब्सक्राइब"), "सब्सक्राइब पर क्लिक करो")
        eq(Screen(TAP_TEXT, text = "Send"), "Send dabao", "Send button par tap karo")
        eq(Screen(TAP_TEXT, text = "Settings"), "Settings par click karo")
    }

    @Test fun numbersAndGrid() {
        eq(Screen(NUMBERS_SHOW), "numbers dikhao", "नंबर दिखाओ", "show numbers")
        eq(Screen(GRID_SHOW), "grid dikhao", "ग्रिड दिखाओ", "show grid")
        eq(Screen(PICK, number = 5, text = "click"), "5", "5 dabao", "number 5", "5 par click karo", "5 number wala", "नंबर 5")
        eq(Screen(PICK, number = 12, text = "long"), "12 long press karo")
        eq(Screen(PICK, number = 7, text = "cursor"), "cursor 7 par le jao")
    }

    @Test fun cursorToText() = eq(Screen(CURSOR_TO, text = "Subscribe"), "cursor Subscribe par le jao")

    @Test fun typing() {
        eq(Screen(TYPE, text = "Arijit Singh"), "Arijit Singh type karo", "type karo Arijit Singh", "search box me Arijit Singh type karo")
        eq(Screen(TYPE, text = "hello", enter = true), "hello type karo aur enter dabao", "type hello and press enter")
        eq(Screen(TYPE, text = "नमस्ते"), "नमस्ते टाइप करो", "नमस्ते लिखो")
        eq(Screen(ENTER), "enter dabao", "एंटर दबाओ", "press enter")
    }

    @Test fun navigation() {
        eq(Screen(BACK), "back jao", "wapas jao", "वापस जाओ", "go back", "peeche jao", "back button dabao")
        eq(Screen(HOME), "home par jao", "home screen", "होम पर जाओ", "go home")
        eq(Screen(RECENTS), "recent apps dikhao")
        eq(Screen(NOTIF_SHADE), "notification kholo", "नोटिफिकेशन दिखाओ")
    }

    @Test fun noRegressions() {
        // media / volume / seek must still win
        eq(Command.Media(MediaAction.SEEK, -10), "10 second peeche karo")
        eq(Command.Media(MediaAction.SEEK, 10), "thoda aage karo")
        eq(Command.Volume(VolOp.UP), "volume upar karo", "volume badhao")
        eq(Command.Volume(VolOp.DOWN), "आवाज़ नीचे करो")
        eq(Command.Timer(300), "5 minute ka timer lagao")
        eq(Command.Torch(false), "light band kar do")
        assertTrue(IntentParser.parse("Rahul ko WhatsApp par message bhejo ki main late aaunga") is Command.WhatsApp)
        assertTrue(IntentParser.parse("Arijit Singh ka gana chalao") is Command.PlayMusic)
    }
}
