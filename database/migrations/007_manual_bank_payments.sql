-- F07 additive provenance, immutable evidence and idempotent receipts.
-- Requires 005; 006 is reserved for tariff management. No real DB fixtures.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Quote','PricingSnapshotJSON') IS NULL
    THROW 51000,'Apply quotation migration005 first.',1;
IF OBJECT_ID('dbo.PaymentOperation','U') IS NULL
    CREATE TABLE dbo.PaymentOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL CHECK(ISJSON(ReceiptJSON)=1),
        CONSTRAINT PK_PaymentOperation PRIMARY KEY(OrderID,UserID,OperationID)
    );
IF OBJECT_ID('dbo.PaymentProof','U') IS NULL
    CREATE TABLE dbo.PaymentProof (
        FileKey UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        MediaType VARCHAR(20) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_PaymentProof_Size CHECK
          (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_PaymentProof_Mime CHECK
          (MediaType IN ('image/png','image/jpeg'))
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.PaymentRequest')
    AND name='UQ_PaymentRequest_Schedule')
    CREATE UNIQUE INDEX UQ_PaymentRequest_Schedule
      ON dbo.PaymentRequest(ScheduleID) WHERE ScheduleID IS NOT NULL;
IF COL_LENGTH('dbo.PaymentTransaction','ReversalReason') IS NULL
    ALTER TABLE dbo.PaymentTransaction ADD ReversalReason NVARCHAR(2000) NULL,
        ReversedAt DATETIME2(0) NULL, ReversedBy BIGINT NULL
        REFERENCES dbo.StaffProfile(StaffID),
        ReversalProofFileKey UNIQUEIDENTIFIER NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID('dbo.Refund') AND name='UQ_Refund_BankReference')
    CREATE UNIQUE INDEX UQ_Refund_BankReference
      ON dbo.Refund(BankRefundReference) WHERE BankRefundReference IS NOT NULL;
COMMIT;
