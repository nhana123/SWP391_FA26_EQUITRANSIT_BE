-- 014: create/repair F12-F14 seams against the approved baseline.
-- Source: database-schema.md metadata snapshot 2026-10-14 (001-004).
-- Preserve existing rows. Never drop tables or invent required backfill data.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @lock<0 THROW 51000,'Schema migration is busy.',1;
IF OBJECT_ID('dbo.TripLeg','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.StaffProfile','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.TransportPlan','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.Customer','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.OrderRevision','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.TransportOrder','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;
IF OBJECT_ID('dbo.User','U') IS NULL
    THROW 51014,'Apply the baseline and prerequisite migrations first.',1;

IF OBJECT_ID('dbo.LegMilestoneLog','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[LegMilestoneLog] (
    [MilestoneID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [ReportedBy] BIGINT NOT NULL,
    [MilestoneType] VARCHAR(50) NOT NULL,
    [OccurredAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_LegMilestoneLog_OccurredAt] DEFAULT (sysutcdatetime()),
    [RecordedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_LegMilestoneLog_RecordedAt] DEFAULT (sysutcdatetime()),
    [Latitude] DECIMAL(9,6) NULL,
    [Longitude] DECIMAL(9,6) NULL,
    [Notes] NVARCHAR(2000) NULL,
    [SourceCode] VARCHAR(30) NULL,
    [SourceReference] NVARCHAR(500) NULL,
    [LocationDescription] NVARCHAR(500) NULL,
    [EstimatedArrivalAt] DATETIME2(0) NULL,
    [DelayReason] NVARCHAR(1000) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL
)';
IF COL_LENGTH('dbo.LegMilestoneLog','MilestoneID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[LegMilestoneLog])
        THROW 51014,'Missing required LegMilestoneLog.MilestoneID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [MilestoneID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','LegID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[LegMilestoneLog])
        THROW 51014,'Missing required LegMilestoneLog.LegID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [LegID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','ReportedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[LegMilestoneLog])
        THROW 51014,'Missing required LegMilestoneLog.ReportedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [ReportedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','MilestoneType') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[LegMilestoneLog])
        THROW 51014,'Missing required LegMilestoneLog.MilestoneType; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [MilestoneType] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','OccurredAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [OccurredAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_LegMilestoneLog_OccurredAt] DEFAULT (sysutcdatetime())';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','RecordedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [RecordedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_LegMilestoneLog_RecordedAt] DEFAULT (sysutcdatetime())';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','Latitude') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [Latitude] DECIMAL(9,6) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','Longitude') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [Longitude] DECIMAL(9,6) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','Notes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [Notes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','SourceCode') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceCode] VARCHAR(30) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','SourceReference') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceReference] NVARCHAR(500) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','LocationDescription') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [LocationDescription] NVARCHAR(500) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','EstimatedArrivalAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EstimatedArrivalAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','DelayReason') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [DelayReason] NVARCHAR(1000) NULL';
END;
IF COL_LENGTH('dbo.LegMilestoneLog','EvidenceFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EvidenceFileKey] NVARCHAR(1024) NULL';
END;

IF OBJECT_ID('dbo.HorseWelfareLog','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[HorseWelfareLog] (
    [WelfareLogID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [RecordedBy] BIGINT NOT NULL,
    [CheckTime] DATETIME2(0) NOT NULL,
    [HealthStatus] VARCHAR(50) NOT NULL,
    [TemperatureCelsius] DECIMAL(8,2) NULL,
    [WaterIntakeLiters] DECIMAL(8,2) NULL,
    [BehaviorNotes] NVARCHAR(2000) NULL,
    [PhotoFileKey] NVARCHAR(1024) NULL,
    [FeedAmountKg] DECIMAL(8,2) NULL,
    [CareNotes] NVARCHAR(2000) NULL,
    [InstructionFileKey] NVARCHAR(1024) NULL,
    [SupersedesLogID] BIGINT NULL
)';
IF COL_LENGTH('dbo.HorseWelfareLog','WelfareLogID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[HorseWelfareLog])
        THROW 51014,'Missing required HorseWelfareLog.WelfareLogID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [WelfareLogID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','LegID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[HorseWelfareLog])
        THROW 51014,'Missing required HorseWelfareLog.LegID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [LegID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','RecordedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[HorseWelfareLog])
        THROW 51014,'Missing required HorseWelfareLog.RecordedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [RecordedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','CheckTime') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[HorseWelfareLog])
        THROW 51014,'Missing required HorseWelfareLog.CheckTime; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [CheckTime] DATETIME2(0) NOT NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','HealthStatus') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[HorseWelfareLog])
        THROW 51014,'Missing required HorseWelfareLog.HealthStatus; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [HealthStatus] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','TemperatureCelsius') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [TemperatureCelsius] DECIMAL(8,2) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','WaterIntakeLiters') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [WaterIntakeLiters] DECIMAL(8,2) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','BehaviorNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [BehaviorNotes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','PhotoFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [PhotoFileKey] NVARCHAR(1024) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','FeedAmountKg') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [FeedAmountKg] DECIMAL(8,2) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','CareNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [CareNotes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','InstructionFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [InstructionFileKey] NVARCHAR(1024) NULL';
END;
IF COL_LENGTH('dbo.HorseWelfareLog','SupersedesLogID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [SupersedesLogID] BIGINT NULL';
END;

IF OBJECT_ID('dbo.IncidentReport','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[IncidentReport] (
    [IncidentID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [ReportedBy] BIGINT NOT NULL,
    [IncidentType] VARCHAR(50) NOT NULL,
    [SeverityLevel] VARCHAR(50) NOT NULL,
    [IncidentTime] DATETIME2(0) NOT NULL,
    [Description] NVARCHAR(2000) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [ImmediateActionNotes] NVARCHAR(2000) NULL,
    [EmergencyContact] NVARCHAR(500) NULL,
    [AcknowledgedBy] BIGINT NULL,
    [AcknowledgedAt] DATETIME2(0) NULL,
    [ResolvedBy] BIGINT NULL,
    [ResolvedAt] DATETIME2(0) NULL,
    [ResolutionNotes] NVARCHAR(2000) NULL,
    [EvidenceFilesJSON] NVARCHAR(MAX) NULL
)';
IF COL_LENGTH('dbo.IncidentReport','IncidentID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.IncidentID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [IncidentID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','LegID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.LegID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [LegID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','ReportedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.ReportedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ReportedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','IncidentType') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.IncidentType; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [IncidentType] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','SeverityLevel') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.SeverityLevel; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [SeverityLevel] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','IncidentTime') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.IncidentTime; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [IncidentTime] DATETIME2(0) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','Description') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.Description; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [Description] NVARCHAR(2000) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','Status') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[IncidentReport])
        THROW 51014,'Missing required IncidentReport.Status; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [Status] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','ImmediateActionNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ImmediateActionNotes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','EmergencyContact') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [EmergencyContact] NVARCHAR(500) NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','AcknowledgedBy') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [AcknowledgedBy] BIGINT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','AcknowledgedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [AcknowledgedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','ResolvedBy') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolvedBy] BIGINT NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','ResolvedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolvedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','ResolutionNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolutionNotes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.IncidentReport','EvidenceFilesJSON') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [EvidenceFilesJSON] NVARCHAR(MAX) NULL';
END;

IF OBJECT_ID('dbo.OrderChangeRequest','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderChangeRequest] (
    [ChangeRequestID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [BaseRevisionID] BIGINT NULL,
    [RequestedBy] BIGINT NOT NULL,
    [ChangeType] VARCHAR(30) NOT NULL,
    [Reason] NVARCHAR(2000) NOT NULL,
    [RequestedChangesJSON] NVARCHAR(MAX) NOT NULL,
    [AttachmentFileKey] NVARCHAR(1024) NULL,
    [Status] VARCHAR(30) NOT NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderChangeRequest_CreatedAt] DEFAULT (sysutcdatetime()),
    [RespondedBy] BIGINT NULL,
    [RespondedAt] DATETIME2(0) NULL,
    [ResponseReason] NVARCHAR(2000) NULL
)';
IF COL_LENGTH('dbo.OrderChangeRequest','ChangeRequestID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.ChangeRequestID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [ChangeRequestID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','OrderID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.OrderID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [OrderID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','BaseRevisionID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [BaseRevisionID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','RequestedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.RequestedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [RequestedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','ChangeType') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.ChangeType; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [ChangeType] VARCHAR(30) NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','Reason') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.Reason; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [Reason] NVARCHAR(2000) NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','RequestedChangesJSON') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.RequestedChangesJSON; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [RequestedChangesJSON] NVARCHAR(MAX) NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','AttachmentFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [AttachmentFileKey] NVARCHAR(1024) NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','Status') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OrderChangeRequest])
        THROW 51014,'Missing required OrderChangeRequest.Status; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [Status] VARCHAR(30) NOT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','CreatedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderChangeRequest_CreatedAt] DEFAULT (sysutcdatetime())';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','RespondedBy') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [RespondedBy] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','RespondedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [RespondedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.OrderChangeRequest','ResponseReason') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD [ResponseReason] NVARCHAR(2000) NULL';
END;

IF OBJECT_ID('dbo.OperationalAdjustment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OperationalAdjustment] (
    [AdjustmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [IncidentID] BIGINT NULL,
    [ProposedBy] BIGINT NOT NULL,
    [ApprovedBy] BIGINT NULL,
    [ProposedChanges] NVARCHAR(MAX) NOT NULL,
    [AdditionalCostEstimate] DECIMAL(18,2) NOT NULL,
    [ApprovalStatus] VARCHAR(50) NOT NULL,
    [ProposedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OperationalAdjustment_ProposedAt] DEFAULT (sysutcdatetime()),
    [DecidedAt] DATETIME2(0) NULL,
    [ChangeRequestID] BIGINT NULL,
    [CustomerDecision] VARCHAR(20) NULL,
    [RespondedByCustomerID] BIGINT NULL,
    [CustomerRespondedAt] DATETIME2(0) NULL,
    [CustomerResponseNotes] NVARCHAR(2000) NULL
)';
IF COL_LENGTH('dbo.OperationalAdjustment','AdjustmentID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.AdjustmentID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [AdjustmentID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','PlanID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.PlanID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [PlanID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','IncidentID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [IncidentID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ProposedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.ProposedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ProposedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ApprovedBy') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ApprovedBy] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ProposedChanges') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.ProposedChanges; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ProposedChanges] NVARCHAR(MAX) NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','AdditionalCostEstimate') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.AdditionalCostEstimate; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [AdditionalCostEstimate] DECIMAL(18,2) NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ApprovalStatus') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[OperationalAdjustment])
        THROW 51014,'Missing required OperationalAdjustment.ApprovalStatus; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ApprovalStatus] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ProposedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ProposedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OperationalAdjustment_ProposedAt] DEFAULT (sysutcdatetime())';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','DecidedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [DecidedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','ChangeRequestID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ChangeRequestID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','CustomerDecision') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerDecision] VARCHAR(20) NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','RespondedByCustomerID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [RespondedByCustomerID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','CustomerRespondedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerRespondedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.OperationalAdjustment','CustomerResponseNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerResponseNotes] NVARCHAR(2000) NULL';
END;

IF OBJECT_ID('dbo.TripHandover','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TripHandover] (
    [HandoverID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [RecordedBy] BIGINT NOT NULL,
    [HandoverType] VARCHAR(50) NOT NULL,
    [HandoverTime] DATETIME2(0) NOT NULL,
    [ReceivedByName] NVARCHAR(300) NOT NULL,
    [ReceiverAuthority] VARCHAR(50) NULL,
    [ReceiverSignatureFileKey] NVARCHAR(1024) NULL,
    [HorseConditionStatus] VARCHAR(50) NOT NULL,
    [Outcome] VARCHAR(50) NOT NULL,
    [ExceptionNotes] NVARCHAR(2000) NULL,
    [PhotoFileKey] NVARCHAR(1024) NULL,
    [CustomerConfirmedAt] DATETIME2(0) NULL,
    [LocationDescription] NVARCHAR(500) NULL,
    [CustomerDecision] VARCHAR(20) NULL,
    [CustomerFeedback] NVARCHAR(2000) NULL,
    [CustomerRespondedAt] DATETIME2(0) NULL,
    [RespondedByCustomerID] BIGINT NULL,
    [EvidenceFilesJSON] NVARCHAR(MAX) NULL
)';
IF COL_LENGTH('dbo.TripHandover','HandoverID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.HandoverID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [HandoverID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','LegID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.LegID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [LegID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','RecordedBy') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.RecordedBy; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [RecordedBy] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','HandoverType') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.HandoverType; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [HandoverType] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','HandoverTime') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.HandoverTime; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [HandoverTime] DATETIME2(0) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','ReceivedByName') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.ReceivedByName; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [ReceivedByName] NVARCHAR(300) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','ReceiverAuthority') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [ReceiverAuthority] VARCHAR(50) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','ReceiverSignatureFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [ReceiverSignatureFileKey] NVARCHAR(1024) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','HorseConditionStatus') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.HorseConditionStatus; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [HorseConditionStatus] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','Outcome') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[TripHandover])
        THROW 51014,'Missing required TripHandover.Outcome; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [Outcome] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','ExceptionNotes') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [ExceptionNotes] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','PhotoFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [PhotoFileKey] NVARCHAR(1024) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','CustomerConfirmedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerConfirmedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','LocationDescription') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [LocationDescription] NVARCHAR(500) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','CustomerDecision') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerDecision] VARCHAR(20) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','CustomerFeedback') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerFeedback] NVARCHAR(2000) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','CustomerRespondedAt') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [CustomerRespondedAt] DATETIME2(0) NULL';
END;
IF COL_LENGTH('dbo.TripHandover','RespondedByCustomerID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [RespondedByCustomerID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.TripHandover','EvidenceFilesJSON') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [EvidenceFilesJSON] NVARCHAR(MAX) NULL';
END;

IF OBJECT_ID('dbo.ActualCost','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ActualCost] (
    [CostID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [LegID] BIGINT NULL,
    [CostCode] VARCHAR(50) NOT NULL,
    [Amount] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [IncurredAt] DATETIME2(0) NOT NULL,
    [Description] NVARCHAR(1000) NULL,
    [RecordedBy] BIGINT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL
)';
IF COL_LENGTH('dbo.ActualCost','CostID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.CostID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [CostID] BIGINT IDENTITY(1,1) NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','PlanID') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.PlanID; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [PlanID] BIGINT NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','LegID') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [LegID] BIGINT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','CostCode') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.CostCode; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [CostCode] VARCHAR(50) NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','Amount') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.Amount; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Amount] DECIMAL(18,2) NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','Currency') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.Currency; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Currency] VARCHAR(3) NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','IncurredAt') IS NULL
BEGIN
    IF EXISTS(SELECT 1 FROM dbo.[ActualCost])
        THROW 51014,'Missing required ActualCost.IncurredAt; reviewed backfill required.',1;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [IncurredAt] DATETIME2(0) NOT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','Description') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Description] NVARCHAR(1000) NULL';
END;
IF COL_LENGTH('dbo.ActualCost','RecordedBy') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [RecordedBy] BIGINT NULL';
END;
IF COL_LENGTH('dbo.ActualCost','EvidenceFileKey') IS NULL
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [EvidenceFileKey] NVARCHAR(1024) NULL';
END;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.LegMilestoneLog') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD CONSTRAINT [PK_LegMilestoneLog] PRIMARY KEY ([MilestoneID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='IX_LegMilestoneLog_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_LegMilestoneLog_LegID] ON dbo.[LegMilestoneLog] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='IX_LegMilestoneLog_ReportedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_LegMilestoneLog_ReportedBy] ON dbo.[LegMilestoneLog] ([ReportedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseWelfareLog') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD CONSTRAINT [PK_HorseWelfareLog] PRIMARY KEY ([WelfareLogID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='IX_HorseWelfareLog_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseWelfareLog_LegID] ON dbo.[HorseWelfareLog] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='IX_HorseWelfareLog_RecordedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseWelfareLog_RecordedBy] ON dbo.[HorseWelfareLog] ([RecordedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='UQ_Figma_HorseWelfareLog_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD CONSTRAINT [UQ_Figma_HorseWelfareLog_Parent] UNIQUE ([WelfareLogID],[LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.IncidentReport') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD CONSTRAINT [PK_IncidentReport] PRIMARY KEY ([IncidentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.IncidentReport') AND name='IX_IncidentReport_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_IncidentReport_LegID] ON dbo.[IncidentReport] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.IncidentReport') AND name='IX_IncidentReport_ReportedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_IncidentReport_ReportedBy] ON dbo.[IncidentReport] ([ReportedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderChangeRequest') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD CONSTRAINT [PK_OrderChangeRequest] PRIMARY KEY ([ChangeRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OperationalAdjustment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD CONSTRAINT [PK_OperationalAdjustment] PRIMARY KEY ([AdjustmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='IX_OperationalAdjustment_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OperationalAdjustment_PlanID] ON dbo.[OperationalAdjustment] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='IX_OperationalAdjustment_IncidentID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OperationalAdjustment_IncidentID] ON dbo.[OperationalAdjustment] ([IncidentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='IX_OperationalAdjustment_ProposedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OperationalAdjustment_ProposedBy] ON dbo.[OperationalAdjustment] ([ProposedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='IX_OperationalAdjustment_ApprovedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OperationalAdjustment_ApprovedBy] ON dbo.[OperationalAdjustment] ([ApprovedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripHandover') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD CONSTRAINT [PK_TripHandover] PRIMARY KEY ([HandoverID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripHandover') AND name='IX_TripHandover_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripHandover_LegID] ON dbo.[TripHandover] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripHandover') AND name='IX_TripHandover_RecordedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripHandover_RecordedBy] ON dbo.[TripHandover] ([RecordedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD CONSTRAINT [PK_ActualCost] PRIMARY KEY ([CostID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_PlanID] ON dbo.[ActualCost] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_LegID] ON dbo.[ActualCost] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Latitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Latitude] CHECK ([Latitude]>=(-90) AND [Latitude]<=(90))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [CK_LegMilestoneLog_Latitude]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Longitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Longitude] CHECK ([Longitude]>=(-180) AND [Longitude]<=(180))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [CK_LegMilestoneLog_Longitude]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [FK_LegMilestoneLog_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK CHECK CONSTRAINT [FK_LegMilestoneLog_ReportedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_FeedAmountKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg] CHECK ([FeedAmountKg]>=(0))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_Supersedes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes] CHECK ([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_HorseWelfareLog_WaterIntakeLiters')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters] CHECK ([WaterIntakeLiters]>=(0))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_Figma_HorseWelfareLog_SupersedesLogID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID] FOREIGN KEY ([SupersedesLogID],[LegID]) REFERENCES dbo.[HorseWelfareLog] ([WelfareLogID],[LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_HorseWelfareLog_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK CHECK CONSTRAINT [FK_HorseWelfareLog_RecordedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='CK_Figma_IncidentReport_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK CHECK CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_AcknowledgedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy] FOREIGN KEY ([AcknowledgedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_ResolvedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy] FOREIGN KEY ([ResolvedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_IncidentReport_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_IncidentReport_ReportedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_JSON] CHECK (isjson([RequestedChangesJSON])=(1))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [CK_OrderChangeRequest_JSON]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_Reason')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_Reason] CHECK (len(ltrim(rtrim([Reason])))>(0))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [CK_OrderChangeRequest_Reason]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_BaseRevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID] FOREIGN KEY ([BaseRevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RequestedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy] FOREIGN KEY ([RequestedBy]) REFERENCES dbo.[User] ([UserID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RespondedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy] FOREIGN KEY ([RespondedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_Figma_OperationalAdjustment_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision] CHECK ([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING'')';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_AdditionalCostEstimate')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate] CHECK ([AdditionalCostEstimate]>=(0))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_ProposedChanges_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON] CHECK (isjson([ProposedChanges])=(1))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_ChangeRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID] FOREIGN KEY ([ChangeRequestID]) REFERENCES dbo.[OrderChangeRequest] ([ChangeRequestID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_ApprovedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_IncidentID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_IncidentID] FOREIGN KEY ([IncidentID]) REFERENCES dbo.[IncidentReport] ([IncidentID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_IncidentID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_PlanID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ProposedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ProposedBy] FOREIGN KEY ([ProposedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_ProposedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_CustomerDecision] CHECK ([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED'')';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripHandover_CustomerDecision]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_Figma_TripHandover_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_TripHandover_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK CHECK CONSTRAINT [FK_TripHandover_RecordedBy]';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='CK_ActualCost_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [CK_ActualCost_Amount] CHECK ([Amount]>=(0))';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [CK_ActualCost_Amount]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_ActualCost_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_ActualCost_PlanID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_LegID] FOREIGN KEY ([LegID],[PlanID]) REFERENCES dbo.[TripLeg] ([LegID],[PlanID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_Figma_ActualCost_LegID]';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK CHECK CONSTRAINT [FK_Figma_ActualCost_RecordedBy]';
IF COL_LENGTH('dbo.LegMilestoneLog','PlanID') IS NULL
    EXEC(N'ALTER TABLE dbo.LegMilestoneLog ADD PlanID BIGINT NULL');
EXEC(N'UPDATE x SET PlanID=l.PlanID FROM dbo.LegMilestoneLog x
    JOIN dbo.TripLeg l ON l.LegID=x.LegID WHERE x.PlanID IS NULL');
EXEC(N'ALTER TABLE dbo.LegMilestoneLog ALTER COLUMN PlanID BIGINT NOT NULL');
IF COL_LENGTH('dbo.LegMilestoneLog','IsCustomerVisible') IS NULL
    EXEC(N'ALTER TABLE dbo.LegMilestoneLog ADD IsCustomerVisible BIT NOT NULL CONSTRAINT DF_Execution_LegMilestoneLog_IsCustomerVisible DEFAULT(0)');
IF COL_LENGTH('dbo.LegMilestoneLog','PublicNote') IS NULL
    EXEC(N'ALTER TABLE dbo.LegMilestoneLog ADD PublicNote NVARCHAR(2000) NULL');
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_Figma_LegMilestoneLog_LegID')
    EXEC(N'ALTER TABLE dbo.LegMilestoneLog WITH CHECK ADD CONSTRAINT FK_Figma_LegMilestoneLog_LegID
        FOREIGN KEY(LegID,PlanID) REFERENCES dbo.TripLeg(LegID,PlanID)');
EXEC(N'ALTER TABLE dbo.LegMilestoneLog WITH CHECK CHECK CONSTRAINT FK_Figma_LegMilestoneLog_LegID');
IF COL_LENGTH('dbo.HorseWelfareLog','PlanID') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD PlanID BIGINT NULL');
EXEC(N'UPDATE x SET PlanID=l.PlanID FROM dbo.HorseWelfareLog x
    JOIN dbo.TripLeg l ON l.LegID=x.LegID WHERE x.PlanID IS NULL');
EXEC(N'ALTER TABLE dbo.HorseWelfareLog ALTER COLUMN PlanID BIGINT NOT NULL');
IF COL_LENGTH('dbo.HorseWelfareLog','IsCustomerVisible') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD IsCustomerVisible BIT NOT NULL CONSTRAINT DF_Execution_HorseWelfareLog_IsCustomerVisible DEFAULT(0)');
IF COL_LENGTH('dbo.HorseWelfareLog','PublicNote') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD PublicNote NVARCHAR(2000) NULL');
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_Figma_HorseWelfareLog_LegID')
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog WITH CHECK ADD CONSTRAINT FK_Figma_HorseWelfareLog_LegID
        FOREIGN KEY(LegID,PlanID) REFERENCES dbo.TripLeg(LegID,PlanID)');
EXEC(N'ALTER TABLE dbo.HorseWelfareLog WITH CHECK CHECK CONSTRAINT FK_Figma_HorseWelfareLog_LegID');
IF COL_LENGTH('dbo.HorseWelfareLog','RecordedAt') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD RecordedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Execution_WelfareRecorded DEFAULT SYSUTCDATETIME()');
IF COL_LENGTH('dbo.HorseWelfareLog','SourceCode') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD SourceCode VARCHAR(30) NULL');
IF COL_LENGTH('dbo.HorseWelfareLog','SourceReference') IS NULL
    EXEC(N'ALTER TABLE dbo.HorseWelfareLog ADD SourceReference NVARCHAR(500) NULL');

IF OBJECT_ID('dbo.LegExecutionOperation','U') IS NULL
    CREATE TABLE dbo.LegExecutionOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_LegExecutionOperation
            PRIMARY KEY(OrderID,UserID,OperationID),
        CONSTRAINT CK_LegExecutionOperation_JSON CHECK(ISJSON(ReceiptJSON)=1)
    );

EXEC(N'CREATE OR ALTER TRIGGER dbo.TR_Execution_WelfareImmutable
ON dbo.HorseWelfareLog AFTER UPDATE, DELETE AS
BEGIN SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted)
        THROW 51014, ''Execution facts are append-only.'', 1;
END');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Execution_Welfare_Page'
    AND object_id=OBJECT_ID('dbo.HorseWelfareLog'))
    EXEC(N'CREATE INDEX IX_Execution_Welfare_Page ON dbo.HorseWelfareLog
        (PlanID,IsCustomerVisible,RecordedAt DESC) INCLUDE(LegID)');
EXEC(N'CREATE OR ALTER TRIGGER dbo.TR_Execution_MilestoneImmutable
ON dbo.LegMilestoneLog AFTER UPDATE, DELETE AS
BEGIN SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted)
        THROW 51014, ''Execution facts are append-only.'', 1;
END');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Execution_Milestone_Page'
    AND object_id=OBJECT_ID('dbo.LegMilestoneLog'))
    EXEC(N'CREATE INDEX IX_Execution_Milestone_Page ON dbo.LegMilestoneLog
        (PlanID,IsCustomerVisible,RecordedAt DESC) INCLUDE(LegID)');

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    name='UQ_Execution_WelfareCorrection'
    AND object_id=OBJECT_ID('dbo.HorseWelfareLog'))
    EXEC(N'CREATE UNIQUE INDEX UQ_Execution_WelfareCorrection
        ON dbo.HorseWelfareLog(SupersedesLogID)
        WHERE SupersedesLogID IS NOT NULL');

-- Recheck F11 only on real departure, not first-leg completion.
EXEC(N'CREATE OR ALTER TRIGGER dbo.TR_Departure_FirstLegGuard
ON dbo.TripLeg AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.LegID=d.LegID
        WHERE d.ActualDeparture IS NOT NULL AND
        (i.ActualDeparture IS NULL OR i.ActualDeparture<>d.ActualDeparture))
        THROW 51010, ''Recorded actual departure is immutable.'', 1;
    IF NOT EXISTS (SELECT 1 FROM inserted i
        JOIN deleted d ON d.LegID=i.LegID WHERE i.SequenceNo=1
        AND ((d.ActualDeparture IS NULL AND i.ActualDeparture IS NOT NULL)
            OR (d.LegStatus<>''IN_TRANSIT'' AND i.LegStatus=''IN_TRANSIT'')))
        RETURN;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN deleted d ON d.LegID=i.LegID
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        LEFT JOIN dbo.StaffProfile m ON m.StaffID=p.DepartureAuthorizedBy
        LEFT JOIN dbo.[User] u ON u.UserID=m.UserID
        LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID
        WHERE i.SequenceNo=1
        AND ((d.ActualDeparture IS NULL AND i.ActualDeparture IS NOT NULL)
            OR (d.LegStatus<>''IN_TRANSIT'' AND i.LegStatus=''IN_TRANSIT''))
        AND (i.TransportMode<>''ROAD''
             OR i.ActualDeparture IS NULL
             OR p.FinancialGateStatus<>''PASS''
             OR p.DocGateStatus<>''PASS''
             OR p.OperationalGateStatus<>''PASS''
             OR p.GatesCheckedAt IS NULL
             OR p.DepartureAuthorizedBy IS NULL
             OR p.DepartureAuthorizedAt IS NULL
             OR u.Status<>''ACTIVE''
             OR r.RoleCode<>''MANAGER''
             OR NOT EXISTS (SELECT 1 FROM dbo.HorseReceipt hr
                 WHERE hr.PlanID=i.PlanID AND hr.LegID=i.LegID
                 AND hr.IdentityCheckResult=''MATCHED''
                 AND hr.ReceiptStatus=''ACCEPTED''
                 AND hr.ReceivedAt<=i.ActualDeparture)))
        THROW 51010, ''First leg departure requires PASS gates, manager authorization and matched horse receipt.'', 1;
END;');
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
