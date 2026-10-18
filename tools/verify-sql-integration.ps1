<# Run the required native suites with flags enabled and reject skipped tests. #>
[CmdletBinding()]
param(
    [string]$Server = 'localhost:1433',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$NativeDirectory = $env:EQ_SQL_JDBC_NATIVE_DIR
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    $JavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Specify -JavaHome pointing to JDK 21.'
}
if (-not $NativeDirectory) {
    $NativeDirectory = Join-Path $env:LOCALAPPDATA (
        'EquiTransit/tools/sqljdbc-native')
}
if (-not (Test-Path -LiteralPath $NativeDirectory)) {
    throw 'Specify the SQL JDBC authentication DLL directory.'
}
$shared = 'EquiTransit_FigmaMigrationTest_20261013_143729'
$staff = 'EquiTransit_FigmaMigrationTest_Staff_20261018'
$connection = 'jdbc:sqlserver://' + $Server + ';integratedSecurity=true;' +
    'encrypt=true;trustServerCertificate=true;databaseName='
$settings = @{
    JAVA_HOME = $JavaHome
    Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
    MAVEN_OPTS = '-Xmx512m'
    EQ_STAFF_TEST_ENABLED = 'true'
    EQ_PLAN_TEST_ENABLED = 'true'
    EQ_DEPARTURE_TEST_ENABLED = 'true'
    EQ_SCHEMA_TEST_ENABLED = 'true'
    EQ_EXECUTION_TEST_ENABLED = 'true'
    EQ_EXECUTION_TEST_DB_URL = $connection + (
        'EquiTransit_FigmaMigrationTest_Execution_20261018')
    EQ_PLAN_TEST_DB_URL = $connection + (
        'EquiTransit_FigmaMigrationTest_Bootstrap_20261018')
    EQ_SCHEMA_TEST_DB_URL = $connection + (
        'EquiTransit_FigmaMigrationTest_Execution_20261018')
    EQ_STAFF_TEST_DB_URL = $connection + $staff
}
$previous = @{}
foreach ($name in $settings.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name)
    [Environment]::SetEnvironmentVariable($name, $settings[$name])
}
$suites = @('StaffAdministrationIntegrationTest',
    'OperationalPlanIntegrationTest', 'DepartureGateIntegrationTest',
    'SchemaReadinessIntegrationTest', 'LegExecutionIntegrationTest',
    'LegExecutionPolicyTest', 'ApiDtoContractTest', 'OpenApiTest')
Push-Location -LiteralPath $backendRoot
try {
    $startedAtUtc = [DateTime]::UtcNow.AddSeconds(-2)
    $native = $NativeDirectory.Replace('\', '/')
    $mavenArguments = @('-B', 'test-compile', 'checkstyle:check', 'test',
        ('-Dtest=' + ($suites -join ',')),
        ('-DargLine=-Xmx768m -Djava.library.path=' + $native))
    & (Join-Path $backendRoot 'mvnw.cmd') @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Native Maven verification failed.' }
    foreach ($suite in $suites) {
        $report = Join-Path $backendRoot (
            'target/surefire-reports/TEST-com.equitransit.backend.' +
            $suite + '.xml')
        if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $startedAtUtc) {
            throw "Stale report cannot prove native verification: $suite"
        }
        [xml]$xml = Get-Content -LiteralPath $report
        $result = $xml.testsuite
        if ([int]$result.tests -le 0 -or [int]$result.skipped -ne 0 -or
            [int]$result.failures -ne 0 -or [int]$result.errors -ne 0) {
            throw "Not a zero-skip PASS: $suite"
        }
        if ($suite -eq $suites[0] -and [int]$result.tests -ne 5) {
            throw 'Staff verification requires all five native cases.'
        }
        Write-Output ("PASS $suite : " + $result.tests + ', skipped=0')
    }
} finally {
    Pop-Location
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name])
    }
}
