# F01 — Account flow and shared authorization

Implementation date: 2026-10-13. Task branch: `feature/1-auth-review`.
The existing verified registration/recovery implementation is retained.
Code and validation are ready for cross-review; Issue #1 stays open until
the PR is reviewed and merged into `dev`.

## Public API contract

The request/response shapes and URLs in README are unchanged. Login issues
a 15-minute bearer access token. Registration creates an INACTIVE customer;
verification changes eligible accounts to ACTIVE. Reset changes the password
and invalidates old access credentials. One-time links are stored as hashes.
Auth errors never echo submitted passwords/tokens.

`GET /api/customers/me` now calls a method-secured service without an ID
parameter. Identity comes from JWT subject plus current database state.
Anonymous/stale credentials return 401; disallowed roles return 403. A
missing domain profile fails closed rather than inventing a CustomerID or
using UserID as StaffID. Profile DTO never includes PasswordHash.

Swagger UI is at `/swagger-ui.html`, schema at `/v3/api-docs`. Auth operations
are public; profile documentation declares `bearerAuth`. Swagger does not
relax authentication on business APIs.

## Shared service contracts for BE1 and BE2

- `CurrentActorService.current()` returns immutable `CurrentActor` with
  UserID, current role and either CustomerID or StaffID. Requires active
  bearer authentication, unchanged password fingerprint and matching profile.
- `DomainAccessPolicy.isCustomer()` is used by the actual profile service's
  `@PreAuthorize`. `@EnableMethodSecurity` enables the enforcement.
- `requireCustomerOwner(ownerCustomerId)` compares a persisted resource's
  CustomerID with the authenticated CustomerID, and rejects staff/null owners.
- `requireManager()` requires current Manager identity and staff profile.
- `requireAssignedStaff(roleCode, assignedStaffId)` requires both role and
  exact StaffID. The calling flow must load its current assignment from the
  database; neither argument may come from a client's permission claim.
  This primitive does not claim to implement F04 order or F10 leg assignment
  lookup. Those flows add their producer logic and integration tests later.

No staff CRUD/provisioning endpoint is introduced. StaffProfile is mapped
read-only using its existing SQL names, types and unique UserID. Its repository
only exposes an identity lookup; F15 owns future administration.

## Database and migration

No production schema change or migration is needed for F01: StaffProfile
already exists on DESKTOP-9VRARFD / EquiTransit. Its columns were checked by a
read-only metadata query. Hibernate remains `validate` for the real database.
Existing `001_authentication_token.sql` still applies to a fresh installation.

`tools/create-auth-test-db.sql` now also prepares StaffProfile in the isolated
EquiTransit_AuthTest database. It does not change the production database.
DomainSecurityTest fixtures run with transactional rollback. AuthFlowTest's
existing auth fixture cleanup now requires an exact test database identity
and skips cleanup if the guard failed. Do not reuse that cleanup for later
modules or point tests at EquiTransit.

## Validation evidence

Maven Wrapper `verify` passes: 25 tests, no failures/errors, zero Checkstyle
violations. Tested with H2 and SQL Server 2019 EquiTransit_AuthTest, with
Hibernate `validate` on SQL Server. Commands are documented in README.

| Suite | Cases | Evidence |
|---|---:|---|
| AuthFlowTest | 16 | Atomic registration, role injection, duplicate email, expiration/replay/superseded links, inactive/locked account, ownership, indistinguishable login failures, expired JWT, password reset, two simultaneous verification/reset requests |
| DomainSecurityTest | 6 | Real service proxy denies anonymous/Manager callers, persisted customer ownership, missing staff profile, distinct StaffID/UserID, role plus assignment checks, changed credentials/disabled account |
| AuthRateLimitTest | 1 | 429 at configured limit, independent addresses and expiry of rate window without sleeping |
| OpenApiTest | 2 | Public documentation/UI, bearer scheme, public login and protected profile |

The two concurrency cases submit independent HTTP requests from two threads
and require exactly one 200 and one 400 response, plus a consumed token and
the expected account/password state. SQL Server integration confirms the
existing owner-then-token locking order, not just an in-memory mock.

Java changes were manually reviewed for four-space indentation, 80 columns,
type/member naming, Javadoc, declaration/field order, braces and one statement
per line. Checkstyle covers main/test source. Secrets/log/outbox/build/IDE
files remain outside Git. No raw token or password was added to source.

## Remaining delivery step and limits

Another team member must review the PR and merge it into dev before Issue #1
is closed. The implementation is ready; this document is not an approval.
Staff provisioning, assignment lookups, refresh/logout sessions, durable
email retries and multi-node rate limiting remain outside F01's current API.
Local email uses files; real SMTP requires deployment configuration. Future
F15 account disabling must revoke pending verification/reset links as part
of its transaction, especially for accounts not yet verified.
