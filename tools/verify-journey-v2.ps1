<# Native journey test fixtures roll back. Does not apply migrations or start BE. #>
[CmdletBinding()]
param(
    [string]$Database = 'EquiTransit_Demo',
    [string]$Server = 'localhost:1433'
)
$ErrorActionPreference = 'Stop'
if ($Database -notin @('EquiTransit_Demo', 'EquiTransit_Test')) {
    throw 'Only Demo/Test are allowed.'
}
$root = Split-Path -Parent $PSScriptRoot
$jdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$native = Join-Path $root '.local/tools/sqljdbc-native'
if (-not (Test-Path (Join-Path $jdk 'bin/java.exe'))) {
    throw 'JDK 21 is required.'
}
if (-not (Test-Path (Join-Path $native 'mssql-jdbc_auth-12.10.0.x64.dll'))) {
    throw 'Install SQL Server JDBC native authentication first.'
}
$settings = @{
    JAVA_HOME = $jdk
    Path = (Join-Path $jdk 'bin') + ';' + $env:Path
    EQ_JOURNEY_SQL = 'true'
    EQ_JOURNEY_DB_URL = 'jdbc:sqlserver://' + $Server + ';databaseName=' +
        $Database + ';integratedSecurity=true;encrypt=true;' +
        'trustServerCertificate=true'
}
$prior = @{}
foreach ($name in $settings.Keys) {
    $prior[$name] = [Environment]::GetEnvironmentVariable($name)
    [Environment]::SetEnvironmentVariable($name, $settings[$name])
}
Push-Location -LiteralPath $root
try {
    $argsList = @('-B', 'test-compile', 'checkstyle:check', 'test',
        '-Dtest=JourneyFlowSqlTest,JourneyPricingTest',
        '-DargLine=-Djava.library.path=.local/tools/sqljdbc-native')
    & (Join-Path $root 'mvnw.cmd') @argsList
    if ($LASTEXITCODE -ne 0) { throw 'Journey verification failed.' }
    $reportPath = 'target/surefire-reports/' +
        'TEST-com.equitransit.backend.JourneyFlowSqlTest.xml'
    [xml]$report = Get-Content $reportPath
    if ([int]$report.testsuite.tests -ne 1 -or
        [int]$report.testsuite.skipped -ne 0 -or
        [int]$report.testsuite.failures -ne 0 -or
        [int]$report.testsuite.errors -ne 0) {
        throw 'Native journey test did not execute successfully.'
    }
} finally {
    Pop-Location
    foreach ($name in $prior.Keys) {
        [Environment]::SetEnvironmentVariable($name, $prior[$name])
    }
}
