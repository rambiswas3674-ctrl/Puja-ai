# PUJA AI — Android personal AI assistant (Hindi/Hinglish)

Package: `com.puja.ai` · Kotlin · Gemini API · Voice (Hindi) · Safe phone automation

> ⚠️ **Zaroori sach:** Is project ka code likha gaya hai aur statically check kiya gaya hai
> (XML sahi hai, resource/ID references match karte hain), lekin **Android SDK ke bina
> compile/build nahi chalaya gaya**, aur app kisi phone par **test nahi hui**. Isliye
> pehli build me chhoti compile error aa sakti hai — error message mujhe bhejo, theek kar dunga.

---

## 1. Kya kya hai (implemented)

- Gemini chat (streaming), local chat history, clear + export (.txt)
- Hindi/Hinglish voice: SpeechRecognizer (mic) + TextToSpeech, mic animation, voice reply toggle
- Commands (bina AI ke bhi, offline): app kholna, website, YouTube search, alarm, task add/dikhao, "yaad rakho", settings pages, call (dialer)
- AI se structured JSON actions (allowlist + validation): calendar event, SMS draft, file save/pick, accessibility click/scroll, etc.
- Confirmation dialog: SMS draft, file save, screen click
- Memory (aap dekho/delete karo), tasks, privacy screen, "saara data delete"
- Code answers me syntax-block + Copy button
- Errors: galat API key, quota, offline, server error, timeout, mic permission denied, retry button

## 2. Jo nahi hai / Android allow nahi karta

- **Wake word ("Puja" bolte hi sunna)**: reliable aur transparent tarike se nahi banta, isliye nahi banaya. Foreground mic service bhi nahi.
- Notification access: implement nahi hua.
- Payments, password, OTP, banking: jaan-bujhkar band. App kisi doosre app ka private data nahi padhti.
- Accessibility sirf tab jab aap Android Settings me khud ON karo. Protected screens (password fields) skip hote hain.
- "Kal subah reminder" jaise kaam: Gemini date/time nikalke Calendar app kholta hai — aapko wahan Save dabana hota hai.
- Alarm: Clock app khulti hai, confirm aapko karna hota hai.
- Chat history ek hi continuous conversation hai (multiple sessions nahi).
- Gemini model naam Settings me badla ja sakta hai (default `gemini-2.5-flash`). Agar Google ne model hata diya ho to naya naam daalo.

## 3. API key aur security

1. https://aistudio.google.com/apikey se free key lo.
2. App > ⋮ menu > Settings > key paste karo > **Test karo**.
3. Key sirf phone ke app-private storage me rehti hai (`allowBackup=false`). Code me koi key nahi hai. Key log ya error message me nahi aati.

**Limitation:** Agar aap key ko APK ke andar daaloge aur APK doosron ko doge, to koi bhi key nikaal sakta hai. Public release ke liye apna **backend proxy** banao (key server par rakho, app proxy ko call kare). Personal use me user-entered key theek hai.

## 4. Build ka sabse bharosemand tarika: GitHub Actions (phone se)

ARM64 phone par Google ka `aapt2` (x86-64) nahi chalta, isliye Termux me local build mushkil hai (section 5). GitHub ke server par build aasaan hai:

```bash
# Termux me
pkg update -y && pkg install -y git unzip
termux-setup-storage
cd ~ && unzip ~/storage/downloads/puja-ai.zip
cd puja-ai
git init -b main
git add .
git commit -m "PUJA AI"
git remote add origin https://github.com/<APKA_USERNAME>/puja-ai.git   # pehle github.com par khaali repo banao
git push -u origin main    # password ki jagah Personal Access Token (repo scope) daalo
```

Phir GitHub repo > **Actions** > "Build PUJA AI debug APK" > run khatam hone par neeche **Artifacts → puja-ai-debug-apk** download karo (zip me `app-debug.apk`). Install karne ke liye "Unknown sources" allow karo.

## 5. Local build: Termux + Ubuntu (proot-distro)

**Dhyan:** ARM64 phone par yeh tabhi chalega jab aapke paas ARM64 `aapt2` ho (community builds milte hain, jaise "android-sdk-tools aarch64 aapt2" search karo). Maine yeh path test nahi kiya.

```bash
# --- Termux me ---
pkg update -y && pkg upgrade -y
pkg install -y proot-distro unzip wget git
termux-setup-storage                       # permission "Allow" karo
proot-distro install ubuntu
proot-distro login ubuntu --bind /storage/emulated/0:/sdcard

# --- ab Ubuntu ke andar ---
apt update && apt install -y openjdk-17-jdk-headless unzip wget zip
cd ~ && unzip /sdcard/Download/puja-ai.zip
cd puja-ai
bash build.sh                              # ARM64 aapt2 ho to:  AAPT2=/root/aapt2 bash build.sh
```

`build.sh` ye karta hai: Java 17 check → Android cmdline-tools + platform 34 + build-tools 34.0.0 install → Gradle 8.7 download → `gradle assembleDebug` → APK ko `/sdcard/Download/PUJA-AI-debug.apk` me copy.

**APK ka exact path:** `~/puja-ai/app/build/outputs/apk/debug/app-debug.apk`

APK dhundhne ka command:
```bash
find ~/puja-ai -name "*.apk"
```

## 6. Troubleshooting

| Problem | Hal |
|---|---|
| `Exec format error` (aapt2) | ARM64 phone. GitHub Actions use karo ya `AAPT2=...` do. |
| `Unsupported class file major version` / Java error | `java -version` = 17 hona chahiye. `apt install openjdk-17-jdk-headless` |
| `SDK location not found` | `build.sh` se chalao (yeh `local.properties` banata hai) ya `echo "sdk.dir=$HOME/android-sdk" > local.properties` |
| `Failed to install the following SDK components / licenses` | `yes \| ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses` |
| `OutOfMemoryError` / Gradle kill | `gradle.properties` me `-Xmx1536m` ko `-Xmx1024m` karo; baaki apps band karo; `--no-daemon` already hai |
| `No space left on device` | 3–4 GB free rakho. `rm -rf ~/.gradle/caches` (purane cache), Termux me `pkg clean` |
| Download atak jaye / network error | Wi-Fi par karo; `build.sh` dobara chalao (resume se aage badhta hai) |
| App me "API key galat" | Settings me key dobara paste karo, Test dabao |
| Voice nahi chal rahi | Mic permission do; Google app / Speech Services update karo |
| Hindi awaaz nahi | Phone Settings > Text-to-speech > Google TTS > Hindi voice data download |

## 7. Project structure

```
puja-ai/
├── settings.gradle.kts  build.gradle.kts  gradle.properties  .gitignore
├── build.sh   README_HI.md   .github/workflows/build.yml
└── app/
    ├── build.gradle.kts  proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/puja/ai/
        │   ├── PujaApp.kt
        │   ├── ai/          GeminiService.kt  AiRepository.kt
        │   ├── automation/  Action.kt  CommandParser.kt  ActionExecutor.kt
        │   │                AppLauncher.kt  AutomationAccessibilityService.kt
        │   ├── data/        Models.kt  SettingsRepository.kt  ChatRepository.kt  LocalMemoryRepository.kt
        │   ├── voice/       VoiceManager.kt
        │   └── ui/          Splash/Main/Voice/History/Settings/Automation/Memory/About Activity,
        │                    ChatViewModel.kt  ChatAdapter.kt  UiUtil.kt
        └── res/             layout/ drawable/ values/ values-night/ xml/
```

Gradle wrapper (`gradlew`) shaamil nahi hai (binary jar bina internet ke nahi ban sakta). `build.sh` / GitHub Actions khud Gradle 8.7 laate hain. Versions: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 26, Java 17.

## 8. Use kaise karein

- Chat: "Mujhe Python ka code likh kar do", "Hindi me translate karo…"
- Voice: ⋮ menu ya mic icon > mic dabao > "YouTube kholo" / "alarm 7 baje lagao" / "task add dudh lana" / "yaad rakho ki mujhe chai pasand hai"
- Automation screen: kaun sa feature chalu hai, permissions, aur privacy.
