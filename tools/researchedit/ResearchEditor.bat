@echo off
rem Opens the Empyrean Engines research editor in your browser. Keep this window open while you edit; close it to stop.
cd /d "%~dp0..\.."
set PY=
where py >nul 2>nul && set PY=py -3
if not defined PY (python -c "import sys" >nul 2>nul && set PY=python)
if not defined PY (
  echo.
  echo Python 3 is not installed, and the research editor needs it.
  echo Install it with this command in PowerShell, then double-click this file again:
  echo.
  echo     winget install Python.Python.3.12
  echo.
  pause
  exit /b 1
)
%PY% tools\researchedit\serve.py %*
if errorlevel 1 pause
