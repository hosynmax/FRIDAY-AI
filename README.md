# FRIDAY AI

Native Android app (Kotlin + Jetpack Compose) and a self-hosted AI backend.

## Android
- Package: `com.friday.assistant`
- Minimum Android: 13 (API 33)
- Persian-first chat UI, local chat history, Android speech recognition and text-to-speech.
- In Settings, enter the backend **HTTPS base URL only**, e.g. `https://ai.example.com`. The app appends `/v1/chat` automatically. Do not enter a GitHub repository URL.

## Free AI backend (no paid API key)
The backend uses Ollama with the open-weight Qwen2.5 model. Inference runs on your own server; no OpenAI/Gemini/Claude API keys are required.
See [backend setup](backend/README.md).

Quick start from repository root:
```bash
docker compose up -d --build
docker compose exec ollama ollama pull qwen2.5:3b
```
Deploy behind HTTPS and enter that HTTPS base URL in the Android app.

## Important
- "Free model" means no per-request AI API bill; hosting, electricity and hardware are still required.
- Never expose Ollama port 11434 publicly.
- Add an HTTPS reverse proxy, firewall and app authentication before public deployment.
- Do not commit secrets.

## Build APK
Open in Android Studio with JDK 17 and Android SDK 35, then run `./gradlew assembleDebug`.
