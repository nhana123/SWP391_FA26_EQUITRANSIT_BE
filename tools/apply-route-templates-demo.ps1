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
    $backup = Join-Path $backupRoot ($Database + '_before_026_' +
        (Get-Date -Format 'yyyyMMdd_HHmmss') + '.bak')
    $command.Parameters.AddWithValue('@backup', $backup) | Out-Null
    $command.CommandText = "BACKUP DATABASE [$Database] TO DISK=@backup " +
        'WITH COPY_ONLY, CHECKSUM; RESTORE VERIFYONLY FROM DISK=@backup ' +
        'WITH CHECKSUM;'
    $command.ExecuteNonQuery() | Out-Null
    $command.Parameters.Clear()
    $command.CommandText = Get-Content -Raw -LiteralPath (Join-Path $root (
        'database/migrations/026_predefined_route_templates.sql'))
    $command.ExecuteNonQuery() | Out-Null
    $command.ExecuteNonQuery() | Out-Null
    $command.CommandText = 'SELECT COUNT(*) FROM dbo.RouteTemplate WHERE ' +
        "RouteCode IN ('ROUTE-VN-CN-ROAD','ROUTE-VN-LA-ROAD','ROUTE-VN-FR-AIR')"
    $routes = [int]$command.ExecuteScalar()
    if ($routes -ne 3) { throw 'Expected three seeded reference routes.' }
    @{database=$Database;backup=$backup;routes=$routes;
        migration='026_predefined_route_templates'} | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $root '.local/route-migration.json')
    Write-Output "Verified backup: $backup"
    Write-Output "Seeded routes: $routes"
} finally {
    $connection.Dispose()
}
