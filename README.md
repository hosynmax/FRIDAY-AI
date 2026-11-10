# JARVIS AI

Native Android personal AI assistant, upgraded from the original FRIDAY-AI project.

## Android application

- Kotlin + Jetpack Compose.
- Persian-first chat interface with locally persisted conversation history.
- Android speech recognition and TextToSpeech integration.
- Google web search via the device browser/search intent.
- Optional HTTPS connection to a self-hosted Ollama backend.
- Full-screen immersive presentation with display-cutout-aware layout.
- Package ID: `com.friday.assistant` (kept stable to preserve upgrade compatibility).
- Minimum Android: 8.0 / API 26.
- Compile SDK / target SDK: API 36.

## Current AI connectivity

The Android app sends chat requests to the configured HTTPS backend base URL and appends `/v1/chat`.
The existing backend uses Ollama with Qwen2.5. Model inference runs on hardware you control; it is not yet an on-device Android model and the phone must reach the server over a network.

Google search currently opens the system search/browser. It is not an embedded Google Search API integration.
Optional cloud AI providers are planned behind a secure backend proxy. Do not put provider API keys in the APK.

## Start the free self-hosted model backend

Requirements: Docker Compose and a host with sufficient RAM/CPU (GPU recommended).

```bash
docker compose up -d --build
docker compose exec ollama ollama pull qwen2.5:3b
curl http://localhost:8080/health
```

Deploy the API behind HTTPS and enter its base URL (for example `https://ai.example.com`) in the app. Do not enter a GitHub URL, and do not expose Ollama port 11434 publicly.

See [backend setup](backend/README.md) and [JARVIS implementation status](JARVIS_IMPLEMENTATION.md).

## Build

The repository currently does not include the Gradle wrapper. Use JDK 17, Android SDK 36 and Gradle 8.11.1:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

GitHub Actions runs tests and builds a debug APK on pushes to `feature/**` and `main`, and on pull requests. The APK is uploaded as the `jarvis-ai-debug-apk` workflow artifact.

## Production requirements

- HTTPS backend hosting, authentication, request limits and monitoring.
- Optional cloud provider keys stored only on the backend.
- Release signing credentials stored in GitHub Actions Secrets.
- Google Play developer account and store listing/privacy disclosures.
- Physical-device QA for microphone, speech packs, display cutouts, and Android 16 behavior.

Never commit API keys, signing keys, or `.env` secrets.