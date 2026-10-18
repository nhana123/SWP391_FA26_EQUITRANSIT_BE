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
