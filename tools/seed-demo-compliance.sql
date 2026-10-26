-- BE-02 default workflow templates. They are not country-specific legal advice.
-- RuleCode represents the rule family; ReviewedAt/effective dates mean active.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'EquiTransit_Demo'
    THROW 51020, 'Seed is restricted to EquiTransit_Demo.', 1;
DECLARE @now DATETIME2(0)=CONVERT(DATETIME2(0),
    CONVERT(CHAR(19),SYSUTCDATETIME(),126));
DECLARE @routes TABLE (Origin VARCHAR(2),Destination VARCHAR(2),
    PRIMARY KEY(Origin,Destination));
INSERT @routes
SELECT DISTINCT OriginCountryCode,DestinationCountryCode
FROM dbo.TransportOrder
WHERE OriginCountryCode IS NOT NULL AND DestinationCountryCode IS NOT NULL;
IF NOT EXISTS(SELECT 1 FROM @routes WHERE Origin='VN' AND Destination='SG')
    INSERT @routes VALUES ('VN','SG');
DECLARE @templates TABLE (Phase VARCHAR(50),Code VARCHAR(50),
    DisplayName NVARCHAR(200),SequenceNo INT);
INSERT @templates VALUES
('PRE_EXPORT','EXPORT_HEALTH_CERTIFICATE',
 N'Export Veterinary Health Certificate',1),
('PRE_EXPORT','EQUINE_PASSPORT_VACCINATION',
 N'Equine Passport / Vaccination Record',2),
('ARRIVAL','IMPORT_CUSTOMS_DECLARATION',
 N'Import Permit & Customs Entry Declaration',3),
('QUARANTINE_RELEASE','QUARANTINE_RELEASE_CERTIFICATE',
 N'Post-Arrival Quarantine Release Certificate',3);
BEGIN TRANSACTION;
DECLARE @lock INT;
EXEC @lock=sys.sp_getapplock
    @Resource='EquiTransit:legal-rule-catalog',@LockMode='Exclusive',
    @LockOwner='Transaction',@LockTimeout=10000;
IF @lock<0 THROW 51020, 'Compliance catalog is busy.', 1;
INSERT dbo.DocumentTypeCatalog(TypeCode,DisplayName,IsActive)
SELECT Code,DisplayName,1 FROM @templates t
WHERE NOT EXISTS(SELECT 1 FROM dbo.DocumentTypeCatalog c
    WHERE c.TypeCode=t.Code);
INSERT dbo.ComplianceRuleVersion(RuleCode,VersionNo,OriginCountryCode,
    DestinationCountryCode,DocumentType,PhaseCode,SourceURL,ReviewedAt,
    EffectiveFrom,EffectiveTo,TransitCountryCode,AuthorityRequired,
    RuleDescription)
SELECT k.RuleCode,COALESCE((SELECT MAX(v.VersionNo) FROM
    dbo.ComplianceRuleVersion v WHERE v.RuleCode=k.RuleCode),0)+1,
    r.Origin,r.Destination,t.Code,t.Phase,
    'https://equitransit.example/compliance/defaults',@now,
    @now,NULL,NULL,1,
    t.DisplayName+N'. Default workflow checklist; assigned Specialist verifies '
    +N'the applicable regulations.'
FROM @routes r CROSS JOIN @templates t
CROSS APPLY(SELECT CONCAT('ET-DEF-',r.Origin,'-',r.Destination,'-',
    t.Phase,'-BASE-',t.SequenceNo) AS RuleCode) k
WHERE NOT EXISTS(SELECT 1 FROM dbo.ComplianceRuleVersion v
    WHERE v.RuleCode=k.RuleCode AND v.ReviewedAt<=@now
    AND v.EffectiveFrom<=@now
    AND (v.EffectiveTo IS NULL OR v.EffectiveTo>@now));
COMMIT TRANSACTION;
SELECT r.OriginCountryCode,r.DestinationCountryCode,r.PhaseCode,
    COUNT(*) AS ActiveDefaultRules
FROM dbo.ComplianceRuleVersion r
WHERE r.RuleCode LIKE 'ET-DEF-%' AND r.ReviewedAt<=@now
AND r.EffectiveFrom<=@now
AND (r.EffectiveTo IS NULL OR r.EffectiveTo>@now)
GROUP BY r.OriginCountryCode,r.DestinationCountryCode,r.PhaseCode;
