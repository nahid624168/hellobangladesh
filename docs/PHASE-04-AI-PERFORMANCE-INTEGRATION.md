# Phase 04 — AI Performance & Integration

## Implemented

- Converted the backend AI provider contract and AI service execution to Kotlin suspend functions.
- Gemini requests now use `HttpClient.sendAsync` rather than blocking `HttpClient.send`.
- The HTTP future is cancelled when the calling coroutine is cancelled; this lets Ktor propagate client disconnect/request cancellation.
- Added bounded request timeout (30 seconds by default) and one bounded retry for transient network errors, HTTP 429, and HTTP 5xx responses. Non-transient 4xx errors are not retried.
- Cancellation is handled separately in the AI service: quota reservations are settled as unused, a cancellation usage record is written, and cancellation is rethrown rather than converted into a generic 503.
- Android Ktor client now has explicit connect (10s), socket (120s), and request (120s) timeouts. Cancellation exceptions continue to propagate through the API client.
- Tightened the server prompt for AI MCQ generation to match the Android parser's exact `Q / A-D / Correct / Explanation` format and to constrain generated questions to supplied source text.
- Existing Android AI repository, consent/quota flow, ViewModel screen integration, flashcard draft parser/saver, and generated-question parser tests were reviewed.

## Existing integration boundary / known gap

The Android app calls the authenticated `/api/v1/ai/run` endpoint through `AiRepository` and the AI Assistant / Note AI ViewModels. The generated-question parser and flashcard saver exist, but the current screens do not yet provide a complete reviewed-draft-to-persistent-quiz / flashcard workflow from the AI Assistant output. The current Note AI sheet can insert, replace, or save AI output as a note. This is deliberately documented rather than claiming the full quiz/flashcard publishing flow is complete.

## Verification

- Source-level review completed.
- Android/backend builds and tests were not executed in this environment because Gradle/Android SDK are not installed here. GitHub Actions is configured to run CI and build the debug APK.
- Real Gemini API calls were not made; configure `GEMINI_API_KEY`, `GEMINI_MODEL`, and the server-side AI feature flag in the deployed backend before live testing.
