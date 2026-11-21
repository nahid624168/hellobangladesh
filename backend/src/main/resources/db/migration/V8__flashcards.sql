-- Step 18: user-owned flashcard sets and spaced-repetition reviews.
-- Flashcard sets are SEPARATE entities from quiz sets (never merged in lists or tables).

CREATE TABLE flashcard_sets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    description TEXT CHECK (description IS NULL OR length(description) <= 1000),
    lesson_id   UUID REFERENCES lessons (id) ON DELETE SET NULL,
    status      TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'private', 'archived')),
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX flashcard_sets_owner_idx ON flashcard_sets (owner_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE flashcards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id          UUID NOT NULL REFERENCES flashcard_sets (id) ON DELETE CASCADE,
    position        INT NOT NULL CHECK (position >= 1),
    front_text      TEXT NOT NULL CHECK (length(btrim(front_text)) BETWEEN 1 AND 2000),
    front_text_bn   TEXT,
    back_text       TEXT NOT NULL CHECK (length(btrim(back_text)) BETWEEN 1 AND 4000),
    back_text_bn    TEXT,
    front_image_url TEXT,
    back_image_url  TEXT,
    difficulty      TEXT NOT NULL DEFAULT 'medium' CHECK (difficulty IN ('easy', 'medium', 'hard')),
    tags            TEXT[] NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (set_id, position)
);
CREATE INDEX flashcards_set_idx ON flashcards (set_id, position);

-- Per-user spaced-repetition state for each card (SM-2 style fields).
CREATE TABLE flashcard_srs (
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    card_id       UUID NOT NULL REFERENCES flashcards (id) ON DELETE CASCADE,
    ease_factor   NUMERIC(4, 2) NOT NULL DEFAULT 2.50 CHECK (ease_factor >= 1.30),
    interval_days INT NOT NULL DEFAULT 0 CHECK (interval_days >= 0),
    repetitions   INT NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
    due_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, card_id)
);
CREATE INDEX flashcard_srs_due_idx ON flashcard_srs (user_id, due_at);

CREATE TABLE flashcard_reviews (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    card_id       UUID NOT NULL REFERENCES flashcards (id) ON DELETE CASCADE,
    set_id        UUID NOT NULL REFERENCES flashcard_sets (id) ON DELETE CASCADE,
    rating        TEXT NOT NULL CHECK (rating IN ('again', 'hard', 'good', 'easy')),
    ease_before   NUMERIC(4, 2) NOT NULL,
    ease_after    NUMERIC(4, 2) NOT NULL,
    interval_before INT NOT NULL,
    interval_after  INT NOT NULL,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX flashcard_reviews_user_idx ON flashcard_reviews (user_id, reviewed_at DESC);
CREATE INDEX flashcard_reviews_card_idx ON flashcard_reviews (card_id, reviewed_at DESC);
