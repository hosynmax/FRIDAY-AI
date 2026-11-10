import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { randomUUID } from "node:crypto";

export function createConversationStore(filePath = process.env.JARVIS_DATA_FILE || "./data/conversations.json") {
  let queue = Promise.resolve();

  async function readState() {
    try {
      const parsed = JSON.parse(await readFile(filePath, "utf8"));
      return { conversations: Array.isArray(parsed.conversations) ? parsed.conversations : [] };
    } catch (error) {
      if (error.code === "ENOENT") return { conversations: [] };
      throw error;
    }
  }

  async function writeState(state) {
    await mkdir(dirname(filePath), { recursive: true });
    const temporary = filePath + ".tmp";
    await writeFile(temporary, JSON.stringify(state, null, 2), { mode: 0o600 });
    await rename(temporary, filePath);
  }

  function mutate(operation) {
    const task = queue.then(async () => {
      const state = await readState();
      const result = await operation(state);
      await writeState(state);
      return result;
    });
    queue = task.catch(() => {});
    return task;
  }

  return {
    async list() {
      await queue;
      const { conversations } = await readState();
      return conversations.map(({ id, title, updatedAt, messages }) => ({
        id, title, updatedAt, messageCount: messages.length
      })).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    },
    async get(id) {
      await queue;
      const { conversations } = await readState();
      return conversations.find(item => item.id === id) || null;
    },
    create(title = "گفت‌وگوی جدید") {
      return mutate(state => {
        const now = new Date().toISOString();
        const conversation = { id: randomUUID(), title: String(title).slice(0, 120), createdAt: now, updatedAt: now, messages: [] };
        state.conversations.push(conversation);
        return conversation;
      });
    },
    append(id, message) {
      return mutate(state => {
        const conversation = state.conversations.find(item => item.id === id);
        if (!conversation) return null;
        conversation.messages.push({ ...message, createdAt: new Date().toISOString() });
        conversation.messages = conversation.messages.slice(-200);
        conversation.updatedAt = new Date().toISOString();
        return conversation;
      });
    },
    delete(id) {
      return mutate(state => {
        const index = state.conversations.findIndex(item => item.id === id);
        if (index < 0) return false;
        state.conversations.splice(index, 1);
        return true;
      });
    }
  };
}
