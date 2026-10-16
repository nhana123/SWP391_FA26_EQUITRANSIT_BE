# EquiTransit backend

Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.9. Kiến trúc monolith phân lớp: Controller → Service → Repository → SQL Server. Entity chỉ dùng cho persistence; request/response dùng DTO.

## Database và nguồn nghiệp vụ

- Database thật: `EquiTransit`, server `DESKTOP-9VRARFD`, TCP 1433; dùng Windows Authentication ở profile `local`.
- Mapping lấy từ metadata SQL Server live: `Role`, `User`, `Customer` giữ nguyên tên, độ dài, Unicode và nullable.
- Đọc plugin `SWP-RE/manifest.json`, `code.js` và catalogue `english-screens.js`. A01 là sign-in theo vai trò; A02 là đăng ký khách cá nhân; A03 yêu cầu email verification/recovery; C17 là hồ sơ cá nhân. Catalogue không phải một kết nối trực tiếp đến Figma đang mở.
- Public registration không có trường role. Staff accounts không được tạo từ API đăng ký khách.

## Chạy trên máy hiện tại

1. IntelliJ: **File → Open → pom.xml → Open as Project**.
2. Chọn Project SDK **21** và Maven **Use Maven Wrapper**.
3. Reload Maven. Chọn run configuration **EquiTransit Local** rồi Run. Nếu IntelliJ hỏi module, chọn module Maven `backend`.
4. Hoặc chạy PowerShell trong BE:

```powershell
.\run-local.ps1
```

API ở `http://127.0.0.1:8080`; kiểm tra `GET /api/health`. Thư viện Windows JDBC native nằm trong `%LOCALAPPDATA%\EquiTransit\tools\sqljdbc-native`. Local profile tin chứng chỉ SQL Server tự ký chỉ để phát triển; profile mặc định yêu cầu xác thực chứng chỉ.

Nếu bản backend chạy nền để kiểm tra đang dùng cổng 8080, chạy `.\tools\stop-local.ps1` trước khi Run trong IntelliJ. Với ứng dụng chạy bằng run-local.ps1, nhấn Ctrl+C ở terminal để dừng.

JWT key cục bộ được sinh ngẫu nhiên, giữ ở `.local/`, không đưa lên Git. Run configuration đọc `.local/application-secrets.properties`; không lưu secret trong XML run configuration. Nếu dùng terminal mới, mở lại terminal/IntelliJ để nhận JAVA_HOME và PATH đã cập nhật.

## Các API

Swagger UI: `http://127.0.0.1:8080/swagger-ui.html` sau khi chạy backend.
OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`.
FE dùng **Try it out** để gọi login, copy `accessToken`, nhấn **Authorize**
và dán token (không thêm chữ Bearer), rồi gọi `/api/customers/me`.
API auth là public; profile vẫn yêu cầu CUSTOMER JWT. Swagger mô tả API
đã có trong source, không tạo API booking hoặc các flow chưa triển khai.

| Method | URL | Quyền | Kết quả |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness |
| POST | `/api/auth/register` | Public | 201; tạo customer INACTIVE |
| POST | `/api/auth/verify-email` | Public, one-time token | 200; chuyển sang ACTIVE |
| POST | `/api/auth/resend-verification` | Public | 202; response giống nhau dù email không đủ điều kiện |
| POST | `/api/auth/login` | Public | 200; access token nếu ACTIVE |
| POST | `/api/auth/forgot-password` | Public | 202; response giống nhau dù email không đủ điều kiện |
| POST | `/api/auth/reset-password` | Public, one-time token | 200; đổi mật khẩu và vô hiệu JWT cũ |
| GET | `/api/customers/me` | CUSTOMER bearer token | 200; hồ sơ của token subject |

Đăng ký:

```json
{
  "email": "minh@example.com",
  "password": "ExamplePassword123!",
  "confirmPassword": "ExamplePassword123!",
  "fullName": "Nguyen Van Minh",
  "phone": "+84901234567",
  "address": "Ho Chi Minh City"
}
```

Mật khẩu 12–72 ký tự và tối đa 72 byte UTF-8 (bcrypt). Email ASCII được trim/lowercase. Không normalize/trim mật khẩu. Phone/address có thể bỏ trống. Giới hạn tên, email, phone/address khớp database. Không đưa mật khẩu hoặc token vào log.

Sau đăng ký, profile local lưu email trong `.local/outbox/`. Đọc file mới nhất để lấy giá trị sau `?token=`; gửi nó qua POST verify-email:

```json
{"token": "TOKEN_FROM_LOCAL_EMAIL"}
```

Email local **không được gửi ra hộp thư thật**. Link trỏ đến route frontend tương ứng; frontend chưa được triển khai trong thay đổi này. Dùng Postman để gọi API backend trước.

Đăng nhập:

```json
{"email": "minh@example.com", "password": "ExamplePassword123!"}
```

Gọi `/api/customers/me` với header `Authorization: Bearer <accessToken>`. Access token hết hạn sau 15 phút. Đây chưa phải refresh-token hoặc logout/revocation session flow; hết hạn thì đăng nhập lại. Mỗi request protected kiểm tra trạng thái/role/password fingerprint từ DB, nên lock account hoặc reset password làm token cũ không dùng được.

Recovery: gửi email tới forgot-password, lấy token trong email mới, rồi POST reset-password với `token`, `password`, `confirmPassword`. Token xác minh có hạn 24 giờ; token reset 30 phút; chỉ dùng một lần. Resend xóa token chưa dùng cũ, giữ lịch sử token đã tiêu thụ. Tài khoản đã xác minh rồi bị admin đổi sang INACTIVE không tự kích hoạt lại qua resend.

## Migration bổ sung

`CurrentActorService` xác định UserID/CustomerID/StaffID từ JWT và database;
`DomainAccessPolicy` kiểm role/ownership/assignment key. Profile service đã
được bảo vệ bằng method security. StaffProfile hiện chỉ được đọc để xác định
identity, chưa có API quản trị staff.

`database/migrations/001_authentication_token.sql` tạo `AuthenticationToken`
để đáp ứng A03. Chỉ lưu hash token, purpose, hạn dùng và thời điểm dùng; raw
token chỉ nằm trong email. Schema nghiệp vụ hiện tại dùng thêm migration 002.

Chạy migration một lần trước khi khởi động trên database chưa có bảng này. Script có guard để chạy lại an toàn. Hibernate dùng **validate**, không dùng update/create trên database thật.

```powershell
sqlcmd -S localhost -E -d EquiTransit -b `
  -i database/migrations/001_authentication_token.sql
```

## Kiểm thử và build

```powershell
.\run-local.ps1 -Verify
```

Hoặc JDK 21 đã có trong terminal:

```powershell
.\mvnw.cmd verify
```

Default tests dùng H2 trong bộ nhớ. `AuthFlowTest` có guard từ chối cleanup trên database thật. Để kiểm thử SQL Server thực tế, tạo **EquiTransit_AuthTest** bằng script tools/create-auth-test-db.sql, chạy migration token trên database test đó, sau đó:

```powershell
$env:EQ_TEST_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=EquiTransit_AuthTest;encrypt=true;trustServerCertificate=true;integratedSecurity=true'
$env:EQ_TEST_DDL = 'validate'
$nativeDir = Join-Path $env:LOCALAPPDATA 'EquiTransit\tools\sqljdbc-native'
.\mvnw.cmd verify "-DargLine=-Djava.library.path=$nativeDir"
```

Mở terminal mới hoặc xóa hai EQ_TEST_* env vars để quay lại H2. Không đổi tên database test thành EquiTransit: các test xóa fixture trong database test.

## Horse profile API — F02

Các endpoint cần JWT của CUSTOMER đang ACTIVE; CustomerID lấy từ tài khoản
đăng nhập. Không nhận customerId, horseId hoặc quyền sở hữu trong body.

| Method / path | Kết quả |
|---|---|
| POST `/api/horses` | 201, Location và profile + recordVersion |
| GET `/api/horses?page=0&size=20&q=Thunder` | 200, danh sách của mình; size 1–100, page từ 0; q tối đa 150 ký tự |
| GET `/api/horses/{id}` | 200 profile của mình; ngựa khác/không có đều 404 |
| PUT `/api/horses/{id}` | 200 profile + version mới; replacement, yêu cầu recordVersion |

Danh sách có dạng `{items, page, size, totalElements, totalPages}`, sort cố định
horseId giảm dần. q tìm tên theo chuỗi literal, không phải SQL wildcard.
DELETE/archive chưa thuộc scope đã chốt; không xóa ngựa và lịch sử qua API này.

Ví dụ tạo hồ sơ nháp (dữ liệu mẫu, không seed vào DB):

```json
{
  "name": "Thunder Strike",
  "sexCode": "GELDING",
  "microchipId": null,
  "weightKg": 500.25,
  "heightCm": null,
  "bodyWidthCm": null,
  "handlingNotes": "Requires calm handling"
}
```

Các field editable trong request/schema Swagger:

- name bắt buộc, tối đa 150; breed tối đa 100.
- microchipId/passportNumber tối đa 50 ký tự ASCII; blank được lưu NULL.
  Microchip đã biết phải duy nhất theo index của SQL Server; không tiết lộ chủ
  của microchip trùng. Nhiều hồ sơ chưa biết microchip được phép tồn tại.
- dateOfBirth là `YYYY-MM-DD`, không ở tương lai. sexCode là STALLION/MARE/GELDING
  hoặc null. Không tự tính tuổi cố định vào DB.
- weightKg > 0, tối đa 6 chữ số nguyên; heightCm/bodyWidthCm > 0, tối đa 4 chữ
  số nguyên. Tất cả tối đa 2 chữ số thập phân; có thể null trong hồ sơ nháp.
- measuredAt dùng ISO 8601 có offset, không ở tương lai, lưu UTC giây; ví dụ
  `2026-10-10T09:00:00+07:00` trả `2026-10-10T02:00:00Z`.
- specialCareNotes, handlingNotes, feedWaterRequirements, stallPreferences và
  veterinaryInstructionSource tối đa 1000; veterinaryInstructions tối đa 2000.
  Đây là thông tin chủ ngựa cung cấp, không phải approval của Specialist.

Response thêm horseId, recordVersion (16 ký tự hex, opaque) và
measurementsComplete. Complete chỉ có nghĩa đủ ba số đo và measuredAt; không
chứng minh fit policy, đủ giấy tờ hoặc được phép vận chuyển. Các lựa chọn slot
thuộc flow F05/F09, không tự suy ra từ profile API.

Khi lưu sửa, gửi **toàn bộ field editable** và recordVersion từ GET mới nhất.
Optional field bị bỏ qua/null sẽ được xóa ở master. Không gửi lại horseId hoặc
measurementsComplete từ response. 409 HORSE_VERSION_CONFLICT yêu cầu reload và
cho người dùng đối chiếu thay đổi; không tự retry bằng version mới. Sửa profile
không đổi horse/order snapshots đã submit và không thay ngựa của booking.

Lỗi dùng JSON an toàn: `message`, optional `fields` khi validation, và `code`
cho lỗi domain: HORSE_NOT_FOUND (404), HORSE_CONFLICT hoặc HORSE_VERSION_CONFLICT
(409). Input sai/field lạ/path-query sai trả 400; thiếu JWT trả 401; sai role 403.
Không trả SQL, stack trace, owner của ngựa khác hoặc credentials. FE hiển thị
chuỗi ghi chú dưới dạng text, không chèn trực tiếp vào HTML. Không tự retry POST
khi chưa biết request trước đã thành công hay chưa.

Horse persistence dùng parameterized JDBC trong Repository để dùng trực tiếp
SQL Server ROWVERSION và CAS update `owner + id + version`; Entity là persistence
model, DTO tách riêng. Transaction vẫn tại Service, cùng datasource và transaction
manager hiện tại. SQL session SET options được cấu hình cho filtered index.
Flow yêu cầu schema đầy đủ sau 001/002; không dùng Hibernate update để thêm cột.
Không cần migration mới cho F02.

Kiểm thử SQL của flow dùng bản restore tên `EquiTransit_FigmaMigrationTest_*`,
không dùng EquiTransit thật hay AuthTest tối giản. Ví dụ Windows Authentication:

```powershell
$env:EQ_HORSE_TEST_ENABLED = 'true'
$env:EQ_HORSE_TEST_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=EquiTransit_FigmaMigrationTest_<stamp>;encrypt=true;trustServerCertificate=true;integratedSecurity=true'
$env:EQ_TEST_DB_USERNAME = ''
$nativeDir = 'C:/Users/thang/AppData/Local/EquiTransit/tools/sqljdbc-native'
.\mvnw.cmd test -Dtest=HorseProfileIntegrationTest "-DargLine=-Djava.library.path=$nativeDir"
```

Thay `<stamp>` bằng test copy của máy. Fixtures thường rollback; test cạnh tranh
cần committed rows và xóa đúng ID fixture trong finally/AfterEach, không deleteAll.
Mở terminal mới sau khi test để không giữ các env test. CI mặc định chạy service,
validation, auth và OpenAPI tests; SQL integration là opt-in, cần chạy riêng trước
review thay đổi persistence. Kết quả skip không được coi là đã kiểm SQL Server.

## F03 — Single-horse transport requests

All endpoints below require a CUSTOMER bearer token. Ownership comes from the
authenticated account. Another customer's order, revision or horse is not
accessible by guessing its ID. Swagger publishes typed requests and responses.

| Method | Path under `/api/transport-orders` | Result |
| --- | --- | --- |
| POST | `/` | Create partial DRAFT, 201 + Location |
| GET | `/?page=0&size=20` | Owned orders, pagination envelope |
| GET | `/{id}` | Current request, version and explicit revision/plan IDs |
| PUT | `/{id}/draft` | Replace DRAFT fields using recordVersion |
| POST | `/{id}/submit` | Atomically submit the saved draft |
| POST | `/{id}/revisions` | Submit a replacement before commitment |
| GET | `/{id}/revisions?page=0&size=20` | History array, newest first |
| GET | `/{id}/revisions/{revision}` | Immutable captured revision |
| POST | `/{id}/change-requests` | Pending proposal after commitment |
| GET | `/{id}/change-requests/{change}` | Proposal receipt |

Use the root path without a trailing slash in actual requests. Page starts at
zero; size is 1–100. A saved draft does not create OrderRevision or an artificial
SubmittedAt. A submitted order has exactly one owned horse in TransportOrderDetail.
Each revision captures horse measurements/care and requested services together.
Changing the horse master later does not change historical snapshots.

Example draft (replace horseId with an owned profile and dates with future dates):

```json
{
  "horseId": 12,
  "originCountryCode": "VN",
  "destinationCountryCode": "GB",
  "originAddress": "Pickup stable address",
  "destinationAddress": "Destination stable address",
  "originTimezone": "Asia/Ho_Chi_Minh",
  "destinationTimezone": "Europe/London",
  "preferredDepartureAt": "2027-02-01T08:00:00+07:00",
  "deliveryDeadline": "2027-02-04T18:00:00Z",
  "importType": "NEEDS_ADVICE",
  "receiverName": "Receiving contact",
  "services": {
    "roadArrangement": "EQUITRANSIT",
    "airStall": "WIDE",
    "quarantinePackage": "STANDARD"
  }
}
```

Draft fields may be incomplete. Submission requires horseId, ISO country codes
(GB, not UK), pickup/delivery addresses, named timezones and a future delivery
deadline. Countries must differ. Departure, when present, precedes the deadline;
the deadline cannot follow eventStartAt. Dates require an offset and are stored
as UTC seconds. Receiver/event/import details can be supplied when known.
The server assigns the order number, USD currency, owner and lifecycle state;
do not send these fields. Unknown fields are rejected.

PUT draft and POST revisions are full replacements: omitted/null optional fields
are cleared. Send only editable fields, not the full GET response. Include the
recordVersion from the latest GET. Stale writes return 409 ORDER_VERSION_CONFLICT;
reload and let the customer reconcile changes rather than silently overwrite.

After saving, submit the saved draft using:

```json
{
  "recordVersion": "0000000000001234",
  "operationId": "b8ea3970-5cdf-4f1b-b3b4-790e2f2b51ef"
}
```

Generate operationId once per submit/revise/proposal action. Retry an uncertain
action using the same UUID, version and input. A successful retry returns the
same immutable revision or proposal receipt; changed input with a reused UUID
returns 409 ORDER_RETRY_CONFLICT. Action responses are 200, including replays.
GET the current order afterwards to obtain its current recordVersion. Replaying
an older action does not make its historical snapshot the current request again.

Revision commands contain the full editable request plus recordVersion,
operationId and a nonblank changeReason (up to 500 characters). Direct revision
is allowed in SUBMITTED/CHANGES_REQUESTED/APPROVED before accepted quotes or protected
operational commitments. It preserves previous revisions and currentPlanId.
Unaccepted preparation quotes become SUPERSEDED and plan gates return to PENDING;
an old plan never becomes applicable to a new revision by selecting MAX(RevisionID).

After acceptance, authorization or departure, use change-requests with the same
command shape. This only records a PENDING proposal, linked to the current base
revision. It does not change the order, plan, quote or itinerary. One pending
proposal is allowed per order. F13 implements staff review, customer consent and
application later. Closed orders cannot open a new proposal.

Services here are requested intent, not confirmed bookings or payable amounts.
F05 rechecks measurement eligibility against approved, sourced fit policies on
submission/revision. SHARED_ONE is rejected unless eligible; known unsuitable
WIDE/PRIVATE choices are rejected, and missing policy remains pending review.
Specialized StallChoice, QuarantineChoice and InsuranceChoice rows retain the
request; unknown required days stay NULL and no insurance cover is activated.
Optional care and insurance are not silently selected. F08/F09/F10 supply source
assessments; quote/payment and departure activation belong to their own flows.
Drafts can coexist for the same horse; overlap prevention at departure belongs
to F10/F11 and is not implemented by draft creation.

Cross-flow transaction contract: quote acceptance and plan activation must lock
the same TransportOrder parent before checking revision/version and committing.
F03 locks that parent, rechecks accepted/authorized/departed evidence and writes
revision, horse detail, service choices and current pointer in one transaction.
Any failed write rolls back the entire action. Persistence uses parameterized
JDBC with SQL Server rowversion, preserving the layered service transaction model.
Requires existing migrations 001/002; F03 adds no database migration. F04 requires
the additional migration 003 described below.

Errors use safe JSON code/message (validation may include fields). Typical codes:
ORDER_NOT_FOUND (404), ORDER_VERSION_CONFLICT, ORDER_RETRY_CONFLICT,
ORDER_CHANGE_REQUEST_REQUIRED, ORDER_NOT_EDITABLE and ORDER_CHANGE_PENDING (409).
Missing token is 401; wrong role is 403; malformed/unknown fields are 400.
Render customer text as text, never raw HTML.

SQL verification uses only the isolated full-schema restore, with rollback or
ID-scoped cleanup. Run the order suite using the F02 command above, replacing
EQ_HORSE_TEST_ENABLED/DB_URL with EQ_ORDER_TEST_ENABLED/DB_URL and selecting
`-Dtest=TransportOrderIntegrationTest`. Default CI runs policy and Swagger tests;
opt-in SQL tests must also be run for persistence changes. Skips are not passes.

## F04 — Manager assessment and assigned preparation cases

Manager reviews the exact submitted revision, approves and assigns its team,
requests clarification, or rejects it. Request approval starts preparation. It
does not mark documents cleared, money received, quotes accepted, reservations
confirmed or departure authorized. Staff provisioning is owned by F15; F04 only
selects existing ACTIVE Specialist/Coordinator profiles. Registration remains
CUSTOMER-only; these APIs cannot create or elevate accounts.

Before running F04, apply `database/migrations/003_order_assessment_assignments.sql`
after 001/002. It adds nullable AssignedBy, DueAt and AssignmentNote to the real
OrderAssignment table, a due-date check, one decision per revision and one active
holder per order/assignment role. Historical rows keep null metadata where the
actor/deadline was not recorded. It never deletes conflicting history or invents
backfilled actors. Preflight stops on duplicate decisions or active assignments:
review those rows before rerunning. Stop the application during migration and
take a backup first. The script is transactional and repeatable. A failed attempt
does not install a partial schema; runtime mappings do not use Hibernate update.

Migration 003 was verified on the isolated full-schema test copy. It is not
automatically applied to the real EquiTransit database. Team members must run it
on their own database before using F04. To repair a deployment, fix the reported
legacy conflict with a reviewed forward migration and rerun 003. If reverting
application code, leave the additive columns/history intact; do not drop populated
columns or audit records as a rollback shortcut.

Manager routes require a MANAGER JWT and a live linked StaffProfile:

| Method | Path under `/api/manager/transport-orders` | Purpose |
| --- | --- | --- |
| GET | root, `?state=SUBMITTED&page=0&size=20` | Review queue envelope |
| GET | `/staff?role=SPECIALIST&page=0&size=20` | Eligible staff and workload |
| GET | `/{id}` | Current captured revision, version and active team |
| POST | `/{id}/decisions` | Atomic assessment and approval/team assignment |
| PUT | `/{id}/assignments/{role}` | Assign or replace responsibility |
| POST | `/{id}/assignments/{role}/end` | Revoke responsibility, keep history |
| GET | `/{id}/decisions?page=0&size=20` | Decision history array |
| GET | `/{id}/assignments?page=0&size=20` | Assignment history array |

Use the root without a trailing slash. Review states are SUBMITTED, APPROVED,
CHANGES_REQUESTED and REJECTED. Role is SPECIALIST or COORDINATOR. Page is
zero-based, size 1–100; history endpoints return bounded arrays, queues/directories
return items/page/size/totalElements/totalPages envelopes. Unsent drafts are not
part of Manager review. Staff directory returns StaffID, display name, live role
and activeCases. It omits credentials, account IDs and private contact fields.

One Specialist/Coordinator may handle several cases; Manager sees workload to
balance them. activeCases counts currently APPROVED cases with active assignments.
availabilityStatus=NOT_CONFIGURED explicitly means no staff calendar/capacity
policy exists. DueAt is a case deadline, not a reserved journey interval. Resource
and travel scheduling conflicts remain with F09/F10; F04 does not claim availability.

After GET review and staff selection, approve using **StaffID**, not UserID:

```json
{
  "revisionId": 42,
  "recordVersion": "0000000000001234",
  "operationId": "fa2c8d7b-6c51-4976-a28d-613c4199cfd6",
  "decision": "APPROVED",
  "specialistId": 7,
  "coordinatorId": 9,
  "dueAt": "2027-02-01T09:00:00+07:00",
  "assignmentNote": "Review the route and document lead times"
}
```

Replace IDs, version and deadline with actual values. Approval requires both
distinct active staff roles; dueAt and assignmentNote are optional. Deadlines
require offsets, a supported SQL date range and future dates, normalized to UTC
seconds. For REJECTED/CHANGES_REQUESTED, supply a nonblank reason and omit all
assignment fields. Reason is at most 1000 characters; staff notes at most 1000.
No body field may supply managerId, owner, next status or downstream gate results.
Unknown fields and malformed enum/path/query values are rejected.

The state transition is SUBMITTED → APPROVED/CHANGES_REQUESTED/REJECTED. A revision
receives one immutable assessment. Changing it requires a new customer revision,
not replacing the old decision. An APPROVED request can still be revised by its
customer before protected commitment; it returns to SUBMITTED and needs Manager
assessment again. Staff case access pauses until the new revision is approved.
Existing assignment history is retained; reapproval records the new active team.

Reassignment PUT takes recordVersion, operationId, staffId, mandatory reason,
optional dueAt/assignmentNote. These optional fields are full replacements; omit
them to clear current metadata by creating a new responsibility row. Ending an
assignment takes recordVersion, operationId and mandatory reason. Both operations
require an APPROVED uncommitted current revision, preserve ended history and
advance the parent version. Old staff immediately lose access; unaccepted
preparation quotes are superseded and stale plan gates reset to PENDING.
Accepted/authorized/departed commitments require controlled handling instead.

Commands return AssessmentReceipt with orderStatus, recordVersion, decision and
active assignment views. Generate a UUID once per action; retries reuse the same
UUID, version and input. The same Manager's retry returns the original completion
receipt, even after subsequent actions. Changed input/action with a reused UUID
is 409 ASSESSMENT_RETRY_CONFLICT. GET the current case after completion/retry for
the latest version; an old receipt is not the current team. Replay remains valid
when a previously recorded dueAt has since passed.

SPECIALIST/COORDINATOR endpoints:

- `GET /api/staff/transport-orders?page=0&size=20`: only assigned APPROVED cases.
- `GET /api/staff/transport-orders/{id}`: rechecks live account role/status and
  active assignment; returns captured revision and only the actor's instructions.

Unassigned/ended/wrong-case access returns the same 404 CASE_NOT_FOUND. Customer
and Driver cannot use staff endpoints. BE2 case writes must call
CaseAssignmentAccess.lockRevision(orderId, requiredRole) **inside their own service
transaction** before work. Its parent lock stays held through the consumer commit,
so reassignment cannot race that write. This facade currently covers APPROVED
preparation cases; later operational flows must define their phase/state access
contract explicitly, not silently bypass the assignment check.

`GET /api/transport-orders/{id}/assessment` requires CUSTOMER ownership. It returns
currentRevisionId, decision and the public rejection/clarification reason. While
unassessed, decision/reason are null. Internal approval reasons, assignment notes,
staff IDs and staff history are omitted. Render all supplied text as text.

Concurrency: Manager actions use the same parent order lock as F03, compare the
expected SQL rowversion and recheck current revision and protected commitments.
Staff accounts are rechecked/locked in the transaction; multiple assignees are
locked in StaffID order. Decision, assignment endings/insertions, order state and
append-only AuditEvent completion record commit together. Failed writes leave
none of these changes applied. Database unique indexes also prevent duplicate
decisions and active role holders. Audit stores UserID; OrderDecision.ManagerID
and OrderAssignment.AssignedBy store StaffID. No raw credentials are stored/logged.

Errors include ASSESSMENT_REVISION_CONFLICT, ASSESSMENT_STATE_CONFLICT,
ASSESSMENT_RETRY_CONFLICT, ASSIGNMENT_STAFF_INVALID, ASSIGNMENT_STATE_CONFLICT,
ASSIGNMENT_NOT_ACTIVE and ORDER_VERSION_CONFLICT (409), and
ASSESSMENT_INPUT_INVALID (400). Missing/invalid credentials return 401, wrong
role 403, unavailable resources 404. Responses do not expose SQL or stack traces.

Run the SQL suite using the earlier native-driver setup with
EQ_ASSESSMENT_TEST_ENABLED=true, EQ_ASSESSMENT_TEST_DB_URL pointing to the isolated
EquiTransit_FigmaMigrationTest_* copy with migration 003, and
`-Dtest=OrderAssessmentIntegrationTest`. Default CI covers input policy, auth and
Swagger; actual SQL persistence/revocation/rollback/concurrency must be verified
separately before review. Opt-in skips are not test passes.

## Java conventions

Theo PDF Oracle/Sun Java Code Conventions được cung cấp: 4 spaces, không tab; 80 ký tự kể cả import (import dài được xuống dòng); tên class/method/field/constant theo quy ước; một public type/file; comment đầu file; Javadoc public API; braces cho control flow; khai báo local variables đầu block; static fields trước instance fields, constructors trước methods; phân nhóm bằng dòng trống.

Checkstyle chạy ở Maven validate, bao gồm source và tests. Cấu hình ở config/checkstyle.xml. Các quy tắc mang tính ngữ nghĩa như ý nghĩa tên, độ chính xác comment, khai báo ở đầu block, hai dòng trống giữa các phần và các quyết định nghiệp vụ còn cần review thủ công. Dùng Java 21/Spring annotation và try-with-resources là bổ sung hiện đại; tài liệu 1997 không mô tả chúng.

## Chạy ngoài môi trường local

Đặt EQ_DB_URL/EQ_DB_USERNAME/EQ_DB_PASSWORD, EQ_JWT_SECRET (Base64, tối thiểu 32 byte ngẫu nhiên), EQ_FRONTEND_URL, EQ_CORS_ORIGIN và EQ_SMTP_HOST/PORT/USERNAME/PASSWORD/MAIL_FROM. Không bật profile local để tránh gửi email qua file hoặc tin chứng chỉ tự ký.

SMTP email được xử lý sau khi transaction commit. Nếu gửi mail lỗi, tài khoản vẫn được giữ INACTIVE và người dùng có thể resend; hiện chưa có durable email queue/retry worker. Rate limit 20 auth requests/phút/IP là bộ nhớ trên một node; khi triển khai nhiều node cần rate limiter chung và cấu hình reverse proxy phù hợp. Ứng dụng mặc định bind localhost; triển khai thật cần cấu hình bind và TLS tại reverse proxy.

Không commit `.local`, credentials, outbox email hoặc bản ghi khách hàng. `.gitignore` đã loại các file đó, target và cấu hình IntelliJ cá nhân.

## Git workflow

Nhánh tích hợp của repository này là `dev`. Tạo nhánh task từ `dev`, mở Pull Request vào `dev` và yêu cầu một thành viên khác review. `main` dành cho bản phát hành đã kiểm thử.


## F05 — Booking services and transparent charge disclosure

Both endpoints require an ACTIVE CUSTOMER and an owned order. Identity comes
from authentication; the body cannot select another horse, owner, policy,
price, staff member or mandatory quarantine/veterinary quantity.

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/transport-orders/{id}/services` | Current saved preferences, seven sections, measured stall options and all charges |
| POST | `/api/transport-orders/{id}/services/confirm` | Replace requested preferences on a draft or create a new submitted revision |

### Frontend integration

First save a request with its horse through F03, then GET services. `sections`
contains TRANSPORTATION, AIR_STALL, HORSE_CARE, VETERINARY, QUARANTINE, INSURANCE
and ADDITIONAL_CHARGES. Fees for coordination, documentation, airport and customs
are presented in ADDITIONAL_CHARGES. Show all only expands `charges`; it must not
submit choices, create fees or start payment. There is no next-horse action.

Each charge has a stable `code`, `uiSection`, physical `costGroup`, `unit`,
`quantity`, `unitRate`, `amount`, `mandatory`, `selected`, `pricingStatus`,
`formula`, published tariff ID/version and safe `blockers`. Quantity and money
are plain decimal **strings**, or NULL while unknown. Display decimals without
converting them to JavaScript floating-point money; calculations belong to the
server. Known zero, included service and unresolved quantity are different states.
Amounts are multiplied exactly and rounded once HALF_UP to two USD decimals.

`knownSubtotal` sums the currently known selected estimates. `totalEstimate`
is NULL while a selected applicable charge remains unresolved. Neither is a
payable quote: `payable` is always false and `carrierConfirmation` remains
NOT_CONFIRMED. Do not render a payment action from this response.

Use `airStalls[].status` and `reasons`, not the mere presence of an option, to
label eligibility. UNSUITABLE options are omitted. SHARED_ONE appears only when
ELIGIBLE; WIDE/PRIVATE may be PENDING requests requiring further assessment.
Approved global policies require complete kg/cm measurements, inclusive limits,
source/reviewer, effective dates and validity through the known delivery horizon.
Ambiguous, incomplete, expired or absent policies are pending. Equipment/carrier
scoped policies require actual operational context in F09/F10 and are not guessed.
Measurement eligibility is not legal clearance, physical equipment acceptance
or a confirmed carrier reservation. No fixture weight threshold is installed.

Confirm sends the **complete replacement** preferences, not a patch:

```json
{
  "recordVersion": "0000000000000001",
  "horseRecordVersion": "0000000000000002",
  "operationId": "029d479b-1f3b-4b40-b5ce-547a711e34d1",
  "changeReason": "Update requested services",
  "services": {
    "roadArrangement": "EQUITRANSIT",
    "veterinaryArrangement": "EQUITRANSIT",
    "airStall": "WIDE",
    "enhancedCare": false,
    "groomingDays": 0,
    "boardingNights": 0,
    "quarantinePackage": "STANDARD",
    "insurance": "NONE"
  }
}
```

The versions above are examples only; use versions from your latest GET.
Arrangements: EQUITRANSIT/CUSTOMER. Stall: SHARED_ONE/WIDE/PRIVATE (1/1.5/3 slots).
Quarantine: STANDARD/ENHANCED. Insurance: NONE/EXISTING/MORTALITY/COMBINED.
EXISTING requires `existingPolicyReference` (max 100 characters); a reference is
not valid with another option. Optional `declaredValue` is positive plain USD
DECIMAL(18,2) text (max 16 integer and 2 fractional digits), not a premium. It
requires an insurance request. Exponent notation is rejected. COMBINED maps to
MORTALITY_INJURY coverage; it does not add another mortality policy.

On DRAFT, confirm updates draft preferences without a submitted revision.
On SUBMITTED/CHANGES_REQUESTED/eligible APPROVED orders, it requires a nonblank
`changeReason` and creates a new immutable F03 revision. It saves REQUESTED
choices and specialized selection rows, never an activated InsurancePolicy,
QuoteItem, PaymentRequest or flight reservation. New revisions require their
own source assessments; the old revision's assessment is not silently inherited.
A requested QuarantineChoice has RequiredDays=NULL; it is not an active assessed
package or a waiver. Verified NOT_REQUIRED is returned by the assessment source,
never saved as RequiredDays=0. Extra boarding does not include quarantine days.

Reuse the operation UUID with exactly the same input on an uncertain retry.
The persisted receipt is stable; GET current again after success to obtain the
new version. Different input with a reused UUID returns SERVICE_RETRY_CONFLICT.
Parent/horse locks and observed versions protect concurrent updates. If the horse
master changed after capture, HORSE_SNAPSHOT_STALE requires an explicit F03
revision refresh; confirming services does not silently replace the snapshot.
Accepted/authorized/departed orders return the existing F03 protected-change
error; use controlled change intake and F13 rather than editing in place.

Other safe errors include ORDER_NOT_FOUND (404), ORDER_VERSION_CONFLICT,
HORSE_VERSION_CONFLICT, ORDER_HORSE_REQUIRED, SERVICE_CHANGE_REASON_REQUIRED,
AIR_STALL_UNSUITABLE, AIR_STALL_ASSESSMENT_REQUIRED, INSURANCE_REFERENCE_REQUIRED
and INSURANCE_INPUT_INVALID (409). Malformed/unknown fields return 400; wrong
role returns 403. Reload on version conflicts and preserve editable FE input.

### Published rates and source contracts

One SQL query reads the server-owned service catalogue per disclosure, preserving
all overlapping versions. There is no database query per charge. Only effective
ACTIVE USD rates are used; multiple matches, missing rates and wrong units/groups
remain pending. Rate changes affect estimates, not previously issued quotes.

| Service / option | Expected unit | Calculation or source |
|---|---|---|
| ROAD_TRANSPORT / EQUITRANSIT | KM | Sourced ROAD km × one published rate |
| AIR_STALL / DEFAULT | SLOT_KM | Selected slots × sourced AIR km × rate |
| DOCUMENT_COORDINATION / DEFAULT | ROUTE_KM | (ROAD + AIR km) × rate, once |
| HORSE_CARE / ENHANCED | HORSE_KM | One horse × route km × rate |
| GROOMING / EXTRA | PERSON_DAY | Requested extra groom days |
| BOARDING / EXTRA | HORSE_NIGHT | Requested extra nights only |
| VETERINARY / EXAM, TEST, VACCINE, CERTIFICATE | HORSE_VISIT, TEST_SAMPLE, DOSE, CERTIFICATE | Exact-revision verified Specialist counts |
| QUARANTINE / STANDARD, ENHANCED | HORSE_DAY | Specialist required days; UNKNOWN is not exemption |
| AIRPORT_HANDLING / DEFAULT | HORSE_AIRPORT | Assessed airport scope, pending until produced |
| CUSTOMS_DECLARATION / DEFAULT | DECLARATION | Assessed declaration scope, excludes authority duties |

Basic care is INCLUDED; document review/follow-up is included in coordination.
Customer-arranged services are EXTERNAL_PROVIDER, requiring later evidence review.
Government duty is AUTHORITY_ASSESSMENT_PENDING. Waiting is EVENT_ONLY and not
charged now. New insurance is INSURER_TERMS_PENDING; airfare is AIRFARE_PENDING
until a customer quote is prepared. Private supplier purchase cost/margin is
never selected or returned by the customer disclosure adapter.

F08 source contracts: `RevisionQuarantineAssessment` and
`RevisionVeterinaryAssessment` point to the exact submitted revision. Source,
assessed time, validity through delivery, current active assigned Specialist and
live account role/status are required. SupersededAt retains assessment history;
only one current assessment per revision is permitted. New/expired/missing
assessment returns UNKNOWN/null quantities. The producer write APIs belong to
F08, not this customer popup.

F10 distance contract: use the order's explicit CurrentPlanID, matching OrderID
and RevisionID, with PlanStatus CONFIRMED. Each ROAD/AIR leg requires positive
DistanceKm, DistanceSource, DistanceConfirmedAt and DistanceConfirmedBy from the
current active Coordinator. Legacy distance alone is not verified. Missing source
makes route quantities pending. F09/F10 own reservation and carrier confirmation.

Selected, nonexpired AIRFARE SupplierOffer bundle metadata requires evidence.
IncludesStall explicitly includes AIR_STALL. IncludedCharges uses this envelope:

```json
{"schemaVersion":1,"services":["AIRPORT_HANDLING","TERMINAL_HANDLING"]}
```

Allowed codes: AIR_STALL, AIRPORT_HANDLING, CUSTOMS_DECLARATION, TERMINAL_HANDLING.
Multiple selected airfare offers, malformed/legacy envelopes or unknown codes
require BUNDLE_REVIEW_REQUIRED, not guessed inclusion. Terminal handling is not
charged separately without verified scope, avoiding accidental bundled duplicates.
F06 owns offer preparation and commercial issuance; F15 owns real tariff publishing.

### Migration, verification and deployment

Migration `004_service_assessment_sources.sql` follows 001/002/003. It adds
nullable leg source metadata and revision-bound assessment tables with FK/CHECK
constraints and filtered unique indexes. No fixture rate, fit threshold, source
or mandatory day is backfilled. Existing unsourced distances remain pending.
The script is transactional and re-runnable; stop and review incompatible legacy
assessment tables/duplicates, repair forward without deleting evidence history.
Back up and review the target environment before deployment. Do not use Hibernate
update, drop populated proof tables or treat a test-copy migration as deployment.

The F05 disclosure handles missing 004 producer tables/leg source columns as
pending, allowing integration with the 002 schema while producer migrations are
not deployed. Full sourced assessment integration requires 003/004. F04 itself
still requires 003. Production EquiTransit has not been changed by F05 work.

Default `./mvnw verify` runs policy/pricing/auth/contract tests; SQL suites are
opt-in and skipped by default. Use a separate full-schema restored
EquiTransit_FigmaMigrationTest_* database with migrations 001–004:

```powershell
$env:EQ_SERVICES_TEST_ENABLED = 'true'
$env:EQ_SERVICES_TEST_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=YOUR_ISOLATED_TEST_DB;encrypt=true;trustServerCertificate=true;integratedSecurity=true'
$env:EQ_TEST_DB_USERNAME = ''
.\mvnw.cmd -B test '-Dtest=BookingServicesIntegrationTest' '-DargLine=-Djava.library.path=YOUR_SQLJDBC_NATIVE_DIRECTORY'
```

The suite refuses fixture writes outside the named test database prefix. It tests
ownership/role/spoofing, read-only Show all, sourced prices and units, fit and
expiry, bundles, NULL semantics, exact decimal precision, malicious decimals,
immutable revisions, specialized selections, stale horse versions, retries,
rollback and competing confirmations. SQL tests do not run against EquiTransit.
API/SQL integration success is not a production throughput or latency guarantee;
load testing and producer-flow end-to-end acceptance remain separate work.


## DTO/OpenAPI contract normalization

The horse and transport-order contracts now publish explicit field and complete
command examples, anchored formats and finite OpenAPI enums. JSON property names,
response shapes and immutable snapshot serialization remain unchanged. Inherited
update/revision examples include their required version/operation identifiers.
See [the field-by-field audit](docs/api-dto-audit.md) for scope and changes.

Optional text is NULL when unknown; non-null empty/whitespace values are rejected.
Receiver/event/provider details remain optional at draft and assessment submission.
Submission requires horse, international countries, addresses, IANA timezones,
preferredDepartureAt, deliveryDeadline and importType (TEMPORARY/PERMANENT/
NEEDS_ADVICE). Drafts can still be incomplete. Dates must follow the existing
chronology rules. This records assessment intent, not a confirmed booking.

Use uppercase country/airport codes, E.164 phone/provider contacts, IANA timezone
names such as Asia/Ho_Chi_Minh, valid uppercase microchip/passport identifiers and
opaque16hex record versions. Email uses standard email validation. Former free
ASCII identifiers/contacts, lowercase codes, UTC shorthand and empty strings may
now return400; preserve input and let the customer correct it. No legacy master
or revision data is automatically rewritten or cleared to satisfy new formats.

requestedInputs is described by RequestedServiceInputsSchema with explicit
version1 keys, rather than a Swagger additionalProp1 map. Its internal persisted
map is preserved, retaining exact applicability and omitted-key behavior.
DTO finite codes are OpenAPI enums without changing existing stored strings.
Response money remains decimal strings, no client-side financial rounding.
SLOT_KM quantity can carry3decimal places (1.5 ×0.01km =0.015); money and published
unit rates retain2decimal places. Quantity is not rounded to change the fee.

Examples are documentation fixtures only. Replace sample horse/order/staff IDs
with actual authorized IDs and use current dates/versions when trying the API.
Restart the correctly configured local backend after switching/building this
branch to see updated Swagger; a previously running JAR does not reload itself.

## Versioned quotations — F06

API contract, actor permissions, JSON command examples, pricing provenance,
state transitions, idempotency, migration and test setup:
[docs/quotation-api.md](docs/quotation-api.md).

Migration `005_versioned_quotations.sql` must be deployed after 001–004 before
using quotation APIs. It is not applied automatically to the real database.
Quote acceptance commits a price; it never confirms payment or departure.

## Manual bank payments (F07)

See [F07 API contract](docs/manual-bank-payments-api.md) for Customer reporting,
Manager reconciliation/allocation/refunds, protected image proofs and the separate
F11 departure/F14 settlement financial facade. Requires reviewed migration007
on an explicitly authorized target database; code never auto-migrates EquiTransit.
Report is not paid; acceptance is not bank receipt or operational clearance.

## F16 — Dashboards, notifications and audit

Authenticated actor dashboard: `GET /api/dashboard` and `/api/dashboard/tasks`.
Customer orders remain private; staff see active case/leg assignments only.
`GET /api/activity/orders/{orderId}/timeline` returns observed milestones.
`GET /api/notifications` and `PUT /api/notifications/{id}/read` are recipient scoped.
Customer ordinary-mail preference: GET/PUT
`/api/customers/me/notification-preferences`; security emails are independent.
Manager-only `/api/manager/audit-events` and `/api/manager/reports/financial`
separate quoted value, customer claims, reconciled cash and net allocations.
All list APIs use `page=0&size=20`, max100; every response is JSON.

See [F16 API and producer contract](docs/activity-api.md) for privacy, exact
financial semantics, AuditWriter/NotificationWriter integration and retry rules.
Deploy migration008 after007 in an explicitly authorized database. It is not run
automatically; existing audit history establishes the notification watermark.
Missing008 returns503 for F16; legacy booking submission remains compatible.
For native SQL verification use EQ_ACTIVITY_TEST_ENABLED=true and
EQ_ACTIVITY_TEST_DB_URL targeting only the documented isolated test catalog.
