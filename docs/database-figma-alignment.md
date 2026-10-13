# EquiTransit database alignment with SWP

Ngày đối chiếu: 13/10/2026. Nhánh `db/figma-alignment`.
Nguồn: file SWP người dùng gửi (export 10/10/2026) và metadata live của page
`0:1` ngày 13/10/2026. Đã đọc chi tiết/ảnh live C03, C17 và BOOK-02.
Không tuyên bố toàn bộ file đã được kiểm tra bằng ảnh live: những lượt đọc
chi tiết sau đó bị giới hạn bởi tài khoản Figma.

## Quyết định phạm vi

Người dùng yêu cầu cập nhật EquiTransit theo Figma, không thêm Organization.
Giữ khách cá nhân, 5 role hiện tại và ràng buộc một ngựa/order. Những dòng
“organization”, mẫu hai ngựa và giá demo còn sót trên thiết kế không trở
thành dữ liệu hoặc cấu trúc production. Nhà cung cấp là đối tác dịch vụ,
không phải tài khoản tổ chức của khách hàng.

Migration `002_align_figma_without_organization.sql` chỉ bổ sung cấu trúc.
Không xóa bảng/cột/dữ liệu, không đổi kiểu cột cũ và không chọn hộ quote/plan.
Các thông tin cũ chưa biết giữ NULL. OrderID mới của TransportPlan được
backfill chính xác từ OrderRevision.OrderID, không đoán trạng thái vận hành.

## Bổ sung theo màn hình

| Màn hình | Persistence bổ sung |
|---|---|
| C03 C07 | Horse: ghi chú xử lý/chăm sóc, thức ăn/nước, stall preference, chỉ dẫn thú y/nguồn; rowversion để chống ghi đè đồng thời |
| C17 | Customer.EmailNotificationsEnabled; không OrganizationName hay OrganizationID |
| C05 C06 C08 C10 C16 | TransportOrder: preferred departure, flexibility, receiver/contact/authority, import type, race event/venue/timezone, transit airports, draft JSON, current revision/operating plan pointers |
| BOOK-01 đến BOOK-08 | OrderServiceChoice.SelectionInputsJSON cho groom days, boarding nights và inputs theo service; provider/evidence cho tự thu xếp |
| BOOK-02 F04 F07 | StallFitPolicy có version, trọng lượng/kích thước, slot, nguồn và người duyệt; equipment tải tối đa; vị trí và bằng chứng hãng chấp nhận |
| F02 M12 | Supplier catalogue/contact/services; SupplierOffer giữ giá snapshot và thêm supplier/source/evidence |
| M04 C13 C14 M07 | Quote prepared/submitted/issued/accepted actor, terms/cancellation snapshot; QuotePaymentSchedule; PaymentRequest.ScheduleID theo đúng quote |
| C14 M07 | PaymentReport thời gian/currency chuyển; PaymentTransaction nguồn/proof/notes; Refund actor/time/actual transfer reference/proof |
| C11 S04 | DocumentTypeCatalog, requirement instructions/scope/horse/validity conditions/justification, transit rule metadata |
| C03 C11 S03 | DocumentVersion uploader, filename/MIME/file size; file bắt buộc vẫn giữ FileKey hiện có |
| S02 S05 | AuthorityProcedure phase/contact/hold reason/expiry/actor; ComplianceClearance expiry và assessment notes |
| F06 F07 F08 | Asset supplier owner, maintenance interval, expiry/notes; personnel buffer/conflict notes |
| F04 M06 | TransportPlan liên kết order và accepted quote cùng revision; TripLeg timezone/provider/flight/buffer/rest/care |
| F04 BOOK-05 BOOK-07 | Flight/quarantine reservation proof/contact/expiry/confirm actor; insurance verification/terms |
| D02 C15 | HorseReceipt leg/plan/identity/passport/location/proof; milestone source/location/ETA/delay/evidence |
| D03 | Welfare feed/care/instruction evidence và liên kết bản ghi được sửa, giữ lịch sử |
| C10 M08 M09 | OrderChangeRequest lưu đề nghị chỉnh ngày/route/cancel và phản hồi; OperationalAdjustment có quyết định khách |
| M10 M11 | Incident acknowledgment/resolution/contact/action/evidence; actual cost description/actor/proof |
| D04 C16 | Handover location, customer decision/feedback/actor/time, nhiều evidence file keys |

5 bảng nghiệp vụ mới: Supplier, DocumentTypeCatalog, QuotePaymentSchedule,
OrderChangeRequest, StallFitPolicy. SchemaMigrationHistory là bảng kỹ thuật.
Migration bổ sung 146 cột vào các bảng hiện có, 41 FK và các CHECK liên quan.
Giữ nguyên 50 bảng nghiệp vụ trước đó và sysdiagrams.

## Air stall

StallChoice giữ nguyên CHECK: SHARED dùng 1 hoặc 1.5 slot; PRIVATE dùng 3.
Policy APPROVED phải có giới hạn trọng lượng, height, width, nguồn và reviewer.
Không seed 480kg/170cm/80cm: đó là fixture của prototype, không là tiêu chuẩn hãng.
Thiếu policy hợp lệ/số đo thì API phải loại option 1 slot và trả lý do pending;
không tự chuyển gói hoặc tăng tiền. FitStatus và CarrierAcceptanceStatus
vẫn là hai điều kiện riêng. Schema hỗ trợ dữ liệu; API eligibility ở F05/F09
và xác nhận vận hành ở F10 phải được code trước khi chức năng chạy thực tế.

## Integrity

Composite FK kiểm current revision thuộc order, operating plan thuộc order,
plan/order/revision cùng lineage, accepted quote cùng revision, receipt và
ActualCost leg cùng plan, payment schedule cùng quote, change request cùng order,
và welfare correction cùng leg. Trigger từ chối một plan tham chiếu draft quote
hoặc việc hủy thông tin accepted của quote đang được plan tham chiếu.

FK không thay quyền truy cập hoặc workflow. Role, ownership, assignment đang
hiệu lực, duplicate charges, payment allocations/refunds, active-plan uniqueness,
resource overlaps và customer consent vẫn cần service transaction/validation.
Không thêm status tự do vào những cột đã có CHECK; quote và insurance codes
hiện tại được giữ nguyên.

## Triển khai và khôi phục

Chạy backup COPY_ONLY/CHECKSUM, VERIFYONLY và restore tên database test riêng
bằng `tools/prepare-figma-migration-test.ps1`. Script từ chối ghi đè database/file
đã tồn tại. Kết quả backup/test path nằm `.local/figma-migration-test.json`, không
commit lên Git. Migration chỉ cho phép EquiTransit hoặc test copy tên chuẩn.

Trên bản sao, chạy migration hai lần và `tools/test-figma-migration.sql`.
Các fixture đều rollback. Các session SET options cần cho filtered microchip
index đã được đặt rõ trong migration/tests. Kiểm tất cả FK/CHECK trusted và
giữ nguyên row count/schema của các bảng cũ trước khi áp dụng database thật.

Đối với thành viên khác, chạy 002 sau schema gốc đầy đủ và 001 AuthenticationToken.
Không chạy 002 vào EquiTransit_AuthTest chỉ có các bảng auth. Những database test
cho flow tiếp theo cần schema nghiệp vụ đầy đủ. Không tự động restore đè bản thật;
backup dành cho khôi phục có kiểm soát, còn lỗi triển khai ưu tiên forward repair.

## Điều chỉnh kế hoạch

Issue #2 dùng các cột Horse mới và rowversion, nhưng chưa có API mới trong PR này.
Archive tài sản dùng OperationalStatus/EquipmentStatus hiện có. Archive ngựa là
đề xuất của backlog trước, chưa có thao tác tương ứng được xác nhận trên Figma;
không tự thêm IsArchived hoặc API archive horse trong migration này.
Requirement và schema sau triển khai phải là nguồn cho các flow BE tiếp theo,
tránh tiếp tục đề xuất lại những cột đã được migration 002 bổ sung.

## Kết quả triển khai ngày 13/10/2026

Đã áp dụng vào EquiTransit sau backup COPY_ONLY/CHECKSUM và RESTORE VERIFYONLY.
Test copy riêng: EquiTransit_FigmaMigrationTest_20261013_143729. Migration chạy
lặp thành công; tests cấu trúc có 12 PASS, fixture rollback. Metadata sau triển
khai: 55 bảng nghiệp vụ, 594 cột, 114 FK constraints (123 ánh xạ cột FK), hai
bảng kỹ thuật. Tất cả FK/CHECK bật và trusted; không có Organization.
Đối chiếu trước/sau giữ nguyên định nghĩa bảng/cột/default và constraints cũ;
Role vẫn 5 dòng, User/Customer 0 dòng. Không dùng bộ tests này để tuyên bố API
booking/gate đã hoạt động; chúng kiểm ràng buộc SQL và tính tương thích cấu trúc.

Maven verify sau cập nhật: BUILD SUCCESS, 25 tests pass và 0 Checkstyle violations.
Không thay source Java trong thay đổi này; bộ kiểm thử xác nhận baseline vẫn build.
