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
