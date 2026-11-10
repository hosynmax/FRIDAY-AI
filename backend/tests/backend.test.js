import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { createChatProvider } from "../provider.js";
import { createConversationStore } from "../storage.js";

test("Ollama provider sends chat and extracts response", async () => {
  let requested;
  const provider = createChatProvider({ AI_PROVIDER: "ollama", OLLAMA_BASE_URL: "http://localhost:11434", OLLAMA_MODEL: "qwen2.5:3b" }, async (url, options) => {
    requested = { url, body: JSON.parse(options.body) };
    return new Response(JSON.stringify({ message: { content: "سلام" } }), { status: 200 });
  });
  assert.equal(await provider.chat([{ role: "user", content: "سلام" }]), "سلام");
  assert.equal(requested.url, "http://localhost:11434/api/chat");
  assert.equal(requested.body.model, "qwen2.5:3b");
});

test("OpenAI-compatible provider keeps API key on server and parses completion", async () => {
  let headers;
  const provider = createChatProvider({
    AI_PROVIDER: "openai-compatible",
    OPENAI_COMPATIBLE_BASE_URL: "https://example.test/v1",
    OPENAI_COMPATIBLE_API_KEY: "server-secret",
    OPENAI_COMPATIBLE_MODEL: "test-model"
  }, async (_url, options) => {
    headers = options.headers;
    return new Response(JSON.stringify({ choices: [{ message: { content: "Ready." } }] }), { status: 200 });
  });
  assert.equal(await provider.chat([{ role: "user", content: "Hello" }]), "Ready.");
  assert.equal(headers.Authorization, "Bearer server-secret");
});

test("conversation storage persists and deletes conversations", async () => {
  const dir = await mkdtemp(join(tmpdir(), "jarvis-store-"));
  try {
    const file = join(dir, "conversations.json");
    const store = createConversationStore(file);
    const conversation = await store.create("تست");
    assert.equal((await store.append(conversation.id, { role: "user", content: "سلام" })).messages.length, 1);
    const reopened = createConversationStore(file);
    assert.equal((await reopened.get(conversation.id)).messages[0].content, "سلام");
    assert.equal(await reopened.delete(conversation.id), true);
    assert.equal(await reopened.get(conversation.id), null);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
