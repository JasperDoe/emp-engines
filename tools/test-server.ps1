# Stage 2 boot test: copies the server pack into build\test-server with a
# brand-new world, boots it once, and writes compat-report.txt.
# Run it after every mod change.
#
# Usage:  .\tools\test-server.ps1            (applies mods.txt, rebuilds with auto-retry, boots)
#         .\tools\test-server.ps1 -NoBuild   (reuse the last build)
#         .\tools\test-server.ps1 -Timeout 900
param([switch]$NoBuild, [int]$Timeout = 600, [int]$Retries = 4)
. "$PSScriptRoot\common.ps1"
if (-not $NoBuild) {
    # Apply any mods.txt changes (adds and removals), then build.
    & "$PSScriptRoot\setup-mods.ps1"
    # Network timeouts are common on big files; retry the build automatically.
    for ($i = 1; $i -le $Retries; $i++) {
        $global:LASTEXITCODE = 0
        & "$PSScriptRoot\build-server.ps1"
        if ($LASTEXITCODE -ne 2) { break }
        Write-Host "Retrying downloads ($i of $Retries)..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
    if ($LASTEXITCODE -ne 0) { Write-Host 'Server pack build failed; see the messages above.' -ForegroundColor Red; exit 1 }
}

$src = Join-Path $Build 'server-pack'
$srv = Join-Path $Build 'test-server'
if (-not (Test-Path $src)) { throw 'No server pack yet. Run .\tools\build-server.ps1' }

# Keep the installed NeoForge libraries between runs (slow to download),
# but replace mods, configs and the world every time.
New-Item -ItemType Directory -Force $srv | Out-Null
foreach ($d in 'mods', 'config', 'defaultconfigs', 'kubejs', 'world', 'logs', 'crash-reports') {
    $p = Join-Path $srv $d; if (Test-Path $p) { Remove-Item $p -Recurse -Force }
}
Copy-Item (Join-Path $src '*') $srv -Recurse -Force
Set-Content (Join-Path $srv 'eula.txt') 'eula=true'
(Get-Content (Join-Path $srv 'server.properties')) -replace '^online-mode=.*', 'online-mode=false' |
    Set-Content (Join-Path $srv 'server.properties')

# Boot. "stop" is queued on stdin and runs as soon as loading finishes.
Write-Host "Booting test server (up to $Timeout s)..."
$stdin = Join-Path $srv 'stdin.txt'; Set-Content $stdin 'stop'
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'start.bat' -WorkingDirectory $srv `
    -RedirectStandardInput $stdin -RedirectStandardOutput (Join-Path $srv 'boot.log') `
    -RedirectStandardError (Join-Path $srv 'boot-err.log') -NoNewWindow -PassThru
if (-not $proc.WaitForExit($Timeout * 1000)) {
    Write-Host 'Timed out, stopping the server.' -ForegroundColor Yellow
    & taskkill /PID $proc.Id /T /F | Out-Null
}

# Report
$log = Join-Path $srv 'logs\latest.log'
$text = if (Test-Path $log) { Get-Content $log } else { @() }
$boot = Get-Content (Join-Path $srv 'boot.log'), (Join-Path $srv 'boot-err.log') -ErrorAction SilentlyContinue
function Get-Hits([string[]]$lines, [string]$pattern, [int]$max = 50) {
    $hits = $lines | Select-String -Pattern $pattern | ForEach-Object { $_.Line.Trim() } | Select-Object -Unique -First $max
    if ($hits) { $hits } else { '  none' }
}
$kjsLog = Join-Path $srv 'logs\kubejs\server.log'
$kjs = if (Test-Path $kjsLog) { Get-Content $kjsLog } else { @() }
$done = [bool]($text | Select-String 'Done \(')
$crashes = Get-ChildItem (Join-Path $srv 'crash-reports') -ErrorAction SilentlyContinue
$report = @(
    "Skybound SMP compatibility report  $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    "Pack $(Get-PackValue 'version'), NeoForge $(Get-PackValue 'neoforge'), $((Get-ChildItem (Join-Path $srv 'mods') -Filter *.jar).Count) server mods"
    ''
    "RESULT: $(if ($done) { 'PASS - server finished loading' } else { 'FAIL - server did not finish loading' })"
    ''
    '== Missing or wrong-version dependencies =='
    Get-Hits ($boot + $text) 'missing or unsupported mandatory dependencies|requires .* (or above|version)|Mod .* requires'
    ''
    '== Mixin failures =='
    Get-Hits $text 'Mixin apply failed|InvalidMixinException|MixinApplyError|Critical injection failure'
    ''
    '== Duplicate or conflicting mods =='
    Get-Hits ($boot + $text) 'duplicate mod|found duplicate|incompatible mod'
    ''
    '== ERROR lines (first 80) =='
    Get-Hits $text '/ERROR\]|\[ERROR\]' 80
    ''
    '== Skybound checks (world height, nether scale) =='
    Get-Hits ($text + $kjs) '\[Skybound\]'
    ''
    '== Quests and research stages (expect 11 chapters, 9 stages) =='
    Get-Hits $text 'datapack stage definitions|registered as FTB Library stage provider|failed to register FTB Library|Ignoring non-object stage|ftbquests.*(error|exception|failed|chapters)|Loaded .* quests' 20
    ''
    '== KubeJS script errors =='
    Get-Hits $kjs 'ERROR|Error in|error:' 40
    ''
    '== Crash reports =='
    $(if ($crashes) { $crashes.FullName } else { '  none' })
)
$reportPath = Join-Path $Root 'compat-report.txt'
$report | Set-Content $reportPath
if ($done) { Write-Host 'PASS: server finished loading.' -ForegroundColor Green } else { Write-Host 'FAIL: server did not finish loading.' -ForegroundColor Red }
Write-Host "Report: $reportPath  (send it to Claude, plus any crash report)"
