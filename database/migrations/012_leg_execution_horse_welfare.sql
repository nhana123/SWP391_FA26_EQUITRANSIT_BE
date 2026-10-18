-- F12: leg execution milestones and horse welfare logs.
-- After baseline and migrations 001-011.
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
    THROW 51012, 'Run migration 012 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51012, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51012, 'VIEW DEFINITION is required to verify the F12 schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51012, 'Another schema migration holds the lock.', 1;
IF COL_LENGTH(N'dbo.TripLeg', N'LegID') IS NULL
    OR COL_LENGTH(N'dbo.TripLeg', N'ActualDeparture') IS NULL
    OR COL_LENGTH(N'dbo.TripLeg', N'ActualArrival') IS NULL
    OR COL_LENGTH(N'dbo.StaffProfile', N'StaffID') IS NULL
    THROW 51012, 'Apply the application baseline and preceding migrations first.', 1;

-- 1. LegMilestoneLog: D02/F04/C15 / FR-12.
IF OBJECT_ID(N'dbo.LegMilestoneLog', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[LegMilestoneLog](
	[MilestoneID] [bigint] IDENTITY(1,1) NOT NULL,
	[LegID] [bigint] NOT NULL,
	[ReportedBy] [bigint] NOT NULL,
	[MilestoneType] [varchar](50) NOT NULL,
	[OccurredAt] [datetime2](0) NOT NULL,
	[RecordedAt] [datetime2](0) NOT NULL,
	[Latitude] [decimal](9, 6) NULL,
	[Longitude] [decimal](9, 6) NULL,
	[Notes] [nvarchar](2000) NULL,
	[SourceCode] [varchar](30) NULL,
	[SourceReference] [nvarchar](500) NULL,
	[LocationDescription] [nvarchar](500) NULL,
	[EstimatedArrivalAt] [datetime2](0) NULL,
	[DelayReason] [nvarchar](1000) NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
 CONSTRAINT [PK_LegMilestoneLog] PRIMARY KEY CLUSTERED 
(
	[MilestoneID] ASC
)
);';

    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'MilestoneID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
        THROW 51012, 'LegMilestoneLog.MilestoneID differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'LegID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
        THROW 51012, 'LegMilestoneLog.LegID differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'ReportedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
        THROW 51012, 'LegMilestoneLog.ReportedBy differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'MilestoneType' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
        THROW 51012, 'LegMilestoneLog.MilestoneType differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'OccurredAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
        THROW 51012, 'LegMilestoneLog.OccurredAt differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'RecordedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
        THROW 51012, 'LegMilestoneLog.RecordedAt differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'Latitude' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=9 AND c.scale=6)
        THROW 51012, 'LegMilestoneLog.Latitude differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'Longitude' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=9 AND c.scale=6)
        THROW 51012, 'LegMilestoneLog.Longitude differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'Notes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
        THROW 51012, 'LegMilestoneLog.Notes differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'SourceCode' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=30)
        THROW 51012, 'LegMilestoneLog.SourceCode differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'SourceReference' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=1000)
        THROW 51012, 'LegMilestoneLog.SourceReference differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'LocationDescription' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=1000)
        THROW 51012, 'LegMilestoneLog.LocationDescription differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'EstimatedArrivalAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
        THROW 51012, 'LegMilestoneLog.EstimatedArrivalAt differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'DelayReason' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2000)
        THROW 51012, 'LegMilestoneLog.DelayReason differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND c.name=N'EvidenceFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
        THROW 51012, 'LegMilestoneLog.EvidenceFileKey differs from the source schema.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND name=N'IX_LegMilestoneLog_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_LegMilestoneLog_LegID] ON [dbo].[LegMilestoneLog]
(
	[LegID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LegMilestoneLog') AND name=N'IX_LegMilestoneLog_ReportedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_LegMilestoneLog_ReportedBy] ON [dbo].[LegMilestoneLog]
(
	[ReportedBy] ASC
);';
IF OBJECT_ID(N'dbo.DF_LegMilestoneLog_OccurredAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog] ADD  CONSTRAINT [DF_LegMilestoneLog_OccurredAt]  DEFAULT (sysutcdatetime()) FOR [OccurredAt];';
IF OBJECT_ID(N'dbo.DF_LegMilestoneLog_RecordedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog] ADD  CONSTRAINT [DF_LegMilestoneLog_RecordedAt]  DEFAULT (sysutcdatetime()) FOR [RecordedAt];';
IF OBJECT_ID(N'dbo.FK_LegMilestoneLog_LegID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [FK_LegMilestoneLog_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [FK_LegMilestoneLog_LegID];';
IF OBJECT_ID(N'dbo.FK_LegMilestoneLog_ReportedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [FK_LegMilestoneLog_ReportedBy] FOREIGN KEY([ReportedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [FK_LegMilestoneLog_ReportedBy];';
IF OBJECT_ID(N'dbo.CK_LegMilestoneLog_Latitude') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [CK_LegMilestoneLog_Latitude] CHECK  (([Latitude]>=(-90) AND [Latitude]<=(90)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [CK_LegMilestoneLog_Latitude];';
IF OBJECT_ID(N'dbo.CK_LegMilestoneLog_Longitude') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [CK_LegMilestoneLog_Longitude] CHECK  (([Longitude]>=(-180) AND [Longitude]<=(180)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [CK_LegMilestoneLog_Longitude];';

-- 2. HorseWelfareLog: D03 / FR-12.
IF OBJECT_ID(N'dbo.HorseWelfareLog', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[HorseWelfareLog](
	[WelfareLogID] [bigint] IDENTITY(1,1) NOT NULL,
	[LegID] [bigint] NOT NULL,
	[RecordedBy] [bigint] NOT NULL,
	[CheckTime] [datetime2](0) NOT NULL,
	[HealthStatus] [varchar](50) NOT NULL,
	[TemperatureCelsius] [decimal](8, 2) NULL,
	[WaterIntakeLiters] [decimal](8, 2) NULL,
	[BehaviorNotes] [nvarchar](2000) NULL,
	[PhotoFileKey] [nvarchar](1024) NULL,
	[FeedAmountKg] [decimal](8, 2) NULL,
	[CareNotes] [nvarchar](2000) NULL,
	[InstructionFileKey] [nvarchar](1024) NULL,
	[SupersedesLogID] [bigint] NULL,
 CONSTRAINT [PK_HorseWelfareLog] PRIMARY KEY CLUSTERED 
(
	[WelfareLogID] ASC
)
);';

    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'WelfareLogID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
        THROW 51012, 'HorseWelfareLog.WelfareLogID differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'LegID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
        THROW 51012, 'HorseWelfareLog.LegID differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'RecordedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
        THROW 51012, 'HorseWelfareLog.RecordedBy differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'CheckTime' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
        THROW 51012, 'HorseWelfareLog.CheckTime differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'HealthStatus' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
        THROW 51012, 'HorseWelfareLog.HealthStatus differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'TemperatureCelsius' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=8 AND c.scale=2)
        THROW 51012, 'HorseWelfareLog.TemperatureCelsius differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'WaterIntakeLiters' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=8 AND c.scale=2)
        THROW 51012, 'HorseWelfareLog.WaterIntakeLiters differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'BehaviorNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
        THROW 51012, 'HorseWelfareLog.BehaviorNotes differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'PhotoFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
        THROW 51012, 'HorseWelfareLog.PhotoFileKey differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'FeedAmountKg' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=8 AND c.scale=2)
        THROW 51012, 'HorseWelfareLog.FeedAmountKg differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'CareNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
        THROW 51012, 'HorseWelfareLog.CareNotes differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'InstructionFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
        THROW 51012, 'HorseWelfareLog.InstructionFileKey differs from the source schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND c.name=N'SupersedesLogID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
        THROW 51012, 'HorseWelfareLog.SupersedesLogID differs from the source schema.', 1;

IF OBJECT_ID(N'dbo.UQ_Figma_HorseWelfareLog_Parent') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog] ADD  CONSTRAINT [UQ_Figma_HorseWelfareLog_Parent] UNIQUE NONCLUSTERED 
(
	[WelfareLogID] ASC,
	[LegID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND name=N'IX_HorseWelfareLog_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_HorseWelfareLog_LegID] ON [dbo].[HorseWelfareLog]
(
	[LegID] ASC
);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND name=N'IX_HorseWelfareLog_RecordedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_HorseWelfareLog_RecordedBy] ON [dbo].[HorseWelfareLog]
(
	[RecordedBy] ASC
);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseWelfareLog_SupersedesLogID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID] FOREIGN KEY([SupersedesLogID], [LegID])
REFERENCES [dbo].[HorseWelfareLog] ([WelfareLogID], [LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID];';
IF OBJECT_ID(N'dbo.FK_HorseWelfareLog_LegID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_HorseWelfareLog_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_HorseWelfareLog_LegID];';
IF OBJECT_ID(N'dbo.FK_HorseWelfareLog_RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_HorseWelfareLog_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_HorseWelfareLog_RecordedBy];';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_FeedAmountKg') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg] CHECK  (([FeedAmountKg]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg];';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_Supersedes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes] CHECK  (([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID]));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes];';
IF OBJECT_ID(N'dbo.CK_HorseWelfareLog_WaterIntakeLiters') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters] CHECK  (([WaterIntakeLiters]>=(0)));';
EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters];';

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
