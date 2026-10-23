-- BE-02 sandbox: preserve exact expiry for one-step document submissions.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() <> N'EquiTransit_Demo'
    THROW 51020, 'BE-02 migration is restricted to EquiTransit_Demo.', 1;
IF COL_LENGTH('dbo.DocumentVersion','ExpiresAt') IS NULL
    ALTER TABLE dbo.DocumentVersion ADD ExpiresAt DATETIME2(0) NULL;
