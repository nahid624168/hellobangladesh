# ExamBase AI Blueprint (condensed)

## 1. Assumptions
- Android first; web admin panel later. English + Bangla UI.
- Monetization is optional and configurable. Guest browsing is off by default.
- Launch target: a reliable, measured system. No concurrency guarantee until load-tested.

## 2. Architecture
Android app -> HTTPS (Cloudflare/WAF) -> Ktor modular monolith -> PostgreSQL + Redis + S3-compatible storage.
WebSockets for live contests. Background jobs for sync, notifications, AI tasks. AI provider is called
server-side only through a provider-neutral gateway.

## 3. Technology choices
| Area | Choice | Alternative |
|---|---|---|
| UI | Jetpack Compose | XML views |
| Architecture | Clean Architecture + MVVM | MVI |
| Local DB / sync | Room + WorkManager | SQLDelight |
| Backend | Ktor modular monolith | Spring Boot |
| DB / cache | PostgreSQL, Redis | MySQL, Memcached |
| Deploy | Docker first, Kubernetes later | Managed container service |

## 4. Modules
Android: app, core/(designsystem, ui, network, database, datastore, model, common),
feature/(auth, home, subjects, lessons, questions, quizzes, quizsets, flashcards, flashcardsets,
notes, ai, contests, exams, analytics, profile, settings).
Backend: app, shared, modules/(identity, users, academic, questions, quizzes, flashcards, contests,
notes, ai, notifications, payments, analytics, admin), infrastructure/(database, cache, messaging, storage).
Steps 1-5 creates only `app` and `core:designsystem` on Android and a single backend module; others are added per milestone.

## 5. Screen map (grouped)
Entry: Splash, Onboarding, Login, Register, Forgot password.
Learn: Home, Search, Subject/Category/Subcategory/Lesson list+detail, Question detail/practice.
Quizzes: Quiz list/detail/player/result/history, My Quiz Sets, Create/edit Quiz Set.
Flashcards: Library, My Flashcard Sets, Create/edit, Study session, Progress.
Notes: My Notes, Folders, Editor, Detail, AI Notes assistant.
AI: AI Learning assistant.
Contests: List, Detail, Lobby, Player, Leaderboard, Results.
Exams: List/detail/player/result.
You: Analytics, Achievements, Notifications, Profile, Settings, Subscription, Help.

## 6. Data model plan
users, roles, user_roles, sessions -> subjects -> categories -> subcategories -> lessons ->
questions (+ options, versions) ; quiz_sets and flashcard_sets are SEPARATE tables owned by users
(with items tables), notes + folders, contests (+ participants, answers, rankings), attempts,
progress, bookmarks, notifications, subscriptions, ai_requests.

## 7. API plan
REST under `/api/v1`: auth, users, academic, questions, quizzes, quiz-sets, flashcards, flashcard-sets,
notes, contests (+ WebSocket), analytics, notifications, admin, ai. Standard error envelope
`{ "error": { "code", "message" } }`, cursor pagination, OpenAPI docs.

## 8. Security and privacy
Server-enforced RBAC + ownership checks. Short-lived access tokens + rotating refresh tokens.
Server-side scoring; correct answers withheld until submission. Secrets only in env/secret manager.
Rate limits on auth and contests. Account deletion and data export.

## 9. Deployment and scaling
Stage 1: Docker Compose on one VM. Stage 2: managed Postgres, Redis, object storage, CDN.
Stage 3: horizontal backend replicas, read replicas, queue. Kubernetes only if justified by load tests.

## 10. Steps
The build follows the 40-step plan in `ExamBase_AI_40_Step_Build_Prompt.txt`; current status is in `docs/PROGRESS.md`.
Database migration numbers (V1, V2, ...) are schema versions, not step numbers.

## 11. Decisions needing owner approval
1. Guest browsing: on or off?
2. Login methods for launch: email only, or also phone OTP / Google?
3. Monetization at launch?
4. AI provider (or keep provider-neutral until later)?
5. Admin panel stack (React is the default proposal).
6. Hosting provider and region.
