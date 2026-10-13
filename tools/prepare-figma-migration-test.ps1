param([string]$Server='localhost')
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$localDir = Join-Path $backendRoot '.local'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss')
$testDatabase = 'EquiTransit_FigmaMigrationTest_' + $stamp

function Invoke-Database([string]$Database,[string]$Query) {
    $builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder['Data Source'] = $Server
    $builder['Initial Catalog'] = $Database
    $builder['Integrated Security'] = $true
    $builder['Connect Timeout'] = 10
    $builder['Application Name'] = 'EquiTransitFigmaMigrationValidation'
    $connection = [System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
    try {
        $connection.Open()
        $command = $connection.CreateCommand()
        $command.CommandText = $Query
        $command.CommandTimeout = 180
        $value = $command.ExecuteScalar()
        if ($value -eq [DBNull]::Value) { return $null }
        return $value
    } finally { $connection.Dispose() }
}
function Sql-Literal([string]$Value) { return "N'" + $Value.Replace("'", "''") + "'" }
$backupDir = [string](Invoke-Database 'master' "SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(4000));")
$dataFile = [string](Invoke-Database 'master' "SELECT physical_name FROM sys.master_files WHERE database_id=DB_ID('EquiTransit') AND type=0;")
if (-not $backupDir -or -not $dataFile) { throw 'SQL Server backup/data paths are unavailable.' }
$dataDir = Split-Path -Parent $dataFile
$backupPath = Join-Path $backupDir ('EquiTransit_before_figma_' + $stamp + '.bak')
$testDataPath = Join-Path $dataDir ($testDatabase + '.mdf')
$testLogPath = Join-Path $dataDir ($testDatabase + '_log.ldf')
foreach ($target in @($backupPath,$testDataPath,$testLogPath)) {
    if (Test-Path -LiteralPath $target) { throw 'A target backup/test file already exists; refusing overwrite.' }
}
if (Invoke-Database 'master' ("SELECT DB_ID(" + (Sql-Literal $testDatabase) + ");")) {
    throw 'The unique test database already exists; refusing replacement.'
}
$null = Invoke-Database 'master' ("BACKUP DATABASE [EquiTransit] TO DISK=" + (Sql-Literal $backupPath) + " WITH COPY_ONLY, CHECKSUM;")
$null = Invoke-Database 'master' ("RESTORE VERIFYONLY FROM DISK=" + (Sql-Literal $backupPath) + " WITH CHECKSUM;")
$null = Invoke-Database 'master' ("RESTORE DATABASE [" + $testDatabase + "] FROM DISK=" + (Sql-Literal $backupPath) +
    " WITH MOVE N'EquiTransit' TO " + (Sql-Literal $testDataPath) +
    ", MOVE N'EquiTransit_log' TO " + (Sql-Literal $testLogPath) + ", RECOVERY, CHECKSUM;")
$result = @{server=$Server;sourceDatabase='EquiTransit';testDatabase=$testDatabase;
    backupPath=$backupPath;backupVerified=$true;testDataPath=$testDataPath;
    testLogPath=$testLogPath;createdAtUtc=[DateTime]::UtcNow.ToString('o')}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'figma-migration-test.json') -Encoding utf8
Write-Output ('Verified backup: ' + $backupPath)
Write-Output ('Restored isolated copy: ' + $testDatabase)
