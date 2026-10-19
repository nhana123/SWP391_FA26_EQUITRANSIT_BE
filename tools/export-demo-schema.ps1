<# Live metadata only. Never exports business rows or modifies the source. #>
[CmdletBinding()]
param(
    [string]$Server = 'localhost,1433',
    [string]$SourceDatabase = 'EquiTransit',
    [Parameter(Mandatory = $true)][string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$builder = [Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source'] = $Server
$builder['Initial Catalog'] = $SourceDatabase
$builder['Integrated Security'] = $true
$builder['Encrypt'] = $true
$builder['TrustServerCertificate'] = $true
$connection = [Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
$connection.Open()
$command = $connection.CreateCommand()
$command.CommandText = @'
SET NOCOUNT ON;
IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.tables t
 ON t.object_id=c.object_id WHERE t.is_ms_shipped=0
 AND (c.is_computed=1 OR c.is_sparse=1 OR c.generated_always_type<>0))
 THROW 51000,'Unsupported column metadata; review export first.',1;
IF EXISTS(SELECT 1 FROM sys.tables WHERE is_ms_shipped=0 AND
 (schema_id<>SCHEMA_ID('dbo') OR temporal_type<>0 OR is_memory_optimized=1))
 THROW 51000,'Unsupported table metadata; review export first.',1;
SELECT 'CREATE TABLE dbo.'+QUOTENAME(t.name)+' ('+CHAR(10)+
 STRING_AGG(CAST('    '+QUOTENAME(c.name)+' '+ty.name+
 CASE WHEN ty.name IN('varchar','char','nvarchar','nchar','binary','varbinary')
 THEN '('+CASE WHEN c.max_length=-1 THEN 'max' ELSE CAST(
 c.max_length/CASE WHEN ty.name IN('nvarchar','nchar') THEN 2 ELSE 1 END
 AS varchar(10)) END+')'
 WHEN ty.name IN('decimal','numeric') THEN '('+CAST(c.precision AS varchar)+
 ','+CAST(c.scale AS varchar)+')'
 WHEN ty.name IN('time','datetime2','datetimeoffset')
 THEN '('+CAST(c.scale AS varchar)+')' ELSE '' END+
 CASE WHEN c.collation_name IS NOT NULL THEN ' COLLATE '+c.collation_name
 ELSE '' END+CASE WHEN ic.column_id IS NOT NULL THEN
 ' IDENTITY('+CAST(ic.seed_value AS varchar)+','+
 CAST(ic.increment_value AS varchar)+')' ELSE '' END+
 CASE WHEN c.is_nullable=1 THEN ' NULL' ELSE ' NOT NULL' END+
 CASE WHEN dc.object_id IS NOT NULL THEN ' CONSTRAINT '+QUOTENAME(dc.name)+
 ' DEFAULT '+dc.definition ELSE '' END AS nvarchar(max)),','+CHAR(10))
 WITHIN GROUP(ORDER BY c.column_id)+CHAR(10)+');' AS ddl
FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
LEFT JOIN sys.identity_columns ic ON ic.object_id=c.object_id
 AND ic.column_id=c.column_id
LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
WHERE t.is_ms_shipped=0 GROUP BY t.name ORDER BY t.name;

SELECT CASE WHEN i.is_primary_key=1 OR i.is_unique_constraint=1 THEN
 'ALTER TABLE dbo.'+QUOTENAME(t.name)+' ADD CONSTRAINT '+QUOTENAME(i.name)+
 CASE WHEN i.is_primary_key=1 THEN ' PRIMARY KEY ' ELSE ' UNIQUE ' END+
 i.type_desc ELSE 'CREATE '+CASE WHEN i.is_unique=1 THEN 'UNIQUE ' ELSE '' END+
 i.type_desc+' INDEX '+QUOTENAME(i.name)+' ON dbo.'+QUOTENAME(t.name) END+
 ' ('+k.keys+')'+CASE WHEN inc.keys IS NOT NULL THEN
 ' INCLUDE ('+inc.keys+')' ELSE '' END+CASE WHEN i.filter_definition IS
 NOT NULL THEN ' WHERE '+i.filter_definition ELSE '' END+';' AS ddl
FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id
CROSS APPLY(SELECT STRING_AGG(CAST(QUOTENAME(c.name)+CASE WHEN
 ic.is_descending_key=1 THEN ' DESC' ELSE ' ASC' END AS nvarchar(max)),',')
 WITHIN GROUP(ORDER BY ic.key_ordinal) AS keys FROM sys.index_columns ic
 JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id
 AND ic.is_included_column=0) k
CROSS APPLY(SELECT STRING_AGG(CAST(QUOTENAME(c.name) AS nvarchar(max)),',')
 AS keys FROM sys.index_columns ic JOIN sys.columns c ON
 c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id
 AND ic.is_included_column=1) inc
WHERE t.is_ms_shipped=0 AND i.index_id>0 AND i.is_hypothetical=0
ORDER BY i.is_primary_key DESC;

SELECT 'ALTER TABLE dbo.'+QUOTENAME(t.name)+' WITH CHECK ADD CONSTRAINT '+
 QUOTENAME(f.name)+' FOREIGN KEY ('+k.parent_keys+') REFERENCES dbo.'+
 QUOTENAME(r.name)+' ('+k.ref_keys+') ON DELETE '+
 REPLACE(f.delete_referential_action_desc,'_',' ')+' ON UPDATE '+
 REPLACE(f.update_referential_action_desc,'_',' ')+';' AS ddl
FROM sys.foreign_keys f JOIN sys.tables t ON t.object_id=f.parent_object_id
JOIN sys.tables r ON r.object_id=f.referenced_object_id
CROSS APPLY(SELECT STRING_AGG(CAST(QUOTENAME(p.name) AS nvarchar(max)),',')
 WITHIN GROUP(ORDER BY c.constraint_column_id) AS parent_keys,
 STRING_AGG(CAST(QUOTENAME(q.name) AS nvarchar(max)),',')
 WITHIN GROUP(ORDER BY c.constraint_column_id) AS ref_keys
 FROM sys.foreign_key_columns c JOIN sys.columns p ON
 p.object_id=c.parent_object_id AND p.column_id=c.parent_column_id
 JOIN sys.columns q ON q.object_id=c.referenced_object_id
 AND q.column_id=c.referenced_column_id WHERE c.constraint_object_id=f.object_id
) k WHERE t.is_ms_shipped=0;
SELECT 'ALTER TABLE dbo.'+QUOTENAME(t.name)+' WITH CHECK ADD CONSTRAINT '+
 QUOTENAME(c.name)+' CHECK '+c.definition+';' AS ddl
FROM sys.check_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id
WHERE t.is_ms_shipped=0;
SELECT m.definition AS ddl FROM sys.sql_modules m
JOIN sys.objects o ON o.object_id=m.object_id WHERE o.is_ms_shipped=0
ORDER BY CASE o.type WHEN 'FN' THEN 0 WHEN 'V' THEN 1
 WHEN 'P' THEN 2 ELSE 3 END,o.name;
'@
try {
    # sys.types/sys.indexes metadata may use different catalog collations.
    foreach ($field in @('ty.name', 'i.type_desc',
            'f.delete_referential_action_desc',
            'f.update_referential_action_desc')) {
        $command.CommandText = $command.CommandText.Replace($field,
            "($field COLLATE DATABASE_DEFAULT)")
    }
    $adapter = [Data.SqlClient.SqlDataAdapter]::new($command)
    $data = [Data.DataSet]::new()
    [void]$adapter.Fill($data)
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;')
    foreach ($table in $data.Tables) {
        foreach ($row in $table.Rows) {
            if ($row.ddl -eq [DBNull]::Value) {
                throw 'Encrypted or unsupported object cannot be exported.'
            }
            $lines.Add($row.ddl)
        }
    }
    [IO.File]::WriteAllText($OutputPath, ($lines -join "`r`nGO`r`n"),
        [Text.UTF8Encoding]::new($false))
    Write-Output "Exported $($data.Tables[0].Rows.Count) live tables."
} finally { $connection.Dispose(); $command.Dispose() }
