@echo off
rem Double-click to set up Sistema Home on the TV at 192.168.4.40.
rem To use another address: Setup-Windows.cmd -Tv 192.168.4.55
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
echo.
pause
