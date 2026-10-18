# FE request: chọn tài nguyên khả dụng trong Planning

**Vai trò:** Coordinator đang được phân công đơn; Manager. Ở màn hình
`Check resource availability` và khi gán xe/chuồng cho một chặng trong
`Operational plans`, thay ô nhập `Resource ID` bằng combobox tìm kiếm.

## API BE

`POST /api/resources/{kind}/candidates?search=STALL&page=0&size=10`

- `kind`: `STALL` cho chặng `AIR`, `VEHICLE` cho chặng `ROAD`.
- Bearer JWT. Manager active hoặc Coordinator active đang được phân công
  đúng `orderId`; sai quyền trả 403, không thuộc case trả 404.
- Body giống API kiểm tra một tài nguyên đang có:

```json
{
  "orderId": 4,
  "revisionId": 4,
  "mode": "AIR",
  "start": "2026-11-15T15:00:00+07:00",
  "end": "2026-11-15T22:00:00+09:00",
  "carrierSupplierId": 21,
  "legId": 6
}
```

`carrierSupplierId` và `legId` có thể là `null`. Khi mở từ một chặng
Planning, FE tự lấy `orderId`, `revisionId`, `mode`, giờ và `legId` từ
chặng; không yêu cầu người dùng gõ ID. Hai thời điểm phải có offset.

Response gồm `items`, `page`, `size`, `totalElements`. Mỗi item:

```json
{
  "id": 11,
  "code": "STALL-101",
  "status": "AVAILABLE",
  "recordVersion": "000000000001A2B3",
  "blockers": []
}
```

`status` là `AVAILABLE`, `PENDING` hoặc `UNAVAILABLE`. `blockers` dùng
mã có kiểu từ API F09 hiện tại; không hiển thị thông tin của đơn khác.
Danh sách phân trang theo **mã tài nguyên**, không phải số lượng còn
trống. `size` từ 1 đến 10, `page` bắt đầu 0. `search` tìm theo tiền tố mã,
dài 1..50 ký tự nếu gửi; không gửi chuỗi rỗng/toàn khoảng trắng. Ký tự
`%`, `_`, `[` được hiểu theo nghĩa đen. Kết quả chưa giữ chỗ.

## UI và tiêu chí hoàn thành

1. Sau khi có đủ loại tài nguyên và cửa sổ thời gian, tải trang đầu.
   Combobox hiển thị `code` và trạng thái. Có tìm kiếm, tải trang tiếp,
   loading, empty và error rõ ràng; không tải toàn bộ danh mục một lần.
2. Ưu tiên các item `AVAILABLE`; cho thấy `PENDING`/`UNAVAILABLE` cùng
   nguyên nhân bằng ngôn ngữ dễ hiểu nhưng không cho chọn để gán.
3. Khi chọn item, lưu `id` và `recordVersion` của **cùng item**. Ở màn
   kiểm tra riêng, gọi lại `POST /api/resources/{kind}/{id}/availability`
   để hiển thị bằng chứng mới nhất.
4. Trước khi gán trong Planning, gửi `id` và `recordVersion` quan sát.
   Nếu BE trả 409 do tài nguyên/phiên bản thay đổi, làm mới danh sách và
   yêu cầu chọn lại. Không coi trạng thái combobox là xác nhận đặt chỗ.
5. Đổi giờ, carrier hoặc chặng phải xóa lựa chọn cũ và tải lại; không
   giữ ID từ một khoảng thời gian khác. Giữ `PRIVATE` không ghép chung;
   `SHARED_ONE`/`WIDE` chỉ được ghép khi backend xác nhận cùng chuyến,
   fit và đủ slot.
6. Không dùng `/api/manager/resources/**` cho Coordinator vì endpoint
   danh mục đó chỉ dành cho Manager. Không hiển thị giá vốn, đơn khác
   hoặc ghi chú nội bộ trong combobox.
7. Dùng API client/Auth hiện có. POST candidates là truy vấn, không có
   `command`, `operationId` hoặc `recordVersion` trong request. Khi gán
   Planning, giữ `command.recordVersion` của đơn và thêm version tài
   nguyên vào `vehicleRecordVersion`/`stallRecordVersion` theo DTO F10.

**Ghi chú:** API cũ kiểm tra từng ID và thao tác gán trong Planning vẫn
là bước xác nhận cuối. BE kiểm tra lại dưới khóa giao dịch khi lưu gán.

## Kiểm chứng BE ngày 19/10/2026

`clean verify` PASS trên bản chụp cùng source, dùng DB test riêng
`EquiTransit_FigmaMigrationTest_20261013_143729`. SQL resources 14/14,
DTO contract 21/21, OpenAPI 11/11 đều PASS, không skip; Checkstyle 0 lỗi.
Tổng suite 314 cases, 159 test opt-in khác chưa bật và bị skip.
Đã kiểm tra quyền, revision/mode, buffer, own-leg replacement, shared
1.5+1.5, overflow và PRIVATE; fixture resources còn 0 sau test.
Server local đang chạy JAR cũ; push source không tự deploy endpoint mới.
