-- Step 14b: learner question bookmarks and content reports.
-- Bookmarks are private to the user. Reports are for moderators (admin later).

CREATE TABLE question_bookmarks (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, question_id)
);
CREATE INDEX question_bookmarks_user_idx ON question_bookmarks (user_id, created_at DESC);

CREATE TABLE question_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    reason      TEXT NOT NULL CHECK (reason IN (
        'wrong_answer', 'unclear', 'offensive', 'typo', 'duplicate', 'other'
    )),
    details     TEXT NOT NULL DEFAULT '' CHECK (length(details) <= 2000),
    status      TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewed', 'dismissed')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX question_reports_question_idx ON question_reports (question_id, created_at DESC);
CREATE INDEX question_reports_user_idx ON question_reports (user_id, created_at DESC);
-- One open report per user per question to reduce spam
CREATE UNIQUE INDEX question_reports_open_unique
    ON question_reports (user_id, question_id) WHERE status = 'open';
