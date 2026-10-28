> UPDATE 2026-10-27: Account EMAIL VERIFICATION is now link-only.
> New verification emails contain only VERIFY NOW (no OTP). FE must not show
> an OTP input or code fallback. Use the captured strong token only after a
> user clicks Verify now; keep resend-link with cooldown and email prefill.
> Verification link lifetime and queued mail expiry are 24 hours. Resend
> invalidates the previous link. Password-reset OTP behavior is unchanged.
> This update supersedes verification-OTP requirements below.

# Backend authentication flow — operations and FE contract

## Completed business flow

Register -> INACTIVE -> verify email (OTP or strong link) -> ACTIVE -> login.
Forgot password -> generic acknowledgement -> OTP/link -> reset -> revoke
old sessions -> password-change notification -> login again.

The FE implementation request is `../FE_AUTH_FLOW_IMPLEMENTATION_REQUEST.md`.
Existing endpoints remain compatible; six-digit OTP requests require email.
Login with correct credentials for an unverified account returns
`EMAIL_VERIFICATION_REQUIRED`. Invalid/expired challenges share
`AUTH_CHALLENGE_INVALID`. Request budgets return 429 and `Retry-After`.

## Durable email delivery

Migration 029 adds AccountMailOutbox and AuthRequestBudget. Apply only to
Demo/Test after a verified backup. Account changes and encrypted mail enqueue
commit in the same transaction. A worker sends outside that transaction,
leases each message for two minutes and fences stale acknowledgements.
Transient failures retry at 30, 60, 120 and 240 seconds (five attempts total).
Expired or superseded challenges are discarded. Delivered/terminal payloads
are erased; terminal metadata is retained for 30 days.

The payload uses AES-GCM with the mail UUID as authenticated context.
Set a stable, separately managed `EQ_MAIL_OUTBOX_SECRET` (at least 32 bytes)
in deployment. Its fallback is the JWT secret for local compatibility.
Changing the encryption key without draining queued messages makes those
messages unreadable; they fail safely and users must request another email.
Do not commit secrets, outbox files or credentials. Restrict backup access.

Delivery is at least once: a crash after SMTP acceptance but before the DB
acknowledgement can resend a message. Retries keep a stable Message-ID.
`SMTP_ACCEPTED` means the SMTP relay accepted the mail, not inbox delivery.
`FILE_WRITTEN` is local offline delivery and never means an email was sent.

Manager-only `GET /api/manager/account-mail/status` exposes aggregate
`states` and safe `errors` counts, with no addresses, OTPs or message bodies.

## Abuse controls

Existing IP throttling remains. Account/action budgets are persisted in DB,
commit even on rejected authentication, and survive application restarts.
Default: 10 requests per 15-minute window for LOGIN, REGISTER and RECOVERY;
forgot and resend share RECOVERY. Keys are HMAC fingerprints, not addresses.
`EQ_AUTH_ACCOUNT_REQUEST_LIMIT` changes this limit. OTP guesses are separately
limited to five; resend cannot reset the attempt count or expiry window.
OTPs expire within ten minutes; resend cooldown is 60 seconds.

## Enable real email

Local defaults to file mode (`.local/outbox`). For SMTP set environment:

- EQ_MAIL_MODE=smtp
- EQ_MAIL_HOST=smtp.gmail.com
- EQ_MAIL_PORT=587
- EQ_MAIL_USERNAME=<sender account>
- EQ_MAIL_PASSWORD=<new app password or provider credential>
- EQ_MAIL_FROM=<authorized sender>

`tools/run-demo-smtp.ps1` securely prompts for credentials and starts the
managed Demo runtime. It does not contain a real password. Revoke the old
previously exposed app password; do not reuse it. SMTP credentials are not
available to the agent, so external inbox delivery requires this setup and
a final test with an address you own.

For deployment use HTTPS, secure HttpOnly refresh cookies, the actual FE
origin allowlist, correct public verification/reset URLs, trusted proxy/IP
configuration, and sender-domain SPF/DKIM/DMARC configured with the provider.
Monitor failures and relay bounce/suppression reports. Local SMTP sink tests
verify transport but cannot prove external inbox placement.

## Verification (2026-10-27)

- `mvnw -B test-compile checkstyle:check test` with native SQL driver and
  opt-in Demo suites: BUILD SUCCESS, 494 discovered, 325 executed,
  169 skipped by existing suite conditions, zero failures/errors.
- Includes 39 focused auth/mail tests: auth flows, IP and durable account
  limits, transactional encrypted outbox, concurrency/leases/retries,
  configuration and real SMTP transport to a local test sink.
- Migration029 applied twice to Demo after COPY_ONLY/CHECKSUM backup and
  RESTORE VERIFYONLY. Backup receipt: ignored `.local/auth-outbox-migration.json`.
- Isolated JAR packaging: BUILD SUCCESS. Managed Demo restarted on 8082;
  OpenAPI 200, monitoring JSON contract present, anonymous monitoring 401.
- No real EquiTransit DDL, no FE edits, no external inbox delivery claimed.
