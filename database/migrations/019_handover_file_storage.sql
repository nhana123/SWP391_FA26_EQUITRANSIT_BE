-- F14 private handover and actual-cost evidence storage.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF @@TRANCOUNT <> 0
    THROW 51019, 'Run migration 019 without an existing transaction.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51019, 'Another schema migration holds the lock.', 1;

IF OBJECT_ID(N'dbo.HandoverFile', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.HandoverFile (
        FileID BIGINT IDENTITY(1,1) NOT NULL,
        FileKey UNIQUEIDENTIFIER NOT NULL,
        OrderID BIGINT NOT NULL,
        UploadedBy BIGINT NOT NULL,
        Purpose VARCHAR(32) NOT NULL,
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash CHAR(64) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL
            CONSTRAINT DF_HandoverFile_CreatedAt DEFAULT (sysutcdatetime()),
        CONSTRAINT PK_HandoverFile PRIMARY KEY CLUSTERED (FileID),
        CONSTRAINT UQ_HandoverFile_FileKey UNIQUE (FileKey),
        CONSTRAINT CK_HandoverFile_Purpose CHECK (Purpose IN (
            'HANDOVER_SIGNATURE', 'HANDOVER_PHOTO',
            'ACTUAL_COST_EVIDENCE')),
        CONSTRAINT CK_HandoverFile_Content CHECK (
            DATALENGTH(ContentBytes) > 0 AND DATALENGTH(ContentBytes)
            <= 5242880)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id=OBJECT_ID(N'dbo.HandoverFile')
        AND name=N'IX_HandoverFile_OrderID')
    CREATE INDEX IX_HandoverFile_OrderID
        ON dbo.HandoverFile(OrderID, Purpose);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_Order')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_Order FOREIGN KEY (OrderID)
        REFERENCES dbo.TransportOrder(OrderID);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE name=N'FK_HandoverFile_UploadedBy')
    ALTER TABLE dbo.HandoverFile WITH CHECK ADD CONSTRAINT
        FK_HandoverFile_UploadedBy FOREIGN KEY (UploadedBy)
        REFERENCES dbo.[User](UserID);

IF OBJECT_ID(N'dbo.SchemaMigrationHistory', N'U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
        WHERE MigrationID='019_handover_file_storage')
    INSERT dbo.SchemaMigrationHistory(MigrationID)
        VALUES ('019_handover_file_storage');

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
