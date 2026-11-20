# Copies raider ships you configured in-game (/skyraid config <ship> save) from your
# CurseForge profile into the pack, so they ship with it and the server gets them.
# They're saved in the profile at config\emp_engines\raider_ships\<name>.json.
#   .\tools\pull-raider-ships.ps1
#   .\tools\pull-raider-ships.ps1 -Profile "Other Name"
# install-client.ps1 runs this first, so a newer ship in the profile is never overwritten.
param([string]$Profile = 'Empyrean Engines', [switch]$Quiet)
. "$PSScriptRoot\common.ps1"

$roots = @(
    (Join-Path $HOME 'curseforge\minecraft\Instances'),
    (Join-Path $env:USERPROFILE 'Documents\curseforge\minecraft\Instances')
) | Where-Object { Test-Path $_ } | Select-Object -Unique
$names = @($Profile)
if (-not $PSBoundParameters.ContainsKey('Profile')) { $names += 'Skybound SMP' }
$target = foreach ($n in $names) { $roots | ForEach-Object { Join-Path $_ $n } | Where-Object { Test-Path $_ } }
$target = $target | Select-Object -First 1
if (-not $target) {
    if (-not $Quiet) { Write-Host "Couldn't find a CurseForge profile called '$Profile'." -ForegroundColor Yellow }
    exit 0
}

$from = Join-Path $target 'config\emp_engines\raider_ships'
$to = Join-Path $PackDir 'config\emp_engines\raider_ships'
if (-not (Test-Path $from)) {
    if (-not $Quiet) { Write-Host 'No raider ships configured in the profile yet (/skyraid config <ship> save).' }
    exit 0
}
New-Item -ItemType Directory -Force $to | Out-Null
$copied = 0
foreach ($f in Get-ChildItem $from -File | Where-Object { $_.Extension -in '.json', '.nbt' }) {
    $dst = Join-Path $to $f.Name
    $same = (Test-Path $dst) -and ((Get-FileHash $dst).Hash -eq (Get-FileHash $f.FullName).Hash)
    $older = (Test-Path $dst) -and ((Get-Item $dst).LastWriteTimeUtc -gt $f.LastWriteTimeUtc)
    if ($same -or $older) { continue }
    Copy-Item $f.FullName $dst -Force
    Write-Host "  raider ship: $($f.Name)"
    $copied++
}
if ($copied -gt 0) { Write-Host "$copied raider ship(s) copied into pack\config\emp_engines\raider_ships. Commit them to keep them." -ForegroundColor Green }
elseif (-not $Quiet) { Write-Host 'Raider ships in the pack are up to date.' }
