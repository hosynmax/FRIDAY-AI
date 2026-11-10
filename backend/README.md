# FRIDAY AI — Free local-model backend

This backend uses **Ollama + Qwen2.5**. It does not require OpenAI, Gemini, Claude, or any paid inference API key. Model inference runs on hardware you control.

## Requirements
- Docker Engine + Docker Compose
- A host with enough RAM/CPU (GPU recommended). Qwen2.5 3B is the default lightweight model; 7B generally needs more memory and responds more slowly on CPU.

## Start
From the repository root:
```bash
docker compose up -d --build
docker compose exec ollama ollama pull qwen2.5:3b
```
Check readiness:
```bash
curl http://localhost:8080/health
```
Set the app's server base URL to your **public HTTPS URL** (for example, `https://ai.your-domain.example`), not a GitHub URL and not `/v1/chat`. The Android app appends `/v1/chat` itself.

## API
`POST /v1/chat`
```json
{"messages":[{"role":"user","content":"سلام"}]}
```
Response:
```json
{"reply":"سلام! چطور می‌توانم کمک کنم؟","provider":"local-ollama","model":"qwen2.5:3b"}
```

## Security and cost
- Ollama is intentionally not published to the host; only the FRIDAY API is exposed.
- Put the API behind HTTPS (Caddy/Nginx/managed reverse proxy) before using it over the internet.
- Restrict CORS to your app's web origins if needed; Android native apps do not use browser CORS enforcement.
- Configure firewall, request limits, monitoring, and preferably an application access token before public launch.
- No API inference fees, but server hosting, electricity, bandwidth, and storage may cost money.
- Do not promise unlimited capacity: throughput depends on your machine.
- Do not commit secrets or .env files.
