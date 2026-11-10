# FRIDAY AI — Native Android

A native Android assistant starter project built with Kotlin, Jetpack Compose and Material 3.

## Build
GitHub Actions builds a debug APK automatically on every push to `main` and on manual workflow dispatch.

- Minimum SDK: 33 (Android 13)
- Target SDK: 35
- Package: `com.friday.assistant`
- UI: Persian-first RTL

## Download APK
Open the repository's **Actions** tab, select the latest successful **Build FRIDAY AI APK** run, and download the `FRIDAY-AI-debug-apk` artifact.

## Current scope
- Native Android application shell
- Persian RTL assistant interface
- Local chat UI and settings
- Internet and microphone permission declarations
- CI workflow for debug APK

Not yet included: production AI provider connectivity, always-on hotword recognition, overlay assistant, calendar/task integrations, or release signing. These require additional implementation, runtime testing, and secure backend configuration. No API keys belong in the Android app.

## Build locally
Use Android Studio with JDK 17 and Android SDK 35, then run:

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
