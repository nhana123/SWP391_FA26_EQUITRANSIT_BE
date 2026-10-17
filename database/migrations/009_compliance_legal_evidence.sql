-- F08 additive private evidence and phase assessment, after 001-008.
-- Never seeds legal rules, exemptions, permits or file evidence.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.NotificationProjection',N'U') IS NULL
    THROW 51000,'Apply prerequisites through 008 before 009.',1;
IF COL_LENGTH(N'dbo.ComplianceRuleVersion',N'AuthorityRequired') IS NULL
    ALTER TABLE dbo.ComplianceRuleVersion ADD AuthorityRequired BIT NOT NULL
        CONSTRAINT DF_ComplianceRule_Authority DEFAULT(1);
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'SourceReference') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD SourceReference NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.AuthorityProcedure',N'RuleID') IS NULL
    ALTER TABLE dbo.AuthorityProcedure ADD RuleID BIGINT NULL
        CONSTRAINT FK_AuthorityProcedure_Rule
        REFERENCES dbo.ComplianceRuleVersion(RuleID);
IF COL_LENGTH(N'dbo.DocumentVersion',N'RevokedAt') IS NULL
    ALTER TABLE dbo.DocumentVersion ADD RevokedAt DATETIME2(0) NULL,
        RevokedBy BIGINT NULL REFERENCES dbo.StaffProfile(StaffID),
        RevocationReason NVARCHAR(2000) NULL;
IF OBJECT_ID(N'dbo.ComplianceFile',N'U') IS NULL
    CREATE TABLE dbo.ComplianceFile (
        FileID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        FileKey UNIQUEIDENTIFIER NOT NULL UNIQUE,
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        ContentType VARCHAR(100) NOT NULL,
        OriginalFileName NVARCHAR(255) NOT NULL,
        ContentBytes VARBINARY(MAX) NOT NULL,
        FileHash VARCHAR(128) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_ComplianceFile_Size CHECK
            (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CONSTRAINT CK_ComplianceFile_Type CHECK
            (ContentType IN ('application/pdf','image/png','image/jpeg'))
    );
IF OBJECT_ID(N'dbo.ComplianceOperation',N'U') IS NULL
    CREATE TABLE dbo.ComplianceOperation (
        OrderID BIGINT NOT NULL REFERENCES dbo.TransportOrder(OrderID),
        UserID BIGINT NOT NULL REFERENCES dbo.[User](UserID),
        OperationID UNIQUEIDENTIFIER NOT NULL,
        RequestHash VARCHAR(64) NOT NULL,
        ReceiptJSON NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_ComplianceOperation PRIMARY KEY(OrderID,UserID,OperationID),
        CONSTRAINT CK_ComplianceOperation_JSON CHECK(ISJSON(ReceiptJSON)=1)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklist',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklist (
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        TransitCountriesJSON NVARCHAR(500) NOT NULL,
        RuleIDsJSON NVARCHAR(2000) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT PK_ComplianceChecklist PRIMARY KEY(RevisionID,PhaseCode),
        CONSTRAINT CK_ComplianceChecklist_JSON CHECK
            (ISJSON(TransitCountriesJSON)=1 AND ISJSON(RuleIDsJSON)=1),
        CONSTRAINT CK_ComplianceChecklist_Dates CHECK(ValidUntil>AssessedAt)
    );
IF OBJECT_ID(N'dbo.ComplianceChecklistHistory',N'U') IS NULL
    CREATE TABLE dbo.ComplianceChecklistHistory (
        AssessmentID BIGINT IDENTITY NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL REFERENCES dbo.OrderRevision(RevisionID),
        PhaseCode VARCHAR(50) NOT NULL,
        SnapshotJSON NVARCHAR(MAX) NOT NULL,
        AssessedAt DATETIME2(0) NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CONSTRAINT CK_ComplianceChecklistHistory_JSON
            CHECK(ISJSON(SnapshotJSON)=1)
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.ComplianceFile') AND name='IX_ComplianceFile_Order')
    CREATE INDEX IX_ComplianceFile_Order ON dbo.ComplianceFile(OrderID,FileID);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE
    object_id=OBJECT_ID(N'dbo.DocumentRequirement')
    AND name='IX_DocumentRequirement_PhaseRule')
    CREATE INDEX IX_DocumentRequirement_PhaseRule ON
        dbo.DocumentRequirement(RevisionID,PhaseCode,RuleID);
COMMIT TRANSACTION;
