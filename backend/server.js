import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";

const app = express();
app.disable("x-powered-by");
app.use(cors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(",") : false }));
app.use(express.json({ limit: "256kb" }));
app.use(rateLimit({ windowMs: 60_000, limit: Number(process.env.RATE_LIMIT_PER_MINUTE || 20), standardHeaders: true, legacyHeaders: false }));

const port = Number(process.env.PORT || 8080);
const ollamaUrl = (process.env.OLLAMA_BASE_URL || "http://ollama:11434").replace(/\/$/, "");
const model = process.env.OLLAMA_MODEL || "qwen2.5:3b";
const systemPrompt = process.env.FRIDAY_SYSTEM_PROMPT || "You are FRIDAY, a helpful, careful multilingual assistant. Reply in the user's language, including Persian when the user writes Persian. Be honest about uncertainty.";

app.get("/health", async (_req, res) => {
  let modelReady = false;
  try {
    const r = await fetch(ollamaUrl + "/api/tags", { signal: AbortSignal.timeout(2500) });
    modelReady = r.ok;
  } catch {}
  res.status(200).json({ ok: true, service: "FRIDAY AI API", provider: "local-ollama", model, modelServerReachable: modelReady });
});

app.post("/v1/chat", async (req, res) => {
  const messages = req.body?.messages;
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > 30) {
    return res.status(400).json({ error: "messages must be a non-empty array with at most 30 entries." });
  }
  const normalized = [];
  for (const m of messages) {
    if (!["user", "assistant"].includes(m?.role) || typeof m?.content !== "string" || !m.content.trim()) {
      return res.status(400).json({ error: "Each message needs a valid role and non-empty content." });
    }
    normalized.push({ role: m.role, content: m.content.slice(0, 6000) });
  }
  try {
    const response = await fetch(ollamaUrl + "/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, stream: false, messages: [{ role: "system", content: systemPrompt }, ...normalized], options: { temperature: 0.65 } }),
      signal: AbortSignal.timeout(Number(process.env.MODEL_TIMEOUT_MS || 120000))
    });
    const raw = await response.text();
    if (!response.ok) {
      console.error("ollama_error", response.status, raw.slice(0, 400));
      return res.status(503).json({ error: "مدل هوش مصنوعی فعلاً در دسترس نیست. سرور مدل را بررسی کنید.", code: "MODEL_UNAVAILABLE" });
    }
    let data;
    try { data = JSON.parse(raw); } catch {
      return res.status(502).json({ error: "پاسخ مدل معتبر نبود.", code: "INVALID_MODEL_RESPONSE" });
    }
    const reply = data?.message?.content;
    if (typeof reply !== "string" || !reply.trim()) return res.status(502).json({ error: "مدل پاسخ متنی برنگرداند.", code: "EMPTY_MODEL_RESPONSE" });
    return res.json({ reply, provider: "local-ollama", model });
  } catch (error) {
    console.error("chat_error", error?.name || "Error", error?.message || "");
    const timeout = error?.name === "TimeoutError" || error?.name === "AbortError";
    return res.status(timeout ? 504 : 503).json({ error: timeout ? "پاسخ‌گویی مدل بیش از حد طول کشید." : "اتصال به سرور مدل برقرار نشد.", code: timeout ? "MODEL_TIMEOUT" : "MODEL_CONNECTION_FAILED" });
  }
});

app.listen(port, "0.0.0.0", () => console.log(`FRIDAY local AI API listening on ${port}; model=${model}`));
