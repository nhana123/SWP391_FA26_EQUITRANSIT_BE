# F13 — Quản Lý Sự Cố Khẩn Cấp & Điều Chỉnh Vận Hành Có Kiểm Soát (Incidents & Controlled Operational Adjustments)

## Overview
Feature 13 (FR-13 / Flow F13: Incidents & Controlled Operational Adjustments) thuộc phạm vi của BE2, quản trị toàn diện chu trình xử lý biến cố vận hành và bảo vệ tính toàn vẹn thương mại của EquiTransit:
1. **Quản trị vòng đời sự cố hiện trường (Incident Lifecycle):** Tiếp nhận báo cáo sự cố từ tài xế (`DRIVER`) và điều phối (`COORDINATOR`), quản lý phân cấp độ nghiêm trọng (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), và kích hoạt quy trình cấp cứu phúc lợi ngựa không rào cản chi phí (Welfare Emergency Rule).
2. **Động cơ điều chỉnh vận hành (Controlled Operational Adjustment Engine):** Lập phương án điều chỉnh kế hoạch vận chuyển (`OperationalAdjustment`) phát sinh từ sự cố hiện trường hoặc từ yêu cầu thay đổi sau xuất phát của khách hàng (`OrderChangeRequest` từ F03).
3. **Ranh giới đồng thuận thương mại (Commercial Consent Boundary):** Đảm bảo chi phí phát sinh (`AdditionalCostEstimate > 0`) bắt buộc phải có sự chấp thuận (`ACCEPTED`) rõ ràng từ khách hàng trước khi cấp quản lý (`MANAGER`) được phép phê duyệt áp dụng.
4. **Tiến hóa kế hoạch vận hành (Plan Evolution - BR-11):** Tự động sinh phiên bản kế hoạch mới (`VersionNo + 1`), nhân bản các chặng hành trình, cập nhật `TransportOrder.CurrentPlanID`, lưu vết lịch sử phiên bản (`SUPERSEDED`), và hoàn tất yêu cầu thay đổi (`APPLIED`).

## Database Migration
- `database/migrations/013_incidents_controlled_operational_adjustments.sql`:
  - Bảng `dbo.IncidentReport`: Lưu vết sự cố hiện trường, liên kết `LegID`, người báo cáo, phân loại loại hình sự cố (`IncidentType`), mức độ nghiêm trọng (`SeverityLevel`), ghi chú cấp cứu tức thì (`ImmediateActionNotes`), quy trình tiếp nhận (`AcknowledgedBy`, `AcknowledgedAt`) và hoàn tất (`ResolvedBy`, `ResolvedAt`, `ResolutionNotes`), ràng buộc chuẩn JSON danh mục chứng từ bằng chứng `EvidenceFilesJSON` (`CK_Figma_IncidentReport_EvidenceFilesJSON`).
  - Bảng `dbo.OperationalAdjustment`: Lưu trữ các đề xuất thay đổi kế hoạch, liên kết `PlanID`, `IncidentID`, `ChangeRequestID`, người đề xuất, quản lý phê duyệt, nội dung thay đổi chuẩn JSON `ProposedChanges` (`CK_OperationalAdjustment_ProposedChanges_JSON`), dự toán chi phí không âm `AdditionalCostEstimate` (`CK_OperationalAdjustment_AdditionalCostEstimate`), trạng thái phê duyệt (`ApprovalStatus`), và quyết định thương mại định kiểu của khách hàng `CustomerDecision` (`CK_Figma_OperationalAdjustment_CustomerDecision`).

## Endpoints

### Driver Incident Reporting (`/api/driver/legs/{legId}`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/driver/legs/{legId}/incidents` | Assigned Active `DRIVER` | Báo cáo sự cố khẩn cấp trên chặng đường bộ đang phụ trách. |

### Coordinator Incident Management (`/api/coordinator`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/coordinator/legs/{legId}/incidents` | `COORDINATOR`, `MANAGER` | Báo cáo sự cố vận hành hoặc sự cố chặng bay bởi điều phối viên. |
| `GET` | `/api/coordinator/legs/{legId}/incidents` | `COORDINATOR`, `MANAGER` | Lấy danh sách toàn bộ sự cố đã ghi nhận trên chặng. |
| `GET` | `/api/coordinator/incidents/{incidentId}` | `COORDINATOR`, `MANAGER` | Lấy chi tiết hồ sơ sự cố theo định danh `IncidentID`. |
| `PUT` | `/api/coordinator/incidents/{incidentId}/acknowledge` | `COORDINATOR`, `MANAGER` | Tiếp nhận và xác nhận đang điều phối giải quyết sự cố (`ACKNOWLEDGED`). |
| `PUT` | `/api/coordinator/incidents/{incidentId}/resolve` | `COORDINATOR`, `MANAGER` | Đóng sự cố và hoàn tất biên bản giải quyết (`RESOLVED`, bắt buộc có `ResolutionNotes`). |

### Operational Adjustment Engine (`/api/coordinator`, `/api/manager`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/coordinator/plans/{planId}/adjustments` | `COORDINATOR`, `MANAGER` | Lập đề xuất điều chỉnh kế hoạch vận hành (liên kết `IncidentID` hoặc `ChangeRequestID`). |
| `GET` | `/api/coordinator/plans/{planId}/adjustments` | `COORDINATOR`, `MANAGER` | Danh sách các đề xuất điều chỉnh gắn với một kế hoạch vận chuyển. |
| `GET` | `/api/coordinator/adjustments/{adjustmentId}` | `COORDINATOR`, `MANAGER` | Xem chi tiết đề xuất điều chỉnh đầy đủ danh tính nhân viên và chi phí dự toán. |
| `PUT` | `/api/manager/adjustments/{adjustmentId}/approve` | `MANAGER` | Quản lý phê duyệt và kích hoạt cơ chế tiến hóa kế hoạch (Plan Evolution BR-11). Bắt buộc khách hàng đã `ACCEPTED` nếu có phát sinh chi phí. |
| `PUT` | `/api/manager/adjustments/{adjustmentId}/reject` | `MANAGER` | Quản lý bác bỏ đề xuất điều chỉnh kế hoạch (`REJECTED`). |

### Customer Commercial Consent Boundary (`/api/customer/transport-orders/{orderId}/adjustments`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/customer/transport-orders/{orderId}/adjustments` | `CUSTOMER` (Chủ đơn hàng) | Xem danh sách các đề xuất điều chỉnh của đơn hàng đã khử dữ liệu nhạy cảm nội bộ (Redacted View). |
| `GET` | `/api/customer/transport-orders/{orderId}/adjustments/{adjustmentId}` | `CUSTOMER` (Chủ đơn hàng) | Xem chi tiết tóm tắt điều chỉnh và phụ phí phát sinh gửi tới khách hàng. |
| `PUT` | `/api/customer/transport-orders/{orderId}/adjustments/{adjustmentId}/decision` | `CUSTOMER` (Chủ đơn hàng) | Khách hàng thực hiện quyền quyết định thương mại (`ACCEPTED` hoặc `REJECTED`) đối với khoản chi phí phát sinh. |

## Error Codes
| Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `CUSTOMER_CONSENT_REQUIRED` | 409 Conflict | Quản lý cố tình duyệt kế hoạch phát sinh phụ phí khi khách hàng chưa chấp thuận (`ACCEPTED`). |
| `ADJUSTMENT_NOT_PENDING_CONSENT` | 409 Conflict | Khách hàng phản hồi trên đề xuất điều chỉnh không nằm ở trạng thái chờ duyệt thương mại (`PENDING`). |
| `ADJUSTMENT_ALREADY_DECIDED` | 409 Conflict | Đề xuất điều chỉnh đã được xử lý chung cuộc trước đó, không thể duyệt lại hoặc bác bỏ lại. |
| `INVALID_CUSTOMER_DECISION` | 400 Bad Request | Quyết định thương mại của khách hàng không hợp lệ (phải là `ACCEPTED` hoặc `REJECTED`). |
| `INVALID_PROPOSED_CHANGES` | 400 Bad Request | Cấu trúc nội dung đề xuất `ProposedChanges` bị để trống. |
| `INVALID_JSON` | 400 Bad Request | Cấu trúc nội dung `ProposedChanges` hoặc `EvidenceFilesJSON` không đúng định dạng chuẩn JSON. |
| `NEGATIVE_COST_ESTIMATE` | 400 Bad Request | Chi phí phát sinh ước tính nhỏ hơn 0 (vi phạm ràng buộc `CK_OperationalAdjustment_AdditionalCostEstimate`). |
| `RESOLUTION_NOTES_REQUIRED` | 400 Bad Request | Đóng giải quyết sự cố (`RESOLVED`) nhưng không cung cấp biên bản ghi chú giải pháp. |
| `FUTURE_TIMESTAMP_NOT_PERMITTED` | 400 Bad Request | Thời điểm xảy ra sự cố `IncidentTime` nằm ở tương lai (+5 phút dung sai lệch clock). |
| `INVALID_INCIDENT_STATUS_TRANSITION` | 409 Conflict | Chuyển đổi trạng thái sự cố trái quy trình vòng đời (ví dụ: giải quyết sự cố đã bị đóng). |
| `ORDER_NOT_FOUND` / `PLAN_NOT_FOUND` / `LEG_NOT_FOUND` / `INCIDENT_NOT_FOUND` / `ADJUSTMENT_NOT_FOUND` | 404 Not Found | Không tìm thấy thực thể tương ứng theo ID yêu cầu. |
| `ACCESS_DENIED` | 403 Forbidden | Nhân viên không được phân công chặng, người dùng không có vai trò phù hợp, hoặc khách hàng không sở hữu đơn hàng. |
