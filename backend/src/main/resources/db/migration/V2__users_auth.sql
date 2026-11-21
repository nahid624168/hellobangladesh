-- Milestone 2: users, role assignment, sessions, one-time email codes.
CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              TEXT NOT NULL,
    password_hash      TEXT NOT NULL,
    display_name       TEXT NOT NULL,
    email_verified_at  TIMESTAMPTZ,
    status             TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'deleted')),
    preferred_language TEXT NOT NULL DEFAULT 'en' CHECK (preferred_language IN ('en', 'bn')),
    theme              TEXT NOT NULL DEFAULT 'system' CHECK (theme IN ('system', 'light', 'dark')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);
CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sessions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash     TEXT NOT NULL,
    previous_refresh_hash  TEXT,
    device_name            TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked_at             TIMESTAMPTZ
);
CREATE UNIQUE INDEX sessions_refresh_uq ON sessions (refresh_token_hash);
CREATE INDEX sessions_previous_idx ON sessions (previous_refresh_hash);
CREATE INDEX sessions_user_idx ON sessions (user_id) WHERE revoked_at IS NULL;

CREATE TABLE email_codes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    TEXT NOT NULL CHECK (purpose IN ('verify_email', 'reset_password')),
    code_hash  TEXT NOT NULL,
    attempts   INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX email_codes_lookup_idx ON email_codes (user_id, purpose, created_at DESC);
