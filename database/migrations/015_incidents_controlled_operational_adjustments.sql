-- F13: incidents and controlled operational adjustments.
-- After baseline and migrations 001-014.
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
    THROW 51015, 'Run migration 015 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51015, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51015, 'VIEW DEFINITION is required to verify the F13 schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51015, 'Another schema migration holds the lock.', 1;

IF COL_LENGTH(N'dbo.TripLeg', N'LegID') IS NULL
    OR COL_LENGTH(N'dbo.TransportPlan', N'PlanID') IS NULL
    OR COL_LENGTH(N'dbo.TransportOrder', N'OrderID') IS NULL
    OR COL_LENGTH(N'dbo.OrderRevision', N'RevisionID') IS NULL
    OR COL_LENGTH(N'dbo.OrderRevision', N'OrderID') IS NULL
    OR COL_LENGTH(N'dbo.StaffProfile', N'StaffID') IS NULL
    OR COL_LENGTH(N'dbo.Customer', N'CustomerID') IS NULL
    OR COL_LENGTH(N'dbo.[User]', N'UserID') IS NULL
    THROW 51015, 'Apply the application baseline and preceding migrations first.', 1;

-- 1. IncidentReport: D02/M10/M11 / FR-13.
IF OBJECT_ID(N'dbo.IncidentReport', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[IncidentReport](
	[IncidentID] [bigint] IDENTITY(1,1) NOT NULL,
	[LegID] [bigint] NOT NULL,
	[ReportedBy] [bigint] NOT NULL,
	[IncidentType] [varchar](50) NOT NULL,
	[SeverityLevel] [varchar](50) NOT NULL,
	[IncidentTime] [datetime2](0) NOT NULL,
	[Description] [nvarchar](2000) NOT NULL,
	[Status] [varchar](50) NOT NULL,
	[ImmediateActionNotes] [nvarchar](2000) NULL,
	[EmergencyContact] [nvarchar](500) NULL,
	[AcknowledgedBy] [bigint] NULL,
	[AcknowledgedAt] [datetime2](0) NULL,
	[ResolvedBy] [bigint] NULL,
	[ResolvedAt] [datetime2](0) NULL,
	[ResolutionNotes] [nvarchar](2000) NULL,
	[EvidenceFilesJSON] [nvarchar](max) NULL,
 CONSTRAINT [PK_IncidentReport] PRIMARY KEY CLUSTERED 
(
	[IncidentID] ASC
)
);';

IF COL_LENGTH(N'dbo.IncidentReport', N'ImmediateActionNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ImmediateActionNotes] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'EmergencyContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [EmergencyContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'AcknowledgedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [AcknowledgedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'AcknowledgedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [AcknowledgedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'ResolvedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolvedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'ResolvedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolvedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'ResolutionNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [ResolutionNotes] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.IncidentReport', N'EvidenceFilesJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] ADD [EvidenceFilesJSON] NVARCHAR(MAX) NULL;';

IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'IncidentID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
    THROW 51015, 'IncidentReport.IncidentID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'LegID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'IncidentReport.LegID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'ReportedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'IncidentReport.ReportedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'IncidentType' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51015, 'IncidentReport.IncidentType differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'SeverityLevel' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51015, 'IncidentReport.SeverityLevel differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'IncidentTime' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'IncidentReport.IncidentTime differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'Description' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'IncidentReport.Description differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'Status' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51015, 'IncidentReport.Status differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'ImmediateActionNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'IncidentReport.ImmediateActionNotes differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'EmergencyContact' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=1000)
    THROW 51015, 'IncidentReport.EmergencyContact differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'AcknowledgedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'IncidentReport.AcknowledgedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'AcknowledgedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'IncidentReport.AcknowledgedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'ResolvedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'IncidentReport.ResolvedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'ResolvedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'IncidentReport.ResolvedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'ResolutionNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'IncidentReport.ResolutionNotes differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IncidentReport') AND c.name=N'EvidenceFilesJSON' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=-1)
    THROW 51015, 'IncidentReport.EvidenceFilesJSON differs from the source schema.', 1;

-- 2. OrderChangeRequest: C10/M08/M09 / FR-13 / FR-03 intake.
IF OBJECT_ID(N'dbo.OrderChangeRequest', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[OrderChangeRequest](
	[ChangeRequestID] [bigint] IDENTITY(1,1) NOT NULL,
	[OrderID] [bigint] NOT NULL,
	[BaseRevisionID] [bigint] NULL,
	[RequestedBy] [bigint] NOT NULL,
	[ChangeType] [varchar](30) NOT NULL,
	[Reason] [nvarchar](2000) NOT NULL,
	[RequestedChangesJSON] [nvarchar](max) NOT NULL,
	[AttachmentFileKey] [nvarchar](1024) NULL,
	[Status] [varchar](30) NOT NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
	[RespondedBy] [bigint] NULL,
	[RespondedAt] [datetime2](0) NULL,
	[ResponseReason] [nvarchar](2000) NULL,
 CONSTRAINT [PK_OrderChangeRequest] PRIMARY KEY CLUSTERED 
(
	[ChangeRequestID] ASC
)
);';

IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'ChangeRequestID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
    THROW 51015, 'OrderChangeRequest.ChangeRequestID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'OrderID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OrderChangeRequest.OrderID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'BaseRevisionID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OrderChangeRequest.BaseRevisionID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'RequestedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OrderChangeRequest.RequestedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'ChangeType' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=30)
    THROW 51015, 'OrderChangeRequest.ChangeType differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'Reason' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'OrderChangeRequest.Reason differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'RequestedChangesJSON' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=-1)
    THROW 51015, 'OrderChangeRequest.RequestedChangesJSON differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'AttachmentFileKey' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=2048)
    THROW 51015, 'OrderChangeRequest.AttachmentFileKey differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'Status' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=30)
    THROW 51015, 'OrderChangeRequest.Status differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'CreatedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'OrderChangeRequest.CreatedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'RespondedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OrderChangeRequest.RespondedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'RespondedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'OrderChangeRequest.RespondedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND c.name=N'ResponseReason' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'OrderChangeRequest.ResponseReason differs from the source schema.', 1;

-- 3. OperationalAdjustment: M08/M09/M11/C10 / FR-13.
IF OBJECT_ID(N'dbo.OperationalAdjustment', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[OperationalAdjustment](
	[AdjustmentID] [bigint] IDENTITY(1,1) NOT NULL,
	[PlanID] [bigint] NOT NULL,
	[IncidentID] [bigint] NULL,
	[ProposedBy] [bigint] NOT NULL,
	[ApprovedBy] [bigint] NULL,
	[ProposedChanges] [nvarchar](max) NOT NULL,
	[AdditionalCostEstimate] [decimal](18, 2) NOT NULL,
	[ApprovalStatus] [varchar](50) NOT NULL,
	[ProposedAt] [datetime2](0) NOT NULL,
	[DecidedAt] [datetime2](0) NULL,
	[ChangeRequestID] [bigint] NULL,
	[CustomerDecision] [varchar](20) NULL,
	[RespondedByCustomerID] [bigint] NULL,
	[CustomerRespondedAt] [datetime2](0) NULL,
	[CustomerResponseNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_OperationalAdjustment] PRIMARY KEY CLUSTERED 
(
	[AdjustmentID] ASC
)
);';

IF COL_LENGTH(N'dbo.OperationalAdjustment', N'ChangeRequestID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [ChangeRequestID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.OperationalAdjustment', N'CustomerDecision') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerDecision] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.OperationalAdjustment', N'RespondedByCustomerID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [RespondedByCustomerID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.OperationalAdjustment', N'CustomerRespondedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerRespondedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.OperationalAdjustment', N'CustomerResponseNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] ADD [CustomerResponseNotes] NVARCHAR(2000) NULL;';

IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'AdjustmentID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=1 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.AdjustmentID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'PlanID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.PlanID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'IncidentID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.IncidentID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ProposedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.ProposedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ApprovedBy' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.ApprovedBy differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ProposedChanges' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=-1)
    THROW 51015, 'OperationalAdjustment.ProposedChanges differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'AdditionalCostEstimate' AND TYPE_NAME(c.system_type_id)=N'decimal' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.precision=18 AND c.scale=2)
    THROW 51015, 'OperationalAdjustment.AdditionalCostEstimate differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ApprovalStatus' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=50)
    THROW 51015, 'OperationalAdjustment.ApprovalStatus differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ProposedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=0 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'OperationalAdjustment.ProposedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'DecidedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'OperationalAdjustment.DecidedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'ChangeRequestID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.ChangeRequestID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'CustomerDecision' AND TYPE_NAME(c.system_type_id)=N'varchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=20)
    THROW 51015, 'OperationalAdjustment.CustomerDecision differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'RespondedByCustomerID' AND TYPE_NAME(c.system_type_id)=N'bigint' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0)
    THROW 51015, 'OperationalAdjustment.RespondedByCustomerID differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'CustomerRespondedAt' AND TYPE_NAME(c.system_type_id)=N'datetime2' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.scale=0)
    THROW 51015, 'OperationalAdjustment.CustomerRespondedAt differs from the source schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND c.name=N'CustomerResponseNotes' AND TYPE_NAME(c.system_type_id)=N'nvarchar' AND c.is_nullable=1 AND c.is_identity=0 AND c.is_computed=0 AND c.max_length=4000)
    THROW 51015, 'OperationalAdjustment.CustomerResponseNotes differs from the source schema.', 1;

-- Default Constraints.
IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints dc
    WHERE dc.parent_object_id=OBJECT_ID(N'dbo.OrderChangeRequest')
        AND dc.parent_column_id=(
            SELECT COLUMNPROPERTY(
                OBJECT_ID(N'dbo.OrderChangeRequest'), N'CreatedAt',
                'ColumnId')))
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] ADD CONSTRAINT [DF_OrderChangeRequest_CreatedAt] DEFAULT (sysutcdatetime()) FOR [CreatedAt];';
IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints dc
    WHERE dc.parent_object_id=OBJECT_ID(N'dbo.OperationalAdjustment')
        AND dc.parent_column_id=(
            SELECT COLUMNPROPERTY(
                OBJECT_ID(N'dbo.OperationalAdjustment'), N'ProposedAt',
                'ColumnId')))
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] ADD CONSTRAINT [DF_OperationalAdjustment_ProposedAt] DEFAULT (sysutcdatetime()) FOR [ProposedAt];';

-- Indexes.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.IncidentReport') AND name=N'IX_IncidentReport_LegID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_IncidentReport_LegID] ON [dbo].[IncidentReport] ([LegID] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.IncidentReport') AND name=N'IX_IncidentReport_ReportedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_IncidentReport_ReportedBy] ON [dbo].[IncidentReport] ([ReportedBy] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.IncidentReport') AND name=N'IX_IncidentReport_AcknowledgedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_IncidentReport_AcknowledgedBy] ON [dbo].[IncidentReport] ([AcknowledgedBy] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.IncidentReport') AND name=N'IX_IncidentReport_ResolvedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_IncidentReport_ResolvedBy] ON [dbo].[IncidentReport] ([ResolvedBy] ASC);';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND name=N'IX_OrderChangeRequest_OrderID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OrderChangeRequest_OrderID] ON [dbo].[OrderChangeRequest] ([OrderID] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND name=N'IX_OrderChangeRequest_RequestedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OrderChangeRequest_RequestedBy] ON [dbo].[OrderChangeRequest] ([RequestedBy] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrderChangeRequest') AND name=N'IX_OrderChangeRequest_RespondedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OrderChangeRequest_RespondedBy] ON [dbo].[OrderChangeRequest] ([RespondedBy] ASC);';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_PlanID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_PlanID] ON [dbo].[OperationalAdjustment] ([PlanID] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_IncidentID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_IncidentID] ON [dbo].[OperationalAdjustment] ([IncidentID] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_ProposedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_ProposedBy] ON [dbo].[OperationalAdjustment] ([ProposedBy] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_ApprovedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_ApprovedBy] ON [dbo].[OperationalAdjustment] ([ApprovedBy] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_ChangeRequestID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_ChangeRequestID] ON [dbo].[OperationalAdjustment] ([ChangeRequestID] ASC);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OperationalAdjustment') AND name=N'IX_OperationalAdjustment_RespondedByCustomerID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_RespondedByCustomerID] ON [dbo].[OperationalAdjustment] ([RespondedByCustomerID] ASC);';

-- Foreign Key Constraints.
IF OBJECT_ID(N'dbo.FK_IncidentReport_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_LegID] FOREIGN KEY([LegID]) REFERENCES [dbo].[TripLeg] ([LegID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_IncidentReport_LegID];';

IF OBJECT_ID(N'dbo.FK_IncidentReport_ReportedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_ReportedBy] FOREIGN KEY([ReportedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_IncidentReport_ReportedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_AcknowledgedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy] FOREIGN KEY([AcknowledgedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_ResolvedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy] FOREIGN KEY([ResolvedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK CHECK CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_OrderID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID] FOREIGN KEY([OrderID]) REFERENCES [dbo].[TransportOrder] ([OrderID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID];';

IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_BaseRevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID] FOREIGN KEY([BaseRevisionID], [OrderID]) REFERENCES [dbo].[OrderRevision] ([RevisionID], [OrderID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID];';

IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RequestedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy] FOREIGN KEY([RequestedBy]) REFERENCES [dbo].[User] ([UserID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RespondedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy] FOREIGN KEY([RespondedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy];';

IF OBJECT_ID(N'dbo.FK_OperationalAdjustment_PlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_PlanID] FOREIGN KEY([PlanID]) REFERENCES [dbo].[TransportPlan] ([PlanID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_PlanID];';

IF OBJECT_ID(N'dbo.FK_OperationalAdjustment_IncidentID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_IncidentID] FOREIGN KEY([IncidentID]) REFERENCES [dbo].[IncidentReport] ([IncidentID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_IncidentID];';

IF OBJECT_ID(N'dbo.FK_OperationalAdjustment_ProposedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ProposedBy] FOREIGN KEY([ProposedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_ProposedBy];';

IF OBJECT_ID(N'dbo.FK_OperationalAdjustment_ApprovedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ApprovedBy] FOREIGN KEY([ApprovedBy]) REFERENCES [dbo].[StaffProfile] ([StaffID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_OperationalAdjustment_ApprovedBy];';

IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_ChangeRequestID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID] FOREIGN KEY([ChangeRequestID]) REFERENCES [dbo].[OrderChangeRequest] ([ChangeRequestID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID];';

IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID] FOREIGN KEY([RespondedByCustomerID]) REFERENCES [dbo].[Customer] ([CustomerID]);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID];';

-- Check Constraints.
IF OBJECT_ID(N'dbo.CK_Figma_IncidentReport_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK ADD CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR ISJSON([EvidenceFilesJSON]) = 1);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[IncidentReport] WITH CHECK CHECK CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON];';

IF OBJECT_ID(N'dbo.CK_OrderChangeRequest_JSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_JSON] CHECK (ISJSON([RequestedChangesJSON]) = 1);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [CK_OrderChangeRequest_JSON];';

IF OBJECT_ID(N'dbo.CK_OrderChangeRequest_Reason', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_Reason] CHECK (LEN(LTRIM(RTRIM([Reason]))) > 0);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OrderChangeRequest] WITH CHECK CHECK CONSTRAINT [CK_OrderChangeRequest_Reason];';

IF OBJECT_ID(N'dbo.CK_OperationalAdjustment_AdditionalCostEstimate', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate] CHECK ([AdditionalCostEstimate] >= 0);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate];';

IF OBJECT_ID(N'dbo.CK_OperationalAdjustment_ProposedChanges_JSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON] CHECK (ISJSON([ProposedChanges]) = 1);';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON];';

IF OBJECT_ID(N'dbo.CK_Figma_OperationalAdjustment_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision] CHECK (([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING''));';
EXEC sys.sp_executesql N'ALTER TABLE [dbo].[OperationalAdjustment] WITH CHECK CHECK CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision];';

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
