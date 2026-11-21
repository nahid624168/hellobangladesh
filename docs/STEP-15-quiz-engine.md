# Step 15: Quiz sets and server-scored attempts (backend)

Builds on Step 14. Nothing from Steps 1 to 14 changes except the additions under "Changed files".
Numbering: this is Step 15 of the 40-step plan (`ExamBase_AI_40_Step_Build_Prompt.txt`). It is the quiz engine backend; the Android quiz player and results are Step 16, My Quiz Sets is Step 17; official/admin quizzes and sharing are later.

## Scope
In: create/list/search/filter/sort/edit/duplicate/soft-delete quiz sets; set questions (from the question bank, ordered, max 100);
quiz settings; start (or resume) an attempt with a server deadline; submit with server-side scoring; stored result and review;
attempt history; attempt limit; shuffle of questions and options done on the server.
Out (and where it goes):
- Android screens (My Quiz Sets, create/edit, player, result, history): Steps 16-17.
- Writing your own questions inside a quiz: later (questions come from the bank for now).
- Secure share links, official/admin-published quizzes, random selection from the bank, `published` status: with the admin work.
- Pause/resume of the clock: not supported (policy decision below). Resuming an attempt keeps the original deadline.
- Anything that awards points, badges or streaks: Step 28 (it may read `quiz_attempts`, not `practice_attempts`).

## Policy decisions (change before launch if the owner disagrees)
- Set statuses for learners: `draft` (editable, not playable), `private` (playable by the owner), `archived` (hidden by default, not playable).
- **Late submit**: the deadline is `start + timeLimit`; a submit up to 10 s later is accepted (network slack). After that the server
  answers `409 attempt_expired` and closes the attempt as `expired` with score 0. Answers typed after time is up are not kept.
- One running attempt per learner and set. Starting again returns the same attempt (same id, same deadline). Enforced by a unique index.
- Attempt limit counts finished attempts (`submitted` + `expired`).
- Scoring (`QuizScoring`, reuses `Grader`): correct +marks, wrong -negativeMarks, blank 0 (no penalty), written questions never graded
  and excluded from the maximum, total never below 0, `percent = score / maxScore * 100`, `passed` null when no passing score or nothing gradable
  (pass mark is inclusive).
- A blank answer in a submission (no choice, no text) counts as unanswered; an invalid one (foreign option, wrong shape, too long) rejects
  the whole submit with `400` and `fields` like `answers[0].selectedOptionIds`; nothing is recorded.
- Submit is idempotent: a second submit returns the stored result and changes nothing.
- Review (correct answers, explanation) appears only after the attempt is finished and only if the set has `showExplanations = true` (frozen at start).
- Delete is a soft delete: the set vanishes from every endpoint, attempts are kept.

## API (all under `/api/v1`, all require a Bearer token, owner-only: someone else's id returns 404)
| Method | Path | Purpose |
|---|---|---|
| POST | `/quiz-sets` | Create. Body `{title, description?, lessonId?, status?, settings?}` -> 201 `QuizSetDto` |
| GET | `/quiz-sets?q=&status=&lessonId=&sort=recent\|title\|played&limit=&cursor=` | My Quiz Sets: one card per set with `attemptCount`, `lastAttemptAt`, `bestPercent`. Archived hidden unless `status=archived` |
| GET | `/quiz-sets/{id}` | `{quizSet, questionIds[]}` |
| PATCH | `/quiz-sets/{id}` | Partial edit (`clearLesson`, `description:""` to clear) |
| DELETE | `/quiz-sets/{id}` | Soft delete, 204 |
| POST | `/quiz-sets/{id}/duplicate` | Copy as a draft with the same questions, 201 |
| PUT | `/quiz-sets/{id}/questions` | Replace the ordered question list (visible published bank questions only) |
| POST | `/quiz-sets/{id}/attempts` | Start or resume -> `QuizAttemptView` (questions without answers, `deadlineAt`, `serverNow`) |
| GET | `/quiz-sets/{id}/attempts` | Attempt history, newest first |
| GET | `/quiz-attempts/{id}` | Running: questions. Finished: `result`. A running attempt past deadline + grace is closed on read |
| POST | `/quiz-attempts/{id}/submit` | Body `{answers:[{questionId, selectedOptionIds[], answerText?}]}` -> `QuizResultDto` |

Settings: `timeLimitSeconds` 30..14400, `passingPercent` 1..100, `maxAttempts` 1..100, `shuffleQuestions`, `shuffleOptions` (true/false questions are never option-shuffled), `showExplanations`.
Error codes added: `not_playable`, `empty_quiz`, `quiz_unavailable`, `attempt_limit_reached`, `attempt_expired` (all 409); `rate_limited` (429, 60 start/submit calls per user per minute, in-memory).
Clients must use `serverNow` and `deadlineAt` for the countdown, never the phone clock.

## Database (`V6__quiz_sets.sql`)
`quiz_sets` (owner, title, description, lesson, status, settings columns, `deleted_at`), `quiz_set_questions` (ordered, cascades with the set or question),
`quiz_attempts` (status, deadline, score/max/percent/passed, frozen `passing_percent` and `show_explanations`, `questions_snapshot` JSONB = what was served),
`quiz_attempt_answers` (per question result; **no foreign key on question_id** so history survives question deletion).
Unique partial index: one `in_progress` attempt per user and set.

## Changed files
New: `V6__quiz_sets.sql`, package `com.exambase.backend.quizzes` (`QuizModels`, `QuizScoring`, `QuizRepository`, `JdbcQuizRepository`, `QuizService`, `QuizRoutes`),
tests `QuizScoringTest`, `QuizRoutesTest`, `InMemoryQuizRepository`. Edited: `Application.kt` (optional `AppServices.quizzes`, route registration, version 0.6.0), `README.md`.

## Acceptance criteria
1. Without a token every quiz endpoint returns 401; another user's set or attempt returns 404 for read, edit, delete, duplicate, start and submit.
2. Each created set is its own item in the list; search, status, lesson and sort work; archived sets are hidden by default.
3. Only visible published bank questions can be added; duplicates and more than 100 are rejected.
4. A started attempt contains no correct flag, answer key or explanation.
5. Submit marks on the server by the scoring rules above; the score, percent and passed flag are stored.
6. Submitting twice returns the first result. Starting twice returns the same attempt.
7. After deadline + 10 s a submit gives 409 and the attempt is `expired` with score 0; within the grace window it is accepted.
8. `maxAttempts` is enforced; draft, archived and empty sets cannot be started.
9. `showExplanations = false` removes the review from every result response.
10. Delete hides the set but keeps its attempts.

## Automated tests (written, not yet run)
`QuizScoringTest` (5, pure) and `QuizRoutesTest` (21, over in-memory doubles, with a controllable clock).
Not covered by any test: the SQL in `JdbcQuizRepository` and `V6` (JSONB snapshot, `ON CONFLICT ... WHERE`, `ANY(?)` arrays, `LEFT JOIN LATERAL`), true concurrency
(two simultaneous starts or submits), and the rate limiter across instances.

## Risks
- **Review reveals answers.** After finishing, a learner sees the correct answers (unless the set hides them) and may retake. Fine for self-study; it is not an exam.
  Exams and contests (later steps) need their own rules.
- A quiz mixes bank questions that can also be practised with answers visible, so quiz scores are only "honest" for the learner themselves. Do not use them for public leaderboards until official quizzes exist.
- Question content is frozen in the attempt snapshot, but marks and answer keys are read live at submit. If an admin edits a question mid-attempt (editing arrives in a later step), scoring uses the new key. Add `question_version` checks then.
- A question deleted after an attempt started is skipped when scoring; the result then has fewer rows than the snapshot.
- `setQuestions` checks visibility with one query per question (max 100). Replace with a batch query if it shows up in load tests.
- Offset cursors and the in-memory rate limiter have the same caveats as earlier steps; move the limiter to Redis before more than one backend instance.
- Expired attempts are closed lazily (when read, started again or submitted). A background sweep is not needed for correctness but will be for analytics.

## Not yet verified
**Nothing in this step has been compiled or run.** The sandbox used to write it had Java and Gradle but no network, so dependencies could not be downloaded.
Most likely trouble spots: Kotlin imports and smart casts, `ON CONFLICT (user_id, quiz_set_id) WHERE status = 'in_progress'` against the partial index,
`questions_snapshot::text` and `?::jsonb`, and the test helper `send` with Ktor client lambdas.

## Run it
```bash
cd backend && gradle test
# stack running, seed_demo.sql and seed_questions.sql loaded, <token> from login:
curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"Algebra drill","status":"private","settings":{"timeLimitSeconds":600,"passingPercent":60}}' http://localhost:8080/api/v1/quiz-sets
curl -X PUT -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"questionIds":["<questionId>"]}' http://localhost:8080/api/v1/quiz-sets/<setId>/questions
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/quiz-sets/<setId>/attempts
curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"answers":[{"questionId":"<questionId>","selectedOptionIds":["<optionId>"]}]}' http://localhost:8080/api/v1/quiz-attempts/<attemptId>/submit
```
