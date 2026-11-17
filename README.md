# Jarvis — Hindi / English / Hinglish voice assistant (Android)

Background me sunta hai, simple commands khud (offline, turant) karta hai, mushkil kaam Gemini se sochta hai,
aur Accessibility se phone chalata hai. YouTube / YouTube Music / Spotify par gaana dhoondh ke **sach me** chalata hai
aur tabhi bolta hai "chal raha hai" jab Android confirm kare.

## APK kaise banaye

**Android Studio (PC):** File > Open > ye folder > Run ▶ (ya Build > Build APK).

**Sirf phone se (GitHub):**
1. Repo > Actions > "Build Jarvis APK" > latest run > Artifacts > `Jarvis-apk` download karo.
2. Zip kholo, `app-debug.apk` install karo.

Har push par GitHub Actions pehle intent-detection tests chalata hai, phir APK banata hai (`test-report` artifact me test result).

## Pehli baar setup

1. **Gemini API** (optional par recommended): key paste karo (free: https://aistudio.google.com/apikey) > SAVE > TEST.
   Bina key ke bhi basic commands (music, volume, call, alarm, apps, torch…) chalenge.
2. **Permissions** section ke 1 se 5 tak saare button dabao. Music controls (pause / next / seek / replay)
   ke liye **3. Notification Access** zaroori hai. Video khud play karne ke liye **2. Phone Control (Accessibility)**.
   Accessibility grey ho to: Settings > Apps > Jarvis > ⋮ > *Allow restricted settings*.
3. **MUSIC / YOUTUBE**: default music app chuno. Optional: YouTube Data API key (neeche dekho).
4. **START JARVIS** dabao. Phir bolo: "Jarvis, YouTube par Arijit Singh ka gana chalao".

Jawab ke baad 12 second tak bina "Jarvis" bole agla command de sakte ho (music shuru hone ke baad nahi,
taaki gaane ke bol command na ban jaayein).

## Bhasha

Koi setting nahi badalni. Hindi, English ya Hinglish me bolo, beech me bhasha badlo — Jarvis usi bhasha me jawab deta hai
(Hindi / Hinglish ke liye Hindi voice, English ke liye English-India voice). "Sunne ki bhasha" ka default **Auto** hai.

## Music kaise chalta hai (sirf supported tarike)

| App | Kaise | Kab "chal raha hai" bolta hai |
|---|---|---|
| YouTube + API key | Official YouTube Data API se exact video, phir `youtube.com/watch` link YouTube app me | Media session PLAYING bole tab |
| YouTube bina key | YouTube app ka search page, phir pehla asli video (ads / Shorts chhod ke) Accessibility se tap | Media session PLAYING bole tab |
| YouTube Music / Spotify | Android ka standard `MEDIA_PLAY_FROM_SEARCH` intent (jo Google Assistant use karta hai) | Media session PLAYING bole tab |

Agar sirf search results khule hain to Jarvis yahi bolta hai, jhooth nahi bolta.
Pause / resume / next / previous / stop / seek / replay Android ke **MediaController** API se hote hain.
Agar app koi control nahi deta (jaise playlist ke bina "next"), Jarvis saaf bata deta hai.

**YouTube Data API key (optional):** console.cloud.google.com > naya project > "YouTube Data API v3" enable > Credentials > API key.
Free quota me roz ~100 searches. Key se Jarvis exact gaana chalata hai aur kai alag gaane match hon to poochta hai "kaunsa chalaun?".

## Kya-kya bol sakte ho (thode examples)

- "YouTube kholo" · "YouTube kholo aur cricket search karo" · "YouTube par latest Bollywood songs search karo"
- "Arijit Singh ka Tum Hi Ho gana chalao" · "Lata Mangeshkar ke old songs play karo" · "Odia romantic songs chalao" · "Hanuman Chalisa play karo"
- "gana pause karo" · "agla gana bajao" · "30 second aage karo" · "10 second peeche karo" · "gana dobara chalao" · "music band karo"
- "wahi gana dobara chalao" · "is singer ke aur gane chalao" · "mera favourite song play karo" · "ye mera favourite gana hai"
- "ek gana chalao" → "Aap kaunsa gana sunna chahenge?" → "Arijit Singh ka gana"
- "volume badha do" · "volume 50 kar do" · "mute karo" · "light band kar do"
- "Rahul ko phone lagao" · "Rahul ko WhatsApp par message bhejo ki main late aaunga"
- "mujhe kal subah 6 baje utha dena" · "5 minute ka timer lagao" · "aaj mausam kaisa hai?"
- "Google par search karo" · "Chrome mein Google search karo" · "Maps mein Puri Jagannath mandir dikhao"
- Devanagari aur English me bhi: "यूट्यूब पर अरिजीत सिंह का गाना चलाओ", "play Tum Hi Ho by Arijit Singh"

Baaki sab (jaise "Wi-Fi band karo", sawal-jawab, kisi app me kuch dhoondhna) Gemini sambhalta hai.

## Code ka naksha

| File | Kaam |
|---|---|
| `MainActivity.kt` | Settings screen (API keys, voice, music, permissions) |
| `JarvisService.kt` | Background: sunna, wake word, bolna, echo se bachna |
| `Assistant.kt` | Dimaag: local command chalana ya Gemini ko bhejna, follow-up sawal, music memory |
| `nlu/` | Bhasha pehchaan + intent samajhna (pure Kotlin, unit tests ke saath) |
| `MusicController.kt` | YouTube / YT Music / Spotify par dhoondhna, chalana, verify karna |
| `MediaControl.kt` | MediaSession se pause / next / seek, volume |
| `GeminiClient.kt` | Gemini API + function calling + Jarvis ka persona |
| `PhoneActions.kt` | Saare phone actions (naya action yahin jodo) |
| `JarvisAccessibility.kt` | Screen padhna, tap, type, scroll, YouTube results padhna |
| `NotifListener.kt` | Notifications + media session access |

Tests: `gradle testDebugUnitTest` (`app/src/test/...`). Phone par test karne ki checklist: [TESTING.md](TESTING.md).

## Limits

- Lock screen ka PIN/pattern voice se nahi khul sakta (Android allow nahi karta).
- Banking/UPI apps apni screen accessibility se chhupa leti hain.
- Xiaomi/Realme/Vivo jaise phones me Autostart on aur battery "No restrictions" karna padta hai, warna background me band ho jata hai.
- Kuch phones me har baar sunna shuru hone par halki beep aati hai (Google speech engine ki). Kuch phones par mic sunte waqt
  music thodi der dheema / ruk sakta hai — ye Google speech engine ka behaviour hai.
- YouTube ka app UI badalta rehta hai; bina API key wala "pehla video tap" tarika kabhi fail ho sakta hai — tab Jarvis
  saaf bolega ki sirf results khule hain. API key wala tarika zyada pakka hai.
- YouTube ads, Premium features ya background-play restrictions bypass nahi kiye jaate.
