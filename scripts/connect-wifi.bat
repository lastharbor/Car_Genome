@echo off
setlocal
cd /d "%~dp0"
echo Launching Wi-Fi ADB Connection...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect-wifi.ps1" %*
if %ERRORLEVEL% NEQ 0 (
    pause
)
