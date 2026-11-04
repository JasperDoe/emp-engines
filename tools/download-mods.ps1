# Downloads the actual mod .jar files (client + server) into mod-files\mods,
# plus the pack's configs and scripts, exactly as a player would get them.
# mod-files\ is never committed to git.
#
# Usage:  .\tools\download-mods.ps1
. "$PSScriptRoot\common.ps1"
Assert-Java21
$bootstrap = Get-Bootstrap
$packwiz = Get-Packwiz
$out = Join-Path $Root 'mod-files'
$log = Join-Path $Build 'download-mods.log'
Set-Content $log ''
New-Item -ItemType Directory -Force $out | Out-Null

Push-Location $PackDir
try { Invoke-Logged $log $packwiz @('refresh') | Out-Null } finally { Pop-Location }

Write-Host 'Downloading mod files (this can take a while the first time)...'
$packUri = ([Uri](Join-Path $PackDir 'pack.toml')).AbsoluteUri
Copy-ManualMods $out
Push-Location $out
try {
    $code = Invoke-Logged $log 'java' @('-jar', $bootstrap, '-g', '-s', 'both', $packUri) 3600
    if ($code -ne 0) { if (Show-ManualDownloads $log) { exit 1 }; throw "Download failed, see $log" }
} finally { Pop-Location }

$jars = Get-ChildItem (Join-Path $out 'mods') -Filter *.jar -ErrorAction SilentlyContinue
$mb = [math]::Round((($jars | Measure-Object Length -Sum).Sum) / 1MB)
Write-Host "Done: $($jars.Count) mod files ($mb MB) in $out\mods"
