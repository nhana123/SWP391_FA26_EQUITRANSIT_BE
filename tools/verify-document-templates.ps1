[CmdletBinding()]
param([string]$Database = 'EquiTransit_Demo')
$ErrorActionPreference = 'Stop'
if ($Database -notin @('EquiTransit_Demo', 'EquiTransit_Test')) {
    throw 'Template verification is restricted to Demo/Test.'
}
$root = Split-Path -Parent $PSScriptRoot
$settings = @{
    JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    EQ_DOCUMENT_TEMPLATE_TEST = 'true'
    EQ_DOCUMENT_TEMPLATE_DB_URL = 'jdbc:sqlserver://localhost:1433;' +
        'databaseName=' + $Database + ';integratedSecurity=true;' +
        'encrypt=true;trustServerCertificate=true'
}
$settings.Path = (Join-Path $settings.JAVA_HOME 'bin') + ';' + $env:Path
$prior = @{}
foreach ($name in $settings.Keys) {
    $prior[$name] = [Environment]::GetEnvironmentVariable($name)
    [Environment]::SetEnvironmentVariable($name, $settings[$name])
}
Push-Location -LiteralPath $root
try {
    & .\mvnw.cmd -B test-compile checkstyle:check test `
        '-Dtest=DocumentTemplateSqlTest' `
        '-DargLine=-Djava.library.path=.local/tools/sqljdbc-native'
    if ($LASTEXITCODE -ne 0) { throw 'Template verification failed.' }
    [xml]$report = Get-Content -LiteralPath (
        'target/surefire-reports/' +
        'TEST-com.equitransit.backend.DocumentTemplateSqlTest.xml')
    if ([int]$report.testsuite.tests -ne 3 -or
        [int]$report.testsuite.skipped -ne 0 -or
        [int]$report.testsuite.failures -ne 0 -or
        [int]$report.testsuite.errors -ne 0) {
        throw 'The three native template tests must actually execute.'
    }
} finally {
    Pop-Location
    foreach ($name in $prior.Keys) {
        [Environment]::SetEnvironmentVariable($name, $prior[$name])
    }
}
