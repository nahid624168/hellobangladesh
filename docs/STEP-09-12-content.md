# Steps 9-12: Academic hierarchy and content browsing

Subject -> Category -> Subcategory -> Lesson, readable by signed-in learners, with bookmarks, completion tracking
and "recently viewed". Builds on Steps 6-8 (auth); nothing from Steps 1-8 changed except the additions listed below.

## Scope
In: browsing all four levels, subject search/sort/featured filter, lesson detail (Markdown content, learning objectives,
attachments as links), bookmarks, completion tracking, recently viewed, English + Bangla titles/content, draft/published state.
Out (and where it goes): creating/editing content and uploading files (Steps 30-31 (admin panel)); related questions, quizzes,
flashcards and notes on a lesson (Steps 13-23, added as extra fields on `LessonDetail`); offline download (Step 22);
global search across all content types (Step 12 onward; only subject search exists now).

## Dependencies
Steps 6-8 (JWT auth, `users` table, `authed` token-refresh wrapper on Android).

## Assumptions (not yet approved by owner)
- Guest browsing stays OFF, so every content endpoint needs sign-in.
- One `status` (draft/published) per level. A node is visible only if it and all its ancestors are published.
- Content is only added by SQL for now (`backend/src/main/resources/db/dev/seed_demo.sql` for development).
- Lesson content is a small Markdown subset (headings, bullets, numbered items, **bold**), rendered natively.
- Bangla fields are optional; the app shows Bangla when the device language is Bangla and the field exists, else English.
  (The stored `preferredLanguage` is still not applied to the UI; same known gap as Steps 6-8.)

## Database (`V3__academic_hierarchy.sql`)
`subjects`, `categories`, `subcategories`, `lessons` (each: title/title_bn, description or summary (+ _bn), `status`,
`sort_order`; subjects add `icon`, `cover_url`, `is_featured`; categories add `image_url`; lessons add `content(_bn)`,
`learning_objectives TEXT[]`, `estimated_minutes`), `lesson_attachments`, `lesson_bookmarks`, `lesson_progress`.
Deleting a parent cascades to everything beneath it. "Not started" = no `lesson_progress` row.

## API (all under `/api/v1`, all require a Bearer token)
| Method | Path | Purpose |
|---|---|---|
| GET | /academic/subjects?q=&sort=order\|title\|newest&featured=true&limit=&cursor= | Paged subject list with category and lesson counts |
| GET | /academic/subjects/{id} | Subject + its categories |
| GET | /academic/categories/{id} | Category + breadcrumb + subcategories |
| GET | /academic/subcategories/{id} | Subcategory + breadcrumb + lessons (with this user's progress and bookmark flags) |
| GET | /academic/lessons/{id} | Full lesson: content, objectives, attachments, breadcrumb, previous/next lesson ids |
| POST | /academic/lessons/{id}/view | Record "opened" (adds to recently viewed; never downgrades completed) |
| PUT / DELETE | /academic/lessons/{id}/bookmark | Add / remove bookmark (idempotent) |
| PUT | /academic/lessons/{id}/progress | `{ "status": "in_progress" \| "completed" }` |
| GET | /users/me/bookmarks?limit=&cursor= | Paged bookmarks, newest first |
| GET | /users/me/recent-lessons?limit= | Last viewed lessons, newest first |

Rules: limit 1-50 (default 20); cursors are opaque (offset inside, may change later); hidden, draft or malformed ids all
return `404 not_found`; bad query values return `400 invalid_request` with `fields`. A GET never changes data, which is why
"opened" is a separate POST. Users only ever see their own bookmarks and progress.

## Android
New: `academic/` (repository over `AcademicApi`, load-state plumbing, paging helper, Markdown parser, language picker),
screens Subjects (search, sort chips, featured filter, endless scroll), Subject detail, Category, Subcategory, Lesson,
Saved & recent. Home gets two entry cards. Back from a lesson refreshes the list behind it so progress marks are current.
Attachment links open only if they are http/https. Bookmark and completion changes show immediately and are undone with a
message if saving fails.

## Acceptance criteria
1. A signed-in user can open Subjects, drill to a lesson, and Back returns one level at a time.
2. Draft content, and anything under a draft parent, never appears and returns 404 if requested directly.
3. Searching "bang" shows only matching subjects; clearing the box restores the list; fast typing never shows stale results.
4. Subject list loads more pages while scrolling and never shows the same subject twice.
5. Bookmarking and completing a lesson persist across app restarts and show on the lesson list and in Saved & recent.
6. Previous/Next stay inside the same subcategory in list order.
7. Losing the connection shows a retry screen; an expired session returns the user to Login.
8. Bangla device language shows Bangla titles where they exist.

## Automated tests
Backend (`cd backend && gradle test`): `AcademicRoutesTest` (auth required; drafts hidden at every level; counts exclude drafts;
search/sort/featured; cursor walk; bad paging input; drill-down and breadcrumb; hidden-branch lessons 404; bookmark add/remove/list;
bookmarks not shared between users; cannot bookmark hidden lesson; recent order and completed-sticks; invalid progress status),
`PagingTest`. Android (`gradle :app:testDebugUnitTest`): `SimpleMarkdownTest`, `PagedListTest`, `LocalizedAndLoadStateTest`
(language choice, failure-to-state mapping, link scheme allow-list).

## Not yet verified
**Nothing here has been compiled or run.** In particular the SQL in `JdbcAcademicRepository` has only been checked by reading,
because the in-memory test double replaces it in tests. Before moving on: run both test suites, load the seed, and walk through
the acceptance list by hand. Expect small compile fixes (imports, version mismatches).

## Risks
- Offset cursors can repeat or skip a row if content changes between pages (the app removes repeats; skips are tolerated).
- Subject search is `ILIKE` without an index; fine for hundreds of subjects, revisit with `pg_trgm` or full-text search later.
- No content-editing path until Steps 30-31; until then data enters by SQL.
- Counts are computed per request with sub-queries; add caching if subject lists get large.

## Definition of done
Both test suites green; seed loaded; acceptance criteria 1-8 checked on an emulator against the Docker backend; owner approves
the assumptions above.

## Try it
```bash
docker compose up --build -d
docker compose exec -T postgres psql -U <DB_USER> -d <DB_NAME> < backend/src/main/resources/db/dev/seed_demo.sql
```
Then sign in on the emulator and open "Browse subjects". The seed adds Bangla Language (featured), General Mathematics
and one draft subject that must stay invisible.
