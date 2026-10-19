-- Additive refresh credentials. No raw bearer or cookie value is persisted.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.RefreshTokenSession', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RefreshTokenSession (
        TokenID bigint IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_RefreshTokenSession PRIMARY KEY,
        FamilyID uniqueidentifier NOT NULL,
        UserID bigint NOT NULL,
        TokenHash char(64) NOT NULL,
        CredentialHash char(64) NOT NULL,
        IssuedAt datetime2(0) NOT NULL,
        ExpiresAt datetime2(0) NOT NULL,
        RevokedAt datetime2(0) NULL,
        RevokeReason varchar(40) NULL,
        ReplacedByTokenID bigint NULL,
        CONSTRAINT UQ_RefreshTokenSession_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_RefreshTokenSession_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID),
        CONSTRAINT FK_RefreshTokenSession_Replacement
            FOREIGN KEY (ReplacedByTokenID)
            REFERENCES dbo.RefreshTokenSession(TokenID),
        CONSTRAINT CK_RefreshTokenSession_Expiry
            CHECK (ExpiresAt > IssuedAt)
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_User')
    CREATE INDEX IX_RefreshTokenSession_User
        ON dbo.RefreshTokenSession(UserID,RevokedAt,ExpiresAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'dbo.RefreshTokenSession')
        AND name=N'IX_RefreshTokenSession_Family')
    CREATE INDEX IX_RefreshTokenSession_Family
        ON dbo.RefreshTokenSession(FamilyID);
COMMIT TRANSACTION;
