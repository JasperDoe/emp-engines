# Builds the client pack for players:
#   build\Empyrean Engines-<version>.zip     (import in the CurseForge app)
#   build\Empyrean Engines-<version>.mrpack  (import in Modrinth app or Prism)
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

# Parity: bundle manually-distributed mods that packwiz does not track (e.g.
# Create: New Age) into the exported pack's overrides, so other players get the
# same mods as the server build and your own client. Mods packwiz DOES ship
# (even the ones it can only download by hand) are left out of overrides, so
# nothing is shipped twice.
$manualJars = @(Get-ChildItem (Join-Path $Root 'manual-mods') -Filter *.jar -ErrorAction SilentlyContinue)
if ($manualJars) {
    $tracked = @{}
    Get-ChildItem (Join-Path $PackDir 'mods') -Filter *.pw.toml | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match '(?m)^filename\s*=\s*"([^"]+)"') { $tracked[$Matches[1]] = $true }
    }
    $inject = @($manualJars | Where-Object { -not $tracked.ContainsKey($_.Name) })
    if ($inject) {
        Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
        $packs = Get-ChildItem $Build -File | Where-Object {
            $_.Extension -in '.zip', '.mrpack' -and $_.Length -gt 0 -and
            $_.Name -notlike 'emp-engines-server-*' -and $_.Name -notlike 'skybound-server-*'
        }
        foreach ($pack in $packs) {
            $zip = [IO.Compression.ZipFile]::Open($pack.FullName, [IO.Compression.ZipArchiveMode]::Update)
            try {
                foreach ($jar in $inject) {
                    $entry = "overrides/mods/$($jar.Name)"
                    @($zip.Entries | Where-Object { $_.FullName -eq $entry }) | ForEach-Object { $_.Delete() }
                    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $jar.FullName, $entry, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
                }
            } finally { $zip.Dispose() }
            Write-Host "  bundled $($inject.Count) manual mod(s) into $($pack.Name): $($inject.Name -join ', ')" -ForegroundColor Green
        }
    }
}

Get-ChildItem $Build -File | Where-Object { $_.Extension -in '.zip', '.mrpack' -and $_.Name -notlike 'emp-engines-server-*' } |
    ForEach-Object { Write-Host "  $($_.FullName)" }
