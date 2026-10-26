-- EXPERIMENTAL: Journey Studio graph. Baseline plus migrations 001-017 required.
-- Sandbox only. Production promotion requires a separate, explicit approval.
-- Existing legs keep NULL endpoint keys; no historical data is rewritten.
-- UTC DATETIME2 storage matches the existing JDBC timestamp contract.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() NOT IN (N'EquiTransit_Test', N'EquiTransit_Demo',
    N'EquiTransit_FigmaMigrationTest_20261013_143729')
    THROW 51018, 'Experimental migration 018 is restricted to sandbox catalogs.', 1;
IF @@TRANCOUNT <> 0
    THROW 51018, 'Run migration 018 without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.TransportPlan', N'U') IS NULL
    OR OBJECT_ID(N'dbo.TripLeg', N'U') IS NULL
    OR OBJECT_ID(N'dbo.QuarantineReservation', N'U') IS NULL
    OR OBJECT_ID(N'dbo.RefreshTokenSession', N'U') IS NULL
    OR COL_LENGTH(N'dbo.ActualCost', N'EvidenceFileKey') IS NULL
    THROW 51018, 'Apply the baseline and preceding migrations 001-017 first.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock = sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
IF @lock < 0 THROW 51018, 'Another schema migration holds the lock.', 1;

IF OBJECT_ID(N'dbo.PlanCheckpointNode', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.PlanCheckpointNode (
        NodeID BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_PlanCheckpointNode PRIMARY KEY,
        PlanID BIGINT NOT NULL,
        NodeKey NVARCHAR(64) NOT NULL,
        NodeType VARCHAR(24) NOT NULL,
        SequenceOrder INT NOT NULL,
        LocationName NVARCHAR(200) NOT NULL,
        CountryCode CHAR(2) NOT NULL,
        PlannedArrivalAt DATETIME2(0) NULL,
        PlannedDepartureAt DATETIME2(0) NULL,
        DwellMinutes INT NOT NULL
            CONSTRAINT DF_PlanCheckpointNode_Dwell DEFAULT (0),
        TransitCustomsMode VARCHAR(24) NULL,
        TransitPermitRef NVARCHAR(120) NULL,
        WelfareCareNotes NVARCHAR(2000) NULL,
        QuarantineReservationID BIGINT NULL,
        PosX DECIMAL(10,2) NOT NULL
            CONSTRAINT DF_PlanCheckpointNode_PosX DEFAULT (0),
        PosY DECIMAL(10,2) NOT NULL
            CONSTRAINT DF_PlanCheckpointNode_PosY DEFAULT (0),
        MetadataJson NVARCHAR(MAX) NULL,
        CONSTRAINT UQ_PlanCheckpointNode_Key UNIQUE (PlanID,NodeKey),
        CONSTRAINT UQ_PlanCheckpointNode_Sequence
            UNIQUE (PlanID,SequenceOrder),
        CONSTRAINT FK_PlanCheckpointNode_Plan FOREIGN KEY (PlanID)
            REFERENCES dbo.TransportPlan(PlanID),
        CONSTRAINT FK_PlanCheckpointNode_Quarantine
            FOREIGN KEY (QuarantineReservationID)
            REFERENCES dbo.QuarantineReservation(ReservationID),
        CONSTRAINT CK_PlanCheckpointNode_Key CHECK
            (LEN(LTRIM(RTRIM(NodeKey))) > 0),
        CONSTRAINT CK_PlanCheckpointNode_Type CHECK (NodeType IN
            (''ORIGIN'',''REST_STOP'',''AIRPORT_HANDOVER'',
             ''TRANSIT_HUB'',''QUARANTINE'',''DESTINATION'')),
        CONSTRAINT CK_PlanCheckpointNode_Sequence
            CHECK (SequenceOrder >= 1),
        CONSTRAINT CK_PlanCheckpointNode_Location CHECK
            (LEN(LTRIM(RTRIM(LocationName))) > 0),
        CONSTRAINT CK_PlanCheckpointNode_Country CHECK
            (CountryCode COLLATE Latin1_General_100_BIN2
                LIKE ''[A-Z][A-Z]''),
        CONSTRAINT CK_PlanCheckpointNode_Times CHECK
            (PlannedArrivalAt IS NULL OR PlannedDepartureAt IS NULL
             OR PlannedDepartureAt >= PlannedArrivalAt),
        CONSTRAINT CK_PlanCheckpointNode_Dwell CHECK
            (DwellMinutes BETWEEN 0 AND 525600),
        CONSTRAINT CK_PlanCheckpointNode_Customs CHECK
            (TransitCustomsMode IS NULL OR TransitCustomsMode IN
             (''STAY_ON_AIRCRAFT'',''AIRSIDE_TRANSFER'',''TERMINAL_HOLDING'')),
        CONSTRAINT CK_PlanCheckpointNode_Quarantine CHECK
            (QuarantineReservationID IS NULL OR NodeType=''QUARANTINE''),
        CONSTRAINT CK_PlanCheckpointNode_Metadata CHECK
            (MetadataJson IS NULL OR (ISJSON(MetadataJson)=1
             AND LEFT(LTRIM(MetadataJson),1)=''{''
             AND DATALENGTH(MetadataJson)<=32768))
    );';

IF COL_LENGTH(N'dbo.TripLeg', N'FromNodeKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg
        ADD FromNodeKey NVARCHAR(64) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'ToNodeKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg
        ADD ToNodeKey NVARCHAR(64) NULL;';
IF COL_LENGTH(N'dbo.TransportPlan', N'GraphLayoutJson') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan
        ADD GraphLayoutJson NVARCHAR(MAX) NULL;';

-- Fail safely on a partially deployed or incompatible schema; never coerce data.
DECLARE @expected TABLE (TableName sysname, ColumnName sysname,
    TypeName sysname, MaxLength int, Scale int, Nullable bit, IdentityFlag bit);
INSERT @expected VALUES
    ('PlanCheckpointNode','NodeID','bigint',8,0,0,1),
    ('PlanCheckpointNode','PlanID','bigint',8,0,0,0),
    ('PlanCheckpointNode','NodeKey','nvarchar',128,0,0,0),
    ('PlanCheckpointNode','NodeType','varchar',24,0,0,0),
    ('PlanCheckpointNode','SequenceOrder','int',4,0,0,0),
    ('PlanCheckpointNode','LocationName','nvarchar',400,0,0,0),
    ('PlanCheckpointNode','CountryCode','char',2,0,0,0),
    ('PlanCheckpointNode','PlannedArrivalAt','datetime2',6,0,1,0),
    ('PlanCheckpointNode','PlannedDepartureAt','datetime2',6,0,1,0),
    ('PlanCheckpointNode','DwellMinutes','int',4,0,0,0),
    ('PlanCheckpointNode','TransitCustomsMode','varchar',24,0,1,0),
    ('PlanCheckpointNode','TransitPermitRef','nvarchar',240,0,1,0),
    ('PlanCheckpointNode','WelfareCareNotes','nvarchar',4000,0,1,0),
    ('PlanCheckpointNode','QuarantineReservationID','bigint',8,0,1,0),
    ('PlanCheckpointNode','PosX','decimal',9,2,0,0),
    ('PlanCheckpointNode','PosY','decimal',9,2,0,0),
    ('PlanCheckpointNode','MetadataJson','nvarchar',-1,0,1,0),
    ('TripLeg','FromNodeKey','nvarchar',128,0,1,0),
    ('TripLeg','ToNodeKey','nvarchar',128,0,1,0),
    ('TransportPlan','GraphLayoutJson','nvarchar',-1,0,1,0);
IF EXISTS (SELECT 1 FROM @expected e LEFT JOIN sys.columns c
    ON c.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.system_type_id)<>e.TypeName
        OR c.max_length<>e.MaxLength OR c.scale<>e.Scale
        OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityFlag
        OR c.is_computed<>0
        OR (e.TypeName='decimal' AND c.precision<>10))
    THROW 51018, 'Journey graph column shape mismatch; review before retry.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'dbo.TripLeg')
        AND name=N'CK_TripLeg_GraphEndpoints')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK
        ADD CONSTRAINT CK_TripLeg_GraphEndpoints CHECK
        ((FromNodeKey IS NULL AND ToNodeKey IS NULL)
         OR (FromNodeKey IS NOT NULL AND ToNodeKey IS NOT NULL
             AND FromNodeKey<>ToNodeKey));';
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
    WHERE name=N'FK_TripLeg_FromCheckpoint')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK
        ADD CONSTRAINT FK_TripLeg_FromCheckpoint
        FOREIGN KEY (PlanID,FromNodeKey)
        REFERENCES dbo.PlanCheckpointNode(PlanID,NodeKey);';
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
    WHERE name=N'FK_TripLeg_ToCheckpoint')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK
        ADD CONSTRAINT FK_TripLeg_ToCheckpoint
        FOREIGN KEY (PlanID,ToNodeKey)
        REFERENCES dbo.PlanCheckpointNode(PlanID,NodeKey);';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'dbo.TransportPlan')
        AND name=N'CK_TransportPlan_GraphLayout')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan WITH CHECK
        ADD CONSTRAINT CK_TransportPlan_GraphLayout CHECK
        (GraphLayoutJson IS NULL OR (ISJSON(GraphLayoutJson)=1
         AND LEFT(LTRIM(GraphLayoutJson),1)=''{''
         AND DATALENGTH(GraphLayoutJson)<=131072));';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.TripLeg')
        AND name=N'IX_TripLeg_GraphFrom')
    EXEC sys.sp_executesql N'CREATE INDEX IX_TripLeg_GraphFrom
        ON dbo.TripLeg(PlanID,FromNodeKey);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.TripLeg')
        AND name=N'IX_TripLeg_GraphTo')
    EXEC sys.sp_executesql N'CREATE INDEX IX_TripLeg_GraphTo
        ON dbo.TripLeg(PlanID,ToNodeKey);';

-- A rerun must not silently accept an existing table missing its protections.
DECLARE @requiredChecks TABLE (TableName sysname, ConstraintName sysname);
INSERT @requiredChecks VALUES
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Key'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Type'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Sequence'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Location'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Country'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Times'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Dwell'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Customs'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Quarantine'),
    ('PlanCheckpointNode','CK_PlanCheckpointNode_Metadata'),
    ('TripLeg','CK_TripLeg_GraphEndpoints'),
    ('TransportPlan','CK_TransportPlan_GraphLayout');
IF EXISTS (SELECT 1 FROM @requiredChecks r
    LEFT JOIN sys.check_constraints c ON c.name=r.ConstraintName
        AND c.parent_object_id=OBJECT_ID(N'dbo.'+r.TableName)
    WHERE c.object_id IS NULL)
    THROW 51018, 'Journey graph is missing required CHECK constraints.', 1;
DECLARE @requiredForeignKeys TABLE (ConstraintName sysname,
    ParentTable sysname, ReferencedTable sysname, ColumnCount int);
INSERT @requiredForeignKeys VALUES
    ('FK_PlanCheckpointNode_Plan','PlanCheckpointNode','TransportPlan',1),
    ('FK_PlanCheckpointNode_Quarantine','PlanCheckpointNode',
        'QuarantineReservation',1),
    ('FK_TripLeg_FromCheckpoint','TripLeg','PlanCheckpointNode',2),
    ('FK_TripLeg_ToCheckpoint','TripLeg','PlanCheckpointNode',2);
IF EXISTS (SELECT 1 FROM @requiredForeignKeys r
    LEFT JOIN sys.foreign_keys f ON f.name=r.ConstraintName
    WHERE f.object_id IS NULL
        OR f.parent_object_id<>OBJECT_ID(N'dbo.'+r.ParentTable)
        OR f.referenced_object_id<>OBJECT_ID(N'dbo.'+r.ReferencedTable)
        OR f.delete_referential_action<>0 OR f.update_referential_action<>0
        OR (SELECT COUNT(*) FROM sys.foreign_key_columns c
            WHERE c.constraint_object_id=f.object_id)<>r.ColumnCount)
    THROW 51018, 'Journey graph FK shape mismatch.', 1;
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name IN
    (N'FK_PlanCheckpointNode_Plan',N'FK_PlanCheckpointNode_Quarantine',
     N'FK_TripLeg_FromCheckpoint',N'FK_TripLeg_ToCheckpoint')
    AND (is_disabled=1 OR is_not_trusted=1))
    OR EXISTS (SELECT 1 FROM sys.check_constraints WHERE
        (parent_object_id=OBJECT_ID(N'dbo.PlanCheckpointNode') OR name IN
        (N'CK_TripLeg_GraphEndpoints',N'CK_TransportPlan_GraphLayout'))
        AND (is_disabled=1 OR is_not_trusted=1))
    THROW 51018, 'Journey graph constraints must remain enabled and trusted.', 1;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
