# EquiTransit — Software Requirements Specification

Phiên bản 1.0 · Ngày 13/10/2026 · Phạm vi: backend Java Spring và hợp đồng cho frontend.

## 1. Mục đích, nguồn và trạng thái

Tài liệu thống nhất nghiệp vụ của website vận chuyển ngựa quốc tế với database
SQL Server `DESKTOP-9VRARFD / EquiTransit` sau migration 002. Đây là yêu cầu để
triển khai và nghiệm thu, không phải tuyên bố tất cả chức năng đã có API.
Auth, xác minh email, đăng nhập JWT, nền kiểm quyền và Swagger đã có code;
các flow thương mại/vận hành còn lại cần triển khai theo backlog.

Thứ tự xử lý mâu thuẫn: quyết định mới nhất của chủ dự án → SRS này → hợp đồng
phối hợp BE → thiết kế/model được đối chiếu → tài liệu cũ. Schema live quyết định
tên, kiểu và ràng buộc hiện có. Yêu cầu mới cần thay schema phải có migration
tiếp theo, không sửa lại migration đã áp dụng hoặc bịa trường không tồn tại.

| Nguồn | Phạm vi chứng cứ |
|---|---|
| [Figma SWP](https://www.figma.com/design/SGnLGpXxE6Ut0iV8rSoNQe/SWP?node-id=0-1) | Metadata live ngày 13/10, 63 frame cấp cao hiển thị; nội dung/screenshot live C03, C17 và BOOK02 |
| SWP-latest-design-2026-10-10.json do người dùng cung cấp | Export đầy đủ có thời điểm 2026-10-10T14:28:05.540Z; tên file không chứng minh bản mới hơn |
| SHA256 export | 22aa9e2bf9be9ed2f651d4687b83cedc53aacc034295e99cf5011e3e0083193a |
| SWP-RE/booking-model.js, english-screens.js | Đơn vị, lọc stall, 7 mục booking và các màn actor; dữ liệu demo không phải cấu hình thật |
| REQUIREMENTS.docx, CHECKPOINT_1, đặc tả nghiệp vụ local | Actor, hồ sơ, điều phối, sự cố, bàn giao; những đoạn cũ trái scope dưới đây không áp dụng |
| [Schema hiện tại](../database-schema.md) | 55 bảng nghiệp vụ, 594 cột, 114 ràng buộc FK; hai bảng kỹ thuật |
| Oracle Java Coding Standards.pdf, skills BE1/BE2 | Quy tắc source Java và phối hợp; không phải quy định pháp lý hoặc biểu phí |

MCP giới hạn lượt đọc nên chưa kiểm tra screenshot của toàn bộ file Figma live.
Export đầy đủ 10/10 và metadata live 13/10 được dùng cùng nhau; thay đổi sau
export ở những màn chưa có nội dung live cần xác nhận khi implement màn đó.
Frame hidden, backup, reference và flow air stall nhiều ngựa cũ không được
coi là màn production. Không đưa dữ liệu khách hàng thật hoặc PDF nguồn vào Git.

## 2. Scope và actor

Website của một công ty, khách hàng cá nhân. Không có Organization, thành viên
tổ chức, club account hoặc portal Admin riêng. Supplier là đối tác dịch vụ,
có thể là công ty; không phải khách hàng tổ chức.

Một order đã submit có đúng một ngựa thuộc khách hàng đó. Một hành trình có thể
có nhiều chặng ROAD/AIR và nhiều phiên bản kế hoạch. Không tự ghép nhiều order
thành một chuyến. Shared stall là yêu cầu về phần chỗ sử dụng; việc xác nhận chỗ
thực tế cần hãng/đối tác. Chưa tích hợp API hãng bay, ngân hàng, cơ quan nhà nước
hoặc bảo hiểm; hệ thống ghi nhận kết quả ngoài hệ thống và bằng chứng.

| Actor | Quyền nghiệp vụ | Giới hạn |
|---|---|---|
| CUSTOMER | Quản lý ngựa, tạo/sửa yêu cầu, chọn dịch vụ, nộp hồ sơ, chấp nhận quote, báo chuyển khoản, theo dõi, yêu cầu thay đổi, phản hồi bàn giao | Chỉ dữ liệu của mình; không duyệt tiền, hồ sơ hoặc tự giảm cách ly bắt buộc |
| MANAGER | Đánh giá yêu cầu, phân công, duyệt quote, đối soát/hoàn tiền, kiểm gate, quản lý nhân viên/giá và báo cáo | Có audit; không coi quyền Manager là quyền bỏ qua bằng chứng/gate |
| SPECIALIST | Đánh giá điều kiện tuyến, yêu cầu/kiểm giấy tờ, thủ tục, clearance và điều kiện cách ly | Case được phân công; không xác nhận tiền hoặc lái xe |
| COORDINATOR | Lập kế hoạch/chặng, đặt đối tác, phân công tài nguyên, theo dõi/sự cố/bàn giao | Case/chặng được phân công; không tự miễn quy định hoặc tự nhận khách đã trả tiền |
| DRIVER | Tiếp nhận ngựa, bắt đầu/chạy chặng được giao, cập nhật mốc/chăm sóc/sự cố/bàn giao | Chặng được giao; không duyệt hồ sơ, y tế, quote hoặc tiền |

Một User có một Role. Customer/StaffProfile liên kết User; StaffID khác UserID.
Đăng ký công khai chỉ tạo CUSTOMER. Quyền phải kiểm ở backend theo role và
ownership/assignment đang hiệu lực, không dựa ID khách gửi hoặc nút ẩn ở UI.

## 3. Hành trình nghiệp vụ

Đăng ký/xác minh → hồ sơ ngựa → order nháp/chọn dịch vụ → submit revision →
Manager đánh giá/phân công → Specialist đánh giá pháp lý và Coordinator lập
itinerary nháp → chuẩn bị/duyệt/phát hành quote → khách chấp nhận → yêu cầu và
đối soát thanh toán → xác nhận tài nguyên/hồ sơ → duyệt xuất phát và nhận ngựa →
thực hiện các chặng/cách ly → bàn giao cuối → quyết toán và đóng đơn.

Đánh giá pháp lý, lập kế hoạch nháp và chuẩn bị báo giá có thể song song.
Chấp nhận quote không đồng nghĩa nhận tiền, chỗ bay xác nhận hoặc đã được phép
xuất phát. Hoàn tất vận hành khác hoàn tất tài chính.

## 4. Yêu cầu chức năng và nghiệm thu

Mỗi FR tương ứng một flow/issue. Những điều kiện nghiệm thu dưới đây bổ sung
checklist trong [backlog](../team/BACKLOG.md); enum/API cụ thể phải chốt trước
khi implement nếu schema chưa có CHECK. Mọi thao tác thất bại phải giữ dữ liệu
nhất quán, trả mã lỗi rõ và không tiết lộ dữ liệu người khác.

### FR-01 — Tài khoản và bảo mật (BE1, issue #1)

Đăng ký cá nhân, xác minh email, đăng nhập, quên/đổi mật khẩu và xem tài khoản
hiện tại. Lưu password hash, token xác minh/recovery dạng hash và hạn dùng.
Không cho client tự chọn role; email/token sai, hết hạn hoặc đã dùng phải bị
từ chối. Không đăng nhập tài khoản chưa được phép sử dụng. Không log token hoặc
mật khẩu. Quyền nhạy cảm kiểm lại trạng thái tài khoản tại thời điểm thao tác.
Persistence: Role, User, Customer, StaffProfile, AuthenticationToken.

### FR-02 — Hồ sơ ngựa (BE1, issue #2)

Customer tạo/xem/sửa ngựa của mình: định danh, giới tính, cân nặng, chiều cao,
chiều rộng, thời điểm đo, handling, feed/water, stall preferences và chỉ dẫn thú y
có nguồn. Cho phép hồ sơ nháp chưa đủ đo; không coi thiếu số đo là 0 hoặc đủ điều
kiện bay. Microchip đã có phải duy nhất theo filtered index hiện tại. Validate
giá trị dương cho số đo được nhập; dùng Horse.RecordVersion chống ghi đè cập
nhật đồng thời. Cập nhật hồ sơ không sửa snapshot của order đã submit.
Persistence: Horse và các trường mới trong migration 002.
AC: người khác không đọc/sửa được; trùng microchip bị từ chối; số đo thiếu vẫn
lưu hồ sơ nhưng không tự mở lựa chọn 1 slot. Archive ngựa chưa được chốt trong
Figma nên không tự thêm chức năng này; không xóa lịch sử vận chuyển.

### FR-03 — Order, submit và revision (BE1, issue #3)

Lưu route, địa chỉ, timezone, lịch mong muốn/deadline/event, mục đích nhập,
người nhận và sân bay transit. Order nháp lưu DraftPayloadJSON; không tạo
OrderRevision có SubmittedAt giả để giữ draft. Submit kiểm ownership, đúng một
ngựa, dữ liệu cần cho đánh giá và tạo snapshot nhất quán gồm route, ngựa và
service inputs. TransportOrder giữ dữ liệu hiện tại, OrderRevision giữ các
phiên bản đã gửi; không phải hai order riêng.

CurrentRevisionID phải thuộc chính order. Khi sửa chưa xuất phát, tạo revision
mới và đánh giá lại những kết quả bị ảnh hưởng; không sửa nội dung revision cũ.
Sau authorize/departure, khách gửi OrderChangeRequest; không ghi đè kế hoạch
đang vận hành. Một ngựa có thể có lịch sử nhiều order, nhưng phải kiểm trùng
lịch order hoạt động trong transaction khi nhận/chốt kế hoạch.
AC: không thêm ngựa thứ hai; cập nhật stale RecordVersion bị từ chối; đổi ngày
làm expiry/gate/quote được đánh giá lại; request sai ownership bị chặn.

### FR-04 — Đánh giá và phân công (BE1, issue #4)

Manager xem revision, yêu cầu bổ sung hoặc quyết định theo state contract;
OrderDecision lưu RevisionID, ManagerID, lý do và thời điểm. Phân công
Specialist/Coordinator qua OrderAssignment; kiểm role, trạng thái tài khoản và
lịch/phạm vi công việc. Không duyệt một revision khác với revision đang xem.
AC: quyết định truy được đúng phiên bản; staff chưa được giao không có quyền
case; thay nhân viên không xóa assignment/audit trước.

### FR-05 — Booking dịch vụ và minh bạch phí (BE1, issue #5)

Bảy mục UI: Transportation, Available Air Stalls for Your Horse, Horse Care,
Veterinary, Quarantine Packages, Insurance, Additional Charges. Mỗi popup làm
việc với một ngựa đã chọn. OrderServiceChoice lưu lựa chọn của revision,
SelectionInputsJSON lưu quantity/care/preferences có schema theo dịch vụ.
StallChoice, QuarantineChoice, InsuranceChoice lưu dữ liệu chuyên biệt.

Air stall lọc theo số đo snapshot, loại stall, policy có nguồn còn hiệu lực và
giới hạn thiết bị. Các mức 1/1.5/3 là loại lựa chọn, không phải slot đã được
hãng xác nhận. Ngựa quá giới hạn 1 slot không thấy/chọn được 1 slot; có thể có
1.5 và private 3 nếu đáp ứng policy. Thiếu số đo/policy trả PENDING và lý do,
không tự coi là fit hoặc gán 480 kg làm chuẩn. Private là cả stall 3 slots.
Backend kiểm lại khi confirm lựa chọn và trước xác nhận vận hành.

Document processing/coordination và Airport & Customs hiển thị trong Additional
Charges. Mũi tên “Show all” mở toàn bộ khoản phí và cách tính: bắt buộc/tùy chọn,
included/tính riêng/pending, đơn vị, quantity, rate, currency và điều kiện phát
sinh. Dịch vụ hồ sơ tính một lần; không thu lại ở màn hồ sơ. Quarantine là gói
dịch vụ công ty/đối tác đặt, tính theo ngày đã được Specialist xác nhận; không
gộp chi phí lưu trú cách ly vào phí xử lý giấy tờ. Basic care included thì không
thu thêm cùng phần chăm sóc đó. Insurance NONE/existing không thu premium mới;
mua mới cần điều khoản/acceptance từ insurer, lựa chọn UI chưa kích hoạt cover.

AC: không có nút next horse; cấm 1 slot cho ngựa vượt giới hạn; client sửa rate
không ảnh hưởng giá server; không giảm số ngày bắt buộc; Show all không giấu
phí bắt buộc. NOT_REQUIRED không insert RequiredDays=0 trái CHECK hiện tại:
không có QuarantineChoice hoạt động, kết quả applicability nằm đánh giá pháp lý
và snapshot. UNKNOWN phải là pending, không phải miễn cách ly.

### FR-06 — Báo giá phiên bản (BE1, issue #6)

Chuẩn bị từ tariff/supplier offer còn hiệu lực, itinerary và lựa chọn đã đánh
giá. QuoteItem snapshot quantity/rate/calculation inputs; không dùng giá demo.
Quote ghi PreparedBy, SubmittedAt, ApprovedBy, IssuedBy, thời hạn, terms và
cancellation terms; QuotePaymentSchedule lưu lịch nghĩa vụ thanh toán.
Manager duyệt trước phát hành. Khách chỉ chấp nhận quote đã phát hành, còn hạn,
thuộc mình và đúng revision; lưu AcceptedAt/AcceptedByCustomerID.

Không sửa quote đã chấp nhận; quote thay thế có version và lịch sử. Pending
quantity/rate nằm estimate DTO, không insert 0 giả vào QuoteItem để đủ schema.
AC: tổng item khớp TotalQuotedAmount theo quy tắc rounding; khoản included
không thu trùng; lịch thu có nguồn từ terms; accept lặp không sinh nghĩa vụ đôi.

### FR-07 — Thanh toán/đối soát/hoàn tiền (BE1, issue #7)

PaymentRequest liên kết Quote và schedule cùng quote. Khách báo chuyển khoản
qua PaymentReport, kèm thời gian/currency và bằng chứng. Manager kiểm chứng
PaymentTransaction, phân bổ qua PaymentAllocation; report không phải tiền thực
nhận. Refund ghi người yêu cầu/duyệt, bank reference/proof và trạng thái thật.
AC: không allocate vượt số tiền thực nhận, không refund vượt phần còn hoàn,
currency khớp; giao dịch lặp/đồng thời không cộng tiền hai lần; chưa có chứng cứ
không đánh dấu đã hoàn. Không xây API chuyển tiền ngân hàng trong MVP.

### FR-08 — Hồ sơ và tuân thủ (BE2, issue #8)

Specialist xác định checklist theo route, transit, phase, ngày và quy định có
nguồn/version. DocumentTypeCatalog thống nhất loại giấy tờ. DocumentRequirement
ghi hướng dẫn, phạm vi/ngựa, validity và lý do case. HorseDocument và
DocumentVersion giữ các phiên bản file; DocumentSubmission/DocumentReview ghi
nộp và kết quả. AuthorityProcedure ghi cơ quan, phase, contact, hold, evidence,
expiry; ComplianceClearance ghi đánh giá theo giai đoạn.
AC: file mới không xóa chứng cứ đã review; đổi route/ngày kiểm lại tính phù hợp;
thiếu rule trả pending assessment; pre-export đạt không đồng nghĩa đã quarantine
release. Specialist không tự tạo giấy phép của cơ quan bằng thao tác approve.

### FR-09 — Tài nguyên và fit (BE2, issue #9)

Quản lý FleetVehicle, StallEquipment, Supplier và StallFitPolicy. Policy APPROVED
phải đủ giới hạn, nguồn và người/thời điểm review; bản nháp không dùng xác nhận
fit. Kiểm bảo trì, hạn tài liệu, kích thước/capacity và overlap có buffer.
AC: không hard-code ngưỡng demo; tài sản không đủ điều kiện không được xác nhận;
cùng stall vật lý không có hai assignment chồng lịch. Danh mục không tự chứng
minh hãng còn chỗ; giữ lịch sử khi ngừng sử dụng tài nguyên.

### FR-10 — Kế hoạch đa phương thức (BE2, issue #10)

Coordinator tạo TransportPlan version, TripLeg ROAD/AIR, timezone, thời gian,
flight/connection/rest/care instructions. TripAssignment và StallAssignment ghi
phân công thực tế; FlightReservation/QuarantineReservation ghi confirmation,
nguồn, người xác nhận và expiry. InsurancePolicy ghi policy thật/verification.
SupplierOffer chỉ là giá đầu vào, không phải booking đã xác nhận.

Plan.OrderID/RevisionID phải khớp, AcceptedQuoteID thuộc cùng revision và có
lịch sử acceptance. CurrentPlanID thuộc order. Có thể lập plan nháp trước quote,
nhưng không activate/authorize khi chưa đủ điều kiện. AC: không nối quote hoặc
leg của đơn khác; HELD/offer không được hiển thị CONFIRMED; thay plan có version.

### FR-11 — Gate và nhận ngựa (BE2, issue #11)

Kiểm financial, legal theo phase và operational gate trên dữ liệu còn hiệu lực;
lưu kết quả/thời điểm trên TransportPlan. Manager authorize khi đủ cả ba.
Driver nhận ngựa qua HorseReceipt đúng plan/leg, microchip/passport, tình trạng,
vị trí và evidence. Bắt đầu chặng ROAD kiểm lại gate/authorization/receipt trong
transaction. AC: tiền report, clearance hết hạn, reservation hết hạn, sai ngựa
hoặc sai leg đều chặn xuất phát; thay dữ liệu quan trọng vô hiệu gate cũ.

### FR-12 — Thực hiện chặng và chăm sóc (BE2, issue #12)

Driver/Coordinator cập nhật LegMilestoneLog có nguồn, mốc/thời gian, location,
ETA và delay; HorseWelfareLog ghi tình trạng, thức ăn/nước, care và chỉ dẫn.
Sửa welfare tạo bản thay thế liên kết SupersedesLogID cùng leg, giữ lịch sử.
AC: chỉ assignment hợp lệ thao tác; thứ tự mốc theo state contract; phân biệt
thời gian ghi nhận với thời gian sự kiện; không giả GPS live trên máy bay.

### FR-13 — Sự cố và thay đổi có kiểm soát (BE2, issue #13; BE1 hỗ trợ tiền)

IncidentReport ghi sự cố, hành động ngay, liên hệ, acknowledgement, resolution
và evidence. OrderChangeRequest ghi nhu cầu khách trên base revision; phản hồi
không tự sửa chuyến. OperationalAdjustment liên kết change request, đề xuất
tác động, phản hồi khách và approval. Thay đổi giá cần quote/consent thích hợp.
AC: customer không trực tiếp sửa chặng đang chạy; cấp cứu bảo vệ ngựa và liên hệ
ngay, không chờ nút duyệt chi phí; không coi im lặng là đồng ý phụ phí.

### FR-14 — Bàn giao và quyết toán (BE2, issue #14)

TripHandover ghi loại bàn giao, người nhận/thẩm quyền/chữ ký, tình trạng ngựa,
location/evidence và phản hồi khách. Bàn giao sân bay/cách ly không tự hoàn tất
đơn. Final delivery kiểm release, người nhận và kết quả bàn giao. ActualCost
ghi chi phí thật, người ghi và chứng cứ; leg phải thuộc plan. Quyết toán kiểm
nghĩa vụ, tiền thu/hoàn và khoản đang tranh chấp.
AC: không tự customer-confirm; sai receiver/release chặn hoàn tất; có thể hoàn
tất vận hành mà tài chính còn mở; không làm mất case ngoại lệ.

### FR-15 — Quản trị nội bộ và giá (BE1, issue #15)

Manager cấp tài khoản staff/role phù hợp, ngừng sử dụng có audit, quản lý Supplier
và ServiceTariff phiên bản/thời gian hiệu lực. Không sửa giá lịch sử đã snapshot.
AC: CUSTOMER không tạo staff, không sửa tariff; tariff không nguồn/chưa có giá
thật không được dùng phát hành quote. Không thêm role ADMIN hoặc Organization.

### FR-16 — Dashboard, notification và audit (BE1, issue #16)

Dashboard tổng hợp từ dữ liệu thật theo quyền; Notification lưu thông báo và
AuditEvent truy thao tác quan trọng. Customer.EmailNotificationsEnabled lưu
tùy chọn thông báo; email transactional/security phải có chính sách riêng.
AC: không trả margin/supplier cost/internal notes cho Customer; log không chứa
secret; notification lỗi không làm mất giao dịch nghiệp vụ; báo cáo phân biệt
quote tổng với tiền thu thật và vận hành hoàn tất với financial closure.

## 5. Quy tắc chung bắt buộc

| Mã | Quy tắc |
|---|---|
| BR-01 | Một order submit đúng một ngựa; UQ OrderID trên TransportOrderDetail chỉ chặn ngựa thứ hai, service phải chặn thiếu ngựa khi submit |
| BR-02 | Snapshot revision gồm dữ liệu ngựa/route/service tại lúc gửi; không ghi đè lịch sử khi sửa master |
| BR-03 | Một ngựa có lịch sử nhiều order; kiểm overlap theo khoảng vận hành khi chốt, không cấm mọi order tương lai chỉ vì có order nháp |
| BR-04 | Chọn service ≠ quote acceptance ≠ tiền thực nhận ≠ confirmation ≠ clearance ≠ depart |
| BR-05 | Dùng BigDecimal/DECIMAL, currency rõ; ROAD km, AIR slot × air km, coordination route km tính một lần; công thức cần nguồn và snapshot |
| BR-06 | Rate/quantity chưa xác nhận trả pending và blocker; không giả 0, không dùng fixture USD/ngưỡng 480 kg làm dữ liệu thật |
| BR-07 | Tính theo đơn vị thực: veterinary/test, grooming day, boarding night, quarantine horse/day; số ngày bắt buộc do đánh giá có nguồn |
| BR-08 | Included phải dẫn về khoản đã bao gồm; tự thu xếp ghi provider/evidence, không thu lại phí provider đó |
| BR-09 | Mọi gate phải xét đúng revision/plan, as-of và expiry; kết quả READY cũ không thay kiểm tại hành động xuất phát |
| BR-10 | Overlap: startA < endB và startB < endA, cộng buffer/khả năng di chuyển; khóa/transaction khi phân công để chống race |
| BR-11 | Revision đang chờ thay đổi có thể khác plan đang vận hành; không chuyển CurrentPlanID chỉ vì khách gửi revision |
| BR-12 | FK chỉ chứng minh tham chiếu tồn tại; role, ownership, assignment, sums, overlap và consent cần service validation |

## 6. Dữ liệu, JSON và trạng thái

Chi tiết chính xác mọi cột, PK, unique, FK và CHECK ở
[database-schema.md](../database-schema.md). SQL `timestamp` của RecordVersion
là rowversion nhị phân chống ghi đè, không phải ngày giờ. Thời gian sự kiện lưu
UTC theo quy ước ứng dụng; các cột DATETIME2 tự nó không lưu timezone. Lưu tên
timezone riêng cho diễn giải ngày giờ địa phương; API dùng ISO 8601 có offset.

DraftPayloadJSON, SelectionInputsJSON, OrderSnapshot và RequestedChangesJSON
phải có contract schemaVersion. Draft chứa horseId, route, scheduling, receiver,
service inputs; snapshot submit chứa giá trị đã kiểm và nguồn/version liên quan.
Không giữ password/token, entity graph hoặc tùy ý ghi status/price client vào
snapshot. SelectionInputs định nghĩa field/type/unit/range riêng từng ServiceCode;
reject unknown fields quan trọng. ISJSON chỉ kiểm cú pháp, service kiểm nội dung.

QuoteStatus CHECK hiện tại: DRAFT, APPROVED, ISSUED, ACCEPTED, REJECTED,
EXPIRED, SUPERSEDED. StallChoice.FitStatus: PENDING, SUITABLE, UNSUITABLE;
StallFitPolicy.Status: DRAFT, APPROVED, INACTIVE. ImportType: TEMPORARY,
PERMANENT, NEEDS_ADVICE. NULL ở cột mới của dữ liệu cũ nghĩa là chưa biết/chưa
ghi nhận, không tự coi là false, zero hoặc approved.

Với cột trạng thái chưa có CHECK, enum/transition cụ thể phải định nghĩa trong
PR flow và kiểm bằng service trước endpoint; đây không phải quyền nhận mọi
chuỗi client. Quote DRAFT → APPROVED → ISSUED → ACCEPTED chỉ khi đúng
actor/điều kiện; trạng thái lịch sử không được xóa acceptance của plan đã dùng.
Quy tắc revoke/cancel phải giữ audit và xử lý hậu quả tài chính/vận hành.

## 7. Traceability UI và database

| Màn/nhóm | Requirement | Persistence chính |
|---|---|---|
| A01–A03 | FR-01 | Role, User, Customer, AuthenticationToken |
| C02/C03/C07 | FR-02 | Horse; snapshot trong TransportOrderDetail |
| C05–C10/C16 | FR-03/05/06 | TransportOrder, OrderRevision, OrderServiceChoice, Quote, QuoteItem |
| BOOK02 live node 206:329 | FR-05/09 | StallChoice, StallFitPolicy, StallEquipment; assignment thực tế ở StallAssignment |
| Quarantine Packages / Additional Charges | FR-05/08/10 | QuarantineChoice, ServiceTariff, QuoteItem, QuarantineReservation |
| C17 live node 103:18013 | FR-01/16 | User, Customer; phần Organization còn trong design phải bỏ theo scope |
| C03 live node 103:16687 | FR-02 | Horse handling/feed/stall/veterinary fields |
| M01–M13 | FR-04/06/07/11/15/16 | OrderDecision, OrderAssignment, Quote, PaymentTransaction, StaffProfile, ServiceTariff, AuditEvent |
| S01–S05 | FR-08 | DocumentRequirement, DocumentVersion, DocumentReview, AuthorityProcedure, ComplianceClearance |
| F01–F08 Coordinator, bỏ frame backup | FR-09–14 | TransportPlan, TripLeg, reservations, assignments, incidents, handovers, ActualCost |
| D01–D04 | FR-11–14 | HorseReceipt, LegMilestoneLog, HorseWelfareLog, IncidentReport, TripHandover |

Tên frame không đủ để kết luận mọi field đã đọc live. Mapping cấp nhóm ở bảng
này được đối chiếu export/model; khi làm API flow phải đối chiếu chi tiết màn.

## 8. Phi chức năng, API và quy tắc code

- Java 21, Spring Boot 3.5.16, Maven Wrapper; monolith Controller → Service →
  Repository → SQL Server. Controller mỏng, transaction tại service, DTO tách
  entity; mapping rõ tên SQL, User được quote, schema validate thay auto-update.
- Tuân thủ Oracle Java Coding Standards đã cung cấp và Checkstyle: 4 spaces,
  không tab, giới hạn 80 ký tự, naming/Javadoc/braces; không gắn copyright Sun
  lên code dự án. Đọc skill của owner flow trước khi code.
- Auth/role/ownership/assignment ở server; least privilege; hash password;
  secrets qua environment/local ignored config; CORS theo FE được cấu hình.
  Customer không nhận supplier cost, margin hoặc internal medical/finance notes.
- Upload kiểm MIME thật, size, tên/path, quyền truy file và opaque storage key;
  file key không phải public URL. Không execute nội dung file hoặc tin extension.
- Validation có mã blocker và field error rõ; phân trang/filter cho danh sách;
  OpenAPI mô tả DTO, quyền và lỗi. Không bịa SLA số giây chưa được đo/chốt.
- Idempotency và concurrency cho submit/accept/allocate/resource assignment;
  transaction không sinh bản ghi nửa chừng. Tiền và gate cần tests cạnh tranh.
- CI chạy Maven verify; tests gồm happy path, unauthorized/ownership, failure
  và concurrency thích hợp. Persistence SQL Server kiểm trên database test riêng,
  không cleanup/seed dữ liệu giả trên EquiTransit thật.

## 9. Cấu hình còn phải cung cấp và giới hạn nghiệm thu

Schema sẵn sàng lưu không đồng nghĩa đã có dữ liệu đủ vận hành. Công ty phải
cung cấp tariff thật, supplier/carrier limits có nguồn, quy định tuyến còn hiệu
lực, payment/cancellation terms, thông tin nhận chuyển khoản công khai từ cấu
hình doanh nghiệp, storage/mail settings và chính sách state transitions chưa
có CHECK. Không seed ngân hàng, legal rule hoặc 480 kg từ thiết kế demo.

Email update trên C17 cần luồng xác minh an toàn; không tuyên bố có API cập nhật
email chỉ vì User.Email tồn tại. Archive ngựa, ranking nhiều phương án, ghép chỗ
tự động và tích hợp bên ngoài chưa thuộc bản triển khai này. Residual tổ chức,
hai ngựa/order và phí demo cố định trong thiết kế cũ không thay scope đã chốt.

## 10. Baseline triển khai và Definition of Done

Migration [002](../../database/migrations/002_align_figma_without_organization.sql)
đã được áp dụng trên DB hiện tại ngày 13/10 sau backup COPY_ONLY/CHECKSUM,
VERIFYONLY, restore test riêng, chạy lặp migration và tests cấu trúc rollback.
Giữ nguyên bảng/cột/ràng buộc cũ; thêm 146 cột và 5 bảng nghiệp vụ. Không thêm
Organization, không seed giá/policy demo, không thay API auth.

Done cho từng flow: contract DTO/quyền, mapping và migration nếu cần, validation
và transitions, tests phù hợp + Maven verify + SQL integration, OpenAPI/docs,
review và merge vào dev. API chỉ coi khả dụng cho FE khi code flow đã merge,
cấu hình/schema đã triển khai và endpoint được kiểm, không chỉ vì DB có bảng.
BE1 = nhana123; BE2 chưa gán tài khoản. Chi tiết phân công ở backlog và hai skill.
