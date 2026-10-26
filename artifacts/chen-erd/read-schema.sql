SET NOCOUNT ON;
IF DB_NAME() <> N'EquiTransit_Demo' THROW 51000,'Wrong catalog',1;
SELECT DB_NAME() AS catalog,
 (SELECT t.name AS tableName,c.name AS columnName,ty.name AS dataType,c.max_length,c.is_nullable,c.is_identity,
 CASE WHEN EXISTS(SELECT 1 FROM sys.indexes ix JOIN sys.index_columns ic ON ic.object_id=ix.object_id AND ic.index_id=ix.index_id WHERE ix.object_id=t.object_id AND ix.is_primary_key=1 AND ic.column_id=c.column_id) THEN 1 ELSE 0 END AS isPrimaryKey
 FROM sys.tables t JOIN sys.schemas sc ON sc.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id WHERE sc.name='dbo' ORDER BY t.name,c.column_id FOR JSON PATH) AS columns,
 (SELECT fk.name AS constraintName,pt.name AS childTable,pc.name AS childColumn,rt.name AS parentTable,rc.name AS parentColumn,pc.is_nullable AS nullable,
 CASE WHEN EXISTS(SELECT 1 FROM sys.indexes ix JOIN sys.index_columns ic ON ic.object_id=ix.object_id AND ic.index_id=ix.index_id WHERE ix.object_id=pt.object_id AND ix.is_unique=1 AND ic.column_id=pc.column_id AND (SELECT COUNT(*) FROM sys.index_columns z WHERE z.object_id=ix.object_id AND z.index_id=ix.index_id AND z.key_ordinal>0)=1 AND ix.has_filter=0) THEN 1 ELSE 0 END AS uniqueChild,
 fk.is_disabled,fk.is_not_trusted
 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=fk.object_id JOIN sys.tables pt ON pt.object_id=fk.parent_object_id JOIN sys.tables rt ON rt.object_id=fk.referenced_object_id JOIN sys.columns pc ON pc.object_id=pt.object_id AND pc.column_id=fc.parent_column_id JOIN sys.columns rc ON rc.object_id=rt.object_id AND rc.column_id=fc.referenced_column_id ORDER BY pt.name,fk.name,fc.constraint_column_id FOR JSON PATH) AS foreignKeys
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
