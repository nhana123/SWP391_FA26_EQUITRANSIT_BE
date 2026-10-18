-- F11: runtime lookup and first-leg departure guard.
-- After 009. Gate values are PASS/BLOCKED; receipt values ACCEPTED/REJECTED.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
IF @@TRANCOUNT <> 0
    THROW 51011, 'Run migration 010 without an existing transaction.', 1;
IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
    THROW 51011, 'Select the intended application/test database.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @r int;
EXEC @r=sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @r<0 THROW 51011, 'Another schema migration holds the lock.', 1;
IF OBJECT_ID(N'dbo.PlanOperation',N'U') IS NULL
    OR OBJECT_ID(N'dbo.HorseReceipt',N'U') IS NULL
    THROW 51011, 'Apply migrations 008-009 first.', 1;
EXEC sys.sp_executesql N'IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(''dbo.HorseReceipt'') AND
    name=''IX_HorseReceipt_PlanLeg'')
    CREATE INDEX IX_HorseReceipt_PlanLeg ON dbo.HorseReceipt
        (PlanID,LegID,ReceiptID DESC)
        INCLUDE (ReceivedBy,IdentityCheckResult,ReceiptStatus,ReceivedAt);';
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Departure_FirstLegGuard
ON dbo.TripLeg AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE SequenceNo=1
        AND (ActualDeparture IS NOT NULL OR LegStatus=''IN_TRANSIT''))
        RETURN;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        LEFT JOIN dbo.StaffProfile m ON m.StaffID=p.DepartureAuthorizedBy
        LEFT JOIN dbo.[User] u ON u.UserID=m.UserID
        LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID
        WHERE i.SequenceNo=1
        AND (i.ActualDeparture IS NOT NULL OR i.LegStatus=''IN_TRANSIT'')
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
END;';
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
