param([string]$JdkHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'))
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $env:LOCALAPPDATA 'EquiTransit\tools'
if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\javac.exe'))) {
    throw 'Provide a valid JDK 21 directory with -JdkHome.'
}
$mavenHome = Join-Path $toolRoot 'apache-maven-3.9.9'
if (-not (Test-Path -LiteralPath (Join-Path $mavenHome 'bin\mvn.cmd'))) {
    throw 'Install Apache Maven 3.9.9 before running this helper.'
}
$nativeDir = Join-Path $toolRoot 'sqljdbc-native'
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
$nativeFile = Join-Path $nativeDir 'mssql-jdbc_auth-12.10.2.x64.dll'
$nativeUrl = 'https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/' +
    'mssql-jdbc_auth/12.10.2.x64/mssql-jdbc_auth-12.10.2.x64.dll'
if (-not (Test-Path -LiteralPath $nativeFile)) {
    Invoke-WebRequest -Uri $nativeUrl -OutFile $nativeFile
}
$expected = (Invoke-WebRequest -Uri ($nativeUrl + '.sha256')).Content.Trim()
$actual = (Get-FileHash -LiteralPath $nativeFile -Algorithm SHA256).Hash
if ($actual -ine $expected) { throw 'JDBC native library checksum mismatch.' }
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkHome, 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$paths = @((Join-Path $jdkHome 'bin'), (Join-Path $mavenHome 'bin'), $nativeDir)
foreach ($entry in ($userPath -split ';')) {
    if ($entry -and $entry -notin $paths) { $paths += $entry }
}
[Environment]::SetEnvironmentVariable('Path', ($paths -join ';'), 'User')
$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' +
    (Join-Path $mavenHome 'bin') + ';' + $nativeDir + ';' + $env:Path
Write-Output 'JDK 21, Maven 3.9.9 and Windows SQL JDBC authentication are ready.'
