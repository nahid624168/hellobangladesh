# STEP 33c — AI content generation and learning tutor

## Implemented scope

- AI-generated MCQ and flashcard drafts from pasted study text or directly from the Notes editor selection/whole-note action, using the Step 32 `make_questions` and `make_flashcards` tasks.
- Editable review UI: question text, every option, correct option, explanation, flashcard front/back, and remove item before save.
- Reviewed flashcards save into the user's separate **My Flashcard Sets** collection through the authenticated flashcard API. Partial card creation is rolled back by deleting the new set.
- Reviewed MCQs save into a separate private **My AI Quiz Sets** collection. This uses owner-scoped API routes and migration V20; it does not insert generated content into the official question bank.
- AI tutor supports a hint-first task, step-by-step explanations, and persistent chat.
- Conversation history can be listed, opened, and deleted; all endpoints enforce the signed-in user's ownership.
- Existing consent, quota, failure handling and the “AI may be wrong” notice are reused.
- English and Bangla labels added for the new screens.

## Private AI quiz storage

Endpoints (JWT required):
- `GET /api/v1/ai/quiz-sets`
- `POST /api/v1/ai/quiz-sets`
- `GET /api/v1/ai/quiz-sets/{id}`
- `DELETE /api/v1/ai/quiz-sets/{id}`

The `ai_generated_quiz_sets` and `ai_generated_quiz_questions` tables are private to the owner. Each set contains 1–5 reviewed questions, 2–6 options per question, one correct option index and an explanation. These are saved AI study drafts, deliberately isolated from official question bank content. They are not yet playable by the standard timed Quiz Engine; that is a future integration, not part of Step 33's review-and-save acceptance criteria.

## Android entry points

Home now links to:
- AI Content Studio — generate, review, edit and save MCQs/flashcards.
- AI Learning Tutor — hints, step-by-step help, chat and conversation history.

## Files added/changed

- `AiGeneratedQuizStore.kt`, `V20__ai_generated_quiz_sets.sql`, and AI routes.
- AI DTO/API/repository extensions for generated quiz sets and conversations.
- `AiContentViewModel.kt`, `AiTutorViewModel.kt`, `AiContentScreen.kt`, `AiTutorScreen.kt`.
- Navigation, Home shortcuts, English/Bangla strings, and this progress documentation.
- Existing `AiGeneratedContent` parser and `AiGeneratedFlashcardSaver` remain the validation and flashcard persistence layer.

## Verification status

Source and XML resource structure have been reviewed. XML parsing passed. Backend and Android Gradle tests/builds have **not** been run in this environment because Gradle/Android SDK tooling is unavailable here. Run backend tests, Android unit tests, and install on a physical device before treating the release as verified.
