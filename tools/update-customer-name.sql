UPDATE dbo.Customer 
SET FullName = N'Tăng Gia Huy' 
WHERE FullName LIKE '%Huy%';

SELECT CustomerID, FullName FROM dbo.Customer;
