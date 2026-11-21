# Step 21: Android notes workspace and editor

Builds on Step 20 notes backend.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- Network: `NoteDtos`, `NoteApi` (folders, notes, versions, sync client method)
- `NoteRepository` / list + editor ViewModels
- My Notes: search, folder chips, pinned/favorite filters, create folder, create note, delete
- Editor: title + body, markdown-style toolbar (bold/italic/strike/heading/list/checklist/quote/link), pin/favorite, word/character count, **autosave (~800ms debounce)** with visible status (Saving/Saved/Error/Conflict)
- Optimistic concurrency via `baseVersion` → conflict status
- Home entry; EN + BN strings

Out / lighter:
- Full WYSIWYG with selection-based formatting, undo/redo stack, tables
- Attachment picker UI, offline draft recovery (Step 22)
- Version history UI (API ready; Step 23 can surface restore)

## Acceptance criteria
1. Notes list never shows another user's content (server-enforced).
2. Autosave updates status; conflict shows explicit message.
3. Empty/loading/error/signed-out states on list.
4. New note opens editor; edits persist through update API.
5. Accessibility: save status exposed to TalkBack via contentDescription.

## Not verified
Nothing compiled or run.

## Run it
```bash
cd android && gradle :app:testDebugUnitTest
# Home -> My Notes -> New note -> type (watch autosave) -> back to list
```
