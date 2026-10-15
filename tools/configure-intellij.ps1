$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$localDir = Join-Path $backendRoot '.local'
$configDir = Join-Path $backendRoot '.idea\runConfigurations'
$jdkHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$nativeDir = Join-Path $env:LOCALAPPDATA 'EquiTransit\tools\sqljdbc-native'
New-Item -ItemType Directory -Force -Path $localDir,$configDir | Out-Null
$secretFile = Join-Path $localDir 'jwt-secret.txt'
if (-not (Test-Path -LiteralPath $secretFile)) {
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($bytes))
}
$secret = [IO.File]::ReadAllText($secretFile).Trim()
[IO.File]::WriteAllText((Join-Path $localDir 'application-secrets.properties'),
    'app.jwt.secret=' + $secret)
$options = '-Djava.library.path="' + $nativeDir + '" ' +
    '-Dspring.profiles.active=local ' +
    '-Dspring.config.additional-location=optional:file:.local/application-secrets.properties'
$escapedOptions = [Security.SecurityElement]::Escape($options)
$escapedJdk = [Security.SecurityElement]::Escape($jdkHome)
$xml = @"
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="EquiTransit Local" type="Application" factoryName="Application">
    <option name="MAIN_CLASS_NAME" value="com.equitransit.backend.BackendApplication" />
    <module name="backend" />
    <option name="ALTERNATIVE_JRE_PATH" value="$escapedJdk" />
    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
    <option name="VM_PARAMETERS" value="$escapedOptions" />
    <option name="WORKING_DIRECTORY" value="`$PROJECT_DIR`$" />
    <method v="2"><option name="Make" enabled="true" /></method>
  </configuration>
</component>
"@
[IO.File]::WriteAllText((Join-Path $configDir 'EquiTransit_Local.xml'), $xml)
Write-Output 'IntelliJ local run configuration created. No secrets are printed.'
