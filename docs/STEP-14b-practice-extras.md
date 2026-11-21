# Step 14b: Question bookmarks, reporting, practice history, images (Coil)

Builds on Step 14. Completes the gaps called out in `STEP-14-practice.md` and the work queue.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- **Backend** (`V7__question_bookmarks_reports.sql`): `question_bookmarks`, `question_reports` with one open report per user/question.
- APIs: `PUT/DELETE /questions/{id}/bookmark`, `GET /users/me/question-bookmarks`, `POST /questions/{id}/report` (reasons: wrong_answer, unclear, offensive, typo, duplicate, other).
- Existing `GET /users/me/practice-history` exposed on Android.
- **Android**: Coil dependency; `QuestionImages` composable used in practice feedback; Practice History screen (paged) with Home entry; network DTOs and repository methods for history, bookmarks, reports.
- English + Bangla strings for history and report labels.

Out / light:
- Full report dialog UI on every practice question (API is ready; a minimal entry can be added later).
- Bookmark toggle on every practice card (API ready).
- Images inside quiz player/result (same Coil helper can be reused in 16b follow-up).
- Admin review of reports (Step 30).

## Acceptance criteria
1. Bookmarks are private; another user's set is never returned; invisible questions return 404 on bookmark/report.
2. Practice history is paged and scoped to the signed-in user; optional lesson filter works.
3. Report rejects unknown reasons with 400 `fields.reason`; duplicate open reports are idempotent.
4. Practice history screen shows loading / empty / error / signed-out.
5. Coil is on the classpath; question and explanation images render in practice when URLs are present.

## Changed files
- `backend/.../V7__question_bookmarks_reports.sql`
- `QuestionModels`, `QuestionRepository`, `JdbcQuestionRepository`, `QuestionService`, `QuestionRoutes`, `InMemoryQuestionRepository`
- `Application.kt` version `0.7.0`
- Android: `libs.versions.toml`, `app/build.gradle.kts` (Coil), `QuestionDtos`, `ExamBaseApi`, `QuestionRepository`, `PracticeViewModels`, `QuestionImages.kt`, `PracticeHistoryScreen.kt`, `PracticeScreen.kt` (images), `HomeScreen`, `ExamBaseNavHost`, strings

## Not verified
Nothing compiled or run. No PostgreSQL / Gradle / Android SDK in the authoring environment.

## Run it
```bash
cd backend && gradle test
cd android && gradle :app:testDebugUnitTest
# App: Home -> Practice history; practice a lesson and confirm images load when the bank has URLs.
```
