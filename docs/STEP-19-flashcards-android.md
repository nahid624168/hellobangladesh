# Step 19: Android flashcards and My Flashcard Sets

Builds on Step 18 backend. Flashcard sets remain a **separate** collection from quiz sets in navigation and UI.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- Network: `FlashcardDtos`, `FlashcardApi` + HTTP client methods for sets, cards, due, review, analytics
- `FlashcardRepository` / `FlashcardSource`
- My Flashcard Sets list (search, status filter, sort recent/title/cards, create, duplicate, delete)
- Create/edit set screen
- Set detail: card list, add card, delete card, Study entry
- Study mode: due queue, flip animation, ratings Again/Hard/Good/Easy, session complete
- Home entry for My Flashcard Sets
- English + Bangla strings

Out / later:
- Full offline review + sync (Step 22)
- Card image rendering in study (Coil helper exists from 14b; not wired here)
- Star/favourite, shuffle UI toggle, import
- Instrumented UI tests

## Acceptance criteria
1. Flashcard sets never appear in the quiz list or share routes with quizzes.
2. List supports search/filter/sort and empty/loading/error/signed-out states.
3. Study only shows due (or new) cards from the server; ratings call `POST /flashcards/{id}/review`.
4. Create/edit/delete are ownership-scoped (404 from server for others).
5. EN + BN strings exist for the new screens.

## Changed files
- `core/network/FlashcardDtos.kt`, `ExamBaseApi.kt`
- `flashcards/FlashcardRepository.kt`, `FlashcardViewModels.kt`
- `screens/FlashcardScreens.kt`
- `AppContainer.kt`, `ExamBaseNavHost.kt`, `HomeScreen.kt`
- `values/strings.xml`, `values-bn/strings.xml`

## Not verified
Nothing compiled or run. Flip animation is Compose graphicsLayer only (no 3D engine).

## Run it
```bash
cd android && gradle :app:testDebugUnitTest
# Home -> My Flashcard Sets -> Create -> add cards -> Study
```
