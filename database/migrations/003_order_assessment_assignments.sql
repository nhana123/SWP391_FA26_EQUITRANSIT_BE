-- F04: preserve history and serialize current case responsibility.
-- Run after 001/002 on a reviewed database. No rows are deleted or backfilled
-- with invented actors/deadlines. Resolve reported legacy duplicates first.
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
IF OBJECT_ID(N'dbo.OrderAssignment', N'U') IS NULL
    OR OBJECT_ID(N'dbo.OrderDecision', N'U') IS NULL
    THROW 51000, 'Apply the EquiTransit baseline and migration 002 first.', 1;

IF EXISTS (SELECT RevisionID FROM dbo.OrderDecision
           GROUP BY RevisionID HAVING COUNT(*) > 1)
    THROW 51001, 'Multiple decisions for a revision require manual review.', 1;
IF EXISTS (SELECT OrderID, AssignmentRole FROM dbo.OrderAssignment
           WHERE EndedAt IS NULL GROUP BY OrderID, AssignmentRole
           HAVING COUNT(*) > 1)
    THROW 51002, 'Duplicate active case assignments require manual review.', 1;

IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'DueAt') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD DueAt DATETIME2(0) NULL;
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignmentNote') IS NULL
    ALTER TABLE dbo.OrderAssignment ADD AssignmentNote NVARCHAR(1000) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = N'FK_OrderAssignment_AssignedBy')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          FK_OrderAssignment_AssignedBy FOREIGN KEY (AssignedBy)
          REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'CK_OrderAssignment_DueAt')
    EXEC(N'ALTER TABLE dbo.OrderAssignment WITH CHECK ADD CONSTRAINT
          CK_OrderAssignment_DueAt CHECK
          (DueAt IS NULL OR DueAt >= AssignedAt);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderDecision')
                 AND name = N'UX_OrderDecision_Revision')
    CREATE UNIQUE INDEX UX_OrderDecision_Revision
        ON dbo.OrderDecision(RevisionID);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'UX_OrderAssignment_ActiveRole')
    CREATE UNIQUE INDEX UX_OrderAssignment_ActiveRole
        ON dbo.OrderAssignment(OrderID, AssignmentRole)
        WHERE EndedAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.OrderAssignment')
                 AND name = N'IX_OrderAssignment_ActiveStaff')
    CREATE INDEX IX_OrderAssignment_ActiveStaff
        ON dbo.OrderAssignment(StaffID, OrderID) WHERE EndedAt IS NULL;
COMMIT TRANSACTION;
