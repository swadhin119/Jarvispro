# Jarvis — testing

## 1. Automated (runs on every push in GitHub Actions)

```bash
gradle testDebugUnitTest
```

- `IntentParserTest` – every command from the feature spec, in Hinglish (Roman), Hindi (Devanagari, the way Google's
  hi-IN recogniser returns it) and English: apps, YouTube search/play, singer / title / movie / mood queries, playback
  controls, seek amounts, volume, torch, calls, WhatsApp slot filling, alarms ("kal subah 6 baje"), timers, weather,
  web search, maps, small talk, cancel, "which one?" answers, and requests that must fall through to Gemini.
- `LanguageAndHelpersTest` – language detection, Devanagari→Latin names, contact matching ("राहुल"→"Rahul Sharma",
  "mom"→"Mummy"), echo guard, YouTube result parsing / ad skipping, ambiguity rule, time parsing, music memory, replies.

The report is uploaded as the `test-report` artifact.

## 2. Manual — on a real phone (not an emulator)

Emulators have no YouTube login, no real mic / speaker echo and often no Google speech engine, so they cannot verify this.
Use a real Android 9+ phone with Google app, YouTube and (optionally) YouTube Music / Spotify installed.

**Setup:** install the APK from the `Jarvis-apk` artifact → grant permissions 1–5 → START JARVIS.
Run the whole list twice: once with Notification Access **ON**, once **OFF** (expect honest "Notification Access chahiye"
replies for seek/replay). Optionally run the YouTube block both with and without a YouTube Data API key.
Watch the LOG section: each command shows `🧠 <intent> [language]` — `Unknown` means it went to Gemini.

Mark each ✅ / ❌. "Spoken reply" should be in the same language you used.

### A. Language
| # | Say | Expected |
|---|---|---|
| A1 | "Jarvis, kya kar rahe ho?" | "Sir, मैं आपके instructions का इंतज़ार कर रहा हूँ।" (Hindi voice), no Gemini call |
| A2 | "Jarvis, what are you doing?" | English reply, English voice |
| A3 | "जार्विस, आज मौसम कैसा है?" | Hindi weather reply with temperature |
| A4 | Hinglish command, then immediately an English one | Reply language switches each time |

### B. YouTube — final requirement
| # | Say | Expected |
|---|---|---|
| B1 | "Jarvis, YouTube par Arijit Singh ka gana chalao." | Says "…YouTube पर Arijit Singh songs search कर रहा हूँ", YouTube opens, a real video **starts playing**, then "…चल रहा है" with the title. Ads/Shorts are not tapped. |
| B2 | "Jarvis, Arijit Singh ka Tum Hi Ho gana chalao." | Plays Tum Hi Ho |
| B3 | "Jarvis, Lata Mangeshkar ke old songs play karo." | Plays an old Lata Mangeshkar song |
| B4 | "Jarvis, Odia romantic songs chalao." | Plays Odia romantic music |
| B5 | "Jarvis, Hanuman Chalisa play karo." | Plays Hanuman Chalisa |
| B6 | "Jarvis, YouTube par latest Bollywood songs search karo." | Results shown, **nothing auto-plays**, reply says results are shown |
| B7 | "Jarvis, YouTube kholo aur cricket search karo." | Cricket results |
| B8 | Turn Accessibility OFF, repeat B1 | Results open; reply honestly says playback could not start + asks to turn on Phone Control |
| B9 | With API key: "Jarvis, love song chalao" | Asks "1: … 2: … कौनसा चलाऊँ?" → "doosra" plays option 2 |

### C. Playback controls (while a YouTube video plays; say "Jarvis" first each time)
| # | Say | Expected |
|---|---|---|
| C1 | "gana pause karo" | Pauses |
| C2 | "resume karo" | Resumes |
| C3 | "30 second aage karo" | Jumps ~30 s forward |
| C4 | "10 second peeche karo" | Jumps ~10 s back |
| C5 | "gana dobara chalao" | Restarts from 0:00 |
| C6 | "agla gana bajao" | Next video (if YouTube offers next) — otherwise an honest "next का control नहीं दे रहा" |
| C7 | "pichla gana" | Previous / restart |
| C8 | "volume badha do" / "volume kam karo" / "volume 50 kar do" / "mute karo" | Volume changes, reply gives new % |
| C9 | "music band karo" | Stops/pauses |
| C10 | While music plays, stay silent 15 s | Jarvis must **not** react to song lyrics (needs "Jarvis" again) |

### D. Smart music
| # | Say | Expected |
|---|---|---|
| D1 | "ek gana chalao" → "Arijit Singh ka gana" | Asks which song, then plays — no "Jarvis" needed for the answer |
| D2 | Close YouTube, "wahi gana dobara chalao" | Replays the last request |
| D3 | After B1: "is singer ke aur gane chalao" | More Arijit Singh |
| D4 | "ye mera favourite gana hai", later "mera favourite song play karo" | Plays it |
| D5 | "sad songs play karo" / "romantic playlist chalao" | Mood music plays |
| D6 | "Spotify par music chalao" / "YouTube Music par Arijit Singh ke gaane chalao" | Plays in that app (or "installed नहीं है", falls back to YouTube) |

### E. Apps and phone
| # | Say | Expected |
|---|---|---|
| E1 | WhatsApp / Instagram / Chrome / Google Maps / Camera / Gallery / Spotify / Phone / Settings "kholo" | Each opens |
| E2 | "Chrome mein Google search karo" → "IPL score" | Chrome opens Google, then searches |
| E3 | "Google par search karo" → "weather in Puri" | Asks what, then searches |
| E4 | "light band kar do" / "torch jalao" | Torch off / on |
| E5 | "Rahul ko phone lagao" / "राहुल को फोन लगाओ" (contact saved as "Rahul …") | Calls Rahul |
| E6 | "WhatsApp par message bhejo" → "Rahul" → "main late aaunga" | Asks who, asks what, sends |
| E7 | "mujhe kal subah 6 baje utha dena" | Alarm 6:00 AM in Clock app |
| E8 | "5 minute ka timer lagao" | Timer starts |
| E9 | "battery kitni hai" / "time kya hua" | Correct values, no Gemini call |

### G. Voice mouse / screen control (Phone Control ON)
| # | Say | Expected |
|---|---|---|
| G1 | Open YouTube, "neeche scroll karo", "upar scroll karo", "bahut neeche scroll karo" | Feed scrolls each time |
| G2 | Open Settings, "neeche" (no cursor) | Scrolls |
| G3 | "cursor dikhao" | Arrow pointer appears in the middle |
| G4 | "neeche", "thoda left", "bahut upar" (no "Jarvis" needed) | Pointer moves, a short tick each time |
| G5 | Put pointer on an app icon, "click karo" | App opens; pointer stays on screen |
| G6 | "numbers dikhao" → "5" | Numbers on all buttons; item 5 is tapped and new screen is renumbered |
| G7 | "grid dikhao" → "7 par click karo" | Box 7 tapped |
| G8 | YouTube: "Subscribe par click karo" and "सब्सक्राइब दबाओ" | Subscribe tapped |
| G9 | Search box: "search box me Arijit Singh type karo aur enter dabao" | Typed and searched |
| G10 | "back jao", "home par jao" | Back / Home |
| G11 | "cursor hatao" | Pointer gone; Jarvis needs "Jarvis" again |

### F. Robustness
| # | Do | Expected |
|---|---|---|
| F1 | Airplane mode, "volume badhao", "torch jalao", "YouTube kholo" | Still work |
| F2 | Airplane mode, "France ki capital kya hai" | "अभी internet नहीं है…" — no crash, keeps listening |
| F3 | Speaker loud, ask something with a long reply | Jarvis does not answer its own voice ("(echo ignored)" in log) |
| F4 | No Gemini key, basic commands | Work; complex question → "Gemini API key डालिए" |
| F5 | Leave running 30 min with screen off (battery: No restrictions) | Still responds to "Jarvis" |
| F6 | Give 10 commands in a row, including 2 YouTube songs and 1 WhatsApp message | Jarvis answers **every** one; it never goes silent after the first |
| F7 | Phone in Hindi language (Settings > Language), "Rahul ko WhatsApp par message bhejo ki main late aaunga" | Message actually appears as sent in the chat; reply "…भेज दिया" |
| F8 | WhatsApp to a contact whose number is not on WhatsApp | Reply "…WhatsApp पर नहीं है", no false "sent" |
| F9 | Force-stop Jarvis from Settings > Apps (Accessibility stays on), open any app | Within ~30 s the LOG shows "wapas chalu kiya" and Jarvis listens again |

If Jarvis ever goes quiet, open the app and check the LOG: lines starting with `(watchdog: …)` show what got stuck
and that it recovered — please share them.
