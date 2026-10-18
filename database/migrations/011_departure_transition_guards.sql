-- F11: strengthen first-leg departure transition guards.
-- After 010. Existing receipt rows and source tables are preserved.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
IF @@TRANCOUNT<>0
    THROW 51011, 'Run migration 011 without an existing transaction.', 1;
IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
    THROW 51011, 'Select the intended application/test database.', 1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @r int;
EXEC @r=sys.sp_getapplock @Resource=N'EquiTransitSchemaMigration',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @r<0 THROW 51011, 'Another schema migration holds the lock.', 1;
IF OBJECT_ID(N'dbo.TR_Departure_FirstLegGuard',N'TR') IS NULL
    THROW 51011, 'Apply migration 010 first.', 1;
EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.TR_Departure_FirstLegGuard
ON dbo.TripLeg AFTER UPDATE AS
BEGIN
    -- F11_GATE_GUARD_V2: validate the transition, not later tracking updates.
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.LegID=i.LegID
        JOIN dbo.TransportPlan p ON p.PlanID=i.PlanID
        JOIN dbo.TransportOrder o ON o.OrderID=p.OrderID
        LEFT JOIN dbo.StaffProfile m ON m.StaffID=p.DepartureAuthorizedBy
        LEFT JOIN dbo.[User] u ON u.UserID=m.UserID
        LEFT JOIN dbo.[Role] r ON r.RoleID=u.RoleID
        OUTER APPLY (SELECT TOP (1) h.* FROM dbo.HorseReceipt h
            WHERE h.PlanID=i.PlanID AND h.LegID=i.LegID
            ORDER BY h.ReceiptID DESC) h
        WHERE i.SequenceNo=1
        AND ((d.ActualDeparture IS NULL AND i.ActualDeparture IS NOT NULL)
            OR (i.LegStatus=''IN_TRANSIT'' AND d.LegStatus<>''IN_TRANSIT''))
        AND (i.TransportMode<>''ROAD'' OR i.ActualDeparture IS NULL
            OR d.ActualDeparture IS NOT NULL OR d.LegStatus<>''CONFIRMED''
            OR p.PlanStatus<>''CONFIRMED''
            OR o.CurrentPlanID IS NULL OR o.CurrentPlanID<>p.PlanID
            OR o.CurrentRevisionID IS NULL OR o.CurrentRevisionID<>p.RevisionID
            OR o.OrderStatus NOT IN (''APPROVED'',''IN_PREPARATION'',''CONFIRMED'')
            OR p.FinancialGateStatus<>''PASS'' OR p.DocGateStatus<>''PASS''
            OR p.OperationalGateStatus<>''PASS'' OR p.GatesCheckedAt IS NULL
            OR p.GatesCheckedAt>i.ActualDeparture
            OR p.DepartureAuthorizedBy IS NULL
            OR p.DepartureAuthorizedAt IS NULL
            OR p.DepartureAuthorizedAt>i.ActualDeparture
            OR u.Status IS NULL OR u.Status<>''ACTIVE''
            OR r.RoleCode IS NULL OR r.RoleCode<>''MANAGER''
            OR h.ReceiptID IS NULL OR h.IdentityCheckResult IS NULL
            OR h.IdentityCheckResult<>''MATCHED'' OR h.ReceiptStatus<>''ACCEPTED''
            OR h.ReceivedAt>i.ActualDeparture
            OR h.ConditionNotes IS NULL OR LEN(LTRIM(RTRIM(h.ConditionNotes)))=0
            OR h.EvidenceFileKey IS NULL OR LEN(LTRIM(RTRIM(h.EvidenceFileKey)))=0
            OR NOT EXISTS (SELECT 1 FROM dbo.TripAssignment a
                JOIN dbo.StaffProfile ds ON ds.StaffID=a.StaffID
                JOIN dbo.[User] du ON du.UserID=ds.UserID
                JOIN dbo.[Role] dr ON dr.RoleID=du.RoleID
                WHERE a.LegID=i.LegID AND a.RoleInTrip=''DRIVER''
                AND a.Status IN (''CONFIRMED'',''ACTIVE'')
                AND a.AssignedFrom<=i.ActualDeparture
                AND a.AssignedTo>i.ActualDeparture
                AND du.Status=''ACTIVE'' AND dr.RoleCode=''DRIVER'')))
        THROW 51010, ''First-leg departure requires current plan, live authorization and latest matched receipt.'', 1;
END;';
EXEC sys.sp_executesql N'ENABLE TRIGGER dbo.TR_Departure_FirstLegGuard ON dbo.TripLeg;';
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
