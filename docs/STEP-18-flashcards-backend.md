# Step 18: Flashcards backend and spaced repetition

Flashcard sets are **separate** entities from quiz sets (own tables, own list API, never merged).

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- Schema `V8__flashcards.sql`: `flashcard_sets`, `flashcards`, `flashcard_srs`, `flashcard_reviews`
- CRUD for sets (create/list/search/filter/sort/update/soft-delete/duplicate)
- Cards: add/update/delete/reorder; front/back text (+ BN), optional image URLs, difficulty, tags
- Study: due queue, review with ratings `again|hard|good|easy`
- Documented SM-2-style scheduler (`SpacedRepetition`) with unit tests
- Set analytics (card count, due count, reviewed count, average ease)
- Ownership: another user's set/card always 404
- Routes under `/api/v1/flashcard-sets` and `/api/v1/flashcards/{id}`
- In-memory repository + routes smoke test; service version `0.8.0`

Out (Step 19+):
- Android UI, offline review, import bulk CSV, audio fields, admin-published decks

## Algorithm (SpacedRepetition)
- Start: ease 2.50, interval 0, reps 0, due now
- again: reps=0, interval=1d, ease−0.20 (≥1.30)
- hard: interval≈prev×1.2, ease−0.15
- good: 1d → 6d → prev×ease; reps++
- easy: 4d first, else prev×ease×1.3; ease+0.15; reps++

## Acceptance criteria
1. Sets and cards are owner-scoped; IDOR returns 404.
2. Scheduler unit tests pin again/good/easy/floor behaviour.
3. Due list includes never-reviewed cards; review writes SRS + history.
4. Archived sets hidden from default list; soft delete keeps history tables.
5. Flashcard sets never share storage or list endpoints with quiz sets.

## Changed files
- `V8__flashcards.sql`
- package `com.exambase.backend.flashcards` (models, SpacedRepetition, repository, JDBC, service, routes)
- tests: `SpacedRepetitionTest`, `InMemoryFlashcardRepository`, `FlashcardRoutesTest`
- `Application.kt` (AppServices.flashcards, routes, version 0.8.0)

## Not verified
Nothing compiled or run in this environment.

## Run it
```bash
cd backend && gradle test --tests 'com.exambase.backend.flashcards.*'
```
