# EquiTransit Backend

Backend vận chuyển ngựa xuyên biên giới: **Java 21 · Spring Boot 3.5.16 ·
SQL Server · Maven Wrapper**. Kiến trúc monolith: Controller → Service → Repository.

## Chạy local

Cần JDK 21 và SQL Server có schema/configuration phù hợp với profile `local`.
Chạy PowerShell tại thư mục BE; lần đầu thiết lập JDK và JDBC native:

```powershell
.\tools\setup-java.ps1 -JdkHome 'C:\path\to\jdk-21'
.\run-local.ps1
```

Thay đường dẫn JDK bằng thư mục cài trên máy. Có thể mở `pom.xml` trong
IntelliJ, chọn SDK 21, Maven Wrapper và run configuration **EquiTransit Local**.

| Công cụ | URL |
|---|---|
| Swagger UI | http://127.0.0.1:8080/swagger-ui/index.html |
| OpenAPI JSON | http://127.0.0.1:8080/v3/api-docs |
| Health | http://127.0.0.1:8080/api/health |

Đăng nhập qua `/api/auth/login`, lấy `accessToken` rồi dán vào **Authorize**
trong Swagger (không thêm `Bearer`). Email local nằm trong `.local/outbox/`,
không gửi đến Gmail. Không commit secret hoặc thư mục `.local/`.

Dừng ứng dụng bằng **Ctrl+C**; bản chạy nền dùng `.\tools\stop-local.ps1`.

## Build và kiểm thử

```powershell
.\mvnw.cmd verify
```

Cần JDK 21 trong terminal. SQL integration tests phải bật riêng và dùng DB test;
không chạy test trên DB thật. Migrations nằm trong
[`database/migrations/`](database/migrations/), cần kiểm tra trước khi áp dụng.
Hibernate không tự cập nhật schema nghiệp vụ.

## Tài liệu API

| Phạm vi | Tài liệu |
|---|---|
| Auth, hồ sơ ngựa, đơn hàng, phân công và quản trị nhân sự | [Tham khảo chi tiết](docs/backend-reference.md) |
| F05/F15 — Dịch vụ và quản lý giá | [Service pricing](docs/service-pricing-api.md) |
| F06 — Báo giá có phiên bản | [Quotation](docs/quotation-api.md) |
| F07 — Thanh toán và hoàn tiền | [Manual bank payments](docs/manual-bank-payments-api.md) |
| F08 — Hồ sơ pháp lý | [Compliance](docs/compliance-api.md) |
| F09 — Tài nguyên và kiểm tra phù hợp | [Resources](docs/resources-api.md) |
| F10 — Kế hoạch vận chuyển | [Operational plan](docs/operational-plan-api.md) |
| F11 — Điều kiện xuất phát và nhận ngựa | [Departure](docs/departure-api.md) |
| F12 — Thực thi chặng, tracking và chăm sóc | [Execution](docs/leg-execution-api.md) |
| F16 — Dashboard, thông báo và audit | [Activity](docs/activity-api.md) |
| Chuẩn DTO/OpenAPI | [DTO audit](docs/api-dto-audit.md) |
| Đồng bộ DB test | [Database setup](docs/database-sync.md) |
| Bàn giao F13/F14 | [BE2 handoff](docs/team/BE2_F13_F14_HANDOFF_SPEC.md) |

Swagger mô tả hợp đồng API từ source hiện tại. Quy ước Java: **4 spaces,
không tab, tối đa 80 ký tự/dòng**. Làm việc trên nhánh feature/fix và mở PR vào `dev`.
