# STEP 33a — AI assistant core (Android)

Step 33 is split in three at the owner's request: **33a** (this doc) = client data layer, consent, quota, failure states and a stand-alone assistant screen;
**33b** = AI inside the notes editor; **33c** = flashcards/questions from a selection and the tutor (hints, steps, chat).

## Scope
- Network layer for the Step 32 gateway: `AiApi`, DTOs (`AiDtos.kt`), `HttpExamBaseApi` implementation
- `AiRepository` (token-refresh wrapper like every other repository) behind a small `AiSource` interface so tests need no network
- Consent: versioned dialog, revoke ("Turn off AI"), server-driven re-ask when the wording version changes
- Quota display: requests and tokens used, progress bar on the nearer limit, low warning at 80%, local refusal at the limit, reset time in local time
- Failure states: offline, quota, temporary block, rate limit, content blocked, AI switched off, service unavailable, invalid input, other
- "AI can make mistakes" notice on screen before and after the first answer (server text used when present)
- Assistant screen: tasks Summarize, Explain, Outline, Revision notes, Simplify, Translate; answer language English/Bangla; editable answer; Copy
- Home entry "AI study assistant"; route `ai-assistant`

Out of scope here: notes-editor integration and save-into-note (33b), `make_flashcards` / `make_questions` and tutor/chat (33c).

## Behaviour
1. Screen opens, reads `GET /ai/consent` and `GET /ai/quota`. Both failing offline gives an error view with Retry; one failing still shows the screen.
2. Run: input must be 1-8000 characters (checked locally, server checks again) -> consent must be active -> quota must have room -> `POST /ai/run`.
3. No consent: the dialog opens and **nothing is sent**. "I agree" posts the version the server reported (`currentVersion`), then runs the waiting request. "Not now" drops it and keeps the typed text.
4. If the server answers `ai_consent_required` (for example the wording changed), the dialog opens again and agreeing retries the request.
5. Failures keep the typed text. After blocked content or quota errors the quota is read again, because safety blocks use allowance; provider outages do not.
6. The answer is plain text in an editable field. Nothing is saved anywhere by this step; Copy puts it on the clipboard.
7. Session gone (401 after refresh) leaves for the login screen.

## Privacy
Input and answer live only in the screen's state. They are not logged, not written to Room or DataStore, and not saved by the server for these one-shot tasks (see Step 32). The consent text says this, and says the outside AI service applies its own terms. It does not claim encryption or a retention period for the provider, because we do not know it.

## Consent wording
`ai_consent_body` (English and Bangla). **When this text changes, bump `AiConsentVersion.CURRENT` in the backend** (`2026-11-21` for this update) so everyone sees the dialog again. The Android app always posts the version it read from the server, so the two cannot drift apart.

## Changed files
- New: `android/core/network/.../AiDtos.kt`; `android/app/.../ai/` (`AiRepository`, `AiRules`, `AiAssistantEngine`, `AiAssistantViewModel`); `screens/AiAssistantScreen.kt` (also holds the shared `AiConsentDialog` for 33b/33c); `app/src/test/.../ai/AiAssistantEngineTest.kt`
- Changed: `ExamBaseApi.kt` (`AiApi`), `AppContainer.kt`, `ExamBaseNavHost.kt` (route, Home callback), `HomeScreen.kt` (entry card), `values/strings.xml`, `values-bn/strings.xml`
- Fixed on the way: `loading`, `retry` and `save` were each defined twice in both strings files, which makes resource merging fail; the later copies were removed (same wording).

## Acceptance criteria
- No AI request leaves the device without active consent for the current wording
- Quota (requests and tokens) and reset time are visible; at the limit the button is off and the message says when it resets
- Every failure has its own readable message in English and Bangla, text is never lost, and Retry works
- The notice that AI may be wrong is visible at all times
- Turning AI off takes effect on the server immediately

## Tests (`AiAssistantEngineTest.kt`, plain JUnit, no Android classes)
- Consent flow (8): no consent opens dialog and sends nothing; agree records current version then runs; decline keeps text and never runs later; active consent skips dialog; old wording counts as not given; server `ai_consent_required` re-asks and retries; revoke then run asks again; accept failure keeps dialog open
- Failure states (11): input empty/too long, offline, each server code mapping, validation message, provider failure leaves quota, blocked content refreshes quota, signed out, offline load, notice and quota after answer, blank notice fallback, trimming
- Quota display (7): exhausted, requests left, fraction, low warning, local refusal at limit, load/refresh, reset-time label in two time zones

**Not compiled or run.** This environment has no Android SDK (Gradle fails before reaching the code).

## Risks / not yet verified
- Never compiled; Compose code (`AiAssistantScreen.kt`) is also untested on a device or emulator, and TalkBack was not tried. Live-region announcements are in place but unchecked.
- `LocalClipboardManager` and `LinearProgressIndicator(progress = { })` need Compose BOM 2024.09 (set in `libs.versions.toml`).
- The reset time uses `java.text` on purpose: `java.time` is already used elsewhere in the app, and with `minSdk = 24` it needs core-library desugaring, which is not enabled in `app/build.gradle.kts`. Worth checking in Step 39 (Android 7.x devices).
- Home shows the AI card even while the `ai_assistants` flag is off; a run then fails with the "AI is switched off" message. A flag-aware Home card needs the dashboard to report flags (not built).
- The answer is not rendered as Markdown (plain editable text). 33b decides how it is inserted into notes.
- "Quota" bar compares tokens as the server counts them; if the provider omits counts the server estimates (chars / 4), so the bar is approximate.

## Run it
Backend as in `STEP-32-ai-gateway.md` (flag on, Gemini env vars set). Then `cd android && gradle :app:testDebugUnitTest`, install the debug app, sign in, open "AI study assistant" on Home.
