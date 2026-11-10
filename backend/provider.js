export function createChatProvider(env = process.env, fetchImpl = fetch) {
  const provider = (env.AI_PROVIDER || "ollama").toLowerCase();
  const timeoutMs = Number(env.MODEL_TIMEOUT_MS || 120000);

  if (provider === "ollama") {
    const baseUrl = (env.OLLAMA_BASE_URL || "http://ollama:11434").replace(/\/$/, "");
    const model = env.OLLAMA_MODEL || "qwen2.5:3b";
    return {
      name: "ollama",
      model,
      async chat(messages) {
        const response = await fetchImpl(baseUrl + "/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            model,
            stream: false,
            messages: [
              { role: "system", content: env.FRIDAY_SYSTEM_PROMPT || "You are JARVIS, a helpful, careful multilingual assistant. Reply in the user's language." },
              ...messages
            ],
            options: { temperature: 0.65 }
          }),
          signal: AbortSignal.timeout(timeoutMs)
        });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error("MODEL_UNAVAILABLE");
        const reply = body?.message?.content;
        if (typeof reply !== "string" || !reply.trim()) throw new Error("EMPTY_MODEL_RESPONSE");
        return reply.trim();
      }
    };
  }

  if (provider === "openai-compatible") {
    const baseUrl = (env.OPENAI_COMPATIBLE_BASE_URL || "").replace(/\/$/, "");
    const apiKey = env.OPENAI_COMPATIBLE_API_KEY || "";
    const model = env.OPENAI_COMPATIBLE_MODEL || "gpt-4o-mini";
    if (!baseUrl) throw new Error("OPENAI_COMPATIBLE_BASE_URL_REQUIRED");
    return {
      name: "openai-compatible",
      model,
      async chat(messages) {
        const headers = { "Content-Type": "application/json" };
        if (apiKey) headers.Authorization = "Bearer " + apiKey;
        const response = await fetchImpl(baseUrl + "/chat/completions", {
          method: "POST",
          headers,
          body: JSON.stringify({
            model,
            messages: [
              { role: "system", content: env.FRIDAY_SYSTEM_PROMPT || "You are JARVIS, a helpful, careful multilingual assistant. Reply in the user's language." },
              ...messages
            ],
            temperature: 0.65
          }),
          signal: AbortSignal.timeout(timeoutMs)
        });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error("MODEL_UNAVAILABLE");
        const reply = body?.choices?.[0]?.message?.content;
        if (typeof reply !== "string" || !reply.trim()) throw new Error("EMPTY_MODEL_RESPONSE");
        return reply.trim();
      }
    };
  }

  throw new Error("UNSUPPORTED_AI_PROVIDER");
}
