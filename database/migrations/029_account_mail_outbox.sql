SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo', 'EquiTransit_Test',
        'EquiTransit_AuthTest')
    THROW 51029, 'Account mail migration is restricted to Demo/Test.', 1;
IF OBJECT_ID('dbo.[User]', 'U') IS NULL
    THROW 51029, 'Account tables must exist before migration029.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.AccountMailOutbox', 'U') IS NULL
    CREATE TABLE dbo.AccountMailOutbox (
        MailID uniqueidentifier NOT NULL PRIMARY KEY,
        UserID bigint NOT NULL,
        Purpose varchar(32) NOT NULL,
        ChallengeHash varchar(64) NULL,
        EncryptedPayload nvarchar(max) NULL,
        Status varchar(20) NOT NULL,
        Attempts int NOT NULL DEFAULT 0,
        CreatedAt datetime2(0) NOT NULL,
        ExpiresAt datetime2(0) NOT NULL,
        AvailableAt datetime2(0) NOT NULL,
        LeaseUntil datetime2(0) NULL,
        LeaseToken uniqueidentifier NULL,
        CompletedAt datetime2(0) NULL,
        LastErrorCode varchar(40) NULL,
        CONSTRAINT FK_AccountMailOutbox_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID) ON DELETE CASCADE,
        CONSTRAINT CK_AccountMailOutbox_Attempts
            CHECK (Attempts BETWEEN 0 AND 5),
        CONSTRAINT CK_AccountMailOutbox_Status CHECK (Status IN (
            'PENDING','PROCESSING','SMTP_ACCEPTED','FILE_WRITTEN',
            'FAILED','CANCELLED','EXPIRED'))
    );
IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.AccountMailOutbox')
        AND name = 'IX_AccountMailOutbox_Due')
    CREATE INDEX IX_AccountMailOutbox_Due ON dbo.AccountMailOutbox
        (Status, AvailableAt, LeaseUntil) INCLUDE (CreatedAt, ExpiresAt);
IF OBJECT_ID('dbo.AuthRequestBudget', 'U') IS NULL
    CREATE TABLE dbo.AuthRequestBudget (
        BudgetKey varchar(64) NOT NULL PRIMARY KEY,
        WindowEnd datetime2(0) NOT NULL,
        Requests int NOT NULL,
        CONSTRAINT CK_AuthRequestBudget_Requests CHECK (Requests >= 0)
    );
IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.AuthRequestBudget')
        AND name = 'IX_AuthRequestBudget_WindowEnd')
    CREATE INDEX IX_AuthRequestBudget_WindowEnd ON dbo.AuthRequestBudget
        (WindowEnd);
IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
        WHERE MigrationID = '029_account_mail_outbox')
    INSERT dbo.SchemaMigrationHistory(MigrationID, AppliedAt, AppliedBy)
        VALUES ('029_account_mail_outbox', SYSUTCDATETIME(), SUSER_SNAME());
COMMIT;
