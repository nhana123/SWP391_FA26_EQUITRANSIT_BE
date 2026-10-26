SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51027, 'Migration027 is restricted to Demo/Test.', 1;
IF OBJECT_ID('dbo.LocationCatalog','U') IS NULL
    THROW 51027, 'Apply migration024 before company hubs and distance.', 1;
BEGIN TRANSACTION;

-- 1. Expand LocationType constraint to allow HUB
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Location_Type' AND parent_object_id = OBJECT_ID('dbo.LocationCatalog'))
    ALTER TABLE dbo.LocationCatalog DROP CONSTRAINT CK_Location_Type;

ALTER TABLE dbo.LocationCatalog
    ADD CONSTRAINT CK_Location_Type CHECK (LocationType IN ('CITY', 'AIRPORT', 'HUB'));

-- 2. Add Hub & Coordinate columns to LocationCatalog if missing
IF COL_LENGTH('dbo.LocationCatalog', 'Address') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Address nvarchar(500) NULL;

IF COL_LENGTH('dbo.LocationCatalog', 'Latitude') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Latitude decimal(10,7) NULL;

IF COL_LENGTH('dbo.LocationCatalog', 'Longitude') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD Longitude decimal(10,7) NULL;

IF COL_LENGTH('dbo.LocationCatalog', 'IsPrimaryHub') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD IsPrimaryHub bit NOT NULL CONSTRAINT DF_Location_PrimaryHub DEFAULT 0;

IF COL_LENGTH('dbo.LocationCatalog', 'DomesticRatePerKm') IS NULL
    ALTER TABLE dbo.LocationCatalog ADD DomesticRatePerKm decimal(10,2) NOT NULL CONSTRAINT DF_Location_DomesticRate DEFAULT 1.50;

-- 3. Populate Coordinates for existing CITY and AIRPORT entries and seed Primary Company Hubs (HUB)
EXEC sys.sp_executesql N'
UPDATE l
SET l.Latitude = c.Latitude,
    l.Longitude = c.Longitude,
    l.Address = COALESCE(l.Address, c.Address)
FROM dbo.LocationCatalog l
JOIN (VALUES
    (''VN'',''CITY'',N''TP. Hồ Chí Minh'',10.7769000,106.7009000,N''Trung tâm TP. Hồ Chí Minh, Việt Nam''),
    (''VN'',''CITY'',N''Hà Nội'',21.0285000,105.8542000,N''Trung tâm Hà Nội, Việt Nam''),
    (''VN'',''CITY'',N''Lạng Sơn'',21.8537000,106.7615000,N''TP. Lạng Sơn, Tỉnh Lạng Sơn, Việt Nam''),
    (''CN'',''CITY'',N''Nam Ninh (Nanning)'',22.8170000,108.3665000,N''Qingxiu, Nanning, Guangxi, China''),
    (''CN'',''CITY'',N''Bằng Tường (Pingxiang)'',22.0967000,106.7550000,N''Pingxiang, Chongzuo, Guangxi, China''),
    (''TH'',''CITY'',N''Bangkok'',13.7563000,100.5018000,N''Pathum Wan, Bangkok, Thailand''),
    (''KH'',''CITY'',N''Phnom Penh'',11.5564000,104.9282000,N''Daun Penh, Phnom Penh, Cambodia''),
    (''LA'',''CITY'',N''Vientiane'',17.9757000,102.6331000,N''Chanthabouly, Vientiane, Laos''),
    (''SG'',''CITY'',N''Singapore'',1.3521000,103.8198000,N''Kranji / Central Singapore''),
    (''MY'',''CITY'',N''Kuala Lumpur'',3.1390000,101.6869000,N''Selangor / Kuala Lumpur, Malaysia''),
    (''VN'',''AIRPORT'',N''Sân bay Tân Sơn Nhất'',10.8188000,106.6519000,N''Trường Sơn, Tân Bình, TP. Hồ Chí Minh''),
    (''VN'',''AIRPORT'',N''Sân bay Nội Bài'',21.2212000,105.8072000,N''Phú Minh, Sóc Sơn, Hà Nội''),
    (''CN'',''AIRPORT'',N''Guangzhou Baiyun'',23.3924000,113.2988000,N''Baiyun District, Guangzhou, Guangdong, China''),
    (''TH'',''AIRPORT'',N''Suvarnabhumi'',13.6900000,100.7501000,N''Bang Phli, Samut Prakan, Bangkok, Thailand''),
    (''SG'',''AIRPORT'',N''Changi'',1.3644000,103.9915000,N''Airport Blvd, Changi, Singapore''),
    (''MY'',''AIRPORT'',N''Kuala Lumpur International'',2.7456000,101.7099000,N''Sepang, Selangor, Malaysia''),
    (''HK'',''AIRPORT'',N''Hong Kong International'',22.3080000,113.9185000,N''Chek Lap Kok, Lantau, Hong Kong''),
    (''JP'',''AIRPORT'',N''Narita'',35.7720000,140.3929000,N''Narita, Chiba, Japan''),
    (''KR'',''AIRPORT'',N''Incheon'',37.4602000,126.4407000,N''Jung-gu, Incheon, South Korea''),
    (''AE'',''AIRPORT'',N''Dubai'',25.2532000,55.3657000,N''Al Garhoud, Dubai, UAE''),
    (''AU'',''AIRPORT'',N''Sydney'',-33.9399000,151.1753000,N''Mascot NSW, Sydney, Australia''),
    (''GB'',''AIRPORT'',N''London Heathrow'',51.4700000,-0.4543000,N''Hounslow, London, United Kingdom''),
    (''FR'',''AIRPORT'',N''Paris Charles de Gaulle'',49.0097000,2.5479000,N''Roissy-en-France, Paris, France''),
    (''US'',''AIRPORT'',N''Los Angeles International'',33.9416000,-118.4085000,N''1 World Way, Los Angeles, CA, USA''),
    (''DE'',''AIRPORT'',N''Frankfurt'',50.0379000,8.5622000,N''60547 Frankfurt am Main, Germany'')
) c(CountryCode, LocationType, LocationName, Latitude, Longitude, Address)
ON l.CountryCode = c.CountryCode AND l.LocationType = c.LocationType AND l.LocationName = c.LocationName;

INSERT dbo.LocationCatalog(CountryCode, CountryName, LocationType, LocationName, LocationCode, Address, Latitude, Longitude, IsPrimaryHub, DomesticRatePerKm)
SELECT s.* FROM (VALUES
    (''VN'',N''Việt Nam'',''HUB'',N''Trụ sở EquiTransit Việt Nam (Củ Chi Hub)'',''HUB-VN'',N''Khu Nông nghiệp Công nghệ cao Củ Chi, Quốc lộ 22, TP. Hồ Chí Minh'',11.0067000,106.5132000,CAST(1 AS bit),1.50),
    (''CN'',N''Trung Quốc'',''HUB'',N''Trạm trung chuyển EquiTransit Bằng Tường (Pingxiang Hub)'',''HUB-CN'',N''Khu kiểm dịch cửa khẩu Hữu Nghị Quan, Bằng Tường, Quảng Tây'',22.0988000,106.7581000,CAST(1 AS bit),1.80),
    (''LA'',N''Lào'',''HUB'',N''Trụ sở EquiTransit Lào (Vientiane Hub)'',''HUB-LA'',N''Thanaleng Dry Port & Equine Quarantine Park, Vientiane'',17.8825000,102.7100000,CAST(1 AS bit),1.50),
    (''KH'',N''Campuchia'',''HUB'',N''Trụ sở EquiTransit Campuchia (Phnom Penh Hub)'',''HUB-KH'',N''Phnom Penh Special Economic Zone Equine Depot, National Road 4'',11.5564000,104.9282000,CAST(1 AS bit),1.50),
    (''TH'',N''Thái Lan'',''HUB'',N''Trụ sở EquiTransit Thái Lan (Bangkok Hub)'',''HUB-TH'',N''Pathum Thani Equine Quarantine & Transit Facility, Bangkok'',14.0135000,100.5305000,CAST(1 AS bit),1.60),
    (''SG'',N''Singapore'',''HUB'',N''Trụ sở EquiTransit Singapore (Kranji Hub)'',''HUB-SG'',N''1 Turf Club Avenue, Kranji Equine Center, Singapore'',1.4225000,103.7605000,CAST(1 AS bit),2.50),
    (''MY'',N''Malaysia'',''HUB'',N''Trụ sở EquiTransit Malaysia (Selangor Hub)'',''HUB-MY'',N''Selangor Turf Club Equine Hub, Seri Kembangan, Kuala Lumpur'',3.0419000,101.7089000,CAST(1 AS bit),1.70),
    (''HK'',N''Hồng Kông'',''HUB'',N''Trụ sở EquiTransit Hồng Kông (Sha Tin Hub)'',''HUB-HK'',N''Sha Tin Racecourse Equine Isolation Facility, Hong Kong'',22.4005000,114.2041000,CAST(1 AS bit),3.00),
    (''JP'',N''Nhật Bản'',''HUB'',N''Trụ sở EquiTransit Nhật Bản (Chiba Narita Hub)'',''HUB-JP'',N''Animal Quarantine Service Hub, Sanrizuka, Narita, Chiba'',35.7583000,140.3736000,CAST(1 AS bit),2.80),
    (''KR'',N''Hàn Quốc'',''HUB'',N''Trụ sở EquiTransit Hàn Quốc (LetsRun Park Seoul Hub)'',''HUB-KR'',N''107 경마공원대로, Gwacheon-si, Gyeonggi-do, South Korea'',37.4469000,127.0147000,CAST(1 AS bit),2.50),
    (''AE'',N''Các Tiểu vương quốc Ả Rập Thống nhất'',''HUB'',N''Trụ sở EquiTransit Dubai (Meydan Hub)'',''HUB-AE'',N''Meydan Stables & Quarantine Center, Nad Al Sheba, Dubai'',25.1558000,55.3003000,CAST(1 AS bit),2.50),
    (''AU'',N''Australia'',''HUB'',N''Trụ sở EquiTransit Australia (Mickleham / Sydney Hub)'',''HUB-AU'',N''Canterbury Park Equine Transit Center, NSW, Sydney'',-33.9096000,151.1146000,CAST(1 AS bit),2.40),
    (''GB'',N''Vương quốc Anh'',''HUB'',N''Trụ sở EquiTransit Anh Quốc (Newmarket Hub)'',''HUB-GB'',N''British Racing School & Equine Hub, Newmarket, Suffolk'',52.2474000,0.4063000,CAST(1 AS bit),2.60),
    (''FR'',N''Pháp'',''HUB'',N''Trụ sở EquiTransit Pháp (Chantilly Roissy Hub)'',''HUB-FR'',N''Domaine de Chantilly Equine Logistics Hub, Oise, Paris'',49.1942000,2.4716000,CAST(1 AS bit),2.60),
    (''US'',N''Hoa Kỳ'',''HUB'',N''Trụ sở EquiTransit Hoa Kỳ (Santa Anita LA Hub)'',''HUB-US'',N''285 W Huntington Dr, Arcadia, Los Angeles, CA'',34.1400000,-118.0435000,CAST(1 AS bit),2.50),
    (''DE'',N''Đức'',''HUB'',N''Trụ sở EquiTransit Đức (Frankfurt Equine Lounge)'',''HUB-DE'',N''CargoCity Süd Animal Lounge, Frankfurt Airport'',50.0226000,8.5503000,CAST(1 AS bit),2.60)
) s(CountryCode, CountryName, LocationType, LocationName, LocationCode, Address, Latitude, Longitude, IsPrimaryHub, DomesticRatePerKm)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.LocationCatalog d
    WHERE d.CountryCode = s.CountryCode AND d.LocationType = s.LocationType AND d.LocationName = s.LocationName
);
';

IF NOT EXISTS(SELECT 1 FROM dbo.SchemaMigrationHistory WHERE MigrationID='027_company_hubs_and_distance')
    INSERT dbo.SchemaMigrationHistory(MigrationID, AppliedAt, AppliedBy)
    VALUES('027_company_hubs_and_distance', SYSUTCDATETIME(), SUSER_SNAME());

COMMIT;
