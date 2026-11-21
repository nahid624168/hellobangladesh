# Step 11b: Signed-URL attachments, related content, Room cache, offline lesson download

Builds on Steps 9–12 (academic hierarchy) and the offline foundation from Step 22.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope

In:
- **Signed attachment URLs**: `SignedUrlService` (HMAC-SHA256, configurable TTL). Storage keys become `{PUBLIC_BASE_URL}/media/{key}?exp=&sig=`. Absolute `http(s)` URLs are left alone.
- **Related content on `LessonDetail`**: `relatedQuestions` (published, no answers), `relatedQuizSets` / `relatedFlashcardSets` (owner’s sets linked to the lesson), `relatedNotes` (owner’s notes for the lesson).
- **Android DTOs** updated to match (unknown fields still ignored safely).
- **Room cache (v2)**: `offline_lessons`, `offline_subjects` entities + DAO methods. Destructive migration from v1 notes-only DB.
- **Config**: `SIGNED_URL_SECRET` (falls back to `JWT_SECRET`), `SIGNED_URL_TTL_MINUTES`, `PUBLIC_BASE_URL`.

Out / later:
- Full Paging 3 `RemoteMediator` for subjects/lessons list (entities + DAO are ready; mediator + UI wiring still TODO).
- Download of attachment binary files (metadata only is cached with the lesson).
- Media serving endpoint that validates the signed query string (client can open the signed URL when a CDN/proxy is in front).
- Official/admin quiz & flashcard sets on the lesson (only owner private sets for now).

## Acceptance criteria

1. `GET /lessons/{id}` returns related arrays; clients that ignore unknown fields keep working.
2. Attachment `url` is either the original public URL or a time-limited signed URL with `urlExpiresAt`.
3. Related questions never include correct answers or option correctness.
4. Related notes/quiz/flashcard sets are scoped to the requesting user.
5. Room can store and read a downloaded lesson (unit-level; not run here).

## Not verified

Nothing compiled or run (no Gradle / Android SDK / PostgreSQL in this environment).

## Run it

```bash
# Backend
cd backend && ./gradlew test
# Exercise: open a seeded lesson; inspect attachments[].url and related* fields

# Android
cd android && ./gradlew :app:testDebugUnitTest
# Lesson screen → Download (once UI is wired) stores OfflineLessonEntity
```

## Risks

- No media-serving route yet; signed URLs assume an external or future `/media/` handler.
- Quiz/flashcard “related” only shows the user’s own sets (official sets arrive with admin content).
- Room version bump uses destructive migration; acceptable before production data.
