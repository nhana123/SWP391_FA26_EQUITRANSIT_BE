# Account email and OTP security

Local and default configuration use file delivery to `.local/outbox`.
No real SMTP credentials are committed. Enable SMTP explicitly using
EQ_MAIL_MODE=smtp, EQ_MAIL_HOST, EQ_MAIL_PORT, EQ_MAIL_USERNAME,
EQ_MAIL_PASSWORD and EQ_MAIL_FROM. Legacy EQ_SMTP_* variables remain
supported as fallbacks. No SMTP connection is needed in file mode.

## Email link versus OTP
Email links use independent 256-bit URL-safe random tokens. Their existing
24-hour verification / 30-minute reset lifetimes remain unchanged.
Six-digit OTPs are stored as HMAC-SHA256 fingerprints scoped to normalized
email and purpose, never as plain SHA-256 of a six-digit code. The secret is
EQ_OTP_SECRET or, if omitted, app.jwt.secret. Keep it outside Git.

Both verification and reset APIs accept either:
- Existing link: token=<43-character bearer token>, email optional.
- OTP: token=<six digits>, email=<owning account email> is required.
  Existing password/confirmPassword fields remain required for password reset.

OTP-only submissions without email, old globally hashed six-digit tokens,
wrong-account codes and cross-purpose codes are rejected. Frontend OTP forms
must send the account email; long email links remain compatible.

## Abuse protection and atomicity
OTP validity is at most 10 minutes. Five wrong attempts exhaust the budget.
The count is stored in AuthenticationToken, serialized by owner/token locks,
and committed even when the API rejects a wrong code. Credential changes and
successful consumption remain in one transaction. The rejection exception is
used only before any credential writes.

A resend has a 60-second cooldown, retains failed attempts and the original
OTP deadline during the active window, and cannot reset the attempt budget.
Unknown accounts retain generic responses. Existing IP request limits remain.
OTP lockout does not invalidate the independent high-entropy email link.

## Deployment
Apply database/migrations/028_auth_otp_security.sql before the updated binary.
The script is idempotent and refuses databases other than Demo/Test catalogs;
production requires a separately authorized deployment migration.
Existing token rows receive FailedAttempts=0 and nullable OTP fields. Strong
existing links remain usable; legacy six-digit bearer codes require resend.

The previously committed Google App Password must be revoked in the Google
account and replaced if SMTP is needed. Removing it from the current YAML does
not revoke it or remove it from Git history. Do not print or commit outbox mail,
local properties, API keys or SMTP credentials. This change does not rewrite
shared Git history or attempt to revoke Google credentials automatically.

## Local Gmail SMTP launcher
Run `./tools/run-demo-smtp.ps1 -Configure` from the Backend directory to enter a
new App Password securely and restart only the managed Demo process on 8082.
The prior sender may be prefilled from `.local/demo/smtp-username.txt`.
Credentials are stored in `.local/demo/smtp-credentials.xml`, encrypted by
Windows for the current user. Neither file is tracked. Later starts can omit
`-Configure` to reuse the encrypted credential. SMTP delivery is not enabled
until credentials are supplied and the helper starts the new process.
