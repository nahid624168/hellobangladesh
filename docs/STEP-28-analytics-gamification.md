# Step 28: Results, analytics and gamification

**Status: PARTIAL (source added; not compiled / not run).**

## Metric definitions (written)

- **accuracy** = correct / answered among auto-graded items
- **streak** = consecutive UTC days with ≥1 answered item
- **points** = server-granted only via append-only `point_ledger` (idempotent by `user+reason+ref`)
- **daily goal** = answers today / `daily_goal_target`

Clients **cannot** grant points — there is no `POST /analytics/points`.

## Backend

- Schema `V14__analytics_gamification.sql`: `user_stats`, daily activity, point ledger, badges, subject performance
- `AnalyticsService.recordActivity(...)` for internal callers (practice/quiz/contest/exam)
- HTTP (JWT):
  - `GET /analytics/overview`
  - `GET /analytics/trends?days=`
  - `GET /analytics/subjects`
  - `GET /analytics/achievements`
  - `PATCH /analytics/daily-goal`
  - `GET|PATCH /analytics/leaderboard-visibility`
- Unit tests: points idempotency, accuracy, streak, daily goal, trends padding

## Android

- DTOs, `AnalyticsApi`, repository, overview + achievements screens
- Home → Analytics → Achievements
- EN + BN strings

## Still open

- Wire `recordActivity` into every practice/quiz/contest/exam submit path
- Downloadable CSV/PDF reports
- Rich charts / weak-topic ranking UI
- Persist leaderboard visibility preference column
- **Not compiled / not run**

## Run it

```bash
cd backend && ./gradlew test --tests com.exambase.backend.analytics.AnalyticsServiceTest
```
