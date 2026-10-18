# F11 — Đối chiếu nghiệp vụ và kiểm thử

| Nghiệp vụ | Thực thi | Bằng chứng tự động |
|---|---|---|
| Ba cổng tính lại khi xem, ký duyệt và xuất phát | F07 FinancialReadinessPolicy, F08 coverage/clearance, F09 nguồn lực và F10 policy | DepartureCompletionTest, các test F07/F08/F09/F10 hiện có |
| PaymentReport không thay cho tiền đối soát | Ledger balances và pending refunds | FinancialReadinessPolicyTest; F11 test chặn NO_RECONCILED_RECEIPT |
| Không có evaluator F08 thì không cấp PASS | DepartureLegalAdapter | DepartureAdapterTest |
| Manager không thể bypass gate; có quyền thu hồi trước xuất phát | authorize/revoke, current plan và rowversion | DepartureCompletionTest |
| Driver đúng chặng và phân công còn hiệu lực | Kiểm tra actor, assignment, actual time | DepartureCompletionTest, DepartureRepositoryTest |
| Ngựa đúng chip/hộ chiếu, khỏe mới ACCEPTED | So sánh lại định danh, biên nhận mới nhất | DepartureGatePolicyTest, DepartureCompletionTest |
| Ảnh thật, giới hạn, hash, scope và restart | DepartureEvidenceStorage, API multipart/download | DepartureEvidenceTest, DepartureHttpAuthorizationTest |
| Xe/chuồng/vé/fit/cách ly/bảo hiểm đúng nguồn và kỳ hạn | F11 gate đọc lại nguồn F09/F10, đánh giá F08, giữ resource lock | DepartureCompletionTest và các test policy F09/F10 |
| Revision/plan thay đổi, trạng thái hủy/kết thúc không mở cổng | Exact current pointer, trạng thái predeparture và deadline | Service guards; các test current revision/gate F08–F10 |
| Retry không khởi hành trùng; audit cùng transaction | PlanOperation, rowversion, DepartureAuditRepository | DepartureCompletionTest; rollback JDBC H2 |
| Toàn bộ leg/plan/order/assignment chuyển trạng thái nguyên tử | departFirstLeg trong transaction service | DepartureRepositoryTest: luồng thành công, lỗi update cuối, lỗi audit |
| Customer xem ảnh và thể trạng, không thấy đối soát nội bộ | Redaction và file route theo receipt/case | Policy, MVC owner/cross-owner download |
| Không ảnh hưởng cập nhật tracking F12 | Migration 011 chỉ xét transition xuất phát | ScriptDom syntax/offline review; native trigger testing hoãn |
| API có DTO typed và xác thực | 8 endpoint, examples và JSON/binary media cụ thể | ApiDtoContractTest, OpenApiTest |

Giữ enum hiện hành để tương thích project: `PASS/BLOCKED`,
`ACCEPTED/REJECTED/PENDING`, `IN_TRANSIT` cho trạng thái đã khởi hành.
Bảo hiểm/cách ly tuân theo dịch vụ đã chọn và đánh giá hiện hành; F11 không
bịa hợp đồng, không chuyển UNKNOWN thành miễn trừ, không cấp gate của module khác.

Code không thay schema hoặc logic nguồn F08/F09/F10. Migration 001–010 và
SQL tổng được giữ nguyên; bổ sung 011 riêng. Java 21 và Spring Boot 3.5.x
được giữ nguyên. Lưu ảnh cần volume bền vững và quyền filesystem riêng của
ứng dụng; không dùng thư mục public/static.

Phạm vi xác nhận là unit/service/MVC, JDBC H2 và phân tích migration offline.
Không suy diễn H2 thành bằng chứng về SQL Server: locking, trigger, race
condition và native lifecycle phải chạy trên database test riêng khi cả nhóm
hoàn thành tích hợp backend, theo quyết định của người dùng. Không tự chạy
migration hay test lên EquiTransit thật trong lượt hoàn thiện này.

## Định danh theo revision

Chip/hộ chiếu dùng để nhận ngựa lấy từ snapshot của revision hiện hành.
Snapshot phải thuộc đúng order và horse, có đủ hai định danh. Hồ sơ master
và chip tại order detail phải khớp định danh đã chốt; dữ liệu master không
được dùng để thay thế hoặc lấp thiếu snapshot. Sai khác tạo blocker
`HORSE_IDENTITY_REVISION_MISMATCH`: không ký duyệt, không ACCEPTED biên nhận
và không xuất phát. Biên nhận sai/thiếu định danh có thể được lưu REJECTED để
giữ lịch sử. Khi đổi định danh, cần cập nhật revision qua luồng đơn hàng và
đánh giá lại hồ sơ/kế hoạch; F11 không tự sửa snapshot, legal PASS hay booking.
