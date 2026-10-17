param([switch]$Verify)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME for JDK 21 first.' }
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$nativeDir = Join-Path $env:LOCALAPPDATA 'EquiTransit\tools\sqljdbc-native'
if (-not (Test-Path -LiteralPath (Join-Path $nativeDir 'mssql-jdbc_auth-12.10.2.x64.dll'))) {
    throw 'Run tools\setup-java.ps1 first.'
}
$localDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
$secretFile = Join-Path $localDir 'jwt-secret.txt'
if (-not (Test-Path -LiteralPath $secretFile)) {
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($bytes))
}
if (-not $env:EQ_JWT_SECRET) {
    $env:EQ_JWT_SECRET = [IO.File]::ReadAllText($secretFile).Trim()
}
$secretProperties = Join-Path $localDir 'application-secrets.properties'
[IO.File]::WriteAllText($secretProperties, 'app.jwt.secret=' + $env:EQ_JWT_SECRET)
$env:SPRING_PROFILES_ACTIVE = 'local'
$maven = Join-Path $PSScriptRoot 'mvnw.cmd'
if ($Verify) {
    & $maven -B verify
} else {
    & $maven -B spring-boot:run "-Dspring-boot.run.jvmArguments=-Djava.library.path=`"$nativeDir`""
}
if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE." }
