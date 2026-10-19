<# Start the current packaged backend on 8080 with ignored demo-only config.
No shared application*.yml is changed. Does not run tests or Maven.
#>
[CmdletBinding()]
param([switch]$Stop)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local/demo'
$pidFile = Join-Path $local 'backend-pid.json'
if ($Stop) {
    if (Test-Path $pidFile) {
        $owned = Get-Content $pidFile -Raw | ConvertFrom-Json
        $process = Get-Process -Id $owned.id -ErrorAction SilentlyContinue
        $details = Get-CimInstance Win32_Process -Filter (
            'ProcessId=' + $owned.id) -ErrorAction SilentlyContinue
        if ($process) {
            $difference = [Math]::Abs(($process.StartTime.ToUniversalTime() -
                ([DateTimeOffset]$owned.startedAt).UtcDateTime).TotalSeconds)
            if ($difference -gt 2 -or $details.CommandLine -notlike
                '*demo/application-demo.properties*') {
                throw 'PID ownership mismatch; will not stop an unrelated app.'
            }
            $process | Stop-Process
            $process.WaitForExit(10000) | Out-Null
        }
        Remove-Item -LiteralPath $pidFile
    }
    return
}
if (Get-NetTCPConnection -State Listen -LocalPort 8080 `
    -ErrorAction SilentlyContinue) {
    throw '8080 is occupied. Stop that backend before starting the demo.'
}
$config = Join-Path $local 'application-demo.properties'
if (-not (Test-Path $config)) { throw 'Run tools/run-demo-seed.ps1 first.' }
$jar = Join-Path $root 'target/backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw 'Package the current dev backend first.' }
$jdk = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$native = Join-Path $env:LOCALAPPDATA 'EquiTransit/tools/sqljdbc-native'
if (-not (Test-Path $native)) {
    $native = Join-Path $root '.local/tools/sqljdbc-native'
}
$secretPath = Join-Path $local 'jwt-secret.txt'
if (-not (Test-Path $secretPath)) {
    $bytes = [byte[]]::new(48)
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [IO.File]::WriteAllText($secretPath,[Convert]::ToBase64String($bytes))
}
$oldSecret = $env:EQ_JWT_SECRET
try {
    $env:EQ_JWT_SECRET = [IO.File]::ReadAllText($secretPath).Trim()
    $arguments = @('"-Djava.library.path=' + $native + '"',
        '-Duser.timezone=UTC','-jar',
        '"' + $jar + '"','--spring.profiles.active=local',
        '"--spring.config.additional-location=file:' +
        $config.Replace('\','/') + '"','--server.address=127.0.0.1',
        '--server.port=8080','--app.cors-origin=http://localhost:5173')
    $process = Start-Process -FilePath (Join-Path $jdk 'bin/java.exe') `
        -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $local 'backend.out.log') `
        -RedirectStandardError (Join-Path $local 'backend.err.log') -PassThru
    @{id=$process.Id;startedAt=$process.StartTime.ToUniversalTime().ToString('o')} |
        ConvertTo-Json | Set-Content $pidFile
} finally { $env:EQ_JWT_SECRET = $oldSecret }
for ($attempt=0; $attempt -lt 90; $attempt++) {
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/health' `
            -TimeoutSec 2
        if ($health.status -eq 'UP') {
            Write-Output 'Demo backend UP: http://127.0.0.1:8080'
            return
        }
    } catch { }
    if ($process.HasExited) { throw 'Backend stopped; see .local/demo logs.' }
    Start-Sleep -Milliseconds 500
}
throw 'Backend startup timed out; see .local/demo logs.'
