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
