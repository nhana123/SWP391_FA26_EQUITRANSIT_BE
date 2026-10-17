-- F08: compliance documents and versioned legal evidence.
-- After 001-004. Transactional and rerunnable; no business data is seeded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF @@TRANCOUNT <> 0
    THROW 51008, 'Run migration 005 without an existing transaction.', 1;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 51008, 'Select the intended EquiTransit application/test database.', 1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) <> 1
    THROW 51008, 'VIEW DEFINITION is required to verify existing schema.', 1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult = sys.sp_getapplock
    @Resource = N'EquiTransitSchemaMigration', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51008, 'Another schema migration holds the lock.', 1;

-- Required BE1 tables.
DECLARE @Dependencies TABLE (TableName sysname, ColumnName sysname);
INSERT @Dependencies VALUES
    (N'User', N'UserID'), (N'StaffProfile', N'StaffID'),
    (N'Horse', N'HorseID'), (N'OrderRevision', N'RevisionID');
IF EXISTS (
    SELECT 1 FROM @Dependencies d
    LEFT JOIN sys.tables t ON t.name = d.TableName
        AND t.schema_id = SCHEMA_ID(N'dbo')
    LEFT JOIN sys.columns c ON c.object_id = t.object_id
        AND c.name = d.ColumnName
    WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id) <> N'bigint'
        OR c.is_nullable <> 0 OR NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns k ON k.object_id = i.object_id
                AND k.index_id = i.index_id AND k.key_ordinal = 1
            WHERE i.object_id = t.object_id AND i.is_unique = 1
                AND i.is_disabled = 0 AND i.has_filter = 0
                AND k.column_id = c.column_id AND NOT EXISTS (
                    SELECT 1 FROM sys.index_columns k2
                    WHERE k2.object_id = i.object_id AND k2.index_id = i.index_id
                        AND k2.key_ordinal > 1)))
    THROW 51008, 'Missing/incompatible BE1 key: apply the team baseline first.', 1;

-- Validate existing table columns before creating missing F08 objects.
DECLARE @ExpectedColumns TABLE (
    TableName sysname, ColumnName sysname, TypeName sysname,
    MaxLength int NULL, PrecisionValue int NULL, ScaleValue int NULL,
    Nullable bit, IsIdentity bit, SeedValue bigint NULL, IncrementValue bigint NULL
);
INSERT @ExpectedColumns VALUES

    (N'DocumentTypeCatalog', N'TypeCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentTypeCatalog', N'DisplayName', N'nvarchar', 400, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentTypeCatalog', N'Description', N'nvarchar', 2000, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentTypeCatalog', N'IsActive', N'bit', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'RuleID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'ComplianceRuleVersion', N'RuleCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'VersionNo', N'int', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'OriginCountryCode', N'varchar', 2, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'DestinationCountryCode', N'varchar', 2, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'DocumentType', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'PhaseCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'SourceURL', N'nvarchar', 4096, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'ReviewedAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'EffectiveFrom', N'datetime2', NULL, NULL, 0, 0, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'EffectiveTo', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'TransitCountryCode', N'varchar', 2, NULL, NULL, 1, 0, NULL, NULL),
    (N'ComplianceRuleVersion', N'RuleDescription', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL),
    (N'HorseDocument', N'DocumentID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'HorseDocument', N'HorseID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'HorseDocument', N'DocumentType', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'HorseDocument', N'CreatedAt', N'datetime2', NULL, NULL, 0, 0, 0, NULL, NULL),
    (N'DocumentVersion', N'DocumentVersionID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'DocumentVersion', N'DocumentID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentVersion', N'VersionNo', N'int', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentVersion', N'DocumentNumber', N'varchar', 50, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'FileKey', N'nvarchar', 2048, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentVersion', N'FileHash', N'varchar', 128, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'IssuedDate', N'date', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'ExpiryDate', N'date', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'UploadedAt', N'datetime2', NULL, NULL, 0, 0, 0, NULL, NULL),
    (N'DocumentVersion', N'UploadedBy', N'bigint', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'OriginalFileName', N'nvarchar', 510, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'ContentType', N'varchar', 100, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentVersion', N'FileSizeBytes', N'bigint', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'RequirementID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'DocumentRequirement', N'RevisionID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'RuleID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'DocumentType', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'PhaseCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'IsMandatory', N'bit', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'DueAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'Status', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentRequirement', N'Instructions', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'ScopeCode', N'varchar', 20, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'HorseID', N'bigint', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'ValidityConditionsJSON', N'nvarchar', -1, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentRequirement', N'CaseJustification', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentSubmission', N'SubmissionID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'DocumentSubmission', N'RequirementID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentSubmission', N'DocumentVersionID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentSubmission', N'Status', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentSubmission', N'SubmittedAt', N'datetime2', NULL, NULL, 0, 0, 0, NULL, NULL),
    (N'DocumentReview', N'ReviewID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'DocumentReview', N'SubmissionID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentReview', N'ReviewedBy', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentReview', N'Decision', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'DocumentReview', N'Feedback', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL),
    (N'DocumentReview', N'ReviewedAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'ProcedureID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'AuthorityProcedure', N'RevisionID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'AuthorityProcedure', N'ProcedureCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'AuthorityProcedure', N'AuthorityName', N'nvarchar', 600, NULL, NULL, 0, 0, NULL, NULL),
    (N'AuthorityProcedure', N'ApplicationReference', N'varchar', 150, NULL, NULL, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'ApprovalStatus', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'AuthorityProcedure', N'EvidenceFileKey', N'nvarchar', 2048, NULL, NULL, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'DecidedAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'PhaseCode', N'varchar', 50, NULL, NULL, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'AuthorityContact', N'nvarchar', 1000, NULL, NULL, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'HoldReason', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'ExpiresAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'AuthorityProcedure', N'RecordedBy', N'bigint', NULL, NULL, NULL, 1, 0, NULL, NULL),
    (N'ComplianceClearance', N'ClearanceID', N'bigint', NULL, NULL, NULL, 0, 1, 1, 1),
    (N'ComplianceClearance', N'RevisionID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceClearance', N'SpecialistID', N'bigint', NULL, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceClearance', N'PhaseCode', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceClearance', N'Status', N'varchar', 50, NULL, NULL, 0, 0, NULL, NULL),
    (N'ComplianceClearance', N'CheckedAt', N'datetime2', NULL, NULL, 0, 0, 0, NULL, NULL),
    (N'ComplianceClearance', N'ExpiresAt', N'datetime2', NULL, NULL, 0, 1, 0, NULL, NULL),
    (N'ComplianceClearance', N'AssessmentNotes', N'nvarchar', 4000, NULL, NULL, 1, 0, NULL, NULL);

IF EXISTS (
    SELECT 1 FROM @ExpectedColumns e
    JOIN sys.tables t ON t.name = e.TableName
        AND t.schema_id = SCHEMA_ID(N'dbo')
    LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = e.ColumnName
    LEFT JOIN sys.identity_columns ic ON ic.object_id = c.object_id
        AND ic.column_id = c.column_id
    WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id) <> e.TypeName
        OR (e.MaxLength IS NOT NULL AND c.max_length <> e.MaxLength)
        OR (e.PrecisionValue IS NOT NULL AND c.precision <> e.PrecisionValue)
        OR (e.ScaleValue IS NOT NULL AND c.scale <> e.ScaleValue)
        OR c.is_nullable <> e.Nullable OR c.is_identity <> e.IsIdentity
        OR c.is_computed <> 0
        OR (e.IsIdentity = 1 AND (
            CONVERT(bigint, ic.seed_value) <> e.SeedValue
            OR CONVERT(bigint, ic.increment_value) <> e.IncrementValue)))
    THROW 51008, 'Existing F08 table differs from snapshot; review its columns.', 1;


-- 1. DocumentTypeCatalog: S04/C11
IF OBJECT_ID(N'dbo.DocumentTypeCatalog', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[DocumentTypeCatalog](
    [TypeCode] [varchar](50) NOT NULL,
    [DisplayName] [nvarchar](200) NOT NULL,
    [Description] [nvarchar](1000) NULL,
    [IsActive] [bit] NOT NULL,
 CONSTRAINT [PK_DocumentTypeCatalog] PRIMARY KEY CLUSTERED
(
    [TypeCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id
        AND c.column_id = d.parent_column_id
    WHERE d.parent_object_id = OBJECT_ID(N'dbo.DocumentTypeCatalog')
        AND c.name = N'IsActive')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentTypeCatalog] ADD  DEFAULT ((1)) FOR [IsActive]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentTypeCatalog')
        AND name = N'CK_DocumentTypeCatalog_Code')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentTypeCatalog]  WITH CHECK ADD  CONSTRAINT [CK_DocumentTypeCatalog_Code] CHECK  ((len(ltrim(rtrim([TypeCode])))>(0)))';

-- 2. ComplianceRuleVersion: S04
IF OBJECT_ID(N'dbo.ComplianceRuleVersion', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[ComplianceRuleVersion](
    [RuleID] [bigint] IDENTITY(1,1) NOT NULL,
    [RuleCode] [varchar](50) NOT NULL,
    [VersionNo] [int] NOT NULL,
    [OriginCountryCode] [varchar](2) NOT NULL,
    [DestinationCountryCode] [varchar](2) NOT NULL,
    [DocumentType] [varchar](50) NOT NULL,
    [PhaseCode] [varchar](50) NOT NULL,
    [SourceURL] [nvarchar](2048) NOT NULL,
    [ReviewedAt] [datetime2](0) NULL,
    [EffectiveFrom] [datetime2](0) NOT NULL,
    [EffectiveTo] [datetime2](0) NULL,
    [TransitCountryCode] [varchar](2) NULL,
    [RuleDescription] [nvarchar](2000) NULL,
 CONSTRAINT [PK_ComplianceRuleVersion] PRIMARY KEY CLUSTERED
(
    [RuleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceRuleVersion')
        AND name = N'UQ_ComplianceRuleVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceRuleVersion] ADD  CONSTRAINT [UQ_ComplianceRuleVersion_Version] UNIQUE NONCLUSTERED
(
    [RuleCode] ASC,
    [VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceRuleVersion')
        AND name = N'FK_Figma_ComplianceRuleVersion_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [FK_Figma_ComplianceRuleVersion_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceRuleVersion')
        AND name = N'CK_ComplianceRuleVersion_EffectiveTo_Range')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [CK_ComplianceRuleVersion_EffectiveTo_Range] CHECK  (([EffectiveTo] IS NULL OR [EffectiveFrom] IS NOT NULL AND [EffectiveTo]>=[EffectiveFrom]))';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceRuleVersion')
        AND name = N'CK_ComplianceRuleVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceRuleVersion]  WITH CHECK ADD  CONSTRAINT [CK_ComplianceRuleVersion_VersionNo] CHECK  (([VersionNo]>(0)))';

-- 3. HorseDocument: C03/C11
IF OBJECT_ID(N'dbo.HorseDocument', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[HorseDocument](
    [DocumentID] [bigint] IDENTITY(1,1) NOT NULL,
    [HorseID] [bigint] NOT NULL,
    [DocumentType] [varchar](50) NOT NULL,
    [CreatedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_HorseDocument] PRIMARY KEY CLUSTERED
(
    [DocumentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.HorseDocument')
        AND name = N'IX_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_HorseDocument_HorseID] ON [dbo].[HorseDocument]
(
    [HorseID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id
        AND c.column_id = d.parent_column_id
    WHERE d.parent_object_id = OBJECT_ID(N'dbo.HorseDocument')
        AND c.name = N'CreatedAt')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseDocument] ADD  CONSTRAINT [DF_HorseDocument_CreatedAt]  DEFAULT (sysutcdatetime()) FOR [CreatedAt]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.HorseDocument')
        AND name = N'FK_Figma_HorseDocument_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseDocument]  WITH CHECK ADD  CONSTRAINT [FK_Figma_HorseDocument_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.HorseDocument')
        AND name = N'FK_HorseDocument_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[HorseDocument]  WITH CHECK ADD  CONSTRAINT [FK_HorseDocument_HorseID] FOREIGN KEY([HorseID])
REFERENCES [dbo].[Horse] ([HorseID])';

-- 4. DocumentVersion: C03/C11/S03
IF OBJECT_ID(N'dbo.DocumentVersion', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[DocumentVersion](
    [DocumentVersionID] [bigint] IDENTITY(1,1) NOT NULL,
    [DocumentID] [bigint] NOT NULL,
    [VersionNo] [int] NOT NULL,
    [DocumentNumber] [varchar](50) NULL,
    [FileKey] [nvarchar](1024) NOT NULL,
    [FileHash] [varchar](128) NULL,
    [IssuedDate] [date] NULL,
    [ExpiryDate] [date] NULL,
    [UploadedAt] [datetime2](0) NOT NULL,
    [UploadedBy] [bigint] NULL,
    [OriginalFileName] [nvarchar](255) NULL,
    [ContentType] [varchar](100) NULL,
    [FileSizeBytes] [bigint] NULL,
 CONSTRAINT [PK_DocumentVersion] PRIMARY KEY CLUSTERED
(
    [DocumentVersionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'UQ_DocumentVersion_Version')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion] ADD  CONSTRAINT [UQ_DocumentVersion_Version] UNIQUE NONCLUSTERED
(
    [DocumentID] ASC,
    [VersionNo] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id
        AND c.column_id = d.parent_column_id
    WHERE d.parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND c.name = N'UploadedAt')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion] ADD  CONSTRAINT [DF_DocumentVersion_UploadedAt]  DEFAULT (sysutcdatetime()) FOR [UploadedAt]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'FK_DocumentVersion_DocumentID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [FK_DocumentVersion_DocumentID] FOREIGN KEY([DocumentID])
REFERENCES [dbo].[HorseDocument] ([DocumentID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'FK_Figma_DocumentVersion_UploadedBy')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentVersion_UploadedBy] FOREIGN KEY([UploadedBy])
REFERENCES [dbo].[User] ([UserID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'CK_DocumentVersion_ExpiryDate_Range')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_DocumentVersion_ExpiryDate_Range] CHECK  (([ExpiryDate] IS NULL OR [IssuedDate] IS NOT NULL AND [ExpiryDate]>=[IssuedDate]))';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'CK_DocumentVersion_VersionNo')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_DocumentVersion_VersionNo] CHECK  (([VersionNo]>(0)))';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentVersion')
        AND name = N'CK_Figma_DocumentVersion_FileSizeBytes')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentVersion]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentVersion_FileSizeBytes] CHECK  (([FileSizeBytes]>(0)))';

-- 5. DocumentRequirement: S04/C11
IF OBJECT_ID(N'dbo.DocumentRequirement', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[DocumentRequirement](
    [RequirementID] [bigint] IDENTITY(1,1) NOT NULL,
    [RevisionID] [bigint] NOT NULL,
    [RuleID] [bigint] NOT NULL,
    [DocumentType] [varchar](50) NOT NULL,
    [PhaseCode] [varchar](50) NOT NULL,
    [IsMandatory] [bit] NOT NULL,
    [DueAt] [datetime2](0) NULL,
    [Status] [varchar](50) NOT NULL,
    [Instructions] [nvarchar](2000) NULL,
    [ScopeCode] [varchar](20) NULL,
    [HorseID] [bigint] NULL,
    [ValidityConditionsJSON] [nvarchar](max) NULL,
    [CaseJustification] [nvarchar](2000) NULL,
 CONSTRAINT [PK_DocumentRequirement] PRIMARY KEY CLUSTERED
(
    [RequirementID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'IX_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentRequirement_RevisionID] ON [dbo].[DocumentRequirement]
(
    [RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'IX_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentRequirement_RuleID] ON [dbo].[DocumentRequirement]
(
    [RuleID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'FK_DocumentRequirement_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_DocumentRequirement_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'FK_DocumentRequirement_RuleID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_DocumentRequirement_RuleID] FOREIGN KEY([RuleID])
REFERENCES [dbo].[ComplianceRuleVersion] ([RuleID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'FK_Figma_DocumentRequirement_DocumentType')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentRequirement_DocumentType] FOREIGN KEY([DocumentType])
REFERENCES [dbo].[DocumentTypeCatalog] ([TypeCode])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'FK_Figma_DocumentRequirement_HorseID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [FK_Figma_DocumentRequirement_HorseID] FOREIGN KEY([HorseID])
REFERENCES [dbo].[Horse] ([HorseID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'CK_Figma_DocumentRequirement_Scope')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentRequirement_Scope] CHECK  (([ScopeCode] IS NULL OR [ScopeCode]=''HORSE'' AND [HorseID] IS NOT NULL OR [ScopeCode]=''SHIPMENT''))';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentRequirement')
        AND name = N'CK_Figma_DocumentRequirement_ValidityConditionsJSON')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentRequirement]  WITH CHECK ADD  CONSTRAINT [CK_Figma_DocumentRequirement_ValidityConditionsJSON] CHECK  (([ValidityConditionsJSON] IS NULL OR isjson([ValidityConditionsJSON])=(1)))';

-- 6. DocumentSubmission: C11/S03
IF OBJECT_ID(N'dbo.DocumentSubmission', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[DocumentSubmission](
    [SubmissionID] [bigint] IDENTITY(1,1) NOT NULL,
    [RequirementID] [bigint] NOT NULL,
    [DocumentVersionID] [bigint] NOT NULL,
    [Status] [varchar](50) NOT NULL,
    [SubmittedAt] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_DocumentSubmission] PRIMARY KEY CLUSTERED
(
    [SubmissionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentSubmission')
        AND name = N'IX_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentSubmission_DocumentVersionID] ON [dbo].[DocumentSubmission]
(
    [DocumentVersionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentSubmission')
        AND name = N'IX_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentSubmission_RequirementID] ON [dbo].[DocumentSubmission]
(
    [RequirementID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id
        AND c.column_id = d.parent_column_id
    WHERE d.parent_object_id = OBJECT_ID(N'dbo.DocumentSubmission')
        AND c.name = N'SubmittedAt')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentSubmission] ADD  CONSTRAINT [DF_DocumentSubmission_SubmittedAt]  DEFAULT (sysutcdatetime()) FOR [SubmittedAt]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentSubmission')
        AND name = N'FK_DocumentSubmission_DocumentVersionID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentSubmission]  WITH CHECK ADD  CONSTRAINT [FK_DocumentSubmission_DocumentVersionID] FOREIGN KEY([DocumentVersionID])
REFERENCES [dbo].[DocumentVersion] ([DocumentVersionID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentSubmission')
        AND name = N'FK_DocumentSubmission_RequirementID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentSubmission]  WITH CHECK ADD  CONSTRAINT [FK_DocumentSubmission_RequirementID] FOREIGN KEY([RequirementID])
REFERENCES [dbo].[DocumentRequirement] ([RequirementID])';

-- 7. DocumentReview: S03/C11
IF OBJECT_ID(N'dbo.DocumentReview', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[DocumentReview](
    [ReviewID] [bigint] IDENTITY(1,1) NOT NULL,
    [SubmissionID] [bigint] NOT NULL,
    [ReviewedBy] [bigint] NOT NULL,
    [Decision] [varchar](50) NOT NULL,
    [Feedback] [nvarchar](2000) NULL,
    [ReviewedAt] [datetime2](0) NULL,
 CONSTRAINT [PK_DocumentReview] PRIMARY KEY CLUSTERED
(
    [ReviewID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentReview')
        AND name = N'IX_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentReview_ReviewedBy] ON [dbo].[DocumentReview]
(
    [ReviewedBy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.DocumentReview')
        AND name = N'IX_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_DocumentReview_SubmissionID] ON [dbo].[DocumentReview]
(
    [SubmissionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentReview')
        AND name = N'FK_DocumentReview_ReviewedBy')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentReview]  WITH CHECK ADD  CONSTRAINT [FK_DocumentReview_ReviewedBy] FOREIGN KEY([ReviewedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.DocumentReview')
        AND name = N'FK_DocumentReview_SubmissionID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[DocumentReview]  WITH CHECK ADD  CONSTRAINT [FK_DocumentReview_SubmissionID] FOREIGN KEY([SubmissionID])
REFERENCES [dbo].[DocumentSubmission] ([SubmissionID])';

-- 8. AuthorityProcedure: S05
IF OBJECT_ID(N'dbo.AuthorityProcedure', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[AuthorityProcedure](
    [ProcedureID] [bigint] IDENTITY(1,1) NOT NULL,
    [RevisionID] [bigint] NOT NULL,
    [ProcedureCode] [varchar](50) NOT NULL,
    [AuthorityName] [nvarchar](300) NOT NULL,
    [ApplicationReference] [varchar](150) NULL,
    [ApprovalStatus] [varchar](50) NOT NULL,
    [EvidenceFileKey] [nvarchar](1024) NULL,
    [DecidedAt] [datetime2](0) NULL,
    [PhaseCode] [varchar](50) NULL,
    [AuthorityContact] [nvarchar](500) NULL,
    [HoldReason] [nvarchar](2000) NULL,
    [ExpiresAt] [datetime2](0) NULL,
    [RecordedBy] [bigint] NULL,
 CONSTRAINT [PK_AuthorityProcedure] PRIMARY KEY CLUSTERED
(
    [ProcedureID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.AuthorityProcedure')
        AND name = N'IX_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_AuthorityProcedure_RevisionID] ON [dbo].[AuthorityProcedure]
(
    [RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.AuthorityProcedure')
        AND name = N'FK_AuthorityProcedure_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[AuthorityProcedure]  WITH CHECK ADD  CONSTRAINT [FK_AuthorityProcedure_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.AuthorityProcedure')
        AND name = N'FK_Figma_AuthorityProcedure_RecordedBy')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[AuthorityProcedure]  WITH CHECK ADD  CONSTRAINT [FK_Figma_AuthorityProcedure_RecordedBy] FOREIGN KEY([RecordedBy])
REFERENCES [dbo].[StaffProfile] ([StaffID])';

-- 9. ComplianceClearance: S02/S05
IF OBJECT_ID(N'dbo.ComplianceClearance', N'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE [dbo].[ComplianceClearance](
    [ClearanceID] [bigint] IDENTITY(1,1) NOT NULL,
    [RevisionID] [bigint] NOT NULL,
    [SpecialistID] [bigint] NOT NULL,
    [PhaseCode] [varchar](50) NOT NULL,
    [Status] [varchar](50) NOT NULL,
    [CheckedAt] [datetime2](0) NOT NULL,
    [ExpiresAt] [datetime2](0) NULL,
    [AssessmentNotes] [nvarchar](2000) NULL,
 CONSTRAINT [PK_ComplianceClearance] PRIMARY KEY CLUSTERED
(
    [ClearanceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.ComplianceClearance')
        AND name = N'IX_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_ComplianceClearance_RevisionID] ON [dbo].[ComplianceClearance]
(
    [RevisionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.ComplianceClearance')
        AND name = N'IX_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'CREATE NONCLUSTERED INDEX [IX_ComplianceClearance_SpecialistID] ON [dbo].[ComplianceClearance]
(
    [SpecialistID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]';
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id
        AND c.column_id = d.parent_column_id
    WHERE d.parent_object_id = OBJECT_ID(N'dbo.ComplianceClearance')
        AND c.name = N'CheckedAt')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceClearance] ADD  CONSTRAINT [DF_ComplianceClearance_CheckedAt]  DEFAULT (sysutcdatetime()) FOR [CheckedAt]';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceClearance')
        AND name = N'FK_ComplianceClearance_RevisionID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceClearance]  WITH CHECK ADD  CONSTRAINT [FK_ComplianceClearance_RevisionID] FOREIGN KEY([RevisionID])
REFERENCES [dbo].[OrderRevision] ([RevisionID])';
IF NOT EXISTS (SELECT 1 FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ComplianceClearance')
        AND name = N'FK_ComplianceClearance_SpecialistID')
    EXEC sys.sp_executesql N'ALTER TABLE [dbo].[ComplianceClearance]  WITH CHECK ADD  CONSTRAINT [FK_ComplianceClearance_SpecialistID] FOREIGN KEY([SpecialistID])
REFERENCES [dbo].[StaffProfile] ([StaffID])';

-- Check existing constraints and primary keys.
IF EXISTS (
    SELECT 1 FROM sys.objects o
    JOIN (SELECT DISTINCT TableName FROM @ExpectedColumns) e
        ON o.parent_object_id = OBJECT_ID(N'dbo.' + e.TableName)
    LEFT JOIN sys.foreign_keys f ON f.object_id = o.object_id
    LEFT JOIN sys.check_constraints c ON c.object_id = o.object_id
    WHERE f.is_disabled = 1 OR f.is_not_trusted = 1
        OR c.is_disabled = 1 OR c.is_not_trusted = 1)
    THROW 51008, 'F08 contains disabled/untrusted constraints; review legacy data.', 1;

IF EXISTS (
    SELECT 1 FROM (SELECT DISTINCT TableName FROM @ExpectedColumns) e
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.key_constraints k
        WHERE k.parent_object_id = OBJECT_ID(N'dbo.' + e.TableName)
            AND k.type = N'PK'))
    THROW 51008, 'An existing F08 table is missing its primary key.', 1;

COMMIT TRANSACTION;
PRINT N'Migration 005 completed: F08 schema exists; no business rows were changed.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
