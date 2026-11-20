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
    if ($code -ne 0) {
        if (Show-ManualDownloads $log) { exit 1 }
        if (Select-String -Path $log -Pattern 'Failed to download .*: timeout' -Quiet) {
            Write-Host 'Some downloads timed out (network). Run the same command again; finished files are kept.' -ForegroundColor Yellow; exit 2
        }
        throw "Download failed, see $log"
    }
} finally { Pop-Location }

# Drop jars the pack no longer has: packwiz only deletes files it installed itself, so a jar put in mod-files\mods
# by hand (an old fix, a test build) would otherwise be installed forever. Keeps everything packwiz tracks and
# everything in manual-mods\.
$manifest = Join-Path $out 'packwiz.json'
if (Test-Path $manifest) {
    $tracked = @{}
    (Get-Content $manifest -Raw | ConvertFrom-Json).cachedFiles.PSObject.Properties | ForEach-Object {
        $loc = $_.Value.cachedLocation
        if ($loc) { $tracked[(Split-Path $loc -Leaf).ToLower()] = $true }
    }
    Get-ChildItem (Join-Path $Root 'manual-mods') -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $tracked[$_.Name.ToLower()] = $true }
    if ($tracked.Count -gt 20) {   # only with a real manifest
        Get-ChildItem (Join-Path $out 'mods') -Filter *.jar -ErrorAction SilentlyContinue |
            Where-Object { -not $tracked.ContainsKey($_.Name.ToLower()) } |
            ForEach-Object { Write-Host "Removing $($_.Name) (no longer in the pack)"; Remove-Item $_.FullName -Force }
    }
}

$jars = Get-ChildItem (Join-Path $out 'mods') -Filter *.jar -ErrorAction SilentlyContinue
$mb = [math]::Round((($jars | Measure-Object Length -Sum).Sum) / 1MB)
Write-Host "Done: $($jars.Count) mod files ($mb MB) in $out\mods"
