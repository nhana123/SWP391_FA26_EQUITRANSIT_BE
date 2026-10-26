# EquiTransit — UML sequence diagrams cho 5 actor

Ngày đối chiếu code: 2026-10-23.

Mở `EquiTransit-Sequence-Diagrams.html` bằng trình duyệt để chọn actor,
phóng to và tải SVG. Không cần mạng hoặc server. PNG dùng để chèn báo cáo;
SVG giữ nét khi phóng lớn. `generate.cjs` và `diagrams.json` là nguồn chỉnh sửa.

## Phạm vi

Đây là 5 sequence diagram của **5 luồng chính**, mỗi actor một sơ đồ,
không phải danh mục sequence cho toàn bộ use case của hệ thống.

| File | Actor | Luồng |
| --- | --- | --- |
| 01-customer | Customer | Tạo, lưu, submit và revise transport request |
| 02-manager | Manager | Quyết định assessment và phân công/thay case team |
| 03-coordinator | Coordinator | Danh sách plan, draft, tài nguyên, confirm |
| 04-specialist | Specialist | Checklist, nộp thay, review và clear phase |
| 05-driver | Driver | Đọc handover, bàn giao trung gian hoặc final delivery |

## Ký hiệu UML

- Hình người: actor bên ngoài phần mềm.
- Hình chữ nhật xanh `: ClassName`: participant đối tượng không đặt tên.
- Đường dọc nét đứt: lifeline; thời gian tăng từ trên xuống.
- Thanh chữ nhật xanh hẹp: execution specification (activation).
- Mũi tên liền, đầu tam giác đặc: synchronous call.
- Mũi tên đứt, đầu mở: reply của lời gọi.
- `opt [guard]`: thực hiện nếu điều kiện đúng, có thể bỏ qua.
- `alt`: các nhánh loại trừ theo guard, ngăn bởi đường ngang nét đứt.
- `loop [guard]`: lặp cho các submission cần review.
- Hộp vàng nhạt: chú thích endpoint/điều kiện, không phải participant.

Tên View là nhãn boundary khái niệm phục vụ tài liệu, không khẳng định FE
có class tương ứng. Tên Controller/Service và method đối chiếu Backend.
Phần gọi repository/SQL được lược bỏ ở mức phân tích này. Chưa mô tả mọi
exception, authorization failure và retry branch; ghi chú các gate chính.
`recordVersion` và `operationId` được đóng gói trong request DTO theo API.
HTTP status trên reply mô tả kết quả HTTP, không phải kiểu Java của Service.

## Đối chiếu code

Đường dẫn tương đối với repo Backend:

- `src/main/java/com/equitransit/backend/controller/TransportOrderController.java`
  → `service/TransportOrderService.java`: create, saveDraft, submit, revise.
- `src/main/java/com/equitransit/backend/controller/ManagerAssessmentController.java`
  → `service/OrderAssessmentService.java`: decide, assign.
- `src/main/java/com/equitransit/backend/controller/OperationalPlanController.java`
  → `service/OperationalPlanService.java`: list, createDraft, assignRoad,
  assignStall, reserveFlight, confirm.
- `src/main/java/com/equitransit/backend/controller/ComplianceController.java`
  → `service/ComplianceService.java`: overview, uploadAndSubmit, review, clear.
- `src/main/java/com/equitransit/backend/controller/handover/DriverHandoverController.java`
  → `service/handover/TripHandoverService.java`: getHandoversForLeg,
  recordIntermediateByDriver, recordFinalDeliveryByDriver.
- `src/main/java/com/equitransit/backend/service/handover/FinalDeliveryValidator.java`:
  kiểm tra clearance QUARANTINE_RELEASE, người nhận và file bắt buộc.

## Ranh giới nghiệp vụ cần giữ

- Submit khác với approve, accept quote và confirm booking.
- Assessment APPROVED và phân công ban đầu nằm trong cùng transaction.
- Coordinator tạo draft theo PlanDraftRequest; nhánh Journey Graph dùng
  JourneyGraphService riêng nên không giả định gọi qua service trên sơ đồ.
- Chuẩn bị ROAD/AIR trên sơ đồ Coordinator là ví dụ tuyến hỗn hợp. Chặng
  thực tế chỉ ROAD thì không làm các bước riêng AIR. Quarantine/insurance
  nếu bắt buộc cũng phải sẵn sàng trước confirm, như ghi chú trên sơ đồ.
- Clear từng phase độc lập, không mặc định cả ba phase cùng clear một lúc.
- Customer có thể nộp bằng upload-and-submit; trên sơ đồ Specialist là
  nhánh Specialist nộp thay, không giả định Specialist luôn nộp mọi file.
- Final delivery kết thúc ở DELIVERED. Manager đóng CLOSED bằng API riêng.
- Ký tên/chụp ảnh dùng fileKey từ API HandoverFileController riêng, không
  giả định DriverHandoverController trực tiếp nhận Base64.

Chỉ tạo tài liệu trong artifacts; không sửa code ứng dụng hoặc dữ liệu DB.
