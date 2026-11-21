# Phase 05: senior review fixes (not compiled here; verify in CI)

Backend
- Rate limiter purges idle keys (no unbounded memory). `TRUST_PROXY=true` installs XForwardedHeaders so limits work per client behind a proxy.
- Suspending a user revokes all of their sessions immediately.
- Guest accounts: JWT `guest` claim, AI routes return 403 `guest_not_allowed`, hourly cleanup of expired guests, no bcrypt run per guest.
- V21: `import_batches.actor_id` made nullable (V17 combined NOT NULL with ON DELETE SET NULL).
- Version 0.14.0.

Android
- Debug-only network security config (cleartext only for 10.0.2.2/localhost). Release stays HTTPS only.
- `proguard-rules.pro` for serialization, Ktor, Room, WorkManager.
- Room: schema export on; only legacy v1 may be destroyed. From v2 on write real Migrations.
- Login screen redesign (tagline, max width, "or" divider, guest button), guest banner no longer double-pads the status bar.

Also done: DB/Redis bound to 127.0.0.1, container runs as non-root. Still open: WS query token, session lookup caching.
