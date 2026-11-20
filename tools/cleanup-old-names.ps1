# One-off: removes the files left behind by the rename to Create: Empyrean Engines
# (createskybound -> emp_engines, dev.skybound -> dev.empengines, Shattered Skies -> Etheric Skies).
# The new files are already in place; these old copies would otherwise get built or packed twice.
#
#   .\tools\cleanup-old-names.ps1
#
# Safe to run more than once. Then rebuild: .\tools\build-mods.ps1 -SkipSetup
. "$PSScriptRoot\common.ps1"

$old = @(
    'mods\createskybound',                                   # the main mod's old folder (sources and its build output)
    'mods\expresearch\src\main\java\dev\skybound',           # old Java package folders
    'mods\chunkupkeep\src\main\java\dev\skybound',
    'mods\expresearch\build',                                # build output with old class names (rebuilt next build)
    'mods\chunkupkeep\build',
    'pack\kubejs\data\skybound',                             # research and stage data, now under data\emp_engines
    'tools\worldgen\build_shattered.py'                      # now build_etheric.py
)
$old += Get-ChildItem (Join-Path $Root 'pack\mods') -Filter 'createskybound-*.jar' -ErrorAction SilentlyContinue |
    ForEach-Object { "pack\mods\$($_.Name)" }

$found = $old | Where-Object { Test-Path (Join-Path $Root $_) }
if (-not $found) { Write-Host 'Nothing left to clean up.' -ForegroundColor Green; exit 0 }
Write-Host 'These old files and folders will be deleted:'
$found | ForEach-Object { Write-Host "  $_" }
$answer = Read-Host 'Delete them? (y/n)'
if ($answer -ne 'y') { Write-Host 'Nothing deleted.'; exit 0 }
foreach ($p in $found) { Remove-Item (Join-Path $Root $p) -Recurse -Force }
Write-Host 'Done. Next: .\tools\build-mods.ps1 -SkipSetup' -ForegroundColor Green
