CREATE TABLE IF NOT EXISTS app_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS usage_logs (
  id TEXT PRIMARY KEY,
  model TEXT NOT NULL,
  input_chars INTEGER NOT NULL,
  output_chars INTEGER NOT NULL,
  ip_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_usage_logs_created_at ON usage_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_usage_logs_model ON usage_logs(model);

INSERT OR IGNORE INTO app_settings(key,value) VALUES
  ('model','@cf/openai/gpt-oss-20b'),
  ('max_tokens','800'),
  ('maintenance','false'),
  ('daily_limit','250'),
  ('system_prompt','You are FRIDAY, a capable and careful bilingual AI assistant. Reply in the same language as the user. Be accurate, explain calculations clearly, state uncertainty, and never invent current facts.');
