<# Resets ONLY EquiTransit_Demo from its empty, fully migrated schema backup.
The source database is read-only. No business row is copied from the source.
#>
[CmdletBinding()]
param(
    [string]$Server = 'localhost,1433',
    [string]$SourceDatabase = 'EquiTransit',
    [switch]$InitializeOnly,
    [switch]$CatalogOnly,
    [switch]$ResumeInitialization
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local/demo'
$manifestPath = Join-Path $local 'manifest.json'
$database = 'EquiTransit_Demo'
$clock = [Diagnostics.Stopwatch]::StartNew()
New-Item -ItemType Directory -Force -Path $local | Out-Null
if ($SourceDatabase -eq $database) { throw 'Source cannot be the demo DB.' }
if (-not $InitializeOnly) {
    & (Join-Path $PSScriptRoot 'run-demo-local.ps1') -Stop
    if (Get-NetTCPConnection -State Listen -LocalPort 8080 `
        -ErrorAction SilentlyContinue) {
        throw '8080 belongs to another app; stop it before resetting Demo.'
    }
}
function Connect([string]$Catalog) {
    $b = [Data.SqlClient.SqlConnectionStringBuilder]::new()
    $b['Data Source'] = $Server
    $b['Initial Catalog'] = $Catalog
    $b['Integrated Security'] = $true
    $b['Encrypt'] = $true
    $b['TrustServerCertificate'] = $true
    $c = [Data.SqlClient.SqlConnection]::new($b.ConnectionString)
    $c.Open()
    return $c
}
function Query($Connection, [string]$Sql, [hashtable]$Parameters = @{}) {
    $cmd = $Connection.CreateCommand()
    $cmd.CommandTimeout = 180
    $cmd.CommandText = $Sql
    foreach ($key in $Parameters.Keys) {
        [void]$cmd.Parameters.AddWithValue('@' + $key, $Parameters[$key])
    }
    $adapter = [Data.SqlClient.SqlDataAdapter]::new($cmd)
    $data = [Data.DataSet]::new()
    try { [void]$adapter.Fill($data); return ,$data }
    finally { $adapter.Dispose(); $cmd.Dispose() }
}
function RunFile($Connection, [string]$Path) {
    $sql = [IO.File]::ReadAllText($Path)
    if ($Connection.Database -cne 'EquiTransit_Demo') {
        throw 'DDL execution is restricted to the exact demo database.'
    }
    # 002 predates Demo. Adapt ONLY its catalog allowlist in memory;
    # checked-in migration files and real-database guards stay unchanged.
    if ([IO.Path]::GetFileName($Path) -eq
        '002_align_figma_without_organization.sql') {
        $sql = $sql.Replace("DB_NAME() <> N'EquiTransit'",
            "DB_NAME() <> N'EquiTransit_Demo'")
    }
    foreach ($batch in [regex]::Split($sql,
            '(?im)^\s*GO\s*(?:--[^\r\n]*)?$')) {
        if (-not [string]::IsNullOrWhiteSpace($batch)) {
            $null = Query $Connection $batch
        }
    }
}
$master = Connect 'master'
try {
    $null = Query $master @'
DECLARE @result int;
EXEC @result=sys.sp_getapplock @Resource='EquiTransit:demo-reset',
 @LockMode='Exclusive',@LockOwner='Session',@LockTimeout=1000;
IF @result<0 THROW 51000,'Another demo reset is running.',1;
'@
    $exists = (Query $master 'SELECT DB_ID(@name) AS id' `
        @{name=$database}).Tables[0].Rows[0].id -ne [DBNull]::Value
    $migrations = @(Get-ChildItem (Join-Path $root 'database/migrations') `
        -Filter '*.sql' | Sort-Object Name)
    $migrationHash = ($migrations | ForEach-Object {
        (Get-FileHash $_.FullName -Algorithm SHA256).Hash
    }) -join ':'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        if ($exists -and -not $ResumeInitialization) {
            throw 'Demo exists without manifest; inspect before resuming.'
        }
        $export = Join-Path $local 'source-schema.sql'
        if (-not $exists) {
            & (Join-Path $PSScriptRoot 'export-demo-schema.ps1') `
                -Server $Server -SourceDatabase $SourceDatabase `
                -OutputPath $export
            $null = Query $master 'CREATE DATABASE [EquiTransit_Demo];'
        }
        $demo = Connect $database
        try {
            if (-not $exists) { RunFile $demo $export }
            RunFile $demo (Join-Path $root `
                'database/bootstrap/000_figma_schema.sql')
            foreach ($file in $migrations) {
                RunFile $demo $file.FullName
                Write-Output ('Applied ' + $file.Name)
            }
            RunFile $demo (Join-Path $root 'database/verify-f12-f14-schema.sql')
            # Copy-only backup of the EMPTY demo schema, never the real DB.
            $backup = (Query $master @'
DECLARE @dir nvarchar(4000)=CAST(SERVERPROPERTY('InstanceDefaultBackupPath')
 AS nvarchar(4000));
DECLARE @file nvarchar(4000)=@dir+N'\EquiTransit_Demo_empty_'+
 REPLACE(CONVERT(varchar(36),NEWID()),'-','')+N'.bak';
BACKUP DATABASE [EquiTransit_Demo] TO DISK=@file WITH COPY_ONLY,CHECKSUM;
RESTORE VERIFYONLY FROM DISK=@file WITH CHECKSUM;
SELECT @file AS path;
'@).Tables[0].Rows[0].path
            $manifest = [ordered]@{database=$database;server=$Server
                source=$SourceDatabase;backup=$backup
                migrationHash=$migrationHash
                schemaHash=(Get-FileHash $export -Algorithm SHA256).Hash
                createdAt=[DateTime]::UtcNow.ToString('o')}
            $manifest | ConvertTo-Json | Set-Content $manifestPath -Encoding utf8
        } finally { $demo.Dispose() }
    } else {
        $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
        if ($manifest.database -cne $database -or
            $manifest.server -cne $Server -or
            $manifest.migrationHash -ne $migrationHash) {
            throw 'Manifest mismatch. Rebuild a reviewed empty demo baseline.'
        }
        $header = (Query $master 'RESTORE HEADERONLY FROM DISK=@file' `
            @{file=$manifest.backup}).Tables[0].Rows[0]
        if ($header.DatabaseName -cne $database) {
            throw 'Backup is not an EquiTransit_Demo backup; restore refused.'
        }
        [Data.SqlClient.SqlConnection]::ClearAllPools()
        $null = Query $master @'
RESTORE VERIFYONLY FROM DISK=@file WITH CHECKSUM;
ALTER DATABASE [EquiTransit_Demo] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
BEGIN TRY
 RESTORE DATABASE [EquiTransit_Demo] FROM DISK=@file WITH REPLACE,CHECKSUM;
 ALTER DATABASE [EquiTransit_Demo] SET MULTI_USER;
END TRY
BEGIN CATCH
 ALTER DATABASE [EquiTransit_Demo] SET MULTI_USER;
 THROW;
END CATCH;
'@ @{file=$manifest.backup}
    }
    if ($InitializeOnly) { Write-Output 'Empty demo schema ready.'; return }
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
    if (-not $javaHome) { throw 'Set user JAVA_HOME to JDK21.' }
    $crypto = Get-ChildItem "$env:USERPROFILE/.m2/repository/org/springframework/security/spring-security-crypto" -Recurse -Filter '*.jar' |
        Sort-Object FullName -Descending | Select-Object -First 1
    $jcl = Get-ChildItem "$env:USERPROFILE/.m2/repository/org/springframework/spring-jcl" -Recurse -Filter '*.jar' |
        Sort-Object FullName -Descending | Select-Object -First 1
    $hashSource = Join-Path $local 'DemoPasswordHash.java'
    @'
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
class DemoPasswordHash {
    public static void main(String[] args) {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var hash = encoder.encode("EquiTransit@123");
        if (!encoder.matches("EquiTransit@123", hash)) {
            throw new IllegalStateException("BCrypt verification failed");
        }
        System.out.print(hash);
    }
}
'@ | Set-Content $hashSource -Encoding ascii
    $hash = & (Join-Path $javaHome 'bin/java.exe') -cp (
        $crypto.FullName + ';' + $jcl.FullName) $hashSource
    if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^\{bcrypt\}\$2[aby]\$') {
        throw 'Project BCrypt encoder failed.'
    }
    $demo = Connect $database
    try {
        $seed = [IO.File]::ReadAllText(
            (Join-Path $PSScriptRoot 'seed-demo-data.sql'))
        $null = Query $demo $seed @{passwordHash=[string]$hash}
    } finally { $demo.Dispose() }
    $override = Join-Path $local 'application-demo.properties'
    $jdbcServer=$Server.Replace(',',':')
    @"
spring.datasource.url=jdbc:sqlserver://$jdbcServer;databaseName=EquiTransit_Demo;encrypt=true;trustServerCertificate=true;integratedSecurity=true
app.mail.mode=file
app.notifications.worker-enabled=false
"@ | Set-Content $override -Encoding ascii
    if (-not $CatalogOnly) {
        & (Join-Path $PSScriptRoot 'run-demo-local.ps1')
        & (Join-Path $PSScriptRoot 'seed-demo-orders.ps1') -Server $Server
        $fe=Join-Path (Split-Path -Parent $root) 'FE/SWP391_FA26_EQUITRANSIT_FE'
        $vite=Join-Path $fe 'node_modules/vite/bin/vite.js'
        if (-not (Get-NetTCPConnection -State Listen -LocalPort 5173 `
                -ErrorAction SilentlyContinue) -and (Test-Path $vite)) {
            $oldApi=$env:VITE_API_BASE_URL
            try {
                $env:VITE_API_BASE_URL='http://127.0.0.1:8080'
                $frontend=Start-Process -FilePath (Get-Command node).Source `
                    -ArgumentList @('"'+$vite+'"','--host','localhost') `
                    -WorkingDirectory $fe -WindowStyle Hidden `
                    -RedirectStandardOutput (Join-Path $local 'frontend.out.log') `
                    -RedirectStandardError (Join-Path $local 'frontend.err.log') `
                    -PassThru
                @{id=$frontend.Id}|ConvertTo-Json|Set-Content (
                    Join-Path $local 'frontend-pid.json')
            } finally { $env:VITE_API_BASE_URL=$oldApi }
        }
    }
    Write-Output ('Demo seeded in ' +
        [Math]::Round($clock.Elapsed.TotalSeconds,1) +
        's. Local override: ' + $override)
} finally { $master.Dispose() }
