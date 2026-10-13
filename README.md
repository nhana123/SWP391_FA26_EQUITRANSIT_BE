# EquiTransit backend — account flow

Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.9. Kiến trúc monolith phân lớp: Controller → Service → Repository → SQL Server. Entity chỉ dùng cho persistence; request/response dùng DTO.

## Database và nguồn nghiệp vụ

- Database thật: `EquiTransit`, server `DESKTOP-9VRARFD`, TCP 1433; dùng Windows Authentication ở profile `local`.
- Mapping lấy từ `docs/database-schema.md` và metadata live: `Role`, `User`, `Customer` giữ nguyên tên, độ dài, Unicode và nullable.
- Đọc plugin `SWP-RE/manifest.json`, `code.js` và catalogue `english-screens.js`. A01 là sign-in theo vai trò; A02 là đăng ký khách cá nhân; A03 yêu cầu email verification/recovery; C17 là hồ sơ cá nhân. Catalogue không phải một kết nối trực tiếp đến Figma đang mở.
- Public registration không có trường role. Staff accounts không được tạo từ API đăng ký khách.

## Chạy trên máy hiện tại

1. IntelliJ: **File → Open → pom.xml → Open as Project**.
2. Chọn Project SDK **21** và Maven **Use Maven Wrapper**.
3. Reload Maven. Chọn run configuration **EquiTransit Local** rồi Run. Nếu IntelliJ hỏi module, chọn module Maven `backend`.
4. Hoặc chạy PowerShell trong BE:

```powershell
.\run-local.ps1
```

API ở `http://127.0.0.1:8080`; kiểm tra `GET /api/health`. Thư viện Windows JDBC native nằm trong `%LOCALAPPDATA%\EquiTransit\tools\sqljdbc-native`. Local profile tin chứng chỉ SQL Server tự ký chỉ để phát triển; profile mặc định yêu cầu xác thực chứng chỉ.

Nếu bản backend chạy nền để kiểm tra đang dùng cổng 8080, chạy `.\tools\stop-local.ps1` trước khi Run trong IntelliJ. Với ứng dụng chạy bằng run-local.ps1, nhấn Ctrl+C ở terminal để dừng.

JWT key cục bộ được sinh ngẫu nhiên, giữ ở `.local/`, không đưa lên Git. Run configuration đọc `.local/application-secrets.properties`; không lưu secret trong XML run configuration. Nếu dùng terminal mới, mở lại terminal/IntelliJ để nhận JAVA_HOME và PATH đã cập nhật.

## Các API

| Method | URL | Quyền | Kết quả |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness |
| POST | `/api/auth/register` | Public | 201; tạo customer INACTIVE |
| POST | `/api/auth/verify-email` | Public, one-time token | 200; chuyển sang ACTIVE |
| POST | `/api/auth/resend-verification` | Public | 202; response giống nhau dù email không đủ điều kiện |
| POST | `/api/auth/login` | Public | 200; access token nếu ACTIVE |
| POST | `/api/auth/forgot-password` | Public | 202; response giống nhau dù email không đủ điều kiện |
| POST | `/api/auth/reset-password` | Public, one-time token | 200; đổi mật khẩu và vô hiệu JWT cũ |
| GET | `/api/customers/me` | CUSTOMER bearer token | 200; hồ sơ của token subject |

Đăng ký:

```json
{
  "email": "minh@example.com",
  "password": "ExamplePassword123!",
  "confirmPassword": "ExamplePassword123!",
  "fullName": "Nguyen Van Minh",
  "phone": "+84901234567",
  "address": "Ho Chi Minh City"
}
```

Mật khẩu 12–72 ký tự và tối đa 72 byte UTF-8 (bcrypt). Email ASCII được trim/lowercase. Không normalize/trim mật khẩu. Phone/address có thể bỏ trống. Giới hạn tên, email, phone/address khớp database. Không đưa mật khẩu hoặc token vào log.

Sau đăng ký, profile local lưu email trong `.local/outbox/`. Đọc file mới nhất để lấy giá trị sau `?token=`; gửi nó qua POST verify-email:

```json
{"token": "TOKEN_FROM_LOCAL_EMAIL"}
```

Email local **không được gửi ra hộp thư thật**. Link trỏ đến route frontend tương ứng; frontend chưa được triển khai trong thay đổi này. Dùng Postman để gọi API backend trước.

Đăng nhập:

```json
{"email": "minh@example.com", "password": "ExamplePassword123!"}
```

Gọi `/api/customers/me` với header `Authorization: Bearer <accessToken>`. Access token hết hạn sau 15 phút. Đây chưa phải refresh-token hoặc logout/revocation session flow; hết hạn thì đăng nhập lại. Mỗi request protected kiểm tra trạng thái/role/password fingerprint từ DB, nên lock account hoặc reset password làm token cũ không dùng được.

Recovery: gửi email tới forgot-password, lấy token trong email mới, rồi POST reset-password với `token`, `password`, `confirmPassword`. Token xác minh có hạn 24 giờ; token reset 30 phút; chỉ dùng một lần. Resend xóa token chưa dùng cũ, giữ lịch sử token đã tiêu thụ. Tài khoản đã xác minh rồi bị admin đổi sang INACTIVE không tự kích hoạt lại qua resend.

## Migration bổ sung

`database/migrations/001_authentication_token.sql` tạo một bảng mới `AuthenticationToken` để đáp ứng A03. Chỉ lưu hash token, purpose, hạn dùng và thời điểm dùng; raw token chỉ nằm trong email. Không sửa cột hoặc dữ liệu hiện có của 49 bảng nghiệp vụ.

Chạy migration một lần trước khi khởi động trên database chưa có bảng này. Script có guard để chạy lại an toàn. Hibernate dùng **validate**, không dùng update/create trên database thật.

```powershell
sqlcmd -S localhost -E -d EquiTransit -b `
  -i database/migrations/001_authentication_token.sql
```

## Kiểm thử và build

```powershell
.\run-local.ps1 -Verify
```

Hoặc JDK 21 đã có trong terminal:

```powershell
.\mvnw.cmd verify
```

Default tests dùng H2 trong bộ nhớ. `AuthFlowTest` có guard từ chối cleanup trên database thật. Để kiểm thử SQL Server thực tế, tạo **EquiTransit_AuthTest** bằng script tools/create-auth-test-db.sql, chạy migration token trên database test đó, sau đó:

```powershell
$env:EQ_TEST_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=EquiTransit_AuthTest;encrypt=true;trustServerCertificate=true;integratedSecurity=true'
$env:EQ_TEST_DDL = 'validate'
$nativeDir = Join-Path $env:LOCALAPPDATA 'EquiTransit\tools\sqljdbc-native'
.\mvnw.cmd verify "-DargLine=-Djava.library.path=$nativeDir"
```

Mở terminal mới hoặc xóa hai EQ_TEST_* env vars để quay lại H2. Không đổi tên database test thành EquiTransit: các test xóa fixture trong database test.

## Java conventions

Theo PDF Oracle/Sun Java Code Conventions được cung cấp: 4 spaces, không tab; 80 ký tự kể cả import (import dài được xuống dòng); tên class/method/field/constant theo quy ước; một public type/file; comment đầu file; Javadoc public API; braces cho control flow; khai báo local variables đầu block; static fields trước instance fields, constructors trước methods; phân nhóm bằng dòng trống.

Checkstyle chạy ở Maven validate, bao gồm source và tests. Cấu hình ở config/checkstyle.xml. Các quy tắc mang tính ngữ nghĩa như ý nghĩa tên, độ chính xác comment, khai báo ở đầu block, hai dòng trống giữa các phần và các quyết định nghiệp vụ còn cần review thủ công. Dùng Java 21/Spring annotation và try-with-resources là bổ sung hiện đại; tài liệu 1997 không mô tả chúng.

## Chạy ngoài môi trường local

Đặt EQ_DB_URL/EQ_DB_USERNAME/EQ_DB_PASSWORD, EQ_JWT_SECRET (Base64, tối thiểu 32 byte ngẫu nhiên), EQ_FRONTEND_URL, EQ_CORS_ORIGIN và EQ_SMTP_HOST/PORT/USERNAME/PASSWORD/MAIL_FROM. Không bật profile local để tránh gửi email qua file hoặc tin chứng chỉ tự ký.

SMTP email được xử lý sau khi transaction commit. Nếu gửi mail lỗi, tài khoản vẫn được giữ INACTIVE và người dùng có thể resend; hiện chưa có durable email queue/retry worker. Rate limit 20 auth requests/phút/IP là bộ nhớ trên một node; khi triển khai nhiều node cần rate limiter chung và cấu hình reverse proxy phù hợp. Ứng dụng mặc định bind localhost; triển khai thật cần cấu hình bind và TLS tại reverse proxy.

Không commit `.local`, credentials, outbox email hoặc bản ghi khách hàng. `.gitignore` đã loại các file đó, target và cấu hình IntelliJ cá nhân.

## Git workflow

Nhánh tích hợp của repository này là `dev`. Tạo nhánh task từ `dev`, mở Pull Request vào `dev` và yêu cầu một thành viên khác review. `main` dành cho bản phát hành đã kiểm thử.
