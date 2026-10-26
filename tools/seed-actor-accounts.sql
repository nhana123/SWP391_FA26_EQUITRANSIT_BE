-- Seed accounts for each actor in EquiTransit database
-- Password for all accounts: EquiTransit2026! (Plain-text for SWP391 development)

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

DECLARE @now DATETIME2(6) = SYSUTCDATETIME();
DECLARE @hash VARCHAR(255) = 'EquiTransit2026!';
DECLARE @newUserId BIGINT;

-- Update existing seeded accounts to plain text password
UPDATE dbo.[User]
SET PasswordHash = @hash
WHERE Email IN (
    'huy832002@gmail.com',
    'manager@equitransit.com',
    'specialist@equitransit.com',
    'coordinator@equitransit.com',
    'driver@equitransit.com'
);

-- 1. CUSTOMER: huy832002@gmail.com
IF NOT EXISTS (SELECT 1 FROM dbo.[User] WHERE Email = 'huy832002@gmail.com')
BEGIN
    INSERT INTO dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
    VALUES ((SELECT RoleID FROM dbo.Role WHERE RoleCode = 'CUSTOMER'),
            'huy832002@gmail.com', @hash, 'ACTIVE', @now);
    SET @newUserId = SCOPE_IDENTITY();

    INSERT INTO dbo.Customer (UserID, FullName, Phone, Address, EmailNotificationsEnabled)
    VALUES (@newUserId, N'Tăng Gia Huy', '+84901234567', N'Ho Chi Minh City, Vietnam', 1);
    PRINT 'Inserted CUSTOMER: huy832002@gmail.com';
END
ELSE
BEGIN
    PRINT 'CUSTOMER huy832002@gmail.com already exists.';
END

-- 2. MANAGER: manager@equitransit.com
IF NOT EXISTS (SELECT 1 FROM dbo.[User] WHERE Email = 'manager@equitransit.com')
BEGIN
    INSERT INTO dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
    VALUES ((SELECT RoleID FROM dbo.Role WHERE RoleCode = 'MANAGER'),
            'manager@equitransit.com', @hash, 'ACTIVE', @now);
    SET @newUserId = SCOPE_IDENTITY();

    INSERT INTO dbo.StaffProfile (UserID, FullName, Phone, LicenseNumber, StaffStatus, EmployeeCode, Department, Specialization)
    VALUES (@newUserId, N'Operations Manager', '+84902345678', 'MGR-LIC-001', 'ACTIVE', 'MGR-001', N'Operations Management', N'General Fleet & Case Oversight');
    PRINT 'Inserted MANAGER: manager@equitransit.com';
END
ELSE
BEGIN
    PRINT 'MANAGER manager@equitransit.com already exists.';
END

-- 3. SPECIALIST: specialist@equitransit.com
IF NOT EXISTS (SELECT 1 FROM dbo.[User] WHERE Email = 'specialist@equitransit.com')
BEGIN
    INSERT INTO dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
    VALUES ((SELECT RoleID FROM dbo.Role WHERE RoleCode = 'SPECIALIST'),
            'specialist@equitransit.com', @hash, 'ACTIVE', @now);
    SET @newUserId = SCOPE_IDENTITY();

    INSERT INTO dbo.StaffProfile (UserID, FullName, Phone, LicenseNumber, StaffStatus, EmployeeCode, Department, Specialization)
    VALUES (@newUserId, N'Compliance Specialist', '+84903456789', 'SPC-LIC-001', 'ACTIVE', 'SPC-001', N'Legal & Veterinary Compliance', N'Equine Quarantine & Cross-Border Customs');
    PRINT 'Inserted SPECIALIST: specialist@equitransit.com';
END
ELSE
BEGIN
    PRINT 'SPECIALIST specialist@equitransit.com already exists.';
END

-- 4. COORDINATOR: coordinator@equitransit.com
IF NOT EXISTS (SELECT 1 FROM dbo.[User] WHERE Email = 'coordinator@equitransit.com')
BEGIN
    INSERT INTO dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
    VALUES ((SELECT RoleID FROM dbo.Role WHERE RoleCode = 'COORDINATOR'),
            'coordinator@equitransit.com', @hash, 'ACTIVE', @now);
    SET @newUserId = SCOPE_IDENTITY();

    INSERT INTO dbo.StaffProfile (UserID, FullName, Phone, LicenseNumber, StaffStatus, EmployeeCode, Department, Specialization)
    VALUES (@newUserId, N'Multimodal Coordinator', '+84904567890', 'CRD-LIC-001', 'ACTIVE', 'CRD-001', N'Logistics Coordination', N'Air Stall Charter & Trip Planning');
    PRINT 'Inserted COORDINATOR: coordinator@equitransit.com';
END
ELSE
BEGIN
    PRINT 'COORDINATOR coordinator@equitransit.com already exists.';
END

-- 5. DRIVER: driver@equitransit.com
IF NOT EXISTS (SELECT 1 FROM dbo.[User] WHERE Email = 'driver@equitransit.com')
BEGIN
    INSERT INTO dbo.[User] (RoleID, Email, PasswordHash, Status, CreatedAt)
    VALUES ((SELECT RoleID FROM dbo.Role WHERE RoleCode = 'DRIVER'),
            'driver@equitransit.com', @hash, 'ACTIVE', @now);
    SET @newUserId = SCOPE_IDENTITY();

    INSERT INTO dbo.StaffProfile (UserID, FullName, Phone, LicenseNumber, StaffStatus, EmployeeCode, Department, Specialization)
    VALUES (@newUserId, N'Lead Equine Driver', '+84905678901', 'DRV-LIC-001', 'ACTIVE', 'DRV-001', N'Ground Fleet Operations', N'Long-Haul Horse Welfare & Highway Transport');
    PRINT 'Inserted DRIVER: driver@equitransit.com';
END
ELSE
BEGIN
    PRINT 'DRIVER driver@equitransit.com already exists.';
END
