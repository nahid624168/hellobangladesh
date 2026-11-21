-- Step 4: question bank (read side). Authoring arrives with the admin backend (Step 20) and bulk import (Step 22).
-- Learners only ever see published questions whose whole lesson chain is published.
-- Correct answers, answer keys and explanations live in tables/columns that the learner-facing read API never selects.

CREATE TABLE questions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id         UUID NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    type              TEXT NOT NULL CHECK (type IN
                        ('single_choice', 'multiple_select', 'true_false', 'short_answer', 'written', 'image_based')),
    question_text     TEXT NOT NULL CHECK (length(btrim(question_text)) > 0),
    question_text_bn  TEXT,
    explanation       TEXT NOT NULL DEFAULT '',      -- withheld until the learner submits an answer (Step 5)
    explanation_bn    TEXT,
    difficulty        TEXT NOT NULL DEFAULT 'medium' CHECK (difficulty IN ('easy', 'medium', 'hard')),
    marks             NUMERIC(5, 2) NOT NULL DEFAULT 1 CHECK (marks > 0),
    negative_marks    NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (negative_marks >= 0),
    status            TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'review', 'published', 'archived')),
    sort_order        INT NOT NULL DEFAULT 0,
    current_version   INT NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    -- Duplicate-detection strategy: same normalised text => same hash. Not unique, because the same wording can
    -- legitimately exist in two lessons; the admin import (Step 22) reports collisions for a human to decide.
    content_hash      TEXT GENERATED ALWAYS AS (md5(lower(btrim(regexp_replace(question_text, '\s+', ' ', 'g'))))) STORED,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX questions_lesson_idx ON questions (lesson_id, status, sort_order);
CREATE INDEX questions_filter_idx ON questions (status, type, difficulty);
CREATE INDEX questions_created_idx ON questions (status, created_at DESC);
CREATE INDEX questions_content_hash_idx ON questions (content_hash);

-- Options for single_choice, multiple_select, true_false and image_based. is_correct must never leave the server
-- before the learner has answered.
CREATE TABLE question_options (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id    UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    option_text    TEXT NOT NULL,
    option_text_bn TEXT,
    is_correct     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order     INT NOT NULL DEFAULT 0,
    UNIQUE (question_id, sort_order)
);

-- Accepted answers for short_answer questions. Compare against answer_normalized (case and spacing folded).
CREATE TABLE question_answer_keys (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id       UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    answer            TEXT NOT NULL CHECK (length(btrim(answer)) > 0),
    answer_normalized TEXT GENERATED ALWAYS AS (lower(btrim(regexp_replace(answer, '\s+', ' ', 'g')))) STORED,
    UNIQUE (question_id, answer_normalized)
);

-- Full snapshot of a question at each version (including correct answers: this table is internal only).
CREATE TABLE question_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    version     INT NOT NULL CHECK (version >= 1),
    snapshot    JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (question_id, version)
);

CREATE TABLE tags (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug    TEXT NOT NULL CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,49}$'),
    name    TEXT NOT NULL,
    name_bn TEXT
);
CREATE UNIQUE INDEX tags_slug_uq ON tags (slug);

CREATE TABLE question_tags (
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (question_id, tag_id)
);
CREATE INDEX question_tags_tag_idx ON question_tags (tag_id);

-- Diagrams and pictures. placement 'question' is shown with the question; 'explanation' is withheld until answered.
CREATE TABLE question_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    kind        TEXT NOT NULL DEFAULT 'image' CHECK (kind IN ('image', 'diagram')),
    placement   TEXT NOT NULL DEFAULT 'question' CHECK (placement IN ('question', 'explanation')),
    url         TEXT NOT NULL,                    -- object-storage upload pipeline arrives with the admin backend (Step 20)
    alt_text    TEXT,
    alt_text_bn TEXT,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX question_images_question_idx ON question_images (question_id, placement, sort_order);

-- Read-only health check for content people. An empty result means every question is well-formed.
-- Run it before publishing and after any import:  SELECT * FROM question_integrity_issues;
CREATE VIEW question_integrity_issues AS
SELECT q.id AS question_id, q.status, 'needs_exactly_one_correct_option' AS issue
FROM questions q
WHERE q.type IN ('single_choice', 'true_false', 'image_based')
  AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id AND o.is_correct) <> 1
UNION ALL
SELECT q.id, q.status, 'needs_at_least_one_correct_option'
FROM questions q
WHERE q.type = 'multiple_select'
  AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id AND o.is_correct) < 1
UNION ALL
SELECT q.id, q.status, 'needs_at_least_two_options'
FROM questions q
WHERE q.type IN ('single_choice', 'multiple_select', 'image_based')
  AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id) < 2
UNION ALL
SELECT q.id, q.status, 'true_false_needs_exactly_two_options'
FROM questions q
WHERE q.type = 'true_false'
  AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id) <> 2
UNION ALL
SELECT q.id, q.status, 'short_answer_needs_an_answer_key'
FROM questions q
WHERE q.type = 'short_answer'
  AND NOT EXISTS (SELECT 1 FROM question_answer_keys k WHERE k.question_id = q.id)
UNION ALL
SELECT q.id, q.status, 'image_based_needs_a_question_image'
FROM questions q
WHERE q.type = 'image_based'
  AND NOT EXISTS (SELECT 1 FROM question_images i WHERE i.question_id = q.id AND i.placement = 'question')
UNION ALL
SELECT q.id, q.status, 'missing_version_snapshot'
FROM questions q
WHERE NOT EXISTS (SELECT 1 FROM question_versions v WHERE v.question_id = q.id AND v.version = q.current_version);
