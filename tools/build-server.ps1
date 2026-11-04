# Builds the server pack: server-side mods + shared files from pack\,
# with the server\ layer copied on top. Output:
#   build\server-pack\                       (ready-to-run folder)
#   build\skybound-server-<version>.zip      (upload this to your host)
#
# Usage:  .\tools\build-server.ps1
. "$PSScriptRoot\common.ps1"
Assert-Java21
$bootstrap = Get-Bootstrap
$packwiz = Get-Packwiz
$version = Get-PackValue 'version'
$neo = Get-PackValue 'neoforge'
$out = Join-Path $Build 'server-pack'
$log = Join-Path $Build 'build-server.log'
Set-Content $log ''

# Make sure the pack index matches the files (needed after any manual edit)
Push-Location $PackDir
try { Invoke-Logged $log $packwiz @('refresh') | Out-Null } finally { Pop-Location }

# Keep the folder between builds: packwiz-installer only downloads what changed
# and removes mods that left the pack (it tracks them in packwiz.json).
New-Item -ItemType Directory -Force $out | Out-Null

# 1. Server-side mods and shared files (packwiz skips client-only mods)
Write-Host 'Collecting server-side mods...'
$packUri = ([Uri](Join-Path $PackDir 'pack.toml')).AbsoluteUri
Copy-ManualMods $out

# Reuse jars already downloaded into mod-files\mods (by download-mods.ps1) so
# only missing ones are fetched. Client-only mods are never copied.
$cache = Join-Path $Root 'mod-files\mods'
if (Test-Path $cache) {
    $destMods = Join-Path $out 'mods'
    New-Item -ItemType Directory -Force $destMods | Out-Null
    $reused = 0
    foreach ($meta in Get-ChildItem (Join-Path $PackDir 'mods') -Filter *.pw.toml) {
        $text = Get-Content $meta.FullName -Raw
        $file = if ($text -match '(?m)^filename\s*=\s*"([^"]+)"') { $Matches[1] } else { $null }
        $side = if ($text -match '(?m)^side\s*=\s*"([^"]+)"') { $Matches[1] } else { 'both' }
        if (-not $file -or $side -eq 'client') { continue }
        $jar = Join-Path $cache $file
        $dst = Join-Path $destMods $file
        if ((Test-Path $jar) -and -not (Test-Path $dst)) { Copy-Item $jar $dst; $reused++ }
    }
    if ($reused) { Write-Host "Reused $reused already-downloaded mod(s) from mod-files\mods" }
}
Push-Location $out
try {
    $code = Invoke-Logged $log 'java' @('-jar', $bootstrap, '-g', '-s', 'server', $packUri) 3600
    if ($code -ne 0) {
        if (Show-ManualDownloads $log) { exit 1 }
        if (Select-String -Path $log -Pattern 'Failed to download .*: timeout' -Quiet) {
            Write-Host 'Some downloads timed out (network). Run the same command again; finished files are kept.' -ForegroundColor Yellow; exit 2
        }
        throw "Mod download failed, see $log"
    }
} finally { Pop-Location }

# 2. Server layer on top (server.properties, start scripts, JVM args)
Copy-Item (Join-Path $Root 'server\*') $out -Recurse -Force -Exclude 'README.md'
Set-Content (Join-Path $out 'neoforge-version.txt') $neo -NoNewline

# 3. Zip for upload
$zip = Join-Path $Build "skybound-server-$version.zip"
if (Test-Path $zip) { Remove-Item $zip }
$items = Get-ChildItem $out | Where-Object { $_.Name -notin 'packwiz.json', 'packwiz-installer.jar' }
Compress-Archive -Path $items.FullName -DestinationPath $zip
$mods = (Get-ChildItem (Join-Path $out 'mods') -Filter *.jar -ErrorAction SilentlyContinue).Count
Write-Host "Server pack built: $mods mods, NeoForge $neo"
Write-Host "  folder: $out"
Write-Host "  zip:    $zip"
