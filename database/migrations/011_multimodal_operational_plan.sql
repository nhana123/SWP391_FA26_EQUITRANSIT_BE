-- F10 operational planning; review then deploy explicitly. No seed data.
-- Requires the existing 001..010 baseline. Never auto-run on EquiTransit.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.ResourceOperation','U') IS NULL
    THROW 51000, 'Deploy migration 010 first.', 1;
IF OBJECT_ID('dbo.PlanOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.PlanOperation (
        OrderID BIGINT NOT NULL,
        UserID BIGINT NOT NULL,
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_PlanOperation PRIMARY KEY (OrderID,UserID,OperationID),
        CONSTRAINT FK_PlanOperation_Order FOREIGN KEY (OrderID)
            REFERENCES dbo.TransportOrder(OrderID),
        CONSTRAINT FK_PlanOperation_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_PlanOperation_JSON CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TripAssignment') AND
    name='IX_Plan_StaffWindow')
    CREATE INDEX IX_Plan_StaffWindow ON dbo.TripAssignment
        (StaffID,AssignedFrom,AssignedTo)
        INCLUDE (LegID,RoleInTrip,Status,BufferMinutes);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.TransportPlan') AND
    name='IX_Plan_OrderSchedule')
    CREATE INDEX IX_Plan_OrderSchedule ON dbo.TransportPlan
        (OrderID,PlannedStartTime,PlannedEndTime)
        INCLUDE (PlanID,RevisionID,PlanStatus,AcceptedQuoteID);
GO
CREATE OR ALTER TRIGGER dbo.TR_Plan_DriverSchedule
ON dbo.TripAssignment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    DECLARE @r INT;
    EXEC @r=sys.sp_getapplock @Resource='EquiTransit:resources',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
    IF @r<0 THROW 51001, 'Resource scheduling busy.', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.TripLeg l ON l.LegID=i.LegID
        LEFT JOIN dbo.StaffProfile s WITH (HOLDLOCK) ON s.StaffID=i.StaffID
        LEFT JOIN dbo.[User] u WITH (HOLDLOCK) ON u.UserID=s.UserID
        LEFT JOIN dbo.[Role] r WITH (HOLDLOCK) ON r.RoleID=u.RoleID
        WHERE i.Status NOT IN ('CANCELLED','CANCELED','INACTIVE') AND
        (s.StaffID IS NULL OR u.Status<>'ACTIVE'
         OR (i.RoleInTrip='DRIVER' AND (l.TransportMode<>'ROAD'
             OR r.RoleCode<>'DRIVER'))
         OR COALESCE(i.BufferMinutes,0)<0
         OR i.AssignedFrom IS NULL OR i.AssignedTo IS NULL
         OR i.AssignedFrom>=i.AssignedTo))
        THROW 51006, 'Trip staff mode, role or window invalid.', 1;
    IF EXISTS (SELECT 1 FROM inserted a JOIN dbo.TripAssignment b
        WITH (UPDLOCK,HOLDLOCK) ON a.StaffID=b.StaffID
        AND a.AssignmentID<>b.AssignmentID
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
         (a.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,b.AssignedTo)
          AND b.AssignedFrom<DATEADD(minute,CASE WHEN COALESCE(a.BufferMinutes,0)>=
              COALESCE(b.BufferMinutes,0) THEN COALESCE(a.BufferMinutes,0)
              ELSE COALESCE(b.BufferMinutes,0) END,a.AssignedTo))))
        THROW 51007, 'Driver or staff has an overlapping trip assignment.', 1;
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
    UPDATE l SET LegStatus=l.LegStatus FROM dbo.TripLeg l
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE a SET Status=a.Status FROM dbo.StallAssignment a
        JOIN dbo.TripLeg l ON l.LegID=a.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
    UPDATE t SET Status=t.Status FROM dbo.TripAssignment t
        JOIN dbo.TripLeg l ON l.LegID=t.LegID
        JOIN inserted i ON i.PlanID=l.PlanID;
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.TransportOrder oa ON oa.OrderID=i.OrderID
        JOIN dbo.TransportOrderDetail da ON da.OrderID=oa.OrderID
        JOIN dbo.TransportOrderDetail db ON db.HorseID=da.HorseID
            AND db.OrderID<>da.OrderID
        JOIN dbo.TransportOrder ob WITH (UPDLOCK,HOLDLOCK)
            ON ob.OrderID=db.OrderID
        JOIN dbo.TransportPlan pb WITH (UPDLOCK,HOLDLOCK)
            ON pb.PlanID=ob.CurrentPlanID AND pb.OrderID=ob.OrderID
        WHERE i.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND ob.OrderStatus NOT IN
            ('COMPLETED','DELIVERED','CANCELLED','CANCELED','CLOSED','REJECTED')
        AND pb.PlanStatus IN ('CONFIRMED','ACTIVE','IN_TRANSIT')
        AND (i.PlannedStartTime IS NULL OR i.PlannedEndTime IS NULL OR
             pb.PlannedStartTime IS NULL OR pb.PlannedEndTime IS NULL OR
             (i.PlannedStartTime<pb.PlannedEndTime AND
              pb.PlannedStartTime<i.PlannedEndTime)))
        THROW 51008, 'Horse has an overlapping active operational plan.', 1;
END;
GO
COMMIT TRANSACTION;
