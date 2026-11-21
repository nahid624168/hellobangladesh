# Step 8b: Profile photo, personal data export, active sessions UI

**Status: PARTIAL (source added; not compiled / not run).**

## Scope

In:
- **Avatar**: migration `V10__profile_avatar.sql` (`users.avatar_key`); `POST /users/me/avatar` (base64 JPEG/PNG/WebP ≤ 1.5 MB); `DELETE /users/me/avatar`; `UserDto.avatarUrl` via `SignedUrlService`
- **Export**: `GET /users/me/export` → profile, sessions, counts (notes, quiz sets, flashcard sets, bookmarks, practice attempts)
- **Sessions UI**: `SessionsScreen` + `SessionsViewModel`; list / revoke other devices; EN + BN strings
- Profile links: Manage sessions, Download my data, Remove photo

Out / later:
- Real object-storage write of avatar bytes (key only is stored; media pipeline later)
- Image picker / camera UI for upload (API + clear-avatar only on Android for now)
- Full export of note bodies / attempt payloads as a downloadable file share intent

## Acceptance criteria

1. Sessions list marks the current device; revoke removes another session only.
2. Export returns JSON with user + session list + aggregate counts for the caller only.
3. Avatar upload rejects non-image types and oversized payloads.
4. Soft-delete clears `avatar_key`.

## Not verified

Nothing compiled or run.

## Run it

```bash
cd backend && ./gradlew test
# POST /users/me/avatar with base64; GET /users/me/export; GET /users/me/sessions

cd android && ./gradlew :app:testDebugUnitTest
# Profile → Manage sessions / Download my data
```
