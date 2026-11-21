# Step 26: Android contest experience

**Status: PARTIAL (source added; not compiled / not run).**

## Scope

In:
- Network: `ContestDtos`, `ContestApi` + `HttpExamBaseApi` methods
- `ContestRepository` / `ContestSource`
- ViewModels: list, detail/lobby, live player (deadline ticker, answer save, submit), leaderboard (15s poll)
- Screens: list, detail, player, leaderboard — each shows **online-only** messaging
- Navigation routes + Home entry
- EN + BN strings

Out / later:
- WebSocket client (resume/reconnect) — leaderboard uses poll for now
- History of past contests screen
- Instrumented UI tests / reconnect tests
- Image-based contest questions rendering polish

## Acceptance criteria

1. Learner can open Contests from Home, join, start, answer, submit.
2. Deadline auto-submits when remaining time hits 0.
3. Leaderboard shows ranks and “you” row when available.
4. Every contest screen states contests need internet.

## Not verified

Nothing compiled or run.

## Run it

```bash
cd android && ./gradlew :app:assembleDebug
# Home → Contests → join → start → submit → leaderboard
```
