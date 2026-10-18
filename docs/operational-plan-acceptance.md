# F10: đối chiếu nghiệp vụ và kiểm thử

Phạm vi: kế hoạch đa phương thức và đặt chỗ vận hành. Các class nằm trong
`controller/plan`, `service/plan`, `repository/plan`, `dto/plan`; test ở package
`com.equitransit.backend.plan`. Giữ nguyên kiến trúc phân tầng của project.

| Yêu cầu | Thực thi | Kiểm tra tự động |
|---|---|---|
| Kế hoạch nháp có phiên bản, sửa chuỗi chặng đồng bộ | `createDraft`, `replaceLegs`; giữ lịch sử chặng đã hủy | `OperationalPlanCompletionTest` |
| ROAD → AIR → ROAD, điểm nối liên tục, nguồn km và múi giờ IANA | `OperationalPlanPolicy.validateLegs` | `OperationalPlanValidationTest` |
| Không dùng xe/tài xế ROAD cho AIR | Endpoint assignment kiểm tra mode; policy kiểm tra lại tại gate | `OperationalPlanPolicyTest`, native suite |
| Không xác nhận bản nháp đã quá giờ bay | `confirm` yêu cầu thời điểm xuất phát còn ở tương lai | `OperationalPlanCompletionTest.elapsedDraftMustBeRescheduled` |
| Đúng báo giá đã chấp nhận của đơn và revision hiện tại | `PlanEvidenceRepository.requireAccepted` đọc Quote thực tế | `OperationalPlanEvidenceTest` |
| Không tự ghi đè kế hoạch đã xác nhận/đang chạy | Bảo vệ CurrentPlanID; Manager mới thay kế hoạch CONFIRMED; ACTIVE/IN_TRANSIT qua F13 | `OperationalPlanCompletionTest` |
| Xe/chuồng đủ điều kiện, chống trùng lịch có buffer | F09 transaction boundary và guard SQL; tài xế có kiểm tra riêng | Service tests; native concurrency test chưa chạy |
| Ngựa không có hai lịch vận hành giao cắt | Kiểm tra khi confirm và khi F11 đọc readiness | `OperationalPlanCompletionTest`, native suite |
| HELD không phải CONFIRMED; chuồng được hãng chấp nhận | Policy yêu cầu proof, xác nhận và khoảng thời gian đúng chặng | `OperationalPlanPolicyTest` |
| Sức chứa chuyến bay đủ cho chuồng đã xác nhận | Tổng slot của các assignment hợp lệ; bỏ HELD khỏi phép tính | `OperationalPlanGatePolicyTest` |
| Booking dùng đúng nguồn báo giá hãng bay | `flightSourceValid` đối chiếu plan/revision/leg/carrier/offer và thời điểm nguồn | `OperationalPlanSourceTest`, service regression |
| Phân biệt hết hạn giá chào và hết hạn vé | Offer hợp lệ lúc booking; expiry của vé được xét riêng ở hiện tại và ngày bay | `OperationalPlanSourceTest`, policy tests |
| Cách ly lấy từ đánh giá F08, đủ số ngày và chứng từ | UNKNOWN chặn gate; NOT_REQUIRED khác REQUIRED; booking ràng buộc PlanID | `OperationalPlanCompletionTest`, policy tests |
| Hạn booking cách ly đủ đến lúc vào cơ sở | Kiểm tra ngay khi ghi nhận và tại gate | `OperationalPlanGatePolicyTest` |
| Bảo hiểm đã xác minh và bao trọn chuyến đi | `verifyInsurance` và live confirmation blockers | `OperationalPlanPolicyTest`, `OperationalPlanEvidenceTest` |
| Customer chỉ thấy hành trình công khai | DTO allowlist; kiểm tra chủ đơn; không trả draft | `PlanItineraryTest` |
| Driver chỉ thấy chặng của mình và liên hệ khẩn cấp | Xác thực assignment trước khi đọc contact Coordinator đang được giao case | `PlanItineraryTest`, `OperationalPlanSourceTest` |
| Không cấp gate cho đơn đã hủy/kết thúc hoặc revision cũ | `operationalReady` xét trạng thái đơn, con trỏ plan, revision và dữ liệu hiện hành | `OperationalPlanCompletionTest` |
| Retry không tạo trùng và audit cùng transaction | PlanOperation, parent rowversion và AuditEvent có actor thực | Completion/evidence tests; native suite |
| Cung cấp km có nguồn cho F05/F06 | `PlanAssessmentAdapter` và `PlanItineraryService.pricing` | `PlanAssessmentAdapterTest`, `PlanItineraryTest` |
| Không N+1 khi tải các collection của chặng | `PlanRepository.legs` đọc batch; detail dùng lại các collection khi tính blocker | Repository implementation; tối đa 20 chặng |

API giữ các đường dẫn đang tích hợp tại [operational-plan-api.md](operational-plan-api.md).
Các tên class/endpoint đề xuất trong bản phân tích là phương án thiết kế; không
tạo thêm bộ controller/entity trùng chức năng khi hợp đồng hiện tại đã đáp ứng.

Migration `008_multimodal_plans_reservations.sql` được đối chiếu offline với
`EquiTransit.sql`: bảy bảng lõi, 95 cột gốc, ba cột nguồn cự ly của migration
004, các khóa/ràng buộc và index. Các mở rộng F10 gồm idempotency, ràng buộc
booking theo plan và guard lịch. Lượt hoàn thiện nghiệp vụ này không sửa SQL
gốc hoặc migration BE1.

Java 21, Spring Boot 3.5.x và Maven Wrapper được giữ nguyên. Unit test và test
JDBC H2 kiểm tra chính sách và câu truy vấn; chúng không chứng minh hành vi
khóa, trigger hay race condition của SQL Server.

Theo phạm vi đã thống nhất, chưa chạy migration hoặc test trên database thật.
Trước nghiệm thu tích hợp toàn nhóm, cần chạy migration 001–008 và native suite
trên database SQL Server test riêng; kiểm tra cạnh tranh cùng xe/tài xế/chuồng,
ngựa trùng lịch, rollback và các foreign key tổ hợp. Không dùng `EquiTransit`
thật làm nơi tạo/xóa fixture.

File chứng từ của F10 hiện là tham chiếu do staff ghi nhận; API này chưa có
luồng upload hoặc xác thực bytes của vé/bảo hiểm. Không coi một file key là
bằng chứng đã kiểm tra nội dung nhị phân.

## Delivery deadline and draft pricing

Confirmation and live operational readiness compare plan arrival with the
immutable revision delivery deadline, by instant. Arrival at the deadline is
allowed; later arrival produces `DELIVERY_DEADLINE_EXCEEDED`. A missing deadline
produces `DELIVERY_DEADLINE_PENDING`. Drafts may retain these blockers for editing.

Pricing uses an eligible selected plan of the exact current revision. When the
operating pointer belongs to an older revision or is absent, one unique
DRAFT/PROPOSED/PLANNED candidate of the requested order and current revision may
supply sourced kilometres. Multiple unselected candidates remain `PENDING`;
select a draft explicitly or supersede the other drafts. Source and ownership
checks still apply. This read never changes `CurrentPlanID`. Public itinerary
and driver endpoints continue to use the operating pointer.

Regression coverage includes late arrival rejection, equal-instant arrival,
missing deadline, live readiness, new revision pricing, no current pointer,
ambiguous drafts, missing drafts, and revoked source assignment.
