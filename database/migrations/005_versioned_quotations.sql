-- F06: source-backed, immutable quotation snapshots. After migrations 001-004.
-- Review/backup before deployment. Never run test fixtures on EquiTransit.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
    THROW 51000, 'Apply migrations 001-004 first.', 1;

IF COL_LENGTH(N'dbo.Quote',N'PricingSnapshotJSON') IS NULL
    ALTER TABLE dbo.Quote ADD PricingSnapshotJSON NVARCHAR(MAX) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RetailUnitPrice') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RetailUnitPrice DECIMAL(18,2) NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedBy') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.SupplierOffer',N'RecordedAt') IS NULL
    ALTER TABLE dbo.SupplierOffer ADD RecordedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_SupplierOffer_RecordedBy')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      FK_SupplierOffer_RecordedBy FOREIGN KEY(RecordedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_Quote_PricingSnapshot')
    EXEC(N'ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT
      CK_Quote_PricingSnapshot CHECK(PricingSnapshotJSON IS NULL
      OR ISJSON(PricingSnapshotJSON)=1);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_SupplierOffer_RetailPrice')
    EXEC(N'ALTER TABLE dbo.SupplierOffer WITH CHECK ADD CONSTRAINT
      CK_SupplierOffer_RetailPrice CHECK(RetailUnitPrice IS NULL
      OR RetailUnitPrice>=0);');

-- A draft needs an expiry before approval, without inventing IssuedAt.
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name=N'CK_Quote_Validity')
    ALTER TABLE dbo.Quote DROP CONSTRAINT CK_Quote_Validity;
ALTER TABLE dbo.Quote WITH CHECK ADD CONSTRAINT CK_Quote_Validity CHECK
    (ValidUntil IS NULL OR
     (IssuedAt IS NULL AND ValidUntil>CreatedAt) OR
     (IssuedAt IS NOT NULL AND ValidUntil>IssuedAt));

IF EXISTS (SELECT RevisionID FROM dbo.Quote WHERE QuoteStatus='ACCEPTED'
           GROUP BY RevisionID HAVING COUNT(*)>1)
    THROW 51001, 'Repair duplicate accepted quotes before migration.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.Quote') AND
               name=N'UX_Quote_AcceptedRevision')
    CREATE UNIQUE INDEX UX_Quote_AcceptedRevision ON dbo.Quote(RevisionID)
        WHERE QuoteStatus='ACCEPTED';
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.QuotePaymentSchedule')
      AND i.is_unique=1 AND i.has_filter=0
      AND (SELECT COUNT(*) FROM sys.index_columns c
           WHERE c.object_id=i.object_id AND c.index_id=i.index_id
             AND c.key_ordinal>0)=2
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'QuoteID')
      AND EXISTS (SELECT 1 FROM sys.index_columns c
                  WHERE c.object_id=i.object_id AND c.index_id=i.index_id
                    AND c.key_ordinal>0
                    AND COL_NAME(c.object_id,c.column_id)=N'SequenceNo'))
    CREATE UNIQUE INDEX UX_QuotePaymentSchedule_Sequence
        ON dbo.QuotePaymentSchedule(QuoteID,SequenceNo);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE
               object_id=OBJECT_ID(N'dbo.AuditEvent') AND
               name=N'IX_AuditEvent_QuotationOperation')
    CREATE INDEX IX_AuditEvent_QuotationOperation
        ON dbo.AuditEvent(EntityType,EntityID,UserID,EventID)
        INCLUDE(ActionCode);
COMMIT;
