-- F14: trip handover and internal actual costs.
-- After baseline and migrations 001-015.
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
    THROW 51016, 'Run migration 016 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51016, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51016, 'VIEW DEFINITION is required to verify the F14 schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51016, 'Another schema migration holds the lock.', 1;

IF COL_LENGTH(N'dbo.TripLeg', N'LegID') IS NULL
    OR COL_LENGTH(N'dbo.TripLeg', N'PlanID') IS NULL
    OR COL_LENGTH(N'dbo.TransportPlan', N'PlanID') IS NULL
    OR COL_LENGTH(N'dbo.StaffProfile', N'StaffID') IS NULL
    OR COL_LENGTH(N'dbo.Customer', N'CustomerID') IS NULL
    THROW 51016, 'Apply the application baseline and preceding migrations first.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.TripLeg', N'U')
        AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=0
        AND (SELECT COUNT(*) FROM sys.index_columns c
            WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                AND c.key_ordinal>0)=2
        AND EXISTS (SELECT 1 FROM sys.index_columns c
            WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                AND c.key_ordinal=1
                AND COL_NAME(c.object_id,c.column_id)=N'LegID')
        AND EXISTS (SELECT 1 FROM sys.index_columns c
            WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                AND c.key_ordinal=2
                AND COL_NAME(c.object_id,c.column_id)=N'PlanID'))
    THROW 51016, 'TripLeg requires its existing unique key (LegID, PlanID).', 1;

-- 1. TripHandover: D04/C16 / FR-14.
IF OBJECT_ID(N'dbo.TripHandover', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[TripHandover](
	[HandoverID] [bigint] IDENTITY(1,1) NOT NULL,
	[LegID] [bigint] NOT NULL,
	[RecordedBy] [bigint] NOT NULL,
	[HandoverType] [varchar](50) NOT NULL,
	[HandoverTime] [datetime2](0) NOT NULL,
	[ReceivedByName] [nvarchar](300) NOT NULL,
	[ReceiverAuthority] [varchar](50) NULL,
	[ReceiverSignatureFileKey] [nvarchar](1024) NULL,
	[HorseConditionStatus] [varchar](50) NOT NULL,
	[Outcome] [varchar](50) NOT NULL,
	[ExceptionNotes] [nvarchar](2000) NULL,
	[PhotoFileKey] [nvarchar](1024) NULL,
	[CustomerConfirmedAt] [datetime2](0) NULL,
	[LocationDescription] [nvarchar](500) NULL,
	[CustomerDecision] [varchar](20) NULL,
	[CustomerFeedback] [nvarchar](2000) NULL,
	[CustomerRespondedAt] [datetime2](0) NULL,
	[RespondedByCustomerID] [bigint] NULL,
	[EvidenceFilesJSON] [nvarchar](max) NULL,
 CONSTRAINT [PK_TripHandover] PRIMARY KEY CLUSTERED 
(
	[HandoverID] ASC
)
);';

IF COL_LENGTH(N'dbo.TripHandover', N'LocationDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [LocationDescription] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.TripHandover', N'CustomerDecision') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerDecision] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.TripHandover', N'CustomerFeedback') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerFeedback] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.TripHandover', N'CustomerRespondedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerRespondedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.TripHandover', N'RespondedByCustomerID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [RespondedByCustomerID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TripHandover', N'EvidenceFilesJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [EvidenceFilesJSON] NVARCHAR(max) NULL;';

IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'HandoverID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
    THROW 51016, 'TripHandover.HandoverID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'LegID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'TripHandover.LegID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'RecordedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'TripHandover.RecordedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'HandoverType' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51016, 'TripHandover.HandoverType differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'HandoverTime' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51016, 'TripHandover.HandoverTime differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'ReceivedByName' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=600)
    THROW 51016, 'TripHandover.ReceivedByName differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'ReceiverAuthority' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51016, 'TripHandover.ReceiverAuthority differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'ReceiverSignatureFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
    THROW 51016, 'TripHandover.ReceiverSignatureFileKey differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'HorseConditionStatus' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51016, 'TripHandover.HorseConditionStatus differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'Outcome' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51016, 'TripHandover.Outcome differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'ExceptionNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51016, 'TripHandover.ExceptionNotes differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'PhotoFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
    THROW 51016, 'TripHandover.PhotoFileKey differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'CustomerConfirmedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51016, 'TripHandover.CustomerConfirmedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'LocationDescription' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=1000)
    THROW 51016, 'TripHandover.LocationDescription differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'CustomerDecision' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=20)
    THROW 51016, 'TripHandover.CustomerDecision differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'CustomerFeedback' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51016, 'TripHandover.CustomerFeedback differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'CustomerRespondedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51016, 'TripHandover.CustomerRespondedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'RespondedByCustomerID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'TripHandover.RespondedByCustomerID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.TripHandover') AND c.name=N'EvidenceFilesJSON' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=-1)
    THROW 51016, 'TripHandover.EvidenceFilesJSON differs from the source schema.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints k
    JOIN sys.index_columns c ON c.object_id=k.parent_object_id
        AND c.index_id=k.unique_index_id AND c.key_ordinal=1
    WHERE k.parent_object_id=OBJECT_ID(N'dbo.TripHandover') AND k.type='PK'
        AND COL_NAME(c.object_id,c.column_id)=N'HandoverID'
        AND NOT EXISTS (SELECT 1 FROM sys.index_columns x
            WHERE x.object_id=c.object_id AND x.index_id=c.index_id
                AND x.key_ordinal>1))
    THROW 51016, 'TripHandover requires its source primary key (HandoverID).', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TripHandover') AND name=N'IX_TripHandover_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TripHandover_LegID] ON [dbo].[TripHandover]
(
	[LegID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TripHandover') AND name=N'IX_TripHandover_RecordedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_TripHandover_RecordedBy] ON [dbo].[TripHandover]
(
	[RecordedBy] ASC
);';
IF OBJECT_ID(N'dbo.FK_Figma_TripHandover_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID] FOREIGN KEY([RespondedByCustomerID])
REFERENCES [dbo].[Customer] ([CustomerID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID];';
IF OBJECT_ID(N'dbo.FK_TripHandover_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_TripHandover_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_TripHandover_LegID];';
IF OBJECT_ID(N'dbo.FK_TripHandover_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_TripHandover_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_TripHandover_RecordedBy];';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripHandover_CustomerDecision] CHECK  (([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED''));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripHandover_CustomerDecision];';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON] CHECK  (([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON];';

-- 2. ActualCost: M11/M13/F04 / FR-14.
IF OBJECT_ID(N'dbo.ActualCost', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[ActualCost](
	[CostID] [bigint] IDENTITY(1,1) NOT NULL,
	[PlanID] [bigint] NOT NULL,
	[LegID] [bigint] NULL,
	[CostCode] [varchar](50) NOT NULL,
	[Amount] [decimal](18, 2) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[IncurredAt] [datetime2](0) NOT NULL,
	[Description] [nvarchar](1000) NULL,
	[RecordedBy] [bigint] NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
 CONSTRAINT [PK_ActualCost] PRIMARY KEY CLUSTERED 
(
	[CostID] ASC
)
);';

IF COL_LENGTH(N'dbo.ActualCost', N'Description') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Description] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [RecordedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'CostID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
    THROW 51016, 'ActualCost.CostID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'PlanID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'ActualCost.PlanID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'LegID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'ActualCost.LegID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'CostCode' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51016, 'ActualCost.CostCode differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'Amount' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=18 AND c.scale=2)
    THROW 51016, 'ActualCost.Amount differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'Currency' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=3)
    THROW 51016, 'ActualCost.Currency differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'IncurredAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51016, 'ActualCost.IncurredAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'Description' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2000)
    THROW 51016, 'ActualCost.Description differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'RecordedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51016, 'ActualCost.RecordedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.ActualCost') AND c.name=N'EvidenceFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
    THROW 51016, 'ActualCost.EvidenceFileKey differs from the source schema.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints k
    JOIN sys.index_columns c ON c.object_id=k.parent_object_id
        AND c.index_id=k.unique_index_id AND c.key_ordinal=1
    WHERE k.parent_object_id=OBJECT_ID(N'dbo.ActualCost') AND k.type='PK'
        AND COL_NAME(c.object_id,c.column_id)=N'CostID'
        AND NOT EXISTS (SELECT 1 FROM sys.index_columns x
            WHERE x.object_id=c.object_id AND x.index_id=c.index_id
                AND x.key_ordinal>1))
    THROW 51016, 'ActualCost requires its source primary key (CostID).', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ActualCost') AND name=N'IX_ActualCost_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_ActualCost_LegID] ON [dbo].[ActualCost]
(
	[LegID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ActualCost') AND name=N'IX_ActualCost_PlanID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_ActualCost_PlanID] ON [dbo].[ActualCost]
(
	[PlanID] ASC
);';
IF OBJECT_ID(N'dbo.FK_ActualCost_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_ActualCost_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_ActualCost_LegID];';
IF OBJECT_ID(N'dbo.FK_ActualCost_PlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_ActualCost_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_ActualCost_PlanID];';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ActualCost_LegID] FOREIGN KEY([LegID], [PlanID])
REFERENCES [dbo].[TripLeg] ([LegID], [PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_Figma_ActualCost_LegID];';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ActualCost_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_Figma_ActualCost_RecordedBy];';
IF OBJECT_ID(N'dbo.CK_ActualCost_Amount', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [CK_ActualCost_Amount] CHECK  (([Amount]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [CK_ActualCost_Amount];';

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
