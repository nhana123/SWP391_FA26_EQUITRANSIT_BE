# F11 — Verify Departure Gates & Physically Receive the Horse

## Overview
Provides real-time 3-gate departure readiness evaluation (`Financial`, `Legal/Document`, `Operational`), manager-only departure authorization (`Authorize Departure`), road driver physical horse receipt & microchip/passport verification (`HorseReceipt`), and atomic first-leg departure (`depart`).

## Database Migration
Apply explicitly in order after migrations 001-008:
- `009_departure_gates_horse_receipt.sql`: source HorseReceipt table and keys.
- `010_departure_runtime_guards.sql`: receipt lookup index and initial guard.
- `011_departure_transition_guards.sql`: current plan/revision, latest receipt,
  active Manager/Driver and transition-only departure validation.

## Endpoints (`/api/transport-orders/{orderId}/departure`)

| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/transport-orders/{orderId}/departure` | `CUSTOMER` (redacted), `MANAGER`, assigned `COORDINATOR`, assigned `DRIVER` | Evaluates live 3-gate readiness, authorization state, and recorded horse receipts. |
| `POST` | `/api/transport-orders/{orderId}/departure/gates/evaluate` | Active `MANAGER`, assigned `COORDINATOR` | Evaluates all 3 gates as of `now` and persists gate statuses on `dbo.TransportPlan` (clearing stale authorization if any gate is blocked). |
| `POST` | `/api/transport-orders/{orderId}/departure/authorize` | Active `MANAGER` only | Authorizes departure when all 3 gates evaluate to `PASS` in the same transaction. Cannot bypass Legal, Financial, or Operational gates. |
| `POST` | `/api/transport-orders/{orderId}/departure/horse-receipts` | Assigned active `DRIVER` on first `ROAD` leg | Records physical horse pickup inspection (`ObservedMicrochip`, `ObservedPassportNumber`, `IdentityCheckResult`, `ReceiptStatus`). |
| `POST` | `/api/transport-orders/{orderId}/departure/depart` | Assigned active `DRIVER` on first `ROAD` leg | Re-verifies all 3 gates (`BR-09`), manager authorization, and latest `MATCHED`/`ACCEPTED` `HorseReceipt`, then atomically sets `ActualDeparture`, `LegStatus='IN_TRANSIT'`, `PlanStatus='IN_TRANSIT'`, and `OrderStatus='IN_TRANSIT'`. |

## Main-project integration

Feature classes use layer/feature packages ending in `departure`. The original
HorseReceipt schema is extracted in 009; 010 contains only the F11 index and
first-leg guard. Existing F08, F09, F10 migrations and scheduling guards remain
unchanged. Financial readiness uses BE1 quote/payment repositories and its
FinancialReadinessPolicy. Legal readiness uses the current F08 coverage,
clearance and dynamic document checks through an internal adapter, preserving
F08 HTTP permissions. Audit writes use the baseline AuditEvent schema.

## Hoàn thiện nghiệp vụ F11

Giữ mã trạng thái đã dùng trong project: `PASS/BLOCKED`, biên nhận
`ACCEPTED/REJECTED/PENDING`, chặng/plan/đơn `IN_TRANSIT`. Không thêm enum
`PASSED`, `RECEIVED` hay `IN_PROGRESS` chỉ để khớp tên trong bản phân tích.

Luồng client:
1. Manager/Coordinator đánh giá lại ba cổng; Manager ký duyệt khi cả ba đạt.
2. Driver được giao chặng ROAD đầu upload ảnh PNG/JPEG thật (tối đa 10 MB):
   `POST /api/transport-orders/{orderId}/departure/evidence` dạng multipart,
   gồm `planId`, `legId`, `file`. API trả key, MIME, kích thước và SHA-256.
3. Driver lập biên nhận bằng key vừa cấp, ghi chip, hộ chiếu, thể trạng,
   vị trí và `fitToTravel`. Không nhận key tự tạo hay ảnh của case/người khác.
4. Driver xuất phát: kiểm tra lại cả ba cổng, Manager đang active, biên nhận
   mới nhất khớp danh tính, ảnh còn nguyên vẹn, phân công bao phủ thời điểm
   thực tế và plan/revision đang hiệu lực. Retry cùng operationId không ghi
   trùng; operation khác không được khởi hành lại chuyến đã bắt đầu.

API bổ sung:
- `POST .../departure/authorization/revoke`: Manager thu hồi ký duyệt trước
  khởi hành, dùng body giống authorize; có rowversion, idempotency và audit.
- `GET .../departure/horse-receipts/{receiptId}/file`: tải ảnh biên nhận đã ghi,
  kiểm tra quyền case/chủ đơn, không nhận key tùy ý; trả attachment và no-store.

Customer thấy thể trạng và ảnh biên nhận, không thấy ID nhân sự, raw chip/hộ
chiếu, key nội bộ hay lý do đối soát tài chính. Trạng thái sẵn sàng xuất phát
luôn false với chuyến đã khởi hành, đơn kết thúc/hủy, ảnh thiếu/hỏng hoặc phân
công tài xế đã hết hiệu lực.

Gate vận hành dùng lại policy F10 và nguồn F09: lịch/buffer/bảo trì/chứng từ
xe/chuồng, chính sách fit hiện hành theo hãng bay, nguồn km/vé, cách ly và
bảo hiểm đúng plan/revision, hạn giao hàng và lịch ngựa. Các lần đánh giá giữ
khóa tài nguyên trong transaction của case. F08 cần bật cấu hình
`be2.compliance.api-enabled=true`; thiếu evaluator trả blocker, không tự PASS.

Lưu ảnh ở thư mục riêng, cấu hình `equitransit.departure.storage-root` trên
volume bền vững chỉ tài khoản ứng dụng được ghi. Key không chứa đường dẫn
client. Metadata scope/hash và bytes nằm trong một file immutable; restart
không cần cache trong RAM. Không công khai thư mục này qua static hosting.

Migration chạy theo thứ tự 009 → 010 → 011. 009 giữ schema SQL gốc, 010 thêm
index/guard ban đầu, 011 vá guard: chỉ xét chuyển sang khởi hành, kiểm tra
plan/revision hiện hành, Manager active, biên nhận mới nhất và phân công đúng
thời gian. Không chặn các update tracking F12 sau khởi hành. Trigger là lớp
bảo vệ bổ sung; tính gate sống và xác minh ảnh vẫn thuộc application service.

Kiểm thử gồm unit/service, MVC upload/download và phân quyền, lưu ảnh qua
restart, chống đổi scope/tamper, JDBC H2 cho mutation và rollback khi lỗi
cập nhật cuối/audit. Các kiểm tra H2 không chứng minh locking/trigger SQL Server.
Theo phạm vi đã thống nhất, chưa chạy database thật. Native suite vẫn opt-in
với `EQ_DEPARTURE_TEST_ENABLED=true` và chỉ chấp nhận catalog test riêng;
kiểm thử SQL Server được giữ cho đợt tích hợp toàn backend.

## Định danh theo revision

Chip/hộ chiếu dùng để nhận ngựa lấy từ snapshot của revision hiện hành.
Snapshot phải thuộc đúng order và horse, có đủ hai định danh. Hồ sơ master
và chip tại order detail phải khớp định danh đã chốt; dữ liệu master không
được dùng để thay thế hoặc lấp thiếu snapshot. Sai khác tạo blocker
`HORSE_IDENTITY_REVISION_MISMATCH`: không ký duyệt, không ACCEPTED biên nhận
và không xuất phát. Biên nhận sai/thiếu định danh có thể được lưu REJECTED để
giữ lịch sử. Khi đổi định danh, cần cập nhật revision qua luồng đơn hàng và
đánh giá lại hồ sơ/kế hoạch; F11 không tự sửa snapshot, legal PASS hay booking.
