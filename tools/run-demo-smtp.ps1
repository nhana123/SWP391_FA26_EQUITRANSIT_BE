<# Configure Gmail only for this machine, then start the managed Demo runtime. #>
[CmdletBinding()]
param(
    [switch]$Configure,
    [ValidateRange(1024, 65535)] [int]$Port = 8082,
    [string]$JarPath = '.local/manager-deposit-final/backend-0.1.0-SNAPSHOT.jar'
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') {
    throw 'This helper requires Windows user-bound credential encryption.'
}
$root = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $root '.local/demo'
$file = Join-Path $directory 'smtp-credentials.xml'
$usernameFile = Join-Path $directory 'smtp-username.txt'
if (-not (Test-Path -LiteralPath $directory)) {
    throw 'Configure EquiTransit_Demo first.'
}
if ($Configure -or -not (Test-Path -LiteralPath $file)) {
    $username = if (Test-Path -LiteralPath $usernameFile) {
        [IO.File]::ReadAllText($usernameFile).Trim()
    } else { Read-Host 'Gmail sending address' }
    $credential = Get-Credential -UserName $username -Message (
        'Enter a NEW Gmail App Password, not the exposed password or Gmail login password.')
    if (-not $credential -or $credential.Password.Length -eq 0) {
        throw 'No credential supplied. Existing runtime was not stopped.'
    }
    $credential | Export-Clixml -LiteralPath $file
} else {
    $credential = Import-Clixml -LiteralPath $file
}
if ($credential -isnot [Management.Automation.PSCredential]) {
    throw 'The encrypted SMTP credential is invalid.'
}
$values = @{
    EQ_MAIL_MODE = 'smtp'
    EQ_MAIL_HOST = 'smtp.gmail.com'
    EQ_MAIL_PORT = '587'
    EQ_MAIL_USERNAME = $credential.UserName
    EQ_MAIL_FROM = $credential.UserName
    EQ_MAIL_PASSWORD = $credential.GetNetworkCredential().Password.Replace(' ', '')
}
$previous = @{}
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name)
        [Environment]::SetEnvironmentVariable($name, $values[$name])
    }
    & (Join-Path $PSScriptRoot 'run-journey-v2-demo.ps1') -Stop -Port $Port
    & (Join-Path $PSScriptRoot 'run-journey-v2-demo.ps1') -Port $Port -JarPath $JarPath
    Write-Output 'SMTP enabled for this Demo process. Register a test account to verify delivery.'
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name])
    }
    $values.Clear()
    $credential = $null
}
