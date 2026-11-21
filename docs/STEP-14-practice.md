# Step 14: Practice (check answer, explanation reveal, practice history, Android practice screens)

Builds on Step 13. Nothing from Steps 1 to 13 changes except the additions listed under "Changed files".

## Scope
In: server-side answer checking for all six question types; reveal of the correct answer, accepted answers, explanation and
explanation images only after an answer is submitted; a record of every practice attempt; practice history and a per-lesson
practice summary (API); Android practice session per lesson, and a "Practice questions" card on the lesson screen (English + Bangla).
Out (and where it goes):
- Android practice-history screen: Step 14b or Step 28 (the API is ready: `GET /users/me/practice-history`).
- Question bookmarks and question reporting: Step 14b (not yet built) (unchanged from Step 13).
- Hints before the answer: AI assistant (Steps 32-33).
- Showing pictures inside the app: needs an image loader (Coil); until then images open in the browser.
- Anything that awards points, badges or streaks: Step 28. Practice attempts must NOT feed those (see Risks).

## Rules
- Marks (practice figure only): correct = +marks, incorrect = -negativeMarks, written = 0 and result `ungraded`.
- single_choice, true_false, image_based: exactly one option. multiple_select: at least one, **all-or-nothing** (chosen set must equal the correct set).
- short_answer: case and spacing are ignored (`"  DHAKA "` matches `Dhaka`); any accepted answer counts. Compared in Kotlin on both sides.
- written: never auto-graded; the learner compares with the model answer (explanation).
- Wrong option ids, repeated ids, text for a choice question (or the reverse), blank text, text over 500 (short) / 5000 (written) characters -> `400 invalid_request` with `fields` naming `selectedOptionIds` or `answerText`. Nothing is recorded for a rejected request.
- Missing, hidden, or malformed question id -> `404 not_found` (same visibility rule as Step 13).
- Rate limit: 60 checks per user per minute (in-memory; move to Redis before more than one backend instance) -> `429 rate_limited`.
- Retrying a question is allowed and unlimited; each check is a new attempt row.

## API (all require a Bearer token)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/questions/{id}/check` | Body `{ "selectedOptionIds": ["..."], "answerText": "..." }` (send only the field that fits the type). Returns `attemptId, result, awardedMarks, correctOptionIds[], acceptedAnswers[], explanation, explanationBn?, explanationImages[], version` |
| GET | `/api/v1/users/me/practice-history?lessonId=&questionId=&limit=&cursor=` | Your attempts, newest first. Only questions that are still visible are returned |
| GET | `/api/v1/users/me/practice-summary?lessonId=` | `{ lessonId, totalQuestions, attemptedQuestions, correctQuestions }` (correct = latest attempt per question is correct) |

The learner-facing list and detail from Step 13 are unchanged and still contain no answers; `/check` is the only response that does.

## Database (`V5__practice_attempts.sql`)
`practice_attempts(id, user_id -> users, question_id -> questions, question_version, selected_option_ids uuid[], answer_text, result, awarded_marks, created_at)`
with indexes on `(user_id, created_at desc)` and `(user_id, question_id, created_at desc)`. Rows are deleted with the user or the question.

## Android
- `core/network`: `QuestionDtos.kt`, new `QuestionApi` interface implemented by `HttpExamBaseApi` (existing fakes still compile).
- `app/.../questions`: `QuestionRepository` (token refresh via `authed`), `PracticeRules` (pure), `PracticeEngine` (state machine, no Android
  dependency), `PracticeViewModels`.
- `screens/PracticeScreen.kt`; `LessonScreen` gets a practice card and an `onPractice` callback; route `practice/{lessonId}`.
- Screen states: loading, empty, error with retry, sign-out, asking (with inline network/server problem), feedback, done.
  The result is written as text as well as colour. Answer options are selectable rows with radio/checkbox roles.
- A question that was unpublished after the list loaded is skipped. A failed summary load hides the card without blocking the lesson.
- New strings in `values/` and `values-bn/` (Bangla wording should be reviewed by a native speaker).

## Changed files
Backend new: `V5__practice_attempts.sql`, `PracticeModels.kt`, `Grader.kt`, `PracticeRepository.kt`, `JdbcPracticeRepository.kt`,
`PracticeService.kt`, `PracticeRoutes.kt`. Backend edited: `Application.kt` (new optional `AppServices.practice`, route registration,
version 0.5.0). Test double edited: `InMemoryQuestionRepository.visible` is no longer private.
Android new: see above. Android edited: `ExamBaseApi.kt`, `AppContainer.kt`, `LessonScreen.kt` (`openUrl` is now `internal`),
`ExamBaseNavHost.kt`, both `strings.xml`, `app/build.gradle.kts` (versionName 0.5.0).

## Acceptance criteria
1. Without a token the three endpoints return 401.
2. Checking a correct answer returns `correct`, the correct option id(s), the explanation and `+marks`; a wrong one returns `incorrect` and `-negativeMarks` and still reveals the answer.
3. Multiple select needs the exact set; short answer ignores case and spacing; written is `ungraded` with the model answer shown.
4. Explanation-placement images appear only in the check response, never in the Step 13 detail.
5. Hidden, draft, archived or malformed questions return 404 and record nothing; bad input returns 400 naming the field and records nothing.
6. History is per user, newest first, filterable by lesson and question, and omits questions that later became hidden.
7. Summary counts distinct attempted questions and uses the latest attempt per question.
8. The 61st check in a minute returns 429.
9. On a device: the lesson shows "x of y tried" once questions exist; a full practice run ends on a summary; airplane mode on "Check answer" keeps the selection and offers a retry.

## Automated tests (written, not yet run)
Backend: `GraderTest` (7), `PracticeRoutesTest` (13, over in-memory doubles). Android: `PracticeRulesTest` (6), `PracticeEngineTest` (12, fake source, no Android runtime).
Not covered by any test: the SQL in `JdbcPracticeRepository` / `V5`, the Compose screen, navigation, and the real HTTP client.

## Risks
- **Practice is not a score.** Answers are checked on the server but there is no server-held timer or question set, so a learner can retry freely and see answers. Never use `practice_attempts` for points, badges, streaks or leaderboards; Step 28 must read quiz and contest attempts.
- Answer text for written questions is stored (the learner's own words). Decide a retention period before launch and include it in data export and account deletion text (rows already cascade on account deletion).
- Offset cursors and the in-memory rate limiter have the same caveats as earlier steps.
- Short-answer matching is exact after folding case and spacing: no typo tolerance, no Bangla digit/Latin digit equivalence. Content authors must list the variants they accept.
- `practice-summary` runs two queries per lesson view; fine at this scale, cache or fold into the lesson response if it shows up in load tests.
- Practice loads at most 200 questions per lesson in one run.

## Not yet verified
**Nothing in this step has been compiled or run.** No Gradle, Kotlin compiler, Android SDK or PostgreSQL was available when it was written.
Most likely trouble spots: Kotlin imports and smart-cast details, `LinearProgressIndicator(progress = Float)` (deprecated in newer Compose Material3, still compiles),
`createArrayOf("uuid", ...)` and the `DISTINCT ON` query against real PostgreSQL, and how Ktor reports a malformed JSON body (the test expects 400).

## Run it
```bash
cd backend && gradle test
# stack running, seed_demo.sql and seed_questions.sql loaded, <token> from login:
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/questions?lessonId=<lessonId>"
curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"selectedOptionIds":["<optionId>"]}' "http://localhost:8080/api/v1/questions/<questionId>/check"
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/users/me/practice-summary?lessonId=<lessonId>"
cd ../android && gradle :app:testDebugUnitTest
```
