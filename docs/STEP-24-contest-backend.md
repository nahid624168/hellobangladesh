# Step 24: Contest backend

**Status: PARTIAL (source added; not compiled / not run).**

## Scope

In:
- **Schema `V11__contests.sql`**: contests, contest_questions, contest_participants, contest_answers, contest_abuse_events
- **Rules**: scheduling windows, capacity, max attempts, join/leave before start, server deadline = min(start+duration, ends_at)
- **Idempotent answers**: upsert per (participant, question); optional `clientKey`
- **Server scoring**: reuses `Grader`; written questions ungraded; negative marks applied
- **Leaderboard**: PostgreSQL source of truth; tie-break `earliest_submit` | `fastest_time` | `none`
- **API** (auth required):
  - `GET /contests`, `GET /contests/{id}`
  - `POST /contests` (seed/create with question ids — full admin authoring in Step 30)
  - `POST /contests/{id}/join|leave|start|answers|submit`
  - `GET /contests/{id}/leaderboard`
- **Anti-abuse**: join rate limit; `contest_abuse_events` for audit; disqualified status supported
- **Tests**: `ContestServiceTest` (join, capacity, leave-after-start, scoring, max attempts, leaderboard)

Out / later:
- Redis-backed leaderboard cache (interface ready via repo; single-node Postgres for now)
- WebSocket live delivery (Step 25)
- Android contest UI (Step 26)
- Official contest authoring in admin panel (Step 30–31)

## Acceptance criteria

1. Capacity blocks extra joins with `contest_full`.
2. Leave only while `joined` and before `starts_at`.
3. Submit scores server-side; clients cannot set score.
4. Leaderboard ranks by score then configured tie-break.
5. Answer upsert is idempotent for the same question (and clientKey when provided).

## Not verified

Nothing compiled or run.

## Run it

```bash
cd backend && ./gradlew test --tests com.exambase.backend.contests.ContestServiceTest
```
