# STEP 33b — AI in the notes editor (Android)

Second of three parts of Step 33 (33a core, **33b notes editor**, 33c flashcards/questions and tutor). Builds on `STEP-33a-ai-assistant-core.md`.

## Scope
- "AI" button in the note editor toolbar opens a bottom sheet
- The AI works on the **selected text**, or on the **whole note** when nothing is selected
- Tasks: Summarize, Explain, Outline, Revision notes, Simplify, Translate; answer language English/Bangla
- The answer is shown in an editable box with the "AI can make mistakes" notice. Nothing reaches the note until the user presses a button:
  **Insert below**, **Replace selected text / Replace whole note**, **Save as new note** (separate note, open note untouched), or **Discard answer**
- **Undo AI change** appears in the toolbar after an insert/replace, until the next edit (older text also stays in note history)
- Consent dialog, quota card, failure messages and retry are the ones from 33a (same `AiAssistantEngine`)

Out of scope: flashcards/questions from a selection and tutor (33c); rendering the answer as Markdown; choosing a folder for "Save as new note" (new note goes to the default list).

## Rules (`NoteAiRules`, pure Kotlin)
- `target`: non-blank selection -> that text and its range, otherwise the whole note. Backwards or out-of-range selections are normalised.
- `apply` INSERT_BELOW: after the selection (or at the end of the note), separated by a blank line; an existing line break after the selection is kept.
- `apply` REPLACE: replaces exactly the captured text.
- **Stale protection:** the target is captured when the sheet opens. If the note no longer has that text at that place when the user applies the answer, nothing is overwritten; the answer is added at the end and a message says so.
- `newNoteTitle`: first meaningful line of the answer without markdown marks, at most 60 characters, else "AI note" / "এআই নোট".
- Notes longer than 8,000 characters need a selection; the sheet says so and nothing is sent.

## Failure and offline states
Offline, quota used up, temporary block, rate limit, blocked content, AI switched off, service unavailable, and consent required (dialog opens again) all show their own message in the sheet. The note and its autosave are never affected: the editor works offline exactly as before, and the typed text stays. "Save as new note" while offline shows a red message in the editor (see risks: the sheet has already closed).

## Changed files
- New: `ai/NoteAiRules.kt`, `ai/NoteAiViewModel.kt`, `screens/NoteAiSheet.kt`, `app/src/test/.../ai/NoteAiRulesTest.kt`
- Changed: `ai/AiAssistantEngine.kt` (`clearOutput`), `notes/NoteViewModels.kt` (`applyAiResult`, `undoAi`, `saveAsNewNote`, notice, cursor/revision), `screens/NoteScreens.kt` (selection-aware body field, AI button, undo, notices, sheet), `screens/AiAssistantScreen.kt` (shared pieces made `internal`), strings (English + Bangla), `AiAssistantEngineTest.kt` (helpers made `internal`)
- Fixed on the way: eight `ErrorView(...)` calls (notes, quiz manage, quiz list, flashcards, practice history) did not pass the required `retryLabel`; that was a compile error. They now pass `R.string.retry`.

## Acceptance criteria
- AI never changes the note without an explicit button press after the user saw (and could edit) the answer
- Selection and whole-note modes both work; typing while the AI runs cannot be overwritten
- Every AI failure leaves the note and the typed text intact and gives a readable message in English and Bangla
- Consent, quota and the "AI may be wrong" notice are visible in the sheet

## Tests (`NoteAiRulesTest.kt`, plain JUnit): 22 new
Target selection (6), apply/insert/replace/stale/title (11), sheet-facing engine behaviour (5: too long, empty, clear output, offline, second run).
**Not compiled or run** (no Android SDK here).

## Risks / not yet verified
- Never compiled. The editor now holds a `TextFieldValue` (for the selection) next to the view model's text; the two are synced in `NoteEditorScreen`. If the server ever returns a body that differs from what was typed, the field is not refreshed until the next AI apply or reload. Needs a device check: cursor position after load, after insert, after undo.
- `ModalBottomSheet` needs Material3 1.2+ (BOM 2024.09 in the project). Keyboard and TalkBack behaviour of the sheet are untested.
- "Save as new note" closes the sheet when pressed. If the save then fails (offline), the answer is gone from the sheet; the editor shows a red message. Keeping the sheet open until the save succeeds is a small follow-up.
- Closing the sheet with the back button keeps nothing: re-opening starts a new request (consent and quota are kept).
- Undo covers one AI change and is dropped by the next keystroke (note history still has older versions).
- The answer is plain text; Markdown marks the AI writes are stored as typed.

## Run it
Backend as in `STEP-32-ai-gateway.md`; `cd android && gradle :app:testDebugUnitTest`; open a note, select a paragraph, tap AI.
