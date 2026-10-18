# F12 — Đối Chiếu Nghiệp Vụ & Tiêu Chí Chấp Nhận (Acceptance Criteria)

## Ma Trận Nghiệp Vụ & Bằng Chứng Kiểm Thử Tự Động

| Nghiệp Vụ Cốt Lõi | Cơ Chế Thực Thi | Bằng Chứng Tự Động |
| :--- | :--- | :--- |
| **1. Tiến độ chặng đường bộ (ROAD)** | Driver được phân công gửi mốc sự kiện; bắt buộc có GPS; kiểm tra chuỗi trạng thái (`DEPARTED` -> `REST_STOP` -> `CHECKPOINT` -> `ARRIVED`). | `MilestoneSequenceValidatorTest`, `LegExecutionCompletionTest.roadLegDriverProgressionAndCompletionSuccess` |
| **2. Tiến độ chặng hàng không (AIR)** | Coordinator ghi nhận mốc bay; bắt buộc có nguồn đối tác (`SourceCode`, `SourceReference`); kiểm tra chuỗi trạng thái (`CARGO_TENDERED` -> `WHEELS_UP` -> `WHEELS_DOWN` -> `CUSTOMS_CLEARED_IN` -> `HANDOVER_READY`). | `MilestoneSequenceValidatorTest`, `LegExecutionCompletionTest.airLegCoordinatorProgressionSuccess` |
| **3. Cấm GPS giả lập trên không** | Chặn toàn bộ tọa độ GPS gửi kèm các mốc bay trên khoang hàng (`WHEELS_UP`, `WHEELS_DOWN`, `AIRCRAFT_LOADED`). | `MilestoneSequenceValidatorTest.rejectAirMilestoneWithForbiddenGps` |
| **4. Ngăn chặn timestamp tương lai** | Chặn các sự kiện có `OccurredAt` hoặc `CheckTime` lớn hơn thời gian hiện tại (+5 phút dung sai lệch clock). | `MilestoneSequenceValidatorTest.rejectMilestoneWithFutureOccurredAt` |
| **5. Phúc lợi ngựa bất biến (FEI)** | Bảng `HorseWelfareLog` hoàn toàn Append-Only. Ghi nhận thân nhiệt, lượng nước, thức ăn, hành vi. Tự động gắn cờ sốt (`Fever`) và cảnh báo khẩn cấp (`EmergencyAlert`). | `LegExecutionCompletionTest.welfareCheckRecordsVitalsAndFlagsFever`, `LegMilestoneLogRepositoryTest` |
| **6. Đính chính cùng chặng (Supersedes)** | Bản ghi mới liên kết `SupersedesLogID` bắt buộc thuộc cùng `LegID`. Ngăn chặn đính chính chéo chặng hoặc tự đè chính mình. | `LegExecutionCompletionTest.supersedingWelfareCorrectionPreservesAuditTrail`, `rejectSupersedingDifferentLeg` |
| **7. Bảo mật dòng thời gian khách hàng** | API timeline cho khách hàng tự động loại bỏ ghi chú nội bộ (`notes`), chi phí, đối tác và mã tham chiếu. | `ApiDtoContractTest.executionSchemasMatchTheFrontendContract`, `CustomerTrackingServiceTest` |
| **8. Bảo mật HTTP & RBAC đa tầng** | Toàn bộ 14 endpoint F12 yêu cầu Bearer token; phân quyền chặt chẽ giữa `DRIVER`, `COORDINATOR`, `CUSTOMER`. | `ExecutionHttpAuthorizationTest`, `OpenApiTest.allExecutionOperationsRequireAuthentication` |

## Tiêu Chí Chấp Nhận Chi Tiết (Detailed Acceptance Scenarios)

### Kịch Bản 1: Tiến độ chặng đường bộ và hoàn tất chặng
1. Driver được phân công bắt đầu hành trình bằng mốc `DEPARTED`.
2. Driver gửi các mốc dừng nghỉ `REST_STOP` và qua trạm kiểm soát `CHECKPOINT`.
3. Khi đến đích, Driver gửi mốc `ARRIVED`. Hệ thống tự động cập nhật `ActualArrival` trên `TripLeg` và chuyển `LegStatus = 'COMPLETED'`.
4. Không cho phép gửi trùng lặp mốc `DEPARTED` hoặc gửi các mốc sau khi chặng đã `ARRIVED`.

### Kịch Bản 2: Tiến độ chặng hàng không với đối tác vận tải
1. Coordinator phụ trách gửi mốc `CARGO_TENDERED` kèm mã vận đơn `AWB-618-91234567` và đối tác `CARRIER_API`.
2. Chặn các request gửi kèm GPS khi máy bay đang trên không (`WHEELS_UP`, `WHEELS_DOWN`).
3. Chuỗi mốc kết thúc bằng `HANDOVER_READY`. `TripLeg` được cập nhật `ActualArrival` và chuyển `LegStatus = 'COMPLETED'`.

### Kịch Bản 3: Ghi nhận phúc lợi ngựa và đính chính bất biến
1. Driver/Groom đo thân nhiệt 39.2°C (vượt ngưỡng bình thường 37.0°C - 38.5°C). Hệ thống tự động kích hoạt `fever = true` và `emergencyAlert = true` để báo hiệu F13.
2. Khi phát hiện nhiệt kế bị sai số, nhân viên tạo bản ghi mới với thân nhiệt đo lại 37.8°C trỏ `supersedesLogId` tới bản ghi ban đầu.
3. Hệ thống lưu bản ghi mới với ID mới, giữ nguyên bản ghi cũ trong database. Lịch sử đính chính (Audit Trail) hiển thị cả 2 bản ghi với bản ghi mới là phiên bản hiện hành.

### Kịch Bản 4: Khử dữ liệu nhạy cảm trên Dòng thời gian khách hàng
1. Khách hàng xem tiến độ chặng qua `/api/transport-orders/{orderId}/tracking`.
2. Response chỉ trả về: loại mốc, địa điểm công khai, thời gian xảy ra, trạng thái sức khỏe tóm tắt và ảnh đính kèm.
3. Toàn bộ `notes` nội bộ của tài xế, lý do chậm trễ kỹ thuật và thông tin chi phí không xuất hiện trong payload trả về cho khách hàng.
