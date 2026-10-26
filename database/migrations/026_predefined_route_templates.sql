SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51026, 'Route templates are restricted to Demo/Test.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.RouteTemplate','U') IS NULL
    CREATE TABLE dbo.RouteTemplate (
        RouteTemplateID bigint IDENTITY PRIMARY KEY,
        RouteCode nvarchar(50) NOT NULL UNIQUE,
        RouteName nvarchar(200) NOT NULL,
        OriginCountryCode varchar(2) NOT NULL,
        OriginCity nvarchar(100) NULL,
        OriginAirportCode varchar(3) NULL,
        DestinationCountryCode varchar(2) NOT NULL,
        DestinationCity nvarchar(100) NULL,
        DestinationAirportCode varchar(3) NULL,
        TransportMode varchar(20) NOT NULL,
        EstimatedRoadKm decimal(10,2) NOT NULL DEFAULT 0,
        EstimatedAirKm decimal(10,2) NOT NULL DEFAULT 0,
        EstimatedHours decimal(6,1) NOT NULL,
        RouteSummary nvarchar(1000) NOT NULL,
        BaseRoadRate decimal(12,2) NOT NULL DEFAULT 1.00,
        BaseAirRate decimal(12,2) NOT NULL DEFAULT 0.00,
        IsActive bit NOT NULL DEFAULT 1,
        CreatedBy bigint NULL REFERENCES dbo.StaffProfile(StaffID),
        CreatedAt datetimeoffset NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        UpdatedAt datetimeoffset NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT CK_RouteTemplate_Mode CHECK
            (TransportMode IN ('ROAD','AIR','MULTIMODAL')),
        CONSTRAINT CK_RouteTemplate_Values CHECK
            (EstimatedRoadKm>=0 AND EstimatedAirKm>=0 AND EstimatedHours>0
            AND BaseRoadRate>=0 AND BaseAirRate>=0),
        CONSTRAINT CK_RouteTemplate_Distances CHECK
            ((TransportMode='ROAD' AND EstimatedRoadKm>0 AND EstimatedAirKm=0)
            OR (TransportMode='AIR' AND EstimatedAirKm>0 AND EstimatedRoadKm=0)
            OR (TransportMode='MULTIMODAL' AND EstimatedAirKm>0
                AND EstimatedRoadKm>0)),
        CONSTRAINT CK_RouteTemplate_Countries CHECK
            (OriginCountryCode<>DestinationCountryCode)
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RouteTemplate') AND name='IX_RouteTemplate_Match')
    CREATE INDEX IX_RouteTemplate_Match ON dbo.RouteTemplate
        (OriginCountryCode,DestinationCountryCode,IsActive,TransportMode);
-- Project-provided indicative routes/rates, not verified carrier quotations.
INSERT dbo.RouteTemplate(RouteCode,RouteName,OriginCountryCode,
    DestinationCountryCode,OriginAirportCode,DestinationAirportCode,
    TransportMode,EstimatedRoadKm,EstimatedAirKm,EstimatedHours,
    RouteSummary,BaseRoadRate,BaseAirRate)
SELECT s.* FROM (VALUES
(N'ROUTE-VN-CN-ROAD',N'Việt Nam - Nam Ninh (đường bộ)','VN','CN',NULL,NULL,
 'ROAD',1950.00,0.00,35.5,
 N'TP.HCM/Miền Nam → Quốc lộ 1A → Hữu Nghị Quan → Nam Ninh',1.00,0.00),
(N'ROUTE-VN-LA-ROAD',N'Việt Nam - Vientiane (đường bộ)','VN','LA',NULL,NULL,
 'ROAD',1100.00,0.00,20.0,
 N'TP.HCM/Miền Nam → Cửa khẩu Bờ Y/Lao Bảo → Vientiane',1.00,0.00),
(N'ROUTE-VN-FR-AIR',N'Hà Nội - Paris (hàng không)','VN','FR','HAN','CDG',
 'AIR',0.00,9200.00,14.5,
 N'Sân bay Nội Bài (HAN) → Charles de Gaulle (CDG) bay chuyên cơ IATA LAR',
 1.00,1200.00)
) s(RouteCode,RouteName,OriginCountryCode,DestinationCountryCode,
    OriginAirportCode,DestinationAirportCode,TransportMode,EstimatedRoadKm,
    EstimatedAirKm,EstimatedHours,RouteSummary,BaseRoadRate,BaseAirRate)
WHERE NOT EXISTS(SELECT 1 FROM dbo.RouteTemplate r WHERE r.RouteCode=s.RouteCode);
IF NOT EXISTS(SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='026_predefined_route_templates')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES('026_predefined_route_templates',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
