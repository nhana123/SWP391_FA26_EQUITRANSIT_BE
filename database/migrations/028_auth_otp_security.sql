SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo', 'EquiTransit_Test',
        'EquiTransit_AuthTest')
    THROW 51028, 'OTP migration is restricted to Demo/Test.', 1;
IF OBJECT_ID('dbo.AuthenticationToken', 'U') IS NULL
    THROW 51028, 'AuthenticationToken must exist before migration028.', 1;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.AuthenticationToken', 'OtpHash') IS NULL
    ALTER TABLE dbo.AuthenticationToken ADD OtpHash varchar(64) NULL;
IF COL_LENGTH('dbo.AuthenticationToken', 'OtpExpiresAt') IS NULL
    ALTER TABLE dbo.AuthenticationToken ADD OtpExpiresAt datetime2(0) NULL;
IF COL_LENGTH('dbo.AuthenticationToken', 'FailedAttempts') IS NULL
    ALTER TABLE dbo.AuthenticationToken ADD FailedAttempts int NOT NULL
        CONSTRAINT DF_AuthenticationToken_FailedAttempts DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID('dbo.AuthenticationToken')
        AND name = 'CK_AuthenticationToken_FailedAttempts')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.AuthenticationToken
        ADD CONSTRAINT CK_AuthenticationToken_FailedAttempts
        CHECK (FailedAttempts BETWEEN 0 AND 5)';
IF OBJECT_ID('dbo.SchemaMigrationHistory', 'U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
        WHERE MigrationID = '028_auth_otp_security')
    INSERT dbo.SchemaMigrationHistory(MigrationID, AppliedAt, AppliedBy)
        VALUES ('028_auth_otp_security', SYSUTCDATETIME(), SUSER_SNAME());
COMMIT;
