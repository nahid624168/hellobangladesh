-- Step 32: AI gateway. Consent, usage log (no prompt or answer text), conversation history.

CREATE TABLE ai_consents (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    version      TEXT NOT NULL,
    granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);

-- One row per gateway call. Never stores the prompt, the answer, note text, keys or tokens.
CREATE TABLE ai_usage (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task               TEXT NOT NULL,
    status             TEXT NOT NULL CHECK (status IN ('ok', 'blocked', 'provider_error')),
    input_chars        INT NOT NULL DEFAULT 0,
    output_chars       INT NOT NULL DEFAULT 0,
    prompt_tokens      INT NOT NULL DEFAULT 0,
    completion_tokens  INT NOT NULL DEFAULT 0,
    latency_ms         INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ai_usage_user_day_idx ON ai_usage (user_id, created_at DESC);
CREATE INDEX ai_usage_day_idx ON ai_usage (created_at DESC);

-- Chat history the user can read and delete. Only chat/tutor tasks are saved.
CREATE TABLE ai_conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task        TEXT NOT NULL,
    title       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ai_conversations_user_idx ON ai_conversations (user_id, updated_at DESC);

CREATE TABLE ai_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    role             TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    content          TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ai_messages_conv_idx ON ai_messages (conversation_id, created_at);
