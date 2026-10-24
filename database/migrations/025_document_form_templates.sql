SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo', 'EquiTransit_Test')
    THROW 51025, 'Document templates are restricted to Demo/Test.', 1;
IF OBJECT_ID('dbo.LocalFileObject','U') IS NULL
    THROW 51025, 'Apply migration024 before document templates.', 1;
IF OBJECT_ID('dbo.SchemaMigrationHistory','U') IS NULL
    THROW 51025, 'Apply the baseline migration history first.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.DocumentTypeCatalog','U') IS NULL
    CREATE TABLE dbo.DocumentTypeCatalog (
        TypeCode varchar(50) NOT NULL PRIMARY KEY,
        DisplayName nvarchar(200) NOT NULL,
        Description nvarchar(1000) NULL,
        IsActive bit NOT NULL CONSTRAINT DF_TemplateCatalog_Active DEFAULT 1
    );
IF COL_LENGTH('dbo.DocumentTypeCatalog','FormCode') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD FormCode varchar(100) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','IssuingAuthority') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD IssuingAuthority nvarchar(255) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','SubAuthority') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD SubAuthority nvarchar(255) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','CertTextTemplate') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD CertTextTemplate nvarchar(max) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','BadgeColor') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD BadgeColor varchar(30) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','EmblemIcon') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog ADD EmblemIcon varchar(50) NULL;
IF COL_LENGTH('dbo.DocumentTypeCatalog','TemplateFileKey') IS NULL
    ALTER TABLE dbo.DocumentTypeCatalog
        ADD TemplateFileKey uniqueidentifier NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentTypeCatalog')
    AND name='FK_DocumentTypeCatalog_TemplateFileKey')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.DocumentTypeCatalog WITH CHECK
        ADD CONSTRAINT FK_DocumentTypeCatalog_TemplateFileKey
        FOREIGN KEY(TemplateFileKey) REFERENCES dbo.LocalFileObject(FileKey)';
-- Project reference layouts, not authenticated authority-issued certificates.
-- Official blank PDFs are uploaded separately after staff verification.
DECLARE @seed TABLE (
    TypeCode varchar(50), DisplayName nvarchar(200), FormCode varchar(100),
    IssuingAuthority nvarchar(255), SubAuthority nvarchar(255),
    CertTextTemplate nvarchar(max), BadgeColor varchar(30), EmblemIcon varchar(50)
);
INSERT @seed VALUES
('FEI_PASSPORT',N'Hộ chiếu ngựa quốc tế và đối soát vi mạch',
 'FEI-PASSPORT / ISO-MICROCHIP (REFERENCE)',
 N'Cơ quan cấp hộ chiếu ngựa có thẩm quyền',
 N'Đối soát vi mạch ISO theo hồ sơ được xác minh',
 N'MẪU THAM KHẢO - CHƯA KÝ. Tên ngựa: ______. Vi mạch: ______. '
 + N'Số hộ chiếu: ______. Cơ quan/người đối soát: ______. Ngày: ______.',
 'blue','passport'),
('COGGINS_EIA',N'Phiếu xét nghiệm Coggins / EIA',
 'AGID-EIA-TEST FORM 10-11',
 N'Cơ quan thú y / phòng xét nghiệm được chấp thuận',
 N'AGID - kết quả phải do phòng xét nghiệm xác nhận',
 N'MẪU THAM KHẢO - CHƯA KÝ. Mẫu huyết thanh: ______. Ngày lấy mẫu: ______. '
 + N'Phương pháp AGID: ______. Kết quả EIA (âm tính/dương tính/chưa xác định): '
 + N'______. Người có thẩm quyền xác nhận: ______.',
 'volcano','blood'),
('EQUINE_INFLUENZA',N'Chứng nhận tiêm vaccine cúm ngựa',
 'FEI VAX-CERT-2026/ANNEX-VI',
 N'Bác sĩ thú y được cơ quan có thẩm quyền chấp thuận',
 N'Tham chiếu WOAH / FEI - cần xác minh quy chuẩn hiện hành',
 N'MẪU THAM KHẢO - CHƯA KÝ. Vaccine: ______. Lô: ______. '
 + N'Ngày tiêm: ______. Lịch nhắc lại: ______. Vi mạch ngựa: ______. '
 + N'Bác sĩ thú y và chữ ký: ______.',
 'green','syringe'),
('EXPORT_PERMIT',N'Giấy phép kiểm dịch xuất nhập cảnh',
 'DAFF-AFCD CUSTOMS PERMIT',
 N'Cục Thú y / cơ quan thú y xuất nhập khẩu có thẩm quyền',
 N'Thủ tục xuất nhập khẩu của tuyến cần được xác minh',
 N'MẪU THAM KHẢO - CHƯA KÝ. Nước xuất/nhập: ______. '
 + N'Định danh ngựa: ______. Điều kiện kiểm dịch: ______. '
 + N'Số giấy phép và thời hạn: ______. Cơ quan cấp xác nhận: ______.',
 'purple','customs'),
('GLANDERS_CFT',N'Phiếu xét nghiệm bệnh tỵ Glanders',
 'GLANDERS-CFT-NEGATIVE (REFERENCE)',
 N'Phòng xét nghiệm được cơ quan thú y chấp thuận',
 N'Complement Fixation Test (CFT)',
 N'MẪU THAM KHẢO - CHƯA KÝ. Mẫu: ______. Ngày: ______. '
 + N'Kết quả CFT Glanders (âm tính/dương tính/chưa xác định): ______. '
 + N'Phòng xét nghiệm và người có thẩm quyền: ______.',
 'geekblue','blood'),
('EVA_TEST',N'Phiếu xét nghiệm EVA cho ngựa đực giống',
 'EVA-STALLION-TEST (REFERENCE)',
 N'Phòng xét nghiệm được cơ quan thú y chấp thuận',
 N'Equine Viral Arteritis - phương pháp xét nghiệm được xác minh',
 N'MẪU THAM KHẢO - CHƯA KÝ. Định danh ngựa đực giống: ______. '
 + N'Loại mẫu/phương pháp: ______. Kết quả EVA: ______. '
 + N'Ngày và người có thẩm quyền xác nhận: ______.',
 'gold','blood');
INSERT dbo.DocumentTypeCatalog(TypeCode,DisplayName,IsActive)
SELECT s.TypeCode,s.DisplayName,1 FROM @seed s WHERE NOT EXISTS(
    SELECT 1 FROM dbo.DocumentTypeCatalog d WHERE d.TypeCode=s.TypeCode);
-- A temp table makes newly-added columns safe in a single SQL batch.
SELECT * INTO #DocumentTemplateSeed FROM @seed;
EXEC sys.sp_executesql N'UPDATE d SET
    FormCode=COALESCE(d.FormCode,s.FormCode),
    IssuingAuthority=COALESCE(d.IssuingAuthority,s.IssuingAuthority),
    SubAuthority=COALESCE(d.SubAuthority,s.SubAuthority),
    CertTextTemplate=COALESCE(d.CertTextTemplate,s.CertTextTemplate),
    BadgeColor=COALESCE(d.BadgeColor,s.BadgeColor),
    EmblemIcon=COALESCE(d.EmblemIcon,s.EmblemIcon)
    FROM dbo.DocumentTypeCatalog d JOIN #DocumentTemplateSeed s
        ON s.TypeCode=d.TypeCode';
DROP TABLE #DocumentTemplateSeed;
IF NOT EXISTS(SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='025_document_form_templates')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES('025_document_form_templates',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
