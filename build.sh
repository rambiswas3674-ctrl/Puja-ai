#!/usr/bin/env bash
# PUJA AI - build helper. Isse Ubuntu (proot-distro) ke ANDAR chalao:   bash build.sh
# Optional: ARM64 phone par  AAPT2=/path/to/arm64/aapt2 bash build.sh
set -e
cd "$(dirname "$0")"

GRADLE_VERSION=8.7
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

say() { echo; echo "==> $*"; }

# 1) Java 17
if ! command -v java >/dev/null 2>&1; then
  echo "Java nahi mila. Chalao: apt update && apt install -y openjdk-17-jdk-headless unzip wget"
  exit 1
fi
JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JV" -lt 17 ] 2>/dev/null; then
  echo "Java 17 ya naya chahiye (abhi: $JV). apt install -y openjdk-17-jdk-headless"
  exit 1
fi
say "Java $JV mil gaya"

# 2) Android SDK command-line tools
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  say "Android command-line tools download ho rahe hain..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  wget -O /tmp/cmdtools.zip "$CMDLINE_URL"
  unzip -q -o /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -d "$ANDROID_HOME/platforms/android-34" ] || [ ! -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
  say "SDK packages install ho rahe hain (platform 34, build-tools 34.0.0)..."
  yes | "$SDKM" --licenses >/dev/null 2>&1 || true
  "$SDKM" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
fi
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 3) Gradle 8.7 (apt wala gradle purana hota hai, isliye khud download)
GH="$HOME/gradle-$GRADLE_VERSION"
if [ ! -x "$GH/bin/gradle" ]; then
  say "Gradle $GRADLE_VERSION download ho raha hai..."
  wget -O /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q -o /tmp/gradle.zip -d "$HOME"
fi

# 4) ARM64 check: Google ka aapt2 x86-64 hai
EXTRA=()
if [ -n "$AAPT2" ]; then
  [ -x "$AAPT2" ] || { echo "AAPT2 file executable nahi hai: $AAPT2"; exit 1; }
  EXTRA+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
  say "ARM64 aapt2 use ho raha hai: $AAPT2"
elif [ "$(uname -m)" = "aarch64" ]; then
  echo
  echo "⚠️  Aapka phone ARM64 hai. Gradle ka default aapt2 x86-64 hai aur yahan 'Exec format error' dega."
  echo "    Ya to ARM64 aapt2 download karke:  AAPT2=/path/aapt2 bash build.sh"
  echo "    Ya GitHub Actions se build karo (README_HI.md dekho) - yeh sabse bharosemand hai."
  echo "    Phir bhi build try kar raha hoon..."
fi

# 5) Build
say "Debug APK ban rahi hai (pehli baar 10-30 min lag sakte hain)..."
"$GH/bin/gradle" assembleDebug --no-daemon --stacktrace "${EXTRA[@]}"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  say "✅ APK ban gayi: $(pwd)/$APK"
  if [ -d /sdcard/Download ]; then
    cp "$APK" /sdcard/Download/PUJA-AI-debug.apk && echo "Copy ho gayi: /sdcard/Download/PUJA-AI-debug.apk (Downloads folder me dekho)"
  else
    echo "Shared storage nahi mili. Termux se copy karo: cp $(pwd)/$APK ~/storage/downloads/"
  fi
else
  echo "❌ APK nahi bani. Upar ke error dekho aur README_HI.md ka Troubleshooting padho."
  exit 1
fi
