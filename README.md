# FRIDAY AI

**FRIDAY AI (فرایدی)** is a Persian-first native Android AI assistant with a server-backed model gateway and a permissioned command/action engine.

> Understands, plans, and acts only through explicit, validated Android capabilities and user approval where required.

## Product documents
- [Product Blueprint (FA)](docs/PRODUCT_BLUEPRINT_FA.md)
- [UX Design System (FA)](docs/UX_DESIGN_SYSTEM_FA.md)
- [AI and Action Architecture (FA)](docs/AI_ACTION_ARCHITECTURE_FA.md)
- [Command Catalog (FA)](docs/COMMAND_CATALOG_FA.md)
- [Implementation Roadmap (FA)](docs/IMPLEMENTATION_ROADMAP_FA.md)
- [Master Build Prompt (FA)](BUILD_PROMPT_FA.md)

## Current project baseline
- Native Android app: Kotlin + Jetpack Compose.
- Package: com.friday.assistant.
- Current minimum Android: API 33.
- Persian-first chat, local conversation history in the current app, Android speech recognition and Text-to-Speech.
- Server-backed chat endpoint: POST /v1/chat.
- Two backend paths are present: self-hosted Ollama and Cloudflare Workers AI with D1.
- GitHub Actions can build a debug APK and upload an artifact when the workflow succeeds.

## Development direction
The next engineering step is to evolve the current chat baseline into the architecture in the product documents: modular Android layers, Room/DataStore, streaming chat, a versioned AI gateway, typed ActionPlans, an allowlisted DeviceActionRegistry, explicit permission and confirmation flows, and automated tests.

The command engine must never execute arbitrary model-generated code. Android actions must use supported public APIs and preserve user control.

## Backend options
### Self-hosted Ollama
See backend/README.md. Inference runs on hardware you control. Model quality and speed depend on the chosen model and server hardware.

### Cloudflare Workers AI
See cloudflare/README.md. The service is not live until the account owner configures D1, secrets and deployment. Free-tier capacity is limited and does not mean unlimited inference.

## Build the Android APK
Open this repository in Android Studio with a compatible JDK and Android SDK, or run the GitHub Actions workflow named Build FRIDAY AI APK. The APK exists only after a successful build. Download it from the successful workflow run’s Artifacts section.

## Security
- Never commit .env, API keys, admin passwords, signing credentials or access tokens.
- Do not expose Ollama directly to the public internet.
- Public backend deployments require HTTPS, authentication, rate limits and monitoring.
- Do not claim device actions completed until Android confirms the result.
- Microphone, location, files, contacts and notifications require transparent permission flows.
