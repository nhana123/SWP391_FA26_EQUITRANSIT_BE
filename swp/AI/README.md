# Hướng dẫn AI cho dự án EquiTransit

Đây là thư mục chung chứa skill và tài liệu định hướng cho AI làm backend.
Các đường dẫn trong SKILL.md tính từ root repository.

Đọc theo thứ tự:

1. [Requirement hiện tại](docs/requirements/EQUITRANSIT_REQUIREMENTS.md): scope,
   actor, 16 luồng và tiêu chí nghiệm thu.
2. [Hợp đồng nghiệp vụ và Java](docs/team/PROJECT_CONTRACT.md): quy tắc dùng
   chung, gate, money, phối hợp BE1/BE2 và Definition of Done.
3. [Backlog/phân công](docs/team/BACKLOG.md), [criteria từng issue](docs/team/issues.json)
   và [số/URL GitHub issue](docs/team/github-issues.json).
4. Skill theo người phụ trách:
   [BE1 commercial](skills/equitransit-be1-commercial/SKILL.md) hoặc
   [BE2 operations](skills/equitransit-be2-operations/SKILL.md).
5. [Schema SQL Server](docs/database-schema.md) và
   [thay đổi theo Figma](docs/database-figma-alignment.md) khi làm persistence;
   [review auth](docs/auth-review.md) khi dùng hoặc sửa flow tài khoản.

[source-versions.json](docs/team/source-versions.json) ghi nguồn và giới hạn
đối chiếu Figma. Requirement không chứng minh API đã được triển khai; kiểm code
và trạng thái PR của issue đang làm. Thư mục này không chứa secret hoặc dữ liệu
khách hàng thật. Source Java, migrations và công cụ triển khai vẫn ở thư mục
chức năng của repository; không sao chép chúng vào hướng dẫn AI.
