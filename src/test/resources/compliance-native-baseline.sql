-- Schema-only fixture extracted from EquiTransit.sql; no production data.
CREATE TABLE [dbo].[ActualCost](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[AuditEvent](
	[EventID] [bigint] IDENTITY(1,1) NOT NULL,
	[UserID] [bigint] NULL,
	[EntityType] [varchar](50) NOT NULL,
	[EntityID] [bigint] NOT NULL,
	[ActionCode] [varchar](50) NOT NULL,
	[ChangeSnapshot] [nvarchar](max) NULL,
	[OccurredAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_AuditEvent] PRIMARY KEY CLUSTERED 
(
	[EventID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[AuthenticationToken](
	[TokenID] [bigint] IDENTITY(1,1) NOT NULL,
	[UserID] [bigint] NOT NULL,
	[TokenHash] [varchar](64) NOT NULL,
	[Purpose] [varchar](20) NOT NULL,
	[ExpiresAt] [datetime2](0) NOT NULL,
	[UsedAt] [datetime2](0) NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_AuthenticationToken] PRIMARY KEY CLUSTERED 
(
	[TokenID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[AuthorityProcedure](
	[ProcedureID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[ProcedureCode] [varchar](50) NOT NULL,
	[AuthorityName] [nvarchar](300) NOT NULL,
	[ApplicationReference] [varchar](150) NULL,
	[ApprovalStatus] [varchar](50) NOT NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
	[DecidedAt] [datetime2](0) NULL,
	[PhaseCode] [varchar](50) NULL,
	[AuthorityContact] [nvarchar](500) NULL,
	[HoldReason] [nvarchar](2000) NULL,
	[ExpiresAt] [datetime2](0) NULL,
	[RecordedBy] [bigint] NULL,
 CONSTRAINT [PK_AuthorityProcedure] PRIMARY KEY CLUSTERED 
(
	[ProcedureID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[ComplianceClearance](
	[ClearanceID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[SpecialistID] [bigint] NOT NULL,
	[PhaseCode] [varchar](50) NOT NULL,
	[Status] [varchar](50) NOT NULL,
	[CheckedAt] [datetime2](0) NOT NULL,
	[ExpiresAt] [datetime2](0) NULL,
	[AssessmentNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_ComplianceClearance] PRIMARY KEY CLUSTERED 
(
	[ClearanceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[ComplianceRuleVersion](
	[RuleID] [bigint] IDENTITY(1,1) NOT NULL,
	[RuleCode] [varchar](50) NOT NULL,
	[VersionNo] [int] NOT NULL,
	[OriginCountryCode] [varchar](2) NOT NULL,
	[DestinationCountryCode] [varchar](2) NOT NULL,
	[DocumentType] [varchar](50) NOT NULL,
	[PhaseCode] [varchar](50) NOT NULL,
	[SourceURL] [nvarchar](2048) NOT NULL,
	[ReviewedAt] [datetime2](0) NULL,
	[EffectiveFrom] [datetime2](0) NOT NULL,
	[EffectiveTo] [datetime2](0) NULL,
	[TransitCountryCode] [varchar](2) NULL,
	[RuleDescription] [nvarchar](2000) NULL,
 CONSTRAINT [PK_ComplianceRuleVersion] PRIMARY KEY CLUSTERED 
(
	[RuleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Customer](
	[CustomerID] [bigint] IDENTITY(1,1) NOT NULL,
	[UserID] [bigint] NOT NULL,
	[FullName] [nvarchar](150) NOT NULL,
	[Phone] [varchar](30) NULL,
	[Address] [nvarchar](500) NULL,
	[EmailNotificationsEnabled] [bit] NULL,
 CONSTRAINT [PK_Customer] PRIMARY KEY CLUSTERED 
(
	[CustomerID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[DocumentRequirement](
	[RequirementID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[RuleID] [bigint] NOT NULL,
	[DocumentType] [varchar](50) NOT NULL,
	[PhaseCode] [varchar](50) NOT NULL,
	[IsMandatory] [bit] NOT NULL,
	[DueAt] [datetime2](0) NULL,
	[Status] [varchar](50) NOT NULL,
	[Instructions] [nvarchar](2000) NULL,
	[ScopeCode] [varchar](20) NULL,
	[HorseID] [bigint] NULL,
	[ValidityConditionsJSON] [nvarchar](max) NULL,
	[CaseJustification] [nvarchar](2000) NULL,
 CONSTRAINT [PK_DocumentRequirement] PRIMARY KEY CLUSTERED 
(
	[RequirementID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[DocumentReview](
	[ReviewID] [bigint] IDENTITY(1,1) NOT NULL,
	[SubmissionID] [bigint] NOT NULL,
	[ReviewedBy] [bigint] NOT NULL,
	[Decision] [varchar](50) NOT NULL,
	[Feedback] [nvarchar](2000) NULL,
	[ReviewedAt] [datetime2](0) NULL,
 CONSTRAINT [PK_DocumentReview] PRIMARY KEY CLUSTERED 
(
	[ReviewID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[DocumentSubmission](
	[SubmissionID] [bigint] IDENTITY(1,1) NOT NULL,
	[RequirementID] [bigint] NOT NULL,
	[DocumentVersionID] [bigint] NOT NULL,
	[Status] [varchar](50) NOT NULL,
	[SubmittedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_DocumentSubmission] PRIMARY KEY CLUSTERED 
(
	[SubmissionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[DocumentTypeCatalog](
	[TypeCode] [varchar](50) NOT NULL,
	[DisplayName] [nvarchar](200) NOT NULL,
	[Description] [nvarchar](1000) NULL,
	[IsActive] [bit] NOT NULL,
 CONSTRAINT [PK_DocumentTypeCatalog] PRIMARY KEY CLUSTERED 
(
	[TypeCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[DocumentVersion](
	[DocumentVersionID] [bigint] IDENTITY(1,1) NOT NULL,
	[DocumentID] [bigint] NOT NULL,
	[VersionNo] [int] NOT NULL,
	[DocumentNumber] [varchar](50) NULL,
	[FileKey] [nvarchar](1024) NOT NULL,
	[FileHash] [varchar](128) NULL,
	[IssuedDate] [date] NULL,
	[ExpiryDate] [date] NULL,
	[UploadedAt] [datetime2](0) NOT NULL,
	[UploadedBy] [bigint] NULL,
	[OriginalFileName] [nvarchar](255) NULL,
	[ContentType] [varchar](100) NULL,
	[FileSizeBytes] [bigint] NULL,
 CONSTRAINT [PK_DocumentVersion] PRIMARY KEY CLUSTERED 
(
	[DocumentVersionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[FleetVehicle](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[FlightReservation](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Horse](
	[HorseID] [bigint] IDENTITY(1,1) NOT NULL,
	[CustomerID] [bigint] NOT NULL,
	[MicrochipID] [varchar](50) NULL,
	[PassportNumber] [varchar](50) NULL,
	[Name] [nvarchar](150) NOT NULL,
	[Breed] [nvarchar](100) NULL,
	[DateOfBirth] [date] NULL,
	[SexCode] [varchar](20) NULL,
	[WeightKg] [decimal](8, 2) NULL,
	[HeightCm] [decimal](6, 2) NULL,
	[BodyWidthCm] [decimal](6, 2) NULL,
	[MeasuredAt] [datetime2](0) NULL,
	[SpecialCareNotes] [nvarchar](1000) NULL,
	[HandlingNotes] [nvarchar](1000) NULL,
	[FeedWaterRequirements] [nvarchar](1000) NULL,
	[StallPreferences] [nvarchar](1000) NULL,
	[VeterinaryInstructions] [nvarchar](2000) NULL,
	[VeterinaryInstructionSource] [nvarchar](1000) NULL,
	[RecordVersion] [timestamp] NOT NULL,
 CONSTRAINT [PK_Horse] PRIMARY KEY CLUSTERED 
(
	[HorseID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[HorseDocument](
	[DocumentID] [bigint] IDENTITY(1,1) NOT NULL,
	[HorseID] [bigint] NOT NULL,
	[DocumentType] [varchar](50) NOT NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_HorseDocument] PRIMARY KEY CLUSTERED 
(
	[DocumentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[HorseReceipt](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[HorseWelfareLog](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[IncidentReport](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[InsuranceChoice](
	[InsuranceChoiceID] [bigint] IDENTITY(1,1) NOT NULL,
	[ChoiceID] [bigint] NOT NULL,
	[CoverageCode] [varchar](50) NOT NULL,
	[DeclaredValue] [decimal](18, 2) NULL,
	[ValueCurrency] [varchar](3) NULL,
	[ExistingPolicyReference] [nvarchar](100) NULL,
 CONSTRAINT [PK_InsuranceChoice] PRIMARY KEY CLUSTERED 
(
	[InsuranceChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[InsurancePolicy](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[LegMilestoneLog](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Notification](
	[NotificationID] [bigint] IDENTITY(1,1) NOT NULL,
	[UserID] [bigint] NOT NULL,
	[OrderID] [bigint] NULL,
	[Title] [nvarchar](300) NOT NULL,
	[Message] [nvarchar](2000) NOT NULL,
	[ReadAt] [datetime2](0) NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Notification] PRIMARY KEY CLUSTERED 
(
	[NotificationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[OperationalAdjustment](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[OrderAssignment](
	[AssignmentID] [bigint] IDENTITY(1,1) NOT NULL,
	[OrderID] [bigint] NOT NULL,
	[StaffID] [bigint] NOT NULL,
	[AssignmentRole] [varchar](30) NOT NULL,
	[AssignedAt] [datetime2](0) NOT NULL,
	[EndedAt] [datetime2](0) NULL,
 CONSTRAINT [PK_OrderAssignment] PRIMARY KEY CLUSTERED 
(
	[AssignmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[OrderChangeRequest](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[OrderDecision](
	[DecisionID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[ManagerID] [bigint] NOT NULL,
	[Decision] [varchar](20) NOT NULL,
	[Reason] [nvarchar](1000) NULL,
	[DecidedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_OrderDecision] PRIMARY KEY CLUSTERED 
(
	[DecisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[OrderRevision](
	[RevisionID] [bigint] IDENTITY(1,1) NOT NULL,
	[OrderID] [bigint] NOT NULL,
	[RevisionNo] [int] NOT NULL,
	[OrderSnapshot] [nvarchar](max) NOT NULL,
	[ChangeReason] [nvarchar](500) NULL,
	[SubmittedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_OrderRevision] PRIMARY KEY CLUSTERED 
(
	[RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[OrderServiceChoice](
	[ChoiceID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[ServiceCode] [varchar](50) NOT NULL,
	[OptionCode] [varchar](50) NOT NULL,
	[ArrangedBy] [varchar](20) NOT NULL,
	[ChosenAt] [datetime2](0) NOT NULL,
	[SelectionInputsJSON] [nvarchar](max) NULL,
	[ProviderName] [nvarchar](200) NULL,
	[ProviderContact] [nvarchar](500) NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
 CONSTRAINT [PK_OrderServiceChoice] PRIMARY KEY CLUSTERED 
(
	[ChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[PaymentAllocation](
	[AllocationID] [bigint] IDENTITY(1,1) NOT NULL,
	[TransactionID] [bigint] NOT NULL,
	[PaymentRequestID] [bigint] NOT NULL,
	[AllocatedAmount] [decimal](18, 2) NOT NULL,
	[AllocatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_PaymentAllocation] PRIMARY KEY CLUSTERED 
(
	[AllocationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[PaymentReport](
	[ReportID] [bigint] IDENTITY(1,1) NOT NULL,
	[PaymentRequestID] [bigint] NOT NULL,
	[ReportedAmount] [decimal](18, 2) NOT NULL,
	[BankReference] [varchar](150) NULL,
	[ProofFileKey] [nvarchar](1024) NULL,
	[CustomerReportedAt] [datetime2](0) NOT NULL,
	[Status] [varchar](50) NOT NULL,
	[TransferredAt] [datetime2](0) NULL,
	[ReportedCurrency] [varchar](3) NULL,
 CONSTRAINT [PK_PaymentReport] PRIMARY KEY CLUSTERED 
(
	[ReportID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[PaymentRequest](
	[PaymentRequestID] [bigint] IDENTITY(1,1) NOT NULL,
	[QuoteID] [bigint] NOT NULL,
	[PaymentNumber] [varchar](50) NOT NULL,
	[PaymentStage] [varchar](50) NOT NULL,
	[AmountDue] [decimal](18, 2) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[DueAt] [datetime2](0) NULL,
	[Status] [varchar](50) NOT NULL,
	[ScheduleID] [bigint] NULL,
 CONSTRAINT [PK_PaymentRequest] PRIMARY KEY CLUSTERED 
(
	[PaymentRequestID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[PaymentTransaction](
	[TransactionID] [bigint] IDENTITY(1,1) NOT NULL,
	[ReconciledBy] [bigint] NULL,
	[RecipientAccount] [varchar](150) NOT NULL,
	[BankTransactionReference] [varchar](150) NOT NULL,
	[AmountReceived] [decimal](18, 2) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[PostedAt] [datetime2](0) NOT NULL,
	[ReconciledAt] [datetime2](0) NULL,
	[Status] [varchar](50) NOT NULL,
	[VerificationSource] [nvarchar](500) NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
	[InternalNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_PaymentTransaction] PRIMARY KEY CLUSTERED 
(
	[TransactionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[QuarantineChoice](
	[QuarantineChoiceID] [bigint] IDENTITY(1,1) NOT NULL,
	[ChoiceID] [bigint] NOT NULL,
	[PackageCode] [varchar](50) NOT NULL,
	[RequiredDays] [int] NULL,
	[ConfirmedBy] [bigint] NULL,
	[DaysConfirmedAt] [datetime2](0) NULL,
 CONSTRAINT [PK_QuarantineChoice] PRIMARY KEY CLUSTERED 
(
	[QuarantineChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[QuarantineReservation](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Quote](
	[QuoteID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[ApprovedBy] [bigint] NULL,
	[QuoteNumber] [varchar](30) NOT NULL,
	[VersionNo] [int] NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[TotalQuotedAmount] [decimal](18, 2) NULL,
	[QuoteStatus] [varchar](20) NOT NULL,
	[ApprovedAt] [datetime2](0) NULL,
	[IssuedAt] [datetime2](0) NULL,
	[ValidUntil] [datetime2](0) NULL,
	[AcceptedAt] [datetime2](0) NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
	[PreparedBy] [bigint] NULL,
	[SubmittedAt] [datetime2](0) NULL,
	[IssuedBy] [bigint] NULL,
	[AcceptedByCustomerID] [bigint] NULL,
	[TermsVersion] [varchar](50) NULL,
	[TermsText] [nvarchar](max) NULL,
	[CancellationTerms] [nvarchar](max) NULL,
	[TermsFileKey] [nvarchar](1024) NULL,
	[ReviewFeedback] [nvarchar](2000) NULL,
 CONSTRAINT [PK_Quote] PRIMARY KEY CLUSTERED 
(
	[QuoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[QuoteItem](
	[ItemID] [bigint] IDENTITY(1,1) NOT NULL,
	[QuoteID] [bigint] NOT NULL,
	[TariffID] [bigint] NULL,
	[OfferID] [bigint] NULL,
	[ServiceCode] [varchar](50) NOT NULL,
	[CostCategory] [varchar](50) NOT NULL,
	[Description] [nvarchar](2000) NOT NULL,
	[UnitCode] [varchar](50) NOT NULL,
	[Quantity] [decimal](18, 4) NOT NULL,
	[UnitPrice] [decimal](18, 4) NOT NULL,
	[Amount] [decimal](18, 2) NOT NULL,
	[CalculationInputs] [nvarchar](max) NULL,
 CONSTRAINT [PK_QuoteItem] PRIMARY KEY CLUSTERED 
(
	[ItemID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[QuotePaymentSchedule](
	[ScheduleID] [bigint] IDENTITY(1,1) NOT NULL,
	[QuoteID] [bigint] NOT NULL,
	[SequenceNo] [int] NOT NULL,
	[PaymentStage] [varchar](50) NOT NULL,
	[AmountDue] [decimal](18, 2) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[DueAt] [datetime2](0) NULL,
 CONSTRAINT [PK_QuotePaymentSchedule] PRIMARY KEY CLUSTERED 
(
	[ScheduleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Refund](
	[RefundID] [bigint] IDENTITY(1,1) NOT NULL,
	[AllocationID] [bigint] NOT NULL,
	[Amount] [decimal](18, 2) NOT NULL,
	[Reason] [nvarchar](2000) NOT NULL,
	[Status] [varchar](50) NOT NULL,
	[RefundedAt] [datetime2](0) NULL,
	[RequestedBy] [bigint] NULL,
	[RequestedAt] [datetime2](0) NULL,
	[ApprovedBy] [bigint] NULL,
	[ApprovedAt] [datetime2](0) NULL,
	[BankRefundReference] [varchar](150) NULL,
	[ProofFileKey] [nvarchar](1024) NULL,
 CONSTRAINT [PK_Refund] PRIMARY KEY CLUSTERED 
(
	[RefundID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Role](
	[RoleID] [bigint] IDENTITY(1,1) NOT NULL,
	[RoleCode] [varchar](30) NOT NULL,
	[Description] [nvarchar](255) NULL,
 CONSTRAINT [PK_Role] PRIMARY KEY CLUSTERED 
(
	[RoleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[SchemaMigrationHistory](
	[MigrationID] [varchar](100) NOT NULL,
	[AppliedAt] [datetime2](0) NOT NULL,
	[AppliedBy] [nvarchar](128) NOT NULL,
 CONSTRAINT [PK_SchemaMigrationHistory] PRIMARY KEY CLUSTERED 
(
	[MigrationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[ServiceTariff](
	[TariffID] [bigint] IDENTITY(1,1) NOT NULL,
	[ServiceCode] [varchar](50) NOT NULL,
	[OptionCode] [varchar](50) NOT NULL,
	[CostGroup] [varchar](50) NOT NULL,
	[UnitCode] [varchar](30) NOT NULL,
	[UnitPrice] [decimal](18, 4) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[VersionNo] [int] NOT NULL,
	[EffectiveFrom] [datetime2](0) NOT NULL,
	[EffectiveTo] [datetime2](0) NULL,
	[Status] [varchar](20) NOT NULL,
 CONSTRAINT [PK_ServiceTariff] PRIMARY KEY CLUSTERED 
(
	[TariffID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[StaffProfile](
	[StaffID] [bigint] IDENTITY(1,1) NOT NULL,
	[UserID] [bigint] NOT NULL,
	[FullName] [nvarchar](150) NOT NULL,
	[Phone] [varchar](30) NULL,
	[LicenseNumber] [varchar](50) NULL,
 CONSTRAINT [PK_StaffProfile] PRIMARY KEY CLUSTERED 
(
	[StaffID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[StallAssignment](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[StallChoice](
	[StallChoiceID] [bigint] IDENTITY(1,1) NOT NULL,
	[ChoiceID] [bigint] NOT NULL,
	[SlotQuantity] [decimal](3, 1) NOT NULL,
	[SharingCode] [varchar](20) NOT NULL,
	[FitStatus] [varchar](20) NOT NULL,
 CONSTRAINT [PK_StallChoice] PRIMARY KEY CLUSTERED 
(
	[StallChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[StallEquipment](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[StallFitPolicy](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[Supplier](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[SupplierOffer](
	[OfferID] [bigint] IDENTITY(1,1) NOT NULL,
	[RevisionID] [bigint] NOT NULL,
	[ProviderName] [nvarchar](200) NOT NULL,
	[OfferReference] [varchar](100) NULL,
	[ServiceCode] [varchar](50) NOT NULL,
	[UnitCode] [varchar](30) NOT NULL,
	[Quantity] [decimal](18, 4) NOT NULL,
	[UnitPrice] [decimal](18, 4) NOT NULL,
	[Currency] [varchar](3) NOT NULL,
	[IncludesStall] [bit] NOT NULL,
	[IncludedCharges] [nvarchar](max) NULL,
	[ValidUntil] [datetime2](0) NOT NULL,
	[Status] [varchar](20) NOT NULL,
	[SupplierID] [bigint] NULL,
	[EvidenceFileKey] [nvarchar](1024) NULL,
	[SourceContact] [nvarchar](500) NULL,
 CONSTRAINT [PK_SupplierOffer] PRIMARY KEY CLUSTERED 
(
	[OfferID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[TransportOrder](
	[OrderID] [bigint] IDENTITY(1,1) NOT NULL,
	[CustomerID] [bigint] NOT NULL,
	[OrderNumber] [varchar](30) NOT NULL,
	[OriginCountryCode] [varchar](2) NULL,
	[DestinationCountryCode] [varchar](2) NULL,
	[OriginAddress] [nvarchar](500) NULL,
	[DestinationAddress] [nvarchar](500) NULL,
	[OriginTimezone] [varchar](100) NULL,
	[DestinationTimezone] [varchar](100) NULL,
	[DeliveryDeadline] [datetime2](0) NULL,
	[EventStartAt] [datetime2](0) NULL,
	[Currency] [varchar](3) NOT NULL,
	[OrderStatus] [varchar](30) NOT NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
	[PreferredDepartureAt] [datetime2](0) NULL,
	[DateFlexibilityNotes] [nvarchar](500) NULL,
	[ReceiverName] [nvarchar](150) NULL,
	[ReceiverPhone] [varchar](30) NULL,
	[ReceiverEmail] [varchar](255) NULL,
	[ReceiverAuthority] [nvarchar](500) NULL,
	[ImportType] [varchar](20) NULL,
	[EventName] [nvarchar](200) NULL,
	[EventVenue] [nvarchar](300) NULL,
	[EventTimezone] [varchar](100) NULL,
	[TransitAirportsJSON] [nvarchar](max) NULL,
	[DraftPayloadJSON] [nvarchar](max) NULL,
	[UpdatedAt] [datetime2](0) NULL,
	[CurrentRevisionID] [bigint] NULL,
	[CurrentPlanID] [bigint] NULL,
	[RecordVersion] [timestamp] NOT NULL,
 CONSTRAINT [PK_TransportOrder] PRIMARY KEY CLUSTERED 
(
	[OrderID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[TransportOrderDetail](
	[DetailID] [bigint] IDENTITY(1,1) NOT NULL,
	[OrderID] [bigint] NOT NULL,
	[HorseID] [bigint] NOT NULL,
	[SnapshotMicrochip] [varchar](50) NULL,
	[SnapshotSexCode] [varchar](20) NULL,
	[SnapshotWeightKg] [decimal](8, 2) NULL,
	[SnapshotHeightCm] [decimal](6, 2) NULL,
	[SnapshotBodyWidthCm] [decimal](6, 2) NULL,
	[SnapshotMeasuredAt] [datetime2](0) NULL,
	[SpecialNeeds] [nvarchar](1000) NULL,
 CONSTRAINT [PK_TransportOrderDetail] PRIMARY KEY CLUSTERED 
(
	[DetailID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[TransportPlan](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[TripAssignment](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[TripHandover](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
CREATE TABLE [dbo].[TripLeg](
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
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE TABLE [dbo].[User](
	[UserID] [bigint] IDENTITY(1,1) NOT NULL,
	[RoleID] [bigint] NOT NULL,
	[Email] [varchar](255) NOT NULL,
	[PasswordHash] [varchar](255) NOT NULL,
	[Status] [varchar](20) NOT NULL,
	[CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_User] PRIMARY KEY CLUSTERED 
(
	[UserID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_ActualCost_LegID] ON [dbo].[ActualCost]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_ActualCost_PlanID] ON [dbo].[ActualCost]
(
	[PlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_AuditEvent_UserID] ON [dbo].[AuditEvent]
(
	[UserID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[AuthenticationToken] ADD  CONSTRAINT [UQ_AuthenticationToken_Hash] UNIQUE NONCLUSTERED 
(
	[TokenHash] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_AuthenticationToken_UserPurpose] ON [dbo].[AuthenticationToken]
(
	[UserID] ASC,
	[Purpose] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_AuthorityProcedure_RevisionID] ON [dbo].[AuthorityProcedure]
(
	[RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_ComplianceClearance_RevisionID] ON [dbo].[ComplianceClearance]
(
	[RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_ComplianceClearance_SpecialistID] ON [dbo].[ComplianceClearance]
(
	[SpecialistID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[ComplianceRuleVersion] ADD  CONSTRAINT [UQ_ComplianceRuleVersion_Version] UNIQUE NONCLUSTERED 
(
	[RuleCode] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Customer] ADD  CONSTRAINT [UQ_Customer_UserID] UNIQUE NONCLUSTERED 
(
	[UserID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentRequirement_RevisionID] ON [dbo].[DocumentRequirement]
(
	[RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentRequirement_RuleID] ON [dbo].[DocumentRequirement]
(
	[RuleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentReview_ReviewedBy] ON [dbo].[DocumentReview]
(
	[ReviewedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentReview_SubmissionID] ON [dbo].[DocumentReview]
(
	[SubmissionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentSubmission_DocumentVersionID] ON [dbo].[DocumentSubmission]
(
	[DocumentVersionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_DocumentSubmission_RequirementID] ON [dbo].[DocumentSubmission]
(
	[RequirementID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[DocumentVersion] ADD  CONSTRAINT [UQ_DocumentVersion_Version] UNIQUE NONCLUSTERED 
(
	[DocumentID] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[FleetVehicle] ADD  CONSTRAINT [UQ_FleetVehicle_PlateNumber] UNIQUE NONCLUSTERED 
(
	[PlateNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[FleetVehicle] ADD  CONSTRAINT [UQ_FleetVehicle_VehicleCode] UNIQUE NONCLUSTERED 
(
	[VehicleCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_FlightReservation_LegID] ON [dbo].[FlightReservation]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_FlightReservation_OfferID] ON [dbo].[FlightReservation]
(
	[OfferID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE UNIQUE NONCLUSTERED INDEX [UX_Horse_MicrochipID] ON [dbo].[Horse]
(
	[MicrochipID] ASC
)
WHERE ([MicrochipID] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_HorseDocument_HorseID] ON [dbo].[HorseDocument]
(
	[HorseID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_HorseReceipt_PlanID] ON [dbo].[HorseReceipt]
(
	[PlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_HorseReceipt_ReceivedBy] ON [dbo].[HorseReceipt]
(
	[ReceivedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[HorseWelfareLog] ADD  CONSTRAINT [UQ_Figma_HorseWelfareLog_Parent] UNIQUE NONCLUSTERED 
(
	[WelfareLogID] ASC,
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_HorseWelfareLog_LegID] ON [dbo].[HorseWelfareLog]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_HorseWelfareLog_RecordedBy] ON [dbo].[HorseWelfareLog]
(
	[RecordedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_IncidentReport_LegID] ON [dbo].[IncidentReport]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_IncidentReport_ReportedBy] ON [dbo].[IncidentReport]
(
	[ReportedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[InsuranceChoice] ADD  CONSTRAINT [UQ_InsuranceChoice_ChoiceID] UNIQUE NONCLUSTERED 
(
	[ChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_InsurancePolicy_InsuranceChoiceID] ON [dbo].[InsurancePolicy]
(
	[InsuranceChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_LegMilestoneLog_LegID] ON [dbo].[LegMilestoneLog]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_LegMilestoneLog_ReportedBy] ON [dbo].[LegMilestoneLog]
(
	[ReportedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_Notification_OrderID] ON [dbo].[Notification]
(
	[OrderID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_Notification_UserID] ON [dbo].[Notification]
(
	[UserID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_ApprovedBy] ON [dbo].[OperationalAdjustment]
(
	[ApprovedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_IncidentID] ON [dbo].[OperationalAdjustment]
(
	[IncidentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_PlanID] ON [dbo].[OperationalAdjustment]
(
	[PlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_OperationalAdjustment_ProposedBy] ON [dbo].[OperationalAdjustment]
(
	[ProposedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[OrderRevision] ADD  CONSTRAINT [UQ_Figma_OrderRevision_Parent] UNIQUE NONCLUSTERED 
(
	[RevisionID] ASC,
	[OrderID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[OrderRevision] ADD  CONSTRAINT [UQ_OrderRevision_Order_RevisionNo] UNIQUE NONCLUSTERED 
(
	[OrderID] ASC,
	[RevisionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[OrderServiceChoice] ADD  CONSTRAINT [UQ_OrderServiceChoice_Revision_Service] UNIQUE NONCLUSTERED 
(
	[RevisionID] ASC,
	[ServiceCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_PaymentAllocation_PaymentRequestID] ON [dbo].[PaymentAllocation]
(
	[PaymentRequestID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_PaymentAllocation_TransactionID] ON [dbo].[PaymentAllocation]
(
	[TransactionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_PaymentReport_PaymentRequestID] ON [dbo].[PaymentReport]
(
	[PaymentRequestID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[PaymentRequest] ADD  CONSTRAINT [UQ_PaymentRequest_PaymentNumber] UNIQUE NONCLUSTERED 
(
	[PaymentNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_PaymentRequest_QuoteID] ON [dbo].[PaymentRequest]
(
	[QuoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[PaymentTransaction] ADD  CONSTRAINT [UQ_PaymentTransaction_BankReference] UNIQUE NONCLUSTERED 
(
	[RecipientAccount] ASC,
	[BankTransactionReference] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_PaymentTransaction_ReconciledBy] ON [dbo].[PaymentTransaction]
(
	[ReconciledBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[QuarantineChoice] ADD  CONSTRAINT [UQ_QuarantineChoice_ChoiceID] UNIQUE NONCLUSTERED 
(
	[ChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_QuarantineReservation_QuarantineChoiceID] ON [dbo].[QuarantineReservation]
(
	[QuarantineChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [UQ_Figma_Quote_Parent] UNIQUE NONCLUSTERED 
(
	[QuoteID] ASC,
	[RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [UQ_Quote_QuoteNumber] UNIQUE NONCLUSTERED 
(
	[QuoteNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [UQ_Quote_Revision_Version] UNIQUE NONCLUSTERED 
(
	[RevisionID] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_QuoteItem_OfferID] ON [dbo].[QuoteItem]
(
	[OfferID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_QuoteItem_QuoteID] ON [dbo].[QuoteItem]
(
	[QuoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_QuoteItem_TariffID] ON [dbo].[QuoteItem]
(
	[TariffID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[QuotePaymentSchedule] ADD  CONSTRAINT [UQ_QuotePaymentSchedule_Parent] UNIQUE NONCLUSTERED 
(
	[ScheduleID] ASC,
	[QuoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[QuotePaymentSchedule] ADD  CONSTRAINT [UQ_QuotePaymentSchedule_Sequence] UNIQUE NONCLUSTERED 
(
	[QuoteID] ASC,
	[SequenceNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_Refund_AllocationID] ON [dbo].[Refund]
(
	[AllocationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Role] ADD  CONSTRAINT [UQ_Role_RoleCode] UNIQUE NONCLUSTERED 
(
	[RoleCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[ServiceTariff] ADD  CONSTRAINT [UQ_ServiceTariff_Version] UNIQUE NONCLUSTERED 
(
	[ServiceCode] ASC,
	[OptionCode] ASC,
	[Currency] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[StaffProfile] ADD  CONSTRAINT [UQ_StaffProfile_UserID] UNIQUE NONCLUSTERED 
(
	[UserID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_StallAssignment_LegID] ON [dbo].[StallAssignment]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_StallAssignment_StallID] ON [dbo].[StallAssignment]
(
	[StallID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[StallChoice] ADD  CONSTRAINT [UQ_StallChoice_ChoiceID] UNIQUE NONCLUSTERED 
(
	[ChoiceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[StallEquipment] ADD  CONSTRAINT [UQ_StallEquipment_StallCode] UNIQUE NONCLUSTERED 
(
	[StallCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[StallFitPolicy] ADD  CONSTRAINT [UQ_StallFitPolicy_Version] UNIQUE NONCLUSTERED 
(
	[PolicyCode] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TransportOrder] ADD  CONSTRAINT [UQ_TransportOrder_OrderNumber] UNIQUE NONCLUSTERED 
(
	[OrderNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TransportOrderDetail] ADD  CONSTRAINT [UQ_TransportOrderDetail_OrderID] UNIQUE NONCLUSTERED 
(
	[OrderID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TransportPlan] ADD  CONSTRAINT [UQ_Figma_TransportPlan_Parent] UNIQUE NONCLUSTERED 
(
	[PlanID] ASC,
	[OrderID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TransportPlan] ADD  CONSTRAINT [UQ_TransportPlan_Version] UNIQUE NONCLUSTERED 
(
	[RevisionID] ASC,
	[VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TransportPlan_CreatedBy] ON [dbo].[TransportPlan]
(
	[CreatedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TransportPlan_DepartureAuthorizedBy] ON [dbo].[TransportPlan]
(
	[DepartureAuthorizedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TripAssignment_LegID] ON [dbo].[TripAssignment]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TripAssignment_StaffID] ON [dbo].[TripAssignment]
(
	[StaffID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TripHandover_LegID] ON [dbo].[TripHandover]
(
	[LegID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TripHandover_RecordedBy] ON [dbo].[TripHandover]
(
	[RecordedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TripLeg] ADD  CONSTRAINT [UQ_Figma_TripLeg_Parent] UNIQUE NONCLUSTERED 
(
	[LegID] ASC,
	[PlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[TripLeg] ADD  CONSTRAINT [UQ_TripLeg_Sequence] UNIQUE NONCLUSTERED 
(
	[PlanID] ASC,
	[SequenceNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_TripLeg_VehicleID] ON [dbo].[TripLeg]
(
	[VehicleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [UQ_User_Email] UNIQUE NONCLUSTERED 
(
	[Email] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[AuditEvent] ADD  CONSTRAINT [DF_AuditEvent_OccurredAt]  DEFAULT (sysutcdatetime()) FOR [OccurredAt]
GO
ALTER TABLE [dbo].[ComplianceClearance] ADD  CONSTRAINT [DF_ComplianceClearance_CheckedAt]  DEFAULT (sysutcdatetime()) FOR [CheckedAt]
GO
ALTER TABLE [dbo].[DocumentSubmission] ADD  CONSTRAINT [DF_DocumentSubmission_SubmittedAt]  DEFAULT (sysutcdatetime()) FOR [SubmittedAt]
GO
ALTER TABLE [dbo].[DocumentTypeCatalog] ADD  DEFAULT ((1)) FOR [IsActive]
GO
ALTER TABLE [dbo].[DocumentVersion] ADD  CONSTRAINT [DF_DocumentVersion_UploadedAt]  DEFAULT (sysutcdatetime()) FOR [UploadedAt]
GO
ALTER TABLE [dbo].[HorseDocument] ADD  CONSTRAINT [DF_HorseDocument_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[LegMilestoneLog] ADD  CONSTRAINT [DF_LegMilestoneLog_OccurredAt]  DEFAULT (sysutcdatetime()) FOR [OccurredAt]
GO
ALTER TABLE [dbo].[LegMilestoneLog] ADD  CONSTRAINT [DF_LegMilestoneLog_RecordedAt]  DEFAULT (sysutcdatetime()) FOR [RecordedAt]
GO
ALTER TABLE [dbo].[Notification] ADD  CONSTRAINT [DF_Notification_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[OperationalAdjustment] ADD  CONSTRAINT [DF_OperationalAdjustment_ProposedAt]  DEFAULT (sysutcdatetime()) FOR [ProposedAt]
GO
ALTER TABLE [dbo].[OrderAssignment] ADD  CONSTRAINT [DF_OrderAssignment_AssignedAt]  DEFAULT (sysutcdatetime()) FOR [AssignedAt]
GO
ALTER TABLE [dbo].[OrderChangeRequest] ADD  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[OrderDecision] ADD  CONSTRAINT [DF_OrderDecision_DecidedAt]  DEFAULT (sysutcdatetime()) FOR [DecidedAt]
GO
ALTER TABLE [dbo].[OrderRevision] ADD  CONSTRAINT [DF_OrderRevision_SubmittedAt]  DEFAULT (sysutcdatetime()) FOR [SubmittedAt]
GO
ALTER TABLE [dbo].[OrderServiceChoice] ADD  CONSTRAINT [DF_OrderServiceChoice_ChosenAt]  DEFAULT (sysutcdatetime()) FOR [ChosenAt]
GO
ALTER TABLE [dbo].[PaymentAllocation] ADD  CONSTRAINT [DF_PaymentAllocation_AllocatedAt]  DEFAULT (sysutcdatetime()) FOR [AllocatedAt]
GO
ALTER TABLE [dbo].[PaymentReport] ADD  CONSTRAINT [DF_PaymentReport_CustomerReportedAt]  DEFAULT (sysutcdatetime()) FOR [CustomerReportedAt]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [DF_Quote_VersionNo]  DEFAULT ((1)) FOR [VersionNo]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [DF_Quote_Status]  DEFAULT ('DRAFT') FOR [QuoteStatus]
GO
ALTER TABLE [dbo].[Quote] ADD  CONSTRAINT [DF_Quote_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[SchemaMigrationHistory] ADD  DEFAULT (sysutcdatetime()) FOR [AppliedAt]
GO
ALTER TABLE [dbo].[SchemaMigrationHistory] ADD  DEFAULT (suser_sname()) FOR [AppliedBy]
GO
ALTER TABLE [dbo].[ServiceTariff] ADD  CONSTRAINT [DF_ServiceTariff_Currency]  DEFAULT ('USD') FOR [Currency]
GO
ALTER TABLE [dbo].[ServiceTariff] ADD  CONSTRAINT [DF_ServiceTariff_VersionNo]  DEFAULT ((1)) FOR [VersionNo]
GO
ALTER TABLE [dbo].[ServiceTariff] ADD  CONSTRAINT [DF_ServiceTariff_Status]  DEFAULT ('ACTIVE') FOR [Status]
GO
ALTER TABLE [dbo].[StallChoice] ADD  CONSTRAINT [DF_StallChoice_FitStatus]  DEFAULT ('PENDING') FOR [FitStatus]
GO
ALTER TABLE [dbo].[Supplier] ADD  DEFAULT ((1)) FOR [IsActive]
GO
ALTER TABLE [dbo].[Supplier] ADD  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[SupplierOffer] ADD  CONSTRAINT [DF_SupplierOffer_IncludesStall]  DEFAULT ((0)) FOR [IncludesStall]
GO
ALTER TABLE [dbo].[SupplierOffer] ADD  CONSTRAINT [DF_SupplierOffer_Status]  DEFAULT ('RECEIVED') FOR [Status]
GO
ALTER TABLE [dbo].[TransportOrder] ADD  CONSTRAINT [DF_TransportOrder_Currency]  DEFAULT ('USD') FOR [Currency]
GO
ALTER TABLE [dbo].[TransportOrder] ADD  CONSTRAINT [DF_TransportOrder_Status]  DEFAULT ('DRAFT') FOR [OrderStatus]
GO
ALTER TABLE [dbo].[TransportOrder] ADD  CONSTRAINT [DF_TransportOrder_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [DF_User_Status]  DEFAULT ('ACTIVE') FOR [Status]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [DF_User_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]
GO
ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_ActualCost_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[ActualCost] CHECK CONSTRAINT [FK_ActualCost_LegID]
GO
ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_ActualCost_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID])
GO
ALTER TABLE [dbo].[ActualCost] CHECK CONSTRAINT [FK_ActualCost_PlanID]
GO
ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ActualCost_LegID] FOREIGN KEY([LegID], [PlanID])
REFERENCES [dbo].[TripLeg] ([LegID], [PlanID])
GO
ALTER TABLE [dbo].[ActualCost] CHECK CONSTRAINT [FK_Figma_ActualCost_LegID]
GO
ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ActualCost_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[ActualCost] CHECK CONSTRAINT [FK_Figma_ActualCost_RecordedBy]
GO
ALTER TABLE [dbo].[AuditEvent]  WITH CHECK ADD  CONSTRAINT [FK_AuditEvent_UserID] FOREIGN KEY([UserID])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[AuditEvent] CHECK CONSTRAINT [FK_AuditEvent_UserID]
GO
ALTER TABLE [dbo].[AuthenticationToken]  WITH CHECK ADD  CONSTRAINT [FK_AuthenticationToken_User] FOREIGN KEY([UserID])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[AuthenticationToken] CHECK CONSTRAINT [FK_AuthenticationToken_User]
GO
ALTER TABLE [dbo].[AuthorityProcedure]  WITH CHECK ADD  CONSTRAINT [FK_AuthorityProcedure_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[AuthorityProcedure] CHECK CONSTRAINT [FK_AuthorityProcedure_RevisionID]
GO
ALTER TABLE [dbo].[AuthorityProcedure]  WITH CHECK ADD  CONSTRAINT [FK_Figma_AuthorityProcedure_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[AuthorityProcedure] CHECK CONSTRAINT [FK_Figma_AuthorityProcedure_RecordedBy]
GO
ALTER TABLE [dbo].[ComplianceClearance]  WITH CHECK ADD  CONSTRAINT [FK_ComplianceClearance_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[ComplianceClearance] CHECK CONSTRAINT [FK_ComplianceClearance_RevisionID]
GO
ALTER TABLE [dbo].[ComplianceClearance]  WITH CHECK ADD  CONSTRAINT [FK_ComplianceClearance_SpecialistID] FOREIGN KEY([SpecialistID])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[ComplianceClearance] CHECK CONSTRAINT [FK_ComplianceClearance_SpecialistID]
GO
ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ComplianceRuleVersion_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])
GO
ALTER TABLE [dbo].[ComplianceRuleVersion] CHECK CONSTRAINT [FK_Figma_ComplianceRuleVersion_DocumentType]
GO
ALTER TABLE [dbo].[Customer]  WITH CHECK ADD  CONSTRAINT [FK_Customer_User] FOREIGN KEY([UserID])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[Customer] CHECK CONSTRAINT [FK_Customer_User]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_DocumentRequirement_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [FK_DocumentRequirement_RevisionID]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_DocumentRequirement_RuleID] FOREIGN KEY([RuleID])
REFERENCES [dbo].[ComplianceRuleVersion] ([RuleID])
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [FK_DocumentRequirement_RuleID]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentRequirement_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [FK_Figma_DocumentRequirement_DocumentType]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentRequirement_HorseID] FOREIGN KEY([HorseID])
REFERENCES [dbo].[Horse] ([HorseID])
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [FK_Figma_DocumentRequirement_HorseID]
GO
ALTER TABLE [dbo].[DocumentReview]  WITH CHECK ADD  CONSTRAINT [FK_DocumentReview_ReviewedBy] FOREIGN KEY([ReviewedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[DocumentReview] CHECK CONSTRAINT [FK_DocumentReview_ReviewedBy]
GO
ALTER TABLE [dbo].[DocumentReview]  WITH CHECK ADD  CONSTRAINT [FK_DocumentReview_SubmissionID] FOREIGN KEY([SubmissionID])
REFERENCES [dbo].[DocumentSubmission] ([SubmissionID])
GO
ALTER TABLE [dbo].[DocumentReview] CHECK CONSTRAINT [FK_DocumentReview_SubmissionID]
GO
ALTER TABLE [dbo].[DocumentSubmission]  WITH CHECK ADD  CONSTRAINT [FK_DocumentSubmission_DocumentVersionID] FOREIGN KEY([DocumentVersionID])
REFERENCES [dbo].[DocumentVersion] ([DocumentVersionID])
GO
ALTER TABLE [dbo].[DocumentSubmission] CHECK CONSTRAINT [FK_DocumentSubmission_DocumentVersionID]
GO
ALTER TABLE [dbo].[DocumentSubmission]  WITH CHECK ADD  CONSTRAINT [FK_DocumentSubmission_RequirementID] FOREIGN KEY([RequirementID])
REFERENCES [dbo].[DocumentRequirement] ([RequirementID])
GO
ALTER TABLE [dbo].[DocumentSubmission] CHECK CONSTRAINT [FK_DocumentSubmission_RequirementID]
GO
ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [FK_DocumentVersion_DocumentID] FOREIGN KEY([DocumentID])
REFERENCES [dbo].[HorseDocument] ([DocumentID])
GO
ALTER TABLE [dbo].[DocumentVersion] CHECK CONSTRAINT [FK_DocumentVersion_DocumentID]
GO
ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentVersion_UploadedBy] FOREIGN KEY([UploadedBy])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[DocumentVersion] CHECK CONSTRAINT [FK_Figma_DocumentVersion_UploadedBy]
GO
ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID] FOREIGN KEY([OwnerSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[FleetVehicle] CHECK CONSTRAINT [FK_Figma_FleetVehicle_OwnerSupplierID]
GO
ALTER TABLE [dbo].[FlightReservation]  WITH CHECK ADD  CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy] FOREIGN KEY([ConfirmedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[FlightReservation] CHECK CONSTRAINT [FK_Figma_FlightReservation_ConfirmedBy]
GO
ALTER TABLE [dbo].[FlightReservation]  WITH CHECK ADD  CONSTRAINT [FK_FlightReservation_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[FlightReservation] CHECK CONSTRAINT [FK_FlightReservation_LegID]
GO
ALTER TABLE [dbo].[FlightReservation]  WITH CHECK ADD  CONSTRAINT [FK_FlightReservation_OfferID] FOREIGN KEY([OfferID])
REFERENCES [dbo].[SupplierOffer] ([OfferID])
GO
ALTER TABLE [dbo].[FlightReservation] CHECK CONSTRAINT [FK_FlightReservation_OfferID]
GO
ALTER TABLE [dbo].[Horse]  WITH CHECK ADD  CONSTRAINT [FK_Horse_Customer] FOREIGN KEY([CustomerID])
REFERENCES [dbo].[Customer] ([CustomerID])
GO
ALTER TABLE [dbo].[Horse] CHECK CONSTRAINT [FK_Horse_Customer]
GO
ALTER TABLE [dbo].[HorseDocument]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseDocument_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])
GO
ALTER TABLE [dbo].[HorseDocument] CHECK CONSTRAINT [FK_Figma_HorseDocument_DocumentType]
GO
ALTER TABLE [dbo].[HorseDocument]  WITH CHECK ADD  CONSTRAINT [FK_HorseDocument_HorseID] FOREIGN KEY([HorseID])
REFERENCES [dbo].[Horse] ([HorseID])
GO
ALTER TABLE [dbo].[HorseDocument] CHECK CONSTRAINT [FK_HorseDocument_HorseID]
GO
ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseReceipt_LegID] FOREIGN KEY([LegID], [PlanID])
REFERENCES [dbo].[TripLeg] ([LegID], [PlanID])
GO
ALTER TABLE [dbo].[HorseReceipt] CHECK CONSTRAINT [FK_Figma_HorseReceipt_LegID]
GO
ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_HorseReceipt_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID])
GO
ALTER TABLE [dbo].[HorseReceipt] CHECK CONSTRAINT [FK_HorseReceipt_PlanID]
GO
ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [FK_HorseReceipt_ReceivedBy] FOREIGN KEY([ReceivedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[HorseReceipt] CHECK CONSTRAINT [FK_HorseReceipt_ReceivedBy]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID] FOREIGN KEY([SupersedesLogID], [LegID])
REFERENCES [dbo].[HorseWelfareLog] ([WelfareLogID], [LegID])
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [FK_Figma_HorseWelfareLog_SupersedesLogID]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_HorseWelfareLog_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [FK_HorseWelfareLog_LegID]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [FK_HorseWelfareLog_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [FK_HorseWelfareLog_RecordedBy]
GO
ALTER TABLE [dbo].[IncidentReport]  WITH CHECK ADD  CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy] FOREIGN KEY([AcknowledgedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[IncidentReport] CHECK CONSTRAINT [FK_Figma_IncidentReport_AcknowledgedBy]
GO
ALTER TABLE [dbo].[IncidentReport]  WITH CHECK ADD  CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy] FOREIGN KEY([ResolvedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[IncidentReport] CHECK CONSTRAINT [FK_Figma_IncidentReport_ResolvedBy]
GO
ALTER TABLE [dbo].[IncidentReport]  WITH CHECK ADD  CONSTRAINT [FK_IncidentReport_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[IncidentReport] CHECK CONSTRAINT [FK_IncidentReport_LegID]
GO
ALTER TABLE [dbo].[IncidentReport]  WITH CHECK ADD  CONSTRAINT [FK_IncidentReport_ReportedBy] FOREIGN KEY([ReportedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[IncidentReport] CHECK CONSTRAINT [FK_IncidentReport_ReportedBy]
GO
ALTER TABLE [dbo].[InsuranceChoice]  WITH CHECK ADD  CONSTRAINT [FK_InsuranceChoice_ServiceChoice] FOREIGN KEY([ChoiceID])
REFERENCES [dbo].[OrderServiceChoice] ([ChoiceID])
GO
ALTER TABLE [dbo].[InsuranceChoice] CHECK CONSTRAINT [FK_InsuranceChoice_ServiceChoice]
GO
ALTER TABLE [dbo].[InsurancePolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy] FOREIGN KEY([VerifiedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[InsurancePolicy] CHECK CONSTRAINT [FK_Figma_InsurancePolicy_VerifiedBy]
GO
ALTER TABLE [dbo].[InsurancePolicy]  WITH CHECK ADD  CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID] FOREIGN KEY([InsuranceChoiceID])
REFERENCES [dbo].[InsuranceChoice] ([InsuranceChoiceID])
GO
ALTER TABLE [dbo].[InsurancePolicy] CHECK CONSTRAINT [FK_InsurancePolicy_InsuranceChoiceID]
GO
ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [FK_LegMilestoneLog_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[LegMilestoneLog] CHECK CONSTRAINT [FK_LegMilestoneLog_LegID]
GO
ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [FK_LegMilestoneLog_ReportedBy] FOREIGN KEY([ReportedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[LegMilestoneLog] CHECK CONSTRAINT [FK_LegMilestoneLog_ReportedBy]
GO
ALTER TABLE [dbo].[Notification]  WITH CHECK ADD  CONSTRAINT [FK_Notification_OrderID] FOREIGN KEY([OrderID])
REFERENCES [dbo].[TransportOrder] ([OrderID])
GO
ALTER TABLE [dbo].[Notification] CHECK CONSTRAINT [FK_Notification_OrderID]
GO
ALTER TABLE [dbo].[Notification]  WITH CHECK ADD  CONSTRAINT [FK_Notification_UserID] FOREIGN KEY([UserID])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[Notification] CHECK CONSTRAINT [FK_Notification_UserID]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID] FOREIGN KEY([ChangeRequestID])
REFERENCES [dbo].[OrderChangeRequest] ([ChangeRequestID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_ChangeRequestID]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID] FOREIGN KEY([RespondedByCustomerID])
REFERENCES [dbo].[Customer] ([CustomerID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_Figma_OperationalAdjustment_RespondedByCustomerID]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_OperationalAdjustment_ApprovedBy] FOREIGN KEY([ApprovedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_OperationalAdjustment_ApprovedBy]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_OperationalAdjustment_IncidentID] FOREIGN KEY([IncidentID])
REFERENCES [dbo].[IncidentReport] ([IncidentID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_OperationalAdjustment_IncidentID]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_OperationalAdjustment_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_OperationalAdjustment_PlanID]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [FK_OperationalAdjustment_ProposedBy] FOREIGN KEY([ProposedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [FK_OperationalAdjustment_ProposedBy]
GO
ALTER TABLE [dbo].[OrderAssignment]  WITH CHECK ADD  CONSTRAINT [FK_OrderAssignment_Order] FOREIGN KEY([OrderID])
REFERENCES [dbo].[TransportOrder] ([OrderID])
GO
ALTER TABLE [dbo].[OrderAssignment] CHECK CONSTRAINT [FK_OrderAssignment_Order]
GO
ALTER TABLE [dbo].[OrderAssignment]  WITH CHECK ADD  CONSTRAINT [FK_OrderAssignment_Staff] FOREIGN KEY([StaffID])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[OrderAssignment] CHECK CONSTRAINT [FK_OrderAssignment_Staff]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID] FOREIGN KEY([BaseRevisionID], [OrderID])
REFERENCES [dbo].[OrderRevision] ([RevisionID], [OrderID])
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_BaseRevisionID]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID] FOREIGN KEY([OrderID])
REFERENCES [dbo].[TransportOrder] ([OrderID])
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_OrderID]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy] FOREIGN KEY([RequestedBy])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RequestedBy]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy] FOREIGN KEY([RespondedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [FK_Figma_OrderChangeRequest_RespondedBy]
GO
ALTER TABLE [dbo].[OrderDecision]  WITH CHECK ADD  CONSTRAINT [FK_OrderDecision_Manager] FOREIGN KEY([ManagerID])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[OrderDecision] CHECK CONSTRAINT [FK_OrderDecision_Manager]
GO
ALTER TABLE [dbo].[OrderDecision]  WITH CHECK ADD  CONSTRAINT [FK_OrderDecision_Revision] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[OrderDecision] CHECK CONSTRAINT [FK_OrderDecision_Revision]
GO
ALTER TABLE [dbo].[OrderRevision]  WITH CHECK ADD  CONSTRAINT [FK_OrderRevision_Order] FOREIGN KEY([OrderID])
REFERENCES [dbo].[TransportOrder] ([OrderID])
GO
ALTER TABLE [dbo].[OrderRevision] CHECK CONSTRAINT [FK_OrderRevision_Order]
GO
ALTER TABLE [dbo].[OrderServiceChoice]  WITH CHECK ADD  CONSTRAINT [FK_OrderServiceChoice_Revision] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[OrderServiceChoice] CHECK CONSTRAINT [FK_OrderServiceChoice_Revision]
GO
ALTER TABLE [dbo].[PaymentAllocation]  WITH CHECK ADD  CONSTRAINT [FK_PaymentAllocation_PaymentRequestID] FOREIGN KEY([PaymentRequestID])
REFERENCES [dbo].[PaymentRequest] ([PaymentRequestID])
GO
ALTER TABLE [dbo].[PaymentAllocation] CHECK CONSTRAINT [FK_PaymentAllocation_PaymentRequestID]
GO
ALTER TABLE [dbo].[PaymentAllocation]  WITH CHECK ADD  CONSTRAINT [FK_PaymentAllocation_TransactionID] FOREIGN KEY([TransactionID])
REFERENCES [dbo].[PaymentTransaction] ([TransactionID])
GO
ALTER TABLE [dbo].[PaymentAllocation] CHECK CONSTRAINT [FK_PaymentAllocation_TransactionID]
GO
ALTER TABLE [dbo].[PaymentReport]  WITH CHECK ADD  CONSTRAINT [FK_PaymentReport_PaymentRequestID] FOREIGN KEY([PaymentRequestID])
REFERENCES [dbo].[PaymentRequest] ([PaymentRequestID])
GO
ALTER TABLE [dbo].[PaymentReport] CHECK CONSTRAINT [FK_PaymentReport_PaymentRequestID]
GO
ALTER TABLE [dbo].[PaymentRequest]  WITH CHECK ADD  CONSTRAINT [FK_Figma_PaymentRequest_ScheduleID] FOREIGN KEY([ScheduleID], [QuoteID])
REFERENCES [dbo].[QuotePaymentSchedule] ([ScheduleID], [QuoteID])
GO
ALTER TABLE [dbo].[PaymentRequest] CHECK CONSTRAINT [FK_Figma_PaymentRequest_ScheduleID]
GO
ALTER TABLE [dbo].[PaymentRequest]  WITH CHECK ADD  CONSTRAINT [FK_PaymentRequest_QuoteID] FOREIGN KEY([QuoteID])
REFERENCES [dbo].[Quote] ([QuoteID])
GO
ALTER TABLE [dbo].[PaymentRequest] CHECK CONSTRAINT [FK_PaymentRequest_QuoteID]
GO
ALTER TABLE [dbo].[PaymentTransaction]  WITH CHECK ADD  CONSTRAINT [FK_PaymentTransaction_ReconciledBy] FOREIGN KEY([ReconciledBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[PaymentTransaction] CHECK CONSTRAINT [FK_PaymentTransaction_ReconciledBy]
GO
ALTER TABLE [dbo].[QuarantineChoice]  WITH CHECK ADD  CONSTRAINT [FK_QuarantineChoice_ServiceChoice] FOREIGN KEY([ChoiceID])
REFERENCES [dbo].[OrderServiceChoice] ([ChoiceID])
GO
ALTER TABLE [dbo].[QuarantineChoice] CHECK CONSTRAINT [FK_QuarantineChoice_ServiceChoice]
GO
ALTER TABLE [dbo].[QuarantineChoice]  WITH CHECK ADD  CONSTRAINT [FK_QuarantineChoice_Staff] FOREIGN KEY([ConfirmedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[QuarantineChoice] CHECK CONSTRAINT [FK_QuarantineChoice_Staff]
GO
ALTER TABLE [dbo].[QuarantineReservation]  WITH CHECK ADD  CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy] FOREIGN KEY([ConfirmedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[QuarantineReservation] CHECK CONSTRAINT [FK_Figma_QuarantineReservation_ConfirmedBy]
GO
ALTER TABLE [dbo].[QuarantineReservation]  WITH CHECK ADD  CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID] FOREIGN KEY([SupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[QuarantineReservation] CHECK CONSTRAINT [FK_Figma_QuarantineReservation_SupplierID]
GO
ALTER TABLE [dbo].[QuarantineReservation]  WITH CHECK ADD  CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID] FOREIGN KEY([QuarantineChoiceID])
REFERENCES [dbo].[QuarantineChoice] ([QuarantineChoiceID])
GO
ALTER TABLE [dbo].[QuarantineReservation] CHECK CONSTRAINT [FK_QuarantineReservation_QuarantineChoiceID]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [FK_Figma_Quote_AcceptedByCustomerID] FOREIGN KEY([AcceptedByCustomerID])
REFERENCES [dbo].[Customer] ([CustomerID])
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [FK_Figma_Quote_AcceptedByCustomerID]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [FK_Figma_Quote_IssuedBy] FOREIGN KEY([IssuedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [FK_Figma_Quote_IssuedBy]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [FK_Figma_Quote_PreparedBy] FOREIGN KEY([PreparedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [FK_Figma_Quote_PreparedBy]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [FK_Quote_ApprovedBy] FOREIGN KEY([ApprovedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [FK_Quote_ApprovedBy]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [FK_Quote_Revision] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [FK_Quote_Revision]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [FK_QuoteItem_OfferID] FOREIGN KEY([OfferID])
REFERENCES [dbo].[SupplierOffer] ([OfferID])
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [FK_QuoteItem_OfferID]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [FK_QuoteItem_QuoteID] FOREIGN KEY([QuoteID])
REFERENCES [dbo].[Quote] ([QuoteID])
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [FK_QuoteItem_QuoteID]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [FK_QuoteItem_TariffID] FOREIGN KEY([TariffID])
REFERENCES [dbo].[ServiceTariff] ([TariffID])
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [FK_QuoteItem_TariffID]
GO
ALTER TABLE [dbo].[QuotePaymentSchedule]  WITH CHECK ADD  CONSTRAINT [FK_Figma_QuotePaymentSchedule_QuoteID] FOREIGN KEY([QuoteID])
REFERENCES [dbo].[Quote] ([QuoteID])
GO
ALTER TABLE [dbo].[QuotePaymentSchedule] CHECK CONSTRAINT [FK_Figma_QuotePaymentSchedule_QuoteID]
GO
ALTER TABLE [dbo].[Refund]  WITH CHECK ADD  CONSTRAINT [FK_Figma_Refund_ApprovedBy] FOREIGN KEY([ApprovedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[Refund] CHECK CONSTRAINT [FK_Figma_Refund_ApprovedBy]
GO
ALTER TABLE [dbo].[Refund]  WITH CHECK ADD  CONSTRAINT [FK_Figma_Refund_RequestedBy] FOREIGN KEY([RequestedBy])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[Refund] CHECK CONSTRAINT [FK_Figma_Refund_RequestedBy]
GO
ALTER TABLE [dbo].[Refund]  WITH CHECK ADD  CONSTRAINT [FK_Refund_AllocationID] FOREIGN KEY([AllocationID])
REFERENCES [dbo].[PaymentAllocation] ([AllocationID])
GO
ALTER TABLE [dbo].[Refund] CHECK CONSTRAINT [FK_Refund_AllocationID]
GO
ALTER TABLE [dbo].[StaffProfile]  WITH CHECK ADD  CONSTRAINT [FK_StaffProfile_User] FOREIGN KEY([UserID])
REFERENCES [dbo].[User] ([UserID])
GO
ALTER TABLE [dbo].[StaffProfile] CHECK CONSTRAINT [FK_StaffProfile_User]
GO
ALTER TABLE [dbo].[StallAssignment]  WITH CHECK ADD  CONSTRAINT [FK_StallAssignment_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[StallAssignment] CHECK CONSTRAINT [FK_StallAssignment_LegID]
GO
ALTER TABLE [dbo].[StallAssignment]  WITH CHECK ADD  CONSTRAINT [FK_StallAssignment_StallID] FOREIGN KEY([StallID])
REFERENCES [dbo].[StallEquipment] ([StallID])
GO
ALTER TABLE [dbo].[StallAssignment] CHECK CONSTRAINT [FK_StallAssignment_StallID]
GO
ALTER TABLE [dbo].[StallChoice]  WITH CHECK ADD  CONSTRAINT [FK_StallChoice_ServiceChoice] FOREIGN KEY([ChoiceID])
REFERENCES [dbo].[OrderServiceChoice] ([ChoiceID])
GO
ALTER TABLE [dbo].[StallChoice] CHECK CONSTRAINT [FK_StallChoice_ServiceChoice]
GO
ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID] FOREIGN KEY([OwnerSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[StallEquipment] CHECK CONSTRAINT [FK_Figma_StallEquipment_OwnerSupplierID]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID] FOREIGN KEY([CarrierSupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [FK_Figma_StallFitPolicy_CarrierSupplierID]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy] FOREIGN KEY([ReviewedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [FK_Figma_StallFitPolicy_ReviewedBy]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [FK_Figma_StallFitPolicy_StallID] FOREIGN KEY([StallID])
REFERENCES [dbo].[StallEquipment] ([StallID])
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [FK_Figma_StallFitPolicy_StallID]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [FK_Figma_SupplierOffer_SupplierID] FOREIGN KEY([SupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [FK_Figma_SupplierOffer_SupplierID]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [FK_SupplierOffer_Revision] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [FK_SupplierOffer_Revision]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID] FOREIGN KEY([CurrentPlanID], [OrderID])
REFERENCES [dbo].[TransportPlan] ([PlanID], [OrderID])
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [FK_Figma_TransportOrder_CurrentPlanID]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TransportOrder_CurrentRevisionID] FOREIGN KEY([CurrentRevisionID], [OrderID])
REFERENCES [dbo].[OrderRevision] ([RevisionID], [OrderID])
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [FK_Figma_TransportOrder_CurrentRevisionID]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [FK_TransportOrder_Customer] FOREIGN KEY([CustomerID])
REFERENCES [dbo].[Customer] ([CustomerID])
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [FK_TransportOrder_Customer]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [FK_TransportOrderDetail_Horse] FOREIGN KEY([HorseID])
REFERENCES [dbo].[Horse] ([HorseID])
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [FK_TransportOrderDetail_Horse]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [FK_TransportOrderDetail_Order] FOREIGN KEY([OrderID])
REFERENCES [dbo].[TransportOrder] ([OrderID])
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [FK_TransportOrderDetail_Order]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID] FOREIGN KEY([AcceptedQuoteID], [RevisionID])
REFERENCES [dbo].[Quote] ([QuoteID], [RevisionID])
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [FK_Figma_TransportPlan_AcceptedQuoteID]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TransportPlan_RevisionID] FOREIGN KEY([RevisionID], [OrderID])
REFERENCES [dbo].[OrderRevision] ([RevisionID], [OrderID])
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [FK_Figma_TransportPlan_RevisionID]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [FK_TransportPlan_CreatedBy] FOREIGN KEY([CreatedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [FK_TransportPlan_CreatedBy]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy] FOREIGN KEY([DepartureAuthorizedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [FK_TransportPlan_DepartureAuthorizedBy]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [FK_TransportPlan_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [FK_TransportPlan_RevisionID]
GO
ALTER TABLE [dbo].[TripAssignment]  WITH CHECK ADD  CONSTRAINT [FK_TripAssignment_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[TripAssignment] CHECK CONSTRAINT [FK_TripAssignment_LegID]
GO
ALTER TABLE [dbo].[TripAssignment]  WITH CHECK ADD  CONSTRAINT [FK_TripAssignment_StaffID] FOREIGN KEY([StaffID])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[TripAssignment] CHECK CONSTRAINT [FK_TripAssignment_StaffID]
GO
ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID] FOREIGN KEY([RespondedByCustomerID])
REFERENCES [dbo].[Customer] ([CustomerID])
GO
ALTER TABLE [dbo].[TripHandover] CHECK CONSTRAINT [FK_Figma_TripHandover_RespondedByCustomerID]
GO
ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_TripHandover_LegID] FOREIGN KEY([LegID])
REFERENCES [dbo].[TripLeg] ([LegID])
GO
ALTER TABLE [dbo].[TripHandover] CHECK CONSTRAINT [FK_TripHandover_LegID]
GO
ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [FK_TripHandover_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])
GO
ALTER TABLE [dbo].[TripHandover] CHECK CONSTRAINT [FK_TripHandover_RecordedBy]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [FK_Figma_TripLeg_SupplierID] FOREIGN KEY([SupplierID])
REFERENCES [dbo].[Supplier] ([SupplierID])
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [FK_Figma_TripLeg_SupplierID]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [FK_TripLeg_PlanID] FOREIGN KEY([PlanID])
REFERENCES [dbo].[TransportPlan] ([PlanID])
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [FK_TripLeg_PlanID]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [FK_TripLeg_VehicleID] FOREIGN KEY([VehicleID])
REFERENCES [dbo].[FleetVehicle] ([VehicleID])
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [FK_TripLeg_VehicleID]
GO
ALTER TABLE [dbo].[User]  WITH CHECK ADD  CONSTRAINT [FK_User_Role] FOREIGN KEY([RoleID])
REFERENCES [dbo].[Role] ([RoleID])
GO
ALTER TABLE [dbo].[User] CHECK CONSTRAINT [FK_User_Role]
GO
ALTER TABLE [dbo].[ActualCost]  WITH CHECK ADD  CONSTRAINT [CK_ActualCost_Amount] CHECK  (([Amount]>=(0)))
GO
ALTER TABLE [dbo].[ActualCost] CHECK CONSTRAINT [CK_ActualCost_Amount]
GO
ALTER TABLE [dbo].[AuditEvent]  WITH CHECK ADD  CONSTRAINT [CK_AuditEvent_ChangeSnapshot_JSON] CHECK  (([ChangeSnapshot] IS NULL OR isjson([ChangeSnapshot])=(1)))
GO
ALTER TABLE [dbo].[AuditEvent] CHECK CONSTRAINT [CK_AuditEvent_ChangeSnapshot_JSON]
GO
ALTER TABLE [dbo].[AuthenticationToken]  WITH CHECK ADD  CONSTRAINT [CK_AuthenticationToken_Expiry] CHECK  (([ExpiresAt]>[CreatedAt]))
GO
ALTER TABLE [dbo].[AuthenticationToken] CHECK CONSTRAINT [CK_AuthenticationToken_Expiry]
GO
ALTER TABLE [dbo].[AuthenticationToken]  WITH CHECK ADD  CONSTRAINT [CK_AuthenticationToken_Purpose] CHECK  (([Purpose]='RESET_PASSWORD' OR [Purpose]='VERIFY_EMAIL'))
GO
ALTER TABLE [dbo].[AuthenticationToken] CHECK CONSTRAINT [CK_AuthenticationToken_Purpose]
GO
ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [CK_ComplianceRuleVersion_EffectiveTo_Range] CHECK  (([EffectiveTo] IS NULL OR [EffectiveFrom] IS NOT NULL AND [EffectiveTo]>=[EffectiveFrom]))
GO
ALTER TABLE [dbo].[ComplianceRuleVersion] CHECK CONSTRAINT [CK_ComplianceRuleVersion_EffectiveTo_Range]
GO
ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [CK_ComplianceRuleVersion_VersionNo] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[ComplianceRuleVersion] CHECK CONSTRAINT [CK_ComplianceRuleVersion_VersionNo]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentRequirement_Scope] CHECK  (([ScopeCode] IS NULL OR [ScopeCode]='HORSE' AND [HorseID] IS NOT NULL OR [ScopeCode]='SHIPMENT'))
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [CK_Figma_DocumentRequirement_Scope]
GO
ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentRequirement_ValidityConditionsJSON] CHECK  (([ValidityConditionsJSON] IS NULL OR isjson([ValidityConditionsJSON])=(1)))
GO
ALTER TABLE [dbo].[DocumentRequirement] CHECK CONSTRAINT [CK_Figma_DocumentRequirement_ValidityConditionsJSON]
GO
ALTER TABLE [dbo].[DocumentTypeCatalog]  WITH CHECK ADD  CONSTRAINT [CK_DocumentTypeCatalog_Code] CHECK  ((len(ltrim(rtrim([TypeCode])))>(0)))
GO
ALTER TABLE [dbo].[DocumentTypeCatalog] CHECK CONSTRAINT [CK_DocumentTypeCatalog_Code]
GO
ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_DocumentVersion_ExpiryDate_Range] CHECK  (([ExpiryDate] IS NULL OR [IssuedDate] IS NOT NULL AND [ExpiryDate]>=[IssuedDate]))
GO
ALTER TABLE [dbo].[DocumentVersion] CHECK CONSTRAINT [CK_DocumentVersion_ExpiryDate_Range]
GO
ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_DocumentVersion_VersionNo] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[DocumentVersion] CHECK CONSTRAINT [CK_DocumentVersion_VersionNo]
GO
ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentVersion_FileSizeBytes] CHECK  (([FileSizeBytes]>(0)))
GO
ALTER TABLE [dbo].[DocumentVersion] CHECK CONSTRAINT [CK_Figma_DocumentVersion_FileSizeBytes]
GO
ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates] CHECK  (([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom]))
GO
ALTER TABLE [dbo].[FleetVehicle] CHECK CONSTRAINT [CK_Figma_FleetVehicle_MaintenanceDates]
GO
ALTER TABLE [dbo].[FleetVehicle]  WITH CHECK ADD  CONSTRAINT [CK_FleetVehicle_HorseCapacity] CHECK  (([HorseCapacity]>(0)))
GO
ALTER TABLE [dbo].[FleetVehicle] CHECK CONSTRAINT [CK_FleetVehicle_HorseCapacity]
GO
ALTER TABLE [dbo].[FlightReservation]  WITH CHECK ADD  CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots] CHECK  (([UsableCapacitySlots]>(0)))
GO
ALTER TABLE [dbo].[FlightReservation] CHECK CONSTRAINT [CK_Figma_FlightReservation_UsableCapacitySlots]
GO
ALTER TABLE [dbo].[Horse]  WITH CHECK ADD  CONSTRAINT [CK_Horse_BodyWidthCm] CHECK  (([BodyWidthCm]>(0)))
GO
ALTER TABLE [dbo].[Horse] CHECK CONSTRAINT [CK_Horse_BodyWidthCm]
GO
ALTER TABLE [dbo].[Horse]  WITH CHECK ADD  CONSTRAINT [CK_Horse_HeightCm] CHECK  (([HeightCm]>(0)))
GO
ALTER TABLE [dbo].[Horse] CHECK CONSTRAINT [CK_Horse_HeightCm]
GO
ALTER TABLE [dbo].[Horse]  WITH CHECK ADD  CONSTRAINT [CK_Horse_SexCode] CHECK  (([SexCode]='GELDING' OR [SexCode]='MARE' OR [SexCode]='STALLION'))
GO
ALTER TABLE [dbo].[Horse] CHECK CONSTRAINT [CK_Horse_SexCode]
GO
ALTER TABLE [dbo].[Horse]  WITH CHECK ADD  CONSTRAINT [CK_Horse_WeightKg] CHECK  (([WeightKg]>(0)))
GO
ALTER TABLE [dbo].[Horse] CHECK CONSTRAINT [CK_Horse_WeightKg]
GO
ALTER TABLE [dbo].[HorseReceipt]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseReceipt_Identity] CHECK  (([IdentityCheckResult]='MISMATCHED' OR [IdentityCheckResult]='MATCHED' OR [IdentityCheckResult]='PENDING'))
GO
ALTER TABLE [dbo].[HorseReceipt] CHECK CONSTRAINT [CK_Figma_HorseReceipt_Identity]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg] CHECK  (([FeedAmountKg]>=(0)))
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_FeedAmountKg]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes] CHECK  (([SupersedesLogID] IS NULL OR [SupersedesLogID]<>[WelfareLogID]))
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [CK_Figma_HorseWelfareLog_Supersedes]
GO
ALTER TABLE [dbo].[HorseWelfareLog]  WITH CHECK ADD  CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters] CHECK  (([WaterIntakeLiters]>=(0)))
GO
ALTER TABLE [dbo].[HorseWelfareLog] CHECK CONSTRAINT [CK_HorseWelfareLog_WaterIntakeLiters]
GO
ALTER TABLE [dbo].[IncidentReport]  WITH CHECK ADD  CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON] CHECK  (([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1)))
GO
ALTER TABLE [dbo].[IncidentReport] CHECK CONSTRAINT [CK_Figma_IncidentReport_EvidenceFilesJSON]
GO
ALTER TABLE [dbo].[InsuranceChoice]  WITH CHECK ADD  CONSTRAINT [CK_InsuranceChoice_CoverageCode] CHECK  (([CoverageCode]='MORTALITY_INJURY' OR [CoverageCode]='MORTALITY' OR [CoverageCode]='EXISTING_POLICY' OR [CoverageCode]='NONE'))
GO
ALTER TABLE [dbo].[InsuranceChoice] CHECK CONSTRAINT [CK_InsuranceChoice_CoverageCode]
GO
ALTER TABLE [dbo].[InsuranceChoice]  WITH CHECK ADD  CONSTRAINT [CK_InsuranceChoice_DeclaredValue] CHECK  (([DeclaredValue]>(0)))
GO
ALTER TABLE [dbo].[InsuranceChoice] CHECK CONSTRAINT [CK_InsuranceChoice_DeclaredValue]
GO
ALTER TABLE [dbo].[InsuranceChoice]  WITH CHECK ADD  CONSTRAINT [CK_InsuranceChoice_ExistingPolicy] CHECK  (([CoverageCode]<>'EXISTING_POLICY' OR [ExistingPolicyReference] IS NOT NULL AND len(ltrim(rtrim([ExistingPolicyReference])))>(0)))
GO
ALTER TABLE [dbo].[InsuranceChoice] CHECK CONSTRAINT [CK_InsuranceChoice_ExistingPolicy]
GO
ALTER TABLE [dbo].[InsuranceChoice]  WITH CHECK ADD  CONSTRAINT [CK_InsuranceChoice_ValueCurrency] CHECK  (([DeclaredValue] IS NULL AND [ValueCurrency] IS NULL OR [DeclaredValue] IS NOT NULL AND [ValueCurrency] IS NOT NULL))
GO
ALTER TABLE [dbo].[InsuranceChoice] CHECK CONSTRAINT [CK_InsuranceChoice_ValueCurrency]
GO
ALTER TABLE [dbo].[InsurancePolicy]  WITH CHECK ADD  CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range] CHECK  (([CoverageTo] IS NULL OR [CoverageFrom] IS NOT NULL AND [CoverageTo]>=[CoverageFrom]))
GO
ALTER TABLE [dbo].[InsurancePolicy] CHECK CONSTRAINT [CK_InsurancePolicy_CoverageTo_Range]
GO
ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [CK_LegMilestoneLog_Latitude] CHECK  (([Latitude]>=(-90) AND [Latitude]<=(90)))
GO
ALTER TABLE [dbo].[LegMilestoneLog] CHECK CONSTRAINT [CK_LegMilestoneLog_Latitude]
GO
ALTER TABLE [dbo].[LegMilestoneLog]  WITH CHECK ADD  CONSTRAINT [CK_LegMilestoneLog_Longitude] CHECK  (([Longitude]>=(-180) AND [Longitude]<=(180)))
GO
ALTER TABLE [dbo].[LegMilestoneLog] CHECK CONSTRAINT [CK_LegMilestoneLog_Longitude]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision] CHECK  (([CustomerDecision]='REJECTED' OR [CustomerDecision]='ACCEPTED' OR [CustomerDecision]='PENDING'))
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [CK_Figma_OperationalAdjustment_CustomerDecision]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate] CHECK  (([AdditionalCostEstimate]>=(0)))
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [CK_OperationalAdjustment_AdditionalCostEstimate]
GO
ALTER TABLE [dbo].[OperationalAdjustment]  WITH CHECK ADD  CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON] CHECK  ((isjson([ProposedChanges])=(1)))
GO
ALTER TABLE [dbo].[OperationalAdjustment] CHECK CONSTRAINT [CK_OperationalAdjustment_ProposedChanges_JSON]
GO
ALTER TABLE [dbo].[OrderAssignment]  WITH CHECK ADD  CONSTRAINT [CK_OrderAssignment_Dates] CHECK  (([EndedAt] IS NULL OR [EndedAt]>=[AssignedAt]))
GO
ALTER TABLE [dbo].[OrderAssignment] CHECK CONSTRAINT [CK_OrderAssignment_Dates]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [CK_OrderChangeRequest_JSON] CHECK  ((isjson([RequestedChangesJSON])=(1)))
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [CK_OrderChangeRequest_JSON]
GO
ALTER TABLE [dbo].[OrderChangeRequest]  WITH CHECK ADD  CONSTRAINT [CK_OrderChangeRequest_Reason] CHECK  ((len(ltrim(rtrim([Reason])))>(0)))
GO
ALTER TABLE [dbo].[OrderChangeRequest] CHECK CONSTRAINT [CK_OrderChangeRequest_Reason]
GO
ALTER TABLE [dbo].[OrderDecision]  WITH CHECK ADD  CONSTRAINT [CK_OrderDecision_Decision] CHECK  (([Decision]='CHANGES_REQUESTED' OR [Decision]='REJECTED' OR [Decision]='APPROVED'))
GO
ALTER TABLE [dbo].[OrderDecision] CHECK CONSTRAINT [CK_OrderDecision_Decision]
GO
ALTER TABLE [dbo].[OrderRevision]  WITH CHECK ADD  CONSTRAINT [CK_OrderRevision_OrderSnapshot] CHECK  ((isjson([OrderSnapshot])=(1)))
GO
ALTER TABLE [dbo].[OrderRevision] CHECK CONSTRAINT [CK_OrderRevision_OrderSnapshot]
GO
ALTER TABLE [dbo].[OrderRevision]  WITH CHECK ADD  CONSTRAINT [CK_OrderRevision_RevisionNo] CHECK  (([RevisionNo]>(0)))
GO
ALTER TABLE [dbo].[OrderRevision] CHECK CONSTRAINT [CK_OrderRevision_RevisionNo]
GO
ALTER TABLE [dbo].[OrderServiceChoice]  WITH CHECK ADD  CONSTRAINT [CK_Figma_OrderServiceChoice_SelectionInputsJSON] CHECK  (([SelectionInputsJSON] IS NULL OR isjson([SelectionInputsJSON])=(1)))
GO
ALTER TABLE [dbo].[OrderServiceChoice] CHECK CONSTRAINT [CK_Figma_OrderServiceChoice_SelectionInputsJSON]
GO
ALTER TABLE [dbo].[OrderServiceChoice]  WITH CHECK ADD  CONSTRAINT [CK_OrderServiceChoice_ArrangedBy] CHECK  (([ArrangedBy]='CUSTOMER' OR [ArrangedBy]='EQUITRANSIT'))
GO
ALTER TABLE [dbo].[OrderServiceChoice] CHECK CONSTRAINT [CK_OrderServiceChoice_ArrangedBy]
GO
ALTER TABLE [dbo].[PaymentAllocation]  WITH CHECK ADD  CONSTRAINT [CK_PaymentAllocation_AllocatedAmount] CHECK  (([AllocatedAmount]>(0)))
GO
ALTER TABLE [dbo].[PaymentAllocation] CHECK CONSTRAINT [CK_PaymentAllocation_AllocatedAmount]
GO
ALTER TABLE [dbo].[PaymentReport]  WITH CHECK ADD  CONSTRAINT [CK_PaymentReport_ReportedAmount] CHECK  (([ReportedAmount]>(0)))
GO
ALTER TABLE [dbo].[PaymentReport] CHECK CONSTRAINT [CK_PaymentReport_ReportedAmount]
GO
ALTER TABLE [dbo].[PaymentRequest]  WITH CHECK ADD  CONSTRAINT [CK_PaymentRequest_AmountDue] CHECK  (([AmountDue]>(0)))
GO
ALTER TABLE [dbo].[PaymentRequest] CHECK CONSTRAINT [CK_PaymentRequest_AmountDue]
GO
ALTER TABLE [dbo].[PaymentTransaction]  WITH CHECK ADD  CONSTRAINT [CK_PaymentTransaction_AmountReceived] CHECK  (([AmountReceived]>(0)))
GO
ALTER TABLE [dbo].[PaymentTransaction] CHECK CONSTRAINT [CK_PaymentTransaction_AmountReceived]
GO
ALTER TABLE [dbo].[QuarantineChoice]  WITH CHECK ADD  CONSTRAINT [CK_QuarantineChoice_Confirmation] CHECK  (([ConfirmedBy] IS NULL AND [DaysConfirmedAt] IS NULL OR [ConfirmedBy] IS NOT NULL AND [DaysConfirmedAt] IS NOT NULL AND [RequiredDays] IS NOT NULL))
GO
ALTER TABLE [dbo].[QuarantineChoice] CHECK CONSTRAINT [CK_QuarantineChoice_Confirmation]
GO
ALTER TABLE [dbo].[QuarantineChoice]  WITH CHECK ADD  CONSTRAINT [CK_QuarantineChoice_RequiredDays] CHECK  (([RequiredDays]>(0)))
GO
ALTER TABLE [dbo].[QuarantineChoice] CHECK CONSTRAINT [CK_QuarantineChoice_RequiredDays]
GO
ALTER TABLE [dbo].[QuarantineReservation]  WITH CHECK ADD  CONSTRAINT [CK_QuarantineReservation_EndsAt_Range] CHECK  (([EndsAt] IS NULL OR [StartsAt] IS NOT NULL AND [EndsAt]>=[StartsAt]))
GO
ALTER TABLE [dbo].[QuarantineReservation] CHECK CONSTRAINT [CK_QuarantineReservation_EndsAt_Range]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_AcceptedAt] CHECK  (([AcceptedAt] IS NULL OR [IssuedAt] IS NOT NULL AND [AcceptedAt]>=[IssuedAt]))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_AcceptedAt]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_Approval] CHECK  (([ApprovedBy] IS NULL AND [ApprovedAt] IS NULL OR [ApprovedBy] IS NOT NULL AND [ApprovedAt] IS NOT NULL))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_Approval]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_Status] CHECK  (([QuoteStatus]='SUPERSEDED' OR [QuoteStatus]='EXPIRED' OR [QuoteStatus]='REJECTED' OR [QuoteStatus]='ACCEPTED' OR [QuoteStatus]='ISSUED' OR [QuoteStatus]='APPROVED' OR [QuoteStatus]='DRAFT'))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_Status]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_TotalAmount] CHECK  (([TotalQuotedAmount]>=(0)))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_TotalAmount]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_Validity] CHECK  (([ValidUntil] IS NULL OR [IssuedAt] IS NOT NULL AND [ValidUntil]>[IssuedAt]))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_Validity]
GO
ALTER TABLE [dbo].[Quote]  WITH CHECK ADD  CONSTRAINT [CK_Quote_VersionNo] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[Quote] CHECK CONSTRAINT [CK_Quote_VersionNo]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [CK_QuoteItem_Amount] CHECK  (([Amount]>=(0)))
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [CK_QuoteItem_Amount]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [CK_QuoteItem_CalculationInputs_JSON] CHECK  (([CalculationInputs] IS NULL OR isjson([CalculationInputs])=(1)))
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [CK_QuoteItem_CalculationInputs_JSON]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [CK_QuoteItem_Quantity] CHECK  (([Quantity]>(0)))
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [CK_QuoteItem_Quantity]
GO
ALTER TABLE [dbo].[QuoteItem]  WITH CHECK ADD  CONSTRAINT [CK_QuoteItem_UnitPrice] CHECK  (([UnitPrice]>=(0)))
GO
ALTER TABLE [dbo].[QuoteItem] CHECK CONSTRAINT [CK_QuoteItem_UnitPrice]
GO
ALTER TABLE [dbo].[QuotePaymentSchedule]  WITH CHECK ADD  CONSTRAINT [CK_QuotePaymentSchedule_Amount] CHECK  (([AmountDue]>(0)))
GO
ALTER TABLE [dbo].[QuotePaymentSchedule] CHECK CONSTRAINT [CK_QuotePaymentSchedule_Amount]
GO
ALTER TABLE [dbo].[QuotePaymentSchedule]  WITH CHECK ADD  CONSTRAINT [CK_QuotePaymentSchedule_Sequence] CHECK  (([SequenceNo]>(0)))
GO
ALTER TABLE [dbo].[QuotePaymentSchedule] CHECK CONSTRAINT [CK_QuotePaymentSchedule_Sequence]
GO
ALTER TABLE [dbo].[Refund]  WITH CHECK ADD  CONSTRAINT [CK_Refund_Amount] CHECK  (([Amount]>=(0)))
GO
ALTER TABLE [dbo].[Refund] CHECK CONSTRAINT [CK_Refund_Amount]
GO
ALTER TABLE [dbo].[ServiceTariff]  WITH CHECK ADD  CONSTRAINT [CK_ServiceTariff_Dates] CHECK  (([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom]))
GO
ALTER TABLE [dbo].[ServiceTariff] CHECK CONSTRAINT [CK_ServiceTariff_Dates]
GO
ALTER TABLE [dbo].[ServiceTariff]  WITH CHECK ADD  CONSTRAINT [CK_ServiceTariff_Status] CHECK  (([Status]='INACTIVE' OR [Status]='ACTIVE'))
GO
ALTER TABLE [dbo].[ServiceTariff] CHECK CONSTRAINT [CK_ServiceTariff_Status]
GO
ALTER TABLE [dbo].[ServiceTariff]  WITH CHECK ADD  CONSTRAINT [CK_ServiceTariff_UnitPrice] CHECK  (([UnitPrice]>=(0)))
GO
ALTER TABLE [dbo].[ServiceTariff] CHECK CONSTRAINT [CK_ServiceTariff_UnitPrice]
GO
ALTER TABLE [dbo].[ServiceTariff]  WITH CHECK ADD  CONSTRAINT [CK_ServiceTariff_VersionNo] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[ServiceTariff] CHECK CONSTRAINT [CK_ServiceTariff_VersionNo]
GO
ALTER TABLE [dbo].[StallAssignment]  WITH CHECK ADD  CONSTRAINT [CK_StallAssignment_AssignedTo_Range] CHECK  (([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom]))
GO
ALTER TABLE [dbo].[StallAssignment] CHECK CONSTRAINT [CK_StallAssignment_AssignedTo_Range]
GO
ALTER TABLE [dbo].[StallAssignment]  WITH CHECK ADD  CONSTRAINT [CK_StallAssignment_SlotQuantity] CHECK  (([SlotQuantity]>(0)))
GO
ALTER TABLE [dbo].[StallAssignment] CHECK CONSTRAINT [CK_StallAssignment_SlotQuantity]
GO
ALTER TABLE [dbo].[StallChoice]  WITH CHECK ADD  CONSTRAINT [CK_StallChoice_Configuration] CHECK  (([SharingCode]='SHARED' AND ([SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)) OR [SharingCode]='PRIVATE' AND [SlotQuantity]=(3.0)))
GO
ALTER TABLE [dbo].[StallChoice] CHECK CONSTRAINT [CK_StallChoice_Configuration]
GO
ALTER TABLE [dbo].[StallChoice]  WITH CHECK ADD  CONSTRAINT [CK_StallChoice_FitStatus] CHECK  (([FitStatus]='UNSUITABLE' OR [FitStatus]='SUITABLE' OR [FitStatus]='PENDING'))
GO
ALTER TABLE [dbo].[StallChoice] CHECK CONSTRAINT [CK_StallChoice_FitStatus]
GO
ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates] CHECK  (([MaintenanceTo] IS NULL OR [MaintenanceFrom] IS NOT NULL AND [MaintenanceTo]>=[MaintenanceFrom]))
GO
ALTER TABLE [dbo].[StallEquipment] CHECK CONSTRAINT [CK_Figma_StallEquipment_MaintenanceDates]
GO
ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg] CHECK  (([WeightLimitKg]>(0)))
GO
ALTER TABLE [dbo].[StallEquipment] CHECK CONSTRAINT [CK_Figma_StallEquipment_WeightLimitKg]
GO
ALTER TABLE [dbo].[StallEquipment]  WITH CHECK ADD  CONSTRAINT [CK_StallEquipment_CapacitySlots] CHECK  (([CapacitySlots]>(0)))
GO
ALTER TABLE [dbo].[StallEquipment] CHECK CONSTRAINT [CK_StallEquipment_CapacitySlots]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Approval] CHECK  (([Status]<>'APPROVED' OR [ReviewedBy] IS NOT NULL AND [ReviewedAt] IS NOT NULL AND [MaxWeightKg] IS NOT NULL AND [MaxHeightCm] IS NOT NULL AND [MaxWidthCm] IS NOT NULL AND ([SourceURL] IS NOT NULL OR [SourceFileKey] IS NOT NULL)))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Approval]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Dates] CHECK  (([EffectiveTo] IS NULL OR [EffectiveTo]>[EffectiveFrom]))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Dates]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Dimensions] CHECK  (([MaxWeightKg]>(0) AND [MaxHeightCm]>(0) AND [MaxWidthCm]>(0)))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Dimensions]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Slots] CHECK  (([SlotQuantity]=(3.0) OR [SlotQuantity]=(1.5) OR [SlotQuantity]=(1.0)))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Slots]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Status] CHECK  (([Status]='INACTIVE' OR [Status]='APPROVED' OR [Status]='DRAFT'))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Status]
GO
ALTER TABLE [dbo].[StallFitPolicy]  WITH CHECK ADD  CONSTRAINT [CK_StallFitPolicy_Version] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[StallFitPolicy] CHECK CONSTRAINT [CK_StallFitPolicy_Version]
GO
ALTER TABLE [dbo].[Supplier]  WITH CHECK ADD  CONSTRAINT [CK_Supplier_Name] CHECK  ((len(ltrim(rtrim([Name])))>(0)))
GO
ALTER TABLE [dbo].[Supplier] CHECK CONSTRAINT [CK_Supplier_Name]
GO
ALTER TABLE [dbo].[Supplier]  WITH CHECK ADD  CONSTRAINT [CK_Supplier_ServicesJSON] CHECK  (([ServicesOfferedJSON] IS NULL OR isjson([ServicesOfferedJSON])=(1)))
GO
ALTER TABLE [dbo].[Supplier] CHECK CONSTRAINT [CK_Supplier_ServicesJSON]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [CK_SupplierOffer_IncludedCharges] CHECK  (([IncludedCharges] IS NULL OR isjson([IncludedCharges])=(1)))
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [CK_SupplierOffer_IncludedCharges]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [CK_SupplierOffer_Quantity] CHECK  (([Quantity]>(0)))
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [CK_SupplierOffer_Quantity]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [CK_SupplierOffer_Status] CHECK  (([Status]='EXPIRED' OR [Status]='REJECTED' OR [Status]='SELECTED' OR [Status]='RECEIVED'))
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [CK_SupplierOffer_Status]
GO
ALTER TABLE [dbo].[SupplierOffer]  WITH CHECK ADD  CONSTRAINT [CK_SupplierOffer_UnitPrice] CHECK  (([UnitPrice]>=(0)))
GO
ALTER TABLE [dbo].[SupplierOffer] CHECK CONSTRAINT [CK_SupplierOffer_UnitPrice]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TransportOrder_DraftPayloadJSON] CHECK  (([DraftPayloadJSON] IS NULL OR isjson([DraftPayloadJSON])=(1)))
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [CK_Figma_TransportOrder_DraftPayloadJSON]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TransportOrder_ImportType] CHECK  (([ImportType]='NEEDS_ADVICE' OR [ImportType]='PERMANENT' OR [ImportType]='TEMPORARY'))
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [CK_Figma_TransportOrder_ImportType]
GO
ALTER TABLE [dbo].[TransportOrder]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TransportOrder_TransitAirportsJSON] CHECK  (([TransitAirportsJSON] IS NULL OR isjson([TransitAirportsJSON])=(1)))
GO
ALTER TABLE [dbo].[TransportOrder] CHECK CONSTRAINT [CK_Figma_TransportOrder_TransitAirportsJSON]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [CK_TransportOrderDetail_Height] CHECK  (([SnapshotHeightCm]>(0)))
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [CK_TransportOrderDetail_Height]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [CK_TransportOrderDetail_SexCode] CHECK  (([SnapshotSexCode]='GELDING' OR [SnapshotSexCode]='MARE' OR [SnapshotSexCode]='STALLION'))
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [CK_TransportOrderDetail_SexCode]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [CK_TransportOrderDetail_Weight] CHECK  (([SnapshotWeightKg]>(0)))
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [CK_TransportOrderDetail_Weight]
GO
ALTER TABLE [dbo].[TransportOrderDetail]  WITH CHECK ADD  CONSTRAINT [CK_TransportOrderDetail_Width] CHECK  (([SnapshotBodyWidthCm]>(0)))
GO
ALTER TABLE [dbo].[TransportOrderDetail] CHECK CONSTRAINT [CK_TransportOrderDetail_Width]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range] CHECK  (([PlannedEndTime] IS NULL OR [PlannedStartTime] IS NOT NULL AND [PlannedEndTime]>=[PlannedStartTime]))
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [CK_TransportPlan_PlannedEndTime_Range]
GO
ALTER TABLE [dbo].[TransportPlan]  WITH CHECK ADD  CONSTRAINT [CK_TransportPlan_VersionNo] CHECK  (([VersionNo]>(0)))
GO
ALTER TABLE [dbo].[TransportPlan] CHECK CONSTRAINT [CK_TransportPlan_VersionNo]
GO
ALTER TABLE [dbo].[TripAssignment]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes] CHECK  (([BufferMinutes]>=(0)))
GO
ALTER TABLE [dbo].[TripAssignment] CHECK CONSTRAINT [CK_Figma_TripAssignment_BufferMinutes]
GO
ALTER TABLE [dbo].[TripAssignment]  WITH CHECK ADD  CONSTRAINT [CK_TripAssignment_AssignedTo_Range] CHECK  (([AssignedTo] IS NULL OR [AssignedFrom] IS NOT NULL AND [AssignedTo]>=[AssignedFrom]))
GO
ALTER TABLE [dbo].[TripAssignment] CHECK CONSTRAINT [CK_TripAssignment_AssignedTo_Range]
GO
ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripHandover_CustomerDecision] CHECK  (([CustomerDecision]='NOT_DELIVERED' OR [CustomerDecision]='EXCEPTION' OR [CustomerDecision]='ACCEPTED'))
GO
ALTER TABLE [dbo].[TripHandover] CHECK CONSTRAINT [CK_Figma_TripHandover_CustomerDecision]
GO
ALTER TABLE [dbo].[TripHandover]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON] CHECK  (([EvidenceFilesJSON] IS NULL OR isjson([EvidenceFilesJSON])=(1)))
GO
ALTER TABLE [dbo].[TripHandover] CHECK CONSTRAINT [CK_Figma_TripHandover_EvidenceFilesJSON]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes] CHECK  (([ConnectionBufferMinutes]>=(0)))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_Figma_TripLeg_ConnectionBufferMinutes]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes] CHECK  (([RestDurationMinutes]>=(0)))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_Figma_TripLeg_RestDurationMinutes]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_TripLeg_ActualArrival_Range] CHECK  (([ActualArrival] IS NULL OR [ActualDeparture] IS NOT NULL AND [ActualArrival]>=[ActualDeparture]))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_TripLeg_ActualArrival_Range]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_TripLeg_DistanceKm] CHECK  (([DistanceKm]>=(0)))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_TripLeg_DistanceKm]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_TripLeg_PlannedArrival_Range] CHECK  (([PlannedArrival] IS NULL OR [PlannedDeparture] IS NOT NULL AND [PlannedArrival]>=[PlannedDeparture]))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_TripLeg_PlannedArrival_Range]
GO
ALTER TABLE [dbo].[TripLeg]  WITH CHECK ADD  CONSTRAINT [CK_TripLeg_SequenceNo] CHECK  (([SequenceNo]>(0)))
GO
ALTER TABLE [dbo].[TripLeg] CHECK CONSTRAINT [CK_TripLeg_SequenceNo]
GO
ALTER TABLE [dbo].[User]  WITH CHECK ADD  CONSTRAINT [CK_User_Status] CHECK  (([Status]='LOCKED' OR [Status]='INACTIVE' OR [Status]='ACTIVE'))
GO
ALTER TABLE [dbo].[User] CHECK CONSTRAINT [CK_User_Status]