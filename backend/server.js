import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { createChatProvider } from "./provider.js";
import { createConversationStore } from "./storage.js";

export const app = express();
app.disable("x-powered-by");
const allowedOrigins = process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(",").map(value => value.trim()) : false;
app.use(cors({ origin: allowedOrigins }));
app.use(express.json({ limit: "256kb" }));
app.use(rateLimit({ windowMs: 60_000, limit: Number(process.env.RATE_LIMIT_PER_MINUTE || 20), standardHeaders: true, legacyHeaders: false }));

const provider = createChatProvider();
const store = createConversationStore();

function requireAccessToken(req, res, next) {
  const expected = process.env.API_ACCESS_TOKEN;
  if (!expected) return next();
  const supplied = req.get("authorization")?.replace(/^Bearer\\s+/i, "");
  if (supplied !== expected) return res.status(401).json({ error: "دسترسی غیرمجاز است.", code: "UNAUTHORIZED" });
  return next();
}

app.get("/health", async (_req, res) => {
  let modelReady = true;
  if (provider.name === "ollama") {
    try {
      const base = (process.env.OLLAMA_BASE_URL || "http://ollama:11434").replace(/\\/$/, "");
      const response = await fetch(base + "/api/tags", { signal: AbortSignal.timeout(2500) });
      modelReady = response.ok;
    } catch { modelReady = false; }
  }
  res.status(200).json({ ok: true, service: "JARVIS AI API", provider: provider.name, model: provider.model, modelServerReachable: modelReady });
});

app.use("/v1", requireAccessToken);

app.post("/v1/chat", async (req, res) => {
  const messages = req.body?.messages;
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > 30) {
    return res.status(400).json({ error: "messages must be a non-empty array with at most 30 entries." });
  }
  const normalized = [];
  for (const message of messages) {
    if (!["user", "assistant"].includes(message?.role) || typeof message?.content !== "string" || !message.content.trim()) {
      return res.status(400).json({ error: "Each message needs a valid role and non-empty content." });
    }
    normalized.push({ role: message.role, content: message.content.slice(0, 6000) });
  }
  try {
    const reply = await provider.chat(normalized);
    return res.json({ reply, provider: provider.name, model: provider.model });
  } catch (error) {
    const timeout = error?.name === "TimeoutError" || error?.name === "AbortError";
    const unavailable = error?.message === "MODEL_UNAVAILABLE";
    console.error("chat_error", error?.message || "unknown");
    return res.status(timeout ? 504 : 503).json({
      error: timeout ? "پاسخ‌گویی مدل بیش از حد طول کشید." : "موتور هوش مصنوعی فعلاً در دسترس نیست.",
      code: timeout ? "MODEL_TIMEOUT" : unavailable ? "MODEL_UNAVAILABLE" : "MODEL_ERROR"
    });
  }
});

app.get("/v1/conversations", async (_req, res) => res.json({ conversations: await store.list() }));
app.post("/v1/conversations", async (req, res) => {
  const title = typeof req.body?.title === "string" ? req.body.title : "گفت‌وگوی جدید";
  res.status(201).json({ conversation: await store.create(title) });
});
app.get("/v1/conversations/:id", async (req, res) => {
  const conversation = await store.get(req.params.id);
  if (!conversation) return res.status(404).json({ error: "گفت‌وگو پیدا نشد." });
  return res.json({ conversation });
});
app.post("/v1/conversations/:id/messages", async (req, res) => {
  const { role, content } = req.body || {};
  if (!["user", "assistant"].includes(role) || typeof content !== "string" || !content.trim()) {
    return res.status(400).json({ error: "پیام معتبر نیست." });
  }
  const conversation = await store.append(req.params.id, { role, content: content.slice(0, 6000) });
  if (!conversation) return res.status(404).json({ error: "گفت‌وگو پیدا نشد." });
  return res.status(201).json({ conversation });
});
app.delete("/v1/conversations/:id", async (req, res) => {
  const deleted = await store.delete(req.params.id);
  return res.status(deleted ? 204 : 404).end();
});

if (process.env.NODE_ENV !== "test") {
  const port = Number(process.env.PORT || 8080);
  app.listen(port, "0.0.0.0", () => console.log(`JARVIS AI API listening on ${port}; provider=${provider.name}; model=${provider.model}`));
}
