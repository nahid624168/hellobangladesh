# Steps 6-8: Authentication, profiles, security foundation

## Assumptions used (not yet approved by owner)
Guest browsing OFF, email + password only, monetization later, email provider not chosen.

## Backend
Migration `V2__users_auth.sql`: users, user_roles, sessions, email_codes.
Base path `/api/v1`. Errors use `{ "error": { "code", "message", "fields"? } }`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /auth/register | no | Create account, returns tokens, sends 6-digit verify code |
| POST | /auth/login | no | Returns access (15 min) + refresh (30 days) tokens |
| POST | /auth/refresh | no | Rotates refresh token; replaying an old one revokes all sessions |
| POST | /auth/logout | yes | Revokes current session (access token stops working immediately) |
| POST | /auth/verify-email | no | `{email, code}` |
| POST | /auth/resend-verification | yes | New code |
| POST | /auth/password-reset/request | no | Same response whether or not the email exists |
| POST | /auth/password-reset/confirm | no | `{email, code, newPassword}`; signs out all devices |
| GET/PATCH | /users/me | yes | Profile (displayName, preferredLanguage en/bn, theme) |
| GET | /users/me/sessions | yes | Active devices |
| DELETE | /users/me/sessions/{id} | yes | Sign out one device |
| POST | /users/me/delete | yes | `{password}`; anonymises user, revokes sessions |

Security notes: bcrypt (cost 12), refresh tokens stored only as SHA-256 hashes, codes hashed + 5 attempts + 15 min,
generic login errors, in-memory rate limiter (20 req/min/IP/path; replace with Redis before multiple replicas),
JWT secret from `JWT_SECRET` (server refuses to start without 32+ chars).

## Android
New modules `core:network` (Ktor client + DTOs), `core:datastore` (Keystore-encrypted token store).
Screens: login, register, verify email, reset password (2 steps), home (loading/error/retry), profile
(edit name, theme, sign out, delete account). Token refresh is serialized with a mutex.

## Known gaps / next
- Email sending: `LogEmailSender` prints codes to the server log (dev only, blocked in production).
- Preferred language is stored on the server but the UI language still follows the device.
- No OpenAPI file yet; table above is the contract. Session list UI not built (API exists).
- Nothing compiled or run here. Run both test suites first.
