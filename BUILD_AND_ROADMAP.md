# FRIDAY AI — Build and implementation notes

## Build pipeline
1. GitHub Actions checks out this repository on pushes to main or manual dispatch.
2. It provisions Temurin JDK 17 and Gradle 8.10.2 on Ubuntu.
3. `gradle assembleDebug` resolves Android Gradle Plugin and Android SDK platform/build-tools, then compiles the native Android app.
4. The generated APK is uploaded as the `FRIDAY-AI-debug-apk` artifact.
5. Download it from Actions → latest successful run → Artifacts.

## Product roadmap
- Milestone 1: native Android shell, Persian RTL chat UI, settings, automated debug APK build.
- Milestone 2: secure backend (Node.js/TypeScript), authentication, provider abstraction for OpenAI/Gemini/Claude, streaming chat, encrypted server-side secrets.
- Milestone 3: Room conversation history, DataStore preferences, voice input/output, SpeechRecognizer and Android TextToSpeech.
- Milestone 4: reminders/tasks/calendar intents, explicit confirmation for sensitive actions, optional foreground service and user-approved overlay.
- Milestone 5: automated tests, accessibility, privacy review, signed release APK/AAB.

Always-on wake-word listening is battery- and policy-sensitive and must be explicitly enabled with a visible foreground notification. It is not part of this first milestone.

## Security
Never put provider API keys, signing passwords, or private credentials in source code, APK resources, Git history, or logs. Use GitHub Actions secrets and a backend for provider keys. Release signing must use encrypted secrets and a protected workflow.
