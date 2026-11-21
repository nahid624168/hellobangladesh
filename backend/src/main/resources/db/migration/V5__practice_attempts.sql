-- Step 5: practice attempts. One row per "check answer" call by a learner.
-- Practice results are NOT scores: nothing here may feed points, badges, streaks or leaderboards (Milestone 9
-- must read quiz attempts, which are server-timed, not this table).
CREATE TABLE practice_attempts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id         UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    question_version    INT NOT NULL CHECK (question_version >= 1),
    selected_option_ids UUID[] NOT NULL DEFAULT '{}',
    answer_text         TEXT,
    result              TEXT NOT NULL CHECK (result IN ('correct', 'incorrect', 'ungraded')),
    awarded_marks       NUMERIC(6, 2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX practice_attempts_user_idx ON practice_attempts (user_id, created_at DESC);
CREATE INDEX practice_attempts_user_question_idx ON practice_attempts (user_id, question_id, created_at DESC);
