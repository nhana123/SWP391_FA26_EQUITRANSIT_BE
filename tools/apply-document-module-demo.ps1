[CmdletBinding()]
param([string]$Database = 'EquiTransit_Demo')
$ErrorActionPreference = 'Stop'
if ($Database -notin @('EquiTransit_Demo', 'EquiTransit_Test')) {
    throw 'Only Demo/Test are authorized.'
}
$root = Split-Path -Parent $PSScriptRoot
$connection = [Data.SqlClient.SqlConnection]::new(
    "Server=tcp:localhost,1433;Database=$Database;Integrated Security=True;" +
    'Encrypt=True;TrustServerCertificate=True')
$connection.Open()
try {
    $command = $connection.CreateCommand()
    $command.CommandTimeout = 180
    $command.CommandText = "SELECT CONVERT(nvarchar(4000)," +
        "SERVERPROPERTY('InstanceDefaultBackupPath'))"
    $backupRoot = [string]$command.ExecuteScalar()
    $backup = Join-Path $backupRoot ($Database + '_before_024_' +
        (Get-Date -Format 'yyyyMMdd_HHmmss') + '.bak')
    $command.Parameters.AddWithValue('@backup', $backup) | Out-Null
    $command.CommandText = "BACKUP DATABASE [$Database] TO DISK=@backup " +
        'WITH COPY_ONLY, CHECKSUM; RESTORE VERIFYONLY FROM DISK=@backup ' +
        'WITH CHECKSUM;'
    $command.ExecuteNonQuery() | Out-Null
    $command.Parameters.Clear()
    $command.CommandText = Get-Content -Raw -LiteralPath (Join-Path $root (
        'database/migrations/024_location_local_documents.sql'))
    $command.ExecuteNonQuery() | Out-Null
    # Verify idempotency without clearing existing catalog configuration.
    $command.ExecuteNonQuery() | Out-Null
    Write-Output "Verified backup: $backup"
    $command.CommandText = 'SELECT COUNT(*) FROM dbo.LocationCatalog'
    Write-Output "Locations: $($command.ExecuteScalar())"
    $command.CommandText = 'SELECT COUNT(DISTINCT CountryCode) ' +
        'FROM dbo.LocationCatalog WHERE IsActive=1'
    Write-Output "Countries: $($command.ExecuteScalar())"
    $command.CommandText = 'SELECT COUNT(*) FROM dbo.ComplianceDocumentPolicy'
    Write-Output "Document policies: $($command.ExecuteScalar())"
} finally {
    $connection.Dispose()
}
