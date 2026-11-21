-- Step 15: user-owned quiz sets and server-timed, server-scored attempts.
-- Quiz sets are separate entities (one row each). They are private to their owner; official/admin quizzes come with the admin panel.
CREATE TABLE quiz_sets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    description       TEXT CHECK (description IS NULL OR length(description) <= 1000),
    lesson_id         UUID REFERENCES lessons (id) ON DELETE SET NULL,
    status            TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'private', 'archived')),
    time_limit_seconds INT CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 30 AND 14400),
    passing_percent   INT CHECK (passing_percent IS NULL OR passing_percent BETWEEN 1 AND 100),
    max_attempts      INT CHECK (max_attempts IS NULL OR max_attempts BETWEEN 1 AND 100),
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options   BOOLEAN NOT NULL DEFAULT FALSE,
    show_explanations BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMPTZ,                 -- soft delete: attempt history is kept
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX quiz_sets_owner_idx ON quiz_sets (owner_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE quiz_set_questions (
    quiz_set_id UUID NOT NULL REFERENCES quiz_sets (id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position    INT NOT NULL CHECK (position >= 1),
    PRIMARY KEY (quiz_set_id, question_id),
    UNIQUE (quiz_set_id, position)
);

CREATE TABLE quiz_attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    quiz_set_id        UUID NOT NULL REFERENCES quiz_sets (id) ON DELETE CASCADE,
    status             TEXT NOT NULL CHECK (status IN ('in_progress', 'submitted', 'expired')),
    started_at         TIMESTAMPTZ NOT NULL,
    deadline_at        TIMESTAMPTZ,
    submitted_at       TIMESTAMPTZ,
    score              NUMERIC(8, 2),
    max_score          NUMERIC(8, 2),
    percent            NUMERIC(5, 2),
    passed             BOOLEAN,
    passing_percent    INT,                         -- frozen at start so later edits cannot change an old result
    show_explanations  BOOLEAN NOT NULL,            -- frozen at start
    questions_snapshot JSONB NOT NULL               -- the questions exactly as served (no answers), in the order served
);
-- At most one running attempt per learner and quiz set; two simultaneous "start" calls cannot create two.
CREATE UNIQUE INDEX quiz_attempts_one_running_uq ON quiz_attempts (user_id, quiz_set_id) WHERE status = 'in_progress';
CREATE INDEX quiz_attempts_user_set_idx ON quiz_attempts (user_id, quiz_set_id, started_at DESC);

-- Deliberately no foreign key on question_id: history must survive an admin deleting a question.
CREATE TABLE quiz_attempt_answers (
    attempt_id          UUID NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    position            INT NOT NULL CHECK (position >= 1),
    question_id         UUID NOT NULL,
    selected_option_ids UUID[] NOT NULL DEFAULT '{}',
    answer_text         TEXT,
    result              TEXT NOT NULL CHECK (result IN ('correct', 'incorrect', 'ungraded', 'unanswered')),
    awarded_marks       NUMERIC(6, 2) NOT NULL,
    PRIMARY KEY (attempt_id, position)
);
