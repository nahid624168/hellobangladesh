# Step 17: My Quiz Sets (create/edit, list, filters, questions)

Builds on Steps 15–16. Backend create/list/update/delete/duplicate/set-questions already existed from Step 15; this step wires the Android client and management UI.

**Status: PARTIAL (source added; not compiled / not run — no Gradle wrapper or Android SDK in the authoring environment).**

## Scope
In:
- Extended `QuizApi` / `QuizRepository` with create, update, delete, duplicate, set-questions, and list filters (`q`, `status`, `lessonId`).
- Extended `QuestionApi` with `searchQuestions` so the picker can search the bank without a forced lesson.
- My Quiz Sets list: search box, status filter chips (All / Draft / Private / Archived), existing sort chips, Create button, per-card Edit / Duplicate / Archive / Delete.
- Create and Edit screens (title, description, status, time limit, pass mark, max attempts, shuffle flags, show-explanations).
- Manage-questions screen: ordered selected list with reorder/remove, bank search results, save via `PUT /quiz-sets/{id}/questions`.
- English + Bangla strings for the new UI.
- Navigation routes: `quiz-create`, `quiz/{id}/edit`, `quiz/{id}/questions`; entry from list and from quiz detail.

Out (and where it goes):
- Subject/category/lesson filter UI beyond the list `lessonId` API param (can be added when content pickers exist).
- Share links and official quizzes (admin steps).
- Image rendering in bank cards (Step 14b / later).
- Compile, instrumented UI tests, offline cache of sets.

## API used (all backend routes from Step 15)
| Call | Client method |
|---|---|
| `GET /quiz-sets?q=&status=&lessonId=&sort=&limit=&cursor=` | `quizSets` (extended) |
| `POST /quiz-sets` | `createQuizSet` |
| `PATCH /quiz-sets/{id}` | `updateQuizSet` |
| `DELETE /quiz-sets/{id}` | `deleteQuizSet` |
| `POST /quiz-sets/{id}/duplicate` | `duplicateQuizSet` |
| `PUT /quiz-sets/{id}/questions` | `setQuizQuestions` |
| `GET /questions?q=&…` | `searchQuestions` (new on QuestionApi) |

## Changed files
- `android/core/network/.../QuizDtos.kt` — Create/Update/SetQuestions requests
- `android/core/network/.../ExamBaseApi.kt` — QuizApi + QuestionApi methods and Http implementations
- `android/app/.../quizzes/QuizRepository.kt` — management methods on `QuizSource`
- `android/app/.../quizzes/QuizViewModels.kt` — list filters/actions, `QuizEditorViewModel`, `QuizQuestionsViewModel`
- `android/app/.../questions/QuestionRepository.kt` — `search`
- `android/app/.../screens/QuizScreens.kt` — list UI with filters and actions
- `android/app/.../screens/QuizManageScreens.kt` — editor and questions screens (new)
- `android/app/.../navigation/ExamBaseNavHost.kt` — new routes and callbacks
- `android/app/src/main/res/values/strings.xml` and `values-bn/strings.xml`
- Test fake in `QuizDetailLoadTest.kt` updated for the new interface

## Acceptance criteria
1. List supports search and status filter and does not merge quiz sets with any other collection.
2. Create produces a draft (or chosen status) owned by the signed-in user; edit updates title/settings/status.
3. Duplicate returns a new set; delete and archive are ownership-scoped (server returns 404 for others).
4. Question order is preserved via the ordered id list; max 100 enforced client-side (server also caps).
5. Loading / empty / error-with-retry / signed-out states remain for list, editor and questions screens.
6. English and Bangla strings exist for the new labels.

## Tests
- Existing unit tests updated for the `QuizSource` signature.
- No new instrumented tests (environment has no Android SDK). Ownership/IDOR remains covered by backend `QuizRoutesTest`.

## Risks / not verified
- Nothing compiled or run.
- Bank search with empty filters depends on backend returning a page; if the server requires a hierarchy filter the UI will show the error.
- Delete confirmation dialog is not yet a separate modal (immediate delete from the card).
- Process-death recovery on the editor is the default ViewModel behaviour only.

## Run it
```bash
cd android
# when a wrapper/SDK is available:
./gradlew :app:testDebugUnitTest
# Home -> My Quiz Sets -> Create / filters / card actions -> Edit -> Manage questions.
```
