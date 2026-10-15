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
