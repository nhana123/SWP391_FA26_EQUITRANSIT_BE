param(
    [string]$MasterUrl = $env:EQ_F08_NATIVE_MASTER_URL,
    [string]$Username = $env:EQ_F08_TEST_DB_USERNAME
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $MasterUrl) {
    $MasterUrl = 'jdbc:sqlserver://localhost:1433;databaseName=master;' +
        'encrypt=true;trustServerCertificate=true;integratedSecurity=true;' +
        'loginTimeout=10'
}
if ($MasterUrl -notmatch '^jdbc:sqlserver://' -or
    $MasterUrl -notmatch '(?i)(^|;)databaseName=master(;|$)') {
    throw 'Set EQ_F08_NATIVE_MASTER_URL to an explicit SQL Server master URL.'
}
if ($MasterUrl -match '(?i)(^|;)(user|username|password|pwd)=') {
    throw 'Use the dedicated credential environment variables, not URL secrets.'
}
if (-not $env:JAVA_HOME -or
    -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to JDK 21 first.'
}
$javaInfo = & (Join-Path $env:JAVA_HOME 'bin/java.exe') --version
if (($javaInfo -join ' ') -notmatch '^(openjdk|java) 21[.]') {
    throw 'The F08 native suite requires JDK 21.'
}
$saved = @{}
foreach ($name in @('EQ_F08_NATIVE_ENABLED','EQ_F08_NATIVE_MASTER_URL',
        'EQ_F08_TEST_DB_USERNAME','PATH')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location -LiteralPath $repo
try {
    $env:EQ_F08_NATIVE_ENABLED = 'true'
    $env:EQ_F08_NATIVE_MASTER_URL = $MasterUrl
    if ($Username) { $env:EQ_F08_TEST_DB_USERNAME = $Username }
    $native = Join-Path $env:LOCALAPPDATA 'EquiTransit/tools/sqljdbc-native'
    if (Test-Path -LiteralPath $native) { $env:PATH = "$native;$env:PATH" }
    & (Join-Path $repo 'mvnw.cmd') '-B' '-Dtest=F08NativeIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { throw 'F08 native verification failed.' }
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}
