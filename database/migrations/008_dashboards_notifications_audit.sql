SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    THROW 51000,'Apply F07 migration007 before F16.',1;
IF COL_LENGTH('dbo.AuditEvent','EventKey') IS NULL
    ALTER TABLE dbo.AuditEvent ADD EventKey VARCHAR(64) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.AuditEvent') AND name='UQ_AuditEvent_EventKey')
    EXEC('CREATE UNIQUE INDEX UQ_AuditEvent_EventKey
      ON dbo.AuditEvent(EventKey) WHERE EventKey IS NOT NULL');
IF COL_LENGTH('dbo.Notification','SourceEventID') IS NULL
    ALTER TABLE dbo.Notification ADD SourceEventID BIGINT NULL
        REFERENCES dbo.AuditEvent(EventID), EmailState VARCHAR(20) NULL,
        EmailLeaseUntil DATETIME2(0) NULL, EmailAttempts INT NOT NULL DEFAULT 0;
IF OBJECT_ID('dbo.NotificationProjection','U') IS NULL
BEGIN
    CREATE TABLE dbo.NotificationProjection (
      EventID BIGINT NOT NULL PRIMARY KEY REFERENCES dbo.AuditEvent(EventID),
      ProjectedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME());
    -- Establish deployment watermark; do not email old audit history.
    INSERT dbo.NotificationProjection(EventID)
        SELECT EventID FROM dbo.AuditEvent;
END;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='UQ_Notification_EventRecipient')
    EXEC('CREATE UNIQUE INDEX UQ_Notification_EventRecipient
      ON dbo.Notification(SourceEventID,UserID)
      WHERE SourceEventID IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Notification')
    AND name='IX_Notification_RecipientPage')
    CREATE INDEX IX_Notification_RecipientPage ON dbo.Notification
      (UserID,NotificationID DESC) INCLUDE (OrderID,ReadAt,CreatedAt);
COMMIT;
