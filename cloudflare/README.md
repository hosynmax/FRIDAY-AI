# FRIDAY AI Cloudflare backend

This is the online, server-side AI backend. It runs on Cloudflare Workers + Workers AI + D1. The Android app sends requests to `https://<worker>.<account>.workers.dev/v1/chat`; provider credentials never go in the APK.

## What is implemented
- Persian/English chat using Cloudflare-hosted `@cf/openai/gpt-oss-20b` by default.
- D1 usage accounting and settings.
- Rate limit binding: 12 chat calls per minute per client IP and Cloudflare location (approximate).
- Daily request guard, editable model, output-token limit, system prompt and maintenance switch.
- Owner-only web admin at `/admin` with a password stored as a Cloudflare secret and a signed short-lived admin token.
- HTTPS on the workers.dev hostname.

## Free tier reality
Cloudflare Workers currently documents 100,000 requests/day on Workers Free. Workers AI currently includes 10,000 Neurons/day at no charge; that is an inference budget, not unlimited AI. The default GPT-OSS 20B model is stronger than a tiny model but can consume the available daily allocation quickly. Actual capacity depends on prompt length, answer length, model and current account limits. See official pages:
- https://developers.cloudflare.com/workers/platform/limits/
- https://developers.cloudflare.com/workers-ai/platform/pricing/
- https://developers.cloudflare.com/workers-ai/models/gpt-oss-20b/

A free plan is suitable for a controlled beta, not a promise of hundreds of thousands of active users.

## One-time account setup
Cloudflare account creation and acceptance of its terms must be done by the account owner. This repository cannot create or own a third-party account for you.

1. Create/sign in to Cloudflare and enable Workers AI in the dashboard.
2. Install Node.js 20+ and Wrangler: `npm install -g wrangler`, then `wrangler login`.
3. In `cloudflare/wrangler.toml`, leave the D1 name as `friday-ai`. Create the DB:
   ```bash
   cd cloudflare
   wrangler d1 create friday-ai
   ```
   Copy the returned `database_id` into `wrangler.toml` where the placeholder is.
4. Initialize the DB:
   ```bash
   wrangler d1 execute friday-ai --remote --file=./migrations/0001_init.sql
   ```
5. Create secrets. Choose a strong unique admin password. Generate its SHA-256 hash locally:
   ```bash
   printf %s 'YOUR-STRONG-ADMIN-PASSWORD' | sha256sum
   wrangler secret put ADMIN_PASSWORD_SHA256
   wrangler secret put ADMIN_JWT_SECRET
   ```
   Paste only the hash into the first prompt and a random 32+ character secret into the second. Never put either value in Git, chat, or the Android app.
6. Deploy:
   ```bash
   wrangler deploy
   ```
7. Verify `https://YOUR-WORKER.workers.dev/health`. Put that **base HTTPS URL** in FRIDAY AI → Settings. The app adds `/v1/chat` itself.
8. Open `https://YOUR-WORKER.workers.dev/admin` and sign in with the password you chose.

## GitHub Actions deployment (optional)
Add repository Actions secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`, and update the D1 ID in the file. Then run the workflow **Deploy Cloudflare backend** manually from GitHub Actions. The token needs permission to edit Workers, use Workers AI and manage D1. Do not create a broad account token if a scoped token is available.

## Limitations and safety
- This first online beta deliberately does not collect accounts, email addresses, or payment details. Chat use is anonymous and protected by IP/location rate limiting plus a daily cap. Before public launch, add per-user authentication, consent/privacy documents, account deletion, abuse controls and billing.
- Rate-limit counters are approximate and local to each Cloudflare location, not a single worldwide strict quota.
- Do not expose model or admin secrets to the client. The Android app contains no inference key.
- Conversation history stays on the device in this version; only usage counts and lengths are stored in D1, not message text.
