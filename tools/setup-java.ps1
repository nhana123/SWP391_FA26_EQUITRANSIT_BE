param([string]$JdkHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'))
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $backendRoot '.local\tools'
if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\javac.exe'))) {
    throw 'Provide a valid JDK 21 directory with -JdkHome.'
}
$mavenWrapper = Join-Path $backendRoot 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw 'Open the backend repository containing mvnw.cmd first.'
}
$nativeDir = Join-Path $toolRoot 'sqljdbc-native'
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
$nativeFile = Join-Path $nativeDir 'mssql-jdbc_auth-12.10.0.x64.dll'
$nativeUrl = 'https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/' +
    'mssql-jdbc_auth/12.10.0.x64/mssql-jdbc_auth-12.10.0.x64.dll'
if (-not (Test-Path -LiteralPath $nativeFile)) {
    Invoke-WebRequest -Uri $nativeUrl -OutFile $nativeFile -TimeoutSec 30
}
$expected = (Invoke-WebRequest -Uri ($nativeUrl + '.sha256') `
    -TimeoutSec 30).Content.Trim()
$actual = (Get-FileHash -LiteralPath $nativeFile -Algorithm SHA256).Hash
if ($actual -ine $expected) { throw 'JDBC native library checksum mismatch.' }
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkHome, 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$paths = @((Join-Path $jdkHome 'bin'))
foreach ($entry in ($userPath -split ';')) {
    if ($entry -and $entry -notin $paths) { $paths += $entry }
}
[Environment]::SetEnvironmentVariable('Path', ($paths -join ';'), 'User')
$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' +
    $nativeDir + ';' + $env:Path
& $mavenWrapper --version
if ($LASTEXITCODE -ne 0) { throw 'Maven Wrapper initialization failed.' }
Write-Output 'JDK, Maven Wrapper and Windows SQL JDBC authentication are ready.'
