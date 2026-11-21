# STEP 32 — AI gateway backend

## Scope
One server-side door for every AI feature. Android never talks to a provider and never holds a key.

- Provider-neutral port (`AiProvider`) with a Gemini adapter (plain HTTP, key in a header) and a "not configured" provider
- Fixed server-side prompt templates for 11 tasks; the client sends only a task name and text
- Consent enforcement (versioned, revocable)
- Per-user daily request and token quotas, per-minute rate limit, global daily token cost guard, pause after repeated safety blocks
- Usage log with counts only (never prompt, answer, note text, keys or tokens)
- Conversation history for tutor/chat tasks, readable and deletable by the user

Out of scope: Android screens (Step 33), admin usage reports (row 32b), payments/plan-based limits (Step 35).

## Tasks (templates in `AiTemplates.kt`)
`summarize`, `explain`, `outline`, `revision_notes`, `make_flashcards`, `make_questions`, `translate`, `simplify`, `tutor_hint` (hint only, no final answer), `tutor_steps`, `chat`.
Only `tutor_hint`, `tutor_steps` and `chat` are saved as conversations. The model's output for `make_flashcards` / `make_questions` is text for the user to review and edit; nothing is written into their sets by this step.

## API (all under `/api/v1/ai`, JWT required)
| Method | Path | Notes |
|---|---|---|
| GET | `/consent` | `{required, currentVersion, acceptedVersion, active}` |
| POST | `/consent` | body `{version}` must equal the current version (`2026-11`) |
| DELETE | `/consent` | revoke; AI calls stop immediately |
| GET | `/quota` | requests/tokens used and limits, `resetsAt` (next UTC midnight) |
| POST | `/run` | body `{task, text, language?: en\|bn, conversationId?}` -> `{output, conversationId, quota, notice}` |
| GET | `/conversations` | paged (`limit` 1-50, `cursor`) |
| GET | `/conversations/{id}` | messages |
| DELETE | `/conversations/{id}` | delete one |
| DELETE | `/conversations` | delete all mine |

Errors (project format): 400 `invalid_request` (names the field), 403 `ai_consent_required`, 404 `not_found` (also for someone else's conversation),
429 `rate_limited` / `ai_quota_exceeded` / `ai_temporarily_blocked`, 422 `ai_blocked`, 502 `ai_bad_response`,
503 `ai_disabled` / `ai_unavailable` / `ai_busy` / `ai_capacity`. Provider error details are never sent to the client.

## Rules enforced (order in `AiService.run`)
1. Validate task, text (1-8000 chars), language, conversation use
2. `ai_assistants` feature flag on (it is **off** by default after V16; turn it on in the admin panel as super_admin)
3. Provider configured (`GEMINI_API_KEY` and `GEMINI_MODEL` both set)
4. Active consent for the current version
5. Rate limit 10/min per user (in-memory)
6. Daily quota per user (default 30 requests, 60,000 tokens), blocked-request pause (5/day), global token cost guard (default 2,000,000/day)
7. Call provider; failures map to the errors above. Provider failures do **not** use quota; safety blocks do.
8. Record usage; save conversation (tutor/chat only); return answer plus the "AI can make mistakes" notice and fresh quota

No prices or provider capacity numbers are assumed: cost is limited in tokens, and the limits are environment variables.
If the provider omits token counts, tokens are estimated as characters / 4 so the guard still works.

## Database
`V18__ai_gateway.sql`: `ai_consents`, `ai_usage`, `ai_conversations`, `ai_messages` (all cascade on user deletion, so account deletion removes AI data).
Next free migration: **V19**.

## Config (`.env.example`, `docker-compose.yml`)
`GEMINI_API_KEY`, `GEMINI_MODEL` (no default: choose the model id from Google's current list), `AI_DAILY_REQUEST_LIMIT`, `AI_DAILY_TOKEN_LIMIT`, `AI_GLOBAL_DAILY_TOKEN_LIMIT`.

## Changed files
- New: `backend/src/main/kotlin/com/exambase/backend/ai/` (`AiModels`, `AiTemplates`, `AiProvider`, `AiRepository`, `JdbcAiRepository`, `AiService`, `AiRoutes`)
- New: `V18__ai_gateway.sql`, `backend/src/test/kotlin/com/exambase/backend/ai/` (`InMemoryAiRepository`, `AiServiceTest`)
- Changed: `AppConfig.kt`, `Application.kt` (`ai` on `AppServices`, `buildAiService`, routes, version 0.13.0), `.env.example`, `docker-compose.yml`, `CLAUDE.md`/`AGENTS.md` (migration number), `docs/PROGRESS.md`, `README.md`, admin panel notice text

## Acceptance criteria
- No provider call without consent, flag, configuration, or quota headroom
- Quotas and conversations are per user; another user's conversation id gives 404
- Usage rows contain no text; one-shot tasks leave no history
- Provider failures give clean errors and do not use up the user's allowance
- Deleting a conversation (or all) removes its messages; deleting the account removes everything

## Tests
`AiServiceTest` (21 tests, in-memory): consent (missing, old version, revoked, wrong version), flag/config switches, input validation, request and token quota, per-user quota, global cost guard, token estimation, rate limit, repeated safety blocks, provider failure mapping and quota effect, no-text usage rows, template and data-wrapping, conversation save/continue/privacy/delete/delete-all, Gemini response parsing (no network). **Not compiled or run.**

## Risks / not yet verified
- **Never called the real Gemini API.** The request and response shape follows Google's `generateContent` REST format from memory; check it against the current docs on first use.
- JDBC SQL not run against PostgreSQL.
- No HTTP-level (Ktor `testApplication`) tests for the AI routes; service level only.
- The provider call blocks a server thread for up to 30 s; fine for early use, revisit in Step 38 (load testing).
- Per-minute rate limit is in-memory (one server only), like the other limiters.
- Conversation text is stored in plain form in PostgreSQL (the user's own messages and AI answers) so the user can read and delete it. Users must not be told it is encrypted.
- Prompt-injection defence is wrapping plus a guard instruction; it lowers risk but is not a guarantee.
- Consent wording itself (what the dialog says) is written in Step 33; bump `AiConsentVersion.CURRENT` when it changes.

## Run it
```bash
# .env: GEMINI_API_KEY=... GEMINI_MODEL=<model id>
docker compose up --build
# as super_admin: PUT /api/v1/admin/feature-flags/ai_assistants {"enabled":true}
curl -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"version":"2026-11"}' localhost:8080/api/v1/ai/consent
curl -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"task":"summarize","text":"..."}' localhost:8080/api/v1/ai/run
```
