SET NOCOUNT ON;
IF DB_NAME()<>N'EquiTransit_Demo' THROW 51000,'Wrong catalog',1;
SELECT t.name tableName,i.name indexName,
 (SELECT c.name columnName FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0 ORDER BY ic.key_ordinal FOR JSON PATH) AS keyColumns
FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id WHERE i.is_unique=1 AND i.has_filter=0 AND i.is_disabled=0 AND i.type>0 FOR JSON PATH;
