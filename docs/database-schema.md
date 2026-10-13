# Database EquiTransit — DESKTOP-9VRARFD

Tài liệu xuất từ metadata trực tiếp của SQL Server ngày **2026-10-12**. Chỉ mô tả cấu trúc; không chứa dữ liệu khách hàng, mật khẩu hoặc nội dung bản ghi.

| Thuộc tính | Giá trị |
|---|---|
| Server | `DESKTOP-9VRARFD` |
| Database | `EquiTransit` |
| Bảng nghiệp vụ | 50 |
| Cột nghiệp vụ | 400 |
| Bảng phụ SSMS | 1 |
| Khóa ngoại | 73 |
| Khóa ngoại bị tắt hoặc chưa được SQL Server xác thực | 0 |

## Cách đọc

- PK: khóa chính. UNIQUE: ràng buộc hoặc index duy nhất; index nhiều cột chỉ bảo đảm duy nhất cho tổ hợp đó.
- FK: khóa ngoại, xem cột tham chiếu tại bảng quan hệ.
- NULL = Có: cột được phép bỏ trống. Identity = Có: SQL Server cấp giá trị tự động.
- NVARCHAR được trình bày theo số ký tự khai báo; metadata SQL Server lưu max_length theo byte.
- Các mô tả mục đích là giải thích nghiệp vụ; kiểu dữ liệu, khóa, default và CHECK được lấy từ database thực tế.
- Tài liệu là snapshot cấu trúc tại thời điểm xuất, không phải script tạo database hoặc migration.

## Danh sách bảng

| Bảng | Số cột | Mục đích |
|---|---:|---|
| [dbo.ActualCost](#dboactualcost) | 7 | Chi phí thực tế của kế hoạch hoặc chặng. |
| [dbo.AuditEvent](#dboauditevent) | 7 | Nhật ký thao tác hệ thống. |
| [dbo.AuthenticationToken](#dboauthenticationtoken) | 7 | Token xác minh email/khôi phục mật khẩu: lưu hash, hạn dùng và thời điểm tiêu thụ; bổ sung cho flow tài khoản. |
| [dbo.AuthorityProcedure](#dboauthorityprocedure) | 8 | Thủ tục với cơ quan chức năng. |
| [dbo.ComplianceClearance](#dbocomplianceclearance) | 6 | Kết quả xác nhận tuân thủ theo giai đoạn. |
| [dbo.ComplianceRuleVersion](#dbocomplianceruleversion) | 11 | Phiên bản quy định tuân thủ áp dụng cho tuyến. |
| [dbo.Customer](#dbocustomer) | 5 | Hồ sơ khách hàng. |
| [dbo.DocumentRequirement](#dbodocumentrequirement) | 8 | Yêu cầu hồ sơ cho phiên bản đơn. |
| [dbo.DocumentReview](#dbodocumentreview) | 6 | Kết quả kiểm tra giấy tờ đã nộp. |
| [dbo.DocumentSubmission](#dbodocumentsubmission) | 5 | Giấy tờ nộp để đáp ứng yêu cầu hồ sơ. |
| [dbo.DocumentVersion](#dbodocumentversion) | 9 | Các phiên bản file giấy tờ. |
| [dbo.FleetVehicle](#dbofleetvehicle) | 6 | Danh mục xe vận chuyển. |
| [dbo.FlightReservation](#dboflightreservation) | 8 | Đặt chỗ vận chuyển hàng không. |
| [dbo.Horse](#dbohorse) | 13 | Hồ sơ ngựa thuộc khách hàng. |
| [dbo.HorseDocument](#dbohorsedocument) | 4 | Danh mục giấy tờ của ngựa. |
| [dbo.HorseReceipt](#dbohorsereceipt) | 7 | Ghi nhận tiếp nhận ngựa. |
| [dbo.HorseWelfareLog](#dbohorsewelfarelog) | 9 | Nhật ký theo dõi sức khỏe và chăm sóc ngựa. |
| [dbo.IncidentReport](#dboincidentreport) | 8 | Báo cáo sự cố. |
| [dbo.InsuranceChoice](#dboinsurancechoice) | 6 | Lựa chọn bảo hiểm và giá trị khai báo. |
| [dbo.InsurancePolicy](#dboinsurancepolicy) | 8 | Hợp đồng bảo hiểm và bằng chứng. |
| [dbo.LegMilestoneLog](#dbolegmilestonelog) | 9 | Nhật ký mốc tiến độ của chặng. |
| [dbo.Notification](#dbonotification) | 7 | Thông báo gửi tài khoản. |
| [dbo.OperationalAdjustment](#dbooperationaladjustment) | 10 | Đề xuất và phê duyệt điều chỉnh vận hành. |
| [dbo.OrderAssignment](#dboorderassignment) | 6 | Phân công nhân viên phụ trách đơn. |
| [dbo.OrderDecision](#dboorderdecision) | 6 | Quyết định của nhân viên đối với phiên bản yêu cầu. |
| [dbo.OrderRevision](#dboorderrevision) | 6 | Các phiên bản yêu cầu đã gửi. |
| [dbo.OrderServiceChoice](#dboorderservicechoice) | 6 | Dịch vụ được chọn cho từng phiên bản yêu cầu. |
| [dbo.PaymentAllocation](#dbopaymentallocation) | 5 | Phân bổ giao dịch cho yêu cầu thanh toán. |
| [dbo.PaymentReport](#dbopaymentreport) | 7 | Khách báo đã thanh toán và gửi bằng chứng. |
| [dbo.PaymentRequest](#dbopaymentrequest) | 8 | Yêu cầu thanh toán cho báo giá. |
| [dbo.PaymentTransaction](#dbopaymenttransaction) | 9 | Giao dịch tiền thực nhận và đối soát. |
| [dbo.QuarantineChoice](#dboquarantinechoice) | 6 | Lựa chọn gói cách ly và số ngày được xác nhận. |
| [dbo.QuarantineReservation](#dboquarantinereservation) | 7 | Đặt chỗ tại cơ sở cách ly. |
| [dbo.Quote](#dboquote) | 13 | Báo giá tổng thể gửi khách. |
| [dbo.QuoteItem](#dboquoteitem) | 12 | Các khoản chi tiết của báo giá. |
| [dbo.Refund](#dborefund) | 6 | Hoàn tiền theo khoản phân bổ. |
| [dbo.Role](#dborole) | 3 | Danh mục vai trò tài khoản. |
| [dbo.ServiceTariff](#dboservicetariff) | 11 | Đơn giá dịch vụ và thời gian hiệu lực. |
| [dbo.StaffProfile](#dbostaffprofile) | 5 | Hồ sơ nhân viên. |
| [dbo.StallAssignment](#dbostallassignment) | 10 | Bố trí stall cho chặng. |
| [dbo.StallChoice](#dbostallchoice) | 5 | Lựa chọn air stall. |
| [dbo.StallEquipment](#dbostallequipment) | 6 | Danh mục stall thực tế và kích thước. |
| [dbo.SupplierOffer](#dbosupplieroffer) | 13 | Báo giá đầu vào từ đối tác. |
| [dbo.sysdiagrams](#dbosysdiagrams) | 5 | Bảng hỗ trợ lưu sơ đồ trong SQL Server Management Studio; không phải bảng nghiệp vụ. |
| [dbo.TransportOrder](#dbotransportorder) | 14 | Thông tin hiện tại của đơn vận chuyển. |
| [dbo.TransportOrderDetail](#dbotransportorderdetail) | 10 | Ngựa của đơn và thông tin snapshot. |
| [dbo.TransportPlan](#dbotransportplan) | 13 | Kế hoạch vận chuyển và điều kiện xuất phát. |
| [dbo.TripAssignment](#dbotripassignment) | 7 | Phân công nhân viên cho chặng. |
| [dbo.TripHandover](#dbotriphandover) | 13 | Biên bản bàn giao ngựa. |
| [dbo.TripLeg](#dbotripleg) | 13 | Từng chặng trong kế hoạch vận chuyển. |
| [dbo.User](#dbouser) | 6 | Tài khoản đăng nhập và vai trò. |

## Cấu trúc từng bảng

### dbo.ActualCost

Chi phí thực tế của kế hoạch hoặc chặng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `CostID` | `BIGINT` | Không | Có | PK | — |
| `PlanID` | `BIGINT` | Không | Không | FK | — |
| `LegID` | `BIGINT` | Có | Không | FK | — |
| `CostCode` | `VARCHAR(50)` | Không | Không | — | — |
| `Amount` | `DECIMAL(18,2)` | Không | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | — | — |
| `IncurredAt` | `DATETIME2(0)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_ActualCost_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_ActualCost_PlanID` | `PlanID` | [dbo.TransportPlan](#dbotransportplan).`PlanID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_ActualCost` | PRIMARY KEY | CostID | — | — |
| `IX_ActualCost_PlanID` | INDEX | PlanID | — | — |
| `IX_ActualCost_LegID` | INDEX | LegID | — | — |

**CHECK constraints**

- `CK_ActualCost_Amount`: `([Amount]>=(0))`. Bật: Có; đã xác thực: Có.

### dbo.AuditEvent

Nhật ký thao tác hệ thống.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `EventID` | `BIGINT` | Không | Có | PK | — |
| `UserID` | `BIGINT` | Có | Không | FK | — |
| `EntityType` | `VARCHAR(50)` | Không | Không | — | — |
| `EntityID` | `BIGINT` | Không | Không | — | — |
| `ActionCode` | `VARCHAR(50)` | Không | Không | — | — |
| `ChangeSnapshot` | `NVARCHAR(MAX)` | Có | Không | — | — |
| `OccurredAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_AuditEvent_UserID` | `UserID` | [dbo.User](#dbouser).`UserID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_AuditEvent` | PRIMARY KEY | EventID | — | — |
| `IX_AuditEvent_UserID` | INDEX | UserID | — | — |

**CHECK constraints**

- `CK_AuditEvent_ChangeSnapshot_JSON`: `([ChangeSnapshot] IS NULL OR isjson([ChangeSnapshot])=(1))`. Bật: Có; đã xác thực: Có.

### dbo.AuthenticationToken

Token xác minh email/khôi phục mật khẩu: lưu hash, hạn dùng và thời điểm tiêu thụ; bổ sung cho flow tài khoản.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `TokenID` | `BIGINT` | Không | Có | PK | — |
| `UserID` | `BIGINT` | Không | Không | FK | — |
| `TokenHash` | `VARCHAR(64)` | Không | Không | UNIQUE UQ_AuthenticationToken_Hash | — |
| `Purpose` | `VARCHAR(20)` | Không | Không | — | — |
| `ExpiresAt` | `DATETIME2(0)` | Không | Không | — | — |
| `UsedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_AuthenticationToken_User` | `UserID` | [dbo.User](#dbouser).`UserID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_AuthenticationToken` | PRIMARY KEY | TokenID | — | — |
| `UQ_AuthenticationToken_Hash` | UNIQUE | TokenHash | — | — |
| `IX_AuthenticationToken_UserPurpose` | INDEX | UserID, Purpose | — | — |

**CHECK constraints**

- `CK_AuthenticationToken_Purpose`: `([Purpose]='RESET_PASSWORD' OR [Purpose]='VERIFY_EMAIL')`. Bật: Có; đã xác thực: Có.
- `CK_AuthenticationToken_Expiry`: `([ExpiresAt]>[CreatedAt])`. Bật: Có; đã xác thực: Có.

### dbo.AuthorityProcedure

Thủ tục với cơ quan chức năng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ProcedureID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | FK | — |
| `ProcedureCode` | `VARCHAR(50)` | Không | Không | — | — |
| `AuthorityName` | `NVARCHAR(300)` | Không | Không | — | — |
| `ApplicationReference` | `VARCHAR(150)` | Có | Không | — | — |
| `ApprovalStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `EvidenceFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |
| `DecidedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_AuthorityProcedure_RevisionID` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_AuthorityProcedure` | PRIMARY KEY | ProcedureID | — | — |
| `IX_AuthorityProcedure_RevisionID` | INDEX | RevisionID | — | — |

**CHECK constraints**

Không có.

### dbo.ComplianceClearance

Kết quả xác nhận tuân thủ theo giai đoạn.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ClearanceID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | FK | — |
| `SpecialistID` | `BIGINT` | Không | Không | FK | — |
| `PhaseCode` | `VARCHAR(50)` | Không | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |
| `CheckedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_ComplianceClearance_RevisionID` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_ComplianceClearance_SpecialistID` | `SpecialistID` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_ComplianceClearance` | PRIMARY KEY | ClearanceID | — | — |
| `IX_ComplianceClearance_RevisionID` | INDEX | RevisionID | — | — |
| `IX_ComplianceClearance_SpecialistID` | INDEX | SpecialistID | — | — |

**CHECK constraints**

Không có.

### dbo.ComplianceRuleVersion

Phiên bản quy định tuân thủ áp dụng cho tuyến.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `RuleID` | `BIGINT` | Không | Có | PK | — |
| `RuleCode` | `VARCHAR(50)` | Không | Không | UNIQUE tổ hợp UQ_ComplianceRuleVersion_Version | — |
| `VersionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_ComplianceRuleVersion_Version | — |
| `OriginCountryCode` | `VARCHAR(2)` | Không | Không | — | — |
| `DestinationCountryCode` | `VARCHAR(2)` | Không | Không | — | — |
| `DocumentType` | `VARCHAR(50)` | Không | Không | — | — |
| `PhaseCode` | `VARCHAR(50)` | Không | Không | — | — |
| `SourceURL` | `NVARCHAR(2048)` | Không | Không | — | — |
| `ReviewedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `EffectiveFrom` | `DATETIME2(0)` | Không | Không | — | — |
| `EffectiveTo` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

- [dbo.DocumentRequirement](#dbodocumentrequirement).`RuleID` → `RuleID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_ComplianceRuleVersion` | PRIMARY KEY | RuleID | — | — |
| `UQ_ComplianceRuleVersion_Version` | UNIQUE | RuleCode, VersionNo | — | — |

**CHECK constraints**

- `CK_ComplianceRuleVersion_VersionNo`: `([VersionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_ComplianceRuleVersion_EffectiveTo_Range`: `([EffectiveTo] IS NULL OR [EffectiveFrom] IS NOT NULL AND [EffectiveTo]>=[EffectiveFrom])`. Bật: Có; đã xác thực: Có.

### dbo.Customer

Hồ sơ khách hàng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `CustomerID` | `BIGINT` | Không | Có | PK | — |
| `UserID` | `BIGINT` | Không | Không | UNIQUE UQ_Customer_UserID; FK | — |
| `FullName` | `NVARCHAR(150)` | Không | Không | — | — |
| `Phone` | `VARCHAR(30)` | Có | Không | — | — |
| `Address` | `NVARCHAR(500)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_Customer_User` | `UserID` | [dbo.User](#dbouser).`UserID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.Horse](#dbohorse).`CustomerID` → `CustomerID`.
- [dbo.TransportOrder](#dbotransportorder).`CustomerID` → `CustomerID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Customer` | PRIMARY KEY | CustomerID | — | — |
| `UQ_Customer_UserID` | UNIQUE | UserID | — | — |

**CHECK constraints**

Không có.

### dbo.DocumentRequirement

Yêu cầu hồ sơ cho phiên bản đơn.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `RequirementID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | FK | — |
| `RuleID` | `BIGINT` | Không | Không | FK | — |
| `DocumentType` | `VARCHAR(50)` | Không | Không | — | — |
| `PhaseCode` | `VARCHAR(50)` | Không | Không | — | — |
| `IsMandatory` | `BIT` | Không | Không | — | — |
| `DueAt` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_DocumentRequirement_RevisionID` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_DocumentRequirement_RuleID` | `RuleID` | [dbo.ComplianceRuleVersion](#dbocomplianceruleversion).`RuleID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.DocumentSubmission](#dbodocumentsubmission).`RequirementID` → `RequirementID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_DocumentRequirement` | PRIMARY KEY | RequirementID | — | — |
| `IX_DocumentRequirement_RevisionID` | INDEX | RevisionID | — | — |
| `IX_DocumentRequirement_RuleID` | INDEX | RuleID | — | — |

**CHECK constraints**

Không có.

### dbo.DocumentReview

Kết quả kiểm tra giấy tờ đã nộp.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ReviewID` | `BIGINT` | Không | Có | PK | — |
| `SubmissionID` | `BIGINT` | Không | Không | FK | — |
| `ReviewedBy` | `BIGINT` | Không | Không | FK | — |
| `Decision` | `VARCHAR(50)` | Không | Không | — | — |
| `Feedback` | `NVARCHAR(2000)` | Có | Không | — | — |
| `ReviewedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_DocumentReview_ReviewedBy` | `ReviewedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_DocumentReview_SubmissionID` | `SubmissionID` | [dbo.DocumentSubmission](#dbodocumentsubmission).`SubmissionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_DocumentReview` | PRIMARY KEY | ReviewID | — | — |
| `IX_DocumentReview_SubmissionID` | INDEX | SubmissionID | — | — |
| `IX_DocumentReview_ReviewedBy` | INDEX | ReviewedBy | — | — |

**CHECK constraints**

Không có.

### dbo.DocumentSubmission

Giấy tờ nộp để đáp ứng yêu cầu hồ sơ.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `SubmissionID` | `BIGINT` | Không | Có | PK | — |
| `RequirementID` | `BIGINT` | Không | Không | FK | — |
| `DocumentVersionID` | `BIGINT` | Không | Không | FK | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |
| `SubmittedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_DocumentSubmission_DocumentVersionID` | `DocumentVersionID` | [dbo.DocumentVersion](#dbodocumentversion).`DocumentVersionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_DocumentSubmission_RequirementID` | `RequirementID` | [dbo.DocumentRequirement](#dbodocumentrequirement).`RequirementID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.DocumentReview](#dbodocumentreview).`SubmissionID` → `SubmissionID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_DocumentSubmission` | PRIMARY KEY | SubmissionID | — | — |
| `IX_DocumentSubmission_RequirementID` | INDEX | RequirementID | — | — |
| `IX_DocumentSubmission_DocumentVersionID` | INDEX | DocumentVersionID | — | — |

**CHECK constraints**

Không có.

### dbo.DocumentVersion

Các phiên bản file giấy tờ.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `DocumentVersionID` | `BIGINT` | Không | Có | PK | — |
| `DocumentID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_DocumentVersion_Version; FK | — |
| `VersionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_DocumentVersion_Version | — |
| `DocumentNumber` | `VARCHAR(50)` | Có | Không | — | — |
| `FileKey` | `NVARCHAR(1024)` | Không | Không | — | — |
| `FileHash` | `VARCHAR(128)` | Có | Không | — | — |
| `IssuedDate` | `DATE` | Có | Không | — | — |
| `ExpiryDate` | `DATE` | Có | Không | — | — |
| `UploadedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_DocumentVersion_DocumentID` | `DocumentID` | [dbo.HorseDocument](#dbohorsedocument).`DocumentID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.DocumentSubmission](#dbodocumentsubmission).`DocumentVersionID` → `DocumentVersionID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_DocumentVersion` | PRIMARY KEY | DocumentVersionID | — | — |
| `UQ_DocumentVersion_Version` | UNIQUE | DocumentID, VersionNo | — | — |

**CHECK constraints**

- `CK_DocumentVersion_VersionNo`: `([VersionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_DocumentVersion_ExpiryDate_Range`: `([ExpiryDate] IS NULL OR [IssuedDate] IS NOT NULL AND [ExpiryDate]>=[IssuedDate])`. Bật: Có; đã xác thực: Có.

### dbo.FleetVehicle

Danh mục xe vận chuyển.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `VehicleID` | `BIGINT` | Không | Có | PK | — |
| `VehicleCode` | `VARCHAR(50)` | Không | Không | UNIQUE UQ_FleetVehicle_VehicleCode | — |
| `PlateNumber` | `VARCHAR(50)` | Không | Không | UNIQUE UQ_FleetVehicle_PlateNumber | — |
| `HorseCapacity` | `INT` | Không | Không | — | — |
| `ClimateSystemType` | `VARCHAR(50)` | Không | Không | — | — |
| `OperationalStatus` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

- [dbo.TripLeg](#dbotripleg).`VehicleID` → `VehicleID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_FleetVehicle` | PRIMARY KEY | VehicleID | — | — |
| `UQ_FleetVehicle_PlateNumber` | UNIQUE | PlateNumber | — | — |
| `UQ_FleetVehicle_VehicleCode` | UNIQUE | VehicleCode | — | — |

**CHECK constraints**

- `CK_FleetVehicle_HorseCapacity`: `([HorseCapacity]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.FlightReservation

Đặt chỗ vận chuyển hàng không.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ReservationID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `OfferID` | `BIGINT` | Không | Không | FK | — |
| `AirlineName` | `VARCHAR(50)` | Không | Không | — | — |
| `FlightNumber` | `VARCHAR(50)` | Không | Không | — | — |
| `ReservationReference` | `VARCHAR(150)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |
| `ConfirmedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_FlightReservation_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_FlightReservation_OfferID` | `OfferID` | [dbo.SupplierOffer](#dbosupplieroffer).`OfferID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_FlightReservation` | PRIMARY KEY | ReservationID | — | — |
| `IX_FlightReservation_LegID` | INDEX | LegID | — | — |
| `IX_FlightReservation_OfferID` | INDEX | OfferID | — | — |

**CHECK constraints**

Không có.

### dbo.Horse

Hồ sơ ngựa thuộc khách hàng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `HorseID` | `BIGINT` | Không | Có | PK | — |
| `CustomerID` | `BIGINT` | Không | Không | FK | — |
| `MicrochipID` | `VARCHAR(50)` | Có | Không | UNIQUE UX_Horse_MicrochipID (có điều kiện) | — |
| `PassportNumber` | `VARCHAR(50)` | Có | Không | — | — |
| `Name` | `NVARCHAR(150)` | Không | Không | — | — |
| `Breed` | `NVARCHAR(100)` | Có | Không | — | — |
| `DateOfBirth` | `DATE` | Có | Không | — | — |
| `SexCode` | `VARCHAR(20)` | Có | Không | — | — |
| `WeightKg` | `DECIMAL(8,2)` | Có | Không | — | — |
| `HeightCm` | `DECIMAL(6,2)` | Có | Không | — | — |
| `BodyWidthCm` | `DECIMAL(6,2)` | Có | Không | — | — |
| `MeasuredAt` | `DATETIME2(0)` | Có | Không | — | — |
| `SpecialCareNotes` | `NVARCHAR(1000)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_Horse_Customer` | `CustomerID` | [dbo.Customer](#dbocustomer).`CustomerID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.HorseDocument](#dbohorsedocument).`HorseID` → `HorseID`.
- [dbo.TransportOrderDetail](#dbotransportorderdetail).`HorseID` → `HorseID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Horse` | PRIMARY KEY | HorseID | — | — |
| `UX_Horse_MicrochipID` | UNIQUE | MicrochipID | — | `([MicrochipID] IS NOT NULL)` |

**CHECK constraints**

- `CK_Horse_SexCode`: `([SexCode]='GELDING' OR [SexCode]='MARE' OR [SexCode]='STALLION')`. Bật: Có; đã xác thực: Có.
- `CK_Horse_WeightKg`: `([WeightKg]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_Horse_HeightCm`: `([HeightCm]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_Horse_BodyWidthCm`: `([BodyWidthCm]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.HorseDocument

Danh mục giấy tờ của ngựa.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `DocumentID` | `BIGINT` | Không | Có | PK | — |
| `HorseID` | `BIGINT` | Không | Không | FK | — |
| `DocumentType` | `VARCHAR(50)` | Không | Không | — | — |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_HorseDocument_HorseID` | `HorseID` | [dbo.Horse](#dbohorse).`HorseID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.DocumentVersion](#dbodocumentversion).`DocumentID` → `DocumentID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_HorseDocument` | PRIMARY KEY | DocumentID | — | — |
| `IX_HorseDocument_HorseID` | INDEX | HorseID | — | — |

**CHECK constraints**

Không có.

### dbo.HorseReceipt

Ghi nhận tiếp nhận ngựa.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ReceiptID` | `BIGINT` | Không | Có | PK | — |
| `PlanID` | `BIGINT` | Không | Không | FK | — |
| `ReceivedBy` | `BIGINT` | Không | Không | FK | — |
| `ObservedMicrochip` | `VARCHAR(50)` | Có | Không | — | — |
| `ConditionNotes` | `NVARCHAR(2000)` | Có | Không | — | — |
| `ReceiptStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `ReceivedAt` | `DATETIME2(0)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_HorseReceipt_PlanID` | `PlanID` | [dbo.TransportPlan](#dbotransportplan).`PlanID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_HorseReceipt_ReceivedBy` | `ReceivedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_HorseReceipt` | PRIMARY KEY | ReceiptID | — | — |
| `IX_HorseReceipt_PlanID` | INDEX | PlanID | — | — |
| `IX_HorseReceipt_ReceivedBy` | INDEX | ReceivedBy | — | — |

**CHECK constraints**

Không có.

### dbo.HorseWelfareLog

Nhật ký theo dõi sức khỏe và chăm sóc ngựa.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `WelfareLogID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `RecordedBy` | `BIGINT` | Không | Không | FK | — |
| `CheckTime` | `DATETIME2(0)` | Không | Không | — | — |
| `HealthStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `TemperatureCelsius` | `DECIMAL(8,2)` | Có | Không | — | — |
| `WaterIntakeLiters` | `DECIMAL(8,2)` | Có | Không | — | — |
| `BehaviorNotes` | `NVARCHAR(2000)` | Có | Không | — | — |
| `PhotoFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_HorseWelfareLog_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_HorseWelfareLog_RecordedBy` | `RecordedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_HorseWelfareLog` | PRIMARY KEY | WelfareLogID | — | — |
| `IX_HorseWelfareLog_LegID` | INDEX | LegID | — | — |
| `IX_HorseWelfareLog_RecordedBy` | INDEX | RecordedBy | — | — |

**CHECK constraints**

- `CK_HorseWelfareLog_WaterIntakeLiters`: `([WaterIntakeLiters]>=(0))`. Bật: Có; đã xác thực: Có.

### dbo.IncidentReport

Báo cáo sự cố.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `IncidentID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `ReportedBy` | `BIGINT` | Không | Không | FK | — |
| `IncidentType` | `VARCHAR(50)` | Không | Không | — | — |
| `SeverityLevel` | `VARCHAR(50)` | Không | Không | — | — |
| `IncidentTime` | `DATETIME2(0)` | Không | Không | — | — |
| `Description` | `NVARCHAR(2000)` | Không | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_IncidentReport_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_IncidentReport_ReportedBy` | `ReportedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.OperationalAdjustment](#dbooperationaladjustment).`IncidentID` → `IncidentID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_IncidentReport` | PRIMARY KEY | IncidentID | — | — |
| `IX_IncidentReport_LegID` | INDEX | LegID | — | — |
| `IX_IncidentReport_ReportedBy` | INDEX | ReportedBy | — | — |

**CHECK constraints**

Không có.

### dbo.InsuranceChoice

Lựa chọn bảo hiểm và giá trị khai báo.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `InsuranceChoiceID` | `BIGINT` | Không | Có | PK | — |
| `ChoiceID` | `BIGINT` | Không | Không | UNIQUE UQ_InsuranceChoice_ChoiceID; FK | — |
| `CoverageCode` | `VARCHAR(50)` | Không | Không | — | — |
| `DeclaredValue` | `DECIMAL(18,2)` | Có | Không | — | — |
| `ValueCurrency` | `VARCHAR(3)` | Có | Không | — | — |
| `ExistingPolicyReference` | `NVARCHAR(100)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_InsuranceChoice_ServiceChoice` | `ChoiceID` | [dbo.OrderServiceChoice](#dboorderservicechoice).`ChoiceID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.InsurancePolicy](#dboinsurancepolicy).`InsuranceChoiceID` → `InsuranceChoiceID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_InsuranceChoice` | PRIMARY KEY | InsuranceChoiceID | — | — |
| `UQ_InsuranceChoice_ChoiceID` | UNIQUE | ChoiceID | — | — |

**CHECK constraints**

- `CK_InsuranceChoice_DeclaredValue`: `([DeclaredValue]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_InsuranceChoice_ValueCurrency`: `([DeclaredValue] IS NULL AND [ValueCurrency] IS NULL OR [DeclaredValue] IS NOT NULL AND [ValueCurrency] IS NOT NULL)`. Bật: Có; đã xác thực: Có.
- `CK_InsuranceChoice_CoverageCode`: `([CoverageCode]='MORTALITY_INJURY' OR [CoverageCode]='MORTALITY' OR [CoverageCode]='EXISTING_POLICY' OR [CoverageCode]='NONE')`. Bật: Có; đã xác thực: Có.
- `CK_InsuranceChoice_ExistingPolicy`: `([CoverageCode]<>'EXISTING_POLICY' OR [ExistingPolicyReference] IS NOT NULL AND len(ltrim(rtrim([ExistingPolicyReference])))>(0))`. Bật: Có; đã xác thực: Có.

### dbo.InsurancePolicy

Hợp đồng bảo hiểm và bằng chứng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `PolicyID` | `BIGINT` | Không | Có | PK | — |
| `InsuranceChoiceID` | `BIGINT` | Không | Không | FK | — |
| `InsurerName` | `NVARCHAR(300)` | Không | Không | — | — |
| `PolicyNumber` | `VARCHAR(150)` | Không | Không | — | — |
| `EvidenceFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |
| `CoverageFrom` | `DATETIME2(0)` | Không | Không | — | — |
| `CoverageTo` | `DATETIME2(0)` | Không | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_InsurancePolicy_InsuranceChoiceID` | `InsuranceChoiceID` | [dbo.InsuranceChoice](#dboinsurancechoice).`InsuranceChoiceID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_InsurancePolicy` | PRIMARY KEY | PolicyID | — | — |
| `IX_InsurancePolicy_InsuranceChoiceID` | INDEX | InsuranceChoiceID | — | — |

**CHECK constraints**

- `CK_InsurancePolicy_CoverageTo_Range`: `([CoverageTo] IS NULL OR [CoverageFrom] IS NOT NULL AND [CoverageTo]>=[CoverageFrom])`. Bật: Có; đã xác thực: Có.

### dbo.LegMilestoneLog

Nhật ký mốc tiến độ của chặng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `MilestoneID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `ReportedBy` | `BIGINT` | Không | Không | FK | — |
| `MilestoneType` | `VARCHAR(50)` | Không | Không | — | — |
| `OccurredAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |
| `RecordedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |
| `Latitude` | `DECIMAL(9,6)` | Có | Không | — | — |
| `Longitude` | `DECIMAL(9,6)` | Có | Không | — | — |
| `Notes` | `NVARCHAR(2000)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_LegMilestoneLog_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_LegMilestoneLog_ReportedBy` | `ReportedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_LegMilestoneLog` | PRIMARY KEY | MilestoneID | — | — |
| `IX_LegMilestoneLog_LegID` | INDEX | LegID | — | — |
| `IX_LegMilestoneLog_ReportedBy` | INDEX | ReportedBy | — | — |

**CHECK constraints**

- `CK_LegMilestoneLog_Latitude`: `([Latitude]>=(-90) AND [Latitude]<=(90))`. Bật: Có; đã xác thực: Có.
- `CK_LegMilestoneLog_Longitude`: `([Longitude]>=(-180) AND [Longitude]<=(180))`. Bật: Có; đã xác thực: Có.

### dbo.Notification

Thông báo gửi tài khoản.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `NotificationID` | `BIGINT` | Không | Có | PK | — |
| `UserID` | `BIGINT` | Không | Không | FK | — |
| `OrderID` | `BIGINT` | Có | Không | FK | — |
| `Title` | `NVARCHAR(300)` | Không | Không | — | — |
| `Message` | `NVARCHAR(2000)` | Không | Không | — | — |
| `ReadAt` | `DATETIME2(0)` | Có | Không | — | — |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_Notification_OrderID` | `OrderID` | [dbo.TransportOrder](#dbotransportorder).`OrderID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_Notification_UserID` | `UserID` | [dbo.User](#dbouser).`UserID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Notification` | PRIMARY KEY | NotificationID | — | — |
| `IX_Notification_UserID` | INDEX | UserID | — | — |
| `IX_Notification_OrderID` | INDEX | OrderID | — | — |

**CHECK constraints**

Không có.

### dbo.OperationalAdjustment

Đề xuất và phê duyệt điều chỉnh vận hành.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `AdjustmentID` | `BIGINT` | Không | Có | PK | — |
| `PlanID` | `BIGINT` | Không | Không | FK | — |
| `IncidentID` | `BIGINT` | Có | Không | FK | — |
| `ProposedBy` | `BIGINT` | Không | Không | FK | — |
| `ApprovedBy` | `BIGINT` | Có | Không | FK | — |
| `ProposedChanges` | `NVARCHAR(MAX)` | Không | Không | — | — |
| `AdditionalCostEstimate` | `DECIMAL(18,2)` | Không | Không | — | — |
| `ApprovalStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `ProposedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |
| `DecidedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_OperationalAdjustment_ApprovedBy` | `ApprovedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_OperationalAdjustment_IncidentID` | `IncidentID` | [dbo.IncidentReport](#dboincidentreport).`IncidentID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_OperationalAdjustment_PlanID` | `PlanID` | [dbo.TransportPlan](#dbotransportplan).`PlanID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_OperationalAdjustment_ProposedBy` | `ProposedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_OperationalAdjustment` | PRIMARY KEY | AdjustmentID | — | — |
| `IX_OperationalAdjustment_PlanID` | INDEX | PlanID | — | — |
| `IX_OperationalAdjustment_IncidentID` | INDEX | IncidentID | — | — |
| `IX_OperationalAdjustment_ProposedBy` | INDEX | ProposedBy | — | — |
| `IX_OperationalAdjustment_ApprovedBy` | INDEX | ApprovedBy | — | — |

**CHECK constraints**

- `CK_OperationalAdjustment_ProposedChanges_JSON`: `(isjson([ProposedChanges])=(1))`. Bật: Có; đã xác thực: Có.
- `CK_OperationalAdjustment_AdditionalCostEstimate`: `([AdditionalCostEstimate]>=(0))`. Bật: Có; đã xác thực: Có.

### dbo.OrderAssignment

Phân công nhân viên phụ trách đơn.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `AssignmentID` | `BIGINT` | Không | Có | PK | — |
| `OrderID` | `BIGINT` | Không | Không | FK | — |
| `StaffID` | `BIGINT` | Không | Không | FK | — |
| `AssignmentRole` | `VARCHAR(30)` | Không | Không | — | — |
| `AssignedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |
| `EndedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_OrderAssignment_Order` | `OrderID` | [dbo.TransportOrder](#dbotransportorder).`OrderID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_OrderAssignment_Staff` | `StaffID` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_OrderAssignment` | PRIMARY KEY | AssignmentID | — | — |

**CHECK constraints**

- `CK_OrderAssignment_Dates`: `([EndedAt] IS NULL OR [EndedAt]>=[AssignedAt])`. Bật: Có; đã xác thực: Có.

### dbo.OrderDecision

Quyết định của nhân viên đối với phiên bản yêu cầu.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `DecisionID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | FK | — |
| `ManagerID` | `BIGINT` | Không | Không | FK | — |
| `Decision` | `VARCHAR(20)` | Không | Không | — | — |
| `Reason` | `NVARCHAR(1000)` | Có | Không | — | — |
| `DecidedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_OrderDecision_Manager` | `ManagerID` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_OrderDecision_Revision` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_OrderDecision` | PRIMARY KEY | DecisionID | — | — |

**CHECK constraints**

- `CK_OrderDecision_Decision`: `([Decision]='CHANGES_REQUESTED' OR [Decision]='REJECTED' OR [Decision]='APPROVED')`. Bật: Có; đã xác thực: Có.

### dbo.OrderRevision

Các phiên bản yêu cầu đã gửi.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `RevisionID` | `BIGINT` | Không | Có | PK | — |
| `OrderID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_OrderRevision_Order_RevisionNo; FK | — |
| `RevisionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_OrderRevision_Order_RevisionNo | — |
| `OrderSnapshot` | `NVARCHAR(MAX)` | Không | Không | — | — |
| `ChangeReason` | `NVARCHAR(500)` | Có | Không | — | — |
| `SubmittedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_OrderRevision_Order` | `OrderID` | [dbo.TransportOrder](#dbotransportorder).`OrderID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.AuthorityProcedure](#dboauthorityprocedure).`RevisionID` → `RevisionID`.
- [dbo.ComplianceClearance](#dbocomplianceclearance).`RevisionID` → `RevisionID`.
- [dbo.DocumentRequirement](#dbodocumentrequirement).`RevisionID` → `RevisionID`.
- [dbo.OrderDecision](#dboorderdecision).`RevisionID` → `RevisionID`.
- [dbo.OrderServiceChoice](#dboorderservicechoice).`RevisionID` → `RevisionID`.
- [dbo.Quote](#dboquote).`RevisionID` → `RevisionID`.
- [dbo.SupplierOffer](#dbosupplieroffer).`RevisionID` → `RevisionID`.
- [dbo.TransportPlan](#dbotransportplan).`RevisionID` → `RevisionID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_OrderRevision` | PRIMARY KEY | RevisionID | — | — |
| `UQ_OrderRevision_Order_RevisionNo` | UNIQUE | OrderID, RevisionNo | — | — |

**CHECK constraints**

- `CK_OrderRevision_RevisionNo`: `([RevisionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_OrderRevision_OrderSnapshot`: `(isjson([OrderSnapshot])=(1))`. Bật: Có; đã xác thực: Có.

### dbo.OrderServiceChoice

Dịch vụ được chọn cho từng phiên bản yêu cầu.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ChoiceID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_OrderServiceChoice_Revision_Service; FK | — |
| `ServiceCode` | `VARCHAR(50)` | Không | Không | UNIQUE tổ hợp UQ_OrderServiceChoice_Revision_Service | — |
| `OptionCode` | `VARCHAR(50)` | Không | Không | — | — |
| `ArrangedBy` | `VARCHAR(20)` | Không | Không | — | — |
| `ChosenAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_OrderServiceChoice_Revision` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.InsuranceChoice](#dboinsurancechoice).`ChoiceID` → `ChoiceID`.
- [dbo.QuarantineChoice](#dboquarantinechoice).`ChoiceID` → `ChoiceID`.
- [dbo.StallChoice](#dbostallchoice).`ChoiceID` → `ChoiceID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_OrderServiceChoice` | PRIMARY KEY | ChoiceID | — | — |
| `UQ_OrderServiceChoice_Revision_Service` | UNIQUE | RevisionID, ServiceCode | — | — |

**CHECK constraints**

- `CK_OrderServiceChoice_ArrangedBy`: `([ArrangedBy]='CUSTOMER' OR [ArrangedBy]='EQUITRANSIT')`. Bật: Có; đã xác thực: Có.

### dbo.PaymentAllocation

Phân bổ giao dịch cho yêu cầu thanh toán.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `AllocationID` | `BIGINT` | Không | Có | PK | — |
| `TransactionID` | `BIGINT` | Không | Không | FK | — |
| `PaymentRequestID` | `BIGINT` | Không | Không | FK | — |
| `AllocatedAmount` | `DECIMAL(18,2)` | Không | Không | — | — |
| `AllocatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_PaymentAllocation_PaymentRequestID` | `PaymentRequestID` | [dbo.PaymentRequest](#dbopaymentrequest).`PaymentRequestID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_PaymentAllocation_TransactionID` | `TransactionID` | [dbo.PaymentTransaction](#dbopaymenttransaction).`TransactionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.Refund](#dborefund).`AllocationID` → `AllocationID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_PaymentAllocation` | PRIMARY KEY | AllocationID | — | — |
| `IX_PaymentAllocation_TransactionID` | INDEX | TransactionID | — | — |
| `IX_PaymentAllocation_PaymentRequestID` | INDEX | PaymentRequestID | — | — |

**CHECK constraints**

- `CK_PaymentAllocation_AllocatedAmount`: `([AllocatedAmount]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.PaymentReport

Khách báo đã thanh toán và gửi bằng chứng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ReportID` | `BIGINT` | Không | Có | PK | — |
| `PaymentRequestID` | `BIGINT` | Không | Không | FK | — |
| `ReportedAmount` | `DECIMAL(18,2)` | Không | Không | — | — |
| `BankReference` | `VARCHAR(150)` | Có | Không | — | — |
| `ProofFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |
| `CustomerReportedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_PaymentReport_PaymentRequestID` | `PaymentRequestID` | [dbo.PaymentRequest](#dbopaymentrequest).`PaymentRequestID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_PaymentReport` | PRIMARY KEY | ReportID | — | — |
| `IX_PaymentReport_PaymentRequestID` | INDEX | PaymentRequestID | — | — |

**CHECK constraints**

- `CK_PaymentReport_ReportedAmount`: `([ReportedAmount]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.PaymentRequest

Yêu cầu thanh toán cho báo giá.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `PaymentRequestID` | `BIGINT` | Không | Có | PK | — |
| `QuoteID` | `BIGINT` | Không | Không | FK | — |
| `PaymentNumber` | `VARCHAR(50)` | Không | Không | UNIQUE UQ_PaymentRequest_PaymentNumber | — |
| `PaymentStage` | `VARCHAR(50)` | Không | Không | — | — |
| `AmountDue` | `DECIMAL(18,2)` | Không | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | — | — |
| `DueAt` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_PaymentRequest_QuoteID` | `QuoteID` | [dbo.Quote](#dboquote).`QuoteID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.PaymentAllocation](#dbopaymentallocation).`PaymentRequestID` → `PaymentRequestID`.
- [dbo.PaymentReport](#dbopaymentreport).`PaymentRequestID` → `PaymentRequestID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_PaymentRequest` | PRIMARY KEY | PaymentRequestID | — | — |
| `UQ_PaymentRequest_PaymentNumber` | UNIQUE | PaymentNumber | — | — |
| `IX_PaymentRequest_QuoteID` | INDEX | QuoteID | — | — |

**CHECK constraints**

- `CK_PaymentRequest_AmountDue`: `([AmountDue]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.PaymentTransaction

Giao dịch tiền thực nhận và đối soát.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `TransactionID` | `BIGINT` | Không | Có | PK | — |
| `ReconciledBy` | `BIGINT` | Có | Không | FK | — |
| `RecipientAccount` | `VARCHAR(150)` | Không | Không | UNIQUE tổ hợp UQ_PaymentTransaction_BankReference | — |
| `BankTransactionReference` | `VARCHAR(150)` | Không | Không | UNIQUE tổ hợp UQ_PaymentTransaction_BankReference | — |
| `AmountReceived` | `DECIMAL(18,2)` | Không | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | — | — |
| `PostedAt` | `DATETIME2(0)` | Không | Không | — | — |
| `ReconciledAt` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_PaymentTransaction_ReconciledBy` | `ReconciledBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.PaymentAllocation](#dbopaymentallocation).`TransactionID` → `TransactionID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_PaymentTransaction` | PRIMARY KEY | TransactionID | — | — |
| `UQ_PaymentTransaction_BankReference` | UNIQUE | RecipientAccount, BankTransactionReference | — | — |
| `IX_PaymentTransaction_ReconciledBy` | INDEX | ReconciledBy | — | — |

**CHECK constraints**

- `CK_PaymentTransaction_AmountReceived`: `([AmountReceived]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.QuarantineChoice

Lựa chọn gói cách ly và số ngày được xác nhận.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `QuarantineChoiceID` | `BIGINT` | Không | Có | PK | — |
| `ChoiceID` | `BIGINT` | Không | Không | UNIQUE UQ_QuarantineChoice_ChoiceID; FK | — |
| `PackageCode` | `VARCHAR(50)` | Không | Không | — | — |
| `RequiredDays` | `INT` | Có | Không | — | — |
| `ConfirmedBy` | `BIGINT` | Có | Không | FK | — |
| `DaysConfirmedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_QuarantineChoice_ServiceChoice` | `ChoiceID` | [dbo.OrderServiceChoice](#dboorderservicechoice).`ChoiceID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_QuarantineChoice_Staff` | `ConfirmedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.QuarantineReservation](#dboquarantinereservation).`QuarantineChoiceID` → `QuarantineChoiceID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_QuarantineChoice` | PRIMARY KEY | QuarantineChoiceID | — | — |
| `UQ_QuarantineChoice_ChoiceID` | UNIQUE | ChoiceID | — | — |

**CHECK constraints**

- `CK_QuarantineChoice_RequiredDays`: `([RequiredDays]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_QuarantineChoice_Confirmation`: `([ConfirmedBy] IS NULL AND [DaysConfirmedAt] IS NULL OR [ConfirmedBy] IS NOT NULL AND [DaysConfirmedAt] IS NOT NULL AND [RequiredDays] IS NOT NULL)`. Bật: Có; đã xác thực: Có.

### dbo.QuarantineReservation

Đặt chỗ tại cơ sở cách ly.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ReservationID` | `BIGINT` | Không | Có | PK | — |
| `QuarantineChoiceID` | `BIGINT` | Không | Không | FK | — |
| `FacilityName` | `NVARCHAR(300)` | Không | Không | — | — |
| `ReservationReference` | `VARCHAR(150)` | Có | Không | — | — |
| `StartsAt` | `DATETIME2(0)` | Không | Không | — | — |
| `EndsAt` | `DATETIME2(0)` | Không | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_QuarantineReservation_QuarantineChoiceID` | `QuarantineChoiceID` | [dbo.QuarantineChoice](#dboquarantinechoice).`QuarantineChoiceID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_QuarantineReservation` | PRIMARY KEY | ReservationID | — | — |
| `IX_QuarantineReservation_QuarantineChoiceID` | INDEX | QuarantineChoiceID | — | — |

**CHECK constraints**

- `CK_QuarantineReservation_EndsAt_Range`: `([EndsAt] IS NULL OR [StartsAt] IS NOT NULL AND [EndsAt]>=[StartsAt])`. Bật: Có; đã xác thực: Có.

### dbo.Quote

Báo giá tổng thể gửi khách.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `QuoteID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_Quote_Revision_Version; FK | — |
| `ApprovedBy` | `BIGINT` | Có | Không | FK | — |
| `QuoteNumber` | `VARCHAR(30)` | Không | Không | UNIQUE UQ_Quote_QuoteNumber | — |
| `VersionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_Quote_Revision_Version | `((1))` |
| `Currency` | `VARCHAR(3)` | Không | Không | — | — |
| `TotalQuotedAmount` | `DECIMAL(18,2)` | Có | Không | — | — |
| `QuoteStatus` | `VARCHAR(20)` | Không | Không | — | `('DRAFT')` |
| `ApprovedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `IssuedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `ValidUntil` | `DATETIME2(0)` | Có | Không | — | — |
| `AcceptedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_Quote_ApprovedBy` | `ApprovedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_Quote_Revision` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.PaymentRequest](#dbopaymentrequest).`QuoteID` → `QuoteID`.
- [dbo.QuoteItem](#dboquoteitem).`QuoteID` → `QuoteID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Quote` | PRIMARY KEY | QuoteID | — | — |
| `UQ_Quote_Revision_Version` | UNIQUE | RevisionID, VersionNo | — | — |
| `UQ_Quote_QuoteNumber` | UNIQUE | QuoteNumber | — | — |

**CHECK constraints**

- `CK_Quote_VersionNo`: `([VersionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_Quote_TotalAmount`: `([TotalQuotedAmount]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_Quote_Status`: `([QuoteStatus]='SUPERSEDED' OR [QuoteStatus]='EXPIRED' OR [QuoteStatus]='REJECTED' OR [QuoteStatus]='ACCEPTED' OR [QuoteStatus]='ISSUED' OR [QuoteStatus]='APPROVED' OR [QuoteStatus]='DRAFT')`. Bật: Có; đã xác thực: Có.
- `CK_Quote_Approval`: `([ApprovedBy] IS NULL AND [ApprovedAt] IS NULL OR [ApprovedBy] IS NOT NULL AND [ApprovedAt] IS NOT NULL)`. Bật: Có; đã xác thực: Có.
- `CK_Quote_Validity`: `([ValidUntil] IS NULL OR [IssuedAt] IS NOT NULL AND [ValidUntil]>[IssuedAt])`. Bật: Có; đã xác thực: Có.
- `CK_Quote_AcceptedAt`: `([AcceptedAt] IS NULL OR [IssuedAt] IS NOT NULL AND [AcceptedAt]>=[IssuedAt])`. Bật: Có; đã xác thực: Có.

### dbo.QuoteItem

Các khoản chi tiết của báo giá.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `ItemID` | `BIGINT` | Không | Có | PK | — |
| `QuoteID` | `BIGINT` | Không | Không | FK | — |
| `TariffID` | `BIGINT` | Có | Không | FK | — |
| `OfferID` | `BIGINT` | Có | Không | FK | — |
| `ServiceCode` | `VARCHAR(50)` | Không | Không | — | — |
| `CostCategory` | `VARCHAR(50)` | Không | Không | — | — |
| `Description` | `NVARCHAR(2000)` | Không | Không | — | — |
| `UnitCode` | `VARCHAR(50)` | Không | Không | — | — |
| `Quantity` | `DECIMAL(18,4)` | Không | Không | — | — |
| `UnitPrice` | `DECIMAL(18,4)` | Không | Không | — | — |
| `Amount` | `DECIMAL(18,2)` | Không | Không | — | — |
| `CalculationInputs` | `NVARCHAR(MAX)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_QuoteItem_OfferID` | `OfferID` | [dbo.SupplierOffer](#dbosupplieroffer).`OfferID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_QuoteItem_QuoteID` | `QuoteID` | [dbo.Quote](#dboquote).`QuoteID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_QuoteItem_TariffID` | `TariffID` | [dbo.ServiceTariff](#dboservicetariff).`TariffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_QuoteItem` | PRIMARY KEY | ItemID | — | — |
| `IX_QuoteItem_QuoteID` | INDEX | QuoteID | — | — |
| `IX_QuoteItem_TariffID` | INDEX | TariffID | — | — |
| `IX_QuoteItem_OfferID` | INDEX | OfferID | — | — |

**CHECK constraints**

- `CK_QuoteItem_Quantity`: `([Quantity]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_QuoteItem_UnitPrice`: `([UnitPrice]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_QuoteItem_Amount`: `([Amount]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_QuoteItem_CalculationInputs_JSON`: `([CalculationInputs] IS NULL OR isjson([CalculationInputs])=(1))`. Bật: Có; đã xác thực: Có.

### dbo.Refund

Hoàn tiền theo khoản phân bổ.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `RefundID` | `BIGINT` | Không | Có | PK | — |
| `AllocationID` | `BIGINT` | Không | Không | FK | — |
| `Amount` | `DECIMAL(18,2)` | Không | Không | — | — |
| `Reason` | `NVARCHAR(2000)` | Không | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |
| `RefundedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_Refund_AllocationID` | `AllocationID` | [dbo.PaymentAllocation](#dbopaymentallocation).`AllocationID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Refund` | PRIMARY KEY | RefundID | — | — |
| `IX_Refund_AllocationID` | INDEX | AllocationID | — | — |

**CHECK constraints**

- `CK_Refund_Amount`: `([Amount]>=(0))`. Bật: Có; đã xác thực: Có.

### dbo.Role

Danh mục vai trò tài khoản.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `RoleID` | `BIGINT` | Không | Có | PK | — |
| `RoleCode` | `VARCHAR(30)` | Không | Không | UNIQUE UQ_Role_RoleCode | — |
| `Description` | `NVARCHAR(255)` | Có | Không | — | — |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

- [dbo.User](#dbouser).`RoleID` → `RoleID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_Role` | PRIMARY KEY | RoleID | — | — |
| `UQ_Role_RoleCode` | UNIQUE | RoleCode | — | — |

**CHECK constraints**

Không có.

### dbo.ServiceTariff

Đơn giá dịch vụ và thời gian hiệu lực.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `TariffID` | `BIGINT` | Không | Có | PK | — |
| `ServiceCode` | `VARCHAR(50)` | Không | Không | UNIQUE tổ hợp UQ_ServiceTariff_Version | — |
| `OptionCode` | `VARCHAR(50)` | Không | Không | UNIQUE tổ hợp UQ_ServiceTariff_Version | — |
| `CostGroup` | `VARCHAR(50)` | Không | Không | — | — |
| `UnitCode` | `VARCHAR(30)` | Không | Không | — | — |
| `UnitPrice` | `DECIMAL(18,4)` | Không | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | UNIQUE tổ hợp UQ_ServiceTariff_Version | `('USD')` |
| `VersionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_ServiceTariff_Version | `((1))` |
| `EffectiveFrom` | `DATETIME2(0)` | Không | Không | — | — |
| `EffectiveTo` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(20)` | Không | Không | — | `('ACTIVE')` |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

- [dbo.QuoteItem](#dboquoteitem).`TariffID` → `TariffID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_ServiceTariff` | PRIMARY KEY | TariffID | — | — |
| `UQ_ServiceTariff_Version` | UNIQUE | ServiceCode, OptionCode, Currency, VersionNo | — | — |

**CHECK constraints**

- `CK_ServiceTariff_UnitPrice`: `([UnitPrice]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_ServiceTariff_VersionNo`: `([VersionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_ServiceTariff_Dates`: `([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom])`. Bật: Có; đã xác thực: Có.
- `CK_ServiceTariff_Status`: `([Status]='INACTIVE' OR [Status]='ACTIVE')`. Bật: Có; đã xác thực: Có.

### dbo.StaffProfile

Hồ sơ nhân viên.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `StaffID` | `BIGINT` | Không | Có | PK | — |
| `UserID` | `BIGINT` | Không | Không | UNIQUE UQ_StaffProfile_UserID; FK | — |
| `FullName` | `NVARCHAR(150)` | Không | Không | — | — |
| `Phone` | `VARCHAR(30)` | Có | Không | — | — |
| `LicenseNumber` | `VARCHAR(50)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_StaffProfile_User` | `UserID` | [dbo.User](#dbouser).`UserID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.ComplianceClearance](#dbocomplianceclearance).`SpecialistID` → `StaffID`.
- [dbo.DocumentReview](#dbodocumentreview).`ReviewedBy` → `StaffID`.
- [dbo.HorseReceipt](#dbohorsereceipt).`ReceivedBy` → `StaffID`.
- [dbo.HorseWelfareLog](#dbohorsewelfarelog).`RecordedBy` → `StaffID`.
- [dbo.IncidentReport](#dboincidentreport).`ReportedBy` → `StaffID`.
- [dbo.LegMilestoneLog](#dbolegmilestonelog).`ReportedBy` → `StaffID`.
- [dbo.OperationalAdjustment](#dbooperationaladjustment).`ApprovedBy` → `StaffID`.
- [dbo.OperationalAdjustment](#dbooperationaladjustment).`ProposedBy` → `StaffID`.
- [dbo.OrderAssignment](#dboorderassignment).`StaffID` → `StaffID`.
- [dbo.OrderDecision](#dboorderdecision).`ManagerID` → `StaffID`.
- [dbo.PaymentTransaction](#dbopaymenttransaction).`ReconciledBy` → `StaffID`.
- [dbo.QuarantineChoice](#dboquarantinechoice).`ConfirmedBy` → `StaffID`.
- [dbo.Quote](#dboquote).`ApprovedBy` → `StaffID`.
- [dbo.TransportPlan](#dbotransportplan).`CreatedBy` → `StaffID`.
- [dbo.TransportPlan](#dbotransportplan).`DepartureAuthorizedBy` → `StaffID`.
- [dbo.TripAssignment](#dbotripassignment).`StaffID` → `StaffID`.
- [dbo.TripHandover](#dbotriphandover).`RecordedBy` → `StaffID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_StaffProfile` | PRIMARY KEY | StaffID | — | — |
| `UQ_StaffProfile_UserID` | UNIQUE | UserID | — | — |

**CHECK constraints**

Không có.

### dbo.StallAssignment

Bố trí stall cho chặng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `StallAssignmentID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `StallID` | `BIGINT` | Không | Không | FK | — |
| `SlotQuantity` | `DECIMAL(5,1)` | Không | Không | — | — |
| `SharingCode` | `VARCHAR(50)` | Không | Không | — | — |
| `FitStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `CarrierAcceptanceStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `AssignedFrom` | `DATETIME2(0)` | Có | Không | — | — |
| `AssignedTo` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_StallAssignment_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_StallAssignment_StallID` | `StallID` | [dbo.StallEquipment](#dbostallequipment).`StallID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_StallAssignment` | PRIMARY KEY | StallAssignmentID | — | — |
| `IX_StallAssignment_LegID` | INDEX | LegID | — | — |
| `IX_StallAssignment_StallID` | INDEX | StallID | — | — |

**CHECK constraints**

- `CK_StallAssignment_SlotQuantity`: `([SlotQuantity]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_StallAssignment_AssignedTo_Range`: `([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])`. Bật: Có; đã xác thực: Có.

### dbo.StallChoice

Lựa chọn air stall.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `StallChoiceID` | `BIGINT` | Không | Có | PK | — |
| `ChoiceID` | `BIGINT` | Không | Không | UNIQUE UQ_StallChoice_ChoiceID; FK | — |
| `SlotQuantity` | `DECIMAL(3,1)` | Không | Không | — | — |
| `SharingCode` | `VARCHAR(20)` | Không | Không | — | — |
| `FitStatus` | `VARCHAR(20)` | Không | Không | — | `('PENDING')` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_StallChoice_ServiceChoice` | `ChoiceID` | [dbo.OrderServiceChoice](#dboorderservicechoice).`ChoiceID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_StallChoice` | PRIMARY KEY | StallChoiceID | — | — |
| `UQ_StallChoice_ChoiceID` | UNIQUE | ChoiceID | — | — |

**CHECK constraints**

- `CK_StallChoice_Configuration`: `([SharingCode]='SHARED' AND ([SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)) OR [SharingCode]='PRIVATE' AND [SlotQuantity]=(3.0))`. Bật: Có; đã xác thực: Có.
- `CK_StallChoice_FitStatus`: `([FitStatus]='UNSUITABLE' OR [FitStatus]='SUITABLE' OR [FitStatus]='PENDING')`. Bật: Có; đã xác thực: Có.

### dbo.StallEquipment

Danh mục stall thực tế và kích thước.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `StallID` | `BIGINT` | Không | Có | PK | — |
| `StallCode` | `VARCHAR(50)` | Không | Không | UNIQUE UQ_StallEquipment_StallCode | — |
| `CapacitySlots` | `DECIMAL(5,1)` | Không | Không | — | — |
| `InternalHeightCm` | `DECIMAL(8,2)` | Không | Không | — | — |
| `InternalWidthCm` | `DECIMAL(8,2)` | Không | Không | — | — |
| `EquipmentStatus` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

- [dbo.StallAssignment](#dbostallassignment).`StallID` → `StallID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_StallEquipment` | PRIMARY KEY | StallID | — | — |
| `UQ_StallEquipment_StallCode` | UNIQUE | StallCode | — | — |

**CHECK constraints**

- `CK_StallEquipment_CapacitySlots`: `([CapacitySlots]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.SupplierOffer

Báo giá đầu vào từ đối tác.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `OfferID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | FK | — |
| `ProviderName` | `NVARCHAR(200)` | Không | Không | — | — |
| `OfferReference` | `VARCHAR(100)` | Có | Không | — | — |
| `ServiceCode` | `VARCHAR(50)` | Không | Không | — | — |
| `UnitCode` | `VARCHAR(30)` | Không | Không | — | — |
| `Quantity` | `DECIMAL(18,4)` | Không | Không | — | — |
| `UnitPrice` | `DECIMAL(18,4)` | Không | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | — | — |
| `IncludesStall` | `BIT` | Không | Không | — | `((0))` |
| `IncludedCharges` | `NVARCHAR(MAX)` | Có | Không | — | — |
| `ValidUntil` | `DATETIME2(0)` | Không | Không | — | — |
| `Status` | `VARCHAR(20)` | Không | Không | — | `('RECEIVED')` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_SupplierOffer_Revision` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.FlightReservation](#dboflightreservation).`OfferID` → `OfferID`.
- [dbo.QuoteItem](#dboquoteitem).`OfferID` → `OfferID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_SupplierOffer` | PRIMARY KEY | OfferID | — | — |

**CHECK constraints**

- `CK_SupplierOffer_Quantity`: `([Quantity]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_SupplierOffer_UnitPrice`: `([UnitPrice]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_SupplierOffer_IncludedCharges`: `([IncludedCharges] IS NULL OR isjson([IncludedCharges])=(1))`. Bật: Có; đã xác thực: Có.
- `CK_SupplierOffer_Status`: `([Status]='EXPIRED' OR [Status]='REJECTED' OR [Status]='SELECTED' OR [Status]='RECEIVED')`. Bật: Có; đã xác thực: Có.

### dbo.sysdiagrams

Bảng hỗ trợ lưu sơ đồ trong SQL Server Management Studio; không phải bảng nghiệp vụ.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `name` | `SYSNAME` | Không | Không | UNIQUE tổ hợp UK_principal_name | — |
| `principal_id` | `INT` | Không | Không | UNIQUE tổ hợp UK_principal_name | — |
| `diagram_id` | `INT` | Không | Có | PK | — |
| `version` | `INT` | Có | Không | — | — |
| `definition` | `VARBINARY(MAX)` | Có | Không | — | — |

**Khóa ngoại của bảng**

Không có.

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK__sysdiagr__C2B05B61E672F905` | PRIMARY KEY | diagram_id | — | — |
| `UK_principal_name` | UNIQUE | principal_id, name | — | — |

**CHECK constraints**

Không có.

### dbo.TransportOrder

Thông tin hiện tại của đơn vận chuyển.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `OrderID` | `BIGINT` | Không | Có | PK | — |
| `CustomerID` | `BIGINT` | Không | Không | FK | — |
| `OrderNumber` | `VARCHAR(30)` | Không | Không | UNIQUE UQ_TransportOrder_OrderNumber | — |
| `OriginCountryCode` | `VARCHAR(2)` | Có | Không | — | — |
| `DestinationCountryCode` | `VARCHAR(2)` | Có | Không | — | — |
| `OriginAddress` | `NVARCHAR(500)` | Có | Không | — | — |
| `DestinationAddress` | `NVARCHAR(500)` | Có | Không | — | — |
| `OriginTimezone` | `VARCHAR(100)` | Có | Không | — | — |
| `DestinationTimezone` | `VARCHAR(100)` | Có | Không | — | — |
| `DeliveryDeadline` | `DATETIME2(0)` | Có | Không | — | — |
| `EventStartAt` | `DATETIME2(0)` | Có | Không | — | — |
| `Currency` | `VARCHAR(3)` | Không | Không | — | `('USD')` |
| `OrderStatus` | `VARCHAR(30)` | Không | Không | — | `('DRAFT')` |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TransportOrder_Customer` | `CustomerID` | [dbo.Customer](#dbocustomer).`CustomerID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.Notification](#dbonotification).`OrderID` → `OrderID`.
- [dbo.OrderAssignment](#dboorderassignment).`OrderID` → `OrderID`.
- [dbo.OrderRevision](#dboorderrevision).`OrderID` → `OrderID`.
- [dbo.TransportOrderDetail](#dbotransportorderdetail).`OrderID` → `OrderID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TransportOrder` | PRIMARY KEY | OrderID | — | — |
| `UQ_TransportOrder_OrderNumber` | UNIQUE | OrderNumber | — | — |

**CHECK constraints**

Không có.

### dbo.TransportOrderDetail

Ngựa của đơn và thông tin snapshot.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `DetailID` | `BIGINT` | Không | Có | PK | — |
| `OrderID` | `BIGINT` | Không | Không | UNIQUE UQ_TransportOrderDetail_OrderID; FK | — |
| `HorseID` | `BIGINT` | Không | Không | FK | — |
| `SnapshotMicrochip` | `VARCHAR(50)` | Có | Không | — | — |
| `SnapshotSexCode` | `VARCHAR(20)` | Có | Không | — | — |
| `SnapshotWeightKg` | `DECIMAL(8,2)` | Có | Không | — | — |
| `SnapshotHeightCm` | `DECIMAL(6,2)` | Có | Không | — | — |
| `SnapshotBodyWidthCm` | `DECIMAL(6,2)` | Có | Không | — | — |
| `SnapshotMeasuredAt` | `DATETIME2(0)` | Có | Không | — | — |
| `SpecialNeeds` | `NVARCHAR(1000)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TransportOrderDetail_Horse` | `HorseID` | [dbo.Horse](#dbohorse).`HorseID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TransportOrderDetail_Order` | `OrderID` | [dbo.TransportOrder](#dbotransportorder).`OrderID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TransportOrderDetail` | PRIMARY KEY | DetailID | — | — |
| `UQ_TransportOrderDetail_OrderID` | UNIQUE | OrderID | — | — |

**CHECK constraints**

- `CK_TransportOrderDetail_SexCode`: `([SnapshotSexCode]='GELDING' OR [SnapshotSexCode]='MARE' OR [SnapshotSexCode]='STALLION')`. Bật: Có; đã xác thực: Có.
- `CK_TransportOrderDetail_Weight`: `([SnapshotWeightKg]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_TransportOrderDetail_Height`: `([SnapshotHeightCm]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_TransportOrderDetail_Width`: `([SnapshotBodyWidthCm]>(0))`. Bật: Có; đã xác thực: Có.

### dbo.TransportPlan

Kế hoạch vận chuyển và điều kiện xuất phát.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `PlanID` | `BIGINT` | Không | Có | PK | — |
| `RevisionID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_TransportPlan_Version; FK | — |
| `CreatedBy` | `BIGINT` | Không | Không | FK | — |
| `DepartureAuthorizedBy` | `BIGINT` | Có | Không | FK | — |
| `VersionNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_TransportPlan_Version | — |
| `PlannedStartTime` | `DATETIME2(0)` | Có | Không | — | — |
| `PlannedEndTime` | `DATETIME2(0)` | Có | Không | — | — |
| `FinancialGateStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `OperationalGateStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `DocGateStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `GatesCheckedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `DepartureAuthorizedAt` | `DATETIME2(0)` | Có | Không | — | — |
| `PlanStatus` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TransportPlan_CreatedBy` | `CreatedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TransportPlan_DepartureAuthorizedBy` | `DepartureAuthorizedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TransportPlan_RevisionID` | `RevisionID` | [dbo.OrderRevision](#dboorderrevision).`RevisionID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.ActualCost](#dboactualcost).`PlanID` → `PlanID`.
- [dbo.HorseReceipt](#dbohorsereceipt).`PlanID` → `PlanID`.
- [dbo.OperationalAdjustment](#dbooperationaladjustment).`PlanID` → `PlanID`.
- [dbo.TripLeg](#dbotripleg).`PlanID` → `PlanID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TransportPlan` | PRIMARY KEY | PlanID | — | — |
| `UQ_TransportPlan_Version` | UNIQUE | RevisionID, VersionNo | — | — |
| `IX_TransportPlan_CreatedBy` | INDEX | CreatedBy | — | — |
| `IX_TransportPlan_DepartureAuthorizedBy` | INDEX | DepartureAuthorizedBy | — | — |

**CHECK constraints**

- `CK_TransportPlan_VersionNo`: `([VersionNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_TransportPlan_PlannedEndTime_Range`: `([PlannedEndTime] IS NULL OR [PlannedStartTime] IS NOT NULL AND [PlannedEndTime]>=[PlannedStartTime])`. Bật: Có; đã xác thực: Có.

### dbo.TripAssignment

Phân công nhân viên cho chặng.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `AssignmentID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `StaffID` | `BIGINT` | Không | Không | FK | — |
| `RoleInTrip` | `VARCHAR(50)` | Không | Không | — | — |
| `AssignedFrom` | `DATETIME2(0)` | Có | Không | — | — |
| `AssignedTo` | `DATETIME2(0)` | Có | Không | — | — |
| `Status` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TripAssignment_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TripAssignment_StaffID` | `StaffID` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TripAssignment` | PRIMARY KEY | AssignmentID | — | — |
| `IX_TripAssignment_LegID` | INDEX | LegID | — | — |
| `IX_TripAssignment_StaffID` | INDEX | StaffID | — | — |

**CHECK constraints**

- `CK_TripAssignment_AssignedTo_Range`: `([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])`. Bật: Có; đã xác thực: Có.

### dbo.TripHandover

Biên bản bàn giao ngựa.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `HandoverID` | `BIGINT` | Không | Có | PK | — |
| `LegID` | `BIGINT` | Không | Không | FK | — |
| `RecordedBy` | `BIGINT` | Không | Không | FK | — |
| `HandoverType` | `VARCHAR(50)` | Không | Không | — | — |
| `HandoverTime` | `DATETIME2(0)` | Không | Không | — | — |
| `ReceivedByName` | `NVARCHAR(300)` | Không | Không | — | — |
| `ReceiverAuthority` | `VARCHAR(50)` | Có | Không | — | — |
| `ReceiverSignatureFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |
| `HorseConditionStatus` | `VARCHAR(50)` | Không | Không | — | — |
| `Outcome` | `VARCHAR(50)` | Không | Không | — | — |
| `ExceptionNotes` | `NVARCHAR(2000)` | Có | Không | — | — |
| `PhotoFileKey` | `NVARCHAR(1024)` | Có | Không | — | — |
| `CustomerConfirmedAt` | `DATETIME2(0)` | Có | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TripHandover_LegID` | `LegID` | [dbo.TripLeg](#dbotripleg).`LegID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TripHandover_RecordedBy` | `RecordedBy` | [dbo.StaffProfile](#dbostaffprofile).`StaffID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

Không có.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TripHandover` | PRIMARY KEY | HandoverID | — | — |
| `IX_TripHandover_LegID` | INDEX | LegID | — | — |
| `IX_TripHandover_RecordedBy` | INDEX | RecordedBy | — | — |

**CHECK constraints**

Không có.

### dbo.TripLeg

Từng chặng trong kế hoạch vận chuyển.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `LegID` | `BIGINT` | Không | Có | PK | — |
| `PlanID` | `BIGINT` | Không | Không | UNIQUE tổ hợp UQ_TripLeg_Sequence; FK | — |
| `VehicleID` | `BIGINT` | Có | Không | FK | — |
| `SequenceNo` | `INT` | Không | Không | UNIQUE tổ hợp UQ_TripLeg_Sequence | — |
| `TransportMode` | `VARCHAR(50)` | Không | Không | — | — |
| `StartPoint` | `NVARCHAR(300)` | Không | Không | — | — |
| `EndPoint` | `NVARCHAR(300)` | Không | Không | — | — |
| `DistanceKm` | `DECIMAL(12,3)` | Không | Không | — | — |
| `PlannedDeparture` | `DATETIME2(0)` | Có | Không | — | — |
| `PlannedArrival` | `DATETIME2(0)` | Có | Không | — | — |
| `ActualDeparture` | `DATETIME2(0)` | Có | Không | — | — |
| `ActualArrival` | `DATETIME2(0)` | Có | Không | — | — |
| `LegStatus` | `VARCHAR(50)` | Không | Không | — | — |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_TripLeg_PlanID` | `PlanID` | [dbo.TransportPlan](#dbotransportplan).`PlanID` | `NO_ACTION` | `NO_ACTION` | Có / Có |
| `FK_TripLeg_VehicleID` | `VehicleID` | [dbo.FleetVehicle](#dbofleetvehicle).`VehicleID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.ActualCost](#dboactualcost).`LegID` → `LegID`.
- [dbo.FlightReservation](#dboflightreservation).`LegID` → `LegID`.
- [dbo.HorseWelfareLog](#dbohorsewelfarelog).`LegID` → `LegID`.
- [dbo.IncidentReport](#dboincidentreport).`LegID` → `LegID`.
- [dbo.LegMilestoneLog](#dbolegmilestonelog).`LegID` → `LegID`.
- [dbo.StallAssignment](#dbostallassignment).`LegID` → `LegID`.
- [dbo.TripAssignment](#dbotripassignment).`LegID` → `LegID`.
- [dbo.TripHandover](#dbotriphandover).`LegID` → `LegID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_TripLeg` | PRIMARY KEY | LegID | — | — |
| `UQ_TripLeg_Sequence` | UNIQUE | PlanID, SequenceNo | — | — |
| `IX_TripLeg_VehicleID` | INDEX | VehicleID | — | — |

**CHECK constraints**

- `CK_TripLeg_SequenceNo`: `([SequenceNo]>(0))`. Bật: Có; đã xác thực: Có.
- `CK_TripLeg_DistanceKm`: `([DistanceKm]>=(0))`. Bật: Có; đã xác thực: Có.
- `CK_TripLeg_PlannedArrival_Range`: `([PlannedArrival] IS NULL OR [PlannedDeparture] IS NOT NULL AND [PlannedArrival]>=[PlannedDeparture])`. Bật: Có; đã xác thực: Có.
- `CK_TripLeg_ActualArrival_Range`: `([ActualArrival] IS NULL OR [ActualDeparture] IS NOT NULL AND [ActualArrival]>=[ActualDeparture])`. Bật: Có; đã xác thực: Có.

### dbo.User

Tài khoản đăng nhập và vai trò.

| Cột | Kiểu dữ liệu | NULL | Identity | Khóa / index duy nhất | Giá trị mặc định |
|---|---|---|---|---|---|
| `UserID` | `BIGINT` | Không | Có | PK | — |
| `RoleID` | `BIGINT` | Không | Không | FK | — |
| `Email` | `VARCHAR(255)` | Không | Không | UNIQUE UQ_User_Email | — |
| `PasswordHash` | `VARCHAR(255)` | Không | Không | — | — |
| `Status` | `VARCHAR(20)` | Không | Không | — | `('ACTIVE')` |
| `CreatedAt` | `DATETIME2(0)` | Không | Không | — | `(sysutcdatetime())` |

**Khóa ngoại của bảng**

| Tên constraint | Cột | Tham chiếu | ON DELETE | ON UPDATE | Bật / đã xác thực |
|---|---|---|---|---|---|
| `FK_User_Role` | `RoleID` | [dbo.Role](#dborole).`RoleID` | `NO_ACTION` | `NO_ACTION` | Có / Có |

**Các bảng tham chiếu đến bảng này**

- [dbo.AuditEvent](#dboauditevent).`UserID` → `UserID`.
- [dbo.AuthenticationToken](#dboauthenticationtoken).`UserID` → `UserID`.
- [dbo.Customer](#dbocustomer).`UserID` → `UserID`.
- [dbo.Notification](#dbonotification).`UserID` → `UserID`.
- [dbo.StaffProfile](#dbostaffprofile).`UserID` → `UserID`.

**Index và ràng buộc duy nhất**

| Tên | Loại | Cột khóa | Cột INCLUDE | Điều kiện lọc |
|---|---|---|---|---|
| `PK_User` | PRIMARY KEY | UserID | — | — |
| `UQ_User_Email` | UNIQUE | Email | — | — |

**CHECK constraints**

- `CK_User_Status`: `([Status]='LOCKED' OR [Status]='INACTIVE' OR [Status]='ACTIVE')`. Bật: Có; đã xác thực: Có.

## Giới hạn của tài liệu

Khóa ngoại xác nhận bản ghi tham chiếu tồn tại, không tự bảo đảm quyền nhân viên hoặc quyền sở hữu ngựa. Các quy tắc liên quan nhiều dòng như tổng báo giá, phân bổ thanh toán, lịch tài nguyên và điều kiện xuất phát cần được xử lý ở ứng dụng hoặc cơ chế database bổ sung.
