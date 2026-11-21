-- Step 30b: Admin content tooling — destructive-action tokens + import batches.
-- Question/academic status columns already exist (V3/V4). Contests/exams already have status.

CREATE TABLE destructive_action_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action          TEXT NOT NULL,          -- e.g. archive_question, delete_subject, cancel_contest
    resource_type   TEXT NOT NULL,
    resource_id     TEXT NOT NULL,
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX destructive_tokens_actor_idx ON destructive_action_tokens (actor_id, created_at DESC);
CREATE INDEX destructive_tokens_expiry_idx ON destructive_action_tokens (expires_at) WHERE used_at IS NULL;

CREATE TABLE import_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    source          TEXT NOT NULL DEFAULT 'json',  -- json | csv (csv reserved)
    status          TEXT NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'partial')),
    total_rows      INT NOT NULL DEFAULT 0,
    success_count   INT NOT NULL DEFAULT 0,
    error_count     INT NOT NULL DEFAULT 0,
    errors          JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX import_batches_actor_idx ON import_batches (actor_id, created_at DESC);

-- Optional review notes on questions (editor/moderator workflow).
ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS review_note TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
