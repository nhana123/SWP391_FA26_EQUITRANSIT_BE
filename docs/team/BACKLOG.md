# Backlog Backend EquiTransit

16 Issue, mỗi Issue là một luồng hoàn chỉnh. BE1 = nhana123; BE2 chưa gán tài khoản, nhận bằng label `be2`. Auth đã có baseline, F01 là review/hoàn thiện nền quyền. Các flow còn lại chưa được triển khai bởi đợt lập kế hoạch này.

Đọc [hợp đồng nghiệp vụ](PROJECT_CONTRACT.md) trước; [issues.json](issues.json) giữ toàn bộ acceptance criteria, [github-issues.json](github-issues.json) giữ số/URL thật sau publish.

Baseline schema đã được sửa theo [SRS](../requirements/EQUITRANSIT_REQUIREMENTS.md)
và migration 002; các đoạn đề xuất schema cũ trong issues.json/GitHub cần đọc
cùng §7 PROJECT_CONTRACT, không thêm lại cột đã tồn tại. F01 đã merge qua PR #17;
trạng thái hoàn thành những issue khác vẫn cần API/tests/review theo Done.

## Phân công

| Flow | Kết quả | Owner | Points | Phụ thuộc |
|---|---|---|---:|---|
| [F01 #1](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/1) | Review verified registration, login and account security | BE1 | 3 | Baseline |
| [F02 #2](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/2) | Manage a customer horse profile and ownership | BE1 | 5 | F01 |
| [F03 #3](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/3) | Create, submit and revise a single-horse transport order | BE1 | 8 | F01, F02 |
| [F04 #4](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/4) | Assess an order revision and assign responsible staff | BE1 | 5 | F03, F15 |
| [F05 #5](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/5) | Select eligible services and disclose every booking charge | BE1 | 8 | F03, F09 |
| [F06 #6](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/6) | Prepare, approve, issue and accept a versioned quotation | BE1 | 13 | F04, F05, F08, F10 |
| [F07 #7](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/7) | Request, reconcile, allocate and refund manual bank payments | BE1 | 13 | F06 |
| [F08 #8](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/8) | Assess compliance and verify versioned legal evidence | BE2 | 13 | F01, F03, F04 |
| [F09 #9](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/9) | Maintain transport resources and check availability and fit | BE2 | 8 | F01, F15 |
| [F10 #10](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/10) | Build and confirm a multimodal operational plan | BE2 | 13 | F03, F04, F05, F09 |
| [F11 #11](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/11) | Verify departure gates and physically receive the horse | BE2 | 8 | F06, F07, F08, F10 |
| [F12 #12](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/12) | Execute assigned legs and record welfare and tracking milestones | BE2 | 8 | F11 |
| [F13 #13](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/13) | Resolve operational incidents and controlled customer changes | BE2 | 8 | F06, F07, F12 |
| [F14 #14](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/14) | Record handovers, final delivery and operational settlement | BE2 | 8 | F07, F08, F12, F13 |
| [F15 #15](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/15) | Provision staff and publish versioned company tariffs | BE1 | 5 | F01 |
| [F16 #16](https://github.com/nhana123/SWP391_FA26_EQUITRANSIT_BE/issues/16) | Provide actor dashboards, notifications and traceable audit history | BE1 | 8 | F04, F06, F07, F12, F14 |

Points là độ phức tạp tương đối cho team điều chỉnh, không phải số ngày. F06 cần itinerary nháp từ F10; F10 chỉ activate sau Quote accepted từ F06: tách contract/draft và activation để tránh vòng phụ thuộc triển khai.

## Trình tự và bốn flow giảng viên

- Wave 1: F01 + nền quyền F15; BE1 F02/F03; BE2 nền F08/F09. Hai người chốt DTO/facade trước.
- Wave 2: F04/F05/F06 song song F08/F10; chốt quote/itinerary/compliance contract.
- Wave 3: F07 và nền audit F16, F11/F12; demo bốn flow cốt lõi.
- Wave 4: F13/F14 và hoàn thiện F15/F16; demo thay đổi, bàn giao, settlement.

| Flow giảng viên | Issue hiện thực |
|---|---|
| Customer tạo yêu cầu, Manager đánh giá | F01–F05 |
| Specialist kiểm hồ sơ và điều kiện xuất/nhập | F08 + gate F11 |
| Coordinator lập kế hoạch/phân nguồn lực | F06/F09/F10 |
| Driver nhận ngựa và thực hiện chặng | F11/F12 |

## Coverage database live

Kiểm sau migration 002 ngày 2026-10-13: 57 bảng tổng, 55 bảng nghiệp vụ; SchemaMigrationHistory và sysdiagrams là kỹ thuật; 114 FK constraints không disabled/untrusted. Mỗi bảng có một owner chính dưới đây; consumer gọi typed service facade, không bypass validation qua repository của owner khác.

| Bảng | Flow chính | Owner |
|---|---|---|
| `ActualCost` | F14 | BE2 |
| `AuditEvent` | F16 | BE1 |
| `AuthenticationToken` | F01 | BE1 |
| `AuthorityProcedure` | F08 | BE2 |
| `ComplianceClearance` | F08 | BE2 |
| `ComplianceRuleVersion` | F08 | BE2 |
| `Customer` | F01 | BE1 |
| `DocumentRequirement` | F08 | BE2 |
| `DocumentReview` | F08 | BE2 |
| `DocumentSubmission` | F08 | BE2 |
| `DocumentVersion` | F08 | BE2 |
| `FleetVehicle` | F09 | BE2 |
| `FlightReservation` | F10 | BE2 |
| `Horse` | F02 | BE1 |
| `HorseDocument` | F08 | BE2 |
| `HorseReceipt` | F11 | BE2 |
| `HorseWelfareLog` | F12 | BE2 |
| `IncidentReport` | F13 | BE2 |
| `InsuranceChoice` | F05 | BE1 |
| `InsurancePolicy` | F10 | BE2 |
| `LegMilestoneLog` | F12 | BE2 |
| `Notification` | F16 | BE1 |
| `OperationalAdjustment` | F13 | BE2 |
| `OrderAssignment` | F04 | BE1 |
| `OrderDecision` | F04 | BE1 |
| `OrderRevision` | F03 | BE1 |
| `OrderServiceChoice` | F05 | BE1 |
| `PaymentAllocation` | F07 | BE1 |
| `PaymentReport` | F07 | BE1 |
| `PaymentRequest` | F07 | BE1 |
| `PaymentTransaction` | F07 | BE1 |
| `QuarantineChoice` | F05 | BE1 |
| `QuarantineReservation` | F10 | BE2 |
| `Quote` | F06 | BE1 |
| `QuoteItem` | F06 | BE1 |
| `Refund` | F07 | BE1 |
| `Role` | F01 | BE1 |
| `ServiceTariff` | F15 | BE1 |
| `StaffProfile` | F15 | BE1 |
| `StallAssignment` | F10 | BE2 |
| `StallChoice` | F05 | BE1 |
| `StallEquipment` | F09 | BE2 |
| `Supplier` | F15 (F09 consumer) | BE1 |
| `DocumentTypeCatalog` | F08 | BE2 |
| `StallFitPolicy` | F09 (F05 consumer) | BE2 |
| `QuotePaymentSchedule` | F06/F07 | BE1 |
| `OrderChangeRequest` | F03 (F13 consumer) | BE1 |
| `SupplierOffer` | F06 | BE1 |
| `TransportOrder` | F03 | BE1 |
| `TransportOrderDetail` | F03 | BE1 |
| `TransportPlan` | F10 | BE2 |
| `TripAssignment` | F10 | BE2 |
| `TripHandover` | F14 | BE2 |
| `TripLeg` | F10 | BE2 |
| `User` | F01 | BE1 |

## Cách giao việc cho AI

BE1 đọc `skills/equitransit-be1-commercial/SKILL.md`; BE2 đọc `skills/equitransit-be2-operations/SKILL.md`. Cung cấp Issue cụ thể, nhánh và phạm vi hành động. Skill không tự giao toàn bộ backlog hay tự chạy migrations vào database thật. Hai skill dùng cùng PROJECT_CONTRACT để tránh hai AI định nghĩa khác nghiệp vụ.

Khi biết username BE2, gán những Issue có label be2; không cần tạo lại Issue. Hai người review chéo PR vào dev. Main chỉ nhận phiên bản được team chấp thuận.
