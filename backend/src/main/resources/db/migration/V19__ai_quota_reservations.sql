-- Atomic daily AI quota reservations. Rows contain counters only, never prompts or responses.
CREATE TABLE ai_user_quota_daily (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day_start TIMESTAMPTZ NOT NULL,
    requests_reserved INT NOT NULL DEFAULT 0 CHECK (requests_reserved >= 0),
    tokens_reserved BIGINT NOT NULL DEFAULT 0 CHECK (tokens_reserved >= 0),
    PRIMARY KEY (user_id, day_start)
);

CREATE TABLE ai_global_quota_daily (
    day_start TIMESTAMPTZ PRIMARY KEY,
    tokens_reserved BIGINT NOT NULL DEFAULT 0 CHECK (tokens_reserved >= 0)
);
