# Step 13: Question bank backend

Read-only question bank for signed-in learners. Builds on Steps 9-12 (academic hierarchy); nothing from Steps 1 to 12 changes except
the additions listed under "Changed files".

## Scope
In: tables for questions, options, answer keys, versions, tags and images; six question types (single choice, multiple select,
true/false, short answer, written, image based); Bangla + English text; marks and negative marks; difficulty; status
(draft / review / published / archived); filtered, sorted, cursor-paged list; question detail; tag list; development seed;
an integrity view for content people.
Out (and where it goes):
- Checking an answer, explanation reveal, practice history, Android screens, "Related questions" on the lesson: Step 14.
- Creating/editing questions, image upload, review workflow: Step 30 (until then data enters by SQL).
- Bulk import and duplicate review: Step 30 (the `content_hash` column is ready for it).
- Question reporting and question bookmarks: Step 14b (not yet built) (not part of this step).
- Quiz use of questions, randomised option order: Steps 15 to 17.

## Dependencies
Steps 9-12 (`lessons` and the whole hierarchy, `Paging`, `Page`, `Crumb`, `escapeLike`), Steps 6-8 (JWT auth).

## Deliverables
- Migration `V4__question_bank.sql` (tables, indexes, `question_integrity_issues` view).
- Package `com.exambase.backend.questions`: `QuestionModels`, `QuestionRepository`, `JdbcQuestionRepository`, `QuestionService`, `QuestionRoutes`.
- Wiring in `Application.kt` (new optional `AppServices.questions`, route registration). Version bumped to 0.4.0.
- Dev seed `db/dev/seed_questions.sql`.
- Tests (below) and this document.

## Database changes (`V4__question_bank.sql`)
| Table | Purpose |
|---|---|
| `questions` | One row per question: `lesson_id`, `type`, `question_text(_bn)`, `explanation(_bn)`, `difficulty`, `marks`, `negative_marks`, `status`, `sort_order`, `current_version`, generated `content_hash` |
| `question_options` | Options for single choice, multiple select, true/false, image based. Holds `is_correct`. Unique `(question_id, sort_order)` |
| `question_answer_keys` | Accepted answers for short answer. Generated `answer_normalized` for case/space-insensitive matching in Step 14 |
| `question_versions` | Full JSON snapshot per version (internal; includes correct answers) |
| `tags`, `question_tags` | Tags by unique lowercase `slug` (letters, digits, hyphens), with Bangla name |
| `question_images` | Pictures and diagrams. `placement`: `question` (shown) or `explanation` (withheld until answered) |
| view `question_integrity_issues` | Lists malformed questions (wrong number of correct options, missing answer key, missing image, missing version snapshot) |

Deleting a lesson cascades to its questions (same rule as the rest of the hierarchy; revisit before the admin panel allows deletes,
because quiz attempts in Step 15 will reference questions).

## API (all under `/api/v1`, all require a Bearer token)
| Method | Path | Purpose |
|---|---|---|
| GET | `/questions?subjectId=&categoryId=&subcategoryId=&lessonId=&type=&difficulty=&tag=&q=&sort=order\|newest\|difficulty&limit=&cursor=` | Paged question list |
| GET | `/questions/{id}` | One question with options (no correct flags), question-placement images, breadcrumb, version |
| GET | `/questions/tags` | Tags that have at least one visible question, with counts |

List item fields: `id, lessonId, type, text, textBn?, difficulty, marks, negativeMarks, tags[], hasImage`.
Detail: `{ question, options:[{id,text,textBn?}], images:[{id,kind,url,altText?,altTextBn?}], breadcrumb:[{type,id,title,titleBn?}], version }`.

Rules:
- Visible = question `published` AND lesson, subcategory, category and subject all `published`. Anything else behaves as missing.
- **Correct answers, answer keys and explanations are never returned.** The response types have no field for them and the SQL never selects them.
- Filters combine with AND. Asking for a hidden branch returns an empty page, not an error and not the hidden content.
- Bad filter values return `400 invalid_request` with `fields` naming the input (`sort`, `type`, `difficulty`, `tag`, `q`, `subjectId`, ...).
  Malformed or hidden ids in the path return `404 not_found`. Limit 1 to 50 (default 20); cursors are the same opaque offset cursors as Steps 9-12.
- `type` values: `single_choice, multiple_select, true_false, short_answer, written, image_based`. `difficulty`: `easy, medium, hard`.
- `short_answer` and `written` questions have an empty `options` list. `image_based` has one correct option and at least one question image.
- Option order is the stored order. Randomising it is a quiz setting (Step 15), not a bank rule.

## UI screens
None (backend only). Android arrives in Step 14.

## Acceptance criteria
1. Without a token, all three endpoints return 401.
2. Draft, review and archived questions, and questions under any unpublished lesson, subcategory, category or subject, never appear in lists or tags and return 404 by id.
3. No list or detail response contains a correct-option flag, an answer key or an explanation (including explanation-placement images).
4. Each filter (subject, category, subcategory, lesson, type, difficulty, tag, text) narrows the list, and combinations narrow further.
5. Walking the cursor returns every visible question exactly once.
6. Sort by difficulty returns easy, then medium, then hard; sort by newest returns the latest first.
7. Invalid filters return 400 with the field name; malformed question ids return 404.
8. After loading both seeds, `SELECT * FROM question_integrity_issues;` returns no rows and the app sees exactly 6 questions.

## Automated tests
`QuestionRoutesTest` (13 tests, uses `InMemoryQuestionRepository` over the Steps 9-12 in-memory hierarchy): sign-in required; only published
questions under published ancestors listed; no leak of correct flags, answer keys or explanations (checked on every question type);
stable default order; each hierarchy filter including hidden branches; type, difficulty, tag, search and combined filters; sorting;
bad filters name their field; cursor walk; detail (options in order, images, breadcrumb, empty options for short answer and written);
true/false options; hidden and malformed ids are 404; tag counts ignore hidden questions.
Android: none (no Android changes in this step).

## Risks
- Same offset-cursor caveat as Steps 9-12 (a row can repeat or be skipped if content changes between pages).
- Text search is `ILIKE` without an index. Fine for thousands of questions; plan `pg_trgm` or full-text search before tens of thousands.
- The tests use an in-memory double, so the SQL in `JdbcQuestionRepository` and `V4__question_bank.sql` is checked only by reading.
- Nothing in the database stops a published question from being malformed. Use `question_integrity_issues` until the admin backend (Step 30) validates on publish.
- `question_versions` is only filled by the seed for now; the admin write path must create a new version on every edit.
- Seed image URLs are placeholders (`example.com`), so image-based demo questions show a broken image until real files exist.
- `current_version` in the detail response is informational; clients must not rely on it for caching yet.

## Definition of done
Both Gradle test suites pass on your machine; `V4` applies on a fresh database via Flyway; both seeds load; the integrity view is empty;
a signed-in `curl` of the three endpoints behaves as in the acceptance list; and a manual check of the raw JSON finds no correct answer.

## Not yet verified
**Nothing in this step has been compiled or run.** No Gradle, Kotlin compiler or PostgreSQL was available when it was written.
Specifically unchecked: that the Kotlin compiles (imports, Ktor/serialization details), that `V4__question_bank.sql` applies
(generated columns, the view), that the JDBC queries return what the in-memory double returns, and that `seed_questions.sql` runs.
Expect small compile fixes.

## Run it
```bash
cd backend && gradle test
# with the stack running and seed_demo.sql loaded:
docker compose exec -T postgres psql -U <DB_USER> -d <DB_NAME> < backend/src/main/resources/db/dev/seed_questions.sql
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/questions?difficulty=easy"
```
