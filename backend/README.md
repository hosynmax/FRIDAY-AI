# JARVIS AI — modular backend

The backend separates the Android client from model providers and keeps credentials on the server.

## Model engine
- Default: local Ollama, default model `qwen2.5:3b`.
- Optional: any OpenAI-compatible chat-completions endpoint.
- Switch with `AI_PROVIDER=ollama` or `AI_PROVIDER=openai-compatible`.
- For the compatible provider set `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_MODEL`, and optionally `OPENAI_COMPATIBLE_API_KEY`.
- Never put provider keys in the Android app, repository, or client settings.

## Run
Requires Node.js 20+. Run `npm install`, `npm test`, then `npm start` from this directory.
For local inference, run Ollama and pull `qwen2.5:3b`; set `OLLAMA_BASE_URL` if it is not at `http://ollama:11434`.

## API
- `GET /health` — provider readiness.
- `POST /v1/chat` — `{"messages":[{"role":"user","content":"سلام"}]}`.
- `GET /v1/conversations`, `POST /v1/conversations`, `GET /v1/conversations/:id`.
- `POST /v1/conversations/:id/messages`, `DELETE /v1/conversations/:id`.

## Storage and security
- Server data persists in `JARVIS_DATA_FILE` (default `./data/conversations.json`) using atomic file replacement.
- The Android app separately stores recent history on-device.
- Set `API_ACCESS_TOKEN` to require Bearer authentication for all `/v1/*` endpoints.
- Set `CORS_ORIGIN` to a comma-separated allowlist for browser clients. Rate limiting is enabled.
- Put public deployments behind HTTPS; never expose the Ollama port publicly.
- API keys remain server-side and are never returned to Android.

Local inference avoids per-request API charges, but hosting, hardware, power, bandwidth and storage may cost money.
