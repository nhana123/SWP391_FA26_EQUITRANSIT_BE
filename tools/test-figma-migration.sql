-- Structural integration tests. ALL fixtures are rolled back.
-- Refuse the production database even when a caller uses the wrong -d flag.
SET NOCOUNT ON;
SET XACT_ABORT OFF;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() NOT LIKE N'EquiTransit[_]FigmaMigrationTest[_]%'
    THROW 51000, 'Structural fixtures require the restored FigmaMigrationTest copy.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @suffix VARCHAR(20) = LEFT(REPLACE(CONVERT(VARCHAR(36), NEWID()), '-', ''), 20);
DECLARE @now DATETIME2(0) = SYSUTCDATETIME();
DECLARE @customerUser BIGINT, @managerUser BIGINT, @driverUser BIGINT;
DECLARE @customer BIGINT, @manager BIGINT, @driver BIGINT;
DECLARE @horse BIGINT, @order BIGINT, @order2 BIGINT, @revision BIGINT, @revision2 BIGINT;
DECLARE @quote BIGINT, @draftQuote BIGINT, @plan BIGINT, @plan2 BIGINT;
DECLARE @leg BIGINT, @leg2 BIGINT, @schedule BIGINT, @choice BIGINT;
DECLARE @caught BIT, @expected INT;

INSERT dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
SELECT RoleID, 'customer-'+@suffix+'@example.invalid', 'TEST-FIXTURE-NOT-A-PASSWORD', 'ACTIVE', @now
FROM dbo.Role WHERE RoleCode='CUSTOMER';
SET @customerUser = SCOPE_IDENTITY();
INSERT dbo.Customer (UserID, FullName, EmailNotificationsEnabled)
VALUES (@customerUser, N'Test customer', 0);
SET @customer = SCOPE_IDENTITY();
INSERT dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
SELECT RoleID, 'manager-'+@suffix+'@example.invalid', 'TEST-FIXTURE-NOT-A-PASSWORD', 'ACTIVE', @now
FROM dbo.Role WHERE RoleCode='MANAGER';
SET @managerUser = SCOPE_IDENTITY();
INSERT dbo.StaffProfile (UserID, FullName) VALUES (@managerUser, N'Test manager');
SET @manager = SCOPE_IDENTITY();
INSERT dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
SELECT RoleID, 'driver-'+@suffix+'@example.invalid', 'TEST-FIXTURE-NOT-A-PASSWORD', 'ACTIVE', @now
FROM dbo.Role WHERE RoleCode='DRIVER';
SET @driverUser = SCOPE_IDENTITY();
INSERT dbo.StaffProfile (UserID, FullName) VALUES (@driverUser, N'Test driver');
SET @driver = SCOPE_IDENTITY();

INSERT dbo.Horse (CustomerID, Name, MicrochipID, WeightKg, HeightCm, BodyWidthCm,
    HandlingNotes, FeedWaterRequirements, VeterinaryInstructionSource)
VALUES (@customer, N'Test horse', NULL, 500, 180, 90,
    N'Fixture handling notes', N'Fixture care requirements', N'Fixture source');
SET @horse = SCOPE_IDENTITY();
-- Two unknown microchips remain valid under the existing filtered unique index.
INSERT dbo.Horse (CustomerID, Name, MicrochipID) VALUES (@customer, N'Other test horse', NULL);
INSERT dbo.TransportOrder (CustomerID, OrderNumber, Currency, OrderStatus, CreatedAt,
    DraftPayloadJSON, PreferredDepartureAt, ReceiverName, ImportType)
VALUES (@customer, 'T-'+@suffix, 'USD', 'DRAFT', @now,
    N'{"schemaVersion":1,"serviceChoices":[]}', DATEADD(DAY,1,@now), N'Test receiver', 'NEEDS_ADVICE');
SET @order = SCOPE_IDENTITY();
INSERT dbo.TransportOrder (CustomerID, OrderNumber, Currency, OrderStatus, CreatedAt)
VALUES (@customer, 'U-'+@suffix, 'USD', 'DRAFT', @now);
SET @order2 = SCOPE_IDENTITY();
INSERT dbo.TransportOrderDetail (OrderID, HorseID, SnapshotWeightKg)
VALUES (@order, @horse, 500);
INSERT dbo.OrderRevision (OrderID, RevisionNo, OrderSnapshot, SubmittedAt)
VALUES (@order, 1, N'{"schemaVersion":1}', @now);
SET @revision = SCOPE_IDENTITY();
INSERT dbo.OrderRevision (OrderID, RevisionNo, OrderSnapshot, SubmittedAt)
VALUES (@order2, 1, N'{"schemaVersion":1}', @now);
SET @revision2 = SCOPE_IDENTITY();
UPDATE dbo.TransportOrder SET CurrentRevisionID=@revision WHERE OrderID=@order;
INSERT dbo.OrderServiceChoice (RevisionID, ServiceCode, OptionCode, ArrangedBy, ChosenAt, SelectionInputsJSON)
VALUES (@revision, 'AIR_STALL', 'WIDE', 'EQUITRANSIT', @now, N'{"slotQuantity":1.5}');
SET @choice = SCOPE_IDENTITY();
INSERT dbo.StallChoice (ChoiceID, SlotQuantity, SharingCode, FitStatus)
VALUES (@choice, 1.5, 'SHARED', 'PENDING');
INSERT dbo.StallFitPolicy (PolicyCode, VersionNo, SlotQuantity, MaxWeightKg,
    MaxHeightCm, MaxWidthCm, EffectiveFrom, SourceURL, ReviewedBy, ReviewedAt, Status)
VALUES ('TEST-'+@suffix, 1, 1.5, 800, 220, 140, @now,
    N'https://example.invalid/test-fixture', @manager, @now, 'APPROVED');

INSERT dbo.Quote (RevisionID, QuoteNumber, VersionNo, Currency, TotalQuotedAmount,
    QuoteStatus, ApprovedBy, ApprovedAt, IssuedAt, AcceptedAt, CreatedAt, AcceptedByCustomerID,
    TermsVersion, TermsText, CancellationTerms)
VALUES (@revision, 'Q-'+@suffix, 1, 'USD', 100, 'ACCEPTED', @manager, @now,
    @now, @now, @now, @customer, 'TEST-V1', N'Fixture terms', N'Fixture cancellation terms');
SET @quote = SCOPE_IDENTITY();
INSERT dbo.Quote (RevisionID, QuoteNumber, VersionNo, Currency, QuoteStatus, CreatedAt)
VALUES (@revision, 'R-'+@suffix, 2, 'USD', 'DRAFT', @now);
SET @draftQuote = SCOPE_IDENTITY();
INSERT dbo.QuotePaymentSchedule (QuoteID, SequenceNo, PaymentStage, AmountDue, Currency)
VALUES (@quote, 1, 'DEPOSIT', 30, 'USD');
SET @schedule = SCOPE_IDENTITY();
INSERT dbo.PaymentRequest (QuoteID, PaymentNumber, PaymentStage, AmountDue, Currency, Status, ScheduleID)
VALUES (@quote, 'P-'+@suffix, 'DEPOSIT', 30, 'USD', 'PENDING', @schedule);
INSERT dbo.TransportPlan (OrderID, RevisionID, CreatedBy, VersionNo, AcceptedQuoteID,
    FinancialGateStatus, OperationalGateStatus, DocGateStatus, PlanStatus)
VALUES (@order, @revision, @manager, 1, @quote, 'BLOCKED', 'BLOCKED', 'BLOCKED', 'DRAFT');
SET @plan = SCOPE_IDENTITY();
INSERT dbo.TransportPlan (OrderID, RevisionID, CreatedBy, VersionNo,
    FinancialGateStatus, OperationalGateStatus, DocGateStatus, PlanStatus)
VALUES (@order2, @revision2, @manager, 1, 'BLOCKED', 'BLOCKED', 'BLOCKED', 'DRAFT');
SET @plan2 = SCOPE_IDENTITY();
UPDATE dbo.TransportOrder SET CurrentPlanID=@plan WHERE OrderID=@order;
INSERT dbo.TripLeg (PlanID, SequenceNo, TransportMode, StartPoint, EndPoint, DistanceKm,
    LegStatus, StartTimezone, EndTimezone, ConnectionBufferMinutes)
VALUES (@plan, 1, 'ROAD', N'Test origin', N'Test airport', 200,
    'PLANNED', 'UTC', 'UTC', 30);
SET @leg = SCOPE_IDENTITY();
INSERT dbo.TripLeg (PlanID, SequenceNo, TransportMode, StartPoint, EndPoint, DistanceKm, LegStatus)
VALUES (@plan2, 1, 'ROAD', N'Other origin', N'Other airport', 100, 'PLANNED');
SET @leg2 = SCOPE_IDENTITY();
INSERT dbo.HorseReceipt (PlanID, LegID, ReceivedBy, ReceiptStatus, ReceivedAt, IdentityCheckResult)
VALUES (@plan, @leg, @driver, 'RECEIVED', @now, 'MATCHED');
INSERT dbo.OrderChangeRequest (OrderID, BaseRevisionID, RequestedBy, ChangeType,
    Reason, RequestedChangesJSON, Status)
VALUES (@order, @revision, @customerUser, 'DATE_CHANGE',
    N'Fixture request', N'{"preferredDepartureAt":null}', 'PENDING');
PRINT 'PASS: valid draft, profile, stall policy, accepted quote, schedule, plan and receipt lineage.';

-- Tests that cause ordinary constraint errors are recoverable at a savepoint.
SAVE TRANSACTION beforeNegative;
SET @caught=0;
BEGIN TRY
    UPDATE dbo.TransportOrder SET DraftPayloadJSON=N'not json' WHERE OrderID=@order;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'Invalid draft JSON was accepted.', 1;
PRINT 'PASS: invalid draft JSON rejected.';

SET @caught=0;
BEGIN TRY UPDATE dbo.TransportOrder SET CurrentRevisionID=@revision WHERE OrderID=@order2;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'An unrelated current revision was accepted.', 1;
PRINT 'PASS: current revision must belong to the order.';

SET @caught=0;
BEGIN TRY UPDATE dbo.TransportOrder SET CurrentPlanID=@plan WHERE OrderID=@order2;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'An unrelated current plan was accepted.', 1;
PRINT 'PASS: current plan must belong to the order.';

SET @caught=0;
BEGIN TRY UPDATE dbo.TransportPlan SET AcceptedQuoteID=@quote WHERE PlanID=@plan2;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'An unrelated accepted quote was accepted.', 1;
PRINT 'PASS: plan and accepted quote must share the revision.';

SET @caught=0;
BEGIN TRY UPDATE dbo.PaymentRequest SET QuoteID=@draftQuote WHERE ScheduleID=@schedule;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'A payment schedule from another quote was accepted.', 1;
PRINT 'PASS: payment request and schedule must share the quote.';

SET @caught=0;
BEGIN TRY UPDATE dbo.HorseReceipt SET LegID=@leg2 WHERE LegID=@leg;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'An unrelated receipt leg was accepted.', 1;
PRINT 'PASS: receipt leg must belong to its plan.';

SET @caught=0;
BEGIN TRY INSERT dbo.TransportOrderDetail (OrderID,HorseID) VALUES (@order,@horse);
END TRY BEGIN CATCH IF ERROR_NUMBER() IN (2601,2627) SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'More than one horse detail was accepted.', 1;
PRINT 'PASS: single horse per order preserved.';

SET @caught=0;
BEGIN TRY UPDATE dbo.StallChoice SET SlotQuantity=2 WHERE ChoiceID=@choice;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'A 2-slot shared choice was accepted.', 1;
PRINT 'PASS: shared choices remain 1 or 1.5 slots.';

SET @caught=0;
BEGIN TRY UPDATE dbo.StallFitPolicy SET MaxWeightKg=NULL WHERE PolicyCode='TEST-'+@suffix;
END TRY BEGIN CATCH IF ERROR_NUMBER()=547 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'Incomplete approved fit policy was accepted.', 1;
PRINT 'PASS: approved fit policy requires weight/dimensions/source/reviewer.';

-- Trigger errors use XACT_ABORT ON. The batch rolls back fixtures explicitly.
SET @caught=0;
BEGIN TRY UPDATE dbo.TransportPlan SET AcceptedQuoteID=@draftQuote WHERE PlanID=@plan;
END TRY BEGIN CATCH IF ERROR_NUMBER()=51002 SET @caught=1; ELSE THROW; END CATCH;
IF @caught=0 THROW 51010, 'A draft quote was linked as an accepted quote.', 1;
IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
PRINT 'PASS: draft quote cannot be the accepted operating contract.';
PRINT 'PASS: test fixtures rolled back.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
