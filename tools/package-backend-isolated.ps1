<# Package the same source without replacing a JAR held by another runtime. #>
[CmdletBinding()]
param([switch]$SkipTests,
    [ValidatePattern('^\.local/[a-z0-9-]+$')]
    [string]$OutputDirectory = '.local/route-package')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$temporary = Join-Path $root ('.local-package-' + [guid]::NewGuid() + '.pom.xml')
$jdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$previousJava = $env:JAVA_HOME
$previousPath = $env:Path
try {
    [xml]$model = Get-Content -Raw -LiteralPath (Join-Path $root 'pom.xml')
    $ns = [Xml.XmlNamespaceManager]::new($model.NameTable)
    $ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $build = $model.SelectSingleNode('/m:project/m:build', $ns)
    $directory = $model.CreateElement('directory', $ns.LookupNamespace('m'))
    $directory.InnerText = $OutputDirectory
    $build.AppendChild($directory) | Out-Null
    $model.Save($temporary)
    $env:JAVA_HOME = $jdk
    $env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
    Push-Location -LiteralPath $root
    try {
        $arguments = @('-B', '-f', $temporary, 'verify')
        if ($SkipTests) { $arguments += '-DskipTests' }
        & .\mvnw.cmd @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Isolated backend packaging failed.' }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:Path = $previousPath
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary
    }
}
