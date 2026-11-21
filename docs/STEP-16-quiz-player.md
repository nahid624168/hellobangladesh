# Step 16: Android quiz player and results (split into 16a, 16b, 16c)

Builds on Step 15 (quiz engine backend). Nothing from Steps 1 to 15 changes except the additions under "Changed files".
Step 16 was split in three at the owner's request (`CLAUDE.md`: a step that is too large becomes `NNa`, `NNb`, ...).
**Status: 16a DONE; 16b PARTIAL (player source added, not compiled); 16c PARTIAL (result/history source added, not compiled).**

## The three parts
| Part | Scope | Status |
|---|---|---|
| 16a | Quiz network layer (DTOs and `QuizApi` for the whole Step 15 API), `QuizRepository`, quiz list screen, quiz instructions/detail screen with attempt history preview, Home entry, English + Bangla strings, unit tests | DONE (not compiled) |
| 16b | "Start" / "Resume" button on the detail screen, quiz player (server-based countdown, question navigation, answer state, submit confirmation, `attempt_expired` and `rate_limited` handling), process-death recovery, player tests | PARTIAL (source added; no build or UI tests; see notes below) |
| 16c | Result summary, answer review (honours `showExplanations`), paged attempt history, open-result, retry to quiz instructions | PARTIAL (source added; compile/UI tests pending; offline is retry-only, no cache) |

## 16a scope
In: list of the learner's quiz sets (paged, sortable: recent, A-Z, most played; one card per set with question count, attempt count,
best percent, Draft/Archived badge); quiz detail with the rules (questions, time limit, pass mark, attempts used of the maximum, shuffle
settings, whether explanations are shown, best score), plain-language instructions, a message when the quiz cannot be taken
(draft, archived, empty, attempt limit used up), a "you have a quiz in progress" note, and the five most recent attempts; entry from Home;
loading, empty, error-with-retry and signed-out states; refresh when returning to the list or detail.
Out (and where it goes):
- Start/Resume button and the player: 16b. (The detail screen deliberately has **no** start button yet; a button with nowhere to go would be a stub.)
- Opening a finished attempt's result, review, full history screen, retry: 16c.
- Search, filters (subject/lesson/status/date/progress), create, edit, duplicate, archive, delete, add questions from the bank: Step 17.
- Related quizzes on lessons: Step 11b.

## API used (all exist since Step 15; the client for all six is written now, 16a calls the first three)
| Call | Used by |
|---|---|
| `GET /quiz-sets?sort=&limit=&cursor=` | list (16a) |
| `GET /quiz-sets/{id}` | detail (16a) |
| `GET /quiz-sets/{id}/attempts?limit=&cursor=` | detail (16a), history screen (16c) |
| `POST /quiz-sets/{id}/attempts` | start/resume (16b) |
| `GET /quiz-attempts/{id}` | resume (16b), result (16c) |
| `POST /quiz-attempts/{id}/submit` | submit (16b) |

## Rules implemented in 16a (`QuizRules`, pure and unit-tested)
- Startability, first match wins: archived -> `ARCHIVED`; any status other than private -> `DRAFT`; no questions -> `EMPTY`; a running attempt -> `RESUME`;
  finished attempts >= `maxAttempts` -> `LIMIT_REACHED`; otherwise `READY`. The server enforces all of these again on start, so this only decides the message.
- "Finished" means `submitted` or `expired` (same as the server's attempt limit). A running attempt resumes even when the limit is used up.
- The detail screen loads the set and up to 3 pages (150) of history. If either call fails the whole screen shows error-with-retry, so
  "attempts used" is never shown wrong. More than 150 attempts sets `attemptsTruncated` (counts become lower bounds; only matters for sets without a limit).
- Time is shown as `mm:ss` or `h:mm:ss`; percent as `80%` or `66.7%`; dates as the UTC day (`2026-11-21`). No locale or time-zone logic.

## Database
None. No backend change in this part. Next free migration number stays `V7`.

## Changed files
New: `android/core/network/.../QuizDtos.kt`, `android/app/.../quizzes/QuizRepository.kt`, `QuizRules.kt`, `QuizViewModels.kt`, `android/app/.../screens/QuizScreens.kt`,
tests `quizzes/QuizRulesTest.kt`, `quizzes/QuizDetailLoadTest.kt`, this document.
Edited: `ExamBaseApi.kt` (new `QuizApi` interface, implemented by `HttpExamBaseApi`; existing fakes keep compiling), `AppContainer.kt` (`quizRepository`),
`ExamBaseNavHost.kt` (routes `quizzes`, `quiz/{id}`), `HomeScreen.kt` (My Quiz Sets card opens the list; Flashcard and Notes cards stay placeholders until Steps 19 and 21),
`values/strings.xml` and `values-bn/strings.xml` (42 new strings each), `README.md`, `docs/PROGRESS.md`.

## Acceptance criteria (16a)
1. Home shows a My Quiz Sets card that opens the quiz list; every quiz set is its own card and flashcard sets are not mixed in.
2. The list pages as you scroll, sorts by recent / A-Z / most played, and shows loading, empty, error-with-retry and network-offline states; a 401 that cannot be refreshed leaves for the login screen.
3. A card shows the title, description, question count, attempt count, best percent (only after a finished attempt) and a Draft or Archived badge.
4. The detail screen shows every rule from the quiz settings; "attempts used" appears only when the set has a limit.
5. A draft, archived, empty or used-up quiz shows the matching message; a quiz with a running attempt shows the "in progress" note.
6. No screen shows a correct answer or explanation (none is requested in this part).
7. Returning to the list or detail from another screen refreshes it without a spinner.
8. All text exists in English and Bangla; the screens have headings for TalkBack and use 48dp-safe components from the design system.

## Automated tests (written, not yet run)
`QuizRulesTest` (9): startability order, limits, clock, percent and date formatting.
`QuizDetailLoadTest` (7, fake `QuizSource`): derived counts and best score, expired attempts, paging with repeated rows, page limit and truncation flag, failure of the set, failure of a later history page.
Not covered: the Compose screens, `QuizListViewModel` paging and sort (needs `kotlinx-coroutines-test` for `viewModelScope`; not in the project yet), `HttpExamBaseApi` quiz calls against a real server.

## Risks
- The list does not filter out archived sets by itself: the server hides archived sets unless `status=archived` is sent, and this part never sends it, so archived sets are not listed. The "Archived" badge only matters once Step 17 adds the archived filter.
- A draft set is listed (so the learner can find it) but cannot be taken; it can only be fixed in Step 17.
- Dates show in UTC, so an attempt made just after midnight local time can show the previous or next day. Acceptable until a date formatter with desugaring is added.
- "Attempts used" counts what the server returned; a very long history on a set without a limit is truncated at 150 and only the count display is affected.
- `QuizSource` mixes list, detail and attempt calls in one interface so 16b and 16c reuse the same fake; split it if it grows.

## Not yet verified
**Nothing in this part has been compiled or run.** The sandbox had Java but no Gradle, no Android SDK and no network, so dependencies could not be fetched.
Most likely trouble spots: composable calls inside `buildList` and `let` lambdas in `QuizScreens.kt`, `String.format` with `Locale.ROOT`, overriding interface methods that have default parameters (`QuizSource`), and type inference of `listOf` with mixed `Success`/`Failure` in `QuizDetailLoadTest`.

## Run it
```bash
cd android && gradle :app:testDebugUnitTest --tests "com.exambase.ai.quizzes.*"
# backend running with seed data and at least one quiz set with questions (see Step 15 "Run it"), then run the app:
# Home -> My Quiz Sets -> open a set -> rules, instructions, recent attempts.
```

## 16b implementation notes (added 2026-11-21)

Implemented in this increment:
- The quiz detail screen now exposes Start or Resume only when `QuizRules` says READY or RESUME. The app calls the existing server `POST /quiz-sets/{id}/attempts`; the backend remains authoritative and returns the existing running attempt when resuming.
- Added the Android quiz player route and screen. It loads the server attempt, displays one question at a time, supports single-choice, multiple-select, short-answer and written responses, question navigation, a submit confirmation, and result summary returned by the submit endpoint.
- Countdown is derived from `deadlineAt - serverNow` returned by the backend; leaving/re-entering does not pause the server clock. Reaching zero triggers submit. A restored navigation entry calls the start endpoint again, which resumes the server's in-progress attempt.
- Choice and text responses are encoded in Bundle-saveable Compose state so Android can restore them after process recreation. No correct answers or explanations are rendered while the attempt is running.
- Added English and Bangla player strings.

Known limitations / verification:
- This source environment has no Gradle executable or Android SDK, so the project could not be compiled and Compose/UI tests could not be run. Treat this increment as implemented but unverified until the commands below pass on a configured Android machine.
- The player currently shows option/question text but does not render question image attachments; image rendering is still a follow-up.
- Backend start/resume returns the server's current attempt but does not persist per-question draft answers before final submit. Local answers are restored by Android saved state; if Android clears the app's saved task state entirely, answers cannot be recovered. A future backend draft-answer endpoint would be needed for cross-device recovery.
- Countdown uses the server-provided `serverNow` and device elapsed wall clock while the screen is active; backend still rejects submissions after deadline, so the client timer is display/auto-submit assistance, not an authority.

Run on a configured machine:
```bash
cd android
gradle :app:testDebugUnitTest
# Then run app: Home -> My Quiz Sets -> quiz detail -> Start/Resume.
```


## 16c implementation notes (added 2026-11-21)

Implemented in this increment:
- Added a result screen that reloads the finished attempt from `GET /quiz-attempts/{id}` rather than trusting transient player state.
- Displays score, maximum score, percentage, pass/fail when the server supplies it, and expired status.
- Displays per-question grading and the learner's submitted response. Explanation/review data is rendered only when the server returns `review`; the backend returns `null` when `showExplanations` is disabled.
- Added a paged attempt-history screen using the existing attempts endpoint. Finished attempts open their result; in-progress attempts are not misrepresented as finished results.
- Recent attempts on the quiz detail screen now open finished results and link to full history. The player result offers a dedicated answer-review action. Retry returns to quiz instructions; the existing server start endpoint remains authoritative for attempt limits and availability.
- Added English and Bangla strings. No database migration or backend API change was needed; next migration remains `V7`.

Known limitations / verification:
- No Gradle wrapper script/JAR or Android SDK is included in this source archive, and no system Gradle executable is available in the authoring environment. Android compilation and UI tests therefore remain unverified. XML resource files were parsed successfully.
- Offline behavior is safe retry-only: network errors are shown and can be retried. Results/history are not cached for offline viewing.
- Correct option IDs and accepted-answer values are displayed as IDs/text because the result DTO does not include option label objects.
- Question and explanation images are not yet rendered in result review.

Acceptance criteria for 16c:
1. A finished attempt can be reopened from recent attempts and full history.
2. Result summary uses server-calculated score, percentage and pass status.
3. Per-question review appears only when the API includes review data; hidden explanations are never reconstructed on-device.
4. History loads additional pages without duplicating attempt IDs.
5. Retry returns to the quiz instructions; the server enforces retry limits.
6. Loading, empty, signed-out and retryable error states are represented.
7. English and Bangla strings exist for the new screens.
