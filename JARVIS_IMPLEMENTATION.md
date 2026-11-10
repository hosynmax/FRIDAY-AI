# JARVIS AI — implementation status and architecture

This repository is being upgraded from FRIDAY AI into JARVIS AI. The app remains a native Android application built with Kotlin and Jetpack Compose.

## Product direction

- Persian-first, multilingual-ready personal assistant.
- Offline-first experience with an optional self-hosted inference server.
- Google web search through Android's browser/search intents.
- Optional cloud AI providers only through a secure backend proxy; never embed provider secrets in the APK.
- Voice input through Android speech recognition and spoken replies through Android TextToSpeech.
- Local conversation history and user-configurable server endpoint.
- Full-screen cinematic interface with display-cutout-aware layout.

## Implemented in the JARVIS foundation branch

- Rebranded launcher label and primary interface from FRIDAY AI to JARVIS AI.
- Updated the hero/title copy and voice prompt.
- Enabled short-edge display cutout layout for modern camera cutouts.
- Applied display-cutout insets to the root scaffold to keep content clear of notches.
- Preserved the existing SharedPreferences namespace so current settings and chat history remain readable.
- Disabled Android cloud backup for local chat/settings data.
- Updated Android compile/target SDK to API 36 and lowered minSdk to API 26.
- Added GitHub Actions workflow to run unit tests, build a debug APK, and upload it as an artifact.

## Current functional baseline

- Android speech recognition is invoked through the system recognizer. Availability and offline language packs depend on the device.
- Android TextToSpeech speaks assistant responses; installed voice data determines language availability.
- Google search opens the system search/browser. This is not a Google Search API integration and does not scrape search results inside the app.
- Chat requests are sent to the configured HTTPS backend at `/v1/chat`.
- The existing backend uses Ollama and Qwen2.5 on hardware controlled by the user. This is self-hosted inference, not on-device inference; the phone needs network access to reach that server.
- No OpenAI/ChatGPT account session is embedded or reused by the app.

## Next engineering milestones

1. Add a provider abstraction and connection diagnostics for local/self-hosted Ollama and optional OpenAI-compatible backend providers.
2. Add a secure backend authentication token flow and rate limiting before public deployment.
3. Add on-device model runtime as a separate optional provider after device-memory and model-size profiling. Do not claim phone-local offline inference until implemented and tested.
4. Replace the legacy icon with original JARVIS branding and add animated assistant states (idle/listening/thinking/speaking/error).
5. Add persistent encrypted memory, explicit memory controls, and a searchable conversation archive.
6. Add Android App Bundle release signing through GitHub Actions secrets; never store signing keys or API keys in Git.
7. Add instrumented UI tests and physical-device testing across cutout, gesture-navigation, font-scale, and screen-size configurations.

## Build

The repository currently does not include the Gradle wrapper. CI installs Gradle 8.11.1 and Android SDK 36, then runs:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

For local builds, use JDK 17, Android SDK 36, and Gradle 8.11.1 or use Android Studio's compatible Gradle installation.

## Required user-owned resources before production

- A publicly reachable HTTPS backend host for remote Ollama inference.
- Optional provider API credentials stored only on the backend, if cloud AI providers are enabled.
- A domain and TLS certificate for the backend.
- A Google Play developer account and Play signing configuration for store publication.
