# EquiTransit backend — account flow

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
is allowed only in SUBMITTED/CHANGES_REQUESTED before accepted quotes or protected
operational commitments. It preserves previous revisions and currentPlanId.
Unaccepted preparation quotes become SUPERSEDED and plan gates return to PENDING;
an old plan never becomes applicable to a new revision by selecting MAX(RevisionID).

After acceptance, authorization or departure, use change-requests with the same
command shape. This only records a PENDING proposal, linked to the current base
revision. It does not change the order, plan, quote or itinerary. One pending
proposal is allowed per order. F13 implements staff review, customer consent and
application later. Closed orders cannot open a new proposal.

Services here are requested intent, not confirmed bookings or payable amounts.
No sample prices, unknown quantities, required quarantine days or eligibility
approvals are invented. WIDE/PRIVATE air stall requests remain pending fit review;
SHARED_ONE submission returns AIR_STALL_ASSESSMENT_REQUIRED until F05 provides
confirmed eligibility. No StallChoice/assignment or QuarantineChoice is fabricated.
Optional care and insurance are not silently selected. F05/F08/F09 supply these
assessments; quote/payment and departure activation belong to their own flows.
Drafts can coexist for the same horse; overlap prevention at departure belongs
to F10/F11 and is not implemented by draft creation.

Cross-flow transaction contract: quote acceptance and plan activation must lock
the same TransportOrder parent before checking revision/version and committing.
F03 locks that parent, rechecks accepted/authorized/departed evidence and writes
revision, horse detail, service choices and current pointer in one transaction.
Any failed write rolls back the entire action. Persistence uses parameterized
JDBC with SQL Server rowversion, preserving the layered service transaction model.
Requires existing migrations 001/002; F03 adds no database migration.

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

## Java conventions

Theo PDF Oracle/Sun Java Code Conventions được cung cấp: 4 spaces, không tab; 80 ký tự kể cả import (import dài được xuống dòng); tên class/method/field/constant theo quy ước; một public type/file; comment đầu file; Javadoc public API; braces cho control flow; khai báo local variables đầu block; static fields trước instance fields, constructors trước methods; phân nhóm bằng dòng trống.

Checkstyle chạy ở Maven validate, bao gồm source và tests. Cấu hình ở config/checkstyle.xml. Các quy tắc mang tính ngữ nghĩa như ý nghĩa tên, độ chính xác comment, khai báo ở đầu block, hai dòng trống giữa các phần và các quyết định nghiệp vụ còn cần review thủ công. Dùng Java 21/Spring annotation và try-with-resources là bổ sung hiện đại; tài liệu 1997 không mô tả chúng.

## Chạy ngoài môi trường local

Đặt EQ_DB_URL/EQ_DB_USERNAME/EQ_DB_PASSWORD, EQ_JWT_SECRET (Base64, tối thiểu 32 byte ngẫu nhiên), EQ_FRONTEND_URL, EQ_CORS_ORIGIN và EQ_SMTP_HOST/PORT/USERNAME/PASSWORD/MAIL_FROM. Không bật profile local để tránh gửi email qua file hoặc tin chứng chỉ tự ký.

SMTP email được xử lý sau khi transaction commit. Nếu gửi mail lỗi, tài khoản vẫn được giữ INACTIVE và người dùng có thể resend; hiện chưa có durable email queue/retry worker. Rate limit 20 auth requests/phút/IP là bộ nhớ trên một node; khi triển khai nhiều node cần rate limiter chung và cấu hình reverse proxy phù hợp. Ứng dụng mặc định bind localhost; triển khai thật cần cấu hình bind và TLS tại reverse proxy.

Không commit `.local`, credentials, outbox email hoặc bản ghi khách hàng. `.gitignore` đã loại các file đó, target và cấu hình IntelliJ cá nhân.

## Git workflow

Nhánh tích hợp của repository này là `dev`. Tạo nhánh task từ `dev`, mở Pull Request vào `dev` và yêu cầu một thành viên khác review. `main` dành cho bản phát hành đã kiểm thử.
