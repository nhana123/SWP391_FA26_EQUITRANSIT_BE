<#
Validates experimental migration 018 on an EXISTING, explicitly named sandbox.
No database creation, reset, backup/restore, or production promotion is performed.
The baseline and migrations 001-017 must already be deployed by their owner.
All synthetic CRUD rows are rolled back; only additive migration 018 persists.
#>
[CmdletBinding()]
param(
    [string]$Server = 'localhost,1433',
    [Parameter(Mandatory = $true)]
    [ValidateSet('EquiTransit_Test', 'EquiTransit_Demo',
        'EquiTransit_FigmaMigrationTest_20261013_143729')]
    [string]$Database
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$migrationDirectory = Join-Path $taskRoot 'database/migrations'
$migrationPath = Join-Path $migrationDirectory '018_visual_journey_graph_nodes.sql'
$fixturePath = Join-Path $PSScriptRoot 'test-graph-crud-sandbox.sql'
$proofDirectory = Join-Path $taskRoot '.local/journey-studio'
New-Item -ItemType Directory -Force -Path $proofDirectory | Out-Null
$proofPath = Join-Path $proofDirectory 'graph-migration-proof.json'
$legacyFiles = @(Get-ChildItem -LiteralPath $migrationDirectory -Filter '*.sql' |
    Where-Object { $_.Name -match '^0(0[1-9]|1[0-7])_' } | Sort-Object Name)
if ($legacyFiles.Count -ne 17) { throw 'Expected protected migrations 001-017.' }
$legacyHashes = @{}
foreach ($taskFile in $legacyFiles) {
    $legacyHashes[$taskFile.Name] = (Get-FileHash -LiteralPath $taskFile.FullName).Hash
}

$builder = [Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source'] = $Server
$builder['Initial Catalog'] = $Database
$builder['Integrated Security'] = $true
$builder['Encrypt'] = $true
$builder['TrustServerCertificate'] = $true
$builder['Connect Timeout'] = 10
$builder['Application Name'] = 'EquiTransitGraphSandboxValidation'
$connection = [Data.SqlClient.SqlConnection]::new($builder.ConnectionString)

function Invoke-SandboxQuery([string]$Sql) {
    if ($connection.Database -cne $Database) { throw 'Catalog changed unexpectedly.' }
    $command = $connection.CreateCommand()
    $command.CommandTimeout = 180
    # Server-side check is evaluated before EVERY command, including migration DDL.
    $command.CommandText = @'
IF DB_NAME() <> @ExpectedSandbox OR DB_NAME() NOT IN
    (N'EquiTransit_Test',N'EquiTransit_Demo',
     N'EquiTransit_FigmaMigrationTest_20261013_143729')
    THROW 51018, 'Refusing execution outside the exact sandbox catalog.', 1;
'@ + [Environment]::NewLine + $Sql
    [void]$command.Parameters.Add('@ExpectedSandbox',
        [Data.SqlDbType]::NVarChar, 128)
    $command.Parameters['@ExpectedSandbox'].Value = $Database
    $adapter = [Data.SqlClient.SqlDataAdapter]::new($command)
    $data = [Data.DataSet]::new()
    try { [void]$adapter.Fill($data); return ,$data }
    finally { $adapter.Dispose(); $command.Dispose() }
}

function Get-TriggerSnapshot {
    $result = Invoke-SandboxQuery @'
SET NOCOUNT ON;
SELECT SCHEMA_NAME(o.schema_id)+'.'+t.name AS Name,
    t.is_disabled AS Disabled, t.is_instead_of_trigger AS InsteadOf,
    CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) AS DefinitionHash
FROM sys.triggers t JOIN sys.objects o ON o.object_id=t.object_id
JOIN sys.sql_modules m ON m.object_id=t.object_id
WHERE t.parent_class=1 AND t.is_ms_shipped=0 ORDER BY Name;
'@
    return @($result.Tables[0].Rows | ForEach-Object {
        [ordered]@{ name=$_.Name; disabled=[bool]$_.Disabled;
            insteadOf=[bool]$_.InsteadOf; hash=$_.DefinitionHash }
    })
}

function Get-ConstraintSnapshot {
    $result = Invoke-SandboxQuery @'
SET NOCOUNT ON;
SELECT 'FK' AS Kind, f.name AS Name, f.is_disabled AS Disabled,
    f.is_not_trusted AS Untrusted,
    OBJECT_SCHEMA_NAME(f.parent_object_id)+'.'+
        OBJECT_NAME(f.parent_object_id) AS ParentTable,
    CONVERT(varchar(max),(SELECT c.parent_column_id,c.referenced_object_id,
        c.referenced_column_id FROM sys.foreign_key_columns c
        WHERE c.constraint_object_id=f.object_id ORDER BY c.constraint_column_id
        FOR JSON PATH)) AS Definition
FROM sys.foreign_keys f
UNION ALL
SELECT 'CHECK',c.name,c.is_disabled,c.is_not_trusted,
    OBJECT_SCHEMA_NAME(c.parent_object_id)+'.'+OBJECT_NAME(c.parent_object_id),
    c.definition FROM sys.check_constraints c
ORDER BY Kind,Name;
'@
    return @($result.Tables[0].Rows | ForEach-Object {
        [ordered]@{kind=$_.Kind; name=$_.Name; disabled=[bool]$_.Disabled;
            untrusted=[bool]$_.Untrusted; parent=$_.ParentTable;
            definition=$_.Definition}
    })
}

function Get-FixtureRowCounts {
    $result = Invoke-SandboxQuery @'
SET NOCOUNT ON;
SELECT 'User' AS Name,COUNT_BIG(*) AS Rows FROM dbo.[User]
UNION ALL SELECT 'Customer',COUNT_BIG(*) FROM dbo.Customer
UNION ALL SELECT 'StaffProfile',COUNT_BIG(*) FROM dbo.StaffProfile
UNION ALL SELECT 'TransportOrder',COUNT_BIG(*) FROM dbo.TransportOrder
UNION ALL SELECT 'OrderRevision',COUNT_BIG(*) FROM dbo.OrderRevision
UNION ALL SELECT 'TransportPlan',COUNT_BIG(*) FROM dbo.TransportPlan
UNION ALL SELECT 'TripLeg',COUNT_BIG(*) FROM dbo.TripLeg
ORDER BY Name;
'@
    return @($result.Tables[0].Rows | ForEach-Object {
        [ordered]@{name=$_.Name; rows=[long]$_.Rows}
    })
}

try {
    $connection.Open()
    $catalog = Invoke-SandboxQuery 'SELECT DB_NAME() AS Catalog;'
    if ($catalog.Tables[0].Rows[0].Catalog -cne $Database) {
        throw 'Server-side DB_NAME guard did not match the requested sandbox.'
    }
    Write-Output "Sandbox verified: $Database ($Server)"
    $triggersBefore = @(Get-TriggerSnapshot)
    $requiredTriggers = @('TR_Figma_TransportPlan_AcceptedQuote',
        'TR_Figma_Quote_AcceptedHistory','TR_Resource_VehicleSchedule',
        'TR_Resource_StallSchedule','TR_Resource_PlanActivation',
        'TR_Plan_DriverSchedule','TR_Departure_FirstLegGuard',
        'TR_Execution_WelfareImmutable','TR_Execution_MilestoneImmutable')
    foreach ($triggerName in $requiredTriggers) {
        if (@($triggersBefore | Where-Object {
            $_.name -eq "dbo.$triggerName" -and -not $_.disabled
        }).Count -ne 1) { throw "Missing enabled baseline trigger: $triggerName" }
    }
    $constraintsBefore = @(Get-ConstraintSnapshot)
    if (@($constraintsBefore | Where-Object {
        $_.disabled -or $_.untrusted
    }).Count -gt 0) { throw 'Baseline contains disabled/untrusted constraints.' }
    $rowsBefore = @(Get-FixtureRowCounts)
    $migrationSql = [IO.File]::ReadAllText($migrationPath)
    # Reject batch separators: migration018 runs as one guarded atomic command.
    if ($migrationSql -match '(?im)^\s*GO\s*$') {
        throw 'Unexpected batch separator in experimental migration.'
    }
    for ($pass = 1; $pass -le 2; $pass++) {
        $null = Invoke-SandboxQuery $migrationSql
        Write-Output "Migration 018 pass $pass`: PASS"
    }
    $null = Invoke-SandboxQuery ([IO.File]::ReadAllText($fixturePath))
    Write-Output 'Graph CRUD + 16 rejection cases + rollback: PASS'
    $rowsAfter = @(Get-FixtureRowCounts)
    if (($rowsBefore | ConvertTo-Json -Compress) -cne
        ($rowsAfter | ConvertTo-Json -Compress)) {
        throw 'Fixture row counts changed after rollback.'
    }
    $triggersAfter = @(Get-TriggerSnapshot)
    if (($triggersBefore | ConvertTo-Json -Compress) -cne
        ($triggersAfter | ConvertTo-Json -Compress)) {
        throw 'Baseline trigger definitions or enabled states changed.'
    }
    $constraintsAfter = @(Get-ConstraintSnapshot)
    foreach ($original in $constraintsBefore) {
        $current = @($constraintsAfter | Where-Object {
            $_.kind -eq $original.kind -and $_.name -eq $original.name
        })
        if ($current.Count -ne 1 -or
            ($original | ConvertTo-Json -Compress) -cne
            ($current[0] | ConvertTo-Json -Compress)) {
            throw "Existing constraint changed: $($original.name)"
        }
    }
    if (@($constraintsAfter | Where-Object {
        $_.disabled -or $_.untrusted
    }).Count -gt 0) { throw 'A constraint is disabled or untrusted.' }
    $integrity = Invoke-SandboxQuery 'DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS, ALL_ERRORMSGS;'
    foreach ($table in $integrity.Tables) {
        if ($table.Rows.Count -gt 0) { throw 'DBCC detected a constraint violation.' }
    }
    foreach ($taskFile in $legacyFiles) {
        if ((Get-FileHash -LiteralPath $taskFile.FullName).Hash -cne
            $legacyHashes[$taskFile.Name]) {
            throw "Protected migration changed: $($taskFile.Name)"
        }
    }
    $proof = [ordered]@{
        utc=[DateTime]::UtcNow.ToString('o'); server=$Server; database=$Database;
        migration=[IO.Path]::GetFileName($migrationPath);
        migrationHash=(Get-FileHash -LiteralPath $migrationPath).Hash;
        applications=2; crudRollback=$true; rejectionCases=16;
        existingTriggersUnchanged=$true; triggers=$triggersAfter;
        existingConstraintsUnchanged=$true; constraints=$constraintsAfter.Count;
        dbccCheckConstraints='PASS'; fixtureRowsUnchanged=$true;
        rowCounts=$rowsAfter; protectedMigrationHashes=$legacyHashes;
        realDatabaseAccessed=$false; resetPerformed=$false
    }
    $proof | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $proofPath -Encoding utf8
    Write-Output "Existing triggers unchanged: $($triggersAfter.Count) PASS"
    Write-Output "Trusted FK/CHECK constraints + DBCC: $($constraintsAfter.Count) PASS"
    Write-Output 'Protected migrations 001-017 unchanged: PASS'
    Write-Output "Proof: $proofPath"
} finally { $connection.Dispose() }
