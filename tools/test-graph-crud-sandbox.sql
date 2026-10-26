-- Synthetic graph CRUD and constraint probes. ALL rows are rolled back.
SET NOCOUNT ON;
SET XACT_ABORT OFF;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() NOT IN (N'EquiTransit_Test',N'EquiTransit_Demo',
    N'EquiTransit_FigmaMigrationTest_20261013_143729')
    THROW 51018, 'Graph fixtures require an explicitly named sandbox.', 1;
IF @@TRANCOUNT<>0 THROW 51018, 'Unexpected ambient transaction.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @suffix varchar(20)=LEFT(REPLACE(CONVERT(varchar(36),NEWID()),'-',''),20);
DECLARE @customerUser bigint,@managerUser bigint,@customer bigint,@staff bigint;
DECLARE @order bigint,@revision bigint,@plan bigint,@otherPlan bigint,@leg bigint;
DECLARE @legacyLeg bigint,@now datetime2(0)=SYSUTCDATETIME();
DECLARE @nodeCount int=(SELECT COUNT(*) FROM dbo.PlanCheckpointNode);

INSERT dbo.[User](RoleID,Email,PasswordHash,Status,CreatedAt)
SELECT RoleID,'graph-customer-'+@suffix+'@example.invalid',
    'SYNTHETIC-NOT-A-PASSWORD','ACTIVE',@now FROM dbo.Role WHERE RoleCode='CUSTOMER';
SET @customerUser=SCOPE_IDENTITY();
INSERT dbo.Customer(UserID,FullName,EmailNotificationsEnabled)
VALUES(@customerUser,N'Synthetic graph customer',0);
SET @customer=SCOPE_IDENTITY();
INSERT dbo.[User](RoleID,Email,PasswordHash,Status,CreatedAt)
SELECT RoleID,'graph-manager-'+@suffix+'@example.invalid',
    'SYNTHETIC-NOT-A-PASSWORD','ACTIVE',@now FROM dbo.Role WHERE RoleCode='MANAGER';
SET @managerUser=SCOPE_IDENTITY();
INSERT dbo.StaffProfile(UserID,FullName) VALUES(@managerUser,N'Synthetic graph manager');
SET @staff=SCOPE_IDENTITY();
INSERT dbo.TransportOrder(CustomerID,OrderNumber,Currency,OrderStatus,CreatedAt)
VALUES(@customer,'GRAPH-'+@suffix,'USD','DRAFT',@now);
SET @order=SCOPE_IDENTITY();
INSERT dbo.OrderRevision(OrderID,RevisionNo,OrderSnapshot,SubmittedAt)
VALUES(@order,1,N'{"schemaVersion":1}',@now);
SET @revision=SCOPE_IDENTITY();
INSERT dbo.TransportPlan(OrderID,RevisionID,CreatedBy,VersionNo,
    FinancialGateStatus,OperationalGateStatus,DocGateStatus,PlanStatus)
VALUES(@order,@revision,@staff,1,'PENDING','PENDING','PENDING','DRAFT');
SET @plan=SCOPE_IDENTITY();
INSERT dbo.TransportPlan(OrderID,RevisionID,CreatedBy,VersionNo,
    FinancialGateStatus,OperationalGateStatus,DocGateStatus,PlanStatus)
VALUES(@order,@revision,@staff,2,'PENDING','PENDING','PENDING','DRAFT');
SET @otherPlan=SCOPE_IDENTITY();
INSERT dbo.PlanCheckpointNode(PlanID,NodeKey,NodeType,SequenceOrder,
    LocationName,CountryCode,PlannedArrivalAt,PlannedDepartureAt,
    DwellMinutes,PosX,PosY,MetadataJson)
VALUES(@plan,'origin','ORIGIN',1,N'Synthetic stable','VN',NULL,@now,
    0,10.25,20.5,N'{"contactPerson":"Synthetic"}'),
    (@plan,'rest','REST_STOP',2,N'Synthetic welfare stop','VN',
        DATEADD(hour,1,@now),DATEADD(hour,2,@now),60,400,20,N'{}'),
    (@plan,'handover','AIRPORT_HANDOVER',3,N'Synthetic terminal','VN',
        NULL,NULL,180,800,20,N'{}'),
    (@plan,'transit','TRANSIT_HUB',4,N'Synthetic transit','SG',
        NULL,NULL,180,1200,20,N'{}'),
    (@plan,'quarantine','QUARANTINE',5,N'Synthetic station','US',
        NULL,NULL,1440,1600,20,N'{"biosecurityLevel":"STANDARD"}'),
    (@plan,'destination','DESTINATION',6,N'Synthetic destination','US',
        NULL,NULL,0,2000,20,N'{}'),
    (@otherPlan,'other-plan-node','DESTINATION',1,N'Other plan','VN',
        NULL,NULL,0,0,0,N'{}');
INSERT dbo.TripLeg(PlanID,SequenceNo,TransportMode,StartPoint,EndPoint,
    DistanceKm,PlannedDeparture,PlannedArrival,LegStatus,
    StartTimezone,EndTimezone,FromNodeKey,ToNodeKey)
VALUES(@plan,1,'ROAD',N'Synthetic stable',N'Synthetic welfare stop',10,
    @now,DATEADD(hour,1,@now),'PLANNED','Asia/Ho_Chi_Minh',
    'Asia/Ho_Chi_Minh','origin','rest');
SET @leg=SCOPE_IDENTITY();
-- Legacy inserts still work with both endpoint keys omitted.
INSERT dbo.TripLeg(PlanID,SequenceNo,TransportMode,StartPoint,EndPoint,
    DistanceKm,LegStatus)
VALUES(@plan,2,'ROAD',N'Legacy origin',N'Legacy destination',1,'PLANNED');
SET @legacyLeg=SCOPE_IDENTITY();
UPDATE dbo.TransportPlan SET GraphLayoutJson=N'{"zoom":1,"panX":5}'
WHERE PlanID=@plan;
UPDATE dbo.PlanCheckpointNode SET PosX=500.75,WelfareCareNotes=N'Synthetic water break'
WHERE PlanID=@plan AND NodeKey='rest';
IF (SELECT COUNT(*) FROM dbo.PlanCheckpointNode WHERE PlanID=@plan)<>6
    OR NOT EXISTS(SELECT 1 FROM dbo.PlanCheckpointNode
        WHERE PlanID=@plan AND NodeKey='rest' AND PosX=500.75)
    OR NOT EXISTS(SELECT 1 FROM dbo.TripLeg WHERE LegID=@leg
        AND FromNodeKey='origin' AND ToNodeKey='rest')
    THROW 51018,'Graph create/read/update roundtrip failed.',1;

-- Parameterized statements only affect synthetic fixture IDs.
DECLARE @reject TABLE (TestID int IDENTITY(1,1),SqlText nvarchar(max));
INSERT @reject(SqlText) VALUES
    (N'UPDATE dbo.TripLeg SET ToNodeKey=''other-plan-node'' WHERE LegID=@l'),
    (N'UPDATE dbo.TripLeg SET FromNodeKey=''other-plan-node'' WHERE LegID=@l'),
    (N'UPDATE dbo.TripLeg SET ToNodeKey=NULL WHERE LegID=@l'),
    (N'UPDATE dbo.TripLeg SET ToNodeKey=FromNodeKey WHERE LegID=@l'),
    (N'DELETE dbo.PlanCheckpointNode WHERE PlanID=@p AND NodeKey=''origin'''),
    (N'UPDATE dbo.PlanCheckpointNode SET PlanID=-1 WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET NodeType=''INVALID'' WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET CountryCode=''V1'' WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET SequenceOrder=0 WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET DwellMinutes=-1 WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET MetadataJson=N''not-json'' WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET MetadataJson=N''[]'' WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET QuarantineReservationID=-1
        WHERE PlanID=@p AND NodeKey=''quarantine'''),
    (N'UPDATE dbo.TransportPlan SET GraphLayoutJson=N''[]'' WHERE PlanID=@p'),
    (N'UPDATE dbo.PlanCheckpointNode SET NodeKey=''origin''
        WHERE PlanID=@p AND NodeKey=''destination'''),
    (N'UPDATE dbo.PlanCheckpointNode SET PlannedDepartureAt=
        DATEADD(minute,-1,PlannedArrivalAt) WHERE PlanID=@p AND NodeKey=''rest''');
DECLARE @i int=1,@sql nvarchar(max),@caught bit;
WHILE @i<=16
BEGIN
    SELECT @sql=SqlText FROM @reject WHERE TestID=@i;
    SET @caught=0;
    BEGIN TRY
        EXEC sys.sp_executesql @sql,N'@p bigint,@l bigint',@p=@plan,@l=@leg;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() NOT IN(547,2601,2627) THROW;
        SET @caught=1;
    END CATCH;
    IF @caught=0 THROW 51018,'An invalid graph mutation was accepted.',1;
    IF XACT_STATE()<>1 THROW 51018,'Probe unexpectedly doomed the fixture transaction.',1;
    SET @i+=1;
END;

DELETE dbo.TripLeg WHERE LegID IN(@leg,@legacyLeg);
DELETE dbo.PlanCheckpointNode WHERE PlanID IN(@plan,@otherPlan);
IF EXISTS(SELECT 1 FROM dbo.PlanCheckpointNode WHERE PlanID=@plan)
    THROW 51018,'Graph delete failed.',1;
ROLLBACK TRANSACTION;
IF (SELECT COUNT(*) FROM dbo.PlanCheckpointNode)<>@nodeCount
    THROW 51018,'Graph fixture rollback left rows behind.',1;
IF EXISTS(SELECT 1 FROM dbo.[User]
    WHERE Email IN('graph-customer-'+@suffix+'@example.invalid',
        'graph-manager-'+@suffix+'@example.invalid'))
    THROW 51018,'Graph fixture account rollback failed.',1;
PRINT 'Graph CRUD, legacy compatibility, 16 rejection probes, rollback: PASS';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
