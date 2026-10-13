# EquiTransit Backend — hợp đồng nghiệp vụ và phối hợp hai BE

Ngày thống nhất: 2026-10-13. Repo: `nhana123/SWP391_FA26_EQUITRANSIT_BE`. Nhánh tích hợp `dev`, nhánh phát hành `main`.

Đây là bản thống nhất cho công việc sắp triển khai, không phải tuyên bố các flow đã được code. Flow tài khoản đã có baseline tại commit `248458d`; các flow còn lại là backlog. Tài liệu không sửa scope giảng viên đã duyệt nếu có một quyết định mới được người dùng cung cấp sau này.

## 1. Nguồn và cách giải quyết mâu thuẫn

Thứ tự quyết định: yêu cầu mới nhất của người dùng → hợp đồng đã thống nhất này → đặc tả booking cập nhật và model plugin → màn actor của English Workflow → tài liệu cũ. Database live quyết định tên/kiểu/khóa đang tồn tại; nếu thiếu dữ liệu để đáp ứng nghiệp vụ thì đề xuất migration, không bỏ yêu cầu hoặc bịa cột.

Đã đối chiếu nguồn local:

| Nguồn | Dùng cho | Điểm cũ không tiếp tục áp dụng |
|---|---|---|
| REQUIREMENTS.docx | 5 actor, 4 flow giảng viên bắt buộc, sự cố/bàn giao | Express/MongoDB, React Native, live GPS liên tục, FEI bắt buộc toàn cầu |
| CHECKPOINT_1_EquiTransit.docx | Cách demo, version và các điều kiện chặn | Chỉ đường bộ, nhiều ngựa/request, loại hàng không/thanh toán khỏi scope |
| REQUIREMENT_NGHIEP_VU_EQUITRANSIT_VI.md | Phân quyền, checklist theo phase, quote/thu tiền/gate/bàn giao | Các đoạn còn nhiều ngựa, giá cũ và đề xuất mở rộng |
| SRS_EQUITRANSIT_CHO_SO_DO.md | Mapping actor/frame và invariant | Khảo sát stall, chặn toàn bộ việc lập kế hoạch trước khi pre-export complete |
| BOOKING_CHI_PHI_8_NHOM.md, SWP-RE/booking-model.js | Chọn dịch vụ, 7 mục UI/8 nhóm chi phí, km, đơn vị thực, lọc stall | Các giá fixture và ngưỡng fixture không phải giá thật/tiêu chuẩn hãng |
| SWP-RE/english-screens.js, manifest.json, code.js | A01–A03, C01–C17, M01–M13, S01–S05, F01–F08, D01–D04 | Nút Demo, ví dụ 2 ngựa, giá 9.800/800 cố định, base catalogue cũ |
| SWP-RE/PRICING_DESIGN.md | Minh bạch đơn vị, included/pending và không thu trùng | UI explorer cũ, vehicle-km, danh sách giá trước cập nhật |
| docs/database-schema.md | Snapshot live DESKTOP-9VRARFD / EquiTransit | Không dùng tên bảng giả từ ERD cũ |
| Oracle Java Coding Standards.pdf | Quy tắc Java của Sun 1997 | Không coi PDF là đặc tả Spring/security hiện đại |

Mã plugin/catalogue local không xác nhận bản Figma live đã cập nhật. Không dùng chữ trong frame để làm dữ liệu runtime. Không đưa nguyên PDF, credentials hoặc hồ sơ khách hàng thật vào repo.

## 2. Scope chốt

- Một công ty, khách cá nhân; không có Organization, club accounts hoặc portal Admin thứ sáu.
- Một tài khoản có một role: CUSTOMER, MANAGER, SPECIALIST, COORDINATOR, DRIVER. Manager có quản trị nội bộ giới hạn.
- Một order đã gửi có đúng một ngựa thuộc Customer. Không chọn lại ngựa trong popup dịch vụ.
- Vận chuyển quốc tế có ROAD/AIR và các giai đoạn thủ tục/cách ly phù hợp. Có thể chứa nhiều chặng; một order có một hành trình vận hành được chọn tại một thời điểm, có nhiều phiên bản TransportPlan. Không tạo bảng Trip riêng chỉ vì tên trên Figma là Trip.
- Chưa tự ghép nhiều booking vào một chuyến/stall inventory. Shared là nhu cầu sử dụng chỗ; xác nhận chỗ ghép thực tế vẫn cần đối tác.
- Website responsive; cập nhật vị trí/mốc có nguồn, không giả GPS liên tục trên máy bay/native offline synchronization.
- Không tích hợp API hãng bay/ngân hàng/chính phủ/insurer, tự chẩn đoán, tự cấp phép hoặc chuyển tiền. Ghi nhận kết quả thật từ bên ngoài và bằng chứng.
- Sự cố, thay đổi, bàn giao và quyết toán làm mức tối thiểu sau 4 flow cốt lõi. So sánh/ranking nhiều phương án, tự động ghép chỗ, ferry/ocean và BI nâng cao là phase sau.
- Customer không tự giảm khám/test/cách ly bắt buộc; tự thu xếp chỉ áp dụng phần được phép và phải có bằng chứng phù hợp.

## 3. Chuỗi nghiệp vụ

Tài khoản → hồ sơ ngựa → draft/order revision + lựa chọn dịch vụ → Manager tiếp nhận/phân công → Specialist lập checklist và Coordinator khảo giá/lập nháp song song → Manager phát hành Quote → Customer accept → PaymentRequest/đối soát và các reservation → kiểm gate/nhận ngựa → thực hiện chặng → nhật ký/sự cố → bàn giao → đóng vận hành và tài chính.

Tiếp nhận request ≠ duyệt giấy tờ ≠ Quote APPROVED ≠ Quote ISSUED ≠ Quote ACCEPTED ≠ tiền thực nhận ≠ chỗ bay confirmed ≠ cho phép xuất phát.

## 4. Giá và lựa chọn dịch vụ

8 CostGroup: TRANSPORTATION, HORSE_ACCOMMODATION, HORSE_CARE, VETERINARY, DOCUMENTATION_QUARANTINE, AIRPORT_CUSTOMS, INSURANCE, ADDITIONAL_CHARGES. UI có 7 mục do phí hồ sơ/sân bay/hải quan được trình bày dưới Additional Charges; không tạo 8 popup cho đủ số.

| Dịch vụ | Đơn vị / công thức chốt | Quy tắc |
|---|---|---|
| Xe công ty | tổng ROAD km tính phí × một đơn giá KM | Không nhân số xe/ngựa hoặc chia giá US/UK; chính sách km có nguồn, không dùng đường chim bay |
| Cước hàng không | offer SHIPMENT hoặc HORSE_LEG phù hợp phạm vi | Coordinator lấy sau khi lập chặng; tách stall; chỉ đơn giá này thường AIRFARE_PENDING |
| Air stall | số slot × tổng AIR km × giá SLOT_KM | Shared 1, Wide 1.5, Private 3; đây là biểu phí bán công ty khi được duyệt, không giả là cách hãng báo giá |
| Lưu trú thêm | HORSE_NIGHT | Không cộng ngày cách ly lần nữa |
| Chăm sóc tăng cường | HORSE_KM; groom thêm PERSON_DAY | Chăm sóc cơ bản bắt buộc đã bao gồm, không bán lại |
| Thú y | HORSE_VISIT, TEST_SAMPLE theo loại, DOSE, CERTIFICATE | Không ép phí bác sĩ thành KM; Specialist xác định số lượng và chứng từ đã có |
| Cách ly | HORSE_DAY theo một gói Standard/Enhanced | Không cộng cả hai gói; RequiredDays do Specialist xác nhận, không do Customer tùy chỉnh |
| Hồ sơ/điều phối | ROUTE_KM tính một lần theo km ROAD+AIR đã chốt | Hiển thị trong Additional Charges; review/processing đã included, không cộng thêm phí hồ sơ 150 mẫu cũ |
| Handling/hải quan | HORSE_AIRPORT, DECLARATION | Nằm ở Additional Charges, chống gộp/thu trùng terminal/airline; không giả thuế bằng 0 |
| Bảo hiểm | giá gói theo ngựa và kỳ hợp đồng được insurer chấp thuận | Gói kết hợp không cộng mortality lần hai; declared value không phải phí; chưa có insurer thì không bán fixture |
| Chờ/phát sinh | HOUR và rule thời gian miễn phí/bước tính được cấu hình | Chưa xảy ra thì EVENT_ONLY, không mặc định cộng vào tiền phải trả |

Đơn giá công bố có thể đã biết nhưng km/số ngày còn QUANTITY_PENDING. Không coi NULL/pending là 0 hoặc tạo PaymentRequest từ estimate. Giá fixture 4, 0.6, 0.5, 100/130, 300/550 chỉ dùng seed demo được đánh dấu; không hard-code vào service production.

BigDecimal cho quantity/rate/amount, lưu đơn vị và công thức. Scope đầu dùng USD; không tự quy đổi tiền tệ khác. Tính các yếu tố đầy đủ rồi làm tròn amount 2 số thập phân theo quy tắc công bố. QuoteItem lưu snapshot rate/inputs; đổi tariff không đổi Quote đã issued.

Oversized hoặc thiếu cân nặng/height/body width: bỏ 1-slot khỏi API options, giữ lựa chọn rộng hơn ở trạng thái chờ fit/carrier. Backend kiểm lại khi ghi lựa chọn; request không hợp lệ trả lỗi có lý do, không tự nâng giá/chuyển gói âm thầm. Ngưỡng 480kg/170cm/80cm chỉ là fixture plugin: production lấy policy đã được xác nhận từ cấu hình/equipment/carrier. Không mở khảo sát tính khí và không hỏi chọn ngựa lần nữa.

Show all charges chỉ mở danh sách các dòng hiện có, không tạo phí mới. Gói optional không mặc định được chọn. Không cộng air stall hoặc handling lần nữa khi offer đã gồm: hiển thị included/breakdown hoặc dùng offer phân tách có bằng chứng.

## 5. Version, trạng thái và quyền

- Draft có thể sửa; submit tạo OrderRevision gồm snapshot bất biến và choices cho phiên bản đó. Sửa sau submit tạo revision; sau accept/khởi hành phải có đề xuất thay đổi được xét tác động, không overwrite lịch/giá được chấp nhận.
- TransportOrder là view hiện tại của yêu cầu; không lấy MAX(RevisionNo) làm phiên bản đang vận hành. TransportPlan.RevisionID và báo giá được chọn phải xác định đúng hợp đồng đang thực hiện.
- OrderDecision theo RevisionID, lý do bắt buộc với REJECTED/CHANGES_REQUESTED; Manager không tự Accept thay khách.
- Quote: DRAFT → APPROVED → ISSUED → ACCEPTED/REJECTED/EXPIRED; revision thay thế dùng SUPERSEDED. Đây là các mã CHECK đang có. Không thêm SUBMITTED/PENDING_REVIEW vào QuoteStatus nếu chưa có migration được review.
- User giữ ACTIVE/INACTIVE/LOCKED; không đổi sang PENDING_VERIFICATION vì sẽ vi phạm CHECK hiện tại. Auth baseline dùng INACTIVE + token để xác minh và không tự kích hoạt lại người đã bị admin ngừng sử dụng.
- Với các status còn VARCHAR không có CHECK: issue phải định nghĩa enum/state transitions, actor và điều kiện trước khi tạo endpoint; không nhận status tùy ý từ client.
- Customer lọc theo ownership từ token subject. StaffID là StaffProfile.StaffID, không phải UserID. Specialist/Coordinator/Driver chỉ case/leg được phân công; Manager được xem phạm vi điều hành.
- Chưa có @EnableMethodSecurity trong baseline: BE1 bổ sung/rà soát khi mở staff/domain APIs. Không mặc định @PreAuthorize đã có tác dụng. Cần kiểm quyền bằng cả role và ownership/assignment, không chỉ ẩn nút UI.

## 6. Gate, thanh toán và bằng chứng

- FinancialGate dựa Quote được khách accept và số allocation thực sự đã đối soát trừ refund/reversal phù hợp; không dựa PaymentReport hoặc Quote.TotalQuotedAmount như tiền thu.
- Không allocate vượt AmountReceived, hoàn vượt tiền còn được phép hoàn, hoặc nhân lặp giao dịch ngân hàng. Có transaction/lock và test hai thao tác đồng thời; currency phải khớp.
- LegalGate theo phase và revision/ngày/route. Pre-export complete không có nghĩa quarantine release đã xong. Thiếu quy định = pending assessment, không phải miễn giấy tờ.
- OperationalGate gồm resources/interval/capacity, stall fit, carrier acceptance, reservation còn hiệu lực. Price offer/HELD không phải CONFIRMED.
- Manager chỉ authorize khi cả ba gate đạt; driver depart kiểm lại ngay trong transaction và phải có HorseReceipt đúng microchip. Thay dữ liệu liên quan vô hiệu kết quả gate cũ.
- Interval xung đột khi startA < endB và startB < endA; xét buffer/khả năng di chuyển. MVP không tự pack nhiều booking vào stall; một stall vật lý không có hai assignment chồng lịch.
- Handover airport/quarantine không hoàn tất order. Final delivery cần release và kết quả/acknowledgement đúng người nhận. Operational completion tách financial closure; không coi chờ im lặng là customer accept.
- Driver không phê duyệt y tế, giấy phép hoặc tiền. Cấp cứu liên hệ hỗ trợ và bảo vệ ngựa trước, không chờ nút duyệt chi phí; thu thêm cần revision/consent sau đó.

## 7. Khoảng trống schema phải giải quyết ở issue liên quan

Không thực hiện migration trong đợt lập kế hoạch này. Các hướng sau là đề xuất kỹ thuật cần được người phụ trách flow hoàn thiện và review trong PR.

| Gap | Flow / owner | Cách xử lý cần làm rõ |
|---|---|---|
| Draft chưa có nơi lưu service choices vì OrderRevision.SubmittedAt bắt buộc | F03/F05 BE1 | Đề xuất DraftPayload JSON trên TransportOrder hoặc persistence draft tương đương; không tạo fake submitted revision |
| Groom days, boarding nights, care preferences chưa có cột trong OrderServiceChoice | F05 BE1 | SelectionInputs JSON có schema theo ServiceCode hoặc bảng con thực sự cần; không nhét vào SpecialNeeds |
| Desired pickup, receiver/contact, request purpose chưa đầy đủ | F03 BE1 | Xác định trường query được cần cột; thông tin snapshot khác có thể nằm JSON có contract; UTC/timezone rõ |
| Quote thiếu prepared/submitted metadata, điều khoản và lịch đặt cọc | F06/F07 BE1 | Snapshot terms/payment schedule; lịch thu được đồng ý phải có nguồn trước khi sinh PaymentRequest |
| QuoteItem không có NULL quantity/rate, không có parent-included field riêng | F05/F06 BE1 | Pending chỉ ở estimate DTO/selection, không ghi 0 giả; snapshot CalculationInputs có includedInItemId hợp lệ hoặc migration rõ |
| QuarantineChoice.RequiredDays CHECK > 0 nhưng UI có NOT_REQUIRED = 0 | F05/F08 BE1+BE2 | Phân biệt NOT_REQUIRED với UNKNOWN và không insert 0 vi phạm CHECK; migration applicability nếu cần, giữ lịch sử lựa chọn |
| Fit limits/load/carrier policy thiếu trong Horse/StallEquipment | F05/F09 BE1+BE2 | Policy có nguồn/config và giới hạn equipment, không hard-code fixture làm chuẩn hãng |
| TransportPlan chưa tham chiếu Quote được accept | F06/F10/F11 BE2 chủ trì, BE1 review | Đề xuất AcceptedQuoteID và quy tắc cùng revision, cùng order; chọn một plan hiện hành |
| Source/evidence và expiry của reservation/procedure chưa đủ mọi màn | F08/F10 BE2 | Bổ sung tối thiểu khi AC cần; file key không là public URL không kiểm quyền |
| Thông tin approve/evidence của refund và proof ngân hàng chưa đầy đủ | F07 BE1 | Metadata hoặc liên kết audit/file được định nghĩa, không claim TRANSFERRED không chứng cứ |
| Cancellation, customer change consent, incident acknowledgement chưa có bảng chuyên biệt | F13/F14 BE2 + BE1 | Dùng OperationalAdjustment/OrderRevision/Quote/AuditEvent khi đủ; migration khi thiếu, không tạo ChangeRequest giả không tồn tại |
| Archive status/optimistic version ở một số entity chưa có | F02/F09/F15 | Không xóa hồ sơ có lịch sử; thêm cột/version nếu flow cần với backfill/check rõ |

## 8. Phối hợp Java và API

Giữ Spring Boot 3.5.16, Java 21, Maven Wrapper 3.9.9 và monolith phân lớp đang có. Không đổi sang microservices/Express/MongoDB hoặc regenerate auth baseline. Tên property Java camelCase ánh xạ rõ cột SQL hiện tại; bảng User phải quote. JPA validate trên database thật.

Các feature mới có thể dùng subpackage dưới controller/service/repository/entity/dto theo nghiệp vụ; không di chuyển toàn bộ baseline trong cùng PR feature. Controllers mỏng, transaction tại service, DTO thay entity trong response. File storage dùng adapter có kiểm quyền, MIME/size/path và opaque key; không public toàn bộ thư mục upload.

BE1 sở hữu financial/booking facades; BE2 sở hữu legal/operational facades. Hợp đồng typed, BigDecimal, UTC và blocker codes thống nhất trước PR phụ thuộc. Không call REST vào chính monolith, không query trực tiếp repository của người kia để vượt business validation.

| Hợp đồng gợi ý | Owner | Consumer |
|---|---|---|
| CurrentActor / DomainAccessPolicy: UserID, CustomerID, StaffID, role, assignment | BE1 | Cả hai |
| CommercialReadiness: revision, acceptedQuoteId, obligations, paid amount, blockers | BE1 | BE2 F11/F14 |
| QuarantineSelection: gói đã chọn, price basis, áp dụng required days đã xác nhận | BE1 | BE2 F08/F10 |
| ComplianceReadiness: phase/revision/asOf, clearance và blockers | BE2 | BE1 quote, BE2 departure |
| ItinerarySummary: planId/revision, ROAD/AIR km có nguồn, times, confirmations | BE2 | BE1 estimate/quote |
| AuditWriter / NotificationWriter append-only | BE1 | Cả hai, bổ sung events của module mình |

Facades có thể là service/interface trong process; tên là hướng thống nhất, không yêu cầu dựng framework mới. Contract tests là việc chung: cả hai cung cấp producer tests và consumer cases, không chỉ mock trả READY trong endpoint thật.

## 9. Quy tắc Java và điều kiện Done

Áp dụng PDF Java Code Conventions được người dùng cung cấp: 4 spaces, không tab; 80 ký tự kể cả import; continuation 8 khi thích hợp; class/interface PascalCase, method/field camelCase, constant UPPER_SNAKE_CASE; một public type/file, comment đầu file đúng tác giả/ngày/bản quyền dự án, package rồi imports; static fields → instance fields → constructors → methods; một khai báo/câu lệnh mỗi dòng; local declarations đầu block; braces kể cả một dòng; Javadoc API; một dòng trống giữa methods, hai giữa phần source; giải thích lý do bằng comment, không comment từng câu lệnh; private fields, static qua class, constants cho magic values, không assignment lồng; switch có default và comment fall-through; tránh file quá 2.000 dòng.

Không gắn license/copyright của Sun lên code dự án. Java 21/Spring annotations/try-with-resources là bổ sung hiện đại. Checkstyle hiện chạy cả src/main và src/test; pass tool không thay review quy tắc ngữ nghĩa. Nếu không có PDF trên máy người khác, dùng bản tóm tắt này và config/checkstyle.xml; không tải lại bản không xác định rồi đổi chuẩn.

Done của một issue: API contract + actor/ownership; persistence mapping/migration khi cần; transition/validation và error; tests happy path, unauthorized/ownership, business failure và concurrency khi có tài nguyên/tiền; ./mvnw verify pass; SQL Server integration cho thay đổi persistence; migration/rollback hoặc forward-repair hướng dẫn; docs/API updated; PR được thành viên còn lại review và merge dev.

Không test bằng cleanup trên EquiTransit thật. AuthFlowTest có guard database test và chỉ là mẫu riêng của auth: không sao chép deleteAll() cho module mới. Transactional rollback/fixture ID và database test riêng bắt buộc. Secrets/env/outbox/log/target/IDE cá nhân không commit. Không log password, token hoặc hồ sơ thú y nhạy cảm; không trả supplier cost/margin/internal notes cho Customer.

## 10. Phân công và trình tự

BE1 = nhana123: F01–F07, F15–F16. BE2 chưa có Assignee: F08–F14, gắn label be2 để nhận sau. Không gán mặc định collaborator AIVIETNAM-AIO-felixdoit.

- Wave 1: BE1 review F01 và nền permission của F15, làm F02/F03; BE2 làm catalog F09 và cơ sở storage/rule của F08.
- Wave 2: BE1 F04/F05/F06; BE2 hoàn thiện F08 và F10. Chốt facade/API cho quote/itinerary/legal trước tích hợp.
- Wave 3: BE1 F07 và F16 audit/notification nền; BE2 F11/F12, kiểm đủ 4 flow giảng viên.
- Wave 4: BE2 F13/F14; BE1 hỗ trợ quote revision/refund và hoàn thiện admin/report của F15/F16; chạy demo end-to-end.

Nhánh mỗi issue: feature/<issue>-<flow>; migration code cùng PR flow, tránh tách thay đổi DB và API không thể triển khai riêng. Không force-push dev/main; commit tập trung, PR vào dev; cross-review owner khác. Skill không tự cấp quyền gửi thông báo, merge, deploy hay sửa DB thật ngoài yêu cầu con người đã giao.
