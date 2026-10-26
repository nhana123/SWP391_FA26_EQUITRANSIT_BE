-- ==============================================================================
-- SWP391: REMOVE PASSWORD HASHING (Convert all passwords to plain text)
-- Run this script in SQL Server Management Studio (SSMS) on your EquiTransit DB
-- ==============================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

-- 1. Reset all actor/staff and customer passwords to plain text: EquiTransit2026!
UPDATE dbo.[User]
SET PasswordHash = 'EquiTransit2026!'
WHERE PasswordHash LIKE '{bcrypt}%'
   OR PasswordHash LIKE 'TEST-%'
   OR PasswordHash LIKE 'SYNTHETIC-%';

PRINT 'All user accounts updated to plain text password: EquiTransit2026!';

-- 2. Verify results
SELECT 
    u.UserID,
    r.RoleCode,
    u.Email,
    u.PasswordHash,
    u.Status,
    COALESCE(c.FullName, sp.FullName, N'—') AS FullName
FROM dbo.[User] u
JOIN dbo.Role r ON u.RoleID = r.RoleID
LEFT JOIN dbo.Customer c ON u.UserID = c.UserID
LEFT JOIN dbo.StaffProfile sp ON u.UserID = sp.UserID
ORDER BY u.UserID ASC;
