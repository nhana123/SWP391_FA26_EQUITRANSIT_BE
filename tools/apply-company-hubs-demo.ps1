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
    $backup = Join-Path $backupRoot ($Database + '_before_027_' +
        (Get-Date -Format 'yyyyMMdd_HHmmss') + '.bak')
    $command.Parameters.AddWithValue('@backup', $backup) | Out-Null
    $command.CommandText = "BACKUP DATABASE [$Database] TO DISK=@backup " +
        'WITH COPY_ONLY, CHECKSUM; RESTORE VERIFYONLY FROM DISK=@backup ' +
        'WITH CHECKSUM;'
    $command.ExecuteNonQuery() | Out-Null
    $command.Parameters.Clear()
    $command.CommandText = Get-Content -Raw -LiteralPath (Join-Path $root (
        'database/migrations/027_company_hubs_and_coordinates.sql'))
    $command.ExecuteNonQuery() | Out-Null
    $command.ExecuteNonQuery() | Out-Null
    $command.CommandText = "SELECT COUNT(*) FROM dbo.LocationCatalog WHERE " +
        "LocationType='HUB' AND IsPrimaryHub=1 AND IsActive=1 AND " +
        "CountryCode IN ('VN','CN','TH','KH','LA')"
    $hubs = [int]$command.ExecuteScalar()
    if ($hubs -ne 5) { throw 'Expected five primary country hubs.' }
    @{database=$Database;backup=$backup;hubs=$hubs;
        migration='027_company_hubs_and_coordinates'} | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $root '.local/hub-migration.json')
    Write-Output "Verified backup: $backup"
    Write-Output "Primary country hubs: $hubs"
} finally {
    $connection.Dispose()
}

