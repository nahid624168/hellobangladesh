-- Step 12b: Global search helpers (recent queries + optional FTS indexes).
-- Privacy: private notes and user-owned sets are filtered in application SQL by owner_id.

CREATE TABLE search_recent (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    query_text  TEXT NOT NULL CHECK (length(btrim(query_text)) BETWEEN 1 AND 120),
    searched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, query_text)
);
CREATE INDEX search_recent_user_time_idx ON search_recent (user_id, searched_at DESC);

-- Lightweight trigram-friendly indexes when pg_trgm is available; plain btree on lower(title) as baseline.
CREATE INDEX IF NOT EXISTS subjects_title_lower_idx ON subjects (lower(title));
CREATE INDEX IF NOT EXISTS lessons_title_lower_idx ON lessons (lower(title));
CREATE INDEX IF NOT EXISTS questions_text_lower_idx ON questions (lower(left(question_text, 200)));
