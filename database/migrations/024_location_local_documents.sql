SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51024, 'Migration024 is restricted to Demo/Test.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.LocationCatalog','U') IS NULL
    CREATE TABLE dbo.LocationCatalog (
        LocationID int IDENTITY PRIMARY KEY,
        CountryCode varchar(2) NOT NULL,
        CountryName nvarchar(100) NOT NULL,
        LocationType varchar(20) NOT NULL,
        LocationName nvarchar(150) NOT NULL,
        LocationCode varchar(10) NULL,
        IsActive bit NOT NULL CONSTRAINT DF_Location_Active DEFAULT 1,
        CONSTRAINT CK_Location_Type CHECK(LocationType IN ('CITY','AIRPORT')),
        CONSTRAINT UQ_Location UNIQUE(CountryCode,LocationType,LocationName)
    );
IF OBJECT_ID('dbo.LocalFileObject','U') IS NULL
    CREATE TABLE dbo.LocalFileObject (
        FileKey uniqueidentifier PRIMARY KEY,
        UserID bigint NOT NULL REFERENCES dbo.[User](UserID),
        FileName nvarchar(255) NOT NULL,
        ContentType varchar(100) NOT NULL,
        FileSizeBytes bigint NOT NULL,
        FileHash varchar(64) NOT NULL,
        CreatedAt datetime2(0) NOT NULL
            CONSTRAINT DF_LocalFile_Created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_LocalFile_Size CHECK(FileSizeBytes BETWEEN 1 AND 10485760),
        CONSTRAINT CK_LocalFile_Type CHECK(ContentType IN
            ('application/pdf','image/png','image/jpeg'))
    );
IF OBJECT_ID('dbo.ComplianceDocumentPolicy','U') IS NULL
    CREATE TABLE dbo.ComplianceDocumentPolicy (
        PolicyCode varchar(50) PRIMARY KEY,
        DocumentType varchar(50) NOT NULL,
        DisplayName nvarchar(150) NOT NULL,
        DestinationCountryCode varchar(2) NULL,
        SexCode varchar(20) NULL,
        ValidityDays int NULL,
        IsActive bit NOT NULL CONSTRAINT DF_DocPolicy_Active DEFAULT 1,
        CONSTRAINT CK_DocPolicy_Days CHECK(ValidityDays>0)
    );
INSERT dbo.LocationCatalog(CountryCode,CountryName,LocationType,
    LocationName,LocationCode)
SELECT s.* FROM (VALUES
('VN',N'Việt Nam','CITY',N'TP. Hồ Chí Minh',NULL),
('VN',N'Việt Nam','CITY',N'Hà Nội',NULL),
('VN',N'Việt Nam','CITY',N'Lạng Sơn',NULL),
('CN',N'Trung Quốc','CITY',N'Nam Ninh (Nanning)',NULL),
('CN',N'Trung Quốc','CITY',N'Bằng Tường (Pingxiang)',NULL),
('TH',N'Thái Lan','CITY',N'Bangkok',NULL),
('KH',N'Campuchia','CITY',N'Phnom Penh',NULL),
('LA',N'Lào','CITY',N'Vientiane',NULL),
('SG',N'Singapore','CITY',N'Singapore',NULL),
('MY',N'Malaysia','CITY',N'Kuala Lumpur',NULL),
('VN',N'Việt Nam','AIRPORT',N'Sân bay Tân Sơn Nhất','SGN'),
('VN',N'Việt Nam','AIRPORT',N'Sân bay Nội Bài','HAN'),
('CN',N'Trung Quốc','AIRPORT',N'Guangzhou Baiyun','CAN'),
('TH',N'Thái Lan','AIRPORT',N'Suvarnabhumi','BKK'),
('SG',N'Singapore','AIRPORT',N'Changi','SIN'),
('MY',N'Malaysia','AIRPORT',N'Kuala Lumpur International','KUL'),
('HK',N'Hồng Kông','AIRPORT',N'Hong Kong International','HKG'),
('JP',N'Nhật Bản','AIRPORT',N'Narita','NRT'),
('KR',N'Hàn Quốc','AIRPORT',N'Incheon','ICN'),
('AE',N'Các Tiểu vương quốc Ả Rập Thống nhất','AIRPORT',N'Dubai','DXB'),
('AU',N'Australia','AIRPORT',N'Sydney','SYD'),
('GB',N'Vương quốc Anh','AIRPORT',N'London Heathrow','LHR'),
('FR',N'Pháp','AIRPORT',N'Paris Charles de Gaulle','CDG'),
('US',N'Hoa Kỳ','AIRPORT',N'Los Angeles International','LAX'),
('DE',N'Đức','AIRPORT',N'Frankfurt','FRA')
) s(CountryCode,CountryName,LocationType,LocationName,LocationCode)
WHERE NOT EXISTS(SELECT 1 FROM dbo.LocationCatalog d WHERE
    d.CountryCode=s.CountryCode AND d.LocationType=s.LocationType
    AND d.LocationName=s.LocationName);
-- Workflow samples supplied by the project, NOT verified legal regulations.
INSERT dbo.ComplianceDocumentPolicy(PolicyCode,DocumentType,DisplayName,
    DestinationCountryCode,SexCode,ValidityDays)
SELECT s.* FROM (VALUES
('BASE_PASSPORT','FEI_PASSPORT',N'Equine Passport',NULL,NULL,NULL),
('BASE_EIA','COGGINS_EIA',N'EIA Test',NULL,NULL,30),
('BASE_INFLUENZA','EQUINE_INFLUENZA',N'Equine Influenza',NULL,NULL,180),
('BASE_EXPORT','EXPORT_PERMIT',N'Export Veterinary Permit',NULL,NULL,NULL),
('GB_CFT','GLANDERS_CFT',N'Glanders CFT','GB',NULL,NULL),
('FR_CFT','GLANDERS_CFT',N'Glanders CFT','FR',NULL,NULL),
('DE_CFT','GLANDERS_CFT',N'Glanders CFT','DE',NULL,NULL),
('STALLION_EVA','EVA_TEST',N'EVA Test',NULL,'STALLION',NULL)
) s(PolicyCode,DocumentType,DisplayName,DestinationCountryCode,SexCode,
    ValidityDays)
WHERE NOT EXISTS(SELECT 1 FROM dbo.ComplianceDocumentPolicy d
    WHERE d.PolicyCode=s.PolicyCode);
IF NOT EXISTS(SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='024_location_local_documents')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES('024_location_local_documents',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
