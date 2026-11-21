# Step 25: Real-time layer (WebSocket)

**Status: PARTIAL (source added; not compiled / not run).**

## Scope

In:
- **Ktor WebSockets** dependency + server plugin (15s ping / 30s timeout)
- **Protocol** (`RealtimeProtocol.kt`): hello/welcome, ping/pong, event, ack, error
- **`ContestRealtimeHub`**: session registry, per-contest fan-out, resume token (`contestId:userId:lastSeq`), in-memory ring buffer replay
- **`EventBus`**: `InMemoryEventBus` (single node). Interface ready for Redis pub/sub swap — no exactly-once claims
- **Transactional outbox** (`V12__realtime_outbox.sql`, `JdbcOutbox` / `InMemoryOutbox`): enqueue then publish; `flushOutbox()` for recovery workers; at-least-once + idempotent consumers (eventId / seq)
- **Endpoint**: `GET /api/v1/ws/contests/{contestId}?token=<accessJWT>&resume=<token>`
- **JWT** query/header verification via `JwtService.verifyAccess`
- **Contest hooks**: join / start / submit emit `participant.update` and `leaderboard.tick`
- **Tests**: welcome, ping/pong, publish delivery, resume replay, ack cursor, mass-join (50), outbox flush

Out / later:
- Redis pub/sub bus implementation (multi-instance)
- Background outbox publisher loop
- Live question streaming frames beyond contest lifecycle events
- Android client (Step 26)

## Acceptance criteria

1. Unauthenticated WS upgrade is rejected.
2. Ping receives pong; ack advances resume cursor.
3. Publish reaches all local subscribers for a contest.
4. Reconnect with resume token replays only events with `seq > lastSeq`.
5. Outbox drain re-publishes pending rows and marks them published.

## Not verified

Nothing compiled or run.

## Run it

```bash
cd backend && ./gradlew test --tests com.exambase.backend.realtime.ContestRealtimeHubTest
# WS: wscat -c "ws://localhost:8080/api/v1/ws/contests/{id}?token=$ACCESS"
```
