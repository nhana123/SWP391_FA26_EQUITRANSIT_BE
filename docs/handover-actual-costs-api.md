# F14 — Bàn Giao Vận Chuyển, Chi Phí Thực Tế & Hoàn Tất Vận Hành (Trip Handover, Actual Costs & Operational Completion)

## Overview
Feature 14 (FR-14 / Flow F14: Trip Handover, Actual Costs & Operational Completion) thuộc phạm vi sở hữu của **BE2**, là điểm kết thúc của chuỗi cung ứng dịch vụ vận tải quốc tế EquiTransit:
1. **Phân cấp loại hình bàn giao (Multimodal Handover Lifecycle):** Phân biệt minh bạch giữa **Bàn giao trung gian** (`INTERMEDIATE_CARRIER`, `INTERMEDIATE_AIRPORT`, `INTERMEDIATE_QUARANTINE`) và **Bàn giao đích cuối cùng** (`FINAL_RECEIVER`).
2. **Cơ chế hoàn tất từng chặng (Leg Completion & Next Leg Activation):** Bàn giao trung gian chỉ hoàn tất chặng tương ứng (`TripLeg.LegStatus = COMPLETED`) và kích hoạt chặng kế tiếp (`CONFIRMED`), giữ nguyên kế hoạch (`ACTIVE`) và đơn hàng (`IN_TRANSIT`).
3. **Cổng kiểm soát giao hàng cuối cùng (Final Delivery Gates):**
   - **Cổng giải phóng kiểm dịch (Quarantine Release Gate):** Bắt buộc chặng kiểm dịch F08 đã đạt chứng nhận giải phóng (`QUARANTINE_RELEASE = PASS`). Nếu chưa đạt, chặn bàn giao với mã lỗi `409 Conflict: QUARANTINE_RELEASE_REQUIRED`.
   - **Đối soát người nhận ủy quyền (Consignee Identity & Proxy Gate):** Người nhận thực tế phải trùng khớp với `TransportOrder.ReceiverName` hoặc có tư cách ủy quyền hợp lệ (`RECEIVER_IDENTITY_MISMATCH`).
   - **Bằng chứng bắt buộc (Mandatory Artifacts):** Chữ ký điện tử của người nhận (`SIGNATURE_REQUIRED`) và ảnh chụp bàn giao tại chuồng (`HANDOVER_PHOTO_REQUIRED`).
4. **Đóng đơn vận hành (Operational Completion):** Khi bàn giao đích thành công, hệ thống đồng thời chuyển `TripLeg.LegStatus = COMPLETED`, `TransportPlan.PlanStatus = COMPLETED`, và `TransportOrder.Status = DELIVERED`.
5. **Xác nhận thực chất từ Khách hàng (Customer Delivery Feedback):** Khách hàng đăng nhập kiểm tra biên bản giao hàng và phản hồi định kiểu (`ACCEPTED`, `EXCEPTION`, hoặc `NOT_DELIVERED` theo ràng buộc `CK_Figma_TripHandover_CustomerDecision`). Tuyệt đối cấm hệ thống tự động xác nhận thay thế khách hàng (No Auto-Confirmation).
6. **Hạch toán chi phí thực tế nội bộ (Actual Cost Tracking):** Cho phép Coordinator ghi nhận chi phí thực tế phát sinh thanh toán cho nhà cung cấp/hãng bay (`ActualCost` với `Amount >= 0` theo `CK_ActualCost_Amount`) kèm ràng buộc khóa ngoại phức hợp `([LegID], [PlanID])` (`FK_Figma_ActualCost_LegID`).
7. **Báo cáo biên lợi nhuận điều hành (Executive Profitability Report):** Cung cấp API riêng cho cấp Quản lý (`MANAGER`) đối soát giữa tổng giá báo khách ban đầu (`TotalQuotedAmount` từ F06) và tổng chi phí thực chi (`TotalActualCost`), tính toán lợi nhuận gộp (`GrossProfit`) và tỷ suất lợi nhuận (`ProfitMarginPercent`).
8. **Bảo mật chi phí gốc (Cost Privacy Isolation):** Toàn bộ chi phí thực tế nội bộ, giá cước đối tác và mã nhân viên bị loại bỏ hoàn toàn khỏi các DTO dành cho khách hàng (`CustomerHandoverResponse`).
9. **Tách biệt Vận hành vs Tài chính:** Hoàn tất vận hành (`DELIVERED`/`COMPLETED`) hoàn toàn độc lập với việc quyết toán tài chính, công nợ hoặc hoàn tiền ở F07 (`OrderStatus` không bị ép buộc đóng tài chính).

## Database Migration
- `database/migrations/014_trip_handover_actual_costs.sql`:
  - Bảng `dbo.TripHandover`: Lưu vết các sự kiện bàn giao chặng và bàn giao đích, liên kết `LegID`, người thực hiện (`HandedOverBy`), người tiếp nhận (`ReceivedBy`, `ReceiverAuthority`), phân loại `HandoverType`, tình trạng thể trạng ngựa `HorseCondition`, chữ ký điện tử `RecipientSignatureFileKey`, ảnh bàn giao `HandoverPhotoFileKey`, kết quả `HandoverOutcome`, phản hồi khách hàng `CustomerDecision` (`CK_Figma_TripHandover_CustomerDecision`).
  - Bảng `dbo.ActualCost`: Lưu chi phí thực tế nội bộ trả đối tác theo từng kế hoạch vận hành (`PlanID`), tùy chọn gán chặng (`LegID`), mã loại chi phí `CostCode`, số tiền không âm `Amount` (`CK_ActualCost_Amount`), tiền tệ `Currency`, hóa đơn chứng từ `EvidenceFileKey`, khóa ngoại phức hợp bảo đảm chặng thuộc kế hoạch (`FK_Figma_ActualCost_LegID`).

## Endpoints

### Driver Handover Operations (`/api/driver/legs/{legId}`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/driver/legs/{legId}/intermediate-handover` | Assigned `DRIVER` | Ghi nhận bàn giao trung gian tại kho/sân bay/kiểm dịch; hoàn tất chặng và kích hoạt chặng tiếp theo. |
| `POST` | `/api/driver/legs/{legId}/final-delivery` | Assigned `DRIVER` | Ghi nhận bàn giao đích cuối cùng cho người nhận ủy quyền; kích hoạt đóng đơn vận hành (`DELIVERED`). |
| `GET` | `/api/driver/legs/{legId}/handovers` | Assigned `DRIVER` | Lấy danh sách toàn bộ biên bản bàn giao đã lập trên chặng. |

### Coordinator Handover Operations (`/api/coordinator`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/coordinator/legs/{legId}/intermediate-handover` | `COORDINATOR`, `MANAGER` | Ghi nhận bàn giao trung gian chặng hàng không hoặc chặng đối tác phụ tá. |
| `POST` | `/api/coordinator/legs/{legId}/final-delivery` | `COORDINATOR`, `MANAGER` | Ghi nhận bàn giao đích cuối cùng thay mặt nhân sự hiện trường. |
| `GET` | `/api/coordinator/legs/{legId}/handovers` | `COORDINATOR`, `MANAGER` | Xem danh sách biên bản bàn giao của chặng. |
| `GET` | `/api/coordinator/handovers/{handoverId}` | `COORDINATOR`, `MANAGER` | Xem chi tiết biên bản bàn giao đầy đủ danh tính nhân viên và chứng từ. |

### Customer Delivery Review & Decision (`/api/customer/transport-orders/{orderId}/handovers`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/customer/transport-orders/{orderId}/handovers` | `CUSTOMER` (Chủ đơn) | Xem danh sách biên bản bàn giao đơn hàng đã khử dữ liệu nội bộ và chi phí nhà cung cấp. |
| `GET` | `/api/customer/transport-orders/{orderId}/handovers/{handoverId}` | `CUSTOMER` (Chủ đơn) | Xem chi tiết biên bản bàn giao (ảnh ngựa, chữ ký, thể trạng). |
| `PUT` | `/api/customer/transport-orders/{orderId}/handovers/{handoverId}/decision` | `CUSTOMER` (Chủ đơn) | Khách hàng gửi phản hồi xác nhận: `ACCEPTED`, `EXCEPTION`, hoặc `NOT_DELIVERED`. |

### Actual Cost Tracking & Profitability (`/api/coordinator`, `/api/manager`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/coordinator/plans/{planId}/actual-costs` | `COORDINATOR`, `MANAGER` | Ghi nhận hóa đơn chi phí thực tế nội bộ đã chi trả đối tác cho kế hoạch/chặng. |
| `GET` | `/api/coordinator/plans/{planId}/actual-costs` | `COORDINATOR`, `MANAGER` | Liệt kê toàn bộ các khoản chi phí thực tế đã hạch toán trên kế hoạch. |
| `GET` | `/api/manager/plans/{planId}/profitability` | `MANAGER` | Báo cáo phân tích biên lợi nhuận điều hành (so sánh giá quote khách hàng F06 với tổng chi phí thực chi F14). |

## Error Codes
| Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `QUARANTINE_RELEASE_REQUIRED` | 409 Conflict | Đích đến chưa đạt chứng nhận giải phóng kiểm dịch (`QUARANTINE_RELEASE = PASS`). Chặn bàn giao giao ngựa. |
| `RECEIVER_IDENTITY_MISMATCH` | 400 Bad Request | Tên người nhận không trùng khớp với người nhận ủy quyền trên đơn hàng và không có giấy ủy quyền. |
| `SIGNATURE_REQUIRED` | 400 Bad Request | Bàn giao đích cuối cùng thiếu chữ ký điện tử của người nhận. |
| `HANDOVER_PHOTO_REQUIRED` | 400 Bad Request | Bàn giao đích cuối cùng thiếu ảnh chụp xác nhận thể trạng ngựa tại chuồng đích. |
| `NOT_FINAL_LEG` | 409 Conflict | Cố tình gọi bàn giao đích cuối cùng trên chặng trung gian chưa phải chặng cuối. |
| `LEG_NOT_IN_TRANSIT` | 409 Conflict | Chặng không ở trạng thái đang vận chuyển (`IN_TRANSIT`) để thực hiện bàn giao. |
| `INVALID_HANDOVER_TYPE` | 400 Bad Request | Loại hình bàn giao không khớp với endpoint gọi. |
| `INVALID_CUSTOMER_DECISION` | 400 Bad Request | Quyết định của khách hàng không nằm trong `ACCEPTED`, `EXCEPTION`, `NOT_DELIVERED`. |
| `HANDOVER_ALREADY_DECIDED` | 409 Conflict | Khách hàng đã gửi phản hồi trước đó, không thể ghi đè quyết định. |
| `NEGATIVE_COST_AMOUNT` | 400 Bad Request | Chi phí thực tế nhỏ hơn 0 (vi phạm `CK_ActualCost_Amount`). |
| `LEG_NOT_IN_PLAN` | 400 Bad Request | Chặng được phân bổ chi phí không thuộc về kế hoạch vận hành tương ứng (`FK_Figma_ActualCost_LegID`). |
| `FUTURE_TIMESTAMP_NOT_PERMITTED` | 400 Bad Request | Thời điểm bàn giao hoặc ngày phát sinh chi phí nằm ở tương lai (+5 phút dung sai lệch clock). |
| `ORDER_NOT_FOUND` / `PLAN_NOT_FOUND` / `LEG_NOT_FOUND` / `HANDOVER_NOT_FOUND` | 404 Not Found | Không tìm thấy thực thể tương ứng theo ID yêu cầu. |
