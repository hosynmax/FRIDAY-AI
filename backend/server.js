import "dotenv/config";
import express from "express";
import cors from "cors";
import OpenAI from "openai";

const app = express();
app.disable("x-powered-by");
app.use(cors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(",") : false }));
app.use(express.json({ limit: "1mb" }));

const port = Number(process.env.PORT || 8080);
const client = process.env.OPENAI_API_KEY ? new OpenAI({ apiKey: process.env.OPENAI_API_KEY }) : null;

app.get("/health", (_req, res) => res.json({ ok: true, service: "FRIDAY AI API" }));

app.post("/v1/chat", async (req, res) => {
  try {
    if (!client) return res.status(503).json({ error: "AI provider is not configured on the server." });
    const messages = req.body?.messages;
    if (!Array.isArray(messages) || messages.length === 0 || messages.length > 40) {
      return res.status(400).json({ error: "messages must be a non-empty array (max 40)." });
    }
    const normalized = messages.map((m) => {
      if (!["user", "assistant"].includes(m?.role) || typeof m?.content !== "string") throw new Error("Invalid message format.");
      return { role: m.role, content: m.content.slice(0, 12000) };
    });
    const completion = await client.chat.completions.create({
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      messages: [
        { role: "system", content: process.env.FRIDAY_SYSTEM_PROMPT || "You are FRIDAY, a precise bilingual Persian and English personal assistant. Reply in the user's language." },
        ...normalized
      ],
      temperature: 0.7
    });
    res.json({ reply: completion.choices[0]?.message?.content || "" });
  } catch (error) {
    console.error("chat_error", error);
    res.status(500).json({ error: "The assistant could not complete the request." });
  }
});

app.listen(port, "0.0.0.0", () => console.log("FRIDAY API listening on " + port));
