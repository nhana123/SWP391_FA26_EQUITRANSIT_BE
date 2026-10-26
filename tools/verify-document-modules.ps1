[CmdletBinding()]
param([string]$Database = 'EquiTransit_Demo')
$ErrorActionPreference = 'Stop'
if ($Database -ne 'EquiTransit_Demo') {
    throw 'The existing combined Compliance suite requires EquiTransit_Demo.'
}
$root = Split-Path -Parent $PSScriptRoot
$settings = @{
    JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    EQ_DOCUMENT_TEST = 'true'
    EQ_COMPLIANCE_TEST_ENABLED = 'true'
    EQ_DOCUMENT_TEST_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=' +
        $Database + ';integratedSecurity=true;encrypt=true;' +
        'trustServerCertificate=true'
}
$settings.EQ_COMPLIANCE_TEST_DB_URL = $settings.EQ_DOCUMENT_TEST_DB_URL
$settings.Path = (Join-Path $settings.JAVA_HOME 'bin') + ';' + $env:Path
$prior = @{}
foreach ($name in $settings.Keys) {
    $prior[$name] = [Environment]::GetEnvironmentVariable($name)
    [Environment]::SetEnvironmentVariable($name, $settings[$name])
}
Push-Location -LiteralPath $root
try {
    & .\mvnw.cmd -B test-compile checkstyle:check test `
        '-Dtest=DocumentWorkflowSqlTest,ComplianceIntegrationTest' `
        '-DargLine=-Djava.library.path=.local/tools/sqljdbc-native'
    if ($LASTEXITCODE -ne 0) { throw 'Document module verification failed.' }
    foreach ($test in @('DocumentWorkflowSqlTest','ComplianceIntegrationTest')) {
        [xml]$report = Get-Content -LiteralPath (
            'target/surefire-reports/TEST-com.equitransit.backend.' +
            $test + '.xml')
        if ([int]$report.testsuite.tests -le 0 -or
            [int]$report.testsuite.skipped -ne 0 -or
            [int]$report.testsuite.failures -ne 0 -or
            [int]$report.testsuite.errors -ne 0) {
            throw "Suite did not execute successfully: $test"
        }
    }
} finally {
    Pop-Location
    foreach ($name in $prior.Keys) {
        [Environment]::SetEnvironmentVariable($name, $prior[$name])
    }
}
