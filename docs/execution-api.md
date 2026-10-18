# F12 — Thực Hiện Chặng & Giám Sát Phúc Lợi Ngựa (Leg Execution & Welfare)

## Overview
Feature 12 (FR-12 / Flow F12: Leg Execution & Horse Welfare Tracking) thuộc phạm
vi của BE2, phụ trách giám sát thời gian thực tiến độ di chuyển của ngựa qua các
chặng đa phương thức (`ROAD` và `AIR`) cùng quản lý chuỗi nhật ký phúc lợi động
vật bất biến (Append-Only Horse Welfare Audit Trail) theo chuẩn FEI.

## Database Migration
- `database/migrations/012_leg_execution_horse_welfare.sql`:
  - Bảng `dbo.LegMilestoneLog`: Lưu trữ các mốc tiến độ chặng, người ghi nhận,
    tọa độ GPS, đối tác tham chiếu, thời gian thực tế `OccurredAt` và thời gian hệ
    thống `RecordedAt`.
  - Bảng `dbo.HorseWelfareLog`: Lưu trữ các chỉ số sinh tồn (thân nhiệt, nước, thức
    ăn, hành vi, hình ảnh), khóa ngoại đính chính cùng chặng
    `FK_Figma_HorseWelfareLog_SupersedesLogID (SupersedesLogID, LegID)`, ràng buộc
    không tự đè `CK_Figma_HorseWelfareLog_Supersedes`, và ràng buộc dinh dưỡng
    không âm.

## Endpoints

### Driver Execution (`/api/driver/legs/{legId}`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/driver/legs/{legId}/milestones` | Assigned Active `DRIVER` | Ghi nhận mốc sự kiện đường bộ kèm GPS bắt buộc và kiểm tra trình tự logic. |
| `POST` | `/api/driver/legs/{legId}/welfare` | Assigned Active `DRIVER` | Ghi nhận chỉ số sinh tồn và thể trạng ngựa trên chặng đường bộ (Append-Only). |
| `POST` | `/api/driver/legs/{legId}/welfare/{logId}/supersede` | Assigned Active `DRIVER` | Ghi nhận bản ghi đính chính liên kết cùng chặng (`SupersedesLogID`). |
| `GET` | `/api/driver/legs/{legId}/milestones` | Assigned Active `DRIVER`, `COORDINATOR`, `MANAGER` | Lấy danh sách toàn bộ mốc sự kiện trên chặng. |
| `GET` | `/api/driver/legs/{legId}/welfare` | Assigned Active `DRIVER`, `COORDINATOR`, `MANAGER` | Lấy danh sách các bản ghi phúc lợi đang hiệu lực trên chặng. |

### Coordinator Milestone & Audit (`/api/coordinator/legs/{legId}`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/coordinator/legs/{legId}/air-milestones` | `COORDINATOR`, `MANAGER` | Ghi nhận mốc sự kiện hàng không kèm nguồn đối tác (`SourceCode`, `SourceReference`). Cấm GPS trên khoang bay. |
| `PUT` | `/api/coordinator/legs/{legId}/estimated-arrival` | `COORDINATOR`, `MANAGER`, Assigned `DRIVER` | Cập nhật thời gian dự kiến đến (`EstimatedArrivalAt`) và lý do chậm trễ (`DelayReason`). |
| `GET` | `/api/coordinator/legs/{legId}/milestones` | `COORDINATOR`, `MANAGER` | Lấy danh sách toàn bộ mốc sự kiện trên chặng. |
| `GET` | `/api/coordinator/legs/{legId}/welfare` | `COORDINATOR`, `MANAGER` | Lấy danh sách các bản ghi phúc lợi đang hiệu lực trên chặng. |
| `GET` | `/api/coordinator/legs/{legId}/welfare/{logId}/audit` | `COORDINATOR`, `MANAGER` | Truy xuất toàn bộ chuỗi lịch sử đính chính bất biến của một bản ghi phúc lợi. |
| `POST` | `/api/coordinator/legs/{legId}/welfare` | `COORDINATOR`, `MANAGER` | Ghi nhận kiểm tra phúc lợi thay mặt groom/bác sĩ thú y trên chuyến bay. |
| `POST` | `/api/coordinator/legs/{legId}/welfare/{logId}/supersede` | `COORDINATOR`, `MANAGER` | Ghi nhận bản ghi đính chính liên kết cùng chặng. |

### Customer Journey Tracking (`/api/transport-orders/{orderId}/tracking`)
| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/transport-orders/{orderId}/tracking` | `CUSTOMER` (chủ đơn hàng), `STAFF`, `MANAGER` | Dòng thời gian chi tiết hành trình đã khử thông tin nội bộ (Redacted Timeline). |
| `GET` | `/api/customer/transport-orders/{orderId}/tracking` | `CUSTOMER` (chủ đơn hàng) | Dòng thời gian chi tiết hành trình cho khách hàng. |

## Error Codes
| Code | HTTP Status | Description |
| :--- | :--- | :--- |
| Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `INVALID_MILESTONE_PROGRESSION` | 409 Conflict | Mốc sự kiện không tuân thủ trình tự logic nghiệp vụ hoặc thời gian không đơn điệu tăng. |
| `PREVIOUS_LEG_INCOMPLETE` | 409 Conflict | Chặng trước trong hành trình chưa đạt trạng thái `COMPLETED`. |
| `INVALID_TRANSPORT_MODE` | 400 Bad Request | Gọi nhầm API đường bộ cho chặng hàng không hoặc ngược lại. |
| `FUTURE_TIMESTAMP_NOT_PERMITTED` | 400 Bad Request | Thời điểm xảy ra sự kiện `OccurredAt` hoặc `CheckTime` lớn hơn hiện tại (+5 phút dung sai). |
| `INVALID_ESTIMATED_ARRIVAL` | 400 Bad Request | Thời gian dự kiến đến `EstimatedArrivalAt` nằm trong quá khứ. |
| `SUPERSEDED_LOG_DIFFERENT_LEG` | 400 Bad Request | Bản ghi cũ cần đính chính không thuộc cùng một chặng (`LegID`). |
| `SELF_SUPERSEDING_NOT_PERMITTED` | 400 Bad Request | Bản ghi đính chính không thể trỏ tới chính nó. |
| `ORDER_NOT_FOUND` / `LEG_NOT_FOUND` / `WELFARE_LOG_NOT_FOUND` | 404 Not Found | Không tìm thấy đơn hàng, chặng, hoặc nhật ký phúc lợi tương ứng. |
| `ACCESS_DENIED` | 403 Forbidden | Nhân viên không được phân công hoặc khách hàng không sở hữu đơn hàng. |
