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
