# FRIDAY AI

Android app (Kotlin + Jetpack Compose) with a Node.js AI backend.

## Android
- Package: `com.friday.assistant`
- Minimum Android: 13 (API 33)
- Compile/target SDK: 35
- Persian chat interface, locally saved chat history, speech-to-text via Android speech recognition, and text-to-speech replies.
- Configure the backend HTTPS base URL in Settings. The app calls `POST /v1/chat`.

## Backend
1. Use Node.js 20 or newer.
2. Copy `backend/.env.example` to `backend/.env` and set `OPENAI_API_KEY`.
3. In `backend/`, run `npm install` then `npm start`.
4. Deploy behind HTTPS and enter its base URL in the Android app.

Never put provider API keys in the Android project or APK.

## Build APK
Open in Android Studio with JDK 17 and Android SDK 35, then run:
```bash
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds a debug APK artifact on pushes to `main`.
