# Guest mode (20-minute preview)

- Login screen has "Continue as guest (20 minutes)".
- `POST /api/v1/auth/guest` creates a temporary account (`guest-<uuid>@guest.exambase.local`, random password, email pre-verified)
  and a session that expires after 20 minutes (`AuthService.guestTtl`). Rate limited like other auth endpoints.
- Refresh never extends a guest session; the server rejects every request after expiry (`sessions.expires_at`).
- Real users cannot register with the reserved guest email domain.
- Android: countdown banner (`GuestSessionLayout`), "Sign up" shortcut, expiry dialog, deadline survives app restart.
- Not done yet: periodic cleanup of expired guest users/data, blocking AI for guests, role `guest` (guests currently have role `user`).
- Not compiled here: verify with CI.
