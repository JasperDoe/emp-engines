# Adds every mod in mods.txt to the pack (plus dependencies), pinned to
# Minecraft 1.21.1 + NeoForge. Safe to re-run: mods already added are skipped.
#
# Usage (from the repo folder):  .\tools\setup-mods.ps1
. "$PSScriptRoot\common.ps1"
$packwiz = Get-Packwiz
$log = Join-Path $Root 'setup.log'
$failedFile = Join-Path $Root 'failed.txt'
Set-Content $failedFile ''
Push-Location $PackDir
try {
    # Pin to the newest NeoForge 21.1.x build
    Invoke-Logged $log $packwiz @('-y', 'migrate', 'loader', 'latest') | Out-Null

    $added = 0; $failed = 0; $skipped = 0
    foreach ($raw in Get-Content (Join-Path $Root 'mods.txt')) {
        $entry = ($raw -split '#')[0].Trim()
        if (-not $entry) { continue }
        $platform = 'any'; $slug = $entry
        if ($entry -like 'cf:*') { $platform = 'cf'; $slug = $entry.Substring(3) }
        elseif ($entry -like 'mr:*') { $platform = 'mr'; $slug = $entry.Substring(3) }

        if (Test-Path (Join-Path $PackDir "mods\$slug.pw.toml")) { $skipped++; continue }
        Write-Host "==> $slug"
        $ok = $false
        if ($platform -ne 'cf') { $ok = (Invoke-Logged $log $packwiz @('-y', 'modrinth', 'add', $slug) 180) -eq 0 }
        if (-not $ok -and $platform -ne 'mr') { $ok = (Invoke-Logged $log $packwiz @('-y', 'curseforge', 'add', $slug) 180) -eq 0 }
        if ($ok) { $added++ } else { $failed++; Add-Content $failedFile $slug; Write-Host '    !! not found for 1.21.1 NeoForge' -ForegroundColor Yellow }
    }

    Invoke-Logged $log $packwiz @('refresh') | Out-Null
    # packwiz falls back to a search when a slug doesn't match exactly,
    # so check this list for anything that isn't the mod you meant.
    & $packwiz list > (Join-Path $Root 'resolved-mods.txt')
} finally { Pop-Location }

Write-Host ''
Write-Host "Added: $added   Already in pack: $skipped   Failed: $failed"
if ($failed -gt 0) { Write-Host 'See failed.txt (wrong slug, or no 1.21.1 NeoForge build). Details in setup.log.' -ForegroundColor Yellow }
Write-Host 'Check resolved-mods.txt, then run .\tools\test-server.ps1'
