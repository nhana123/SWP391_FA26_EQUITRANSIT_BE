param([ValidateSet('inspect','publish')][string]$Mode = 'inspect')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apiRoot = 'https://api.github.com'
$repository = 'nhana123/SWP391_FA26_EQUITRANSIT_BE'
$env:GIT_TERMINAL_PROMPT = '0'
$credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill
if ($LASTEXITCODE -ne 0) { throw 'GitHub authentication is unavailable.' }
$credential = @{}
foreach ($line in $credentialLines) {
    $parts = $line -split '=',2
    if ($parts.Length -eq 2) { $credential[$parts[0]] = $parts[1] }
}
if (-not $credential.password) { throw 'No credential returned by Git.' }
$headers = @{
    Authorization = 'Bearer ' + $credential.password
    Accept = 'application/vnd.github+json'
    'User-Agent' = 'EquiTransit-Team-Planning'
    'X-GitHub-Api-Version' = '2022-11-28'
}
function Request-GitHub([string]$Method,[string]$Path,$Body=$null) {
    $params = @{Uri=($apiRoot+$Path);Method=$Method;Headers=$headers}
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30))
    }
    Invoke-RestMethod @params
}
if ($Mode -eq 'inspect') {
    $repo = Request-GitHub GET "/repos/$repository"
    $collaborators = Request-GitHub GET "/repos/$repository/collaborators?per_page=100"
    $issues = Request-GitHub GET "/repos/$repository/issues?state=all&per_page=100"
    [pscustomobject]@{
        owner=$repo.owner.login
        defaultBranch=$repo.default_branch
        collaborators=@($collaborators | ForEach-Object { $_.login })
        issues=@($issues | Where-Object { -not $_.pull_request } | ForEach-Object {
            @{number=$_.number;title=$_.title;state=$_.state;url=$_.html_url}
        })
    } | ConvertTo-Json -Depth 8
    exit
}
$planPath = Join-Path $repoRoot 'docs\team\issues.json'
$plan = Get-Content -LiteralPath $planPath -Raw -Encoding utf8 | ConvertFrom-Json
$labels = @(
    @{name='be1';color='2563eb';description='BE1: customer, booking, pricing and finance'},
    @{name='be2';color='16a34a';description='BE2: compliance, planning and operations'},
    @{name='business-flow';color='5319e7';description='One issue represents one complete business flow'},
    @{name='baseline-review';color='c2e0c6';description='Implementation exists; review and acceptance remain'},
    @{name='mvp-core';color='fbca04';description='Required MVP flow'},
    @{name='mvp-extension';color='f9d0c4';description='Requested extension, lower delivery priority'}
)
$existingLabels = @(Request-GitHub GET "/repos/$repository/labels?per_page=100")
foreach ($label in $labels) {
    if ($label.name -notin $existingLabels.name) {
        $null = Request-GitHub POST "/repos/$repository/labels" $label
    }
}
$existing = @(Request-GitHub GET "/repos/$repository/issues?state=all&per_page=100")
$mapping = @()
foreach ($issue in $plan) {
    $match = $existing | Where-Object { $_.title -eq $issue.title -and -not $_.pull_request } | Select-Object -First 1
    if ($match) {
        $created = $match
    } else {
        $body = @{title=$issue.title;body=$issue.body;labels=@($issue.labels)}
        if ($issue.assignee) { $body.assignees=@($issue.assignee) }
        $created = Request-GitHub POST "/repos/$repository/issues" $body
    }
    $mapping += @{
        flow=$issue.flow;number=$created.number;url=$created.html_url
        owner=$issue.owner;assignees=@($created.assignees | ForEach-Object { $_.login })
    }
    Write-Output ("{0}: #{1} {2}" -f $issue.flow,$created.number,$created.html_url)
}
$mapping | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $repoRoot 'docs\team\github-issues.json') -Encoding utf8
