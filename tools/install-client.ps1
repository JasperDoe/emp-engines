# Copies the pack into your CurseForge profile, so you can just press Play.
# Run it again after any pack change: it adds new mods, removes ones that
# left the pack, and updates configs, scripts and resource packs.
#
# One-time setup: in the CurseForge app make a custom profile named
# "Empyrean Engines" (Minecraft 1.21.1, NeoForge). An older profile named
# "Skybound SMP" is used if there is no "Empyrean Engines" one. Then:
#   .\tools\install-client.ps1
#   .\tools\install-client.ps1 -Profile "Other Name"   (different profile name)
param([string]$Profile = 'Empyrean Engines', [switch]$SkipDownload)
. "$PSScriptRoot\common.ps1"

# 1. Find the CurseForge profile folder
$roots = @(
    (Join-Path $HOME 'curseforge\minecraft\Instances'),
    (Join-Path $env:USERPROFILE 'Documents\curseforge\minecraft\Instances')
) | Where-Object { Test-Path $_ } | Select-Object -Unique
$names = @($Profile)
if (-not $PSBoundParameters.ContainsKey('Profile')) { $names += 'Skybound SMP' }   # the profile's name before the pack was renamed
$target = foreach ($n in $names) { $roots | ForEach-Object { Join-Path $_ $n } | Where-Object { Test-Path $_ } }
$target = $target | Select-Object -First 1
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

# Raider ships configured in-game go into the pack first, so the copy below can't overwrite them
& "$PSScriptRoot\pull-raider-ships.ps1" -Profile (Split-Path $target -Leaf) -Quiet

# 2. Apply mods.txt changes, then make sure the downloaded files match the pack
#    (retries automatically when downloads time out)
if (-not $SkipDownload) {
    & "$PSScriptRoot\setup-mods.ps1"
    for ($i = 1; $i -le 4; $i++) {
        $global:LASTEXITCODE = 0
        & "$PSScriptRoot\download-mods.ps1"
        if ($LASTEXITCODE -ne 2) { break }
        Write-Host "Retrying downloads ($i of 4)..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
    if ($LASTEXITCODE -ne 0) { exit 1 }
}
$src = Join-Path $Root 'mod-files'
$srcMods = Join-Path $src 'mods'
if (-not (Test-Path $srcMods)) { throw 'No downloaded mods. Run .\tools\download-mods.ps1 first.' }

# 3. Mods: mirror exactly (add new, replace changed, remove ones not in the pack)
$dstMods = Join-Path $target 'mods'
New-Item -ItemType Directory -Force $dstMods | Out-Null
# Manually-distributed mods (e.g. Create: New Age) live in manual-mods\, not in
# the packwiz cache. They must reach the client too, or it will be missing their
# registries and the server will reject the connection ("unknown keys ..."). Treat
# them as wanted so the mirror below keeps and copies them instead of deleting them.
$want = @(Get-ChildItem $srcMods -Filter *.jar)
$manualMods = Join-Path $Root 'manual-mods'
$want += @(Get-ChildItem $manualMods -Filter *.jar -ErrorAction SilentlyContinue)
$wantNames = $want.Name
$removed = 0; $copied = 0
Get-ChildItem $dstMods -Filter *.jar | Where-Object { $wantNames -notcontains $_.Name } | ForEach-Object { Remove-Item $_.FullName; $removed++ }
foreach ($jar in $want) {
    $dst = Join-Path $dstMods $jar.Name
    # size alone misses a rebuilt jar that happens to keep its size, so compare contents when the sizes match
    $same = (Test-Path $dst) -and (Get-Item $dst).Length -eq $jar.Length -and
            (Get-FileHash $dst -Algorithm MD5).Hash -eq (Get-FileHash $jar.FullName -Algorithm MD5).Hash
    if (-not $same) { Copy-Item $jar.FullName $dst -Force; $copied++ }
}

# 4. Configs, scripts, resource packs and shader packs from the pack (overwrites
#    the pack's own files; files you added yourself are left alone). Shader
#    choices and shader settings are only set the first time, so changes made
#    in-game survive updates. Delete them from the profile to get the defaults back.
$keep = @('config\iris.properties', 'config\notenoughanimations.json', 'config\DistantHorizons.toml') + @(Get-ChildItem (Join-Path $src 'shaderpacks') -Filter *.txt -ErrorAction SilentlyContinue | ForEach-Object { "shaderpacks\$($_.Name)" })
$saved = @{}
foreach ($rel in $keep) {
    $p = Join-Path $target $rel
    if (Test-Path $p) { $saved[$p] = Get-Content $p -Raw }
}
# KubeJS scripts and data belong to the pack: mirror them, so renamed or removed files don't linger
foreach ($sub in 'data', 'assets', 'server_scripts', 'startup_scripts', 'client_scripts') {
    $old = Join-Path $target "kubejs\$sub"
    if (Test-Path $old) { Remove-Item $old -Recurse -Force }
}
foreach ($dir in 'config', 'defaultconfigs', 'kubejs', 'resourcepacks', 'shaderpacks', 'cloudworks') {
    $from = Join-Path $src $dir
    if (Test-Path $from) {
        $to = Join-Path $target $dir
        New-Item -ItemType Directory -Force $to | Out-Null
        Copy-Item (Join-Path $from '*') $to -Recurse -Force
    }
}
foreach ($p in $saved.Keys) { Set-Content $p $saved[$p] -NoNewline }

# 5. Switch on the pack's resource packs in the profile's settings
#    (options.txt line: resourcePacks:["vanilla",...,"file/<name>.zip"])
$packs = Get-ChildItem (Join-Path $src 'resourcepacks') -File -ErrorAction SilentlyContinue | ForEach-Object { "file/$($_.Name)" }
if ($packs) {
    $opt = Join-Path $target 'options.txt'
    [string[]]$lines = @()
    if (Test-Path $opt) { [string[]]$lines = @(Get-Content $opt) }
    $i = -1
    for ($n = 0; $n -lt $lines.Count; $n++) { if ($lines[$n] -like 'resourcePacks:*') { $i = $n; break } }
    $current = @()
    if ($i -ge 0) { $current = @([regex]::Matches($lines[$i], '"([^"]*)"') | ForEach-Object { $_.Groups[1].Value }) }
    if ($current -notcontains 'vanilla') { $current = @('vanilla') + $current }
    # Each pack is switched on once, the first time it shows up, so turning one off (or picking another UI
    # theme with /uitheme) sticks. The light UI theme is never switched on here. The seen list ignores version
    # numbers, so an update of a pack you turned off stays off.
    $seenFile = Join-Path $target 'emp_resourcepacks_seen.txt'
    $seen = @()
    if (Test-Path $seenFile) { $seen = @(Get-Content $seenFile) }
    $packKey = { param($id) ($id.ToLower() -replace '[0-9.]+', '' -replace '\s+', ' ').Trim() }
    if (-not (Test-Path $seenFile)) { $seen = @($current | ForEach-Object { & $packKey $_ }) }
    $optional = { param($id) ($id -match '(?i)recolourful') -and ($id -notmatch '(?i)dark') }
    $added = @($packs | Where-Object { $current -notcontains $_ -and $seen -notcontains (& $packKey $_) -and -not (& $optional $_) })
    $seenNow = @($seen + @($packs | ForEach-Object { & $packKey $_ }) | Sort-Object -Unique)
    Set-Content $seenFile $seenNow
    $list = [System.Collections.Generic.List[string]]::new()
    foreach ($e in @($current) + $added) { $list.Add($e) }
    # Later in the list = higher priority. Fresh Animations: Player Extension must sit above Fresh Animations.
    $fa = -1; $pe = -1
    for ($n = 0; $n -lt $list.Count; $n++) {
        if ($list[$n] -match '(?i)player.?ext') { if ($pe -lt 0) { $pe = $n } }
        elseif ($list[$n] -match '(?i)fresh.?animations') { $fa = $n }
    }
    $moved = $false
    if ($fa -ge 0 -and $pe -ge 0 -and $pe -lt $fa) {
        $item = $list[$pe]; $list.RemoveAt($pe); $list.Insert($fa, $item); $moved = $true
    }
    if ($added -or $moved) {
        $newLine = 'resourcePacks:[' + (($list | ForEach-Object { '"' + $_ + '"' }) -join ',') + ']'
        if ($i -ge 0) { $lines[$i] = $newLine } else { $lines += $newLine }
        Set-Content $opt $lines
        if ($added) { Write-Host "Enabled resource pack(s): $($added -join ', ')" }
        if ($moved) { Write-Host 'Moved FA: Player Extension above Fresh Animations' }
    }
}

# 6. Distant Horizons: at most a quarter of the CPU threads, below normal priority. With 20 threads at normal
#    priority its far-terrain generation starved Minecraft's own chunk generation on sky runs and the server
#    thread stalled for many seconds. Only lowers values, never raises ones you set lower yourself.
$dh = Join-Path $target 'config\DistantHorizons.toml'
if (Test-Path $dh) {
    $text = Get-Content $dh -Raw
    $maxThreads = [Math]::Max(2, [Math]::Floor([Environment]::ProcessorCount / 4))
    $changed = $false
    $m = [regex]::Match($text, '(?m)^(\s*numberOfThreads\s*=\s*)(\d+)')
    if ($m.Success -and [int]$m.Groups[2].Value -gt $maxThreads) {
        $text = $text.Remove($m.Index, $m.Length).Insert($m.Index, $m.Groups[1].Value + $maxThreads); $changed = $true
    }
    $m = [regex]::Match($text, '(?m)^(\s*threadPriority\s*=\s*)(\d+)')
    if ($m.Success -and [int]$m.Groups[2].Value -gt 3) {
        $text = $text.Remove($m.Index, $m.Length).Insert($m.Index, $m.Groups[1].Value + '3'); $changed = $true
    }
    $m = [regex]::Match($text, '(?m)^(\s*maxGenerationRequestDistance\s*=\s*)(\d+)')
    if ($m.Success -and [int]$m.Groups[2].Value -gt 2048) {
        $text = $text.Remove($m.Index, $m.Length).Insert($m.Index, $m.Groups[1].Value + '2048'); $changed = $true
    }
    if ($changed) { Set-Content $dh $text -NoNewline; Write-Host "Distant Horizons: at most $maxThreads threads, low priority, far generation within 2048 blocks" }
}

# 7. Softer colour changes between biomes: Biome Blend at least 11x11 (options.txt biomeBlendRadius:5)
$opt = Join-Path $target 'options.txt'
if (Test-Path $opt) {
    [string[]]$lines = @(Get-Content $opt)
    $i = -1
    for ($n = 0; $n -lt $lines.Count; $n++) { if ($lines[$n] -like 'biomeBlendRadius:*') { $i = $n; break } }
    if ($i -lt 0) { $lines += 'biomeBlendRadius:5'; Set-Content $opt $lines; Write-Host 'Biome Blend set to 11x11' }
    elseif ([int]($lines[$i] -replace 'biomeBlendRadius:', '') -lt 5) { $lines[$i] = 'biomeBlendRadius:5'; Set-Content $opt $lines; Write-Host 'Biome Blend set to 11x11' }
} else {
    Set-Content $opt @('biomeBlendRadius:5')
}

Write-Host "Done: $($want.Count) mods in the profile ($copied copied, $removed removed)." -ForegroundColor Green
Write-Host 'Open the CurseForge app and press Play on the profile.'
Write-Host 'Tip: give Minecraft about 8 GB in CurseForge Settings > Minecraft > Allocated Memory.'
