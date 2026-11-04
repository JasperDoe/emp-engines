# Shared helpers for the Skybound SMP tools. Dot-source this: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster

$Root    = Split-Path $PSScriptRoot -Parent
$PackDir = Join-Path $Root 'pack'
$Build   = Join-Path $Root 'build'
$Bin     = Join-Path $PSScriptRoot 'bin'
New-Item -ItemType Directory -Force $Bin, $Build | Out-Null

# Use a Java 21 install even if an older Java comes first on PATH.
# Putting it first on PATH for this session also covers start.bat and other child processes.
$java21 = @(
    $env:JAVA_HOME
    (@('C:\Program Files\Eclipse Adoptium\jdk-21*', 'C:\Program Files\Java\jdk-21*',
        'C:\Program Files\Microsoft\jdk-21*', 'C:\Program Files\Zulu\zulu-21*') |
        Where-Object { Test-Path $_ } | ForEach-Object { Get-Item $_ } | Where-Object PSIsContainer |
        Sort-Object Name -Descending | ForEach-Object FullName)
) | Where-Object { $_ -and ($_ -match '(jdk|zulu)-?21') -and (Test-Path (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
if ($java21) {
    $env:JAVA_HOME = $java21
    $env:Path = (Join-Path $java21 'bin') + ';' + $env:Path
}

function Get-PackValue([string]$Key) {
    $line = Select-String -Path (Join-Path $PackDir 'pack.toml') -Pattern "^$Key\s*=" | Select-Object -First 1
    if (-not $line) { throw "No '$Key' in pack.toml" }
    return ($line.Line -split '"')[1]
}

function Get-Packwiz {
    $local = Join-Path $Bin 'packwiz.exe'
    if (Test-Path $local) { return $local }
    $onPath = Get-Command packwiz -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    Write-Host 'packwiz not found, downloading the Windows build...'
    $zip = Join-Path $Bin 'packwiz.zip'
    try {
        Invoke-WebRequest 'https://nightly.link/packwiz/packwiz/workflows/go/main/Windows%2064-bit.zip' -OutFile $zip
        Expand-Archive $zip -DestinationPath $Bin -Force
        Remove-Item $zip
    } catch {
        throw "Couldn't download packwiz. Install it from https://packwiz.infra.link/installation/ and put packwiz.exe in tools\bin."
    }
    if (-not (Test-Path $local)) { throw 'packwiz.exe missing after download. See https://packwiz.infra.link/installation/' }
    return $local
}

function Get-Bootstrap {
    $jar = Join-Path $Bin 'packwiz-installer-bootstrap.jar'
    if (-not (Test-Path $jar)) {
        Write-Host 'Downloading packwiz-installer-bootstrap...'
        Invoke-WebRequest 'https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar' -OutFile $jar
    }
    return $jar
}

# Runs a program, appends all its output to $Log, returns its exit code.
# Any question the program asks gets the default answer (Enter), and a run
# that takes longer than $TimeoutSec is killed, so a prompt can never hang a script.
function Invoke-Logged([string]$Log, [string]$Exe, [string[]]$ArgList, [int]$TimeoutSec = 900) {
    $tmp = [IO.Path]::GetTempPath()
    $in = Join-Path $tmp 'skybound-in.txt'; $out = Join-Path $tmp 'skybound-out.txt'; $err = Join-Path $tmp 'skybound-err.txt'
    Set-Content $in ("`r`n" * 20)
    $quoted = $ArgList | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
    $p = Start-Process -FilePath $Exe -ArgumentList $quoted -NoNewWindow -PassThru `
        -RedirectStandardInput $in -RedirectStandardOutput $out -RedirectStandardError $err
    $null = $p.Handle   # makes ExitCode available after exit
    if (-not $p.WaitForExit($TimeoutSec * 1000)) {
        & taskkill /PID $p.Id /T /F | Out-Null
        Add-Content $Log "!! timed out after $TimeoutSec s: $Exe $($ArgList -join ' ')"
        return 124
    }
    Get-Content $out, $err -ErrorAction SilentlyContinue | Add-Content $Log
    return $p.ExitCode
}

function Assert-Java21 {
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $v = (& java -version 2>&1 | Out-String)
    $ErrorActionPreference = $old
    if ($v -notmatch 'version "21') { throw "Java 21 is required (found: $($v.Split("`n")[0])). Install Temurin 21 from https://adoptium.net" }
}

# Some authors block automatic CurseForge downloads. Those jars are saved by
# hand into manual-mods\ and copied into place before packwiz-installer runs,
# which then accepts them (it checks the file hash).
function Copy-ManualMods([string]$TargetDir) {
    $src = Join-Path $Root 'manual-mods'
    $jars = Get-ChildItem $src -Filter *.jar -ErrorAction SilentlyContinue
    if (-not $jars) { return }
    $dest = Join-Path $TargetDir 'mods'
    New-Item -ItemType Directory -Force $dest | Out-Null
    $jars | Copy-Item -Destination $dest -Force
    Write-Host "Copied $($jars.Count) manually downloaded mod(s) from manual-mods\"
}

# Prints the manual-download instructions from a packwiz-installer log.
function Show-ManualDownloads([string]$Log) {
    $hits = Select-String -Path $Log -Pattern 'Please go to (\S+) and save this file to (.+\.jar)' -AllMatches |
        ForEach-Object { $_.Matches } | ForEach-Object { [pscustomobject]@{ Url = $_.Groups[1].Value; File = Split-Path $_.Groups[2].Value -Leaf } } |
        Sort-Object Url -Unique
    if (-not $hits) { return $false }
    Write-Host ''
    Write-Host 'These mods block automatic downloads. Download each one in your browser' -ForegroundColor Yellow
    Write-Host "and save it into: $(Join-Path $Root 'manual-mods')" -ForegroundColor Yellow
    foreach ($h in $hits) { Write-Host "  $($h.File)"; Write-Host "     $($h.Url)" }
    Write-Host 'Then run the same command again.' -ForegroundColor Yellow
    return $true
}

