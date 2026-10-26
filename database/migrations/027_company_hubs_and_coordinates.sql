SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51027, 'Company hubs are restricted to Demo/Test.', 1;
IF OBJECT_ID('dbo.LocationCatalog','U') IS NULL
    THROW 51027, 'Apply migration024 before company hubs.', 1;
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource='EquiTransit:location-catalog',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @lock<0 THROW 51027, 'Location catalog is busy.', 1;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LocationCatalog')
    AND name='CK_Location_Type')
    ALTER TABLE dbo.LocationCatalog DROP CONSTRAINT CK_Location_Type;
ALTER TABLE dbo.LocationCatalog WITH CHECK ADD CONSTRAINT CK_Location_Type
    CHECK(LocationType IN ('CITY','AIRPORT','HUB'));
IF COL_LENGTH('dbo.LocationCatalog','Address') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Address nvarchar(500) NULL;
IF COL_LENGTH('dbo.LocationCatalog','Latitude') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Latitude decimal(10,7) NULL;
IF COL_LENGTH('dbo.LocationCatalog','Longitude') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Longitude decimal(10,7) NULL;
IF COL_LENGTH('dbo.LocationCatalog','IsPrimaryHub') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD IsPrimaryHub bit NOT NULL
        CONSTRAINT DF_Location_PrimaryHub DEFAULT 0;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LocationCatalog')
    AND name='CK_Location_Coordinates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.LocationCatalog WITH CHECK
        ADD CONSTRAINT CK_Location_Coordinates CHECK
        ((Latitude IS NULL AND Longitude IS NULL) OR
        (Latitude IS NOT NULL AND Longitude IS NOT NULL
            AND Latitude BETWEEN -90 AND 90
            AND Longitude BETWEEN -180 AND 180))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LocationCatalog')
    AND name='CK_Location_PrimaryHub')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.LocationCatalog WITH CHECK
        ADD CONSTRAINT CK_Location_PrimaryHub CHECK
        (IsPrimaryHub=0 OR (LocationType=''HUB'' AND IsActive=1))';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.LocationCatalog')
    AND name='UX_Location_PrimaryHub_Country')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UX_Location_PrimaryHub_Country
        ON dbo.LocationCatalog(CountryCode)
        WHERE LocationType=''HUB'' AND IsActive=1 AND IsPrimaryHub=1';
CREATE TABLE #CompanyHubSeed (
    CountryCode varchar(2), CountryName nvarchar(100),
    LocationName nvarchar(150), Address nvarchar(500),
    Latitude decimal(10,7), Longitude decimal(10,7)
);
-- VN/CN/TH coordinates and addresses supplied by the project owner.
-- KH/LA are configurable placeholders: no actual depot coordinates supplied.
INSERT #CompanyHubSeed VALUES
('VN',N'Việt Nam',N'Trụ sở EquiTransit Việt Nam (Củ Chi, TP.HCM)',
 N'Đường Bến Súc, An Phú, Củ Chi, TP. Hồ Chí Minh',11.0067,106.5132),
('CN',N'Trung Quốc',
 N'Trạm trung chuyển EquiTransit Bằng Tường (Pingxiang Hub)',
 N'Khu thương mại cửa khẩu Hữu Nghị Quan, Bằng Tường, Quảng Tây',
 22.0988,106.7581),
('TH',N'Thái Lan',N'Trụ sở EquiTransit Thái Lan (Bangkok Hub)',
 NULL,13.7563,100.5018),
('KH',N'Campuchia',N'Trụ sở EquiTransit Campuchia (Phnom Penh Hub)',
 NULL,NULL,NULL),
('LA',N'Lào',N'Trụ sở EquiTransit Lào (Vientiane Hub)',NULL,NULL,NULL);
EXEC sys.sp_executesql N'INSERT dbo.LocationCatalog(CountryCode,CountryName,
    LocationType,LocationName,Address,Latitude,Longitude,IsPrimaryHub,IsActive)
    SELECT s.CountryCode,COALESCE((SELECT TOP(1) c.CountryName
        FROM dbo.LocationCatalog c WHERE c.CountryCode=s.CountryCode
        ORDER BY c.IsActive DESC,c.LocationID),s.CountryName),
        ''HUB'',s.LocationName,s.Address,s.Latitude,s.Longitude,
        CASE WHEN EXISTS(SELECT 1 FROM dbo.LocationCatalog p WHERE
            p.CountryCode=s.CountryCode AND p.IsPrimaryHub=1
            AND p.IsActive=1) THEN 0 ELSE 1 END,1
    FROM #CompanyHubSeed s WHERE NOT EXISTS(SELECT 1
        FROM dbo.LocationCatalog d WHERE d.CountryCode=s.CountryCode
        AND d.LocationType=''HUB'' AND d.LocationName=s.LocationName)';
DROP TABLE #CompanyHubSeed;
IF NOT EXISTS(SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='027_company_hubs_and_coordinates')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES('027_company_hubs_and_coordinates',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
