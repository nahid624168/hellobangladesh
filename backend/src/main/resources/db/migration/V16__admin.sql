-- Step 30a: Admin foundation — audit logs, feature flags, app config, abuse reports.
-- Roles already exist in V1. user_roles already exists in V2.

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT,
    details         JSONB NOT NULL DEFAULT '{}',
    ip_address      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_id, created_at DESC);
CREATE INDEX audit_logs_resource_idx ON audit_logs (resource_type, resource_id, created_at DESC);
CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);

CREATE TABLE feature_flags (
    key             TEXT PRIMARY KEY,
    enabled         BOOLEAN NOT NULL DEFAULT false,
    description     TEXT NOT NULL DEFAULT '',
    updated_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_config (
    key             TEXT PRIMARY KEY,
    value           JSONB NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    updated_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Abuse / content reports (extends the question-report idea from V7 to general targets).
CREATE TABLE abuse_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    target_type     TEXT NOT NULL CHECK (target_type IN ('user', 'question', 'note', 'quiz_set', 'flashcard_set', 'comment', 'other')),
    target_id       TEXT NOT NULL,
    reason          TEXT NOT NULL,
    details         TEXT,
    status          TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewing', 'resolved', 'dismissed')),
    resolver_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);
CREATE INDEX abuse_reports_status_idx ON abuse_reports (status, created_at DESC);
CREATE INDEX abuse_reports_target_idx ON abuse_reports (target_type, target_id);

-- Seed a few useful feature flags (disabled by default).
INSERT INTO feature_flags (key, enabled, description) VALUES
    ('guest_browsing', false, 'Allow unauthenticated browse of public content'),
    ('ai_assistants', false, 'Enable AI notes and learning assistants'),
    ('contests_public', true, 'Public contest list is visible'),
    ('payments', false, 'Enable subscription / payment flows'),
    ('registration_open', true, 'Allow new user registration');

INSERT INTO app_config (key, value, description) VALUES
    ('support_email', '"support@exambase.example"', 'Public support contact'),
    ('max_quiz_questions', '100', 'Hard cap on questions per quiz set'),
    ('daily_goal_default', '20', 'Default daily practice goal for new users');
