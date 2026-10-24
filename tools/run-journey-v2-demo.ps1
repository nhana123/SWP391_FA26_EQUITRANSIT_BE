<# Opt-in sandbox runtime, isolated from the existing 8080 service. #>
[CmdletBinding()]
param([switch]$Stop)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local/journey-v2'
$pidFile = Join-Path $local 'pid.json'
if ($Stop) {
    if (Test-Path -LiteralPath $pidFile) {
        $saved = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
        $process = Get-CimInstance Win32_Process -Filter (
            'ProcessId=' + [int]$saved.id)
        if ($process -and $process.CommandLine.Contains($root) -and
            $process.CommandLine.Contains('--app.journey.enabled=true') -and
            [Math]::Abs((([DateTime]$process.CreationDate).ToUniversalTime() -
                [DateTime]$saved.startedAt).TotalSeconds) -lt 1) {
            Stop-Process -Id ([int]$saved.id)
        }
        Remove-Item -LiteralPath $pidFile
    }
    return
}
if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8081 is occupied; no existing process was stopped.'
}
$config = Join-Path $root '.local/demo/application-demo.properties'
$settings = [IO.File]::ReadAllText($config)
if ($settings -notmatch '(?im)^spring\.datasource\.url=.*databaseName=EquiTransit_Demo(?:;|\s|$)') {
    throw 'Explicit EquiTransit_Demo datasource is required.'
}
$jdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$native = Join-Path $root '.local/tools/sqljdbc-native'
$jar = Join-Path $root 'target/backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Package backend first.' }
New-Item -ItemType Directory -Path $local -Force | Out-Null
$prior = $env:EQ_JWT_SECRET
try {
    $env:EQ_JWT_SECRET = [IO.File]::ReadAllText(
        (Join-Path $root '.local/demo/jwt-secret.txt')).Trim()
    $arguments = @('-Xmx512m', ('"-Djava.library.path=' + $native + '"'),
        '-Duser.timezone=UTC', '-jar', ('"' + $jar + '"'),
        '--spring.profiles.active=local',
        ('"--spring.config.additional-location=file:' +
            $config.Replace('\', '/') + '"'),
        '--app.journey.enabled=true', '--server.address=127.0.0.1',
        '--server.port=8081')
    $process = Start-Process -FilePath (Join-Path $jdk 'bin/java.exe') `
        -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $local 'stdout.log') `
        -RedirectStandardError (Join-Path $local 'stderr.log') -PassThru
    if ($process.HasExited) { throw 'Startup failed; read journey-v2 logs.' }
    @{id=$process.Id;startedAt=$process.StartTime.ToUniversalTime().ToString('o');
        port=8081} | ConvertTo-Json |
        Set-Content -LiteralPath $pidFile
    Write-Output ('Journey v2 starting on http://127.0.0.1:8081; PID ' +
        $process.Id + '. Logs: .local/journey-v2/')
} finally {
    $env:EQ_JWT_SECRET = $prior
}
