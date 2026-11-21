-- Milestone 3: Subject -> Category -> Subcategory -> Lesson, plus per-user bookmarks and progress.
-- Content is authored by editors/admins (admin panel arrives in Milestone 10); learners only read published rows.
-- Every level has its own status so a whole branch can be hidden by unpublishing one parent.

CREATE TABLE subjects (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title          TEXT NOT NULL,
    title_bn       TEXT,
    description    TEXT NOT NULL DEFAULT '',
    description_bn TEXT,
    icon           TEXT,
    cover_url      TEXT,
    is_featured    BOOLEAN NOT NULL DEFAULT FALSE,
    status         TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX subjects_listing_idx ON subjects (status, sort_order, title);

CREATE TABLE categories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id     UUID NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    title          TEXT NOT NULL,
    title_bn       TEXT,
    description    TEXT NOT NULL DEFAULT '',
    description_bn TEXT,
    image_url      TEXT,
    status         TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX categories_subject_idx ON categories (subject_id, sort_order, title);

CREATE TABLE subcategories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id    UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    title          TEXT NOT NULL,
    title_bn       TEXT,
    description    TEXT NOT NULL DEFAULT '',
    description_bn TEXT,
    status         TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX subcategories_category_idx ON subcategories (category_id, sort_order, title);

CREATE TABLE lessons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subcategory_id      UUID NOT NULL REFERENCES subcategories (id) ON DELETE CASCADE,
    title               TEXT NOT NULL,
    title_bn            TEXT,
    summary             TEXT NOT NULL DEFAULT '',
    summary_bn          TEXT,
    content             TEXT NOT NULL DEFAULT '',   -- Markdown subset: headings, lists, **bold**
    content_bn          TEXT,
    learning_objectives TEXT[] NOT NULL DEFAULT '{}',
    estimated_minutes   INT CHECK (estimated_minutes IS NULL OR estimated_minutes > 0),
    status              TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    sort_order          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX lessons_subcategory_idx ON lessons (subcategory_id, sort_order, title);

CREATE TABLE lesson_attachments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id  UUID NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('image', 'pdf', 'video', 'link', 'file')),
    title      TEXT NOT NULL,
    url        TEXT NOT NULL,                       -- object-storage upload pipeline arrives with the admin panel
    size_bytes BIGINT,
    sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX lesson_attachments_lesson_idx ON lesson_attachments (lesson_id, sort_order);

CREATE TABLE lesson_bookmarks (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lesson_id  UUID NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, lesson_id)
);
CREATE INDEX lesson_bookmarks_user_idx ON lesson_bookmarks (user_id, created_at DESC);

-- A row exists once the learner has opened the lesson. "Not started" is the absence of a row.
CREATE TABLE lesson_progress (
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lesson_id      UUID NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    status         TEXT NOT NULL CHECK (status IN ('in_progress', 'completed')),
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    PRIMARY KEY (user_id, lesson_id)
);
CREATE INDEX lesson_progress_recent_idx ON lesson_progress (user_id, last_viewed_at DESC);
