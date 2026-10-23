# EQUITRANSIT BACKEND HANDOFF CHO FRONTEND TEAM (BE-01 / BE-02 / BE-03)

> **Tài liệu bàn giao kỹ thuật từ:** Principal Backend Tech Lead
> **Dự án:** Hệ thống Quản trị Vận chuyển Ngựa Đua Xuyên Biên Giới (EquiTransit)
> **Backend Base URL (Local Demo):** `http://127.0.0.1:8080`
> **Xác thực chung:** Tất cả các endpoint yêu cầu Header `Authorization: Bearer <accessToken>`.
> **Quy tắc phiên bản & Idempotency:**
> - `recordVersion`: 16 ký tự Hex đại diện cho SQL rowversion hiện tại của `TransportOrder`. FE bắt buộc phải đọc lại sau mỗi mutation.
> - `operationId`: Chuỗi UUIDv4 duy nhất cho mỗi thao tác người dùng. Khi retry request mạng bị lỗi, FE gửi lại **cùng `operationId`** với body y hệt để nhận lại kết quả cũ nguyên vẹn (chống duplicate mutation).

---

## 1. DANH SÁCH API ENDPOINTS MỚI & ĐƯỢC CẬP NHẬT

| Nghiệp vụ | HTTP Method | URL Path | Vai trò được phép (@PreAuthorize / SecurityConfig) | Mô tả & Lưu ý |
|---|---|---|---|---|
| **Resource Catalog** | `GET` | `/api/manager/resources/suppliers` | `MANAGER`, `COORDINATOR` | Lấy danh sách hãng hàng không, nhà cung cấp dịch vụ để Coordinator chọn khi lập lộ trình. |
| **Resource Catalog** | `GET` | `/api/manager/resources/**` | `MANAGER`, `COORDINATOR` | Lấy danh mục xe, chuồng (`/vehicles`, `/stalls`, `/fit-policies`). Quyền ghi (`POST`/`PUT`) vẫn chỉ dành riêng cho `MANAGER`. |
| **Lộ trình Draft** | `POST` | `/api/transport-orders/{orderId}/plans` | `MANAGER`, `COORDINATOR` (được gán vào ca) | Lưu/cập nhật kế hoạch vận chuyển dạng danh sách chặng (`PlanDraftRequest`). Hỗ trợ nhập `estimatedAirfare`. |
| **Journey Studio Graph Draft** | `POST` | `/api/transport-orders/{orderId}/plans/graph-drafts` | `MANAGER`, `COORDINATOR` (được gán vào ca) | Lưu/cập nhật sơ đồ lộ trình đồ thị trực quan (`GraphDraftRequest`). Chặng bay hỗ trợ `estimatedAirfare`. |
| **Compliance F08 Checklist** | `GET` | `/api/transport-orders/{orderId}/compliance?phase=PRE_EXPORT` | `CUSTOMER` (sở hữu đơn), `SPECIALIST` (được gán) | Tự động khởi tạo (bootstrap) toàn bộ 3 checklist (`PRE_EXPORT`, `ARRIVAL`, `QUARANTINE_RELEASE`), nạp default rule và đồng bộ nước quá cảnh từ F10. |
| **Compliance F08 Nộp 1-chạm** | `POST` | `/api/transport-orders/{orderId}/compliance/requirements/{requirementId}/upload-and-submit` | `CUSTOMER` (sở hữu đơn), `SPECIALIST` (được gán) | **API 1-chạm:** Upload file Base64 + tạo DocumentVersion + submit hồ sơ chứng từ trong 1 transaction duy nhất. |
| **Handover F14 Upload File** | `POST` | `/api/transport-orders/{orderId}/handover-files` | `DRIVER` (được gán chặng), `COORDINATOR`, `MANAGER` | Upload file chữ ký khách nhận (`HANDOVER_SIGNATURE`), ảnh bàn giao (`HANDOVER_PHOTO`) hoặc hóa đơn thực tế (`ACTUAL_COST_EVIDENCE`). |
| **Handover F14 Get File** | `GET` | `/api/transport-orders/{orderId}/handover-files/{fileKey}` | `CUSTOMER` (sở hữu đơn), `DRIVER`, `COORDINATOR`, `MANAGER` | Tải về file bằng `fileKey` (dạng JSON Base64). Customer không được xem file loại `ACTUAL_COST_EVIDENCE` (trả về 404). |
| **Chốt đóng đơn (Close Order)** | `POST` | `/api/transport-orders/{orderId}/handovers/close-order` | `MANAGER` | Chuyển trạng thái đơn từ `DELIVERED` sang `CLOSED`. Ghi `AuditEvent` nguyên tử. |

---

## 2. CẤU TRÚC JSON DTO (REQUEST & RESPONSE)

### 2.1. Giá vé máy bay trong Kế hoạch Lộ trình (`PlanLegInput`)

Khi tạo/cập nhật lộ trình (`POST /api/transport-orders/{orderId}/plans` hoặc `POST /api/transport-orders/{orderId}/plans/graph-drafts`), FE bổ sung trường **`estimatedAirfare`** vào từng object chặng bay (`transportMode == "AIR"`):

```json
{
  "command": {
    "recordVersion": "000000000001a2b3",
    "operationId": "94d0b4a7-d5be-4cd7-a973-5ae4bb6d1031"
  },
  "revisionId": 201,
  "setAsCurrentDraft": true,
  "legs": [
    {
      "sequenceNo": 1,
      "transportMode": "AIR",
      "startPoint": "SGN",
      "endPoint": "SIN",
      "distanceKm": 1100,
      "distanceSource": "Coordinator route estimate",
      "plannedDeparture": "2026-12-01T06:00:00Z",
      "plannedArrival": "2026-12-01T09:00:00Z",
      "plannedFlightNumber": "VN-1234",
      "estimatedAirfare": 1500.00
    }
  ]
}
```

- **Chi tiết trường `estimatedAirfare`:**
  - **Tên biến:** `estimatedAirfare` (camelCase)
  - **Kiểu dữ liệu:** `BigDecimal` / `number` (JSON number)
  - **Ràng buộc:**
    - Tùy chọn (Optional / Nullable).
    - Chỉ được nhập khi `transportMode == "AIR"`.
    - Số dương (`min = 0.01`), tối đa 2 chữ số thập phân (`digits: integer=14, fraction=2`).
  - **Cơ chế BE:** BE tổng hợp các giá dương vào `dbo.SupplierOffer`, dịch vụ `AIRFARE`, trạng thái `SELECTED`, theo plan/revision hiện hành. F05/F06 tự nạp nguồn này mà FE không cần truyền tay `offerId`. Nếu chưa có giá hợp lệ, blocker vẫn được giữ. Giá ước tính không thay thế xác nhận đặt chỗ hay chấp thuận của hãng bay.

- **Graph request:** `GraphDraftRequest` gồm `draft`, `checkpointNodes`, `wires`, `layout`. Trường giá nằm tại `draft.legs[i].estimatedAirfare`; request danh sách chặng dùng `legs[i].estimatedAirfare`.
- **Response:** API `/plans` trả `PlanDetailResponse` (`planId`, `recordVersion`, `legs[].legId`). API `/plans/graph-drafts` trả `JourneyGraphView` (`planId`, `recordVersion`, `checkpointNodes`, `wires`, `layout`). Không có trường giá mới bắt buộc trong response.

---

### 2.2. Nộp giấy tờ 1-chạm F08 (`ComplianceUploadSubmitRequest`)

Endpoint: `POST /api/transport-orders/{orderId}/compliance/requirements/{requirementId}/upload-and-submit`
*(Flat payload - **KHÔNG** bọc trong object `command`)*

#### Request Body (`ComplianceUploadSubmitRequest`):
```json
{
  "fileName": "export_health_cert.pdf",
  "contentType": "application/pdf",
  "contentBase64": "JVBERi0xLjQKJ...",
  "expiresAt": "2027-03-01T00:00:00Z",
  "referenceNumber": "CERT-2027-VN01",
  "recordVersion": "000000000001a2b3",
  "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814"
}
```

| Field Name | Data Type | Required? | Constraints & Notes |
|---|---|:---:|---|
| `fileName` | `string` | **Bắt buộc** | Tối đa 255 ký tự, tên file gốc (ví dụ: `cert.pdf`). |
| `contentType` | `string` | **Bắt buộc** | MIME type hợp lệ: `application/pdf`, `image/png`, `image/jpeg`. |
| `contentBase64` | `string` | **Bắt buộc** | Chuỗi Base64 nguyên chất, dung lượng tối đa 5MB. **LƯU Ý:** Không chứa tiền tố Data URL (Cắt bỏ phần `data:...;base64,` trước khi gửi). |
| `expiresAt` | `string` (ISO-8601) | **Bắt buộc** | Thời điểm hết hạn của chứng từ (phải ở tương lai). Định dạng UTC: `YYYY-MM-DDTHH:mm:ssZ`. |
| `referenceNumber` | `string` | *Tùy chọn* | Số hiệu giấy chứng nhận/giấy phép (tối đa 50 ký tự). Có thể bỏ qua hoặc để `null`. |
| `recordVersion` | `string` | **Bắt buộc** | 16 ký tự Hex lấy từ trường `recordVersion` hiện tại của đơn hàng. |
| `operationId` | `string` (UUIDv4) | **Bắt buộc** | Mã UUID duy nhất cho lần submit này. |

#### Response Body (`ComplianceReceipt` - HTTP 200 OK):
```json
{
  "entityId": 501,
  "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814",
  "recordVersion": "000000000001a2b4",
  "fileKey": "84032724-eac7-45df-8d04-e54f092279d0"
}
```
> **FE cần làm sau khi nhận Response:** Cập nhật lại `recordVersion` của đơn hàng bằng giá trị trả về trong response.

`entityId` là ID submission vừa tạo; không gọi thêm bước submit riêng sau khi API này thành công. GET compliance trả trạng thái, `decision`, `feedback` và `attachment` mới nhất của từng requirement. Attachment gồm `fileKey`, `fileName`, `contentType`, `fileSizeBytes`, `referenceNumber`, `expiresAt`. File F08 đọc qua API `/compliance/files`, không qua API file F14.

GET compliance mặc định bootstrap các checklist còn thiếu cho đơn đang `APPROVED`, `IN_PREPARATION`, `CONFIRMED` hoặc `IN_TRANSIT`, có Specialist đang phụ trách. `bootstrap=false` giữ chế độ chỉ đọc. Khi tất cả giấy tờ bắt buộc đã được duyệt và còn hiệu lực, lệnh Clear Phase tạo procedure mặc định còn thiếu; procedure đã bị từ chối/thu hồi hoặc đang chờ vẫn chặn thông quan.

---

### 2.3. Upload & Đọc File Bàn giao F14 (`HandoverFileUploadRequest`)

Endpoint Upload: `POST /api/transport-orders/{orderId}/handover-files` (HTTP 201 Created)
*(Flat payload - **KHÔNG** cần `recordVersion` và `operationId`)*

#### Request Body (`HandoverFileUploadRequest`):
```json
{
  "fileName": "receiver-signature.png",
  "contentType": "image/png",
  "purpose": "HANDOVER_SIGNATURE",
  "contentBase64": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```

| Field Name | Data Type | Required? | Allowable Values & Notes |
|---|---|:---:|---|
| `fileName` | `string` | **Bắt buộc** | Tối đa 255 ký tự (ví dụ: `signature.png`, `delivery-photo.jpg`). |
| `contentType` | `string` | **Bắt buộc** | `image/png`, `image/jpeg`, `application/pdf`. |
| `purpose` | `string` (Enum) | **Bắt buộc** | Một trong 3 giá trị: <br>• `HANDOVER_SIGNATURE` (Chữ ký người nhận bàn giao)<br>• `HANDOVER_PHOTO` (Ảnh chụp bàn giao thực tế)<br>• `ACTUAL_COST_EVIDENCE` (Hóa đơn chứng từ chi phí thực tế) |
| `contentBase64` | `string` | **Bắt buộc** | Dữ liệu Base64 không có tiền tố Data URL (tối đa 5MB). |

#### Response Body (`HandoverFileResponse` - HTTP 201 Created hoặc GET 200 OK):
```json
{
  "fileKey": "84032724-eac7-45df-8d04-e54f092279d0",
  "purpose": "HANDOVER_SIGNATURE",
  "contentType": "image/png",
  "fileName": "receiver-signature.png",
  "contentBase64": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```
> **Cách FE sử dụng `fileKey`:**
> - Lấy `fileKey` điền vào trường `receiverSignatureFileKey` hoặc `photoFileKey` khi Driver/Coordinator gọi API xác nhận bàn giao (`/legs/{legId}/final-delivery` hoặc `/legs/{legId}/intermediate-handover`).
> - Khi cần hiển thị ảnh chữ ký/chứng từ, FE gọi `GET /api/transport-orders/{orderId}/handover-files/{fileKey}` và dùng `contentBase64` + `contentType` để tạo ảnh: `data:${res.contentType};base64,${res.contentBase64}`.

Coordinator phải được gán vào đơn; Driver phải được gán chặng của plan hiện hành (kể cả assignment đã `COMPLETED` để hoàn tất bàn giao). Upload mới không có idempotency: retry có thể tạo file mới. `ACTUAL_COST_EVIDENCE` dùng cho `evidenceFileKey` của API chi phí.

Các API final delivery trả `OperationalCompletionReceipt`, không chứa order rowversion. FE cần GET lại hồ sơ đơn của Manager trước khi tạo lệnh `close-order`.

---

### 2.4. Chốt đóng đơn F14 (`OrderClosureController`)

Endpoint: `POST /api/transport-orders/{orderId}/handovers/close-order` (Dành cho `MANAGER`)
*(Flat payload - DTO `QuoteCommand`)*

#### Request Body (`QuoteCommand`):
```json
{
  "recordVersion": "000000000001a2b4",
  "operationId": "22b675c8-f091-45ea-9034-f3934d7b8040"
}
```

#### Response Body (`OrderClosureResponse` - HTTP 200 OK):
```json
{
  "orderId": 101,
  "orderStatus": "CLOSED",
  "recordVersion": "000000000001a2b5",
  "operationId": "22b675c8-f091-45ea-9034-f3934d7b8040",
  "updatedAt": "2026-10-23T08:15:00Z"
}
```

---

## 3. DANH SÁCH MÃ LỖI (ERROR CODES) & TRẠNG THÁI NGHIỆP VỤ (ENUMS)

### 3.1. Các Enum Trạng thái Nghiệp vụ chính xác trên BE

#### Vòng đời Đơn hàng (`OrderStatus`):
`DRAFT` ➔ `SUBMITTED` ➔ `APPROVED` ➔ `IN_PREPARATION` ➔ `CONFIRMED` ➔ `IN_TRANSIT` ➔ `DELIVERED` ➔ `CLOSED`
*(Trạng thái hủy/từ chối: `REJECTED`, `CANCELLED`)*
- `IN_PREPARATION`: Chuyển từ `APPROVED` khi Manager ghi nhận phân bổ thanh toán (`AllocationCommand`). Customer chỉ báo đã chuyển khoản chưa làm đơn đổi trạng thái hoặc được ghi nhận đã thanh toán.
- `DELIVERED`: Thiết lập khi Driver/Coordinator bấm hoàn thành bàn giao chặng cuối (`FinalDelivery`).
- `CLOSED`: Thiết lập khi Manager bấm đóng đơn (`close-order`).

#### Trạng thái Hồ sơ Kiểm dịch F08 (`RequirementStatus`):
- `PENDING`: Chưa có tài liệu nộp lên.
- `SUBMITTED`: Khách/Chuyên viên vừa nộp file, đang chờ Specialist duyệt.
- `APPROVED`: Specialist đã phê duyệt giấy tờ hợp lệ.
- `REJECTED`: Specialist từ chối (có kèm `feedback` / lý do từ chối).
- `EXPIRED`: Giấy tờ đã quá hạn `expiresAt`.
- `REVOKED`: Giấy phép đã bị thu hồi.

#### Giai đoạn Pháp lý (`CompliancePhase`):
- `PRE_EXPORT`: Kiểm dịch & thủ tục xuất cảnh trước chuyến đi.
- `ARRIVAL`: Thủ tục nhập cảnh & hải quan tại sân bay đến.
- `QUARANTINE_RELEASE`: Kiểm dịch sau nhập cảnh & giải phóng cách ly.

---

### 3.2. Bảng Mã Lỗi (HTTP Status + Error Code) để Frontend Bắt Lỗi (Catch & Alert)

Lỗi nghiệp vụ `OrderException` từ `ApiExceptionHandler` trả object `{ "code": "ERROR_CODE", "message": "Chi tiết lỗi" }` với HTTP status tương ứng. FE đọc trường `code` để hiển thị thông báo; lỗi validation hoặc xác thực có thể dùng response riêng.

| HTTP Status | Error String Code | Xảy ra khi nào? | Hướng xử lý gợi ý cho FE |
|:---:|---|---|---|
| `409 CONFLICT` | `QUARANTINE_RELEASE_REQUIRED` | Driver/Coordinator bấm bàn giao cuối (`FinalDelivery`) nhưng Specialist chưa thực hiện Clear Phase `QUARANTINE_RELEASE`. | Hiển thị thông báo: *"Ngựa chưa được giải phóng kiểm dịch cách ly (Quarantine Release). Vui lòng liên hệ Chuyên viên Pháp lý để Clear Phase trước khi bàn giao."* |
| `422 UNPROCESSABLE` | `HANDOVER_TIME_BEFORE_DEPARTURE` | Thời gian bàn giao (`occurredAt` / `handoverTime`) trước thời điểm xe thực tế xuất phát (`actualDeparture`). | Báo lỗi form: *"Thời gian bàn giao không được trước thời gian khởi hành thực tế của chặng."* |
| `409 CONFLICT` | `ORDER_NOT_DELIVERED` | Manager bấm đóng đơn (`close-order`) khi đơn chưa đạt trạng thái `DELIVERED`. | Báo lỗi: *"Chỉ có thể đóng đơn sau khi ngựa đã được giao thành công cho người nhận."* |
| `409 CONFLICT` | `ORDER_VERSION_CONFLICT` | Dữ liệu `recordVersion` gửi lên không khớp với phiên bản hiện tại trong DB (do người khác vừa thao tác hoặc phiên làm việc đã cũ). | Tự động gọi API reload lại trang để lấy `recordVersion` mới nhất và thông báo: *"Dữ liệu đơn hàng vừa được cập nhật, vui lòng thử lại."* |
| `409 CONFLICT` | `CLOSURE_RETRY_CONFLICT` | Gửi lại `operationId` đóng đơn cũ nhưng với `recordVersion` khác. | Cảnh báo: *"Thao tác đóng đơn không hợp lệ, vui lòng làm mới trang."* |
| `409 CONFLICT` | `QUOTE_PRICING_PENDING` | Manager bấm phát hành báo giá (`issueQuote`) nhưng thiếu thông tin chi phí (ví dụ: chưa có chặng, chưa có giá vé máy bay). | Kiểm tra các blocker trả về (`ITINERARY_PENDING`, `AIRFARE_PENDING`, `QUARANTINE_PENDING`) để chỉ đích danh cho Manager biết bộ phận nào chưa hoàn thành. |
| `409 CONFLICT` | `TRANSIT_COUNTRIES_PENDING` | Specialist bấm tạo checklist nhưng lộ trình có sân bay quá cảnh mà không có danh sách nước quá cảnh. | Nhắc Specialist kiểm tra lại điểm dừng quá cảnh trên bản đồ của Coordinator. |
| `503 SERVICE UNAVAILABLE` | `COMPLIANCE_SCHEMA_PENDING` | Backend đang chạy trên cơ sở dữ liệu chưa chạy migration 020 (chưa có cột `ExpiresAt`). | Báo Admin: *"Cơ sở dữ liệu thiếu migration 020. Hãy chạy tools/run-demo-local.ps1 để kết nối đúng database Demo."* |
| `409 CONFLICT` | `FILE_CONTENT_INVALID` / `FILE_SIZE_INVALID` / `FILE_TYPE_INVALID` / `FILE_NAME_INVALID` | File không qua kiểm tra nội dung, kích thước tối đa 5 MiB sau giải mã, MIME hoặc tên file. | Chọn lại file PDF, PNG hoặc JPEG hợp lệ; gửi Base64 không có tiền tố Data URL. Lỗi validation DTO có thể trả 400. |

---
Migration 019 cần cho file F14, migration 020 cần cho hạn chứng từ chính xác. Migration 020 hiện giới hạn chạy trên `EquiTransit_Demo`; không chạy DDL lên DB thật trong các task này. Chi tiết kiểm chứng xem `docs/BE-01.md`, `docs/BE-02.md`, `docs/BE-03.md`.
