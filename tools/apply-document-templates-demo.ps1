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
    $backup = Join-Path $backupRoot ($Database + '_before_025_' +
        (Get-Date -Format 'yyyyMMdd_HHmmss') + '.bak')
    $command.Parameters.AddWithValue('@backup', $backup) | Out-Null
    $command.CommandText = "BACKUP DATABASE [$Database] TO DISK=@backup " +
        'WITH COPY_ONLY, CHECKSUM; RESTORE VERIFYONLY FROM DISK=@backup ' +
        'WITH CHECKSUM;'
    $command.ExecuteNonQuery() | Out-Null
    $command.Parameters.Clear()
    $command.CommandText = Get-Content -Raw -LiteralPath (Join-Path $root (
        'database/migrations/025_document_form_templates.sql'))
    $command.ExecuteNonQuery() | Out-Null
    $command.ExecuteNonQuery() | Out-Null
    Write-Output "Verified backup: $backup"
    $command.CommandText = "SELECT COUNT(*) FROM dbo.DocumentTypeCatalog " +
        "WHERE TypeCode IN ('FEI_PASSPORT','COGGINS_EIA'," +
        "'EQUINE_INFLUENZA','EXPORT_PERMIT','GLANDERS_CFT','EVA_TEST') " +
        'AND FormCode IS NOT NULL AND CertTextTemplate IS NOT NULL'
    $forms = [int]$command.ExecuteScalar()
    if ($forms -ne 6) { throw 'All six reference layouts must be seeded.' }
    @{database=$Database;backup=$backup;forms=$forms;
        migration='025_document_form_templates'} | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $root '.local/template-migration.json')
    Write-Output "Seeded reference forms: $forms"
} finally {
    $connection.Dispose()
}
