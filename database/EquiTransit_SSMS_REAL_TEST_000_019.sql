-- SQL Server native migration / schema / graph CRUD smoke test.
-- Source: fix/finaldelivery @ d22dc3daa1c41058d5fba8e1376304e130d99f7d
-- REQUIRED: SSMS Query > SQLCMD Mode. Connect to the local SQL Server.
-- Run the ENTIRE file with F5. Do not execute isolated selected portions.
-- Refuses existing database. No automatic DROP, reset, or production migration.
-- A failed run keeps the new database and already committed migrations for diagnosis.
-- This checks migrations and SQL smoke behavior, not the full backend integration suite.
:ON ERROR EXIT
USE [master];
GO
IF DB_ID(N'EquiTransit_FigmaMigrationTest_20261013_143729') IS NOT NULL
    THROW 51000, 'Test database already exists. STOP: review it before rerunning; nothing was reset.', 1;
GO
CREATE DATABASE [EquiTransit_FigmaMigrationTest_20261013_143729];
GO
USE [EquiTransit_FigmaMigrationTest_20261013_143729];
GO
SELECT @@SERVERNAME AS ServerName, DB_NAME() AS TestDatabase,
       SUSER_SNAME() AS LoginName, @@VERSION AS SqlServerVersion,
       SYSUTCDATETIME() AS StartedAtUtc;
GO
PRINT N'RUN first application: 000_figma_schema.sql';
GO
-- 000: reproducible 57-table baseline, no customer/demo data.
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

IF OBJECT_ID('dbo.AuditEvent','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuditEvent] (
    [EventID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NULL,
    [EntityType] VARCHAR(50) NOT NULL,
    [EntityID] BIGINT NOT NULL,
    [ActionCode] VARCHAR(50) NOT NULL,
    [ChangeSnapshot] NVARCHAR(MAX) NULL,
    [OccurredAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_AuditEvent_OccurredAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.AuthenticationToken','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuthenticationToken] (
    [TokenID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [TokenHash] VARCHAR(64) NOT NULL,
    [Purpose] VARCHAR(20) NOT NULL,
    [ExpiresAt] DATETIME2(0) NOT NULL,
    [UsedAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
)';

IF OBJECT_ID('dbo.AuthorityProcedure','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuthorityProcedure] (
    [ProcedureID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ProcedureCode] VARCHAR(50) NOT NULL,
    [AuthorityName] NVARCHAR(300) NOT NULL,
    [ApplicationReference] VARCHAR(150) NULL,
    [ApprovalStatus] VARCHAR(50) NOT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [DecidedAt] DATETIME2(0) NULL,
    [PhaseCode] VARCHAR(50) NULL,
    [AuthorityContact] NVARCHAR(500) NULL,
    [HoldReason] NVARCHAR(2000) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [RecordedBy] BIGINT NULL
)';

IF OBJECT_ID('dbo.ComplianceClearance','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ComplianceClearance] (
    [ClearanceID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [CheckedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_ComplianceClearance_CheckedAt] DEFAULT (sysutcdatetime()),
    [ExpiresAt] DATETIME2(0) NULL,
    [AssessmentNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.ComplianceRuleVersion','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ComplianceRuleVersion] (
    [RuleID] BIGINT IDENTITY(1,1) NOT NULL,
    [RuleCode] VARCHAR(50) NOT NULL,
    [VersionNo] INT NOT NULL,
    [OriginCountryCode] VARCHAR(2) NOT NULL,
    [DestinationCountryCode] VARCHAR(2) NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [SourceURL] NVARCHAR(2048) NOT NULL,
    [ReviewedAt] DATETIME2(0) NULL,
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [TransitCountryCode] VARCHAR(2) NULL,
    [RuleDescription] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.Customer','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Customer] (
    [CustomerID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [FullName] NVARCHAR(150) NOT NULL,
    [Phone] VARCHAR(30) NULL,
    [Address] NVARCHAR(500) NULL,
    [EmailNotificationsEnabled] BIT NULL
)';

IF OBJECT_ID('dbo.DocumentRequirement','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentRequirement] (
    [RequirementID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [RuleID] BIGINT NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [IsMandatory] BIT NOT NULL,
    [DueAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [Instructions] NVARCHAR(2000) NULL,
    [ScopeCode] VARCHAR(20) NULL,
    [HorseID] BIGINT NULL,
    [ValidityConditionsJSON] NVARCHAR(MAX) NULL,
    [CaseJustification] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.DocumentReview','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentReview] (
    [ReviewID] BIGINT IDENTITY(1,1) NOT NULL,
    [SubmissionID] BIGINT NOT NULL,
    [ReviewedBy] BIGINT NOT NULL,
    [Decision] VARCHAR(50) NOT NULL,
    [Feedback] NVARCHAR(2000) NULL,
    [ReviewedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.DocumentSubmission','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentSubmission] (
    [SubmissionID] BIGINT IDENTITY(1,1) NOT NULL,
    [RequirementID] BIGINT NOT NULL,
    [DocumentVersionID] BIGINT NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [SubmittedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_DocumentSubmission_SubmittedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.DocumentTypeCatalog','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentTypeCatalog] (
    [TypeCode] VARCHAR(50) NOT NULL,
    [DisplayName] NVARCHAR(200) NOT NULL,
    [Description] NVARCHAR(1000) NULL,
    [IsActive] BIT NOT NULL
        CONSTRAINT [DF_Base_DocumentTypeCatalog_IsActive] DEFAULT ((1))
)';

IF OBJECT_ID('dbo.DocumentVersion','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentVersion] (
    [DocumentVersionID] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentID] BIGINT NOT NULL,
    [VersionNo] INT NOT NULL,
    [DocumentNumber] VARCHAR(50) NULL,
    [FileKey] NVARCHAR(1024) NOT NULL,
    [FileHash] VARCHAR(128) NULL,
    [IssuedDate] DATE NULL,
    [ExpiryDate] DATE NULL,
    [UploadedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_DocumentVersion_UploadedAt] DEFAULT (sysutcdatetime()),
    [UploadedBy] BIGINT NULL,
    [OriginalFileName] NVARCHAR(255) NULL,
    [ContentType] VARCHAR(100) NULL,
    [FileSizeBytes] BIGINT NULL
)';

IF OBJECT_ID('dbo.FleetVehicle','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[FleetVehicle] (
    [VehicleID] BIGINT IDENTITY(1,1) NOT NULL,
    [VehicleCode] VARCHAR(50) NOT NULL,
    [PlateNumber] VARCHAR(50) NOT NULL,
    [HorseCapacity] INT NOT NULL,
    [ClimateSystemType] VARCHAR(50) NOT NULL,
    [OperationalStatus] VARCHAR(50) NOT NULL,
    [OwnerSupplierID] BIGINT NULL,
    [MaintenanceFrom] DATETIME2(0) NULL,
    [MaintenanceTo] DATETIME2(0) NULL,
    [DocumentExpiresAt] DATETIME2(0) NULL,
    [AssetNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.FlightReservation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[FlightReservation] (
    [ReservationID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [OfferID] BIGINT NOT NULL,
    [AirlineName] VARCHAR(50) NOT NULL,
    [FlightNumber] VARCHAR(50) NOT NULL,
    [ReservationReference] VARCHAR(150) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [ConfirmedAt] DATETIME2(0) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [ConfirmedBy] BIGINT NULL,
    [SupplierContact] NVARCHAR(500) NULL,
    [UsableCapacitySlots] DECIMAL(5,1) NULL
)';

IF OBJECT_ID('dbo.Horse','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Horse] (
    [HorseID] BIGINT IDENTITY(1,1) NOT NULL,
    [CustomerID] BIGINT NOT NULL,
    [MicrochipID] VARCHAR(50) NULL,
    [PassportNumber] VARCHAR(50) NULL,
    [Name] NVARCHAR(150) NOT NULL,
    [Breed] NVARCHAR(100) NULL,
    [DateOfBirth] DATE NULL,
    [SexCode] VARCHAR(20) NULL,
    [WeightKg] DECIMAL(8,2) NULL,
    [HeightCm] DECIMAL(6,2) NULL,
    [BodyWidthCm] DECIMAL(6,2) NULL,
    [MeasuredAt] DATETIME2(0) NULL,
    [SpecialCareNotes] NVARCHAR(1000) NULL,
    [HandlingNotes] NVARCHAR(1000) NULL,
    [FeedWaterRequirements] NVARCHAR(1000) NULL,
    [StallPreferences] NVARCHAR(1000) NULL,
    [VeterinaryInstructions] NVARCHAR(2000) NULL,
    [VeterinaryInstructionSource] NVARCHAR(1000) NULL,
    [RecordVersion] TIMESTAMP NOT NULL
)';

IF OBJECT_ID('dbo.HorseDocument','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[HorseDocument] (
    [DocumentID] BIGINT IDENTITY(1,1) NOT NULL,
    [HorseID] BIGINT NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_HorseDocument_CreatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.HorseReceipt','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[HorseReceipt] (
    [ReceiptID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [ReceivedBy] BIGINT NOT NULL,
    [ObservedMicrochip] VARCHAR(50) NULL,
    [ConditionNotes] NVARCHAR(2000) NULL,
    [ReceiptStatus] VARCHAR(50) NOT NULL,
    [ReceivedAt] DATETIME2(0) NOT NULL,
    [LegID] BIGINT NULL,
    [ObservedPassportNumber] VARCHAR(50) NULL,
    [IdentityCheckResult] VARCHAR(20) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [LocationDescription] NVARCHAR(500) NULL
)';

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

IF OBJECT_ID('dbo.InsuranceChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[InsuranceChoice] (
    [InsuranceChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [CoverageCode] VARCHAR(50) NOT NULL,
    [DeclaredValue] DECIMAL(18,2) NULL,
    [ValueCurrency] VARCHAR(3) NULL,
    [ExistingPolicyReference] NVARCHAR(100) NULL
)';

IF OBJECT_ID('dbo.InsurancePolicy','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[InsurancePolicy] (
    [PolicyID] BIGINT IDENTITY(1,1) NOT NULL,
    [InsuranceChoiceID] BIGINT NOT NULL,
    [InsurerName] NVARCHAR(300) NOT NULL,
    [PolicyNumber] VARCHAR(150) NOT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [CoverageFrom] DATETIME2(0) NOT NULL,
    [CoverageTo] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [VerifiedBy] BIGINT NULL,
    [VerifiedAt] DATETIME2(0) NULL,
    [PolicyTerms] NVARCHAR(MAX) NULL
)';

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

IF OBJECT_ID('dbo.Notification','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Notification] (
    [NotificationID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [OrderID] BIGINT NULL,
    [Title] NVARCHAR(300) NOT NULL,
    [Message] NVARCHAR(2000) NOT NULL,
    [ReadAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Notification_CreatedAt] DEFAULT (sysutcdatetime())
)';

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

IF OBJECT_ID('dbo.OrderAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderAssignment] (
    [AssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [StaffID] BIGINT NOT NULL,
    [AssignmentRole] VARCHAR(30) NOT NULL,
    [AssignedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderAssignment_AssignedAt] DEFAULT (sysutcdatetime()),
    [EndedAt] DATETIME2(0) NULL,
    [AssignedBy] BIGINT NULL,
    [DueAt] DATETIME2(0) NULL,
    [AssignmentNote] NVARCHAR(1000) NULL
)';

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

IF OBJECT_ID('dbo.OrderDecision','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderDecision] (
    [DecisionID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ManagerID] BIGINT NOT NULL,
    [Decision] VARCHAR(20) NOT NULL,
    [Reason] NVARCHAR(1000) NULL,
    [DecidedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderDecision_DecidedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.OrderRevision','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderRevision] (
    [RevisionID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [RevisionNo] INT NOT NULL,
    [OrderSnapshot] NVARCHAR(MAX) NOT NULL,
    [ChangeReason] NVARCHAR(500) NULL,
    [SubmittedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderRevision_SubmittedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.OrderServiceChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderServiceChoice] (
    [ChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [OptionCode] VARCHAR(50) NOT NULL,
    [ArrangedBy] VARCHAR(20) NOT NULL,
    [ChosenAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderServiceChoice_ChosenAt] DEFAULT (sysutcdatetime()),
    [SelectionInputsJSON] NVARCHAR(MAX) NULL,
    [ProviderName] NVARCHAR(200) NULL,
    [ProviderContact] NVARCHAR(500) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL
)';

IF OBJECT_ID('dbo.PaymentAllocation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentAllocation] (
    [AllocationID] BIGINT IDENTITY(1,1) NOT NULL,
    [TransactionID] BIGINT NOT NULL,
    [PaymentRequestID] BIGINT NOT NULL,
    [AllocatedAmount] DECIMAL(18,2) NOT NULL,
    [AllocatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_PaymentAllocation_AllocatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.PaymentReport','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentReport] (
    [ReportID] BIGINT IDENTITY(1,1) NOT NULL,
    [PaymentRequestID] BIGINT NOT NULL,
    [ReportedAmount] DECIMAL(18,2) NOT NULL,
    [BankReference] VARCHAR(150) NULL,
    [ProofFileKey] NVARCHAR(1024) NULL,
    [CustomerReportedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_PaymentReport_CustomerReportedAt] DEFAULT (sysutcdatetime()),
    [Status] VARCHAR(50) NOT NULL,
    [TransferredAt] DATETIME2(0) NULL,
    [ReportedCurrency] VARCHAR(3) NULL
)';

IF OBJECT_ID('dbo.PaymentRequest','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentRequest] (
    [PaymentRequestID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [PaymentNumber] VARCHAR(50) NOT NULL,
    [PaymentStage] VARCHAR(50) NOT NULL,
    [AmountDue] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [DueAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [ScheduleID] BIGINT NULL
)';

IF OBJECT_ID('dbo.PaymentTransaction','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentTransaction] (
    [TransactionID] BIGINT IDENTITY(1,1) NOT NULL,
    [ReconciledBy] BIGINT NULL,
    [RecipientAccount] VARCHAR(150) NOT NULL,
    [BankTransactionReference] VARCHAR(150) NOT NULL,
    [AmountReceived] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [PostedAt] DATETIME2(0) NOT NULL,
    [ReconciledAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [VerificationSource] NVARCHAR(500) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [InternalNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.QuarantineChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuarantineChoice] (
    [QuarantineChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [PackageCode] VARCHAR(50) NOT NULL,
    [RequiredDays] INT NULL,
    [ConfirmedBy] BIGINT NULL,
    [DaysConfirmedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.QuarantineReservation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuarantineReservation] (
    [ReservationID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuarantineChoiceID] BIGINT NOT NULL,
    [FacilityName] NVARCHAR(300) NOT NULL,
    [ReservationReference] VARCHAR(150) NULL,
    [StartsAt] DATETIME2(0) NOT NULL,
    [EndsAt] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [SupplierID] BIGINT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [ConfirmedBy] BIGINT NULL,
    [ConfirmedAt] DATETIME2(0) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [FacilityContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.Quote','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Quote] (
    [QuoteID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ApprovedBy] BIGINT NULL,
    [QuoteNumber] VARCHAR(30) NOT NULL,
    [VersionNo] INT NOT NULL
        CONSTRAINT [DF_Base_Quote_VersionNo] DEFAULT ((1)),
    [Currency] VARCHAR(3) NOT NULL,
    [TotalQuotedAmount] DECIMAL(18,2) NULL,
    [QuoteStatus] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_Quote_QuoteStatus] DEFAULT (''DRAFT''),
    [ApprovedAt] DATETIME2(0) NULL,
    [IssuedAt] DATETIME2(0) NULL,
    [ValidUntil] DATETIME2(0) NULL,
    [AcceptedAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Quote_CreatedAt] DEFAULT (sysutcdatetime()),
    [PreparedBy] BIGINT NULL,
    [SubmittedAt] DATETIME2(0) NULL,
    [IssuedBy] BIGINT NULL,
    [AcceptedByCustomerID] BIGINT NULL,
    [TermsVersion] VARCHAR(50) NULL,
    [TermsText] NVARCHAR(MAX) NULL,
    [CancellationTerms] NVARCHAR(MAX) NULL,
    [TermsFileKey] NVARCHAR(1024) NULL,
    [ReviewFeedback] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.QuoteItem','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuoteItem] (
    [ItemID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [TariffID] BIGINT NULL,
    [OfferID] BIGINT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [CostCategory] VARCHAR(50) NOT NULL,
    [Description] NVARCHAR(2000) NOT NULL,
    [UnitCode] VARCHAR(50) NOT NULL,
    [Quantity] DECIMAL(18,4) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Amount] DECIMAL(18,2) NOT NULL,
    [CalculationInputs] NVARCHAR(MAX) NULL
)';

IF OBJECT_ID('dbo.QuotePaymentSchedule','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuotePaymentSchedule] (
    [ScheduleID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [SequenceNo] INT NOT NULL,
    [PaymentStage] VARCHAR(50) NOT NULL,
    [AmountDue] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [DueAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.Refund','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Refund] (
    [RefundID] BIGINT IDENTITY(1,1) NOT NULL,
    [AllocationID] BIGINT NOT NULL,
    [Amount] DECIMAL(18,2) NOT NULL,
    [Reason] NVARCHAR(2000) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [RefundedAt] DATETIME2(0) NULL,
    [RequestedBy] BIGINT NULL,
    [RequestedAt] DATETIME2(0) NULL,
    [ApprovedBy] BIGINT NULL,
    [ApprovedAt] DATETIME2(0) NULL,
    [BankRefundReference] VARCHAR(150) NULL,
    [ProofFileKey] NVARCHAR(1024) NULL
)';

IF OBJECT_ID('dbo.RevisionQuarantineAssessment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[RevisionQuarantineAssessment] (
    [AssessmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [Applicability] VARCHAR(20) NOT NULL,
    [RequiredDays] INT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [AssessedAt] DATETIME2(0) NOT NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [SourceReference] NVARCHAR(1000) NOT NULL,
    [SupersededAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.RevisionVeterinaryAssessment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[RevisionVeterinaryAssessment] (
    [AssessmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [VisitCount] INT NOT NULL,
    [TestCount] INT NOT NULL,
    [DoseCount] INT NOT NULL,
    [CertificateCount] INT NOT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [AssessedAt] DATETIME2(0) NOT NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [SourceReference] NVARCHAR(1000) NOT NULL,
    [SupersededAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.Role','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Role] (
    [RoleID] BIGINT IDENTITY(1,1) NOT NULL,
    [RoleCode] VARCHAR(30) NOT NULL,
    [Description] NVARCHAR(255) NULL
)';

IF OBJECT_ID('dbo.ServiceTariff','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ServiceTariff] (
    [TariffID] BIGINT IDENTITY(1,1) NOT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [OptionCode] VARCHAR(50) NOT NULL,
    [CostGroup] VARCHAR(50) NOT NULL,
    [UnitCode] VARCHAR(30) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_Currency] DEFAULT (''USD''),
    [VersionNo] INT NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_VersionNo] DEFAULT ((1)),
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_Status] DEFAULT (''ACTIVE'')
)';

IF OBJECT_ID('dbo.StaffProfile','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StaffProfile] (
    [StaffID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [FullName] NVARCHAR(150) NOT NULL,
    [Phone] VARCHAR(30) NULL,
    [LicenseNumber] VARCHAR(50) NULL
)';

IF OBJECT_ID('dbo.StallAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallAssignment] (
    [StallAssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [StallID] BIGINT NOT NULL,
    [SlotQuantity] DECIMAL(5,1) NOT NULL,
    [SharingCode] VARCHAR(50) NOT NULL,
    [FitStatus] VARCHAR(50) NOT NULL,
    [CarrierAcceptanceStatus] VARCHAR(50) NOT NULL,
    [AssignedFrom] DATETIME2(0) NULL,
    [AssignedTo] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [PositionDescription] NVARCHAR(500) NULL,
    [CarrierApprovalFileKey] NVARCHAR(1024) NULL,
    [CarrierApprovedAt] DATETIME2(0) NULL,
    [CarrierContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.StallChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallChoice] (
    [StallChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [SlotQuantity] DECIMAL(3,1) NOT NULL,
    [SharingCode] VARCHAR(20) NOT NULL,
    [FitStatus] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_StallChoice_FitStatus] DEFAULT (''PENDING'')
)';

IF OBJECT_ID('dbo.StallEquipment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallEquipment] (
    [StallID] BIGINT IDENTITY(1,1) NOT NULL,
    [StallCode] VARCHAR(50) NOT NULL,
    [CapacitySlots] DECIMAL(5,1) NOT NULL,
    [InternalHeightCm] DECIMAL(8,2) NOT NULL,
    [InternalWidthCm] DECIMAL(8,2) NOT NULL,
    [EquipmentStatus] VARCHAR(50) NOT NULL,
    [OwnerSupplierID] BIGINT NULL,
    [WeightLimitKg] DECIMAL(8,2) NULL,
    [MaintenanceFrom] DATETIME2(0) NULL,
    [MaintenanceTo] DATETIME2(0) NULL,
    [DocumentExpiresAt] DATETIME2(0) NULL,
    [AssetNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallFitPolicy] (
    [PolicyID] BIGINT IDENTITY(1,1) NOT NULL,
    [PolicyCode] VARCHAR(50) NOT NULL,
    [VersionNo] INT NOT NULL,
    [StallID] BIGINT NULL,
    [CarrierSupplierID] BIGINT NULL,
    [SlotQuantity] DECIMAL(5,1) NOT NULL,
    [MaxWeightKg] DECIMAL(8,2) NULL,
    [MaxHeightCm] DECIMAL(8,2) NULL,
    [MaxWidthCm] DECIMAL(8,2) NULL,
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [SourceURL] NVARCHAR(2048) NULL,
    [SourceFileKey] NVARCHAR(1024) NULL,
    [ReviewedBy] BIGINT NULL,
    [ReviewedAt] DATETIME2(0) NULL,
    [Status] VARCHAR(20) NOT NULL
)';

IF OBJECT_ID('dbo.Supplier','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Supplier] (
    [SupplierID] BIGINT IDENTITY(1,1) NOT NULL,
    [Name] NVARCHAR(200) NOT NULL,
    [ContactName] NVARCHAR(150) NULL,
    [Email] VARCHAR(255) NULL,
    [Phone] VARCHAR(30) NULL,
    [ServicesOfferedJSON] NVARCHAR(MAX) NULL,
    [IsActive] BIT NOT NULL
        CONSTRAINT [DF_Base_Supplier_IsActive] DEFAULT ((1)),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Supplier_CreatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.SupplierOffer','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[SupplierOffer] (
    [OfferID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ProviderName] NVARCHAR(200) NOT NULL,
    [OfferReference] VARCHAR(100) NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [UnitCode] VARCHAR(30) NOT NULL,
    [Quantity] DECIMAL(18,4) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [IncludesStall] BIT NOT NULL
        CONSTRAINT [DF_Base_SupplierOffer_IncludesStall] DEFAULT ((0)),
    [IncludedCharges] NVARCHAR(MAX) NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_SupplierOffer_Status] DEFAULT (''RECEIVED''),
    [SupplierID] BIGINT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [SourceContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.TransportOrder','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportOrder] (
    [OrderID] BIGINT IDENTITY(1,1) NOT NULL,
    [CustomerID] BIGINT NOT NULL,
    [OrderNumber] VARCHAR(30) NOT NULL,
    [OriginCountryCode] VARCHAR(2) NULL,
    [DestinationCountryCode] VARCHAR(2) NULL,
    [OriginAddress] NVARCHAR(500) NULL,
    [DestinationAddress] NVARCHAR(500) NULL,
    [OriginTimezone] VARCHAR(100) NULL,
    [DestinationTimezone] VARCHAR(100) NULL,
    [DeliveryDeadline] DATETIME2(0) NULL,
    [EventStartAt] DATETIME2(0) NULL,
    [Currency] VARCHAR(3) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_Currency] DEFAULT (''USD''),
    [OrderStatus] VARCHAR(30) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_OrderStatus] DEFAULT (''DRAFT''),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_CreatedAt] DEFAULT (sysutcdatetime()),
    [PreferredDepartureAt] DATETIME2(0) NULL,
    [DateFlexibilityNotes] NVARCHAR(500) NULL,
    [ReceiverName] NVARCHAR(150) NULL,
    [ReceiverPhone] VARCHAR(30) NULL,
    [ReceiverEmail] VARCHAR(255) NULL,
    [ReceiverAuthority] NVARCHAR(500) NULL,
    [ImportType] VARCHAR(20) NULL,
    [EventName] NVARCHAR(200) NULL,
    [EventVenue] NVARCHAR(300) NULL,
    [EventTimezone] VARCHAR(100) NULL,
    [TransitAirportsJSON] NVARCHAR(MAX) NULL,
    [DraftPayloadJSON] NVARCHAR(MAX) NULL,
    [UpdatedAt] DATETIME2(0) NULL,
    [CurrentRevisionID] BIGINT NULL,
    [CurrentPlanID] BIGINT NULL,
    [RecordVersion] TIMESTAMP NOT NULL
)';

IF OBJECT_ID('dbo.TransportOrderDetail','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportOrderDetail] (
    [DetailID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [HorseID] BIGINT NOT NULL,
    [SnapshotMicrochip] VARCHAR(50) NULL,
    [SnapshotSexCode] VARCHAR(20) NULL,
    [SnapshotWeightKg] DECIMAL(8,2) NULL,
    [SnapshotHeightCm] DECIMAL(6,2) NULL,
    [SnapshotBodyWidthCm] DECIMAL(6,2) NULL,
    [SnapshotMeasuredAt] DATETIME2(0) NULL,
    [SpecialNeeds] NVARCHAR(1000) NULL
)';

IF OBJECT_ID('dbo.TransportPlan','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportPlan] (
    [PlanID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [CreatedBy] BIGINT NOT NULL,
    [DepartureAuthorizedBy] BIGINT NULL,
    [VersionNo] INT NOT NULL,
    [PlannedStartTime] DATETIME2(0) NULL,
    [PlannedEndTime] DATETIME2(0) NULL,
    [FinancialGateStatus] VARCHAR(50) NOT NULL,
    [OperationalGateStatus] VARCHAR(50) NOT NULL,
    [DocGateStatus] VARCHAR(50) NOT NULL,
    [GatesCheckedAt] DATETIME2(0) NULL,
    [DepartureAuthorizedAt] DATETIME2(0) NULL,
    [PlanStatus] VARCHAR(50) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [AcceptedQuoteID] BIGINT NULL
)';

IF OBJECT_ID('dbo.TripAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TripAssignment] (
    [AssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [StaffID] BIGINT NOT NULL,
    [RoleInTrip] VARCHAR(50) NOT NULL,
    [AssignedFrom] DATETIME2(0) NULL,
    [AssignedTo] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [BufferMinutes] INT NULL,
    [ConflictNotes] NVARCHAR(1000) NULL
)';

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

IF OBJECT_ID('dbo.TripLeg','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TripLeg] (
    [LegID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [VehicleID] BIGINT NULL,
    [SequenceNo] INT NOT NULL,
    [TransportMode] VARCHAR(50) NOT NULL,
    [StartPoint] NVARCHAR(300) NOT NULL,
    [EndPoint] NVARCHAR(300) NOT NULL,
    [DistanceKm] DECIMAL(12,3) NOT NULL,
    [PlannedDeparture] DATETIME2(0) NULL,
    [PlannedArrival] DATETIME2(0) NULL,
    [ActualDeparture] DATETIME2(0) NULL,
    [ActualArrival] DATETIME2(0) NULL,
    [LegStatus] VARCHAR(50) NOT NULL,
    [StartTimezone] VARCHAR(100) NULL,
    [EndTimezone] VARCHAR(100) NULL,
    [SupplierID] BIGINT NULL,
    [PlannedFlightNumber] VARCHAR(50) NULL,
    [ConnectionBufferMinutes] INT NULL,
    [RestDurationMinutes] INT NULL,
    [CareInstructions] NVARCHAR(2000) NULL,
    [DistanceSource] NVARCHAR(1000) NULL,
    [DistanceConfirmedBy] BIGINT NULL,
    [DistanceConfirmedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.User','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[User] (
    [UserID] BIGINT IDENTITY(1,1) NOT NULL,
    [RoleID] BIGINT NOT NULL,
    [Email] VARCHAR(255) NOT NULL,
    [PasswordHash] VARCHAR(255) NOT NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_User_Status] DEFAULT (''ACTIVE''),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_User_CreatedAt] DEFAULT (sysutcdatetime())
)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD CONSTRAINT [PK_ActualCost] PRIMARY KEY ([CostID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_PlanID] ON dbo.[ActualCost] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_LegID] ON dbo.[ActualCost] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] ADD CONSTRAINT [PK_AuditEvent] PRIMARY KEY ([EventID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND name='IX_AuditEvent_UserID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuditEvent_UserID] ON dbo.[AuditEvent] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] ADD CONSTRAINT [PK_AuthenticationToken] PRIMARY KEY ([TokenID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='UQ_AuthenticationToken_Hash')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] ADD CONSTRAINT [UQ_AuthenticationToken_Hash] UNIQUE ([TokenHash])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='IX_AuthenticationToken_UserPurpose')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuthenticationToken_UserPurpose] ON dbo.[AuthenticationToken] ([UserID],[Purpose])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthorityProcedure') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD CONSTRAINT [PK_AuthorityProcedure] PRIMARY KEY ([ProcedureID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='IX_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuthorityProcedure_RevisionID] ON dbo.[AuthorityProcedure] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD CONSTRAINT [PK_ComplianceClearance] PRIMARY KEY ([ClearanceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='IX_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ComplianceClearance_RevisionID] ON dbo.[ComplianceClearance] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='IX_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ComplianceClearance_SpecialistID] ON dbo.[ComplianceClearance] ([SpecialistID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD CONSTRAINT [PK_ComplianceRuleVersion] PRIMARY KEY ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='UQ_ComplianceRuleVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD CONSTRAINT [UQ_ComplianceRuleVersion_Version] UNIQUE ([RuleCode],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Customer') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD CONSTRAINT [PK_Customer] PRIMARY KEY ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Customer') AND name='UQ_Customer_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD CONSTRAINT [UQ_Customer_UserID] UNIQUE ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD CONSTRAINT [PK_DocumentRequirement] PRIMARY KEY ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='IX_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentRequirement_RevisionID] ON dbo.[DocumentRequirement] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='IX_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentRequirement_RuleID] ON dbo.[DocumentRequirement] ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] ADD CONSTRAINT [PK_DocumentReview] PRIMARY KEY ([ReviewID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND name='IX_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentReview_SubmissionID] ON dbo.[DocumentReview] ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND name='IX_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentReview_ReviewedBy] ON dbo.[DocumentReview] ([ReviewedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] ADD CONSTRAINT [PK_DocumentSubmission] PRIMARY KEY ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='IX_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentSubmission_RequirementID] ON dbo.[DocumentSubmission] ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='IX_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentSubmission_DocumentVersionID] ON dbo.[DocumentSubmission] ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentTypeCatalog') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentTypeCatalog] ADD CONSTRAINT [PK_DocumentTypeCatalog] PRIMARY KEY ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentVersion') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD CONSTRAINT [PK_DocumentVersion] PRIMARY KEY ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentVersion') AND name='UQ_DocumentVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD CONSTRAINT [UQ_DocumentVersion_Version] UNIQUE ([DocumentID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [PK_FleetVehicle] PRIMARY KEY ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND name='UQ_FleetVehicle_PlateNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [UQ_FleetVehicle_PlateNumber] UNIQUE ([PlateNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND name='UQ_FleetVehicle_VehicleCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [UQ_FleetVehicle_VehicleCode] UNIQUE ([VehicleCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD CONSTRAINT [PK_FlightReservation] PRIMARY KEY ([ReservationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND name='IX_FlightReservation_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_FlightReservation_LegID] ON dbo.[FlightReservation] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND name='IX_FlightReservation_OfferID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_FlightReservation_OfferID] ON dbo.[FlightReservation] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Horse') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD CONSTRAINT [PK_Horse] PRIMARY KEY ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Horse') AND name='UX_Horse_MicrochipID')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_Horse_MicrochipID] ON dbo.[Horse] ([MicrochipID]) WHERE ([MicrochipID] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseDocument') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] ADD CONSTRAINT [PK_HorseDocument] PRIMARY KEY ([DocumentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseDocument') AND name='IX_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseDocument_HorseID] ON dbo.[HorseDocument] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD CONSTRAINT [PK_HorseReceipt] PRIMARY KEY ([ReceiptID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND name='IX_HorseReceipt_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseReceipt_PlanID] ON dbo.[HorseReceipt] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND name='IX_HorseReceipt_ReceivedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseReceipt_ReceivedBy] ON dbo.[HorseReceipt] ([ReceivedBy])';
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
    object_id=OBJECT_ID('dbo.InsuranceChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] ADD CONSTRAINT [PK_InsuranceChoice] PRIMARY KEY ([InsuranceChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='UQ_InsuranceChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] ADD CONSTRAINT [UQ_InsuranceChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsurancePolicy') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD CONSTRAINT [PK_InsurancePolicy] PRIMARY KEY ([PolicyID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='IX_InsurancePolicy_InsuranceChoiceID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_InsurancePolicy_InsuranceChoiceID] ON dbo.[InsurancePolicy] ([InsuranceChoiceID])';
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
    object_id=OBJECT_ID('dbo.Notification') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] ADD CONSTRAINT [PK_Notification] PRIMARY KEY ([NotificationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification') AND name='IX_Notification_UserID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Notification_UserID] ON dbo.[Notification] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification') AND name='IX_Notification_OrderID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Notification_OrderID] ON dbo.[Notification] ([OrderID])';
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
    object_id=OBJECT_ID('dbo.OrderAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] ADD CONSTRAINT [PK_OrderAssignment] PRIMARY KEY ([AssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderAssignment') AND name='UX_OrderAssignment_ActiveRole')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_OrderAssignment_ActiveRole] ON dbo.[OrderAssignment] ([OrderID],[AssignmentRole]) WHERE ([EndedAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderAssignment') AND name='IX_OrderAssignment_ActiveStaff')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OrderAssignment_ActiveStaff] ON dbo.[OrderAssignment] ([StaffID],[OrderID]) WHERE ([EndedAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderChangeRequest') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD CONSTRAINT [PK_OrderChangeRequest] PRIMARY KEY ([ChangeRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderDecision') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] ADD CONSTRAINT [PK_OrderDecision] PRIMARY KEY ([DecisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderDecision') AND name='UX_OrderDecision_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] ADD CONSTRAINT [UX_OrderDecision_Revision] UNIQUE ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [PK_OrderRevision] PRIMARY KEY ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND name='UQ_OrderRevision_Order_RevisionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [UQ_OrderRevision_Order_RevisionNo] UNIQUE ([OrderID],[RevisionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND name='UQ_Figma_OrderRevision_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [UQ_Figma_OrderRevision_Parent] UNIQUE ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderServiceChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD CONSTRAINT [PK_OrderServiceChoice] PRIMARY KEY ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='UQ_OrderServiceChoice_Revision_Service')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD CONSTRAINT [UQ_OrderServiceChoice_Revision_Service] UNIQUE ([RevisionID],[ServiceCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] ADD CONSTRAINT [PK_PaymentAllocation] PRIMARY KEY ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='IX_PaymentAllocation_TransactionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentAllocation_TransactionID] ON dbo.[PaymentAllocation] ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='IX_PaymentAllocation_PaymentRequestID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentAllocation_PaymentRequestID] ON dbo.[PaymentAllocation] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentReport') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD CONSTRAINT [PK_PaymentReport] PRIMARY KEY ([ReportID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentReport') AND name='IX_PaymentReport_PaymentRequestID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentReport_PaymentRequestID] ON dbo.[PaymentReport] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD CONSTRAINT [PK_PaymentRequest] PRIMARY KEY ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND name='UQ_PaymentRequest_PaymentNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD CONSTRAINT [UQ_PaymentRequest_PaymentNumber] UNIQUE ([PaymentNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND name='IX_PaymentRequest_QuoteID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentRequest_QuoteID] ON dbo.[PaymentRequest] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD CONSTRAINT [PK_PaymentTransaction] PRIMARY KEY ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='UQ_PaymentTransaction_BankReference')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD CONSTRAINT [UQ_PaymentTransaction_BankReference] UNIQUE ([RecipientAccount],[BankTransactionReference])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='IX_PaymentTransaction_ReconciledBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentTransaction_ReconciledBy] ON dbo.[PaymentTransaction] ([ReconciledBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] ADD CONSTRAINT [PK_QuarantineChoice] PRIMARY KEY ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='UQ_QuarantineChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] ADD CONSTRAINT [UQ_QuarantineChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineReservation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD CONSTRAINT [PK_QuarantineReservation] PRIMARY KEY ([ReservationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='IX_QuarantineReservation_QuarantineChoiceID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuarantineReservation_QuarantineChoiceID] ON dbo.[QuarantineReservation] ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [PK_Quote] PRIMARY KEY ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Quote_Revision_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Quote_Revision_Version] UNIQUE ([RevisionID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Quote_QuoteNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Quote_QuoteNumber] UNIQUE ([QuoteNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Figma_Quote_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Figma_Quote_Parent] UNIQUE ([QuoteID],[RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] ADD CONSTRAINT [PK_QuoteItem] PRIMARY KEY ([ItemID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_QuoteID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_QuoteID] ON dbo.[QuoteItem] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_TariffID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_TariffID] ON dbo.[QuoteItem] ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_OfferID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_OfferID] ON dbo.[QuoteItem] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [PK_QuotePaymentSchedule] PRIMARY KEY ([ScheduleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='UQ_QuotePaymentSchedule_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [UQ_QuotePaymentSchedule_Parent] UNIQUE ([ScheduleID],[QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='UQ_QuotePaymentSchedule_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [UQ_QuotePaymentSchedule_Sequence] UNIQUE ([QuoteID],[SequenceNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD CONSTRAINT [PK_Refund] PRIMARY KEY ([RefundID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND name='IX_Refund_AllocationID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Refund_AllocationID] ON dbo.[Refund] ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] ADD CONSTRAINT [PK__Revision__3D2BF83E1E42ACCE] PRIMARY KEY ([AssessmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='UX_QuarantineAssessment_Current')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_QuarantineAssessment_Current] ON dbo.[RevisionQuarantineAssessment] ([RevisionID]) WHERE ([SupersededAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] ADD CONSTRAINT [PK__Revision__3D2BF83EB0D2F0D7] PRIMARY KEY ([AssessmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='UX_VeterinaryAssessment_Current')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_VeterinaryAssessment_Current] ON dbo.[RevisionVeterinaryAssessment] ([RevisionID]) WHERE ([SupersededAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Role') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Role] ADD CONSTRAINT [PK_Role] PRIMARY KEY ([RoleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Role') AND name='UQ_Role_RoleCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Role] ADD CONSTRAINT [UQ_Role_RoleCode] UNIQUE ([RoleCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ServiceTariff') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] ADD CONSTRAINT [PK_ServiceTariff] PRIMARY KEY ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ServiceTariff') AND name='UQ_ServiceTariff_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] ADD CONSTRAINT [UQ_ServiceTariff_Version] UNIQUE ([ServiceCode],[OptionCode],[Currency],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StaffProfile') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] ADD CONSTRAINT [PK_StaffProfile] PRIMARY KEY ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StaffProfile') AND name='UQ_StaffProfile_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] ADD CONSTRAINT [UQ_StaffProfile_UserID] UNIQUE ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD CONSTRAINT [PK_StallAssignment] PRIMARY KEY ([StallAssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND name='IX_StallAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_StallAssignment_LegID] ON dbo.[StallAssignment] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND name='IX_StallAssignment_StallID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_StallAssignment_StallID] ON dbo.[StallAssignment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] ADD CONSTRAINT [PK_StallChoice] PRIMARY KEY ([StallChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallChoice') AND name='UQ_StallChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] ADD CONSTRAINT [UQ_StallChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallEquipment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD CONSTRAINT [PK_StallEquipment] PRIMARY KEY ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallEquipment') AND name='UQ_StallEquipment_StallCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD CONSTRAINT [UQ_StallEquipment_StallCode] UNIQUE ([StallCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallFitPolicy') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] ADD CONSTRAINT [PK_StallFitPolicy] PRIMARY KEY ([PolicyID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='UQ_StallFitPolicy_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] ADD CONSTRAINT [UQ_StallFitPolicy_Version] UNIQUE ([PolicyCode],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Supplier') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] ADD CONSTRAINT [PK_Supplier] PRIMARY KEY ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.SupplierOffer') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD CONSTRAINT [PK_SupplierOffer] PRIMARY KEY ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrder') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD CONSTRAINT [PK_TransportOrder] PRIMARY KEY ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrder') AND name='UQ_TransportOrder_OrderNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD CONSTRAINT [UQ_TransportOrder_OrderNumber] UNIQUE ([OrderNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrderDetail') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] ADD CONSTRAINT [PK_TransportOrderDetail] PRIMARY KEY ([DetailID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='UQ_TransportOrderDetail_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] ADD CONSTRAINT [UQ_TransportOrderDetail_OrderID] UNIQUE ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [PK_TransportPlan] PRIMARY KEY ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='UQ_TransportPlan_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [UQ_TransportPlan_Version] UNIQUE ([RevisionID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='IX_TransportPlan_CreatedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TransportPlan_CreatedBy] ON dbo.[TransportPlan] ([CreatedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='IX_TransportPlan_DepartureAuthorizedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TransportPlan_DepartureAuthorizedBy] ON dbo.[TransportPlan] ([DepartureAuthorizedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='UQ_Figma_TransportPlan_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [UQ_Figma_TransportPlan_Parent] UNIQUE ([PlanID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD CONSTRAINT [PK_TripAssignment] PRIMARY KEY ([AssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND name='IX_TripAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripAssignment_LegID] ON dbo.[TripAssignment] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND name='IX_TripAssignment_StaffID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripAssignment_StaffID] ON dbo.[TripAssignment] ([StaffID])';
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
    object_id=OBJECT_ID('dbo.TripLeg') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [PK_TripLeg] PRIMARY KEY ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='UQ_TripLeg_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [UQ_TripLeg_Sequence] UNIQUE ([PlanID],[SequenceNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='IX_TripLeg_VehicleID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripLeg_VehicleID] ON dbo.[TripLeg] ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='UQ_Figma_TripLeg_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [UQ_Figma_TripLeg_Parent] UNIQUE ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.User') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] ADD CONSTRAINT [PK_User] PRIMARY KEY ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.User') AND name='UQ_User_Email')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] ADD CONSTRAINT [UQ_User_Email] UNIQUE ([Email])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='CK_ActualCost_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [CK_ActualCost_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_LegID] FOREIGN KEY ([LegID],[PlanID]) REFERENCES dbo.[TripLeg] ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuditEvent') AND name='CK_AuditEvent_ChangeSnapshot_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] WITH CHECK ADD CONSTRAINT [CK_AuditEvent_ChangeSnapshot_JSON] CHECK ([ChangeSnapshot] IS NULL OR isjson([ChangeSnapshot])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuditEvent') AND name='FK_AuditEvent_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] WITH CHECK ADD CONSTRAINT [FK_AuditEvent_UserID] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='CK_AuthenticationToken_Expiry')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [CK_AuthenticationToken_Expiry] CHECK ([ExpiresAt]>[CreatedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='CK_AuthenticationToken_Purpose')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [CK_AuthenticationToken_Purpose] CHECK ([Purpose]=''RESET_PASSWORD'' OR [Purpose]=''VERIFY_EMAIL'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='FK_AuthenticationToken_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [FK_AuthenticationToken_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='FK_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT [FK_AuthorityProcedure_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='FK_Figma_AuthorityProcedure_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT [FK_Figma_AuthorityProcedure_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='FK_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] WITH CHECK ADD CONSTRAINT [FK_ComplianceClearance_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='FK_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] WITH CHECK ADD CONSTRAINT [FK_ComplianceClearance_SpecialistID] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='CK_ComplianceRuleVersion_EffectiveTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [CK_ComplianceRuleVersion_EffectiveTo_Range] CHECK ([EffectiveTo] IS NULL OR [EffectiveFrom] IS NOT NULL AND [EffectiveTo]>=[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='CK_ComplianceRuleVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [CK_ComplianceRuleVersion_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='FK_Figma_ComplianceRuleVersion_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [FK_Figma_ComplianceRuleVersion_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Customer') AND name='FK_Customer_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] WITH CHECK ADD CONSTRAINT [FK_Customer_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='CK_Figma_DocumentRequirement_Scope')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentRequirement_Scope] CHECK ([ScopeCode] IS NULL OR [ScopeCode]=''HORSE'' AND [HorseID] IS NOT NULL OR [ScopeCode]=''SHIPMENT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='CK_Figma_DocumentRequirement_ValidityConditionsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentRequirement_ValidityConditionsJSON] CHECK ([ValidityConditionsJSON] IS NULL OR isjson([ValidityConditionsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_DocumentRequirement_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_DocumentRequirement_RuleID] FOREIGN KEY ([RuleID]) REFERENCES dbo.[ComplianceRuleVersion] ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_Figma_DocumentRequirement_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentRequirement_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_Figma_DocumentRequirement_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentRequirement_HorseID] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentReview') AND name='FK_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] WITH CHECK ADD CONSTRAINT [FK_DocumentReview_ReviewedBy] FOREIGN KEY ([ReviewedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentReview') AND name='FK_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] WITH CHECK ADD CONSTRAINT [FK_DocumentReview_SubmissionID] FOREIGN KEY ([SubmissionID]) REFERENCES dbo.[DocumentSubmission] ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='FK_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] WITH CHECK ADD CONSTRAINT [FK_DocumentSubmission_DocumentVersionID] FOREIGN KEY ([DocumentVersionID]) REFERENCES dbo.[DocumentVersion] ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='FK_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] WITH CHECK ADD CONSTRAINT [FK_DocumentSubmission_RequirementID] FOREIGN KEY ([RequirementID]) REFERENCES dbo.[DocumentRequirement] ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentTypeCatalog') AND name='CK_DocumentTypeCatalog_Code')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentTypeCatalog] WITH CHECK ADD CONSTRAINT [CK_DocumentTypeCatalog_Code] CHECK (len(ltrim(rtrim([TypeCode])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_DocumentVersion_ExpiryDate_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_DocumentVersion_ExpiryDate_Range] CHECK ([ExpiryDate] IS NULL OR [IssuedDate] IS NOT NULL AND [ExpiryDate]>=[IssuedDate])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_DocumentVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_DocumentVersion_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_Figma_DocumentVersion_FileSizeBytes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentVersion_FileSizeBytes] CHECK ([FileSizeBytes]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='FK_DocumentVersion_DocumentID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [FK_DocumentVersion_DocumentID] FOREIGN KEY ([DocumentID]) REFERENCES dbo.[HorseDocument] ([DocumentID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='FK_Figma_DocumentVersion_UploadedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentVersion_UploadedBy] FOREIGN KEY ([UploadedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='CK_Figma_FleetVehicle_MaintenanceDates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates] CHECK ([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='CK_FleetVehicle_HorseCapacity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [CK_FleetVehicle_HorseCapacity] CHECK ([HorseCapacity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='FK_Figma_FleetVehicle_OwnerSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID] FOREIGN KEY ([OwnerSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='CK_Figma_FlightReservation_UsableCapacitySlots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots] CHECK ([UsableCapacitySlots]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_Figma_FlightReservation_ConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_FlightReservation_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_FlightReservation_OfferID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_OfferID] FOREIGN KEY ([OfferID]) REFERENCES dbo.[SupplierOffer] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_BodyWidthCm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_BodyWidthCm] CHECK ([BodyWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_HeightCm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_HeightCm] CHECK ([HeightCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_SexCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_SexCode] CHECK ([SexCode]=''GELDING'' OR [SexCode]=''MARE'' OR [SexCode]=''STALLION'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_WeightKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_WeightKg] CHECK ([WeightKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='FK_Horse_Customer')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [FK_Horse_Customer] FOREIGN KEY ([CustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseDocument') AND name='FK_Figma_HorseDocument_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseDocument_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseDocument') AND name='FK_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT [FK_HorseDocument_HorseID] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='CK_Figma_HorseReceipt_Identity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseReceipt_Identity] CHECK ([IdentityCheckResult]=''MISMATCHED'' OR [IdentityCheckResult]=''MATCHED'' OR [IdentityCheckResult]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_Figma_HorseReceipt_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseReceipt_LegID] FOREIGN KEY ([LegID],[PlanID]) REFERENCES dbo.[TripLeg] ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_HorseReceipt_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_HorseReceipt_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_HorseReceipt_ReceivedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_HorseReceipt_ReceivedBy] FOREIGN KEY ([ReceivedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_FeedAmountKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg] CHECK ([FeedAmountKg]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_Supersedes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes] CHECK ([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_HorseWelfareLog_WaterIntakeLiters')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters] CHECK ([WaterIntakeLiters]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_Figma_HorseWelfareLog_SupersedesLogID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID] FOREIGN KEY ([SupersedesLogID],[LegID]) REFERENCES dbo.[HorseWelfareLog] ([WelfareLogID],[LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='CK_Figma_IncidentReport_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_AcknowledgedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy] FOREIGN KEY ([AcknowledgedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_ResolvedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy] FOREIGN KEY ([ResolvedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_CoverageCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_CoverageCode] CHECK ([CoverageCode]=''MORTALITY_INJURY'' OR [CoverageCode]=''MORTALITY'' OR [CoverageCode]=''EXISTING_POLICY'' OR [CoverageCode]=''NONE'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_DeclaredValue')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_DeclaredValue] CHECK ([DeclaredValue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_ExistingPolicy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_ExistingPolicy] CHECK ([CoverageCode]<>''EXISTING_POLICY'' OR [ExistingPolicyReference] IS NOT NULL AND len(ltrim(rtrim([ExistingPolicyReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_ValueCurrency')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_ValueCurrency] CHECK ([DeclaredValue] IS NULL AND [ValueCurrency] IS NULL OR [DeclaredValue] IS NOT NULL AND [ValueCurrency] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='FK_InsuranceChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [FK_InsuranceChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='CK_InsurancePolicy_CoverageTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range] CHECK ([CoverageTo] IS NULL OR [CoverageFrom] IS NOT NULL AND [CoverageTo]>=[CoverageFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='FK_Figma_InsurancePolicy_VerifiedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy] FOREIGN KEY ([VerifiedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='FK_InsurancePolicy_InsuranceChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID] FOREIGN KEY ([InsuranceChoiceID]) REFERENCES dbo.[InsuranceChoice] ([InsuranceChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Latitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Latitude] CHECK ([Latitude]>=(-90) AND [Latitude]<=(90))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Longitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Longitude] CHECK ([Longitude]>=(-180) AND [Longitude]<=(180))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Notification') AND name='FK_Notification_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] WITH CHECK ADD CONSTRAINT [FK_Notification_OrderID] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Notification') AND name='FK_Notification_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] WITH CHECK ADD CONSTRAINT [FK_Notification_UserID] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_Figma_OperationalAdjustment_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision] CHECK ([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_AdditionalCostEstimate')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate] CHECK ([AdditionalCostEstimate]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_ProposedChanges_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON] CHECK (isjson([ProposedChanges])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_ChangeRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID] FOREIGN KEY ([ChangeRequestID]) REFERENCES dbo.[OrderChangeRequest] ([ChangeRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_IncidentID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_IncidentID] FOREIGN KEY ([IncidentID]) REFERENCES dbo.[IncidentReport] ([IncidentID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ProposedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ProposedBy] FOREIGN KEY ([ProposedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='CK_OrderAssignment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [CK_OrderAssignment_Dates] CHECK ([EndedAt] IS NULL OR [EndedAt]>=[AssignedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='CK_OrderAssignment_DueAt')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [CK_OrderAssignment_DueAt] CHECK ([DueAt] IS NULL OR [DueAt]>=[AssignedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_AssignedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_AssignedBy] FOREIGN KEY ([AssignedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_Staff')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_Staff] FOREIGN KEY ([StaffID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_JSON] CHECK (isjson([RequestedChangesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_Reason')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_Reason] CHECK (len(ltrim(rtrim([Reason])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_BaseRevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID] FOREIGN KEY ([BaseRevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RequestedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy] FOREIGN KEY ([RequestedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RespondedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy] FOREIGN KEY ([RespondedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='CK_OrderDecision_Decision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [CK_OrderDecision_Decision] CHECK ([Decision]=''CHANGES_REQUESTED'' OR [Decision]=''REJECTED'' OR [Decision]=''APPROVED'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='FK_OrderDecision_Manager')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [FK_OrderDecision_Manager] FOREIGN KEY ([ManagerID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='FK_OrderDecision_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [FK_OrderDecision_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='CK_OrderRevision_OrderSnapshot')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [CK_OrderRevision_OrderSnapshot] CHECK (isjson([OrderSnapshot])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='CK_OrderRevision_RevisionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [CK_OrderRevision_RevisionNo] CHECK ([RevisionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='FK_OrderRevision_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [FK_OrderRevision_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='CK_Figma_OrderServiceChoice_SelectionInputsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [CK_Figma_OrderServiceChoice_SelectionInputsJSON] CHECK ([SelectionInputsJSON] IS NULL OR isjson([SelectionInputsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='CK_OrderServiceChoice_ArrangedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [CK_OrderServiceChoice_ArrangedBy] CHECK ([ArrangedBy]=''CUSTOMER'' OR [ArrangedBy]=''EQUITRANSIT'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='FK_OrderServiceChoice_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [FK_OrderServiceChoice_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='CK_PaymentAllocation_AllocatedAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [CK_PaymentAllocation_AllocatedAmount] CHECK ([AllocatedAmount]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='FK_PaymentAllocation_PaymentRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [FK_PaymentAllocation_PaymentRequestID] FOREIGN KEY ([PaymentRequestID]) REFERENCES dbo.[PaymentRequest] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='FK_PaymentAllocation_TransactionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [FK_PaymentAllocation_TransactionID] FOREIGN KEY ([TransactionID]) REFERENCES dbo.[PaymentTransaction] ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentReport') AND name='CK_PaymentReport_ReportedAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] WITH CHECK ADD CONSTRAINT [CK_PaymentReport_ReportedAmount] CHECK ([ReportedAmount]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentReport') AND name='FK_PaymentReport_PaymentRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] WITH CHECK ADD CONSTRAINT [FK_PaymentReport_PaymentRequestID] FOREIGN KEY ([PaymentRequestID]) REFERENCES dbo.[PaymentRequest] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='CK_PaymentRequest_AmountDue')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [CK_PaymentRequest_AmountDue] CHECK ([AmountDue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='FK_Figma_PaymentRequest_ScheduleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_PaymentRequest_ScheduleID] FOREIGN KEY ([ScheduleID],[QuoteID]) REFERENCES dbo.[QuotePaymentSchedule] ([ScheduleID],[QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='FK_PaymentRequest_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [FK_PaymentRequest_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='CK_PaymentTransaction_AmountReceived')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] WITH CHECK ADD CONSTRAINT [CK_PaymentTransaction_AmountReceived] CHECK ([AmountReceived]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='FK_PaymentTransaction_ReconciledBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] WITH CHECK ADD CONSTRAINT [FK_PaymentTransaction_ReconciledBy] FOREIGN KEY ([ReconciledBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='CK_QuarantineChoice_Confirmation')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [CK_QuarantineChoice_Confirmation] CHECK ([ConfirmedBy] IS NULL AND [DaysConfirmedAt] IS NULL OR [ConfirmedBy] IS NOT NULL AND [DaysConfirmedAt] IS NOT NULL AND [RequiredDays] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='CK_QuarantineChoice_RequiredDays')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [CK_QuarantineChoice_RequiredDays] CHECK ([RequiredDays]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='FK_QuarantineChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [FK_QuarantineChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='FK_QuarantineChoice_Staff')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [FK_QuarantineChoice_Staff] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='CK_QuarantineReservation_EndsAt_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [CK_QuarantineReservation_EndsAt_Range] CHECK ([EndsAt] IS NULL OR [StartsAt] IS NOT NULL AND [EndsAt]>=[StartsAt])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_Figma_QuarantineReservation_ConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_Figma_QuarantineReservation_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_QuarantineReservation_QuarantineChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID] FOREIGN KEY ([QuarantineChoiceID]) REFERENCES dbo.[QuarantineChoice] ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_AcceptedAt')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_AcceptedAt] CHECK ([AcceptedAt] IS NULL OR [IssuedAt] IS NOT NULL AND [AcceptedAt]>=[IssuedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Approval')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Approval] CHECK ([ApprovedBy] IS NULL AND [ApprovedAt] IS NULL OR [ApprovedBy] IS NOT NULL AND [ApprovedAt] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Status] CHECK ([QuoteStatus]=''SUPERSEDED'' OR [QuoteStatus]=''EXPIRED'' OR [QuoteStatus]=''REJECTED'' OR [QuoteStatus]=''ACCEPTED'' OR [QuoteStatus]=''ISSUED'' OR [QuoteStatus]=''APPROVED'' OR [QuoteStatus]=''DRAFT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_TotalAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_TotalAmount] CHECK ([TotalQuotedAmount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Validity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Validity] CHECK ([ValidUntil] IS NULL OR [IssuedAt] IS NOT NULL AND [ValidUntil]>[IssuedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_AcceptedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_AcceptedByCustomerID] FOREIGN KEY ([AcceptedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_IssuedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_IssuedBy] FOREIGN KEY ([IssuedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_PreparedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_PreparedBy] FOREIGN KEY ([PreparedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Quote_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Quote_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Quote_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Quote_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_CalculationInputs_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_CalculationInputs_JSON] CHECK ([CalculationInputs] IS NULL OR isjson([CalculationInputs])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_Quantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_Quantity] CHECK ([Quantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_OfferID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_OfferID] FOREIGN KEY ([OfferID]) REFERENCES dbo.[SupplierOffer] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_TariffID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_TariffID] FOREIGN KEY ([TariffID]) REFERENCES dbo.[ServiceTariff] ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='CK_QuotePaymentSchedule_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [CK_QuotePaymentSchedule_Amount] CHECK ([AmountDue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='CK_QuotePaymentSchedule_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [CK_QuotePaymentSchedule_Sequence] CHECK ([SequenceNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='FK_Figma_QuotePaymentSchedule_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [FK_Figma_QuotePaymentSchedule_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='CK_Refund_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [CK_Refund_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Figma_Refund_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Figma_Refund_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Figma_Refund_RequestedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Figma_Refund_RequestedBy] FOREIGN KEY ([RequestedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Refund_AllocationID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Refund_AllocationID] FOREIGN KEY ([AllocationID]) REFERENCES dbo.[PaymentAllocation] ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Dates] CHECK ([ValidUntil]>[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_History')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_History] CHECK ([SupersededAt] IS NULL OR [SupersededAt]>=[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Outcome')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Outcome] CHECK ([Applicability]=''REQUIRED'' AND [RequiredDays] IS NOT NULL AND [RequiredDays]>(0) OR [Applicability]=''NOT_REQUIRED'' AND [RequiredDays] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Source')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Source] CHECK (len(ltrim(rtrim([SourceReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='FK__RevisionQ__Revis__473C8FC7')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionQ__Revis__473C8FC7] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='FK__RevisionQ__Speci__4830B400')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionQ__Speci__4830B400] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Counts')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Counts] CHECK ([VisitCount]>=(0) AND [TestCount]>=(0) AND [DoseCount]>=(0) AND [CertificateCount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Dates] CHECK ([ValidUntil]>[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_History')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_History] CHECK ([SupersededAt] IS NULL OR [SupersededAt]>=[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Source')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Source] CHECK (len(ltrim(rtrim([SourceReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='FK__RevisionV__Revis__4EDDB18F')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionV__Revis__4EDDB18F] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='FK__RevisionV__Speci__4FD1D5C8')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionV__Speci__4FD1D5C8] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_Dates] CHECK ([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_Status] CHECK ([Status]=''INACTIVE'' OR [Status]=''ACTIVE'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StaffProfile') AND name='FK_StaffProfile_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] WITH CHECK ADD CONSTRAINT [FK_StaffProfile_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='CK_StallAssignment_AssignedTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_AssignedTo_Range] CHECK ([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='CK_StallAssignment_SlotQuantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_SlotQuantity] CHECK ([SlotQuantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='FK_StallAssignment_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='FK_StallAssignment_StallID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_StallID] FOREIGN KEY ([StallID]) REFERENCES dbo.[StallEquipment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='CK_StallChoice_Configuration')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [CK_StallChoice_Configuration] CHECK ([SharingCode]=''SHARED'' AND ([SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)) OR [SharingCode]=''PRIVATE'' AND [SlotQuantity]=(3.0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='CK_StallChoice_FitStatus')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [CK_StallChoice_FitStatus] CHECK ([FitStatus]=''UNSUITABLE'' OR [FitStatus]=''SUITABLE'' OR [FitStatus]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='FK_StallChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [FK_StallChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_Figma_StallEquipment_MaintenanceDates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates] CHECK ([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_Figma_StallEquipment_WeightLimitKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg] CHECK ([WeightLimitKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_StallEquipment_CapacitySlots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_StallEquipment_CapacitySlots] CHECK ([CapacitySlots]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='FK_Figma_StallEquipment_OwnerSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID] FOREIGN KEY ([OwnerSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Approval')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Approval] CHECK ([Status]<>''APPROVED'' OR [ReviewedBy] IS NOT NULL AND [ReviewedAt] IS NOT NULL AND [MaxWeightKg] IS NOT NULL AND [MaxHeightCm] IS NOT NULL AND [MaxWidthCm] IS NOT NULL AND ([SourceURL] IS NOT NULL OR [SourceFileKey] IS NOT NULL))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Dates] CHECK ([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Dimensions')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Dimensions] CHECK ([MaxWeightKg]>(0) AND [MaxHeightCm]>(0) AND [MaxWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Slots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Slots] CHECK ([SlotQuantity]=(3.0) OR [SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Status] CHECK ([Status]=''INACTIVE'' OR [Status]=''APPROVED'' OR [Status]=''DRAFT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Version] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_CarrierSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID] FOREIGN KEY ([CarrierSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_ReviewedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy] FOREIGN KEY ([ReviewedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_StallID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_StallID] FOREIGN KEY ([StallID]) REFERENCES dbo.[StallEquipment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Supplier') AND name='CK_Supplier_Name')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK ADD CONSTRAINT [CK_Supplier_Name] CHECK (len(ltrim(rtrim([Name])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Supplier') AND name='CK_Supplier_ServicesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK ADD CONSTRAINT [CK_Supplier_ServicesJSON] CHECK ([ServicesOfferedJSON] IS NULL OR isjson([ServicesOfferedJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_IncludedCharges')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_IncludedCharges] CHECK ([IncludedCharges] IS NULL OR isjson([IncludedCharges])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_Quantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_Quantity] CHECK ([Quantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_Status] CHECK ([Status]=''EXPIRED'' OR [Status]=''REJECTED'' OR [Status]=''SELECTED'' OR [Status]=''RECEIVED'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='FK_Figma_SupplierOffer_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [FK_Figma_SupplierOffer_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='FK_SupplierOffer_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [FK_SupplierOffer_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_DraftPayloadJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_DraftPayloadJSON] CHECK ([DraftPayloadJSON] IS NULL OR isjson([DraftPayloadJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_ImportType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_ImportType] CHECK ([ImportType]=''NEEDS_ADVICE'' OR [ImportType]=''PERMANENT'' OR [ImportType]=''TEMPORARY'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_TransitAirportsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_TransitAirportsJSON] CHECK ([TransitAirportsJSON] IS NULL OR isjson([TransitAirportsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_Figma_TransportOrder_CurrentPlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID] FOREIGN KEY ([CurrentPlanID],[OrderID]) REFERENCES dbo.[TransportPlan] ([PlanID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_Figma_TransportOrder_CurrentRevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportOrder_CurrentRevisionID] FOREIGN KEY ([CurrentRevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_TransportOrder_Customer')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_TransportOrder_Customer] FOREIGN KEY ([CustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Height')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Height] CHECK ([SnapshotHeightCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_SexCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_SexCode] CHECK ([SnapshotSexCode]=''GELDING'' OR [SnapshotSexCode]=''MARE'' OR [SnapshotSexCode]=''STALLION'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Weight')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Weight] CHECK ([SnapshotWeightKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Width')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Width] CHECK ([SnapshotBodyWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='FK_TransportOrderDetail_Horse')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [FK_TransportOrderDetail_Horse] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='FK_TransportOrderDetail_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [FK_TransportOrderDetail_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='CK_TransportPlan_PlannedEndTime_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range] CHECK ([PlannedEndTime] IS NULL OR [PlannedStartTime] IS NOT NULL AND [PlannedEndTime]>=[PlannedStartTime])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='CK_TransportPlan_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_Figma_TransportPlan_AcceptedQuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID] FOREIGN KEY ([AcceptedQuoteID],[RevisionID]) REFERENCES dbo.[Quote] ([QuoteID],[RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_Figma_TransportPlan_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_RevisionID] FOREIGN KEY ([RevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_CreatedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_CreatedBy] FOREIGN KEY ([CreatedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_DepartureAuthorizedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy] FOREIGN KEY ([DepartureAuthorizedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='CK_Figma_TripAssignment_BufferMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes] CHECK ([BufferMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='CK_TripAssignment_AssignedTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_TripAssignment_AssignedTo_Range] CHECK ([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='FK_TripAssignment_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='FK_TripAssignment_StaffID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_StaffID] FOREIGN KEY ([StaffID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_CustomerDecision] CHECK ([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_Figma_TripHandover_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_Figma_TripLeg_ConnectionBufferMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes] CHECK ([ConnectionBufferMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_Figma_TripLeg_RestDurationMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes] CHECK ([RestDurationMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_ActualArrival_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_ActualArrival_Range] CHECK ([ActualArrival] IS NULL OR [ActualDeparture] IS NOT NULL AND [ActualArrival]>=[ActualDeparture])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_DistanceKm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_DistanceKm] CHECK ([DistanceKm]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_DistanceSource')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_DistanceSource] CHECK ([DistanceSource] IS NULL AND [DistanceConfirmedBy] IS NULL AND [DistanceConfirmedAt] IS NULL OR [DistanceSource] IS NOT NULL AND len(ltrim(rtrim([DistanceSource])))>(0) AND [DistanceConfirmedBy] IS NOT NULL AND [DistanceConfirmedAt] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_PlannedArrival_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_PlannedArrival_Range] CHECK ([PlannedArrival] IS NULL OR [PlannedDeparture] IS NOT NULL AND [PlannedArrival]>=[PlannedDeparture])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_SequenceNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_SequenceNo] CHECK ([SequenceNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_Figma_TripLeg_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_Figma_TripLeg_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_DistanceConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_DistanceConfirmedBy] FOREIGN KEY ([DistanceConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_VehicleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_VehicleID] FOREIGN KEY ([VehicleID]) REFERENCES dbo.[FleetVehicle] ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.User') AND name='CK_User_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] WITH CHECK ADD CONSTRAINT [CK_User_Status] CHECK ([Status]=''LOCKED'' OR [Status]=''INACTIVE'' OR [Status]=''ACTIVE'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.User') AND name='FK_User_Role')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] WITH CHECK ADD CONSTRAINT [FK_User_Role] FOREIGN KEY ([RoleID]) REFERENCES dbo.[Role] ([RoleID])';

-- Required reference roles, not accounts or demo price/operating evidence.
INSERT dbo.Role(RoleCode)
SELECT v.Code FROM (VALUES ('CUSTOMER'),('MANAGER'),('SPECIALIST'),
    ('COORDINATOR'),('DRIVER')) v(Code)
WHERE NOT EXISTS(SELECT 1 FROM dbo.Role r WHERE r.RoleCode=v.Code);
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS first application: 000_figma_schema.sql';
GO
PRINT N'RUN first application: 001_authentication_token.sql';
GO
-- Add only the persistence required by Figma A03 verification/recovery.
-- Existing User, Role and Customer tables and rows remain unchanged.
IF OBJECT_ID(N'dbo.AuthenticationToken', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AuthenticationToken (
        TokenID BIGINT IDENTITY(1,1) NOT NULL,
        UserID BIGINT NOT NULL,
        TokenHash VARCHAR(64) NOT NULL,
        Purpose VARCHAR(20) NOT NULL,
        ExpiresAt DATETIME2(0) NOT NULL,
        UsedAt DATETIME2(0) NULL,
        CreatedAt DATETIME2(0) NOT NULL,
        CONSTRAINT PK_AuthenticationToken PRIMARY KEY (TokenID),
        CONSTRAINT UQ_AuthenticationToken_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_AuthenticationToken_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User] (UserID),
        CONSTRAINT CK_AuthenticationToken_Purpose CHECK (
            Purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')
        ),
        CONSTRAINT CK_AuthenticationToken_Expiry
            CHECK (ExpiresAt > CreatedAt)
    );
    CREATE INDEX IX_AuthenticationToken_UserPurpose
        ON dbo.AuthenticationToken (UserID, Purpose);
END;

GO
PRINT N'PASS first application: 001_authentication_token.sql';
GO
PRINT N'RUN first application: 002_align_figma_without_organization.sql';
GO
-- 002: Align persisted business fields with SWP Figma, excluding customer organizations.
-- Sources: export 2026-10-10 plus live page 0:1 metadata checked 2026-10-13.
-- Additive, transactional and rerunnable. Preserve original columns and rows.
-- Back up and validate on a restored test copy before applying EquiTransit.
-- No production tariff, insurance price or carrier fit limit is seeded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'EquiTransit' AND DB_NAME() NOT LIKE N'EquiTransit[_]FigmaMigrationTest[_]%'
    THROW 51000, 'Use EquiTransit or a dedicated FigmaMigrationTest copy.', 1;
IF OBJECT_ID(N'dbo.TransportOrderDetail', N'U') IS NULL
    THROW 51000, 'The full existing EquiTransit schema is required.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lockResult INT;
EXEC @lockResult = sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
IF @lockResult < 0 THROW 51000, 'Another schema migration holds the lock.', 1;

-- Customer: C17
IF COL_LENGTH(N'dbo.Customer', N'EmailNotificationsEnabled') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD [EmailNotificationsEnabled] BIT NULL;';

-- Horse: C03/C07
IF COL_LENGTH(N'dbo.Horse', N'HandlingNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [HandlingNotes] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'FeedWaterRequirements') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [FeedWaterRequirements] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'StallPreferences') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [StallPreferences] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'VeterinaryInstructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [VeterinaryInstructions] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'VeterinaryInstructionSource') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [VeterinaryInstructionSource] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [RecordVersion] ROWVERSION;';

-- TransportOrder: C05/C06/C07/C08/C10/C16
IF COL_LENGTH(N'dbo.TransportOrder', N'PreferredDepartureAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [PreferredDepartureAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'DateFlexibilityNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [DateFlexibilityNotes] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverName] NVARCHAR(150) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverPhone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverPhone] VARCHAR(30) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverEmail') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverEmail] VARCHAR(255) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverAuthority') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverAuthority] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ImportType') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ImportType] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventName] NVARCHAR(200) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventVenue') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventVenue] NVARCHAR(300) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'TransitAirportsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [TransitAirportsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'DraftPayloadJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [DraftPayloadJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'UpdatedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [UpdatedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'CurrentRevisionID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [CurrentRevisionID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'CurrentPlanID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [CurrentPlanID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [RecordVersion] ROWVERSION;';

-- OrderServiceChoice: C08/BOOK-01..08
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'SelectionInputsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [SelectionInputsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'ProviderName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [ProviderName] NVARCHAR(200) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'ProviderContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [ProviderContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- Quote: F02/M04/C13/M09
IF COL_LENGTH(N'dbo.Quote', N'PreparedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [PreparedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'SubmittedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [SubmittedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'IssuedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [IssuedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'AcceptedByCustomerID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [AcceptedByCustomerID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsVersion] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsText') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsText] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'CancellationTerms') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [CancellationTerms] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'ReviewFeedback') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [ReviewFeedback] NVARCHAR(2000) NULL;';

-- PaymentRequest: M04/C14/M07
IF COL_LENGTH(N'dbo.PaymentRequest', N'ScheduleID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD [ScheduleID] BIGINT NULL;';

-- PaymentReport: C14/M07
IF COL_LENGTH(N'dbo.PaymentReport', N'TransferredAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD [TransferredAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.PaymentReport', N'ReportedCurrency') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD [ReportedCurrency] VARCHAR(3) NULL;';

-- PaymentTransaction: M07
IF COL_LENGTH(N'dbo.PaymentTransaction', N'VerificationSource') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [VerificationSource] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.PaymentTransaction', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.PaymentTransaction', N'InternalNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [InternalNotes] NVARCHAR(2000) NULL;';

-- Refund: M07/M09
IF COL_LENGTH(N'dbo.Refund', N'RequestedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [RequestedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Refund', N'RequestedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [RequestedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ApprovedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ApprovedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ApprovedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ApprovedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'BankRefundReference') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [BankRefundReference] VARCHAR(150) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ProofFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ProofFileKey] NVARCHAR(1024) NULL;';

-- DocumentRequirement: S04/C11
IF COL_LENGTH(N'dbo.DocumentRequirement', N'Instructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [Instructions] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'ScopeCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [ScopeCode] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'HorseID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [HorseID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'ValidityConditionsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [ValidityConditionsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'CaseJustification') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [CaseJustification] NVARCHAR(2000) NULL;';

-- DocumentVersion: C03/C11/S03
IF COL_LENGTH(N'dbo.DocumentVersion', N'UploadedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [UploadedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'OriginalFileName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [OriginalFileName] NVARCHAR(255) NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'ContentType') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [ContentType] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'FileSizeBytes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [FileSizeBytes] BIGINT NULL;';

-- ComplianceRuleVersion: S04
IF COL_LENGTH(N'dbo.ComplianceRuleVersion', N'TransitCountryCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD [TransitCountryCode] VARCHAR(2) NULL;';
IF COL_LENGTH(N'dbo.ComplianceRuleVersion', N'RuleDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD [RuleDescription] NVARCHAR(2000) NULL;';

-- AuthorityProcedure: S05
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'PhaseCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [PhaseCode] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'AuthorityContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [AuthorityContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'HoldReason') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [HoldReason] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [RecordedBy] BIGINT NULL;';

-- ComplianceClearance: S02/S05
IF COL_LENGTH(N'dbo.ComplianceClearance', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.ComplianceClearance', N'AssessmentNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD [AssessmentNotes] NVARCHAR(2000) NULL;';

-- SupplierOffer: F02/M12
IF COL_LENGTH(N'dbo.SupplierOffer', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.SupplierOffer', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.SupplierOffer', N'SourceContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [SourceContact] NVARCHAR(500) NULL;';

-- FleetVehicle: F06/F07
IF COL_LENGTH(N'dbo.FleetVehicle', N'OwnerSupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [OwnerSupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'MaintenanceFrom') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [MaintenanceFrom] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'MaintenanceTo') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [MaintenanceTo] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'DocumentExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [DocumentExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'AssetNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [AssetNotes] NVARCHAR(2000) NULL;';

-- StallEquipment: F06/F07/BOOK-02
IF COL_LENGTH(N'dbo.StallEquipment', N'OwnerSupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [OwnerSupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'WeightLimitKg') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [WeightLimitKg] DECIMAL(8,2) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'MaintenanceFrom') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [MaintenanceFrom] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'MaintenanceTo') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [MaintenanceTo] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'DocumentExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [DocumentExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'AssetNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [AssetNotes] NVARCHAR(2000) NULL;';

-- TransportPlan: F04/M06/C15
IF COL_LENGTH(N'dbo.TransportPlan', N'OrderID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD [OrderID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportPlan', N'AcceptedQuoteID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD [AcceptedQuoteID] BIGINT NULL;';

-- TripLeg: F04/C15/D02
IF COL_LENGTH(N'dbo.TripLeg', N'StartTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [StartTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'EndTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [EndTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'PlannedFlightNumber') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [PlannedFlightNumber] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'ConnectionBufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [ConnectionBufferMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'RestDurationMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [RestDurationMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'CareInstructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [CareInstructions] NVARCHAR(2000) NULL;';

-- TripAssignment: F08
IF COL_LENGTH(N'dbo.TripAssignment', N'BufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD [BufferMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripAssignment', N'ConflictNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD [ConflictNotes] NVARCHAR(1000) NULL;';

-- FlightReservation: F04
IF COL_LENGTH(N'dbo.FlightReservation', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'ConfirmedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [ConfirmedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'SupplierContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [SupplierContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'UsableCapacitySlots') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [UsableCapacitySlots] DECIMAL(5,1) NULL;';

-- StallAssignment: F04/BOOK-02
IF COL_LENGTH(N'dbo.StallAssignment', N'PositionDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [PositionDescription] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierApprovalFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierApprovalFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierApprovedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierApprovedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierContact] NVARCHAR(500) NULL;';

-- QuarantineReservation: F04/S05/BOOK-05
IF COL_LENGTH(N'dbo.QuarantineReservation', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ConfirmedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ConfirmedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ConfirmedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ConfirmedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'FacilityContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [FacilityContact] NVARCHAR(500) NULL;';

-- InsurancePolicy: BOOK-07/F04
IF COL_LENGTH(N'dbo.InsurancePolicy', N'VerifiedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [VerifiedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.InsurancePolicy', N'VerifiedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [VerifiedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.InsurancePolicy', N'PolicyTerms') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [PolicyTerms] NVARCHAR(MAX) NULL;';

-- HorseReceipt: D02/D02-RECEIPT
IF COL_LENGTH(N'dbo.HorseReceipt', N'LegID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [LegID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'ObservedPassportNumber') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [ObservedPassportNumber] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'IdentityCheckResult') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [IdentityCheckResult] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'LocationDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [LocationDescription] NVARCHAR(500) NULL;';

-- LegMilestoneLog: D02/F04/C15
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'SourceCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceCode] VARCHAR(30) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'SourceReference') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceReference] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'LocationDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [LocationDescription] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'EstimatedArrivalAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EstimatedArrivalAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'DelayReason') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [DelayReason] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- HorseWelfareLog: D03
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'FeedAmountKg') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [FeedAmountKg] DECIMAL(8,2) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'CareNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [CareNotes] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'InstructionFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [InstructionFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'SupersedesLogID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [SupersedesLogID] BIGINT NULL;';

-- IncidentReport: D02/M10/M11
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

-- OperationalAdjustment: M08/M09/M11/C10
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

-- TripHandover: D04/C16
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
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [EvidenceFilesJSON] NVARCHAR(MAX) NULL;';

-- ActualCost: M11/M13/F04
IF COL_LENGTH(N'dbo.ActualCost', N'Description') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Description] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [RecordedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- Supplier: M12/F02/F04/F07
IF OBJECT_ID(N'dbo.Supplier', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Supplier] (
    SupplierID BIGINT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(200) NOT NULL, ContactName NVARCHAR(150) NULL,
    Email VARCHAR(255) NULL, Phone VARCHAR(30) NULL,
    ServicesOfferedJSON NVARCHAR(MAX) NULL, IsActive BIT NOT NULL DEFAULT (1),
    CreatedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_Supplier PRIMARY KEY (SupplierID),
    CONSTRAINT CK_Supplier_Name CHECK (LEN(LTRIM(RTRIM(Name))) > 0),
    CONSTRAINT CK_Supplier_ServicesJSON CHECK
        (ServicesOfferedJSON IS NULL OR ISJSON(ServicesOfferedJSON) = 1)
);';

-- DocumentTypeCatalog: S04/C11
IF OBJECT_ID(N'dbo.DocumentTypeCatalog', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentTypeCatalog] (
    TypeCode VARCHAR(50) NOT NULL,
    DisplayName NVARCHAR(200) NOT NULL, Description NVARCHAR(1000) NULL,
    IsActive BIT NOT NULL DEFAULT (1),
    CONSTRAINT PK_DocumentTypeCatalog PRIMARY KEY (TypeCode),
    CONSTRAINT CK_DocumentTypeCatalog_Code CHECK
        (LEN(LTRIM(RTRIM(TypeCode))) > 0)
);';

-- QuotePaymentSchedule: M04/C13/C14/M07
IF OBJECT_ID(N'dbo.QuotePaymentSchedule', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuotePaymentSchedule] (
    ScheduleID BIGINT IDENTITY(1,1) NOT NULL,
    QuoteID BIGINT NOT NULL, SequenceNo INT NOT NULL,
    PaymentStage VARCHAR(50) NOT NULL, AmountDue DECIMAL(18,2) NOT NULL,
    Currency VARCHAR(3) NOT NULL, DueAt DATETIME2(0) NULL,
    CONSTRAINT PK_QuotePaymentSchedule PRIMARY KEY (ScheduleID),
    CONSTRAINT UQ_QuotePaymentSchedule_Sequence UNIQUE (QuoteID, SequenceNo),
    CONSTRAINT UQ_QuotePaymentSchedule_Parent UNIQUE (ScheduleID, QuoteID),
    CONSTRAINT CK_QuotePaymentSchedule_Sequence CHECK (SequenceNo > 0),
    CONSTRAINT CK_QuotePaymentSchedule_Amount CHECK (AmountDue > 0)
);';

-- OrderChangeRequest: C10/M08/M09
IF OBJECT_ID(N'dbo.OrderChangeRequest', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderChangeRequest] (
    ChangeRequestID BIGINT IDENTITY(1,1) NOT NULL,
    OrderID BIGINT NOT NULL, BaseRevisionID BIGINT NULL,
    RequestedBy BIGINT NOT NULL, ChangeType VARCHAR(30) NOT NULL,
    Reason NVARCHAR(2000) NOT NULL, RequestedChangesJSON NVARCHAR(MAX) NOT NULL,
    AttachmentFileKey NVARCHAR(1024) NULL, Status VARCHAR(30) NOT NULL,
    CreatedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    RespondedBy BIGINT NULL, RespondedAt DATETIME2(0) NULL,
    ResponseReason NVARCHAR(2000) NULL,
    CONSTRAINT PK_OrderChangeRequest PRIMARY KEY (ChangeRequestID),
    CONSTRAINT CK_OrderChangeRequest_JSON CHECK (ISJSON(RequestedChangesJSON) = 1),
    CONSTRAINT CK_OrderChangeRequest_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) > 0)
);';

-- StallFitPolicy: BOOK-02/F04/F07
IF OBJECT_ID(N'dbo.StallFitPolicy', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallFitPolicy] (
    PolicyID BIGINT IDENTITY(1,1) NOT NULL,
    PolicyCode VARCHAR(50) NOT NULL, VersionNo INT NOT NULL,
    StallID BIGINT NULL, CarrierSupplierID BIGINT NULL,
    SlotQuantity DECIMAL(5,1) NOT NULL, MaxWeightKg DECIMAL(8,2) NULL,
    MaxHeightCm DECIMAL(8,2) NULL, MaxWidthCm DECIMAL(8,2) NULL,
    EffectiveFrom DATETIME2(0) NOT NULL, EffectiveTo DATETIME2(0) NULL,
    SourceURL NVARCHAR(2048) NULL, SourceFileKey NVARCHAR(1024) NULL,
    ReviewedBy BIGINT NULL, ReviewedAt DATETIME2(0) NULL,
    Status VARCHAR(20) NOT NULL,
    CONSTRAINT PK_StallFitPolicy PRIMARY KEY (PolicyID),
    CONSTRAINT UQ_StallFitPolicy_Version UNIQUE (PolicyCode, VersionNo),
    CONSTRAINT CK_StallFitPolicy_Version CHECK (VersionNo > 0),
    CONSTRAINT CK_StallFitPolicy_Slots CHECK (SlotQuantity IN (1.0, 1.5, 3.0)),
    CONSTRAINT CK_StallFitPolicy_Dimensions CHECK
        (MaxWeightKg > 0 AND MaxHeightCm > 0 AND MaxWidthCm > 0),
    CONSTRAINT CK_StallFitPolicy_Dates CHECK
        (EffectiveTo IS NULL OR EffectiveTo > EffectiveFrom),
    CONSTRAINT CK_StallFitPolicy_Status CHECK
        (Status IN (''DRAFT'', ''APPROVED'', ''INACTIVE'')),
    CONSTRAINT CK_StallFitPolicy_Approval CHECK
        (Status <> ''APPROVED'' OR (ReviewedBy IS NOT NULL AND ReviewedAt IS NOT NULL
        AND MaxWeightKg IS NOT NULL AND MaxHeightCm IS NOT NULL
        AND MaxWidthCm IS NOT NULL AND (SourceURL IS NOT NULL
        OR SourceFileKey IS NOT NULL)))
);';

-- SchemaMigrationHistory: Technical
IF OBJECT_ID(N'dbo.SchemaMigrationHistory', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[SchemaMigrationHistory] (
    MigrationID VARCHAR(100) NOT NULL,
    AppliedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    AppliedBy NVARCHAR(128) NOT NULL DEFAULT (SUSER_SNAME()),
    CONSTRAINT PK_SchemaMigrationHistory PRIMARY KEY (MigrationID)
);';

-- Exact backfill of the new plan OrderID through its existing revision.
EXEC sys.sp_executesql N'UPDATE p SET OrderID = r.OrderID FROM dbo.TransportPlan p JOIN dbo.OrderRevision r ON r.RevisionID=p.RevisionID WHERE p.OrderID IS NULL;';
EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan ALTER COLUMN OrderID BIGINT NOT NULL;';
EXEC sys.sp_executesql N'INSERT INTO dbo.DocumentTypeCatalog (TypeCode, DisplayName)
SELECT s.DocumentType, s.DocumentType FROM
    (SELECT DocumentType FROM dbo.HorseDocument
     UNION SELECT DocumentType FROM dbo.DocumentRequirement
     UNION SELECT DocumentType FROM dbo.ComplianceRuleVersion) s
WHERE NOT EXISTS (SELECT 1 FROM dbo.DocumentTypeCatalog d
                  WHERE d.TypeCode=s.DocumentType);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrderRevision') AND name=N'UQ_Figma_OrderRevision_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.OrderRevision ADD CONSTRAINT UQ_Figma_OrderRevision_Parent UNIQUE (RevisionID, OrderID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Quote') AND name=N'UQ_Figma_Quote_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Quote ADD CONSTRAINT UQ_Figma_Quote_Parent UNIQUE (QuoteID, RevisionID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TransportPlan') AND name=N'UQ_Figma_TransportPlan_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan ADD CONSTRAINT UQ_Figma_TransportPlan_Parent UNIQUE (PlanID, OrderID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TripLeg') AND name=N'UQ_Figma_TripLeg_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg ADD CONSTRAINT UQ_Figma_TripLeg_Parent UNIQUE (LegID, PlanID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND name=N'UQ_Figma_HorseWelfareLog_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseWelfareLog ADD CONSTRAINT UQ_Figma_HorseWelfareLog_Parent UNIQUE (WelfareLogID, LegID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_PreparedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_PreparedBy FOREIGN KEY (PreparedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_IssuedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_IssuedBy FOREIGN KEY (IssuedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Refund_ApprovedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT FK_Figma_Refund_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_AuthorityProcedure_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT FK_Figma_AuthorityProcedure_RecordedBy FOREIGN KEY (RecordedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_FlightReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT FK_Figma_FlightReservation_ConfirmedBy FOREIGN KEY (ConfirmedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT FK_Figma_QuarantineReservation_ConfirmedBy FOREIGN KEY (ConfirmedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_InsurancePolicy_VerifiedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT FK_Figma_InsurancePolicy_VerifiedBy FOREIGN KEY (VerifiedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_AcknowledgedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT FK_Figma_IncidentReport_AcknowledgedBy FOREIGN KEY (AcknowledgedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_ResolvedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT FK_Figma_IncidentReport_ResolvedBy FOREIGN KEY (ResolvedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT FK_Figma_ActualCost_RecordedBy FOREIGN KEY (RecordedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_ReviewedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_ReviewedBy FOREIGN KEY (ReviewedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RespondedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_RespondedBy FOREIGN KEY (RespondedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_AcceptedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_AcceptedByCustomerID FOREIGN KEY (AcceptedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT FK_Figma_OperationalAdjustment_RespondedByCustomerID FOREIGN KEY (RespondedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_TripHandover_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT FK_Figma_TripHandover_RespondedByCustomerID FOREIGN KEY (RespondedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_Refund_RequestedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT FK_Figma_Refund_RequestedBy FOREIGN KEY (RequestedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentVersion_UploadedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentVersion_UploadedBy FOREIGN KEY (UploadedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RequestedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_RequestedBy FOREIGN KEY (RequestedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_SupplierOffer_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT FK_Figma_SupplierOffer_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_FleetVehicle_OwnerSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT FK_Figma_FleetVehicle_OwnerSupplierID FOREIGN KEY (OwnerSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallEquipment_OwnerSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT FK_Figma_StallEquipment_OwnerSupplierID FOREIGN KEY (OwnerSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_TripLeg_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT FK_Figma_TripLeg_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT FK_Figma_QuarantineReservation_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_CarrierSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_CarrierSupplierID FOREIGN KEY (CarrierSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseDocument_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT FK_Figma_HorseDocument_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentRequirement_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentRequirement_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_ComplianceRuleVersion_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT FK_Figma_ComplianceRuleVersion_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentRequirement_HorseID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentRequirement_HorseID FOREIGN KEY (HorseID) REFERENCES dbo.[Horse] (HorseID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_StallID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_StallID FOREIGN KEY (StallID) REFERENCES dbo.[StallEquipment] (StallID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuotePaymentSchedule_QuoteID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT FK_Figma_QuotePaymentSchedule_QuoteID FOREIGN KEY (QuoteID) REFERENCES dbo.[Quote] (QuoteID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_OrderID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_OrderID FOREIGN KEY (OrderID) REFERENCES dbo.[TransportOrder] (OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_ChangeRequestID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT FK_Figma_OperationalAdjustment_ChangeRequestID FOREIGN KEY (ChangeRequestID) REFERENCES dbo.[OrderChangeRequest] (ChangeRequestID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportOrder_CurrentRevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT FK_Figma_TransportOrder_CurrentRevisionID FOREIGN KEY (CurrentRevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportOrder_CurrentPlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT FK_Figma_TransportOrder_CurrentPlanID FOREIGN KEY (CurrentPlanID, OrderID) REFERENCES dbo.[TransportPlan] (PlanID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_RevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT FK_Figma_TransportPlan_RevisionID FOREIGN KEY (RevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_AcceptedQuoteID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT FK_Figma_TransportPlan_AcceptedQuoteID FOREIGN KEY (AcceptedQuoteID, RevisionID) REFERENCES dbo.[Quote] (QuoteID, RevisionID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseReceipt_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT FK_Figma_HorseReceipt_LegID FOREIGN KEY (LegID, PlanID) REFERENCES dbo.[TripLeg] (LegID, PlanID);';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT FK_Figma_ActualCost_LegID FOREIGN KEY (LegID, PlanID) REFERENCES dbo.[TripLeg] (LegID, PlanID);';
IF OBJECT_ID(N'dbo.FK_Figma_PaymentRequest_ScheduleID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT FK_Figma_PaymentRequest_ScheduleID FOREIGN KEY (ScheduleID, QuoteID) REFERENCES dbo.[QuotePaymentSchedule] (ScheduleID, QuoteID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_BaseRevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_BaseRevisionID FOREIGN KEY (BaseRevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseWelfareLog_SupersedesLogID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT FK_Figma_HorseWelfareLog_SupersedesLogID FOREIGN KEY (SupersedesLogID, LegID) REFERENCES dbo.[HorseWelfareLog] (WelfareLogID, LegID);';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_TransitAirportsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_TransitAirportsJSON CHECK (TransitAirportsJSON IS NULL OR ISJSON(TransitAirportsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_DraftPayloadJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_DraftPayloadJSON CHECK (DraftPayloadJSON IS NULL OR ISJSON(DraftPayloadJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_OrderServiceChoice_SelectionInputsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT CK_Figma_OrderServiceChoice_SelectionInputsJSON CHECK (SelectionInputsJSON IS NULL OR ISJSON(SelectionInputsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentRequirement_ValidityConditionsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentRequirement_ValidityConditionsJSON CHECK (ValidityConditionsJSON IS NULL OR ISJSON(ValidityConditionsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_IncidentReport_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT CK_Figma_IncidentReport_EvidenceFilesJSON CHECK (EvidenceFilesJSON IS NULL OR ISJSON(EvidenceFilesJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT CK_Figma_TripHandover_EvidenceFilesJSON CHECK (EvidenceFilesJSON IS NULL OR ISJSON(EvidenceFilesJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_WeightLimitKg', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT CK_Figma_StallEquipment_WeightLimitKg CHECK (WeightLimitKg > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentVersion_FileSizeBytes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentVersion_FileSizeBytes CHECK (FileSizeBytes > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_FlightReservation_UsableCapacitySlots', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT CK_Figma_FlightReservation_UsableCapacitySlots CHECK (UsableCapacitySlots > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_ConnectionBufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT CK_Figma_TripLeg_ConnectionBufferMinutes CHECK (ConnectionBufferMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_RestDurationMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT CK_Figma_TripLeg_RestDurationMinutes CHECK (RestDurationMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripAssignment_BufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT CK_Figma_TripAssignment_BufferMinutes CHECK (BufferMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_FeedAmountKg', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT CK_Figma_HorseWelfareLog_FeedAmountKg CHECK (FeedAmountKg >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_FleetVehicle_MaintenanceDates', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT CK_Figma_FleetVehicle_MaintenanceDates CHECK (MaintenanceTo IS NULL OR (MaintenanceFrom IS NOT NULL AND MaintenanceTo >= MaintenanceFrom));';
IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_MaintenanceDates', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT CK_Figma_StallEquipment_MaintenanceDates CHECK (MaintenanceTo IS NULL OR (MaintenanceFrom IS NOT NULL AND MaintenanceTo >= MaintenanceFrom));';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_ImportType', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_ImportType CHECK (ImportType IN (''TEMPORARY'',''PERMANENT'',''NEEDS_ADVICE''));';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentRequirement_Scope', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentRequirement_Scope CHECK (ScopeCode IS NULL OR (ScopeCode=''HORSE'' AND HorseID IS NOT NULL) OR ScopeCode=''SHIPMENT'');';
IF OBJECT_ID(N'dbo.CK_Figma_HorseReceipt_Identity', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT CK_Figma_HorseReceipt_Identity CHECK (IdentityCheckResult IN (''PENDING'',''MATCHED'',''MISMATCHED''));';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_Supersedes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT CK_Figma_HorseWelfareLog_Supersedes CHECK (SupersedesLogID IS NULL OR SupersedesLogID <> WelfareLogID);';
IF OBJECT_ID(N'dbo.CK_Figma_OperationalAdjustment_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT CK_Figma_OperationalAdjustment_CustomerDecision CHECK (CustomerDecision IN (''PENDING'',''ACCEPTED'',''REJECTED''));';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT CK_Figma_TripHandover_CustomerDecision CHECK (CustomerDecision IN (''ACCEPTED'',''EXCEPTION'',''NOT_DELIVERED''));';

-- Protect the selected operating quote; draft estimates cannot authorize a plan.
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Figma_TransportPlan_AcceptedQuote
ON dbo.TransportPlan AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted p JOIN dbo.Quote q ON q.QuoteID=p.AcceptedQuoteID
               WHERE q.AcceptedAt IS NULL OR q.QuoteStatus NOT IN (''ACCEPTED'',''SUPERSEDED''))
        THROW 51002, ''The plan requires a genuinely accepted quotation.'', 1;
END;';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Figma_Quote_AcceptedHistory
ON dbo.Quote AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted q JOIN dbo.TransportPlan p ON p.AcceptedQuoteID=q.QuoteID
               WHERE q.AcceptedAt IS NULL OR q.QuoteStatus NOT IN (''ACCEPTED'',''SUPERSEDED''))
        THROW 51002, ''An operating plan still references this accepted quote.'', 1;
END;';
EXEC sys.sp_executesql N'IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory WHERE MigrationID=''002_align_figma_without_organization'') INSERT dbo.SchemaMigrationHistory (MigrationID) VALUES (''002_align_figma_without_organization'');';
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS first application: 002_align_figma_without_organization.sql';
GO
PRINT N'RUN first application: 003_order_assessment_assignments.sql';
GO
-- F04: preserve history and serialize current case responsibility.
-- Run after 001/002 on a reviewed database. No rows are deleted or backfilled
-- with invented actors/deadlines. Resolve reported legacy duplicates first.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.OrderAssignment', N'U') IS NULL
    OR OBJECT_ID(N'dbo.OrderDecision', N'U') IS NULL
    THROW 51000, 'Apply the EquiTransit baseline and migration 002 first.', 1;

IF EXISTS (SELECT RevisionID FROM dbo.OrderDecision
           GROUP BY RevisionID HAVING COUNT(*) > 1)
    THROW 51001, 'Multiple decisions for a revision require manual review.', 1;
IF EXISTS (SELECT OrderID, AssignmentRole FROM dbo.OrderAssignment
           WHERE EndedAt IS NULL GROUP BY OrderID, AssignmentRole
           HAVING COUNT(*) > 1)
    THROW 51002, 'Duplicate active case assignments require manual review.', 1;

IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'DueAt') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD DueAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignmentNote') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignmentNote NVARCHAR(1000) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = N'FK_OrderAssignment_AssignedBy')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          FK_OrderAssignment_AssignedBy FOREIGN KEY (AssignedBy)
          REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'CK_OrderAssignment_DueAt')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          CK_OrderAssignment_DueAt CHECK
          (DueAt IS NULL OR DueAt >= AssignedAt);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderDecision')
                 AND name = N'UX_OrderDecision_Revision')
    CREATE UNIQUE INDEX UX_OrderDecision_Revision
        ON dbo.OrderDecision(RevisionID);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'UX_OrderAssignment_ActiveRole')
    CREATE UNIQUE INDEX UX_OrderAssignment_ActiveRole
        ON dbo.OrderAssignment(OrderID, AssignmentRole)
        WHERE EndedAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'IX_OrderAssignment_ActiveStaff')
    CREATE INDEX IX_OrderAssignment_ActiveStaff
        ON dbo.OrderAssignment(StaffID, OrderID) WHERE EndedAt IS NULL;
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 003_order_assessment_assignments.sql';
GO
PRINT N'RUN first application: 004_service_assessment_sources.sql';
GO
-- F05 read contracts for sourced itinerary and Specialist quantity assessment.
-- After 001/002/003. Does not approve existing distances or seed demo prices.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    THROW 51000, 'Apply migration 003 before migration 004.', 1;

IF COL_LENGTH(N'dbo.TripLeg', N'DistanceSource') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceSource NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedBy') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedAt') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_TripLeg_DistanceConfirmedBy')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      FK_TripLeg_DistanceConfirmedBy FOREIGN KEY (DistanceConfirmedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_TripLeg_DistanceSource')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      CK_TripLeg_DistanceSource CHECK
      ((DistanceSource IS NULL AND DistanceConfirmedBy IS NULL
        AND DistanceConfirmedAt IS NULL) OR
       (DistanceSource IS NOT NULL AND LEN(LTRIM(RTRIM(DistanceSource)))>0
        AND DistanceConfirmedBy IS NOT NULL AND DistanceConfirmedAt IS NOT NULL));');

IF OBJECT_ID(N'dbo.RevisionQuarantineAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionQuarantineAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        Applicability VARCHAR(20) NOT NULL,
        RequiredDays INT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionQuarantineAssessment_Outcome CHECK
            ((Applicability='REQUIRED' AND RequiredDays IS NOT NULL
              AND RequiredDays>0)
              OR (Applicability='NOT_REQUIRED' AND RequiredDays IS NULL)),
        CONSTRAINT CK_RevisionQuarantineAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionQuarantineAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionQuarantineAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;

IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionVeterinaryAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        VisitCount INT NOT NULL,
        TestCount INT NOT NULL,
        DoseCount INT NOT NULL,
        CertificateCount INT NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionVeterinaryAssessment_Counts CHECK
            (VisitCount>=0 AND TestCount>=0 AND DoseCount>=0
              AND CertificateCount>=0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;
IF COL_LENGTH(N'dbo.RevisionQuarantineAssessment',N'SupersededAt') IS NULL
    OR COL_LENGTH(N'dbo.RevisionVeterinaryAssessment',N'SupersededAt') IS NULL
    THROW 51001, 'Review existing assessment tables before applying 004.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionQuarantineAssessment')
      AND name=N'UX_QuarantineAssessment_Current')
    CREATE UNIQUE INDEX UX_QuarantineAssessment_Current
        ON dbo.RevisionQuarantineAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionVeterinaryAssessment')
      AND name=N'UX_VeterinaryAssessment_Current')
    CREATE UNIQUE INDEX UX_VeterinaryAssessment_Current
        ON dbo.RevisionVeterinaryAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 004_service_assessment_sources.sql';
GO
PRINT N'RUN first application: 005_versioned_quotations.sql';
GO
-- F06: source-backed, immutable quotation snapshots. After migrations 001-004.
-- Review/backup before deployment. Never run test fixtures on EquiTransit.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
    THROW 51000, 'Apply migrations 001-004 first.', 1;

IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    ALTER TABLE dbo.Quote ADD PricingSnapshotJSON NVARCHAR(MAX) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RetailUnitPrice') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RetailUnitPrice DECIMAL(18,2) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedBy') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedAt') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_SupplierOffer_RecordedBy')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      FK_SupplierOffer_RecordedBy FOREIGN KEY(RecordedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_Quote_PricingSnapshot')
    EXEC(N'ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT
      CK_Quote_PricingSnapshot CHECK(PricingSnapshotJSON IS NULL
      OR ISJSON(PricingSnapshotJSON)=1);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_SupplierOffer_RetailPrice')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      CK_SupplierOffer_RetailPrice CHECK(RetailUnitPrice IS NULL
      OR RetailUnitPrice>=0);');

-- A draft needs an expiry before approval, without inventing IssuedAt.
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name=N'CK_Quote_Validity')
    ALTER TABLE dbo.Quote DROP CONSTRAINT CK_Quote_Validity;
ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT CK_Quote_Validity CHECK
    (ValidUntil IS NULL OR
     (IssuedAt IS NULL AND ValidUntil>CreatedAt) OR
     (IssuedAt IS NOT NULL AND ValidUntil>IssuedAt));

IF EXISTS (SELECT RevisionID FROM dbo.Quote WHERE QuoteStatus='ACCEPTED'
           GROUP BY RevisionID HAVING COUNT(*)>1)
    THROW 51001, 'Repair duplicate accepted quotes before migration.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.Quote') AND
               name=N'UX_Quote_AcceptedRevision')
    CREATE UNIQUE INDEX UX_Quote_AcceptedRevision ON dbo.Quote(RevisionID)
        WHERE QuoteStatus='ACCEPTED';
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.QuotePaymentSchedule')
      AND i.is_unique=1 AND i.has_filter=0
      AND (SELECT COUNT(*) FROM sys.index_columns c
           WHERE c.object_id=i.object_id AND c.index_id=i.index_id
             AND c.key_ordinal>0)=2
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'QuoteID')
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'SequenceNo'))
    CREATE UNIQUE INDEX UX_QuotePaymentSchedule_Sequence
        ON dbo.QuotePaymentSchedule(QuoteID,SequenceNo);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.AuditEvent') AND
               name=N'IX_AuditEvent_QuotationOperation')
    CREATE INDEX IX_AuditEvent_QuotationOperation
        ON dbo.AuditEvent(EntityType,EntityID,UserID,EventID)
        INCLUDE(ActionCode);
COMMIT;

GO
PRINT N'PASS first application: 005_versioned_quotations.sql';
GO
PRINT N'RUN first application: 006_service_tariff_management.sql';
GO
-- Manager price version provenance; apply after 005. No production fixtures.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    THROW 51000, 'Apply migrations 001-005 before tariff management.', 1;
IF COL_LENGTH(N'dbo.ServiceTariff',N'SourceReference') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedBy') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedAt') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'RecordVersion') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD RecordVersion ROWVERSION NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys
              WHERE name=N'FK_ServiceTariff_PublishedBy')
    EXEC(N'ALTER TABLE dbo.ServiceTariff WITH CHECK ADD CONSTRAINT
      FK_ServiceTariff_PublishedBy FOREIGN KEY(PublishedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
-- Preserve legacy rows as unknown provenance; never invent approval evidence.
COMMIT;

GO
PRINT N'PASS first application: 006_service_tariff_management.sql';
GO
PRINT N'RUN first application: 007_manual_bank_payments.sql';
GO
-- F07 additive provenance, immutable evidence and idempotent receipts.
-- Requires 005; 006 is reserved for tariff management. No real DB fixtures.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Quote','PricingSnapshotJSON') IS NULL
    THROW 51000,'Apply quotation migration005 first.',1;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    CREATE TABLE dbo.PaymentOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL CHECK(ISJSON(ReceiptJSON)=1),
        CONSTRAINT PK_PaymentOperation PRIMARY KEY(OrderID,UserID,OperationID)
    );
IF OBJECT_ID('dbo.PaymentProof','U') IS NULL
    CREATE TABLE dbo.PaymentProof (
        FileKey UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        MediaType VARCHAR(20) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_PaymentProof_Size CHECK
          (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_PaymentProof_Mime CHECK
          (MediaType IN ('image/png','image/jpeg'))
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest')
    AND name='UQ_PaymentRequest_Schedule')
    CREATE UNIQUE INDEX UQ_PaymentRequest_Schedule
      ON dbo.PaymentRequest(ScheduleID) WHERE ScheduleID IS NOT NULL;
IF COL_LENGTH('dbo.PaymentTransaction','ReversalReason') IS NULL
    ALTER TABLE dbo.PaymentTransaction ADD ReversalReason NVARCHAR(2000) NULL,
        ReversedAt DATETIME2(0) NULL, ReversedBy BIGINT NULL
        REFERENCES dbo.StaffProfile(StaffID),
        ReversalProofFileKey UNIQUEIDENTIFIER NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND name='UQ_Refund_BankReference')
    CREATE UNIQUE INDEX UQ_Refund_BankReference
      ON dbo.Refund(BankRefundReference) WHERE BankRefundReference IS NOT NULL;
COMMIT;

GO
PRINT N'PASS first application: 007_manual_bank_payments.sql';
GO
PRINT N'RUN first application: 008_dashboards_notifications_audit.sql';
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    THROW 51000,'Apply F07 migration007 before F16.',1;
IF COL_LENGTH('dbo.AuditEvent','EventKey') IS NULL
    ALTER TABLE dbo.AuditEvent ADD EventKey VARCHAR(64) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND name='UQ_AuditEvent_EventKey')
    EXEC('CREATE UNIQUE INDEX UQ_AuditEvent_EventKey
      ON dbo.AuditEvent(EventKey) WHERE EventKey IS NOT NULL');
IF COL_LENGTH('dbo.Notification','SourceEventID') IS NULL
    ALTER TABLE dbo.Notification ADD SourceEventID BIGINT NULL
        REFERENCES dbo.AuditEvent(EventID), EmailState VARCHAR(20) NULL,
        EmailLeaseUntil DATETIME2(0) NULL, EmailAttempts INT NOT NULL DEFAULT 0;
IF OBJECT_ID('dbo.NotificationProjection','U') IS NULL
BEGIN
    CREATE TABLE dbo.NotificationProjection (
      EventID BIGINT NOT NULL PRIMARY KEY REFERENCES dbo.AuditEvent(EventID),
      ProjectedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME());
    -- Establish deployment watermark; do not email old audit history.
    INSERT dbo.NotificationProjection(EventID)
        SELECT EventID FROM dbo.AuditEvent;
END;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='UQ_Notification_EventRecipient')
    EXEC('CREATE UNIQUE INDEX UQ_Notification_EventRecipient
      ON dbo.Notification(SourceEventID,UserID)
      WHERE SourceEventID IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='IX_Notification_RecipientPage')
    CREATE INDEX IX_Notification_RecipientPage ON dbo.Notification
      (UserID,NotificationID DESC) INCLUDE (OrderID,ReadAt,CreatedAt);
COMMIT;

GO
PRINT N'PASS first application: 008_dashboards_notifications_audit.sql';
GO
PRINT N'RUN first application: 009_compliance_legal_evidence.sql';
GO
-- F08 additive private evidence and phase assessment, after 001-008.
-- Never seeds legal rules, exemptions, permits or file evidence.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.NotificationProjection',N'U') IS NULL
    THROW 51000,'Apply prerequisites through 008 before 009.',1;
IF COL_LENGTH(N'dbo.ComplianceRuleVersion',N'AuthorityRequired') IS NULL
    ALTER TABLE dbo.ComplianceRuleVersion ADD AuthorityRequired BIT NOT NULL
        CONSTRAINT DF_ComplianceRule_Authority DEFAULT(1);
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'SourceReference') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'RuleID') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD RuleID BIGINT NULL
        CONSTRAINT FK_AuthorityProcedure_Rule
        REFERENCES dbo.ComplianceRuleVersion(RuleID);
IF COL_LENGTH(N'dbo.DocumentVersion',N'RevokedAt') IS NULL
    ALTER TABLE dbo.DocumentVersion ADD RevokedAt DATETIME2(0) NULL,
        RevokedBy BIGINT NULL REFERENCES dbo.StaffProfile(StaffID),
        RevocationReason NVARCHAR(2000) NULL;
IF OBJECT_ID(N'dbo.ComplianceFile',N'U') IS NULL
    CREATE TABLE dbo.ComplianceFile (
        FileID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        FileKey UNIQUEIDENTIFIER NOT NULL UNIQUE,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash VARCHAR(128) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_ComplianceFile_Size CHECK
            (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_ComplianceFile_Type CHECK
            (ContentType IN ('application/pdf','image/png','image/jpeg'))
    );
IF OBJECT_ID(N'dbo.ComplianceOperation',N'U') IS NULL
    CREATE TABLE dbo.ComplianceOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_ComplianceOperation PRIMARY KEY(OrderID,UserID,OperationID),
        CONSTRAINT CK_ComplianceOperation_JSON CHECK(ISJSON(ReceiptJSON)=1)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklist',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklist (
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        TransitCountriesJSON NVARCHAR(500) NOT NULL,
        RuleIDsJSON NVARCHAR(2000) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT PK_ComplianceChecklist PRIMARY KEY(RevisionID,PhaseCode),
        CONSTRAINT CK_ComplianceChecklist_JSON CHECK
            (ISJSON(TransitCountriesJSON)=1 AND ISJSON(RuleIDsJSON)=1),
        CONSTRAINT CK_ComplianceChecklist_Dates CHECK(ValidUntil>AssessedAt)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklistHistory',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklistHistory (
        AssessmentID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        SnapshotJSON NVARCHAR(MAX) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT CK_ComplianceChecklistHistory_JSON
            CHECK(ISJSON(SnapshotJSON)=1)
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.ComplianceFile') AND name='IX_ComplianceFile_Order')
    CREATE INDEX IX_ComplianceFile_Order ON dbo.ComplianceFile(OrderID,FileID);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.DocumentRequirement')
    AND name='IX_DocumentRequirement_PhaseRule')
    CREATE INDEX IX_DocumentRequirement_PhaseRule ON
        dbo.DocumentRequirement(RevisionID,PhaseCode,RuleID);
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 009_compliance_legal_evidence.sql';
GO
PRINT N'RUN first application: 010_resources_availability_fit.sql';
GO
-- F09 additive resources; review then deploy explicitly. No seed data.
-- Requires the existing 001..009 baseline. Never auto-run on EquiTransit.
-- Legacy buffers stay NULL: Manager must explicitly configure them.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    THROW 51000, 'Deploy resource baseline first.', 1;
IF COL_LENGTH('dbo.FleetVehicle','RecordVersion') IS NULL
    ALTER TABLE dbo.FleetVehicle ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.StallEquipment','RecordVersion') IS NULL
    ALTER TABLE dbo.StallEquipment ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.Supplier','RecordVersion') IS NULL
    ALTER TABLE dbo.Supplier ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.StallFitPolicy','RecordVersion') IS NULL
    ALTER TABLE dbo.StallFitPolicy ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.FleetVehicle','BufferMinutes') IS NULL
    ALTER TABLE dbo.FleetVehicle ADD BufferMinutes INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE name='CK_Resource_FleetVehicle_Buffer')
    ALTER TABLE dbo.FleetVehicle WITH CHECK ADD CONSTRAINT
        CK_Resource_FleetVehicle_Buffer CHECK
        (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);
IF COL_LENGTH('dbo.StallEquipment','BufferMinutes') IS NULL
    ALTER TABLE dbo.StallEquipment ADD BufferMinutes INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE name='CK_Resource_StallEquipment_Buffer')
    ALTER TABLE dbo.StallEquipment WITH CHECK ADD CONSTRAINT
        CK_Resource_StallEquipment_Buffer CHECK
        (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);
IF OBJECT_ID('dbo.ResourceOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.ResourceOperation (
        UserID BIGINT NOT NULL,
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ResourceOperation PRIMARY KEY (UserID,OperationID),
        CONSTRAINT FK_ResourceOperation_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_ResourceOperation_JSON CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND
    name='IX_Resource_VehicleWindow')
    CREATE INDEX IX_Resource_VehicleWindow ON dbo.TripLeg
        (VehicleID,PlannedDeparture,PlannedArrival)
        INCLUDE (PlanID,LegStatus) WHERE VehicleID IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND
    name='IX_Resource_StallWindow')
    CREATE INDEX IX_Resource_StallWindow ON dbo.StallAssignment
        (StallID,AssignedFrom,AssignedTo) INCLUDE (LegID,Status);
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_VehicleSchedule
ON dbo.TripLeg AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE VehicleID IS NOT NULL)
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.FleetVehicle v
        WITH (UPDLOCK,HOLDLOCK) ON v.VehicleID=i.VehicleID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=v.OwnerSupplierID
        WHERE i.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        (i.TransportMode<>'ROAD' OR v.OperationalStatus<>'ACTIVE'
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
        THROW 51002, 'Vehicle mode, evidence or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripLeg b
        WITH (UPDLOCK,HOLDLOCK) ON a.VehicleID=b.VehicleID AND a.LegID<>b.LegID
        JOIN dbo.FleetVehicle v ON v.VehicleID=a.VehicleID
        JOIN dbo.TransportPlan pa ON pa.PlanID=a.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=b.PlanID
        WHERE a.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        b.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.PlannedDeparture IS NULL OR b.PlannedArrival IS NULL OR
         (a.PlannedDeparture<DATEADD(minute,v.BufferMinutes,b.PlannedArrival)
          AND b.PlannedDeparture<DATEADD(minute,v.BufferMinutes,
              a.PlannedArrival))))
        THROW 51003, 'Vehicle has an overlapping assignment.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_StallSchedule
ON dbo.StallAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallEquipment e
        WITH (UPDLOCK,HOLDLOCK) ON e.StallID=i.StallID
        JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=e.OwnerSupplierID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (l.TransportMode<>'AIR' OR e.EquipmentStatus<>'ACTIVE'
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
        THROW 51004, 'Stall mode, capacity or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.StallAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StallID=b.StallID
        AND a.StallAssignmentID<>b.StallAssignmentID
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo)))
        AND NOT (
            a.SharingCode='SHARED' AND b.SharingCode='SHARED'
            AND la.TransportMode='AIR' AND lb.TransportMode='AIR'
            AND la.StartPoint=lb.StartPoint AND la.EndPoint=lb.EndPoint
            AND la.PlannedFlightNumber IS NOT NULL
            AND la.PlannedFlightNumber=lb.PlannedFlightNumber
            AND la.PlannedDeparture=lb.PlannedDeparture
            AND la.PlannedArrival=lb.PlannedArrival
            AND pa.OrderID<>pb.OrderID))
        THROW 51005, 'Physical stall has an overlapping assignment.', 1;
    IF EXISTS (SELECT 1 FROM inserted a
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
        AND pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
        AND (SELECT COALESCE(SUM(b.SlotQuantity),0)
             FROM dbo.StallAssignment b
             JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
             JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
             WHERE b.StallID=a.StallID
             AND b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
             AND pb.PlanStatus NOT IN
                 ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
             AND a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
             AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo))
            > e.CapacitySlots)
        THROW 51006, 'Shared stall slot capacity exceeded.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_PlanActivation
ON dbo.TransportPlan AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(PlanStatus) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d
        ON d.PlanID=i.PlanID WHERE
        (i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT') AND
         d.PlanStatus NOT IN ('CONFIRMED','ACTIVE','IN_TRANSIT')) OR
        (d.PlanStatus IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
         i.PlanStatus NOT IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')))
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    -- Reuse the same checks; no window, status or carrier proof is changed.
    UPDATE l SET LegStatus=l.LegStatus FROM dbo.TripLeg l
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE a SET Status=a.Status FROM dbo.StallAssignment a
        JOIN dbo.TripLeg l ON l.LegID=a.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 010_resources_availability_fit.sql';
GO
PRINT N'RUN first application: 011_multimodal_operational_plan.sql';
GO
-- F10 operational planning; review then deploy explicitly. No seed data.
-- Requires the existing 001..010 baseline. Never auto-run on EquiTransit.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.ResourceOperation','U') IS NULL
    THROW 51000, 'Deploy migration 010 first.', 1;
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
GO
CREATE OR ALTER TRIGGER dbo.TR_Plan_DriverSchedule
ON dbo.TripAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.StaffProfile s WITH (HOLDLOCK) ON s.StaffID=i.StaffID
        LEFT JOIN dbo.[User] u WITH (HOLDLOCK) ON u.UserID=s.UserID
        LEFT JOIN dbo.[Role] r WITH (HOLDLOCK) ON r.RoleID=u.RoleID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (s.StaffID IS NULL OR u.Status<>'ACTIVE'
         OR (i.RoleInTrip='DRIVER' AND (l.TransportMode<>'ROAD'
             OR r.RoleCode<>'DRIVER'))
         OR COALESCE(i.BufferMinutes,0)<0
         OR i.AssignedFrom IS NULL OR i.AssignedTo IS NULL
         OR i.AssignedFrom>=i.AssignedTo))
        THROW 51006, 'Trip staff mode, role or window invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StaffID=b.StaffID
        AND a.AssignmentID<>b.AssignmentID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,a.AssignedTo))))
        THROW 51007, 'Driver or staff has an overlapping trip assignment.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_PlanActivation
ON dbo.TransportPlan AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(PlanStatus) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d
        ON d.PlanID=i.PlanID WHERE
        (i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT') AND
         d.PlanStatus NOT IN ('CONFIRMED','ACTIVE','IN_TRANSIT')) OR
        (d.PlanStatus IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
         i.PlanStatus NOT IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')))
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
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
        WHERE i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND ob.OrderStatus NOT IN
            ('COMPLETED','DELIVERED','CANCELLED','CANCELED','CLOSED','REJECTED')
        AND pb.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND (i.PlannedStartTime IS NULL OR i.PlannedEndTime IS NULL OR
             pb.PlannedStartTime IS NULL OR pb.PlannedEndTime IS NULL OR
             (i.PlannedStartTime<pb.PlannedEndTime AND
              pb.PlannedStartTime<i.PlannedEndTime)))
        THROW 51008, 'Horse has an overlapping active operational plan.', 1;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 011_multimodal_operational_plan.sql';
GO
PRINT N'RUN first application: 012_departure_gates_horse_receipt.sql';
GO
-- F11 departure gates and horse receipt; review then deploy explicitly.
-- Requires the existing 001..011 baseline. Never auto-run on EquiTransit.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.PlanOperation','U') IS NULL
    THROW 51000, 'Deploy migration 011 first.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND
    name='IX_HorseReceipt_PlanLeg')
    CREATE INDEX IX_HorseReceipt_PlanLeg ON dbo.HorseReceipt
        (PlanID,LegID,ReceiptID DESC)
        INCLUDE (ReceivedBy,IdentityCheckResult,ReceiptStatus,ReceivedAt);
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_StallSchedule
ON dbo.StallAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallEquipment e
        WITH (UPDLOCK,HOLDLOCK) ON e.StallID=i.StallID
        JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=e.OwnerSupplierID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (l.TransportMode<>'AIR' OR e.EquipmentStatus<>'ACTIVE'
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
        THROW 51004, 'Stall mode, capacity or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.StallAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StallID=b.StallID
        AND a.StallAssignmentID<>b.StallAssignmentID
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo)))
        AND NOT (
            a.SharingCode='SHARED' AND b.SharingCode='SHARED'
            AND la.TransportMode='AIR' AND lb.TransportMode='AIR'
            AND la.StartPoint=lb.StartPoint AND la.EndPoint=lb.EndPoint
            AND la.PlannedFlightNumber IS NOT NULL
            AND la.PlannedFlightNumber=lb.PlannedFlightNumber
            AND la.PlannedDeparture=lb.PlannedDeparture
            AND la.PlannedArrival=lb.PlannedArrival
            AND pa.OrderID<>pb.OrderID))
        THROW 51005, 'Physical stall has an overlapping assignment.', 1;
    IF EXISTS (SELECT 1 FROM inserted a
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
        AND pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
        AND (SELECT COALESCE(SUM(b.SlotQuantity),0)
             FROM dbo.StallAssignment b
             JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
             JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
             WHERE b.StallID=a.StallID
             AND b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
             AND pb.PlanStatus NOT IN
                 ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
             AND a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
             AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo))
            > e.CapacitySlots)
        THROW 51006, 'Shared stall slot capacity exceeded.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Departure_FirstLegGuard
ON dbo.TripLeg AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE SequenceNo=1
        AND (ActualDeparture IS NOT NULL OR LegStatus='IN_TRANSIT'))
        RETURN;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        LEFT JOIN dbo.StaffProfile m ON m.StaffID=p.DepartureAuthorizedBy
        LEFT JOIN dbo.[User] u ON u.UserID=m.UserID
        LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID
        WHERE i.SequenceNo=1
        AND (i.ActualDeparture IS NOT NULL OR i.LegStatus='IN_TRANSIT')
        AND (i.TransportMode<>'ROAD'
             OR i.ActualDeparture IS NULL
             OR p.FinancialGateStatus<>'PASS'
             OR p.DocGateStatus<>'PASS'
             OR p.OperationalGateStatus<>'PASS'
             OR p.GatesCheckedAt IS NULL
             OR p.DepartureAuthorizedBy IS NULL
             OR p.DepartureAuthorizedAt IS NULL
             OR u.Status<>'ACTIVE'
             OR r.RoleCode<>'MANAGER'
             OR NOT EXISTS (SELECT 1 FROM dbo.HorseReceipt hr
                 WHERE hr.PlanID=i.PlanID AND hr.LegID=i.LegID
                 AND hr.IdentityCheckResult='MATCHED'
                 AND hr.ReceiptStatus='ACCEPTED'
                 AND hr.ReceivedAt<=i.ActualDeparture)))
        THROW 51010, 'First leg departure requires PASS gates, manager authorization and matched horse receipt.', 1;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 012_departure_gates_horse_receipt.sql';
GO
PRINT N'RUN first application: 013_staff_provisioning.sql';
GO
-- F15: additive staff metadata, CAS and private idempotent receipts.
-- Apply through the reviewed deployment process; never on app startup.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.StaffProfile','StaffStatus') IS NULL
    ALTER TABLE dbo.StaffProfile ADD StaffStatus varchar(20) NOT NULL
        CONSTRAINT DF_StaffProfile_StaffStatus DEFAULT 'ACTIVE' WITH VALUES;
IF COL_LENGTH('dbo.StaffProfile','EmployeeCode') IS NULL
    ALTER TABLE dbo.StaffProfile ADD EmployeeCode varchar(50) NULL;
IF COL_LENGTH('dbo.StaffProfile','Department') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Department nvarchar(100) NULL;
IF COL_LENGTH('dbo.StaffProfile','Specialization') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Specialization nvarchar(200) NULL;
IF COL_LENGTH('dbo.StaffProfile','RecordVersion') IS NULL
    ALTER TABLE dbo.StaffProfile ADD RecordVersion rowversion NOT NULL;

IF OBJECT_ID('dbo.CK_StaffProfile_StaffStatus','C') IS NULL
    EXEC('ALTER TABLE dbo.StaffProfile WITH CHECK ADD CONSTRAINT '
        + 'CK_StaffProfile_StaffStatus CHECK '
        + '(StaffStatus IN (''ACTIVE'',''INACTIVE'',''LOCKED''))');
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID('dbo.StaffProfile')
        AND name='UQ_StaffProfile_EmployeeCode')
    EXEC('CREATE UNIQUE INDEX UQ_StaffProfile_EmployeeCode '
        + 'ON dbo.StaffProfile(EmployeeCode) '
        + 'WHERE EmployeeCode IS NOT NULL');

IF OBJECT_ID('dbo.StaffAdminOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.StaffAdminOperation (
        UserID bigint NOT NULL,
        OperationID uniqueidentifier NOT NULL,
        RequestHash char(64) NOT NULL,
        ReceiptJSON nvarchar(max) NOT NULL,
        CreatedAt datetime2(0) NOT NULL
            CONSTRAINT DF_StaffAdminOperation_CreatedAt
                DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_StaffAdminOperation
            PRIMARY KEY (UserID,OperationID),
        CONSTRAINT FK_StaffAdminOperation_User
            FOREIGN KEY (UserID) REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_StaffAdminOperation_JSON
            CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 013_staff_provisioning.sql';
GO
PRINT N'RUN first application: 014_leg_execution_welfare_tracking.sql';
GO
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

GO
PRINT N'PASS first application: 014_leg_execution_welfare_tracking.sql';
GO
PRINT N'RUN first application: 015_incidents_controlled_operational_adjustments.sql';
GO
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

GO
PRINT N'PASS first application: 015_incidents_controlled_operational_adjustments.sql';
GO
PRINT N'RUN first application: 016_trip_handover_actual_costs.sql';
GO
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

GO
PRINT N'PASS first application: 016_trip_handover_actual_costs.sql';
GO
PRINT N'RUN first application: 017_refresh_token_rotation.sql';
GO
-- Additive refresh credentials. No raw bearer or cookie value is persisted.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RefreshTokenSession', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RefreshTokenSession (
        TokenID bigint IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_RefreshTokenSession PRIMARY KEY,
        FamilyID uniqueidentifier NOT NULL,
        UserID bigint NOT NULL,
        TokenHash char(64) NOT NULL,
        CredentialHash char(64) NOT NULL,
        IssuedAt datetime2(0) NOT NULL,
        ExpiresAt datetime2(0) NOT NULL,
        RevokedAt datetime2(0) NULL,
        RevokeReason varchar(40) NULL,
        ReplacedByTokenID bigint NULL,
        CONSTRAINT UQ_RefreshTokenSession_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_RefreshTokenSession_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT FK_RefreshTokenSession_Replacement
            FOREIGN KEY (ReplacedByTokenID)
            REFERENCES dbo.RefreshTokenSession(TokenID),
        CONSTRAINT CK_RefreshTokenSession_Expiry
            CHECK (ExpiresAt > IssuedAt)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_User')
    CREATE INDEX IX_RefreshTokenSession_User
        ON dbo.RefreshTokenSession(UserID,RevokedAt,ExpiresAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_Family')
    CREATE INDEX IX_RefreshTokenSession_Family
        ON dbo.RefreshTokenSession(FamilyID);
COMMIT TRANSACTION;

GO
PRINT N'PASS first application: 017_refresh_token_rotation.sql';
GO
PRINT N'RUN first application: 018_visual_journey_graph_nodes.sql';
GO
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

GO
PRINT N'PASS first application: 018_visual_journey_graph_nodes.sql';
GO
PRINT N'RUN first application: 019_handover_file_storage.sql';
GO
-- F14 private handover and actual-cost evidence storage.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF @@TRANCOUNT <> 0
    THROW 51019, 'Run migration 019 without an existing transaction.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51019, 'Another schema migration holds the lock.', 1;

IF OBJECT_ID(N'dbo.HandoverFile', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.HandoverFile (
        FileID BIGINT IDENTITY(1,1) NOT NULL,
        FileKey UNIQUEIDENTIFIER NOT NULL,
        OrderID BIGINT NOT NULL,
        UploadedBy BIGINT NOT NULL,
        Purpose VARCHAR(32) NOT NULL,
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash CHAR(64) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL
            CONSTRAINT DF_HandoverFile_CreatedAt DEFAULT (sysutcdatetime()),
        CONSTRAINT PK_HandoverFile PRIMARY KEY CLUSTERED (FileID),
        CONSTRAINT UQ_HandoverFile_FileKey UNIQUE (FileKey),
        CONSTRAINT CK_HandoverFile_Purpose CHECK (Purpose IN (
            'HANDOVER_SIGNATURE', 'HANDOVER_PHOTO',
            'ACTUAL_COST_EVIDENCE')),
        CONSTRAINT CK_HandoverFile_Content CHECK (
            DATALENGTH(ContentBytes) > 0 AND DATALENGTH(ContentBytes)
            <= 5242880)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id=OBJECT_ID(N'dbo.HandoverFile')
        AND name=N'IX_HandoverFile_OrderID')
    CREATE INDEX IX_HandoverFile_OrderID
        ON dbo.HandoverFile(OrderID, Purpose);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_Order')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_Order FOREIGN KEY (OrderID)
        REFERENCES dbo.TransportOrder(OrderID);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_UploadedBy')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_UploadedBy FOREIGN KEY (UploadedBy)
        REFERENCES dbo.[User](UserID);

IF OBJECT_ID(N'dbo.SchemaMigrationHistory', N'U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
        WHERE MigrationID='019_handover_file_storage')
    INSERT dbo.SchemaMigrationHistory(MigrationID)
        VALUES ('019_handover_file_storage');

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS first application: 019_handover_file_storage.sql';
GO
PRINT N'RUN schema contract: verify-f12-f14-schema.sql';
GO
-- Read-only verification, no inferred migration history table.
SET NOCOUNT ON;
DECLARE @fail TABLE (Detail nvarchar(1000));
DECLARE @columns TABLE (T sysname,C sysname,Typ sysname,L int,
    P int,S int,N bit,I bit);
INSERT @columns VALUES
('LegMilestoneLog','MilestoneID','bigint',0,0,0,0,1),
('LegMilestoneLog','LegID','bigint',0,0,0,0,0),
('LegMilestoneLog','ReportedBy','bigint',0,0,0,0,0),
('LegMilestoneLog','MilestoneType','varchar',50,0,0,0,0),
('LegMilestoneLog','OccurredAt','datetime2',0,0,0,0,0),
('LegMilestoneLog','RecordedAt','datetime2',0,0,0,0,0),
('LegMilestoneLog','Latitude','decimal',0,9,6,1,0),
('LegMilestoneLog','Longitude','decimal',0,9,6,1,0),
('LegMilestoneLog','Notes','nvarchar',4000,0,0,1,0),
('LegMilestoneLog','SourceCode','varchar',30,0,0,1,0),
('LegMilestoneLog','SourceReference','nvarchar',1000,0,0,1,0),
('LegMilestoneLog','LocationDescription','nvarchar',1000,0,0,1,0),
('LegMilestoneLog','EstimatedArrivalAt','datetime2',0,0,0,1,0),
('LegMilestoneLog','DelayReason','nvarchar',2000,0,0,1,0),
('LegMilestoneLog','EvidenceFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','WelfareLogID','bigint',0,0,0,0,1),
('HorseWelfareLog','LegID','bigint',0,0,0,0,0),
('HorseWelfareLog','RecordedBy','bigint',0,0,0,0,0),
('HorseWelfareLog','CheckTime','datetime2',0,0,0,0,0),
('HorseWelfareLog','HealthStatus','varchar',50,0,0,0,0),
('HorseWelfareLog','TemperatureCelsius','decimal',0,8,2,1,0),
('HorseWelfareLog','WaterIntakeLiters','decimal',0,8,2,1,0),
('HorseWelfareLog','BehaviorNotes','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','PhotoFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','FeedAmountKg','decimal',0,8,2,1,0),
('HorseWelfareLog','CareNotes','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','InstructionFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','SupersedesLogID','bigint',0,0,0,1,0),
('IncidentReport','IncidentID','bigint',0,0,0,0,1),
('IncidentReport','LegID','bigint',0,0,0,0,0),
('IncidentReport','ReportedBy','bigint',0,0,0,0,0),
('IncidentReport','IncidentType','varchar',50,0,0,0,0),
('IncidentReport','SeverityLevel','varchar',50,0,0,0,0),
('IncidentReport','IncidentTime','datetime2',0,0,0,0,0),
('IncidentReport','Description','nvarchar',4000,0,0,0,0),
('IncidentReport','Status','varchar',50,0,0,0,0),
('IncidentReport','ImmediateActionNotes','nvarchar',4000,0,0,1,0),
('IncidentReport','EmergencyContact','nvarchar',1000,0,0,1,0),
('IncidentReport','AcknowledgedBy','bigint',0,0,0,1,0),
('IncidentReport','AcknowledgedAt','datetime2',0,0,0,1,0),
('IncidentReport','ResolvedBy','bigint',0,0,0,1,0),
('IncidentReport','ResolvedAt','datetime2',0,0,0,1,0),
('IncidentReport','ResolutionNotes','nvarchar',4000,0,0,1,0),
('IncidentReport','EvidenceFilesJSON','nvarchar',-1,0,0,1,0),
('OrderChangeRequest','ChangeRequestID','bigint',0,0,0,0,1),
('OrderChangeRequest','OrderID','bigint',0,0,0,0,0),
('OrderChangeRequest','BaseRevisionID','bigint',0,0,0,1,0),
('OrderChangeRequest','RequestedBy','bigint',0,0,0,0,0),
('OrderChangeRequest','ChangeType','varchar',30,0,0,0,0),
('OrderChangeRequest','Reason','nvarchar',4000,0,0,0,0),
('OrderChangeRequest','RequestedChangesJSON','nvarchar',-1,0,0,0,0),
('OrderChangeRequest','AttachmentFileKey','nvarchar',2048,0,0,1,0),
('OrderChangeRequest','Status','varchar',30,0,0,0,0),
('OrderChangeRequest','CreatedAt','datetime2',0,0,0,0,0),
('OrderChangeRequest','RespondedBy','bigint',0,0,0,1,0),
('OrderChangeRequest','RespondedAt','datetime2',0,0,0,1,0),
('OrderChangeRequest','ResponseReason','nvarchar',4000,0,0,1,0),
('OperationalAdjustment','AdjustmentID','bigint',0,0,0,0,1),
('OperationalAdjustment','PlanID','bigint',0,0,0,0,0),
('OperationalAdjustment','IncidentID','bigint',0,0,0,1,0),
('OperationalAdjustment','ProposedBy','bigint',0,0,0,0,0),
('OperationalAdjustment','ApprovedBy','bigint',0,0,0,1,0),
('OperationalAdjustment','ProposedChanges','nvarchar',-1,0,0,0,0),
('OperationalAdjustment','AdditionalCostEstimate','decimal',0,18,2,0,0),
('OperationalAdjustment','ApprovalStatus','varchar',50,0,0,0,0),
('OperationalAdjustment','ProposedAt','datetime2',0,0,0,0,0),
('OperationalAdjustment','DecidedAt','datetime2',0,0,0,1,0),
('OperationalAdjustment','ChangeRequestID','bigint',0,0,0,1,0),
('OperationalAdjustment','CustomerDecision','varchar',20,0,0,1,0),
('OperationalAdjustment','RespondedByCustomerID','bigint',0,0,0,1,0),
('OperationalAdjustment','CustomerRespondedAt','datetime2',0,0,0,1,0),
('OperationalAdjustment','CustomerResponseNotes','nvarchar',4000,0,0,1,0),
('TripHandover','HandoverID','bigint',0,0,0,0,1),
('TripHandover','LegID','bigint',0,0,0,0,0),
('TripHandover','RecordedBy','bigint',0,0,0,0,0),
('TripHandover','HandoverType','varchar',50,0,0,0,0),
('TripHandover','HandoverTime','datetime2',0,0,0,0,0),
('TripHandover','ReceivedByName','nvarchar',600,0,0,0,0),
('TripHandover','ReceiverAuthority','varchar',50,0,0,1,0),
('TripHandover','ReceiverSignatureFileKey','nvarchar',2048,0,0,1,0),
('TripHandover','HorseConditionStatus','varchar',50,0,0,0,0),
('TripHandover','Outcome','varchar',50,0,0,0,0),
('TripHandover','ExceptionNotes','nvarchar',4000,0,0,1,0),
('TripHandover','PhotoFileKey','nvarchar',2048,0,0,1,0),
('TripHandover','CustomerConfirmedAt','datetime2',0,0,0,1,0),
('TripHandover','LocationDescription','nvarchar',1000,0,0,1,0),
('TripHandover','CustomerDecision','varchar',20,0,0,1,0),
('TripHandover','CustomerFeedback','nvarchar',4000,0,0,1,0),
('TripHandover','CustomerRespondedAt','datetime2',0,0,0,1,0),
('TripHandover','RespondedByCustomerID','bigint',0,0,0,1,0),
('TripHandover','EvidenceFilesJSON','nvarchar',-1,0,0,1,0),
('ActualCost','CostID','bigint',0,0,0,0,1),
('ActualCost','PlanID','bigint',0,0,0,0,0),
('ActualCost','LegID','bigint',0,0,0,1,0),
('ActualCost','CostCode','varchar',50,0,0,0,0),
('ActualCost','Amount','decimal',0,18,2,0,0),
('ActualCost','Currency','varchar',3,0,0,0,0),
('ActualCost','IncurredAt','datetime2',0,0,0,0,0),
('ActualCost','Description','nvarchar',2000,0,0,1,0),
('ActualCost','RecordedBy','bigint',0,0,0,1,0),
('ActualCost','EvidenceFileKey','nvarchar',2048,0,0,1,0);
INSERT @columns VALUES
('LegMilestoneLog','PlanID','bigint',0,0,0,0,0),
('LegMilestoneLog','IsCustomerVisible','bit',0,0,0,0,0),
('LegMilestoneLog','PublicNote','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','PlanID','bigint',0,0,0,0,0),
('HorseWelfareLog','IsCustomerVisible','bit',0,0,0,0,0),
('HorseWelfareLog','PublicNote','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','RecordedAt','datetime2',0,0,0,0,0),
('HorseWelfareLog','SourceCode','varchar',30,0,0,1,0),
('HorseWelfareLog','SourceReference','nvarchar',1000,0,0,1,0);
INSERT @fail
SELECT CONCAT(e.T,'.',e.C,' missing/type/nullability/identity mismatch')
FROM @columns e LEFT JOIN sys.columns c
    ON c.object_id=OBJECT_ID('dbo.'+e.T) AND c.name=e.C
LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
WHERE c.column_id IS NULL OR t.name<>e.Typ OR c.is_nullable<>e.N
    OR c.is_identity<>e.I OR (e.L<>0 AND c.max_length<>e.L)
    OR (e.P<>0 AND c.precision<>e.P) OR c.scale<>e.S;
INSERT @fail
SELECT CONCAT(t.name,'.',c.name,' unexpected column')
FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
WHERE t.name IN (SELECT DISTINCT T FROM @columns)
    AND t.schema_id=SCHEMA_ID('dbo')
    AND NOT EXISTS(SELECT 1 FROM @columns e WHERE e.T=t.name AND e.C=c.name);
DECLARE @keys TABLE (T sysname,N sysname,C nvarchar(500),K varchar(20));
INSERT @keys VALUES
('LegMilestoneLog','PK_LegMilestoneLog','MilestoneID','PRIMARY KEY'),
('LegMilestoneLog','IX_LegMilestoneLog_LegID','LegID','INDEX'),
('LegMilestoneLog','IX_LegMilestoneLog_ReportedBy','ReportedBy','INDEX'),
('HorseWelfareLog','PK_HorseWelfareLog','WelfareLogID','PRIMARY KEY'),
('HorseWelfareLog','IX_HorseWelfareLog_LegID','LegID','INDEX'),
('HorseWelfareLog','IX_HorseWelfareLog_RecordedBy','RecordedBy','INDEX'),
('HorseWelfareLog','UQ_Figma_HorseWelfareLog_Parent','WelfareLogID,LegID','UNIQUE'),
('IncidentReport','PK_IncidentReport','IncidentID','PRIMARY KEY'),
('IncidentReport','IX_IncidentReport_LegID','LegID','INDEX'),
('IncidentReport','IX_IncidentReport_ReportedBy','ReportedBy','INDEX'),
('OrderChangeRequest','PK_OrderChangeRequest','ChangeRequestID','PRIMARY KEY'),
('OperationalAdjustment','PK_OperationalAdjustment','AdjustmentID','PRIMARY KEY'),
('OperationalAdjustment','IX_OperationalAdjustment_PlanID','PlanID','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_IncidentID','IncidentID','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_ProposedBy','ProposedBy','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_ApprovedBy','ApprovedBy','INDEX'),
('TripHandover','PK_TripHandover','HandoverID','PRIMARY KEY'),
('TripHandover','IX_TripHandover_LegID','LegID','INDEX'),
('TripHandover','IX_TripHandover_RecordedBy','RecordedBy','INDEX'),
('ActualCost','PK_ActualCost','CostID','PRIMARY KEY'),
('ActualCost','IX_ActualCost_PlanID','PlanID','INDEX'),
('ActualCost','IX_ActualCost_LegID','LegID','INDEX');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/disabled/key mismatch')
FROM @keys e LEFT JOIN sys.indexes i
    ON i.object_id=OBJECT_ID('dbo.'+e.T) AND i.name=e.N
OUTER APPLY(SELECT STRING_AGG(CAST(COL_NAME(ic.object_id,ic.column_id)
    AS nvarchar(max)),',') WITHIN GROUP(ORDER BY ic.key_ordinal) AS Keys
    FROM sys.index_columns ic WHERE ic.object_id=i.object_id
    AND ic.index_id=i.index_id AND ic.key_ordinal>0) k
WHERE i.index_id IS NULL OR i.is_disabled=1 OR k.Keys<>e.C
    OR (e.K='PRIMARY KEY' AND i.is_primary_key=0)
    OR (e.K='UNIQUE' AND i.is_unique=0);
DECLARE @fk TABLE (T sysname,N sysname,C nvarchar(500),
    R sysname,RC nvarchar(500));
INSERT @fk VALUES
('LegMilestoneLog','FK_LegMilestoneLog_LegID','LegID','TripLeg','LegID'),
('LegMilestoneLog','FK_LegMilestoneLog_ReportedBy','ReportedBy','StaffProfile','StaffID'),
('HorseWelfareLog','FK_Figma_HorseWelfareLog_SupersedesLogID','SupersedesLogID,LegID','HorseWelfareLog','WelfareLogID,LegID'),
('HorseWelfareLog','FK_HorseWelfareLog_LegID','LegID','TripLeg','LegID'),
('HorseWelfareLog','FK_HorseWelfareLog_RecordedBy','RecordedBy','StaffProfile','StaffID'),
('IncidentReport','FK_Figma_IncidentReport_AcknowledgedBy','AcknowledgedBy','StaffProfile','StaffID'),
('IncidentReport','FK_Figma_IncidentReport_ResolvedBy','ResolvedBy','StaffProfile','StaffID'),
('IncidentReport','FK_IncidentReport_LegID','LegID','TripLeg','LegID'),
('IncidentReport','FK_IncidentReport_ReportedBy','ReportedBy','StaffProfile','StaffID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_BaseRevisionID','BaseRevisionID,OrderID','OrderRevision','RevisionID,OrderID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_OrderID','OrderID','TransportOrder','OrderID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_RequestedBy','RequestedBy','User','UserID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_RespondedBy','RespondedBy','StaffProfile','StaffID'),
('OperationalAdjustment','FK_Figma_OperationalAdjustment_ChangeRequestID','ChangeRequestID','OrderChangeRequest','ChangeRequestID'),
('OperationalAdjustment','FK_Figma_OperationalAdjustment_RespondedByCustomerID','RespondedByCustomerID','Customer','CustomerID'),
('OperationalAdjustment','FK_OperationalAdjustment_ApprovedBy','ApprovedBy','StaffProfile','StaffID'),
('OperationalAdjustment','FK_OperationalAdjustment_IncidentID','IncidentID','IncidentReport','IncidentID'),
('OperationalAdjustment','FK_OperationalAdjustment_PlanID','PlanID','TransportPlan','PlanID'),
('OperationalAdjustment','FK_OperationalAdjustment_ProposedBy','ProposedBy','StaffProfile','StaffID'),
('TripHandover','FK_Figma_TripHandover_RespondedByCustomerID','RespondedByCustomerID','Customer','CustomerID'),
('TripHandover','FK_TripHandover_LegID','LegID','TripLeg','LegID'),
('TripHandover','FK_TripHandover_RecordedBy','RecordedBy','StaffProfile','StaffID'),
('ActualCost','FK_ActualCost_LegID','LegID','TripLeg','LegID'),
('ActualCost','FK_ActualCost_PlanID','PlanID','TransportPlan','PlanID'),
('ActualCost','FK_Figma_ActualCost_LegID','LegID,PlanID','TripLeg','LegID,PlanID'),
('ActualCost','FK_Figma_ActualCost_RecordedBy','RecordedBy','StaffProfile','StaffID');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/untrusted/composite FK mismatch')
FROM @fk e LEFT JOIN sys.foreign_keys f
    ON f.parent_object_id=OBJECT_ID('dbo.'+e.T) AND f.name=e.N
OUTER APPLY(SELECT STRING_AGG(CAST(COL_NAME(x.parent_object_id,
        x.parent_column_id) AS nvarchar(max)),',')
        WITHIN GROUP(ORDER BY x.constraint_column_id) AS C,
    STRING_AGG(CAST(COL_NAME(x.referenced_object_id,x.referenced_column_id)
        AS nvarchar(max)),',')
        WITHIN GROUP(ORDER BY x.constraint_column_id) AS RC
    FROM sys.foreign_key_columns x WHERE x.constraint_object_id=f.object_id) m
WHERE f.object_id IS NULL OR f.is_disabled=1 OR f.is_not_trusted=1
    OR OBJECT_NAME(f.referenced_object_id)<>e.R OR m.C<>e.C OR m.RC<>e.RC
    OR f.delete_referential_action<>0 OR f.update_referential_action<>0;
DECLARE @ck TABLE (T sysname,N sysname,D nvarchar(max));
INSERT @ck VALUES
('LegMilestoneLog','CK_LegMilestoneLog_Latitude',N'([Latitude]>=(-90) AND [Latitude]<=(90))'),
('LegMilestoneLog','CK_LegMilestoneLog_Longitude',N'([Longitude]>=(-180) AND [Longitude]<=(180))'),
('HorseWelfareLog','CK_Figma_HorseWelfareLog_FeedAmountKg',N'([FeedAmountKg]>=(0))'),
('HorseWelfareLog','CK_Figma_HorseWelfareLog_Supersedes',N'([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID])'),
('HorseWelfareLog','CK_HorseWelfareLog_WaterIntakeLiters',N'([WaterIntakeLiters]>=(0))'),
('IncidentReport','CK_Figma_IncidentReport_EvidenceFilesJSON',N'([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))'),
('OrderChangeRequest','CK_OrderChangeRequest_JSON',N'(isjson([RequestedChangesJSON])=(1))'),
('OrderChangeRequest','CK_OrderChangeRequest_Reason',N'(len(ltrim(rtrim([Reason])))>(0))'),
('OperationalAdjustment','CK_Figma_OperationalAdjustment_CustomerDecision',N'([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING'')'),
('OperationalAdjustment','CK_OperationalAdjustment_AdditionalCostEstimate',N'([AdditionalCostEstimate]>=(0))'),
('OperationalAdjustment','CK_OperationalAdjustment_ProposedChanges_JSON',N'(isjson([ProposedChanges])=(1))'),
('TripHandover','CK_Figma_TripHandover_CustomerDecision',N'([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED'')'),
('TripHandover','CK_Figma_TripHandover_EvidenceFilesJSON',N'([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))'),
('ActualCost','CK_ActualCost_Amount',N'([Amount]>=(0))');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/untrusted/check definition mismatch')
FROM @ck e LEFT JOIN sys.check_constraints c
    ON c.parent_object_id=OBJECT_ID('dbo.'+e.T) AND c.name=e.N
WHERE c.object_id IS NULL OR c.is_disabled=1 OR c.is_not_trusted=1
    OR LOWER(REPLACE(REPLACE(REPLACE(c.definition,CHAR(13),''),
        CHAR(10),''),' ',''))<>LOWER(REPLACE(e.D,' ',''));
IF OBJECT_ID('dbo.ActualCost','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ActualCost');
IF OBJECT_ID('dbo.AuditEvent','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuditEvent');
IF OBJECT_ID('dbo.AuthenticationToken','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuthenticationToken');
IF OBJECT_ID('dbo.AuthorityProcedure','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuthorityProcedure');
IF OBJECT_ID('dbo.ComplianceClearance','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ComplianceClearance');
IF OBJECT_ID('dbo.ComplianceRuleVersion','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ComplianceRuleVersion');
IF OBJECT_ID('dbo.Customer','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Customer');
IF OBJECT_ID('dbo.DocumentRequirement','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentRequirement');
IF OBJECT_ID('dbo.DocumentReview','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentReview');
IF OBJECT_ID('dbo.DocumentSubmission','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentSubmission');
IF OBJECT_ID('dbo.DocumentTypeCatalog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentTypeCatalog');
IF OBJECT_ID('dbo.DocumentVersion','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentVersion');
IF OBJECT_ID('dbo.FleetVehicle','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table FleetVehicle');
IF OBJECT_ID('dbo.FlightReservation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table FlightReservation');
IF OBJECT_ID('dbo.Horse','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Horse');
IF OBJECT_ID('dbo.HorseDocument','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseDocument');
IF OBJECT_ID('dbo.HorseReceipt','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseReceipt');
IF OBJECT_ID('dbo.HorseWelfareLog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseWelfareLog');
IF OBJECT_ID('dbo.IncidentReport','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table IncidentReport');
IF OBJECT_ID('dbo.InsuranceChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table InsuranceChoice');
IF OBJECT_ID('dbo.InsurancePolicy','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table InsurancePolicy');
IF OBJECT_ID('dbo.LegMilestoneLog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table LegMilestoneLog');
IF OBJECT_ID('dbo.Notification','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Notification');
IF OBJECT_ID('dbo.OperationalAdjustment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OperationalAdjustment');
IF OBJECT_ID('dbo.OrderAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderAssignment');
IF OBJECT_ID('dbo.OrderChangeRequest','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderChangeRequest');
IF OBJECT_ID('dbo.OrderDecision','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderDecision');
IF OBJECT_ID('dbo.OrderRevision','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderRevision');
IF OBJECT_ID('dbo.OrderServiceChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderServiceChoice');
IF OBJECT_ID('dbo.PaymentAllocation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentAllocation');
IF OBJECT_ID('dbo.PaymentReport','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentReport');
IF OBJECT_ID('dbo.PaymentRequest','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentRequest');
IF OBJECT_ID('dbo.PaymentTransaction','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentTransaction');
IF OBJECT_ID('dbo.QuarantineChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuarantineChoice');
IF OBJECT_ID('dbo.QuarantineReservation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuarantineReservation');
IF OBJECT_ID('dbo.Quote','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Quote');
IF OBJECT_ID('dbo.QuoteItem','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuoteItem');
IF OBJECT_ID('dbo.QuotePaymentSchedule','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuotePaymentSchedule');
IF OBJECT_ID('dbo.Refund','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Refund');
IF OBJECT_ID('dbo.RevisionQuarantineAssessment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table RevisionQuarantineAssessment');
IF OBJECT_ID('dbo.RevisionVeterinaryAssessment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table RevisionVeterinaryAssessment');
IF OBJECT_ID('dbo.Role','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Role');
IF OBJECT_ID('dbo.ServiceTariff','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ServiceTariff');
IF OBJECT_ID('dbo.StaffProfile','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StaffProfile');
IF OBJECT_ID('dbo.StallAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallAssignment');
IF OBJECT_ID('dbo.StallChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallChoice');
IF OBJECT_ID('dbo.StallEquipment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallEquipment');
IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallFitPolicy');
IF OBJECT_ID('dbo.Supplier','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Supplier');
IF OBJECT_ID('dbo.SupplierOffer','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table SupplierOffer');
IF OBJECT_ID('dbo.TransportOrder','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportOrder');
IF OBJECT_ID('dbo.TransportOrderDetail','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportOrderDetail');
IF OBJECT_ID('dbo.TransportPlan','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportPlan');
IF OBJECT_ID('dbo.TripAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripAssignment');
IF OBJECT_ID('dbo.TripHandover','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripHandover');
IF OBJECT_ID('dbo.TripLeg','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripLeg');
IF OBJECT_ID('dbo.User','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table User');
IF EXISTS(SELECT 1 FROM @fail)
BEGIN
    SELECT Detail FROM @fail ORDER BY Detail;
    THROW 51014,'Schema verification failed; inspect metadata differences.',1;
END;
SELECT e.T AS TableName,COUNT(*) AS VerifiedColumns,'PASS' AS Status
FROM @columns e GROUP BY e.T ORDER BY e.T;

GO
PRINT N'PASS schema contract: verify-f12-f14-schema.sql';
GO
PRINT N'RUN graph smoke test: test-graph-crud-sandbox.sql';
GO
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

GO
PRINT N'PASS graph smoke test: test-graph-crud-sandbox.sql';
GO
SET XACT_ABORT ON;
GO
PRINT N'RUN reapplication: 000_figma_schema.sql';
GO
-- 000: reproducible 57-table baseline, no customer/demo data.
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

IF OBJECT_ID('dbo.AuditEvent','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuditEvent] (
    [EventID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NULL,
    [EntityType] VARCHAR(50) NOT NULL,
    [EntityID] BIGINT NOT NULL,
    [ActionCode] VARCHAR(50) NOT NULL,
    [ChangeSnapshot] NVARCHAR(MAX) NULL,
    [OccurredAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_AuditEvent_OccurredAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.AuthenticationToken','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuthenticationToken] (
    [TokenID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [TokenHash] VARCHAR(64) NOT NULL,
    [Purpose] VARCHAR(20) NOT NULL,
    [ExpiresAt] DATETIME2(0) NOT NULL,
    [UsedAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
)';

IF OBJECT_ID('dbo.AuthorityProcedure','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[AuthorityProcedure] (
    [ProcedureID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ProcedureCode] VARCHAR(50) NOT NULL,
    [AuthorityName] NVARCHAR(300) NOT NULL,
    [ApplicationReference] VARCHAR(150) NULL,
    [ApprovalStatus] VARCHAR(50) NOT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [DecidedAt] DATETIME2(0) NULL,
    [PhaseCode] VARCHAR(50) NULL,
    [AuthorityContact] NVARCHAR(500) NULL,
    [HoldReason] NVARCHAR(2000) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [RecordedBy] BIGINT NULL
)';

IF OBJECT_ID('dbo.ComplianceClearance','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ComplianceClearance] (
    [ClearanceID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [CheckedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_ComplianceClearance_CheckedAt] DEFAULT (sysutcdatetime()),
    [ExpiresAt] DATETIME2(0) NULL,
    [AssessmentNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.ComplianceRuleVersion','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ComplianceRuleVersion] (
    [RuleID] BIGINT IDENTITY(1,1) NOT NULL,
    [RuleCode] VARCHAR(50) NOT NULL,
    [VersionNo] INT NOT NULL,
    [OriginCountryCode] VARCHAR(2) NOT NULL,
    [DestinationCountryCode] VARCHAR(2) NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [SourceURL] NVARCHAR(2048) NOT NULL,
    [ReviewedAt] DATETIME2(0) NULL,
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [TransitCountryCode] VARCHAR(2) NULL,
    [RuleDescription] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.Customer','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Customer] (
    [CustomerID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [FullName] NVARCHAR(150) NOT NULL,
    [Phone] VARCHAR(30) NULL,
    [Address] NVARCHAR(500) NULL,
    [EmailNotificationsEnabled] BIT NULL
)';

IF OBJECT_ID('dbo.DocumentRequirement','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentRequirement] (
    [RequirementID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [RuleID] BIGINT NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [PhaseCode] VARCHAR(50) NOT NULL,
    [IsMandatory] BIT NOT NULL,
    [DueAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [Instructions] NVARCHAR(2000) NULL,
    [ScopeCode] VARCHAR(20) NULL,
    [HorseID] BIGINT NULL,
    [ValidityConditionsJSON] NVARCHAR(MAX) NULL,
    [CaseJustification] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.DocumentReview','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentReview] (
    [ReviewID] BIGINT IDENTITY(1,1) NOT NULL,
    [SubmissionID] BIGINT NOT NULL,
    [ReviewedBy] BIGINT NOT NULL,
    [Decision] VARCHAR(50) NOT NULL,
    [Feedback] NVARCHAR(2000) NULL,
    [ReviewedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.DocumentSubmission','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentSubmission] (
    [SubmissionID] BIGINT IDENTITY(1,1) NOT NULL,
    [RequirementID] BIGINT NOT NULL,
    [DocumentVersionID] BIGINT NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [SubmittedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_DocumentSubmission_SubmittedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.DocumentTypeCatalog','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentTypeCatalog] (
    [TypeCode] VARCHAR(50) NOT NULL,
    [DisplayName] NVARCHAR(200) NOT NULL,
    [Description] NVARCHAR(1000) NULL,
    [IsActive] BIT NOT NULL
        CONSTRAINT [DF_Base_DocumentTypeCatalog_IsActive] DEFAULT ((1))
)';

IF OBJECT_ID('dbo.DocumentVersion','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentVersion] (
    [DocumentVersionID] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentID] BIGINT NOT NULL,
    [VersionNo] INT NOT NULL,
    [DocumentNumber] VARCHAR(50) NULL,
    [FileKey] NVARCHAR(1024) NOT NULL,
    [FileHash] VARCHAR(128) NULL,
    [IssuedDate] DATE NULL,
    [ExpiryDate] DATE NULL,
    [UploadedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_DocumentVersion_UploadedAt] DEFAULT (sysutcdatetime()),
    [UploadedBy] BIGINT NULL,
    [OriginalFileName] NVARCHAR(255) NULL,
    [ContentType] VARCHAR(100) NULL,
    [FileSizeBytes] BIGINT NULL
)';

IF OBJECT_ID('dbo.FleetVehicle','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[FleetVehicle] (
    [VehicleID] BIGINT IDENTITY(1,1) NOT NULL,
    [VehicleCode] VARCHAR(50) NOT NULL,
    [PlateNumber] VARCHAR(50) NOT NULL,
    [HorseCapacity] INT NOT NULL,
    [ClimateSystemType] VARCHAR(50) NOT NULL,
    [OperationalStatus] VARCHAR(50) NOT NULL,
    [OwnerSupplierID] BIGINT NULL,
    [MaintenanceFrom] DATETIME2(0) NULL,
    [MaintenanceTo] DATETIME2(0) NULL,
    [DocumentExpiresAt] DATETIME2(0) NULL,
    [AssetNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.FlightReservation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[FlightReservation] (
    [ReservationID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [OfferID] BIGINT NOT NULL,
    [AirlineName] VARCHAR(50) NOT NULL,
    [FlightNumber] VARCHAR(50) NOT NULL,
    [ReservationReference] VARCHAR(150) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [ConfirmedAt] DATETIME2(0) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [ConfirmedBy] BIGINT NULL,
    [SupplierContact] NVARCHAR(500) NULL,
    [UsableCapacitySlots] DECIMAL(5,1) NULL
)';

IF OBJECT_ID('dbo.Horse','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Horse] (
    [HorseID] BIGINT IDENTITY(1,1) NOT NULL,
    [CustomerID] BIGINT NOT NULL,
    [MicrochipID] VARCHAR(50) NULL,
    [PassportNumber] VARCHAR(50) NULL,
    [Name] NVARCHAR(150) NOT NULL,
    [Breed] NVARCHAR(100) NULL,
    [DateOfBirth] DATE NULL,
    [SexCode] VARCHAR(20) NULL,
    [WeightKg] DECIMAL(8,2) NULL,
    [HeightCm] DECIMAL(6,2) NULL,
    [BodyWidthCm] DECIMAL(6,2) NULL,
    [MeasuredAt] DATETIME2(0) NULL,
    [SpecialCareNotes] NVARCHAR(1000) NULL,
    [HandlingNotes] NVARCHAR(1000) NULL,
    [FeedWaterRequirements] NVARCHAR(1000) NULL,
    [StallPreferences] NVARCHAR(1000) NULL,
    [VeterinaryInstructions] NVARCHAR(2000) NULL,
    [VeterinaryInstructionSource] NVARCHAR(1000) NULL,
    [RecordVersion] TIMESTAMP NOT NULL
)';

IF OBJECT_ID('dbo.HorseDocument','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[HorseDocument] (
    [DocumentID] BIGINT IDENTITY(1,1) NOT NULL,
    [HorseID] BIGINT NOT NULL,
    [DocumentType] VARCHAR(50) NOT NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_HorseDocument_CreatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.HorseReceipt','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[HorseReceipt] (
    [ReceiptID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [ReceivedBy] BIGINT NOT NULL,
    [ObservedMicrochip] VARCHAR(50) NULL,
    [ConditionNotes] NVARCHAR(2000) NULL,
    [ReceiptStatus] VARCHAR(50) NOT NULL,
    [ReceivedAt] DATETIME2(0) NOT NULL,
    [LegID] BIGINT NULL,
    [ObservedPassportNumber] VARCHAR(50) NULL,
    [IdentityCheckResult] VARCHAR(20) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [LocationDescription] NVARCHAR(500) NULL
)';

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

IF OBJECT_ID('dbo.InsuranceChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[InsuranceChoice] (
    [InsuranceChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [CoverageCode] VARCHAR(50) NOT NULL,
    [DeclaredValue] DECIMAL(18,2) NULL,
    [ValueCurrency] VARCHAR(3) NULL,
    [ExistingPolicyReference] NVARCHAR(100) NULL
)';

IF OBJECT_ID('dbo.InsurancePolicy','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[InsurancePolicy] (
    [PolicyID] BIGINT IDENTITY(1,1) NOT NULL,
    [InsuranceChoiceID] BIGINT NOT NULL,
    [InsurerName] NVARCHAR(300) NOT NULL,
    [PolicyNumber] VARCHAR(150) NOT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [CoverageFrom] DATETIME2(0) NOT NULL,
    [CoverageTo] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [VerifiedBy] BIGINT NULL,
    [VerifiedAt] DATETIME2(0) NULL,
    [PolicyTerms] NVARCHAR(MAX) NULL
)';

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

IF OBJECT_ID('dbo.Notification','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Notification] (
    [NotificationID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [OrderID] BIGINT NULL,
    [Title] NVARCHAR(300) NOT NULL,
    [Message] NVARCHAR(2000) NOT NULL,
    [ReadAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Notification_CreatedAt] DEFAULT (sysutcdatetime())
)';

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

IF OBJECT_ID('dbo.OrderAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderAssignment] (
    [AssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [StaffID] BIGINT NOT NULL,
    [AssignmentRole] VARCHAR(30) NOT NULL,
    [AssignedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderAssignment_AssignedAt] DEFAULT (sysutcdatetime()),
    [EndedAt] DATETIME2(0) NULL,
    [AssignedBy] BIGINT NULL,
    [DueAt] DATETIME2(0) NULL,
    [AssignmentNote] NVARCHAR(1000) NULL
)';

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

IF OBJECT_ID('dbo.OrderDecision','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderDecision] (
    [DecisionID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ManagerID] BIGINT NOT NULL,
    [Decision] VARCHAR(20) NOT NULL,
    [Reason] NVARCHAR(1000) NULL,
    [DecidedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderDecision_DecidedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.OrderRevision','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderRevision] (
    [RevisionID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [RevisionNo] INT NOT NULL,
    [OrderSnapshot] NVARCHAR(MAX) NOT NULL,
    [ChangeReason] NVARCHAR(500) NULL,
    [SubmittedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderRevision_SubmittedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.OrderServiceChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderServiceChoice] (
    [ChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [OptionCode] VARCHAR(50) NOT NULL,
    [ArrangedBy] VARCHAR(20) NOT NULL,
    [ChosenAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_OrderServiceChoice_ChosenAt] DEFAULT (sysutcdatetime()),
    [SelectionInputsJSON] NVARCHAR(MAX) NULL,
    [ProviderName] NVARCHAR(200) NULL,
    [ProviderContact] NVARCHAR(500) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL
)';

IF OBJECT_ID('dbo.PaymentAllocation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentAllocation] (
    [AllocationID] BIGINT IDENTITY(1,1) NOT NULL,
    [TransactionID] BIGINT NOT NULL,
    [PaymentRequestID] BIGINT NOT NULL,
    [AllocatedAmount] DECIMAL(18,2) NOT NULL,
    [AllocatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_PaymentAllocation_AllocatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.PaymentReport','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentReport] (
    [ReportID] BIGINT IDENTITY(1,1) NOT NULL,
    [PaymentRequestID] BIGINT NOT NULL,
    [ReportedAmount] DECIMAL(18,2) NOT NULL,
    [BankReference] VARCHAR(150) NULL,
    [ProofFileKey] NVARCHAR(1024) NULL,
    [CustomerReportedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_PaymentReport_CustomerReportedAt] DEFAULT (sysutcdatetime()),
    [Status] VARCHAR(50) NOT NULL,
    [TransferredAt] DATETIME2(0) NULL,
    [ReportedCurrency] VARCHAR(3) NULL
)';

IF OBJECT_ID('dbo.PaymentRequest','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentRequest] (
    [PaymentRequestID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [PaymentNumber] VARCHAR(50) NOT NULL,
    [PaymentStage] VARCHAR(50) NOT NULL,
    [AmountDue] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [DueAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [ScheduleID] BIGINT NULL
)';

IF OBJECT_ID('dbo.PaymentTransaction','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[PaymentTransaction] (
    [TransactionID] BIGINT IDENTITY(1,1) NOT NULL,
    [ReconciledBy] BIGINT NULL,
    [RecipientAccount] VARCHAR(150) NOT NULL,
    [BankTransactionReference] VARCHAR(150) NOT NULL,
    [AmountReceived] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [PostedAt] DATETIME2(0) NOT NULL,
    [ReconciledAt] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [VerificationSource] NVARCHAR(500) NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [InternalNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.QuarantineChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuarantineChoice] (
    [QuarantineChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [PackageCode] VARCHAR(50) NOT NULL,
    [RequiredDays] INT NULL,
    [ConfirmedBy] BIGINT NULL,
    [DaysConfirmedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.QuarantineReservation','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuarantineReservation] (
    [ReservationID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuarantineChoiceID] BIGINT NOT NULL,
    [FacilityName] NVARCHAR(300) NOT NULL,
    [ReservationReference] VARCHAR(150) NULL,
    [StartsAt] DATETIME2(0) NOT NULL,
    [EndsAt] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [SupplierID] BIGINT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [ConfirmedBy] BIGINT NULL,
    [ConfirmedAt] DATETIME2(0) NULL,
    [ExpiresAt] DATETIME2(0) NULL,
    [FacilityContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.Quote','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Quote] (
    [QuoteID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ApprovedBy] BIGINT NULL,
    [QuoteNumber] VARCHAR(30) NOT NULL,
    [VersionNo] INT NOT NULL
        CONSTRAINT [DF_Base_Quote_VersionNo] DEFAULT ((1)),
    [Currency] VARCHAR(3) NOT NULL,
    [TotalQuotedAmount] DECIMAL(18,2) NULL,
    [QuoteStatus] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_Quote_QuoteStatus] DEFAULT (''DRAFT''),
    [ApprovedAt] DATETIME2(0) NULL,
    [IssuedAt] DATETIME2(0) NULL,
    [ValidUntil] DATETIME2(0) NULL,
    [AcceptedAt] DATETIME2(0) NULL,
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Quote_CreatedAt] DEFAULT (sysutcdatetime()),
    [PreparedBy] BIGINT NULL,
    [SubmittedAt] DATETIME2(0) NULL,
    [IssuedBy] BIGINT NULL,
    [AcceptedByCustomerID] BIGINT NULL,
    [TermsVersion] VARCHAR(50) NULL,
    [TermsText] NVARCHAR(MAX) NULL,
    [CancellationTerms] NVARCHAR(MAX) NULL,
    [TermsFileKey] NVARCHAR(1024) NULL,
    [ReviewFeedback] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.QuoteItem','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuoteItem] (
    [ItemID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [TariffID] BIGINT NULL,
    [OfferID] BIGINT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [CostCategory] VARCHAR(50) NOT NULL,
    [Description] NVARCHAR(2000) NOT NULL,
    [UnitCode] VARCHAR(50) NOT NULL,
    [Quantity] DECIMAL(18,4) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Amount] DECIMAL(18,2) NOT NULL,
    [CalculationInputs] NVARCHAR(MAX) NULL
)';

IF OBJECT_ID('dbo.QuotePaymentSchedule','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuotePaymentSchedule] (
    [ScheduleID] BIGINT IDENTITY(1,1) NOT NULL,
    [QuoteID] BIGINT NOT NULL,
    [SequenceNo] INT NOT NULL,
    [PaymentStage] VARCHAR(50) NOT NULL,
    [AmountDue] DECIMAL(18,2) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [DueAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.Refund','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Refund] (
    [RefundID] BIGINT IDENTITY(1,1) NOT NULL,
    [AllocationID] BIGINT NOT NULL,
    [Amount] DECIMAL(18,2) NOT NULL,
    [Reason] NVARCHAR(2000) NOT NULL,
    [Status] VARCHAR(50) NOT NULL,
    [RefundedAt] DATETIME2(0) NULL,
    [RequestedBy] BIGINT NULL,
    [RequestedAt] DATETIME2(0) NULL,
    [ApprovedBy] BIGINT NULL,
    [ApprovedAt] DATETIME2(0) NULL,
    [BankRefundReference] VARCHAR(150) NULL,
    [ProofFileKey] NVARCHAR(1024) NULL
)';

IF OBJECT_ID('dbo.RevisionQuarantineAssessment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[RevisionQuarantineAssessment] (
    [AssessmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [Applicability] VARCHAR(20) NOT NULL,
    [RequiredDays] INT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [AssessedAt] DATETIME2(0) NOT NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [SourceReference] NVARCHAR(1000) NOT NULL,
    [SupersededAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.RevisionVeterinaryAssessment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[RevisionVeterinaryAssessment] (
    [AssessmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [VisitCount] INT NOT NULL,
    [TestCount] INT NOT NULL,
    [DoseCount] INT NOT NULL,
    [CertificateCount] INT NOT NULL,
    [SpecialistID] BIGINT NOT NULL,
    [AssessedAt] DATETIME2(0) NOT NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [SourceReference] NVARCHAR(1000) NOT NULL,
    [SupersededAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.Role','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Role] (
    [RoleID] BIGINT IDENTITY(1,1) NOT NULL,
    [RoleCode] VARCHAR(30) NOT NULL,
    [Description] NVARCHAR(255) NULL
)';

IF OBJECT_ID('dbo.ServiceTariff','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[ServiceTariff] (
    [TariffID] BIGINT IDENTITY(1,1) NOT NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [OptionCode] VARCHAR(50) NOT NULL,
    [CostGroup] VARCHAR(50) NOT NULL,
    [UnitCode] VARCHAR(30) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_Currency] DEFAULT (''USD''),
    [VersionNo] INT NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_VersionNo] DEFAULT ((1)),
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_ServiceTariff_Status] DEFAULT (''ACTIVE'')
)';

IF OBJECT_ID('dbo.StaffProfile','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StaffProfile] (
    [StaffID] BIGINT IDENTITY(1,1) NOT NULL,
    [UserID] BIGINT NOT NULL,
    [FullName] NVARCHAR(150) NOT NULL,
    [Phone] VARCHAR(30) NULL,
    [LicenseNumber] VARCHAR(50) NULL
)';

IF OBJECT_ID('dbo.StallAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallAssignment] (
    [StallAssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [StallID] BIGINT NOT NULL,
    [SlotQuantity] DECIMAL(5,1) NOT NULL,
    [SharingCode] VARCHAR(50) NOT NULL,
    [FitStatus] VARCHAR(50) NOT NULL,
    [CarrierAcceptanceStatus] VARCHAR(50) NOT NULL,
    [AssignedFrom] DATETIME2(0) NULL,
    [AssignedTo] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [PositionDescription] NVARCHAR(500) NULL,
    [CarrierApprovalFileKey] NVARCHAR(1024) NULL,
    [CarrierApprovedAt] DATETIME2(0) NULL,
    [CarrierContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.StallChoice','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallChoice] (
    [StallChoiceID] BIGINT IDENTITY(1,1) NOT NULL,
    [ChoiceID] BIGINT NOT NULL,
    [SlotQuantity] DECIMAL(3,1) NOT NULL,
    [SharingCode] VARCHAR(20) NOT NULL,
    [FitStatus] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_StallChoice_FitStatus] DEFAULT (''PENDING'')
)';

IF OBJECT_ID('dbo.StallEquipment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallEquipment] (
    [StallID] BIGINT IDENTITY(1,1) NOT NULL,
    [StallCode] VARCHAR(50) NOT NULL,
    [CapacitySlots] DECIMAL(5,1) NOT NULL,
    [InternalHeightCm] DECIMAL(8,2) NOT NULL,
    [InternalWidthCm] DECIMAL(8,2) NOT NULL,
    [EquipmentStatus] VARCHAR(50) NOT NULL,
    [OwnerSupplierID] BIGINT NULL,
    [WeightLimitKg] DECIMAL(8,2) NULL,
    [MaintenanceFrom] DATETIME2(0) NULL,
    [MaintenanceTo] DATETIME2(0) NULL,
    [DocumentExpiresAt] DATETIME2(0) NULL,
    [AssetNotes] NVARCHAR(2000) NULL
)';

IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallFitPolicy] (
    [PolicyID] BIGINT IDENTITY(1,1) NOT NULL,
    [PolicyCode] VARCHAR(50) NOT NULL,
    [VersionNo] INT NOT NULL,
    [StallID] BIGINT NULL,
    [CarrierSupplierID] BIGINT NULL,
    [SlotQuantity] DECIMAL(5,1) NOT NULL,
    [MaxWeightKg] DECIMAL(8,2) NULL,
    [MaxHeightCm] DECIMAL(8,2) NULL,
    [MaxWidthCm] DECIMAL(8,2) NULL,
    [EffectiveFrom] DATETIME2(0) NOT NULL,
    [EffectiveTo] DATETIME2(0) NULL,
    [SourceURL] NVARCHAR(2048) NULL,
    [SourceFileKey] NVARCHAR(1024) NULL,
    [ReviewedBy] BIGINT NULL,
    [ReviewedAt] DATETIME2(0) NULL,
    [Status] VARCHAR(20) NOT NULL
)';

IF OBJECT_ID('dbo.Supplier','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Supplier] (
    [SupplierID] BIGINT IDENTITY(1,1) NOT NULL,
    [Name] NVARCHAR(200) NOT NULL,
    [ContactName] NVARCHAR(150) NULL,
    [Email] VARCHAR(255) NULL,
    [Phone] VARCHAR(30) NULL,
    [ServicesOfferedJSON] NVARCHAR(MAX) NULL,
    [IsActive] BIT NOT NULL
        CONSTRAINT [DF_Base_Supplier_IsActive] DEFAULT ((1)),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_Supplier_CreatedAt] DEFAULT (sysutcdatetime())
)';

IF OBJECT_ID('dbo.SupplierOffer','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[SupplierOffer] (
    [OfferID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [ProviderName] NVARCHAR(200) NOT NULL,
    [OfferReference] VARCHAR(100) NULL,
    [ServiceCode] VARCHAR(50) NOT NULL,
    [UnitCode] VARCHAR(30) NOT NULL,
    [Quantity] DECIMAL(18,4) NOT NULL,
    [UnitPrice] DECIMAL(18,4) NOT NULL,
    [Currency] VARCHAR(3) NOT NULL,
    [IncludesStall] BIT NOT NULL
        CONSTRAINT [DF_Base_SupplierOffer_IncludesStall] DEFAULT ((0)),
    [IncludedCharges] NVARCHAR(MAX) NULL,
    [ValidUntil] DATETIME2(0) NOT NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_SupplierOffer_Status] DEFAULT (''RECEIVED''),
    [SupplierID] BIGINT NULL,
    [EvidenceFileKey] NVARCHAR(1024) NULL,
    [SourceContact] NVARCHAR(500) NULL
)';

IF OBJECT_ID('dbo.TransportOrder','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportOrder] (
    [OrderID] BIGINT IDENTITY(1,1) NOT NULL,
    [CustomerID] BIGINT NOT NULL,
    [OrderNumber] VARCHAR(30) NOT NULL,
    [OriginCountryCode] VARCHAR(2) NULL,
    [DestinationCountryCode] VARCHAR(2) NULL,
    [OriginAddress] NVARCHAR(500) NULL,
    [DestinationAddress] NVARCHAR(500) NULL,
    [OriginTimezone] VARCHAR(100) NULL,
    [DestinationTimezone] VARCHAR(100) NULL,
    [DeliveryDeadline] DATETIME2(0) NULL,
    [EventStartAt] DATETIME2(0) NULL,
    [Currency] VARCHAR(3) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_Currency] DEFAULT (''USD''),
    [OrderStatus] VARCHAR(30) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_OrderStatus] DEFAULT (''DRAFT''),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_TransportOrder_CreatedAt] DEFAULT (sysutcdatetime()),
    [PreferredDepartureAt] DATETIME2(0) NULL,
    [DateFlexibilityNotes] NVARCHAR(500) NULL,
    [ReceiverName] NVARCHAR(150) NULL,
    [ReceiverPhone] VARCHAR(30) NULL,
    [ReceiverEmail] VARCHAR(255) NULL,
    [ReceiverAuthority] NVARCHAR(500) NULL,
    [ImportType] VARCHAR(20) NULL,
    [EventName] NVARCHAR(200) NULL,
    [EventVenue] NVARCHAR(300) NULL,
    [EventTimezone] VARCHAR(100) NULL,
    [TransitAirportsJSON] NVARCHAR(MAX) NULL,
    [DraftPayloadJSON] NVARCHAR(MAX) NULL,
    [UpdatedAt] DATETIME2(0) NULL,
    [CurrentRevisionID] BIGINT NULL,
    [CurrentPlanID] BIGINT NULL,
    [RecordVersion] TIMESTAMP NOT NULL
)';

IF OBJECT_ID('dbo.TransportOrderDetail','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportOrderDetail] (
    [DetailID] BIGINT IDENTITY(1,1) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [HorseID] BIGINT NOT NULL,
    [SnapshotMicrochip] VARCHAR(50) NULL,
    [SnapshotSexCode] VARCHAR(20) NULL,
    [SnapshotWeightKg] DECIMAL(8,2) NULL,
    [SnapshotHeightCm] DECIMAL(6,2) NULL,
    [SnapshotBodyWidthCm] DECIMAL(6,2) NULL,
    [SnapshotMeasuredAt] DATETIME2(0) NULL,
    [SpecialNeeds] NVARCHAR(1000) NULL
)';

IF OBJECT_ID('dbo.TransportPlan','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TransportPlan] (
    [PlanID] BIGINT IDENTITY(1,1) NOT NULL,
    [RevisionID] BIGINT NOT NULL,
    [CreatedBy] BIGINT NOT NULL,
    [DepartureAuthorizedBy] BIGINT NULL,
    [VersionNo] INT NOT NULL,
    [PlannedStartTime] DATETIME2(0) NULL,
    [PlannedEndTime] DATETIME2(0) NULL,
    [FinancialGateStatus] VARCHAR(50) NOT NULL,
    [OperationalGateStatus] VARCHAR(50) NOT NULL,
    [DocGateStatus] VARCHAR(50) NOT NULL,
    [GatesCheckedAt] DATETIME2(0) NULL,
    [DepartureAuthorizedAt] DATETIME2(0) NULL,
    [PlanStatus] VARCHAR(50) NOT NULL,
    [OrderID] BIGINT NOT NULL,
    [AcceptedQuoteID] BIGINT NULL
)';

IF OBJECT_ID('dbo.TripAssignment','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TripAssignment] (
    [AssignmentID] BIGINT IDENTITY(1,1) NOT NULL,
    [LegID] BIGINT NOT NULL,
    [StaffID] BIGINT NOT NULL,
    [RoleInTrip] VARCHAR(50) NOT NULL,
    [AssignedFrom] DATETIME2(0) NULL,
    [AssignedTo] DATETIME2(0) NULL,
    [Status] VARCHAR(50) NOT NULL,
    [BufferMinutes] INT NULL,
    [ConflictNotes] NVARCHAR(1000) NULL
)';

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

IF OBJECT_ID('dbo.TripLeg','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[TripLeg] (
    [LegID] BIGINT IDENTITY(1,1) NOT NULL,
    [PlanID] BIGINT NOT NULL,
    [VehicleID] BIGINT NULL,
    [SequenceNo] INT NOT NULL,
    [TransportMode] VARCHAR(50) NOT NULL,
    [StartPoint] NVARCHAR(300) NOT NULL,
    [EndPoint] NVARCHAR(300) NOT NULL,
    [DistanceKm] DECIMAL(12,3) NOT NULL,
    [PlannedDeparture] DATETIME2(0) NULL,
    [PlannedArrival] DATETIME2(0) NULL,
    [ActualDeparture] DATETIME2(0) NULL,
    [ActualArrival] DATETIME2(0) NULL,
    [LegStatus] VARCHAR(50) NOT NULL,
    [StartTimezone] VARCHAR(100) NULL,
    [EndTimezone] VARCHAR(100) NULL,
    [SupplierID] BIGINT NULL,
    [PlannedFlightNumber] VARCHAR(50) NULL,
    [ConnectionBufferMinutes] INT NULL,
    [RestDurationMinutes] INT NULL,
    [CareInstructions] NVARCHAR(2000) NULL,
    [DistanceSource] NVARCHAR(1000) NULL,
    [DistanceConfirmedBy] BIGINT NULL,
    [DistanceConfirmedAt] DATETIME2(0) NULL
)';

IF OBJECT_ID('dbo.User','U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[User] (
    [UserID] BIGINT IDENTITY(1,1) NOT NULL,
    [RoleID] BIGINT NOT NULL,
    [Email] VARCHAR(255) NOT NULL,
    [PasswordHash] VARCHAR(255) NOT NULL,
    [Status] VARCHAR(20) NOT NULL
        CONSTRAINT [DF_Base_User_Status] DEFAULT (''ACTIVE''),
    [CreatedAt] DATETIME2(0) NOT NULL
        CONSTRAINT [DF_Base_User_CreatedAt] DEFAULT (sysutcdatetime())
)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD CONSTRAINT [PK_ActualCost] PRIMARY KEY ([CostID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_PlanID] ON dbo.[ActualCost] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ActualCost') AND name='IX_ActualCost_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ActualCost_LegID] ON dbo.[ActualCost] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] ADD CONSTRAINT [PK_AuditEvent] PRIMARY KEY ([EventID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND name='IX_AuditEvent_UserID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuditEvent_UserID] ON dbo.[AuditEvent] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] ADD CONSTRAINT [PK_AuthenticationToken] PRIMARY KEY ([TokenID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='UQ_AuthenticationToken_Hash')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] ADD CONSTRAINT [UQ_AuthenticationToken_Hash] UNIQUE ([TokenHash])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='IX_AuthenticationToken_UserPurpose')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuthenticationToken_UserPurpose] ON dbo.[AuthenticationToken] ([UserID],[Purpose])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthorityProcedure') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD CONSTRAINT [PK_AuthorityProcedure] PRIMARY KEY ([ProcedureID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='IX_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_AuthorityProcedure_RevisionID] ON dbo.[AuthorityProcedure] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD CONSTRAINT [PK_ComplianceClearance] PRIMARY KEY ([ClearanceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='IX_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ComplianceClearance_RevisionID] ON dbo.[ComplianceClearance] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='IX_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_ComplianceClearance_SpecialistID] ON dbo.[ComplianceClearance] ([SpecialistID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD CONSTRAINT [PK_ComplianceRuleVersion] PRIMARY KEY ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='UQ_ComplianceRuleVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD CONSTRAINT [UQ_ComplianceRuleVersion_Version] UNIQUE ([RuleCode],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Customer') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD CONSTRAINT [PK_Customer] PRIMARY KEY ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Customer') AND name='UQ_Customer_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD CONSTRAINT [UQ_Customer_UserID] UNIQUE ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD CONSTRAINT [PK_DocumentRequirement] PRIMARY KEY ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='IX_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentRequirement_RevisionID] ON dbo.[DocumentRequirement] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='IX_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentRequirement_RuleID] ON dbo.[DocumentRequirement] ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] ADD CONSTRAINT [PK_DocumentReview] PRIMARY KEY ([ReviewID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND name='IX_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentReview_SubmissionID] ON dbo.[DocumentReview] ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentReview') AND name='IX_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentReview_ReviewedBy] ON dbo.[DocumentReview] ([ReviewedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] ADD CONSTRAINT [PK_DocumentSubmission] PRIMARY KEY ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='IX_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentSubmission_RequirementID] ON dbo.[DocumentSubmission] ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='IX_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_DocumentSubmission_DocumentVersionID] ON dbo.[DocumentSubmission] ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentTypeCatalog') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentTypeCatalog] ADD CONSTRAINT [PK_DocumentTypeCatalog] PRIMARY KEY ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentVersion') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD CONSTRAINT [PK_DocumentVersion] PRIMARY KEY ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.DocumentVersion') AND name='UQ_DocumentVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD CONSTRAINT [UQ_DocumentVersion_Version] UNIQUE ([DocumentID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [PK_FleetVehicle] PRIMARY KEY ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND name='UQ_FleetVehicle_PlateNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [UQ_FleetVehicle_PlateNumber] UNIQUE ([PlateNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FleetVehicle') AND name='UQ_FleetVehicle_VehicleCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD CONSTRAINT [UQ_FleetVehicle_VehicleCode] UNIQUE ([VehicleCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD CONSTRAINT [PK_FlightReservation] PRIMARY KEY ([ReservationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND name='IX_FlightReservation_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_FlightReservation_LegID] ON dbo.[FlightReservation] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.FlightReservation') AND name='IX_FlightReservation_OfferID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_FlightReservation_OfferID] ON dbo.[FlightReservation] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Horse') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD CONSTRAINT [PK_Horse] PRIMARY KEY ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Horse') AND name='UX_Horse_MicrochipID')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_Horse_MicrochipID] ON dbo.[Horse] ([MicrochipID]) WHERE ([MicrochipID] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseDocument') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] ADD CONSTRAINT [PK_HorseDocument] PRIMARY KEY ([DocumentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseDocument') AND name='IX_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseDocument_HorseID] ON dbo.[HorseDocument] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD CONSTRAINT [PK_HorseReceipt] PRIMARY KEY ([ReceiptID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND name='IX_HorseReceipt_PlanID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseReceipt_PlanID] ON dbo.[HorseReceipt] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND name='IX_HorseReceipt_ReceivedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_HorseReceipt_ReceivedBy] ON dbo.[HorseReceipt] ([ReceivedBy])';
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
    object_id=OBJECT_ID('dbo.InsuranceChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] ADD CONSTRAINT [PK_InsuranceChoice] PRIMARY KEY ([InsuranceChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='UQ_InsuranceChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] ADD CONSTRAINT [UQ_InsuranceChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsurancePolicy') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD CONSTRAINT [PK_InsurancePolicy] PRIMARY KEY ([PolicyID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='IX_InsurancePolicy_InsuranceChoiceID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_InsurancePolicy_InsuranceChoiceID] ON dbo.[InsurancePolicy] ([InsuranceChoiceID])';
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
    object_id=OBJECT_ID('dbo.Notification') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] ADD CONSTRAINT [PK_Notification] PRIMARY KEY ([NotificationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification') AND name='IX_Notification_UserID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Notification_UserID] ON dbo.[Notification] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification') AND name='IX_Notification_OrderID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Notification_OrderID] ON dbo.[Notification] ([OrderID])';
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
    object_id=OBJECT_ID('dbo.OrderAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] ADD CONSTRAINT [PK_OrderAssignment] PRIMARY KEY ([AssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderAssignment') AND name='UX_OrderAssignment_ActiveRole')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_OrderAssignment_ActiveRole] ON dbo.[OrderAssignment] ([OrderID],[AssignmentRole]) WHERE ([EndedAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderAssignment') AND name='IX_OrderAssignment_ActiveStaff')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_OrderAssignment_ActiveStaff] ON dbo.[OrderAssignment] ([StaffID],[OrderID]) WHERE ([EndedAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderChangeRequest') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] ADD CONSTRAINT [PK_OrderChangeRequest] PRIMARY KEY ([ChangeRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderDecision') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] ADD CONSTRAINT [PK_OrderDecision] PRIMARY KEY ([DecisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderDecision') AND name='UX_OrderDecision_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] ADD CONSTRAINT [UX_OrderDecision_Revision] UNIQUE ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [PK_OrderRevision] PRIMARY KEY ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND name='UQ_OrderRevision_Order_RevisionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [UQ_OrderRevision_Order_RevisionNo] UNIQUE ([OrderID],[RevisionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderRevision') AND name='UQ_Figma_OrderRevision_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] ADD CONSTRAINT [UQ_Figma_OrderRevision_Parent] UNIQUE ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderServiceChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD CONSTRAINT [PK_OrderServiceChoice] PRIMARY KEY ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='UQ_OrderServiceChoice_Revision_Service')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD CONSTRAINT [UQ_OrderServiceChoice_Revision_Service] UNIQUE ([RevisionID],[ServiceCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] ADD CONSTRAINT [PK_PaymentAllocation] PRIMARY KEY ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='IX_PaymentAllocation_TransactionID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentAllocation_TransactionID] ON dbo.[PaymentAllocation] ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='IX_PaymentAllocation_PaymentRequestID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentAllocation_PaymentRequestID] ON dbo.[PaymentAllocation] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentReport') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD CONSTRAINT [PK_PaymentReport] PRIMARY KEY ([ReportID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentReport') AND name='IX_PaymentReport_PaymentRequestID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentReport_PaymentRequestID] ON dbo.[PaymentReport] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD CONSTRAINT [PK_PaymentRequest] PRIMARY KEY ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND name='UQ_PaymentRequest_PaymentNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD CONSTRAINT [UQ_PaymentRequest_PaymentNumber] UNIQUE ([PaymentNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest') AND name='IX_PaymentRequest_QuoteID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentRequest_QuoteID] ON dbo.[PaymentRequest] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD CONSTRAINT [PK_PaymentTransaction] PRIMARY KEY ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='UQ_PaymentTransaction_BankReference')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD CONSTRAINT [UQ_PaymentTransaction_BankReference] UNIQUE ([RecipientAccount],[BankTransactionReference])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='IX_PaymentTransaction_ReconciledBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_PaymentTransaction_ReconciledBy] ON dbo.[PaymentTransaction] ([ReconciledBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] ADD CONSTRAINT [PK_QuarantineChoice] PRIMARY KEY ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='UQ_QuarantineChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] ADD CONSTRAINT [UQ_QuarantineChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineReservation') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD CONSTRAINT [PK_QuarantineReservation] PRIMARY KEY ([ReservationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='IX_QuarantineReservation_QuarantineChoiceID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuarantineReservation_QuarantineChoiceID] ON dbo.[QuarantineReservation] ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [PK_Quote] PRIMARY KEY ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Quote_Revision_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Quote_Revision_Version] UNIQUE ([RevisionID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Quote_QuoteNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Quote_QuoteNumber] UNIQUE ([QuoteNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Quote') AND name='UQ_Figma_Quote_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD CONSTRAINT [UQ_Figma_Quote_Parent] UNIQUE ([QuoteID],[RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] ADD CONSTRAINT [PK_QuoteItem] PRIMARY KEY ([ItemID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_QuoteID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_QuoteID] ON dbo.[QuoteItem] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_TariffID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_TariffID] ON dbo.[QuoteItem] ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuoteItem') AND name='IX_QuoteItem_OfferID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_QuoteItem_OfferID] ON dbo.[QuoteItem] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [PK_QuotePaymentSchedule] PRIMARY KEY ([ScheduleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='UQ_QuotePaymentSchedule_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [UQ_QuotePaymentSchedule_Parent] UNIQUE ([ScheduleID],[QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='UQ_QuotePaymentSchedule_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] ADD CONSTRAINT [UQ_QuotePaymentSchedule_Sequence] UNIQUE ([QuoteID],[SequenceNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD CONSTRAINT [PK_Refund] PRIMARY KEY ([RefundID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND name='IX_Refund_AllocationID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_Refund_AllocationID] ON dbo.[Refund] ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] ADD CONSTRAINT [PK__Revision__3D2BF83E1E42ACCE] PRIMARY KEY ([AssessmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='UX_QuarantineAssessment_Current')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_QuarantineAssessment_Current] ON dbo.[RevisionQuarantineAssessment] ([RevisionID]) WHERE ([SupersededAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] ADD CONSTRAINT [PK__Revision__3D2BF83EB0D2F0D7] PRIMARY KEY ([AssessmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='UX_VeterinaryAssessment_Current')
    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX [UX_VeterinaryAssessment_Current] ON dbo.[RevisionVeterinaryAssessment] ([RevisionID]) WHERE ([SupersededAt] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Role') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Role] ADD CONSTRAINT [PK_Role] PRIMARY KEY ([RoleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Role') AND name='UQ_Role_RoleCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Role] ADD CONSTRAINT [UQ_Role_RoleCode] UNIQUE ([RoleCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ServiceTariff') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] ADD CONSTRAINT [PK_ServiceTariff] PRIMARY KEY ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.ServiceTariff') AND name='UQ_ServiceTariff_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] ADD CONSTRAINT [UQ_ServiceTariff_Version] UNIQUE ([ServiceCode],[OptionCode],[Currency],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StaffProfile') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] ADD CONSTRAINT [PK_StaffProfile] PRIMARY KEY ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StaffProfile') AND name='UQ_StaffProfile_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] ADD CONSTRAINT [UQ_StaffProfile_UserID] UNIQUE ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD CONSTRAINT [PK_StallAssignment] PRIMARY KEY ([StallAssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND name='IX_StallAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_StallAssignment_LegID] ON dbo.[StallAssignment] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND name='IX_StallAssignment_StallID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_StallAssignment_StallID] ON dbo.[StallAssignment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallChoice') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] ADD CONSTRAINT [PK_StallChoice] PRIMARY KEY ([StallChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallChoice') AND name='UQ_StallChoice_ChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] ADD CONSTRAINT [UQ_StallChoice_ChoiceID] UNIQUE ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallEquipment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD CONSTRAINT [PK_StallEquipment] PRIMARY KEY ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallEquipment') AND name='UQ_StallEquipment_StallCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD CONSTRAINT [UQ_StallEquipment_StallCode] UNIQUE ([StallCode])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallFitPolicy') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] ADD CONSTRAINT [PK_StallFitPolicy] PRIMARY KEY ([PolicyID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='UQ_StallFitPolicy_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] ADD CONSTRAINT [UQ_StallFitPolicy_Version] UNIQUE ([PolicyCode],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Supplier') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] ADD CONSTRAINT [PK_Supplier] PRIMARY KEY ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.SupplierOffer') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD CONSTRAINT [PK_SupplierOffer] PRIMARY KEY ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrder') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD CONSTRAINT [PK_TransportOrder] PRIMARY KEY ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrder') AND name='UQ_TransportOrder_OrderNumber')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD CONSTRAINT [UQ_TransportOrder_OrderNumber] UNIQUE ([OrderNumber])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrderDetail') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] ADD CONSTRAINT [PK_TransportOrderDetail] PRIMARY KEY ([DetailID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='UQ_TransportOrderDetail_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] ADD CONSTRAINT [UQ_TransportOrderDetail_OrderID] UNIQUE ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [PK_TransportPlan] PRIMARY KEY ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='UQ_TransportPlan_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [UQ_TransportPlan_Version] UNIQUE ([RevisionID],[VersionNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='IX_TransportPlan_CreatedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TransportPlan_CreatedBy] ON dbo.[TransportPlan] ([CreatedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='IX_TransportPlan_DepartureAuthorizedBy')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TransportPlan_DepartureAuthorizedBy] ON dbo.[TransportPlan] ([DepartureAuthorizedBy])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND name='UQ_Figma_TransportPlan_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD CONSTRAINT [UQ_Figma_TransportPlan_Parent] UNIQUE ([PlanID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD CONSTRAINT [PK_TripAssignment] PRIMARY KEY ([AssignmentID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND name='IX_TripAssignment_LegID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripAssignment_LegID] ON dbo.[TripAssignment] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND name='IX_TripAssignment_StaffID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripAssignment_StaffID] ON dbo.[TripAssignment] ([StaffID])';
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
    object_id=OBJECT_ID('dbo.TripLeg') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [PK_TripLeg] PRIMARY KEY ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='UQ_TripLeg_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [UQ_TripLeg_Sequence] UNIQUE ([PlanID],[SequenceNo])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='IX_TripLeg_VehicleID')
    EXEC sys.sp_executesql N'CREATE INDEX [IX_TripLeg_VehicleID] ON dbo.[TripLeg] ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND name='UQ_Figma_TripLeg_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD CONSTRAINT [UQ_Figma_TripLeg_Parent] UNIQUE ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.User') AND is_primary_key=1)
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] ADD CONSTRAINT [PK_User] PRIMARY KEY ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.User') AND name='UQ_User_Email')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] ADD CONSTRAINT [UQ_User_Email] UNIQUE ([Email])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='CK_ActualCost_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [CK_ActualCost_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_ActualCost_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_ActualCost_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_LegID] FOREIGN KEY ([LegID],[PlanID]) REFERENCES dbo.[TripLeg] ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ActualCost') AND name='FK_Figma_ActualCost_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT [FK_Figma_ActualCost_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuditEvent') AND name='CK_AuditEvent_ChangeSnapshot_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] WITH CHECK ADD CONSTRAINT [CK_AuditEvent_ChangeSnapshot_JSON] CHECK ([ChangeSnapshot] IS NULL OR isjson([ChangeSnapshot])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuditEvent') AND name='FK_AuditEvent_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuditEvent] WITH CHECK ADD CONSTRAINT [FK_AuditEvent_UserID] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='CK_AuthenticationToken_Expiry')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [CK_AuthenticationToken_Expiry] CHECK ([ExpiresAt]>[CreatedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='CK_AuthenticationToken_Purpose')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [CK_AuthenticationToken_Purpose] CHECK ([Purpose]=''RESET_PASSWORD'' OR [Purpose]=''VERIFY_EMAIL'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthenticationToken') AND name='FK_AuthenticationToken_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthenticationToken] WITH CHECK ADD CONSTRAINT [FK_AuthenticationToken_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='FK_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT [FK_AuthorityProcedure_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.AuthorityProcedure') AND name='FK_Figma_AuthorityProcedure_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT [FK_Figma_AuthorityProcedure_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='FK_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] WITH CHECK ADD CONSTRAINT [FK_ComplianceClearance_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceClearance') AND name='FK_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] WITH CHECK ADD CONSTRAINT [FK_ComplianceClearance_SpecialistID] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='CK_ComplianceRuleVersion_EffectiveTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [CK_ComplianceRuleVersion_EffectiveTo_Range] CHECK ([EffectiveTo] IS NULL OR [EffectiveFrom] IS NOT NULL AND [EffectiveTo]>=[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='CK_ComplianceRuleVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [CK_ComplianceRuleVersion_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.ComplianceRuleVersion') AND name='FK_Figma_ComplianceRuleVersion_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT [FK_Figma_ComplianceRuleVersion_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Customer') AND name='FK_Customer_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] WITH CHECK ADD CONSTRAINT [FK_Customer_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='CK_Figma_DocumentRequirement_Scope')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentRequirement_Scope] CHECK ([ScopeCode] IS NULL OR [ScopeCode]=''HORSE'' AND [HorseID] IS NOT NULL OR [ScopeCode]=''SHIPMENT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='CK_Figma_DocumentRequirement_ValidityConditionsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentRequirement_ValidityConditionsJSON] CHECK ([ValidityConditionsJSON] IS NULL OR isjson([ValidityConditionsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_DocumentRequirement_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_DocumentRequirement_RuleID] FOREIGN KEY ([RuleID]) REFERENCES dbo.[ComplianceRuleVersion] ([RuleID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_Figma_DocumentRequirement_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentRequirement_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentRequirement') AND name='FK_Figma_DocumentRequirement_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentRequirement_HorseID] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentReview') AND name='FK_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] WITH CHECK ADD CONSTRAINT [FK_DocumentReview_ReviewedBy] FOREIGN KEY ([ReviewedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentReview') AND name='FK_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentReview] WITH CHECK ADD CONSTRAINT [FK_DocumentReview_SubmissionID] FOREIGN KEY ([SubmissionID]) REFERENCES dbo.[DocumentSubmission] ([SubmissionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='FK_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] WITH CHECK ADD CONSTRAINT [FK_DocumentSubmission_DocumentVersionID] FOREIGN KEY ([DocumentVersionID]) REFERENCES dbo.[DocumentVersion] ([DocumentVersionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentSubmission') AND name='FK_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentSubmission] WITH CHECK ADD CONSTRAINT [FK_DocumentSubmission_RequirementID] FOREIGN KEY ([RequirementID]) REFERENCES dbo.[DocumentRequirement] ([RequirementID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentTypeCatalog') AND name='CK_DocumentTypeCatalog_Code')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentTypeCatalog] WITH CHECK ADD CONSTRAINT [CK_DocumentTypeCatalog_Code] CHECK (len(ltrim(rtrim([TypeCode])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_DocumentVersion_ExpiryDate_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_DocumentVersion_ExpiryDate_Range] CHECK ([ExpiryDate] IS NULL OR [IssuedDate] IS NOT NULL AND [ExpiryDate]>=[IssuedDate])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_DocumentVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_DocumentVersion_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='CK_Figma_DocumentVersion_FileSizeBytes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [CK_Figma_DocumentVersion_FileSizeBytes] CHECK ([FileSizeBytes]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='FK_DocumentVersion_DocumentID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [FK_DocumentVersion_DocumentID] FOREIGN KEY ([DocumentID]) REFERENCES dbo.[HorseDocument] ([DocumentID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.DocumentVersion') AND name='FK_Figma_DocumentVersion_UploadedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT [FK_Figma_DocumentVersion_UploadedBy] FOREIGN KEY ([UploadedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='CK_Figma_FleetVehicle_MaintenanceDates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates] CHECK ([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='CK_FleetVehicle_HorseCapacity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [CK_FleetVehicle_HorseCapacity] CHECK ([HorseCapacity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FleetVehicle') AND name='FK_Figma_FleetVehicle_OwnerSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID] FOREIGN KEY ([OwnerSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='CK_Figma_FlightReservation_UsableCapacitySlots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots] CHECK ([UsableCapacitySlots]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_Figma_FlightReservation_ConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_FlightReservation_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.FlightReservation') AND name='FK_FlightReservation_OfferID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT [FK_FlightReservation_OfferID] FOREIGN KEY ([OfferID]) REFERENCES dbo.[SupplierOffer] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_BodyWidthCm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_BodyWidthCm] CHECK ([BodyWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_HeightCm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_HeightCm] CHECK ([HeightCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_SexCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_SexCode] CHECK ([SexCode]=''GELDING'' OR [SexCode]=''MARE'' OR [SexCode]=''STALLION'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='CK_Horse_WeightKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [CK_Horse_WeightKg] CHECK ([WeightKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Horse') AND name='FK_Horse_Customer')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] WITH CHECK ADD CONSTRAINT [FK_Horse_Customer] FOREIGN KEY ([CustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseDocument') AND name='FK_Figma_HorseDocument_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseDocument_DocumentType] FOREIGN KEY ([DocumentType]) REFERENCES dbo.[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseDocument') AND name='FK_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT [FK_HorseDocument_HorseID] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='CK_Figma_HorseReceipt_Identity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseReceipt_Identity] CHECK ([IdentityCheckResult]=''MISMATCHED'' OR [IdentityCheckResult]=''MATCHED'' OR [IdentityCheckResult]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_Figma_HorseReceipt_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseReceipt_LegID] FOREIGN KEY ([LegID],[PlanID]) REFERENCES dbo.[TripLeg] ([LegID],[PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_HorseReceipt_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_HorseReceipt_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseReceipt') AND name='FK_HorseReceipt_ReceivedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT [FK_HorseReceipt_ReceivedBy] FOREIGN KEY ([ReceivedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_FeedAmountKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg] CHECK ([FeedAmountKg]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_Figma_HorseWelfareLog_Supersedes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes] CHECK ([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='CK_HorseWelfareLog_WaterIntakeLiters')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters] CHECK ([WaterIntakeLiters]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_Figma_HorseWelfareLog_SupersedesLogID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID] FOREIGN KEY ([SupersedesLogID],[LegID]) REFERENCES dbo.[HorseWelfareLog] ([WelfareLogID],[LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.HorseWelfareLog') AND name='FK_HorseWelfareLog_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT [FK_HorseWelfareLog_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='CK_Figma_IncidentReport_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_AcknowledgedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy] FOREIGN KEY ([AcknowledgedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_Figma_IncidentReport_ResolvedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy] FOREIGN KEY ([ResolvedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.IncidentReport') AND name='FK_IncidentReport_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT [FK_IncidentReport_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_CoverageCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_CoverageCode] CHECK ([CoverageCode]=''MORTALITY_INJURY'' OR [CoverageCode]=''MORTALITY'' OR [CoverageCode]=''EXISTING_POLICY'' OR [CoverageCode]=''NONE'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_DeclaredValue')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_DeclaredValue] CHECK ([DeclaredValue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_ExistingPolicy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_ExistingPolicy] CHECK ([CoverageCode]<>''EXISTING_POLICY'' OR [ExistingPolicyReference] IS NOT NULL AND len(ltrim(rtrim([ExistingPolicyReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='CK_InsuranceChoice_ValueCurrency')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [CK_InsuranceChoice_ValueCurrency] CHECK ([DeclaredValue] IS NULL AND [ValueCurrency] IS NULL OR [DeclaredValue] IS NOT NULL AND [ValueCurrency] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsuranceChoice') AND name='FK_InsuranceChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsuranceChoice] WITH CHECK ADD CONSTRAINT [FK_InsuranceChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='CK_InsurancePolicy_CoverageTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range] CHECK ([CoverageTo] IS NULL OR [CoverageFrom] IS NOT NULL AND [CoverageTo]>=[CoverageFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='FK_Figma_InsurancePolicy_VerifiedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy] FOREIGN KEY ([VerifiedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.InsurancePolicy') AND name='FK_InsurancePolicy_InsuranceChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID] FOREIGN KEY ([InsuranceChoiceID]) REFERENCES dbo.[InsuranceChoice] ([InsuranceChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Latitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Latitude] CHECK ([Latitude]>=(-90) AND [Latitude]<=(90))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='CK_LegMilestoneLog_Longitude')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [CK_LegMilestoneLog_Longitude] CHECK ([Longitude]>=(-180) AND [Longitude]<=(180))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.LegMilestoneLog') AND name='FK_LegMilestoneLog_ReportedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] WITH CHECK ADD CONSTRAINT [FK_LegMilestoneLog_ReportedBy] FOREIGN KEY ([ReportedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Notification') AND name='FK_Notification_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] WITH CHECK ADD CONSTRAINT [FK_Notification_OrderID] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Notification') AND name='FK_Notification_UserID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Notification] WITH CHECK ADD CONSTRAINT [FK_Notification_UserID] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_Figma_OperationalAdjustment_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision] CHECK ([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_AdditionalCostEstimate')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate] CHECK ([AdditionalCostEstimate]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='CK_OperationalAdjustment_ProposedChanges_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON] CHECK (isjson([ProposedChanges])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_ChangeRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID] FOREIGN KEY ([ChangeRequestID]) REFERENCES dbo.[OrderChangeRequest] ([ChangeRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_Figma_OperationalAdjustment_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_IncidentID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_IncidentID] FOREIGN KEY ([IncidentID]) REFERENCES dbo.[IncidentReport] ([IncidentID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OperationalAdjustment') AND name='FK_OperationalAdjustment_ProposedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT [FK_OperationalAdjustment_ProposedBy] FOREIGN KEY ([ProposedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='CK_OrderAssignment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [CK_OrderAssignment_Dates] CHECK ([EndedAt] IS NULL OR [EndedAt]>=[AssignedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='CK_OrderAssignment_DueAt')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [CK_OrderAssignment_DueAt] CHECK ([DueAt] IS NULL OR [DueAt]>=[AssignedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_AssignedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_AssignedBy] FOREIGN KEY ([AssignedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderAssignment') AND name='FK_OrderAssignment_Staff')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderAssignment] WITH CHECK ADD CONSTRAINT [FK_OrderAssignment_Staff] FOREIGN KEY ([StaffID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_JSON] CHECK (isjson([RequestedChangesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='CK_OrderChangeRequest_Reason')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [CK_OrderChangeRequest_Reason] CHECK (len(ltrim(rtrim([Reason])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_BaseRevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID] FOREIGN KEY ([BaseRevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_OrderID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RequestedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy] FOREIGN KEY ([RequestedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderChangeRequest') AND name='FK_Figma_OrderChangeRequest_RespondedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy] FOREIGN KEY ([RespondedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='CK_OrderDecision_Decision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [CK_OrderDecision_Decision] CHECK ([Decision]=''CHANGES_REQUESTED'' OR [Decision]=''REJECTED'' OR [Decision]=''APPROVED'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='FK_OrderDecision_Manager')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [FK_OrderDecision_Manager] FOREIGN KEY ([ManagerID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderDecision') AND name='FK_OrderDecision_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderDecision] WITH CHECK ADD CONSTRAINT [FK_OrderDecision_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='CK_OrderRevision_OrderSnapshot')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [CK_OrderRevision_OrderSnapshot] CHECK (isjson([OrderSnapshot])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='CK_OrderRevision_RevisionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [CK_OrderRevision_RevisionNo] CHECK ([RevisionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderRevision') AND name='FK_OrderRevision_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderRevision] WITH CHECK ADD CONSTRAINT [FK_OrderRevision_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='CK_Figma_OrderServiceChoice_SelectionInputsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [CK_Figma_OrderServiceChoice_SelectionInputsJSON] CHECK ([SelectionInputsJSON] IS NULL OR isjson([SelectionInputsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='CK_OrderServiceChoice_ArrangedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [CK_OrderServiceChoice_ArrangedBy] CHECK ([ArrangedBy]=''CUSTOMER'' OR [ArrangedBy]=''EQUITRANSIT'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.OrderServiceChoice') AND name='FK_OrderServiceChoice_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT [FK_OrderServiceChoice_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='CK_PaymentAllocation_AllocatedAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [CK_PaymentAllocation_AllocatedAmount] CHECK ([AllocatedAmount]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='FK_PaymentAllocation_PaymentRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [FK_PaymentAllocation_PaymentRequestID] FOREIGN KEY ([PaymentRequestID]) REFERENCES dbo.[PaymentRequest] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentAllocation') AND name='FK_PaymentAllocation_TransactionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentAllocation] WITH CHECK ADD CONSTRAINT [FK_PaymentAllocation_TransactionID] FOREIGN KEY ([TransactionID]) REFERENCES dbo.[PaymentTransaction] ([TransactionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentReport') AND name='CK_PaymentReport_ReportedAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] WITH CHECK ADD CONSTRAINT [CK_PaymentReport_ReportedAmount] CHECK ([ReportedAmount]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentReport') AND name='FK_PaymentReport_PaymentRequestID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] WITH CHECK ADD CONSTRAINT [FK_PaymentReport_PaymentRequestID] FOREIGN KEY ([PaymentRequestID]) REFERENCES dbo.[PaymentRequest] ([PaymentRequestID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='CK_PaymentRequest_AmountDue')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [CK_PaymentRequest_AmountDue] CHECK ([AmountDue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='FK_Figma_PaymentRequest_ScheduleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [FK_Figma_PaymentRequest_ScheduleID] FOREIGN KEY ([ScheduleID],[QuoteID]) REFERENCES dbo.[QuotePaymentSchedule] ([ScheduleID],[QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentRequest') AND name='FK_PaymentRequest_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT [FK_PaymentRequest_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='CK_PaymentTransaction_AmountReceived')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] WITH CHECK ADD CONSTRAINT [CK_PaymentTransaction_AmountReceived] CHECK ([AmountReceived]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.PaymentTransaction') AND name='FK_PaymentTransaction_ReconciledBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] WITH CHECK ADD CONSTRAINT [FK_PaymentTransaction_ReconciledBy] FOREIGN KEY ([ReconciledBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='CK_QuarantineChoice_Confirmation')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [CK_QuarantineChoice_Confirmation] CHECK ([ConfirmedBy] IS NULL AND [DaysConfirmedAt] IS NULL OR [ConfirmedBy] IS NOT NULL AND [DaysConfirmedAt] IS NOT NULL AND [RequiredDays] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='CK_QuarantineChoice_RequiredDays')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [CK_QuarantineChoice_RequiredDays] CHECK ([RequiredDays]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='FK_QuarantineChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [FK_QuarantineChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineChoice') AND name='FK_QuarantineChoice_Staff')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineChoice] WITH CHECK ADD CONSTRAINT [FK_QuarantineChoice_Staff] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='CK_QuarantineReservation_EndsAt_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [CK_QuarantineReservation_EndsAt_Range] CHECK ([EndsAt] IS NULL OR [StartsAt] IS NOT NULL AND [EndsAt]>=[StartsAt])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_Figma_QuarantineReservation_ConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy] FOREIGN KEY ([ConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_Figma_QuarantineReservation_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuarantineReservation') AND name='FK_QuarantineReservation_QuarantineChoiceID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID] FOREIGN KEY ([QuarantineChoiceID]) REFERENCES dbo.[QuarantineChoice] ([QuarantineChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_AcceptedAt')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_AcceptedAt] CHECK ([AcceptedAt] IS NULL OR [IssuedAt] IS NOT NULL AND [AcceptedAt]>=[IssuedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Approval')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Approval] CHECK ([ApprovedBy] IS NULL AND [ApprovedAt] IS NULL OR [ApprovedBy] IS NOT NULL AND [ApprovedAt] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Status] CHECK ([QuoteStatus]=''SUPERSEDED'' OR [QuoteStatus]=''EXPIRED'' OR [QuoteStatus]=''REJECTED'' OR [QuoteStatus]=''ACCEPTED'' OR [QuoteStatus]=''ISSUED'' OR [QuoteStatus]=''APPROVED'' OR [QuoteStatus]=''DRAFT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_TotalAmount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_TotalAmount] CHECK ([TotalQuotedAmount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_Validity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_Validity] CHECK ([ValidUntil] IS NULL OR [IssuedAt] IS NOT NULL AND [ValidUntil]>[IssuedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='CK_Quote_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [CK_Quote_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_AcceptedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_AcceptedByCustomerID] FOREIGN KEY ([AcceptedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_IssuedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_IssuedBy] FOREIGN KEY ([IssuedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Figma_Quote_PreparedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Figma_Quote_PreparedBy] FOREIGN KEY ([PreparedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Quote_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Quote_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Quote') AND name='FK_Quote_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT [FK_Quote_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_CalculationInputs_JSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_CalculationInputs_JSON] CHECK ([CalculationInputs] IS NULL OR isjson([CalculationInputs])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_Quantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_Quantity] CHECK ([Quantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='CK_QuoteItem_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [CK_QuoteItem_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_OfferID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_OfferID] FOREIGN KEY ([OfferID]) REFERENCES dbo.[SupplierOffer] ([OfferID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuoteItem') AND name='FK_QuoteItem_TariffID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuoteItem] WITH CHECK ADD CONSTRAINT [FK_QuoteItem_TariffID] FOREIGN KEY ([TariffID]) REFERENCES dbo.[ServiceTariff] ([TariffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='CK_QuotePaymentSchedule_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [CK_QuotePaymentSchedule_Amount] CHECK ([AmountDue]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='CK_QuotePaymentSchedule_Sequence')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [CK_QuotePaymentSchedule_Sequence] CHECK ([SequenceNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.QuotePaymentSchedule') AND name='FK_Figma_QuotePaymentSchedule_QuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT [FK_Figma_QuotePaymentSchedule_QuoteID] FOREIGN KEY ([QuoteID]) REFERENCES dbo.[Quote] ([QuoteID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='CK_Refund_Amount')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [CK_Refund_Amount] CHECK ([Amount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Figma_Refund_ApprovedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Figma_Refund_ApprovedBy] FOREIGN KEY ([ApprovedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Figma_Refund_RequestedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Figma_Refund_RequestedBy] FOREIGN KEY ([RequestedBy]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.Refund') AND name='FK_Refund_AllocationID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT [FK_Refund_AllocationID] FOREIGN KEY ([AllocationID]) REFERENCES dbo.[PaymentAllocation] ([AllocationID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Dates] CHECK ([ValidUntil]>[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_History')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_History] CHECK ([SupersededAt] IS NULL OR [SupersededAt]>=[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Outcome')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Outcome] CHECK ([Applicability]=''REQUIRED'' AND [RequiredDays] IS NOT NULL AND [RequiredDays]>(0) OR [Applicability]=''NOT_REQUIRED'' AND [RequiredDays] IS NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='CK_RevisionQuarantineAssessment_Source')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionQuarantineAssessment_Source] CHECK (len(ltrim(rtrim([SourceReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='FK__RevisionQ__Revis__473C8FC7')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionQ__Revis__473C8FC7] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionQuarantineAssessment') AND name='FK__RevisionQ__Speci__4830B400')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionQuarantineAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionQ__Speci__4830B400] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Counts')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Counts] CHECK ([VisitCount]>=(0) AND [TestCount]>=(0) AND [DoseCount]>=(0) AND [CertificateCount]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Dates] CHECK ([ValidUntil]>[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_History')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_History] CHECK ([SupersededAt] IS NULL OR [SupersededAt]>=[AssessedAt])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='CK_RevisionVeterinaryAssessment_Source')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [CK_RevisionVeterinaryAssessment_Source] CHECK (len(ltrim(rtrim([SourceReference])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='FK__RevisionV__Revis__4EDDB18F')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionV__Revis__4EDDB18F] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.RevisionVeterinaryAssessment') AND name='FK__RevisionV__Speci__4FD1D5C8')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[RevisionVeterinaryAssessment] WITH CHECK ADD CONSTRAINT [FK__RevisionV__Speci__4FD1D5C8] FOREIGN KEY ([SpecialistID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_Dates] CHECK ([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_Status] CHECK ([Status]=''INACTIVE'' OR [Status]=''ACTIVE'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.ServiceTariff') AND name='CK_ServiceTariff_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ServiceTariff] WITH CHECK ADD CONSTRAINT [CK_ServiceTariff_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StaffProfile') AND name='FK_StaffProfile_User')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StaffProfile] WITH CHECK ADD CONSTRAINT [FK_StaffProfile_User] FOREIGN KEY ([UserID]) REFERENCES dbo.[User] ([UserID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='CK_StallAssignment_AssignedTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_AssignedTo_Range] CHECK ([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='CK_StallAssignment_SlotQuantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [CK_StallAssignment_SlotQuantity] CHECK ([SlotQuantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='FK_StallAssignment_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallAssignment') AND name='FK_StallAssignment_StallID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] WITH CHECK ADD CONSTRAINT [FK_StallAssignment_StallID] FOREIGN KEY ([StallID]) REFERENCES dbo.[StallEquipment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='CK_StallChoice_Configuration')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [CK_StallChoice_Configuration] CHECK ([SharingCode]=''SHARED'' AND ([SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)) OR [SharingCode]=''PRIVATE'' AND [SlotQuantity]=(3.0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='CK_StallChoice_FitStatus')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [CK_StallChoice_FitStatus] CHECK ([FitStatus]=''UNSUITABLE'' OR [FitStatus]=''SUITABLE'' OR [FitStatus]=''PENDING'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallChoice') AND name='FK_StallChoice_ServiceChoice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallChoice] WITH CHECK ADD CONSTRAINT [FK_StallChoice_ServiceChoice] FOREIGN KEY ([ChoiceID]) REFERENCES dbo.[OrderServiceChoice] ([ChoiceID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_Figma_StallEquipment_MaintenanceDates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates] CHECK ([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_Figma_StallEquipment_WeightLimitKg')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg] CHECK ([WeightLimitKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='CK_StallEquipment_CapacitySlots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [CK_StallEquipment_CapacitySlots] CHECK ([CapacitySlots]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallEquipment') AND name='FK_Figma_StallEquipment_OwnerSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID] FOREIGN KEY ([OwnerSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Approval')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Approval] CHECK ([Status]<>''APPROVED'' OR [ReviewedBy] IS NOT NULL AND [ReviewedAt] IS NOT NULL AND [MaxWeightKg] IS NOT NULL AND [MaxHeightCm] IS NOT NULL AND [MaxWidthCm] IS NOT NULL AND ([SourceURL] IS NOT NULL OR [SourceFileKey] IS NOT NULL))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Dates')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Dates] CHECK ([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Dimensions')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Dimensions] CHECK ([MaxWeightKg]>(0) AND [MaxHeightCm]>(0) AND [MaxWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Slots')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Slots] CHECK ([SlotQuantity]=(3.0) OR [SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Status] CHECK ([Status]=''INACTIVE'' OR [Status]=''APPROVED'' OR [Status]=''DRAFT'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='CK_StallFitPolicy_Version')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [CK_StallFitPolicy_Version] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_CarrierSupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID] FOREIGN KEY ([CarrierSupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_ReviewedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy] FOREIGN KEY ([ReviewedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.StallFitPolicy') AND name='FK_Figma_StallFitPolicy_StallID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT [FK_Figma_StallFitPolicy_StallID] FOREIGN KEY ([StallID]) REFERENCES dbo.[StallEquipment] ([StallID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Supplier') AND name='CK_Supplier_Name')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK ADD CONSTRAINT [CK_Supplier_Name] CHECK (len(ltrim(rtrim([Name])))>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.Supplier') AND name='CK_Supplier_ServicesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Supplier] WITH CHECK ADD CONSTRAINT [CK_Supplier_ServicesJSON] CHECK ([ServicesOfferedJSON] IS NULL OR isjson([ServicesOfferedJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_IncludedCharges')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_IncludedCharges] CHECK ([IncludedCharges] IS NULL OR isjson([IncludedCharges])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_Quantity')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_Quantity] CHECK ([Quantity]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_Status] CHECK ([Status]=''EXPIRED'' OR [Status]=''REJECTED'' OR [Status]=''SELECTED'' OR [Status]=''RECEIVED'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='CK_SupplierOffer_UnitPrice')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [CK_SupplierOffer_UnitPrice] CHECK ([UnitPrice]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='FK_Figma_SupplierOffer_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [FK_Figma_SupplierOffer_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.SupplierOffer') AND name='FK_SupplierOffer_Revision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT [FK_SupplierOffer_Revision] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_DraftPayloadJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_DraftPayloadJSON] CHECK ([DraftPayloadJSON] IS NULL OR isjson([DraftPayloadJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_ImportType')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_ImportType] CHECK ([ImportType]=''NEEDS_ADVICE'' OR [ImportType]=''PERMANENT'' OR [ImportType]=''TEMPORARY'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='CK_Figma_TransportOrder_TransitAirportsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [CK_Figma_TransportOrder_TransitAirportsJSON] CHECK ([TransitAirportsJSON] IS NULL OR isjson([TransitAirportsJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_Figma_TransportOrder_CurrentPlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID] FOREIGN KEY ([CurrentPlanID],[OrderID]) REFERENCES dbo.[TransportPlan] ([PlanID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_Figma_TransportOrder_CurrentRevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportOrder_CurrentRevisionID] FOREIGN KEY ([CurrentRevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrder') AND name='FK_TransportOrder_Customer')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT [FK_TransportOrder_Customer] FOREIGN KEY ([CustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Height')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Height] CHECK ([SnapshotHeightCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_SexCode')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_SexCode] CHECK ([SnapshotSexCode]=''GELDING'' OR [SnapshotSexCode]=''MARE'' OR [SnapshotSexCode]=''STALLION'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Weight')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Weight] CHECK ([SnapshotWeightKg]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='CK_TransportOrderDetail_Width')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [CK_TransportOrderDetail_Width] CHECK ([SnapshotBodyWidthCm]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='FK_TransportOrderDetail_Horse')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [FK_TransportOrderDetail_Horse] FOREIGN KEY ([HorseID]) REFERENCES dbo.[Horse] ([HorseID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportOrderDetail') AND name='FK_TransportOrderDetail_Order')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrderDetail] WITH CHECK ADD CONSTRAINT [FK_TransportOrderDetail_Order] FOREIGN KEY ([OrderID]) REFERENCES dbo.[TransportOrder] ([OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='CK_TransportPlan_PlannedEndTime_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range] CHECK ([PlannedEndTime] IS NULL OR [PlannedStartTime] IS NOT NULL AND [PlannedEndTime]>=[PlannedStartTime])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='CK_TransportPlan_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [CK_TransportPlan_VersionNo] CHECK ([VersionNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_Figma_TransportPlan_AcceptedQuoteID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID] FOREIGN KEY ([AcceptedQuoteID],[RevisionID]) REFERENCES dbo.[Quote] ([QuoteID],[RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_Figma_TransportPlan_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_Figma_TransportPlan_RevisionID] FOREIGN KEY ([RevisionID],[OrderID]) REFERENCES dbo.[OrderRevision] ([RevisionID],[OrderID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_CreatedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_CreatedBy] FOREIGN KEY ([CreatedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_DepartureAuthorizedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy] FOREIGN KEY ([DepartureAuthorizedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TransportPlan') AND name='FK_TransportPlan_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT [FK_TransportPlan_RevisionID] FOREIGN KEY ([RevisionID]) REFERENCES dbo.[OrderRevision] ([RevisionID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='CK_Figma_TripAssignment_BufferMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes] CHECK ([BufferMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='CK_TripAssignment_AssignedTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [CK_TripAssignment_AssignedTo_Range] CHECK ([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='FK_TripAssignment_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripAssignment') AND name='FK_TripAssignment_StaffID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT [FK_TripAssignment_StaffID] FOREIGN KEY ([StaffID]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_CustomerDecision')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_CustomerDecision] CHECK ([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED'')';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='CK_Figma_TripHandover_EvidenceFilesJSON')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON] CHECK ([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_Figma_TripHandover_RespondedByCustomerID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID] FOREIGN KEY ([RespondedByCustomerID]) REFERENCES dbo.[Customer] ([CustomerID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_LegID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_LegID] FOREIGN KEY ([LegID]) REFERENCES dbo.[TripLeg] ([LegID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripHandover') AND name='FK_TripHandover_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT [FK_TripHandover_RecordedBy] FOREIGN KEY ([RecordedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_Figma_TripLeg_ConnectionBufferMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes] CHECK ([ConnectionBufferMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_Figma_TripLeg_RestDurationMinutes')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes] CHECK ([RestDurationMinutes]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_ActualArrival_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_ActualArrival_Range] CHECK ([ActualArrival] IS NULL OR [ActualDeparture] IS NOT NULL AND [ActualArrival]>=[ActualDeparture])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_DistanceKm')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_DistanceKm] CHECK ([DistanceKm]>=(0))';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_DistanceSource')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_DistanceSource] CHECK ([DistanceSource] IS NULL AND [DistanceConfirmedBy] IS NULL AND [DistanceConfirmedAt] IS NULL OR [DistanceSource] IS NOT NULL AND len(ltrim(rtrim([DistanceSource])))>(0) AND [DistanceConfirmedBy] IS NOT NULL AND [DistanceConfirmedAt] IS NOT NULL)';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_PlannedArrival_Range')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_PlannedArrival_Range] CHECK ([PlannedArrival] IS NULL OR [PlannedDeparture] IS NOT NULL AND [PlannedArrival]>=[PlannedDeparture])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='CK_TripLeg_SequenceNo')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [CK_TripLeg_SequenceNo] CHECK ([SequenceNo]>(0))';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_Figma_TripLeg_SupplierID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_Figma_TripLeg_SupplierID] FOREIGN KEY ([SupplierID]) REFERENCES dbo.[Supplier] ([SupplierID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_DistanceConfirmedBy')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_DistanceConfirmedBy] FOREIGN KEY ([DistanceConfirmedBy]) REFERENCES dbo.[StaffProfile] ([StaffID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_PlanID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_PlanID] FOREIGN KEY ([PlanID]) REFERENCES dbo.[TransportPlan] ([PlanID])';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.TripLeg') AND name='FK_TripLeg_VehicleID')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT [FK_TripLeg_VehicleID] FOREIGN KEY ([VehicleID]) REFERENCES dbo.[FleetVehicle] ([VehicleID])';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE
    parent_object_id=OBJECT_ID('dbo.User') AND name='CK_User_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] WITH CHECK ADD CONSTRAINT [CK_User_Status] CHECK ([Status]=''LOCKED'' OR [Status]=''INACTIVE'' OR [Status]=''ACTIVE'')';
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE
    parent_object_id=OBJECT_ID('dbo.User') AND name='FK_User_Role')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] WITH CHECK ADD CONSTRAINT [FK_User_Role] FOREIGN KEY ([RoleID]) REFERENCES dbo.[Role] ([RoleID])';

-- Required reference roles, not accounts or demo price/operating evidence.
INSERT dbo.Role(RoleCode)
SELECT v.Code FROM (VALUES ('CUSTOMER'),('MANAGER'),('SPECIALIST'),
    ('COORDINATOR'),('DRIVER')) v(Code)
WHERE NOT EXISTS(SELECT 1 FROM dbo.Role r WHERE r.RoleCode=v.Code);
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS reapplication: 000_figma_schema.sql';
GO
PRINT N'RUN reapplication: 001_authentication_token.sql';
GO
-- Add only the persistence required by Figma A03 verification/recovery.
-- Existing User, Role and Customer tables and rows remain unchanged.
IF OBJECT_ID(N'dbo.AuthenticationToken', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AuthenticationToken (
        TokenID BIGINT IDENTITY(1,1) NOT NULL,
        UserID BIGINT NOT NULL,
        TokenHash VARCHAR(64) NOT NULL,
        Purpose VARCHAR(20) NOT NULL,
        ExpiresAt DATETIME2(0) NOT NULL,
        UsedAt DATETIME2(0) NULL,
        CreatedAt DATETIME2(0) NOT NULL,
        CONSTRAINT PK_AuthenticationToken PRIMARY KEY (TokenID),
        CONSTRAINT UQ_AuthenticationToken_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_AuthenticationToken_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User] (UserID),
        CONSTRAINT CK_AuthenticationToken_Purpose CHECK (
            Purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')
        ),
        CONSTRAINT CK_AuthenticationToken_Expiry
            CHECK (ExpiresAt > CreatedAt)
    );
    CREATE INDEX IX_AuthenticationToken_UserPurpose
        ON dbo.AuthenticationToken (UserID, Purpose);
END;

GO
PRINT N'PASS reapplication: 001_authentication_token.sql';
GO
PRINT N'RUN reapplication: 002_align_figma_without_organization.sql';
GO
-- 002: Align persisted business fields with SWP Figma, excluding customer organizations.
-- Sources: export 2026-10-10 plus live page 0:1 metadata checked 2026-10-13.
-- Additive, transactional and rerunnable. Preserve original columns and rows.
-- Back up and validate on a restored test copy before applying EquiTransit.
-- No production tariff, insurance price or carrier fit limit is seeded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'EquiTransit' AND DB_NAME() NOT LIKE N'EquiTransit[_]FigmaMigrationTest[_]%'
    THROW 51000, 'Use EquiTransit or a dedicated FigmaMigrationTest copy.', 1;
IF OBJECT_ID(N'dbo.TransportOrderDetail', N'U') IS NULL
    THROW 51000, 'The full existing EquiTransit schema is required.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @lockResult INT;
EXEC @lockResult = sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
IF @lockResult < 0 THROW 51000, 'Another schema migration holds the lock.', 1;

-- Customer: C17
IF COL_LENGTH(N'dbo.Customer', N'EmailNotificationsEnabled') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Customer] ADD [EmailNotificationsEnabled] BIT NULL;';

-- Horse: C03/C07
IF COL_LENGTH(N'dbo.Horse', N'HandlingNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [HandlingNotes] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'FeedWaterRequirements') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [FeedWaterRequirements] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'StallPreferences') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [StallPreferences] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'VeterinaryInstructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [VeterinaryInstructions] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'VeterinaryInstructionSource') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [VeterinaryInstructionSource] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.Horse', N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Horse] ADD [RecordVersion] ROWVERSION;';

-- TransportOrder: C05/C06/C07/C08/C10/C16
IF COL_LENGTH(N'dbo.TransportOrder', N'PreferredDepartureAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [PreferredDepartureAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'DateFlexibilityNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [DateFlexibilityNotes] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverName] NVARCHAR(150) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverPhone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverPhone] VARCHAR(30) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverEmail') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverEmail] VARCHAR(255) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ReceiverAuthority') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ReceiverAuthority] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'ImportType') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [ImportType] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventName] NVARCHAR(200) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventVenue') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventVenue] NVARCHAR(300) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'EventTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [EventTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'TransitAirportsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [TransitAirportsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'DraftPayloadJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [DraftPayloadJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'UpdatedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [UpdatedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'CurrentRevisionID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [CurrentRevisionID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'CurrentPlanID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [CurrentPlanID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportOrder', N'RecordVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] ADD [RecordVersion] ROWVERSION;';

-- OrderServiceChoice: C08/BOOK-01..08
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'SelectionInputsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [SelectionInputsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'ProviderName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [ProviderName] NVARCHAR(200) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'ProviderContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [ProviderContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.OrderServiceChoice', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- Quote: F02/M04/C13/M09
IF COL_LENGTH(N'dbo.Quote', N'PreparedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [PreparedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'SubmittedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [SubmittedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'IssuedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [IssuedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'AcceptedByCustomerID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [AcceptedByCustomerID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsVersion') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsVersion] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsText') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsText] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'CancellationTerms') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [CancellationTerms] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'TermsFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [TermsFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.Quote', N'ReviewFeedback') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] ADD [ReviewFeedback] NVARCHAR(2000) NULL;';

-- PaymentRequest: M04/C14/M07
IF COL_LENGTH(N'dbo.PaymentRequest', N'ScheduleID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] ADD [ScheduleID] BIGINT NULL;';

-- PaymentReport: C14/M07
IF COL_LENGTH(N'dbo.PaymentReport', N'TransferredAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD [TransferredAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.PaymentReport', N'ReportedCurrency') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentReport] ADD [ReportedCurrency] VARCHAR(3) NULL;';

-- PaymentTransaction: M07
IF COL_LENGTH(N'dbo.PaymentTransaction', N'VerificationSource') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [VerificationSource] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.PaymentTransaction', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.PaymentTransaction', N'InternalNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentTransaction] ADD [InternalNotes] NVARCHAR(2000) NULL;';

-- Refund: M07/M09
IF COL_LENGTH(N'dbo.Refund', N'RequestedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [RequestedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Refund', N'RequestedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [RequestedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ApprovedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ApprovedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ApprovedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ApprovedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'BankRefundReference') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [BankRefundReference] VARCHAR(150) NULL;';
IF COL_LENGTH(N'dbo.Refund', N'ProofFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] ADD [ProofFileKey] NVARCHAR(1024) NULL;';

-- DocumentRequirement: S04/C11
IF COL_LENGTH(N'dbo.DocumentRequirement', N'Instructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [Instructions] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'ScopeCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [ScopeCode] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'HorseID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [HorseID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'ValidityConditionsJSON') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [ValidityConditionsJSON] NVARCHAR(MAX) NULL;';
IF COL_LENGTH(N'dbo.DocumentRequirement', N'CaseJustification') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] ADD [CaseJustification] NVARCHAR(2000) NULL;';

-- DocumentVersion: C03/C11/S03
IF COL_LENGTH(N'dbo.DocumentVersion', N'UploadedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [UploadedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'OriginalFileName') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [OriginalFileName] NVARCHAR(255) NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'ContentType') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [ContentType] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.DocumentVersion', N'FileSizeBytes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] ADD [FileSizeBytes] BIGINT NULL;';

-- ComplianceRuleVersion: S04
IF COL_LENGTH(N'dbo.ComplianceRuleVersion', N'TransitCountryCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD [TransitCountryCode] VARCHAR(2) NULL;';
IF COL_LENGTH(N'dbo.ComplianceRuleVersion', N'RuleDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] ADD [RuleDescription] NVARCHAR(2000) NULL;';

-- AuthorityProcedure: S05
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'PhaseCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [PhaseCode] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'AuthorityContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [AuthorityContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'HoldReason') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [HoldReason] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.AuthorityProcedure', N'RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] ADD [RecordedBy] BIGINT NULL;';

-- ComplianceClearance: S02/S05
IF COL_LENGTH(N'dbo.ComplianceClearance', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.ComplianceClearance', N'AssessmentNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceClearance] ADD [AssessmentNotes] NVARCHAR(2000) NULL;';

-- SupplierOffer: F02/M12
IF COL_LENGTH(N'dbo.SupplierOffer', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.SupplierOffer', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.SupplierOffer', N'SourceContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] ADD [SourceContact] NVARCHAR(500) NULL;';

-- FleetVehicle: F06/F07
IF COL_LENGTH(N'dbo.FleetVehicle', N'OwnerSupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [OwnerSupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'MaintenanceFrom') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [MaintenanceFrom] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'MaintenanceTo') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [MaintenanceTo] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'DocumentExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [DocumentExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FleetVehicle', N'AssetNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] ADD [AssetNotes] NVARCHAR(2000) NULL;';

-- StallEquipment: F06/F07/BOOK-02
IF COL_LENGTH(N'dbo.StallEquipment', N'OwnerSupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [OwnerSupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'WeightLimitKg') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [WeightLimitKg] DECIMAL(8,2) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'MaintenanceFrom') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [MaintenanceFrom] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'MaintenanceTo') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [MaintenanceTo] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'DocumentExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [DocumentExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallEquipment', N'AssetNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] ADD [AssetNotes] NVARCHAR(2000) NULL;';

-- TransportPlan: F04/M06/C15
IF COL_LENGTH(N'dbo.TransportPlan', N'OrderID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD [OrderID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TransportPlan', N'AcceptedQuoteID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] ADD [AcceptedQuoteID] BIGINT NULL;';

-- TripLeg: F04/C15/D02
IF COL_LENGTH(N'dbo.TripLeg', N'StartTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [StartTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'EndTimezone') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [EndTimezone] VARCHAR(100) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'PlannedFlightNumber') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [PlannedFlightNumber] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'ConnectionBufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [ConnectionBufferMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'RestDurationMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [RestDurationMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripLeg', N'CareInstructions') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] ADD [CareInstructions] NVARCHAR(2000) NULL;';

-- TripAssignment: F08
IF COL_LENGTH(N'dbo.TripAssignment', N'BufferMinutes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD [BufferMinutes] INT NULL;';
IF COL_LENGTH(N'dbo.TripAssignment', N'ConflictNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] ADD [ConflictNotes] NVARCHAR(1000) NULL;';

-- FlightReservation: F04
IF COL_LENGTH(N'dbo.FlightReservation', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'ConfirmedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [ConfirmedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'SupplierContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [SupplierContact] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.FlightReservation', N'UsableCapacitySlots') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] ADD [UsableCapacitySlots] DECIMAL(5,1) NULL;';

-- StallAssignment: F04/BOOK-02
IF COL_LENGTH(N'dbo.StallAssignment', N'PositionDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [PositionDescription] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierApprovalFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierApprovalFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierApprovedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierApprovedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.StallAssignment', N'CarrierContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallAssignment] ADD [CarrierContact] NVARCHAR(500) NULL;';

-- QuarantineReservation: F04/S05/BOOK-05
IF COL_LENGTH(N'dbo.QuarantineReservation', N'SupplierID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [SupplierID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ConfirmedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ConfirmedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ConfirmedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ConfirmedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'ExpiresAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [ExpiresAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.QuarantineReservation', N'FacilityContact') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] ADD [FacilityContact] NVARCHAR(500) NULL;';

-- InsurancePolicy: BOOK-07/F04
IF COL_LENGTH(N'dbo.InsurancePolicy', N'VerifiedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [VerifiedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.InsurancePolicy', N'VerifiedAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [VerifiedAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.InsurancePolicy', N'PolicyTerms') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] ADD [PolicyTerms] NVARCHAR(MAX) NULL;';

-- HorseReceipt: D02/D02-RECEIPT
IF COL_LENGTH(N'dbo.HorseReceipt', N'LegID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [LegID] BIGINT NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'ObservedPassportNumber') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [ObservedPassportNumber] VARCHAR(50) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'IdentityCheckResult') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [IdentityCheckResult] VARCHAR(20) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.HorseReceipt', N'LocationDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] ADD [LocationDescription] NVARCHAR(500) NULL;';

-- LegMilestoneLog: D02/F04/C15
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'SourceCode') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceCode] VARCHAR(30) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'SourceReference') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [SourceReference] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'LocationDescription') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [LocationDescription] NVARCHAR(500) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'EstimatedArrivalAt') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EstimatedArrivalAt] DATETIME2(0) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'DelayReason') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [DelayReason] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.LegMilestoneLog', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[LegMilestoneLog] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- HorseWelfareLog: D03
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'FeedAmountKg') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [FeedAmountKg] DECIMAL(8,2) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'CareNotes') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [CareNotes] NVARCHAR(2000) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'InstructionFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [InstructionFileKey] NVARCHAR(1024) NULL;';
IF COL_LENGTH(N'dbo.HorseWelfareLog', N'SupersedesLogID') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] ADD [SupersedesLogID] BIGINT NULL;';

-- IncidentReport: D02/M10/M11
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

-- OperationalAdjustment: M08/M09/M11/C10
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

-- TripHandover: D04/C16
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
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] ADD [EvidenceFilesJSON] NVARCHAR(MAX) NULL;';

-- ActualCost: M11/M13/F04
IF COL_LENGTH(N'dbo.ActualCost', N'Description') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [Description] NVARCHAR(1000) NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'RecordedBy') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [RecordedBy] BIGINT NULL;';
IF COL_LENGTH(N'dbo.ActualCost', N'EvidenceFileKey') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] ADD [EvidenceFileKey] NVARCHAR(1024) NULL;';

-- Supplier: M12/F02/F04/F07
IF OBJECT_ID(N'dbo.Supplier', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[Supplier] (
    SupplierID BIGINT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(200) NOT NULL, ContactName NVARCHAR(150) NULL,
    Email VARCHAR(255) NULL, Phone VARCHAR(30) NULL,
    ServicesOfferedJSON NVARCHAR(MAX) NULL, IsActive BIT NOT NULL DEFAULT (1),
    CreatedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_Supplier PRIMARY KEY (SupplierID),
    CONSTRAINT CK_Supplier_Name CHECK (LEN(LTRIM(RTRIM(Name))) > 0),
    CONSTRAINT CK_Supplier_ServicesJSON CHECK
        (ServicesOfferedJSON IS NULL OR ISJSON(ServicesOfferedJSON) = 1)
);';

-- DocumentTypeCatalog: S04/C11
IF OBJECT_ID(N'dbo.DocumentTypeCatalog', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[DocumentTypeCatalog] (
    TypeCode VARCHAR(50) NOT NULL,
    DisplayName NVARCHAR(200) NOT NULL, Description NVARCHAR(1000) NULL,
    IsActive BIT NOT NULL DEFAULT (1),
    CONSTRAINT PK_DocumentTypeCatalog PRIMARY KEY (TypeCode),
    CONSTRAINT CK_DocumentTypeCatalog_Code CHECK
        (LEN(LTRIM(RTRIM(TypeCode))) > 0)
);';

-- QuotePaymentSchedule: M04/C13/C14/M07
IF OBJECT_ID(N'dbo.QuotePaymentSchedule', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[QuotePaymentSchedule] (
    ScheduleID BIGINT IDENTITY(1,1) NOT NULL,
    QuoteID BIGINT NOT NULL, SequenceNo INT NOT NULL,
    PaymentStage VARCHAR(50) NOT NULL, AmountDue DECIMAL(18,2) NOT NULL,
    Currency VARCHAR(3) NOT NULL, DueAt DATETIME2(0) NULL,
    CONSTRAINT PK_QuotePaymentSchedule PRIMARY KEY (ScheduleID),
    CONSTRAINT UQ_QuotePaymentSchedule_Sequence UNIQUE (QuoteID, SequenceNo),
    CONSTRAINT UQ_QuotePaymentSchedule_Parent UNIQUE (ScheduleID, QuoteID),
    CONSTRAINT CK_QuotePaymentSchedule_Sequence CHECK (SequenceNo > 0),
    CONSTRAINT CK_QuotePaymentSchedule_Amount CHECK (AmountDue > 0)
);';

-- OrderChangeRequest: C10/M08/M09
IF OBJECT_ID(N'dbo.OrderChangeRequest', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[OrderChangeRequest] (
    ChangeRequestID BIGINT IDENTITY(1,1) NOT NULL,
    OrderID BIGINT NOT NULL, BaseRevisionID BIGINT NULL,
    RequestedBy BIGINT NOT NULL, ChangeType VARCHAR(30) NOT NULL,
    Reason NVARCHAR(2000) NOT NULL, RequestedChangesJSON NVARCHAR(MAX) NOT NULL,
    AttachmentFileKey NVARCHAR(1024) NULL, Status VARCHAR(30) NOT NULL,
    CreatedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    RespondedBy BIGINT NULL, RespondedAt DATETIME2(0) NULL,
    ResponseReason NVARCHAR(2000) NULL,
    CONSTRAINT PK_OrderChangeRequest PRIMARY KEY (ChangeRequestID),
    CONSTRAINT CK_OrderChangeRequest_JSON CHECK (ISJSON(RequestedChangesJSON) = 1),
    CONSTRAINT CK_OrderChangeRequest_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) > 0)
);';

-- StallFitPolicy: BOOK-02/F04/F07
IF OBJECT_ID(N'dbo.StallFitPolicy', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[StallFitPolicy] (
    PolicyID BIGINT IDENTITY(1,1) NOT NULL,
    PolicyCode VARCHAR(50) NOT NULL, VersionNo INT NOT NULL,
    StallID BIGINT NULL, CarrierSupplierID BIGINT NULL,
    SlotQuantity DECIMAL(5,1) NOT NULL, MaxWeightKg DECIMAL(8,2) NULL,
    MaxHeightCm DECIMAL(8,2) NULL, MaxWidthCm DECIMAL(8,2) NULL,
    EffectiveFrom DATETIME2(0) NOT NULL, EffectiveTo DATETIME2(0) NULL,
    SourceURL NVARCHAR(2048) NULL, SourceFileKey NVARCHAR(1024) NULL,
    ReviewedBy BIGINT NULL, ReviewedAt DATETIME2(0) NULL,
    Status VARCHAR(20) NOT NULL,
    CONSTRAINT PK_StallFitPolicy PRIMARY KEY (PolicyID),
    CONSTRAINT UQ_StallFitPolicy_Version UNIQUE (PolicyCode, VersionNo),
    CONSTRAINT CK_StallFitPolicy_Version CHECK (VersionNo > 0),
    CONSTRAINT CK_StallFitPolicy_Slots CHECK (SlotQuantity IN (1.0, 1.5, 3.0)),
    CONSTRAINT CK_StallFitPolicy_Dimensions CHECK
        (MaxWeightKg > 0 AND MaxHeightCm > 0 AND MaxWidthCm > 0),
    CONSTRAINT CK_StallFitPolicy_Dates CHECK
        (EffectiveTo IS NULL OR EffectiveTo > EffectiveFrom),
    CONSTRAINT CK_StallFitPolicy_Status CHECK
        (Status IN (''DRAFT'', ''APPROVED'', ''INACTIVE'')),
    CONSTRAINT CK_StallFitPolicy_Approval CHECK
        (Status <> ''APPROVED'' OR (ReviewedBy IS NOT NULL AND ReviewedAt IS NOT NULL
        AND MaxWeightKg IS NOT NULL AND MaxHeightCm IS NOT NULL
        AND MaxWidthCm IS NOT NULL AND (SourceURL IS NOT NULL
        OR SourceFileKey IS NOT NULL)))
);';

-- SchemaMigrationHistory: Technical
IF OBJECT_ID(N'dbo.SchemaMigrationHistory', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.[SchemaMigrationHistory] (
    MigrationID VARCHAR(100) NOT NULL,
    AppliedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
    AppliedBy NVARCHAR(128) NOT NULL DEFAULT (SUSER_SNAME()),
    CONSTRAINT PK_SchemaMigrationHistory PRIMARY KEY (MigrationID)
);';

-- Exact backfill of the new plan OrderID through its existing revision.
EXEC sys.sp_executesql N'UPDATE p SET OrderID = r.OrderID FROM dbo.TransportPlan p JOIN dbo.OrderRevision r ON r.RevisionID=p.RevisionID WHERE p.OrderID IS NULL;';
EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan ALTER COLUMN OrderID BIGINT NOT NULL;';
EXEC sys.sp_executesql N'INSERT INTO dbo.DocumentTypeCatalog (TypeCode, DisplayName)
SELECT s.DocumentType, s.DocumentType FROM
    (SELECT DocumentType FROM dbo.HorseDocument
     UNION SELECT DocumentType FROM dbo.DocumentRequirement
     UNION SELECT DocumentType FROM dbo.ComplianceRuleVersion) s
WHERE NOT EXISTS (SELECT 1 FROM dbo.DocumentTypeCatalog d
                  WHERE d.TypeCode=s.DocumentType);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrderRevision') AND name=N'UQ_Figma_OrderRevision_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.OrderRevision ADD CONSTRAINT UQ_Figma_OrderRevision_Parent UNIQUE (RevisionID, OrderID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Quote') AND name=N'UQ_Figma_Quote_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Quote ADD CONSTRAINT UQ_Figma_Quote_Parent UNIQUE (QuoteID, RevisionID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TransportPlan') AND name=N'UQ_Figma_TransportPlan_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TransportPlan ADD CONSTRAINT UQ_Figma_TransportPlan_Parent UNIQUE (PlanID, OrderID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TripLeg') AND name=N'UQ_Figma_TripLeg_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.TripLeg ADD CONSTRAINT UQ_Figma_TripLeg_Parent UNIQUE (LegID, PlanID);';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HorseWelfareLog') AND name=N'UQ_Figma_HorseWelfareLog_Parent')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.HorseWelfareLog ADD CONSTRAINT UQ_Figma_HorseWelfareLog_Parent UNIQUE (WelfareLogID, LegID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_PreparedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_PreparedBy FOREIGN KEY (PreparedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_IssuedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_IssuedBy FOREIGN KEY (IssuedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Refund_ApprovedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT FK_Figma_Refund_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_AuthorityProcedure_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[AuthorityProcedure] WITH CHECK ADD CONSTRAINT FK_Figma_AuthorityProcedure_RecordedBy FOREIGN KEY (RecordedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_FlightReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT FK_Figma_FlightReservation_ConfirmedBy FOREIGN KEY (ConfirmedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_ConfirmedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT FK_Figma_QuarantineReservation_ConfirmedBy FOREIGN KEY (ConfirmedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_InsurancePolicy_VerifiedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[InsurancePolicy] WITH CHECK ADD CONSTRAINT FK_Figma_InsurancePolicy_VerifiedBy FOREIGN KEY (VerifiedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_AcknowledgedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT FK_Figma_IncidentReport_AcknowledgedBy FOREIGN KEY (AcknowledgedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_IncidentReport_ResolvedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT FK_Figma_IncidentReport_ResolvedBy FOREIGN KEY (ResolvedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_RecordedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT FK_Figma_ActualCost_RecordedBy FOREIGN KEY (RecordedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_ReviewedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_ReviewedBy FOREIGN KEY (ReviewedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RespondedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_RespondedBy FOREIGN KEY (RespondedBy) REFERENCES dbo.[StaffProfile] (StaffID);';
IF OBJECT_ID(N'dbo.FK_Figma_Quote_AcceptedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Quote] WITH CHECK ADD CONSTRAINT FK_Figma_Quote_AcceptedByCustomerID FOREIGN KEY (AcceptedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT FK_Figma_OperationalAdjustment_RespondedByCustomerID FOREIGN KEY (RespondedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_TripHandover_RespondedByCustomerID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT FK_Figma_TripHandover_RespondedByCustomerID FOREIGN KEY (RespondedByCustomerID) REFERENCES dbo.[Customer] (CustomerID);';
IF OBJECT_ID(N'dbo.FK_Figma_Refund_RequestedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[Refund] WITH CHECK ADD CONSTRAINT FK_Figma_Refund_RequestedBy FOREIGN KEY (RequestedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentVersion_UploadedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentVersion_UploadedBy FOREIGN KEY (UploadedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_RequestedBy', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_RequestedBy FOREIGN KEY (RequestedBy) REFERENCES dbo.[User] (UserID);';
IF OBJECT_ID(N'dbo.FK_Figma_SupplierOffer_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[SupplierOffer] WITH CHECK ADD CONSTRAINT FK_Figma_SupplierOffer_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_FleetVehicle_OwnerSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT FK_Figma_FleetVehicle_OwnerSupplierID FOREIGN KEY (OwnerSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallEquipment_OwnerSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT FK_Figma_StallEquipment_OwnerSupplierID FOREIGN KEY (OwnerSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_TripLeg_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT FK_Figma_TripLeg_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuarantineReservation_SupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuarantineReservation] WITH CHECK ADD CONSTRAINT FK_Figma_QuarantineReservation_SupplierID FOREIGN KEY (SupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_CarrierSupplierID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_CarrierSupplierID FOREIGN KEY (CarrierSupplierID) REFERENCES dbo.[Supplier] (SupplierID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseDocument_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseDocument] WITH CHECK ADD CONSTRAINT FK_Figma_HorseDocument_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentRequirement_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentRequirement_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_ComplianceRuleVersion_DocumentType', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ComplianceRuleVersion] WITH CHECK ADD CONSTRAINT FK_Figma_ComplianceRuleVersion_DocumentType FOREIGN KEY (DocumentType) REFERENCES dbo.[DocumentTypeCatalog] (TypeCode);';
IF OBJECT_ID(N'dbo.FK_Figma_DocumentRequirement_HorseID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT FK_Figma_DocumentRequirement_HorseID FOREIGN KEY (HorseID) REFERENCES dbo.[Horse] (HorseID);';
IF OBJECT_ID(N'dbo.FK_Figma_StallFitPolicy_StallID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallFitPolicy] WITH CHECK ADD CONSTRAINT FK_Figma_StallFitPolicy_StallID FOREIGN KEY (StallID) REFERENCES dbo.[StallEquipment] (StallID);';
IF OBJECT_ID(N'dbo.FK_Figma_QuotePaymentSchedule_QuoteID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[QuotePaymentSchedule] WITH CHECK ADD CONSTRAINT FK_Figma_QuotePaymentSchedule_QuoteID FOREIGN KEY (QuoteID) REFERENCES dbo.[Quote] (QuoteID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_OrderID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_OrderID FOREIGN KEY (OrderID) REFERENCES dbo.[TransportOrder] (OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_OperationalAdjustment_ChangeRequestID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT FK_Figma_OperationalAdjustment_ChangeRequestID FOREIGN KEY (ChangeRequestID) REFERENCES dbo.[OrderChangeRequest] (ChangeRequestID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportOrder_CurrentRevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT FK_Figma_TransportOrder_CurrentRevisionID FOREIGN KEY (CurrentRevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportOrder_CurrentPlanID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT FK_Figma_TransportOrder_CurrentPlanID FOREIGN KEY (CurrentPlanID, OrderID) REFERENCES dbo.[TransportPlan] (PlanID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_RevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT FK_Figma_TransportPlan_RevisionID FOREIGN KEY (RevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_TransportPlan_AcceptedQuoteID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportPlan] WITH CHECK ADD CONSTRAINT FK_Figma_TransportPlan_AcceptedQuoteID FOREIGN KEY (AcceptedQuoteID, RevisionID) REFERENCES dbo.[Quote] (QuoteID, RevisionID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseReceipt_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT FK_Figma_HorseReceipt_LegID FOREIGN KEY (LegID, PlanID) REFERENCES dbo.[TripLeg] (LegID, PlanID);';
IF OBJECT_ID(N'dbo.FK_Figma_ActualCost_LegID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[ActualCost] WITH CHECK ADD CONSTRAINT FK_Figma_ActualCost_LegID FOREIGN KEY (LegID, PlanID) REFERENCES dbo.[TripLeg] (LegID, PlanID);';
IF OBJECT_ID(N'dbo.FK_Figma_PaymentRequest_ScheduleID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[PaymentRequest] WITH CHECK ADD CONSTRAINT FK_Figma_PaymentRequest_ScheduleID FOREIGN KEY (ScheduleID, QuoteID) REFERENCES dbo.[QuotePaymentSchedule] (ScheduleID, QuoteID);';
IF OBJECT_ID(N'dbo.FK_Figma_OrderChangeRequest_BaseRevisionID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderChangeRequest] WITH CHECK ADD CONSTRAINT FK_Figma_OrderChangeRequest_BaseRevisionID FOREIGN KEY (BaseRevisionID, OrderID) REFERENCES dbo.[OrderRevision] (RevisionID, OrderID);';
IF OBJECT_ID(N'dbo.FK_Figma_HorseWelfareLog_SupersedesLogID', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT FK_Figma_HorseWelfareLog_SupersedesLogID FOREIGN KEY (SupersedesLogID, LegID) REFERENCES dbo.[HorseWelfareLog] (WelfareLogID, LegID);';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_TransitAirportsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_TransitAirportsJSON CHECK (TransitAirportsJSON IS NULL OR ISJSON(TransitAirportsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_DraftPayloadJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_DraftPayloadJSON CHECK (DraftPayloadJSON IS NULL OR ISJSON(DraftPayloadJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_OrderServiceChoice_SelectionInputsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OrderServiceChoice] WITH CHECK ADD CONSTRAINT CK_Figma_OrderServiceChoice_SelectionInputsJSON CHECK (SelectionInputsJSON IS NULL OR ISJSON(SelectionInputsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentRequirement_ValidityConditionsJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentRequirement_ValidityConditionsJSON CHECK (ValidityConditionsJSON IS NULL OR ISJSON(ValidityConditionsJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_IncidentReport_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[IncidentReport] WITH CHECK ADD CONSTRAINT CK_Figma_IncidentReport_EvidenceFilesJSON CHECK (EvidenceFilesJSON IS NULL OR ISJSON(EvidenceFilesJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_EvidenceFilesJSON', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT CK_Figma_TripHandover_EvidenceFilesJSON CHECK (EvidenceFilesJSON IS NULL OR ISJSON(EvidenceFilesJSON) = 1);';
IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_WeightLimitKg', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT CK_Figma_StallEquipment_WeightLimitKg CHECK (WeightLimitKg > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentVersion_FileSizeBytes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentVersion] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentVersion_FileSizeBytes CHECK (FileSizeBytes > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_FlightReservation_UsableCapacitySlots', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FlightReservation] WITH CHECK ADD CONSTRAINT CK_Figma_FlightReservation_UsableCapacitySlots CHECK (UsableCapacitySlots > 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_ConnectionBufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT CK_Figma_TripLeg_ConnectionBufferMinutes CHECK (ConnectionBufferMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripLeg_RestDurationMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripLeg] WITH CHECK ADD CONSTRAINT CK_Figma_TripLeg_RestDurationMinutes CHECK (RestDurationMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_TripAssignment_BufferMinutes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripAssignment] WITH CHECK ADD CONSTRAINT CK_Figma_TripAssignment_BufferMinutes CHECK (BufferMinutes >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_FeedAmountKg', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT CK_Figma_HorseWelfareLog_FeedAmountKg CHECK (FeedAmountKg >= 0);';
IF OBJECT_ID(N'dbo.CK_Figma_FleetVehicle_MaintenanceDates', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[FleetVehicle] WITH CHECK ADD CONSTRAINT CK_Figma_FleetVehicle_MaintenanceDates CHECK (MaintenanceTo IS NULL OR (MaintenanceFrom IS NOT NULL AND MaintenanceTo >= MaintenanceFrom));';
IF OBJECT_ID(N'dbo.CK_Figma_StallEquipment_MaintenanceDates', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[StallEquipment] WITH CHECK ADD CONSTRAINT CK_Figma_StallEquipment_MaintenanceDates CHECK (MaintenanceTo IS NULL OR (MaintenanceFrom IS NOT NULL AND MaintenanceTo >= MaintenanceFrom));';
IF OBJECT_ID(N'dbo.CK_Figma_TransportOrder_ImportType', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TransportOrder] WITH CHECK ADD CONSTRAINT CK_Figma_TransportOrder_ImportType CHECK (ImportType IN (''TEMPORARY'',''PERMANENT'',''NEEDS_ADVICE''));';
IF OBJECT_ID(N'dbo.CK_Figma_DocumentRequirement_Scope', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[DocumentRequirement] WITH CHECK ADD CONSTRAINT CK_Figma_DocumentRequirement_Scope CHECK (ScopeCode IS NULL OR (ScopeCode=''HORSE'' AND HorseID IS NOT NULL) OR ScopeCode=''SHIPMENT'');';
IF OBJECT_ID(N'dbo.CK_Figma_HorseReceipt_Identity', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseReceipt] WITH CHECK ADD CONSTRAINT CK_Figma_HorseReceipt_Identity CHECK (IdentityCheckResult IN (''PENDING'',''MATCHED'',''MISMATCHED''));';
IF OBJECT_ID(N'dbo.CK_Figma_HorseWelfareLog_Supersedes', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[HorseWelfareLog] WITH CHECK ADD CONSTRAINT CK_Figma_HorseWelfareLog_Supersedes CHECK (SupersedesLogID IS NULL OR SupersedesLogID <> WelfareLogID);';
IF OBJECT_ID(N'dbo.CK_Figma_OperationalAdjustment_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[OperationalAdjustment] WITH CHECK ADD CONSTRAINT CK_Figma_OperationalAdjustment_CustomerDecision CHECK (CustomerDecision IN (''PENDING'',''ACCEPTED'',''REJECTED''));';
IF OBJECT_ID(N'dbo.CK_Figma_TripHandover_CustomerDecision', N'C') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[TripHandover] WITH CHECK ADD CONSTRAINT CK_Figma_TripHandover_CustomerDecision CHECK (CustomerDecision IN (''ACCEPTED'',''EXCEPTION'',''NOT_DELIVERED''));';

-- Protect the selected operating quote; draft estimates cannot authorize a plan.
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Figma_TransportPlan_AcceptedQuote
ON dbo.TransportPlan AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted p JOIN dbo.Quote q ON q.QuoteID=p.AcceptedQuoteID
               WHERE q.AcceptedAt IS NULL OR q.QuoteStatus NOT IN (''ACCEPTED'',''SUPERSEDED''))
        THROW 51002, ''The plan requires a genuinely accepted quotation.'', 1;
END;';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Figma_Quote_AcceptedHistory
ON dbo.Quote AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted q JOIN dbo.TransportPlan p ON p.AcceptedQuoteID=q.QuoteID
               WHERE q.AcceptedAt IS NULL OR q.QuoteStatus NOT IN (''ACCEPTED'',''SUPERSEDED''))
        THROW 51002, ''An operating plan still references this accepted quote.'', 1;
END;';
EXEC sys.sp_executesql N'IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory WHERE MigrationID=''002_align_figma_without_organization'') INSERT dbo.SchemaMigrationHistory (MigrationID) VALUES (''002_align_figma_without_organization'');';
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS reapplication: 002_align_figma_without_organization.sql';
GO
PRINT N'RUN reapplication: 003_order_assessment_assignments.sql';
GO
-- F04: preserve history and serialize current case responsibility.
-- Run after 001/002 on a reviewed database. No rows are deleted or backfilled
-- with invented actors/deadlines. Resolve reported legacy duplicates first.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.OrderAssignment', N'U') IS NULL
    OR OBJECT_ID(N'dbo.OrderDecision', N'U') IS NULL
    THROW 51000, 'Apply the EquiTransit baseline and migration 002 first.', 1;

IF EXISTS (SELECT RevisionID FROM dbo.OrderDecision
           GROUP BY RevisionID HAVING COUNT(*) > 1)
    THROW 51001, 'Multiple decisions for a revision require manual review.', 1;
IF EXISTS (SELECT OrderID, AssignmentRole FROM dbo.OrderAssignment
           WHERE EndedAt IS NULL GROUP BY OrderID, AssignmentRole
           HAVING COUNT(*) > 1)
    THROW 51002, 'Duplicate active case assignments require manual review.', 1;

IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'DueAt') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD DueAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignmentNote') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignmentNote NVARCHAR(1000) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = N'FK_OrderAssignment_AssignedBy')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          FK_OrderAssignment_AssignedBy FOREIGN KEY (AssignedBy)
          REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'CK_OrderAssignment_DueAt')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          CK_OrderAssignment_DueAt CHECK
          (DueAt IS NULL OR DueAt >= AssignedAt);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderDecision')
                 AND name = N'UX_OrderDecision_Revision')
    CREATE UNIQUE INDEX UX_OrderDecision_Revision
        ON dbo.OrderDecision(RevisionID);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'UX_OrderAssignment_ActiveRole')
    CREATE UNIQUE INDEX UX_OrderAssignment_ActiveRole
        ON dbo.OrderAssignment(OrderID, AssignmentRole)
        WHERE EndedAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'IX_OrderAssignment_ActiveStaff')
    CREATE INDEX IX_OrderAssignment_ActiveStaff
        ON dbo.OrderAssignment(StaffID, OrderID) WHERE EndedAt IS NULL;
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 003_order_assessment_assignments.sql';
GO
PRINT N'RUN reapplication: 004_service_assessment_sources.sql';
GO
-- F05 read contracts for sourced itinerary and Specialist quantity assessment.
-- After 001/002/003. Does not approve existing distances or seed demo prices.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    THROW 51000, 'Apply migration 003 before migration 004.', 1;

IF COL_LENGTH(N'dbo.TripLeg', N'DistanceSource') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceSource NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedBy') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedAt') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_TripLeg_DistanceConfirmedBy')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      FK_TripLeg_DistanceConfirmedBy FOREIGN KEY (DistanceConfirmedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_TripLeg_DistanceSource')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      CK_TripLeg_DistanceSource CHECK
      ((DistanceSource IS NULL AND DistanceConfirmedBy IS NULL
        AND DistanceConfirmedAt IS NULL) OR
       (DistanceSource IS NOT NULL AND LEN(LTRIM(RTRIM(DistanceSource)))>0
        AND DistanceConfirmedBy IS NOT NULL AND DistanceConfirmedAt IS NOT NULL));');

IF OBJECT_ID(N'dbo.RevisionQuarantineAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionQuarantineAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        Applicability VARCHAR(20) NOT NULL,
        RequiredDays INT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionQuarantineAssessment_Outcome CHECK
            ((Applicability='REQUIRED' AND RequiredDays IS NOT NULL
              AND RequiredDays>0)
              OR (Applicability='NOT_REQUIRED' AND RequiredDays IS NULL)),
        CONSTRAINT CK_RevisionQuarantineAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionQuarantineAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionQuarantineAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;

IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionVeterinaryAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        VisitCount INT NOT NULL,
        TestCount INT NOT NULL,
        DoseCount INT NOT NULL,
        CertificateCount INT NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionVeterinaryAssessment_Counts CHECK
            (VisitCount>=0 AND TestCount>=0 AND DoseCount>=0
              AND CertificateCount>=0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;
IF COL_LENGTH(N'dbo.RevisionQuarantineAssessment',N'SupersededAt') IS NULL
    OR COL_LENGTH(N'dbo.RevisionVeterinaryAssessment',N'SupersededAt') IS NULL
    THROW 51001, 'Review existing assessment tables before applying 004.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionQuarantineAssessment')
      AND name=N'UX_QuarantineAssessment_Current')
    CREATE UNIQUE INDEX UX_QuarantineAssessment_Current
        ON dbo.RevisionQuarantineAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionVeterinaryAssessment')
      AND name=N'UX_VeterinaryAssessment_Current')
    CREATE UNIQUE INDEX UX_VeterinaryAssessment_Current
        ON dbo.RevisionVeterinaryAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 004_service_assessment_sources.sql';
GO
PRINT N'RUN reapplication: 005_versioned_quotations.sql';
GO
-- F06: source-backed, immutable quotation snapshots. After migrations 001-004.
-- Review/backup before deployment. Never run test fixtures on EquiTransit.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
    THROW 51000, 'Apply migrations 001-004 first.', 1;

IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    ALTER TABLE dbo.Quote ADD PricingSnapshotJSON NVARCHAR(MAX) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RetailUnitPrice') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RetailUnitPrice DECIMAL(18,2) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedBy') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedAt') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_SupplierOffer_RecordedBy')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      FK_SupplierOffer_RecordedBy FOREIGN KEY(RecordedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_Quote_PricingSnapshot')
    EXEC(N'ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT
      CK_Quote_PricingSnapshot CHECK(PricingSnapshotJSON IS NULL
      OR ISJSON(PricingSnapshotJSON)=1);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_SupplierOffer_RetailPrice')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      CK_SupplierOffer_RetailPrice CHECK(RetailUnitPrice IS NULL
      OR RetailUnitPrice>=0);');

-- A draft needs an expiry before approval, without inventing IssuedAt.
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name=N'CK_Quote_Validity')
    ALTER TABLE dbo.Quote DROP CONSTRAINT CK_Quote_Validity;
ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT CK_Quote_Validity CHECK
    (ValidUntil IS NULL OR
     (IssuedAt IS NULL AND ValidUntil>CreatedAt) OR
     (IssuedAt IS NOT NULL AND ValidUntil>IssuedAt));

IF EXISTS (SELECT RevisionID FROM dbo.Quote WHERE QuoteStatus='ACCEPTED'
           GROUP BY RevisionID HAVING COUNT(*)>1)
    THROW 51001, 'Repair duplicate accepted quotes before migration.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.Quote') AND
               name=N'UX_Quote_AcceptedRevision')
    CREATE UNIQUE INDEX UX_Quote_AcceptedRevision ON dbo.Quote(RevisionID)
        WHERE QuoteStatus='ACCEPTED';
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.QuotePaymentSchedule')
      AND i.is_unique=1 AND i.has_filter=0
      AND (SELECT COUNT(*) FROM sys.index_columns c
           WHERE c.object_id=i.object_id AND c.index_id=i.index_id
             AND c.key_ordinal>0)=2
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'QuoteID')
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'SequenceNo'))
    CREATE UNIQUE INDEX UX_QuotePaymentSchedule_Sequence
        ON dbo.QuotePaymentSchedule(QuoteID,SequenceNo);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.AuditEvent') AND
               name=N'IX_AuditEvent_QuotationOperation')
    CREATE INDEX IX_AuditEvent_QuotationOperation
        ON dbo.AuditEvent(EntityType,EntityID,UserID,EventID)
        INCLUDE(ActionCode);
COMMIT;

GO
PRINT N'PASS reapplication: 005_versioned_quotations.sql';
GO
PRINT N'RUN reapplication: 006_service_tariff_management.sql';
GO
-- Manager price version provenance; apply after 005. No production fixtures.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    THROW 51000, 'Apply migrations 001-005 before tariff management.', 1;
IF COL_LENGTH(N'dbo.ServiceTariff',N'SourceReference') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedBy') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedAt') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'RecordVersion') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD RecordVersion ROWVERSION NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys
              WHERE name=N'FK_ServiceTariff_PublishedBy')
    EXEC(N'ALTER TABLE dbo.ServiceTariff WITH CHECK ADD CONSTRAINT
      FK_ServiceTariff_PublishedBy FOREIGN KEY(PublishedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
-- Preserve legacy rows as unknown provenance; never invent approval evidence.
COMMIT;

GO
PRINT N'PASS reapplication: 006_service_tariff_management.sql';
GO
PRINT N'RUN reapplication: 007_manual_bank_payments.sql';
GO
-- F07 additive provenance, immutable evidence and idempotent receipts.
-- Requires 005; 006 is reserved for tariff management. No real DB fixtures.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Quote','PricingSnapshotJSON') IS NULL
    THROW 51000,'Apply quotation migration005 first.',1;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    CREATE TABLE dbo.PaymentOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL CHECK(ISJSON(ReceiptJSON)=1),
        CONSTRAINT PK_PaymentOperation PRIMARY KEY(OrderID,UserID,OperationID)
    );
IF OBJECT_ID('dbo.PaymentProof','U') IS NULL
    CREATE TABLE dbo.PaymentProof (
        FileKey UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        MediaType VARCHAR(20) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_PaymentProof_Size CHECK
          (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_PaymentProof_Mime CHECK
          (MediaType IN ('image/png','image/jpeg'))
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest')
    AND name='UQ_PaymentRequest_Schedule')
    CREATE UNIQUE INDEX UQ_PaymentRequest_Schedule
      ON dbo.PaymentRequest(ScheduleID) WHERE ScheduleID IS NOT NULL;
IF COL_LENGTH('dbo.PaymentTransaction','ReversalReason') IS NULL
    ALTER TABLE dbo.PaymentTransaction ADD ReversalReason NVARCHAR(2000) NULL,
        ReversedAt DATETIME2(0) NULL, ReversedBy BIGINT NULL
        REFERENCES dbo.StaffProfile(StaffID),
        ReversalProofFileKey UNIQUEIDENTIFIER NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND name='UQ_Refund_BankReference')
    CREATE UNIQUE INDEX UQ_Refund_BankReference
      ON dbo.Refund(BankRefundReference) WHERE BankRefundReference IS NOT NULL;
COMMIT;

GO
PRINT N'PASS reapplication: 007_manual_bank_payments.sql';
GO
PRINT N'RUN reapplication: 008_dashboards_notifications_audit.sql';
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    THROW 51000,'Apply F07 migration007 before F16.',1;
IF COL_LENGTH('dbo.AuditEvent','EventKey') IS NULL
    ALTER TABLE dbo.AuditEvent ADD EventKey VARCHAR(64) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND name='UQ_AuditEvent_EventKey')
    EXEC('CREATE UNIQUE INDEX UQ_AuditEvent_EventKey
      ON dbo.AuditEvent(EventKey) WHERE EventKey IS NOT NULL');
IF COL_LENGTH('dbo.Notification','SourceEventID') IS NULL
    ALTER TABLE dbo.Notification ADD SourceEventID BIGINT NULL
        REFERENCES dbo.AuditEvent(EventID), EmailState VARCHAR(20) NULL,
        EmailLeaseUntil DATETIME2(0) NULL, EmailAttempts INT NOT NULL DEFAULT 0;
IF OBJECT_ID('dbo.NotificationProjection','U') IS NULL
BEGIN
    CREATE TABLE dbo.NotificationProjection (
      EventID BIGINT NOT NULL PRIMARY KEY REFERENCES dbo.AuditEvent(EventID),
      ProjectedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME());
    -- Establish deployment watermark; do not email old audit history.
    INSERT dbo.NotificationProjection(EventID)
        SELECT EventID FROM dbo.AuditEvent;
END;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='UQ_Notification_EventRecipient')
    EXEC('CREATE UNIQUE INDEX UQ_Notification_EventRecipient
      ON dbo.Notification(SourceEventID,UserID)
      WHERE SourceEventID IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='IX_Notification_RecipientPage')
    CREATE INDEX IX_Notification_RecipientPage ON dbo.Notification
      (UserID,NotificationID DESC) INCLUDE (OrderID,ReadAt,CreatedAt);
COMMIT;

GO
PRINT N'PASS reapplication: 008_dashboards_notifications_audit.sql';
GO
PRINT N'RUN reapplication: 009_compliance_legal_evidence.sql';
GO
-- F08 additive private evidence and phase assessment, after 001-008.
-- Never seeds legal rules, exemptions, permits or file evidence.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.NotificationProjection',N'U') IS NULL
    THROW 51000,'Apply prerequisites through 008 before 009.',1;
IF COL_LENGTH(N'dbo.ComplianceRuleVersion',N'AuthorityRequired') IS NULL
    ALTER TABLE dbo.ComplianceRuleVersion ADD AuthorityRequired BIT NOT NULL
        CONSTRAINT DF_ComplianceRule_Authority DEFAULT(1);
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'SourceReference') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'RuleID') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD RuleID BIGINT NULL
        CONSTRAINT FK_AuthorityProcedure_Rule
        REFERENCES dbo.ComplianceRuleVersion(RuleID);
IF COL_LENGTH(N'dbo.DocumentVersion',N'RevokedAt') IS NULL
    ALTER TABLE dbo.DocumentVersion ADD RevokedAt DATETIME2(0) NULL,
        RevokedBy BIGINT NULL REFERENCES dbo.StaffProfile(StaffID),
        RevocationReason NVARCHAR(2000) NULL;
IF OBJECT_ID(N'dbo.ComplianceFile',N'U') IS NULL
    CREATE TABLE dbo.ComplianceFile (
        FileID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        FileKey UNIQUEIDENTIFIER NOT NULL UNIQUE,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash VARCHAR(128) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_ComplianceFile_Size CHECK
            (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_ComplianceFile_Type CHECK
            (ContentType IN ('application/pdf','image/png','image/jpeg'))
    );
IF OBJECT_ID(N'dbo.ComplianceOperation',N'U') IS NULL
    CREATE TABLE dbo.ComplianceOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_ComplianceOperation PRIMARY KEY(OrderID,UserID,OperationID),
        CONSTRAINT CK_ComplianceOperation_JSON CHECK(ISJSON(ReceiptJSON)=1)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklist',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklist (
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        TransitCountriesJSON NVARCHAR(500) NOT NULL,
        RuleIDsJSON NVARCHAR(2000) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT PK_ComplianceChecklist PRIMARY KEY(RevisionID,PhaseCode),
        CONSTRAINT CK_ComplianceChecklist_JSON CHECK
            (ISJSON(TransitCountriesJSON)=1 AND ISJSON(RuleIDsJSON)=1),
        CONSTRAINT CK_ComplianceChecklist_Dates CHECK(ValidUntil>AssessedAt)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklistHistory',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklistHistory (
        AssessmentID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        SnapshotJSON NVARCHAR(MAX) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT CK_ComplianceChecklistHistory_JSON
            CHECK(ISJSON(SnapshotJSON)=1)
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.ComplianceFile') AND name='IX_ComplianceFile_Order')
    CREATE INDEX IX_ComplianceFile_Order ON dbo.ComplianceFile(OrderID,FileID);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.DocumentRequirement')
    AND name='IX_DocumentRequirement_PhaseRule')
    CREATE INDEX IX_DocumentRequirement_PhaseRule ON
        dbo.DocumentRequirement(RevisionID,PhaseCode,RuleID);
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 009_compliance_legal_evidence.sql';
GO
PRINT N'RUN reapplication: 010_resources_availability_fit.sql';
GO
-- F09 additive resources; review then deploy explicitly. No seed data.
-- Requires the existing 001..009 baseline. Never auto-run on EquiTransit.
-- Legacy buffers stay NULL: Manager must explicitly configure them.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    THROW 51000, 'Deploy resource baseline first.', 1;
IF COL_LENGTH('dbo.FleetVehicle','RecordVersion') IS NULL
    ALTER TABLE dbo.FleetVehicle ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.StallEquipment','RecordVersion') IS NULL
    ALTER TABLE dbo.StallEquipment ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.Supplier','RecordVersion') IS NULL
    ALTER TABLE dbo.Supplier ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.StallFitPolicy','RecordVersion') IS NULL
    ALTER TABLE dbo.StallFitPolicy ADD RecordVersion ROWVERSION;
IF COL_LENGTH('dbo.FleetVehicle','BufferMinutes') IS NULL
    ALTER TABLE dbo.FleetVehicle ADD BufferMinutes INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE name='CK_Resource_FleetVehicle_Buffer')
    ALTER TABLE dbo.FleetVehicle WITH CHECK ADD CONSTRAINT
        CK_Resource_FleetVehicle_Buffer CHECK
        (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);
IF COL_LENGTH('dbo.StallEquipment','BufferMinutes') IS NULL
    ALTER TABLE dbo.StallEquipment ADD BufferMinutes INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE name='CK_Resource_StallEquipment_Buffer')
    ALTER TABLE dbo.StallEquipment WITH CHECK ADD CONSTRAINT
        CK_Resource_StallEquipment_Buffer CHECK
        (BufferMinutes IS NULL OR BufferMinutes BETWEEN 0 AND 525600);
IF OBJECT_ID('dbo.ResourceOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.ResourceOperation (
        UserID BIGINT NOT NULL,
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ResourceOperation PRIMARY KEY (UserID,OperationID),
        CONSTRAINT FK_ResourceOperation_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_ResourceOperation_JSON CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripLeg') AND
    name='IX_Resource_VehicleWindow')
    CREATE INDEX IX_Resource_VehicleWindow ON dbo.TripLeg
        (VehicleID,PlannedDeparture,PlannedArrival)
        INCLUDE (PlanID,LegStatus) WHERE VehicleID IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.StallAssignment') AND
    name='IX_Resource_StallWindow')
    CREATE INDEX IX_Resource_StallWindow ON dbo.StallAssignment
        (StallID,AssignedFrom,AssignedTo) INCLUDE (LegID,Status);
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_VehicleSchedule
ON dbo.TripLeg AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE VehicleID IS NOT NULL)
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.FleetVehicle v
        WITH (UPDLOCK,HOLDLOCK) ON v.VehicleID=i.VehicleID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=v.OwnerSupplierID
        WHERE i.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        (i.TransportMode<>'ROAD' OR v.OperationalStatus<>'ACTIVE'
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
        THROW 51002, 'Vehicle mode, evidence or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripLeg b
        WITH (UPDLOCK,HOLDLOCK) ON a.VehicleID=b.VehicleID AND a.LegID<>b.LegID
        JOIN dbo.FleetVehicle v ON v.VehicleID=a.VehicleID
        JOIN dbo.TransportPlan pa ON pa.PlanID=a.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=b.PlanID
        WHERE a.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        b.LegStatus NOT IN ('CANCELLED','CANCELED','REJECTED') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.PlannedDeparture IS NULL OR b.PlannedArrival IS NULL OR
         (a.PlannedDeparture<DATEADD(minute,v.BufferMinutes,b.PlannedArrival)
          AND b.PlannedDeparture<DATEADD(minute,v.BufferMinutes,
              a.PlannedArrival))))
        THROW 51003, 'Vehicle has an overlapping assignment.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_StallSchedule
ON dbo.StallAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallEquipment e
        WITH (UPDLOCK,HOLDLOCK) ON e.StallID=i.StallID
        JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=e.OwnerSupplierID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (l.TransportMode<>'AIR' OR e.EquipmentStatus<>'ACTIVE'
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
        THROW 51004, 'Stall mode, capacity or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.StallAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StallID=b.StallID
        AND a.StallAssignmentID<>b.StallAssignmentID
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo)))
        AND NOT (
            a.SharingCode='SHARED' AND b.SharingCode='SHARED'
            AND la.TransportMode='AIR' AND lb.TransportMode='AIR'
            AND la.StartPoint=lb.StartPoint AND la.EndPoint=lb.EndPoint
            AND la.PlannedFlightNumber IS NOT NULL
            AND la.PlannedFlightNumber=lb.PlannedFlightNumber
            AND la.PlannedDeparture=lb.PlannedDeparture
            AND la.PlannedArrival=lb.PlannedArrival
            AND pa.OrderID<>pb.OrderID))
        THROW 51005, 'Physical stall has an overlapping assignment.', 1;
    IF EXISTS (SELECT 1 FROM inserted a
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
        AND pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
        AND (SELECT COALESCE(SUM(b.SlotQuantity),0)
             FROM dbo.StallAssignment b
             JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
             JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
             WHERE b.StallID=a.StallID
             AND b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
             AND pb.PlanStatus NOT IN
                 ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
             AND a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
             AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo))
            > e.CapacitySlots)
        THROW 51006, 'Shared stall slot capacity exceeded.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_PlanActivation
ON dbo.TransportPlan AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(PlanStatus) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d
        ON d.PlanID=i.PlanID WHERE
        (i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT') AND
         d.PlanStatus NOT IN ('CONFIRMED','ACTIVE','IN_TRANSIT')) OR
        (d.PlanStatus IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
         i.PlanStatus NOT IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')))
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    -- Reuse the same checks; no window, status or carrier proof is changed.
    UPDATE l SET LegStatus=l.LegStatus FROM dbo.TripLeg l
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE a SET Status=a.Status FROM dbo.StallAssignment a
        JOIN dbo.TripLeg l ON l.LegID=a.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 010_resources_availability_fit.sql';
GO
PRINT N'RUN reapplication: 011_multimodal_operational_plan.sql';
GO
-- F10 operational planning; review then deploy explicitly. No seed data.
-- Requires the existing 001..010 baseline. Never auto-run on EquiTransit.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.ResourceOperation','U') IS NULL
    THROW 51000, 'Deploy migration 010 first.', 1;
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
GO
CREATE OR ALTER TRIGGER dbo.TR_Plan_DriverSchedule
ON dbo.TripAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.StaffProfile s WITH (HOLDLOCK) ON s.StaffID=i.StaffID
        LEFT JOIN dbo.[User] u WITH (HOLDLOCK) ON u.UserID=s.UserID
        LEFT JOIN dbo.[Role] r WITH (HOLDLOCK) ON r.RoleID=u.RoleID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (s.StaffID IS NULL OR u.Status<>'ACTIVE'
         OR (i.RoleInTrip='DRIVER' AND (l.TransportMode<>'ROAD'
             OR r.RoleCode<>'DRIVER'))
         OR COALESCE(i.BufferMinutes,0)<0
         OR i.AssignedFrom IS NULL OR i.AssignedTo IS NULL
         OR i.AssignedFrom>=i.AssignedTo))
        THROW 51006, 'Trip staff mode, role or window invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StaffID=b.StaffID
        AND a.AssignmentID<>b.AssignmentID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,a.AssignedTo))))
        THROW 51007, 'Driver or staff has an overlapping trip assignment.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_PlanActivation
ON dbo.TransportPlan AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(PlanStatus) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d
        ON d.PlanID=i.PlanID WHERE
        (i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT') AND
         d.PlanStatus NOT IN ('CONFIRMED','ACTIVE','IN_TRANSIT')) OR
        (d.PlanStatus IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
         i.PlanStatus NOT IN ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')))
        RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
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
        WHERE i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND ob.OrderStatus NOT IN
            ('COMPLETED','DELIVERED','CANCELLED','CANCELED','CLOSED','REJECTED')
        AND pb.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND (i.PlannedStartTime IS NULL OR i.PlannedEndTime IS NULL OR
             pb.PlannedStartTime IS NULL OR pb.PlannedEndTime IS NULL OR
             (i.PlannedStartTime<pb.PlannedEndTime AND
              pb.PlannedStartTime<i.PlannedEndTime)))
        THROW 51008, 'Horse has an overlapping active operational plan.', 1;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 011_multimodal_operational_plan.sql';
GO
PRINT N'RUN reapplication: 012_departure_gates_horse_receipt.sql';
GO
-- F11 departure gates and horse receipt; review then deploy explicitly.
-- Requires the existing 001..011 baseline. Never auto-run on EquiTransit.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.PlanOperation','U') IS NULL
    THROW 51000, 'Deploy migration 011 first.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.HorseReceipt') AND
    name='IX_HorseReceipt_PlanLeg')
    CREATE INDEX IX_HorseReceipt_PlanLeg ON dbo.HorseReceipt
        (PlanID,LegID,ReceiptID DESC)
        INCLUDE (ReceivedBy,IdentityCheckResult,ReceiptStatus,ReceivedAt);
GO
CREATE OR ALTER TRIGGER dbo.TR_Resource_StallSchedule
ON dbo.StallAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.StallEquipment e
        WITH (UPDLOCK,HOLDLOCK) ON e.StallID=i.StallID
        JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.Supplier s ON s.SupplierID=e.OwnerSupplierID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (l.TransportMode<>'AIR' OR e.EquipmentStatus<>'ACTIVE'
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
        THROW 51004, 'Stall mode, capacity or availability invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.StallAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StallID=b.StallID
        AND a.StallAssignmentID<>b.StallAssignmentID
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        pb.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED') AND
        (b.AssignedFrom IS NULL OR b.AssignedTo IS NULL OR
         (a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo)))
        AND NOT (
            a.SharingCode='SHARED' AND b.SharingCode='SHARED'
            AND la.TransportMode='AIR' AND lb.TransportMode='AIR'
            AND la.StartPoint=lb.StartPoint AND la.EndPoint=lb.EndPoint
            AND la.PlannedFlightNumber IS NOT NULL
            AND la.PlannedFlightNumber=lb.PlannedFlightNumber
            AND la.PlannedDeparture=lb.PlannedDeparture
            AND la.PlannedArrival=lb.PlannedArrival
            AND pa.OrderID<>pb.OrderID))
        THROW 51005, 'Physical stall has an overlapping assignment.', 1;
    IF EXISTS (SELECT 1 FROM inserted a
        JOIN dbo.StallEquipment e ON e.StallID=a.StallID
        JOIN dbo.TripLeg la ON la.LegID=a.LegID
        JOIN dbo.TransportPlan pa ON pa.PlanID=la.PlanID
        WHERE a.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
        AND pa.PlanStatus NOT IN
            ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
        AND (SELECT COALESCE(SUM(b.SlotQuantity),0)
             FROM dbo.StallAssignment b
             JOIN dbo.TripLeg lb ON lb.LegID=b.LegID
             JOIN dbo.TransportPlan pb ON pb.PlanID=lb.PlanID
             WHERE b.StallID=a.StallID
             AND b.Status NOT IN ('CANCELLED','CANCELED','INACTIVE')
             AND pb.PlanStatus NOT IN
                 ('CANCELLED','CANCELED','SUPERSEDED','REJECTED')
             AND a.AssignedFrom<DATEADD(minute,e.BufferMinutes,b.AssignedTo)
             AND b.AssignedFrom<DATEADD(minute,e.BufferMinutes,a.AssignedTo))
            > e.CapacitySlots)
        THROW 51006, 'Shared stall slot capacity exceeded.', 1;
END;
GO
CREATE OR ALTER TRIGGER dbo.TR_Departure_FirstLegGuard
ON dbo.TripLeg AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE SequenceNo=1
        AND (ActualDeparture IS NOT NULL OR LegStatus='IN_TRANSIT'))
        RETURN;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        LEFT JOIN dbo.StaffProfile m ON m.StaffID=p.DepartureAuthorizedBy
        LEFT JOIN dbo.[User] u ON u.UserID=m.UserID
        LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID
        WHERE i.SequenceNo=1
        AND (i.ActualDeparture IS NOT NULL OR i.LegStatus='IN_TRANSIT')
        AND (i.TransportMode<>'ROAD'
             OR i.ActualDeparture IS NULL
             OR p.FinancialGateStatus<>'PASS'
             OR p.DocGateStatus<>'PASS'
             OR p.OperationalGateStatus<>'PASS'
             OR p.GatesCheckedAt IS NULL
             OR p.DepartureAuthorizedBy IS NULL
             OR p.DepartureAuthorizedAt IS NULL
             OR u.Status<>'ACTIVE'
             OR r.RoleCode<>'MANAGER'
             OR NOT EXISTS (SELECT 1 FROM dbo.HorseReceipt hr
                 WHERE hr.PlanID=i.PlanID AND hr.LegID=i.LegID
                 AND hr.IdentityCheckResult='MATCHED'
                 AND hr.ReceiptStatus='ACCEPTED'
                 AND hr.ReceivedAt<=i.ActualDeparture)))
        THROW 51010, 'First leg departure requires PASS gates, manager authorization and matched horse receipt.', 1;
END;
GO
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 012_departure_gates_horse_receipt.sql';
GO
PRINT N'RUN reapplication: 013_staff_provisioning.sql';
GO
-- F15: additive staff metadata, CAS and private idempotent receipts.
-- Apply through the reviewed deployment process; never on app startup.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.StaffProfile','StaffStatus') IS NULL
    ALTER TABLE dbo.StaffProfile ADD StaffStatus varchar(20) NOT NULL
        CONSTRAINT DF_StaffProfile_StaffStatus DEFAULT 'ACTIVE' WITH VALUES;
IF COL_LENGTH('dbo.StaffProfile','EmployeeCode') IS NULL
    ALTER TABLE dbo.StaffProfile ADD EmployeeCode varchar(50) NULL;
IF COL_LENGTH('dbo.StaffProfile','Department') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Department nvarchar(100) NULL;
IF COL_LENGTH('dbo.StaffProfile','Specialization') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Specialization nvarchar(200) NULL;
IF COL_LENGTH('dbo.StaffProfile','RecordVersion') IS NULL
    ALTER TABLE dbo.StaffProfile ADD RecordVersion rowversion NOT NULL;

IF OBJECT_ID('dbo.CK_StaffProfile_StaffStatus','C') IS NULL
    EXEC('ALTER TABLE dbo.StaffProfile WITH CHECK ADD CONSTRAINT '
        + 'CK_StaffProfile_StaffStatus CHECK '
        + '(StaffStatus IN (''ACTIVE'',''INACTIVE'',''LOCKED''))');
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID('dbo.StaffProfile')
        AND name='UQ_StaffProfile_EmployeeCode')
    EXEC('CREATE UNIQUE INDEX UQ_StaffProfile_EmployeeCode '
        + 'ON dbo.StaffProfile(EmployeeCode) '
        + 'WHERE EmployeeCode IS NOT NULL');

IF OBJECT_ID('dbo.StaffAdminOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.StaffAdminOperation (
        UserID bigint NOT NULL,
        OperationID uniqueidentifier NOT NULL,
        RequestHash char(64) NOT NULL,
        ReceiptJSON nvarchar(max) NOT NULL,
        CreatedAt datetime2(0) NOT NULL
            CONSTRAINT DF_StaffAdminOperation_CreatedAt
                DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_StaffAdminOperation
            PRIMARY KEY (UserID,OperationID),
        CONSTRAINT FK_StaffAdminOperation_User
            FOREIGN KEY (UserID) REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_StaffAdminOperation_JSON
            CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 013_staff_provisioning.sql';
GO
PRINT N'RUN reapplication: 014_leg_execution_welfare_tracking.sql';
GO
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

GO
PRINT N'PASS reapplication: 014_leg_execution_welfare_tracking.sql';
GO
PRINT N'RUN reapplication: 015_incidents_controlled_operational_adjustments.sql';
GO
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

GO
PRINT N'PASS reapplication: 015_incidents_controlled_operational_adjustments.sql';
GO
PRINT N'RUN reapplication: 016_trip_handover_actual_costs.sql';
GO
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

GO
PRINT N'PASS reapplication: 016_trip_handover_actual_costs.sql';
GO
PRINT N'RUN reapplication: 017_refresh_token_rotation.sql';
GO
-- Additive refresh credentials. No raw bearer or cookie value is persisted.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RefreshTokenSession', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RefreshTokenSession (
        TokenID bigint IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_RefreshTokenSession PRIMARY KEY,
        FamilyID uniqueidentifier NOT NULL,
        UserID bigint NOT NULL,
        TokenHash char(64) NOT NULL,
        CredentialHash char(64) NOT NULL,
        IssuedAt datetime2(0) NOT NULL,
        ExpiresAt datetime2(0) NOT NULL,
        RevokedAt datetime2(0) NULL,
        RevokeReason varchar(40) NULL,
        ReplacedByTokenID bigint NULL,
        CONSTRAINT UQ_RefreshTokenSession_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_RefreshTokenSession_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT FK_RefreshTokenSession_Replacement
            FOREIGN KEY (ReplacedByTokenID)
            REFERENCES dbo.RefreshTokenSession(TokenID),
        CONSTRAINT CK_RefreshTokenSession_Expiry
            CHECK (ExpiresAt > IssuedAt)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_User')
    CREATE INDEX IX_RefreshTokenSession_User
        ON dbo.RefreshTokenSession(UserID,RevokedAt,ExpiresAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_Family')
    CREATE INDEX IX_RefreshTokenSession_Family
        ON dbo.RefreshTokenSession(FamilyID);
COMMIT TRANSACTION;

GO
PRINT N'PASS reapplication: 017_refresh_token_rotation.sql';
GO
PRINT N'RUN reapplication: 018_visual_journey_graph_nodes.sql';
GO
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

GO
PRINT N'PASS reapplication: 018_visual_journey_graph_nodes.sql';
GO
PRINT N'RUN reapplication: 019_handover_file_storage.sql';
GO
-- F14 private handover and actual-cost evidence storage.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF @@TRANCOUNT <> 0
    THROW 51019, 'Run migration 019 without an existing transaction.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51019, 'Another schema migration holds the lock.', 1;

IF OBJECT_ID(N'dbo.HandoverFile', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.HandoverFile (
        FileID BIGINT IDENTITY(1,1) NOT NULL,
        FileKey UNIQUEIDENTIFIER NOT NULL,
        OrderID BIGINT NOT NULL,
        UploadedBy BIGINT NOT NULL,
        Purpose VARCHAR(32) NOT NULL,
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash CHAR(64) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL
            CONSTRAINT DF_HandoverFile_CreatedAt DEFAULT (sysutcdatetime()),
        CONSTRAINT PK_HandoverFile PRIMARY KEY CLUSTERED (FileID),
        CONSTRAINT UQ_HandoverFile_FileKey UNIQUE (FileKey),
        CONSTRAINT CK_HandoverFile_Purpose CHECK (Purpose IN (
            'HANDOVER_SIGNATURE', 'HANDOVER_PHOTO',
            'ACTUAL_COST_EVIDENCE')),
        CONSTRAINT CK_HandoverFile_Content CHECK (
            DATALENGTH(ContentBytes) > 0 AND DATALENGTH(ContentBytes)
            <= 5242880)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id=OBJECT_ID(N'dbo.HandoverFile')
        AND name=N'IX_HandoverFile_OrderID')
    CREATE INDEX IX_HandoverFile_OrderID
        ON dbo.HandoverFile(OrderID, Purpose);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_Order')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_Order FOREIGN KEY (OrderID)
        REFERENCES dbo.TransportOrder(OrderID);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_UploadedBy')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_UploadedBy FOREIGN KEY (UploadedBy)
        REFERENCES dbo.[User](UserID);

IF OBJECT_ID(N'dbo.SchemaMigrationHistory', N'U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
        WHERE MigrationID='019_handover_file_storage')
    INSERT dbo.SchemaMigrationHistory(MigrationID)
        VALUES ('019_handover_file_storage');

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO
PRINT N'PASS reapplication: 019_handover_file_storage.sql';
GO
PRINT N'RUN schema after reapplication: verify-f12-f14-schema.sql';
GO
-- Read-only verification, no inferred migration history table.
SET NOCOUNT ON;
DECLARE @fail TABLE (Detail nvarchar(1000));
DECLARE @columns TABLE (T sysname,C sysname,Typ sysname,L int,
    P int,S int,N bit,I bit);
INSERT @columns VALUES
('LegMilestoneLog','MilestoneID','bigint',0,0,0,0,1),
('LegMilestoneLog','LegID','bigint',0,0,0,0,0),
('LegMilestoneLog','ReportedBy','bigint',0,0,0,0,0),
('LegMilestoneLog','MilestoneType','varchar',50,0,0,0,0),
('LegMilestoneLog','OccurredAt','datetime2',0,0,0,0,0),
('LegMilestoneLog','RecordedAt','datetime2',0,0,0,0,0),
('LegMilestoneLog','Latitude','decimal',0,9,6,1,0),
('LegMilestoneLog','Longitude','decimal',0,9,6,1,0),
('LegMilestoneLog','Notes','nvarchar',4000,0,0,1,0),
('LegMilestoneLog','SourceCode','varchar',30,0,0,1,0),
('LegMilestoneLog','SourceReference','nvarchar',1000,0,0,1,0),
('LegMilestoneLog','LocationDescription','nvarchar',1000,0,0,1,0),
('LegMilestoneLog','EstimatedArrivalAt','datetime2',0,0,0,1,0),
('LegMilestoneLog','DelayReason','nvarchar',2000,0,0,1,0),
('LegMilestoneLog','EvidenceFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','WelfareLogID','bigint',0,0,0,0,1),
('HorseWelfareLog','LegID','bigint',0,0,0,0,0),
('HorseWelfareLog','RecordedBy','bigint',0,0,0,0,0),
('HorseWelfareLog','CheckTime','datetime2',0,0,0,0,0),
('HorseWelfareLog','HealthStatus','varchar',50,0,0,0,0),
('HorseWelfareLog','TemperatureCelsius','decimal',0,8,2,1,0),
('HorseWelfareLog','WaterIntakeLiters','decimal',0,8,2,1,0),
('HorseWelfareLog','BehaviorNotes','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','PhotoFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','FeedAmountKg','decimal',0,8,2,1,0),
('HorseWelfareLog','CareNotes','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','InstructionFileKey','nvarchar',2048,0,0,1,0),
('HorseWelfareLog','SupersedesLogID','bigint',0,0,0,1,0),
('IncidentReport','IncidentID','bigint',0,0,0,0,1),
('IncidentReport','LegID','bigint',0,0,0,0,0),
('IncidentReport','ReportedBy','bigint',0,0,0,0,0),
('IncidentReport','IncidentType','varchar',50,0,0,0,0),
('IncidentReport','SeverityLevel','varchar',50,0,0,0,0),
('IncidentReport','IncidentTime','datetime2',0,0,0,0,0),
('IncidentReport','Description','nvarchar',4000,0,0,0,0),
('IncidentReport','Status','varchar',50,0,0,0,0),
('IncidentReport','ImmediateActionNotes','nvarchar',4000,0,0,1,0),
('IncidentReport','EmergencyContact','nvarchar',1000,0,0,1,0),
('IncidentReport','AcknowledgedBy','bigint',0,0,0,1,0),
('IncidentReport','AcknowledgedAt','datetime2',0,0,0,1,0),
('IncidentReport','ResolvedBy','bigint',0,0,0,1,0),
('IncidentReport','ResolvedAt','datetime2',0,0,0,1,0),
('IncidentReport','ResolutionNotes','nvarchar',4000,0,0,1,0),
('IncidentReport','EvidenceFilesJSON','nvarchar',-1,0,0,1,0),
('OrderChangeRequest','ChangeRequestID','bigint',0,0,0,0,1),
('OrderChangeRequest','OrderID','bigint',0,0,0,0,0),
('OrderChangeRequest','BaseRevisionID','bigint',0,0,0,1,0),
('OrderChangeRequest','RequestedBy','bigint',0,0,0,0,0),
('OrderChangeRequest','ChangeType','varchar',30,0,0,0,0),
('OrderChangeRequest','Reason','nvarchar',4000,0,0,0,0),
('OrderChangeRequest','RequestedChangesJSON','nvarchar',-1,0,0,0,0),
('OrderChangeRequest','AttachmentFileKey','nvarchar',2048,0,0,1,0),
('OrderChangeRequest','Status','varchar',30,0,0,0,0),
('OrderChangeRequest','CreatedAt','datetime2',0,0,0,0,0),
('OrderChangeRequest','RespondedBy','bigint',0,0,0,1,0),
('OrderChangeRequest','RespondedAt','datetime2',0,0,0,1,0),
('OrderChangeRequest','ResponseReason','nvarchar',4000,0,0,1,0),
('OperationalAdjustment','AdjustmentID','bigint',0,0,0,0,1),
('OperationalAdjustment','PlanID','bigint',0,0,0,0,0),
('OperationalAdjustment','IncidentID','bigint',0,0,0,1,0),
('OperationalAdjustment','ProposedBy','bigint',0,0,0,0,0),
('OperationalAdjustment','ApprovedBy','bigint',0,0,0,1,0),
('OperationalAdjustment','ProposedChanges','nvarchar',-1,0,0,0,0),
('OperationalAdjustment','AdditionalCostEstimate','decimal',0,18,2,0,0),
('OperationalAdjustment','ApprovalStatus','varchar',50,0,0,0,0),
('OperationalAdjustment','ProposedAt','datetime2',0,0,0,0,0),
('OperationalAdjustment','DecidedAt','datetime2',0,0,0,1,0),
('OperationalAdjustment','ChangeRequestID','bigint',0,0,0,1,0),
('OperationalAdjustment','CustomerDecision','varchar',20,0,0,1,0),
('OperationalAdjustment','RespondedByCustomerID','bigint',0,0,0,1,0),
('OperationalAdjustment','CustomerRespondedAt','datetime2',0,0,0,1,0),
('OperationalAdjustment','CustomerResponseNotes','nvarchar',4000,0,0,1,0),
('TripHandover','HandoverID','bigint',0,0,0,0,1),
('TripHandover','LegID','bigint',0,0,0,0,0),
('TripHandover','RecordedBy','bigint',0,0,0,0,0),
('TripHandover','HandoverType','varchar',50,0,0,0,0),
('TripHandover','HandoverTime','datetime2',0,0,0,0,0),
('TripHandover','ReceivedByName','nvarchar',600,0,0,0,0),
('TripHandover','ReceiverAuthority','varchar',50,0,0,1,0),
('TripHandover','ReceiverSignatureFileKey','nvarchar',2048,0,0,1,0),
('TripHandover','HorseConditionStatus','varchar',50,0,0,0,0),
('TripHandover','Outcome','varchar',50,0,0,0,0),
('TripHandover','ExceptionNotes','nvarchar',4000,0,0,1,0),
('TripHandover','PhotoFileKey','nvarchar',2048,0,0,1,0),
('TripHandover','CustomerConfirmedAt','datetime2',0,0,0,1,0),
('TripHandover','LocationDescription','nvarchar',1000,0,0,1,0),
('TripHandover','CustomerDecision','varchar',20,0,0,1,0),
('TripHandover','CustomerFeedback','nvarchar',4000,0,0,1,0),
('TripHandover','CustomerRespondedAt','datetime2',0,0,0,1,0),
('TripHandover','RespondedByCustomerID','bigint',0,0,0,1,0),
('TripHandover','EvidenceFilesJSON','nvarchar',-1,0,0,1,0),
('ActualCost','CostID','bigint',0,0,0,0,1),
('ActualCost','PlanID','bigint',0,0,0,0,0),
('ActualCost','LegID','bigint',0,0,0,1,0),
('ActualCost','CostCode','varchar',50,0,0,0,0),
('ActualCost','Amount','decimal',0,18,2,0,0),
('ActualCost','Currency','varchar',3,0,0,0,0),
('ActualCost','IncurredAt','datetime2',0,0,0,0,0),
('ActualCost','Description','nvarchar',2000,0,0,1,0),
('ActualCost','RecordedBy','bigint',0,0,0,1,0),
('ActualCost','EvidenceFileKey','nvarchar',2048,0,0,1,0);
INSERT @columns VALUES
('LegMilestoneLog','PlanID','bigint',0,0,0,0,0),
('LegMilestoneLog','IsCustomerVisible','bit',0,0,0,0,0),
('LegMilestoneLog','PublicNote','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','PlanID','bigint',0,0,0,0,0),
('HorseWelfareLog','IsCustomerVisible','bit',0,0,0,0,0),
('HorseWelfareLog','PublicNote','nvarchar',4000,0,0,1,0),
('HorseWelfareLog','RecordedAt','datetime2',0,0,0,0,0),
('HorseWelfareLog','SourceCode','varchar',30,0,0,1,0),
('HorseWelfareLog','SourceReference','nvarchar',1000,0,0,1,0);
INSERT @fail
SELECT CONCAT(e.T,'.',e.C,' missing/type/nullability/identity mismatch')
FROM @columns e LEFT JOIN sys.columns c
    ON c.object_id=OBJECT_ID('dbo.'+e.T) AND c.name=e.C
LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
WHERE c.column_id IS NULL OR t.name<>e.Typ OR c.is_nullable<>e.N
    OR c.is_identity<>e.I OR (e.L<>0 AND c.max_length<>e.L)
    OR (e.P<>0 AND c.precision<>e.P) OR c.scale<>e.S;
INSERT @fail
SELECT CONCAT(t.name,'.',c.name,' unexpected column')
FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
WHERE t.name IN (SELECT DISTINCT T FROM @columns)
    AND t.schema_id=SCHEMA_ID('dbo')
    AND NOT EXISTS(SELECT 1 FROM @columns e WHERE e.T=t.name AND e.C=c.name);
DECLARE @keys TABLE (T sysname,N sysname,C nvarchar(500),K varchar(20));
INSERT @keys VALUES
('LegMilestoneLog','PK_LegMilestoneLog','MilestoneID','PRIMARY KEY'),
('LegMilestoneLog','IX_LegMilestoneLog_LegID','LegID','INDEX'),
('LegMilestoneLog','IX_LegMilestoneLog_ReportedBy','ReportedBy','INDEX'),
('HorseWelfareLog','PK_HorseWelfareLog','WelfareLogID','PRIMARY KEY'),
('HorseWelfareLog','IX_HorseWelfareLog_LegID','LegID','INDEX'),
('HorseWelfareLog','IX_HorseWelfareLog_RecordedBy','RecordedBy','INDEX'),
('HorseWelfareLog','UQ_Figma_HorseWelfareLog_Parent','WelfareLogID,LegID','UNIQUE'),
('IncidentReport','PK_IncidentReport','IncidentID','PRIMARY KEY'),
('IncidentReport','IX_IncidentReport_LegID','LegID','INDEX'),
('IncidentReport','IX_IncidentReport_ReportedBy','ReportedBy','INDEX'),
('OrderChangeRequest','PK_OrderChangeRequest','ChangeRequestID','PRIMARY KEY'),
('OperationalAdjustment','PK_OperationalAdjustment','AdjustmentID','PRIMARY KEY'),
('OperationalAdjustment','IX_OperationalAdjustment_PlanID','PlanID','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_IncidentID','IncidentID','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_ProposedBy','ProposedBy','INDEX'),
('OperationalAdjustment','IX_OperationalAdjustment_ApprovedBy','ApprovedBy','INDEX'),
('TripHandover','PK_TripHandover','HandoverID','PRIMARY KEY'),
('TripHandover','IX_TripHandover_LegID','LegID','INDEX'),
('TripHandover','IX_TripHandover_RecordedBy','RecordedBy','INDEX'),
('ActualCost','PK_ActualCost','CostID','PRIMARY KEY'),
('ActualCost','IX_ActualCost_PlanID','PlanID','INDEX'),
('ActualCost','IX_ActualCost_LegID','LegID','INDEX');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/disabled/key mismatch')
FROM @keys e LEFT JOIN sys.indexes i
    ON i.object_id=OBJECT_ID('dbo.'+e.T) AND i.name=e.N
OUTER APPLY(SELECT STRING_AGG(CAST(COL_NAME(ic.object_id,ic.column_id)
    AS nvarchar(max)),',') WITHIN GROUP(ORDER BY ic.key_ordinal) AS Keys
    FROM sys.index_columns ic WHERE ic.object_id=i.object_id
    AND ic.index_id=i.index_id AND ic.key_ordinal>0) k
WHERE i.index_id IS NULL OR i.is_disabled=1 OR k.Keys<>e.C
    OR (e.K='PRIMARY KEY' AND i.is_primary_key=0)
    OR (e.K='UNIQUE' AND i.is_unique=0);
DECLARE @fk TABLE (T sysname,N sysname,C nvarchar(500),
    R sysname,RC nvarchar(500));
INSERT @fk VALUES
('LegMilestoneLog','FK_LegMilestoneLog_LegID','LegID','TripLeg','LegID'),
('LegMilestoneLog','FK_LegMilestoneLog_ReportedBy','ReportedBy','StaffProfile','StaffID'),
('HorseWelfareLog','FK_Figma_HorseWelfareLog_SupersedesLogID','SupersedesLogID,LegID','HorseWelfareLog','WelfareLogID,LegID'),
('HorseWelfareLog','FK_HorseWelfareLog_LegID','LegID','TripLeg','LegID'),
('HorseWelfareLog','FK_HorseWelfareLog_RecordedBy','RecordedBy','StaffProfile','StaffID'),
('IncidentReport','FK_Figma_IncidentReport_AcknowledgedBy','AcknowledgedBy','StaffProfile','StaffID'),
('IncidentReport','FK_Figma_IncidentReport_ResolvedBy','ResolvedBy','StaffProfile','StaffID'),
('IncidentReport','FK_IncidentReport_LegID','LegID','TripLeg','LegID'),
('IncidentReport','FK_IncidentReport_ReportedBy','ReportedBy','StaffProfile','StaffID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_BaseRevisionID','BaseRevisionID,OrderID','OrderRevision','RevisionID,OrderID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_OrderID','OrderID','TransportOrder','OrderID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_RequestedBy','RequestedBy','User','UserID'),
('OrderChangeRequest','FK_Figma_OrderChangeRequest_RespondedBy','RespondedBy','StaffProfile','StaffID'),
('OperationalAdjustment','FK_Figma_OperationalAdjustment_ChangeRequestID','ChangeRequestID','OrderChangeRequest','ChangeRequestID'),
('OperationalAdjustment','FK_Figma_OperationalAdjustment_RespondedByCustomerID','RespondedByCustomerID','Customer','CustomerID'),
('OperationalAdjustment','FK_OperationalAdjustment_ApprovedBy','ApprovedBy','StaffProfile','StaffID'),
('OperationalAdjustment','FK_OperationalAdjustment_IncidentID','IncidentID','IncidentReport','IncidentID'),
('OperationalAdjustment','FK_OperationalAdjustment_PlanID','PlanID','TransportPlan','PlanID'),
('OperationalAdjustment','FK_OperationalAdjustment_ProposedBy','ProposedBy','StaffProfile','StaffID'),
('TripHandover','FK_Figma_TripHandover_RespondedByCustomerID','RespondedByCustomerID','Customer','CustomerID'),
('TripHandover','FK_TripHandover_LegID','LegID','TripLeg','LegID'),
('TripHandover','FK_TripHandover_RecordedBy','RecordedBy','StaffProfile','StaffID'),
('ActualCost','FK_ActualCost_LegID','LegID','TripLeg','LegID'),
('ActualCost','FK_ActualCost_PlanID','PlanID','TransportPlan','PlanID'),
('ActualCost','FK_Figma_ActualCost_LegID','LegID,PlanID','TripLeg','LegID,PlanID'),
('ActualCost','FK_Figma_ActualCost_RecordedBy','RecordedBy','StaffProfile','StaffID');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/untrusted/composite FK mismatch')
FROM @fk e LEFT JOIN sys.foreign_keys f
    ON f.parent_object_id=OBJECT_ID('dbo.'+e.T) AND f.name=e.N
OUTER APPLY(SELECT STRING_AGG(CAST(COL_NAME(x.parent_object_id,
        x.parent_column_id) AS nvarchar(max)),',')
        WITHIN GROUP(ORDER BY x.constraint_column_id) AS C,
    STRING_AGG(CAST(COL_NAME(x.referenced_object_id,x.referenced_column_id)
        AS nvarchar(max)),',')
        WITHIN GROUP(ORDER BY x.constraint_column_id) AS RC
    FROM sys.foreign_key_columns x WHERE x.constraint_object_id=f.object_id) m
WHERE f.object_id IS NULL OR f.is_disabled=1 OR f.is_not_trusted=1
    OR OBJECT_NAME(f.referenced_object_id)<>e.R OR m.C<>e.C OR m.RC<>e.RC
    OR f.delete_referential_action<>0 OR f.update_referential_action<>0;
DECLARE @ck TABLE (T sysname,N sysname,D nvarchar(max));
INSERT @ck VALUES
('LegMilestoneLog','CK_LegMilestoneLog_Latitude',N'([Latitude]>=(-90) AND [Latitude]<=(90))'),
('LegMilestoneLog','CK_LegMilestoneLog_Longitude',N'([Longitude]>=(-180) AND [Longitude]<=(180))'),
('HorseWelfareLog','CK_Figma_HorseWelfareLog_FeedAmountKg',N'([FeedAmountKg]>=(0))'),
('HorseWelfareLog','CK_Figma_HorseWelfareLog_Supersedes',N'([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID])'),
('HorseWelfareLog','CK_HorseWelfareLog_WaterIntakeLiters',N'([WaterIntakeLiters]>=(0))'),
('IncidentReport','CK_Figma_IncidentReport_EvidenceFilesJSON',N'([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))'),
('OrderChangeRequest','CK_OrderChangeRequest_JSON',N'(isjson([RequestedChangesJSON])=(1))'),
('OrderChangeRequest','CK_OrderChangeRequest_Reason',N'(len(ltrim(rtrim([Reason])))>(0))'),
('OperationalAdjustment','CK_Figma_OperationalAdjustment_CustomerDecision',N'([CustomerDecision]=''REJECTED'' OR [CustomerDecision]=''ACCEPTED'' OR [CustomerDecision]=''PENDING'')'),
('OperationalAdjustment','CK_OperationalAdjustment_AdditionalCostEstimate',N'([AdditionalCostEstimate]>=(0))'),
('OperationalAdjustment','CK_OperationalAdjustment_ProposedChanges_JSON',N'(isjson([ProposedChanges])=(1))'),
('TripHandover','CK_Figma_TripHandover_CustomerDecision',N'([CustomerDecision]=''NOT_DELIVERED'' OR [CustomerDecision]=''EXCEPTION'' OR [CustomerDecision]=''ACCEPTED'')'),
('TripHandover','CK_Figma_TripHandover_EvidenceFilesJSON',N'([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1))'),
('ActualCost','CK_ActualCost_Amount',N'([Amount]>=(0))');
INSERT @fail
SELECT CONCAT(e.T,'.',e.N,' missing/untrusted/check definition mismatch')
FROM @ck e LEFT JOIN sys.check_constraints c
    ON c.parent_object_id=OBJECT_ID('dbo.'+e.T) AND c.name=e.N
WHERE c.object_id IS NULL OR c.is_disabled=1 OR c.is_not_trusted=1
    OR LOWER(REPLACE(REPLACE(REPLACE(c.definition,CHAR(13),''),
        CHAR(10),''),' ',''))<>LOWER(REPLACE(e.D,' ',''));
IF OBJECT_ID('dbo.ActualCost','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ActualCost');
IF OBJECT_ID('dbo.AuditEvent','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuditEvent');
IF OBJECT_ID('dbo.AuthenticationToken','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuthenticationToken');
IF OBJECT_ID('dbo.AuthorityProcedure','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table AuthorityProcedure');
IF OBJECT_ID('dbo.ComplianceClearance','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ComplianceClearance');
IF OBJECT_ID('dbo.ComplianceRuleVersion','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ComplianceRuleVersion');
IF OBJECT_ID('dbo.Customer','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Customer');
IF OBJECT_ID('dbo.DocumentRequirement','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentRequirement');
IF OBJECT_ID('dbo.DocumentReview','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentReview');
IF OBJECT_ID('dbo.DocumentSubmission','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentSubmission');
IF OBJECT_ID('dbo.DocumentTypeCatalog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentTypeCatalog');
IF OBJECT_ID('dbo.DocumentVersion','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table DocumentVersion');
IF OBJECT_ID('dbo.FleetVehicle','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table FleetVehicle');
IF OBJECT_ID('dbo.FlightReservation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table FlightReservation');
IF OBJECT_ID('dbo.Horse','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Horse');
IF OBJECT_ID('dbo.HorseDocument','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseDocument');
IF OBJECT_ID('dbo.HorseReceipt','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseReceipt');
IF OBJECT_ID('dbo.HorseWelfareLog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table HorseWelfareLog');
IF OBJECT_ID('dbo.IncidentReport','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table IncidentReport');
IF OBJECT_ID('dbo.InsuranceChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table InsuranceChoice');
IF OBJECT_ID('dbo.InsurancePolicy','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table InsurancePolicy');
IF OBJECT_ID('dbo.LegMilestoneLog','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table LegMilestoneLog');
IF OBJECT_ID('dbo.Notification','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Notification');
IF OBJECT_ID('dbo.OperationalAdjustment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OperationalAdjustment');
IF OBJECT_ID('dbo.OrderAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderAssignment');
IF OBJECT_ID('dbo.OrderChangeRequest','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderChangeRequest');
IF OBJECT_ID('dbo.OrderDecision','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderDecision');
IF OBJECT_ID('dbo.OrderRevision','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderRevision');
IF OBJECT_ID('dbo.OrderServiceChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table OrderServiceChoice');
IF OBJECT_ID('dbo.PaymentAllocation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentAllocation');
IF OBJECT_ID('dbo.PaymentReport','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentReport');
IF OBJECT_ID('dbo.PaymentRequest','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentRequest');
IF OBJECT_ID('dbo.PaymentTransaction','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table PaymentTransaction');
IF OBJECT_ID('dbo.QuarantineChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuarantineChoice');
IF OBJECT_ID('dbo.QuarantineReservation','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuarantineReservation');
IF OBJECT_ID('dbo.Quote','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Quote');
IF OBJECT_ID('dbo.QuoteItem','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuoteItem');
IF OBJECT_ID('dbo.QuotePaymentSchedule','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table QuotePaymentSchedule');
IF OBJECT_ID('dbo.Refund','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Refund');
IF OBJECT_ID('dbo.RevisionQuarantineAssessment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table RevisionQuarantineAssessment');
IF OBJECT_ID('dbo.RevisionVeterinaryAssessment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table RevisionVeterinaryAssessment');
IF OBJECT_ID('dbo.Role','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Role');
IF OBJECT_ID('dbo.ServiceTariff','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table ServiceTariff');
IF OBJECT_ID('dbo.StaffProfile','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StaffProfile');
IF OBJECT_ID('dbo.StallAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallAssignment');
IF OBJECT_ID('dbo.StallChoice','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallChoice');
IF OBJECT_ID('dbo.StallEquipment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallEquipment');
IF OBJECT_ID('dbo.StallFitPolicy','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table StallFitPolicy');
IF OBJECT_ID('dbo.Supplier','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table Supplier');
IF OBJECT_ID('dbo.SupplierOffer','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table SupplierOffer');
IF OBJECT_ID('dbo.TransportOrder','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportOrder');
IF OBJECT_ID('dbo.TransportOrderDetail','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportOrderDetail');
IF OBJECT_ID('dbo.TransportPlan','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TransportPlan');
IF OBJECT_ID('dbo.TripAssignment','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripAssignment');
IF OBJECT_ID('dbo.TripHandover','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripHandover');
IF OBJECT_ID('dbo.TripLeg','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table TripLeg');
IF OBJECT_ID('dbo.User','U') IS NULL
    INSERT @fail VALUES ('Missing baseline table User');
IF EXISTS(SELECT 1 FROM @fail)
BEGIN
    SELECT Detail FROM @fail ORDER BY Detail;
    THROW 51014,'Schema verification failed; inspect metadata differences.',1;
END;
SELECT e.T AS TableName,COUNT(*) AS VerifiedColumns,'PASS' AS Status
FROM @columns e GROUP BY e.T ORDER BY e.T;

GO
PRINT N'PASS schema after reapplication: verify-f12-f14-schema.sql';
GO
SET XACT_ABORT ON;
IF @@TRANCOUNT <> 0 THROW 51020, 'Unexpected transaction left open.', 1;
IF OBJECT_ID(N'dbo.RefreshTokenSession',N'U') IS NULL
    THROW 51020, 'Migration 017 table missing.', 1;
IF OBJECT_ID(N'dbo.PlanCheckpointNode',N'U') IS NULL
    THROW 51020, 'Migration 018 table missing.', 1;
IF OBJECT_ID(N'dbo.HandoverFile',N'U') IS NULL
    THROW 51020, 'Migration 019 table missing.', 1;
IF COL_LENGTH(N'dbo.HandoverFile',N'ContentBytes') IS NULL
    OR COL_LENGTH(N'dbo.HandoverFile',N'FileHash') IS NULL
    THROW 51020, 'Migration 019 storage columns missing.', 1;
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1)
    THROW 51020, 'Disabled or untrusted foreign key.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1)
    THROW 51020, 'Disabled or untrusted CHECK constraint.', 1;
IF EXISTS (SELECT 1 FROM sys.triggers WHERE parent_class=1 AND is_disabled=1)
    THROW 51020, 'Disabled table trigger.', 1;
CREATE TABLE #ConstraintViolations (
    [Table] nvarchar(517), [Constraint] nvarchar(517), [Where] nvarchar(max));
INSERT INTO #ConstraintViolations
    EXEC(N'DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS, ALL_ERRORMSGS');
IF EXISTS (SELECT 1 FROM #ConstraintViolations)
BEGIN
    SELECT * FROM #ConstraintViolations;
    THROW 51020, 'DBCC found constraint violations.', 1;
END;
DROP TABLE #ConstraintViolations;
SELECT N'PASS: native migrations 000-019, schema contract, graph CRUD and rejection probes, reapplication, trusted constraints' AS TestResult,
       @@SERVERNAME AS ServerName, DB_NAME() AS DatabaseName,
       (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0) AS TableCount,
       SYSUTCDATETIME() AS FinishedAtUtc;
SELECT name AS LogicalName, physical_name AS PhysicalPath FROM sys.database_files;
PRINT N'ALL SQL SMOKE CHECKS PASSED. Save Results and Messages as execution evidence.';
GO
