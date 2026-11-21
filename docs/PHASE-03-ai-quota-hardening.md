# Phase 03 — AI quota reservation hardening

## What changed

- Added Flyway migration `V19__ai_quota_reservations.sql` with per-user/per-UTC-day and global-per-UTC-day reservation counters. These rows contain counters only; prompts, generated answers, API keys, and note contents are not stored there.
- Added atomic repository operations to reserve a request and its estimated maximum token budget before contacting the AI provider.
- PostgreSQL reservation transactions lock the global daily counter first and the user daily counter second. All reservation/settlement paths use the same lock order to reduce deadlock risk.
- Successful and safety-blocked calls settle the reservation against provider-reported token usage. Provider errors release the request reservation and token budget, preserving the existing policy that provider failures do not consume the user's daily request quota.
- Updated the in-memory repository and tests, including a concurrent-request regression test for a one-request daily limit.

## Operational notes

- The migration runs automatically through the existing Flyway startup migration process. Back up the database before deploying any migration to production.
- The gateway reserves a conservative prompt-size estimate plus the task's full `maxOutputTokens` allowance. When the provider omits token metadata, the same conservative character-based estimate is used for accounting. Token estimates are still estimates, not a tokenizer-equivalent guarantee. Some calls may be rejected before reaching the provider when the remaining quota is smaller than the reserved budget.
- The existing `/ai/quota` response still reports completed usage rows. A request currently in flight is reserved for admission but is not shown as completed usage until it finishes.
- The counters are database-backed, so they coordinate multiple backend instances sharing the same PostgreSQL database. The existing in-memory per-minute rate limiter is still instance-local and is not replaced by this phase.

## Verification status

- Source-level review and archive integrity checks only.
- Automated Gradle tests and compilation were not run in this environment because the project ZIP does not include the Gradle wrapper scripts/JAR and a system Gradle installation is unavailable.
- Before production rollout, run the backend test suite, apply Flyway migration against a staging database, and load-test parallel requests with the same user and with different users.
