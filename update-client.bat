@echo off
rem Double-click to update the client: downloads new/updated mods, builds our mods, installs into the CurseForge profile.
cd /d "%~dp0"
echo === 1/2  Updating mods and building (first time after new mods: several minutes) ===
powershell -NoProfile -ExecutionPolicy Bypass -File ".\tools\build-mods.ps1"
if errorlevel 1 (
  echo.
  echo Build FAILED. Send mods-errors.txt or build-mods.log to Claude.
  pause
  exit /b 1
)
echo.
echo === 2/2  Installing into the CurseForge profile ===
powershell -NoProfile -ExecutionPolicy Bypass -File ".\tools\install-client.ps1"
echo.
echo Done. Close CurseForge's game if it was open, then launch the profile.
pause
