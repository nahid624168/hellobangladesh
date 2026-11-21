# Progress against the 40-step plan

Plan: `ExamBase_AI_40_Step_Build_Prompt.txt`. Every doc, code comment and README in this project now uses these step numbers.
Status words: DONE = built as planned; PARTIAL = built, with the gaps listed; TODO = not started.
**Nothing below has been compiled or run yet** (no Gradle/Android SDK/PostgreSQL was available to the author). Run the tests first.

| Step | Topic | Status | Notes / gaps | Doc |
|---|---|---|---|---|
| 1-4 | Requirements, architecture, screen map, ERD, API, security, deploy plan | DONE (condensed) | Short blueprint only; owner decisions in `00-blueprint.md` section 11 are still open | `00-blueprint.md` |
| 5 | Repo setup, CI, design system | DONE | | `STEP-01-05-foundation.md` |
| 6 | Backend identity | DONE | No OpenAPI file; rate limiter is in-memory | `STEP-06-08-auth-profile.md` |
| 7 | Android auth and onboarding | DONE | | same |
| 8 | Profile, settings, account controls | PARTIAL | Profile, theme, language, delete account done. 8b: avatar API, export, sessions UI source added; image picker and real storage write still open | `STEP-06-08-auth-profile.md`, `STEP-08b-profile-extras.md` |
| 9 | Academic hierarchy backend | DONE | Content enters by SQL until the admin steps | `STEP-09-12-content.md` |
| 10 | Android content browsing | DONE | No Room cache / Paging 3 yet | same |
| 11 | Lesson content, media, progress | PARTIAL | Bookmarks, completion, recently viewed done. 11b: signed URLs, related content on LessonDetail, Room lesson cache source added; Paging 3 UI + download button still open | `STEP-09-12-content.md`, `STEP-11b-lesson-enhancements.md` |
| 12 | Global search v1 | PARTIAL | Subject search only; full-text search across all content types still to do | same |
| 13 | Question bank backend | PARTIAL | Read side done (6 types, filters, hidden answers). Missing: versioning workflow, duplicate review, bulk import, reporting (moved to Step 30) | `STEP-13-question-bank.md` |
| 14 | Android question practice | PARTIAL | Check answer, reveal, history API, practice session done. 14b: bookmarks/report APIs, history screen, Coil images source added (not compiled) | `STEP-14-practice.md`, `STEP-14b-practice-extras.md` |
| 15 | Quiz engine backend | DONE | Missing for later: share links, random pick from bank, official quizzes | `STEP-15-quiz-engine.md` |
| 16 | Android quiz player and results | PARTIAL (split in three) | 16a done; 16b source added but uncompiled/UI-untested; image rendering and server-side draft answer persistence remain gaps; 16c source added but uncompiled/untested | `STEP-16-quiz-player.md` |
| 17 | My Quiz Sets (create/edit, list, filters) | PARTIAL | Client management UI, create/edit, search/status filters, question picker source added; not compiled; subject/lesson filter UI and delete-confirm dialog still light | `STEP-17-my-quiz-sets.md` |
| 18 | Flashcards backend and spaced repetition | PARTIAL | Schema, CRUD, SM-2 scheduler, due/review, analytics source added; not compiled | `STEP-18-flashcards-backend.md` |
| 19 | Android flashcards and My Flashcard Sets | PARTIAL | List/create/edit/study source added; offline sync deferred to Step 22; not compiled | `STEP-19-flashcards-android.md` |
| 20 | Notes backend | PARTIAL | Folders, notes, versions, attachments metadata, sync tombstones source added; not compiled | `STEP-20-notes-backend.md` |
| 21 | Android notes workspace and editor | PARTIAL | List/folders/search/editor autosave source added; full WYSIWYG/undo deferred; not compiled | `STEP-21-notes-android.md` |
| 22 | Offline mode and sync | PARTIAL | Room + WorkManager notes sync, conflict policy, encrypted watermark; not compiled | `STEP-22-offline-sync.md` |
| 23 | Note versions, export and sharing | PARTIAL | History UI, restore, simple compare, text share; PDF deferred; not compiled | `STEP-23-note-versions-export.md` |
| 20-23 | Notes, editor, offline sync, versions/export | TODO | | |
| 24-27 | Contests, real-time, exams | TODO | | |
| 28-29 | Analytics, gamification, home dashboard | TODO | | |
| 30-31 | Admin backend and web panel | PARTIAL | 30a+30b backend done; 31 web panel built (no MFA, no banners/notifications/AI usage yet); not compiled or opened in a browser | `STEP-30a-admin-backend.md`, `STEP-30b-admin-content.md`, `STEP-31-admin-web-panel.md` |
| 32-33 | AI gateway and assistants | IMPLEMENTED — VERIFICATION PENDING | 32 gateway; 33a assistant + consent/quota; 33b selection-aware Notes AI; 33c MCQ/flashcard generation from Notes selection or pasted text with editable review, private owner-scoped AI Quiz Sets, My Flashcard Sets persistence, tutor hints/step-by-step/chat, conversation history open/delete. Android/backend compile and device tests still required. | `STEP-32-ai-gateway.md`, `STEP-33a-ai-assistant-core.md`, `STEP-33b-ai-notes-editor.md`, `STEP-33c-ai-content-and-tutor.md` |
| 34-35 | Notifications, banners, payments, support | TODO | | |
| 36-40 | Observability, security review, load test, accessibility/QA, release | TODO | | |

Database migrations (`V1` to `V6`) are schema versions and keep their names. Do not edit old migration files: Flyway checks their contents.
Because of that, comments inside `V1` to `V5` still use the old milestone wording.

## Work queue (the AI does the first `NEXT` row when the owner says "next")
Order matters. Step 16 was split into 16a/16b/16c at the owner's request. `b` rows are the gaps left by PARTIAL steps, placed where their dependencies exist. Next free migration number: `V21`.

| # | Row | Status | What it contains |
|---|---|---|---|
| 1a | 16a | DONE | Quiz network layer (DTOs, `QuizApi`), `QuizRepository`, quiz list and quiz instructions/detail screens, Home entry |
| 1b | 16b | PARTIAL | Start/resume, player, server deadline countdown, navigation, answer entry, confirm/submit, expiry handling and Bundle-saveable local answer state added. Missing: compile/UI tests, image rendering, robust recovery if Android discards saved task state, server-side draft answer persistence. |
| 1c | 16c | DONE | Result summary, answer review (per `showExplanations`), attempt history screen, retry, offline rules |
| 2 | 17 | PARTIAL | My Quiz Sets screen, create/edit quiz set, add questions from the bank (source added; compile pending) |
| 3 | 14b | PARTIAL | Question bookmarks, question reporting (backend + Android), practice-history screen, images in app (Coil) — source added; compile pending |
| 4 | 18 | PARTIAL | Flashcards backend and spaced repetition (source added; compile pending) |
| 5 | 19 | PARTIAL | Android flashcards and My Flashcard Sets (source added; offline sync out; compile pending) |
| 6 | 20 | PARTIAL | Notes backend (schema, CRUD, versions, sync tombstones; not compiled) |
| 7 | 21 | PARTIAL | Android notes list + editor (autosave, markdown toolbar; not compiled) |
| 8 | 22 | PARTIAL | Offline mode and sync (Room notes cache, WorkManager, conflict policy; not compiled) |
| 9 | 23 | PARTIAL | Note versions UI, compare, text share/export (not compiled) |
| 10 | 11b | PARTIAL | Signed-URL service + related fields on LessonDetail + Room lesson/subject entities. Missing: Paging 3 RemoteMediator, LessonScreen related UI + download action, media-serving route |
| 11 | 8b | PARTIAL | Avatar key + upload/export/sessions APIs and SessionsScreen. Missing: image picker UI, real object-storage write of avatar bytes, full export share file |
| 12 | 24 | PARTIAL | Schema, service, JDBC, routes, unit tests for join/capacity/scoring/leaderboard. Missing: Redis leaderboard cache, compiled verification |
| 13 | 25 | PARTIAL | WS hub, outbox, protocol, contest emit hooks, unit tests. Missing: Redis bus, outbox worker loop, compiled verification |
| 14 | 26 | PARTIAL | List/detail/player/leaderboard screens, repo, API, Home entry, online-only copy. Missing: WS client, history screen, UI tests |
| 15 | 27 | PARTIAL | Exams schema/service/API/Android screens; audit trail; no tournaments/teams (owner checkpoint). Not compiled |
| 16 | 28 | PARTIAL | Overview/trends/subjects/achievements APIs + Android; server-only points; badges. Missing: downloadable reports, full practice hook-in. Not compiled |
| 17 | 12b | PARTIAL | Global search API + privacy tests + Android screen. Missing: FTS ranking/highlight. Not compiled |
| 18 | 29 | PARTIAL | Dashboard API + Home UI (greeting, goal, streak, continue, shortcuts, offline). Missing: notifications entry, UI tests. Not compiled |
| 19 | 30a | DONE | Admin core: users/roles, feature flags, app config, audit logs, abuse reports, V16 migration, unit tests. Not compiled |
| 20 | 30b | DONE | Content/question authoring, review workflow, bulk import, contest/exam status, destructive tokens, V17, auth-matrix unit tests. Not compiled; HTTP matrix still light |
| 21 | 31 | PARTIAL | Admin web panel at `/admin-panel/` (users, content, questions, imports, contests, exams, reports, flags, config, audit). Missing: admin MFA, banners/notifications/AI usage (need Steps 32/34), full content/question forms, browser/UI tests. Not compiled |
| 22 | 32 | DONE | AI gateway backend: Gemini adapter behind a provider interface, templates, quotas, cost guard, consent, usage log without content, conversations with delete, V18. Not compiled; never called the real Gemini API |
| 22b | 32b | TODO | Admin AI usage/cost endpoint (totals per day, per task, error rate) + panel section; needs no new tables |
| 23a | 33a | IMPLEMENTED — VERIFICATION PENDING | AI core: `AiApi`, repository, consent/revoke, quota, failure states, notice, assistant screen, editable answer + copy, Home entry. Compile/device verification pending. |
| 23b | 33b | IMPLEMENTED — VERIFICATION PENDING | AI in Notes editor: selected text or whole note, review/edit, insert/replace/save as new note, undo, stale-text protection, consent/quota/failure states. Compile/device verification pending. |
| 23c | 33c | IMPLEMENTED — VERIFICATION PENDING | Editable MCQ/flashcard review UI from Notes selection or pasted text; private owner-scoped AI Quiz Set storage (V20), My Flashcard Sets save with rollback; tutor hint/step/chat; conversation history list/open/delete. Quiz drafts are stored separately from official question bank and are not yet playable in the timed quiz engine. Compile/device verification pending. | `STEP-33c-ai-content-and-tutor.md` |
| 24 | 34 | TODO | Notifications and banners |
| 25 | 35 | TODO | Payments readiness and support |
| 26 | 36 | TODO | Observability and deployment |
| 27 | 37 | TODO | Security review (checkpoint) |
| 28 | 38 | TODO | Load testing and scaling |
| 29 | 39 | TODO | Accessibility, localization, QA (checkpoint) |
| 30 | 40 | TODO | Release preparation |

Step 33 was split into 33a/33b/33c at the owner's request ("complete 33 in 3 steps").
Large steps (20, 21, 22, 24, 30, 31) may be split into `a`/`b` rows by the AI; it must add the rows here when it does.
Next free migration number: V22.
