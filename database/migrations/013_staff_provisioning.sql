-- F15: additive staff metadata, CAS and private idempotent receipts.
-- Apply through the reviewed deployment process; never on app startup.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.StaffProfile','StaffStatus') IS NULL
    ALTER TABLE dbo.StaffProfile ADD StaffStatus varchar(20) NOT NULL
        CONSTRAINT DF_StaffProfile_StaffStatus DEFAULT 'ACTIVE' WITH VALUES;
IF COL_LENGTH('dbo.StaffProfile','EmployeeCode') IS NULL
    ALTER TABLE dbo.StaffProfile ADD EmployeeCode varchar(50) NULL;
IF COL_LENGTH('dbo.StaffProfile','Department') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Department nvarchar(100) NULL;
IF COL_LENGTH('dbo.StaffProfile','Specialization') IS NULL
    ALTER TABLE dbo.StaffProfile ADD Specialization nvarchar(200) NULL;
IF COL_LENGTH('dbo.StaffProfile','RecordVersion') IS NULL
    ALTER TABLE dbo.StaffProfile ADD RecordVersion rowversion NOT NULL;

IF OBJECT_ID('dbo.CK_StaffProfile_StaffStatus','C') IS NULL
    EXEC('ALTER TABLE dbo.StaffProfile WITH CHECK ADD CONSTRAINT '
        + 'CK_StaffProfile_StaffStatus CHECK '
        + '(StaffStatus IN (''ACTIVE'',''INACTIVE'',''LOCKED''))');
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID('dbo.StaffProfile')
        AND name='UQ_StaffProfile_EmployeeCode')
    EXEC('CREATE UNIQUE INDEX UQ_StaffProfile_EmployeeCode '
        + 'ON dbo.StaffProfile(EmployeeCode) '
        + 'WHERE EmployeeCode IS NOT NULL');

IF OBJECT_ID('dbo.StaffAdminOperation','U') IS NULL
BEGIN
    CREATE TABLE dbo.StaffAdminOperation (
        UserID bigint NOT NULL,
        OperationID uniqueidentifier NOT NULL,
        RequestHash char(64) NOT NULL,
        ReceiptJSON nvarchar(max) NOT NULL,
        CreatedAt datetime2(0) NOT NULL
            CONSTRAINT DF_StaffAdminOperation_CreatedAt
                DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_StaffAdminOperation
            PRIMARY KEY (UserID,OperationID),
        CONSTRAINT FK_StaffAdminOperation_User
            FOREIGN KEY (UserID) REFERENCES dbo.[User](UserID),
        CONSTRAINT CK_StaffAdminOperation_JSON
            CHECK (ISJSON(ReceiptJSON)=1)
    );
END;
COMMIT TRANSACTION;
