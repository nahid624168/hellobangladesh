-- Milestone 1: role catalogue. Users, sessions and the academic hierarchy arrive in later migrations.
CREATE TABLE roles (
    id          SMALLINT PRIMARY KEY,
    code        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

INSERT INTO roles (id, code, description) VALUES
    (1, 'guest',       'Browse-only visitor (only if guest browsing is enabled)'),
    (2, 'user',        'Registered user'),
    (3, 'premium',     'Subscriber (monetization is configurable)'),
    (4, 'moderator',   'Reviews reports and user content'),
    (5, 'editor',      'Creates and edits official content'),
    (6, 'admin',       'Manages content and operations'),
    (7, 'super_admin', 'Full platform control');
