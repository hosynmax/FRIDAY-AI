const DEFAULT_MODEL = "@cf/openai/gpt-oss-20b";
const ALLOWED_MODELS = new Set([
  "@cf/openai/gpt-oss-20b",
  "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
]);
const DEFAULT_PROMPT = "You are FRIDAY, a capable and careful bilingual AI assistant. Reply in the same language as the user's latest message: Persian (Farsi) or English. Be accurate, explain calculations clearly, state uncertainty, never invent current facts, and ask a focused question only when essential. Do not claim to have browsed the web or performed actions unless tools actually did so.";

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,OPTIONS",
  "access-control-allow-headers": "Content-Type,Authorization",
  "access-control-max-age": "86400",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff"
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "content-type": "application/json; charset=utf-8" }
  });
}

async function setting(db, key, fallback) {
  if (!db) return fallback;
  const row = await db.prepare("SELECT value FROM app_settings WHERE key = ?").bind(key).first();
  return row?.value ?? fallback;
}

async function saveSetting(db, key, value) {
  await db.prepare("INSERT INTO app_settings(key,value,updated_at) VALUES(?,?,datetime('now')) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=datetime('now')")
    .bind(key, String(value)).run();
}

function b64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function decodeB64url(text) {
  const normalized = text.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

async function hmacKey(secret) {
  return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

async function issueAdminToken(secret) {
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = b64url(new TextEncoder().encode(JSON.stringify({ sub: "owner", role: "owner", exp: Math.floor(Date.now() / 1000) + 14400 })));
  const input = header + "." + payload;
  const signature = await crypto.subtle.sign("HMAC", await hmacKey(secret), new TextEncoder().encode(input));
  return input + "." + b64url(new Uint8Array(signature));
}

async function isOwner(request, secret) {
  try {
    if (!secret) return false;
    const token = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
    const parts = token.split(".");
    if (parts.length !== 3) return false;
    const input = parts[0] + "." + parts[1];
    const valid = await crypto.subtle.verify("HMAC", await hmacKey(secret), decodeB64url(parts[2]), new TextEncoder().encode(input));
    if (!valid) return false;
    const payload = JSON.parse(new TextDecoder().decode(decodeB64url(parts[1])));
    return payload.role === "owner" && payload.exp > Math.floor(Date.now() / 1000);
  } catch { return false; }
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

async function handleChat(request, env) {
  if (!env.AI) return json({ error: "AI binding is not configured." }, 503);
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  if (env.API_LIMITER) {
    const limit = await env.API_LIMITER.limit({ key: ip });
    if (!limit.success) return json({ error: "تعداد درخواست‌ها زیاد است؛ یک دقیقه بعد دوباره تلاش کن.", code: "RATE_LIMITED" }, 429);
  }
  if (!env.DB) return json({ error: "Database binding is not configured." }, 503);
  const maintenance = await setting(env.DB, "maintenance", "false");
  if (maintenance === "true") return json({ error: "سرویس موقتاً در حال تعمیر است.", code: "MAINTENANCE" }, 503);

  const limit = Math.min(1000, Math.max(10, Number(await setting(env.DB, "daily_limit", "250")) || 250));
  const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM usage_logs WHERE created_at >= datetime('now','start of day')").first();
  if ((count?.n || 0) >= limit) return json({ error: "سهمیه روزانه سرویس به پایان رسیده است.", code: "DAILY_QUOTA" }, 429);

  let body;
  try { body = await request.json(); } catch { return json({ error: "بدنه درخواست JSON معتبر نیست." }, 400); }
  if (!Array.isArray(body?.messages) || body.messages.length < 1 || body.messages.length > 20) {
    return json({ error: "messages باید بین ۱ تا ۲۰ پیام داشته باشد." }, 400);
  }
  const normalized = [];
  for (const message of body.messages) {
    if (!["user", "assistant"].includes(message?.role) || typeof message?.content !== "string" || !message.content.trim()) {
      return json({ error: "ساختار پیام نامعتبر است." }, 400);
    }
    if (message.content.length > 6000) return json({ error: "هر پیام حداکثر ۶۰۰۰ نویسه است." }, 413);
    normalized.push({ role: message.role, content: message.content.trim() });
  }

  const model = await setting(env.DB, "model", DEFAULT_MODEL);
  if (!ALLOWED_MODELS.has(model)) return json({ error: "مدل تنظیم‌شده مجاز نیست." }, 500);
  const maxTokens = Math.min(2048, Math.max(128, Number(await setting(env.DB, "max_tokens", "800")) || 800));
  const systemPrompt = await setting(env.DB, "system_prompt", DEFAULT_PROMPT);

  try {
    const result = await env.AI.run(model, {
      messages: [{ role: "system", content: systemPrompt }, ...normalized],
      max_tokens: maxTokens,
      temperature: 0.55
    });
    const reply = typeof result?.response === "string" ? result.response.trim() : "";
    if (!reply) return json({ error: "مدل پاسخ متنی معتبر برنگرداند.", code: "EMPTY_MODEL_RESPONSE" }, 502);

    const lastUser = [...normalized].reverse().find(message => message.role === "user")?.content || "";
    await env.DB.prepare("INSERT INTO usage_logs(id,model,input_chars,output_chars,ip_hash,created_at) VALUES(?,?,?,?,?,datetime('now'))")
      .bind(crypto.randomUUID(), model, lastUser.length, reply.length, (await sha256Hex(ip)).slice(0, 20)).run();

    return json({ reply, provider: "cloudflare-workers-ai", model });
  } catch (error) {
    console.error("workers_ai_error", error?.message || "unknown");
    return json({ error: "مدل هوش مصنوعی موقتاً پاسخ نمی‌دهد. کمی بعد دوباره تلاش کن.", code: "MODEL_UNAVAILABLE" }, 503);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "FRIDAY AI", provider: "Cloudflare Workers AI", model: await setting(env.DB, "model", DEFAULT_MODEL) });
    }
    if (request.method === "GET" && url.pathname === "/v1/config/public") {
      return json({ appName: "FRIDAY AI", minAndroidApi: 33, maintenanceMode: (await setting(env.DB, "maintenance", "false")) === "true" });
    }
    if (request.method === "GET" && (url.pathname === "/admin" || url.pathname === "/admin/")) {
      if (!env.ASSETS) return new Response("Admin static asset binding is missing.", { status: 503 });
      return env.ASSETS.fetch(new Request(new URL("/admin/index.html", request.url), request));
    }
    if (request.method === "POST" && url.pathname === "/admin/login") {
      if (!env.ADMIN_PASSWORD_SHA256 || !env.ADMIN_JWT_SECRET) return json({ error: "پنل مدیر هنوز پیکربندی نشده است." }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ error: "درخواست نامعتبر است." }, 400); }
      const supplied = await sha256Hex(String(body?.password || ""));
      if (supplied.length !== env.ADMIN_PASSWORD_SHA256.length || supplied !== env.ADMIN_PASSWORD_SHA256) {
        return json({ error: "رمز عبور مدیر اشتباه است." }, 401);
      }
      return json({ token: await issueAdminToken(env.ADMIN_JWT_SECRET), expiresIn: 14400 });
    }
    if (url.pathname.startsWith("/admin/api/")) {
      if (!(await isOwner(request, env.ADMIN_JWT_SECRET))) return json({ error: "دسترسی مدیر لازم است." }, 401);
      if (!env.DB) return json({ error: "Database binding is missing." }, 503);
      if (request.method === "GET" && url.pathname === "/admin/api/stats") {
        const total = await env.DB.prepare("SELECT COUNT(*) AS n FROM usage_logs").first();
        const daily = await env.DB.prepare("SELECT COUNT(*) AS n FROM usage_logs WHERE created_at >= datetime('now','-1 day')").first();
        const recent = await env.DB.prepare("SELECT model,input_chars,output_chars,created_at FROM usage_logs ORDER BY created_at DESC LIMIT 20").all();
        return json({
          totalRequests: total?.n || 0,
          requests24h: daily?.n || 0,
          settings: {
            model: await setting(env.DB, "model", DEFAULT_MODEL),
            maxTokens: Number(await setting(env.DB, "max_tokens", "800")),
            systemPrompt: await setting(env.DB, "system_prompt", DEFAULT_PROMPT),
            maintenance: (await setting(env.DB, "maintenance", "false")) === "true"
          },
          recent: recent?.results || []
        });
      }
      if (request.method === "POST" && url.pathname === "/admin/api/settings") {
        let body;
        try { body = await request.json(); } catch { return json({ error: "درخواست نامعتبر است." }, 400); }
        const model = String(body.model || DEFAULT_MODEL);
        const maxTokens = Number(body.maxTokens);
        const prompt = String(body.systemPrompt || DEFAULT_PROMPT).slice(0, 4000);
        if (!ALLOWED_MODELS.has(model)) return json({ error: "مدل انتخاب‌شده مجاز نیست." }, 400);
        if (!Number.isInteger(maxTokens) || maxTokens < 128 || maxTokens > 2048) return json({ error: "حد توکن باید بین ۱۲۸ تا ۲۰۴۸ باشد." }, 400);
        await saveSetting(env.DB, "model", model);
        await saveSetting(env.DB, "max_tokens", maxTokens);
        await saveSetting(env.DB, "system_prompt", prompt);
        await saveSetting(env.DB, "maintenance", body.maintenance === true ? "true" : "false");
        return json({ ok: true });
      }
    }
    if (request.method === "POST" && url.pathname === "/v1/chat") return handleChat(request, env);
    return json({ error: "مسیر پیدا نشد." }, 404);
  }
};
