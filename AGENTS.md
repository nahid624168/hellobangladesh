# ExamBase AI: instructions for the AI that builds this project

(The same text is in `AGENTS.md`. Read this file first, every session.)

## What this project is
ExamBase AI is an Android-first education app (Kotlin, Jetpack Compose, Ktor backend, PostgreSQL), built in 40 steps.
The full plan is `ExamBase_AI_40_Step_Build_Prompt.txt`. Current status and the ordered work queue are in `docs/PROGRESS.md`.
Read both before doing anything else.

## The one rule: "next"
When the owner says **"next"** (or "ok", "continue", "kam koro"), do this without asking anything else:

1. Read `docs/PROGRESS.md`. Take the first row of the **Work queue** whose status is `NEXT` (if none, the first `TODO`).
2. Read that step in `ExamBase_AI_40_Step_Build_Prompt.txt`, and the docs of the steps it builds on (`docs/STEP-*.md`).
3. In one short line, say which step you are starting and which files you will create or change.
4. Build the step completely: backend, Android, migration, strings (English + Bangla), tests. No placeholders, no "TODO later" stubs inside the step.
5. If you can run tools, run the tests (`cd backend && gradle test`, `cd android && gradle :app:testDebugUnitTest`) and fix failures. If you cannot, say plainly that nothing was compiled or run. Never say tests passed unless you ran them.
6. Write `docs/STEP-NN-<name>.md` for the step (same layout as `docs/STEP-15-quiz-engine.md`: scope, API, database, changed files, acceptance criteria, tests, risks, not yet verified, run it).
7. Update `docs/PROGRESS.md` (set the step to DONE or PARTIAL with the gaps, move `NEXT` to the following queue row) and the one-line summary in `README.md`.
8. Reply with: what was built (2-3 sentences), what was not verified, and "Say `next` for Step X (name)". If you can produce files, give the updated project as a zip named `ExamBaseAI-after-step-NN.zip`.

Other messages:
- **"status"**: show the progress table and the next three queue rows. Change nothing.
- Pasted compile or test errors: fix those first, then stop and report. Do not start the next step in the same reply.
- **"redo step N"** or a bug report: fix it, update that step's doc and `PROGRESS.md`, do not move on.
- A question or a change of plan: answer it, update the plan file and `PROGRESS.md` if the plan changes, then wait.

## Checkpoint steps
Steps 4, 13, 27, 37 and 39 are checkpoints in the plan. Finish the step, summarise the decisions the owner must confirm, and treat the owner's next "next" as approval. Do not ask for approval at any other step.

## If a step is too large
Split it into `NNa` and `NNb` (backend first, Android second is the usual split). Add both rows to the Work queue in `PROGRESS.md` and do `NNa` now. Never leave a half-built step marked DONE.

## Gap steps (PARTIAL items) are already scheduled
They are in the Work queue as `b` steps (14b, 8b, 12b, 11b). Do them in queue order, not earlier. Related-item links on lessons (questions, quizzes, flashcards, notes) are added by each module as it lands.

## Project conventions (follow them; do not silently change them)
- Backend package `com.exambase.backend.<module>`; one folder per module; `Routes` -> `Service` -> `Repository` interface -> `Jdbc...Repository`.
  Every module is wired through an optional field on `AppServices` in `Application.kt` and registered in `module(...)`. Bump `SERVICE_VERSION`.
- Tests use in-memory doubles (`InMemory*Repository`) with Ktor `testApplication`; add a double for every new repository interface and keep its rules identical to the JDBC version.
- Database: Flyway files `backend/src/main/resources/db/migration/V<n>__name.sql`; the next number is the highest existing + 1 (currently `V19`). **Never edit an existing migration**; add a new one. Migration numbers are schema versions, not step numbers.
- Errors: `ApiException(status, code, message, fields)` -> `{"error":{"code","message","fields"}}`. Bad input is 400 naming the field; someone else's resource is 404 (never 403 for ownership); too many requests is 429. Paging uses `Paging` opaque cursors, limit 1-50.
- Response DTO fields have no defaults (always present in JSON); request DTO fields have defaults.
- Security rules never to break: correct answers and explanations are returned only after submission; scoring, payments, permissions and ranking are decided on the server; no secrets in the APK; private notes and user-owned content never appear for other users; never log passwords, tokens or note content.
- Android: package `com.exambase.ai.<feature>` (repository with the `authed` token-refresh wrapper, ViewModel + state class, Compose screens under `screens/`), route added in `navigation/ExamBaseNavHost.kt`, network DTOs and API interface in `core/network`, strings in both `values/strings.xml` and `values-bn/strings.xml`.
  Every screen has loading, empty, error-with-retry, offline and (where relevant) signed-out states, readable by TalkBack, touch targets of at least 48dp.
- Keep each user's Quiz Sets and Flashcard Sets as separate items everywhere (UI, API, database). Never merge them into one list.
- Practice attempts (`practice_attempts`) must never feed points, badges, streaks or leaderboards.
- Do not invent API keys, prices, provider names, benchmark numbers or capacity guarantees. Use environment variables for secrets.
- Build only what the current step asks for. Do not add features under another name.

## Honesty rules
- Say "not compiled / not run" whenever that is true.
- If a step cannot be completed (missing information, missing tool), say exactly what is missing, finish what you can, and mark the step PARTIAL.
- Do not change the plan, numbering or architecture silently. If something must change, say why and add migration steps.

## Finished?
When every row in the Work queue is DONE, run a final pass: full test run, README accurate, `PROGRESS.md` all DONE, then tell the owner the project is complete and list anything still PARTIAL or unverified.
