-- Step 27: Examination system (no tournaments/teams — owner checkpoint pending).
-- Server-side timing, sections, eligibility, result publication, audit trail.

CREATE TABLE exams (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                  TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    title_bn               TEXT,
    instructions           TEXT NOT NULL DEFAULT '' CHECK (length(instructions) <= 8000),
    instructions_bn        TEXT,
    status                 TEXT NOT NULL DEFAULT 'draft'
                           CHECK (status IN ('draft', 'scheduled', 'open', 'closed', 'archived')),
    visibility             TEXT NOT NULL DEFAULT 'public'
                           CHECK (visibility IN ('public', 'unlisted')),
    -- Window when attempts may start
    opens_at               TIMESTAMPTZ NOT NULL,
    closes_at              TIMESTAMPTZ NOT NULL,
    duration_seconds       INT NOT NULL CHECK (duration_seconds BETWEEN 60 AND 28800),
    max_attempts           INT NOT NULL DEFAULT 1 CHECK (max_attempts BETWEEN 1 AND 20),
    -- Eligibility (null = open to all authenticated learners)
    require_email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    subject_id             UUID REFERENCES subjects (id) ON DELETE SET NULL,
    -- Presentation
    shuffle_questions      BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options        BOOLEAN NOT NULL DEFAULT FALSE,
    -- Result publication: immediate | after_close | manual
    results_policy         TEXT NOT NULL DEFAULT 'after_close'
                           CHECK (results_policy IN ('immediate', 'after_close', 'manual')),
    results_published_at   TIMESTAMPTZ,
    pass_percent           INT CHECK (pass_percent IS NULL OR pass_percent BETWEEN 1 AND 100),
    created_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (closes_at > opens_at)
);
CREATE INDEX exams_listing_idx ON exams (status, opens_at);

CREATE TABLE exam_sections (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id       UUID NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    position      INT NOT NULL CHECK (position >= 1),
    title         TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    title_bn      TEXT,
    instructions  TEXT NOT NULL DEFAULT '' CHECK (length(instructions) <= 2000),
    time_limit_seconds INT CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 30 AND 14400),
    UNIQUE (exam_id, position)
);

CREATE TABLE exam_section_questions (
    section_id   UUID NOT NULL REFERENCES exam_sections (id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position     INT NOT NULL CHECK (position >= 1),
    marks_override NUMERIC(5, 2) CHECK (marks_override IS NULL OR marks_override > 0),
    PRIMARY KEY (section_id, question_id),
    UNIQUE (section_id, position)
);

CREATE TABLE exam_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    attempt_number  INT NOT NULL CHECK (attempt_number >= 1),
    status          TEXT NOT NULL DEFAULT 'in_progress'
                    CHECK (status IN ('in_progress', 'submitted', 'expired', 'void')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline_at     TIMESTAMPTZ NOT NULL,
    submitted_at    TIMESTAMPTZ,
    score           NUMERIC(10, 2),
    max_score       NUMERIC(10, 2),
    percent         NUMERIC(6, 2),
    passed          BOOLEAN,
    UNIQUE (exam_id, user_id, attempt_number)
);
CREATE INDEX exam_attempts_user_idx ON exam_attempts (user_id, started_at DESC);
CREATE INDEX exam_attempts_exam_idx ON exam_attempts (exam_id, status);

CREATE TABLE exam_answers (
    attempt_id   UUID NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    selected     TEXT[] NOT NULL DEFAULT '{}',
    text_answer  TEXT,
    client_key   TEXT,
    result       TEXT CHECK (result IS NULL OR result IN ('correct', 'incorrect', 'partial', 'unanswered', 'ungraded')),
    marks_awarded NUMERIC(8, 2),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (attempt_id, question_id)
);
CREATE UNIQUE INDEX exam_answers_client_key_uq
    ON exam_answers (attempt_id, client_key) WHERE client_key IS NOT NULL;

-- Append-only audit trail (interruption recovery, admin review).
CREATE TABLE exam_audit_events (
    id          BIGSERIAL PRIMARY KEY,
    exam_id     UUID NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    attempt_id  UUID REFERENCES exam_attempts (id) ON DELETE SET NULL,
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    event_type  TEXT NOT NULL,
    detail      JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX exam_audit_exam_idx ON exam_audit_events (exam_id, id);
CREATE INDEX exam_audit_attempt_idx ON exam_audit_events (attempt_id, id);
