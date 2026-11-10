# Builds the three Skybound mods (EXP Research, Chunk Upkeep, Create: Skybound)
# and puts their jars into pack\mods, so the next server build or client install
# includes them.
#
# The mods compile against the jars in mod-files\mods, so this first makes sure
# those are downloaded and up to date.
#
#   .\tools\build-mods.ps1              (update mods, download, build)
#   .\tools\build-mods.ps1 -SkipSetup   (just build)
#
# The first build downloads Gradle, NeoForge and Minecraft (about 1-2 GB) and
# takes 5-15 minutes. Later builds take about a minute.
param([switch]$SkipSetup)
. "$PSScriptRoot\common.ps1"

if (-not $SkipSetup) {
    & "$PSScriptRoot\setup-mods.ps1"
    for ($i = 1; $i -le 4; $i++) {
        $global:LASTEXITCODE = 0
        & "$PSScriptRoot\download-mods.ps1"
        if ($LASTEXITCODE -ne 2) { break }
        Write-Host "Retrying downloads ($i of 4)..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
}
if (-not (Test-Path (Join-Path $Root 'mod-files\mods'))) { throw 'No mod-files\mods yet. Run .\tools\download-mods.ps1 first.' }

$modsDir = Join-Path $Root 'mods'
$log = Join-Path $Root 'build-mods.log'
Write-Host 'Building the mods (first run downloads NeoForge and Minecraft; be patient)...'
Push-Location $modsDir
$oldPref = $ErrorActionPreference
try {
    # Java prints warnings on stderr; don't let PowerShell treat them as a failure.
    $ErrorActionPreference = 'Continue'
    $global:LASTEXITCODE = 0
    & .\gradlew.bat build --console=plain 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $log
    $code = $LASTEXITCODE
} finally { $ErrorActionPreference = $oldPref; Pop-Location }

$errors = Join-Path $Root 'mods-errors.txt'
if ($code -ne 0) {
    Select-String -Path $log -Pattern 'error:|FAILURE|What went wrong|Could not' -Context 0, 3 |
        ForEach-Object { $_.Line; $_.Context.PostContext } | Set-Content $errors
    Write-Host "Build FAILED. Send mods-errors.txt (or build-mods.log) to Claude." -ForegroundColor Red
    exit 1
}
if (Test-Path $errors) { Remove-Item $errors }

$packwiz = Get-Packwiz
Push-Location $PackDir
try { & $packwiz refresh | Out-Null } finally { Pop-Location }
Get-ChildItem (Join-Path $PackDir 'mods') -Filter *.jar | ForEach-Object { Write-Host "  pack\mods\$($_.Name)" }
Write-Host 'Mods built and added to the pack. Next: .\tools\test-server.ps1, then .\tools\install-client.ps1' -ForegroundColor Green
