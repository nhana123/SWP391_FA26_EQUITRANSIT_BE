# F13 — Đối Chiếu Nghiệp Vụ & Tiêu Chí Chấp Nhận (Acceptance Criteria)

## Ma Trận Nghiệp Vụ & Bằng Chứng Kiểm Thử Tự Động

| Nghiệp Vụ Cốt Lõi | Cơ Chế Thực Thi | Bằng Chứng Tự Động |
| :--- | :--- | :--- |
| **1. Báo cáo sự cố bởi Driver được phân công** | Tài xế active được phân công trên chặng đường bộ gửi báo cáo sự cố; chặn tài xế khác hoặc người ngoài (`403 Forbidden`). | `IncidentManagementCompletionTest.driverCanReportIncidentOnAssignedLeg`, `IncidentManagementCompletionTest.unassignedDriverCannotReportIncident` |
| **2. Nguyên tắc Cấp cứu Phúc lợi Ngựa (Welfare Emergency Rule)** | Báo cáo sự cố khẩn cấp (Colic, sốt cao, chấn thương) cho phép thực hiện sơ cứu ngay lập tức mà không bị chặn bởi rào cản chi phí hay phê duyệt tài chính. | `IncidentManagementCompletionTest.driverCanReportIncidentOnAssignedLeg`, `IncidentManagementCompletionTest.coordinatorCanReportIncidentOnAnyLeg` |
| **3. Quy trình xác nhận & đóng sự cố (Acknowledge & Resolve)** | Coordinator/Manager tiếp nhận sự cố (`ACKNOWLEDGED`); khi giải quyết dứt điểm (`RESOLVED`) bắt buộc phải có biên bản giải pháp `ResolutionNotes`. Chặn chuyển trạng thái trái phép. | `IncidentManagementCompletionTest.coordinatorCanAcknowledgeIncident`, `IncidentManagementCompletionTest.coordinatorCanResolveIncidentWithNotes`, `IncidentManagementCompletionTest.resolveIncidentRequiresNotes` |
| **4. Ràng buộc JSON Bằng chứng sự cố** | Trường `EvidenceFilesJSON` bắt buộc phải là chuỗi JSON hợp lệ (mảng các FileKey ảnh hiện trường, bệnh án thú y) theo ràng buộc `CK_Figma_IncidentReport_EvidenceFilesJSON`. | `IncidentManagementCompletionTest.evidenceFilesJsonMustBeValidJson` |
| **5. Đề xuất điều chỉnh vận hành & Kiểm tra JSON/Chi phí** | Coordinator lập đề xuất `OperationalAdjustment` liên kết `PlanID`, `IncidentID`, hoặc `ChangeRequestID`. Bắt buộc `ProposedChanges` chuẩn JSON (`CK_OperationalAdjustment_ProposedChanges_JSON`) và `AdditionalCostEstimate >= 0`. | `CustomerConsentPolicyTest.proposeAdjustmentWithCostRequiresPendingConsent`, `CustomerConsentPolicyTest.proposeAdjustmentWithNegativeCostIsRejected`, `CustomerConsentPolicyTest.proposeAdjustmentWithInvalidJsonIsRejected` |
| **6. Ranh giới đồng thuận thương mại (Commercial Consent)** | Nếu `AdditionalCostEstimate > 0`, đề xuất khởi tạo ở trạng thái `CustomerDecision = PENDING`. Khách hàng có quyền chấp thuận (`ACCEPTED`) hoặc từ chối (`REJECTED`). Manager không thể phê duyệt nếu khách chưa `ACCEPTED` (`409 Conflict: CUSTOMER_CONSENT_REQUIRED`). | `CustomerConsentPolicyTest.proposeAdjustmentWithCostRequiresPendingConsent`, `CustomerConsentPolicyTest.customerCanAcceptAdjustmentWithCost`, `CustomerConsentPolicyTest.customerCanRejectAdjustmentWithCost`, `CustomerConsentPolicyTest.managerCannotApproveWhenCustomerPendingConsent` |
| **7. Điều chỉnh không phát sinh phụ phí (Cost = 0)** | Nếu `AdditionalCostEstimate = 0` (lỗi kỹ thuật, chi phí nội bộ công ty), đề xuất mặc định `CustomerDecision = ACCEPTED` và cho phép Manager phê duyệt ngay không cần chờ khách hàng. | `CustomerConsentPolicyTest.proposeAdjustmentZeroCostAutoAcceptsAndManagerCanApprove` |
| **8. Tiến hóa kế hoạch vận hành (Plan Evolution BR-11)** | Khi Manager phê duyệt: sinh phiên bản mới `TransportPlan` (`VersionNo + 1`), chuyển trạng thái `ACTIVE`, nhân bản toàn bộ chặng hành trình (`TripLeg`), cập nhật `TransportOrder.CurrentPlanID`, đánh dấu kế hoạch cũ `SUPERSEDED`, và cập nhật `OrderChangeRequest = 'APPLIED'`. Trả về `PlanEvolutionReceipt`. | `CustomerConsentPolicyTest.managerApprovalTriggersPlanEvolutionAndAppliesChangeRequest` |
| **9. Khử dữ liệu nhạy cảm cho Khách hàng (Redacted View)** | API dành cho khách hàng tự động loại bỏ chi phí nhà cung cấp nội bộ, `staffId` của Coordinator/Manager, và chỉ hiển thị tóm tắt phương án điều chỉnh (`CustomerAdjustmentResponse`). | `CustomerConsentPolicyTest.customerListAdjustmentsIsRedacted`, `CustomerConsentPolicyTest.customerAdjustmentMustBelongToCustomerOrder`, `ApiDtoContractTest.incidentSchemasMatchTheFrontendContract` |
| **10. Bảo mật HTTP & RBAC đa tầng** | Toàn bộ 14 endpoint F13 được bảo vệ bởi Bearer token JWT; phân quyền nghiêm ngặt giữa `DRIVER`, `COORDINATOR`, `MANAGER`, và `CUSTOMER`. | `IncidentHttpAuthorizationTest`, `OpenApiTest.allIncidentAndAdjustmentOperationsRequireAuthentication` |

## Tiêu Chí Chấp Nhận Chi Tiết (Detailed Acceptance Scenarios)

### Kịch Bản 1: Báo cáo sự cố y tế ngựa và sơ cứu khẩn cấp trên đường
1. Tài xế đang vận chuyển phát hiện ngựa có dấu hiệu đau bụng cấp (Colic) và thân nhiệt tăng vọt.
2. Tài xế gọi `POST /api/driver/legs/{legId}/incidents` với `severityLevel = CRITICAL`, `incidentType = HORSE_WELFARE_HEALTH`, mô tả triệu chứng và ghi rõ hành động sơ cứu tức thì `immediateActionNotes = "Dừng xe khẩn cấp tại trạm thú y lưu động, tiêm thuốc giảm co thắt"`.
3. Hệ thống ghi nhận sự cố thành công mà không yêu cầu phê duyệt ngân sách trước, đảm bảo an toàn tính mạng động vật theo Welfare Emergency Rule.
4. Điều phối viên nhận cảnh báo, gọi `PUT /api/coordinator/incidents/{incidentId}/acknowledge` để tiếp nhận điều phối. Sau khi ngựa ổn định, gọi `PUT /api/coordinator/incidents/{incidentId}/resolve` kèm biên bản thú y để đóng sự cố.

### Kịch Bản 2: Biến cố hỏng xe và Đề xuất điều chỉnh không phát sinh phụ phí (Cost = 0)
1. Xe chuyên dụng bị hỏng trục truyền động giữa cao tốc.
2. Coordinator lập đề xuất đổi xe cứu hộ: `POST /api/coordinator/plans/{planId}/adjustments` với `additionalCostEstimate = 0.00` (chi phí sửa chữa và điều xe là trách nhiệm bảo hành của EquiTransit).
3. Hệ thống tự động gán `customerDecision = 'ACCEPTED'`.
4. Manager gọi `PUT /api/manager/adjustments/{adjustmentId}/approve`. Hệ thống ngay lập tức kích hoạt Plan Evolution: tạo `TransportPlan` phiên bản mới, nhân bản các chặng với tài nguyên xe mới, và chuyển đơn hàng sang kế hoạch mới.

### Kịch Bản 3: Khách hàng đổi địa điểm nhận và Ranh giới đồng thuận thương mại (Cost > 0)
1. Khách hàng gửi `OrderChangeRequest` (F03) yêu cầu chuyển địa điểm trả ngựa từ Paris sang Normandy sau khi xe đã xuất phát.
2. Coordinator lập phương án điều chỉnh vận hành: `POST /api/coordinator/plans/{planId}/adjustments` liên kết `changeRequestId = 31`, phát sinh phụ phí kéo dài lộ trình `additionalCostEstimate = 450.00 EUR`.
3. Đề xuất được tạo với `customerDecision = 'PENDING'`.
4. Nếu Manager cố tình phê duyệt trước khi khách hàng đồng thuận: Hệ thống trả về `409 Conflict` kèm mã lỗi `CUSTOMER_CONSENT_REQUIRED`.
5. Khách hàng đăng nhập, xem tóm tắt điều chỉnh qua `GET /api/customer/transport-orders/{orderId}/adjustments` (không thấy thông tin nội bộ nhân viên).
6. Khách hàng gửi phản hồi: `PUT /api/customer/transport-orders/{orderId}/adjustments/{adjustmentId}/decision` với `decision = 'ACCEPTED'`.
7. Lúc này, Manager gọi `PUT /api/manager/adjustments/{adjustmentId}/approve`: Phê duyệt thành công! Kế hoạch tiến hóa lên Version 2, `CurrentPlanID` trên đơn hàng được cập nhật, và `OrderChangeRequest` chuyển thành `APPLIED`.
