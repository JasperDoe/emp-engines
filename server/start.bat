@echo off
rem Empyrean Engines server start (Windows). Installs NeoForge on first run.
setlocal
cd /d "%~dp0"
set /p NEO=<neoforge-version.txt

if not exist "libraries\net\neoforged\neoforge\%NEO%\win_args.txt" (
  echo Installing NeoForge %NEO%...
  powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEO%/neoforge-%NEO%-installer.jar' -OutFile neoforge-installer.jar" || exit /b 1
  java -jar neoforge-installer.jar --installServer > neoforge-install.log 2>&1 || (echo NeoForge install failed, see neoforge-install.log & exit /b 1)
  del neoforge-installer.jar
)

java @jvm-args.txt @libraries/net/neoforged/neoforge/%NEO%/win_args.txt nogui %*
