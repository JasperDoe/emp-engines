# Builds the server pack: server-side mods + shared files from pack\,
# with the server\ layer copied on top. Output:
#   build\server-pack\                       (ready-to-run folder)
#   build\skybound-server-<version>.zip      (upload this to your host)
#
# Usage:  .\tools\build-server.ps1
. "$PSScriptRoot\common.ps1"
Assert-Java21
$bootstrap = Get-Bootstrap
$version = Get-PackValue 'version'
$neo = Get-PackValue 'neoforge'
$out = Join-Path $Build 'server-pack'
$log = Join-Path $Build 'build-server.log'
Set-Content $log ''

# Fresh folder each build so removed mods don't linger
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory $out | Out-Null

# 1. Server-side mods and shared files (packwiz skips client-only mods)
Write-Host 'Collecting server-side mods...'
$packUri = ([Uri](Join-Path $PackDir 'pack.toml')).AbsoluteUri
Push-Location $out
try {
    $code = Invoke-Logged $log 'java' @('-jar', $bootstrap, '-g', '-s', 'server', $packUri) 3600
    if ($code -ne 0) { throw "Mod download failed, see $log" }
} finally { Pop-Location }
Remove-Item (Join-Path $out 'packwiz.json') -ErrorAction SilentlyContinue

# 2. Server layer on top (server.properties, start scripts, JVM args)
Copy-Item (Join-Path $Root 'server\*') $out -Recurse -Force -Exclude 'README.md'
Set-Content (Join-Path $out 'neoforge-version.txt') $neo -NoNewline

# 3. Zip for upload
$zip = Join-Path $Build "skybound-server-$version.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path (Join-Path $out '*') -DestinationPath $zip
$mods = (Get-ChildItem (Join-Path $out 'mods') -Filter *.jar -ErrorAction SilentlyContinue).Count
Write-Host "Server pack built: $mods mods, NeoForge $neo"
Write-Host "  folder: $out"
Write-Host "  zip:    $zip"
