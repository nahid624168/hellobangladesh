-- Step 33c: private, owner-scoped AI quiz drafts. These are deliberately separate
-- from the official question bank and from admin-published questions.
CREATE TABLE ai_generated_quiz_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    description TEXT CHECK (description IS NULL OR length(description) <= 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ai_generated_quiz_sets_owner_idx ON ai_generated_quiz_sets(owner_id, updated_at DESC);

CREATE TABLE ai_generated_quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id UUID NOT NULL REFERENCES ai_generated_quiz_sets(id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position BETWEEN 1 AND 5),
    question TEXT NOT NULL CHECK (length(btrim(question)) BETWEEN 1 AND 2000),
    options JSONB NOT NULL CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) BETWEEN 2 AND 6),
    correct_option_index INT NOT NULL CHECK (correct_option_index BETWEEN 0 AND 5),
    explanation TEXT NOT NULL DEFAULT '',
    UNIQUE(set_id, position)
);
