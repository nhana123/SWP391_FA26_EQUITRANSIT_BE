[CmdletBinding()]
param([string]$Server = 'localhost,1433')
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
# This is the common allowlisted catalog of original migrations 002 and 018.
$database = 'EquiTransit_FigmaMigrationTest_20261013_143729'
$connection = [System.Data.SqlClient.SqlConnection]::new(
    "Server=$Server;Database=master;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=10")
try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandText = 'SELECT DB_ID(@name)'
    $null = $command.Parameters.AddWithValue('@name', $database)
    if ($command.ExecuteScalar() -ne [DBNull]::Value) {
        throw "Database $database already exists. Stopped before making changes. Review or back up that database before applying migrations manually."
    }
    $command.Dispose()
} finally { $connection.Dispose() }

& (Join-Path $PSScriptRoot 'sync-db-migrations.ps1') `
    -Server $Server -Database $database -CreateDatabase

$connection = [System.Data.SqlClient.SqlConnection]::new(
    "Server=$Server;Database=$database;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=10")
try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandText = @'
IF OBJECT_ID('dbo.RefreshTokenSession','U') IS NULL
    THROW 51020, 'Migration 017 table missing.', 1;
IF OBJECT_ID('dbo.PlanCheckpointNode','U') IS NULL
    THROW 51020, 'Migration 018 table missing.', 1;
IF OBJECT_ID('dbo.HandoverFile','U') IS NULL
    THROW 51020, 'Migration 019 table missing.', 1;
IF EXISTS (SELECT 1 FROM sys.foreign_keys
           WHERE is_disabled=1 OR is_not_trusted=1)
    THROW 51020, 'Disabled or untrusted foreign key found.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE is_disabled=1 OR is_not_trusted=1)
    THROW 51020, 'Disabled or untrusted CHECK found.', 1;
SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0;
'@
    $count = $command.ExecuteScalar()
    $command.CommandText = 'SELECT physical_name FROM sys.database_files'
    $reader = $command.ExecuteReader()
    $paths = @()
    while ($reader.Read()) { $paths += $reader.GetString(0) }
    $reader.Close()
    $command.Dispose()
    $result = [ordered]@{
        VerifiedAt = (Get-Date).ToString('o')
        Server = $Server
        Database = $database
        SourceBranch = 'fix/finaldelivery'
        SourceCommit = 'd22dc3daa1c41058d5fba8e1376304e130d99f7d'
        LastMigration = '019_handover_file_storage.sql'
        TableCount = $count
        PhysicalFiles = $paths
    }
    $proof = Join-Path $backend 'database/local-database-created.json'
    $result | ConvertTo-Json | Set-Content -LiteralPath $proof -Encoding UTF8
    Write-Host "Created and verified: $Server / $database ($count tables)"
    Write-Host "Physical SQL Server files: $($paths -join ', ')"
    Write-Host "Verification record: $proof"
} finally { $connection.Dispose() }
