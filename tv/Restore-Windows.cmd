@echo off
rem Double-click to put the TV back to stock and remove Sistema Home.
rem Keep the app installed: Restore-Windows.cmd -KeepApp
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1" %*
echo.
pause
