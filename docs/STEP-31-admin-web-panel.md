# STEP 31 — Admin web panel

## Scope
A responsive, dependency-free web panel for staff, served by the backend at `/admin-panel/` (same origin as the API, so no CORS change).
Built on the Step 30a/30b admin API. The panel hides sections your role cannot use, but that is only convenience: every call is checked by the backend.

Sections: Dashboard, Users, Content (browse, publish/unpublish, create subject), Questions (filter, move through draft/review/published/archived),
Imports (JSON bulk import), Contests and Exams (list, change status), Reports (review/resolve/dismiss), Feature flags, App config, Audit logs.

## API
No new endpoints. Uses `/auth/login`, `/auth/refresh`, `/auth/logout`, `/users/me` and `/admin/*` from Steps 6 and 30.
Staff check: after login the panel calls `GET /admin/users/{ownId}` (moderator or higher). A 403 means no panel access and the user is signed out.

## Database
None. Next free migration number stays **V18**.

## Changed files
- `backend/src/main/resources/admin-panel/index.html`, `admin.css`, `admin.js` (new)
- `backend/src/main/kotlin/com/exambase/backend/Application.kt` (`staticResources("/admin-panel", "admin-panel")`, `SERVICE_VERSION` 0.12.0)
- `backend/src/test/kotlin/com/exambase/backend/AdminPanelStaticTest.kt` (new)
- `docs/PROGRESS.md`, `README.md`

## Security choices
- Strict Content-Security-Policy in `index.html` (scripts, styles and requests only from the same origin, no framing).
- The DOM is built with text nodes only; a test fails if `innerHTML` or `eval(` appears in `admin.js`.
- Tokens are kept in `sessionStorage` (gone when the tab closes), not `localStorage`. Passwords are never stored.
- One automatic token refresh on 401, then sign-out.
- Role hiding does not replace backend checks.

## Acceptance criteria
- Staff (moderator+) can sign in; ordinary users are refused with a clear message.
- Each section shows loading, error with retry, and empty states; works on a phone-width screen; keyboard and screen-reader friendly (labels, focus rings, 44px targets).
- Editors see content/questions/imports/contests/exams; only admins can change contest/exam status, user status/roles and see config/audit; only super_admin changes flags.

## Tests
`AdminPanelStaticTest`: panel and CSP served, no `innerHTML`/`eval`, API health unaffected. **Not compiled or run.**
No browser/UI test run exists yet.

## Gaps (why this step is PARTIAL)
- **Admin MFA**: not built. It needs backend support (TOTP enrol/verify, recovery codes); not in the plan as its own step. Recommend adding it before Step 37 (security review).
- **Banners, notifications, AI usage/cost**: no admin read endpoint for AI usage yet (queue row 32b) and no notifications/banners backend (Step 34). The panel shows a notice instead.
- No user totals or analytics endpoint for the dashboard; it shows counts from existing lists only.
- Content: only subjects can be created in the UI; categories, subcategories, lessons and single-question create/edit are API-only for now. Edits use browser prompts, not forms.
- Destructive-action tokens (`/admin/destructive-tokens`) are not used by the UI yet.
- Not opened in a real browser; no automated UI tests.

## Run it
```bash
docker compose up --build
# open http://localhost:8080/admin-panel/ and sign in with a staff account
```
Give an account a role first (SQL on `user_roles`, or an existing admin via `PUT /api/v1/admin/users/{id}/roles`).
