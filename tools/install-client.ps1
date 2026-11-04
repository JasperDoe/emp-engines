# Copies the pack into your CurseForge profile, so you can just press Play.
# Run it again after any pack change: it adds new mods, removes ones that
# left the pack, and updates configs, scripts and resource packs.
#
# One-time setup: in the CurseForge app make a custom profile named
# "Skybound SMP" (Minecraft 1.21.1, NeoForge). Then:
#   .\tools\install-client.ps1
#   .\tools\install-client.ps1 -Profile "Other Name"   (different profile name)
param([string]$Profile = 'Skybound SMP', [switch]$SkipDownload)
. "$PSScriptRoot\common.ps1"

# 1. Find the CurseForge profile folder
$roots = @(
    (Join-Path $HOME 'curseforge\minecraft\Instances'),
    (Join-Path $env:USERPROFILE 'Documents\curseforge\minecraft\Instances')
) | Where-Object { Test-Path $_ } | Select-Object -Unique
$target = $roots | ForEach-Object { Join-Path $_ $Profile } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $target) {
    Write-Host "Couldn't find a CurseForge profile called '$Profile'." -ForegroundColor Yellow
    Write-Host 'Create it once in the CurseForge app: Minecraft > Create Custom Profile,'
    Write-Host "name '$Profile', version 1.21.1, modloader NeoForge. Then run this again."
    if ($roots) {
        Write-Host 'Profiles found:'
        $roots | ForEach-Object { Get-ChildItem $_ -Directory } | ForEach-Object { Write-Host "  $($_.Name)" }
    }
    exit 1
}
Write-Host "Profile: $target"

# 2. Make sure the downloaded files match the pack
if (-not $SkipDownload) { & "$PSScriptRoot\download-mods.ps1"; if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit 1 } }
$src = Join-Path $Root 'mod-files'
$srcMods = Join-Path $src 'mods'
if (-not (Test-Path $srcMods)) { throw 'No downloaded mods. Run .\tools\download-mods.ps1 first.' }

# 3. Mods: mirror exactly (add new, replace changed, remove ones not in the pack)
$dstMods = Join-Path $target 'mods'
New-Item -ItemType Directory -Force $dstMods | Out-Null
$want = Get-ChildItem $srcMods -Filter *.jar
$wantNames = $want.Name
$removed = 0; $copied = 0
Get-ChildItem $dstMods -Filter *.jar | Where-Object { $wantNames -notcontains $_.Name } | ForEach-Object { Remove-Item $_.FullName; $removed++ }
foreach ($jar in $want) {
    $dst = Join-Path $dstMods $jar.Name
    if (-not (Test-Path $dst) -or (Get-Item $dst).Length -ne $jar.Length) { Copy-Item $jar.FullName $dst -Force; $copied++ }
}

# 4. Configs, scripts and resource packs from the pack (overwrites the pack's
#    own files; files you added yourself are left alone)
foreach ($dir in 'config', 'defaultconfigs', 'kubejs', 'resourcepacks') {
    $from = Join-Path $src $dir
    if (Test-Path $from) {
        $to = Join-Path $target $dir
        New-Item -ItemType Directory -Force $to | Out-Null
        Copy-Item (Join-Path $from '*') $to -Recurse -Force
    }
}

Write-Host "Done: $($want.Count) mods in the profile ($copied copied, $removed removed)." -ForegroundColor Green
Write-Host 'Open the CurseForge app and press Play on the profile.'
Write-Host 'Tip: give Minecraft about 8 GB in CurseForge Settings > Minecraft > Allocated Memory.'
