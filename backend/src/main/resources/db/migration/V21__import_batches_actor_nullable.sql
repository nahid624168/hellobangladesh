-- V17 declared actor_id NOT NULL together with ON DELETE SET NULL, so deleting a user who ran an import would fail.
ALTER TABLE import_batches ALTER COLUMN actor_id DROP NOT NULL;
