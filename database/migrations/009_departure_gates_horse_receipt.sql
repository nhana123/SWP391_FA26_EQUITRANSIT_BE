-- F11: departure gates and physical horse receipt.
-- After baseline and 001-008. Extracted from EquiTransit.sql.
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
    THROW 51011, 'Run migration 009 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51011, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51011, 'VIEW DEFINITION is required to verify the F11 schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51011, 'Another schema migration holds the lock.', 1;

-- Shared gate/authorization and actual-time columns already belong to 008.
DECLARE @Dependencies TABLE (TableName sysname, ColumnName sysname);
INSERT @Dependencies VALUES
    (N'TransportPlan', N'PlanID'), (N'TransportPlan', N'OrderID'),
    (N'TransportPlan', N'RevisionID'), (N'TransportPlan', N'PlanStatus'),
    (N'TransportPlan', N'FinancialGateStatus'),
    (N'TransportPlan', N'OperationalGateStatus'),
    (N'TransportPlan', N'DocGateStatus'),
    (N'TransportPlan', N'GatesCheckedAt'),
    (N'TransportPlan', N'DepartureAuthorizedBy'),
    (N'TransportPlan', N'DepartureAuthorizedAt'),
    (N'TripLeg', N'LegID'), (N'TripLeg', N'PlanID'),
    (N'TripLeg', N'ActualDeparture'), (N'TripLeg', N'ActualArrival'),
    (N'StaffProfile', N'StaffID'), (N'PlanOperation', N'OperationID');
IF EXISTS (SELECT 1 FROM @Dependencies d
    WHERE OBJECT_ID(N'dbo.' + QUOTENAME(d.TableName), N'U') IS NULL
        OR COL_LENGTH(N'dbo.' + QUOTENAME(d.TableName), d.ColumnName) IS NULL)
    THROW 51011, 'Apply the application baseline and migrations 001-008 first.', 1;

-- 1. HorseReceipt: D02/D02-RECEIPT / FR-11.
IF OBJECT_ID(N'dbo.HorseReceipt', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[HorseReceipt](
	[ReceiptID] [bigint] IDENTITY(1,1) NOT NULL,
	[PlanID] [bigint] NOT NULL,
	[ReceivedBy] [bigint] NOT NULL,
	[ObservedMicrochip] [varchar](50) NULL,
	[ConditionNotes] [nvarchar](2000) NULL,
	[ReceiptStatus] [varchar](50) NOT NULL,
	[ReceivedAt] [datetime2](0) NOT NULL,
	[LegID] [bigint] NULL,
	[ObservedPassportNumber] [varchar](50) NULL,
	[IdentityCheckResult] [varchar](20) NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
	[LocationDescription] [nvarchar](500) NULL,
 CONSTRAINT [PK_HorseReceipt] PRIMARY KEY CLUSTERED 
(
	[ReceiptID] ASC
)
);';

-- Preserve source column types, nullability and identity on restored databases.
DECLARE @Columns TABLE (
    ColumnName sysname, TypeName sysname, MaxLength smallint,
    Scale tinyint, Nullable bit, IdentityColumn bit);
INSERT @Columns VALUES
    (N'ReceiptID', N'bigint', 8, 0, 0, 1),
    (N'PlanID', N'bigint', 8, 0, 0, 0),
    (N'ReceivedBy', N'bigint', 8, 0, 0, 0),
    (N'ObservedMicrochip', N'varchar', 50, 0, 1, 0),
    (N'ConditionNotes', N'nvarchar', 4000, 0, 1, 0),
    (N'ReceiptStatus', N'varchar', 50, 0, 0, 0),
    (N'ReceivedAt', N'datetime2', 6, 0, 0, 0),
    (N'LegID', N'bigint', 8, 0, 1, 0),
    (N'ObservedPassportNumber', N'varchar', 50, 0, 1, 0),
    (N'IdentityCheckResult', N'varchar', 20, 0, 1, 0),
    (N'EvidenceFileKey', N'nvarchar', 2048, 0, 1, 0),
    (N'LocationDescription', N'nvarchar', 1000, 0, 1, 0);
IF EXISTS (SELECT 1 FROM @Columns e WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns c
    WHERE c.object_id = OBJECT_ID(N'dbo.HorseReceipt') AND c.name=e.ColumnName
        AND TYPE_NAME(c.system_type_id)=e.TypeName
        AND c.max_length=e.MaxLength AND c.scale=e.Scale
        AND c.is_nullable=e.Nullable AND c.is_identity=e.IdentityColumn
        AND c.is_computed=0))
    THROW 51011, 'HorseReceipt differs from the source schema; review drift.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints k
    JOIN sys.index_columns ic ON ic.object_id=k.parent_object_id
        AND ic.index_id=k.unique_index_id AND ic.key_ordinal=1
    JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
    WHERE k.parent_object_id=OBJECT_ID(N'dbo.HorseReceipt') AND k.type='PK'
        AND c.name=N'ReceiptID' AND NOT EXISTS (
            SELECT 1 FROM sys.index_columns more
            WHERE more.object_id=ic.object_id AND more.index_id=ic.index_id
                AND more.key_ordinal>1))
    THROW 51011, 'HorseReceipt must have ReceiptID as its primary key.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.HorseReceipt') AND name=N'IX_HorseReceipt_PlanID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_HorseReceipt_PlanID] ON [dbo].[HorseReceipt]
(
	[PlanID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.HorseReceipt') AND name=N'IX_HorseReceipt_ReceivedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_HorseReceipt_ReceivedBy] ON [dbo].[HorseReceipt]
(
	[ReceivedBy] ASC
);';

IF OBJECT_ID(N'dbo.FK_Figma_HorseReceipt_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseReceipt_LegID] FOREIGN KEY([LegID], [PlanID])
REFERENCES [dbo].[TripLeg] ([LegID], [PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseReceipt WITH CHECK CHECK CONSTRAINT [FK_Figma_HorseReceipt_LegID];';

IF OBJECT_ID(N'dbo.FK_HorseReceipt_PlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_HorseReceipt_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseReceipt WITH CHECK CHECK CONSTRAINT [FK_HorseReceipt_PlanID];';

IF OBJECT_ID(N'dbo.FK_HorseReceipt_ReceivedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_HorseReceipt_ReceivedBy] FOREIGN KEY([ReceivedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseReceipt WITH CHECK CHECK CONSTRAINT [FK_HorseReceipt_ReceivedBy];';

IF OBJECT_ID(N'dbo.CK_Figma_HorseReceipt_Identity', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseReceipt_Identity] CHECK  (([IdentityCheckResult]=''MISMATCHED'' OR [IdentityCheckResult]=''MATCHED'' OR [IdentityCheckResult]=''PENDING''));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseReceipt WITH CHECK CHECK CONSTRAINT [CK_Figma_HorseReceipt_Identity];';

-- Verify each source foreign key still binds the intended parent columns.
DECLARE @ForeignKeys TABLE (
    ConstraintName sysname, Ordinal int, ColumnName sysname,
    ParentTable sysname, ParentColumn sysname);
INSERT @ForeignKeys VALUES
    (N'FK_Figma_HorseReceipt_LegID', 1, N'LegID', N'TripLeg', N'LegID'),
    (N'FK_Figma_HorseReceipt_LegID', 2, N'PlanID', N'TripLeg', N'PlanID'),
    (N'FK_HorseReceipt_PlanID', 1, N'PlanID', N'TransportPlan', N'PlanID'),
    (N'FK_HorseReceipt_ReceivedBy', 1, N'ReceivedBy', N'StaffProfile', N'StaffID');
IF EXISTS (SELECT 1 FROM @ForeignKeys e WHERE NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys f
    JOIN sys.foreign_key_columns c ON c.constraint_object_id=f.object_id
    WHERE f.object_id=OBJECT_ID(N'dbo.' + QUOTENAME(e.ConstraintName), N'F')
        AND f.parent_object_id=OBJECT_ID(N'dbo.HorseReceipt')
        AND f.referenced_object_id=OBJECT_ID(N'dbo.'+QUOTENAME(e.ParentTable))
        AND c.constraint_column_id=e.Ordinal
        AND COL_NAME(c.parent_object_id,c.parent_column_id)=e.ColumnName
        AND COL_NAME(c.referenced_object_id,c.referenced_column_id)=e.ParentColumn
        AND f.delete_referential_action=0 AND f.update_referential_action=0
        AND f.is_disabled=0 AND f.is_not_trusted=0))
    THROW 51011, 'HorseReceipt foreign keys differ from the source schema.', 1;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
