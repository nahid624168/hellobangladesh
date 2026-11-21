-- Step 28: Analytics snapshots + gamification (server-only point grants).
-- Clients never write points/badges; all mutations go through AnalyticsService.

CREATE TABLE user_stats (
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_points         BIGINT NOT NULL DEFAULT 0 CHECK (total_points >= 0),
    practice_answered    INT NOT NULL DEFAULT 0,
    practice_correct     INT NOT NULL DEFAULT 0,
    quiz_attempts        INT NOT NULL DEFAULT 0,
    contest_submissions  INT NOT NULL DEFAULT 0,
    flashcard_reviews    INT NOT NULL DEFAULT 0,
    exam_attempts        INT NOT NULL DEFAULT 0,
    current_streak_days  INT NOT NULL DEFAULT 0 CHECK (current_streak_days >= 0),
    longest_streak_days  INT NOT NULL DEFAULT 0 CHECK (longest_streak_days >= 0),
    last_active_date     DATE,
    daily_goal_target    INT NOT NULL DEFAULT 20 CHECK (daily_goal_target BETWEEN 1 AND 500),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_daily_activity (
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    activity_date  DATE NOT NULL,
    answered       INT NOT NULL DEFAULT 0,
    correct        INT NOT NULL DEFAULT 0,
    points_earned  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, activity_date)
);

CREATE TABLE point_ledger (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount       INT NOT NULL CHECK (amount <> 0),
    reason       TEXT NOT NULL, -- practice_correct, quiz_complete, contest_place, badge, daily_goal, ...
    ref_type     TEXT,
    ref_id       UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX point_ledger_user_idx ON point_ledger (user_id, id DESC);
-- Anti-fraud: one grant per (user, reason, ref) when ref is present
CREATE UNIQUE INDEX point_ledger_idempotent_uq
    ON point_ledger (user_id, reason, ref_type, ref_id)
    WHERE ref_id IS NOT NULL;

CREATE TABLE badges (
    code         TEXT PRIMARY KEY,
    title        TEXT NOT NULL,
    title_bn     TEXT,
    description  TEXT NOT NULL DEFAULT '',
    description_bn TEXT,
    points_award INT NOT NULL DEFAULT 0 CHECK (points_award >= 0)
);

CREATE TABLE user_badges (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_code  TEXT NOT NULL REFERENCES badges (code),
    earned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_code)
);

CREATE TABLE subject_performance (
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_id       UUID NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    answered         INT NOT NULL DEFAULT 0,
    correct          INT NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, subject_id)
);

INSERT INTO badges (code, title, title_bn, description, description_bn, points_award) VALUES
  ('first_practice', 'First Steps', 'প্রথম পদক্ষেপ', 'Answered your first practice question.', 'প্রথম প্র্যাকটিস প্রশ্নের উত্তর দিয়েছেন।', 10),
  ('streak_3', 'On a Roll', 'ধারাবাহিক', '3-day study streak.', '৩ দিনের স্টাডি স্ট্রিক।', 25),
  ('streak_7', 'Week Warrior', 'সপ্তাহের যোদ্ধা', '7-day study streak.', '৭ দিনের স্টাডি স্ট্রিক।', 50),
  ('quiz_master', 'Quiz Master', 'কুইজ মাস্টার', 'Completed 5 quizzes.', '৫টি কুইজ সম্পন্ন।', 40),
  ('contest_entrant', 'Competitor', 'প্রতিযোগী', 'Submitted a contest attempt.', 'প্রতিযোগিতায় অংশগ্রহণ।', 30);
