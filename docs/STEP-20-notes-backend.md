# Step 20: Notes backend

Private-by-default notes with nested folders, tags, pin/favorite, optional links, version history, attachments metadata, and version-based sync with tombstones.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- Schema `V9__notes.sql`: `note_folders`, `notes`, `note_versions`, `note_attachments`
- Folders: create/list/update/soft-delete (nested via `parentId`)
- Notes: create/list (folder/q/tag/pinned/favorite)/get/update/soft-delete
- Optimistic concurrency via `baseVersion` → `409 version_conflict`
- Version list + restore
- Attachment URL metadata add/remove
- Sync: `GET /notes/sync?since=&limit=` returns notes + folders including tombstones (`deleted=true`)
- Privacy: other user's note/folder always 404 / empty list
- Unit tests: privacy, version conflict, restore, tombstones
- Service version `0.9.0`

Out / lighter:
- Binary upload pipeline (URLs only)
- Rich templates library
- Real-time collaborative editing

## API
| Method | Path |
|---|---|
| POST/GET | `/note-folders` |
| PATCH/DELETE | `/note-folders/{id}` |
| POST/GET | `/notes` |
| GET | `/notes/sync` |
| GET/PATCH/DELETE | `/notes/{id}` |
| GET | `/notes/{id}/versions` |
| POST | `/notes/{id}/versions/{version}/restore` |
| POST/DELETE | `/notes/{id}/attachments[/{attachmentId}]` |

## Acceptance criteria
1. Notes never leak across users.
2. Soft delete produces tombstones visible to sync.
3. Stale `baseVersion` yields 409, not silent overwrite.
4. Version restore creates a new version with prior content.
5. Folders cannot be moved under another user's tree.

## Not verified
Nothing compiled or run.

## Run it
```bash
cd backend && gradle test --tests 'com.exambase.backend.notes.*'
```
