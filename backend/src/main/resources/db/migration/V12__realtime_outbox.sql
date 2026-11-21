-- Step 25: transactional outbox for contest realtime fan-out (at-least-once).
-- Consumers must be idempotent; we never claim exactly-once delivery.

CREATE TABLE realtime_outbox (
    id           BIGSERIAL PRIMARY KEY,
    channel      TEXT NOT NULL,                 -- e.g. contest:{uuid}
    event_type   TEXT NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                    -- null until a worker acknowledges publish
);
CREATE INDEX realtime_outbox_pending_idx ON realtime_outbox (id) WHERE published_at IS NULL;
CREATE INDEX realtime_outbox_channel_idx ON realtime_outbox (channel, id);
