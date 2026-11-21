-- Step 24: Contest scheduling, participation, server-scored answers, leaderboard.
-- PostgreSQL is the source of truth; Redis acceleration is optional (interface in code).

CREATE TABLE contests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                 TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    title_bn              TEXT,
    description           TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 4000),
    description_bn        TEXT,
    status                TEXT NOT NULL DEFAULT 'draft'
                          CHECK (status IN ('draft', 'scheduled', 'live', 'ended', 'cancelled')),
    -- Visibility: published contests appear in learner lists when scheduled/live/ended.
    visibility            TEXT NOT NULL DEFAULT 'public'
                          CHECK (visibility IN ('public', 'unlisted')),
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,
    join_opens_at         TIMESTAMPTZ,                 -- null = join allowed from creation until starts_at
    join_closes_at        TIMESTAMPTZ,                 -- null = join allowed until starts_at
    capacity              INT CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 1000000),
    duration_seconds      INT NOT NULL CHECK (duration_seconds BETWEEN 30 AND 14400),
    -- After join, participant deadline = least(joined_at + duration, ends_at)
    shuffle_questions     BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options       BOOLEAN NOT NULL DEFAULT FALSE,
    show_leaderboard      BOOLEAN NOT NULL DEFAULT TRUE,
    show_results_at       TIMESTAMPTZ,                 -- null = after ends_at
    tie_break             TEXT NOT NULL DEFAULT 'earliest_submit'
                          CHECK (tie_break IN ('earliest_submit', 'fastest_time', 'none')),
    max_attempts          INT NOT NULL DEFAULT 1 CHECK (max_attempts BETWEEN 1 AND 10),
    subject_id            UUID REFERENCES subjects (id) ON DELETE SET NULL,
    created_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX contests_listing_idx ON contests (status, starts_at);
CREATE INDEX contests_live_idx ON contests (status, ends_at) WHERE status IN ('scheduled', 'live');

CREATE TABLE contest_questions (
    contest_id   UUID NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position     INT NOT NULL CHECK (position >= 1),
    marks_override NUMERIC(5, 2) CHECK (marks_override IS NULL OR marks_override > 0),
    PRIMARY KEY (contest_id, question_id),
    UNIQUE (contest_id, position)
);

CREATE TABLE contest_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contest_id      UUID NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'joined'
                    CHECK (status IN ('joined', 'in_progress', 'submitted', 'expired', 'disqualified')),
    attempt_number  INT NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    deadline_at     TIMESTAMPTZ,
    submitted_at    TIMESTAMPTZ,
    score           NUMERIC(10, 2),
    max_score       NUMERIC(10, 2),
    percent         NUMERIC(6, 2),
    -- Tie-break helpers
    time_taken_ms   BIGINT,
    UNIQUE (contest_id, user_id, attempt_number)
);
CREATE INDEX contest_participants_user_idx ON contest_participants (user_id, joined_at DESC);
CREATE INDEX contest_participants_contest_idx ON contest_participants (contest_id, status);
CREATE INDEX contest_participants_rank_idx ON contest_participants (contest_id, score DESC NULLS LAST, submitted_at ASC NULLS LAST)
    WHERE status = 'submitted';

-- Idempotent answer rows: one row per (participant, question). Upsert on submit.
CREATE TABLE contest_answers (
    participant_id  UUID NOT NULL REFERENCES contest_participants (id) ON DELETE CASCADE,
    question_id     UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    selected        TEXT[] NOT NULL DEFAULT '{}',
    text_answer     TEXT,
    -- Client-supplied idempotency key for a single answer write (optional).
    client_key      TEXT,
    result          TEXT CHECK (result IS NULL OR result IN ('correct', 'incorrect', 'partial', 'unanswered', 'ungraded')),
    marks_awarded   NUMERIC(8, 2),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (participant_id, question_id)
);
CREATE UNIQUE INDEX contest_answers_client_key_uq
    ON contest_answers (participant_id, client_key) WHERE client_key IS NOT NULL;

-- Anti-abuse: simple join/submit rate tracking is application-side (RateLimiter);
-- this table records disqualification reasons for audit.
CREATE TABLE contest_abuse_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contest_id      UUID NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    participant_id  UUID REFERENCES contest_participants (id) ON DELETE SET NULL,
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX contest_abuse_user_idx ON contest_abuse_events (user_id, created_at DESC);
