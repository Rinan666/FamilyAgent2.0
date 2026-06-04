@echo off
:: Launch PowerShell script with bypassed execution policy
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
pause
