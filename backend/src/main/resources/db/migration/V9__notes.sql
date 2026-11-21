-- Step 20: private notes, nested folders, version history, sync tombstones.
-- Notes are private-by-default; never appear in another user's queries.

CREATE TABLE note_folders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES note_folders (id) ON DELETE CASCADE,
    name        TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    color       TEXT CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    position    INT NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ, -- tombstone for sync
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX note_folders_owner_idx ON note_folders (owner_id, parent_id) WHERE deleted_at IS NULL;
CREATE INDEX note_folders_sync_idx ON note_folders (owner_id, updated_at);

CREATE TABLE notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    folder_id       UUID REFERENCES note_folders (id) ON DELETE SET NULL,
    title           TEXT NOT NULL DEFAULT '' CHECK (length(title) <= 200),
    body            TEXT NOT NULL DEFAULT '' CHECK (length(body) <= 200000),
    color           TEXT CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    pinned          BOOLEAN NOT NULL DEFAULT FALSE,
    favorite        BOOLEAN NOT NULL DEFAULT FALSE,
    tags            TEXT[] NOT NULL DEFAULT '{}',
    -- optional links into the learning hierarchy / user sets
    lesson_id       UUID REFERENCES lessons (id) ON DELETE SET NULL,
    quiz_set_id     UUID REFERENCES quiz_sets (id) ON DELETE SET NULL,
    flashcard_set_id UUID REFERENCES flashcard_sets (id) ON DELETE SET NULL,
    version         INT NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      TIMESTAMPTZ, -- tombstone for sync
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX notes_owner_idx ON notes (owner_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX notes_folder_idx ON notes (folder_id) WHERE deleted_at IS NULL;
CREATE INDEX notes_pinned_idx ON notes (owner_id, pinned, updated_at DESC) WHERE deleted_at IS NULL AND pinned;
CREATE INDEX notes_sync_idx ON notes (owner_id, updated_at);

CREATE TABLE note_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id     UUID NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    version     INT NOT NULL CHECK (version >= 1),
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (note_id, version)
);
CREATE INDEX note_versions_note_idx ON note_versions (note_id, version DESC);

CREATE TABLE note_attachments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id     UUID NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    url         TEXT NOT NULL CHECK (length(url) BETWEEN 1 AND 2000),
    file_name   TEXT NOT NULL DEFAULT '',
    content_type TEXT,
    size_bytes  BIGINT CHECK (size_bytes IS NULL OR size_bytes >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX note_attachments_note_idx ON note_attachments (note_id);
