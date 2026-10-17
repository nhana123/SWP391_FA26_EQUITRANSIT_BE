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
                 AND hr.ReceiptStatus='ACCEPTED')))
        THROW 51010, 'First leg departure requires PASS gates, manager authorization and matched horse receipt.', 1;
END;
GO
COMMIT TRANSACTION;
