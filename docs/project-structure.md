# Cấu trúc package Java

Project giữ kiến trúc phân tầng. Trong mỗi tầng, các class được nhóm theo
nghiệp vụ; quy ước này áp dụng cho cả BE1 và BE2.

Đường dẫn gốc: `src/main/java/com/equitransit/backend/`.

```text
backend/
├── BackendApplication.java
├── controller/<nghiệp vụ>/
├── service/<nghiệp vụ>/
├── repository/<nghiệp vụ>/
├── entity/<nghiệp vụ>/
├── dto/<nghiệp vụ>/
│   ├── request/
│   └── response/
├── dto/common/
├── dto/validation/
├── exception/
│   ├── compliance/
│   ├── horse/
│   └── order/
├── json/order/
├── config/
├── security/
└── mail/
```

Mỗi tầng chỉ có các nhóm mà tầng đó thực sự sử dụng. Các model đã được gom
trong `dto/plan`, `dto/resource`, `dto/quote` giữ cách tổ chức hiện có; không
tạo thư mục rỗng hoặc tách thêm chỉ để đồng nhất hình thức.

| Nhóm | Nội dung |
|---|---|
| `auth` | Đăng ký, đăng nhập, token và tài khoản |
| `customer` | Hồ sơ khách hàng |
| `horse` | Hồ sơ ngựa |
| `order` | Đơn vận chuyển, revision và lựa chọn dịch vụ của đơn |
| `assessment` | Phân công và đánh giá đơn, hồ sơ nhân viên |
| `pricing` | Dịch vụ đặt chỗ, nguồn tính phí và đánh giá độ phù hợp chuồng |
| `quote` | DTO, entity và truy vấn báo giá |
| `payment` | Truy vấn thanh toán và chính sách sẵn sàng tài chính |
| `activity` | Truy vấn hoạt động và lọc dữ liệu audit |
| `compliance` | F08: giấy tờ, luật, thủ tục, đánh giá cách ly và cổng pháp lý |
| `resource` | F09: danh mục, lịch khả dụng và tài nguyên tham chiếu |
| `plan` | F10: kế hoạch vận hành, chặng và lịch trình |
| `system` | API health |
| `common` | Ví dụ, giới hạn validation và response dùng chung |

`config`, `security`, `mail` và `dto/validation` là các nhóm kỹ thuật dùng
chung. `ApiException` và `ApiExceptionHandler` vẫn ở gốc `exception` để xử lý
lỗi của nhiều nghiệp vụ.

Class dùng chung giữa các feature vẫn nằm trong nhóm cung cấp hợp đồng đó.
Ví dụ, `service/pricing/StallFitEvaluator` được F09 sử dụng; không cần chép
class này vào `service/resource`. Tương tự, F10 gọi các hợp đồng assessment,
pricing và compliance qua import của package tương ứng.

Test nằm tại `src/test/java/com/equitransit/backend/<nghiệp vụ>/`. Hai test
`ApiDtoContractTest` và `OpenApiTest` kiểm tra API toàn project nên giữ ở
package gốc. Helper lưu trữ của compliance đi cùng các test compliance.

Khi thêm hoặc chuyển file Java, đường dẫn thư mục phải khớp khai báo
`package`; cập nhật toàn bộ import và tham chiếu liên quan. Giữ
`BackendApplication` tại package gốc để Spring quét các nhóm con. Tên class,
đường dẫn API, DTO JSON, bean và tên bảng SQL không đổi theo vị trí thư mục.

Migration vẫn nằm tại `database/migrations`, theo thứ tự số đã thống nhất.
SQL fixture của test vẫn nằm tại `src/test/resources`.

Kiểm tra bằng JDK 21 và Maven Wrapper: `.\mvnw.cmd verify`. Các bộ test native
SQL Server vẫn cần được bật rõ ràng theo tài liệu từng feature.
