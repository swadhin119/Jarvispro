# Jarvis — Hindi/English voice assistant (Android)

Background me sunta hai, Gemini se sochta hai, aur Accessibility se phone chalata hai.

## APK kaise banaye

**Android Studio (PC):** File > Open > ye `Jarvis` folder > Run ▶ (ya Build > Build APK).

**Sirf phone se (GitHub):**
1. GitHub par nayi repo banao, is folder ki saari files upload karo (`.github` folder samet).
2. Repo > Actions > "Build Jarvis APK" > latest run > Artifacts > `Jarvis-apk` download karo.
3. Zip kholo, `app-debug.apk` install karo.

## Pehli baar setup

1. **Gemini API**: key paste karo (free: https://aistudio.google.com/apikey) > SAVE > TEST.
   Model default `gemini-flash-latest` hai; chaho to badal sakte ho.
2. **Permissions** section ke 1 se 5 tak saare button dabao.
   Accessibility grey ho to: Settings > Apps > Jarvis > ⋮ > *Allow restricted settings*.
3. **START JARVIS** dabao. Phir bolo: "Jarvis, YouTube kholo".

Jawab ke baad 12 second tak bina "Jarvis" bole agla command de sakte ho.

## Kya-kya kar sakta hai

Apps kholna, call, SMS, WhatsApp message, alarm/timer, torch, volume, brightness,
music control, YouTube/Google search, settings kholna, Home/Back/Recents/lock/screenshot,
notifications padhna, aur kisi bhi app me screen padh kar tap / type / scroll karna.

## Code ka naksha

| File | Kaam |
|---|---|
| `MainActivity.kt` | Settings screen (API key, voice, permissions) |
| `JarvisService.kt` | Background: sunna, wake word, bolna |
| `GeminiClient.kt` | Gemini API + function calling + Jarvis ka persona |
| `PhoneActions.kt` | Saare phone actions (naya action yahin jodo) |
| `JarvisAccessibility.kt` | Screen padhna, tap, type, scroll |
| `NotifListener.kt` | Notifications |

## Limits

- Lock screen ka PIN/pattern voice se nahi khul sakta (Android allow nahi karta).
- Banking/UPI apps apni screen accessibility se chhupa leti hain.
- Xiaomi/Realme/Vivo jaise phones me Autostart on aur battery "No restrictions" karna padta hai, warna background me band ho jata hai.
- Kuch phones me har baar sunna shuru hone par halki beep aati hai (Google speech engine ki).
