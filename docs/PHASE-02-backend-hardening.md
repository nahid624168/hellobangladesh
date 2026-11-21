# Phase 02 — Backend hardening

## Implemented in this increment

- Added configurable SMTP email delivery using Angus Jakarta Mail.
- Added environment configuration: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS`.
- Production startup continues to reject the development-only `LogEmailSender`; the app now selects SMTP when host and sender are configured.
- Added SMTP connection/read/write timeouts and server identity verification. STARTTLS is enabled by default.
- Changed refresh-token rotation to a database compare-and-swap update. A token can only rotate if its stored hash is still current, the session is active, and it has not expired.
- If a concurrent refresh loses the compare-and-swap because the same token was already rotated, the session family is revoked as a replay response.
- Updated in-memory test repository and added a compare-and-swap regression test.

## Deployment configuration

Set these secrets in the backend deployment environment (do not commit credentials):

```env
APP_ENV=production
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=provider-issued-username
SMTP_PASSWORD=provider-issued-password
SMTP_FROM=verified-sender@example.com
SMTP_STARTTLS=true
```

If your provider uses port 465 with implicit TLS, this SMTP adapter is not configured for implicit TLS yet; use a provider's STARTTLS endpoint or implement a dedicated SMTPS mode before deployment.

## Not completed / external dependencies

- No SMTP credentials were supplied, so actual email delivery has not been verified.
- No Redis/shared rate limiter was introduced in this increment.
- AI quota accounting is not yet atomic across multiple backend instances.
- Gemini networking remains synchronous and needs a coroutine-based provider adapter.
- Realtime fan-out remains in-memory and is single-instance only.
- Avatar/object storage still needs a real provider and upload lifecycle.
- Gradle tests/build were not run in this environment because the project has no Gradle wrapper and no system Gradle installation.
