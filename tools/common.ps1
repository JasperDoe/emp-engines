# Shared helpers for the Skybound SMP tools. Dot-source this: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster

$Root    = Split-Path $PSScriptRoot -Parent
$PackDir = Join-Path $Root 'pack'
$Build   = Join-Path $Root 'build'
$Bin     = Join-Path $PSScriptRoot 'bin'
New-Item -ItemType Directory -Force $Bin, $Build | Out-Null

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
