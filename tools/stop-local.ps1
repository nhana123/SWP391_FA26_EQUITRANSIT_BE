$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$recordPath = Join-Path $backendRoot '.local\runtime-process.json'
if (-not (Test-Path -LiteralPath $recordPath)) {
    Write-Output 'No managed background backend process is recorded.'
    exit
}
$record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
$backendProcess = Get-Process -Id $record.processId -ErrorAction SilentlyContinue
if (-not $backendProcess) {
    Write-Output 'The recorded backend has already stopped.'
    exit
}
if ($backendProcess.ProcessName -ne 'java' -or
    $backendProcess.StartTime.ToUniversalTime().Ticks -ne $record.startTicks) {
    throw 'Process identity changed; refusing to stop an unrelated process.'
}
Stop-Process -Id $backendProcess.Id
Write-Output 'The managed EquiTransit background backend has stopped.'
