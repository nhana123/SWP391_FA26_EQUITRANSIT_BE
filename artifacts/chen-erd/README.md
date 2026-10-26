# EquiTransit — ERD theo ký hiệu Chen

Nguồn: metadata đọc trực tiếp từ EquiTransit_Demo ngày 23/10/2026.
Chỉ đọc schema; không truy cập DB thật, không đọc hồ sơ khách hàng, không sửa DB.

## Mở file

- EquiTransit-Chen-ERD.html: bản đầy đủ tự chứa, mở offline; chọn sơ đồ,
  cuộn để zoom, kéo để di chuyển, nút Fit để xem trọn trang, Tải SVG để xuất.
- 00-overview.png / .svg: tổng quan 16 thực thể lõi, 18 quan hệ, thuộc tính chọn lọc.
- full-schema.png / .svg: toàn bộ 73 bảng, 152 quan hệ sau khi gộp FK dư.
  Ảnh đầy đủ rất lớn; nên xem bằng HTML hoặc SVG để đọc chữ khi zoom.
- 01–09: các nhóm nghiệp vụ. Một thực thể có thể được nhắc lại ở trang khác
  để giữ liên kết với nhóm liên quan; đó vẫn là cùng một thực thể trong DB.
- attributes-*.svg: từ điển thuộc tính theo Chen cho từng bảng.
- schema.json, unique-keys.json: metadata gốc, không chứa bản ghi nghiệp vụ.
- relationships.csv, manifest.json: đối chiếu mỗi hình thoi với FK thật.
- qa.json, viewer-qa.json: kiểm tra độ bao phủ, bố cục và trình xem.
- generate.cjs và *.dot: nguồn tạo sơ đồ để có thể chỉnh sửa/tái tạo.

## Quy tắc ký hiệu

1. Hình chữ nhật xám: thực thể, tên khớp bảng DB (tách từ để dễ đọc).
2. Hình thoi tím: quan hệ. Đường nối không có mũi tên và không dùng crow's foot.
3. Hình bầu dục xanh: một thuộc tính, khóa chính được gạch chân.
4. 1/N biểu diễn bội số tối đa theo hướng Chen: phía thực thể cha là 1,
   phía thực thể con là N; phía con đổi thành 1 nếu unique key chứng minh 1:1.
5. Đường đôi ở phía con: FK bắt buộc, con phải tham gia quan hệ.
   Đường đơn ở phía con: FK nullable, con có thể chưa tham gia.
   Phía cha dùng đường đơn vì FK không bắt buộc cha phải có ít nhất một con.
6. Khóa ngoại được biểu diễn bằng quan hệ, không lặp thành thuộc tính oval;
   cột thuộc PK vẫn được giữ và gạch chân. 775 cột đều được đối chiếu
   qua thuộc tính hoặc ánh xạ FK trong manifest.
7. FK nhiều cột được gộp thành một quan hệ, không vẽ riêng từng cột.
8. Không tự suy diễn thực thể yếu, thuộc tính đa trị hoặc thuộc tính dẫn xuất.
   Có FK không đồng nghĩa thực thể yếu. FullName không bị tự tách first/last name.
   Cột JSON không tự động được coi là thuộc tính đa trị của Chen.

## Kết quả đối chiếu

- 73 bảng, 775 cột, 156 ràng buộc FK (169 cặp cột FK).
- 13 FK nhiều cột. Bốn ràng buộc FK dư được gom vào quan hệ tương ứng:
  kết quả 152 hình thoi, giữ toàn bộ tên constraint trong manifest.
- Toàn bộ FK đang enabled và trusted tại thời điểm đọc.
- Các trang Graphviz đã kiểm tra bounding box: 0 cặp hình chồng nhau.
- Trình xem đã kiểm tra bằng Edge headless: mở SVG, chuyển sơ đồ đầy đủ,
  xem thuộc tính và zoom hoạt động; không có lỗi JavaScript.
- Đây là đối chiếu mô hình/schema, không phải kiểm toán mọi dòng dữ liệu thực tế.

## Những chỗ được sửa so với ảnh gốc

- Email / RoleCode nằm ở User / Role, không tự gán thành cột Customer hoặc StaffProfile.
- Customer có FullName; không có CompanyName trong snapshot này.
- Quote tham chiếu OrderRevision; lịch cọc/đợt thanh toán nằm ở QuotePaymentSchedule.
- Đơn và ngựa được nối qua TransportOrderDetail. Unique key hiện tại giới hạn
  tối đa một dòng chi tiết cho mỗi đơn; một ngựa có thể xuất hiện ở nhiều đơn.
- Phân công nhân viên đi qua OrderAssignment và TripAssignment.
- TripLeg có VehicleID nullable; xe có HorseCapacity, không phải capacity slots.
- estimatedAirfare là dữ liệu DTO được tổng hợp sang SupplierOffer; không vẽ
  nó như một cột được lưu trong TripLeg.
- Giữ các liên kết revision/current-plan/quote và khóa ngoại nhiều cột trong
  bản đầy đủ; tổng quan chỉ chọn các quan hệ lõi để không gây rối.

Tài liệu ký hiệu tham chiếu: Peter P. Chen, The Entity-Relationship Model—Toward
 a Unified View of Data (1976), https://doi.org/10.1145/320434.320440.
