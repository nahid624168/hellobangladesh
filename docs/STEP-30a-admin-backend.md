# STEP 30a — Admin backend core

## Scope
Foundation for platform administration (first half of the Step 30 plan):

- User search, view, suspend/unsuspend, role assignment
- Feature flags (read for all authenticated users; write for super_admin)
- App config key/value store (admin+)
- Audit log write + query (admin+)
- Abuse reports (any authenticated user can file; moderator+ can list/resolve)
- Role hierarchy enforcement (moderator < editor < admin < super_admin)

Content/question bulk management, contest/exam admin UIs, destructive-action confirmations and the full authorization-matrix HTTP tests remain for **30b**.

## API (all under `/api/v1`)

| Method | Path | Min role | Notes |
|--------|------|----------|-------|
| GET | `/feature-flags` | any signed-in | Public flag list for client gating |
| POST | `/abuse-reports` | any signed-in | File a report |
| GET | `/admin/users` | moderator | `q`, `status`, `role`, `limit`, `cursor` |
| GET | `/admin/users/{id}` | moderator | |
| PATCH | `/admin/users/{id}/status` | admin | body `{status, reason?}` — cannot self |
| PUT | `/admin/users/{id}/roles` | admin | body `{roles:[]}` — cannot self; cannot escalate above self |
| GET | `/admin/feature-flags` | admin | |
| PUT | `/admin/feature-flags/{key}` | super_admin | |
| GET | `/admin/config` | admin | |
| PUT | `/admin/config/{key}` | admin | body `{value, description?}` |
| GET | `/admin/audit-logs` | admin | filters: actorId, resourceType, action |
| GET | `/admin/abuse-reports` | moderator | `status`, `limit`, `cursor` |
| PATCH | `/admin/abuse-reports/{id}` | moderator | body `{status, resolutionNote?}` |

Errors follow project convention (`ApiException` → `{error:{code,message,fields}}`). Ownership / missing resources return 404; insufficient role returns 403.

## Database
`V16__admin.sql`:

- `audit_logs`
- `feature_flags` (seeded with guest_browsing, ai_assistants, contests_public, payments, registration_open)
- `app_config` (seeded support_email, max_quiz_questions, daily_goal_default)
- `abuse_reports`

Roles table already exists from V1; `user_roles` from V2. Next free migration number: **V17**.

## Changed / added files
- `backend/src/main/resources/db/migration/V16__admin.sql`
- `backend/src/main/kotlin/com/exambase/backend/admin/` (Models, Repository, JdbcAdminRepository, AdminService, AdminRoutes)
- `backend/src/main/kotlin/com/exambase/backend/Application.kt` (wire `admin`, SERVICE_VERSION → 0.10.0)
- `backend/src/test/kotlin/com/exambase/backend/admin/` (InMemoryAdminRepository, AdminServiceTest)

## Acceptance criteria
- Moderator can search users and resolve abuse reports; cannot change status or roles.
- Admin can suspend ordinary users and manage config/audit; cannot touch other admins.
- Super_admin can change admin status/roles and feature flags.
- No endpoint returns another user’s private notes/quiz content.
- All mutating admin actions write an audit row.
- Unit tests cover the authorization matrix for the cases above.

## Tests
`AdminServiceTest` (in-memory) covers role gates for search, status, roles, flags, reports. Full Ktor route + matrix tests planned for 30b. **Not compiled or run in this environment.**

## Risks / not yet verified
- JDBC repository not exercised against a real PostgreSQL instance.
- No HTTP-level tests yet.
- No Android / web admin UI (Step 31).
- Bulk import, question review workflow, contest/exam admin CRUD → 30b.
- Destructive-action confirmation tokens not implemented yet.

## Run it
```bash
# after docker compose up and migrations
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/admin/users
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/feature-flags
```
