# STEP 30b — Admin content, questions, bulk import, contests/exams, destructive confirmation

## Scope
Completes Step 30 after 30a:

- Academic hierarchy authoring (subjects → categories → subcategories → lessons) with publish/unpublish
- Question authoring (create/update/options/tags), review workflow (`draft` → `review` → `published` / `archived`)
- Bulk JSON import of questions (batch tracking)
- Contest and exam admin list + status changes
- Destructive-action confirmation tokens (5-minute single-use)
- Authorization matrix unit tests for the new gates

Admin **web panel** remains Step 31.

## API (all under `/api/v1/admin`, JWT required)

| Method | Path | Min role | Notes |
|--------|------|----------|-------|
| GET | `/subjects` | editor | optional `status` |
| POST | `/subjects` | editor | create |
| PUT | `/subjects/{id}` | editor | update |
| GET | `/content/{kind}/{id}/children` | editor | kind = subject\|category\|subcategory |
| PATCH | `/content/{kind}/{id}/status` | editor | draft \| published |
| POST | `/categories` | editor | |
| POST | `/subcategories` | editor | |
| POST | `/lessons` | editor | |
| PUT | `/lessons/{id}` | editor | |
| GET | `/questions` | editor | `lessonId`, `status` |
| GET | `/questions/{id}` | editor | includes `isCorrect` on options |
| POST | `/questions` | editor | |
| PUT | `/questions/{id}` | editor | |
| PATCH | `/questions/{id}/status` | editor (draft/review), moderator (published/archived) | |
| POST | `/questions/import` | editor | body `{questions:[…]}` max 200 |
| GET | `/contests` | editor | |
| PATCH | `/contests/{id}/status` | admin | |
| GET | `/exams` | editor | |
| PATCH | `/exams/{id}/status` | admin | |
| POST | `/destructive-tokens` | admin | request single-use token |
| POST | `/destructive-confirm` | admin | body `{token}` |

Correct answers are returned only on **admin** question endpoints, never on learner APIs.

## Database
`V17__admin_content.sql`:

- `destructive_action_tokens`
- `import_batches`
- optional columns on `questions`: `review_note`, `reviewed_by`, `reviewed_at`

Next free migration: **V18**.

## Changed files
- `backend/src/main/resources/db/migration/V17__admin_content.sql`
- `admin/AdminModels.kt`, `AdminRepository.kt`, `JdbcAdminRepository.kt`, `AdminService.kt`, `AdminRoutes.kt`
- `backend/src/test/.../admin/InMemoryAdminRepository.kt`, `AdminServiceTest.kt`
- `docs/PROGRESS.md`, `README.md`

## Acceptance criteria
- Editor can create hierarchy nodes and questions; ordinary user cannot.
- Only moderator+ can publish/archive questions.
- Only admin+ can change contest/exam status and request destructive tokens.
- Destructive actions require a fresh token owned by the same admin; tokens expire and are single-use.
- Bulk import records a batch with success/error counts.
- Mutating actions write audit rows (via 30a audit helper).

## Tests
Extended `AdminServiceTest` covers editor/user gates, publish workflow, destructive token flow, contest status gate. **Not compiled or run.**

## Risks / not yet verified
- JDBC against real PostgreSQL not executed.
- No HTTP Ktor testApplication matrix yet (service-level only).
- No full question versioning snapshots on every edit (table exists; snapshot write deferred).
- CSV import not implemented (JSON only).
- Contest/exam **create** still goes through existing domain services; admin only lists + status here.
- Android has no admin UI (web panel is Step 31).

## Run it
```bash
# after migrations
curl -H "Authorization: Bearer $EDITOR_TOKEN" -d '{"title":"Physics","status":"draft"}' \
  http://localhost:8080/api/v1/admin/subjects
curl -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"action":"archive_question","resourceType":"question","resourceId":"..."}' \
  http://localhost:8080/api/v1/admin/destructive-tokens
```
