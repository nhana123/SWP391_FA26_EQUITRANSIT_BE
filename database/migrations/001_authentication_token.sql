-- Add only the persistence required by Figma A03 verification/recovery.
-- Existing User, Role and Customer tables and rows remain unchanged.
IF OBJECT_ID(N'dbo.AuthenticationToken', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AuthenticationToken (
        TokenID BIGINT IDENTITY(1,1) NOT NULL,
        UserID BIGINT NOT NULL,
        TokenHash VARCHAR(64) NOT NULL,
        Purpose VARCHAR(20) NOT NULL,
        ExpiresAt DATETIME2(0) NOT NULL,
        UsedAt DATETIME2(0) NULL,
        CreatedAt DATETIME2(0) NOT NULL,
        CONSTRAINT PK_AuthenticationToken PRIMARY KEY (TokenID),
        CONSTRAINT UQ_AuthenticationToken_Hash UNIQUE (TokenHash),
        CONSTRAINT FK_AuthenticationToken_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User] (UserID),
        CONSTRAINT CK_AuthenticationToken_Purpose CHECK (
            Purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')
        ),
        CONSTRAINT CK_AuthenticationToken_Expiry
            CHECK (ExpiresAt > CreatedAt)
    );
    CREATE INDEX IX_AuthenticationToken_UserPurpose
        ON dbo.AuthenticationToken (UserID, Purpose);
END;
