-- Manager price version provenance; apply after 005. No production fixtures.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    THROW 51000, 'Apply migrations 001-005 before tariff management.', 1;
IF COL_LENGTH(N'dbo.ServiceTariff',N'SourceReference') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedBy') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'PublishedAt') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD PublishedAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.ServiceTariff',N'RecordVersion') IS NULL
    ALTER TABLE dbo.ServiceTariff ADD RecordVersion ROWVERSION NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys
              WHERE name=N'FK_ServiceTariff_PublishedBy')
    EXEC(N'ALTER TABLE dbo.ServiceTariff WITH CHECK ADD CONSTRAINT
      FK_ServiceTariff_PublishedBy FOREIGN KEY(PublishedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
-- Preserve legacy rows as unknown provenance; never invent approval evidence.
COMMIT;
