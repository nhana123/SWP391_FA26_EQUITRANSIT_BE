-- F08: quarantine assessments, reviewed policies and revision route sources.
-- After 001-005. No business data is seeded; BE1 schema remains unchanged.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF @@TRANCOUNT <> 0
    THROW 51008, 'Run migration 006 without an existing transaction.', 1;
IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
    THROW 51008, 'Select the intended EquiTransit database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),N'DATABASE',N'VIEW DEFINITION'),0)<>1
    THROW 51008, 'VIEW DEFINITION is required to verify existing schema.', 1;
IF (SELECT compatibility_level FROM sys.databases WHERE name=DB_NAME())<130
    THROW 51008, 'Compatibility level 130 or newer is required.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @lock<0 THROW 51008, 'Another migration holds the lock.', 1;
IF OBJECT_ID(N'dbo.RevisionQuarantineAssessment',N'U') IS NULL
    OR OBJECT_ID(N'dbo.ComplianceRuleVersion',N'U') IS NULL
    OR COL_LENGTH(N'dbo.OrderRevision',N'OrderSnapshot') IS NULL
    THROW 51008, 'Apply baseline and migrations 001-005 first.', 1;

-- Preserve rows when upgrading the earlier BE2 object names.
IF OBJECT_ID(N'dbo.QuarantineAssessment',N'V') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.sql_expression_dependencies
        WHERE referencing_id=OBJECT_ID(N'dbo.QuarantineAssessment')
            AND referenced_id=OBJECT_ID(N'dbo.RevisionQuarantineAssessment'))
        OR EXISTS (SELECT 1 FROM sys.sql_expression_dependencies
            WHERE referencing_id=OBJECT_ID(N'dbo.QuarantineAssessment')
                AND (referenced_id IS NULL
                    OR referenced_id<>OBJECT_ID(N'dbo.RevisionQuarantineAssessment')))
        THROW 51008, 'Reconcile the unrecognized QuarantineAssessment view.', 1;
    EXEC(N'DROP VIEW dbo.QuarantineAssessment');
END;
DECLARE @Names TABLE (OldName sysname,NewName sysname,ObjectType char(1));
INSERT @Names VALUES
    (N'F08QuarantineAssessment',N'QuarantineAssessment','U'),
    (N'F08RuleRequirementPolicy',N'RuleRequirementPolicy','U'),
    (N'F08RevisionChecklistState',N'RevisionChecklistState','U'),
    (N'F08RevisionRouteSource',N'RevisionRouteSource','V');
DECLARE @OldName sysname,@NewName sysname,@ObjectType char(1),@Qualified nvarchar(517);
DECLARE RenameObjects CURSOR LOCAL FAST_FORWARD FOR
    SELECT OldName,NewName,ObjectType FROM @Names;
OPEN RenameObjects;
FETCH NEXT FROM RenameObjects INTO @OldName,@NewName,@ObjectType;
WHILE @@FETCH_STATUS=0
BEGIN
    IF OBJECT_ID(N'dbo.'+QUOTENAME(@OldName),@ObjectType) IS NOT NULL
    BEGIN
        IF OBJECT_ID(N'dbo.'+QUOTENAME(@NewName)) IS NOT NULL
            THROW 51008, 'Both legacy and new BE2 objects exist; reconcile before migration.', 1;
        SET @Qualified=N'dbo.'+QUOTENAME(@OldName);
        EXEC sys.sp_rename @objname=@Qualified,@newname=@NewName,@objtype=N'OBJECT';
    END;
    FETCH NEXT FROM RenameObjects INTO @OldName,@NewName,@ObjectType;
END;
CLOSE RenameObjects;
DEALLOCATE RenameObjects;

-- 1. QuarantineAssessment: S04 / C11.
IF OBJECT_ID(N'dbo.QuarantineAssessment',N'U') IS NULL
EXEC(N'CREATE TABLE dbo.QuarantineAssessment (
    AssessmentID bigint IDENTITY(1,1) PRIMARY KEY,
    RevisionID bigint NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
    Applicability varchar(20) NOT NULL,
    RequiredDays int NULL,
    RuleID bigint NULL REFERENCES dbo.ComplianceRuleVersion(RuleID),
    AssessmentNotes nvarchar(2000) NULL,
    SpecialistID bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
    AssessedAt datetime2(0) NOT NULL,
    ValidUntil datetime2(0) NULL,
    SourceReference nvarchar(1000) NULL,
    SupersededAt datetime2(0) NULL,
    CONSTRAINT CK_QuarantineAssessment_Outcome CHECK (
        (Applicability=''REQUIRED'' AND RequiredDays IS NOT NULL AND RequiredDays>0)
        OR (Applicability IN (''PENDING'',''NOT_REQUIRED'') AND RequiredDays IS NULL)),
    CONSTRAINT CK_QuarantineAssessment_Proof CHECK (
        (Applicability=''PENDING'' AND ValidUntil IS NULL)
        OR (Applicability IN (''REQUIRED'',''NOT_REQUIRED'')
            AND ValidUntil IS NOT NULL AND ValidUntil>AssessedAt
            AND SourceReference IS NOT NULL
            AND LEN(LTRIM(RTRIM(SourceReference)))>0)),
    CONSTRAINT CK_QuarantineAssessment_History CHECK (
        SupersededAt IS NULL OR SupersededAt>=AssessedAt)
)');

-- 2. RuleRequirementPolicy: S04.
IF OBJECT_ID(N'dbo.RuleRequirementPolicy',N'U') IS NULL
EXEC(N'CREATE TABLE dbo.RuleRequirementPolicy (
    RuleID bigint PRIMARY KEY REFERENCES dbo.ComplianceRuleVersion(RuleID),
    ScopeCode varchar(20) NOT NULL CHECK (ScopeCode IN (''HORSE'',''SHIPMENT'')),
    IsMandatory bit NOT NULL,
    ValidityBasis varchar(20) NOT NULL
        CHECK (ValidityBasis IN (''DEPARTURE'',''DELIVERY'')),
    Instructions nvarchar(2000) NULL,
    ReviewedBy bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
    ReviewedAt datetime2(0) NOT NULL
)');

-- 3. RevisionChecklistState: C11.
IF OBJECT_ID(N'dbo.RevisionChecklistState',N'U') IS NULL
EXEC(N'CREATE TABLE dbo.RevisionChecklistState (
    RevisionID bigint PRIMARY KEY REFERENCES dbo.OrderRevision(RevisionID),
    AssessedAt datetime2(0) NOT NULL,
    PendingRuleAssessment bit NOT NULL
)');

-- 4. RevisionRouteSource: C11.
EXEC(N'CREATE OR ALTER VIEW dbo.RevisionRouteSource AS
    SELECT r.RevisionID,r.OrderID,
      CONVERT(varchar(2),CASE WHEN DATALENGTH(j.OriginCode)=4
        AND j.OriginCode COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^A-Z]%''
        THEN j.OriginCode END) AS OriginCountryCode,
      CONVERT(varchar(2),CASE WHEN DATALENGTH(j.DestinationCode)=4
        AND j.DestinationCode COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^A-Z]%''
        THEN j.DestinationCode END) AS DestinationCountryCode,
      CONVERT(datetime2(0),SWITCHOFFSET(TRY_CONVERT(datetimeoffset(0),
        JSON_VALUE(p.Payload,''$.request.preferredDepartureAt'')),''+00:00'')) AS PreferredDepartureAt,
      CONVERT(datetime2(0),SWITCHOFFSET(TRY_CONVERT(datetimeoffset(0),
        JSON_VALUE(p.Payload,''$.request.deliveryDeadline'')),''+00:00'')) AS DeliveryDeadline,
      TRY_CONVERT(bigint,JSON_VALUE(p.Payload,''$.request.horseId'')) AS HorseID
    FROM dbo.OrderRevision r
    CROSS APPLY (VALUES(CASE WHEN ISJSON(r.OrderSnapshot)=1
        THEN r.OrderSnapshot ELSE N''{}'' END)) p(Payload)
    CROSS APPLY (VALUES(
        UPPER(JSON_VALUE(p.Payload,''$.request.originCountryCode'')),
        UPPER(JSON_VALUE(p.Payload,''$.request.destinationCountryCode'')))) j(OriginCode,DestinationCode)');

-- 5. Airport: S04.
IF OBJECT_ID(N'dbo.Airport',N'U') IS NULL
EXEC(N'CREATE TABLE dbo.Airport (
    AirportCode varchar(3) PRIMARY KEY,
    CountryCode varchar(2) NOT NULL,
    SourceReference nvarchar(1000) NOT NULL,
    ReviewedBy bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
    ReviewedAt datetime2(0) NOT NULL,
    CONSTRAINT CK_Airport_Code CHECK (DATALENGTH(AirportCode)=3
        AND AirportCode COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^A-Z]%''),
    CONSTRAINT CK_Airport_Country CHECK (DATALENGTH(CountryCode)=2
        AND CountryCode COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^A-Z]%''),
    CONSTRAINT CK_Airport_Source CHECK (LEN(LTRIM(RTRIM(SourceReference)))>0)
)');

DECLARE @Expected TABLE (
    TableName sysname,ColumnName sysname,TypeName sysname,
    MaxLength smallint NULL,ScaleValue tinyint NULL,Nullable bit,IsIdentity bit
);
INSERT @Expected VALUES
    (N'QuarantineAssessment',N'AssessmentID',N'bigint',NULL,NULL,0,1),
    (N'QuarantineAssessment',N'RevisionID',N'bigint',NULL,NULL,0,0),
    (N'QuarantineAssessment',N'Applicability',N'varchar',20,NULL,0,0),
    (N'QuarantineAssessment',N'RequiredDays',N'int',NULL,NULL,1,0),
    (N'QuarantineAssessment',N'RuleID',N'bigint',NULL,NULL,1,0),
    (N'QuarantineAssessment',N'AssessmentNotes',N'nvarchar',4000,NULL,1,0),
    (N'QuarantineAssessment',N'SpecialistID',N'bigint',NULL,NULL,0,0),
    (N'QuarantineAssessment',N'AssessedAt',N'datetime2',NULL,0,0,0),
    (N'QuarantineAssessment',N'ValidUntil',N'datetime2',NULL,0,1,0),
    (N'QuarantineAssessment',N'SourceReference',N'nvarchar',2000,NULL,1,0),
    (N'QuarantineAssessment',N'SupersededAt',N'datetime2',NULL,0,1,0),
    (N'RuleRequirementPolicy',N'RuleID',N'bigint',NULL,NULL,0,0),
    (N'RuleRequirementPolicy',N'ScopeCode',N'varchar',20,NULL,0,0),
    (N'RuleRequirementPolicy',N'IsMandatory',N'bit',NULL,NULL,0,0),
    (N'RuleRequirementPolicy',N'ValidityBasis',N'varchar',20,NULL,0,0),
    (N'RuleRequirementPolicy',N'Instructions',N'nvarchar',4000,NULL,1,0),
    (N'RuleRequirementPolicy',N'ReviewedBy',N'bigint',NULL,NULL,0,0),
    (N'RuleRequirementPolicy',N'ReviewedAt',N'datetime2',NULL,0,0,0),
    (N'RevisionChecklistState',N'RevisionID',N'bigint',NULL,NULL,0,0),
    (N'RevisionChecklistState',N'AssessedAt',N'datetime2',NULL,0,0,0),
    (N'RevisionChecklistState',N'PendingRuleAssessment',N'bit',NULL,NULL,0,0),
    (N'Airport',N'AirportCode',N'varchar',3,NULL,0,0),
    (N'Airport',N'CountryCode',N'varchar',2,NULL,0,0),
    (N'Airport',N'SourceReference',N'nvarchar',2000,NULL,0,0),
    (N'Airport',N'ReviewedBy',N'bigint',NULL,NULL,0,0),
    (N'Airport',N'ReviewedAt',N'datetime2',NULL,0,0,0);
IF EXISTS (SELECT 1 FROM @Expected e
    LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+QUOTENAME(e.TableName))
        AND c.name=e.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id)<>e.TypeName
        OR (e.MaxLength IS NOT NULL AND c.max_length<>e.MaxLength)
        OR (e.ScaleValue IS NOT NULL AND c.scale<>e.ScaleValue)
        OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IsIdentity OR c.is_computed<>0)
    THROW 51008, 'Existing BE2 compliance schema differs; reconcile before migration.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.QuarantineAssessment')
        AND name IN (N'UX_QuarantineAssessment_Current',N'UX_F08Quarantine_Current'))
    EXEC(N'CREATE UNIQUE INDEX UX_QuarantineAssessment_Current
        ON dbo.QuarantineAssessment(RevisionID) WHERE SupersededAt IS NULL');

-- 6. TransitAirport: C11.
IF OBJECT_ID(N'dbo.TransitAirport',N'U') IS NOT NULL
    THROW 51008, 'Reconcile the legacy transit table before migration.', 1;
EXEC(N'CREATE OR ALTER VIEW dbo.TransitAirport AS
    SELECT r.RevisionID,CONVERT(varchar(3),UPPER(a.[value])) AS AirportCode
    FROM dbo.OrderRevision r
    CROSS APPLY OPENJSON(CASE WHEN ISJSON(r.OrderSnapshot)=1
        THEN r.OrderSnapshot ELSE N''{}'' END,''$.request.transitAirports'') a
    WHERE a.[type]=1 AND LEN(a.[value])=3');

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
