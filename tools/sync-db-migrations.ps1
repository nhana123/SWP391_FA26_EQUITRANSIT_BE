<#
Rebuild a local/test schema from the checked-in baseline, then run 001..latest.
Existing rows are retained. Production catalog EquiTransit is refused.
#>
[CmdletBinding()]
param(
    [string]$Server = 'localhost,1433',
    [ValidatePattern('^EquiTransit_FigmaMigrationTest_[A-Za-z0-9_]+$')]
    [string]$Database = 'EquiTransit_FigmaMigrationTest_20261013_143729',
    [switch]$CreateDatabase,
    [switch]$Backup,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$migrationDir = Join-Path $backendRoot 'database/migrations'

function Open-Database([string]$Catalog) {
    $builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder['Data Source'] = $Server
    $builder['Initial Catalog'] = $Catalog
    $builder['Integrated Security'] = $true
    $builder['Encrypt'] = $true
    $builder['TrustServerCertificate'] = $true
    $builder['Connect Timeout'] = 10
    $builder['Application Name'] = 'EquiTransitSchemaSync'
    $connection = [System.Data.SqlClient.SqlConnection]::new(
        $builder.ConnectionString)
    $connection.Open()
    return $connection
}

function Invoke-SqlFile($Connection, [string]$Path) {
    $sql = [IO.File]::ReadAllText($Path)
    # The existing scripts use standalone GO, not SQLCMD variables/includes.
    $batches = [regex]::Split($sql, '(?im)^\s*GO\s*(?:--[^\r\n]*)?$')
    foreach ($batch in $batches) {
        if ([string]::IsNullOrWhiteSpace($batch)) { continue }
        $command = $Connection.CreateCommand()
        $command.CommandTimeout = 120
        $command.CommandText = $batch
        try {
            $adapter = [System.Data.SqlClient.SqlDataAdapter]::new($command)
            $results = [System.Data.DataSet]::new()
            try {
                $null = $adapter.Fill($results)
                foreach ($table in $results.Tables) {
                    foreach ($row in $table.Rows) {
                        $values = [ordered]@{}
                        foreach ($column in $table.Columns) {
                            $values[$column.ColumnName] = $row[$column]
                        }
                        [pscustomobject]$values
                    }
                }
            } finally {
                $adapter.Dispose()
                $results.Dispose()
            }
        } catch {
            throw "Failed $([IO.Path]::GetFileName($Path)): $_"
        } finally { $command.Dispose() }
    }
}

$connection = $null
try {
    $master = Open-Database 'master'
    try {
        $command = $master.CreateCommand()
        $command.CommandText = 'SELECT DB_ID(@name)'
        $null = $command.Parameters.AddWithValue('@name', $Database)
        $exists = $command.ExecuteScalar()
        if ($exists -eq [DBNull]::Value) {
            if (-not $CreateDatabase -or $VerifyOnly) {
                throw 'Test catalog missing; use -CreateDatabase explicitly.'
            }
            # Database is allowlisted above; escape the identifier as well.
            $quoted = '[' + $Database.Replace(']', ']]') + ']'
            $command.CommandText = "CREATE DATABASE $quoted"
            $null = $command.ExecuteNonQuery()
        }
        $command.Dispose()
    } finally { $master.Dispose() }
    $connection = Open-Database $Database
    $lock = $connection.CreateCommand()
    $lock.CommandText = @'
DECLARE @r int;
EXEC @r=sys.sp_getapplock @Resource=N'EquiTransitSchemaSync',
    @LockMode='Exclusive',@LockOwner='Session',@LockTimeout=10000;
IF @r<0 THROW 51000,'Another schema sync is running.',1;
'@
    $null = $lock.ExecuteNonQuery()
    $lock.Dispose()
    if ($Backup -and -not $VerifyOnly) {
        $command = $connection.CreateCommand()
        $command.CommandTimeout = 120
        $command.CommandText = @'
DECLARE @dir nvarchar(4000)=
    CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS nvarchar(4000));
IF @dir IS NULL THROW 51000,'Backup directory unavailable.',1;
DECLARE @file nvarchar(4000)=@dir+N'\'+@name+N'_before_sync_'+
    REPLACE(CONVERT(varchar(36),NEWID()),'-','')+N'.bak';
BACKUP DATABASE @name TO DISK=@file WITH COPY_ONLY,CHECKSUM;
RESTORE VERIFYONLY FROM DISK=@file WITH CHECKSUM;
SELECT @file;
'@
        $null = $command.Parameters.AddWithValue('@name', $Database)
        Write-Output ('Verified backup: ' + $command.ExecuteScalar())
        $command.Dispose()
    }
    if (-not $VerifyOnly) {
        $baseline = Join-Path $backendRoot (
            'database/bootstrap/000_figma_schema.sql')
        Invoke-SqlFile $connection $baseline
        Write-Output 'PASS baseline 000 (57 approved tables)'
        $files = @(Get-ChildItem -LiteralPath $migrationDir -File |
            Where-Object Name -Match '^\d{3}_.+\.sql$' | Sort-Object Name)
        $expected = 1
        foreach ($file in $files) {
            if ([int]$file.Name.Substring(0, 3) -ne $expected) {
                throw 'Migration sequence has a duplicate or gap.'
            }
            Invoke-SqlFile $connection $file.FullName
            Write-Output ('PASS ' + $file.Name)
            $expected++
        }
    }
    Invoke-SqlFile $connection (Join-Path $backendRoot (
        'database/verify-f12-f14-schema.sql'))
} catch {
    if ($connection -and $connection.State -eq 'Open') {
        $rollback = $connection.CreateCommand()
        $rollback.CommandText = 'IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;'
        try { $null = $rollback.ExecuteNonQuery() }
        finally { $rollback.Dispose() }
    }
    throw
} finally {
    if ($connection) { $connection.Dispose() }
}
