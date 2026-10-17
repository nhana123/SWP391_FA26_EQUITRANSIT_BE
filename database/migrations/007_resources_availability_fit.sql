-- F09: resource catalogues, availability and sourced stall fit.
-- After baseline and 001-006. Core tables from EquiTransit.sql; no seed data.
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
    THROW 51009, 'Run migration 007 without an existing transaction.', 1;
IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
    THROW 51009, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),N'DATABASE',N'VIEW DEFINITION'),0)<>1
    THROW 51009, 'VIEW DEFINITION is required to verify the resource schema.', 1;
IF (SELECT compatibility_level FROM sys.databases WHERE name=DB_NAME())<130
    THROW 51009, 'Database compatibility level 130 or newer is required.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult=sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @LockResult<0
    THROW 51009, 'Another schema migration holds the lock.', 1;
EXEC @LockResult=sys.sp_getapplock @Resource=N'EquiTransit:resources',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
IF @LockResult<0
    THROW 51009, 'Resource scheduling is busy; retry the migration.', 1;

DECLARE @Dependencies TABLE (TableName sysname,ColumnName sysname);
INSERT @Dependencies VALUES
    (N'User',N'UserID'),(N'StaffProfile',N'StaffID'),
    (N'TripLeg',N'LegID'),(N'TripLeg',N'PlanID'),
    (N'TripLeg',N'VehicleID'),(N'TripLeg',N'TransportMode'),
    (N'TripLeg',N'PlannedDeparture'),(N'TripLeg',N'PlannedArrival'),
    (N'TripLeg',N'LegStatus'),(N'TripLeg',N'SupplierID'),(N'StallAssignment',N'StallAssignmentID'),
    (N'StallAssignment',N'LegID'),(N'StallAssignment',N'StallID'),
    (N'StallAssignment',N'SlotQuantity'),(N'StallAssignment',N'AssignedFrom'),
    (N'StallAssignment',N'AssignedTo'),(N'StallAssignment',N'Status'),
    (N'TransportPlan',N'PlanID'),(N'TransportPlan',N'PlanStatus'),
    (N'TransportPlan',N'OrderID'),(N'TransportPlan',N'RevisionID'),
    (N'TransportOrder',N'CurrentPlanID'),
    (N'TransportOrder',N'CurrentRevisionID'),
    (N'TripAssignment',N'LegID'),(N'TripAssignment',N'StaffID'),
    (N'TripAssignment',N'RoleInTrip'),(N'TripAssignment',N'Status'),
    (N'TripAssignment',N'AssignedTo');
IF EXISTS (SELECT 1 FROM @Dependencies d
    WHERE OBJECT_ID(N'dbo.'+QUOTENAME(d.TableName),N'U') IS NULL
        OR COL_LENGTH(N'dbo.'+QUOTENAME(d.TableName),d.ColumnName) IS NULL)
    THROW 51009, 'Apply the operational baseline before migration 007.', 1;


-- 1. Supplier: M12/F02/F04/F07 / F09.
IF OBJECT_ID(N'dbo.Supplier',N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[Supplier](
    [SupplierID] [bigint] IDENTITY(1,1) NOT NULL,
    [Name] [nvarchar](200) NOT NULL,
    [ContactName] [nvarchar](150) NULL,
    [Email] [varchar](255) NULL,
    [Phone] [varchar](30) NULL,
    [ServicesOfferedJSON] [nvarchar](max) NULL,
    [IsActive] [bit] NOT NULL,
    [CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Supplier] PRIMARY KEY CLUSTERED 
(
    [SupplierID] ASC
)
);';

IF COL_LENGTH(N'dbo.Supplier',N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] ADD RecordVersion rowversion;';

-- 2. FleetVehicle: F06/F07 / F09.
IF OBJECT_ID(N'dbo.FleetVehicle',N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[FleetVehicle](
    [VehicleID] [bigint] IDENTITY(1,1) NOT NULL,
    [VehicleCode] [varchar](50) NOT NULL,
    [PlateNumber] [varchar](50) NOT NULL,
    [HorseCapacity] [int] NOT NULL,
    [ClimateSystemType] [varchar](50) NOT NULL,
    [OperationalStatus] [varchar](50) NOT NULL,
    [OwnerSupplierID] [bigint] NULL,
    [MaintenanceFrom] [datetime2](0) NULL,
    [MaintenanceTo] [datetime2](0) NULL,
    [DocumentExpiresAt] [datetime2](0) NULL,
    [AssetNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_FleetVehicle] PRIMARY KEY CLUSTERED 
(
    [VehicleID] ASC
)
);';

IF COL_LENGTH(N'dbo.FleetVehicle',N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD RecordVersion rowversion;';

IF COL_LENGTH(N'dbo.FleetVehicle',N'BufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD BufferMinutes int NULL;';

-- 3. StallEquipment: F06/F07/BOOK-02 / F09.
IF OBJECT_ID(N'dbo.StallEquipment',N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[StallEquipment](
    [StallID] [bigint] IDENTITY(1,1) NOT NULL,
    [StallCode] [varchar](50) NOT NULL,
    [CapacitySlots] [decimal](5, 1) NOT NULL,
    [InternalHeightCm] [decimal](8, 2) NOT NULL,
    [InternalWidthCm] [decimal](8, 2) NOT NULL,
    [EquipmentStatus] [varchar](50) NOT NULL,
    [OwnerSupplierID] [bigint] NULL,
    [WeightLimitKg] [decimal](8, 2) NULL,
    [MaintenanceFrom] [datetime2](0) NULL,
    [MaintenanceTo] [datetime2](0) NULL,
    [DocumentExpiresAt] [datetime2](0) NULL,
    [AssetNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_StallEquipment] PRIMARY KEY CLUSTERED 
(
    [StallID] ASC
)
);';

IF COL_LENGTH(N'dbo.StallEquipment',N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD RecordVersion rowversion;';

IF COL_LENGTH(N'dbo.StallEquipment',N'BufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD BufferMinutes int NULL;';

-- 4. StallFitPolicy: BOOK-02/F04/F07 / F09.
IF OBJECT_ID(N'dbo.StallFitPolicy',N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[StallFitPolicy](
    [PolicyID] [bigint] IDENTITY(1,1) NOT NULL,
    [PolicyCode] [varchar](50) NOT NULL,
    [VersionNo] [int] NOT NULL,
    [StallID] [bigint] NULL,
    [CarrierSupplierID] [bigint] NULL,
    [SlotQuantity] [decimal](5, 1) NOT NULL,
    [MaxWeightKg] [decimal](8, 2) NULL,
    [MaxHeightCm] [decimal](8, 2) NULL,
    [MaxWidthCm] [decimal](8, 2) NULL,
    [EffectiveFrom] [datetime2](0) NOT NULL,
    [EffectiveTo] [datetime2](0) NULL,
    [SourceURL] [nvarchar](2048) NULL,
    [SourceFileKey] [nvarchar](1024) NULL,
    [ReviewedBy] [bigint] NULL,
    [ReviewedAt] [datetime2](0) NULL,
    [Status] [varchar](20) NOT NULL,
 CONSTRAINT [PK_StallFitPolicy] PRIMARY KEY CLUSTERED 
(
    [PolicyID] ASC
)
);';

IF COL_LENGTH(N'dbo.StallFitPolicy',N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] ADD RecordVersion rowversion;';

-- 5. ResourceOperation: Technical / F09.
IF OBJECT_ID(N'dbo.ResourceOperation',N'U') IS NULL
EXEC sys.sp_executesql N'CREATE TABLE dbo.ResourceOperation (
    UserID bigint NOT NULL,
    OperationID uniqueidentifier NOT NULL,
    RequestHash varchar(64) NOT NULL,
    ReceiptJSON nvarchar(max) NOT NULL,
    CreatedAt datetime2(0) NOT NULL
        CONSTRAINT DF_ResourceOperation_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_ResourceOperation PRIMARY KEY(UserID,OperationID)
);';

-- F09 extensions: RecordVersion, BufferMinutes and ResourceOperation.
-- Validate existing definitions without rewriting stored resource rows.
DECLARE @Expected TABLE (
    TableName sysname,ColumnName sysname,TypeName sysname,
    MaxLength smallint NULL,NumberPrecision tinyint NULL,NumberScale tinyint NULL,
    Nullable bit,IdentityColumn bit
);
INSERT @Expected VALUES
    (N'Supplier',N'SupplierID',N'bigint',NULL,NULL,NULL,0,1),
    (N'Supplier',N'Name',N'nvarchar',400,NULL,NULL,0,0),
    (N'Supplier',N'ContactName',N'nvarchar',300,NULL,NULL,1,0),
    (N'Supplier',N'Email',N'varchar',255,NULL,NULL,1,0),
    (N'Supplier',N'Phone',N'varchar',30,NULL,NULL,1,0),
    (N'Supplier',N'ServicesOfferedJSON',N'nvarchar',-1,NULL,NULL,1,0),
    (N'Supplier',N'IsActive',N'bit',NULL,NULL,NULL,0,0),
    (N'Supplier',N'CreatedAt',N'datetime2',NULL,NULL,0,0,0),
    (N'FleetVehicle',N'VehicleID',N'bigint',NULL,NULL,NULL,0,1),
    (N'FleetVehicle',N'VehicleCode',N'varchar',50,NULL,NULL,0,0),
    (N'FleetVehicle',N'PlateNumber',N'varchar',50,NULL,NULL,0,0),
    (N'FleetVehicle',N'HorseCapacity',N'int',NULL,NULL,NULL,0,0),
    (N'FleetVehicle',N'ClimateSystemType',N'varchar',50,NULL,NULL,0,0),
    (N'FleetVehicle',N'OperationalStatus',N'varchar',50,NULL,NULL,0,0),
    (N'FleetVehicle',N'OwnerSupplierID',N'bigint',NULL,NULL,NULL,1,0),
    (N'FleetVehicle',N'MaintenanceFrom',N'datetime2',NULL,NULL,0,1,0),
    (N'FleetVehicle',N'MaintenanceTo',N'datetime2',NULL,NULL,0,1,0),
    (N'FleetVehicle',N'DocumentExpiresAt',N'datetime2',NULL,NULL,0,1,0),
    (N'FleetVehicle',N'AssetNotes',N'nvarchar',4000,NULL,NULL,1,0),
    (N'StallEquipment',N'StallID',N'bigint',NULL,NULL,NULL,0,1),
    (N'StallEquipment',N'StallCode',N'varchar',50,NULL,NULL,0,0),
    (N'StallEquipment',N'CapacitySlots',N'decimal',NULL,5,1,0,0),
    (N'StallEquipment',N'InternalHeightCm',N'decimal',NULL,8,2,0,0),
    (N'StallEquipment',N'InternalWidthCm',N'decimal',NULL,8,2,0,0),
    (N'StallEquipment',N'EquipmentStatus',N'varchar',50,NULL,NULL,0,0),
    (N'StallEquipment',N'OwnerSupplierID',N'bigint',NULL,NULL,NULL,1,0),
    (N'StallEquipment',N'WeightLimitKg',N'decimal',NULL,8,2,1,0),
    (N'StallEquipment',N'MaintenanceFrom',N'datetime2',NULL,NULL,0,1,0),
    (N'StallEquipment',N'MaintenanceTo',N'datetime2',NULL,NULL,0,1,0),
    (N'StallEquipment',N'DocumentExpiresAt',N'datetime2',NULL,NULL,0,1,0),
    (N'StallEquipment',N'AssetNotes',N'nvarchar',4000,NULL,NULL,1,0),
    (N'StallFitPolicy',N'PolicyID',N'bigint',NULL,NULL,NULL,0,1),
    (N'StallFitPolicy',N'PolicyCode',N'varchar',50,NULL,NULL,0,0),
    (N'StallFitPolicy',N'VersionNo',N'int',NULL,NULL,NULL,0,0),
    (N'StallFitPolicy',N'StallID',N'bigint',NULL,NULL,NULL,1,0),
    (N'StallFitPolicy',N'CarrierSupplierID',N'bigint',NULL,NULL,NULL,1,0),
    (N'StallFitPolicy',N'SlotQuantity',N'decimal',NULL,5,1,0,0),
    (N'StallFitPolicy',N'MaxWeightKg',N'decimal',NULL,8,2,1,0),
    (N'StallFitPolicy',N'MaxHeightCm',N'decimal',NULL,8,2,1,0),
    (N'StallFitPolicy',N'MaxWidthCm',N'decimal',NULL,8,2,1,0),
    (N'StallFitPolicy',N'EffectiveFrom',N'datetime2',NULL,NULL,0,0,0),
    (N'StallFitPolicy',N'EffectiveTo',N'datetime2',NULL,NULL,0,1,0),
    (N'StallFitPolicy',N'SourceURL',N'nvarchar',4096,NULL,NULL,1,0),
    (N'StallFitPolicy',N'SourceFileKey',N'nvarchar',2048,NULL,NULL,1,0),
    (N'StallFitPolicy',N'ReviewedBy',N'bigint',NULL,NULL,NULL,1,0),
    (N'StallFitPolicy',N'ReviewedAt',N'datetime2',NULL,NULL,0,1,0),
    (N'StallFitPolicy',N'Status',N'varchar',20,NULL,NULL,0,0),
    (N'Supplier',N'RecordVersion',N'timestamp',8,NULL,NULL,0,0),
    (N'FleetVehicle',N'RecordVersion',N'timestamp',8,NULL,NULL,0,0),
    (N'FleetVehicle',N'BufferMinutes',N'int',NULL,NULL,NULL,1,0),
    (N'StallEquipment',N'RecordVersion',N'timestamp',8,NULL,NULL,0,0),
    (N'StallEquipment',N'BufferMinutes',N'int',NULL,NULL,NULL,1,0),
    (N'StallFitPolicy',N'RecordVersion',N'timestamp',8,NULL,NULL,0,0),
    (N'ResourceOperation',N'UserID',N'bigint',NULL,NULL,NULL,0,0),
    (N'ResourceOperation',N'OperationID',N'uniqueidentifier',NULL,NULL,NULL,0,0),
    (N'ResourceOperation',N'RequestHash',N'varchar',64,NULL,NULL,0,0),
    (N'ResourceOperation',N'ReceiptJSON',N'nvarchar',-1,NULL,NULL,0,0),
    (N'ResourceOperation',N'CreatedAt',N'datetime2',NULL,NULL,0,0,0);
IF EXISTS (SELECT 1 FROM @Expected e
    LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+QUOTENAME(e.TableName))
        AND c.name=e.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.system_type_id)<>e.TypeName
        OR (e.MaxLength IS NOT NULL AND c.max_length<>e.MaxLength)
        OR (e.NumberPrecision IS NOT NULL AND c.precision<>e.NumberPrecision)
        OR (e.NumberScale IS NOT NULL AND c.scale<>e.NumberScale)
        OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityColumn
        OR c.is_computed<>0)
    THROW 51009, 'Existing resource columns differ from the supported contract.', 1;

DECLARE @PrimaryKeys TABLE (TableName sysname,ColumnName sysname);
INSERT @PrimaryKeys VALUES
    (N'Supplier',N'SupplierID'),
    (N'FleetVehicle',N'VehicleID'),
    (N'StallEquipment',N'StallID'),
    (N'StallFitPolicy',N'PolicyID');
IF EXISTS (SELECT 1 FROM @PrimaryKeys p WHERE NOT EXISTS (
    SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic
        ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1
    JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
    WHERE i.object_id=OBJECT_ID(N'dbo.'+QUOTENAME(p.TableName))
        AND i.is_primary_key=1 AND c.name=p.ColumnName
        AND (SELECT COUNT(*) FROM sys.index_columns k
            WHERE k.object_id=i.object_id AND k.index_id=i.index_id
                AND k.key_ordinal>0)=1))
    THROW 51009, 'Resource primary keys differ from the baseline.', 1;

IF EXISTS (SELECT 1 FROM @PrimaryKeys p
    JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+QUOTENAME(p.TableName))
        AND c.name=p.ColumnName
    LEFT JOIN sys.identity_columns i ON i.object_id=c.object_id
        AND i.column_id=c.column_id
    WHERE i.column_id IS NULL OR CONVERT(bigint,i.seed_value)<>1
        OR CONVERT(bigint,i.increment_value)<>1)
    THROW 51009, 'Resource identities must use the original IDENTITY(1,1).', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.ResourceOperation') AND i.is_primary_key=1
        AND i.is_disabled=0 AND
        (SELECT COUNT(*) FROM sys.index_columns k
         WHERE k.object_id=i.object_id AND k.index_id=i.index_id
             AND k.key_ordinal>0)=2
        AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
            ON c.object_id=k.object_id AND c.column_id=k.column_id
            WHERE k.object_id=i.object_id AND k.index_id=i.index_id
                AND k.key_ordinal=1 AND c.name=N'UserID')
        AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
            ON c.object_id=k.object_id AND c.column_id=k.column_id
            WHERE k.object_id=i.object_id AND k.index_id=i.index_id
                AND k.key_ordinal=2 AND c.name=N'OperationID'))
    THROW 51009, 'ResourceOperation requires primary key (UserID,OperationID).', 1;



IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    WHERE d.parent_object_id=OBJECT_ID(N'dbo.Supplier')
        AND COL_NAME(d.parent_object_id,d.parent_column_id)=N'IsActive')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] ADD CONSTRAINT DF_Supplier_IsActive DEFAULT ((1)) FOR [IsActive];';

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    WHERE d.parent_object_id=OBJECT_ID(N'dbo.Supplier')
        AND COL_NAME(d.parent_object_id,d.parent_column_id)=N'CreatedAt')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] ADD CONSTRAINT DF_Supplier_CreatedAt DEFAULT (sysutcdatetime()) FOR [CreatedAt];';

-- Original unique keys, checks and resource relationships.

IF OBJECT_ID(N'dbo.UQ_FleetVehicle_PlateNumber',N'UQ') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FleetVehicle] ADD  CONSTRAINT [UQ_FleetVehicle_PlateNumber] UNIQUE NONCLUSTERED 
(
    [PlateNumber] ASC
);';

IF OBJECT_ID(N'dbo.UQ_FleetVehicle_VehicleCode',N'UQ') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FleetVehicle] ADD  CONSTRAINT [UQ_FleetVehicle_VehicleCode] UNIQUE NONCLUSTERED 
(
    [VehicleCode] ASC
);';

IF OBJECT_ID(N'dbo.UQ_StallEquipment_StallCode',N'UQ') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallEquipment] ADD  CONSTRAINT [UQ_StallEquipment_StallCode] UNIQUE NONCLUSTERED 
(
    [StallCode] ASC
);';

IF OBJECT_ID(N'dbo.UQ_StallFitPolicy_Version',N'UQ') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy] ADD  CONSTRAINT [UQ_StallFitPolicy_Version] UNIQUE NONCLUSTERED 
(
    [PolicyCode] ASC,
    [VersionNo] ASC
);';

IF OBJECT_ID(N'dbo.FK_Figma_FleetVehicle_OwnerSupplierID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID] FOREIGN KEY([OwnerSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK CHECK CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID];';

IF OBJECT_ID(N'dbo.FK_StallAssignment_StallID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallAssignment]  WITH CHECK ADD  CONSTRAINT [FK_StallAssignment_StallID] FOREIGN KEY([StallID])
REFERENCES [dbo].[StallEquipment] ([StallID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK CHECK CONSTRAINT [FK_StallAssignment_StallID];';

IF OBJECT_ID(N'dbo.FK_Figma_StallEquipment_OwnerSupplierID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID] FOREIGN KEY([OwnerSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK CHECK CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID];';

IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_CarrierSupplierID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID] FOREIGN KEY([CarrierSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID];';

IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_ReviewedBy',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy] FOREIGN KEY([ReviewedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_StallID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_StallID] FOREIGN KEY([StallID])
REFERENCES [dbo].[StallEquipment] ([StallID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [FK_Figma_StallFitPolicy_StallID];';

IF OBJECT_ID(N'dbo.FK_TripLeg_VehicleID',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [FK_TripLeg_VehicleID] FOREIGN KEY([VehicleID])
REFERENCES [dbo].[FleetVehicle] ([VehicleID]);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK CHECK CONSTRAINT [FK_TripLeg_VehicleID];';

IF OBJECT_ID(N'dbo.CK_Figma_FleetVehicle_MaintenanceDates',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates] CHECK  (([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom]));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK CHECK CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates];';

IF OBJECT_ID(N'dbo.CK_FleetVehicle_HorseCapacity',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [CK_FleetVehicle_HorseCapacity] CHECK  (([HorseCapacity]>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK CHECK CONSTRAINT [CK_FleetVehicle_HorseCapacity];';

IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_MaintenanceDates',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates] CHECK  (([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom]));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK CHECK CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates];';

IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_WeightLimitKg',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg] CHECK  (([WeightLimitKg]>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK CHECK CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg];';

IF OBJECT_ID(N'dbo.CK_StallEquipment_CapacitySlots',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_StallEquipment_CapacitySlots] CHECK  (([CapacitySlots]>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK CHECK CONSTRAINT [CK_StallEquipment_CapacitySlots];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Approval',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Approval] CHECK  (([Status]<>''APPROVED'' OR [ReviewedBy] IS NOT NULL AND [ReviewedAt] IS NOT NULL AND [MaxWeightKg] IS NOT NULL AND [MaxHeightCm] IS NOT NULL AND [MaxWidthCm] IS NOT NULL AND ([SourceURL] IS NOT NULL OR [SourceFileKey] IS NOT NULL)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Approval];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Dates',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Dates] CHECK  (([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom]));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Dates];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Dimensions',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Dimensions] CHECK  (([MaxWeightKg]>(0) AND [MaxHeightCm]>(0) AND [MaxWidthCm]>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Dimensions];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Slots',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Slots] CHECK  (([SlotQuantity]=(3.0) OR [SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Slots];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Status',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Status] CHECK  (([Status]=''INACTIVE'' OR [Status]=''APPROVED'' OR [Status]=''DRAFT''));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Status];';

IF OBJECT_ID(N'dbo.CK_StallFitPolicy_Version',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Version] CHECK  (([VersionNo]>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK CHECK CONSTRAINT [CK_StallFitPolicy_Version];';

IF OBJECT_ID(N'dbo.CK_Supplier_Name',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[Supplier]  WITH CHECK ADD  CONSTRAINT [CK_Supplier_Name] CHECK  ((len(ltrim(rtrim([Name])))>(0)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK CHECK CONSTRAINT [CK_Supplier_Name];';

IF OBJECT_ID(N'dbo.CK_Supplier_ServicesJSON',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[Supplier]  WITH CHECK ADD  CONSTRAINT [CK_Supplier_ServicesJSON] CHECK  (([ServicesOfferedJSON] IS NULL OR isjson([ServicesOfferedJSON])=(1)));';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK CHECK CONSTRAINT [CK_Supplier_ServicesJSON];';

IF OBJECT_ID(N'dbo.CK_Resource_FleetVehicle_Buffer',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.FleetVehicle WITH CHECK ADD CONSTRAINT CK_Resource_FleetVehicle_Buffer CHECK (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK CHECK CONSTRAINT [CK_Resource_FleetVehicle_Buffer];';

IF OBJECT_ID(N'dbo.CK_Resource_StallEquipment_Buffer',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.StallEquipment WITH CHECK ADD CONSTRAINT CK_Resource_StallEquipment_Buffer CHECK (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK CHECK CONSTRAINT [CK_Resource_StallEquipment_Buffer];';

IF OBJECT_ID(N'dbo.FK_ResourceOperation_User',N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.ResourceOperation WITH CHECK ADD CONSTRAINT FK_ResourceOperation_User FOREIGN KEY(UserID) REFERENCES dbo.[User](UserID);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[ResourceOperation] WITH CHECK CHECK CONSTRAINT [FK_ResourceOperation_User];';

IF OBJECT_ID(N'dbo.CK_ResourceOperation_JSON',N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.ResourceOperation WITH CHECK ADD CONSTRAINT CK_ResourceOperation_JSON CHECK(ISJSON(ReceiptJSON)=1);';

EXEC sys.sp_executesql N'ALTER TABLE dbo.[ResourceOperation] WITH CHECK CHECK CONSTRAINT [CK_ResourceOperation_JSON];';

-- F09 scheduling indexes; operational tables remain owned by their flows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.TripLeg') AND name=N'IX_Resource_VehicleWindow')
EXEC sys.sp_executesql N'CREATE INDEX IX_Resource_VehicleWindow ON dbo.TripLeg
    (VehicleID,PlannedDeparture,PlannedArrival) INCLUDE(PlanID,LegStatus)
    WHERE VehicleID IS NOT NULL;';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.StallAssignment') AND name=N'IX_Resource_StallWindow')
EXEC sys.sp_executesql N'CREATE INDEX IX_Resource_StallWindow ON dbo.StallAssignment
    (StallID,AssignedFrom,AssignedTo) INCLUDE(LegID,Status);';

-- TR_Resource_VehicleSchedule: F09 / F10.
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Resource_VehicleSchedule
ON dbo.TripLeg AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE VehicleID IS NOT NULL)
        AND NOT EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallAssignment a
            ON a.LegID=i.LegID) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource=''EquiTransit:resources'',
        @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=5000;
    IF @r<0 THROW 51001, ''Resource scheduling busy.'', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.FleetVehicle v
        WITH (UPDLOCK,HOLDLOCK) ON v.VehicleID=i.VehicleID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=v.OwnerSupplierID
        WHERE i.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        (i.TransportMode<>''ROAD'' OR v.OperationalStatus<>''ACTIVE''
         OR v.HorseCapacity<1 OR v.BufferMinutes IS NULL
         OR (v.OwnerSupplierID IS NOT NULL AND s.IsActive<>1)
         OR i.PlannedDeparture IS NULL OR i.PlannedArrival IS NULL
         OR i.PlannedDeparture>=i.PlannedArrival
         OR v.DocumentExpiresAt IS NULL
         OR v.DocumentExpiresAt<=DATEADD(minute,v.BufferMinutes,
             i.PlannedArrival)
         OR (v.MaintenanceFrom IS NOT NULL AND
             v.MaintenanceFrom<DATEADD(minute,v.BufferMinutes,i.PlannedArrival)
             AND (v.MaintenanceTo IS NULL OR
                 DATEADD(minute,v.BufferMinutes,v.MaintenanceTo)
                    >i.PlannedDeparture))))
        THROW 51002, ''Vehicle mode, evidence or availability invalid.'', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripLeg b
        WITH (UPDLOCK,HOLDLOCK) ON a.VehicleID=b.VehicleID AND a.LegID<>b.LegID
        JOIN dbo.FleetVehicle v ON v.VehicleID=a.VehicleID
        JOIN dbo.TransportPlan pa ON pa.PlanID=a.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=b.PlanID
        WHERE a.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        b.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        pa.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        pb.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        (b.PlannedDeparture IS NULL OR b.PlannedArrival IS NULL OR
         b.PlannedDeparture>=b.PlannedArrival OR
         (a.PlannedDeparture<DATEADD(minute,v.BufferMinutes,b.PlannedArrival)
          AND b.PlannedDeparture<DATEADD(minute,v.BufferMinutes,
              a.PlannedArrival))))
        THROW 51003, ''Vehicle has an overlapping assignment.'', 1;
    UPDATE a SET Status=a.Status FROM dbo.StallAssignment a
        JOIN inserted i ON i.LegID=a.LegID;
END;';

-- TR_Resource_StallSchedule: F09 / F10.
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Resource_StallSchedule
ON dbo.StallAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource=''EquiTransit:resources'',
        @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=5000;
    IF @r<0 THROW 51001, ''Resource scheduling busy.'', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallEquipment e
        WITH (UPDLOCK,HOLDLOCK) ON e.StallID=i.StallID
        JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=e.OwnerSupplierID
        WHERE i.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        l.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        (l.TransportMode<>''AIR'' OR e.EquipmentStatus<>''ACTIVE''
         OR e.BufferMinutes IS NULL OR e.CapacitySlots<i.SlotQuantity
         OR (e.OwnerSupplierID IS NOT NULL AND s.IsActive<>1)
         OR i.AssignedFrom IS NULL OR i.AssignedTo IS NULL
         OR i.AssignedFrom>=i.AssignedTo
         OR e.DocumentExpiresAt IS NULL
         OR e.DocumentExpiresAt<=DATEADD(minute,e.BufferMinutes,i.AssignedTo)
         OR (e.MaintenanceFrom IS NOT NULL AND
             e.MaintenanceFrom<DATEADD(minute,e.BufferMinutes,i.AssignedTo)
             AND (e.MaintenanceTo IS NULL OR
                 DATEADD(minute,e.BufferMinutes,e.MaintenanceTo)
                    >i.AssignedFrom))))
        THROW 51004, ''Stall mode, capacity or availability invalid.'', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.StallAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StallID=b.StallID
        AND a.StallAssignmentID<>b.StallAssignmentID
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        b.Status NOT IN (''CANCELLED'',''CANCELED'',''INACTIVE'') AND
        la.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        lb.LegStatus NOT IN (''CANCELLED'',''CANCELED'',''REJECTED'') AND
        pa.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        pb.PlanStatus NOT IN
            (''CANCELLED'',''CANCELED'',''SUPERSEDED'',''REJECTED'') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         b.AssignedFrom>=b.AssignedTo OR
         (a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo))))
        THROW 51005, ''Physical stall has an overlapping assignment.'', 1;
END;';

-- TR_Resource_PlanActivation: F09 / F10.
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
END;';

COMMIT TRANSACTION;
PRINT N'Migration 007 completed: resource catalogues, fit policies and schedule guards.';
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
