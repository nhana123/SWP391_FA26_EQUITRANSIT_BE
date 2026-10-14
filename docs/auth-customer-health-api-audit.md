# Auth, customer and health API contract audit

Scope: all current `/api/auth/**` (six POST operations), `/api/customers/**` (GET /me), and `/api/health` (GET). Eight existing DTOs, 24 declared fields, plus HealthResponse with two fixed properties.

| DTO / fields | Before | Refactor |
|---|---|---|
| EmailRequest.email, LoginRequest.email, RegisterRequest.email | Custom ASCII regex plus @Email; no example | Remove ASCII regex, preserve @Email/@NotBlank/max length; email format and customer@equitransit.com example |
| LoginRequest.password, RegisterRequest.password/confirmPassword, ResetPasswordRequest.password/confirmPassword | No examples | Matching ExamplePass!2026 examples, password format, writeOnly OpenAPI metadata; registration/reset confirmPassword also bounded to existing max72 |
| RegisterRequest.fullName | Missing example | Alex Nguyen |
| RegisterRequest.phone | Only length constraint, missing example | Strict anchored E164 with +84901234567 example; null permitted, supplied blank invalid |
| RegisterRequest.address | Missing example; optional blank permitted | Concrete address example; null permitted, supplied blank invalid |
| TokenRequest.token, ResetPasswordRequest.token | Missing examples | Clearly fake documentation tokens, instructed to use real email-link token; writeOnly schema |
| LoginResponse.accessToken | Missing example | Explicit illustrative JWT, never a real signed token or runtime fallback |
| LoginResponse.tokenType/role/expiresIn | Missing examples or enum/bounds | Bearer/CUSTOMER/3600; OpenAPI allowable role codes match actual five DB roles, positive TTL in seconds |
| CustomerResponse.customerId/userId | Missing examples | Positive12/101 |
| CustomerResponse.email/fullName/phone/address | Missing examples/formats | Explicit email, name, E164 phone, address; phone/address nullable |
| MessageResponse.message | Missing example | Concrete verification success message; generic recovery runtime response unchanged |
| HealthController return | Raw Map<String,String>, additionalProp1 | HealthResponse with status=UP/application=EquiTransit, additionalProperties=false, exact same runtime JSON |
| Three controllers | produces unspecified | application/json for every scoped endpoint; operation summaries/tags |

Examples are documentation only. Password schema writeOnly describes the request contract; it does not replace runtime authorization or secret handling. Responses remain safe DTOs without password/hash. Verification/reset tokens and accessToken examples are nonfunctional, not actual credentials.

Existing auth password rules, UTF-8 byte guard, token expiry/replay protection, public customer-only registration, generic account recovery responses and /customers/me identity/role checks remain unchanged. No DB migration/backfill or production data writes. Optional phone/address clients must send null rather than empty/free-text phone values. Email grammar now follows standard @Email instead of the removed ASCII-only filter.

Tests inspect real generated OpenAPI examples/email patterns/password formats/media type/health schema and exact public health body, validate input examples/invalid emails/optional phone, and run existing auth/security regressions. Final counts and CI evidence are in the PR. This change builds on PR24 and PR25, both verified merged into dev on 14/10/2026. Private AI instructions remain local.
