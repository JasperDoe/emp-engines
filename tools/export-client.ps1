# Builds the client pack for players:
#   build\Skybound SMP-<version>.zip     (import in the CurseForge app)
#   build\Skybound SMP-<version>.mrpack  (import in Modrinth app or Prism)
#
# Usage:  .\tools\export-client.ps1
. "$PSScriptRoot\common.ps1"
$packwiz = Get-Packwiz
$log = Join-Path $Build 'export-client.log'
Set-Content $log ''
Push-Location $PackDir
try {
    Invoke-Logged $log $packwiz @('refresh') | Out-Null
    $cf = Invoke-Logged $log $packwiz @('-y', 'curseforge', 'export')
    $mr = Invoke-Logged $log $packwiz @('-y', 'modrinth', 'export')
    Get-ChildItem -File | Where-Object { $_.Extension -in '.zip', '.mrpack' } | Move-Item -Destination $Build -Force
} finally { Pop-Location }
if ($cf -ne 0) { Write-Host 'CurseForge export had problems (often a mod that is Modrinth-only). See build\export-client.log' -ForegroundColor Yellow }
if ($mr -ne 0) { Write-Host 'Modrinth export had problems. See build\export-client.log' -ForegroundColor Yellow }
Get-ChildItem $Build -File | Where-Object { $_.Extension -in '.zip', '.mrpack' -and $_.Name -notlike 'skybound-server-*' } |
    ForEach-Object { Write-Host "  $($_.FullName)" }
