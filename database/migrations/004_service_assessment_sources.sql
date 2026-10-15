-- F05 read contracts for sourced itinerary and Specialist quantity assessment.
-- After 001/002/003. Does not approve existing distances or seed demo prices.
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
IF COL_LENGTH(N'dbo.OrderAssignment', N'AssignedBy') IS NULL
    THROW 51000, 'Apply migration 003 before migration 004.', 1;

IF COL_LENGTH(N'dbo.TripLeg', N'DistanceSource') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceSource NVARCHAR(1000) NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedBy') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedBy BIGINT NULL;
IF COL_LENGTH(N'dbo.TripLeg', N'DistanceConfirmedAt') IS NULL
    ALTER TABLE dbo.TripLeg ADD DistanceConfirmedAt DATETIME2(0) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name=N'FK_TripLeg_DistanceConfirmedBy')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      FK_TripLeg_DistanceConfirmedBy FOREIGN KEY (DistanceConfirmedBy)
      REFERENCES dbo.StaffProfile(StaffID);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name=N'CK_TripLeg_DistanceSource')
    EXEC(N'ALTER TABLE dbo.TripLeg WITH CHECK ADD CONSTRAINT
      CK_TripLeg_DistanceSource CHECK
      ((DistanceSource IS NULL AND DistanceConfirmedBy IS NULL
        AND DistanceConfirmedAt IS NULL) OR
       (DistanceSource IS NOT NULL AND LEN(LTRIM(RTRIM(DistanceSource)))>0
        AND DistanceConfirmedBy IS NOT NULL AND DistanceConfirmedAt IS NOT NULL));');

IF OBJECT_ID(N'dbo.RevisionQuarantineAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionQuarantineAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        Applicability VARCHAR(20) NOT NULL,
        RequiredDays INT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionQuarantineAssessment_Outcome CHECK
            ((Applicability='REQUIRED' AND RequiredDays IS NOT NULL
              AND RequiredDays>0)
              OR (Applicability='NOT_REQUIRED' AND RequiredDays IS NULL)),
        CONSTRAINT CK_RevisionQuarantineAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionQuarantineAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionQuarantineAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;

IF OBJECT_ID(N'dbo.RevisionVeterinaryAssessment',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RevisionVeterinaryAssessment (
        AssessmentID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RevisionID BIGINT NOT NULL
            REFERENCES dbo.OrderRevision(RevisionID),
        VisitCount INT NOT NULL,
        TestCount INT NOT NULL,
        DoseCount INT NOT NULL,
        CertificateCount INT NOT NULL,
        SpecialistID BIGINT NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        AssessedAt DATETIME2(0) NOT NULL,
        ValidUntil DATETIME2(0) NOT NULL,
        SourceReference NVARCHAR(1000) NOT NULL,
        SupersededAt DATETIME2(0) NULL,
        CONSTRAINT CK_RevisionVeterinaryAssessment_Counts CHECK
            (VisitCount>=0 AND TestCount>=0 AND DoseCount>=0
              AND CertificateCount>=0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Dates CHECK
            (ValidUntil>AssessedAt),
        CONSTRAINT CK_RevisionVeterinaryAssessment_Source CHECK
            (LEN(LTRIM(RTRIM(SourceReference)))>0),
        CONSTRAINT CK_RevisionVeterinaryAssessment_History CHECK
            (SupersededAt IS NULL OR SupersededAt>=AssessedAt)
    );
END;
IF COL_LENGTH(N'dbo.RevisionQuarantineAssessment',N'SupersededAt') IS NULL
    OR COL_LENGTH(N'dbo.RevisionVeterinaryAssessment',N'SupersededAt') IS NULL
    THROW 51001, 'Review existing assessment tables before applying 004.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionQuarantineAssessment')
      AND name=N'UX_QuarantineAssessment_Current')
    CREATE UNIQUE INDEX UX_QuarantineAssessment_Current
        ON dbo.RevisionQuarantineAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RevisionVeterinaryAssessment')
      AND name=N'UX_VeterinaryAssessment_Current')
    CREATE UNIQUE INDEX UX_VeterinaryAssessment_Current
        ON dbo.RevisionVeterinaryAssessment(RevisionID)
        WHERE SupersededAt IS NULL;
COMMIT TRANSACTION;
