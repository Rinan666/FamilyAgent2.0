@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "VENV_PYTHON=%SCRIPT_DIR%.venv\Scripts\python.exe"

if not exist "%VENV_PYTHON%" (
  echo [ERROR] Project virtual environment not found: "%VENV_PYTHON%"
  echo Create it with:
  echo   py -3.12 -m venv .venv
  echo   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
  exit /b 1
)

if "%AI_SERVICE_PORT%"=="" set AI_SERVICE_PORT=8090
"%VENV_PYTHON%" -m uvicorn app.main:app --host 0.0.0.0 --port %AI_SERVICE_PORT%
