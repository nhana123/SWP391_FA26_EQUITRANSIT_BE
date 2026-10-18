-- F10: multimodal transport plans, assignments and reservations.
-- After baseline and 001-007. Core from EquiTransit.sql; F10 runtime extensions.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF @@TRANCOUNT <> 0
    THROW 51010, 'Run migration 008 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51010, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51010, 'VIEW DEFINITION is required to verify the F10 schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51010, 'Another schema migration holds the lock.', 1;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransit:resources', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 5000;
IF @LockResult < 0
    THROW 51010, 'Resource scheduling is busy; retry the migration.', 1;

DECLARE @Dependencies TABLE (TableName sysname, ColumnName sysname);
INSERT @Dependencies VALUES
    (N'StaffProfile', N'StaffID'), (N'TransportOrder', N'OrderID'),
    (N'TransportOrder', N'CurrentPlanID'), (N'OrderRevision', N'RevisionID'),
    (N'OrderRevision', N'OrderID'), (N'Quote', N'QuoteID'),
    (N'Quote', N'RevisionID'), (N'Quote', N'AcceptedAt'),
    (N'Quote', N'QuoteStatus'), (N'Supplier', N'SupplierID'),
    (N'SupplierOffer', N'OfferID'), (N'FleetVehicle', N'VehicleID'),
    (N'StallEquipment', N'StallID'), (N'QuarantineChoice', N'QuarantineChoiceID'),
    (N'InsuranceChoice', N'InsuranceChoiceID'), (N'ResourceOperation', N'OperationID');
IF EXISTS (SELECT 1 FROM @Dependencies d
    WHERE OBJECT_ID(N'dbo.' + QUOTENAME(d.TableName), N'U') IS NULL
        OR COL_LENGTH(N'dbo.' + QUOTENAME(d.TableName), d.ColumnName) IS NULL)
    THROW 51010, 'Apply the application baseline and migrations 001-007 first.', 1;

-- Existing BE1 quote acceptance and F09 schedule guards remain authoritative.
DECLARE @RequiredTriggers TABLE (TableName sysname, TriggerName sysname);
INSERT @RequiredTriggers VALUES
    (N'TransportPlan', N'TR_Figma_TransportPlan_AcceptedQuote'),
    (N'Quote', N'TR_Figma_Quote_AcceptedHistory'),
    (N'TripLeg', N'TR_Resource_VehicleSchedule'),
    (N'StallAssignment', N'TR_Resource_StallSchedule'),
    (N'TransportPlan', N'TR_Resource_PlanActivation');
IF EXISTS (SELECT 1 FROM @RequiredTriggers e
    WHERE NOT EXISTS (SELECT 1 FROM sys.triggers t
        WHERE t.object_id = OBJECT_ID(N'dbo.' + QUOTENAME(e.TriggerName), N'TR')
            AND t.parent_id = OBJECT_ID(N'dbo.' + QUOTENAME(e.TableName), N'U')
            AND t.is_disabled = 0))
    THROW 51010, 'Missing/disabled quote or resource guards; apply 002 and 007.', 1;

-- 1. TransportPlan: F04/M06/C15 / FR-10.
IF OBJECT_ID(N'dbo.TransportPlan', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[TransportPlan](
    [PlanID] [bigint] IDENTITY(1,1) NOT NULL,
    [RevisionID] [bigint] NOT NULL,
    [CreatedBy] [bigint] NOT NULL,
    [DepartureAuthorizedBy] [bigint] NULL,
    [VersionNo] [int] NOT NULL,
    [PlannedStartTime] [datetime2](0) NULL,
    [PlannedEndTime] [datetime2](0) NULL,
    [FinancialGateStatus] [varchar](50) NOT NULL,
    [OperationalGateStatus] [varchar](50) NOT NULL,
    [DocGateStatus] [varchar](50) NOT NULL,
    [GatesCheckedAt] [datetime2](0) NULL,
    [DepartureAuthorizedAt] [datetime2](0) NULL,
    [PlanStatus] [varchar](50) NOT NULL,
    [OrderID] [bigint] NOT NULL,
    [AcceptedQuoteID] [bigint] NULL,
 CONSTRAINT [PK_TransportPlan] PRIMARY KEY CLUSTERED 
(
    [PlanID] ASC
)
);';

-- 2. TripLeg: F04/C15/D02 / FR-10.
IF OBJECT_ID(N'dbo.TripLeg', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[TripLeg](
    [LegID] [bigint] IDENTITY(1,1) NOT NULL,
    [PlanID] [bigint] NOT NULL,
    [VehicleID] [bigint] NULL,
    [SequenceNo] [int] NOT NULL,
    [TransportMode] [varchar](50) NOT NULL,
    [StartPoint] [nvarchar](300) NOT NULL,
    [EndPoint] [nvarchar](300) NOT NULL,
    [DistanceKm] [decimal](12, 3) NOT NULL,
    [PlannedDeparture] [datetime2](0) NULL,
    [PlannedArrival] [datetime2](0) NULL,
    [ActualDeparture] [datetime2](0) NULL,
    [ActualArrival] [datetime2](0) NULL,
    [LegStatus] [varchar](50) NOT NULL,
    [StartTimezone] [varchar](100) NULL,
    [EndTimezone] [varchar](100) NULL,
    [SupplierID] [bigint] NULL,
    [PlannedFlightNumber] [varchar](50) NULL,
    [ConnectionBufferMinutes] [int] NULL,
    [RestDurationMinutes] [int] NULL,
    [CareInstructions] [nvarchar](2000) NULL,
 CONSTRAINT [PK_TripLeg] PRIMARY KEY CLUSTERED 
(
    [LegID] ASC
)
);';

-- 3. TripAssignment: F08 / FR-10.
IF OBJECT_ID(N'dbo.TripAssignment', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[TripAssignment](
    [AssignmentID] [bigint] IDENTITY(1,1) NOT NULL,
    [LegID] [bigint] NOT NULL,
    [StaffID] [bigint] NOT NULL,
    [RoleInTrip] [varchar](50) NOT NULL,
    [AssignedFrom] [datetime2](0) NULL,
    [AssignedTo] [datetime2](0) NULL,
    [Status] [varchar](50) NOT NULL,
    [BufferMinutes] [int] NULL,
    [ConflictNotes] [nvarchar](1000) NULL,
 CONSTRAINT [PK_TripAssignment] PRIMARY KEY CLUSTERED 
(
    [AssignmentID] ASC
)
);';

-- 4. StallAssignment: F04/BOOK-02 / FR-10.
IF OBJECT_ID(N'dbo.StallAssignment', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[StallAssignment](
    [StallAssignmentID] [bigint] IDENTITY(1,1) NOT NULL,
    [LegID] [bigint] NOT NULL,
    [StallID] [bigint] NOT NULL,
    [SlotQuantity] [decimal](5, 1) NOT NULL,
    [SharingCode] [varchar](50) NOT NULL,
    [FitStatus] [varchar](50) NOT NULL,
    [CarrierAcceptanceStatus] [varchar](50) NOT NULL,
    [AssignedFrom] [datetime2](0) NULL,
    [AssignedTo] [datetime2](0) NULL,
    [Status] [varchar](50) NOT NULL,
    [PositionDescription] [nvarchar](500) NULL,
    [CarrierApprovalFileKey] [nvarchar](1024) NULL,
    [CarrierApprovedAt] [datetime2](0) NULL,
    [CarrierContact] [nvarchar](500) NULL,
 CONSTRAINT [PK_StallAssignment] PRIMARY KEY CLUSTERED 
(
    [StallAssignmentID] ASC
)
);';

-- 5. FlightReservation: F04 / FR-10.
IF OBJECT_ID(N'dbo.FlightReservation', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[FlightReservation](
    [ReservationID] [bigint] IDENTITY(1,1) NOT NULL,
    [LegID] [bigint] NOT NULL,
    [OfferID] [bigint] NOT NULL,
    [AirlineName] [varchar](50) NOT NULL,
    [FlightNumber] [varchar](50) NOT NULL,
    [ReservationReference] [varchar](150) NULL,
    [Status] [varchar](50) NOT NULL,
    [ConfirmedAt] [datetime2](0) NULL,
    [EvidenceFileKey] [nvarchar](1024) NULL,
    [ExpiresAt] [datetime2](0) NULL,
    [ConfirmedBy] [bigint] NULL,
    [SupplierContact] [nvarchar](500) NULL,
    [UsableCapacitySlots] [decimal](5, 1) NULL,
 CONSTRAINT [PK_FlightReservation] PRIMARY KEY CLUSTERED 
(
    [ReservationID] ASC
)
);';

-- 6. QuarantineReservation: F04/S05/BOOK-05 / FR-10.
IF OBJECT_ID(N'dbo.QuarantineReservation', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[QuarantineReservation](
    [ReservationID] [bigint] IDENTITY(1,1) NOT NULL,
    [QuarantineChoiceID] [bigint] NOT NULL,
    [FacilityName] [nvarchar](300) NOT NULL,
    [ReservationReference] [varchar](150) NULL,
    [StartsAt] [datetime2](0) NOT NULL,
    [EndsAt] [datetime2](0) NOT NULL,
    [Status] [varchar](50) NOT NULL,
    [SupplierID] [bigint] NULL,
    [EvidenceFileKey] [nvarchar](1024) NULL,
    [ConfirmedBy] [bigint] NULL,
    [ConfirmedAt] [datetime2](0) NULL,
    [ExpiresAt] [datetime2](0) NULL,
    [FacilityContact] [nvarchar](500) NULL,
 CONSTRAINT [PK_QuarantineReservation] PRIMARY KEY CLUSTERED 
(
    [ReservationID] ASC
)
);';

-- 7. InsurancePolicy: BOOK-07/F04 / FR-10.
IF OBJECT_ID(N'dbo.InsurancePolicy', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[InsurancePolicy](
    [PolicyID] [bigint] IDENTITY(1,1) NOT NULL,
    [InsuranceChoiceID] [bigint] NOT NULL,
    [InsurerName] [nvarchar](300) NOT NULL,
    [PolicyNumber] [varchar](150) NOT NULL,
    [EvidenceFileKey] [nvarchar](1024) NULL,
    [CoverageFrom] [datetime2](0) NOT NULL,
    [CoverageTo] [datetime2](0) NOT NULL,
    [Status] [varchar](50) NOT NULL,
    [VerifiedBy] [bigint] NULL,
    [VerifiedAt] [datetime2](0) NULL,
    [PolicyTerms] [nvarchar](max) NULL,
 CONSTRAINT [PK_InsurancePolicy] PRIMARY KEY CLUSTERED 
(
    [PolicyID] ASC
)
);';

-- TripLeg: sourced distances used by the BE1 itinerary pricing contract.
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceSource') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg ADD DistanceSource NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg ADD DistanceConfirmedBy BIGINT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg ADD DistanceConfirmedAt DATETIME2(0) NULL;';

-- Validate restored table definitions before applying constraints.
DECLARE @Columns TABLE (
    TableName sysname, ColumnName sysname, TypeName sysname,
    MaxLength int, [Precision] int, Scale int, IsNullable bit, IsIdentity bit);
INSERT @Columns VALUES
    (N'TransportPlan', N'PlanID', N'bigint', -1, -1, -1, 0, 1),
    (N'TransportPlan', N'RevisionID', N'bigint', -1, -1, -1, 0, 0),
    (N'TransportPlan', N'CreatedBy', N'bigint', -1, -1, -1, 0, 0),
    (N'TransportPlan', N'DepartureAuthorizedBy', N'bigint', -1, -1, -1, 1, 0),
    (N'TransportPlan', N'VersionNo', N'int', -1, -1, -1, 0, 0),
    (N'TransportPlan', N'PlannedStartTime', N'datetime2', -1, -1, 0, 1, 0),
    (N'TransportPlan', N'PlannedEndTime', N'datetime2', -1, -1, 0, 1, 0),
    (N'TransportPlan', N'FinancialGateStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'TransportPlan', N'OperationalGateStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'TransportPlan', N'DocGateStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'TransportPlan', N'GatesCheckedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'TransportPlan', N'DepartureAuthorizedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'TransportPlan', N'PlanStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'TransportPlan', N'OrderID', N'bigint', -1, -1, -1, 0, 0),
    (N'TransportPlan', N'AcceptedQuoteID', N'bigint', -1, -1, -1, 1, 0),
    (N'TripLeg', N'LegID', N'bigint', -1, -1, -1, 0, 1),
    (N'TripLeg', N'PlanID', N'bigint', -1, -1, -1, 0, 0),
    (N'TripLeg', N'VehicleID', N'bigint', -1, -1, -1, 1, 0),
    (N'TripLeg', N'SequenceNo', N'int', -1, -1, -1, 0, 0),
    (N'TripLeg', N'TransportMode', N'varchar', 50, -1, -1, 0, 0),
    (N'TripLeg', N'StartPoint', N'nvarchar', 600, -1, -1, 0, 0),
    (N'TripLeg', N'EndPoint', N'nvarchar', 600, -1, -1, 0, 0),
    (N'TripLeg', N'DistanceKm', N'decimal', -1, 12, 3, 0, 0),
    (N'TripLeg', N'PlannedDeparture', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripLeg', N'PlannedArrival', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripLeg', N'ActualDeparture', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripLeg', N'ActualArrival', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripLeg', N'LegStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'TripLeg', N'StartTimezone', N'varchar', 100, -1, -1, 1, 0),
    (N'TripLeg', N'EndTimezone', N'varchar', 100, -1, -1, 1, 0),
    (N'TripLeg', N'SupplierID', N'bigint', -1, -1, -1, 1, 0),
    (N'TripLeg', N'PlannedFlightNumber', N'varchar', 50, -1, -1, 1, 0),
    (N'TripLeg', N'ConnectionBufferMinutes', N'int', -1, -1, -1, 1, 0),
    (N'TripLeg', N'RestDurationMinutes', N'int', -1, -1, -1, 1, 0),
    (N'TripLeg', N'CareInstructions', N'nvarchar', 4000, -1, -1, 1, 0),
    (N'TripAssignment', N'AssignmentID', N'bigint', -1, -1, -1, 0, 1),
    (N'TripAssignment', N'LegID', N'bigint', -1, -1, -1, 0, 0),
    (N'TripAssignment', N'StaffID', N'bigint', -1, -1, -1, 0, 0),
    (N'TripAssignment', N'RoleInTrip', N'varchar', 50, -1, -1, 0, 0),
    (N'TripAssignment', N'AssignedFrom', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripAssignment', N'AssignedTo', N'datetime2', -1, -1, 0, 1, 0),
    (N'TripAssignment', N'Status', N'varchar', 50, -1, -1, 0, 0),
    (N'TripAssignment', N'BufferMinutes', N'int', -1, -1, -1, 1, 0),
    (N'TripAssignment', N'ConflictNotes', N'nvarchar', 2000, -1, -1, 1, 0),
    (N'StallAssignment', N'StallAssignmentID', N'bigint', -1, -1, -1, 0, 1),
    (N'StallAssignment', N'LegID', N'bigint', -1, -1, -1, 0, 0),
    (N'StallAssignment', N'StallID', N'bigint', -1, -1, -1, 0, 0),
    (N'StallAssignment', N'SlotQuantity', N'decimal', -1, 5, 1, 0, 0),
    (N'StallAssignment', N'SharingCode', N'varchar', 50, -1, -1, 0, 0),
    (N'StallAssignment', N'FitStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'StallAssignment', N'CarrierAcceptanceStatus', N'varchar', 50, -1, -1, 0, 0),
    (N'StallAssignment', N'AssignedFrom', N'datetime2', -1, -1, 0, 1, 0),
    (N'StallAssignment', N'AssignedTo', N'datetime2', -1, -1, 0, 1, 0),
    (N'StallAssignment', N'Status', N'varchar', 50, -1, -1, 0, 0),
    (N'StallAssignment', N'PositionDescription', N'nvarchar', 1000, -1, -1, 1, 0),
    (N'StallAssignment', N'CarrierApprovalFileKey', N'nvarchar', 2048, -1, -1, 1, 0),
    (N'StallAssignment', N'CarrierApprovedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'StallAssignment', N'CarrierContact', N'nvarchar', 1000, -1, -1, 1, 0),
    (N'FlightReservation', N'ReservationID', N'bigint', -1, -1, -1, 0, 1),
    (N'FlightReservation', N'LegID', N'bigint', -1, -1, -1, 0, 0),
    (N'FlightReservation', N'OfferID', N'bigint', -1, -1, -1, 0, 0),
    (N'FlightReservation', N'AirlineName', N'varchar', 50, -1, -1, 0, 0),
    (N'FlightReservation', N'FlightNumber', N'varchar', 50, -1, -1, 0, 0),
    (N'FlightReservation', N'ReservationReference', N'varchar', 150, -1, -1, 1, 0),
    (N'FlightReservation', N'Status', N'varchar', 50, -1, -1, 0, 0),
    (N'FlightReservation', N'ConfirmedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'FlightReservation', N'EvidenceFileKey', N'nvarchar', 2048, -1, -1, 1, 0),
    (N'FlightReservation', N'ExpiresAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'FlightReservation', N'ConfirmedBy', N'bigint', -1, -1, -1, 1, 0),
    (N'FlightReservation', N'SupplierContact', N'nvarchar', 1000, -1, -1, 1, 0),
    (N'FlightReservation', N'UsableCapacitySlots', N'decimal', -1, 5, 1, 1, 0),
    (N'QuarantineReservation', N'ReservationID', N'bigint', -1, -1, -1, 0, 1),
    (N'QuarantineReservation', N'QuarantineChoiceID', N'bigint', -1, -1, -1, 0, 0),
    (N'QuarantineReservation', N'FacilityName', N'nvarchar', 600, -1, -1, 0, 0),
    (N'QuarantineReservation', N'ReservationReference', N'varchar', 150, -1, -1, 1, 0),
    (N'QuarantineReservation', N'StartsAt', N'datetime2', -1, -1, 0, 0, 0),
    (N'QuarantineReservation', N'EndsAt', N'datetime2', -1, -1, 0, 0, 0),
    (N'QuarantineReservation', N'Status', N'varchar', 50, -1, -1, 0, 0),
    (N'QuarantineReservation', N'SupplierID', N'bigint', -1, -1, -1, 1, 0),
    (N'QuarantineReservation', N'EvidenceFileKey', N'nvarchar', 2048, -1, -1, 1, 0),
    (N'QuarantineReservation', N'ConfirmedBy', N'bigint', -1, -1, -1, 1, 0),
    (N'QuarantineReservation', N'ConfirmedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'QuarantineReservation', N'ExpiresAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'QuarantineReservation', N'FacilityContact', N'nvarchar', 1000, -1, -1, 1, 0),
    (N'InsurancePolicy', N'PolicyID', N'bigint', -1, -1, -1, 0, 1),
    (N'InsurancePolicy', N'InsuranceChoiceID', N'bigint', -1, -1, -1, 0, 0),
    (N'InsurancePolicy', N'InsurerName', N'nvarchar', 600, -1, -1, 0, 0),
    (N'InsurancePolicy', N'PolicyNumber', N'varchar', 150, -1, -1, 0, 0),
    (N'InsurancePolicy', N'EvidenceFileKey', N'nvarchar', 2048, -1, -1, 1, 0),
    (N'InsurancePolicy', N'CoverageFrom', N'datetime2', -1, -1, 0, 0, 0),
    (N'InsurancePolicy', N'CoverageTo', N'datetime2', -1, -1, 0, 0, 0),
    (N'InsurancePolicy', N'Status', N'varchar', 50, -1, -1, 0, 0),
    (N'InsurancePolicy', N'VerifiedBy', N'bigint', -1, -1, -1, 1, 0),
    (N'InsurancePolicy', N'VerifiedAt', N'datetime2', -1, -1, 0, 1, 0),
    (N'InsurancePolicy', N'PolicyTerms', N'nvarchar', -1, -1, -1, 1, 0),
    (N'TripLeg', N'DistanceSource', N'nvarchar', 2000, -1, -1, 1, 0),
    (N'TripLeg', N'DistanceConfirmedBy', N'bigint', -1, -1, -1, 1, 0),
    (N'TripLeg', N'DistanceConfirmedAt', N'datetime2', -1, -1, 0, 1, 0);
IF EXISTS (SELECT 1 FROM @Columns e
    LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.' + QUOTENAME(e.TableName))
        AND c.name = e.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id) <> e.TypeName
        OR (e.MaxLength <> -1 AND c.max_length <> e.MaxLength)
        OR (e.[Precision] <> -1 AND c.precision <> e.[Precision])
        OR (e.Scale <> -1 AND c.scale <> e.Scale)
        OR c.is_nullable <> e.IsNullable OR c.is_identity <> e.IsIdentity
        OR c.is_computed <> 0)
    THROW 51010, 'F10 columns differ from EquiTransit.sql; reconcile the schema.', 1;

DECLARE @PrimaryKeys TABLE (TableName sysname, ColumnName sysname);
INSERT @PrimaryKeys VALUES
    (N'TransportPlan', N'PlanID'),
    (N'TripLeg', N'LegID'),
    (N'TripAssignment', N'AssignmentID'),
    (N'StallAssignment', N'StallAssignmentID'),
    (N'FlightReservation', N'ReservationID'),
    (N'QuarantineReservation', N'ReservationID'),
    (N'InsurancePolicy', N'PolicyID');
IF EXISTS (SELECT 1 FROM @PrimaryKeys e
    WHERE NOT EXISTS (SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns k ON k.object_id = i.object_id AND k.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
        JOIN sys.identity_columns id ON id.object_id = c.object_id AND id.column_id = c.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.' + QUOTENAME(e.TableName))
            AND i.is_primary_key = 1 AND i.is_disabled = 0
            AND k.key_ordinal = 1 AND c.name = e.ColumnName
            AND CONVERT(bigint, id.seed_value) = 1 AND CONVERT(bigint, id.increment_value) = 1
            AND NOT EXISTS (SELECT 1 FROM sys.index_columns other
                WHERE other.object_id = i.object_id AND other.index_id = i.index_id
                    AND other.key_ordinal > 1)))
    THROW 51010, 'F10 primary keys must preserve IDENTITY(1,1) from the baseline.', 1;

-- Original unique keys and lookup indexes.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.FlightReservation') AND name = N'IX_FlightReservation_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_FlightReservation_LegID] ON [dbo].[FlightReservation] ( [LegID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.FlightReservation') AND name = N'IX_FlightReservation_OfferID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_FlightReservation_OfferID] ON [dbo].[FlightReservation] ( [OfferID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.InsurancePolicy') AND name = N'IX_InsurancePolicy_InsuranceChoiceID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_InsurancePolicy_InsuranceChoiceID] ON [dbo].[InsurancePolicy] ( [InsuranceChoiceID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.QuarantineReservation') AND name = N'IX_QuarantineReservation_QuarantineChoiceID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_QuarantineReservation_QuarantineChoiceID] ON [dbo].[QuarantineReservation] ( [QuarantineChoiceID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.StallAssignment') AND name = N'IX_StallAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_StallAssignment_LegID] ON [dbo].[StallAssignment] ( [LegID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.StallAssignment') AND name = N'IX_StallAssignment_StallID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_StallAssignment_StallID] ON [dbo].[StallAssignment] ( [StallID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TransportPlan') AND name = N'UQ_Figma_TransportPlan_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] ADD CONSTRAINT [UQ_Figma_TransportPlan_Parent] UNIQUE NONCLUSTERED ( [PlanID] ASC, [OrderID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TransportPlan') AND name = N'UQ_TransportPlan_Version')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] ADD CONSTRAINT [UQ_TransportPlan_Version] UNIQUE NONCLUSTERED ( [RevisionID] ASC, [VersionNo] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TransportPlan') AND name = N'IX_TransportPlan_CreatedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TransportPlan_CreatedBy] ON [dbo].[TransportPlan] ( [CreatedBy] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TransportPlan') AND name = N'IX_TransportPlan_DepartureAuthorizedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TransportPlan_DepartureAuthorizedBy] ON [dbo].[TransportPlan] ( [DepartureAuthorizedBy] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TripAssignment') AND name = N'IX_TripAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TripAssignment_LegID] ON [dbo].[TripAssignment] ( [LegID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TripAssignment') AND name = N'IX_TripAssignment_StaffID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TripAssignment_StaffID] ON [dbo].[TripAssignment] ( [StaffID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TripLeg') AND name = N'UQ_Figma_TripLeg_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] ADD CONSTRAINT [UQ_Figma_TripLeg_Parent] UNIQUE NONCLUSTERED ( [LegID] ASC, [PlanID] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TripLeg') AND name = N'UQ_TripLeg_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] ADD CONSTRAINT [UQ_TripLeg_Sequence] UNIQUE NONCLUSTERED ( [PlanID] ASC, [SequenceNo] ASC );';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TripLeg') AND name = N'IX_TripLeg_VehicleID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TripLeg_VehicleID] ON [dbo].[TripLeg] ( [VehicleID] ASC );';

-- Original foreign keys and CHECK constraints, including the current-plan pointer.
IF OBJECT_ID(N'dbo.FK_Figma_FlightReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy] FOREIGN KEY([ConfirmedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK CHECK CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy];';
IF OBJECT_ID(N'dbo.FK_FlightReservation_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_LegID] FOREIGN KEY([LegID]) REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK CHECK CONSTRAINT [FK_FlightReservation_LegID];';
IF OBJECT_ID(N'dbo.FK_FlightReservation_OfferID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_OfferID] FOREIGN KEY([OfferID]) REFERENCES [dbo].[SupplierOffer] ([OfferID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK CHECK CONSTRAINT [FK_FlightReservation_OfferID];';
IF OBJECT_ID(N'dbo.FK_Figma_InsurancePolicy_VerifiedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy] FOREIGN KEY([VerifiedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK CHECK CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy];';
IF OBJECT_ID(N'dbo.FK_InsurancePolicy_InsuranceChoiceID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID] FOREIGN KEY([InsuranceChoiceID]) REFERENCES [dbo].[InsuranceChoice] ([InsuranceChoiceID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK CHECK CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID];';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy] FOREIGN KEY([ConfirmedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK CHECK CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy];';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID] FOREIGN KEY([SupplierID]) REFERENCES [dbo].[Supplier] ([SupplierID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK CHECK CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID];';
IF OBJECT_ID(N'dbo.FK_QuarantineReservation_QuarantineChoiceID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID] FOREIGN KEY([QuarantineChoiceID]) REFERENCES [dbo].[QuarantineChoice] ([QuarantineChoiceID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK CHECK CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID];';
IF OBJECT_ID(N'dbo.FK_StallAssignment_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_LegID] FOREIGN KEY([LegID]) REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK CHECK CONSTRAINT [FK_StallAssignment_LegID];';
IF OBJECT_ID(N'dbo.FK_StallAssignment_StallID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_StallID] FOREIGN KEY([StallID]) REFERENCES [dbo].[StallEquipment] ([StallID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK CHECK CONSTRAINT [FK_StallAssignment_StallID];';
IF OBJECT_ID(N'dbo.FK_Figma_TransportOrder_CurrentPlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID] FOREIGN KEY([CurrentPlanID], [OrderID]) REFERENCES [dbo].[TransportPlan] ([PlanID], [OrderID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK CHECK CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID];';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_AcceptedQuoteID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID] FOREIGN KEY([AcceptedQuoteID], [RevisionID]) REFERENCES [dbo].[Quote] ([QuoteID], [RevisionID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID];';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_RevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_RevisionID] FOREIGN KEY([RevisionID], [OrderID]) REFERENCES [dbo].[OrderRevision] ([RevisionID], [OrderID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [FK_Figma_TransportPlan_RevisionID];';
IF OBJECT_ID(N'dbo.FK_TransportPlan_CreatedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_CreatedBy] FOREIGN KEY([CreatedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [FK_TransportPlan_CreatedBy];';
IF OBJECT_ID(N'dbo.FK_TransportPlan_DepartureAuthorizedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy] FOREIGN KEY([DepartureAuthorizedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy];';
IF OBJECT_ID(N'dbo.FK_TransportPlan_RevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_RevisionID] FOREIGN KEY([RevisionID]) REFERENCES [dbo].[OrderRevision] ([RevisionID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [FK_TransportPlan_RevisionID];';
IF OBJECT_ID(N'dbo.FK_TripAssignment_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_LegID] FOREIGN KEY([LegID]) REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK CHECK CONSTRAINT [FK_TripAssignment_LegID];';
IF OBJECT_ID(N'dbo.FK_TripAssignment_StaffID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_StaffID] FOREIGN KEY([StaffID]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK CHECK CONSTRAINT [FK_TripAssignment_StaffID];';
IF OBJECT_ID(N'dbo.FK_Figma_TripLeg_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [FK_Figma_TripLeg_SupplierID] FOREIGN KEY([SupplierID]) REFERENCES [dbo].[Supplier] ([SupplierID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [FK_Figma_TripLeg_SupplierID];';
IF OBJECT_ID(N'dbo.FK_TripLeg_PlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_PlanID] FOREIGN KEY([PlanID]) REFERENCES [dbo].[TransportPlan] ([PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [FK_TripLeg_PlanID];';
IF OBJECT_ID(N'dbo.FK_TripLeg_VehicleID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_VehicleID] FOREIGN KEY([VehicleID]) REFERENCES [dbo].[FleetVehicle] ([VehicleID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [FK_TripLeg_VehicleID];';
IF OBJECT_ID(N'dbo.CK_Figma_FlightReservation_UsableCapacitySlots', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FlightReservation] WITH CHECK ADD CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots] CHECK (([UsableCapacitySlots]>(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK CHECK CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots];';
IF OBJECT_ID(N'dbo.CK_InsurancePolicy_CoverageTo_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[InsurancePolicy] WITH CHECK ADD CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range] CHECK (([CoverageTo] IS NULL OR [CoverageFrom] IS NOT NULL AND [CoverageTo]>=[CoverageFrom]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK CHECK CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range];';
IF OBJECT_ID(N'dbo.CK_QuarantineReservation_EndsAt_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[QuarantineReservation] WITH CHECK ADD CONSTRAINT [CK_QuarantineReservation_EndsAt_Range] CHECK (([EndsAt] IS NULL OR [StartsAt] IS NOT NULL AND [EndsAt]>=[StartsAt]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK CHECK CONSTRAINT [CK_QuarantineReservation_EndsAt_Range];';
IF OBJECT_ID(N'dbo.CK_StallAssignment_AssignedTo_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_AssignedTo_Range] CHECK (([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK CHECK CONSTRAINT [CK_StallAssignment_AssignedTo_Range];';
IF OBJECT_ID(N'dbo.CK_StallAssignment_SlotQuantity', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_SlotQuantity] CHECK (([SlotQuantity]>(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK CHECK CONSTRAINT [CK_StallAssignment_SlotQuantity];';
IF OBJECT_ID(N'dbo.CK_TransportPlan_PlannedEndTime_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range] CHECK (([PlannedEndTime] IS NULL OR [PlannedStartTime] IS NOT NULL AND [PlannedEndTime]>=[PlannedStartTime]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range];';
IF OBJECT_ID(N'dbo.CK_TransportPlan_VersionNo', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_VersionNo] CHECK (([VersionNo]>(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK CHECK CONSTRAINT [CK_TransportPlan_VersionNo];';
IF OBJECT_ID(N'dbo.CK_Figma_TripAssignment_BufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes] CHECK (([BufferMinutes]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes];';
IF OBJECT_ID(N'dbo.CK_TripAssignment_AssignedTo_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_TripAssignment_AssignedTo_Range] CHECK (([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK CHECK CONSTRAINT [CK_TripAssignment_AssignedTo_Range];';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_ConnectionBufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes] CHECK (([ConnectionBufferMinutes]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes];';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_RestDurationMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes] CHECK (([RestDurationMinutes]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes];';
IF OBJECT_ID(N'dbo.CK_TripLeg_ActualArrival_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_ActualArrival_Range] CHECK (([ActualArrival] IS NULL OR [ActualDeparture] IS NOT NULL AND [ActualArrival]>=[ActualDeparture]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_TripLeg_ActualArrival_Range];';
IF OBJECT_ID(N'dbo.CK_TripLeg_DistanceKm', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_DistanceKm] CHECK (([DistanceKm]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_TripLeg_DistanceKm];';
IF OBJECT_ID(N'dbo.CK_TripLeg_PlannedArrival_Range', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_PlannedArrival_Range] CHECK (([PlannedArrival] IS NULL OR [PlannedDeparture] IS NOT NULL AND [PlannedArrival]>=[PlannedDeparture]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_TripLeg_PlannedArrival_Range];';
IF OBJECT_ID(N'dbo.CK_TripLeg_SequenceNo', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_SequenceNo] CHECK (([SequenceNo]>(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [CK_TripLeg_SequenceNo];';
IF OBJECT_ID(N'dbo.FK_TripLeg_DistanceConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT FK_TripLeg_DistanceConfirmedBy FOREIGN KEY (DistanceConfirmedBy) REFERENCES dbo.StaffProfile(StaffID);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK CHECK CONSTRAINT [FK_TripLeg_DistanceConfirmedBy];';
IF OBJECT_ID(N'dbo.CK_TripLeg_DistanceSource', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT CK_TripLeg_DistanceSource CHECK ((DistanceSource IS NULL AND DistanceConfirmedBy IS NULL AND DistanceConfirmedAt IS NULL) OR (DistanceSource IS NOT NULL AND LEN(LTRIM(RTRIM(DistanceSource)))>0 AND DistanceConfirmedBy IS NOT NULL AND DistanceConfirmedAt IS NOT NULL));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg WITH CHECK CHECK CONSTRAINT [CK_TripLeg_DistanceSource];';


-- 8. PlanOperation: Technical / FR-10.
IF OBJECT_ID('dbo.PlanOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.PlanOperation (
        OrderID BIGINT NOT NULL,
        UserID BIGINT NOT NULL,
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_PlanOperation PRIMARY KEY (OrderID,UserID,OperationID),
        CONSTRAINT FK_PlanOperation_Order FOREIGN KEY (OrderID)
            REFERENCES dbo.TransportOrder(OrderID),
        CONSTRAINT FK_PlanOperation_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_PlanOperation_JSON CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND
    name='IX_Plan_StaffWindow')
    CREATE INDEX IX_Plan_StaffWindow ON dbo.TripAssignment
        (StaffID,AssignedFrom,AssignedTo)
        INCLUDE (LegID,RoleInTrip,Status,BufferMinutes);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND
    name='IX_Plan_OrderSchedule')
    CREATE INDEX IX_Plan_OrderSchedule ON dbo.TransportPlan
        (OrderID,PlannedStartTime,PlannedEndTime)
        INCLUDE (PlanID,RevisionID,PlanStatus,AcceptedQuoteID);


DECLARE @OperationColumns TABLE (ColumnName sysname,TypeName sysname,
    MaxLength int);
INSERT @OperationColumns VALUES
    (N'OrderID',N'bigint',8),(N'UserID',N'bigint',8),
    (N'OperationID',N'uniqueidentifier',16),(N'RequestHash',N'varchar',64),
    (N'ReceiptJSON',N'nvarchar',-1),(N'CreatedAt',N'datetime2',6);
IF EXISTS (SELECT 1 FROM @OperationColumns e
    LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.PlanOperation')
        AND c.name=e.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id)<>e.TypeName
        OR c.max_length<>e.MaxLength OR c.is_nullable<>0)
    THROW 51010, 'PlanOperation schema differs from the F10 contract.', 1;
EXEC sys.sp_executesql N'ALTER TABLE dbo.PlanOperation WITH CHECK CHECK CONSTRAINT ALL;';

-- QuarantineReservation: plan-scoped F10 proof; legacy rows stay unbound.
IF COL_LENGTH(N'dbo.QuarantineReservation',N'PlanID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.QuarantineReservation ADD PlanID bigint NULL;';
IF OBJECT_ID(N'dbo.FK_QuarantineReservation_PlanID',N'F') IS NULL
EXEC sys.sp_executesql N'ALTER TABLE dbo.QuarantineReservation WITH CHECK ADD CONSTRAINT FK_QuarantineReservation_PlanID FOREIGN KEY (PlanID) REFERENCES dbo.TransportPlan(PlanID);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.QuarantineReservation WITH CHECK CHECK CONSTRAINT FK_QuarantineReservation_PlanID;';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.QuarantineReservation') AND name=N'IX_QuarantineReservation_Plan')
EXEC sys.sp_executesql N'CREATE INDEX IX_QuarantineReservation_Plan ON dbo.QuarantineReservation(PlanID,QuarantineChoiceID);';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Plan_QuarantineScope
ON dbo.QuarantineReservation AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        JOIN dbo.QuarantineChoice q ON q.QuarantineChoiceID=i.QuarantineChoiceID
        JOIN dbo.OrderServiceChoice c ON c.ChoiceID=q.ChoiceID
        WHERE i.PlanID IS NOT NULL AND p.RevisionID<>c.RevisionID)
        THROW 51010, ''Reservation/policy belongs to another plan revision.'', 1;
END;';
EXEC sys.sp_executesql N'ENABLE TRIGGER dbo.TR_Plan_QuarantineScope ON dbo.QuarantineReservation;';

-- InsurancePolicy: plan-scoped F10 proof; legacy rows stay unbound.
IF COL_LENGTH(N'dbo.InsurancePolicy',N'PlanID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.InsurancePolicy ADD PlanID bigint NULL;';
IF OBJECT_ID(N'dbo.FK_InsurancePolicy_PlanID',N'F') IS NULL
EXEC sys.sp_executesql N'ALTER TABLE dbo.InsurancePolicy WITH CHECK ADD CONSTRAINT FK_InsurancePolicy_PlanID FOREIGN KEY (PlanID) REFERENCES dbo.TransportPlan(PlanID);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.InsurancePolicy WITH CHECK CHECK CONSTRAINT FK_InsurancePolicy_PlanID;';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.InsurancePolicy') AND name=N'IX_InsurancePolicy_Plan')
EXEC sys.sp_executesql N'CREATE INDEX IX_InsurancePolicy_Plan ON dbo.InsurancePolicy(PlanID,InsuranceChoiceID);';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Plan_InsuranceScope
ON dbo.InsurancePolicy AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        JOIN dbo.InsuranceChoice q ON q.InsuranceChoiceID=i.InsuranceChoiceID
        JOIN dbo.OrderServiceChoice c ON c.ChoiceID=q.ChoiceID
        WHERE i.PlanID IS NOT NULL AND p.RevisionID<>c.RevisionID)
        THROW 51010, ''Reservation/policy belongs to another plan revision.'', 1;
END;';
EXEC sys.sp_executesql N'ENABLE TRIGGER dbo.TR_Plan_InsuranceScope ON dbo.InsurancePolicy;';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Plan_DriverSchedule
ON dbo.TripAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource=''EquiTransit:resources'',
        @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=5000;
    IF @r<0 THROW 51001, ''Resource scheduling busy.'', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.StaffProfile s WITH (HOLDLOCK) ON s.StaffID=i.StaffID
        LEFT JOIN dbo.[User] u WITH (HOLDLOCK) ON u.UserID=s.UserID
        LEFT JOIN dbo.[Role] r WITH (HOLDLOCK) ON r.RoleID=u.RoleID
        WHERE i.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        (s.StaffID IS NULL OR u.Status<>''ACTIVE''
         OR (i.RoleInTrip=''DRIVER'' AND (l.TransportMode<>''ROAD''
             OR r.RoleCode<>''DRIVER''))
         OR i.BufferMinutes IS NULL OR i.BufferMinutes<0
         OR i.AssignedFrom IS NULL OR i.AssignedTo IS NULL
         OR i.AssignedFrom>=i.AssignedTo))
        THROW 51006, ''Trip staff mode, role or window invalid.'', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StaffID=b.StaffID
        AND a.AssignmentID<>b.AssignmentID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        b.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        pa.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        pb.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,a.AssignedTo))))
        THROW 51007, ''Driver or staff has an overlapping trip assignment.'', 1;
END;';
EXEC sys.sp_executesql N'ENABLE TRIGGER dbo.TR_Plan_DriverSchedule ON dbo.TripAssignment;';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Resource_PlanActivation
ON dbo.TransportPlan AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(PlanStatus) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d
        ON d.PlanID=i.PlanID WHERE
        (i.PlanStatus IN (''CONFIRMED'',''ACTIVE'',''IN_TRANSIT'') AND
         d.PlanStatus NOT IN (''CONFIRMED'',''ACTIVE'',''IN_TRANSIT'')) OR
        (d.PlanStatus IN (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
         i.PlanStatus NOT IN (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'')))
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource=''EquiTransit:resources'',
        @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=5000;
    IF @r<0 THROW 51001, ''Resource scheduling busy.'', 1;
    UPDATE l SET LegStatus=l.LegStatus FROM dbo.TripLeg l
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE a SET Status=a.Status FROM dbo.StallAssignment a
        JOIN dbo.TripLeg l ON l.LegID=a.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE t SET Status=t.Status FROM dbo.TripAssignment t
        JOIN dbo.TripLeg l ON l.LegID=t.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportOrder oa ON oa.OrderID=i.OrderID
        JOIN dbo.TransportOrderDetail da ON da.OrderID=oa.OrderID
        JOIN dbo.TransportOrderDetail db ON db.HorseID=da.HorseID
            AND db.OrderID<>da.OrderID
        JOIN dbo.TransportOrder ob WITH (UPDLOCK,HOLDLOCK)
            ON ob.OrderID=db.OrderID
        JOIN dbo.TransportPlan pb WITH (UPDLOCK,HOLDLOCK)
            ON pb.PlanID=ob.CurrentPlanID AND pb.OrderID=ob.OrderID
        WHERE i.PlanStatus IN (''CONFIRMED'',''ACTIVE'',''IN_TRANSIT'')
        AND ob.OrderStatus NOT IN
            (''COMPLETED'',''DELIVERED'',''CANCELLED'',''CANCELED'',''CLOSED'',''REJECTED'')
        AND pb.PlanStatus IN (''CONFIRMED'',''ACTIVE'',''IN_TRANSIT'')
        AND (i.PlannedStartTime IS NULL OR i.PlannedEndTime IS NULL OR
             pb.PlannedStartTime IS NULL OR pb.PlannedEndTime IS NULL OR
             (i.PlannedStartTime<pb.PlannedEndTime AND
              pb.PlannedStartTime<i.PlannedEndTime)))
        THROW 51008, ''Horse has an overlapping active operational plan.'', 1;
END;';
EXEC sys.sp_executesql N'ENABLE TRIGGER dbo.TR_Resource_PlanActivation ON dbo.TransportPlan;';

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
