-- Isolated integration database. Never replace its name with EquiTransit.
USE master;
GO
IF DB_ID(N'EquiTransit_AuthTest') IS NULL
    EXEC(N'CREATE DATABASE [EquiTransit_AuthTest]');
GO
USE EquiTransit_AuthTest;
GO
IF OBJECT_ID(N'dbo.Role', N'U') IS NULL
    CREATE TABLE dbo.Role (
        RoleID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RoleCode VARCHAR(30) NOT NULL UNIQUE,
        Description NVARCHAR(255) NULL
    );
IF OBJECT_ID(N'dbo.User', N'U') IS NULL
    CREATE TABLE dbo.[User] (
        UserID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        RoleID BIGINT NOT NULL REFERENCES dbo.Role(RoleID),
        Email VARCHAR(255) NOT NULL UNIQUE,
        PasswordHash VARCHAR(255) NOT NULL,
        Status VARCHAR(20) NOT NULL DEFAULT ('ACTIVE'),
        CreatedAt DATETIME2(0) NOT NULL DEFAULT (SYSUTCDATETIME()),
        CHECK (Status IN ('ACTIVE','INACTIVE','LOCKED'))
    );
IF OBJECT_ID(N'dbo.Customer', N'U') IS NULL
    CREATE TABLE dbo.Customer (
        CustomerID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        UserID BIGINT NOT NULL UNIQUE REFERENCES dbo.[User](UserID),
        FullName NVARCHAR(150) NOT NULL,
        Phone VARCHAR(30) NULL,
        Address NVARCHAR(500) NULL
    );
