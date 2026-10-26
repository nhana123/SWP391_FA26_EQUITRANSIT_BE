# F14 — Đối Chiếu Nghiệp Vụ & Tiêu Chí Chấp Nhận (Acceptance Criteria)

## Ma Trận Nghiệp Vụ & Bằng Chứng Kiểm Thử Tự Động

| Nghiệp Vụ Cốt Lõi | Cơ Chế Thực Thi | Bằng Chứng Tự Động |
| :--- | :--- | :--- |
| **1. Bàn giao trung gian (Intermediate Handover)** | Tài xế hoặc điều phối viên ghi nhận chuyển giao tại kho hàng/sân bay/kiểm dịch; chỉ hoàn tất chặng tương ứng (`COMPLETED`) và kích hoạt chặng tiếp theo (`CONFIRMED`). Không được phép đóng kế hoạch hoặc đơn hàng (`IN_TRANSIT`). | `TripHandoverCompletionTest.driverCanRecordIntermediateHandoverOnAssignedLeg`, `TripHandoverCompletionTest.coordinatorCanRecordIntermediateHandover` |
| **2. Bàn giao đích & Cổng giải phóng kiểm dịch (F08 Gate)** | Bàn giao đích cuối cùng (`FINAL_RECEIVER`) bắt buộc phải kiểm tra thông quan giải phóng kiểm dịch (`QUARANTINE_RELEASE = PASS`). Nếu chưa đạt kiểm dịch, chặn bàn giao với lỗi `409 Conflict: QUARANTINE_RELEASE_REQUIRED`. | `TripHandoverCompletionTest.finalDeliveryRejectsIfQuarantineReleaseNotPassed` |
| **3. Cổng đối soát người nhận ủy quyền (Consignee Gate)** | Tên người nhận thực tế phải khớp với `ReceiverName` trên đơn hàng hoặc có chức vụ ủy quyền hợp lệ (`STABLE_MANAGER`, `TRAINER`, `VETERINARIAN`). Nếu sai người nhận và không có giấy ủy quyền, trả về `400 Bad Request: RECEIVER_IDENTITY_MISMATCH`. | `TripHandoverCompletionTest.finalDeliveryRejectsMismatchReceiverWithoutProxyAuthority`, `TripHandoverCompletionTest.finalDeliveryAllowedWhenProxyAuthorityRecognized` |
| **4. Bằng chứng bàn giao bắt buộc (Artifacts Gate)** | Bàn giao đích bắt buộc phải có chữ ký điện tử (`SIGNATURE_REQUIRED`) và ảnh chụp xác nhận ngựa an toàn tại chuồng (`HANDOVER_PHOTO_REQUIRED`). | `TripHandoverCompletionTest.finalDeliveryRejectsMissingSignatureOrPhoto` |
| **5. Đóng đơn vận hành (Operational Completion)** | Khi bàn giao đích thành công: chuyển `TripLeg.LegStatus = COMPLETED`, `TransportPlan.PlanStatus = COMPLETED`, và `TransportOrder.Status = DELIVERED`. Trả về `OperationalCompletionReceipt`. | `TripHandoverCompletionTest.finalDeliveryCompletesLegPlanAndOrderWhenPrerequisitesPass` |
| **6. Ngăn chặn bàn giao đích trên chặng trung gian** | Chỉ chặng cuối cùng của kế hoạch vận hành mới được phép thực hiện bàn giao đích `FINAL_RECEIVER`. Cố tình bàn giao đích trên chặng trước bị chặn với mã lỗi `409 Conflict: NOT_FINAL_LEG`. | `TripHandoverCompletionTest.finalDeliveryRejectsIfNotFinalLeg` |
| **7. Phản hồi thực chất từ Khách hàng (Customer Decision)** | Khách hàng đăng nhập kiểm tra biên bản giao hàng và gửi phản hồi: `ACCEPTED`, `EXCEPTION`, hoặc `NOT_DELIVERED` (`CK_Figma_TripHandover_CustomerDecision`). Tuyệt đối cấm cron hoặc background worker tự động xác nhận thay khách hàng. | `TripHandoverCompletionTest.customerCanSubmitDeliveryDecisionAndExceptionNotes` |
| **8. Ghi nhận chi phí thực tế nội bộ (Actual Cost Tracking)** | Coordinator ghi nhận hóa đơn/chi phí thanh toán cho đối tác hãng bay/trạm kiểm dịch. Bắt buộc số tiền không âm `Amount >= 0` (`CK_ActualCost_Amount`) và chặng được chỉ định phải thuộc về kế hoạch (`FK_Figma_ActualCost_LegID`). Cho phép chi phí toàn kế hoạch (`legId = null`). | `ActualCostAndProfitabilityTest.coordinatorCanRecordActualCostOnPlan`, `ActualCostAndProfitabilityTest.actualCostRejectsNegativeAmount`, `ActualCostAndProfitabilityTest.actualCostRejectsLegNotBelongingToPlan`, `ActualCostAndProfitabilityTest.actualCostWithoutLegIdAllowedForPlanWideCost` |
| **9. Báo cáo biên lợi nhuận điều hành (Executive Profitability)** | Cấp Quản lý (`MANAGER`) truy cập báo cáo phân tích đối soát giữa tổng giá quote đã chốt với khách (`TotalQuotedAmount` từ F06) và tổng chi phí thực tế (`TotalActualCost` từ F14). Hệ thống tính toán `GrossProfit` và tỷ suất `ProfitMarginPercent`. Chặn các vai trò không phải Manager (`403 Forbidden`). | `ActualCostAndProfitabilityTest.managerCanViewPlanProfitabilitySummary`, `ActualCostAndProfitabilityTest.nonManagerCannotViewPlanProfitability` |
| **10. Bảo mật chi phí gốc tuyệt đối (Cost Privacy Isolation)** | Toàn bộ chi phí thực tế nội bộ, giá cước đối tác và ID nhân sự bị ẩn hoàn toàn khỏi phản hồi trả về cho khách hàng (`CustomerHandoverResponse`). Khách hàng chỉ xem biên bản bàn giao, thể trạng và ảnh chữ ký. | `ActualCostAndProfitabilityTest.customerViewsNeverExposeActualCosts`, `CustomerConsentPolicyTest.customerListAdjustmentsIsRedacted` |
| **11. Phân quyền HTTP & RBAC đa tầng** | Toàn bộ 13 endpoint F14 được bảo vệ bởi Bearer JWT token; phân quyền chặt chẽ giữa `DRIVER`, `COORDINATOR`, `MANAGER`, và `CUSTOMER`. | `HandoverHttpAuthorizationTest`, `OpenApiTest.allHandoverAndActualCostOperationsRequireAuthentication` |
| **12. Chuẩn hóa Hợp đồng DTO & OpenAPI Spec** | Toàn bộ 9 Schema DTO mới của F14 tuân thủ đầy đủ định nghĩa trường, ví dụ Swagger và ràng buộc kiểm thực. | `ApiDtoContractTest.handoverSchemasMatchTheFrontendContract` |

## Tiêu Chí Chấp Nhận Chi Tiết (Detailed Acceptance Scenarios)

### Kịch Bản 1: Bàn giao trung gian tại Cảng hàng không Charles de Gaulle (CDG)
1. Tài xế phụ trách Chặng 1 (Đường bộ từ Normandy tới CDG) đưa ngựa tới trung tâm hàng hóa của Air France Cargo.
2. Tài xế gọi `POST /api/driver/legs/{legId}/intermediate-handover` với `handoverType = INTERMEDIATE_AIRPORT`, `receivedBy = "Air France Cargo Handler - Robert"`, ghi nhận thể trạng ngựa `HorseCondition = NORMAL`.
3. Hệ thống hoàn tất Chặng 1 (`LegStatus = COMPLETED`) và tự động kích hoạt Chặng 2 (Chặng bay CDG - JFK) sang trạng thái `CONFIRMED`.
4. Kế hoạch vận hành (`TransportPlan`) vẫn giữ nguyên trạng thái `ACTIVE`, và đơn hàng (`TransportOrder`) tiếp tục ở trạng thái `IN_TRANSIT`.

### Kịch Bản 2: Bàn giao đích cuối cùng bị chặn do chưa đạt giải phóng kiểm dịch (F08 Gate)
1. Tài xế đến trạm đích chuẩn bị bàn giao ngựa cho người nhận tại chuồng nuôi.
2. Tài xế gọi `POST /api/driver/legs/{legId}/final-delivery`.
3. Hệ thống kiểm tra kết quả kiểm dịch F08: Trạm kiểm dịch chưa cấp chứng nhận thông quan (`QUARANTINE_RELEASE != PASS`).
4. Hệ thống lập tức từ chối bàn giao, trả về mã lỗi `409 Conflict` kèm thông điệp `QUARANTINE_RELEASE_REQUIRED`. Tính mạng ngựa và an toàn dịch tễ được bảo vệ tuyệt đối.

### Kịch Bản 3: Bàn giao đích thành công cho Quản lý chuồng được ủy quyền & Hoàn tất vận hành
1. Sau khi trạm kiểm dịch cấp phép thông quan giải phóng (`QUARANTINE_RELEASE = PASS`), tài xế bàn giao ngựa cho Quản lý chuồng trại `Jean-Pierre Dubois` có giấy ủy quyền `STABLE_MANAGER`.
2. Tài xế thu thập chữ ký điện tử `signatures/2026/10/dubois.png` và chụp ảnh thể trạng ngựa trong ô chuồng an toàn `photos/2026/10/stall.jpg`.
3. Tài xế gọi `POST /api/driver/legs/{legId}/final-delivery`.
4. Hệ thống xác thực người nhận, chữ ký và ảnh chụp hợp lệ:
   - Chặng cuối cùng chuyển sang `COMPLETED`.
   - Toàn bộ kế hoạch vận chuyển `TransportPlan` chuyển sang `COMPLETED`.
   - Đơn hàng `TransportOrder` chính thức chuyển sang `DELIVERED`.
   - Trả về `OperationalCompletionReceipt`. Vận hành vật lý hoàn tất mà không can thiệp trái phép vào trạng thái thanh toán tài chính của BE1.

### Kịch Bản 4: Khách hàng đánh giá giao hàng & phát hiện xây xát (Customer Feedback)
1. Khách hàng đăng nhập vào cổng thông tin, gọi `GET /api/customer/transport-orders/{orderId}/handovers`. Khách hàng xem được ảnh chụp và chữ ký mà không thấy bất kỳ chi phí nội bộ nào.
2. Khách hàng nhận thấy ngựa có vết trầy nhẹ ở chân sau khi xuống xe.
3. Khách hàng gọi `PUT /api/customer/transport-orders/{orderId}/handovers/{handoverId}/decision` với `decision = EXCEPTION` và `feedbackNotes = "Ngựa có vết xước nhẹ ở chân sau trái, đã bôi thuốc sát trùng"`.
4. Hệ thống lưu vết phản hồi chính thức của khách hàng. Không có bất kỳ tiến trình tự động nào ghi đè quyết định này.

### Kịch Bản 5: Hạch toán chi phí vé máy bay và Báo cáo lợi nhuận điều hành
1. Coordinator nhận hóa đơn Airway Bill từ Air France Cargo trị giá 1,850.00 EUR.
2. Coordinator gọi `POST /api/coordinator/plans/{planId}/actual-costs` liên kết đúng chặng bay `LegID = 401`, đính kèm file hóa đơn `invoices/af-618.pdf`.
3. Quản lý (`MANAGER`) truy cập báo cáo kinh doanh qua `GET /api/manager/plans/{planId}/profitability`:
   - Giá báo khách ban đầu (`TotalQuotedAmount`): 14,500.00 EUR.
   - Tổng chi phí thực chi (`TotalActualCost`): 10,300.00 EUR (4 khoản chi phí).
   - Lợi nhuận gộp (`GrossProfit`): 4,200.00 EUR.
   - Tỷ suất lợi nhuận (`ProfitMarginPercent`): 28.97%.
